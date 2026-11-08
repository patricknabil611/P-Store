package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.example.installer.InstallEvent
import com.example.installer.InstallEvents
import java.io.File

class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_STATUS = "com.example.pstore.ACTION_INSTALL_STATUS"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_APK_PATH = "extra_apk_path"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: ""

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system needs user confirmation to install/update the app
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    InstallEvents.emit(InstallEvent.UserActionRequired(packageName, appName))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // Delete the APK file on success as required
                if (apkPath.isNotEmpty()) {
                    try {
                        val file = File(apkPath)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (_: Exception) {}
                }
                InstallEvents.emit(InstallEvent.Success(packageName, appName, apkPath))
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                InstallEvents.emit(InstallEvent.SignatureConflict(packageName, appName, apkPath))
            }

            else -> {
                val isSignatureMismatch = statusMessage.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                        statusMessage.contains("signature", ignoreCase = true) ||
                        statusMessage.contains("signatures do not match", ignoreCase = true)

                if (isSignatureMismatch) {
                    InstallEvents.emit(InstallEvent.SignatureConflict(packageName, appName, apkPath))
                } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    val err = if (statusMessage.isNotBlank()) statusMessage else "Install failed ($status)"
                    InstallEvents.emit(InstallEvent.Failure(packageName, appName, err, apkPath))
                } else {
                    // Aborted by user
                    InstallEvents.emit(InstallEvent.Failure(packageName, appName, "Installation cancelled", apkPath))
                }
            }
        }
    }
}
