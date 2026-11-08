package com.example.data.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "assets") val assets: List<GitHubAssetDto> = emptyList(),
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = false)
data class GitHubAssetDto(
    @Json(name = "name") val name: String = "",
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    @Json(name = "content_type") val contentType: String? = null
)
