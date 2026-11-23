package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.pm.PackageInfoCompat
import com.example.catalog.CatalogApp
import com.example.data.api.GitHubApiClient
import com.example.data.preferences.PStoreDataStore
import com.example.installer.ApkArchiveDetails
import com.example.installer.ApkDownloadManager
import com.example.installer.PackageInstallerHelper
import com.example.model.AppInstallStatus
import com.example.model.AutoUpdateMode
import com.example.model.CheckIntervalHours
import com.example.model.NetworkPreference
import com.example.model.RecentUpdateRecord
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus
import com.example.model.ThemeMode
import com.example.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledPackageInfo(
    val isInstalled: Boolean,
    val versionName: String?,
    val versionCode: Long?
)

class PStoreRepository(
    private val context: Context,
    private val apiClient: GitHubApiClient = GitHubApiClient(),
    val dataStore: PStoreDataStore = PStoreDataStore(context),
    val downloadManager: ApkDownloadManager = ApkDownloadManager(context),
    val installerHelper: PackageInstallerHelper = PackageInstallerHelper(context)
) {

    val themeMode: Flow<ThemeMode> = dataStore.themeMode
    val autoCheckEnabled: Flow<Boolean> = dataStore.autoCheckEnabled
    val checkInterval: Flow<CheckIntervalHours> = dataStore.checkInterval
    val downloadOver: Flow<NetworkPreference> = dataStore.downloadOver
    val notificationsEnabled: Flow<Boolean> = dataStore.notificationsEnabled
    val hasGitHubToken: Flow<Boolean> = dataStore.hasGitHubToken
    val lastCheckedAtMillis: Flow<Long> = dataStore.lastCheckedAtMillis
    val recentUpdates: Flow<List<RecentUpdateRecord>> = dataStore.recentUpdates

    fun getInstalledInfo(packageName: String): InstalledPackageInfo {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            InstalledPackageInfo(
                isInstalled = true,
                versionName = info.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(info)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            InstalledPackageInfo(
                isInstalled = false,
                versionName = null,
                versionCode = null
            )
        } catch (_: Exception) {
            InstalledPackageInfo(
                isInstalled = false,
                versionName = null,
                versionCode = null
            )
        }
    }

    fun computeInstallStatus(
        installedInfo: InstalledPackageInfo,
        release: ReleaseInfo?
    ): AppInstallStatus {
        if (!installedInfo.isInstalled) return AppInstallStatus.NOT_INSTALLED
        if (release == null || installedInfo.versionName.isNullOrBlank()) return AppInstallStatus.UP_TO_DATE

        val targetVersion = release.apkVersionName ?: release.displayVersion
        val targetCode = release.apkVersionCode

        if (release.apkVersionName != null) {
            val cleanInstalled = VersionComparator.cleanVersion(installedInfo.versionName)
            val cleanApk = VersionComparator.cleanVersion(release.apkVersionName)
            if (cleanInstalled.equals(cleanApk, ignoreCase = true)) {
                val codeNewer = targetCode != null && installedInfo.versionCode != null && targetCode > installedInfo.versionCode
                return if (codeNewer) AppInstallStatus.UPDATE_AVAILABLE else AppInstallStatus.UP_TO_DATE
            }
        }

        val hasUpdate = VersionComparator.isUpdateAvailable(
            installedVersionName = installedInfo.versionName,
            installedVersionCode = installedInfo.versionCode,
            latestVersionName = targetVersion,
            apkVersionCode = targetCode
        )
        return if (hasUpdate) AppInstallStatus.UPDATE_AVAILABLE else AppInstallStatus.UP_TO_DATE
    }

    fun isOnUnmeteredWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    suspend fun getCachedRelease(app: CatalogApp): ReleaseInfo? =
        dataStore.getCachedReleaseOnce(app.githubOwner, app.githubRepo)

    suspend fun fetchLatestRelease(app: CatalogApp): ReleaseStatus {
        val token = dataStore.getGitHubTokenOnce()
        val status = apiClient.getLatestRelease(app.githubOwner, app.githubRepo, token)
        if (status is ReleaseStatus.Success) {
            val cached = dataStore.getCachedReleaseOnce(app.githubOwner, app.githubRepo)
            val sameRelease = cached != null &&
                cached.tagName == status.release.tagName &&
                cached.apkDownloadUrl == status.release.apkDownloadUrl
            val merged = status.release.copy(
                apkVersionName = if (sameRelease) cached?.apkVersionName else null,
                apkVersionCode = if (sameRelease) cached?.apkVersionCode else null
            )
            dataStore.saveCachedRelease(app.githubOwner, app.githubRepo, merged)
            return ReleaseStatus.Success(merged)
        }
        return status
    }

    suspend fun fetchAllReleases(app: CatalogApp): Result<List<ReleaseInfo>> {
        val token = dataStore.getGitHubTokenOnce()
        return apiClient.getAllReleases(app.githubOwner, app.githubRepo, token)
    }

    suspend fun downloadApk(
        app: CatalogApp,
        release: ReleaseInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> {
        val token = dataStore.getGitHubTokenOnce()
        return downloadManager.downloadApk(
            packageName = app.packageName,
            url = release.apkDownloadUrl,
            expectedSize = release.apkSize,
            token = token,
            onProgress = onProgress
        )
    }

    suspend fun readApkDetails(apkFile: File): ApkArchiveDetails? = withContext(Dispatchers.IO) {
        installerHelper.readApkDetails(apkFile)
    }

    suspend fun saveApkVersionMetadata(app: CatalogApp, versionName: String?, versionCode: Long?) {
        dataStore.saveApkVersion(app.githubOwner, app.githubRepo, versionName, versionCode)
    }

    suspend fun recordSuccessfulUpdate(app: CatalogApp, version: String) {
        dataStore.recordRecentUpdate(
            RecentUpdateRecord(
                packageName = app.packageName,
                displayName = app.displayName,
                version = VersionComparator.cleanVersion(version),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun getAutoUpdateMode(packageName: String): AutoUpdateMode =
        dataStore.getAutoUpdateModeOnce(packageName)

    suspend fun setAutoUpdateMode(packageName: String, mode: AutoUpdateMode) =
        dataStore.setAutoUpdateMode(packageName, mode)

    suspend fun setLastCheckTime(timeMs: Long) = dataStore.setLastCheckTime(timeMs)
    suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    suspend fun setAutoCheckEnabled(enabled: Boolean) = dataStore.setAutoCheckEnabled(enabled)
    suspend fun setCheckInterval(interval: CheckIntervalHours) = dataStore.setCheckInterval(interval)
    suspend fun setDownloadOver(pref: NetworkPreference) = dataStore.setDownloadOver(pref)
    suspend fun setNotificationsEnabled(enabled: Boolean) = dataStore.setNotificationsEnabled(enabled)
    suspend fun saveGitHubToken(token: String): Boolean = dataStore.saveGitHubToken(token)
    suspend fun clearGitHubToken() = dataStore.clearGitHubToken()

    fun clearDownloadCache(): Long = downloadManager.clearCache()
    fun getCacheSizeBytes(): Long = downloadManager.getCacheSizeBytes()
}
