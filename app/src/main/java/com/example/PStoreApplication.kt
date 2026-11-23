package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.preferences.PStoreDataStore
import com.example.worker.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PStoreApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        appScope.launch {
            val dataStore = PStoreDataStore(this@PStoreApplication)
            val enabled = dataStore.autoCheckEnabled.first()
            val interval = dataStore.checkInterval.first()
            val network = dataStore.downloadOver.first()
            if (enabled) {
                UpdateCheckWorker.schedule(
                    context = this@PStoreApplication,
                    interval = interval,
                    networkPreference = network,
                    replaceExisting = false
                )
            } else {
                UpdateCheckWorker.cancel(this@PStoreApplication)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateCheckWorker.CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
