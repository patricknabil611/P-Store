package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.pm.PackageInfoCompat
import com.example.catalog.AppCatalog
import com.example.catalog.AppIconType
import com.example.catalog.CatalogApp
import com.example.data.api.GitHubApiClient
import com.example.data.api.LatestReleaseFetchResult
import com.example.data.api.ReposFetchResult
import com.example.data.preferences.CachedCatalogAppRecord
import com.example.data.preferences.PStoreDataStore
import com.example.installer.ApkArchiveDetails
import com.example.installer.ApkDownloadManager
import com.example.installer.PackageInstallerHelper
import com.example.model.AppInstallStatus
import com.example.model.AutoUpdateMode
import com.example.model.CheckIntervalHours
import com.example.model.HiddenAppInfo
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
    val versionCode: Long?,
    val lastUpdateTime: Long = 0L,
    val installedReleaseTag: String? = null
) {
    val effectiveVersion: String?
        get() = installedReleaseTag?.takeIf { it.isNotBlank() } ?: versionName
}

data class DiscoveryResult(
    val visibleApps: List<CatalogApp>,
    val releasesByRepoKey: Map<String, ReleaseInfo>,
    val newlyDiscoveredApps: List<CatalogApp>,
    val rateLimitResetEpochSeconds: Long? = null,
    val isOffline: Boolean = false,
    val anySuccess: Boolean = false
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
    val hiddenRepoKeys: Flow<Set<String>> = dataStore.hiddenRepoKeys

    /**
     * Returns the cached catalog combining discovered GitHub apps, manually added apps,
     * and P Store itself (isSelf = true). Excludes hidden repos unless [includeHidden] is true.
     */
    suspend fun getCatalogApps(includeHidden: Boolean = false): List<CatalogApp> {
        val hiddenRepos = if (includeHidden) emptySet() else dataStore.getHiddenReposOnce()
        val learnedPackages = dataStore.getLearnedPackageNamesOnce()
        val discoveredRecords = dataStore.getDiscoveredCatalogOnce()
        val customRecords = dataStore.getCustomAppsOnce()

        return mergeCatalogApps(
            discoveredRecords = discoveredRecords,
            customRecords = customRecords,
            hiddenRepos = hiddenRepos,
            learnedPackages = learnedPackages
        )
    }

    suspend fun getHiddenApps(): List<HiddenAppInfo> {
        val hiddenKeys = dataStore.getHiddenReposOnce()
        if (hiddenKeys.isEmpty()) return emptyList()

        val allRecords = (dataStore.getDiscoveredCatalogOnce() + dataStore.getCustomAppsOnce())
            .associateBy { it.repoKey }

        return hiddenKeys.sorted().map { key ->
            val record = allRecords[key]
            if (record != null) {
                HiddenAppInfo(
                    repoKey = key,
                    displayName = record.displayName,
                    fullRepoName = "${record.githubOwner}/${record.githubRepo}"
                )
            } else {
                val repoPart = key.substringAfter('/', key)
                HiddenAppInfo(
                    repoKey = key,
                    displayName = AppCatalog.formatRepoDisplayName(repoPart),
                    fullRepoName = key
                )
            }
        }
    }

    /**
     * Automatically discovers public repos from [AppCatalog.GITHUB_USERNAME] whose latest
     * release contains an `.apk` asset, applies optional `p-store.json` metadata with silent
     * fallbacks, uses ETag / If-None-Match to conserve API quota, and preserves cached data
     * if rate-limited or offline.
     */
    suspend fun discoverAppsFromGitHub(): DiscoveryResult {
        val token = dataStore.getGitHubTokenOnce()
        val hiddenRepos = dataStore.getHiddenReposOnce()
        val learnedPackages = dataStore.getLearnedPackageNamesOnce()
        val previousDiscovered = dataStore.getDiscoveredCatalogOnce()
        val customRecords = dataStore.getCustomAppsOnce()
        val hasCompletedInitialDiscovery = dataStore.hasCompletedInitialDiscoveryOnce()
        val cachedUserRepos = dataStore.getCachedUserReposOnce()
        val cachedReposETag = if (cachedUserRepos.isNotEmpty()) dataStore.getUserReposETagOnce() else null

        val reposResult = apiClient.fetchUserRepos(
            username = AppCatalog.GITHUB_USERNAME,
            token = token,
            cachedPage1ETag = cachedReposETag
        )

        val candidateRepos = when (reposResult) {
            is ReposFetchResult.Success -> {
                dataStore.saveCachedUserRepos(reposResult.repos, reposResult.etag)
                reposResult.repos
            }
            is ReposFetchResult.NotModified -> {
                cachedUserRepos
            }
            is ReposFetchResult.RateLimited -> {
                val cachedApps = mergeCatalogApps(previousDiscovered, customRecords, hiddenRepos, learnedPackages)
                val cachedReleases = loadCachedReleasesFor(cachedApps)
                return DiscoveryResult(
                    visibleApps = cachedApps,
                    releasesByRepoKey = cachedReleases,
                    newlyDiscoveredApps = emptyList(),
                    rateLimitResetEpochSeconds = reposResult.resetEpochSeconds,
                    isOffline = false,
                    anySuccess = false
                )
            }
            is ReposFetchResult.NoInternet -> {
                val cachedApps = mergeCatalogApps(previousDiscovered, customRecords, hiddenRepos, learnedPackages)
                val cachedReleases = loadCachedReleasesFor(cachedApps)
                return DiscoveryResult(
                    visibleApps = cachedApps,
                    releasesByRepoKey = cachedReleases,
                    newlyDiscoveredApps = emptyList(),
                    rateLimitResetEpochSeconds = null,
                    isOffline = true,
                    anySuccess = false
                )
            }
            is ReposFetchResult.Error -> {
                val cachedApps = mergeCatalogApps(previousDiscovered, customRecords, hiddenRepos, learnedPackages)
                val cachedReleases = loadCachedReleasesFor(cachedApps)
                return DiscoveryResult(
                    visibleApps = cachedApps,
                    releasesByRepoKey = cachedReleases,
                    newlyDiscoveredApps = emptyList(),
                    rateLimitResetEpochSeconds = null,
                    isOffline = false,
                    anySuccess = false
                )
            }
        }

        val filteredRepos = candidateRepos.filter { repo ->
            repo.name.isNotBlank() &&
                !repo.fork &&
                !repo.archived &&
                !repo.name.equals(AppCatalog.SELF_REPO_NAME, ignoreCase = true)
        }

        val previousByKey = previousDiscovered.associateBy { it.repoKey }
        val discoveredRecords = mutableListOf<CachedCatalogAppRecord>()
        val releasesByKey = mutableMapOf<String, ReleaseInfo>()
        val newlyDiscovered = mutableListOf<CatalogApp>()

        val now = System.currentTimeMillis()
        var anySuccess = reposResult is ReposFetchResult.Success || reposResult is ReposFetchResult.NotModified
        var rateLimitReset: Long? = null
        var hitRateLimit = false
        var sawOffline = false

        for (repo in filteredRepos) {
            val owner = repo.owner?.login?.takeIf { it.isNotBlank() } ?: AppCatalog.GITHUB_USERNAME
            val repoName = repo.name.trim()
            val repoKey = "$owner/$repoName".lowercase()
            val previousRecord = previousByKey[repoKey]
            val cachedRelease = dataStore.getCachedReleaseOnce(owner, repoName)

            if (hitRateLimit) {
                if (previousRecord != null) {
                    discoveredRecords.add(previousRecord)
                    if (cachedRelease != null) {
                        releasesByKey[repoKey] = cachedRelease
                    }
                }
                continue
            }

            val cachedETag = if (cachedRelease != null) {
                dataStore.getReleaseETagOnce(owner, repoName)
            } else {
                null
            }

            val releaseResult = apiClient.fetchLatestReleaseWithETag(
                owner = owner,
                repo = repoName,
                token = token,
                cachedETag = cachedETag,
                allowPrereleaseFallback = false
            )

            when (releaseResult) {
                is LatestReleaseFetchResult.Success -> {
                    anySuccess = true
                    dataStore.saveCachedRelease(owner, repoName, releaseResult.release, releaseResult.etag)
                    releasesByKey[repoKey] = releaseResult.release

                    val built = buildDiscoveredRecord(
                        owner = owner,
                        repoName = repoName,
                        defaultBranch = repo.defaultBranch ?: "main",
                        repoDescription = repo.description,
                        previousRecord = previousRecord,
                        learnedPackages = learnedPackages,
                        token = token,
                        hasCompletedInitialDiscovery = hasCompletedInitialDiscovery,
                        nowMillis = now
                    )
                    discoveredRecords.add(built.first)
                    if (built.second && repoKey !in hiddenRepos) {
                        newlyDiscovered.add(built.first.toCatalogApp())
                    }
                }

                is LatestReleaseFetchResult.NotModified -> {
                    anySuccess = true
                    if (cachedRelease != null) {
                        releasesByKey[repoKey] = cachedRelease
                        val built = buildDiscoveredRecord(
                            owner = owner,
                            repoName = repoName,
                            defaultBranch = repo.defaultBranch ?: "main",
                            repoDescription = repo.description,
                            previousRecord = previousRecord,
                            learnedPackages = learnedPackages,
                            token = token,
                            hasCompletedInitialDiscovery = hasCompletedInitialDiscovery,
                            nowMillis = now
                        )
                        discoveredRecords.add(built.first)
                        if (built.second && repoKey !in hiddenRepos) {
                            newlyDiscovered.add(built.first.toCatalogApp())
                        }
                    } else if (previousRecord != null) {
                        discoveredRecords.add(previousRecord)
                    }
                }

                is LatestReleaseFetchResult.RateLimited -> {
                    hitRateLimit = true
                    rateLimitReset = releaseResult.resetEpochSeconds ?: rateLimitReset
                    if (previousRecord != null) {
                        discoveredRecords.add(previousRecord)
                        if (cachedRelease != null) {
                            releasesByKey[repoKey] = cachedRelease
                        }
                    }
                }

                is LatestReleaseFetchResult.NoInternet -> {
                    sawOffline = true
                    if (previousRecord != null) {
                        discoveredRecords.add(previousRecord)
                        if (cachedRelease != null) {
                            releasesByKey[repoKey] = cachedRelease
                        }
                    }
                }

                is LatestReleaseFetchResult.NoRelease,
                is LatestReleaseFetchResult.NoApkAsset,
                is LatestReleaseFetchResult.RepoNotFound -> {
                    // Repo does not have a latest release with an APK asset; skip it.
                }

                is LatestReleaseFetchResult.Error -> {
                    if (previousRecord != null) {
                        discoveredRecords.add(previousRecord)
                        if (cachedRelease != null) {
                            releasesByKey[repoKey] = cachedRelease
                        }
                    }
                }
            }
        }

        // Persist updated discovered catalog (never wipe existing cache on pure network/rate-limit failure)
        val finalDiscovered = if (discoveredRecords.isEmpty() && (hitRateLimit || sawOffline) && previousDiscovered.isNotEmpty()) {
            previousDiscovered
        } else {
            discoveredRecords
        }
        dataStore.saveDiscoveredCatalog(finalDiscovered)

        if (!hasCompletedInitialDiscovery && anySuccess && !hitRateLimit && !sawOffline) {
            dataStore.markInitialDiscoveryCompleted()
        }

        // Refresh custom apps (if any aren't already in discovered) and P Store self-update
        val discoveredKeys = finalDiscovered.map { it.repoKey }.toSet()
        val extraAppsToCheck = customRecords
            .filter { it.repoKey !in discoveredKeys && it.repoKey !in hiddenRepos }
            .map { it.toCatalogApp() } + AppCatalog.selfApp

        for (extraApp in extraAppsToCheck) {
            val cachedRel = dataStore.getCachedReleaseOnce(extraApp.githubOwner, extraApp.githubRepo)
            if (hitRateLimit) {
                if (cachedRel != null) {
                    releasesByKey[extraApp.repoKey] = cachedRel
                }
                continue
            }

            val cachedETag = if (cachedRel != null) {
                dataStore.getReleaseETagOnce(extraApp.githubOwner, extraApp.githubRepo)
            } else {
                null
            }

            val relRes = apiClient.fetchLatestReleaseWithETag(
                owner = extraApp.githubOwner,
                repo = extraApp.githubRepo,
                token = token,
                cachedETag = cachedETag,
                allowPrereleaseFallback = extraApp.isSelf
            )
            when (relRes) {
                is LatestReleaseFetchResult.Success -> {
                    anySuccess = true
                    dataStore.saveCachedRelease(extraApp.githubOwner, extraApp.githubRepo, relRes.release, relRes.etag)
                    releasesByKey[extraApp.repoKey] = relRes.release
                }
                is LatestReleaseFetchResult.NotModified -> {
                    anySuccess = true
                    if (cachedRel != null) {
                        releasesByKey[extraApp.repoKey] = cachedRel
                    }
                }
                is LatestReleaseFetchResult.RateLimited -> {
                    hitRateLimit = true
                    rateLimitReset = relRes.resetEpochSeconds ?: rateLimitReset
                    if (cachedRel != null) {
                        releasesByKey[extraApp.repoKey] = cachedRel
                    }
                }
                else -> {
                    if (cachedRel != null) {
                        releasesByKey[extraApp.repoKey] = cachedRel
                    }
                }
            }
        }

        if (anySuccess) {
            dataStore.setLastCatalogRefreshTime(now)
            dataStore.setLastCheckTime(now)
        }

        val visibleApps = mergeCatalogApps(finalDiscovered, customRecords, hiddenRepos, learnedPackages)
        return DiscoveryResult(
            visibleApps = visibleApps,
            releasesByRepoKey = releasesByKey,
            newlyDiscoveredApps = newlyDiscovered,
            rateLimitResetEpochSeconds = rateLimitReset,
            isOffline = sawOffline && !anySuccess,
            anySuccess = anySuccess
        )
    }

    /**
     * Builds a [CachedCatalogAppRecord] by checking optional `p-store.json` and falling back silently.
     * Returns a pair of (record, isNewlyDiscoveredAfterInitialRun).
     */
    private suspend fun buildDiscoveredRecord(
        owner: String,
        repoName: String,
        defaultBranch: String,
        repoDescription: String?,
        previousRecord: CachedCatalogAppRecord?,
        learnedPackages: Map<String, String>,
        token: String?,
        hasCompletedInitialDiscovery: Boolean,
        nowMillis: Long
    ): Pair<CachedCatalogAppRecord, Boolean> {
        val repoKey = "$owner/$repoName".lowercase()
        val manifest = apiClient.fetchPStoreManifest(owner, repoName, defaultBranch, token)

        val displayName = manifest?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: AppCatalog.formatRepoDisplayName(repoName)

        val description = manifest?.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: repoDescription?.trim().orEmpty()

        val resolvedPackageName = manifest?.packageName?.trim()?.takeIf { AppCatalog.isValidPackageName(it) }
            ?: learnedPackages[repoKey]?.takeIf { it.isNotBlank() }
            ?: previousRecord?.packageName?.takeIf { it.isNotBlank() }
            ?: AppCatalog.preSeededPackageForRepo(repoName)
            ?: ""

        val resolvedIconUrl = resolveManifestIconUrl(owner, repoName, defaultBranch, manifest?.icon)
        val colorPreset = AppCatalog.parseHexAccentColor(manifest?.accentColor)
            ?: AppCatalog.colorPresetForName(displayName)

        val isNewAfterInitial = previousRecord == null && hasCompletedInitialDiscovery
        val firstDiscoveredAt = when {
            previousRecord != null -> previousRecord.firstDiscoveredAtMillis
            hasCompletedInitialDiscovery -> nowMillis
            else -> 0L
        }

        val record = CachedCatalogAppRecord(
            displayName = displayName,
            packageName = resolvedPackageName,
            githubOwner = owner,
            githubRepo = repoName,
            brandColorDark = colorPreset.darkColor,
            brandColorLight = colorPreset.lightColor,
            darkTileTint = colorPreset.darkTileTint,
            iconName = AppIconType.INITIALS.name,
            description = description,
            iconUrl = resolvedIconUrl,
            firstDiscoveredAtMillis = firstDiscoveredAt
        )
        return record to isNewAfterInitial
    }

    private fun resolveManifestIconUrl(
        owner: String,
        repoName: String,
        defaultBranch: String,
        iconField: String?
    ): String? {
        val trimmed = iconField?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return trimmed
        }
        val branch = defaultBranch.ifBlank { "main" }
        val cleanPath = trimmed.trimStart('/')
        return "https://raw.githubusercontent.com/$owner/$repoName/$branch/$cleanPath"
    }

    private fun mergeCatalogApps(
        discoveredRecords: List<CachedCatalogAppRecord>,
        customRecords: List<CachedCatalogAppRecord>,
        hiddenRepos: Set<String>,
        learnedPackages: Map<String, String>
    ): List<CatalogApp> {
        val discoveredByKey = discoveredRecords.associateBy { it.repoKey }

        val visibleDiscovered = discoveredRecords
            .filterNot { it.repoKey in hiddenRepos || it.repoKey == AppCatalog.selfApp.repoKey }
            .map { record -> applyLearnedPackage(record, learnedPackages).toCatalogApp() }
            .sortedBy { it.displayName.lowercase() }

        val visibleCustom = customRecords
            .filterNot {
                it.repoKey in hiddenRepos ||
                    it.repoKey == AppCatalog.selfApp.repoKey ||
                    it.repoKey in discoveredByKey
            }
            .map { record -> applyLearnedPackage(record, learnedPackages).toCatalogApp() }
            .sortedBy { it.displayName.lowercase() }

        return visibleDiscovered + visibleCustom + AppCatalog.selfApp
    }

    private fun applyLearnedPackage(
        record: CachedCatalogAppRecord,
        learnedPackages: Map<String, String>
    ): CachedCatalogAppRecord {
        if (record.packageName.isNotBlank()) return record
        val learned = learnedPackages[record.repoKey]
            ?: AppCatalog.preSeededPackageForRepo(record.githubRepo)
        return if (!learned.isNullOrBlank()) record.copy(packageName = learned) else record
    }

    private suspend fun loadCachedReleasesFor(apps: List<CatalogApp>): Map<String, ReleaseInfo> {
        val map = mutableMapOf<String, ReleaseInfo>()
        for (app in apps) {
            val cached = dataStore.getCachedReleaseOnce(app.githubOwner, app.githubRepo)
            if (cached != null) {
                map[app.repoKey] = cached
            }
        }
        return map
    }

    /**
     * Permanently records the Android packageName extracted from a downloaded APK for a repo
     * that did not declare one in `p-store.json` or the pre-seeded map.
     */
    suspend fun learnPackageName(app: CatalogApp, learnedPackageName: String) {
        val pkg = learnedPackageName.trim()
        if (pkg.isBlank()) return
        dataStore.saveLearnedPackageName(app.repoKey, pkg)

        val discovered = dataStore.getDiscoveredCatalogOnce()
        if (discovered.any { it.repoKey == app.repoKey }) {
            val updated = discovered.map { rec ->
                if (rec.repoKey == app.repoKey) rec.copy(packageName = pkg) else rec
            }
            dataStore.saveDiscoveredCatalog(updated)
        }

        val custom = dataStore.getCustomAppsOnce()
        val matchingCustom = custom.firstOrNull { it.repoKey == app.repoKey }
        if (matchingCustom != null && matchingCustom.packageName != pkg) {
            dataStore.addOrUpdateCustomApp(matchingCustom.copy(packageName = pkg))
        }
    }

    suspend fun addAppToCatalog(app: CatalogApp) {
        dataStore.unhideRepo(app.repoKey)
        if (app.packageName.isNotBlank()) {
            dataStore.saveLearnedPackageName(app.repoKey, app.packageName)
        }
        dataStore.addOrUpdateCustomApp(CachedCatalogAppRecord.fromCatalogApp(app))
    }

    suspend fun hideApp(repoKey: String) {
        val normalized = repoKey.trim().lowercase()
        if (normalized == AppCatalog.selfApp.repoKey) return
        dataStore.hideRepo(normalized)
    }

    suspend fun unhideApp(repoKey: String) {
        dataStore.unhideRepo(repoKey)
    }

    fun getInstalledInfo(packageName: String): InstalledPackageInfo {
        if (packageName.isBlank()) {
            return InstalledPackageInfo(
                isInstalled = false,
                versionName = null,
                versionCode = null,
                lastUpdateTime = 0L
            )
        }
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            InstalledPackageInfo(
                isInstalled = true,
                versionName = info.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                lastUpdateTime = info.lastUpdateTime
            )
        } catch (_: PackageManager.NameNotFoundException) {
            InstalledPackageInfo(
                isInstalled = false,
                versionName = null,
                versionCode = null,
                lastUpdateTime = 0L
            )
        } catch (_: Exception) {
            InstalledPackageInfo(
                isInstalled = false,
                versionName = null,
                versionCode = null,
                lastUpdateTime = 0L
            )
        }
    }

    /**
     * Reads installed package info and resolves the last GitHub release tag installed via P Store.
     * Clears stored tag if the app was uninstalled or externally replaced outside P Store.
     */
    suspend fun getInstalledInfoWithTag(packageName: String): InstalledPackageInfo {
        val raw = getInstalledInfo(packageName)
        if (packageName.isBlank()) return raw
        if (!raw.isInstalled) {
            if (dataStore.getInstalledTagOnce(packageName) != null) {
                dataStore.clearInstalledTag(packageName)
            }
            return raw
        }

        val saved = dataStore.getInstalledTagOnce(packageName) ?: return raw
        val timestampMatches = saved.packageLastUpdateTime == 0L ||
            saved.packageLastUpdateTime == raw.lastUpdateTime
        return if (timestampMatches) {
            raw.copy(installedReleaseTag = saved.tagName)
        } else {
            dataStore.clearInstalledTag(packageName)
            raw
        }
    }

    suspend fun saveInstalledTag(packageName: String, tagName: String) {
        if (packageName.isBlank()) return
        val raw = getInstalledInfo(packageName)
        if (!raw.isInstalled) return
        dataStore.saveInstalledTag(
            packageName = packageName,
            tagName = tagName,
            packageLastUpdateTime = raw.lastUpdateTime
        )
    }

    fun computeInstallStatus(
        installedInfo: InstalledPackageInfo,
        release: ReleaseInfo?
    ): AppInstallStatus {
        if (!installedInfo.isInstalled) return AppInstallStatus.NOT_INSTALLED
        if (release == null) return AppInstallStatus.UP_TO_DATE
        val effectiveInstalled = installedInfo.effectiveVersion
        if (effectiveInstalled.isNullOrBlank()) return AppInstallStatus.UP_TO_DATE

        val hasUpdate = VersionComparator.isTagUpdateAvailable(
            installedVersion = effectiveInstalled,
            githubTagName = release.tagName
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

    /**
     * Fetches the latest release for a single app using ETag validation when [useETag] is true.
     * Falls back to cached release transparently on HTTP 304 Not Modified.
     */
    suspend fun fetchLatestRelease(app: CatalogApp, useETag: Boolean = true): ReleaseStatus {
        val token = dataStore.getGitHubTokenOnce()
        val cached = dataStore.getCachedReleaseOnce(app.githubOwner, app.githubRepo)
        val cachedETag = if (useETag && cached != null) {
            dataStore.getReleaseETagOnce(app.githubOwner, app.githubRepo)
        } else {
            null
        }

        val result = apiClient.fetchLatestReleaseWithETag(
            owner = app.githubOwner,
            repo = app.githubRepo,
            token = token,
            cachedETag = cachedETag,
            allowPrereleaseFallback = true
        )

        return when (result) {
            is LatestReleaseFetchResult.Success -> {
                dataStore.saveCachedRelease(app.githubOwner, app.githubRepo, result.release, result.etag)
                ReleaseStatus.Success(result.release)
            }
            is LatestReleaseFetchResult.NotModified -> {
                if (cached != null) {
                    ReleaseStatus.Success(cached)
                } else {
                    apiClient.getLatestRelease(app.githubOwner, app.githubRepo, token)
                }
            }
            is LatestReleaseFetchResult.NoRelease -> ReleaseStatus.NoRelease(result.message)
            is LatestReleaseFetchResult.NoApkAsset -> ReleaseStatus.NoApkAsset(result.message)
            is LatestReleaseFetchResult.RepoNotFound -> ReleaseStatus.RepoNotFound(result.message)
            is LatestReleaseFetchResult.RateLimited -> ReleaseStatus.RateLimit(result.message, result.resetEpochSeconds)
            is LatestReleaseFetchResult.NoInternet -> ReleaseStatus.NoInternet(result.message)
            is LatestReleaseFetchResult.Error -> ReleaseStatus.Error(result.message)
        }
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
        val downloadKey = app.packageName.ifBlank { app.repoKey }
        return downloadManager.downloadApk(
            packageName = downloadKey,
            url = release.apkDownloadUrl,
            expectedSize = release.apkSize,
            token = token,
            onProgress = onProgress
        )
    }

    suspend fun readApkDetails(apkFile: File): ApkArchiveDetails? = withContext(Dispatchers.IO) {
        installerHelper.readApkDetails(apkFile)
    }

    suspend fun recordSuccessfulUpdate(app: CatalogApp, version: String) {
        dataStore.recordRecentUpdate(
            RecentUpdateRecord(
                packageName = app.packageName,
                displayName = app.displayName,
                version = VersionComparator.cleanVersion(version),
                updatedAtMillis = System.currentTimeMillis(),
                repoKey = app.repoKey
            )
        )
    }

    suspend fun getAutoUpdateMode(identifier: String): AutoUpdateMode =
        dataStore.getAutoUpdateModeOnce(identifier)

    suspend fun setAutoUpdateMode(identifier: String, mode: AutoUpdateMode) =
        dataStore.setAutoUpdateMode(identifier, mode)

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
