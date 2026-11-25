package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.catalog.AppCatalog
import com.example.catalog.AppIconType
import com.example.catalog.BrandColorPreset
import com.example.catalog.CatalogApp
import com.example.data.repository.PStoreRepository
import com.example.installer.InstallEvent
import com.example.installer.InstallEvents
import com.example.model.AppActionState
import com.example.model.AppInstallStatus
import com.example.model.AppItemUiState
import com.example.model.AutoUpdateMode
import com.example.model.CheckIntervalHours
import com.example.model.HiddenAppInfo
import com.example.model.NetworkPreference
import com.example.model.OlderReleasesState
import com.example.model.RecentUpdateRecord
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus
import com.example.model.SettingsUiState
import com.example.model.StoreFilter
import com.example.model.ThemeMode
import com.example.util.Formatters
import com.example.util.VersionComparator
import com.example.worker.UpdateCheckWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiMessage {
    data class Snackbar(val message: String) : UiMessage
}

data class PendingInstallRequest(
    val appId: String,
    val verifiedRelease: ReleaseInfo? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val CATALOG_REFRESH_STALE_MS = 15L * 60L * 1000L
    }

    private val context: Context get() = getApplication()
    private val repository = PStoreRepository(context)

    private val _apps = MutableStateFlow<List<AppItemUiState>>(emptyList())
    val apps: StateFlow<List<AppItemUiState>> = _apps.asStateFlow()

    private val _hiddenApps = MutableStateFlow<List<HiddenAppInfo>>(emptyList())
    val hiddenApps: StateFlow<List<HiddenAppInfo>> = _hiddenApps.asStateFlow()

    private val _rateLimitNotice = MutableStateFlow<String?>(null)
    val rateLimitNotice: StateFlow<String?> = _rateLimitNotice.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _storeFilter = MutableStateFlow(StoreFilter.ALL)
    val storeFilter: StateFlow<StoreFilter> = _storeFilter.asStateFlow()

    private val _isUpdatingAll = MutableStateFlow(false)
    val isUpdatingAll: StateFlow<Boolean> = _isUpdatingAll.asStateFlow()

    private val _updatingAllCurrentIndex = MutableStateFlow(0)
    val updatingAllCurrentIndex: StateFlow<Int> = _updatingAllCurrentIndex.asStateFlow()

    private val _updatingAllTotal = MutableStateFlow(0)
    val updatingAllTotal: StateFlow<Int> = _updatingAllTotal.asStateFlow()

    private val _showUnknownAppsPermissionDialog = MutableStateFlow(false)
    val showUnknownAppsPermissionDialog: StateFlow<Boolean> = _showUnknownAppsPermissionDialog.asStateFlow()

    private val _cacheSizeFormatted = MutableStateFlow(
        Formatters.formatFileSizeAlwaysMb(repository.getCacheSizeBytes())
    )

    private val _uiMessages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 32)
    val uiMessages = _uiMessages.asSharedFlow()

    val recentUpdates: StateFlow<List<RecentUpdateRecord>> = repository.recentUpdates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<SettingsUiState> = combine(
        combine(
            repository.themeMode,
            repository.autoCheckEnabled,
            repository.checkInterval,
            repository.downloadOver
        ) { theme, autoCheck, interval, downloadOver ->
            Quad(theme, autoCheck, interval, downloadOver)
        },
        combine(
            repository.notificationsEnabled,
            repository.hasGitHubToken,
            repository.lastCheckedAtMillis,
            _cacheSizeFormatted
        ) { notifications, hasToken, lastChecked, cacheStr ->
            Quad(notifications, hasToken, lastChecked, cacheStr)
        }
    ) { first, second ->
        SettingsUiState(
            themeMode = first.a,
            autoCheckEnabled = first.b,
            checkInterval = first.c,
            downloadOver = first.d,
            notificationsEnabled = second.a,
            hasGitHubToken = second.b,
            lastCheckedAtMillis = second.c,
            cacheSizeFormatted = second.d
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private var pendingInstallRequest: PendingInstallRequest? = null
    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        observeInstallEvents()
        viewModelScope.launch {
            loadLocalState()
            val lastRefresh = repository.dataStore.getLastCatalogRefreshTimeOnce()
            val hasCachedLauncherApps = _apps.value.any { it.catalogApp.showInLauncher }
            val isStale = lastRefresh == 0L || (System.currentTimeMillis() - lastRefresh) > CATALOG_REFRESH_STALE_MS
            if (isStale || !hasCachedLauncherApps) {
                refreshReleases(isInitial = true)
            }
        }
    }

    private fun observeInstallEvents() {
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                when (event) {
                    is InstallEvent.Success -> {
                        val matchingItem = _apps.value.firstOrNull { it.packageName == event.packageName }
                        val targetId = matchingItem?.id ?: event.packageName
                        val app = matchingItem?.catalogApp
                            ?: if (event.packageName == AppCatalog.SELF_PACKAGE_NAME) AppCatalog.selfApp else null
                        if (event.version.isNotBlank()) {
                            repository.saveInstalledTag(event.packageName, event.version)
                        }
                        if (app != null && event.version.isNotBlank()) {
                            repository.recordSuccessfulUpdate(app, event.version)
                        }
                        updateActionStateByIdOrPackage(targetId, event.packageName, AppActionState.InstalledSuccess(event.version))
                        updateCacheSizeState()
                        refreshInstalledStatusNow()
                        _uiMessages.tryEmit(
                            UiMessage.Snackbar("${event.appName}: ${context.getString(R.string.status_installed_success)}")
                        )
                        delay(2_200)
                        updateActionStateByIdOrPackage(targetId, event.packageName, AppActionState.Idle)
                    }
                    is InstallEvent.SignatureConflict -> {
                        val matchingItem = _apps.value.firstOrNull { it.packageName == event.packageName }
                        val targetId = matchingItem?.id ?: event.packageName
                        val msg = context.getString(R.string.error_signature_mismatch)
                        updateActionStateByIdOrPackage(targetId, event.packageName, AppActionState.SignatureMismatch(msg))
                        _uiMessages.tryEmit(UiMessage.Snackbar(msg))
                    }
                    is InstallEvent.Failure -> {
                        val matchingItem = _apps.value.firstOrNull { it.packageName == event.packageName }
                        val targetId = matchingItem?.id ?: event.packageName
                        updateActionStateByIdOrPackage(targetId, event.packageName, AppActionState.Error(event.errorMessage))
                        _uiMessages.tryEmit(UiMessage.Snackbar("${event.appName}: ${event.errorMessage}"))
                    }
                    is InstallEvent.UserActionRequired -> {
                        // System confirmation sheet is active
                    }
                }
            }
        }
    }

    private suspend fun loadLocalState() {
        val catalogApps = repository.getCatalogApps()
        val loaded = catalogApps.map { app ->
            val installedInfo = repository.getInstalledInfoWithTag(app.packageName)
            val cachedRelease = repository.getCachedRelease(app)
            val autoUpdateMode = repository.getAutoUpdateMode(app.repoKey)
            val installStatus = repository.computeInstallStatus(installedInfo, cachedRelease)

            AppItemUiState(
                catalogApp = app,
                installedVersionName = installedInfo.versionName,
                installedVersionCode = installedInfo.versionCode,
                installedReleaseTag = installedInfo.installedReleaseTag,
                isInstalled = installedInfo.isInstalled,
                installStatus = installStatus,
                latestRelease = cachedRelease,
                releaseStatus = if (cachedRelease != null) ReleaseStatus.Success(cachedRelease) else ReleaseStatus.Loading,
                autoUpdateMode = autoUpdateMode
            )
        }
        _apps.value = loaded
        _hiddenApps.value = repository.getHiddenApps()
    }

    fun refreshInstalledStatus() {
        viewModelScope.launch {
            refreshInstalledStatusNow()
        }
    }

    private suspend fun refreshInstalledStatusNow() {
        val currentList = _apps.value
        val updated = currentList.map { item ->
            val installedInfo = repository.getInstalledInfoWithTag(item.packageName)
            val installStatus = repository.computeInstallStatus(installedInfo, item.latestRelease)
            item.copy(
                installedVersionName = installedInfo.versionName,
                installedVersionCode = installedInfo.versionCode,
                installedReleaseTag = installedInfo.installedReleaseTag,
                isInstalled = installedInfo.isInstalled,
                installStatus = installStatus
            )
        }
        _apps.value = updated
    }

    fun refreshReleases(isInitial: Boolean = false) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val previousById = _apps.value.associateBy { it.id }
                val discovery = repository.discoverAppsFromGitHub()

                val updatedItems = discovery.visibleApps.map { app ->
                    val existing = previousById[app.repoKey]
                    val installedInfo = repository.getInstalledInfoWithTag(app.packageName)
                    val release = discovery.releasesByRepoKey[app.repoKey]
                        ?: existing?.latestRelease
                        ?: repository.getCachedRelease(app)
                    val autoUpdateMode = existing?.autoUpdateMode
                        ?: repository.getAutoUpdateMode(app.repoKey)
                    val installStatus = repository.computeInstallStatus(installedInfo, release)
                    val releaseStatus = when {
                        release != null -> ReleaseStatus.Success(release)
                        discovery.isOffline -> ReleaseStatus.NoInternet(context.getString(R.string.error_no_internet))
                        discovery.rateLimitResetEpochSeconds != null -> ReleaseStatus.RateLimit(
                            message = formatRateLimitMessage(discovery.rateLimitResetEpochSeconds),
                            resetEpochSeconds = discovery.rateLimitResetEpochSeconds
                        )
                        else -> ReleaseStatus.NoRelease(context.getString(R.string.error_no_release))
                    }

                    AppItemUiState(
                        catalogApp = app,
                        installedVersionName = installedInfo.versionName,
                        installedVersionCode = installedInfo.versionCode,
                        installedReleaseTag = installedInfo.installedReleaseTag,
                        isInstalled = installedInfo.isInstalled,
                        installStatus = installStatus,
                        latestRelease = release,
                        releaseStatus = releaseStatus,
                        actionState = existing?.actionState ?: AppActionState.Idle,
                        autoUpdateMode = autoUpdateMode,
                        olderReleasesState = existing?.olderReleasesState ?: OlderReleasesState.Idle
                    )
                }

                _apps.value = updatedItems
                _hiddenApps.value = repository.getHiddenApps()

                if (discovery.rateLimitResetEpochSeconds != null) {
                    val notice = formatRateLimitMessage(discovery.rateLimitResetEpochSeconds)
                    _rateLimitNotice.value = notice
                    if (!isInitial) {
                        _uiMessages.tryEmit(UiMessage.Snackbar(notice))
                    }
                } else if (discovery.anySuccess) {
                    _rateLimitNotice.value = null
                } else if (discovery.isOffline && !isInitial) {
                    _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_no_internet)))
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun formatRateLimitMessage(resetEpochSeconds: Long?): String {
        val resetTime = resetEpochSeconds?.let { Formatters.formatResetTime(it) }.orEmpty()
        return if (resetTime.isNotBlank()) {
            context.getString(R.string.error_rate_limit_reset, resetTime)
        } else {
            context.getString(R.string.error_rate_limit_cached)
        }
    }

    fun refreshSingleApp(appId: String) {
        val target = _apps.value.firstOrNull { it.id == appId || it.packageName == appId } ?: return
        viewModelScope.launch {
            _apps.update { list ->
                list.map {
                    if (it.id == target.id) {
                        it.copy(
                            releaseStatus = ReleaseStatus.Loading,
                            actionState = if (it.actionState is AppActionState.Error || it.actionState is AppActionState.SignatureMismatch) {
                                AppActionState.Idle
                            } else {
                                it.actionState
                            }
                        )
                    } else it
                }
            }

            val status = repository.fetchLatestRelease(target.catalogApp, useETag = true)
            val installedInfo = repository.getInstalledInfoWithTag(target.packageName)

            if (status is ReleaseStatus.Success) {
                repository.setLastCheckTime(System.currentTimeMillis())
                _rateLimitNotice.value = null
            } else if (status is ReleaseStatus.RateLimit) {
                _rateLimitNotice.value = formatRateLimitMessage(status.resetEpochSeconds)
            }

            _apps.update { list ->
                list.map { item ->
                    if (item.id != target.id) return@map item
                    when (status) {
                        is ReleaseStatus.Success -> {
                            item.copy(
                                installedVersionName = installedInfo.versionName,
                                installedVersionCode = installedInfo.versionCode,
                                installedReleaseTag = installedInfo.installedReleaseTag,
                                isInstalled = installedInfo.isInstalled,
                                installStatus = repository.computeInstallStatus(installedInfo, status.release),
                                latestRelease = status.release,
                                releaseStatus = status
                            )
                        }
                        else -> item.copy(
                            installedVersionName = installedInfo.versionName,
                            installedVersionCode = installedInfo.versionCode,
                            installedReleaseTag = installedInfo.installedReleaseTag,
                            isInstalled = installedInfo.isInstalled,
                            releaseStatus = status
                        )
                    }
                }
            }
        }
    }

    fun checkForPStoreUpdate() {
        val selfApp = AppCatalog.selfApp
        viewModelScope.launch {
            val status = repository.fetchLatestRelease(selfApp, useETag = true)
            val installedInfo = repository.getInstalledInfoWithTag(selfApp.packageName)
            when (status) {
                is ReleaseStatus.Success -> {
                    repository.setLastCheckTime(System.currentTimeMillis())
                    _rateLimitNotice.value = null
                    val installStatus = repository.computeInstallStatus(installedInfo, status.release)
                    _apps.update { list ->
                        list.map { item ->
                            if (item.id == selfApp.repoKey) {
                                item.copy(
                                    installedVersionName = installedInfo.versionName,
                                    installedVersionCode = installedInfo.versionCode,
                                    installedReleaseTag = installedInfo.installedReleaseTag,
                                    isInstalled = installedInfo.isInstalled,
                                    installStatus = installStatus,
                                    latestRelease = status.release,
                                    releaseStatus = status
                                )
                            } else item
                        }
                    }
                    val message = if (installStatus == AppInstallStatus.UPDATE_AVAILABLE) {
                        context.getString(R.string.settings_pstore_update_ready, status.release.displayVersion)
                    } else {
                        context.getString(R.string.settings_pstore_up_to_date)
                    }
                    _uiMessages.tryEmit(UiMessage.Snackbar(message))
                }
                is ReleaseStatus.RateLimit -> {
                    val notice = formatRateLimitMessage(status.resetEpochSeconds)
                    _rateLimitNotice.value = notice
                    _uiMessages.tryEmit(UiMessage.Snackbar(notice))
                }
                else -> {
                    extractReleaseErrorMessage(status)?.let { msg ->
                        _uiMessages.tryEmit(UiMessage.Snackbar(msg))
                    }
                }
            }
        }
    }

    fun setStoreFilter(filter: StoreFilter) {
        _storeFilter.value = filter
    }

    /**
     * When tapping Update/Install (specificRelease == null), re-checks GitHub's latest release
     * tag_name live and compares it against the installed version/tracked tag before downloading.
     * When installing a specific rollback release from Older Versions, downloads it directly.
     */
    fun onInstallOrUpdateClicked(item: AppItemUiState, specificRelease: ReleaseInfo? = null) {
        val currentState = _apps.value.firstOrNull { it.id == item.id }?.actionState
        if (currentState is AppActionState.CheckingUpdate ||
            currentState is AppActionState.Downloading ||
            currentState is AppActionState.Verifying ||
            currentState is AppActionState.Installing
        ) {
            return
        }

        if (specificRelease != null) {
            if (!repository.installerHelper.canRequestPackageInstalls()) {
                pendingInstallRequest = PendingInstallRequest(item.id, specificRelease)
                _showUnknownAppsPermissionDialog.value = true
                return
            }
            launchDownloadAndInstall(item, specificRelease)
            return
        }

        val job = viewModelScope.launch {
            updateActionState(item.id, AppActionState.CheckingUpdate)

            val status = repository.fetchLatestRelease(item.catalogApp, useETag = true)
            val installedInfo = repository.getInstalledInfoWithTag(item.packageName)

            if (status !is ReleaseStatus.Success) {
                val errMsg = if (status is ReleaseStatus.RateLimit) {
                    val notice = formatRateLimitMessage(status.resetEpochSeconds)
                    _rateLimitNotice.value = notice
                    notice
                } else {
                    extractReleaseErrorMessage(status) ?: context.getString(R.string.error_no_release)
                }

                // If we already have a cached release and hit a rate limit, allow proceeding only on explicit retry or show clear message
                _apps.update { list ->
                    list.map { existing ->
                        if (existing.id != item.id) return@map existing
                        existing.copy(
                            installedVersionName = installedInfo.versionName,
                            installedVersionCode = installedInfo.versionCode,
                            installedReleaseTag = installedInfo.installedReleaseTag,
                            isInstalled = installedInfo.isInstalled,
                            releaseStatus = if (existing.latestRelease != null && status is ReleaseStatus.RateLimit) {
                                existing.releaseStatus
                            } else {
                                status
                            },
                            actionState = AppActionState.Error(errMsg)
                        )
                    }
                }
                _uiMessages.tryEmit(UiMessage.Snackbar(errMsg))
                return@launch
            }

            repository.setLastCheckTime(System.currentTimeMillis())
            _rateLimitNotice.value = null
            val freshRelease = status.release
            val freshInstallStatus = repository.computeInstallStatus(installedInfo, freshRelease)

            _apps.update { list ->
                list.map { existing ->
                    if (existing.id != item.id) return@map existing
                    existing.copy(
                        installedVersionName = installedInfo.versionName,
                        installedVersionCode = installedInfo.versionCode,
                        installedReleaseTag = installedInfo.installedReleaseTag,
                        isInstalled = installedInfo.isInstalled,
                        installStatus = freshInstallStatus,
                        latestRelease = freshRelease,
                        releaseStatus = status
                    )
                }
            }

            if (installedInfo.isInstalled && freshInstallStatus != AppInstallStatus.UPDATE_AVAILABLE) {
                updateActionState(item.id, AppActionState.Idle)
                val upToDateMsg = if (freshRelease.displayVersion.isNotBlank()) {
                    context.getString(R.string.status_already_up_to_date_ver, freshRelease.displayVersion)
                } else {
                    context.getString(R.string.status_already_up_to_date)
                }
                _uiMessages.tryEmit(UiMessage.Snackbar(upToDateMsg))
                return@launch
            }

            if (!repository.installerHelper.canRequestPackageInstalls()) {
                updateActionState(item.id, AppActionState.Idle)
                pendingInstallRequest = PendingInstallRequest(item.id, freshRelease)
                _showUnknownAppsPermissionDialog.value = true
                return@launch
            }

            val updatedItem = _apps.value.firstOrNull { it.id == item.id } ?: item
            downloadAndInstallRelease(updatedItem, freshRelease)
        }

        activeDownloadJobs[item.id] = job
    }

    fun dismissPermissionDialog() {
        _showUnknownAppsPermissionDialog.value = false
        pendingInstallRequest = null
    }

    fun openUnknownAppsSettings() {
        _showUnknownAppsPermissionDialog.value = false
        val intent = repository.installerHelper.createManageUnknownAppSourcesIntent()
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_generic_install)))
        }
    }

    fun resumeAfterPermissionCheck() {
        if (repository.installerHelper.canRequestPackageInstalls()) {
            _showUnknownAppsPermissionDialog.value = false
            val pending = pendingInstallRequest ?: return
            pendingInstallRequest = null
            val item = _apps.value.firstOrNull { it.id == pending.appId } ?: return
            val release = pending.verifiedRelease
            if (release != null) {
                launchDownloadAndInstall(item, release)
            } else {
                onInstallOrUpdateClicked(item)
            }
        }
    }

    fun cancelDownload(item: AppItemUiState) {
        val downloadKey = item.packageName.ifBlank { item.id }
        repository.downloadManager.cancelDownload(downloadKey)
        activeDownloadJobs[item.id]?.cancel()
        activeDownloadJobs.remove(item.id)
        updateActionState(item.id, AppActionState.Idle)
    }

    private fun launchDownloadAndInstall(item: AppItemUiState, release: ReleaseInfo) {
        val job = viewModelScope.launch {
            downloadAndInstallRelease(item, release)
        }
        activeDownloadJobs[item.id] = job
    }

    private suspend fun downloadAndInstallRelease(item: AppItemUiState, release: ReleaseInfo) {
        val networkPref = repository.downloadOver.first()
        if (networkPref == NetworkPreference.WIFI_ONLY && !repository.isOnUnmeteredWifi()) {
            val msg = context.getString(R.string.error_wifi_required)
            updateActionState(item.id, AppActionState.Error(msg))
            _uiMessages.tryEmit(UiMessage.Snackbar(msg))
            return
        }

        val targetVer = release.displayVersion
        updateActionState(
            item.id,
            AppActionState.Downloading(
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = release.apkSize,
                targetVersion = targetVer
            )
        )

        val downloadResult = repository.downloadApk(item.catalogApp, release) { progress, downloaded, total ->
            updateActionState(
                item.id,
                AppActionState.Downloading(
                    progress = progress,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    targetVersion = targetVer
                )
            )
        }

        updateCacheSizeState()

        val apkFile = downloadResult.getOrElse { err ->
            val message = err.message ?: context.getString(R.string.error_no_internet)
            updateActionState(item.id, AppActionState.Error(message))
            return
        }

        updateActionState(item.id, AppActionState.Verifying)
        val details = repository.readApkDetails(apkFile)
        if (details == null || details.packageName.isBlank()) {
            apkFile.delete()
            updateCacheSizeState()
            val err = context.getString(R.string.error_invalid_apk)
            updateActionState(item.id, AppActionState.Error(err))
            _uiMessages.tryEmit(UiMessage.Snackbar(err))
            return
        }

        val resolvedPackageName = if (item.packageName.isBlank()) {
            // Learn packageName permanently from the downloaded APK archive
            repository.learnPackageName(item.catalogApp, details.packageName)
            _apps.update { list ->
                list.map { existing ->
                    if (existing.id == item.id) {
                        existing.copy(catalogApp = existing.catalogApp.copy(packageName = details.packageName))
                    } else existing
                }
            }
            details.packageName
        } else {
            if (details.packageName != item.packageName) {
                apkFile.delete()
                updateCacheSizeState()
                val err = context.getString(R.string.error_package_mismatch)
                updateActionState(item.id, AppActionState.Error(err))
                _uiMessages.tryEmit(UiMessage.Snackbar(err))
                return
            }
            item.packageName
        }

        // Always track and report the GitHub release tag_name (ignore internal APK versionName/versionCode)
        val releaseTagToTrack = release.tagName.ifBlank { targetVer }
        updateActionState(item.id, AppActionState.Installing(VersionComparator.cleanVersion(releaseTagToTrack)))

        val installRes = repository.installerHelper.installApk(
            apkFile = apkFile,
            appName = item.catalogApp.displayName,
            expectedPackageName = resolvedPackageName,
            targetVersion = releaseTagToTrack
        )

        installRes.onFailure { err ->
            val msg = err.message ?: context.getString(R.string.error_generic_install)
            updateActionState(item.id, AppActionState.Error(msg))
            _uiMessages.tryEmit(UiMessage.Snackbar(msg))
        }
    }

    fun updateAllApps() {
        if (_isUpdatingAll.value) return
        val updatable = _apps.value
            .filter { it.updateAvailable && it.latestRelease != null }
            // Install P Store last so self-update doesn't interrupt other installs
            .sortedBy { if (it.catalogApp.isSelf) 1 else 0 }

        if (updatable.isEmpty()) {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.empty_no_updates_title)))
            return
        }

        if (!repository.installerHelper.canRequestPackageInstalls()) {
            pendingInstallRequest = PendingInstallRequest(updatable.first().id)
            _showUnknownAppsPermissionDialog.value = true
            return
        }

        viewModelScope.launch {
            _isUpdatingAll.value = true
            _updatingAllTotal.value = updatable.size

            for (index in updatable.indices) {
                val target = updatable[index]
                _updatingAllCurrentIndex.value = index + 1
                val latestState = _apps.value.firstOrNull { it.id == target.id } ?: target
                onInstallOrUpdateClicked(latestState)

                var waitedMs = 0
                while (waitedMs < 90_000) {
                    delay(1_000)
                    waitedMs += 1_000
                    val current = _apps.value.firstOrNull { it.id == target.id }
                    val state = current?.actionState
                    if (state is AppActionState.Idle ||
                        state is AppActionState.InstalledSuccess ||
                        state is AppActionState.Error ||
                        state is AppActionState.SignatureMismatch
                    ) {
                        break
                    }
                }
            }

            _isUpdatingAll.value = false
        }
    }

    fun openApp(item: AppItemUiState) {
        if (item.packageName.isBlank()) {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_cannot_open)))
            return
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_cannot_open)))
        }
    }

    fun uninstallApp(item: AppItemUiState) {
        if (item.packageName.isBlank()) return
        val intent = repository.installerHelper.createUninstallIntent(item.packageName)
        try {
            context.startActivity(intent)
            updateActionState(item.id, AppActionState.Idle)
        } catch (_: Exception) {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_generic_install)))
        }
    }

    fun openGitHubReleases(item: AppItemUiState) {
        val url = "https://github.com/${item.catalogApp.fullRepoName}/releases"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_cannot_open)))
        }
    }

    fun loadOlderReleases(item: AppItemUiState) {
        updateOlderReleasesState(item.id, OlderReleasesState.Loading)
        viewModelScope.launch {
            val result = repository.fetchAllReleases(item.catalogApp)
            result.fold(
                onSuccess = { releases ->
                    updateOlderReleasesState(item.id, OlderReleasesState.Success(releases))
                },
                onFailure = { error ->
                    updateOlderReleasesState(
                        item.id,
                        OlderReleasesState.Error(error.message ?: context.getString(R.string.error_no_internet))
                    )
                }
            )
        }
    }

    fun copyAppInfo(app: CatalogApp) {
        val text = AppCatalog.formatAppInfo(app)
        copyToClipboard(
            text = text,
            confirmation = context.getString(R.string.copied_single_app, app.displayName)
        )
    }

    fun copyAllAppsInfo() {
        val allApps = _apps.value.map { it.catalogApp }.ifEmpty { listOf(AppCatalog.selfApp) }
        val text = AppCatalog.formatAllAppsInfo(allApps)
        copyToClipboard(
            text = text,
            confirmation = context.getString(R.string.copied_all_apps, allApps.size)
        )
    }

    /**
     * Validates and adds a custom GitHub Releases app to P Store's catalog, then immediately checks its release.
     * Returns an error message string if validation fails, or null on success.
     */
    fun addApp(
        displayNameInput: String,
        packageNameInput: String,
        repoInput: String,
        colorPreset: BrandColorPreset,
        iconType: AppIconType
    ): String? {
        val displayName = displayNameInput.trim()
        if (displayName.isEmpty()) {
            return context.getString(R.string.error_add_app_name_empty)
        }

        val packageName = packageNameInput.trim()
        if (!AppCatalog.isValidPackageName(packageName)) {
            return context.getString(R.string.error_add_app_package_invalid)
        }

        val parsedRepo = AppCatalog.parseGitHubRepo(repoInput)
            ?: return context.getString(R.string.error_add_app_repo_invalid)

        val candidateKey = "${parsedRepo.first}/${parsedRepo.second}".lowercase()
        if (_apps.value.any {
                it.id == candidateKey ||
                    (it.packageName.isNotBlank() && it.packageName.equals(packageName, ignoreCase = true))
            }
        ) {
            return context.getString(R.string.error_add_app_duplicate)
        }

        val newApp = CatalogApp(
            displayName = displayName,
            packageName = packageName,
            githubOwner = parsedRepo.first,
            githubRepo = parsedRepo.second,
            brandColorDark = colorPreset.darkColor,
            brandColorLight = colorPreset.lightColor,
            darkTileTint = colorPreset.darkTileTint,
            icon = iconType,
            showInLauncher = true,
            isSelf = false
        )

        viewModelScope.launch {
            repository.addAppToCatalog(newApp)
            val installedInfo = repository.getInstalledInfoWithTag(newApp.packageName)
            val cachedRelease = repository.getCachedRelease(newApp)
            val autoUpdateMode = repository.getAutoUpdateMode(newApp.repoKey)
            val installStatus = repository.computeInstallStatus(installedInfo, cachedRelease)

            val newItem = AppItemUiState(
                catalogApp = newApp,
                installedVersionName = installedInfo.versionName,
                installedVersionCode = installedInfo.versionCode,
                installedReleaseTag = installedInfo.installedReleaseTag,
                isInstalled = installedInfo.isInstalled,
                installStatus = installStatus,
                latestRelease = cachedRelease,
                releaseStatus = if (cachedRelease != null) ReleaseStatus.Success(cachedRelease) else ReleaseStatus.Loading,
                autoUpdateMode = autoUpdateMode
            )

            _apps.update { current ->
                val withoutSelf = current.filterNot { it.catalogApp.isSelf }
                val selfApps = current.filter { it.catalogApp.isSelf }
                withoutSelf + newItem + selfApps
            }
            _hiddenApps.value = repository.getHiddenApps()

            _uiMessages.tryEmit(
                UiMessage.Snackbar(context.getString(R.string.app_added_snackbar, newApp.displayName))
            )
            refreshSingleApp(newApp.repoKey)
        }
        return null
    }

    /**
     * Hides a repo from P Store without uninstalling the app from Android.
     * Can be restored later from Settings -> Hidden apps.
     */
    fun hideApp(item: AppItemUiState) {
        if (item.catalogApp.isSelf) return
        cancelDownload(item)
        viewModelScope.launch {
            repository.hideApp(item.id)
            _apps.update { current -> current.filterNot { it.id == item.id } }
            _hiddenApps.value = repository.getHiddenApps()
            _uiMessages.tryEmit(
                UiMessage.Snackbar(context.getString(R.string.app_hidden_snackbar, item.catalogApp.displayName))
            )
        }
    }

    fun unhideApp(hiddenApp: HiddenAppInfo) {
        viewModelScope.launch {
            repository.unhideApp(hiddenApp.repoKey)
            loadLocalState()
            _uiMessages.tryEmit(
                UiMessage.Snackbar(context.getString(R.string.app_unhidden_snackbar, hiddenApp.displayName))
            )
            // If the unhidden app wasn't in local cache yet, refresh from GitHub
            if (_apps.value.none { it.id == hiddenApp.repoKey }) {
                refreshReleases()
            }
        }
    }

    private fun copyToClipboard(text: String, confirmation: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(context.getString(R.string.clipboard_label), text)
        clipboard.setPrimaryClip(clip)
        // Android 13 (API 33)+ shows its own native clipboard toast
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _uiMessages.tryEmit(UiMessage.Snackbar(confirmation))
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoCheckEnabled(enabled)
            rescheduleWorker()
        }
    }

    fun setCheckInterval(interval: CheckIntervalHours) {
        viewModelScope.launch {
            repository.setCheckInterval(interval)
            rescheduleWorker()
        }
    }

    fun setDownloadOver(pref: NetworkPreference) {
        viewModelScope.launch {
            repository.setDownloadOver(pref)
            rescheduleWorker()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun cyclePerAppAutoUpdate(item: AppItemUiState) {
        val next = when (item.autoUpdateMode) {
            AutoUpdateMode.WIFI_ONLY -> AutoUpdateMode.ALWAYS
            AutoUpdateMode.ALWAYS -> AutoUpdateMode.OFF
            AutoUpdateMode.OFF -> AutoUpdateMode.WIFI_ONLY
        }
        setPerAppAutoUpdate(item.id, next)
    }

    fun setPerAppAutoUpdate(appId: String, mode: AutoUpdateMode) {
        viewModelScope.launch {
            repository.setAutoUpdateMode(appId, mode)
            _apps.update { list ->
                list.map { if (it.id == appId) it.copy(autoUpdateMode = mode) else it }
            }
        }
    }

    fun saveGitHubToken(token: String) {
        viewModelScope.launch {
            val saved = repository.saveGitHubToken(token)
            val msg = if (saved) {
                context.getString(R.string.settings_github_token_saved)
            } else {
                context.getString(R.string.error_generic_install)
            }
            _uiMessages.tryEmit(UiMessage.Snackbar(msg))
        }
    }

    fun clearGitHubToken() {
        viewModelScope.launch {
            repository.clearGitHubToken()
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.settings_github_token_cleared)))
        }
    }

    fun clearDownloadCache() {
        val freed = repository.clearDownloadCache()
        updateCacheSizeState()
        val freedStr = Formatters.formatFileSizeAlwaysMb(freed)
        _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.settings_cache_cleared, freedStr)))
    }

    private suspend fun rescheduleWorker() {
        val enabled = repository.autoCheckEnabled.first()
        val interval = repository.checkInterval.first()
        val network = repository.downloadOver.first()
        if (enabled) {
            UpdateCheckWorker.schedule(
                context = context,
                interval = interval,
                networkPreference = network,
                replaceExisting = true
            )
        } else {
            UpdateCheckWorker.cancel(context)
        }
    }

    private fun extractReleaseErrorMessage(status: ReleaseStatus): String? = when (status) {
        is ReleaseStatus.NoInternet -> status.message
        is ReleaseStatus.RateLimit -> formatRateLimitMessage(status.resetEpochSeconds)
        is ReleaseStatus.NoApkAsset -> status.message
        is ReleaseStatus.NoRelease -> status.message
        is ReleaseStatus.RepoNotFound -> status.message
        is ReleaseStatus.Error -> status.message
        else -> null
    }

    private fun updateActionState(appId: String, state: AppActionState) {
        _apps.update { list ->
            list.map { if (it.id == appId) it.copy(actionState = state) else it }
        }
    }

    private fun updateActionStateByIdOrPackage(appId: String, packageName: String, state: AppActionState) {
        _apps.update { list ->
            list.map {
                if (it.id == appId || (packageName.isNotBlank() && it.packageName == packageName)) {
                    it.copy(actionState = state)
                } else it
            }
        }
    }

    private fun updateOlderReleasesState(appId: String, state: OlderReleasesState) {
        _apps.update { list ->
            list.map { if (it.id == appId) it.copy(olderReleasesState = state) else it }
        }
    }

    private fun updateCacheSizeState() {
        _cacheSizeFormatted.value = Formatters.formatFileSizeAlwaysMb(repository.getCacheSizeBytes())
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
