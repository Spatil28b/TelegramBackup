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
import com.example.telegrambackup.network.ServerApi
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
import java.io.BufferedOutputStream
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
        val fileDao = db.uploadedFileDao()
        val chunkDao = db.chunkProgressDao()

        // 0. Auto-hydrate stripped in-memory file objects from Room DB
        if (file.chunksJson.isBlank() || file.chunksJson == "[]" || file.totalChunks <= 1 || file.telegramFileId.isBlank()) {
            val dbMatch = try {
                fileDao.getFileByPath(file.path)
                    ?: fileDao.getFileByName(file.fileName)
            } catch (_: Exception) { null }

            if (dbMatch != null) {
                if (file.telegramFileId.isBlank()) file.telegramFileId = dbMatch.telegramFileId
                if ((file.chunksJson.isBlank() || file.chunksJson == "[]") && dbMatch.chunksJson.isNotBlank() && dbMatch.chunksJson != "[]") {
                    file.chunksJson = dbMatch.chunksJson
                }
                if (file.totalChunks <= 1 && dbMatch.totalChunks > 1) file.totalChunks = dbMatch.totalChunks
                if (file.channelId.isBlank()) file.channelId = dbMatch.channelId
                if (file.botTokenUsed.isBlank()) file.botTokenUsed = dbMatch.botTokenUsed
                if (file.messageId <= 0L) file.messageId = dbMatch.messageId
                if (file.size <= 0L && dbMatch.size > 0L) file.size = dbMatch.size
            }
        }

        val chunkMap = mutableMapOf<Int, String>()
        val tokenMap = mutableMapOf<Int, String>()

        // 1. First check chunksJson directly on the UploadedFile entity
        val fromJson = file.parseChunks()
        for (c in fromJson) {
            chunkMap[c.first] = c.second
            if (c.third.isNotBlank()) tokenMap[c.first] = c.third
        }

        // 2. Query chunk_upload_progress in Room DB
        val recorded = try { chunkDao.getUploadedChunksForFile(file.path) } catch (_: Exception) { emptyList() }
        for (c in recorded) {
            if (c.telegramFileId.isNotBlank()) {
                if (!chunkMap.containsKey(c.chunkIndex)) chunkMap[c.chunkIndex] = c.telegramFileId
                if (c.botTokenUsed.isNotBlank() && !tokenMap.containsKey(c.chunkIndex)) {
                    tokenMap[c.chunkIndex] = c.botTokenUsed
                }
            }
        }

        val recordedMax = recorded.maxOfOrNull { it.chunkIndex } ?: 1
        val recordedTotal = recorded.firstOrNull { it.totalChunks > 1 }?.totalChunks ?: 1
        val targetTotalChunks = maxOf(file.totalChunks, computedFromSize, recordedMax, recordedTotal, chunkMap.keys.maxOrNull() ?: 1)

        // Part 1 is always available on the main uploaded_files record
        if (file.telegramFileId.isNotBlank() && !chunkMap.containsKey(1)) {
            chunkMap[1] = file.telegramFileId
            if (file.botTokenUsed.isNotBlank()) {
                tokenMap[1] = file.botTokenUsed
            }
        }

        // Only genuine 1-chunk small files without chunk records exit early
        if (targetTotalChunks <= 1 && recorded.isEmpty() && fromJson.isEmpty()) {
            return@withContext if (file.telegramFileId.isNotBlank()) listOf(Triple(1, file.telegramFileId, file.botTokenUsed)) else emptyList()
        }

        // If all chunks are already resolved from JSON or DB, return immediately
        if (chunkMap.size >= targetTotalChunks) {
            val list = (1..targetTotalChunks).mapNotNull { idx ->
                chunkMap[idx]?.let { Triple(idx, it, tokenMap[idx] ?: file.botTokenUsed) }
            }
            if (list.size >= targetTotalChunks) {
                // Ensure file.chunksJson is in sync if it was empty
                if (file.chunksJson.isBlank() || file.chunksJson == "[]") {
                    file.setChunks(list)
                    try { db.uploadedFileDao().insert(file) } catch (_: Exception) {}
                }
                return@withContext list
            }
        }

        // 2b. Cloud API re-fetch: if local data is insufficient, pull fresh chunksJson from server
        if (chunkMap.size < targetTotalChunks) {
            try {
                val prefs = AppPrefs(context)
                val userEmail = prefs.userEmail
                if (userEmail.isNotBlank()) {
                    val cloudFiles = ServerApi().listFiles(prefs.serverUrl, userEmail)
                    val cloudMatch = cloudFiles.firstOrNull { it.path == file.path }
                        ?: cloudFiles.firstOrNull { it.fileName == file.fileName && it.size == file.size }
                    if (cloudMatch != null) {
                        val cloudChunks = cloudMatch.parseChunks()
                        if (cloudChunks.isNotEmpty()) {
                            Log.d(TAG, "Cloud re-fetch found ${cloudChunks.size} chunks for ${file.fileName}")
                            for (c in cloudChunks) {
                                if (!chunkMap.containsKey(c.first) && c.second.isNotBlank()) {
                                    chunkMap[c.first] = c.second
                                    if (c.third.isNotBlank() && !tokenMap.containsKey(c.first)) tokenMap[c.first] = c.third
                                }
                            }
                            // Persist to local DB for future use
                            if (file.chunksJson.isBlank() || file.chunksJson == "[]") {
                                file.chunksJson = cloudMatch.chunksJson
                                file.totalChunks = maxOf(file.totalChunks, cloudMatch.totalChunks)
                                try { db.uploadedFileDao().insert(file) } catch (_: Exception) {}
                            }
                            // Also populate chunk_upload_progress
                            for (c in cloudChunks) {
                                try {
                                    chunkDao.insert(ChunkProgressEntity(
                                        sourcePath = file.path, chunkIndex = c.first,
                                        totalChunks = maxOf(file.totalChunks, cloudChunks.size),
                                        telegramFileId = c.second, messageId = if (c.first == 1) file.messageId else 0L,
                                        chunkSize = 0L, status = "COMPLETED",
                                        botTokenUsed = c.third.ifBlank { file.botTokenUsed },
                                        channelId = file.channelId, uploadedAt = file.timestamp
                                    ))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud re-fetch for chunks failed: ${e.message}")
            }
        }

        // 3. Fallback: Search all chunks by matching filename or path in chunkDao
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
                        if (c.botTokenUsed.isNotBlank() && !tokenMap.containsKey(c.chunkIndex)) {
                            tokenMap[c.chunkIndex] = c.botTokenUsed
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. If some chunks are still missing, recover directly from Telegram channel
        if (chunkMap.size < targetTotalChunks) {
            recoverMissingChunksFromTelegram(context, file, chunkMap, tokenMap, targetTotalChunks)
        }

        val finalTotal = maxOf(targetTotalChunks, chunkMap.keys.maxOrNull() ?: 1)
        val resolvedList = (1..finalTotal).mapNotNull { idx ->
            chunkMap[idx]?.let { Triple(idx, it, tokenMap[idx] ?: file.botTokenUsed) }
        }

        // Integrity guard: if file requires multiple chunks, DO NOT return an incomplete partial list
        if (targetTotalChunks > 1) {
            if (resolvedList.size >= targetTotalChunks && (1..targetTotalChunks).all { chunkMap.containsKey(it) }) {
                if (file.chunksJson.isBlank() || file.chunksJson == "[]" || file.totalChunks < targetTotalChunks) {
                    file.setChunks(resolvedList)
                    file.totalChunks = targetTotalChunks
                    try { db.uploadedFileDao().insert(file) } catch (_: Exception) {}
                }
                return@withContext resolvedList
            } else {
                Log.w(TAG, "File ${file.fileName} requires $targetTotalChunks chunks, but only ${resolvedList.size} were resolved. Refusing to return truncated chunk list.")
                return@withContext emptyList()
            }
        }

        if (resolvedList.isNotEmpty()) {
            if (resolvedList.size >= finalTotal && (file.chunksJson.isBlank() || file.chunksJson == "[]")) {
                file.setChunks(resolvedList)
                try { db.uploadedFileDao().insert(file) } catch (_: Exception) {}
            }
            return@withContext resolvedList
        }

        return@withContext if (file.telegramFileId.isNotBlank()) listOf(Triple(1, file.telegramFileId, file.botTokenUsed)) else emptyList()
    }

    /**
     * Recovers missing chunk file IDs for older backups directly from Telegram channel messages.
     * Decrypts AES-256 encrypted captions to identify parts, and scans both backwards and forwards
     * relative to message ID to catch concurrently dispatched chunks.
     * Inserts any discovered chunks into Room DB and updates the cloud index.
     */
    private suspend fun recoverMissingChunksFromTelegram(
        context: Context,
        file: UploadedFile,
        chunkMap: MutableMap<Int, String>,
        tokenMap: MutableMap<Int, String>,
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

        // Find reference message ID
        var baseMsgId = file.messageId
        if (baseMsgId <= 0L) {
            val otherMsgId = try {
                db.uploadedFileDao().getAllFiles()
                    .filter { it.channelId == targetChatId && it.messageId > 0L }
                    .maxOfOrNull { it.messageId } ?: 0L
            } catch (_: Exception) { 0L }
            if (otherMsgId > 0L) {
                baseMsgId = otherMsgId
            }
        }

        if (baseMsgId <= 0L) return@withContext

        val scanRadius = minOf(effectiveTotal * 8 + 20, 80)
        val startId = (baseMsgId - scanRadius).coerceAtLeast(1L)
        val endId = baseMsgId + scanRadius

        // Priority scan order: closest to baseMsgId first
        val scanIds = (1..scanRadius).flatMap { offset ->
            listOf(baseMsgId - offset, baseMsgId + offset)
        }.filter { it in startId..endId }

        val candidateKeys = FileEncryptor.getCandidateKeys(context)

        for (mId in scanIds) {
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
                    val rawCaption = resultObj?.optString("caption") ?: ""

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
                        // Decrypt encrypted caption if needed
                        var caption = rawCaption
                        if (rawCaption.contains("ENC:")) {
                            val encBody = rawCaption.substringAfter("ENC:").trim()
                            for (key in candidateKeys) {
                                try {
                                    val dec = FileEncryptor.decryptText(encBody, key)
                                    if (dec.isNotBlank() && (dec.contains("[Part") || dec.contains(file.fileName))) {
                                        caption = dec
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        val partRegex = Regex("\\[Part\\s*(\\d+)/(\\d+)\\]")
                        val match = partRegex.find(caption)
                        val partIdx = match?.groupValues?.get(1)?.toIntOrNull()
                        val captionTotal = match?.groupValues?.get(2)?.toIntOrNull()

                        val isTargetFile = caption.contains(file.fileName) ||
                                          caption.contains(file.fileName.take(15)) ||
                                          (docFileName.isNotBlank() && (docFileName.contains(file.fileName) || docFileName.contains(file.fileName.take(15))))

                        if (isTargetFile && partIdx != null && partIdx in 1..effectiveTotal) {
                            chunkMap[partIdx] = docFileId
                            tokenMap[partIdx] = token

                            val entity = ChunkProgressEntity(
                                sourcePath = file.path,
                                chunkIndex = partIdx,
                                totalChunks = captionTotal ?: effectiveTotal,
                                telegramFileId = docFileId,
                                messageId = mId,
                                chunkSize = docObj?.optLong("file_size") ?: 0L,
                                status = "COMPLETED",
                                botTokenUsed = token,
                                channelId = targetChatId,
                                uploadedAt = System.currentTimeMillis()
                            )
                            chunkDao.insert(entity)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If recovery found all chunks, sync to uploadedFile and push to cloud index
        if (chunkMap.size >= effectiveTotal) {
            val chunkList = (1..effectiveTotal).mapNotNull { idx ->
                chunkMap[idx]?.let { Triple(idx, it, tokenMap[idx] ?: file.botTokenUsed) }
            }
            file.setChunks(chunkList)
            file.totalChunks = effectiveTotal
            try {
                db.uploadedFileDao().insert(file)
                val userEmail = prefs.userEmail
                if (userEmail.isNotBlank()) {
                    ServerApi().indexFile(
                        serverUrl = prefs.serverUrl,
                        email = userEmail,
                        fileName = file.fileName,
                        telegramFileId = chunkMap[1] ?: file.telegramFileId,
                        size = file.size,
                        path = file.path,
                        timestamp = file.timestamp,
                        messageId = file.messageId,
                        isDriveUpload = file.isDriveUpload,
                        chunksJson = file.chunksJson,
                        channelId = targetChatId,
                        botTokenUsed = tokenMap[1] ?: file.botTokenUsed,
                        totalChunks = effectiveTotal
                    )
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

        val computedChunks = if (file.size > FileChunker.CHUNK_SIZE) {
            ((file.size + FileChunker.CHUNK_SIZE - 1) / FileChunker.CHUNK_SIZE).toInt()
        } else 1
        val targetTotalChunks = maxOf(file.totalChunks, computedChunks)

        var chunks = resolveChunksForFile(context, file)
        if (chunks.isEmpty()) return@withContext false

        // If still missing chunks, abort immediately instead of downloading partial file
        if (targetTotalChunks > 1 && (chunks.size < targetTotalChunks || (1..targetTotalChunks).any { idx -> chunks.none { it.first == idx } })) {
            Log.e(TAG, "Cannot download ${file.fileName}: required $targetTotalChunks chunks, but only ${chunks.size} are available.")
            // Single-stream fallback ONLY if Telegram local server or file is genuinely small enough
            if (chunks.size == 1 && chunks[0].first == 1) {
                val directUrl = resolveDirectUrl(context, chunks[0].second, chunks[0].third.ifBlank { file.botTokenUsed })
                if (directUrl != null) {
                    val ok = downloadUrlToFile(directUrl, tempMergedFile, onProgress)
                    if (ok && tempMergedFile.exists() && tempMergedFile.length() > 0L) {
                        if (file.size <= 0L || tempMergedFile.length() >= (file.size * 0.90).toLong()) {
                            FileEncryptor.ensureDecrypted(tempMergedFile, context, file.fileName)
                            if (targetFile.exists()) targetFile.delete()
                            val success = tempMergedFile.renameTo(targetFile)
                            if (!success && tempMergedFile.exists()) {
                                tempMergedFile.copyTo(targetFile, overwrite = true)
                                tempMergedFile.delete()
                            }
                            val finalized = verifyAndFinalizeDownload(context, targetFile, file)
                            if (finalized) {
                                onProgress(1f)
                                return@withContext true
                            }
                        }
                    }
                    if (tempMergedFile.exists()) tempMergedFile.delete()
                }
            }
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
            val partFiles = mutableListOf<Pair<Int, File>>()
            val chunkCount = chunks.size
            val downloadedBytes = AtomicLong(0L)
            val totalEstimatedSize = if (file.size > 0L) file.size else chunkCount.toLong() * FileChunker.CHUNK_SIZE
            val botCount = maxOf(candidateTokens.size, 1)
            val userThreads = AppPrefs(context).uploadThreads
            val concurrency = maxOf(6, minOf(20, maxOf(userThreads, botCount * 4)))
            val semaphore = Semaphore(concurrency)

            var downloadFailed = false
            coroutineScope {
                val jobs = chunks.map { (chunkIdx, chunkFileId, chunkBotToken) ->
                    launch {
                        semaphore.withPermit {
                            if (downloadFailed) return@withPermit
                            val partFile = File(parentDir, "${targetFile.name}.part%03d.tmp".format(chunkIdx))
                            if (partFile.exists() && partFile.length() > 0) {
                                synchronized(partFiles) { partFiles.add(Pair(chunkIdx, partFile)) }
                                downloadedBytes.addAndGet(partFile.length())
                                return@withPermit
                            }

                            // Retry each chunk up to 3 times with exponential backoff
                            var chunkOk = false
                            for (attempt in 1..3) {
                                if (downloadFailed) break
                                val directUrl = resolveDirectUrl(context, chunkFileId, chunkBotToken)
                                if (directUrl == null) {
                                    if (attempt == 3) { downloadFailed = true; break }
                                    kotlinx.coroutines.delay(attempt * 2000L)
                                    continue
                                }

                                val ok = downloadUrlToFile(directUrl, partFile) { _ ->
                                    val totalProg = (downloadedBytes.get().toFloat() / totalEstimatedSize.toFloat()).coerceIn(0f, 0.98f)
                                    onProgress(totalProg)
                                }
                                if (ok && partFile.exists() && partFile.length() > 0) {
                                    synchronized(partFiles) { partFiles.add(Pair(chunkIdx, partFile)) }
                                    downloadedBytes.addAndGet(partFile.length())
                                    chunkOk = true
                                    break
                                } else {
                                    if (partFile.exists()) partFile.delete()
                                    if (attempt < 3) {
                                        Log.w(TAG, "Chunk $chunkIdx download attempt $attempt failed, retrying in ${attempt * 2}s")
                                        kotlinx.coroutines.delay(attempt * 2000L)
                                    }
                                }
                            }
                            if (!chunkOk) downloadFailed = true
                        }
                    }
                }
                jobs.joinAll()
            }

            if (downloadFailed || partFiles.size < chunkCount || (targetTotalChunks > 1 && partFiles.size < targetTotalChunks)) {
                partFiles.forEach { if (it.second.exists()) it.second.delete() }
                if (tempMergedFile.exists()) tempMergedFile.delete()
                return@withContext false
            }

            // Merge all downloaded chunks in strict sequential chunkIndex order
            partFiles.sortBy { it.first }
            val orderedFiles = partFiles.map { it.second }
            FileChunker.mergeChunks(orderedFiles, tempMergedFile)
            orderedFiles.forEach { if (it.exists()) it.delete() }

            if (!tempMergedFile.exists() || tempMergedFile.length() == 0L) {
                return@withContext false
            }
        }

        // Decrypt in place using candidate keys (handles both modern and legacy cipherstreams)
        // Decrypt in place using candidate keys (handles both modern and legacy cipherstreams)
        FileEncryptor.ensureDecrypted(tempMergedFile, context, file.fileName)

        if (targetFile.exists()) targetFile.delete()
        val success = tempMergedFile.renameTo(targetFile)
        if (!success && tempMergedFile.exists()) {
            tempMergedFile.copyTo(targetFile, overwrite = true)
            tempMergedFile.delete()
        }

        val finalized = verifyAndFinalizeDownload(context, targetFile, file)
        if (!finalized) return@withContext false

        onProgress(1f)
        return@withContext true
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
                val ok = verifyAndFinalizeDownload(context, targetFile, file)
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
                val verified = verifyAndFinalizeDownload(context, targetFile, file)
                if (verified) {
                    AppNotificationHelper.showDownloadFinishedNotification(context, file.fileName, targetFile.absolutePath)
                    return@withContext targetFile
                } else {
                    targetFile.delete()
                    return@withContext null
                }
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
        var fileRecord = db.uploadedFileDao().getFileByTelegramFileId(telegramFileId)
            ?: db.uploadedFileDao().getFileByPath(telegramFileId)
            ?: db.uploadedFileDao().getFileByName(fileName)
            ?: run {
                try {
                    db.uploadedFileDao().getAllFiles().firstOrNull {
                        it.fileName.equals(fileName, ignoreCase = true) || it.telegramFileId == telegramFileId
                    }
                } catch (_: Exception) { null }
            }

        // Check cloud if not found locally
        if (fileRecord == null) {
            try {
                val prefs = AppPrefs(context)
                if (prefs.userEmail.isNotBlank()) {
                    val cloudFiles = ServerApi().listFiles(prefs.serverUrl, prefs.userEmail)
                    fileRecord = cloudFiles.firstOrNull { it.telegramFileId == telegramFileId }
                        ?: cloudFiles.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
                    if (fileRecord != null) {
                        try { db.uploadedFileDao().insert(fileRecord) } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud file lookup for $fileName failed: ${e.message}")
            }
        }

        if (fileRecord != null) {
            return@withContext fetchToTempCache(context, fileRecord, onProgress)
        }

        // Standalone fallback: construct UploadedFile and route through full multi-chunk assembly pipeline
        val pseudoFile = UploadedFile(
            path = fileName,
            fileName = fileName,
            telegramFileId = telegramFileId,
            timestamp = System.currentTimeMillis(),
            size = 0L,
            messageId = 0L
        )
        return@withContext fetchToTempCache(context, pseudoFile, onProgress)
    }

    private fun downloadUrlToFile(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        // Use dedicated downloadClient with high concurrency (maxRequestsPerHost = 32)
        // and 15-minute read timeout to support ultra-fast parallel chunk streaming
        val client = HttpClientProvider.downloadClient
        val request = Request.Builder().url(url).get().build()
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                BufferedOutputStream(FileOutputStream(targetFile), 128 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgressTime = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        BackupProgressTracker.reportBytesDownloaded(bytesRead.toLong())
                        if (contentLength > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime > 150L || totalRead == contentLength) {
                                lastProgressTime = now
                                val prog = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                onProgress(prog)
                            }
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
            } catch (t: Throwable) {
                Log.w(TAG, "Error decoding bitmap for $fileName", t)
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
        // Fast-path 1: Check if already present in preview cache
        val cacheDir = File(context.cacheDir, "cloud_preview_cache")
        val safeName = file.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
        val cachedPreview = File(cacheDir, "${file.telegramFileId.hashCode()}_$safeName")
        if (cachedPreview.exists() && cachedPreview.length() > 0 && FileEncryptor.isLikelyPlainFile(cachedPreview, file.fileName)) {
            try {
                if (targetFile.exists()) targetFile.delete()
                val parent = targetFile.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                cachedPreview.copyTo(targetFile, overwrite = true)
                if (targetFile.exists() && targetFile.length() > 0) {
                    onProgress(1f)
                    try {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            null,
                            null
                        )
                    } catch (_: Exception) {}
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path copy from cache failed, falling back to download: ${e.message}")
            }
        }

        // Fast-path 2: Check if original local file exists on device storage
        val localOriginal = FileViewerHelper.findLocalFile(file.path, file.fileName)
        if (localOriginal != null && localOriginal.exists() && localOriginal.length() > 0 && localOriginal.absolutePath != targetFile.absolutePath) {
            try {
                if (targetFile.exists()) targetFile.delete()
                val parent = targetFile.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                localOriginal.copyTo(targetFile, overwrite = true)
                FileEncryptor.ensureDecrypted(targetFile, context, file.fileName)
                if (targetFile.exists() && targetFile.length() > 0 && FileEncryptor.isLikelyPlainFile(targetFile, file.fileName)) {
                    onProgress(1f)
                    try {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            null,
                            null
                        )
                    } catch (_: Exception) {}
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path copy from local file failed, falling back to download: ${e.message}")
            }
        }

        val ok = downloadAndAssembleFile(context, file, targetFile, onProgress)
        if (ok && targetFile.exists()) {
            val verified = verifyAndFinalizeDownload(context, targetFile, file)
            if (!verified) return@withContext false
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    null,
                    null
                )
            } catch (_: Exception) {}
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Deeply verifies that a downloaded or restored file is fully decrypted and valid.
     * Prevents delivering raw encrypted ciphertext or corrupt files to user or system.
     */
    fun verifyAndFinalizeDownload(
        context: Context,
        targetFile: File,
        file: UploadedFile
    ): Boolean {
        if (!targetFile.exists() || targetFile.length() == 0L) return false

        // Ensure decrypted with all candidate keys and fallback cloud key
        FileEncryptor.ensureDecrypted(targetFile, context, file.fileName)

        var isStillEncrypted = FileEncryptor.isEncryptedFile(targetFile, file.fileName) || FileEncryptor.isEncryptedWithMagicHeader(targetFile)
        if (isStillEncrypted) {
            // Force fetch cloud key and attempt recovery
            val prefs = AppPrefs(context)
            val userEmail = prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()
            if (userEmail.isNotBlank()) {
                try {
                    val serverSettings = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        com.example.telegrambackup.network.ServerApi().getUserSettings(prefs.serverUrl, userEmail)
                    }
                    if (!serverSettings.isNullOrBlank() && serverSettings.contains("encryption_key")) {
                        val cloudKey = org.json.JSONObject(serverSettings).optJSONObject("security")?.optString("encryption_key", "")
                        if (!cloudKey.isNullOrBlank()) {
                            FileEncryptor.decryptFileInPlace(targetFile, cloudKey)
                            prefs.forceSetEncryptionKey(cloudKey)
                            isStillEncrypted = FileEncryptor.isEncryptedFile(targetFile, file.fileName) || FileEncryptor.isEncryptedWithMagicHeader(targetFile)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        if (isStillEncrypted) {
            Log.e(TAG, "FATAL: Downloaded file ${file.fileName} remains encrypted after all decryption attempts! Purging unreadable ciphertext.")
            targetFile.delete()
            return false
        }

        val ext = file.fileName.substringAfterLast('.', "").lowercase()
        if (ext == "apk" || ext == "zip") {
            if (!FileEncryptor.isLikelyPlainFile(targetFile, file.fileName)) {
                Log.e(TAG, "FATAL: $ext file ${file.fileName} lacks valid ZIP/APK magic header (PK\\x03\\x04). Purging corrupt download.")
                targetFile.delete()
                return false
            }
        }

        if (file.size > 0L) {
            val actualSize = targetFile.length()
            val minAcceptable = (file.size * 0.90).toLong()
            if (actualSize < minAcceptable) {
                Log.e(TAG, "Download integrity check failed for ${file.fileName}: got $actualSize bytes, expected at least $minAcceptable bytes. Purging corrupt download.")
                targetFile.delete()
                return false
            }
        }

        return true
    }
}
