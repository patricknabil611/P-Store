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
import com.example.catalog.CatalogApp
import com.example.data.repository.PStoreRepository
import com.example.installer.InstallEvent
import com.example.installer.InstallEvents
import com.example.model.AppActionState
import com.example.model.AppInstallStatus
import com.example.model.AppItemUiState
import com.example.model.AutoUpdateMode
import com.example.model.CheckIntervalHours
import com.example.model.NetworkPreference
import com.example.model.OlderReleasesState
import com.example.model.RecentUpdateRecord
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus
import com.example.model.SettingsUiState
import com.example.model.StoreFilter
import com.example.model.ThemeMode
import com.example.util.Formatters
import com.example.worker.UpdateCheckWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val packageName: String,
    val specificRelease: ReleaseInfo? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val repository = PStoreRepository(context)

    private val _apps = MutableStateFlow<List<AppItemUiState>>(emptyList())
    val apps: StateFlow<List<AppItemUiState>> = _apps.asStateFlow()

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
        _apps.value = AppCatalog.apps.map { AppItemUiState(catalogApp = it) }
        observeInstallEvents()
        viewModelScope.launch {
            loadLocalState()
            refreshReleases(isInitial = true)
        }
    }

    private fun observeInstallEvents() {
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                when (event) {
                    is InstallEvent.Success -> {
                        val app = AppCatalog.findByPackageName(event.packageName)
                        if (app != null && event.version.isNotBlank()) {
                            repository.recordSuccessfulUpdate(app, event.version)
                        }
                        updateActionState(event.packageName, AppActionState.InstalledSuccess(event.version))
                        updateCacheSizeState()
                        refreshInstalledStatus()
                        _uiMessages.tryEmit(
                            UiMessage.Snackbar("${event.appName}: ${context.getString(R.string.status_installed_success)}")
                        )
                        delay(2_200)
                        updateActionState(event.packageName, AppActionState.Idle)
                    }
                    is InstallEvent.SignatureConflict -> {
                        val msg = context.getString(R.string.error_signature_mismatch)
                        updateActionState(event.packageName, AppActionState.SignatureMismatch(msg))
                        _uiMessages.tryEmit(UiMessage.Snackbar(msg))
                    }
                    is InstallEvent.Failure -> {
                        updateActionState(event.packageName, AppActionState.Error(event.errorMessage))
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
        val loaded = AppCatalog.apps.map { app ->
            val installedInfo = repository.getInstalledInfo(app.packageName)
            val cachedRelease = repository.getCachedRelease(app)
            val autoUpdateMode = repository.getAutoUpdateMode(app.packageName)
            val installStatus = repository.computeInstallStatus(installedInfo, cachedRelease)

            AppItemUiState(
                catalogApp = app,
                installedVersionName = installedInfo.versionName,
                installedVersionCode = installedInfo.versionCode,
                isInstalled = installedInfo.isInstalled,
                installStatus = installStatus,
                latestRelease = cachedRelease,
                releaseStatus = if (cachedRelease != null) ReleaseStatus.Success(cachedRelease) else ReleaseStatus.Loading,
                autoUpdateMode = autoUpdateMode
            )
        }
        _apps.value = loaded
    }

    fun refreshInstalledStatus() {
        _apps.update { currentList ->
            currentList.map { item ->
                val installedInfo = repository.getInstalledInfo(item.packageName)
                val installStatus = repository.computeInstallStatus(installedInfo, item.latestRelease)
                item.copy(
                    installedVersionName = installedInfo.versionName,
                    installedVersionCode = installedInfo.versionCode,
                    isInstalled = installedInfo.isInstalled,
                    installStatus = installStatus
                )
            }
        }
    }

    fun refreshReleases(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!isInitial) _isRefreshing.value = true

            val results = _apps.value.map { item ->
                async {
                    val status = repository.fetchLatestRelease(item.catalogApp)
                    item.packageName to status
                }
            }.awaitAll().toMap()

            var anySuccess = false
            var allOffline = results.isNotEmpty()

            _apps.update { currentList ->
                currentList.map { item ->
                    val status = results[item.packageName] ?: item.releaseStatus
                    if (status is ReleaseStatus.Success) anySuccess = true
                    if (status !is ReleaseStatus.NoInternet) allOffline = false

                    val installedInfo = repository.getInstalledInfo(item.packageName)
                    when (status) {
                        is ReleaseStatus.Success -> {
                            val installStatus = repository.computeInstallStatus(installedInfo, status.release)
                            item.copy(
                                installedVersionName = installedInfo.versionName,
                                installedVersionCode = installedInfo.versionCode,
                                isInstalled = installedInfo.isInstalled,
                                installStatus = installStatus,
                                latestRelease = status.release,
                                releaseStatus = status
                            )
                        }
                        is ReleaseStatus.NoInternet -> {
                            // Keep cached release visible offline while preserving status if none cached
                            if (item.latestRelease != null) {
                                item.copy(
                                    installedVersionName = installedInfo.versionName,
                                    installedVersionCode = installedInfo.versionCode,
                                    isInstalled = installedInfo.isInstalled,
                                    installStatus = repository.computeInstallStatus(installedInfo, item.latestRelease)
                                )
                            } else {
                                item.copy(
                                    installedVersionName = installedInfo.versionName,
                                    installedVersionCode = installedInfo.versionCode,
                                    isInstalled = installedInfo.isInstalled,
                                    releaseStatus = status
                                )
                            }
                        }
                        else -> {
                            item.copy(
                                installedVersionName = installedInfo.versionName,
                                installedVersionCode = installedInfo.versionCode,
                                isInstalled = installedInfo.isInstalled,
                                releaseStatus = status
                            )
                        }
                    }
                }
            }

            if (anySuccess) {
                repository.setLastCheckTime(System.currentTimeMillis())
            } else if (allOffline && !isInitial) {
                _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_no_internet)))
            }

            _isRefreshing.value = false
        }
    }

    fun refreshSingleApp(packageName: String) {
        val target = _apps.value.firstOrNull { it.packageName == packageName } ?: return
        viewModelScope.launch {
            _apps.update { list ->
                list.map {
                    if (it.packageName == packageName) {
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

            val status = repository.fetchLatestRelease(target.catalogApp)
            val installedInfo = repository.getInstalledInfo(packageName)

            _apps.update { list ->
                list.map { item ->
                    if (item.packageName != packageName) return@map item
                    when (status) {
                        is ReleaseStatus.Success -> {
                            repository.setLastCheckTime(System.currentTimeMillis())
                            item.copy(
                                installedVersionName = installedInfo.versionName,
                                installedVersionCode = installedInfo.versionCode,
                                isInstalled = installedInfo.isInstalled,
                                installStatus = repository.computeInstallStatus(installedInfo, status.release),
                                latestRelease = status.release,
                                releaseStatus = status
                            )
                        }
                        else -> item.copy(
                            installedVersionName = installedInfo.versionName,
                            installedVersionCode = installedInfo.versionCode,
                            isInstalled = installedInfo.isInstalled,
                            releaseStatus = status
                        )
                    }
                }
            }
        }
    }

    fun checkForPStoreUpdate() {
        val selfApp = AppCatalog.apps.firstOrNull { it.isSelf } ?: return
        viewModelScope.launch {
            val status = repository.fetchLatestRelease(selfApp)
            val installedInfo = repository.getInstalledInfo(selfApp.packageName)
            when (status) {
                is ReleaseStatus.Success -> {
                    repository.setLastCheckTime(System.currentTimeMillis())
                    val installStatus = repository.computeInstallStatus(installedInfo, status.release)
                    _apps.update { list ->
                        list.map { item ->
                            if (item.packageName == selfApp.packageName) {
                                item.copy(
                                    installedVersionName = installedInfo.versionName,
                                    installedVersionCode = installedInfo.versionCode,
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
                is ReleaseStatus.NoInternet -> _uiMessages.tryEmit(UiMessage.Snackbar(status.message))
                is ReleaseStatus.RateLimit -> _uiMessages.tryEmit(UiMessage.Snackbar(status.message))
                is ReleaseStatus.NoApkAsset -> _uiMessages.tryEmit(UiMessage.Snackbar(status.message))
                is ReleaseStatus.NoRelease -> _uiMessages.tryEmit(UiMessage.Snackbar(status.message))
                is ReleaseStatus.RepoNotFound -> _uiMessages.tryEmit(UiMessage.Snackbar(status.message))
                is ReleaseStatus.Error -> _uiMessages.tryEmit(UiMessage.Snackbar(status.message))
                ReleaseStatus.Loading -> Unit
            }
        }
    }

    fun setStoreFilter(filter: StoreFilter) {
        _storeFilter.value = filter
    }

    fun onInstallOrUpdateClicked(item: AppItemUiState, specificRelease: ReleaseInfo? = null) {
        if (!repository.installerHelper.canRequestPackageInstalls()) {
            pendingInstallRequest = PendingInstallRequest(item.packageName, specificRelease)
            _showUnknownAppsPermissionDialog.value = true
            return
        }
        executeInstall(item, specificRelease)
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
            val item = _apps.value.firstOrNull { it.packageName == pending.packageName } ?: return
            executeInstall(item, pending.specificRelease)
        }
    }

    fun cancelDownload(item: AppItemUiState) {
        repository.downloadManager.cancelDownload(item.packageName)
        activeDownloadJobs[item.packageName]?.cancel()
        activeDownloadJobs.remove(item.packageName)
        updateActionState(item.packageName, AppActionState.Idle)
    }

    private fun executeInstall(item: AppItemUiState, specificRelease: ReleaseInfo? = null) {
        val release = specificRelease ?: item.latestRelease
        if (release == null) {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_no_release)))
            return
        }

        val job = viewModelScope.launch {
            val networkPref = repository.downloadOver.first()
            if (networkPref == NetworkPreference.WIFI_ONLY && !repository.isOnUnmeteredWifi()) {
                val msg = context.getString(R.string.error_wifi_required)
                updateActionState(item.packageName, AppActionState.Error(msg))
                _uiMessages.tryEmit(UiMessage.Snackbar(msg))
                return@launch
            }

            val targetVer = release.displayVersion
            updateActionState(
                item.packageName,
                AppActionState.Downloading(
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSize,
                    targetVersion = targetVer
                )
            )

            val downloadResult = repository.downloadApk(item.catalogApp, release) { progress, downloaded, total ->
                updateActionState(
                    item.packageName,
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
                updateActionState(item.packageName, AppActionState.Error(message))
                return@launch
            }

            updateActionState(item.packageName, AppActionState.Verifying)
            val details = repository.readApkDetails(apkFile)
            if (details == null) {
                apkFile.delete()
                updateCacheSizeState()
                val err = context.getString(R.string.error_invalid_apk)
                updateActionState(item.packageName, AppActionState.Error(err))
                _uiMessages.tryEmit(UiMessage.Snackbar(err))
                return@launch
            }

            if (details.packageName != item.packageName) {
                apkFile.delete()
                updateCacheSizeState()
                val err = context.getString(R.string.error_package_mismatch)
                updateActionState(item.packageName, AppActionState.Error(err))
                _uiMessages.tryEmit(UiMessage.Snackbar(err))
                return@launch
            }

            // Only update latest cached APK metadata if installing the latest release
            if (specificRelease == null) {
                repository.saveApkVersionMetadata(item.catalogApp, details.versionName, details.versionCode)
                val updatedRelease = release.copy(
                    apkVersionName = details.versionName,
                    apkVersionCode = details.versionCode
                )
                val installedInfo = repository.getInstalledInfo(item.packageName)
                val status = repository.computeInstallStatus(installedInfo, updatedRelease)
                _apps.update { list ->
                    list.map {
                        if (it.packageName == item.packageName) {
                            it.copy(latestRelease = updatedRelease, installStatus = status)
                        } else it
                    }
                }
            }

            val finalVersionLabel = details.versionName ?: targetVer
            updateActionState(item.packageName, AppActionState.Installing(finalVersionLabel))

            val installRes = repository.installerHelper.installApk(
                apkFile = apkFile,
                appName = item.catalogApp.displayName,
                expectedPackageName = item.packageName,
                targetVersion = finalVersionLabel
            )

            installRes.onFailure { err ->
                val msg = err.message ?: context.getString(R.string.error_generic_install)
                updateActionState(item.packageName, AppActionState.Error(msg))
                _uiMessages.tryEmit(UiMessage.Snackbar(msg))
            }
        }

        activeDownloadJobs[item.packageName] = job
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
            pendingInstallRequest = PendingInstallRequest(updatable.first().packageName)
            _showUnknownAppsPermissionDialog.value = true
            return
        }

        viewModelScope.launch {
            _isUpdatingAll.value = true
            _updatingAllTotal.value = updatable.size

            for (index in updatable.indices) {
                val target = updatable[index]
                _updatingAllCurrentIndex.value = index + 1
                val latestState = _apps.value.firstOrNull { it.packageName == target.packageName } ?: target
                executeInstall(latestState)

                var waitedMs = 0
                while (waitedMs < 90_000) {
                    delay(1_000)
                    waitedMs += 1_000
                    val current = _apps.value.firstOrNull { it.packageName == target.packageName }
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
        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            _uiMessages.tryEmit(UiMessage.Snackbar(context.getString(R.string.error_cannot_open)))
        }
    }

    fun uninstallApp(item: AppItemUiState) {
        val intent = repository.installerHelper.createUninstallIntent(item.packageName)
        try {
            context.startActivity(intent)
            updateActionState(item.packageName, AppActionState.Idle)
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
        updateOlderReleasesState(item.packageName, OlderReleasesState.Loading)
        viewModelScope.launch {
            val result = repository.fetchAllReleases(item.catalogApp)
            result.fold(
                onSuccess = { releases ->
                    updateOlderReleasesState(item.packageName, OlderReleasesState.Success(releases))
                },
                onFailure = { error ->
                    updateOlderReleasesState(
                        item.packageName,
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
        val allApps = AppCatalog.apps
        val text = AppCatalog.formatAllAppsInfo(allApps)
        copyToClipboard(
            text = text,
            confirmation = context.getString(R.string.copied_all_apps, allApps.size)
        )
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
        setPerAppAutoUpdate(item.packageName, next)
    }

    fun setPerAppAutoUpdate(packageName: String, mode: AutoUpdateMode) {
        viewModelScope.launch {
            repository.setAutoUpdateMode(packageName, mode)
            _apps.update { list ->
                list.map { if (it.packageName == packageName) it.copy(autoUpdateMode = mode) else it }
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

    private fun updateActionState(packageName: String, state: AppActionState) {
        _apps.update { list ->
            list.map { if (it.packageName == packageName) it.copy(actionState = state) else it }
        }
    }

    private fun updateOlderReleasesState(packageName: String, state: OlderReleasesState) {
        _apps.update { list ->
            list.map { if (it.packageName == packageName) it.copy(olderReleasesState = state) else it }
        }
    }

    private fun updateCacheSizeState() {
        _cacheSizeFormatted.value = Formatters.formatFileSizeAlwaysMb(repository.getCacheSizeBytes())
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
