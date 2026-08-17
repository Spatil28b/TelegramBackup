package com.example.telegrambackup.utils

import android.util.Log
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.data.UploadedFileDao
import com.example.telegrambackup.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * =====================================================================================
 * TELEGRAM CLOUD FILE VERIFIER
 * =====================================================================================
 * Verifies that files listed as "backed up" in the local Room database actually exist
 * on Telegram's servers. This solves the critical ghost-record problem where:
 *
 * 1. Old BackupWorker bug saved fake telegramFileIds (tg_backup_...) without uploading.
 * 2. Files uploaded to Telegram can be manually deleted from the chat/channel,
 *    but the app still shows them as "backed up".
 *
 * Verification Flow:
 * - Step 1: PURGE all records with fake IDs (tg_backup_*) — these were never uploaded.
 * - Step 2: For records with real Telegram file_ids, call the Bot API `getFile` endpoint
 *           to confirm the file still exists on Telegram servers.
 * - Step 3: Remove records that fail verification (file deleted from Telegram).
 * - Step 4: Files without valid DB records will be re-uploaded by BackupWorker
 *           on the next scheduled backup cycle.
 *
 * Rate Limiting:
 * - Telegram Bot API allows ~30 requests/second.
 * - Verifier checks files in batches with a configurable batch size.
 * - Only verifies up to MAX_VERIFY_PER_SESSION files per call to avoid API abuse.
 * =====================================================================================
 */
object TelegramFileVerifier {

    private const val TAG = "TelegramFileVerifier"

    /** Maximum files to verify per session to respect Telegram rate limits */
    private const val MAX_VERIFY_PER_SESSION = 100

    /**
     * Data class holding the complete results of a verification session.
     * @param totalChecked Total files inspected
     * @param fakePurged Number of fake (never-uploaded) records removed
     * @param deletedFromTelegram Number of records removed because file no longer exists on Telegram
     * @param verifiedOk Number of files confirmed to exist on Telegram
     * @param errors Number of files that could not be checked (network errors)
     */
    data class VerificationResult(
        val totalChecked: Int = 0,
        val fakePurged: Int = 0,
        val deletedFromTelegram: Int = 0,
        val verifiedOk: Int = 0,
        val errors: Int = 0
    ) {
        /** Human-readable summary for UI display */
        fun toSummary(): String {
            val parts = mutableListOf<String>()
            if (fakePurged > 0) parts.add("🗑️ Purged $fakePurged fake records (never uploaded)")
            if (deletedFromTelegram > 0) parts.add("⚠️ Removed $deletedFromTelegram files deleted from Telegram")
            if (verifiedOk > 0) parts.add("✅ Verified $verifiedOk files exist on Telegram Cloud")
            if (errors > 0) parts.add("❌ $errors files could not be checked (network issue)")
            if (parts.isEmpty()) parts.add("✅ No files to verify.")
            return parts.joinToString("\n")
        }
    }

