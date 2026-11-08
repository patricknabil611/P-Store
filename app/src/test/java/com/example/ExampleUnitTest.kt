package com.example

import com.example.catalog.AppCatalog
import com.example.data.api.model.GitHubAssetDto
import com.example.data.api.model.GitHubReleaseDto
import com.example.util.VersionComparator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testAppCatalogContainsRequiredApps() {
        assertEquals(4, AppCatalog.apps.size)

        val money = AppCatalog.apps.find { it.name == "P Money" }
        assertEquals("patricknabil611", money?.owner)
        assertEquals("Money-Map", money?.repo)
        assertFalse(money?.isSelf ?: true)

        val task = AppCatalog.apps.find { it.name == "P Task" }
        assertEquals("patricknabil611", task?.owner)
        assertEquals("P-Task", task?.repo)

        val habits = AppCatalog.apps.find { it.name == "P Habits" }
        assertEquals("patricknabil611", habits?.owner)
        assertEquals("P-Habits", habits?.repo)

        val store = AppCatalog.apps.find { it.name == "P Store" }
        assertEquals("patricknabil611", store?.owner)
        assertEquals("P-Store", store?.repo)
        assertTrue(store?.isSelf ?: false)
    }

    @Test
    fun testSemanticVersionComparison() {
        // Explicit user requirement: 1.10.0 > 1.9.0
        val cmp = VersionComparator.compareSemantic("1.10.0", "1.9.0")
        assertTrue(cmp != null && cmp > 0)

        // Leading 'v' stripping
        assertEquals("1.6.0", VersionComparator.cleanVersion("v1.6.0"))
        assertEquals("1.6.0", VersionComparator.cleanVersion("V1.6.0"))

        // Update available checks
        assertTrue(
            VersionComparator.isUpdateAvailable(
                installedVersionName = "1.5.0",
                installedVersionCode = 10,
                latestVersionName = "1.6.0"
            )
        )

        assertFalse(
            VersionComparator.isUpdateAvailable(
                installedVersionName = "1.6.0",
                installedVersionCode = 20,
                latestVersionName = "1.6.0"
            )
        )

        assertFalse(
            VersionComparator.isUpdateAvailable(
                installedVersionName = "2.0.0",
                installedVersionCode = 30,
                latestVersionName = "1.9.0"
            )
        )
    }

    @Test
    fun testVersionCodeFallbackWhenNameUnparseable() {
        val hasUpdate = VersionComparator.isUpdateAvailable(
            installedVersionName = "custom_beta",
            installedVersionCode = 100L,
            latestVersionName = "custom_alpha",
            apkVersionCode = 200L
        )
        // Since names are non-numeric strings that might not yield a clear version, check fallback
        assertTrue(hasUpdate)
    }

    @Test
    fun testGitHubReleaseDtoDeserialization() {
        val json = """
            {
              "tag_name": "v1.6.0",
              "name": "Release 1.6.0",
              "body": "Fixed bugs and added features",
              "published_at": "2026-11-03T12:00:00Z",
              "assets": [
                {
                  "name": "release.zip",
                  "size": 5000,
                  "browser_download_url": "https://github.com/test/download.zip"
                },
                {
                  "name": "app-release.apk",
                  "size": 15000000,
                  "browser_download_url": "https://github.com/test/app.apk"
                }
              ]
            }
        """.trimIndent()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(GitHubReleaseDto::class.java)
        val dto = adapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("v1.6.0", dto?.tagName)
        assertEquals("1.6.0", VersionComparator.cleanVersion(dto?.tagName))
        assertEquals(2, dto?.assets?.size)

        val apkAsset = dto?.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        assertNotNull(apkAsset)
        assertEquals("app-release.apk", apkAsset?.name)
        assertEquals(15000000L, apkAsset?.size)
        assertEquals("https://github.com/test/app.apk", apkAsset?.browserDownloadUrl)
    }
}
