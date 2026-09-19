package com.example.telegrambackup.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.ChunkProgressEntity
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.BotFleetManager
import com.example.telegrambackup.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance Media & File Resolver for Telegram Cloud items.
 * Handles instant direct URL resolution, thumbnail resolution, multi-chunk parallel assembly,
 * and transparent on-demand caching/decryption for images, videos, audio, PDFs, and documents.
 */
object TelegramMediaResolver {
    private const val TAG = "TelegramMediaResolver"
    private val urlCache = LruCache<String, String>(500)
    private val bitmapMemoryCache = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private suspend fun getCandidateBotTokens(context: Context): List<String> = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val tokens = mutableListOf<String>()
        if (prefs.botToken.isNotBlank()) tokens.add(prefs.botToken.trim())

        try {
            val bots = BotFleetManager.getBotPoolForParallelChunks(context, 16)
            for (b in bots) {
                val t = b.botToken.trim()
                if (t.isNotBlank() && !tokens.contains(t)) {
                    tokens.add(t)
                }
            }
        } catch (_: Exception) {}

        try {
            val dbBots = AppDatabase.getDatabase(context).telegramBotDao().getActiveBots()
            for (b in dbBots) {
                val t = b.botToken.trim()
                if (t.isNotBlank() && !tokens.contains(t)) {
                    tokens.add(t)
                }
            }
        } catch (_: Exception) {}

        tokens
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
     * Resolves all sequential chunk parts (chunkIndex -> telegramFileId) for a multi-chunk file.
     * Uses local Room DB records first. If older records were pruned, scans Telegram channel messages
     * to reconstruct the complete multi-part list and caches them permanently in Room DB.
     */
    suspend fun resolveChunksForFile(context: Context, file: UploadedFile): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        if (file.totalChunks <= 1) {
            return@withContext if (file.telegramFileId.isNotBlank()) listOf(Pair(1, file.telegramFileId)) else emptyList()
        }

        val db = AppDatabase.getDatabase(context)
        val chunkDao = db.chunkProgressDao()
        val recorded = chunkDao.getUploadedChunksForFile(file.path)
        val chunkMap = mutableMapOf<Int, String>()
        for (c in recorded) {
            if (c.telegramFileId.isNotBlank()) {
                chunkMap[c.chunkIndex] = c.telegramFileId
            }
        }

        // Part 1 is always available on the main uploaded_files record
        if (file.telegramFileId.isNotBlank() && !chunkMap.containsKey(1)) {
            chunkMap[1] = file.telegramFileId
        }

        // If all chunks are present in Room DB, return them sorted
        if (chunkMap.size >= file.totalChunks) {
            return@withContext (1..file.totalChunks).mapNotNull { idx -> chunkMap[idx]?.let { Pair(idx, it) } }
        }

        // If some chunks are missing from DB, query Telegram channel messages starting from file.messageId
        if (file.messageId > 0L) {
            recoverMissingChunksFromTelegram(context, file, chunkMap)
        }