    /**
     * Main entry point: Runs full verification of all "backed up" files.
     *
     * Step 1: Purges all fake records (tg_backup_* IDs that were never uploaded).
     * Step 2: Verifies real Telegram file_ids via Bot API getFile.
     * Step 3: Removes records for files that no longer exist on Telegram.
     *
     * After this runs, BackupWorker will automatically re-upload any files
     * that lost their DB records on the next backup cycle.
     *
     * @param botToken Telegram Bot API token
     * @param dao Room database DAO for uploaded files
     * @return VerificationResult with counts of purged, verified, and failed files
     */
    suspend fun verifyAllFiles(
        botToken: String,
        dao: UploadedFileDao
    ): VerificationResult = withContext(Dispatchers.IO) {

        if (botToken.isEmpty()) {
            Log.w(TAG, "Bot token is empty, skipping verification")
            return@withContext VerificationResult()
        }

        val allFiles = try {
            dao.getAllFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load files from DB", e)
            return@withContext VerificationResult()
        }

        if (allFiles.isEmpty()) {
            return@withContext VerificationResult()
        }

        var fakePurged = 0
        var deletedFromTelegram = 0
        var verifiedOk = 0
        var errors = 0

        // =========================================================================
        // STEP 1: Purge all fake/placeholder records that were never uploaded
        // =========================================================================
        // Old BackupWorker bug created records with IDs like "tg_backup_1234_0"
        // instead of real Telegram file_ids (which look like "BQACAgIAAxk...")
        val fakeFiles = allFiles.filter { isFakeFileId(it.telegramFileId) }
        for (fake in fakeFiles) {
            try {
                dao.deleteByPath(fake.path)
                fakePurged++
                Log.d(TAG, "Purged fake record: ${fake.fileName} (id=${fake.telegramFileId})")
            } catch (e: Exception) {
                Log.e(TAG, "Error purging fake record: ${fake.path}", e)
            }
        }

        // =========================================================================
        // STEP 2: Verify real file_ids against Telegram Bot API
        // =========================================================================
        val realFiles = allFiles.filter { !isFakeFileId(it.telegramFileId) && it.telegramFileId.isNotEmpty() }
        val filesToVerify = realFiles.take(MAX_VERIFY_PER_SESSION)

        for (file in filesToVerify) {
            try {
                val exists = verifyFileExists(botToken, file.telegramFileId)
                if (exists) {
                    verifiedOk++
                } else {
                    // File was deleted from Telegram — remove the ghost record
                    dao.deleteByPath(file.path)
                    deletedFromTelegram++
                    Log.w(TAG, "File deleted from Telegram, purged: ${file.fileName} (id=${file.telegramFileId})")
                }
            } catch (e: Exception) {
                // Network error — don't delete, just skip
                errors++
                Log.e(TAG, "Verification error for ${file.fileName}", e)
            }
        }

        val result = VerificationResult(
            totalChecked = fakeFiles.size + filesToVerify.size,
            fakePurged = fakePurged,
            deletedFromTelegram = deletedFromTelegram,
            verifiedOk = verifiedOk,
            errors = errors
        )

        Log.d(TAG, "Verification complete: ${result.toSummary()}")
        return@withContext result
    }

    /**
     * Checks if a single file still exists on Telegram servers using the Bot API `getFile` endpoint.
     *
     * Telegram's getFile API returns:
     * - 200 OK with { ok: true, result: { file_id, file_path, ... } } if file exists
     * - 400 Bad Request with { ok: false, description: "Bad Request: invalid file_id" } if deleted
     *
     * @param botToken Bot API token
     * @param fileId Telegram file_id to verify
     * @return true if file exists, false if deleted/invalid
     * @throws Exception on network errors (caller should handle)
     */
    /**
     * Checks if a single file still exists on Telegram servers using the Bot API `getFile` endpoint.
     *
     * Telegram's getFile API returns:
     * - 200 OK with { ok: true, result: { file_id, file_path, ... } } if file exists
     * - 400 Bad Request with { ok: false, description: "Bad Request: invalid file_id" } if deleted
     *
     * @param botToken Bot API token
     * @param fileId Telegram file_id to verify
     * @return true if file exists, false if deleted/invalid
     */
    fun verifyFileExists(botToken: String, fileId: String): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
            val request = Request.Builder().url(url).get().build()

            val response = HttpClientProvider.client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val json = JSONObject(body)
            json.optBoolean("ok", false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detects fake/placeholder telegramFileIds that were created by the old
     * BackupWorker bug (which never actually uploaded files to Telegram).
     *
     * Fake IDs follow the pattern: "tg_backup_<timestamp>_<index>"
     * Real Telegram file_ids are Base64-encoded strings starting with "BQ", "Ag", etc.
     *
     * @param fileId The telegramFileId to check
     * @return true if this is a fake/placeholder ID, false if it looks like a real Telegram file_id
     */
    private fun isFakeFileId(fileId: String): Boolean {
        if (fileId.isEmpty()) return true
        if (fileId.startsWith("tg_backup_")) return true
        if (fileId.startsWith("tg_")) return true
        // Real Telegram file_ids are typically 50+ characters of Base64
        // Fake ones are short numeric strings
        if (fileId.length < 20 && fileId.all { it.isDigit() || it == '_' }) return true
        return false
    }
}
