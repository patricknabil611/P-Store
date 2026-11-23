package com.example.catalog

enum class AppIconType {
    COIN_DOLLAR,
    CHECKBOX,
    FLAME,
    PEN_NIB,
    DIE_FIVE,
    P_STORE
}

/**
 * Single source of truth for apps managed by P Store.
 * Add new apps here without touching UI code.
 */
data class CatalogApp(
    val displayName: String,
    val packageName: String,
    val githubOwner: String,
    val githubRepo: String,
    val brandColorDark: Long,
    val brandColorLight: Long,
    val darkTileTint: Long,
    val icon: AppIconType,
    val showInLauncher: Boolean = true,
    val isSelf: Boolean = false
) {
    val fullRepoName: String
        get() = "$githubOwner/$githubRepo"
}

object AppCatalog {
    val apps: List<CatalogApp> = listOf(
        CatalogApp(
            displayName = "P Money",
            packageName = "com.aistudio.moneymap.zqpjfl",
            githubOwner = "patricknabil611",
            githubRepo = "Money-Map",
            brandColorDark = 0xFF2FBF85,
            brandColorLight = 0xFF1E9E6A,
            darkTileTint = 0xFF12291F,
            icon = AppIconType.COIN_DOLLAR,
            showInLauncher = true
        ),
        CatalogApp(
            displayName = "P Task",
            packageName = "com.aistudio.todolist.zkvmrq",
            githubOwner = "patricknabil611",
            githubRepo = "P-Task",
            brandColorDark = 0xFF4C8DFF,
            brandColorLight = 0xFF2F6BFF,
            darkTileTint = 0xFF142240,
            icon = AppIconType.CHECKBOX,
            showInLauncher = true
        ),
        CatalogApp(
            displayName = "P Habits",
            packageName = "com.aistudio.phabits.qvxrzt",
            githubOwner = "patricknabil611",
            githubRepo = "P-Habits",
            brandColorDark = 0xFFFF9A3D,
            brandColorLight = 0xFFE8741C,
            darkTileTint = 0xFF33220F,
            icon = AppIconType.FLAME,
            showInLauncher = true
        ),
        CatalogApp(
            displayName = "P Ink",
            packageName = "com.aistudio.pink.spenzx",
            githubOwner = "patricknabil611",
            githubRepo = "P-Ink",
            brandColorDark = 0xFFB38CFF,
            brandColorLight = 0xFF7C4DDB,
            darkTileTint = 0xFF241A3A,
            icon = AppIconType.PEN_NIB,
            showInLauncher = true
        ),
        CatalogApp(
            displayName = "Ludo for M",
            packageName = "com.aistudio.ludoform.qwrtxz",
            githubOwner = "patricknabil611",
            githubRepo = "Ludo-for-M",
            brandColorDark = 0xFFFF5C7A,
            brandColorLight = 0xFFD93A5A,
            darkTileTint = 0xFF3A1620,
            icon = AppIconType.DIE_FIVE,
            showInLauncher = true
        ),
        CatalogApp(
            displayName = "P Store",
            packageName = "com.patricknabil.pstore",
            githubOwner = "patricknabil611",
            githubRepo = "P-Store",
            brandColorDark = 0xFF8FA8FF,
            brandColorLight = 0xFF4F46E5,
            darkTileTint = 0xFF1A2038,
            icon = AppIconType.P_STORE,
            showInLauncher = false,
            isSelf = true
        )
    )

    fun findByPackageName(packageName: String): CatalogApp? =
        apps.firstOrNull { it.packageName == packageName }

    fun formatAppInfo(app: CatalogApp): String =
        "Display name: ${app.displayName}\nPackage name: ${app.packageName}\nGitHub repo: ${app.fullRepoName}"

    fun formatAllAppsInfo(catalog: List<CatalogApp> = apps): String =
        catalog.joinToString("\n\n") { formatAppInfo(it) }
}
