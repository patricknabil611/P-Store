package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.catalog.AppIconType
import com.example.catalog.CatalogApp
import com.example.data.api.model.GitHubRepoDto
import com.example.data.security.TokenCryptoManager
import com.example.model.AutoUpdateMode
import com.example.model.CheckIntervalHours
import com.example.model.NetworkPreference
import com.example.model.RecentUpdateRecord
import com.example.model.ReleaseInfo
import com.example.model.ThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.pStoreDataStore: DataStore<Preferences> by preferencesDataStore(name = "p_store_preferences")

data class InstalledTagRecord(
    val tagName: String,
    val packageLastUpdateTime: Long
)

data class CachedCatalogAppRecord(
    val displayName: String,
    val packageName: String,
    val githubOwner: String,
    val githubRepo: String,
    val brandColorDark: Long,
    val brandColorLight: Long,
    val darkTileTint: Long,
    val iconName: String = AppIconType.INITIALS.name,
    val description: String = "",
    val iconUrl: String? = null,
    val firstDiscoveredAtMillis: Long = 0L
) {
    val repoKey: String get() = "$githubOwner/$githubRepo".lowercase()

    fun toCatalogApp(): CatalogApp = CatalogApp(
        displayName = displayName,
        packageName = packageName,
        githubOwner = githubOwner,
        githubRepo = githubRepo,
        brandColorDark = brandColorDark,
        brandColorLight = brandColorLight,
        darkTileTint = darkTileTint,
        icon = AppIconType.fromNameOrDefault(iconName),
        description = description,
        iconUrl = iconUrl,
        firstDiscoveredAtMillis = firstDiscoveredAtMillis,
        showInLauncher = true,
        isSelf = false
    )

    companion object {
        fun fromCatalogApp(app: CatalogApp): CachedCatalogAppRecord = CachedCatalogAppRecord(
            displayName = app.displayName,
            packageName = app.packageName,
            githubOwner = app.githubOwner,
            githubRepo = app.githubRepo,
            brandColorDark = app.brandColorDark,
            brandColorLight = app.brandColorLight,
            darkTileTint = app.darkTileTint,
            iconName = app.icon.name,
            description = app.description,
            iconUrl = app.iconUrl,
            firstDiscoveredAtMillis = app.firstDiscoveredAtMillis
        )
    }
}

