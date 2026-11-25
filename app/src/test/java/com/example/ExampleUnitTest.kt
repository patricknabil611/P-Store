package com.example

import com.example.catalog.AppCatalog
import com.example.catalog.AppIconType
import com.example.catalog.CatalogApp
import com.example.data.api.model.GitHubReleaseDto
import com.example.data.api.model.GitHubRepoDto
import com.example.data.api.model.PStoreManifestDto
import com.example.model.AppItemUiState
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
    fun testPreSeededPackageNamesAndSelfApp() {
        assertEquals(5, AppCatalog.preSeededPackageNames.size)
        assertEquals("com.aistudio.moneymap.zqpjfl", AppCatalog.preSeededPackageForRepo("Money-Map"))
        assertEquals("com.aistudio.todolist.zkvmrq", AppCatalog.preSeededPackageForRepo("P-Task"))
        assertEquals("com.aistudio.phabits.qvxrzt", AppCatalog.preSeededPackageForRepo("P-Habits"))
        assertEquals("com.aistudio.pink.spenzx", AppCatalog.preSeededPackageForRepo("P-Ink"))
        assertEquals("com.aistudio.ludoform.qwrtxz", AppCatalog.preSeededPackageForRepo("Ludo-for-M"))

        val store = AppCatalog.selfApp
        assertEquals("P Store", store.displayName)
        assertEquals("com.patricknabil.pstore", store.packageName)
        assertEquals("patricknabil611/P-Store", store.fullRepoName)
        assertEquals(AppIconType.P_STORE, store.icon)
        assertFalse(store.showInLauncher)
        assertTrue(store.isSelf)
    }

    @Test
    fun testDiscoveryFallbacksForDisplayNameInitialsAndColors() {
        assertEquals("Money Map", AppCatalog.formatRepoDisplayName("Money-Map"))
        assertEquals("My Cool App", AppCatalog.formatRepoDisplayName("My_Cool-App"))
        assertEquals("MM", AppCatalog.extractInitials("Money Map"))
        assertEquals("PI", AppCatalog.extractInitials("P Ink"))
        assertEquals("LU", AppCatalog.extractInitials("Ludo"))

        val colorA = AppCatalog.colorPresetForName("Money Map")
        val colorB = AppCatalog.colorPresetForName("money map")
        assertEquals(colorA, colorB)

        val custom = AppCatalog.parseHexAccentColor("#2FBF85")
        assertNotNull(custom)
        assertEquals(0xFF2FBF85L, custom?.darkColor)
        assertEquals(null, AppCatalog.parseHexAccentColor("not-a-hex"))
    }

    @Test
    fun testNewBadgeSevenDayWindow() {
        val now = 1_700_000_000_000L
        val twoDaysAgo = now - (2L * 24L * 60L * 60L * 1000L)
        val eightDaysAgo = now - (8L * 24L * 60L * 60L * 1000L)

        val base = CatalogApp(
            displayName = "Demo App",
            packageName = "",
            githubOwner = "patricknabil611",
            githubRepo = "Demo-App",
            brandColorDark = 0xFF4C8DFF,
            brandColorLight = 0xFF2F6BFF,
            darkTileTint = 0xFF142240
        )

        assertFalse(AppItemUiState(base.copy(firstDiscoveredAtMillis = 0L)).isNewlyDiscovered(now))
        assertTrue(AppItemUiState(base.copy(firstDiscoveredAtMillis = twoDaysAgo)).isNewlyDiscovered(now))
        assertFalse(AppItemUiState(base.copy(firstDiscoveredAtMillis = eightDaysAgo)).isNewlyDiscovered(now))
        assertFalse(AppItemUiState(AppCatalog.selfApp.copy(firstDiscoveredAtMillis = twoDaysAgo)).isNewlyDiscovered(now))
    }

    @Test
    fun testClipboardFormattingMatchesSpecification() {
        val habits = CatalogApp(
            displayName = "P Habits",
            packageName = "com.aistudio.phabits.qvxrzt",
            githubOwner = "patricknabil611",
            githubRepo = "P-Habits",
            brandColorDark = 0xFFFF9A3D,
            brandColorLight = 0xFFE8741C,
            darkTileTint = 0xFF33220F
        )
        val expectedSingle = """
            Display name: P Habits
            Package name: com.aistudio.phabits.qvxrzt
            GitHub repo: patricknabil611/P-Habits
        """.trimIndent()
        assertEquals(expectedSingle, AppCatalog.formatAppInfo(habits))

        val allFormatted = AppCatalog.formatAllAppsInfo(listOf(habits, AppCatalog.selfApp))
        val blocks = allFormatted.split("\n\n")
        assertEquals(2, blocks.size)
        assertTrue(allFormatted.contains("Display name: P Store\nPackage name: com.patricknabil.pstore\nGitHub repo: patricknabil611/P-Store"))
    }

    @Test
    fun testSemanticAndTagVersionComparison() {
        val cmp = VersionComparator.compareSemantic("1.10.0", "1.9.0")
        assertTrue(cmp != null && cmp > 0)

        assertEquals("1.6.0", VersionComparator.cleanVersion("v1.6.0"))
        assertEquals("1.6.0", VersionComparator.cleanVersion("V1.6.0"))

        assertTrue(VersionComparator.areTagsEqual("26.06.26.1", "v26.06.26.1"))
        assertTrue(VersionComparator.areTagsEqual("1.2", "v1.2.0"))
        assertFalse(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = "26.06.26.1",
                githubTagName = "v26.06.26.1"
            )
        )

        assertTrue(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = "1.9.0",
                githubTagName = "v1.10.0"
            )
        )

        assertTrue(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = "2.0.0",
                githubTagName = "v1.9.0"
            )
        )
        assertTrue(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = "release-alpha",
                githubTagName = "release-beta"
            )
        )
        assertFalse(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = "release-beta",
                githubTagName = "release-beta"
            )
        )
    }

    @Test
    fun testTrackedInstalledTagOverridesMismatchedApkVersionName() {
        val tracked = com.example.data.repository.InstalledPackageInfo(
            isInstalled = true,
            versionName = "1.0",
            versionCode = 1L,
            lastUpdateTime = 123456L,
            installedReleaseTag = "v26.06.26.2"
        )
        assertEquals("v26.06.26.2", tracked.effectiveVersion)
        assertFalse(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = tracked.effectiveVersion,
                githubTagName = "26.06.26.2"
            )
        )
        assertTrue(
            VersionComparator.isTagUpdateAvailable(
                installedVersion = tracked.effectiveVersion,
                githubTagName = "26.06.26.3"
            )
        )

        val untracked = com.example.data.repository.InstalledPackageInfo(
            isInstalled = true,
            versionName = "1.0",
            versionCode = 1L,
            lastUpdateTime = 123456L,
            installedReleaseTag = null
        )
        assertEquals("1.0", untracked.effectiveVersion)
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
    fun testGitHubDtosAndManifestDeserialization() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val releaseJson = """
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
        val releaseDto = moshi.adapter(GitHubReleaseDto::class.java).fromJson(releaseJson)
        assertNotNull(releaseDto)
        assertEquals("v1.6.0", releaseDto?.tagName)
        assertEquals("app-release.apk", releaseDto?.assets?.firstOrNull { it.name.endsWith(".apk") }?.name)

        val repoJson = """
            {
              "name": "Money-Map",
              "description": "Personal expense tracker",
              "fork": false,
              "archived": false,
              "default_branch": "main",
              "owner": { "login": "patricknabil611" }
            }
        """.trimIndent()
        val repoDto = moshi.adapter(GitHubRepoDto::class.java).fromJson(repoJson)
        assertNotNull(repoDto)
        assertEquals("Money-Map", repoDto?.name)
        assertEquals("patricknabil611", repoDto?.owner?.login)
        assertFalse(repoDto?.fork == true)
        assertFalse(repoDto?.archived == true)

        val manifestJson = """
            {
              "name": "P Money",
              "packageName": "com.aistudio.moneymap.zqpjfl",
              "description": "Track budgets and spending",
              "icon": "assets/icon.png",
              "accentColor": "#2FBF85"
            }
        """.trimIndent()
        val manifestDto = moshi.adapter(PStoreManifestDto::class.java).fromJson(manifestJson)
        assertNotNull(manifestDto)
        assertEquals("P Money", manifestDto?.name)
        assertEquals("com.aistudio.moneymap.zqpjfl", manifestDto?.packageName)
        assertEquals("assets/icon.png", manifestDto?.icon)
        assertEquals("#2FBF85", manifestDto?.accentColor)
    }

    @Test
    fun testCustomAppValidationAndParsing() {
        assertTrue(AppCatalog.isValidPackageName("com.example.myapp"))
        assertTrue(AppCatalog.isValidPackageName("com.aistudio.pink.spenzx"))
        assertFalse(AppCatalog.isValidPackageName("invalidpackage"))
        assertFalse(AppCatalog.isValidPackageName("com..broken"))
        assertFalse(AppCatalog.isValidPackageName(""))

        assertEquals("patricknabil611" to "P-Ink", AppCatalog.parseGitHubRepo("patricknabil611/P-Ink"))
        assertEquals(
            "patricknabil611" to "P-Ink",
            AppCatalog.parseGitHubRepo("https://github.com/patricknabil611/P-Ink")
        )
        assertEquals(
            "patricknabil611" to "P-Ink",
            AppCatalog.parseGitHubRepo("https://github.com/patricknabil611/P-Ink/releases")
        )
        assertEquals(
            "patricknabil611" to "P-Ink",
            AppCatalog.parseGitHubRepo("github.com/patricknabil611/P-Ink.git")
        )
        assertEquals(null, AppCatalog.parseGitHubRepo("onlyowner"))
        assertEquals(null, AppCatalog.parseGitHubRepo(""))
    }
}
