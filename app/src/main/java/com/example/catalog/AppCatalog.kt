package com.example.catalog

/**
 * Single source of truth for apps managed by P Store.
 * Add new apps here to include them in the catalog.
 */
data class CatalogApp(
    val name: String,
    val owner: String,
    val repo: String,
    val isSelf: Boolean = false,
    val defaultPackageName: String? = null,
    val knownApkVersionName: String? = null,
    val knownApkVersionCode: Long? = null
)

object AppCatalog {
    val apps: List<CatalogApp> = listOf(
        CatalogApp(
            name = "P Money",
            owner = "patricknabil611",
            repo = "Money-Map",
            defaultPackageName = "com.aistudio.moneymap.zqpjfl",
            knownApkVersionName = "2.5.0",
            knownApkVersionCode = 25L
        ),
        CatalogApp(
            name = "P Task",
            owner = "patricknabil611",
            repo = "P-Task",
            defaultPackageName = "com.aistudio.todolist.zkvmrq",
            knownApkVersionName = "4.0",
            knownApkVersionCode = 4L
        ),
        CatalogApp(
            name = "P Habits",
            owner = "patricknabil611",
            repo = "P-Habits",
            defaultPackageName = "com.aistudio.phabits.qvxrzt",
            knownApkVersionName = "4.0",
            knownApkVersionCode = 4L
        ),
        CatalogApp(
            name = "P Store",
            owner = "patricknabil611",
            repo = "P-Store",
            isSelf = true, // P Store updates itself
            defaultPackageName = "com.patricknabil.pstore",
            knownApkVersionName = "26.06.26.1",
            knownApkVersionCode = 26062601L
        )
    )
}
