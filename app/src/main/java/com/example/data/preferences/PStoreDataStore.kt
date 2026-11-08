package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ReleaseInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.pStoreDataStore: DataStore<Preferences> by preferencesDataStore(name = "p_store_preferences")

class PStoreDataStore(private val context: Context) {

    companion object {
        val KEY_BACKGROUND_CHECKS = booleanPreferencesKey("background_checks_enabled")
        val KEY_LAST_CHECK_TIME = longPreferencesKey("last_check_time")

        fun packageKey(owner: String, repo: String) =
            stringPreferencesKey("pkg_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseTagKey(owner: String, repo: String) =
            stringPreferencesKey("rel_tag_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseVersionKey(owner: String, repo: String) =
            stringPreferencesKey("rel_ver_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseUrlKey(owner: String, repo: String) =
            stringPreferencesKey("rel_url_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseSizeKey(owner: String, repo: String) =
            longPreferencesKey("rel_size_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseBodyKey(owner: String, repo: String) =
            stringPreferencesKey("rel_body_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseDateKey(owner: String, repo: String) =
            stringPreferencesKey("rel_date_${owner.lowercase()}_${repo.lowercase()}")

        fun releaseRawDateKey(owner: String, repo: String) =
            stringPreferencesKey("rel_rawdate_${owner.lowercase()}_${repo.lowercase()}")
    }

    val backgroundChecksEnabled: Flow<Boolean> = context.pStoreDataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_CHECKS] ?: true
    }

    suspend fun setBackgroundChecksEnabled(enabled: Boolean) {
        context.pStoreDataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_CHECKS] = enabled
        }
    }

    suspend fun setLastCheckTime(timeMs: Long) {
        context.pStoreDataStore.edit { prefs ->
            prefs[KEY_LAST_CHECK_TIME] = timeMs
        }
    }

    fun getSavedPackageName(owner: String, repo: String): Flow<String?> =
        context.pStoreDataStore.data.map { prefs ->
            prefs[packageKey(owner, repo)]
        }

    suspend fun getSavedPackageNameOnce(owner: String, repo: String): String? {
        val prefs = context.pStoreDataStore.data.first()
        return prefs[packageKey(owner, repo)]
    }

    suspend fun savePackageName(owner: String, repo: String, packageName: String) {
        context.pStoreDataStore.edit { prefs ->
            prefs[packageKey(owner, repo)] = packageName
        }
    }

    suspend fun resetPackageName(owner: String, repo: String) {
        context.pStoreDataStore.edit { prefs ->
            prefs.remove(packageKey(owner, repo))
        }
    }

    fun getCachedRelease(owner: String, repo: String): Flow<ReleaseInfo?> =
        context.pStoreDataStore.data.map { prefs ->
            val tag = prefs[releaseTagKey(owner, repo)]
            val url = prefs[releaseUrlKey(owner, repo)]
            if (tag != null && url != null) {
                ReleaseInfo(
                    tagName = tag,
                    version = prefs[releaseVersionKey(owner, repo)] ?: tag.removePrefix("v"),
                    apkDownloadUrl = url,
                    apkSize = prefs[releaseSizeKey(owner, repo)] ?: 0L,
                    body = prefs[releaseBodyKey(owner, repo)] ?: "",
                    publishedAt = prefs[releaseDateKey(owner, repo)] ?: "",
                    rawPublishedAt = prefs[releaseRawDateKey(owner, repo)] ?: ""
                )
            } else {
                null
            }
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
        }
    }

    fun getAllPackageNames(): Flow<Map<String, String>> =
        context.pStoreDataStore.data.map { prefs ->
            val result = mutableMapOf<String, String>()
            prefs.asMap().forEach { (key, value) ->
                if (key.name.startsWith("pkg_") && value is String) {
                    val repoKey = key.name.removePrefix("pkg_")
                    result[repoKey] = value
                }
            }
            result
        }
}
