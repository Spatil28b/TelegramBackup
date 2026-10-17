package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background daemon to enforce trash retention policies (7, 14, 30 days).
 * Automatically sweeps and permanently purges expired files from Room DB,
 * server index, and Telegram Cloud channels.
 */
object TrashRetentionDaemon {
    private const val TAG = "TrashRetentionDaemon"

    suspend fun sweepExpiredTrash(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val prefs = AppPrefs(context)
            val autoCleanDays = prefs.trashAutoCleanDays
            if (autoCleanDays <= 0) return@withContext 0

            val db = AppDatabase.getDatabase(context)
            val dao = db.uploadedFileDao()

            // 1. Ensure all trashed files have deletedAt timestamps
            dao.fixMissingDeletedAt(System.currentTimeMillis())

            val cutoff = System.currentTimeMillis() - (autoCleanDays.toLong() * 86400000L)
            val expired = dao.getExpiredTrashFiles(cutoff)
            if (expired.isEmpty()) return@withContext 0

            Log.i(TAG, "Sweeping ${expired.size} expired trash files older than $autoCleanDays days...")
            val serverApi = ServerApi()
            var purgedCount = 0

            expired.forEach { expFile ->
                try {
                    val botToken = expFile.botTokenUsed.ifBlank { prefs.botToken }
                    val targetChatId = expFile.channelId.ifBlank { prefs.chatId }
                    serverApi.purgeFileCompleteFromTelegram(
                        context = context,
                        botToken = botToken,
                        chatId = targetChatId,
                        file = expFile,
                        forceLocalDelete = true
                    )
                    purgedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed purging expired file ${expFile.fileName}: ${e.message}")
                }
            }

            Log.i(TAG, "✓ Successfully purged $purgedCount expired trash files from DB & Telegram Cloud")
            return@withContext purgedCount
        } catch (t: Throwable) {
            Log.e(TAG, "Error in sweepExpiredTrash: ${t.message}", t)
            return@withContext 0
        }
    }
}
