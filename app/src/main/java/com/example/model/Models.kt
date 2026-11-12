package com.example.model

import com.example.catalog.CatalogApp

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
)

sealed interface ReleaseStatus {
    object Loading : ReleaseStatus
    data class Success(val release: ReleaseInfo) : ReleaseStatus
    data class NoRelease(val message: String = "No release yet") : ReleaseStatus
    data class RepoNotFound(val message: String = "Repo not found or private") : ReleaseStatus
    data class RateLimit(val message: String = "GitHub limit reached, try again later") : ReleaseStatus
    data class Error(val message: String) : ReleaseStatus
}

sealed interface AppActionState {
    object Idle : AppActionState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppActionState
    object Verifying : AppActionState
    object Installing : AppActionState
    object InstalledSuccess : AppActionState
    data class Error(val message: String) : AppActionState
}

data class AppItemUiState(
    val catalogApp: CatalogApp,
    val packageName: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val isInstalled: Boolean = false,
    val latestRelease: ReleaseInfo? = null,
    val releaseStatus: ReleaseStatus = ReleaseStatus.Loading,
    val updateAvailable: Boolean = false,
    val actionState: AppActionState = AppActionState.Idle,
    val isExpandedChangelog: Boolean = false
) {
    val id: String get() = "${catalogApp.owner}/${catalogApp.repo}"
}

data class DetectionProgress(
    val currentAppName: String,
    val currentIndex: Int,
    val totalCount: Int
)
