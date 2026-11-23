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

    private fun buildAuthHeader(token: String?): String? {
        val trimmed = token?.trim()
        return if (trimmed.isNullOrEmpty()) null else "Bearer $trimmed"
    }

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        token: String? = null
    ): ReleaseStatus = withContext(Dispatchers.IO) {
        val authHeader = buildAuthHeader(token)
        try {
            val response = api.getLatestRelease(owner, repo, authHeader)
            when (response.code()) {
                200 -> {
                    val dto = response.body()
                    if (dto == null || dto.tagName.isBlank()) {
                        return@withContext ReleaseStatus.NoRelease("No releases published yet")
                    }
                    mapReleaseDtoToStatus(dto)
                }
                404 -> {
                    // /releases/latest returns 404 when only prereleases exist or no releases exist.
                    try {
                        val allResponse = api.getReleases(owner, repo, perPage = 10, authorization = authHeader)
                        when (allResponse.code()) {
                            200 -> {
                                val releases = allResponse.body().orEmpty()
                                if (releases.isEmpty()) {
                                    ReleaseStatus.NoRelease("No releases published yet")
                                } else {
                                    val withApk = releases.firstOrNull { r ->
                                        r.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                                    }
                                    if (withApk != null) {
                                        mapReleaseDtoToStatus(withApk)
                                    } else {
                                        ReleaseStatus.NoApkAsset("Release has no APK file attached")
                                    }
                                }
                            }
                            404 -> ReleaseStatus.RepoNotFound("Repository not found or private")
                            403, 429 -> ReleaseStatus.RateLimit("GitHub rate limit reached")
                            else -> ReleaseStatus.NoRelease("No releases published yet")
                        }
                    } catch (_: IOException) {
                        ReleaseStatus.NoInternet("No internet connection")
                    } catch (_: Exception) {
                        ReleaseStatus.NoRelease("No releases published yet")
                    }
                }
                403, 429 -> ReleaseStatus.RateLimit("GitHub rate limit reached")
                else -> ReleaseStatus.Error("GitHub error (${response.code()})")
            }
        } catch (_: IOException) {
            ReleaseStatus.NoInternet("No internet connection")
        } catch (e: Exception) {
            ReleaseStatus.Error(e.message ?: "Failed to load release")
        }
    }

    suspend fun getAllReleases(
        owner: String,
        repo: String,
        token: String? = null,
        limit: Int = 30
    ): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        val authHeader = buildAuthHeader(token)
        try {
            val response = api.getReleases(owner, repo, perPage = limit, authorization = authHeader)
            when {
                response.isSuccessful -> {
                    val list = response.body().orEmpty().mapNotNull { dto ->
                        val apk = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        if (apk != null && apk.browserDownloadUrl.isNotBlank()) {
                            ReleaseInfo(
                                tagName = dto.tagName,
                                version = VersionComparator.cleanVersion(dto.tagName),
                                apkDownloadUrl = apk.browserDownloadUrl,
                                apkSize = apk.size,
                                body = dto.body ?: "",
                                publishedAt = formatPublishedDate(dto.publishedAt ?: ""),
                                rawPublishedAt = dto.publishedAt ?: ""
                            )
                        } else {
                            null
                        }
                    }
                    Result.success(list)
                }
                response.code() == 403 || response.code() == 429 -> {
                    Result.failure(IOException("GitHub rate limit reached"))
                }
                response.code() == 404 -> {
                    Result.failure(IOException("Repository not found or private"))
                }
                else -> {
                    Result.failure(IOException("GitHub error (${response.code()})"))
                }
            }
        } catch (_: IOException) {
            Result.failure(IOException("No internet connection"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapReleaseDtoToStatus(dto: GitHubReleaseDto): ReleaseStatus {
        val apkAsset = dto.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true)
        }
        if (apkAsset == null || apkAsset.browserDownloadUrl.isBlank()) {
            return ReleaseStatus.NoApkAsset("Release has no APK file attached")
        }
        val version = VersionComparator.cleanVersion(dto.tagName)
        return ReleaseStatus.Success(
            ReleaseInfo(
                tagName = dto.tagName,
                version = version,
                apkDownloadUrl = apkAsset.browserDownloadUrl,
                apkSize = apkAsset.size,
                body = dto.body ?: "",
                publishedAt = formatPublishedDate(dto.publishedAt ?: ""),
                rawPublishedAt = dto.publishedAt ?: ""
            )
        )
    }

    private fun formatPublishedDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        return try {
            val instant = Instant.parse(rawDate)
            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            instant.atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            rawDate.take(10)
        }
    }
}
