package com.example.telegrambackup.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.widget.Toast
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ShareLinkResult(
    val shareUrl: String,
    val formattedShareText: String,
    val expiresAtText: String,
    val hasPassword: Boolean,
    val shareId: String = "",
    val directDownloadUrl: String = "",
    val isOpaqueSecure: Boolean = true
)

object ShareLinkManager {

    suspend fun generateShareLink(
        context: Context,
        file: UploadedFile,
        expiryHours: Int = 24,
        passwordPin: String = ""
    ): ShareLinkResult = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val db = AppDatabase.getDatabase(context)

        // Find primary or active bot token
        val candidateTokens = TelegramMediaResolver.getCandidateBotTokens(context)
        val botToken = db.telegramBotDao().getPrimaryBot()?.botToken
            ?: db.telegramBotDao().getActiveBots().firstOrNull()?.botToken
            ?: prefs.botToken

        val expiresAtMs = if (expiryHours > 0) System.currentTimeMillis() + (expiryHours * 3600 * 1000L) else 0L

        val computedFromSize = if (file.size > FileChunker.CHUNK_SIZE) {
            ((file.size + FileChunker.CHUNK_SIZE - 1) / FileChunker.CHUNK_SIZE).toInt()
        } else 1
        val expectedChunks = maxOf(file.totalChunks, computedFromSize)

        // Resolve all sequential chunks (handles multi-chunk files of any size)
        val rawChunks = TelegramMediaResolver.resolveChunksForFile(context, file)
        val resolvedChunks = if (rawChunks.isEmpty()) {
            if (expectedChunks <= 1 && file.telegramFileId.isNotBlank()) {
                listOf(Triple(1, file.telegramFileId, file.botTokenUsed.ifBlank { botToken }))
            } else {
                emptyList()
            }
        } else {
            rawChunks.map { c ->
                if (c.third.isBlank()) Triple(c.first, c.second, file.botTokenUsed.ifBlank { botToken }) else c
            }
        }

        val serverUrl = prefs.serverUrl.ifEmpty { "https://telegram-backup-backend.vercel.app" }.trimEnd('/')
        val expiryText = when (expiryHours) {
            1 -> "Valid for 1 Hour"
            24 -> "Valid for 24 Hours"
            168 -> "Valid for 7 Days"
            else -> "Permanent Link"
        }

        // Try primary opaque secure share link via server
        val serverResult = ServerApi().createSecureShareLink(
            serverUrl = serverUrl,
            fileName = file.fileName,
            fileSize = file.size,
            fileId = file.telegramFileId.ifBlank { resolvedChunks.firstOrNull()?.second ?: "" },
            botToken = botToken,
            encryptionKey = prefs.encryptionKey,
            chunks = resolvedChunks,
            pin = passwordPin.trim(),
            expiresAt = expiresAtMs,
            candidateBots = candidateTokens
        )

        val finalUrl: String
        val shareId: String
        val directDl: String
        val isOpaque: Boolean

        if (serverResult.success && serverResult.shareUrl.isNotBlank()) {
            finalUrl = serverResult.shareUrl
            shareId = serverResult.shareId
            directDl = serverResult.directDownloadUrl
            isOpaque = true
        } else {
            // Fallback: URL token with full chunk map so multi-chunk files remain downloadable
            val fallbackPayload = JSONObject().apply {
                put("fileId", file.telegramFileId)
                put("fileName", file.fileName)
                put("fileSize", file.size)
                put("botToken", botToken)
                if (candidateTokens.isNotEmpty()) {
                    val bArray = JSONArray()
                    for (t in candidateTokens) bArray.put(t)
                    put("candidateBots", bArray)
                }
                if (prefs.encryptionKey.isNotBlank()) {
                    put("encryptionKey", prefs.encryptionKey)
                }
                put("expiresAt", expiresAtMs)
                if (passwordPin.isNotBlank()) {
                    put("pin", passwordPin.trim())
                }
                if (resolvedChunks.isNotEmpty()) {
                    val chunksArray = JSONArray()
                    for (c in resolvedChunks) {
                        chunksArray.put(JSONObject().apply {
                            put("chunkIndex", c.first)
                            put("telegramFileId", c.second)
                            if (c.third.isNotBlank()) put("botTokenUsed", c.third)
                        })
                    }
                    put("chunks", chunksArray)
                    put("totalChunks", resolvedChunks.size)
                }
            }

            val encodedPayload = Base64.encodeToString(
                fallbackPayload.toString().toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )
            finalUrl = "$serverUrl/share?data=$encodedPayload"
            shareId = ""
            directDl = "$serverUrl/api/share/download/fallback?data=$encodedPayload"
            isOpaque = false
        }

        val shareMessage = buildString {
            append("📦 File: ${file.fileName}\n")
            append("☁️ Protected Cloud Share ($expiryText)\n")
            if (passwordPin.isNotBlank()) {
                append("🔒 Access PIN: ${passwordPin.trim()}\n")
            }
            append("\n🔗 Download & Stream Link:\n$finalUrl")
        }

        return@withContext ShareLinkResult(
            shareUrl = finalUrl,
            formattedShareText = shareMessage,
            expiresAtText = expiryText,
            hasPassword = passwordPin.isNotBlank(),
            shareId = shareId,
            directDownloadUrl = directDl,
            isOpaqueSecure = isOpaque
        )
    }

    /**
     * Generates a high-contrast scannable QR code bitmap for any URL.
     */
    fun generateQrBitmap(url: String, sizePx: Int = 512): Bitmap? {
        return KeyRecoveryManager.generateQrBitmap(url, sizePx)
    }

    fun launchShareSheet(context: Context, shareText: String, title: String = "Share Cloud File") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
        }
    }
}
