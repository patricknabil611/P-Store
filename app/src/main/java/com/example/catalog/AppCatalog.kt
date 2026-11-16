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
    val knownApkTagName: String? = null,
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
            knownApkTagName = "26.06.26.1",
            knownApkVersionName = "2.5.0",
            knownApkVersionCode = 25L
        ),
        CatalogApp(
            name = "P Task",
            owner = "patricknabil611",
            repo = "P-Task",
            defaultPackageName = "com.aistudio.todolist.zkvmrq",
            knownApkTagName = "26.06.26.4",
            knownApkVersionName = "26.06.26.2",
            knownApkVersionCode = 7L
        ),
        CatalogApp(
            name = "P Habits",
            owner = "patricknabil611",
            repo = "P-Habits",
            defaultPackageName = "com.aistudio.phabits.qvxrzt",
            knownApkTagName = "26.06.26.1",
            knownApkVersionName = "4.0",
            knownApkVersionCode = 4L
        ),
        CatalogApp(
            name = "P Store",
            owner = "patricknabil611",
            repo = "P-Store",
            isSelf = true, // P Store updates itself
            defaultPackageName = "com.patricknabil.pstore",
            knownApkTagName = "26.06.26.2",
            knownApkVersionName = "1.1",
            knownApkVersionCode = 2L
        )
    )
}
