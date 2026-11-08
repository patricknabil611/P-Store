package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.example.receiver.InstallResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

data class ApkArchiveDetails(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long
)

class PackageInstallerHelper(private val context: Context) {

    /**
     * Checks if P Store has permission to install packages.
     */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates an Intent to take the user to the "Install unknown apps" settings page for P Store.
     */
    fun createManageUnknownAppSourcesIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Inspects a downloaded APK and extracts its package name and versions.
     */
    fun readApkDetails(apkFile: File): ApkArchiveDetails? {
        if (!apkFile.exists() || apkFile.length() == 0L) return null
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)

            archiveInfo?.let {
                val versionCode = PackageInfoCompat.getLongVersionCode(it)
                ApkArchiveDetails(
                    packageName = it.packageName,
                    versionName = it.versionName,
                    versionCode = versionCode
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Starts installation session with PackageInstaller API.
     */
    suspend fun installApk(apkFile: File, appName: String, expectedPackageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            return@withContext Result.failure(IllegalStateException("APK file not found"))
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(expectedPackageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        var sessionId = -1
        try {
            sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { inputStream: InputStream ->
                val outputStream: OutputStream = session.openWrite("package", 0L, apkFile.length())
                outputStream.use { out ->
                    inputStream.copyTo(out)
                    session.fsync(out)
                }
            }

            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_STATUS
                putExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME, expectedPackageName)
                putExtra(InstallResultReceiver.EXTRA_APP_NAME, appName)
                putExtra(InstallResultReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                flags
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            Result.success(Unit)
        } catch (e: Exception) {
            if (sessionId != -1) {
                try {
                    packageInstaller.abandonSession(sessionId)
                } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }
}
