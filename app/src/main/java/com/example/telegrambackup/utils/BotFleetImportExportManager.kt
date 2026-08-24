package com.example.telegrambackup.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.TelegramBotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BotFleetImportExportManager {
    private const val TAG = "BotFleetImportExport"

    /**
     * Sanitizes raw bot token strings, removing URL prefixes and parameters.
     */
    fun sanitizeToken(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("https://api.telegram.org/bot", ignoreCase = true)) {
            cleaned = cleaned.substring(28).substringBefore("/").substringBefore("?")
        } else if (cleaned.startsWith("http://api.telegram.org/bot", ignoreCase = true)) {
            cleaned = cleaned.substring(27).substringBefore("/").substringBefore("?")
        } else if (cleaned.startsWith("bot", ignoreCase = true) && cleaned.contains(":")) {
            cleaned = cleaned.substring(3)
        }
        return cleaned.trim()
    }

    /**
     * Exports all Telegram Bots configured in the Room Database to a formatted JSON string.
     */
    suspend fun exportBotsToJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val bots = db.telegramBotDao().getAllBots()

        val root = JSONObject()
        root.put("export_version", 1)
        root.put("exported_at", System.currentTimeMillis())
        root.put("bot_count", bots.size)

        val botsArray = JSONArray()
        bots.forEach { bot ->
            val obj = JSONObject().apply {
                put("bot_token", bot.botToken)
                put("display_name", bot.displayName)
                put("bot_username", bot.botUsername)
                put("is_primary", bot.isPrimary)
                put("is_active", bot.isActive)
                put("total_bytes_uploaded", bot.totalBytesUploaded)
                put("success_chunks", bot.successChunks)
                put("failed_chunks", bot.failedChunks)
                put("added_at", bot.addedAt)
            }
            botsArray.put(obj)
        }
        root.put("bots", botsArray)

        return@withContext root.toString(2)
    }

    /**
     * Imports bots from a JSON string or raw text list of tokens.
     * Upserts each bot into Room Database and triggers Cloud Manifest Synchronization.
     * Returns Pair(importedCount, statusMessage).
     */
    suspend fun importBotsFromJson(context: Context, rawInput: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (rawInput.isBlank()) {
            return@withContext Pair(0, "Input is empty.")
        }

        val db = AppDatabase.getDatabase(context)
        val prefs = AppPrefs(context)
        val botDao = db.telegramBotDao()
        val existingBots = botDao.getAllBots().associateBy { it.botToken }
        var importedCount = 0

        try {
            val trimmed = rawInput.trim()
            if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val botsArray = root.optJSONArray("bots") ?: root.optJSONArray("bot_fleet")
                if (botsArray != null) {
                    for (i in 0 until botsArray.length()) {
                        val obj = botsArray.optJSONObject(i) ?: continue
                        val token = sanitizeToken(obj.optString("bot_token", "").ifEmpty { obj.optString("token", "") })
                        if (token.length >= 25 && token.contains(":")) {
                            val existing = existingBots[token]
                            val entity = TelegramBotEntity(
                                botToken = token,
                                displayName = obj.optString("display_name", "").ifEmpty { existing?.displayName ?: "Bot ${existingBots.size + importedCount + 1}" },
                                botUsername = obj.optString("bot_username", "").ifEmpty { existing?.botUsername ?: "" },
                                isPrimary = obj.optBoolean("is_primary", existingBots.isEmpty() && importedCount == 0),
                                isActive = obj.optBoolean("is_active", true),
                                totalBytesUploaded = obj.optLong("total_bytes_uploaded", existing?.totalBytesUploaded ?: 0L),
                                successChunks = obj.optInt("success_chunks", existing?.successChunks ?: 0),
                                failedChunks = obj.optInt("failed_chunks", existing?.failedChunks ?: 0)
                            )
                            botDao.insert(entity)
                            if (entity.isPrimary) prefs.botToken = token
                            importedCount++
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.get(i)
                    val token = if (item is JSONObject) {
                        sanitizeToken(item.optString("bot_token", "").ifEmpty { item.optString("token", "") })
                    } else {
                        sanitizeToken(item.toString())
                    }
                    if (token.length >= 25 && token.contains(":")) {
                        val existing = existingBots[token]
                        val name = if (item is JSONObject) item.optString("display_name", "") else ""
                        val entity = TelegramBotEntity(
                            botToken = token,
                            displayName = name.ifEmpty { existing?.displayName ?: "Bot ${existingBots.size + importedCount + 1}" },
                            botUsername = if (item is JSONObject) item.optString("bot_username", "") else "",
                            isPrimary = existingBots.isEmpty() && importedCount == 0,
                            isActive = true
                        )
                        botDao.insert(entity)
                        if (entity.isPrimary) prefs.botToken = token
                        importedCount++
                    }
                }
            } else {
                // Parse line by line / comma-separated plain tokens
                val lines = trimmed.lines().flatMap { it.split(",") }
                for (line in lines) {
                    val token = sanitizeToken(line)
                    if (token.length >= 25 && token.contains(":")) {
                        val existing = existingBots[token]
                        val entity = TelegramBotEntity(
                            botToken = token,
                            displayName = existing?.displayName ?: "Bot ${existingBots.size + importedCount + 1}",
                            botUsername = existing?.botUsername ?: "",
                            isPrimary = existingBots.isEmpty() && importedCount == 0,
                            isActive = true
                        )
                        botDao.insert(entity)
                        if (entity.isPrimary) prefs.botToken = token
                        importedCount++
                    }
                }
            }

            if (importedCount > 0) {
                // Immediately synchronize updated fleet to PostgreSQL server manifest
                CloudProfileSyncManager.syncLocalConfigToCloud(context)
                return@withContext Pair(importedCount, "Successfully imported $importedCount bot(s) into fleet & synced to database!")
            } else {
                return@withContext Pair(0, "No valid Telegram bot tokens (format 123456789:ABC...) found in input.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import error", e)
            return@withContext Pair(0, "Import failed: ${e.localizedMessage}")
        }
    }

    /**
     * Copies text to system clipboard.
     */
    fun copyToClipboard(context: Context, text: String, label: String = "Bot Fleet JSON") {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "📋 Copied $label to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Copy failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares export text via Android Share Intent.
     */
    fun shareExportText(context: Context, jsonText: String, title: String = "Export Bot Fleet") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, jsonText)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Saves JSON backup to user's public Downloads directory.
     */
    fun saveToDownloads(context: Context, jsonText: String): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "telegram_bot_fleet_backup_${System.currentTimeMillis()}.json")
            FileOutputStream(file).use { it.write(jsonText.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "💾 Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
            file
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