        return@withContext (1..file.totalChunks).mapNotNull { idx -> chunkMap[idx]?.let { Pair(idx, it) } }
    }

    /**
     * Recovers missing chunk file IDs for older backups directly from Telegram channel messages.
     * Inserts any discovered chunks into Room DB so future resolutions are instant.
     */
    private suspend fun recoverMissingChunksFromTelegram(
        context: Context,
        file: UploadedFile,
        chunkMap: MutableMap<Int, String>
    ) = withContext(Dispatchers.IO) {
        val candidateTokens = getCandidateBotTokens(context)
        if (candidateTokens.isEmpty()) return@withContext

        val prefs = AppPrefs(context)
        val targetChatId = file.channelId.ifBlank { prefs.chatId }.trim()
        if (targetChatId.isBlank()) return@withContext

        val db = AppDatabase.getDatabase(context)
        val chunkDao = db.chunkProgressDao()
        val client = HttpClientProvider.client

        val scanWindow = minOf(file.totalChunks * 4 + 10, 50)
        val startId = file.messageId + 1
        val endId = file.messageId + scanWindow

        for (mId in startId..endId) {
            if (chunkMap.size >= file.totalChunks) break

            val token = candidateTokens[((mId.toInt()) % candidateTokens.size).coerceAtLeast(0)]
            try {
                val forwardUrl = "https://api.telegram.org/bot$token/forwardMessage"
                val payload = JSONObject().apply {
                    put("chat_id", targetChatId)
                    put("from_chat_id", targetChatId)
                    put("message_id", mId)
                    put("disable_notification", true)
                }
                val reqBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder().url(forwardUrl).post(reqBody).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string() ?: continue
                if (!res.isSuccessful) continue

                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    val resultObj = json.optJSONObject("result")
                    val forwardedMsgId = resultObj?.optLong("message_id") ?: 0L
                    val docObj = resultObj?.optJSONObject("document")
                    val docFileId = docObj?.optString("file_id") ?: ""
                    val docFileName = docObj?.optString("file_name") ?: ""
                    val caption = resultObj?.optString("caption") ?: ""

                    // Clean up the forwarded probe message immediately
                    if (forwardedMsgId > 0L) {
                        try {
                            val delUrl = "https://api.telegram.org/bot$token/deleteMessage"
                            val delPayload = JSONObject().apply {
                                put("chat_id", targetChatId)
                                put("message_id", forwardedMsgId)
                            }
                            val delBody = delPayload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                            client.newCall(Request.Builder().url(delUrl).post(delBody).build()).execute().close()
                        } catch (_: Exception) {}
                    }

                    if (docFileId.isNotBlank()) {
                        for (partIdx in 2..file.totalChunks) {
                            if (chunkMap.containsKey(partIdx)) continue
                            val matchPattern = "[Part $partIdx/${file.totalChunks}]"
                            val partExt = ".part%03d".format(partIdx)
                            val isMatch = (caption.contains(matchPattern) && (caption.contains(file.fileName) || caption.contains(file.fileName.take(12)))) ||
                                          (docFileName.contains(partExt) && (docFileName.contains(file.fileName) || docFileName.contains(file.fileName.take(12))))

                            if (isMatch) {
                                chunkMap[partIdx] = docFileId
                                val entity = ChunkProgressEntity(
                                    sourcePath = file.path,
                                    chunkIndex = partIdx,
                                    totalChunks = file.totalChunks,
                                    telegramFileId = docFileId,
                                    messageId = mId,
                                    chunkSize = docObj?.optLong("file_size") ?: 0L,
                                    status = "COMPLETED",
                                    botTokenUsed = token,
                                    channelId = targetChatId,
                                    uploadedAt = System.currentTimeMillis()
                                )
                                chunkDao.insert(entity)
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Downloads an entire file (including parallel multi-chunk assembly if chunked)
     * and performs AES-256 decryption in place if encrypted.
     */
    suspend fun downloadAndAssembleFile(
        context: Context,
        file: UploadedFile,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val parentDir = targetFile.parentFile ?: return@withContext false
        if (!parentDir.exists()) parentDir.mkdirs()

        val tempMergedFile = File(parentDir, "${targetFile.name}.downloading")
        val candidateTokens = getCandidateBotTokens(context)
        if (candidateTokens.isEmpty()) return@withContext false

        val chunks = resolveChunksForFile(context, file)
        if (chunks.isEmpty()) return@withContext false

        if (chunks.size == 1 && file.totalChunks <= 1) {
            // Single chunk file: direct stream
            val directUrl = resolveDirectUrl(context, chunks[0].second) ?: return@withContext false
            val ok = downloadUrlToFile(directUrl, tempMergedFile, onProgress)
            if (!ok || !tempMergedFile.exists() || tempMergedFile.length() == 0L) {
                if (tempMergedFile.exists()) tempMergedFile.delete()
                return@withContext false
            }
        } else {
            // Multi-part chunked file: parallel multi-bot fleet download
            val partFiles = mutableListOf<File>()
            val chunkCount = chunks.size
            val downloadedBytes = AtomicLong(0L)
            val totalEstimatedSize = if (file.size > 0L) file.size else chunkCount.toLong() * FileChunker.CHUNK_SIZE
            val concurrency = minOf(candidateTokens.size, maxOf(AppPrefs(context).uploadThreads, 16))
            val semaphore = Semaphore(concurrency)

            var downloadFailed = false
            coroutineScope {
                val jobs = chunks.map { (chunkIdx, chunkFileId) ->
                    launch {
                        semaphore.withPermit {
                            if (downloadFailed) return@withPermit
                            val partFile = File(parentDir, "${targetFile.name}.part%03d.tmp".format(chunkIdx))
                            if (partFile.exists() && partFile.length() > 0) {
                                synchronized(partFiles) { partFiles.add(partFile) }
                                downloadedBytes.addAndGet(partFile.length())
                                return@withPermit
                            }

                            val directUrl = resolveDirectUrl(context, chunkFileId)
                            if (directUrl == null) {
                                downloadFailed = true
                                return@withPermit
                            }

                            val ok = downloadUrlToFile(directUrl, partFile) { _ ->
                                val totalProg = (downloadedBytes.get().toFloat() / totalEstimatedSize.toFloat()).coerceIn(0f, 0.98f)
                                onProgress(totalProg)
                            }
                            if (ok && partFile.exists() && partFile.length() > 0) {
                                synchronized(partFiles) { partFiles.add(partFile) }
                                downloadedBytes.addAndGet(partFile.length())
                            } else {
                                downloadFailed = true
                            }
                        }
                    }
                }
                jobs.joinAll()
            }

            if (downloadFailed || partFiles.size < chunkCount) {
                partFiles.forEach { if (it.exists()) it.delete() }
                if (tempMergedFile.exists()) tempMergedFile.delete()
                return@withContext false
            }

            // Merge all downloaded chunks in sequential order
            partFiles.sortBy { it.name }
            FileChunker.mergeChunks(partFiles, tempMergedFile)
            partFiles.forEach { if (it.exists()) it.delete() }

            if (!tempMergedFile.exists() || tempMergedFile.length() == 0L) {
                return@withContext false
            }
        }

        // Decrypt in place ONLY if encrypted
        val prefs = AppPrefs(context)
        if (prefs.encryptionKey.isNotBlank() && FileEncryptor.isEncryptedFile(tempMergedFile)) {
            try {
                FileEncryptor.decryptFileInPlace(tempMergedFile, prefs.encryptionKey)
            } catch (e: Exception) {
                Log.w(TAG, "Decryption error for ${file.fileName}", e)
            }
        }

        if (targetFile.exists()) targetFile.delete()
        val success = tempMergedFile.renameTo(targetFile)
        if (!success && tempMergedFile.exists()) {
            tempMergedFile.copyTo(targetFile, overwrite = true)
            tempMergedFile.delete()
        }

        onProgress(1f)
        return@withContext targetFile.exists() && targetFile.length() > 0L
    }

    /**
     * Downloads and caches a cloud file into a local temporary cache directory.
     * Overloaded for UploadedFile entity with full multi-chunk awareness.
     */
    suspend fun fetchToTempCache(
        context: Context,
        file: UploadedFile,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "cloud_preview_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeName = file.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
        val targetFile = File(cacheDir, "${file.telegramFileId.hashCode()}_$safeName")

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        try {
            BackupProgressTracker.startDownload(file.fileName)
            val ok = downloadAndAssembleFile(context, file, targetFile, onProgress)
            BackupProgressTracker.finishDownload()
            AppCacheManager.autoPruneCache(context)
            if (ok && targetFile.exists() && targetFile.length() > 0) {
                AppNotificationHelper.showDownloadFinishedNotification(context, file.fileName, targetFile.absolutePath)
                return@withContext targetFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching ${file.fileName} to cache", e)
            BackupProgressTracker.finishDownload()
            AppNotificationHelper.cancelDownloadNotification(context)
        }
        return@withContext null
    }

    /**
     * Downloads and caches a cloud file into a local temporary cache directory.
     * Resolves the UploadedFile entity from Room DB to ensure multi-chunk files are merged.
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

        val db = AppDatabase.getDatabase(context)
        val fileRecord = db.uploadedFileDao().getFileByTelegramFileId(telegramFileId)
            ?: db.uploadedFileDao().getFileByName(fileName)

        if (fileRecord != null) {
            return@withContext fetchToTempCache(context, fileRecord, onProgress)
        }

        // Fallback for standalone single file_id without database record
        val cacheDir = File(context.cacheDir, "cloud_preview_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
        val targetFile = File(cacheDir, "${telegramFileId.hashCode()}_$safeName")
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        val directUrl = resolveDirectUrl(context, telegramFileId) ?: return@withContext null
        val tempDownloadFile = File(cacheDir, "${targetFile.name}.downloading")
        try {
            BackupProgressTracker.startDownload(fileName)
            val ok = downloadUrlToFile(directUrl, tempDownloadFile, onProgress)
            if (ok && tempDownloadFile.exists() && tempDownloadFile.length() > 0) {
                val prefs = AppPrefs(context)
                if (prefs.encryptionKey.isNotBlank() && FileEncryptor.isEncryptedFile(tempDownloadFile)) {
                    try { FileEncryptor.decryptFileInPlace(tempDownloadFile, prefs.encryptionKey) } catch (_: Exception) {}
                }
                if (tempDownloadFile.renameTo(targetFile)) {
                    BackupProgressTracker.finishDownload()
                    return@withContext targetFile
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $fileName", e)
        } finally {
            BackupProgressTracker.finishDownload()
        }
        return@withContext null
    }

    private fun downloadUrlToFile(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val client = HttpClientProvider.client
        val request = Request.Builder().url(url).get().build()
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        BackupProgressTracker.reportBytesDownloaded(bytesRead.toLong())
                        if (contentLength > 0) {
                            val prog = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            onProgress(prog)
                        }
                    }
                    output.flush()
                }
            }
            targetFile.exists() && targetFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading stream from $url", e)
            false
        }
    }

    /**
     * Resolves an image thumbnail bitmap directly for fast list/grid rendering.
     * Uses the multi-chunk and decrypted cached file so previews render flawlessly.
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

    /**
     * Dedicated restore function for CloudRestoreWizardDialog.
     * Restores the complete, assembled, and decrypted file to the destination folder.
     */
    suspend fun restoreFileToTarget(
        context: Context,
        file: UploadedFile,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext downloadAndAssembleFile(context, file, targetFile, onProgress)
    }
}
