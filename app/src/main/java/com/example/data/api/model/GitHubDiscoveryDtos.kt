package com.example.data.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class GitHubRepoOwnerDto(
    @Json(name = "login") val login: String = ""
)

@JsonClass(generateAdapter = false)
data class GitHubRepoDto(
    @Json(name = "name") val name: String = "",
    @Json(name = "full_name") val fullName: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "fork") val fork: Boolean = false,
    @Json(name = "archived") val archived: Boolean = false,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "owner") val owner: GitHubRepoOwnerDto? = null
)

/**
 * Optional metadata file `p-store.json` placed at the root of a repository's default branch.
 * All fields are optional; missing fields fall back automatically.
 */
@JsonClass(generateAdapter = false)
data class PStoreManifestDto(
    @Json(name = "name") val name: String? = null,
    @Json(name = "packageName") val packageName: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "icon") val icon: String? = null,
    @Json(name = "accentColor") val accentColor: String? = null
)
