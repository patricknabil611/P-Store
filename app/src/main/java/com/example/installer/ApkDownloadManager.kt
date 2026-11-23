package com.example.installer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ApkDownloadManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val apksDir: File
        get() = File(context.cacheDir, "apks").apply {
            if (!exists()) mkdirs()
        }

    fun getApkFile(packageName: String): File {
        val safeName = "${packageName}.apk".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(apksDir, safeName)
    }

    fun cancelDownload(packageName: String) {
        activeCalls.remove(packageName)?.cancel()
    }

    suspend fun downloadApk(
        packageName: String,
        url: String,
        expectedSize: Long = 0L,
        token: String? = null,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val destinationFile = getApkFile(packageName)

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "P-Store-Android-App")
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${token.trim()}")
        }

        val call = client.newCall(requestBuilder.build())
        activeCalls[packageName] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Download failed (HTTP ${response.code})"))
                }

                val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: expectedSize
                var downloadedBytes = 0L
                var lastEmitTime = 0L

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!coroutineContext.isActive) {
                                destinationFile.delete()
                                return@withContext Result.failure(IOException("Download cancelled"))
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 80L || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
                                lastEmitTime = now
                                val progress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                onProgress(progress, downloadedBytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(1f, downloadedBytes, if (totalBytes > 0L) totalBytes else downloadedBytes)
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            destinationFile.delete()
            Result.failure(e)
        } finally {
            activeCalls.remove(packageName)
        }
    }

    fun clearCache(): Long {
        var bytesFreed = 0L
        try {
            apksDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    bytesFreed += file.length()
                    file.delete()
                }
            }
        } catch (_: Exception) {}
        return bytesFreed
    }

    fun getCacheSizeBytes(): Long {
        var total = 0L
        try {
            apksDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    total += file.length()
                }
            }
        } catch (_: Exception) {}
        return total
    }
}
