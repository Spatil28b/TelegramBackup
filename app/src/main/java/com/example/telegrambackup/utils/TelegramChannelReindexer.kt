package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.HttpClientProvider
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * =====================================================================================
 * ZERO-DEPENDENCY TELEGRAM CHANNEL DEEP RE-INDEXER
 * =====================================================================================
 * Emergency disaster recovery engine that rebuilds the complete Drive & Backup catalog
 * directly from the user's private Telegram channel without requiring our central server
 * or old device databases.
 * =====================================================================================
 */
object TelegramChannelReindexer {

    private const val TAG = "TelegramReindexer"

    data class ReindexProgress(
        val scannedCount: Int = 0,
        val foundFilesCount: Int = 0,
        val currentMessageId: Long = 0L,
        val statusMessage: String = "",
        val isCompleted: Boolean = false,
        val error: String? = null
    )

    /**
     * Traverses the Telegram storage channel/chat message history, parses all attachments,
     * reconstructs file metadata records, and inserts them directly into Room DB and PostgreSQL.
     */
    suspend fun deepReindexChannel(
        context: Context,
        botToken: String,
        chatId: String,
        onProgress: (ReindexProgress) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            onProgress(ReindexProgress(statusMessage = "Bot token or Channel ID missing.", error = "Credentials missing", isCompleted = true))
            return@withContext 0
        }

        val db = AppDatabase.getDatabase(context)
        val dao = db.uploadedFileDao()
        val prefs = AppPrefs(context)
        val serverApi = ServerApi()

        onProgress(ReindexProgress(statusMessage = "Connecting to Telegram Storage Channel..."))

        // Fetch bot fleet pool for high-speed multi-bot parallel scanning
        val activeBots = try {
            db.telegramBotDao().getAllBots().filter { it.isActive && it.botToken.isNotBlank() }.map { it.botToken }
        } catch (_: Exception) {
            emptyList()
        }.ifEmpty { listOf(botToken) }

        // Step 1: Probe channel to determine the absolute highest message ID
        val latestProbeId = getChannelLatestMessageId(botToken, chatId)
        val existingMax = try { dao.getMaxMessageId() ?: 0L } catch (_: Exception) { 0L }
        val startMessageId = if (latestProbeId > 0L) latestProbeId else maxOf(existingMax + 500L, 1000L)

        val foundFiles = mutableListOf<UploadedFile>()
        var scannedCount = 0
        var consecutiveEmpty = 0
        val batchSize = 10

        onProgress(
            ReindexProgress(
                scannedCount = 0,
                foundFilesCount = 0,
                currentMessageId = startMessageId,
                statusMessage = "Scanning channel history from message #$startMessageId using ${activeBots.size} bot(s)..."
            )
        )

        var currentTop = startMessageId
        while (currentTop >= 1L) {
            val chunkRange = (currentTop downTo (currentTop - batchSize + 1).coerceAtLeast(1L)).toList()
            currentTop -= batchSize

            // Inspect message batch in parallel using active bot fleet
            val batchResults = coroutineScope {
                chunkRange.mapIndexed { idx, msgId ->
                    val bot = activeBots[idx % activeBots.size]
                    async(Dispatchers.IO) {
                        inspectTelegramMessage(context, bot, chatId, msgId)
                    }
                }.awaitAll().filterNotNull()
            }

            scannedCount += chunkRange.size
            if (batchResults.isNotEmpty()) {
                foundFiles.addAll(batchResults)
                consecutiveEmpty = 0
            } else {
                consecutiveEmpty += chunkRange.size
            }

            onProgress(
                ReindexProgress(
                    scannedCount = scannedCount,
                    foundFilesCount = foundFiles.size,
                    currentMessageId = chunkRange.lastOrNull() ?: 1L,
                    statusMessage = "Inspecting message #${chunkRange.lastOrNull() ?: 1L} (Found ${foundFiles.size} files)..."
                )
            )

            // Early exit if 120 consecutive messages are empty after finding files
            if (consecutiveEmpty > 120 && foundFiles.isNotEmpty()) {
                break
            }
        }

        // Save all recovered files to local Room DB
        if (foundFiles.isNotEmpty()) {
            onProgress(ReindexProgress(scannedCount = scannedCount, foundFilesCount = foundFiles.size, statusMessage = "Saving ${foundFiles.size} recovered files to local catalog..."))
            foundFiles.chunked(300).forEach { batch ->
                dao.insertAll(batch)
            }

            // Sync with PostgreSQL Cloud database if user email is active
            if (prefs.userEmail.isNotBlank()) {
                try {
                    serverApi.indexFilesBatch(prefs.serverUrl, prefs.userEmail, foundFiles)
                } catch (_: Exception) {}
            }
        }

        onProgress(
            ReindexProgress(
                scannedCount = scannedCount,
                foundFilesCount = foundFiles.size,
                statusMessage = "Recovery Complete! Restored ${foundFiles.size} files from Telegram Cloud.",
                isCompleted = true
            )
        )

