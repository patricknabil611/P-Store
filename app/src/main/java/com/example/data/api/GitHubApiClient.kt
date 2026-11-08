package com.example.data.api

import com.example.model.ReleaseStatus

/**
 * Service client for GitHub releases API, backed by Retrofit.
 */
class GitHubApiClient(
    private val service: GitHubReleaseService = GitHubReleaseService()
) {

    suspend fun getLatestRelease(owner: String, repo: String): ReleaseStatus {
        return service.getLatestRelease(owner, repo)
    }

    suspend fun getReleasesHistory(owner: String, repo: String, limit: Int = 5) =
        service.getReleasesHistory(owner, repo, limit)
}
