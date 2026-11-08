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
    val defaultPackageName: String? = null
)

object AppCatalog {
    val apps: List<CatalogApp> = listOf(
        CatalogApp(
            name = "P Money",
            owner = "patricknabil611",
            repo = "Money-Map",
            defaultPackageName = "com.aistudio.moneymap.zqpjfl"
        ),
        CatalogApp(
            name = "P Task",
            owner = "patricknabil611",
            repo = "P-Task",
            defaultPackageName = "com.aistudio.todolist.zkvmrq"
        ),
        CatalogApp(
            name = "P Habits",
            owner = "patricknabil611",
            repo = "P-Habits",
            defaultPackageName = "com.aistudio.phabits.qvxrzt"
        ),
        CatalogApp(
            name = "P Store",
            owner = "patricknabil611",
            repo = "P-Store",
            isSelf = true, // P Store updates itself
            defaultPackageName = "com.patricknabil.pstore"
        )
    )
}
