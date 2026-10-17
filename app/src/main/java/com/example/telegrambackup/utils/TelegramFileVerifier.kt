package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.data.UploadedFileDao
import com.example.telegrambackup.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * =====================================================================================
 * TELEGRAM CLOUD FILE VERIFIER (MULTI-BOT FLEET AWARE)
 * =====================================================================================
 * Verifies that files listed as "backed up" in the local Room database actually exist
 * on Telegram's servers.
 *
 * Safeguards:
 * 1. Multi-Bot Fleet Awareness: Checks files against the exact bot that uploaded them
 *    (file.botTokenUsed), plus rotating candidate bots, eliminating false-deletion bugs
 *    where Bot A checks a file uploaded by Bot B.
 * 2. Custom Bot API Endpoint Support: Uses TelegramApiConfig to respect local Bot API servers.
 * 3. Rate-Limit Immunity: HTTP 429 and temporary server 5xx errors NEVER delete records.
 * 4. Two-Way Integrity: Purges orphaned chunk records when a file is confirmed deleted.
 * =====================================================================================
 */
object TelegramFileVerifier {

    private const val TAG = "TelegramFileVerifier"

    /** Maximum files to verify per session to respect Telegram rate limits */
    private const val MAX_VERIFY_PER_SESSION = 100

    enum class FileCheckStatus {
        EXISTS,
        NOT_FOUND,
        NETWORK_OR_RATE_LIMIT
    }

