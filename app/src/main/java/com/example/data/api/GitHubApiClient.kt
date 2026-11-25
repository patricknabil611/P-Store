package com.example.data.api

import com.example.data.api.model.PStoreManifestDto
import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus

/**
 * Service client for GitHub API (repo discovery, Releases, and optional p-store.json), backed by Retrofit/OkHttp.
 */
class GitHubApiClient(
    private val service: GitHubReleaseService = GitHubReleaseService()
) {

    suspend fun fetchUserRepos(
        username: String,
        token: String? = null,
        cachedPage1ETag: String? = null
    ): ReposFetchResult = service.fetchUserRepos(username, token, cachedPage1ETag)

    suspend fun fetchLatestReleaseWithETag(
        owner: String,
        repo: String,
        token: String? = null,
        cachedETag: String? = null,
        allowPrereleaseFallback: Boolean = false
    ): LatestReleaseFetchResult = service.fetchLatestReleaseWithETag(
        owner = owner,
        repo = repo,
        token = token,
        cachedETag = cachedETag,
        allowPrereleaseFallback = allowPrereleaseFallback
    )

    suspend fun fetchPStoreManifest(
        owner: String,
        repo: String,
        defaultBranch: String,
        token: String? = null
    ): PStoreManifestDto? = service.fetchPStoreManifest(owner, repo, defaultBranch, token)

    suspend fun getLatestRelease(owner: String, repo: String, token: String? = null): ReleaseStatus {
        return service.getLatestRelease(owner, repo, token)
    }

    suspend fun getAllReleases(
        owner: String,
        repo: String,
        token: String? = null,
        limit: Int = 30
    ): Result<List<ReleaseInfo>> {
        return service.getAllReleases(owner, repo, token, limit)
    }
}
