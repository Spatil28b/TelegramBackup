package com.example.telegrambackup.utils

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ShareLinkResult(
    val shareUrl: String,
    val formattedShareText: String,
    val expiresAtText: String,
    val hasPassword: Boolean
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
        val botToken = db.telegramBotDao().getPrimaryBot()?.botToken
            ?: db.telegramBotDao().getActiveBots().firstOrNull()?.botToken
            ?: prefs.botToken

        val expiresAtMs = if (expiryHours > 0) System.currentTimeMillis() + (expiryHours * 3600 * 1000L) else 0L
        val directUrl = TelegramMediaResolver.resolveDirectUrl(context, file.telegramFileId.ifBlank { file.path })

        val payload = JSONObject().apply {
            put("fileId", file.telegramFileId)
            put("fileName", file.fileName)
            put("fileSize", file.size)
            put("botToken", botToken)
            if (!directUrl.isNullOrBlank()) {
                put("directUrl", directUrl)
            }
            if (prefs.encryptionKey.isNotBlank()) {
                put("encryptionKey", prefs.encryptionKey)
            }
            put("expiresAt", expiresAtMs)
            put("pin", passwordPin.trim())
        }

        val encodedPayload = Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )

        val serverUrl = prefs.serverUrl.ifEmpty { "https://telegram-backup-backend.vercel.app" }.trimEnd('/')
        val finalUrl = "$serverUrl/api/share?data=$encodedPayload"

        val expiryText = when (expiryHours) {
            1 -> "Valid for 1 Hour"
            24 -> "Valid for 24 Hours"
            168 -> "Valid for 7 Days"
            else -> "Permanent Link"
        }

        val shareMessage = buildString {
            append("📦 File: ${file.fileName}\n")
            append("☁️ Protected Cloud Share ($expiryText)\n")
            if (passwordPin.isNotBlank()) {
                append("🔒 Access PIN: ${passwordPin.trim()}\n")
            }
            append("\n🔗 Download & Stream Link (Includes Full File Type & Preview):\n$finalUrl")
        }

        return@withContext ShareLinkResult(
            shareUrl = finalUrl,
            formattedShareText = shareMessage,
            expiresAtText = expiryText,
            hasPassword = passwordPin.isNotBlank()
        )
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
