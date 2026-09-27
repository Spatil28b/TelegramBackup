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
        val savedAuth = prefs.authToken
        if (savedAuth.isNotBlank() && ServerApi.activeAuthToken.isBlank()) {
            ServerApi.activeAuthToken = savedAuth
        }
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
        var rawChunks = TelegramMediaResolver.resolveChunksForFile(context, file)
        if (rawChunks.isEmpty() && expectedChunks > 1) {
            // Check if another local entity with the same filename has the complete chunks
            val alt = try {
                db.uploadedFileDao().getAllFiles().firstOrNull {
                    it.fileName.equals(file.fileName, ignoreCase = true) &&
                    it.totalChunks >= expectedChunks &&
                    it.chunksJson.isNotBlank() &&
                    it.chunksJson != "[]"
                }
            } catch (_: Exception) { null }
            if (alt != null) {
                rawChunks = TelegramMediaResolver.resolveChunksForFile(context, alt)
            }
        }

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
            val errMsg = serverResult.message.ifBlank { "Could not generate secure share link from server" }
            return@withContext ShareLinkResult(
                shareUrl = "",
                formattedShareText = "⚠️ Failed to create share link: $errMsg",
                expiresAtText = expiryText,
                hasPassword = passwordPin.isNotBlank(),
                shareId = "",
                directDownloadUrl = "",
                isOpaqueSecure = false
            )
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
