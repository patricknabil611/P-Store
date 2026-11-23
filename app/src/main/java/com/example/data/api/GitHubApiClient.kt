package com.example.data.api

import com.example.model.ReleaseInfo
import com.example.model.ReleaseStatus

/**
 * Service client for GitHub Releases API, backed by Retrofit.
 */
class GitHubApiClient(
    private val service: GitHubReleaseService = GitHubReleaseService()
) {

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
