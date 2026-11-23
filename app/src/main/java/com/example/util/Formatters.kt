package com.example.util

import android.content.Context
import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024f * 1024f)
        return if (mb >= 1.0f) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024f
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatFileSizeAlwaysMb(bytes: Long): String {
        val mb = (bytes.coerceAtLeast(0L)) / (1024f * 1024f)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun formatRelativeChecked(context: Context, lastCheckedMs: Long, withSyncPrefix: Boolean): String {
        if (lastCheckedMs <= 0L) {
            return context.getString(R.string.synced_with_github_never)
        }
        val diffMs = (System.currentTimeMillis() - lastCheckedMs).coerceAtLeast(0L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)

        return if (withSyncPrefix) {
            when {
                mins < 1 -> context.getString(R.string.synced_with_github_just_now)
                hours < 1 -> context.getString(R.string.synced_with_github_mins, mins)
                days < 1 -> context.getString(R.string.synced_with_github_hours, hours)
                else -> context.getString(R.string.synced_with_github_days, days)
            }
        } else {
            when {
                mins < 1 -> context.getString(R.string.last_checked_just_now)
                hours < 1 -> context.getString(R.string.last_checked_mins, mins)
                days < 1 -> context.getString(R.string.last_checked_hours, hours)
                else -> context.getString(R.string.last_checked_days, days)
            }
        }
    }

    fun formatGreetingResId(): Int {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    }

    fun formatShortDate(millis: Long): String {
        return try {
            SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(millis))
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Parses simple GitHub markdown release notes into clean bullet lines.
     */
    fun parseReleaseBullets(rawMarkdown: String): List<String> {
        if (rawMarkdown.isBlank()) return emptyList()
        return rawMarkdown
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("---") && !it.startsWith("```") }
            .map { line ->
                line
                    .replace(Regex("^#+\\s*"), "")
                    .replace(Regex("^[-*•+]\\s+"), "")
                    .replace(Regex("^\\d+\\.\\s+"), "")
                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                    .replace(Regex("__(.*?)__"), "$1")
                    .replace(Regex("`([^`]*)`"), "$1")
                    .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                    .trim()
            }
            .filter { it.isNotEmpty() }
    }
}
