package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalog.AppCatalog
import com.example.catalog.CatalogApp
import com.example.data.api.GitHubApiClient
import com.example.data.preferences.PStoreDataStore
import com.example.installer.ApkDownloadManager
import com.example.installer.InstallEvent
import com.example.installer.InstallEvents
import com.example.installer.PackageInstallerHelper
import com.example.model.AppActionState
import com.example.model.AppItemUiState
import com.example.model.DetectionProgress
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus
import com.example.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiMessage {
    data class Snackbar(val message: String) : UiMessage
    data class ToastMsg(val message: String) : UiMessage
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val dataStore = PStoreDataStore(context)
    private val apiClient = GitHubApiClient()
    private val installerHelper = PackageInstallerHelper(context)
    private val downloadManager = ApkDownloadManager(context)
    private val authRepository = com.example.data.auth.AuthRepository(context)

    val currentUser = authRepository.currentUser
    val isAuthLoading = authRepository.isLoading

    private val _apps = MutableStateFlow<List<AppItemUiState>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _uiMessages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 64)
    val uiMessages = _uiMessages.asSharedFlow()

    private val _showUnknownAppsPermissionDialog = MutableStateFlow(false)
    val showUnknownAppsPermissionDialog = _showUnknownAppsPermissionDialog.asStateFlow()

    private val _detectingProgress = MutableStateFlow<DetectionProgress?>(null)
    val detectingProgress = _detectingProgress.asStateFlow()

    private val _isUpdatingAll = MutableStateFlow(false)
    val isUpdatingAll = _isUpdatingAll.asStateFlow()

    private val _updatingAllCurrentIndex = MutableStateFlow(0)
    val updatingAllCurrentIndex = _updatingAllCurrentIndex.asStateFlow()

    private val _updatingAllTotal = MutableStateFlow(0)
    val updatingAllTotal = _updatingAllTotal.asStateFlow()

    val backgroundChecksEnabled = dataStore.backgroundChecksEnabled

    private var pendingInstallApp: AppItemUiState? = null
    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        // Initialize catalog items
        val initialItems = AppCatalog.apps.map { catalogApp ->
            AppItemUiState(
                catalogApp = catalogApp,
                packageName = if (catalogApp.isSelf) context.packageName else catalogApp.defaultPackageName
            )
        }
        _apps.value = initialItems

        // Listen for install broadcast events
        observeInstallEvents()

        // Auto sign-in with Google if already signed in on device
        authRepository.attemptAutoSignIn(viewModelScope)

        // Load cached data & installed statuses immediately, then fetch latest releases
        viewModelScope.launch {
            loadInitialData()
            refreshReleases(isInitial = true)
        }
    }

    fun signInWithGoogle(activity: android.app.Activity) {
        authRepository.signInWithGoogle(
            activity = activity,
            scope = viewModelScope,
            onSuccess = {
                _uiMessages.tryEmit(UiMessage.Snackbar("Signed in successfully with Google!"))
            },
            onError = { error ->
                _uiMessages.tryEmit(UiMessage.Snackbar(error))
            }
        )
    }

    fun signOut() {
        authRepository.signOut(viewModelScope) {
            _uiMessages.tryEmit(UiMessage.Snackbar("Signed out of Google account"))
        }
    }

    private fun observeInstallEvents() {
        viewModelScope.launch {
            InstallEvents.events.collect { event ->
                when (event) {
                    is InstallEvent.Success -> {
                        updateAppActionState(event.packageName, AppActionState.InstalledSuccess)
                        _uiMessages.tryEmit(UiMessage.Snackbar("${event.appName}: Installed ✓"))
                        refreshInstalledStatus()
                        delay(2500)
                        updateAppActionState(event.packageName, AppActionState.Idle)
                    }
                    is InstallEvent.SignatureConflict -> {
                        val msg = "This update was signed with a different key. Uninstall the old version, then install again."
                        updateAppActionState(event.packageName, AppActionState.Error(msg))
                        _uiMessages.tryEmit(UiMessage.Snackbar(msg))
                    }
                    is InstallEvent.Failure -> {
                        updateAppActionState(event.packageName, AppActionState.Error(event.errorMessage))
                        _uiMessages.tryEmit(UiMessage.Snackbar("${event.appName}: ${event.errorMessage}"))
                    }
                    is InstallEvent.UserActionRequired -> {
                        // In background install waiting for user tap in system dialog
                    }
                }
            }
        }
    }

    private fun updateAppActionState(packageName: String, newState: AppActionState) {
        _apps.update { list ->
            list.map { item ->
                if (item.packageName == packageName || (item.catalogApp.isSelf && packageName == context.packageName)) {
                    item.copy(actionState = newState)
                } else {
                    item
                }
            }
        }
    }

    private fun updateAppActionStateByAppId(appId: String, newState: AppActionState) {
        _apps.update { list ->
            list.map { item ->
                if (item.id == appId) {
                    item.copy(actionState = newState)
                } else {
                    item
                }
            }
        }
    }

    /**
     * Compares installed version against the release.
     * CRITICAL: Prioritizes the APK's internal versionName & versionCode
     * when they differ from the GitHub release tag name!
     */
    private fun isUpdateAvailable(
        installedVersionName: String?,
        installedVersionCode: Long?,
        isInstalled: Boolean,
        release: ReleaseInfo?
    ): Boolean {
        if (!isInstalled || installedVersionName.isNullOrBlank() || release == null) {
            return false
        }

        val effectiveTargetVersion = release.apkVersionName ?: release.version
        val effectiveTargetCode = release.apkVersionCode

        // If APK version name is known and matches installed version name:
        if (release.apkVersionName != null) {
            val cleanInstalled = VersionComparator.cleanVersion(installedVersionName)
            val cleanApk = VersionComparator.cleanVersion(release.apkVersionName)
            if (cleanInstalled.equals(cleanApk, ignoreCase = true)) {
                // If version codes are both available, only update if APK code > installed code
                if (effectiveTargetCode != null && installedVersionCode != null) {
                    return effectiveTargetCode > installedVersionCode
                }
                return false
            }
        }

        return VersionComparator.isUpdateAvailable(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            latestVersionName = effectiveTargetVersion,
            apkVersionCode = effectiveTargetCode
        )
    }

    private suspend fun loadInitialData() {
        val pm = context.packageManager

        // Also check if any installed app's label matches catalog app names
        val installedAppsList = withContext(Dispatchers.IO) {
            try {
                pm.getInstalledApplications(0)
            } catch (_: Exception) {
                emptyList()
            }
        }

        _apps.update { currentList ->
            currentList.map { item ->
                val savedPkg = if (item.catalogApp.isSelf) {
                    context.packageName
                } else {
                    val fromStore = dataStore.getSavedPackageNameOnce(item.catalogApp.owner, item.catalogApp.repo)
                    if (fromStore != null) {
                        fromStore
                    } else {
                        // Auto-detect if already installed by label matching
                        val matched = installedAppsList.firstOrNull { appInfo ->
                            try {
                                pm.getApplicationLabel(appInfo).toString().equals(item.catalogApp.name, ignoreCase = true)
                            } catch (_: Exception) {
                                false
                            }
                        }
                        if (matched != null) {
                            // Persist discovered package name
                            viewModelScope.launch {
                                dataStore.savePackageName(item.catalogApp.owner, item.catalogApp.repo, matched.packageName)
                            }
                            matched.packageName
                        } else {
                            item.catalogApp.defaultPackageName
                        }
                    }
                }

                // Check installed version
                var installedVer: String? = null
                var installedCode: Long? = null
                var isInstalled = false

                if (savedPkg != null) {
                    try {
                        val info = pm.getPackageInfo(savedPkg, 0)
                        installedVer = info.versionName
                        installedCode = PackageInfoCompat.getLongVersionCode(info)
                        isInstalled = true
                    } catch (_: PackageManager.NameNotFoundException) {}
                }

                // Load cached release from DataStore
                var cachedRelease = dataStore.getCachedRelease(item.catalogApp.owner, item.catalogApp.repo).first()

                // If cachedRelease has no apkVersionName yet, resolve from local APK or catalog known APK version
                if (cachedRelease != null && cachedRelease.apkVersionName == null) {
                    val localApk = downloadManager.getApkFile(item.catalogApp.owner, item.catalogApp.repo)
                    if (localApk.exists()) {
                        val details = installerHelper.readApkDetails(localApk)
                        if (details != null) {
                            cachedRelease = cachedRelease.copy(
                                apkVersionName = details.versionName,
                                apkVersionCode = details.versionCode
                            )
                            viewModelScope.launch {
                                dataStore.saveApkVersion(item.catalogApp.owner, item.catalogApp.repo, details.versionName, details.versionCode)
                            }
                        }
                    } else if (item.catalogApp.knownApkVersionName != null) {
                        cachedRelease = cachedRelease.copy(
                            apkVersionName = item.catalogApp.knownApkVersionName,
                            apkVersionCode = item.catalogApp.knownApkVersionCode
                        )
                    }
                }

                val hasUpdate = isUpdateAvailable(
                    installedVersionName = installedVer,
                    installedVersionCode = installedCode,
                    isInstalled = isInstalled,
                    release = cachedRelease
                )

                item.copy(
                    packageName = savedPkg,
                    installedVersionName = installedVer,
                    installedVersionCode = installedCode,
                    isInstalled = isInstalled,
                    latestRelease = cachedRelease,
                    releaseStatus = if (cachedRelease != null) ReleaseStatus.Success(cachedRelease) else ReleaseStatus.Loading,
                    updateAvailable = hasUpdate
                )
            }
        }
    }

    fun refreshInstalledStatus() {
        val pm = context.packageManager
        _apps.update { currentList ->
            currentList.map { item ->
                val pkg = item.packageName
                var installedVer: String? = null
                var installedCode: Long? = null
                var isInstalled = false

                if (pkg != null) {
                    try {
                        val info = pm.getPackageInfo(pkg, 0)
                        installedVer = info.versionName
                        installedCode = PackageInfoCompat.getLongVersionCode(info)
                        isInstalled = true
                    } catch (_: PackageManager.NameNotFoundException) {}
                }

                val hasUpdate = isUpdateAvailable(
                    installedVersionName = installedVer,
                    installedVersionCode = installedCode,
                    isInstalled = isInstalled,
                    release = item.latestRelease
                )

                item.copy(
                    installedVersionName = installedVer,
                    installedVersionCode = installedCode,
                    isInstalled = isInstalled,
                    updateAvailable = hasUpdate
                )
            }
        }
    }

    fun refreshReleases(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!isInitial) _isRefreshing.value = true

            var networkErrorOccurred = false

            val jobs = _apps.value.map { item ->
                async {
                    val status = apiClient.getLatestRelease(item.catalogApp.owner, item.catalogApp.repo)
                    if (status is ReleaseStatus.Error && status.message == "No internet connection") {
                        networkErrorOccurred = true
                    }
                    Pair(item.id, status)
                }
            }

            val results = jobs.awaitAll().toMap()

            _apps.update { currentList ->
                currentList.map { item ->
                    val status = results[item.id] ?: item.releaseStatus
                    when (status) {
                        is ReleaseStatus.Success -> {
                            val cachedApk = dataStore.getCachedRelease(item.catalogApp.owner, item.catalogApp.repo).first()
                            val localApk = downloadManager.getApkFile(item.catalogApp.owner, item.catalogApp.repo)
                            val inspectedDetails = if (localApk.exists()) installerHelper.readApkDetails(localApk) else null

                            val resolvedApkVerName = inspectedDetails?.versionName
                                ?: status.release.apkVersionName
                                ?: cachedApk?.apkVersionName
                                ?: item.catalogApp.knownApkVersionName
                            val resolvedApkVerCode = inspectedDetails?.versionCode
                                ?: status.release.apkVersionCode
                                ?: cachedApk?.apkVersionCode
                                ?: item.catalogApp.knownApkVersionCode

                            val releaseWithApk = status.release.copy(
                                apkVersionName = resolvedApkVerName,
                                apkVersionCode = resolvedApkVerCode
                            )

                            viewModelScope.launch {
                                dataStore.saveCachedRelease(item.catalogApp.owner, item.catalogApp.repo, releaseWithApk)
                            }

                            val hasUpdate = isUpdateAvailable(
                                installedVersionName = item.installedVersionName,
                                installedVersionCode = item.installedVersionCode,
                                isInstalled = item.isInstalled,
                                release = releaseWithApk
                            )

                            item.copy(
                                latestRelease = releaseWithApk,
                                releaseStatus = ReleaseStatus.Success(releaseWithApk),
                                updateAvailable = hasUpdate
                            )
                        }
                        is ReleaseStatus.Error -> {
                            // If we have cached release, keep showing cached release but notify network error
                            if (item.latestRelease != null) {
                                item // keep cached release
                            } else {
                                item.copy(releaseStatus = status)
                            }
                        }
                        else -> {
                            item.copy(releaseStatus = status)
                        }
                    }
                }
            }

            if (networkErrorOccurred) {
                _uiMessages.tryEmit(UiMessage.Snackbar("No internet connection. Displaying cached data."))
            }

            _isRefreshing.value = false
        }
    }

    fun toggleChangelog(appId: String) {
        _apps.update { list ->
            list.map { item ->
                if (item.id == appId) item.copy(isExpandedChangelog = !item.isExpandedChangelog) else item
            }
        }
    }

    fun onInstallOrUpdateClicked(item: AppItemUiState) {
        // Step 1: Check canRequestPackageInstalls()
        if (!installerHelper.canRequestPackageInstalls()) {
            pendingInstallApp = item
            _showUnknownAppsPermissionDialog.value = true
            return
        }

        executeInstall(item)
    }

    fun dismissPermissionDialog() {
        _showUnknownAppsPermissionDialog.value = false
        pendingInstallApp = null
    }

    fun openUnknownAppsSettings() {
        _showUnknownAppsPermissionDialog.value = false
        val intent = installerHelper.createManageUnknownAppSourcesIntent()
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            _uiMessages.tryEmit(UiMessage.Snackbar("Could not open install settings"))
        }
    }

    fun resumeAfterPermissionCheck() {
        if (installerHelper.canRequestPackageInstalls()) {
            _showUnknownAppsPermissionDialog.value = false
            pendingInstallApp?.let { app ->
                pendingInstallApp = null
                executeInstall(app)
            }
        }
    }

    fun cancelDownload(item: AppItemUiState) {
        downloadManager.cancelDownload(item.id)
        activeDownloadJobs[item.id]?.cancel()
        activeDownloadJobs.remove(item.id)
        updateAppActionStateByAppId(item.id, AppActionState.Idle)
    }

    private fun executeInstall(item: AppItemUiState) {
        val release = item.latestRelease ?: run {
            _uiMessages.tryEmit(UiMessage.Snackbar("No release available to install"))
            return
        }

        val job = viewModelScope.launch {
            // 2. Download APK to cache folder with progress
            updateAppActionStateByAppId(
                item.id,
                AppActionState.Downloading(progress = 0f, downloadedBytes = 0L, totalBytes = release.apkSize)
            )

            val downloadResult = downloadManager.downloadApk(
                owner = item.catalogApp.owner,
                repo = item.catalogApp.repo,
                url = release.apkDownloadUrl
            ) { progress, downloaded, total ->
                updateAppActionStateByAppId(
                    item.id,
                    AppActionState.Downloading(progress, downloaded, total)
                )
            }

            val apkFile = downloadResult.getOrElse { error ->
                updateAppActionStateByAppId(item.id, AppActionState.Error(error.message ?: "Download failed"))
                return@launch
            }

            // 3. Inspect APK package name
            updateAppActionStateByAppId(item.id, AppActionState.Verifying)
            val details = installerHelper.readApkDetails(apkFile)
            if (details == null) {
                apkFile.delete()
                val err = "Invalid or corrupt APK"
                updateAppActionStateByAppId(item.id, AppActionState.Error(err))
                _uiMessages.tryEmit(UiMessage.Snackbar(err))
                return@launch
            }

            val detectedPackageName = details.packageName
            val savedPackage = item.packageName

            // Save and update APK's internal version name and code
            val apkVerName = details.versionName
            val apkVerCode = details.versionCode
            dataStore.saveApkVersion(item.catalogApp.owner, item.catalogApp.repo, apkVerName, apkVerCode)

            val updatedRelease = (item.latestRelease ?: release).copy(
                apkVersionName = apkVerName,
                apkVersionCode = apkVerCode
            )

            val stillNeedsUpdate = isUpdateAvailable(
                installedVersionName = item.installedVersionName,
                installedVersionCode = item.installedVersionCode,
                isInstalled = item.isInstalled,
                release = updatedRelease
            )

            _apps.update { list ->
                list.map {
                    if (it.id == item.id) it.copy(
                        latestRelease = updatedRelease,
                        updateAvailable = stillNeedsUpdate
                    ) else it
                }
            }

            if (savedPackage.isNullOrBlank()) {
                // First time downloaded: save package name automatically in DataStore!
                dataStore.savePackageName(item.catalogApp.owner, item.catalogApp.repo, detectedPackageName)
                _apps.update { list ->
                    list.map { if (it.id == item.id) it.copy(packageName = detectedPackageName) else it }
                }
            } else if (savedPackage != detectedPackageName) {
                // Package mismatch safety check!
                apkFile.delete()
                val err = "This APK belongs to a different app."
                updateAppActionStateByAppId(item.id, AppActionState.Error(err))
                _uiMessages.tryEmit(UiMessage.Snackbar(err))
                return@launch
            }

            // 4. Install with PackageInstaller session API
            updateAppActionStateByAppId(item.id, AppActionState.Installing)
            val installResult = installerHelper.installApk(
                apkFile = apkFile,
                appName = item.catalogApp.name,
                expectedPackageName = detectedPackageName
            )

            installResult.onFailure { error ->
                updateAppActionStateByAppId(item.id, AppActionState.Error(error.message ?: "Failed to start install session"))
                _uiMessages.tryEmit(UiMessage.Snackbar("Install error: ${error.message}"))
            }
        }

        activeDownloadJobs[item.id] = job
    }

    fun openInstalledApp(item: AppItemUiState) {
        val pkg = item.packageName ?: return
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            _uiMessages.tryEmit(UiMessage.Snackbar("Cannot open application"))
        }
    }

    fun openGitHubReleases(item: AppItemUiState) {
        val url = "https://github.com/${item.catalogApp.owner}/${item.catalogApp.repo}/releases"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            _uiMessages.tryEmit(UiMessage.Snackbar("No web browser available"))
        }
    }

    /**
     * "Update all" installs apps one at a time.
     * When P Store updates itself, install it last!
     */
    fun updateAllApps() {
        if (_isUpdatingAll.value) return

        val updatableApps = _apps.value.filter { it.updateAvailable && it.latestRelease != null }
        if (updatableApps.isEmpty()) {
            _uiMessages.tryEmit(UiMessage.Snackbar("All apps are up to date"))
            return
        }

        if (!installerHelper.canRequestPackageInstalls()) {
            _showUnknownAppsPermissionDialog.value = true
            return
        }

        // Sort so that P Store (isSelf == true) is installed LAST!
        val orderedApps = updatableApps.sortedBy { if (it.catalogApp.isSelf) 1 else 0 }

        viewModelScope.launch {
            _isUpdatingAll.value = true
            _updatingAllTotal.value = orderedApps.size

            for (index in orderedApps.indices) {
                val app = orderedApps[index]
                _updatingAllCurrentIndex.value = index + 1

                val latestAppState = _apps.value.firstOrNull { it.id == app.id } ?: app
                executeInstall(latestAppState)

                // Wait until this app is no longer downloading/installing before moving to the next
                var timeoutMs = 0
                while (timeoutMs < 60_000) {
                    delay(1000)
                    timeoutMs += 1000
                    val currentItem = _apps.value.firstOrNull { it.id == app.id }
                    if (currentItem?.actionState is AppActionState.Idle ||
                        currentItem?.actionState is AppActionState.InstalledSuccess ||
                        currentItem?.actionState is AppActionState.Error
                    ) {
                        break
                    }
                }
            }

            _isUpdatingAll.value = false
            _uiMessages.tryEmit(UiMessage.Snackbar("Update all completed"))
        }
    }

    /**
     * Settings: "Detect my apps" button
     * Downloads each latest APK, reads and saves the package name, then deletes the file.
     */
    fun detectMyApps() {
        viewModelScope.launch {
            val appsToDetect = _apps.value
            _detectingProgress.value = DetectionProgress(
                currentAppName = "Starting...",
                currentIndex = 0,
                totalCount = appsToDetect.size
            )

            var detectedCount = 0

            for (i in appsToDetect.indices) {
                val app = appsToDetect[i]
                _detectingProgress.value = DetectionProgress(
                    currentAppName = app.catalogApp.name,
                    currentIndex = i + 1,
                    totalCount = appsToDetect.size
                )

                if (app.catalogApp.isSelf) {
                    dataStore.savePackageName(app.catalogApp.owner, app.catalogApp.repo, context.packageName)
                    dataStore.saveApkVersion(
                        app.catalogApp.owner,
                        app.catalogApp.repo,
                        com.example.BuildConfig.VERSION_NAME,
                        com.example.BuildConfig.VERSION_CODE.toLong()
                    )
                    detectedCount++
                    continue
                }

                // If we don't have latest release yet, fetch it
                val release = app.latestRelease ?: when (val res = apiClient.getLatestRelease(app.catalogApp.owner, app.catalogApp.repo)) {
                    is ReleaseStatus.Success -> res.release
                    else -> null
                }

                if (release != null) {
                    val downloadRes = downloadManager.downloadApk(
                        owner = app.catalogApp.owner,
                        repo = app.catalogApp.repo,
                        url = release.apkDownloadUrl
                    ) { _, _, _ -> }

                    val apkFile = downloadRes.getOrNull()
                    if (apkFile != null) {
                        val details = installerHelper.readApkDetails(apkFile)
                        if (details != null) {
                            dataStore.savePackageName(app.catalogApp.owner, app.catalogApp.repo, details.packageName)
                            dataStore.saveApkVersion(app.catalogApp.owner, app.catalogApp.repo, details.versionName, details.versionCode)
                            detectedCount++
                        }
                        apkFile.delete()
                    }
                }
            }

            _detectingProgress.value = null
            loadInitialData()
            _uiMessages.tryEmit(UiMessage.Snackbar("Detected $detectedCount app package names"))
        }
    }

    fun resetPackageName(item: AppItemUiState) {
        viewModelScope.launch {
            dataStore.resetPackageName(item.catalogApp.owner, item.catalogApp.repo)
            _apps.update { list ->
                list.map {
                    if (it.id == item.id) it.copy(
                        packageName = if (it.catalogApp.isSelf) context.packageName else null,
                        isInstalled = if (it.catalogApp.isSelf) true else false,
                        installedVersionName = if (it.catalogApp.isSelf) it.installedVersionName else null
                    ) else it
                }
            }
            refreshInstalledStatus()
            _uiMessages.tryEmit(UiMessage.Snackbar("Reset package name for ${item.catalogApp.name}"))
        }
    }

    fun setBackgroundChecks(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBackgroundChecksEnabled(enabled)
            if (enabled) {
                com.example.worker.UpdateCheckWorker.schedule(context)
                _uiMessages.tryEmit(UiMessage.Snackbar("Background checks enabled (every 12h)"))
            } else {
                com.example.worker.UpdateCheckWorker.cancel(context)
                _uiMessages.tryEmit(UiMessage.Snackbar("Background checks disabled"))
            }
        }
    }

    fun clearDownloadCache(): Long {
        val bytesFreed = downloadManager.clearCache()
        val mb = bytesFreed / (1024f * 1024f)
        val formatted = String.format(java.util.Locale.US, "%.1f MB", mb)
        _uiMessages.tryEmit(UiMessage.Snackbar("Cleared $formatted from cache"))
        return bytesFreed
    }

    fun getCacheSizeFormatted(): String {
        val bytes = downloadManager.getCacheSizeBytes()
        val mb = bytes / (1024f * 1024f)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
