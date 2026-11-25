package com.example.data.api

import com.example.data.api.model.GitHubReleaseDto
import com.example.data.api.model.GitHubRepoDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {

    @GET("users/{username}/repos")
    suspend fun getUserRepos(
        @Path("username") username: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Header("Authorization") authorization: String? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<List<GitHubRepoDto>>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<GitHubReleaseDto>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Header("Authorization") authorization: String? = null
    ): Response<List<GitHubReleaseDto>>
}
