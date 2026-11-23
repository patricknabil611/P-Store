package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

        private const val MAX_RECENT_UPDATES = 20

        private val moshi: Moshi by lazy {
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        }
        private val recentUpdatesType = Types.newParameterizedType(List::class.java, RecentUpdateRecord::class.java)
        private val recentUpdatesAdapter by lazy {
            moshi.adapter<List<RecentUpdateRecord>>(recentUpdatesType)
        }

        private fun sanitize(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9_]"), "_")

        fun autoUpdateKey(packageName: String) =
            stringPreferencesKey("auto_update_${sanitize(packageName)}")

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

        fun releaseApkVersionKey(owner: String, repo: String) =
            stringPreferencesKey("rel_apkver_${sanitize(owner)}_${sanitize(repo)}")

        fun releaseApkCodeKey(owner: String, repo: String) =
            longPreferencesKey("rel_apkcode_${sanitize(owner)}_${sanitize(repo)}")
    }

    val themeMode: Flow<ThemeMode> = context.pStoreDataStore.data.map { prefs ->
        val raw = prefs[KEY_THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val autoCheckEnabled: Flow<Boolean> = context.pStoreDataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_ENABLED] ?: true
    }

    val checkInterval: Flow<CheckIntervalHours> = context.pStoreDataStore.data.map { prefs ->
        CheckIntervalHours.fromHours(prefs[KEY_CHECK_INTERVAL_HOURS] ?: 12L)
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

    suspend fun getAutoUpdateModeOnce(packageName: String): AutoUpdateMode {
        val prefs = context.pStoreDataStore.data.first()
        val raw = prefs[autoUpdateKey(packageName)]
        return AutoUpdateMode.entries.firstOrNull { it.name == raw } ?: AutoUpdateMode.WIFI_ONLY
    }

    suspend fun setAutoUpdateMode(packageName: String, mode: AutoUpdateMode) {
        context.pStoreDataStore.edit { it[autoUpdateKey(packageName)] = mode.name }
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
            rawPublishedAt = prefs[releaseRawDateKey(owner, repo)] ?: "",
            apkVersionName = prefs[releaseApkVersionKey(owner, repo)],
            apkVersionCode = prefs[releaseApkCodeKey(owner, repo)]
        )
    }

    suspend fun saveCachedRelease(owner: String, repo: String, release: ReleaseInfo) {
        context.pStoreDataStore.edit { prefs ->
            prefs[releaseTagKey(owner, repo)] = release.tagName
            prefs[releaseVersionKey(owner, repo)] = release.version
            prefs[releaseUrlKey(owner, repo)] = release.apkDownloadUrl
            prefs[releaseSizeKey(owner, repo)] = release.apkSize
            prefs[releaseBodyKey(owner, repo)] = release.body
            prefs[releaseDateKey(owner, repo)] = release.publishedAt
            prefs[releaseRawDateKey(owner, repo)] = release.rawPublishedAt
            if (release.apkVersionName != null) {
                prefs[releaseApkVersionKey(owner, repo)] = release.apkVersionName
            } else {
                prefs.remove(releaseApkVersionKey(owner, repo))
            }
            if (release.apkVersionCode != null) {
                prefs[releaseApkCodeKey(owner, repo)] = release.apkVersionCode
            } else {
                prefs.remove(releaseApkCodeKey(owner, repo))
            }
        }
    }

    suspend fun saveApkVersion(owner: String, repo: String, apkVersionName: String?, apkVersionCode: Long?) {
        context.pStoreDataStore.edit { prefs ->
            if (apkVersionName != null) {
                prefs[releaseApkVersionKey(owner, repo)] = apkVersionName
            }
            if (apkVersionCode != null) {
                prefs[releaseApkCodeKey(owner, repo)] = apkVersionCode
            }
        }
    }
}
