package com.example.telegrambackup.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class UserSession(
    val email: String = "",
    val isLoggedIn: Boolean = false
)

class AuthRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val appPrefs = AppPrefs(context)
    private val serverApi = ServerApi()

    private val _session = MutableStateFlow(
        UserSession(
            email = prefs.getString("user_email", "") ?: "",
            isLoggedIn = prefs.getBoolean("is_logged_in", false) || (prefs.getString("user_email", "")?.isNotBlank() == true && appPrefs.botToken.isNotBlank())
        )
    )
    val session: StateFlow<UserSession> = _session

    init {
        val savedToken = appPrefs.authToken
        if (savedToken.isNotBlank() && ServerApi.activeAuthToken.isBlank()) {
            ServerApi.activeAuthToken = savedToken
        }
    }

    fun refreshSession() {
        val email = prefs.getString("user_email", "") ?: ""
        val loggedIn = prefs.getBoolean("is_logged_in", false) || (email.isNotBlank() && appPrefs.botToken.isNotBlank())
        _session.value = UserSession(email = email, isLoggedIn = loggedIn)
    }

    suspend fun requestOtp(email: String, type: String = "register"): Result<String> {
        return serverApi.sendOtp(appPrefs.serverUrl, email, type)
    }

    suspend fun registerWithOtp(email: String, code: String, password: String): Result<String> {
        val trimmedEmail = email.trim().lowercase()
        val result = serverApi.verifyOtpRegister(appPrefs.serverUrl, trimmedEmail, code, password)
        result.onSuccess {
            val token = ServerApi.activeAuthToken
            if (token.isNotBlank()) {
                appPrefs.authToken = token
            }
            prefs.edit()
                .putString("user_email", trimmedEmail)
                .putString("user_password", password)
                .putBoolean("is_logged_in", true)
                .apply()
            appPrefs.userEmail = trimmedEmail
            _session.value = UserSession(email = trimmedEmail, isLoggedIn = true)

            // Auto-enable backup for new accounts
            appPrefs.autoBackupEnabled = true

            // Restore complete profile, encryption key, bot fleet & settings from cloud
            try {
                val savedSettingsStr = serverApi.getUserSettings(appPrefs.serverUrl, trimmedEmail)
                if (!savedSettingsStr.isNullOrEmpty() && savedSettingsStr != "{}") {
                    com.example.telegrambackup.utils.CloudProfileSyncManager.applyConfigManifestJson(context, savedSettingsStr)
                }
                com.example.telegrambackup.utils.CloudProfileSyncManager.autoRestoreConfigFromCloud(context)
            } catch (_: Exception) {}

            // If account has no bot credentials yet, set default fallback cloud storage bot credentials
            if (appPrefs.botToken.isEmpty() || appPrefs.chatId.isEmpty()) {
                val defaultToken = decodeSecret("UkVEQUNURURfRkFMTEJBQ0tfVEVMRUdSQU1fQk9UX1RPS0VOXzAxMDAwMDAw")
                val defaultChat = decodeSecret("UkVEQUNURURfQ0hBVF9J")
                appPrefs.botToken = defaultToken
                appPrefs.chatId = defaultChat
                appPrefs.credentialsVerified = true
            }

            // Immediately push credentials to server PostgreSQL database for user account
            try {
                serverApi.saveCredentials(appPrefs.serverUrl, trimmedEmail, appPrefs.botToken, appPrefs.chatId)
            } catch (_: Exception) { }

            // Restore full cloud file index into Room DB synchronously
            try {
                com.example.telegrambackup.utils.SettingsDbManager.restoreCloudFileIndexToRoom(context)
            } catch (_: Exception) {}
        }
        return result
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<String> {
        val trimmedEmail = email.trim().lowercase()
        return serverApi.resetPassword(
            serverUrl = appPrefs.serverUrl,
            email = trimmedEmail,
            code = code.trim(),
            newPassword = newPassword.trim()
        )
    }

    suspend fun login(email: String, password: String): Result<String> {
        val trimmedEmail = email.trim().lowercase()
        val result = serverApi.login(appPrefs.serverUrl, trimmedEmail, password)
        if (result.isSuccess) {
            val json = result.getOrNull()
            val token = json?.optString("token", "") ?: ""
            if (token.isNotBlank()) {
                appPrefs.authToken = token
                ServerApi.activeAuthToken = token
            }
            val userObj = json?.optJSONObject("user")
            val serverBotToken = userObj?.optString("botToken", "") ?: json?.optString("botToken", "") ?: ""
            val serverChatId = userObj?.optString("chatId", "") ?: json?.optString("chatId", "") ?: ""

            // 1. Establish session & save email immediately
            prefs.edit()
                .putString("user_email", trimmedEmail)
                .putString("user_password", password)
                .putBoolean("is_logged_in", true)
                .apply()
            appPrefs.userEmail = trimmedEmail

            // 2. Restore complete profile, master encryption key, bot fleet, channel routes, and subsettings
            try {
                val savedSettingsStr = serverApi.getUserSettings(appPrefs.serverUrl, trimmedEmail)
                if (!savedSettingsStr.isNullOrEmpty() && savedSettingsStr != "{}") {
                    com.example.telegrambackup.utils.CloudProfileSyncManager.applyConfigManifestJson(context, savedSettingsStr)
                }
                com.example.telegrambackup.utils.CloudProfileSyncManager.autoRestoreConfigFromCloud(context)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Cloud settings restore error", e)
            }

            // 3. If bot token / chat ID are still empty after manifest restore, check server botToken
            if (appPrefs.botToken.isEmpty() && serverBotToken.isNotEmpty()) {
                appPrefs.botToken = serverBotToken
                appPrefs.credentialsVerified = true
            }
            if (appPrefs.chatId.isEmpty() && serverChatId.isNotEmpty()) {
                appPrefs.chatId = serverChatId
            }

            // 4. Fallback default cloud storage bot credentials ONLY if completely unconfigured
            if (appPrefs.botToken.isEmpty() || appPrefs.chatId.isEmpty()) {
                val defaultToken = decodeSecret("UkVEQUNURURfRkFMTEJBQ0tfVEVMRUdSQU1fQk9UX1RPS0VOXzAxMDAwMDAw")
                val defaultChat = decodeSecret("UkVEQUNURURfQ0hBVF9J")
                appPrefs.botToken = defaultToken
                appPrefs.chatId = defaultChat
                appPrefs.credentialsVerified = true
            }

            // Dual Sync: Push active credentials to server PostgreSQL DB
            try {
                serverApi.saveCredentials(appPrefs.serverUrl, trimmedEmail, appPrefs.botToken, appPrefs.chatId)
            } catch (_: Exception) { }

            // 5. Restore full cloud file index into Room DB synchronously
            try {
                com.example.telegrambackup.utils.SettingsDbManager.restoreCloudFileIndexToRoom(context)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Cloud files restore on login error", e)
            }

            _session.value = UserSession(email = trimmedEmail, isLoggedIn = true)
            return Result.success("Logged in successfully!")
        } else {
            return Result.failure(result.exceptionOrNull() ?: Exception("Login failed."))
        }
    }

    fun registerLocalQuick(email: String, password: String): Result<String> {
        val trimmedEmail = email.trim().lowercase()
        prefs.edit()
            .putString("user_email", trimmedEmail)
            .putBoolean("is_logged_in", true)
            .apply()

        _session.value = UserSession(email = trimmedEmail, isLoggedIn = true)
        return Result.success("Signed in successfully!")
    }

    fun logout() {
        prefs.edit()
            .putBoolean("is_logged_in", false)
            .remove("user_email")
            .remove("user_password")
            .apply()

        // Clear cached credentials & wipe local Room cache to prevent cross-account pollution
        appPrefs.authToken = ""
        ServerApi.activeAuthToken = ""
        appPrefs.botToken = ""
        appPrefs.chatId = ""
        appPrefs.credentialsVerified = false

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getDatabase(context).uploadedFileDao().deleteAll()
            } catch (_: Exception) { }
        }

        _session.value = UserSession(email = "", isLoggedIn = false)
    }

    suspend fun ensureAuthenticated(): Boolean {
        if (ServerApi.activeAuthToken.isNotBlank()) return true
        val savedToken = appPrefs.authToken
        if (savedToken.isNotBlank()) {
            ServerApi.activeAuthToken = savedToken
            return true
        }
        val email = prefs.getString("user_email", "") ?: ""
        val pass = prefs.getString("user_password", "") ?: ""
        if (email.isNotBlank() && pass.isNotBlank()) {
            try {
                val loginRes = serverApi.login(appPrefs.serverUrl, email, pass)
                if (loginRes.isSuccess) {
                    val token = loginRes.getOrNull()?.optString("token", "") ?: ""
                    if (token.isNotBlank()) {
                        appPrefs.authToken = token
                        ServerApi.activeAuthToken = token
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun decodeSecret(encoded: String): String {
        return try {
            String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}