class PStoreDataStore(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AUTO_CHECK_ENABLED = booleanPreferencesKey("background_checks_enabled")
        val KEY_CHECK_INTERVAL_HOURS = longPreferencesKey("check_interval_hours")
        val KEY_DOWNLOAD_OVER = stringPreferencesKey("download_over_network")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_ENCRYPTED_GITHUB_TOKEN = stringPreferencesKey("encrypted_github_token")
        val KEY_LAST_CHECK_TIME = longPreferencesKey("last_check_time")
        val KEY_RECENT_UPDATES_JSON = stringPreferencesKey("recent_updates_json")

        // Discovery, caching, ETag, and hide/unhide keys
        val KEY_DISCOVERED_CATALOG_JSON = stringPreferencesKey("discovered_catalog_json")
        val KEY_CUSTOM_APPS_JSON = stringPreferencesKey("custom_apps_json")
        val KEY_HIDDEN_REPOS = stringSetPreferencesKey("hidden_repos")
        val KEY_CACHED_USER_REPOS_JSON = stringPreferencesKey("cached_user_repos_json")
        val KEY_USER_REPOS_ETAG = stringPreferencesKey("user_repos_etag")
        val KEY_HAS_INITIAL_DISCOVERY = booleanPreferencesKey("has_initial_discovery")
        val KEY_LAST_CATALOG_REFRESH_TIME = longPreferencesKey("last_catalog_refresh_time")
        val KEY_LEARNED_PACKAGES_JSON = stringPreferencesKey("learned_packages_json")

        private const val MAX_RECENT_UPDATES = 20

        private val moshi: Moshi by lazy {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        }
        private val recentUpdatesAdapter by lazy {
            moshi.adapter<List<RecentUpdateRecord>>(
                Types.newParameterizedType(List::class.java, RecentUpdateRecord::class.java)
            )
        }
        private val catalogRecordsAdapter by lazy {
            moshi.adapter<List<CachedCatalogAppRecord>>(
                Types.newParameterizedType(List::class.java, CachedCatalogAppRecord::class.java)
            )
        }
        private val repoDtosAdapter by lazy {
            moshi.adapter<List<GitHubRepoDto>>(
                Types.newParameterizedType(List::class.java, GitHubRepoDto::class.java)
            )
        }
        private val stringMapAdapter by lazy {
            moshi.adapter<Map<String, String>>(
                Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            )
        }

        private fun sanitize(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9_]"), "_")

        fun autoUpdateKey(identifier: String) =
            stringPreferencesKey("auto_update_${sanitize(identifier)}")

        fun releaseTagKey(owner: String, repo: String) =
            stringPreferencesKey("rel_tag_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseVersionKey(owner: String, repo: String) =
            stringPreferencesKey("rel_ver_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseUrlKey(owner: String, repo: String) =
            stringPreferencesKey("rel_url_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseSizeKey(owner: String, repo: String) =
            longPreferencesKey("rel_size_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseBodyKey(owner: String, repo: String) =
            stringPreferencesKey("rel_body_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseDateKey(owner: String, repo: String) =
            stringPreferencesKey("rel_date_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseRawDateKey(owner: String, repo: String) =
            stringPreferencesKey("rel_rawdate_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseETagKey(owner: String, repo: String) =
            stringPreferencesKey("rel_etag_${sanitize(owner)}_${sanitize(repo)}")

        fun installedTagKey(packageName: String) =
            stringPreferencesKey("installed_tag_${sanitize(packageName)}")

        fun installedUpdateTimeKey(packageName: String) =
            longPreferencesKey("installed_updatetime_${sanitize(packageName)}")
    }

    val themeMode: Flow<ThemeMode> = context.pStoreDataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val autoCheckEnabled: Flow<Boolean> = context.pStoreDataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_ENABLED] ?: true
    }

    val checkInterval: Flow<CheckIntervalHours> = context.pStoreDataStore.data.map { prefs ->
        CheckIntervalHours.fromHours(prefs[KEY_CHECK_INTERVAL_HOURS] ?: 6L)
    }

    val downloadOver: Flow<NetworkPreference> = context.pStoreDataStore.data.map { prefs ->
        val raw = prefs[KEY_DOWNLOAD_OVER]
        NetworkPreference.entries.firstOrNull { it.name == raw } ?: NetworkPreference.WIFI_ONLY
    }

    val notificationsEnabled: Flow<Boolean> = context.pStoreDataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val hasGitHubToken: Flow<Boolean> = context.pStoreDataStore.data.map { prefs ->
        !prefs[KEY_ENCRYPTED_GITHUB_TOKEN].isNullOrBlank()
    }

    val lastCheckedAtMillis: Flow<Long> = context.pStoreDataStore.data.map { prefs ->
        prefs[KEY_LAST_CHECK_TIME] ?: 0L
    }

    val recentUpdates: Flow<List<RecentUpdateRecord>> = context.pStoreDataStore.data.map { prefs ->
        val raw = prefs[KEY_RECENT_UPDATES_JSON] ?: return@map emptyList()
        try {
            recentUpdatesAdapter.fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val hiddenRepoKeys: Flow<Set<String>> = context.pStoreDataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_REPOS].orEmpty()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.pStoreDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.pStoreDataStore.edit { it[KEY_AUTO_CHECK_ENABLED] = enabled }
    }

    suspend fun setCheckInterval(interval: CheckIntervalHours) {
        context.pStoreDataStore.edit { it[KEY_CHECK_INTERVAL_HOURS] = interval.hours }
    }

    suspend fun setDownloadOver(pref: NetworkPreference) {
        context.pStoreDataStore.edit { it[KEY_DOWNLOAD_OVER] = pref.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.pStoreDataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLastCheckTime(timeMs: Long) {
        context.pStoreDataStore.edit { it[KEY_LAST_CHECK_TIME] = timeMs }
    }

    suspend fun getLastCatalogRefreshTimeOnce(): Long {
        val prefs = context.pStoreDataStore.data.first()
        return prefs[KEY_LAST_CATALOG_REFRESH_TIME] ?: 0L
    }

    suspend fun setLastCatalogRefreshTime(timeMs: Long) {
        context.pStoreDataStore.edit { it[KEY_LAST_CATALOG_REFRESH_TIME] = timeMs }
    }

    suspend fun hasCompletedInitialDiscoveryOnce(): Boolean {
        val prefs = context.pStoreDataStore.data.first()
        return prefs[KEY_HAS_INITIAL_DISCOVERY] ?: false
    }

    suspend fun markInitialDiscoveryCompleted() {
        context.pStoreDataStore.edit { it[KEY_HAS_INITIAL_DISCOVERY] = true }
    }

    suspend fun saveGitHubToken(plainToken: String): Boolean {
        val trimmed = plainToken.trim()
        if (trimmed.isEmpty()) {
            clearGitHubToken()
            return true
        }
        val encrypted = TokenCryptoManager.encrypt(trimmed) ?: return false
        context.pStoreDataStore.edit { it[KEY_ENCRYPTED_GITHUB_TOKEN] = encrypted }
        return true
    }

    suspend fun clearGitHubToken() {
        context.pStoreDataStore.edit { it.remove(KEY_ENCRYPTED_GITHUB_TOKEN) }
    }

    suspend fun getGitHubTokenOnce(): String? {
        val prefs = context.pStoreDataStore.data.first()
        val encrypted = prefs[KEY_ENCRYPTED_GITHUB_TOKEN] ?: return null
        return TokenCryptoManager.decrypt(encrypted)
    }

    suspend fun getAutoUpdateModeOnce(identifier: String): AutoUpdateMode {
        val prefs = context.pStoreDataStore.data.first()
        val raw = prefs[autoUpdateKey(identifier)]
        return AutoUpdateMode.entries.firstOrNull { it.name == raw } ?: AutoUpdateMode.WIFI_ONLY
    }

    suspend fun setAutoUpdateMode(identifier: String, mode: AutoUpdateMode) {
        context.pStoreDataStore.edit { it[autoUpdateKey(identifier)] = mode.name }
    }

    suspend fun recordRecentUpdate(record: RecentUpdateRecord) {
        context.pStoreDataStore.edit { prefs ->
            val current = try {
                prefs[KEY_RECENT_UPDATES_JSON]?.let { recentUpdatesAdapter.fromJson(it) }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val updated = (listOf(record) + current).take(MAX_RECENT_UPDATES)
            prefs[KEY_RECENT_UPDATES_JSON] = recentUpdatesAdapter.toJson(updated)
        }
    }

    // ---- Discovered & Custom Catalog Cache ----

    suspend fun getDiscoveredCatalogOnce(): List<CachedCatalogAppRecord> {
        val prefs = context.pStoreDataStore.data.first()
        val raw = prefs[KEY_DISCOVERED_CATALOG_JSON] ?: return emptyList()
        return try {
            catalogRecordsAdapter.fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveDiscoveredCatalog(records: List<CachedCatalogAppRecord>) {
        context.pStoreDataStore.edit { prefs ->
            prefs[KEY_DISCOVERED_CATALOG_JSON] = catalogRecordsAdapter.toJson(records)
        }
    }

    suspend fun getCustomAppsOnce(): List<CachedCatalogAppRecord> {
        val prefs = context.pStoreDataStore.data.first()
        val raw = prefs[KEY_CUSTOM_APPS_JSON] ?: return emptyList()
        return try {
            catalogRecordsAdapter.fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addOrUpdateCustomApp(record: CachedCatalogAppRecord) {
        context.pStoreDataStore.edit { prefs ->
            val current = try {
                prefs[KEY_CUSTOM_APPS_JSON]?.let { catalogRecordsAdapter.fromJson(it) }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val updated = current.filterNot { it.repoKey == record.repoKey } + record
            prefs[KEY_CUSTOM_APPS_JSON] = catalogRecordsAdapter.toJson(updated)
        }
    }

    suspend fun removeCustomApp(repoKey: String) {
        context.pStoreDataStore.edit { prefs ->
            val current = try {
                prefs[KEY_CUSTOM_APPS_JSON]?.let { catalogRecordsAdapter.fromJson(it) }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val updated = current.filterNot { it.repoKey == repoKey.lowercase() }
            prefs[KEY_CUSTOM_APPS_JSON] = catalogRecordsAdapter.toJson(updated)
        }
    }

    // ---- Hidden Repos ----

    suspend fun getHiddenReposOnce(): Set<String> {
        val prefs = context.pStoreDataStore.data.first()
        return prefs[KEY_HIDDEN_REPOS].orEmpty()
    }

    suspend fun hideRepo(repoKey: String) {
        val normalized = repoKey.trim().lowercase()
        if (normalized.isEmpty()) return
        context.pStoreDataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_REPOS].orEmpty()
            prefs[KEY_HIDDEN_REPOS] = current + normalized
        }
    }

    suspend fun unhideRepo(repoKey: String) {
        val normalized = repoKey.trim().lowercase()
        context.pStoreDataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_REPOS].orEmpty()
            if (normalized in current) {
                prefs[KEY_HIDDEN_REPOS] = current - normalized
            }
        }
    }

    // ---- Learned Repo -> PackageName mapping (persisted permanently after APK download) ----

    suspend fun getLearnedPackageNamesOnce(): Map<String, String> {
        val prefs = context.pStoreDataStore.data.first()
        val raw = prefs[KEY_LEARNED_PACKAGES_JSON] ?: return emptyMap()
        return try {
            stringMapAdapter.fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveLearnedPackageName(repoKey: String, packageName: String) {
        val key = repoKey.trim().lowercase()
        val pkg = packageName.trim()
        if (key.isEmpty() || pkg.isEmpty()) return
        context.pStoreDataStore.edit { prefs ->
            val current = try {
                prefs[KEY_LEARNED_PACKAGES_JSON]?.let { stringMapAdapter.fromJson(it) }.orEmpty()
            } catch (_: Exception) {
                emptyMap()
            }
            val updated = current.toMutableMap().apply { put(key, pkg) }
            prefs[KEY_LEARNED_PACKAGES_JSON] = stringMapAdapter.toJson(updated)
        }
    }

    // ---- ETag & Cached User Repos ----

    suspend fun getUserReposETagOnce(): String? {
        val prefs = context.pStoreDataStore.data.first()
        return prefs[KEY_USER_REPOS_ETAG]
    }

    suspend fun getCachedUserReposOnce(): List<GitHubRepoDto> {
        val prefs = context.pStoreDataStore.data.first()
        val raw = prefs[KEY_CACHED_USER_REPOS_JSON] ?: return emptyList()
        return try {
            repoDtosAdapter.fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCachedUserRepos(repos: List<GitHubRepoDto>, etag: String?) {
        context.pStoreDataStore.edit { prefs ->
            prefs[KEY_CACHED_USER_REPOS_JSON] = repoDtosAdapter.toJson(repos)
            if (!etag.isNullOrBlank()) {
                prefs[KEY_USER_REPOS_ETAG] = etag
            }
        }
    }

    suspend fun getReleaseETagOnce(owner: String, repo: String): String? {
        val prefs = context.pStoreDataStore.data.first()
        return prefs[releaseETagKey(owner, repo)]
    }

    suspend fun saveReleaseETag(owner: String, repo: String, etag: String?) {
        if (etag.isNullOrBlank()) return
        context.pStoreDataStore.edit { prefs ->
            prefs[releaseETagKey(owner, repo)] = etag
        }
    }

    // ---- Cached Releases & Installed Tags ----

    suspend fun getCachedReleaseOnce(owner: String, repo: String): ReleaseInfo? {
        val prefs = context.pStoreDataStore.data.first()
        val tag = prefs[releaseTagKey(owner, repo)]
        val url = prefs[releaseUrlKey(owner, repo)]
        if (tag == null || url == null) return null
        return ReleaseInfo(
            tagName = tag,
            version = prefs[releaseVersionKey(owner, repo)] ?: tag.removePrefix("v").removePrefix("V"),
            apkDownloadUrl = url,
            apkSize = prefs[releaseSizeKey(owner, repo)] ?: 0L,
            body = prefs[releaseBodyKey(owner, repo)] ?: "",
            publishedAt = prefs[releaseDateKey(owner, repo)] ?: "",
            rawPublishedAt = prefs[releaseRawDateKey(owner, repo)] ?: ""
        )
    }

    suspend fun saveCachedRelease(owner: String, repo: String, release: ReleaseInfo, etag: String? = null) {
        context.pStoreDataStore.edit { prefs ->
            prefs[releaseTagKey(owner, repo)] = release.tagName
            prefs[releaseVersionKey(owner, repo)] = release.version
            prefs[releaseUrlKey(owner, repo)] = release.apkDownloadUrl
            prefs[releaseSizeKey(owner, repo)] = release.apkSize
            prefs[releaseBodyKey(owner, repo)] = release.body
            prefs[releaseDateKey(owner, repo)] = release.publishedAt
            prefs[releaseRawDateKey(owner, repo)] = release.rawPublishedAt
            if (!etag.isNullOrBlank()) {
                prefs[releaseETagKey(owner, repo)] = etag
            }
        }
    }

    suspend fun getInstalledTagOnce(packageName: String): InstalledTagRecord? {
        if (packageName.isBlank()) return null
        val prefs = context.pStoreDataStore.data.first()
        val tag = prefs[installedTagKey(packageName)]?.takeIf { it.isNotBlank() } ?: return null
        val updateTime = prefs[installedUpdateTimeKey(packageName)] ?: 0L
        return InstalledTagRecord(tagName = tag, packageLastUpdateTime = updateTime)
    }

    suspend fun saveInstalledTag(packageName: String, tagName: String, packageLastUpdateTime: Long) {
        val trimmed = tagName.trim()
        if (packageName.isBlank() || trimmed.isEmpty()) return
        context.pStoreDataStore.edit { prefs ->
            prefs[installedTagKey(packageName)] = trimmed
            prefs[installedUpdateTimeKey(packageName)] = packageLastUpdateTime
        }
    }

    suspend fun clearInstalledTag(packageName: String) {
        if (packageName.isBlank()) return
        context.pStoreDataStore.edit { prefs ->
            prefs.remove(installedTagKey(packageName))
            prefs.remove(installedUpdateTimeKey(packageName))
        }
    }
}
