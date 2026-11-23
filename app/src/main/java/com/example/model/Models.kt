package com.example.model

import com.example.catalog.CatalogApp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class NetworkPreference {
    WIFI_ONLY,
    ANY_NETWORK
}

enum class AutoUpdateMode {
    WIFI_ONLY,
    ALWAYS,
    OFF
}

enum class CheckIntervalHours(val hours: Long) {
    H6(6L),
    H12(12L),
    H24(24L);

    companion object {
        fun fromHours(hours: Long): CheckIntervalHours =
            entries.firstOrNull { it.hours == hours } ?: H12
    }
}

enum class AppInstallStatus {
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    UP_TO_DATE
}

enum class StoreFilter {
    ALL,
    UPDATES,
    NOT_INSTALLED
}

data class ReleaseInfo(
    val tagName: String,
    val version: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val body: String,
    val publishedAt: String,
    val rawPublishedAt: String = "",
    val apkVersionName: String? = null,
    val apkVersionCode: Long? = null
) {
    val displayVersion: String
        get() = version.ifBlank { tagName.removePrefix("v").removePrefix("V") }
}

sealed interface ReleaseStatus {
    data object Loading : ReleaseStatus
    data class Success(val release: ReleaseInfo) : ReleaseStatus
    data class NoRelease(val message: String) : ReleaseStatus
    data class NoApkAsset(val message: String) : ReleaseStatus
    data class RepoNotFound(val message: String) : ReleaseStatus
    data class RateLimit(val message: String) : ReleaseStatus
    data class NoInternet(val message: String) : ReleaseStatus
    data class Error(val message: String) : ReleaseStatus
}

sealed interface AppActionState {
    data object Idle : AppActionState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val targetVersion: String
    ) : AppActionState
    data object Verifying : AppActionState
    data class Installing(val targetVersion: String) : AppActionState
    data class InstalledSuccess(val installedVersion: String) : AppActionState
    data class SignatureMismatch(val message: String) : AppActionState
    data class Error(val message: String) : AppActionState
}

sealed interface OlderReleasesState {
    data object Idle : OlderReleasesState
    data object Loading : OlderReleasesState
    data class Success(val releases: List<ReleaseInfo>) : OlderReleasesState
    data class Error(val message: String) : OlderReleasesState
}

data class RecentUpdateRecord(
    val packageName: String,
    val displayName: String,
    val version: String,
    val updatedAtMillis: Long
)

data class AppItemUiState(
    val catalogApp: CatalogApp,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val isInstalled: Boolean = false,
    val installStatus: AppInstallStatus = AppInstallStatus.NOT_INSTALLED,
    val latestRelease: ReleaseInfo? = null,
    val releaseStatus: ReleaseStatus = ReleaseStatus.Loading,
    val actionState: AppActionState = AppActionState.Idle,
    val autoUpdateMode: AutoUpdateMode = AutoUpdateMode.WIFI_ONLY,
    val olderReleasesState: OlderReleasesState = OlderReleasesState.Idle
) {
    val packageName: String get() = catalogApp.packageName
    val updateAvailable: Boolean get() = installStatus == AppInstallStatus.UPDATE_AVAILABLE
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoCheckEnabled: Boolean = true,
    val checkInterval: CheckIntervalHours = CheckIntervalHours.H12,
    val downloadOver: NetworkPreference = NetworkPreference.WIFI_ONLY,
    val notificationsEnabled: Boolean = true,
    val hasGitHubToken: Boolean = false,
    val lastCheckedAtMillis: Long = 0L,
    val cacheSizeFormatted: String = "0.0 MB"
)
