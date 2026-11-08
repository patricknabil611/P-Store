package com.example.data.api

import com.example.data.api.model.GitHubReleaseDto
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus
import com.example.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class GitHubReleaseService(
    private val api: GitHubApiService = RetrofitClient.apiService
) {

    /**
     * Fetches the latest release for a given repository and extracts APK details.
     */
    suspend fun getLatestRelease(owner: String, repo: String): ReleaseStatus = withContext(Dispatchers.IO) {
        try {
            val response = api.getLatestRelease(owner, repo)
            when (response.code()) {
                200 -> {
                    val dto = response.body()
                    if (dto == null || dto.tagName.isBlank()) {
                        return@withContext ReleaseStatus.NoRelease("No release yet")
                    }

                    val apkAsset = dto.assets.firstOrNull { asset ->
                        asset.name.endsWith(".apk", ignoreCase = true)
                    }

                    if (apkAsset == null || apkAsset.browserDownloadUrl.isBlank()) {
                        return@withContext ReleaseStatus.NoRelease("No release yet")
                    }

                    val version = VersionComparator.cleanVersion(dto.tagName)
                    val formattedDate = formatPublishedDate(dto.publishedAt ?: "")

                    ReleaseStatus.Success(
                        ReleaseInfo(
                            tagName = dto.tagName,
                            version = version,
                            apkDownloadUrl = apkAsset.browserDownloadUrl,
                            apkSize = apkAsset.size,
                            body = dto.body ?: "",
                            publishedAt = formattedDate,
                            rawPublishedAt = dto.publishedAt ?: ""
                        )
                    )
                }
                404 -> ReleaseStatus.RepoNotFound("Repo not found or private")
                403 -> ReleaseStatus.RateLimit("GitHub limit reached, try again later")
                else -> ReleaseStatus.Error("GitHub error (${response.code()})")
            }
        } catch (e: IOException) {
            ReleaseStatus.Error("No internet connection")
        } catch (e: Exception) {
            ReleaseStatus.Error(e.message ?: "Failed to load release")
        }
    }

    /**
     * Fetches multiple recent releases for a repository.
     */
    suspend fun getReleasesHistory(owner: String, repo: String, limit: Int = 5): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getReleases(owner, repo, limit)
            if (response.isSuccessful) {
                val dtoList = response.body().orEmpty()
                val list = dtoList.mapNotNull { dto ->
                    val apk = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    if (apk != null) {
                        ReleaseInfo(
                            tagName = dto.tagName,
                            version = VersionComparator.cleanVersion(dto.tagName),
                            apkDownloadUrl = apk.browserDownloadUrl,
                            apkSize = apk.size,
                            body = dto.body ?: "",
                            publishedAt = formatPublishedDate(dto.publishedAt ?: ""),
                            rawPublishedAt = dto.publishedAt ?: ""
                        )
                    } else null
                }
                Result.success(list)
            } else {
                Result.failure(IOException("HTTP error code ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatPublishedDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        return try {
            val instant = Instant.parse(rawDate)
            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            instant.atZone(ZoneId.systemDefault()).format(formatter)
        } catch (e: Exception) {
            rawDate.take(10)
        }
    }
}
