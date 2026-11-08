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
        .build()
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val apksDir: File
        get() = File(context.cacheDir, "apks").apply {
            if (!exists()) mkdirs()
        }

    fun getApkFile(owner: String, repo: String): File {
        val safeName = "${owner}_${repo}.apk".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(apksDir, safeName)
    }

    fun cancelDownload(repoKey: String) {
        activeCalls.remove(repoKey)?.cancel()
    }

    suspend fun downloadApk(
        owner: String,
        repo: String,
        url: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val repoKey = "${owner}/$repo"
        val destinationFile = getApkFile(owner, repo)

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "P-Store-Android-App")
            .build()

        val call = client.newCall(request)
        activeCalls[repoKey] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP error code ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!coroutineContext.isActive) {
                                destinationFile.delete()
                                return@withContext Result.failure(IOException("Download cancelled"))
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }

                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            destinationFile.delete()
            Result.failure(e)
        } finally {
            activeCalls.remove(repoKey)
        }
    }

    /**
     * Clears all downloaded APK files from cache directory.
     * Returns total bytes freed.
     */
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
