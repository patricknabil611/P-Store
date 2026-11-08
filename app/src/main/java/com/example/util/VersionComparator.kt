package com.example.util

object VersionComparator {

    /**
     * Cleans a version string by removing leading 'v'/'V' and whitespace.
     */
    fun cleanVersion(version: String?): String {
        if (version == null) return ""
        return version.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Compares two semantic version strings (e.g., "1.10.0" vs "1.9.0").
     * Returns:
     *   > 0 if v1 > v2
     *   < 0 if v1 < v2
     *   0 if v1 == v2
     *   null if versions cannot be parsed semantically
     */
    fun compareSemantic(v1: String?, v2: String?): Int? {
        val clean1 = cleanVersion(v1)
        val clean2 = cleanVersion(v2)

        if (clean1.isEmpty() || clean2.isEmpty()) return null

        val hasDigits1 = clean1.any { it.isDigit() }
        val hasDigits2 = clean2.any { it.isDigit() }
        if (!hasDigits1 || !hasDigits2) return null

        // Extract numeric segments, e.g. "1.10.0" -> [1, 10, 0]
        val parts1 = clean1.split(".", "-", "_")
        val parts2 = clean2.split(".", "-", "_")

        val maxLen = maxOf(parts1.size, parts2.size)
        var parsedAtLeastOne = false

        for (i in 0 until maxLen) {
            val token1 = parts1.getOrNull(i)
            val token2 = parts2.getOrNull(i)

            val num1 = token1?.filter { it.isDigit() }?.toLongOrNull()
            val num2 = token2?.filter { it.isDigit() }?.toLongOrNull()

            if (num1 != null || num2 != null) {
                parsedAtLeastOne = true
                val n1 = num1 ?: 0L
                val n2 = num2 ?: 0L
                if (n1 != n2) {
                    return n1.compareTo(n2)
                }
            } else if (token1 != null && token2 != null) {
                val cmp = token1.compareTo(token2, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }

        return if (parsedAtLeastOne) 0 else null
    }

    /**
     * Checks if the latest version is newer than the installed version.
     */
    fun isUpdateAvailable(
        installedVersionName: String?,
        installedVersionCode: Long?,
        latestVersionName: String,
        apkVersionCode: Long? = null
    ): Boolean {
        if (installedVersionName.isNullOrBlank()) return false

        val semanticCmp = compareSemantic(latestVersionName, installedVersionName)
        if (semanticCmp != null) {
            return semanticCmp > 0
        }

        // Fallback to versionCode if versionName cannot be parsed
        if (apkVersionCode != null && installedVersionCode != null) {
            return apkVersionCode > installedVersionCode
        }

        return false
    }
}