    /**
     * Data class holding the complete results of a verification session.
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
     * Main entry point with full Context and multi-bot fleet awareness.
     */
    suspend fun verifyAllFiles(
        context: Context,
        botToken: String,
        dao: UploadedFileDao
    ): VerificationResult = withContext(Dispatchers.IO) {
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
        val fakeFiles = allFiles.filter { isFakeFileId(it.telegramFileId) }
        for (fake in fakeFiles) {
            try {
                dao.deleteByPath(fake.path)
                try {
                    AppDatabase.getDatabase(context).chunkProgressDao().deleteChunksForFile(fake.path)
                } catch (_: Exception) {}
                fakePurged++
                Log.d(TAG, "Purged fake record: ${fake.fileName} (id=${fake.telegramFileId})")
            } catch (e: Exception) {
                Log.e(TAG, "Error purging fake record: ${fake.path}", e)
            }
        }

        // =========================================================================
        // STEP 2: Collect Candidate Bots from Fleet to prevent false token mismatches
        // =========================================================================
        val candidateTokens = mutableListOf<String>()
        if (botToken.isNotBlank()) candidateTokens.add(botToken.trim())
        try {
            val fleetTokens = TelegramMediaResolver.getCandidateBotTokens(context)
            for (t in fleetTokens) {
                val clean = t.trim()
                if (clean.isNotBlank() && !candidateTokens.contains(clean)) {
                    candidateTokens.add(clean)
                }
            }
        } catch (_: Exception) {}

        if (candidateTokens.isEmpty()) {
            Log.w(TAG, "No candidate bot tokens available, skipping verification")
            return@withContext VerificationResult(fakePurged = fakePurged)
        }

        // =========================================================================
        // STEP 3: Multi-Bot Cloud Verification Loop
        // =========================================================================
        val realFiles = allFiles.filter { !isFakeFileId(it.telegramFileId) && it.telegramFileId.isNotEmpty() }
        val filesToVerify = realFiles.take(MAX_VERIFY_PER_SESSION)

        for (file in filesToVerify) {
            try {
                // Determine tokens to test: priority to the token that actually uploaded this file
                val tokensToTry = mutableListOf<String>()
                if (file.botTokenUsed.isNotBlank() && !tokensToTry.contains(file.botTokenUsed.trim())) {
                    tokensToTry.add(file.botTokenUsed.trim())
                }
                candidateTokens.forEach { if (!tokensToTry.contains(it)) tokensToTry.add(it) }

                var fileConfirmedExists = false
                var allTokensConfirmedInvalid = true
                var hadNetworkError = false

                for (token in tokensToTry) {
                    val status = checkFileStatus(context, token, file.telegramFileId)
                    when (status) {
                        FileCheckStatus.EXISTS -> {
                            fileConfirmedExists = true
                            allTokensConfirmedInvalid = false
                            break
                        }
                        FileCheckStatus.NETWORK_OR_RATE_LIMIT -> {
                            allTokensConfirmedInvalid = false
                            hadNetworkError = true
                            break
                        }
                        FileCheckStatus.NOT_FOUND -> {
                            // Continue checking remaining candidate bots
                        }
                    }
                }

                if (fileConfirmedExists) {
                    verifiedOk++
                } else if (allTokensConfirmedInvalid && !hadNetworkError) {
                    // Confirmed missing across all available bot accounts: purge from DB and chunk cache
                    dao.deleteByPath(file.path)
                    try {
                        AppDatabase.getDatabase(context).chunkProgressDao().deleteChunksForFile(file.path)
                    } catch (_: Exception) {}
                    deletedFromTelegram++
                    Log.w(TAG, "File confirmed deleted from Telegram, purged: ${file.fileName} (id=${file.telegramFileId})")
                } else {
                    // Retain safely if ambiguous / network glitch / rate limit
                    errors++
                }
            } catch (e: Exception) {
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
     * Backward-compatible overload without Context parameter.
     */
    suspend fun verifyAllFiles(
        botToken: String,
        dao: UploadedFileDao
    ): VerificationResult = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext VerificationResult()
        val allFiles = try { dao.getAllFiles() } catch (_: Exception) { emptyList() }
        if (allFiles.isEmpty()) return@withContext VerificationResult()

        var fakePurged = 0
        var deletedFromTelegram = 0
        var verifiedOk = 0
        var errors = 0

        val fakeFiles = allFiles.filter { isFakeFileId(it.telegramFileId) }
        for (fake in fakeFiles) {
            try {
                dao.deleteByPath(fake.path)
                fakePurged++
            } catch (_: Exception) {}
        }

        val realFiles = allFiles.filter { !isFakeFileId(it.telegramFileId) && it.telegramFileId.isNotEmpty() }
        val filesToVerify = realFiles.take(MAX_VERIFY_PER_SESSION)

        for (file in filesToVerify) {
            try {
                val token = file.botTokenUsed.ifBlank { botToken }
                val exists = verifyFileExists(token, file.telegramFileId)
                if (exists) {
                    verifiedOk++
                } else {
                    dao.deleteByPath(file.path)
                    deletedFromTelegram++
                }
            } catch (_: Exception) {
                errors++
            }
        }

        return@withContext VerificationResult(
            totalChecked = fakeFiles.size + filesToVerify.size,
            fakePurged = fakePurged,
            deletedFromTelegram = deletedFromTelegram,
            verifiedOk = verifiedOk,
            errors = errors
        )
    }

    /**
     * Checks if a single file exists on Telegram servers with full endpoint and error classification.
     */
    fun checkFileStatus(context: Context, botToken: String, fileId: String): FileCheckStatus {
        if (botToken.isBlank() || fileId.isBlank()) return FileCheckStatus.NOT_FOUND
        return try {
            val url = TelegramApiConfig.getMethodUrl(context, botToken, "getFile?file_id=$fileId")
            val request = Request.Builder().url(url).get().build()

            val response = HttpClientProvider.client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val json = JSONObject(body)
            val isOk = json.optBoolean("ok", false)
            if (isOk) return FileCheckStatus.EXISTS

            val desc = json.optString("description", "").lowercase()
            val errorCode = json.optInt("error_code", 0)

            // Telegram Bot API getFile returns 400 with "file is too big" for files >20MB.
            // These files ARE 100% VALID and safely stored on Telegram Cloud!
            if (desc.contains("file is too big") || desc.contains("too big") || desc.contains("file_id is valid")) {
                return FileCheckStatus.EXISTS
            }

            // If Telegram is rate limiting (429) or experiencing temporary errors (500/502/503), do not purge!
            if (errorCode == 429 || errorCode >= 500 || desc.contains("too many requests") || desc.contains("retry after")) {
                return FileCheckStatus.NETWORK_OR_RATE_LIMIT
            }

            // Only mark as missing if Telegram explicitly confirms the file_id is invalid/deleted
            if (desc.contains("wrong file identifier") || desc.contains("invalid file_id") || desc.contains("file not found")) {
                return FileCheckStatus.NOT_FOUND
            }

            // Default safe fallback: retain record
            FileCheckStatus.NETWORK_OR_RATE_LIMIT
        } catch (_: Exception) {
            FileCheckStatus.NETWORK_OR_RATE_LIMIT
        }
    }

    /**
     * Legacy helper checking file existence on official API endpoint.
     */
    fun verifyFileExists(botToken: String, fileId: String): Boolean {
        if (botToken.isBlank() || fileId.isBlank()) return false
        return try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/getFile?file_id=$fileId"
            val request = Request.Builder().url(url).get().build()

            val response = HttpClientProvider.client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val json = JSONObject(body)
            val isOk = json.optBoolean("ok", false)
            if (isOk) return true

            val desc = json.optString("description", "").lowercase()
            val errorCode = json.optInt("error_code", 0)

            if (desc.contains("file is too big") || desc.contains("too big") || desc.contains("file_id is valid")) {
                return true
            }

            if (errorCode == 429 || errorCode >= 500 || desc.contains("too many requests") || desc.contains("retry after")) {
                return true
            }

            if (desc.contains("wrong file identifier") || desc.contains("invalid file_id") || desc.contains("file not found")) {
                return false
            }

            true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Detects fake/placeholder telegramFileIds that were created by old versions.
     */
    private fun isFakeFileId(fileId: String): Boolean {
        if (fileId.isEmpty()) return true
        if (fileId.startsWith("tg_backup_")) return true
        if (fileId.startsWith("tg_")) return true
        if (fileId.length < 20 && fileId.all { it.isDigit() || it == '_' }) return true
        return false
    }
}

