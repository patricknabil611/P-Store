package com.example.catalog

import kotlin.math.abs

enum class AppIconType {
    COIN_DOLLAR,
    CHECKBOX,
    FLAME,
    PEN_NIB,
    DIE_FIVE,
    P_STORE,
    APPS_GRID,
    STAR,
    BOLT,
    CODE,
    INITIALS;

    companion object {
        val customSelectable: List<AppIconType> = listOf(
            INITIALS,
            APPS_GRID,
            STAR,
            BOLT,
            CODE,
            CHECKBOX,
            FLAME,
            PEN_NIB,
            COIN_DOLLAR,
            DIE_FIVE
        )

        fun fromNameOrDefault(name: String?): AppIconType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: INITIALS
    }
}

data class BrandColorPreset(
    val name: String,
    val darkColor: Long,
    val lightColor: Long,
    val darkTileTint: Long
)

/**
 * Represents an app discovered from GitHub Releases (or P Store itself for self-update).
 */
data class CatalogApp(
    val displayName: String,
    val packageName: String,
    val githubOwner: String,
    val githubRepo: String,
    val brandColorDark: Long,
    val brandColorLight: Long,
    val darkTileTint: Long,
    val icon: AppIconType = AppIconType.INITIALS,
    val description: String = "",
    val iconUrl: String? = null,
    val firstDiscoveredAtMillis: Long = 0L,
    val showInLauncher: Boolean = true,
    val isSelf: Boolean = false
) {
    val fullRepoName: String
        get() = "$githubOwner/$githubRepo"

    /**
     * Stable unique identifier across discovery, caching, and navigation,
     * even before packageName is learned from an APK download.
     */
    val repoKey: String
        get() = fullRepoName.lowercase()
}

object AppCatalog {
    const val GITHUB_USERNAME = "patricknabil611"
    const val SELF_REPO_NAME = "P-Store"
    const val SELF_PACKAGE_NAME = "com.patricknabil.pstore"

    /**
     * P Store self-update descriptor (never shown in the launcher grid).
     */
    val selfApp: CatalogApp = CatalogApp(
        displayName = "P Store",
        packageName = SELF_PACKAGE_NAME,
        githubOwner = GITHUB_USERNAME,
        githubRepo = SELF_REPO_NAME,
        brandColorDark = 0xFF8FA8FF,
        brandColorLight = 0xFF4F46E5,
        darkTileTint = 0xFF1A2038,
        icon = AppIconType.P_STORE,
        showInLauncher = false,
        isSelf = true
    )

    /**
     * Pre-seeded repo -> Android packageName mapping for existing apps so
     * installed/update detection works immediately before any APK download.
     */
    val preSeededPackageNames: Map<String, String> = mapOf(
        "money-map" to "com.aistudio.moneymap.zqpjfl",
        "p-task" to "com.aistudio.todolist.zkvmrq",
        "p-habits" to "com.aistudio.phabits.qvxrzt",
        "p-ink" to "com.aistudio.pink.spenzx",
        "ludo-for-m" to "com.aistudio.ludoform.qwrtxz"
    )

    val colorPresets: List<BrandColorPreset> = listOf(
        BrandColorPreset("Blue", 0xFF4C8DFF, 0xFF2F6BFF, 0xFF142240),
        BrandColorPreset("Emerald", 0xFF2FBF85, 0xFF1E9E6A, 0xFF12291F),
        BrandColorPreset("Amber", 0xFFFF9A3D, 0xFFE8741C, 0xFF33220F),
        BrandColorPreset("Violet", 0xFFB38CFF, 0xFF7C4DDB, 0xFF241A3A),
        BrandColorPreset("Rose", 0xFFFF5C7A, 0xFFD93A5A, 0xFF3A1620),
        BrandColorPreset("Teal", 0xFF2DD4BF, 0xFF0D9488, 0xFF11292A),
        BrandColorPreset("Indigo", 0xFF8FA8FF, 0xFF4F46E5, 0xFF1A2038)
    )

    private val packageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val githubSegmentRegex = Regex("^[A-Za-z0-9._-]+$")

    fun preSeededPackageForRepo(repoName: String): String? =
        preSeededPackageNames[repoName.trim().lowercase()]

    fun formatRepoDisplayName(repoName: String): String =
        repoName.trim()
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { repoName }

    fun extractInitials(displayName: String): String {
        val parts = displayName.trim()
            .split(Regex("[\\s_-]+"))
            .filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "AP"
        }
    }

    fun colorPresetForName(name: String): BrandColorPreset {
        val idx = abs(name.trim().lowercase().hashCode()) % colorPresets.size
        return colorPresets[idx]
    }

    /**
     * Parses a hex accent color string (e.g. "#2FBF85" or "2FBF85") into dark, light, and tinted tile ARGB longs.
     */
    fun parseHexAccentColor(hex: String?): BrandColorPreset? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.trim().removePrefix("#")
        val rgb = when (cleaned.length) {
            6 -> cleaned.toLongOrNull(16)
            8 -> cleaned.substring(2).toLongOrNull(16)
            else -> null
        } ?: return null

        val r = ((rgb shr 16) and 0xFF).toInt()
        val g = ((rgb shr 8) and 0xFF).toInt()
        val b = (rgb and 0xFF).toInt()

        val darkArgb = (0xFFL shl 24) or rgb
        // Slightly richer light-mode color
        val lightR = (r * 0.85f).toInt().coerceIn(0, 255)
        val lightG = (g * 0.85f).toInt().coerceIn(0, 255)
        val lightB = (b * 0.85f).toInt().coerceIn(0, 255)
        val lightArgb = (0xFFL shl 24) or (lightR.toLong() shl 16) or (lightG.toLong() shl 8) or lightB.toLong()

        // Blend ~16% of accent into Midnight Dock surface (#0D1017) for dark tile tint
        val tintR = (13 + (r - 13) * 0.16f).toInt().coerceIn(0, 255)
        val tintG = (16 + (g - 16) * 0.16f).toInt().coerceIn(0, 255)
        val tintB = (23 + (b - 23) * 0.16f).toInt().coerceIn(0, 255)
        val tileTintArgb = (0xFFL shl 24) or (tintR.toLong() shl 16) or (tintG.toLong() shl 8) or tintB.toLong()

        return BrandColorPreset(
            name = "Custom",
            darkColor = darkArgb,
            lightColor = lightArgb,
            darkTileTint = tileTintArgb
        )
    }

    fun isValidPackageName(packageName: String): Boolean =
        packageNameRegex.matches(packageName.trim())

    fun parseGitHubRepo(input: String): Pair<String, String>? {
        val cleaned = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .trim('/')

        val segments = cleaned.split("/").filter { it.isNotBlank() }
        if (segments.size < 2) return null

        val owner = segments[0].trim()
        val repo = segments[1].trim().removeSuffix(".git")
        if (owner.isEmpty() || repo.isEmpty()) return null
        if (!githubSegmentRegex.matches(owner) || !githubSegmentRegex.matches(repo)) return null

        return owner to repo
    }

    fun formatAppInfo(app: CatalogApp): String {
        val pkgLine = app.packageName.ifBlank { "Detected on install" }
        return "Display name: ${app.displayName}\nPackage name: $pkgLine\nGitHub repo: ${app.fullRepoName}"
    }

    fun formatAllAppsInfo(catalog: List<CatalogApp>): String =
        catalog.joinToString("\n\n") { formatAppInfo(it) }
}
