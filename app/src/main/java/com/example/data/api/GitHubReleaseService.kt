package com.example.data.api

import com.example.data.api.model.GitHubReleaseDto
import com.example.data.api.model.GitHubRepoDto
import com.example.data.api.model.PStoreManifestDto
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus
import com.example.util.VersionComparator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface ReposFetchResult {
    data class Success(val repos: List<GitHubRepoDto>, val etag: String?) : ReposFetchResult
    data object NotModified : ReposFetchResult
    data class RateLimited(val resetEpochSeconds: Long?) : ReposFetchResult
    data object NoInternet : ReposFetchResult
    data class Error(val message: String) : ReposFetchResult
}

sealed interface LatestReleaseFetchResult {
    data class Success(val release: ReleaseInfo, val etag: String?) : LatestReleaseFetchResult
    data object NotModified : LatestReleaseFetchResult
    data class NoRelease(val message: String) : LatestReleaseFetchResult
    data class NoApkAsset(val message: String) : LatestReleaseFetchResult
    data class RepoNotFound(val message: String) : LatestReleaseFetchResult
    data class RateLimited(val message: String, val resetEpochSeconds: Long?) : LatestReleaseFetchResult
    data class NoInternet(val message: String) : LatestReleaseFetchResult
    data class Error(val message: String) : LatestReleaseFetchResult
}

