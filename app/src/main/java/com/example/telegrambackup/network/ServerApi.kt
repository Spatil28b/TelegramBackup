package com.example.telegrambackup.network

import android.util.Log
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * =====================================================================================
 * SERVER API HTTP NETWORK CLIENT
 * =====================================================================================
 * Handles all asynchronous REST API communication between the Android Telegram Backup app
 * and the Vercel Serverless Node.js backend (`telegram-backup-backend.vercel.app`).
 *
 * Capabilities:
 * - OTP email dispatch & verification for user registration & 2FA.
 * - Password reset workflows.
 * - Secure login authentication.
 * - Telegram Bot credentials (botToken & chatId) cloud syncing.
 * - Master AES-256 Security Encryption Key dispatch.
 * - Cloud database file index synchronization, listing, and file deletion.
 * =====================================================================================
 */
class ServerApi {
    companion object {
        @Volatile
        var activeAuthToken: String = ""
    }

    private val client = HttpClientProvider.client

    /**
     * Normalizes base server URL and appends the requested API endpoint path.
     * Prevents duplicate slashes or missing protocol prefixes (http:// or https://).
     */
    private fun cleanUrl(serverUrl: String, path: String): String {
        var base = serverUrl.trim().trim('"', '\'', ' ', '\t', '\n', '\r').removeSuffix("/")
        val cleanBase = if (base.startsWith("http://") || base.startsWith("https://")) base else "https://$base"
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$cleanBase$cleanPath"
    }

