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

    suspend fun getCandidateBotTokens(context: Context): List<String> = withContext(Dispatchers.IO) {
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
    suspend fun resolveDirectUrl(context: Context, telegramFileId: String, preferredBotToken: String = ""): String? = withContext(Dispatchers.IO) {
        if (telegramFileId.isBlank() || telegramFileId == "DIR_MARKER" || telegramFileId.startsWith("NOTE_MARKER")) {
            return@withContext null
        }

        urlCache.get(telegramFileId)?.let { return@withContext it }

        val candidateTokens = mutableListOf<String>()
        if (preferredBotToken.isNotBlank()) {
            candidateTokens.add(preferredBotToken.trim())
        }
        for (t in getCandidateBotTokens(context)) {
            if (!candidateTokens.contains(t)) {
                candidateTokens.add(t)
            }
        }
        val client = HttpClientProvider.client

        for (botToken in candidateTokens) {
            try {
                val requestUrl = TelegramApiConfig.getMethodUrl(context, botToken, "getFile?file_id=$telegramFileId")
                val request = Request.Builder().url(requestUrl).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val filePath = json.optJSONObject("result")?.optString("file_path")
                    if (!filePath.isNullOrBlank()) {
                        val directUrl = TelegramApiConfig.getFileUrl(context, botToken, filePath)
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
    suspend fun resolveChunksForFile(context: Context, file: UploadedFile): List<Triple<Int, String, String>> = withContext(Dispatchers.IO) {
        val computedFromSize = if (file.size > FileChunker.CHUNK_SIZE) {
            ((file.size + FileChunker.CHUNK_SIZE - 1) / FileChunker.CHUNK_SIZE).toInt()
        } else 1

        val db = AppDatabase.getDatabase(context)
        val chunkDao = db.chunkProgressDao()
        val recorded = try { chunkDao.getUploadedChunksForFile(file.path) } catch (_: Exception) { emptyList() }
        val recordedMax = recorded.maxOfOrNull { it.chunkIndex } ?: 1
        val recordedTotal = recorded.firstOrNull { it.totalChunks > 1 }?.totalChunks ?: 1

        val targetTotalChunks = maxOf(file.totalChunks, computedFromSize, recordedMax, recordedTotal)

        // Only genuine 1-chunk small files without chunk records exit early
        if (targetTotalChunks <= 1 && recorded.isEmpty()) {
            return@withContext if (file.telegramFileId.isNotBlank()) listOf(Triple(1, file.telegramFileId, file.botTokenUsed)) else emptyList()
        }

        // If targetTotalChunks is greater than stored file.totalChunks, update the DB record so it stays multi-chunk
        if (file.totalChunks < targetTotalChunks) {
            file.totalChunks = targetTotalChunks
            try {
                db.uploadedFileDao().insert(file)
            } catch (_: Exception) {}
        }

        val chunkMap = mutableMapOf<Int, String>()
        val tokenMap = mutableMapOf<Int, String>()
        for (c in recorded) {
            if (c.telegramFileId.isNotBlank()) {
                chunkMap[c.chunkIndex] = c.telegramFileId
                if (c.botTokenUsed.isNotBlank()) {
                    tokenMap[c.chunkIndex] = c.botTokenUsed
                }
            }
        }

        // Part 1 is always available on the main uploaded_files record
        if (file.telegramFileId.isNotBlank() && !chunkMap.containsKey(1)) {
            chunkMap[1] = file.telegramFileId
            if (file.botTokenUsed.isNotBlank()) {
                tokenMap[1] = file.botTokenUsed
            }
        }

        // Fallback: If not all chunks were found by exact file.path, search by filename or end of path
        if (chunkMap.size < targetTotalChunks) {
            try {
                val allChunks = chunkDao.getAllChunks()
                val matches = allChunks.filter {
                    it.sourcePath.endsWith(file.fileName) ||
                    it.sourcePath.substringAfterLast('/').equals(file.fileName, ignoreCase = true) ||
                    it.sourcePath.equals(file.path, ignoreCase = true)
                }
                for (c in matches) {
                    if (c.telegramFileId.isNotBlank() && !chunkMap.containsKey(c.chunkIndex)) {
                        chunkMap[c.chunkIndex] = c.telegramFileId
                        if (c.botTokenUsed.isNotBlank()) {
                            tokenMap[c.chunkIndex] = c.botTokenUsed
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If some chunks are still missing from DB, query Telegram channel messages starting from file.messageId
        if (chunkMap.size < targetTotalChunks && file.messageId > 0L) {
            recoverMissingChunksFromTelegram(context, file, chunkMap, targetTotalChunks)
        }

        val finalTotal = maxOf(targetTotalChunks, chunkMap.keys.maxOrNull() ?: 1)
        val resolvedList = (1..finalTotal).mapNotNull { idx ->
            chunkMap[idx]?.let { Triple(idx, it, tokenMap[idx] ?: "") }
        }

        if (resolvedList.isNotEmpty()) {
            return@withContext resolvedList
        }

        return@withContext if (file.telegramFileId.isNotBlank()) listOf(Triple(1, file.telegramFileId, file.botTokenUsed)) else emptyList()
    }

    /**
     * Recovers missing chunk file IDs for older backups directly from Telegram channel messages.
     * Inserts any discovered chunks into Room DB so future resolutions are instant.
     */
    private suspend fun recoverMissingChunksFromTelegram(
        context: Context,
        file: UploadedFile,
        chunkMap: MutableMap<Int, String>,
        targetTotalChunks: Int = file.totalChunks
    ) = withContext(Dispatchers.IO) {
        val candidateTokens = getCandidateBotTokens(context)
        if (candidateTokens.isEmpty()) return@withContext

        val prefs = AppPrefs(context)
        val targetChatId = file.channelId.ifBlank { prefs.chatId }.trim()
        if (targetChatId.isBlank()) return@withContext

        val db = AppDatabase.getDatabase(context)
        val chunkDao = db.chunkProgressDao()
        val client = HttpClientProvider.client

        val effectiveTotal = maxOf(file.totalChunks, targetTotalChunks)
        val scanWindow = minOf(effectiveTotal * 4 + 10, 60)
        val startId = file.messageId + 1
        val endId = file.messageId + scanWindow

        for (mId in startId..endId) {
            if (chunkMap.size >= effectiveTotal) break

            val token = candidateTokens[((mId.toInt()) % candidateTokens.size).coerceAtLeast(0)]
            try {
                val forwardUrl = TelegramApiConfig.getMethodUrl(context, token, "forwardMessage")
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
                            val delUrl = TelegramApiConfig.getMethodUrl(context, token, "deleteMessage")
                            val delPayload = JSONObject().apply {
                                put("chat_id", targetChatId)
                                put("message_id", forwardedMsgId)
                            }
                            val delBody = delPayload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                            client.newCall(Request.Builder().url(delUrl).post(delBody).build()).execute().close()
                        } catch (_: Exception) {}
                    }

                    if (docFileId.isNotBlank()) {
                        for (partIdx in 2..effectiveTotal) {
                            if (chunkMap.containsKey(partIdx)) continue
                            val matchPattern = "[Part $partIdx/$effectiveTotal]"
                            val partPatternAlt = "[Part $partIdx/"
                            val partExt = ".part%03d".format(partIdx)
                            val isMatch = (caption.contains(partPatternAlt) && (caption.contains(file.fileName) || caption.contains(file.fileName.take(12)))) ||
                                          (docFileName.contains(partExt) && (docFileName.contains(file.fileName) || docFileName.contains(file.fileName.take(12))))

                            if (isMatch) {
                                chunkMap[partIdx] = docFileId
                                val entity = ChunkProgressEntity(
                                    sourcePath = file.path,
                                    chunkIndex = partIdx,
                                    totalChunks = effectiveTotal,
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

        if (file.totalChunks > 1 && chunks.size < file.totalChunks) {
            Log.e(TAG, "Cannot assemble ${file.fileName}: found only ${chunks.size} of ${file.totalChunks} chunks")
            return@withContext false
        }

        if (chunks.size == 1 && file.totalChunks <= 1) {
            // Single chunk file: direct stream
            val directUrl = resolveDirectUrl(context, chunks[0].second, chunks[0].third.ifBlank { file.botTokenUsed }) ?: return@withContext false
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
                val jobs = chunks.map { (chunkIdx, chunkFileId, chunkBotToken) ->
                    launch {
                        semaphore.withPermit {
                            if (downloadFailed) return@withPermit
                            val partFile = File(parentDir, "${targetFile.name}.part%03d.tmp".format(chunkIdx))
                            if (partFile.exists() && partFile.length() > 0) {
                                synchronized(partFiles) { partFiles.add(partFile) }
                                downloadedBytes.addAndGet(partFile.length())
                                return@withPermit
                            }

                            val directUrl = resolveDirectUrl(context, chunkFileId, chunkBotToken)
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

        // Decrypt in place using candidate keys (handles both modern and legacy cipherstreams)
        FileEncryptor.ensureDecrypted(tempMergedFile, context, file.fileName)

        if (targetFile.exists()) targetFile.delete()
        val success = tempMergedFile.renameTo(targetFile)
        if (!success && tempMergedFile.exists()) {
            tempMergedFile.copyTo(targetFile, overwrite = true)
            tempMergedFile.delete()
        }

        // Final safety check: ensure targetFile is confirmed decrypted
        if (targetFile.exists()) {
            FileEncryptor.ensureDecrypted(targetFile, context, file.fileName)
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
            if (!FileEncryptor.isLikelyPlainFile(targetFile, file.fileName)) {
                val ok = FileEncryptor.ensureDecrypted(targetFile, context, file.fileName)
                if (ok && FileEncryptor.isLikelyPlainFile(targetFile, file.fileName)) {
                    return@withContext targetFile
                } else {
                    // Stale, encrypted, or corrupted cache file: purge it to download clean from Telegram
                    Log.w(TAG, "Cached file ${targetFile.name} was corrupted or not plain, purging to re-download")
                    targetFile.delete()
                }
            } else {
                return@withContext targetFile
            }
        }

        try {
            BackupProgressTracker.startDownload(file.fileName)
            val ok = downloadAndAssembleFile(context, file, targetFile, onProgress)
            BackupProgressTracker.finishDownload()
            AppCacheManager.autoPruneCache(context)
            if (ok && targetFile.exists() && targetFile.length() > 0) {
                FileEncryptor.ensureDecrypted(targetFile, context, file.fileName)
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
            ?: db.uploadedFileDao().getFileByPath(telegramFileId)
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
            if (!FileEncryptor.isLikelyPlainFile(targetFile, fileName)) {
                val ok = FileEncryptor.ensureDecrypted(targetFile, context, fileName)
                if (ok && FileEncryptor.isLikelyPlainFile(targetFile, fileName)) {
                    return@withContext targetFile
                } else {
                    targetFile.delete()
                }
            } else {
                return@withContext targetFile
            }
        }

        val directUrl = resolveDirectUrl(context, telegramFileId) ?: return@withContext null
        val tempDownloadFile = File(cacheDir, "${targetFile.name}.downloading")
        try {
            BackupProgressTracker.startDownload(fileName)
            val ok = downloadUrlToFile(directUrl, tempDownloadFile, onProgress)
            if (ok && tempDownloadFile.exists() && tempDownloadFile.length() > 0) {
                FileEncryptor.ensureDecrypted(tempDownloadFile, context, fileName)
                if (tempDownloadFile.renameTo(targetFile) || (tempDownloadFile.exists() && run { tempDownloadFile.copyTo(targetFile, true); tempDownloadFile.delete(); true })) {
                    FileEncryptor.ensureDecrypted(targetFile, context, fileName)
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
        val ok = downloadAndAssembleFile(context, file, targetFile, onProgress)
        if (ok && targetFile.exists()) {
            FileEncryptor.ensureDecrypted(targetFile, context, file.fileName)
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }
        return@withContext ok
    }
}