class GitHubReleaseService(
    private val api: GitHubApiService = RetrofitClient.apiService
) {

    private val rawClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val manifestAdapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(PStoreManifestDto::class.java)
    }

    private fun buildAuthHeader(token: String?): String? {
        val trimmed = token?.trim()
        return if (trimmed.isNullOrEmpty()) null else "Bearer $trimmed"
    }

    private fun parseRateLimitReset(response: Response<*>): Long? =
        response.headers()["X-RateLimit-Reset"]?.trim()?.toLongOrNull()

    private fun isRateLimitResponse(response: Response<*>): Boolean {
        if (response.code() == 429) return true
        if (response.code() == 403) {
            val remaining = response.headers()["X-RateLimit-Remaining"]?.trim()
            return remaining == "0" || response.headers()["X-RateLimit-Reset"] != null
        }
        return false
    }

    /**
     * Fetches all public repos for [username] across all pages (per_page=100).
     * Uses [cachedPage1ETag] via If-None-Match on page 1 to avoid consuming GitHub API quota when unchanged.
     */
    suspend fun fetchUserRepos(
        username: String,
        token: String? = null,
        cachedPage1ETag: String? = null
    ): ReposFetchResult = withContext(Dispatchers.IO) {
        val authHeader = buildAuthHeader(token)
        val collected = mutableListOf<GitHubRepoDto>()
        var page1ETag: String? = null
        var page = 1

        try {
            while (true) {
                val ifNoneMatch = if (page == 1) cachedPage1ETag else null
                val response = api.getUserRepos(
                    username = username,
                    perPage = 100,
                    page = page,
                    authorization = authHeader,
                    ifNoneMatch = ifNoneMatch
                )

                when {
                    response.code() == 304 -> {
                        return@withContext ReposFetchResult.NotModified
                    }
                    isRateLimitResponse(response) -> {
                        return@withContext ReposFetchResult.RateLimited(parseRateLimitReset(response))
                    }
                    response.code() == 200 -> {
                        if (page == 1) {
                            page1ETag = response.headers()["ETag"]
                        }
                        val batch = response.body().orEmpty()
                        collected.addAll(batch)
                        val linkHeader = response.headers()["Link"].orEmpty()
                        val hasNextLink = linkHeader.contains("rel=\"next\"", ignoreCase = true)
                        if (batch.size < 100 && !hasNextLink) {
                            break
                        }
                        if (batch.isEmpty() || page >= 20) {
                            break
                        }
                        page++
                    }
                    else -> {
                        return@withContext ReposFetchResult.Error("GitHub error (${response.code()})")
                    }
                }
            }
            ReposFetchResult.Success(repos = collected, etag = page1ETag)
        } catch (_: IOException) {
            ReposFetchResult.NoInternet
        } catch (e: Exception) {
            ReposFetchResult.Error(e.message ?: "Failed to list repositories")
        }
    }

    /**
     * Fetches the latest release for a repo with optional ETag/If-None-Match validation.
     * Used during discovery and refresh to minimize GitHub API quota usage.
     */
    suspend fun fetchLatestReleaseWithETag(
        owner: String,
        repo: String,
        token: String? = null,
        cachedETag: String? = null,
        allowPrereleaseFallback: Boolean = false
    ): LatestReleaseFetchResult = withContext(Dispatchers.IO) {
        val authHeader = buildAuthHeader(token)
        try {
            val response = api.getLatestRelease(
                owner = owner,
                repo = repo,
                authorization = authHeader,
                ifNoneMatch = cachedETag
            )
            when {
                response.code() == 304 -> LatestReleaseFetchResult.NotModified
                isRateLimitResponse(response) -> LatestReleaseFetchResult.RateLimited(
                    message = "GitHub rate limit reached",
                    resetEpochSeconds = parseRateLimitReset(response)
                )
                response.code() == 200 -> {
                    val dto = response.body()
                    if (dto == null || dto.tagName.isBlank()) {
                        return@withContext LatestReleaseFetchResult.NoRelease("No releases published yet")
                    }
                    val etag = response.headers()["ETag"]
                    when (val mapped = mapReleaseDtoToStatus(dto)) {
                        is ReleaseStatus.Success -> LatestReleaseFetchResult.Success(mapped.release, etag)
                        is ReleaseStatus.NoApkAsset -> LatestReleaseFetchResult.NoApkAsset(mapped.message)
                        else -> LatestReleaseFetchResult.NoRelease("No releases published yet")
                    }
                }
                response.code() == 404 -> {
                    if (!allowPrereleaseFallback) {
                        return@withContext LatestReleaseFetchResult.NoRelease("No releases published yet")
                    }
                    try {
                        val allResponse = api.getReleases(owner, repo, perPage = 10, authorization = authHeader)
                        when {
                            isRateLimitResponse(allResponse) -> LatestReleaseFetchResult.RateLimited(
                                message = "GitHub rate limit reached",
                                resetEpochSeconds = parseRateLimitReset(allResponse)
                            )
                            allResponse.code() == 200 -> {
                                val releases = allResponse.body().orEmpty()
                                val withApk = releases.firstOrNull { r ->
                                    r.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                                }
                                if (withApk != null) {
                                    when (val mapped = mapReleaseDtoToStatus(withApk)) {
                                        is ReleaseStatus.Success -> LatestReleaseFetchResult.Success(mapped.release, null)
                                        is ReleaseStatus.NoApkAsset -> LatestReleaseFetchResult.NoApkAsset(mapped.message)
                                        else -> LatestReleaseFetchResult.NoRelease("No releases published yet")
                                    }
                                } else if (releases.isEmpty()) {
                                    LatestReleaseFetchResult.NoRelease("No releases published yet")
                                } else {
                                    LatestReleaseFetchResult.NoApkAsset("Release has no APK file attached")
                                }
                            }
                            allResponse.code() == 404 -> LatestReleaseFetchResult.RepoNotFound("Repository not found or private")
                            else -> LatestReleaseFetchResult.NoRelease("No releases published yet")
                        }
                    } catch (_: IOException) {
                        LatestReleaseFetchResult.NoInternet("No internet connection")
                    } catch (_: Exception) {
                        LatestReleaseFetchResult.NoRelease("No releases published yet")
                    }
                }
                response.code() == 403 -> LatestReleaseFetchResult.RateLimited(
                    message = "GitHub rate limit reached",
                    resetEpochSeconds = parseRateLimitReset(response)
                )
                else -> LatestReleaseFetchResult.Error("GitHub error (${response.code()})")
            }
        } catch (_: IOException) {
            LatestReleaseFetchResult.NoInternet("No internet connection")
        } catch (e: Exception) {
            LatestReleaseFetchResult.Error(e.message ?: "Failed to load release")
        }
    }

    /**
     * Reads optional `p-store.json` from the repo's default branch on raw.githubusercontent.com.
     * Silently returns null on 404 or any error.
     */
    suspend fun fetchPStoreManifest(
        owner: String,
        repo: String,
        defaultBranch: String,
        token: String? = null
    ): PStoreManifestDto? = withContext(Dispatchers.IO) {
        val branch = defaultBranch.ifBlank { "main" }
        val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/p-store.json"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "P-Store-Android-App")
        val auth = buildAuthHeader(token)
        if (auth != null) {
            reqBuilder.header("Authorization", auth)
        }
        try {
            rawClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()?.trim()
                if (body.isNullOrEmpty()) return@withContext null
                manifestAdapter.fromJson(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        token: String? = null
    ): ReleaseStatus {
        return when (
            val result = fetchLatestReleaseWithETag(
                owner = owner,
                repo = repo,
                token = token,
                cachedETag = null,
                allowPrereleaseFallback = true
            )
        ) {
            is LatestReleaseFetchResult.Success -> ReleaseStatus.Success(result.release)
            is LatestReleaseFetchResult.NoRelease -> ReleaseStatus.NoRelease(result.message)
            is LatestReleaseFetchResult.NoApkAsset -> ReleaseStatus.NoApkAsset(result.message)
            is LatestReleaseFetchResult.RepoNotFound -> ReleaseStatus.RepoNotFound(result.message)
            is LatestReleaseFetchResult.RateLimited -> ReleaseStatus.RateLimit(result.message, result.resetEpochSeconds)
            is LatestReleaseFetchResult.NoInternet -> ReleaseStatus.NoInternet(result.message)
            is LatestReleaseFetchResult.Error -> ReleaseStatus.Error(result.message)
            LatestReleaseFetchResult.NotModified -> ReleaseStatus.Error("Unexpected 304 without cached release")
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
