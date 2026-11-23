package com.example

import com.example.catalog.AppCatalog
import com.example.catalog.AppIconType
import com.example.data.api.model.GitHubReleaseDto
import com.example.util.Formatters
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
    fun testAppCatalogContainsAllSixAppsWithExactMetadata() {
        assertEquals(6, AppCatalog.apps.size)

        val launcherApps = AppCatalog.apps.filter { it.showInLauncher }
        assertEquals(5, launcherApps.size)

        val money = AppCatalog.findByPackageName("com.aistudio.moneymap.zqpjfl")
        assertEquals("P Money", money?.displayName)
        assertEquals("patricknabil611/Money-Map", money?.fullRepoName)
        assertEquals(AppIconType.COIN_DOLLAR, money?.icon)
        assertTrue(money?.showInLauncher == true)

        val task = AppCatalog.findByPackageName("com.aistudio.todolist.zkvmrq")
        assertEquals("P Task", task?.displayName)
        assertEquals("patricknabil611/P-Task", task?.fullRepoName)
        assertEquals(AppIconType.CHECKBOX, task?.icon)

        val habits = AppCatalog.findByPackageName("com.aistudio.phabits.qvxrzt")
        assertEquals("P Habits", habits?.displayName)
        assertEquals("patricknabil611/P-Habits", habits?.fullRepoName)
        assertEquals(AppIconType.FLAME, habits?.icon)

        val ink = AppCatalog.findByPackageName("com.aistudio.pink.spenzx")
        assertEquals("P Ink", ink?.displayName)
        assertEquals("patricknabil611/P-Ink", ink?.fullRepoName)
        assertEquals(AppIconType.PEN_NIB, ink?.icon)

        val ludo = AppCatalog.findByPackageName("com.aistudio.ludoform.qwrtxz")
        assertEquals("Ludo for M", ludo?.displayName)
        assertEquals("patricknabil611/Ludo-for-M", ludo?.fullRepoName)
        assertEquals(AppIconType.DIE_FIVE, ludo?.icon)
        assertTrue(ludo?.showInLauncher == true)

        val store = AppCatalog.findByPackageName("com.patricknabil.pstore")
        assertEquals("P Store", store?.displayName)
        assertEquals("patricknabil611/P-Store", store?.fullRepoName)
        assertEquals(AppIconType.P_STORE, store?.icon)
        assertFalse(store?.showInLauncher == true)
        assertTrue(store?.isSelf == true)
    }

    @Test
    fun testClipboardFormattingMatchesSpecification() {
        val habits = AppCatalog.findByPackageName("com.aistudio.phabits.qvxrzt")!!
        val expectedSingle = """
            Display name: P Habits
            Package name: com.aistudio.phabits.qvxrzt
            GitHub repo: patricknabil611/P-Habits
        """.trimIndent()
        assertEquals(expectedSingle, AppCatalog.formatAppInfo(habits))

        val allFormatted = AppCatalog.formatAllAppsInfo()
        val blocks = allFormatted.split("\n\n")
        assertEquals(6, blocks.size)
        assertTrue(allFormatted.contains("Display name: P Store\nPackage name: com.patricknabil.pstore\nGitHub repo: patricknabil611/P-Store"))
        assertTrue(allFormatted.contains("Display name: Ludo for M\nPackage name: com.aistudio.ludoform.qwrtxz\nGitHub repo: patricknabil611/Ludo-for-M"))
    }

    @Test
    fun testSemanticVersionComparison() {
        val cmp = VersionComparator.compareSemantic("1.10.0", "1.9.0")
        assertTrue(cmp != null && cmp > 0)

        assertEquals("1.6.0", VersionComparator.cleanVersion("v1.6.0"))
        assertEquals("1.6.0", VersionComparator.cleanVersion("V1.6.0"))

        assertTrue(
            VersionComparator.isUpdateAvailable(
                installedVersionName = "1.9.0",
                installedVersionCode = 9,
                latestVersionName = "1.10.0"
            )
        )
        assertFalse(
            VersionComparator.isUpdateAvailable(
                installedVersionName = "1.10.0",
                installedVersionCode = 10,
                latestVersionName = "1.10.0"
            )
        )
        assertFalse(
            VersionComparator.isUpdateAvailable(
                installedVersionName = "2.0.0",
                installedVersionCode = 20,
                latestVersionName = "1.9.0"
            )
        )
    }

    @Test
    fun testReleaseMarkdownBulletParser() {
        val raw = """
            ## What's Changed
            - Added **Midnight Dock** dark mode
            * Added `Clean Shelf` light mode
            1. Improved download speed
        """.trimIndent()

        val bullets = Formatters.parseReleaseBullets(raw)
        assertEquals(4, bullets.size)
        assertEquals("What's Changed", bullets[0])
        assertEquals("Added Midnight Dock dark mode", bullets[1])
        assertEquals("Added Clean Shelf light mode", bullets[2])
        assertEquals("Improved download speed", bullets[3])
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
        val apkAsset = dto?.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        assertNotNull(apkAsset)
        assertEquals("app-release.apk", apkAsset?.name)
        assertEquals(15000000L, apkAsset?.size)
    }
}