        return@withContext foundFiles.size
    }

    /**
     * Determines the latest message ID in the channel by sending and immediately deleting a test probe.
     */
    private fun getChannelLatestMessageId(botToken: String, chatId: String): Long {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", "🔄 [Channel Reindex Probe]")
                put("disable_notification", true)
            }
            val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = HttpClientProvider.client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            if (json.optBoolean("ok", false)) {
                val msgId = json.optJSONObject("result")?.optLong("message_id", 0L) ?: 0L
                if (msgId > 0L) {
                    deleteTelegramMessage(botToken, chatId, msgId)
                    return msgId
                }
            }
            0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Inspects a specific message ID in the channel using Telegram Bot API to extract attached files.
     * Uses forwardMessage to obtain complete Message metadata (documents, photos, videos, audio, date, caption).
     */
    private fun inspectTelegramMessage(
        context: Context,
        botToken: String,
        chatId: String,
        messageId: Long
    ): UploadedFile? {
        return try {
            val url = "https://api.telegram.org/bot$botToken/forwardMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("from_chat_id", chatId)
                put("message_id", messageId)
                put("disable_notification", true)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = HttpClientProvider.client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val json = JSONObject(body)
            if (json.optBoolean("ok", false)) {
                val resultObj = json.optJSONObject("result") ?: return null
                val forwardedMsgId = resultObj.optLong("message_id", 0L)
                if (forwardedMsgId > 0L) {
                    deleteTelegramMessage(botToken, chatId, forwardedMsgId)
                }

                val dateSec = resultObj.optLong("date", 0L)
                val timestampMs = if (dateSec > 0L) dateSec * 1000L else System.currentTimeMillis()
                val rawCaption = resultObj.optString("caption", "").trim()

                // Try decrypting caption with known candidate keys
                var effectiveCaption = rawCaption
                if (rawCaption.startsWith("🔒 ENC:")) {
                    val cipherOnly = rawCaption.removePrefix("🔒 ENC:").trim()
                    for (candidateKey in FileEncryptor.getCandidateKeys(context)) {
                        val decrypted = FileEncryptor.decryptText(cipherOnly, candidateKey)
                        if (decrypted != cipherOnly && (decrypted.contains("Drive:") || decrypted.contains("Part") || decrypted.contains("📦") || decrypted.contains("📤"))) {
                            effectiveCaption = decrypted
                            break
                        }
                    }
                }

                // Check media attachment
                val doc = resultObj.optJSONObject("document")
                val video = resultObj.optJSONObject("video")
                val audio = resultObj.optJSONObject("audio")
                val photos = resultObj.optJSONArray("photo")

                var fileId = ""
                var rawFileName = ""
                var fileSize = 0L

                if (doc != null) {
                    fileId = doc.optString("file_id", "")
                    rawFileName = doc.optString("file_name", "")
                    fileSize = doc.optLong("file_size", 0L)
                } else if (video != null) {
                    fileId = video.optString("file_id", "")
                    rawFileName = video.optString("file_name", "Video_$messageId.mp4")
                    fileSize = video.optLong("file_size", 0L)
                } else if (audio != null) {
                    fileId = audio.optString("file_id", "")
                    rawFileName = audio.optString("file_name", "Audio_$messageId.mp3")
                    fileSize = audio.optLong("file_size", 0L)
                } else if (photos != null && photos.length() > 0) {
                    val largestPhoto = photos.optJSONObject(photos.length() - 1)
                    fileId = largestPhoto?.optString("file_id", "") ?: ""
                    rawFileName = "Photo_$messageId.jpg"
                    fileSize = largestPhoto?.optLong("file_size", 0L) ?: 0L
                }

                if (fileId.isEmpty()) return null

                // Determine isDriveUpload
                val isDrive = effectiveCaption.contains("Drive:", ignoreCase = true) ||
                              effectiveCaption.contains("Internal Storage", ignoreCase = true) ||
                              rawFileName.contains("Drive", ignoreCase = true)

                // Derive real filename from caption if available
                var resolvedFileName = rawFileName.ifBlank { "Cloud_Item_$messageId" }
                var path = if (isDrive) "Internal Storage/$resolvedFileName" else "Telegram/$resolvedFileName"

                if (effectiveCaption.isNotEmpty()) {
                    if (effectiveCaption.contains("Drive:")) {
                        val afterDrive = effectiveCaption.substringAfter("Drive:").trim()
                        val candidateName = if (afterDrive.contains("[")) afterDrive.substringBefore("[").trim() else afterDrive
                        if (candidateName.isNotBlank() && !candidateName.startsWith("ENC_")) {
                            resolvedFileName = candidateName
                            path = "Internal Storage/$candidateName"
                        }
                    } else if (effectiveCaption.startsWith("📦") || effectiveCaption.startsWith("📤")) {
                        val clean = effectiveCaption.removePrefix("📦").removePrefix("📤").trim()
                        val candidateName = if (clean.contains("[")) clean.substringBefore("[").trim()
                                           else if (clean.contains("(")) clean.substringBefore("(").trim()
                                           else clean
                        if (candidateName.isNotBlank() && !candidateName.startsWith("ENC_")) {
                            resolvedFileName = candidateName
                            path = "/storage/emulated/0/Download/$candidateName"
                        }
                    }
                }

                return UploadedFile(
                    path = path,
                    fileName = resolvedFileName,
                    telegramFileId = fileId,
                    timestamp = timestampMs,
                    size = if (fileSize > 0L) fileSize else 1024L,
                    messageId = messageId,
                    isDriveUpload = isDrive,
                    channelId = chatId
                ).apply {
                    this.botTokenUsed = botToken
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteTelegramMessage(botToken: String, chatId: String, messageId: Long) {
        try {
            val url = "https://api.telegram.org/bot$botToken/deleteMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("message_id", messageId)
            }
            val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            HttpClientProvider.client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }
}
