package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.catalog.AppCatalog
import com.example.data.api.GitHubApiClient
import com.example.data.preferences.PStoreDataStore
import com.example.model.ReleaseStatus
import com.example.util.VersionComparator
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "p_store_update_check"
        const val CHANNEL_ID = "p_store_updates_channel"
        const val NOTIFICATION_ID = 1001

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val dataStore = PStoreDataStore(appContext)
        val isEnabled = dataStore.backgroundChecksEnabled.first()
        if (!isEnabled) {
            return Result.success()
        }

        val api = GitHubApiClient()
        val pm = appContext.packageManager
        var updateCount = 0

        for (app in AppCatalog.apps) {
            val savedPackage = if (app.isSelf) {
                appContext.packageName
            } else {
                dataStore.getSavedPackageNameOnce(app.owner, app.repo)
            }

            var installedVersionName: String? = null
            var installedVersionCode: Long? = null

            if (savedPackage != null) {
                try {
                    val info = pm.getPackageInfo(savedPackage, 0)
                    installedVersionName = info.versionName
                    installedVersionCode = PackageInfoCompat.getLongVersionCode(info)
                } catch (_: PackageManager.NameNotFoundException) {}
            }

            // If not installed, skip update count
            if (installedVersionName == null) continue

            when (val result = api.getLatestRelease(app.owner, app.repo)) {
                is ReleaseStatus.Success -> {
                    dataStore.saveCachedRelease(app.owner, app.repo, result.release)
                    val hasUpdate = VersionComparator.isUpdateAvailable(
                        installedVersionName = installedVersionName,
                        installedVersionCode = installedVersionCode,
                        latestVersionName = result.release.version
                    )
                    if (hasUpdate) {
                        updateCount++
                    }
                }
                else -> { /* Skip errors in background */ }
            }
        }

        dataStore.setLastCheckTime(System.currentTimeMillis())

        if (updateCount > 0) {
            showNotification(updateCount)
        }

        return Result.success()
    }

    private fun showNotification(count: Int) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when updates are available for your apps"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, flags)

        val text = if (count == 1) "1 update available" else "$count updates available"

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.p_store_icon)
            .setContentTitle("P Store")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission might be revoked
        }
    }
}