    fun sanitizeBotId(token: String): String {
        if (token.isBlank()) return ""
        val trimmed = token.trim()
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx > 0) {
            val prefix = trimmed.substring(0, colonIdx)
            if (prefix.all { it.isDigit() }) {
                return prefix
            }
        }
        return trimmed
    }

    fun sanitizeChunksJson(chunksJson: String): String {
        if (chunksJson.isBlank() || chunksJson == "[]") return chunksJson
        return try {
            val arr = org.json.JSONArray(chunksJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.has("botToken")) {
                    val raw = obj.optString("botToken", "")
                    obj.put("botToken", sanitizeBotId(raw))
                }
                if (obj.has("bot_token")) {
                    val raw = obj.optString("bot_token", "")
                    obj.put("bot_token", sanitizeBotId(raw))
                }
                if (obj.has("botTokenUsed")) {
                    val raw = obj.optString("botTokenUsed", "")
                    obj.put("botTokenUsed", sanitizeBotId(raw))
                }
                if (obj.has("bot_token_used")) {
                    val raw = obj.optString("bot_token_used", "")
                    obj.put("bot_token_used", sanitizeBotId(raw))
                }
            }
            arr.toString()
        } catch (_: Exception) {
            chunksJson
        }
    }

    /**
     * Checks online server for the latest released app version & APK download link.
     * Uses backend endpoint with fallback to production backend.
     */
    suspend fun getLatestAppVersion(serverUrl: String): com.example.telegrambackup.ui.update.AppVersionInfo = withContext(Dispatchers.IO) {
        val urlsToTry = mutableListOf<String>()
        val primary = cleanUrl(serverUrl, "/api/app/latest-version")
        urlsToTry.add(primary)
        val fallback = "https://telegram-backup-backend.vercel.app/api/app/latest-version"
        if (!urlsToTry.contains(fallback)) {
            urlsToTry.add(fallback)
        }

        for (u in urlsToTry) {
            try {
                val request = Request.Builder().url(u).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful && body.contains("\"success\":true")) {
                    val json = JSONObject(body)
                    var dlUrl = json.optString("downloadUrl", "")
                    if (dlUrl.isBlank() || dlUrl.startsWith("/")) {
                        dlUrl = cleanUrl(serverUrl, if (dlUrl.isNotBlank()) dlUrl else "/api/app/download-latest")
                    }
                    return@withContext com.example.telegrambackup.ui.update.AppVersionInfo(
                        success = true,
                        versionCode = json.optInt("versionCode", 1),
                        versionName = json.optString("versionName", "1.0.0"),
                        downloadUrl = dlUrl,
                        releaseNotes = json.optString("releaseNotes", "New performance and feature updates.")
                    )
                }
            } catch (e: Exception) {
                Log.e("ServerApi", "getLatestAppVersion check error for $u", e)
            }
        }

        return@withContext com.example.telegrambackup.ui.update.AppVersionInfo(
            success = false,
            versionCode = 1,
            versionName = "1.0.0",
            downloadUrl = "",
            releaseNotes = "Unable to check updates"
        )
    }

    /**
     * Dispatches a 6-digit OTP verification code to the user's email address.
     * @param serverUrl Base backend Vercel URL
     * @param email User's email address
     * @param type Purpose of OTP ('register', '2fa', or 'reset')
     */
    suspend fun sendOtp(serverUrl: String, email: String, type: String = "register"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/auth/send-otp")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("type", type)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) {
                return@withContext Result.failure(Exception("Server returned an invalid response. Please try again."))
            }
            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val message = jsonRes.optString("message", "OTP sent successfully!")
                return@withContext Result.success(message)
            } else {
                val err = jsonRes.optString("message", "Failed to send OTP.")
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "sendOtp error", e)
            return@withContext Result.failure(Exception(e.localizedMessage ?: "Network error"))
        }
    }

    /**
     * Verifies the 6-digit OTP registration code and registers the user account.
     */
    suspend fun verifyOtpRegister(serverUrl: String, email: String, code: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/auth/verify-otp-register")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("code", code.trim())
                put("password", password)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) {
                return@withContext Result.failure(Exception("Server returned an invalid response. Please try again."))
            }
            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val token = jsonRes.optString("token", "")
                if (token.isNotEmpty()) {
                    activeAuthToken = token
                }
                val message = jsonRes.optString("message", "Registered successfully!")
                return@withContext Result.success(message)
            } else {
                val err = jsonRes.optString("message", "Invalid OTP or registration failed.")
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "verifyOtpRegister error", e)
            return@withContext Result.failure(Exception(e.localizedMessage ?: "Network error"))
        }
    }

    /**
     * Verifies password reset OTP code and updates user's account password.
     * Bulletproof: logs every step, handles empty/HTML responses, extracts server error messages.
     */
    suspend fun resetPassword(serverUrl: String, email: String, code: String, newPassword: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/auth/reset-password")
            val trimmedEmail = email.trim().lowercase()
            val trimmedCode = code.trim()
            val trimmedPassword = newPassword.trim()

            Log.d("ServerApi", "resetPassword → URL: $url | email: $trimmedEmail | codeLen: ${trimmedCode.length} | passLen: ${trimmedPassword.length}")

            val json = JSONObject().apply {
                put("email", trimmedEmail)
                put("code", trimmedCode)
                put("newPassword", trimmedPassword)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d("ServerApi", "resetPassword ← HTTP ${response.code} | bodyLen: ${body.length} | body: ${body.take(500)}")

            if (body.isBlank()) {
                Log.e("ServerApi", "resetPassword: EMPTY response body from server (HTTP ${response.code})")
                return@withContext Result.failure(Exception("Server returned empty response (HTTP ${response.code}). Please try again."))
            }

            if (!body.trim().startsWith("{")) {
                Log.e("ServerApi", "resetPassword: NON-JSON response (HTTP ${response.code}): ${body.take(300)}")
                // Try to extract useful error from HTML/text response
                val lower = body.lowercase()
                val userErr = when {
                    lower.contains("expired") -> "Verification code expired. Please request a new OTP."
                    lower.contains("invalid") -> "Invalid verification code."
                    lower.contains("internal server error") -> "Server error occurred. Please try again."
                    lower.contains("timeout") || lower.contains("timed out") -> "Server timed out. Please try again."
                    lower.contains("too many") -> "Too many attempts. Please wait and try again."
                    response.code == 404 -> "Server endpoint not found. Please update the app."
                    response.code >= 500 -> "Server error (${response.code}). Please try again later."
                    else -> "Server returned invalid response (HTTP ${response.code}). Please try again."
                }
                return@withContext Result.failure(Exception(userErr))
            }

            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val message = jsonRes.optString("message", "Password reset successfully!")
                return@withContext Result.success(message)
            } else {
                val err = jsonRes.optString("message", "Failed to reset password.")
                Log.e("ServerApi", "resetPassword: server returned failure — $err")
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "resetPassword EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
            val userMsg = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "Connection timed out. Please check your internet and try again."
                e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "No internet connection. Please check your network."
                e.message?.contains("Connection refused", ignoreCase = true) == true -> "Cannot reach server. Please try again later."
                else -> e.localizedMessage ?: "Network error. Please try again."
            }
            return@withContext Result.failure(Exception(userMsg))
        }
    }

    /**
     * Authenticates user login credentials against cloud database.
     * Returns JSON object containing user email, botToken, and chatId.
     */
    suspend fun login(serverUrl: String, email: String, password: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/auth/login")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) {
                return@withContext Result.failure(Exception("Server returned an invalid response. Please try again."))
            }
            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val token = jsonRes.optString("token", "")
                if (token.isNotEmpty()) {
                    activeAuthToken = token
                }
                return@withContext Result.success(jsonRes)
            } else {
                val err = jsonRes.optString("message", "Login failed.")
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "login error", e)
            return@withContext Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    /**
     * Saves user's Telegram Bot Token and Chat ID to cloud account settings.
     */
    suspend fun saveCredentials(serverUrl: String, email: String, botToken: String, chatId: String, otpCode: String? = null): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/credentials/save")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("botToken", botToken.trim())
                put("chatId", chatId.trim())
                if (!otpCode.isNullOrEmpty()) put("otpCode", otpCode.trim())
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                return@withContext Result.success(jsonRes)
            } else {
                val err = jsonRes.optString("message", "Credentials save failed.")
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "saveCredentials error", e)
            return@withContext Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }

    /**
     * Retrieves saved Telegram Bot Token and Chat ID from cloud server database.
     */
    suspend fun getCredentials(serverUrl: String, email: String = ""): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/credentials/get")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val token = jsonRes.optString("botToken", "")
                val chat = jsonRes.optString("chatId", "")
                if (token.isNotEmpty()) {
                    return@withContext Pair(token, chat)
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "getCredentials error", e)
        }
        return@withContext null
    }

    /**
     * Syncs a newly backed up file record (filename, Telegram fileId, size, path, timestamp)
     * to the user's cloud PostgreSQL database.
     */
    /**
     * Syncs a newly backed up file record (filename, Telegram fileId, size, path, timestamp)
     * to the user's cloud PostgreSQL database.
     */
    suspend fun indexFile(
        serverUrl: String,
        email: String,
        fileName: String,
        telegramFileId: String,
        size: Long,
        path: String,
        timestamp: Long = System.currentTimeMillis(),
        messageId: Long = 0L,
        isDriveUpload: Boolean = false,
        chunksJson: String = "",
        channelId: String = "",
        botTokenUsed: String = "",
        totalChunks: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/files/index")
            val sanitizedBot = sanitizeBotId(botTokenUsed)
            val sanitizedChunks = sanitizeChunksJson(chunksJson)
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("fileName", fileName)
                put("telegramFileId", telegramFileId)
                put("size", size)
                put("path", path)
                put("timestamp", timestamp)
                put("messageId", messageId)
                put("isDriveUpload", isDriveUpload)
                if (sanitizedChunks.isNotBlank() && sanitizedChunks != "[]") put("chunksJson", sanitizedChunks)
                if (channelId.isNotBlank()) put("channelId", channelId)
                if (sanitizedBot.isNotBlank()) put("botTokenUsed", sanitizedBot)
                if (totalChunks > 1) put("totalChunks", totalChunks)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e("ServerApi", "indexFile error", e)
            return@withContext false
        }
    }

    suspend fun indexFileWithId(
        serverUrl: String,
        email: String,
        fileName: String,
        telegramFileId: String,
        size: Long,
        path: String,
        timestamp: Long,
        messageId: Long = 0L,
        isDriveUpload: Boolean = false,
        chunksJson: String = "",
        channelId: String = "",
        botTokenUsed: String = "",
        totalChunks: Int = 1
    ): Long? = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/files/index")
            val sanitizedBot = sanitizeBotId(botTokenUsed)
            val sanitizedChunks = sanitizeChunksJson(chunksJson)
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("fileName", fileName)
                put("telegramFileId", telegramFileId)
                put("size", size)
                put("path", path)
                put("timestamp", timestamp)
                put("messageId", messageId)
                put("isDriveUpload", isDriveUpload)
                if (sanitizedChunks.isNotBlank() && sanitizedChunks != "[]") put("chunksJson", sanitizedChunks)
                if (channelId.isNotBlank()) put("channelId", channelId)
                if (sanitizedBot.isNotBlank()) put("botTokenUsed", sanitizedBot)
                if (totalChunks > 1) put("totalChunks", totalChunks)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val requestBuilder = Request.Builder().url(url).post(requestBody)
            val token = activeAuthToken
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                val respJson = JSONObject(respBody)
                val id = respJson.optLong("fileRecordId", 0L)
                if (id > 0L) return@withContext id
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e("ServerApi", "indexFileWithId error", e)
            return@withContext null
        }
    }

    /**
     * Batch-syncs a list of file records to the user's cloud PostgreSQL database in a single network request.
     */
    suspend fun indexFilesBatch(
        serverUrl: String,
        email: String,
        files: List<UploadedFile>
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (files.isEmpty() || email.isBlank()) return@withContext Result.success(0)
        try {
            val url = cleanUrl(serverUrl, "/api/files/index-batch")
            var totalSynced = 0

            // Send in batches of 100 to maintain optimal payload sizes and sub-second DB execution
            for (batch in files.chunked(100)) {
                val array = org.json.JSONArray()
                for (f in batch) {
                    val sanitizedBot = sanitizeBotId(f.botTokenUsed)
                    val sanitizedChunks = sanitizeChunksJson(f.chunksJson)
                    val obj = JSONObject().apply {
                        put("fileName", f.fileName)
                        put("telegramFileId", f.telegramFileId)
                        put("size", f.size)
                        put("path", f.path)
                        put("timestamp", f.timestamp)
                        put("messageId", f.messageId)
                        put("isDriveUpload", f.isDriveUpload)
                        if (sanitizedChunks.isNotBlank() && sanitizedChunks != "[]") put("chunksJson", sanitizedChunks)
                        if (f.channelId.isNotBlank()) put("channelId", f.channelId)
                        if (sanitizedBot.isNotBlank()) put("botTokenUsed", sanitizedBot)
                        if (f.totalChunks > 1) put("totalChunks", f.totalChunks)
                    }
                    array.put(obj)
                }

                val json = JSONObject().apply {
                    put("email", email.trim().lowercase())
                    put("files", array)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(requestBody).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    totalSynced += batch.size
                }
            }
            return@withContext Result.success(totalSynced)
        } catch (e: Exception) {
            Log.e("ServerApi", "indexFilesBatch error", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Retrieves the list of backed up files from the user's cloud database account with delta sync support.
     * @param since Optional timestamp filter in ms; if > 0, fetches only records added/modified after this time.
     */
    suspend fun listFiles(serverUrl: String, email: String = "", since: Long = 0L): Result<List<UploadedFile>> = withContext(Dispatchers.IO) {
        try {
            val sinceParam = if (since > 0L) "&since=$since" else ""
            val url = cleanUrl(serverUrl, "/api/files/list?limit=10000$sinceParam")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val array = jsonRes.optJSONArray("files") ?: return@withContext Result.success(emptyList())
                val list = mutableListOf<UploadedFile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tgId = obj.optString("telegramFileId", "")
                    val pathStr = obj.optString("path", "")
                    var fName = obj.optString("fileName", "")

                    if (fName.isBlank() && pathStr.isNotBlank()) {
                        fName = pathStr.substringAfterLast('/')
                    }

                    val isDrive = obj.optBoolean("isDriveUpload", false) ||
                                 obj.optBoolean("is_drive_upload", false) ||
                                 tgId == "DIR_MARKER" ||
                                 pathStr.startsWith("Internal Storage")

                    val fSize = obj.optLong("size", 0L)
                    val rawTime = obj.optLong("timestamp", 0L)
                    val safeTime = if (rawTime < 946684800000L || rawTime == 1784030400000L) {
                        val localCandidate = if (pathStr.isNotBlank()) java.io.File(pathStr) else null
                        if (localCandidate != null && localCandidate.exists() && localCandidate.lastModified() > 946684800000L) {
                            localCandidate.lastModified()
                        } else {
                            System.currentTimeMillis()
                        }
                    } else {
                        rawTime
                    }

                    val computedChunks = if (fSize > com.example.telegrambackup.utils.FileChunker.CHUNK_SIZE) {
                        ((fSize + com.example.telegrambackup.utils.FileChunker.CHUNK_SIZE - 1) / com.example.telegrambackup.utils.FileChunker.CHUNK_SIZE).toInt()
                    } else 1
                    val resolvedChunks = maxOf(obj.optInt("totalChunks", 1), obj.optInt("total_chunks", 1), computedChunks)

                    val chunksJsonStr = obj.optString("chunksJson", "").ifBlank { obj.optString("chunks_json", "") }
                    val chId = obj.optString("channelId", "").ifBlank { obj.optString("channel_id", "") }
                    val bToken = obj.optString("botTokenUsed", "").ifBlank { obj.optString("bot_token_used", "") }

                    val entity = UploadedFile(
                        pathStr,
                        fName,
                        tgId,
                        safeTime,
                        fSize,
                        obj.optLong("messageId", 0L),
                        totalChunks = resolvedChunks
                    ).apply {
                        isDriveUpload = isDrive
                        totalChunks = resolvedChunks
                        if (chunksJsonStr.isNotBlank() && chunksJsonStr != "[]") this.chunksJson = chunksJsonStr
                        if (chId.isNotBlank()) this.channelId = chId
                        if (bToken.isNotBlank()) this.botTokenUsed = bToken
                    }
                    list.add(entity)
                }
                return@withContext Result.success(list)
            } else {
                val msg = jsonRes.optString("message", "Server returned HTTP ${response.code}")
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "listFiles error", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Triggers backend email dispatch to send the user's Master AES-256 Encryption Key
     * and file protection PIN to their registered email address.
     */
    suspend fun sendEncryptionKeyEmail(serverUrl: String, email: String, key: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/auth/send-key")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("encryptionKey", key)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!body.trim().startsWith("{")) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: Server error (verify server URL in Settings)."))
            }

            val jsonRes = JSONObject(body)

            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val msg = jsonRes.optString("message", "Security Key sent to $email!")
                return@withContext Result.success(msg)
            } else {
                val err = jsonRes.optString("message", "Failed to send email.")
                return@withContext Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "sendEncryptionKeyEmail error", e)
            return@withContext Result.failure(Exception(e.localizedMessage ?: "Network error"))
        }
    }

    /**
     * Deletes a file record from the user's cloud database index.
     */
    suspend fun deleteFile(serverUrl: String, email: String, telegramFileId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/files/delete")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("telegramFileId", telegramFileId)
                put("path", path)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) return@withContext false
            val jsonRes = JSONObject(body)
            return@withContext response.isSuccessful && jsonRes.optBoolean("success")
        } catch (e: Exception) {
            Log.e("ServerApi", "deleteFile error", e)
            return@withContext false
        }
    }

    /**
     * Persists user app settings JSON string to cloud PostgreSQL database.
     */
    suspend fun saveUserSettings(serverUrl: String, email: String, settingsJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/user/settings")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("settings", settingsJson)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) return@withContext false
            val jsonRes = JSONObject(body)
            return@withContext response.isSuccessful && jsonRes.optBoolean("success")
        } catch (e: Exception) {
            Log.e("ServerApi", "saveUserSettings error", e)
            return@withContext false
        }
    }

    /**
     * Retrieves stored user app settings JSON string from cloud database on login.
     */
    suspend fun getUserSettings(serverUrl: String, email: String = ""): String? = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/user/settings")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) return@withContext null
            val jsonRes = JSONObject(body)
            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val manifest = jsonRes.optString("manifest", "").ifEmpty { jsonRes.optString("settings", "") }
                return@withContext if (manifest.isNotEmpty()) manifest else null
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "getUserSettings error", e)
        }
        return@withContext null
    }

    /**
     * Purges file records with custom mode filters (ALL, DRIVE_ONLY, AUTO_BACKUP_ONLY, OLDER_THAN_DAYS).
     * Returns pair of (success boolean, list of messageIds to purge from Telegram Cloud).
     */
    suspend fun purgeFiles(
        serverUrl: String,
        email: String,
        mode: String = "ALL",
        olderThanDays: Int = 30
    ): Pair<Boolean, List<Long>> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/files/purge")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("mode", mode)
                put("olderThanDays", olderThanDays)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) return@withContext Pair(false, emptyList())
            val jsonRes = JSONObject(body)
            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val messageIds = mutableSetOf<Long>()
                val allMsgIdsArr = jsonRes.optJSONArray("allMessageIds")
                if (allMsgIdsArr != null) {
                    for (i in 0 until allMsgIdsArr.length()) {
                        val id = allMsgIdsArr.optLong(i, 0L)
                        if (id > 0) messageIds.add(id)
                    }
                }
                val array = jsonRes.optJSONArray("deletedItems") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val msgIdStr = obj.optString("messageId", "0")
                    val msgId = obj.optLong("messageId", 0L).let { if (it > 0) it else msgIdStr.toLongOrNull() ?: 0L }
                    if (msgId > 0) messageIds.add(msgId)
                    val chunksStr = obj.optString("chunksJson", "")
                    if (chunksStr.isNotBlank() && chunksStr != "[]") {
                        try {
                            val chunkArr = JSONArray(chunksStr)
                            for (c in 0 until chunkArr.length()) {
                                val cObj = chunkArr.optJSONObject(c)
                                val cMsgId = cObj?.optLong("messageId", 0L) ?: 0L
                                if (cMsgId > 0) messageIds.add(cMsgId)
                            }
                        } catch (_: Exception) {}
                    }
                }
                return@withContext Pair(true, messageIds.toList())
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "purgeFiles error", e)
        }
        return@withContext Pair(false, emptyList())
    }

    /**
     * Deletes user account and all cloud database records permanently.
     */
    suspend fun deleteAccount(
        serverUrl: String,
        email: String,
        password: String = ""
    ): Pair<Boolean, List<Long>> = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/user/delete-account")
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                if (password.isNotEmpty()) put("password", password)
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!body.trim().startsWith("{")) return@withContext Pair(false, emptyList())
            val jsonRes = JSONObject(body)
            if (response.isSuccessful && jsonRes.optBoolean("success")) {
                val messageIds = mutableSetOf<Long>()
                val allMsgIdsArr = jsonRes.optJSONArray("allMessageIds")
                if (allMsgIdsArr != null) {
                    for (i in 0 until allMsgIdsArr.length()) {
                        val id = allMsgIdsArr.optLong(i, 0L)
                        if (id > 0) messageIds.add(id)
                    }
                }
                val array = jsonRes.optJSONArray("deletedItems") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val msgIdStr = obj.optString("messageId", "0")
                    val msgId = obj.optLong("messageId", 0L).let { if (it > 0) it else msgIdStr.toLongOrNull() ?: 0L }
                    if (msgId > 0) messageIds.add(msgId)
                    val chunksStr = obj.optString("chunksJson", "")
                    if (chunksStr.isNotBlank() && chunksStr != "[]") {
                        try {
                            val chunkArr = JSONArray(chunksStr)
                            for (c in 0 until chunkArr.length()) {
                                val cObj = chunkArr.optJSONObject(c)
                                val cMsgId = cObj?.optLong("messageId", 0L) ?: 0L
                                if (cMsgId > 0) messageIds.add(cMsgId)
                            }
                        } catch (_: Exception) {}
                    }
                }
                return@withContext Pair(true, messageIds.toList())
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "deleteAccount error", e)
        }
        return@withContext Pair(false, emptyList())
    }

    /**
     * Normalizes Telegram chat ID / channel ID:
     * - Numerical channel IDs: e.g. -1001234567890
     * - Handle channel links: https://t.me/c/1234567890/12 -> -1001234567890
     * - Handle usernames: @mychannel
     */
    fun formatChatIdForTelegram(chatId: String): Any {
        var clean = chatId.trim().trim('"', '\'', ' ', '\t', '\n', '\r')
        if (clean.startsWith("https://t.me/c/")) {
            val after = clean.removePrefix("https://t.me/c/").substringBefore("/")
            val num = after.toLongOrNull()
            if (num != null) return if (after.startsWith("100")) "-$after".toLong() else "-100$after".toLong()
        }
        if (clean.startsWith("https://t.me/")) clean = "@" + clean.removePrefix("https://t.me/").substringBefore("/")
        if (clean.startsWith("t.me/")) clean = "@" + clean.removePrefix("t.me/").substringBefore("/")
        val num = clean.toLongOrNull()
        if (num != null) {
            // Telegram channels and supergroups have -100 prefix
            if (num > 0 && num.toString().length >= 9) {
                return if (num.toString().startsWith("100")) "-$num".toLong() else "-100$num".toLong()
            }
            if (num < 0 && !num.toString().startsWith("-100") && num.toString().length >= 10) {
                val absNum = kotlin.math.abs(num)
                return "-100$absNum".toLong()
            }
            return num
        }
        return clean
    }

    enum class TelegramDeletionStatus {
        DELETED,            // Message was present in channel and successfully deleted by Telegram
        ALREADY_DELETED,    // Message does not exist or was already deleted (400 not found)
        PERMISSION_DENIED,  // Bot lacks administrator or delete messages rights
        ERROR               // Network failure or timeout
    }

    /**
     * Executes single message deletion on Telegram Bot API with accurate status classification.
     */
    suspend fun executeTelegramMessageDeletion(botToken: String, chatId: String, messageId: Long): TelegramDeletionStatus = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val formattedChatId = formatChatIdForTelegram(chatId)
        if (cleanToken.isEmpty() || messageId <= 0) return@withContext TelegramDeletionStatus.ERROR

        try {
            val url = "https://api.telegram.org/bot$cleanToken/deleteMessage"
            val json = JSONObject().apply {
                if (formattedChatId is Long) put("chat_id", formattedChatId) else put("chat_id", formattedChatId.toString())
                put("message_id", messageId)
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            // Handle HTTP 429 Rate Limiting
            if (response.code == 429 || body.contains("TOO_MANY_REQUESTS")) {
                val retryAfterSec = try { JSONObject(body).optJSONObject("parameters")?.optInt("retry_after", 2) ?: 2 } catch (_: Exception) { 2 }
                Log.w("ServerApi", "Telegram 429 Rate Limit hit. Waiting ${retryAfterSec}s before retry...")
                kotlinx.coroutines.delay(retryAfterSec * 1000L)
                return@withContext executeTelegramMessageDeletion(cleanToken, chatId, messageId)
            }

            val jsonRes = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            val ok = jsonRes.optBoolean("ok", false)
            val desc = jsonRes.optString("description", "")

            if (ok) return@withContext TelegramDeletionStatus.DELETED

            if (desc.contains("message to delete not found", ignoreCase = true) ||
                desc.contains("MESSAGE_ID_INVALID", ignoreCase = true) ||
                desc.contains("message identifier is not specified", ignoreCase = true)) {
                return@withContext TelegramDeletionStatus.ALREADY_DELETED
            }

            if (desc.contains("not enough rights", ignoreCase = true) ||
                desc.contains("CHAT_ADMIN_REQUIRED", ignoreCase = true) ||
                desc.contains("message can't be deleted", ignoreCase = true)) {
                Log.w("ServerApi", "Bot needs Admin Delete rights in Telegram Channel: $formattedChatId. Desc: $desc")
                return@withContext TelegramDeletionStatus.PERMISSION_DENIED
            }

            // Fallback via URL query parameter
            val fallbackUrl = "https://api.telegram.org/bot$cleanToken/deleteMessage?chat_id=$formattedChatId&message_id=$messageId"
            val fallbackReq = Request.Builder().url(fallbackUrl).post("".toRequestBody(null)).build()
            val fallbackRes = client.newCall(fallbackReq).execute()
            val fallbackBody = fallbackRes.body?.string() ?: ""

            if (fallbackRes.isSuccessful && fallbackBody.contains("\"ok\":true")) {
                return@withContext TelegramDeletionStatus.DELETED
            }

            if (fallbackBody.contains("message to delete not found", ignoreCase = true) ||
                fallbackBody.contains("MESSAGE_ID_INVALID", ignoreCase = true)) {
                return@withContext TelegramDeletionStatus.ALREADY_DELETED
            }

            return@withContext TelegramDeletionStatus.ERROR
        } catch (e: Exception) {
            Log.e("ServerApi", "executeTelegramMessageDeletion error for msgId #$messageId", e)
            return@withContext TelegramDeletionStatus.ERROR
        }
    }

    /**
     * Deletes Telegram messages using INDIVIDUAL deleteMessage calls for each ID.
     * NEVER uses batch deleteMessages — batch returns ok:true even when 0 messages are deleted,
     * which caused the app to report phantom deletions while files remained in the channel.
     * Individual deleteMessage gives honest per-message results.
     * Returns Pair(allSuccess, actuallyDeletedCount).
     */
    suspend fun deleteTelegramMessagesBatch(botToken: String, chatId: String, messageIds: List<Long>): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val validIds = messageIds.filter { it > 0 }
        if (cleanToken.isEmpty() || validIds.isEmpty()) return@withContext Pair(false, 0)

        var allSuccess = true
        var totalDeletedCount = 0
        var permissionDenied = false

        // Process in chunks of 20 concurrent requests to avoid rate limits
        val chunks = validIds.chunked(20)
        for (chunk in chunks) {
            // If we already hit PERMISSION_DENIED, stop wasting API calls
            if (permissionDenied) {
                allSuccess = false
                break
            }

            coroutineScope {
                val deferreds = chunk.map { msgId ->
                    async(Dispatchers.IO) {
                        executeTelegramMessageDeletion(cleanToken, chatId, msgId)
                    }
                }
                val results = deferreds.awaitAll()
                val actuallyDeleted = results.count { it == TelegramDeletionStatus.DELETED }
                totalDeletedCount += actuallyDeleted

                if (results.any { it == TelegramDeletionStatus.PERMISSION_DENIED }) {
                    permissionDenied = true
                    allSuccess = false
                    Log.e("ServerApi", "deleteTelegramMessagesBatch: PERMISSION_DENIED — Bot cannot delete messages in this channel! Stopping.")
                }
                if (results.any { it == TelegramDeletionStatus.ERROR }) {
                    allSuccess = false
                }

                if (actuallyDeleted > 0) {
                    Log.d("ServerApi", "deleteTelegramMessagesBatch: Actually deleted $actuallyDeleted out of ${chunk.size} in this chunk (total so far: $totalDeletedCount)")
                }
            }

            // Small delay between chunks to respect Telegram rate limits
            if (chunks.size > 1) {
                kotlinx.coroutines.delay(200L)
            }
        }

        Log.d("ServerApi", "deleteTelegramMessagesBatch FINAL: $totalDeletedCount messages ACTUALLY deleted from Telegram out of ${validIds.size} attempted")
        return@withContext Pair(allSuccess, totalDeletedCount)
    }

    /**
     * Deletes a large batch of messages from a Telegram channel using all available bots in the fleet.
     * Rotates bot tokens to bypass single-bot rate limits and provides live progress callbacks.
     */
    suspend fun deleteTelegramMessagesWithFleet(
        context: android.content.Context,
        chatId: String,
        messageIds: List<Long>,
        onProgress: (deleted: Int, total: Int) -> Unit = { _, _ -> }
    ): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val validIds = messageIds.filter { it > 0 }.distinct()
        if (validIds.isEmpty()) return@withContext Pair(true, 0)

        val primaryToken = com.example.telegrambackup.utils.AppPrefs(context).botToken.trim()
        val candidateTokens = com.example.telegrambackup.utils.TelegramMediaResolver.getCandidateBotTokens(context).toMutableList()
        if (primaryToken.isNotBlank()) {
            candidateTokens.remove(primaryToken)
            candidateTokens.add(0, primaryToken)
        }
        if (candidateTokens.isEmpty()) {
            if (primaryToken.isEmpty()) return@withContext Pair(false, 0)
            candidateTokens.add(primaryToken)
        }

        var totalDeletedCount = 0
        var allSuccess = true
        val chunks = validIds.chunked(20)

        for ((chunkIdx, chunk) in chunks.withIndex()) {
            val token = candidateTokens[chunkIdx % candidateTokens.size]
            coroutineScope {
                val deferreds = chunk.map { msgId ->
                    async(Dispatchers.IO) {
                        var status = executeTelegramMessageDeletion(token, chatId, msgId)
                        // If secondary token fails with PERMISSION_DENIED or ERROR, immediately try primary token
                        if ((status == TelegramDeletionStatus.PERMISSION_DENIED || status == TelegramDeletionStatus.ERROR) && token != primaryToken && primaryToken.isNotBlank()) {
                            status = executeTelegramMessageDeletion(primaryToken, chatId, msgId)
                        }
                        status
                    }
                }
                val results = deferreds.awaitAll()
                val chunkDeleted = results.count { it == TelegramDeletionStatus.DELETED || it == TelegramDeletionStatus.ALREADY_DELETED }
                totalDeletedCount += chunkDeleted
                if (results.any { it == TelegramDeletionStatus.ERROR || it == TelegramDeletionStatus.PERMISSION_DENIED }) allSuccess = false
            }
            onProgress(totalDeletedCount, validIds.size)
            if (chunks.size > 1) {
                kotlinx.coroutines.delay(100L)
            }
        }
        return@withContext Pair(allSuccess, totalDeletedCount)
    }

    /**
     * Sends HTTP deleteMessage request directly to Telegram Bot API.
     */
    suspend fun deleteTelegramMessage(botToken: String, chatId: String, messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val status = executeTelegramMessageDeletion(botToken, chatId, messageId)
        return@withContext (status == TelegramDeletionStatus.DELETED || status == TelegramDeletionStatus.ALREADY_DELETED)
    }

    /**
     * Complete deep deletion of a file and ALL of its chunk messages from Telegram Cloud Chat & Room DB.
     * CRITICAL: Only clears Room DB records AFTER verified Telegram deletion.
     * If Telegram deletion fails (permission denied, error), DB records are PRESERVED so files aren't lost.
     */
    suspend fun purgeFileCompleteFromTelegram(
        context: android.content.Context,
        botToken: String,
        chatId: String,
        file: com.example.telegrambackup.data.UploadedFile,
        forceLocalDelete: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = file.botTokenUsed.ifBlank { botToken }.trim()
        val cleanChatId = file.channelId.ifBlank { chatId }.trim()

        val msgIds = mutableSetOf<Long>()
        if (file.messageId > 0) {
            msgIds.add(file.messageId)
        }
        val directId = file.telegramFileId.toLongOrNull()
        if (directId != null && directId > 0) {
            msgIds.add(directId)
        }
        // Include all message IDs from chunksJson
        msgIds.addAll(file.getAllMessageIds())

        // Query all chunk message IDs for this file path from Room database
        try {
            val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
            val chunks = db.chunkProgressDao().getUploadedChunksForFile(file.path)
            chunks.forEach { chunk ->
                if (chunk.messageId > 0) {
                    msgIds.add(chunk.messageId)
                }
            }
            if (msgIds.size <= 1) {
                // Fallback: check all chunks in case path normalization differed
                val allChunks = db.chunkProgressDao().getAllChunks()
                allChunks.filter { it.sourcePath.endsWith(file.fileName) || it.sourcePath.equals(file.path, ignoreCase = true) }.forEach { chunk ->
                    if (chunk.messageId > 0) {
                        msgIds.add(chunk.messageId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "Error querying chunk message IDs for ${file.path}", e)
        }

        var deletionOk = true
        var actuallyDeletedCount = 0
        var permissionBlocked = false

        if (cleanToken.isNotEmpty() && cleanChatId.isNotEmpty() && msgIds.isNotEmpty()) {
            val idList = msgIds.toList()
            Log.d("ServerApi", "Purging ${idList.size} Telegram messages for file ${file.fileName}: $idList")

            val fallbackToken = com.example.telegrambackup.utils.AppPrefs(context).botToken.trim()

            // Delete each message individually for accurate results
            for (msgId in idList) {
                var status = executeTelegramMessageDeletion(cleanToken, cleanChatId, msgId)
                if ((status == TelegramDeletionStatus.PERMISSION_DENIED || status == TelegramDeletionStatus.ERROR) && fallbackToken.isNotBlank() && fallbackToken != cleanToken) {
                    status = executeTelegramMessageDeletion(fallbackToken, cleanChatId, msgId)
                }
                when (status) {
                    TelegramDeletionStatus.DELETED -> {
                        actuallyDeletedCount++
                        Log.d("ServerApi", "✅ Message #$msgId ACTUALLY deleted from Telegram channel")
                    }
                    TelegramDeletionStatus.ALREADY_DELETED -> {
                        Log.d("ServerApi", "⚪ Message #$msgId already gone from Telegram (not found)")
                    }
                    TelegramDeletionStatus.PERMISSION_DENIED -> {
                        permissionBlocked = true
                        deletionOk = false
                        Log.e("ServerApi", "🚫 PERMISSION DENIED: Bot cannot delete message #$msgId — file REMAINS in Telegram channel!")
                    }
                    TelegramDeletionStatus.ERROR -> {
                        deletionOk = false
                        Log.e("ServerApi", "❌ Error deleting message #$msgId from Telegram")
                    }
                }
            }
            Log.d("ServerApi", "purgeFileComplete result for ${file.fileName}: $actuallyDeletedCount ACTUALLY deleted, permissionBlocked=$permissionBlocked, totalMsgIds=${idList.size}")
        }

        // Clean local preview cache
        try {
            val previewCacheDir = java.io.File(context.cacheDir, "cloud_preview_cache")
            if (previewCacheDir.exists()) {
                val safeName = file.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                previewCacheDir.listFiles()?.filter { 
                    it.name.contains(file.telegramFileId.hashCode().toString()) || 
                    (safeName.isNotEmpty() && it.name.endsWith(safeName)) 
                }?.forEach { it.delete() }
            }
        } catch (_: Exception) {}

        // Clear Room DB records if Telegram deletion was NOT blocked or forceLocalDelete is requested
        if (!permissionBlocked || forceLocalDelete) {
            try {
                val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                db.chunkProgressDao().deleteChunksForFile(file.path)
                db.uploadedFileDao().deleteByPath(file.path)
                if (file.telegramFileId.isNotEmpty()) {
                    db.uploadedFileDao().deleteByTelegramFileId(file.telegramFileId)
                }
                Log.d("ServerApi", "Cleared Room DB records for ${file.fileName} (Telegram deletion verified or forceLocalDelete=$forceLocalDelete)")
            } catch (e: Exception) {
                Log.e("ServerApi", "Error deleting local DB record for ${file.path}", e)
            }
        } else {
            Log.e("ServerApi", "⚠️ KEEPING Room DB records for ${file.fileName} because Telegram deletion was BLOCKED (permission denied). File still exists in channel!")
        }

        return@withContext deletionOk
    }

    /**
     * Complete Telegram Channel Storage Cleanup.
     * 1. Verifies channel administrator delete permissions.
     * 2. Deletes all explicit message IDs provided (from local database & cloud database).
     * 3. If purgeAllRemaining is true, performs a sequential top-down scan from the channel's highest message_id
     *    down to 1, deleting all remaining uploaded files/messages.
     * Returns Triple(actuallyDeletedCount, totalScannedCount, hasPermissionError).
     */
    suspend fun cleanTelegramChannelStorage(
        context: android.content.Context,
        botToken: String,
        chatId: String,
        explicitMessageIds: List<Long> = emptyList(),
        purgeAllRemaining: Boolean = false,
        onProgress: (scanned: Int, total: Int, deleted: Int, status: String) -> Unit = { _, _, _, _ -> }
    ): Triple<Int, Int, Boolean> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val targetChatId = formatChatIdForTelegram(chatId).toString()
        if (cleanToken.isEmpty() || chatId.trim().isEmpty()) {
            return@withContext Triple(0, 0, false)
        }

        // 1. Verify bot can delete messages in this channel
        onProgress(0, 0, 0, "Checking bot channel administrator rights...")
        val canDelete = verifyTelegramDeletionWorks(cleanToken, targetChatId)
        if (!canDelete) {
            Log.e("ServerApi", "cleanTelegramChannelStorage: Bot lacks delete permission in channel $targetChatId")
            return@withContext Triple(0, 0, true) // hasPermissionError = true
        }

        var totalDeleted = 0
        val deletedMsgIds = mutableSetOf<Long>()
        val validExplicitIds = explicitMessageIds.filter { it > 0 }.distinct()

        // 2. Delete explicitly known message IDs first
        if (validExplicitIds.isNotEmpty()) {
            onProgress(0, validExplicitIds.size, 0, "Deleting ${validExplicitIds.size} tracked files from Telegram...")
            val chunks = validExplicitIds.chunked(25)
            for (chunk in chunks) {
                coroutineScope {
                    val deferreds = chunk.map { msgId ->
                        async(Dispatchers.IO) {
                            Pair(msgId, executeTelegramMessageDeletion(cleanToken, targetChatId, msgId))
                        }
                    }
                    val results = deferreds.awaitAll()
                    for ((msgId, status) in results) {
                        if (status == TelegramDeletionStatus.DELETED) {
                            totalDeleted++
                            deletedMsgIds.add(msgId)
                        } else if (status == TelegramDeletionStatus.ALREADY_DELETED) {
                            deletedMsgIds.add(msgId)
                        }
                    }
                }
                onProgress(deletedMsgIds.size, validExplicitIds.size, totalDeleted, "Deleting tracked files: $totalDeleted / ${validExplicitIds.size}...")
                kotlinx.coroutines.delay(100L)
            }
        }

        // 3. If purgeAllRemaining is requested (e.g. Wipe All or empty DB), scan from channel top down to 1
        var totalScanned = validExplicitIds.size
        if (purgeAllRemaining) {
            onProgress(totalScanned, totalScanned, totalDeleted, "Discovering latest channel messages...")
            val pingId = getHighestTelegramMessageId(cleanToken, targetChatId)
            val currentHighest = if (pingId > 0) pingId + 5L else 5000L
            val estimatedTotal = currentHighest.toInt()

            Log.d("ServerApi", "cleanTelegramChannelStorage: Sweeping remaining channel messages from #$currentHighest down to 1...")
            var currentTop = currentHighest
            val chunkSize = 25
            var consecutiveMisses = 0
            val maxConsecutiveMisses = 350

            while (currentTop > 0) {
                val chunkStart = maxOf(1L, currentTop - chunkSize + 1)
                val chunkIds = (currentTop downTo chunkStart).filter { !deletedMsgIds.contains(it) }

                if (chunkIds.isNotEmpty()) {
                    coroutineScope {
                        val deferreds = chunkIds.map { msgId ->
                            async(Dispatchers.IO) {
                                Pair(msgId, executeTelegramMessageDeletion(cleanToken, targetChatId, msgId))
                            }
                        }
                        val results = deferreds.awaitAll()
                        for ((msgId, status) in results) {
                            totalScanned++
                            when (status) {
                                TelegramDeletionStatus.DELETED -> {
                                    totalDeleted++
                                    deletedMsgIds.add(msgId)
                                    consecutiveMisses = 0
                                }
                                TelegramDeletionStatus.ALREADY_DELETED -> {
                                    deletedMsgIds.add(msgId)
                                    consecutiveMisses++
                                }
                                TelegramDeletionStatus.PERMISSION_DENIED -> {
                                    Log.e("ServerApi", "Permission denied during channel sweep at msgId #$msgId")
                                    return@coroutineScope
                                }
                                TelegramDeletionStatus.ERROR -> {
                                    consecutiveMisses++
                                }
                            }
                        }
                    }
                }

                onProgress(totalScanned, estimatedTotal, totalDeleted, "Scanning channel: $totalDeleted files purged ($totalScanned / $estimatedTotal)...")

                currentTop = chunkStart - 1
                kotlinx.coroutines.delay(60L)
            }
        }

        Log.d("ServerApi", "cleanTelegramChannelStorage COMPLETE: $totalDeleted messages deleted ($totalScanned scanned)")
        return@withContext Triple(totalDeleted, totalScanned, false)
    }

    /**
     * Purge an unlisted file or path from Telegram Cloud Chat even if no UploadedFile record exists in Room DB.
     * Only clears Room DB records after verified Telegram deletion or if forceLocalDelete is true.
     */
    suspend fun purgeUnlistedPathFromTelegram(
        context: android.content.Context,
        botToken: String,
        chatId: String,
        path: String,
        telegramFileId: String = "",
        forceLocalDelete: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        if (cleanToken.isEmpty() || cleanChatId.isEmpty()) return@withContext false

        val msgIds = mutableSetOf<Long>()
        val directId = telegramFileId.toLongOrNull()
        if (directId != null && directId > 0) {
            msgIds.add(directId)
        }

        try {
            val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
            val file = db.uploadedFileDao().getFileByPath(path)
            if (file != null) {
                if (file.messageId > 0) msgIds.add(file.messageId)
                file.telegramFileId.toLongOrNull()?.let { if (it > 0) msgIds.add(it) }
            }
            val chunks = db.chunkProgressDao().getUploadedChunksForFile(path)
            chunks.forEach { chunk ->
                if (chunk.messageId > 0) msgIds.add(chunk.messageId)
            }
        } catch (_: Exception) {}

        var ok = true
        var permissionBlocked = false
        if (msgIds.isNotEmpty()) {
            for (msgId in msgIds) {
                val status = executeTelegramMessageDeletion(cleanToken, cleanChatId, msgId)
                when (status) {
                    TelegramDeletionStatus.DELETED -> Log.d("ServerApi", "✅ Purged unlisted message #$msgId from Telegram")
                    TelegramDeletionStatus.ALREADY_DELETED -> Log.d("ServerApi", "⚪ Unlisted message #$msgId already gone")
                    TelegramDeletionStatus.PERMISSION_DENIED -> {
                        permissionBlocked = true
                        ok = false
                        Log.e("ServerApi", "🚫 PERMISSION DENIED: Cannot delete unlisted message #$msgId")
                    }
                    TelegramDeletionStatus.ERROR -> {
                        ok = false
                        Log.e("ServerApi", "❌ Error deleting unlisted message #$msgId")
                    }
                }
            }
        }

        // Clear DB records if Telegram didn't block the deletion or forceLocalDelete is requested
        if (!permissionBlocked || forceLocalDelete) {
            try {
                val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                db.chunkProgressDao().deleteChunksForFile(path)
                db.uploadedFileDao().deleteByPath(path)
                if (telegramFileId.isNotEmpty()) {
                    db.uploadedFileDao().deleteByTelegramFileId(telegramFileId)
                }
            } catch (_: Exception) {}
        } else {
            Log.e("ServerApi", "⚠️ KEEPING DB records for $path — Telegram deletion was BLOCKED")
        }

        return@withContext ok
    }

    data class TelegramChatInfo(
        val success: Boolean,
        val canonicalId: Long? = null,
        val title: String = "",
        val type: String = "",
        val isBotAdmin: Boolean = false,
        val canDeleteMessages: Boolean = false,
        val errorMessage: String = ""
    )

    /**
     * Inspects the Telegram Channel / Chat, retrieves official canonical ID, title, and verifies bot Admin Delete rights.
     */
    suspend fun getTelegramChatInfo(botToken: String, rawChatId: String): TelegramChatInfo = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val formattedChatId = formatChatIdForTelegram(rawChatId)
        if (cleanToken.isEmpty()) return@withContext TelegramChatInfo(false, errorMessage = "Bot token is empty")

        try {
            // 1. Query getChat
            val chatUrl = "https://api.telegram.org/bot$cleanToken/getChat?chat_id=$formattedChatId"
            val chatReq = Request.Builder().url(chatUrl).build()
            val chatRes = client.newCall(chatReq).execute()
            val chatBody = chatRes.body?.string() ?: ""

            if (!chatRes.isSuccessful || !chatBody.contains("\"ok\":true")) {
                val errorDesc = try { JSONObject(chatBody).optString("description", "Chat not found") } catch (_: Exception) { "Chat not found" }
                return@withContext TelegramChatInfo(false, errorMessage = errorDesc)
            }

            val chatJson = JSONObject(chatBody).optJSONObject("result")
            val canonicalId = chatJson?.optLong("id")
            val title = chatJson?.optString("title", "") ?: ""
            val type = chatJson?.optString("type", "") ?: ""

            // 2. Query getMe to get bot's ID
            val meUrl = "https://api.telegram.org/bot$cleanToken/getMe"
            val meReq = Request.Builder().url(meUrl).build()
            val meRes = client.newCall(meReq).execute()
            val meBody = meRes.body?.string() ?: ""
            val botId = try { JSONObject(meBody).optJSONObject("result")?.optLong("id") } catch (_: Exception) { null }

            var isBotAdmin = false
            var canDeleteMessages = false

            if (botId != null && canonicalId != null) {
                // 3. Query getChatMember to check admin delete rights
                val memberUrl = "https://api.telegram.org/bot$cleanToken/getChatMember?chat_id=$canonicalId&user_id=$botId"
                val memberReq = Request.Builder().url(memberUrl).build()
                val memberRes = client.newCall(memberReq).execute()
                val memberBody = memberRes.body?.string() ?: ""

                if (memberBody.contains("\"ok\":true")) {
                    val memberObj = JSONObject(memberBody).optJSONObject("result")
                    val status = memberObj?.optString("status", "") ?: ""
                    isBotAdmin = status == "administrator" || status == "creator"
                    canDeleteMessages = isBotAdmin && (memberObj?.optBoolean("can_delete_messages", true) ?: true)
                }
            }

            return@withContext TelegramChatInfo(
                success = true,
                canonicalId = canonicalId,
                title = title,
                type = type,
                isBotAdmin = isBotAdmin,
                canDeleteMessages = canDeleteMessages
            )
        } catch (e: Exception) {
            return@withContext TelegramChatInfo(false, errorMessage = e.localizedMessage ?: "Network error")
        }
    }

    /**
     * Sends a temporary ping message to Telegram chat/channel to discover current highest message_id.
     */
    suspend fun getHighestTelegramMessageId(botToken: String, chatId: String): Long = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val formattedChatId = formatChatIdForTelegram(chatId)
        if (cleanToken.isEmpty()) return@withContext 0L
        try {
            val url = "https://api.telegram.org/bot$cleanToken/sendMessage"
            val json = JSONObject().apply {
                if (formattedChatId is Long) put("chat_id", formattedChatId) else put("chat_id", formattedChatId.toString())
                put("text", "🧹 Telegram Storage Deep Cleanup Scanner...")
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (body.contains("\"ok\":true")) {
                val jsonRes = JSONObject(body)
                val resultObj = jsonRes.optJSONObject("result")
                val highestId = resultObj?.optLong("message_id", 0L) ?: 0L
                if (highestId > 0) {
                    deleteTelegramMessage(cleanToken, chatId, highestId)
                    return@withContext highestId
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "getHighestTelegramMessageId error", e)
        }
        return@withContext 0L
    }

    /**
     * Sends a test message, then deletes it, to VERIFY the bot can actually delete messages in this channel.
     * Returns true if the bot successfully created AND deleted a test message.
     */
    suspend fun verifyTelegramDeletionWorks(botToken: String, chatId: String): Boolean = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val formattedChatId = formatChatIdForTelegram(chatId)
        if (cleanToken.isEmpty()) return@withContext false

        try {
            // Step 1: Send a test message
            val sendUrl = "https://api.telegram.org/bot$cleanToken/sendMessage"
            val sendJson = JSONObject().apply {
                if (formattedChatId is Long) put("chat_id", formattedChatId) else put("chat_id", formattedChatId.toString())
                put("text", "🔬 Deletion verification test — this message should disappear immediately")
            }
            val sendBody = sendJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val sendReq = Request.Builder().url(sendUrl).post(sendBody).build()
            val sendRes = client.newCall(sendReq).execute()
            val sendResBody = sendRes.body?.string() ?: ""

            if (!sendResBody.contains("\"ok\":true")) {
                Log.e("ServerApi", "verifyDeletion: Failed to send test message: $sendResBody")
                return@withContext false
            }

            val testMsgId = try {
                JSONObject(sendResBody).optJSONObject("result")?.optLong("message_id", 0L) ?: 0L
            } catch (_: Exception) { 0L }

            if (testMsgId <= 0) {
                Log.e("ServerApi", "verifyDeletion: Could not extract test message_id")
                return@withContext false
            }

            // Step 2: Delete the test message using individual deleteMessage
            val status = executeTelegramMessageDeletion(cleanToken, chatId, testMsgId)
            Log.d("ServerApi", "verifyDeletion: test message #$testMsgId deletion result = $status")

            if (status == TelegramDeletionStatus.DELETED) {
                Log.d("ServerApi", "✅ VERIFIED: Bot CAN delete messages in this channel")
                return@withContext true
            } else {
                Log.e("ServerApi", "🚫 VERIFICATION FAILED: Bot CANNOT delete messages! Status=$status")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "verifyDeletion error", e)
            return@withContext false
        }
    }

    /**
     * Deep-scans and purges ALL messages/files in Telegram Bot Chat/Channel up to maxMessageId.
     * Uses INDIVIDUAL deleteMessage calls (never batch) for honest, verified deletion counts.
     * Runs a deletion verification test before starting the scan.
     * Returns Pair(totalDeletedCount, totalScannedCount).
     */
    /**
     * Deep-scans and purges ALL messages/files in Telegram Bot Chat/Channel in sequential descending loop.
     * Top-down scanning from highestId downwards. If 200 consecutive message IDs contain no files,
     * the loop automatically terminates early since all older storage is empty.
     * Uses INDIVIDUAL deleteMessage calls for honest, verified deletion counts.
     * Returns Pair(totalDeletedCount, totalScannedCount).
     */
    suspend fun deepPurgeTelegramChatStorage(
        botToken: String,
        chatId: String,
        knownMaxId: Long = 0L,
        consecutiveMissLimit: Int = 200,
        onProgress: (Int, Int, Int) -> Unit = { _, _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        if (cleanToken.isEmpty()) return@withContext Pair(0, 0)

        // 1. Resolve canonical chat ID and verify channel status
        val chatInfo = getTelegramChatInfo(cleanToken, chatId)
        val targetChatId = if (chatInfo.canonicalId != null) chatInfo.canonicalId.toString() else chatId

        // 2. VERIFY deletion actually works before scanning
        Log.d("ServerApi", "deepPurge: Running deletion verification test for channel '${chatInfo.title}' (${targetChatId})...")
        val canDelete = verifyTelegramDeletionWorks(cleanToken, targetChatId)
        if (!canDelete) {
            Log.e("ServerApi", "deepPurge: ❌ ABORTED — Bot cannot delete messages in this channel. Ensure bot is Admin with 'Delete Messages' right.")
            return@withContext Pair(-1, 0) // Return -1 to signal permission failure to UI
        }

        // 3. Get highest message ID via ping
        val pingId = getHighestTelegramMessageId(cleanToken, targetChatId)
        val highestId = if (pingId > 0) {
            pingId + 10L
        } else {
            maxOf(knownMaxId + 50L, 5000L)
        }

        Log.d("ServerApi", "deepPurge: Starting sequential top-down scan from ID $highestId down to 1 (miss limit=$consecutiveMissLimit)")

        var currentTopId = highestId
        var totalDeleted = 0
        var totalScanned = 0
        var consecutiveMisses = 0
        var permissionFailed = false
        val chunkSize = 20

        while (currentTopId > 0 && !permissionFailed) {
            val chunkStart = maxOf(1L, currentTopId - chunkSize + 1)
            val chunkIds = (currentTopId downTo chunkStart).toList()
            if (chunkIds.isEmpty()) break

            var chunkDeletedCount = 0

            try {
                coroutineScope {
                    val deferreds = chunkIds.map { msgId ->
                        async(Dispatchers.IO) {
                            Pair(msgId, executeTelegramMessageDeletion(cleanToken, targetChatId, msgId))
                        }
                    }
                    val results = deferreds.awaitAll()

                    for ((msgId, status) in results) {
                        totalScanned++
                        when (status) {
                            TelegramDeletionStatus.DELETED -> {
                                totalDeleted++
                                chunkDeletedCount++
                                consecutiveMisses = 0 // Reset miss counter when a real file is found & deleted
                                Log.d("ServerApi", "deepPurge: ✅ Deleted unlisted message #$msgId (total deleted: $totalDeleted)")
                            }
                            TelegramDeletionStatus.ALREADY_DELETED -> {
                                consecutiveMisses++
                            }
                            TelegramDeletionStatus.PERMISSION_DENIED -> {
                                permissionFailed = true
                                Log.e("ServerApi", "deepPurge: PERMISSION DENIED on message #$msgId — stopping scan")
                                break
                            }
                            TelegramDeletionStatus.ERROR -> {
                                consecutiveMisses++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerApi", "deepPurge chunk error at ID $currentTopId", e)
            }

            val estimatedTotal = highestId.toInt()
            onProgress(totalScanned, estimatedTotal, totalDeleted)

            if (permissionFailed) {
                return@withContext Pair(-1, totalScanned)
            }

            // Early exit condition: if next consecutiveMissLimit loop checks found no single file
            if (totalDeleted > 0 && consecutiveMisses >= consecutiveMissLimit) {
                Log.d("ServerApi", "deepPurge: Reached $consecutiveMisses consecutive empty slots after deleting $totalDeleted files. Channel is fully clear! Stopping loop.")
                break
            } else if (totalDeleted == 0 && consecutiveMisses >= (consecutiveMissLimit * 2)) {
                // If channel is completely empty from top, stop after 400 empty checks
                Log.d("ServerApi", "deepPurge: Scanned $consecutiveMisses empty IDs with 0 files found. Channel already clean. Stopping loop.")
                break
            }

            currentTopId = chunkStart - 1

            // Rate limit safety
            if (totalScanned % 100 < chunkSize) {
                kotlinx.coroutines.delay(200L)
            }
        }

        Log.d("ServerApi", "deepPurge COMPLETE: $totalDeleted unlisted messages ACTUALLY deleted from Telegram out of $totalScanned scanned IDs")
        return@withContext Pair(totalDeleted, totalScanned)
    }

    /**
     * Sends a text message to a Telegram chat / channel.
     */
    suspend fun sendTelegramMessage(botToken: String, chatId: String, text: String): Long? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val form = okhttp3.FormBody.Builder()
                .add("chat_id", chatId.trim())
                .add("text", text)
                .build()
            val request = Request.Builder().url(url).post(form).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    return@withContext json.optJSONObject("result")?.optLong("message_id")
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "sendTelegramMessage error", e)
        }
        return@withContext null
    }

    /**
     * Queries recent messages from a Telegram channel / bot updates.
     */
    suspend fun getRecentChannelMessages(botToken: String, chatId: String, limit: Int = 30): List<JSONObject> = withContext(Dispatchers.IO) {
        val list = mutableListOf<JSONObject>()
        try {
            val url = "https://api.telegram.org/bot$botToken/getUpdates?limit=${limit.coerceIn(1, 100)}"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    val results = json.optJSONArray("result") ?: JSONArray()
                    for (i in 0 until results.length()) {
                        val update = results.optJSONObject(i) ?: continue
                        val msg = update.optJSONObject("channel_post") 
                            ?: update.optJSONObject("message")
                            ?: update.optJSONObject("edited_message")
                        if (msg != null) {
                            list.add(msg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "getRecentChannelMessages error", e)
        }
        return@withContext list
    }

    /**
     * Creates an authoritative, ultra-secure cloud file sharing link on the server.
     * Strictly requires fileRecordId and authorization token.
     * Excludes bot tokens and encryption keys to ensure zero-knowledge server payload.
     */
    suspend fun createSecureShareLink(
        serverUrl: String,
        fileRecordId: Long,
        pin: String = "",
        expiresAt: Long = 0L
    ): ShareLinkServerResult = withContext(Dispatchers.IO) {
        try {
            val url = cleanUrl(serverUrl, "/api/share/create")
            val json = JSONObject().apply {
                put("fileRecordId", fileRecordId)
                if (pin.isNotBlank()) put("pin", pin.trim())
                if (expiresAt > 0L) put("expiresAt", expiresAt)
            }

            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val requestBuilder = Request.Builder().url(url).post(body)
            val token = activeAuthToken
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val respJson = JSONObject(respBody)
                if (respJson.optBoolean("success")) {
                    return@withContext ShareLinkServerResult(
                        success = true,
                        shareId = respJson.optString("shareId"),
                        shareUrl = respJson.optString("shareUrl"),
                        directDownloadUrl = respJson.optString("directDownloadUrl"),
                        expiresAt = respJson.optLong("expiresAt"),
                        hasPin = respJson.optBoolean("hasPin")
                    )
                }
            }
            val err = try {
                val j = JSONObject(respBody)
                j.optString("message", "HTTP ${response.code}")
            } catch (_: Exception) {
                "HTTP ${response.code}"
            }
            return@withContext ShareLinkServerResult(success = false, message = err)
        } catch (e: Exception) {
            Log.e("ServerApi", "createSecureShareLink failed", e)
            return@withContext ShareLinkServerResult(success = false, message = e.message ?: "Network error")
        }
    }
}

data class ShareLinkServerResult(
    val success: Boolean,
    val shareId: String = "",
    val shareUrl: String = "",
    val directDownloadUrl: String = "",
    val expiresAt: Long = 0L,
    val hasPin: Boolean = false,
    val message: String = ""
)
