package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.catalog.CatalogApp
import com.example.data.repository.PStoreRepository
import com.example.model.AppInstallStatus
import com.example.model.CheckIntervalHours
import com.example.model.NetworkPreference
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class UpdateCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "p_store_update_check"
        const val CHANNEL_ID = "p_store_updates_channel"
        const val NOTIFICATION_ID = 1001
        const val NEW_APP_NOTIFICATION_BASE_ID = 2000
        const val EXTRA_OPEN_APP_KEY = "extra_open_app_key"

        fun schedule(
            context: Context,
            interval: CheckIntervalHours = CheckIntervalHours.H6,
            networkPreference: NetworkPreference = NetworkPreference.WIFI_ONLY,
            replaceExisting: Boolean = false
        ) {
            val networkType = when (networkPreference) {
                NetworkPreference.WIFI_ONLY -> NetworkType.UNMETERED
                NetworkPreference.ANY_NETWORK -> NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                interval.hours,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                policy,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val repository = PStoreRepository(appContext)
        val autoCheckEnabled = repository.autoCheckEnabled.first()
        if (!autoCheckEnabled) {
            return Result.success()
        }

        val notificationsEnabled = repository.notificationsEnabled.first()
        val discovery = repository.discoverAppsFromGitHub()

        if (notificationsEnabled && discovery.newlyDiscoveredApps.isNotEmpty()) {
            discovery.newlyDiscoveredApps.forEach { newApp ->
                showNewAppNotification(newApp)
            }
        }

        var updateCount = 0
        for (app in discovery.visibleApps) {
            val installedInfo = repository.getInstalledInfoWithTag(app.packageName)
            if (!installedInfo.isInstalled) continue

            val latestRelease = discovery.releasesByRepoKey[app.repoKey]
                ?: repository.getCachedRelease(app)
                ?: continue

            val installStatus = repository.computeInstallStatus(installedInfo, latestRelease)
            if (installStatus == AppInstallStatus.UPDATE_AVAILABLE) {
                updateCount++
            }
        }

        if (updateCount > 0 && notificationsEnabled) {
            showUpdatesNotification(updateCount)
        }

        return Result.success()
    }

    private fun ensureChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun pendingIntentFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    private fun showNewAppNotification(app: CatalogApp) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val notificationId = NEW_APP_NOTIFICATION_BASE_ID + (abs(app.repoKey.hashCode()) % 1000)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_APP_KEY, app.repoKey)
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            pendingIntentFlags()
        )

        val text = appContext.getString(R.string.notification_new_app, app.displayName)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.p_store_icon)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked by user
        }
    }

    private fun showUpdatesNotification(count: Int) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(notificationManager)

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent, pendingIntentFlags())
        val text = if (count == 1) {
            appContext.getString(R.string.notification_update_one)
        } else {
            appContext.getString(R.string.notification_update_many, count)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.p_store_icon)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked by user
        }
    }
}
