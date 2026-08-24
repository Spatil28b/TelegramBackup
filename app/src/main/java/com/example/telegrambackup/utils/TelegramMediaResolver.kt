package com.example.telegrambackup.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.example.telegrambackup.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * High-performance Media & File Resolver for Telegram Cloud items.
 * Handles instant direct URL resolution, thumbnail resolution, and transparent on-demand caching
 * for images, videos, audio, PDFs, and documents.
 */
object TelegramMediaResolver {
    private const val TAG = "TelegramMediaResolver"
    private val urlCache = LruCache<String, String>(500)
    private val bitmapMemoryCache = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private fun getEffectiveBotToken(context: Context): String {
        val prefs = AppPrefs(context)
        if (prefs.botToken.isNotBlank()) return prefs.botToken.trim()

        try {
            val dbToken = com.example.telegrambackup.data.AppDatabase.getDatabase(context).appSettingDao().getSetting("bot_token")?.value
            if (!dbToken.isNullOrBlank()) {
                prefs.botToken = dbToken.trim()
                return dbToken.trim()
            }
        } catch (_: Exception) {}

        return ""
    }

    private suspend fun getCandidateBotTokens(context: Context): List<String> {
        val prefs = AppPrefs(context)
        val tokens = mutableListOf<String>()
        if (prefs.botToken.isNotBlank()) tokens.add(prefs.botToken.trim())
        try {
            val bots = com.example.telegrambackup.network.BotFleetManager.getBotPoolForParallelChunks(context, 10)
            for (b in bots) {
                val t = b.botToken.trim()
                if (t.isNotBlank() && !tokens.contains(t)) {
                    tokens.add(t)
                }
            }
        } catch (_: Exception) {}
        return tokens
    }

    /**
     * Resolves a Telegram file_id to a direct HTTPS file streaming URL with multi-bot fleet failover.
     */
    suspend fun resolveDirectUrl(context: Context, telegramFileId: String): String? = withContext(Dispatchers.IO) {
        if (telegramFileId.isBlank() || telegramFileId == "DIR_MARKER" || telegramFileId.startsWith("NOTE_MARKER")) {
            return@withContext null
        }

        urlCache.get(telegramFileId)?.let { return@withContext it }

        val candidateTokens = getCandidateBotTokens(context)
        val client = HttpClientProvider.client

        for (botToken in candidateTokens) {
            try {
                val requestUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$telegramFileId"
                val request = Request.Builder().url(requestUrl).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val filePath = json.optJSONObject("result")?.optString("file_path")
                    if (!filePath.isNullOrBlank()) {
                        val directUrl = "https://api.telegram.org/file/bot$botToken/$filePath"
                        urlCache.put(telegramFileId, directUrl)
                        return@withContext directUrl
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bot failed to resolve direct URL for $telegramFileId", e)
            }
        }
        return@withContext null
    }

    /**
     * Checks if a downloaded file already starts with standard plain media magic bytes
     * (JPEG, PNG, WEBP, GIF, PDF, etc.) so we never corrupt valid unencrypted media with decryption.
     */
    private fun isPlainMedia(file: File): Boolean {
        if (!file.exists() || file.length() < 2) return false
        try {
            java.io.FileInputStream(file).use { fis ->
                val header = ByteArray(12)
                val read = fis.read(header)
                if (read >= 2) {
                    // JPEG: FF D8 FF
                    if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return true
                    // PNG: 89 50 4E 47
                    if (read >= 4 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) return true
                    // GIF: 47 49 46 38
                    if (read >= 4 && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) return true
                    // WEBP: 52 49 46 46 ... 57 45 42 50
                    if (read >= 12 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
                        header[8] == 0x57.toByte() && header[9] == 0x45.toByte() && header[10] == 0x42.toByte() && header[11] == 0x50.toByte()) return true
                    // PDF: 25 50 44 46 (%PDF)
                    if (read >= 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Downloads and caches a cloud file into a local temporary cache directory.
     * Decrypts in-place if encryption is enabled and file is encrypted.
     */
    suspend fun fetchToTempCache(
        context: Context,
        telegramFileId: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        if (telegramFileId.isBlank() || telegramFileId == "DIR_MARKER" || telegramFileId.startsWith("NOTE_MARKER")) {
            return@withContext null
        }

        val cacheDir = File(context.cacheDir, "cloud_preview_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
        val targetFile = File(cacheDir, "${telegramFileId.hashCode()}_$safeName")

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        val directUrl = resolveDirectUrl(context, telegramFileId) ?: return@withContext null
        val client = HttpClientProvider.client

        try {
            BackupProgressTracker.startDownload(fileName)
            val request = Request.Builder().url(directUrl).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                BackupProgressTracker.finishDownload()
                return@withContext null
            }

            val body = response.body ?: run {
                BackupProgressTracker.finishDownload()
                return@withContext null
            }
            val contentLength = body.contentLength()

            val tempDownloadFile = File(cacheDir, "${targetFile.name}.downloading")
            var lastNotifTime = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempDownloadFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        BackupProgressTracker.reportBytesDownloaded(bytesRead.toLong())

                        if (contentLength > 0) {
                            val prog = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            BackupProgressTracker.updateDownloadProgress(prog)
                            onProgress(prog)

                            val now = System.currentTimeMillis()
                            if (now - lastNotifTime > 800) {
                                lastNotifTime = now
                                AppNotificationHelper.showDownloadActiveNotification(
                                    context = context,
                                    fileName = fileName,
                                    progressPercent = (prog * 100).toInt(),
                                    speedStr = BackupProgressTracker.formattedDownloadSpeed.value
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }

            // In-place decryption ONLY if the file has encryption header
            val prefs = AppPrefs(context)
            if (prefs.encryptionKey.isNotBlank() && FileEncryptor.isEncryptedFile(tempDownloadFile)) {
                try {
                    FileEncryptor.decryptFileInPlace(tempDownloadFile, prefs.encryptionKey)
                } catch (_: Exception) {}
            }

            BackupProgressTracker.finishDownload()
            AppCacheManager.autoPruneCache(context)

            val finalFile = if (tempDownloadFile.renameTo(targetFile)) targetFile else tempDownloadFile
            if (finalFile.exists() && finalFile.length() > 0) {
                AppNotificationHelper.showDownloadFinishedNotification(context, fileName, finalFile.absolutePath)
                return@withContext finalFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading cloud file $fileName", e)
            BackupProgressTracker.finishDownload()
            AppNotificationHelper.cancelDownloadNotification(context)
        }
        return@withContext null
    }

    /**
     * Resolves an image thumbnail bitmap directly for fast list/grid rendering.
     */
    suspend fun resolveImageBitmap(context: Context, telegramFileId: String, fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        if (telegramFileId.isBlank() || telegramFileId == "DIR_MARKER") return@withContext null

        bitmapMemoryCache.get(telegramFileId)?.let { return@withContext it }

        val cachedFile = fetchToTempCache(context, telegramFileId, fileName)
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(cachedFile.absolutePath, opts)

                val maxDim = 512
                var sampleSize = 1
                while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeFile(cachedFile.absolutePath, decodeOpts)
                if (bmp != null) {
                    bitmapMemoryCache.put(telegramFileId, bmp)
                    return@withContext bmp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error decoding bitmap for $fileName", e)
            }
        }
        return@withContext null
    }
}
