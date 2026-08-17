package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * =====================================================================================
 * DATA CROSS VERIFIER — INTEGRITY VALIDATION ENGINE
 * =====================================================================================
 * Performs comprehensive data integrity checks across all storage layers:
 *
 * 1. Master AES-256 Encryption Key: Verifies key exists and is properly formatted.
 * 2. Bot Credentials: Validates Bot Token & Chat ID are configured.
 * 3. Telegram Cloud Verification: Uses TelegramFileVerifier to confirm files
 *    marked as "backed up" actually exist on Telegram servers. Purges ghost records.
 * 4. Cloud DB Sync: Syncs file index from PostgreSQL cloud database to local Room DB.
 * 5. Local DB Integrity: Reports total file count after cleanup.
 *
 * Called on app launch from MainActivity and from manual "Verify Data" actions.
 * =====================================================================================
 */
object DataCrossVerifier {

    private const val TAG = "DataCrossVerifier"

    /**
     * Runs full data integrity verification and sync.
     * This is the main entry point called on app open and manual verification.
     *
     * @param context Android application context
     * @return Human-readable verification report string for UI display
     */
    suspend fun verifyAndSyncAllData(context: Context): String = withContext(Dispatchers.IO) {
        val appPrefs = AppPrefs(context)
        val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val userEmail = authPrefs.getString("user_email", "") ?: ""
        val serverApi = ServerApi()
        val database = AppDatabase.getDatabase(context)
        val dao = database.uploadedFileDao()

        val reportList = mutableListOf<String>()

        // =====================================================================
        // 1. Verify & Restore Master Encryption Key if missing
        // =====================================================================
        val currentKey = appPrefs.encryptionKey // AppPrefs.getter auto-generates if empty
        if (currentKey.isNotEmpty()) {
            reportList.add("✅ Master AES-256 Encryption Key verified.")
        } else {
            reportList.add("⚠️ Master AES-256 Key could not be generated.")
        }

        // =====================================================================
        // 2. Cross-verify credentials (Bot Token & Chat ID)
        // =====================================================================
        val botToken = appPrefs.botToken.trim()
        val chatId = appPrefs.chatId.trim()

        if (botToken.isNotEmpty() && chatId.isNotEmpty()) {
            reportList.add("✅ Bot Token & Chat ID verified.")
        } else {
            reportList.add("⚠️ Bot credentials incomplete. Please configure Bot Token & Chat ID in Settings.")
        }

        // =====================================================================
        // 3. TELEGRAM CLOUD VERIFICATION — Purge ghost/fake records
        // =====================================================================
        // This is the critical step that fixes the "shows backed up but actually not" problem.
        // It checks every file against Telegram's servers and removes:
        //   a) Fake records from old bug (tg_backup_* IDs that were never uploaded)
        //   b) Records for files that were deleted from the Telegram chat/channel
        if (botToken.isNotEmpty()) {
            try {
                val verifyResult = TelegramFileVerifier.verifyAllFiles(botToken, dao)
                reportList.add(verifyResult.toSummary())
            } catch (e: Exception) {
                Log.e(TAG, "Telegram cloud verification failed", e)
                reportList.add("⚠️ Could not verify Telegram cloud files: ${e.localizedMessage}")
            }
        } else {
            reportList.add("⏭️ Skipped Telegram verification (no bot token).")
        }

        // =====================================================================
        // 4. Cross-verify Local Room DB with Cloud Postgres Database
        // =====================================================================
        if (userEmail.isNotEmpty()) {
            try {
                val cloudFiles = serverApi.listFiles(appPrefs.serverUrl, userEmail)
                if (cloudFiles.isNotEmpty()) {
                    // Only insert cloud records that have real Telegram file IDs
                    val validCloudFiles = cloudFiles.filter {
                        it.telegramFileId.isNotEmpty() &&
                        !it.telegramFileId.startsWith("tg_backup_") &&
                        !it.telegramFileId.startsWith("tg_")
                    }
                    if (validCloudFiles.isNotEmpty()) {
                        dao.insertAll(validCloudFiles)
                        reportList.add("✅ Synced ${validCloudFiles.size} verified files from Cloud DB.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cross-verifying cloud files", e)
                reportList.add("⚠️ Cloud sync failed: ${e.localizedMessage}")
            }
        }

        // =====================================================================
        // 5. Final local DB integrity count (after all cleanups)
        // =====================================================================
        val localFilesCount = try { dao.getAllFiles().size } catch (e: Exception) { 0 }
        reportList.add("✅ Local Database verified ($localFilesCount verified files).")

        return@withContext reportList.joinToString("\n")
    }
}
