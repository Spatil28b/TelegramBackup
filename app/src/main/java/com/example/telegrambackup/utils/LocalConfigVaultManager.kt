package com.example.telegrambackup.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AppSettingEntity
import com.example.telegrambackup.data.TelegramBotEntity
import com.example.telegrambackup.data.TelegramChannelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * =====================================================================================
 * LOCAL CONFIG VAULT MANAGER (ZERO-LOSS PERSISTENCE ACROSS UNINSTALLS)
 * =====================================================================================
 * Because the application has MANAGE_EXTERNAL_STORAGE permission, external storage
 * directories (/sdcard/Documents/TelegramBackup/ and /sdcard/Download/TelegramBackup/)
 * ARE PERMANENT AND ARE NEVER WIPED BY ANDROID ON APP UNINSTALL.
 *
 * This manager continuously maintains an encrypted/structured snapshot of:
 * 1. User Account & Profile (email, recovery email, display name, avatar)
 * 2. Master AES-256 Encryption Key & Security Preferences
 * 3. Complete Bot Fleet Pool (all bot tokens and configurations)
 * 4. Channel Routing Rules (all channel chat IDs and categories)
 * 5. Google Gemini AI API Keys, Selected Models & OCR Scanner settings
 * 6. Detailed Backup Rules, Filters, Size Limits & WiFi-only conditions
 * 7. Customization Settings (App Icon, Theme, Starred Files, Safe Vault Files)
 *
 * When the app is reinstalled after an uninstall, this vault immediately and
 * transparently restores everything on first app launch before network requests!
 * =====================================================================================
 */
object LocalConfigVaultManager {
    private const val TAG = "LocalConfigVault"
    private const val VAULT_VERSION = 3
    private const val HIDDEN_FILE_NAME = ".tspace_vault_config.json"
    private const val BACKUP_FILE_NAME = "tspace_config_backup.json"

    private fun getStorageDirs(): List<File> {
        val dirs = mutableListOf<File>()
        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            dirs.add(File(docs, "TelegramBackup"))
        } catch (_: Exception) {}

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dirs.add(File(downloads, "TelegramBackup"))
        } catch (_: Exception) {}

        return dirs
    }

    /**
     * Saves a complete persistent snapshot of all settings, API keys, bots, and channels
     * into external storage (Documents & Downloads) and internal storage fallback.
     */
    suspend fun saveVaultSnapshot(context: Context) = withContext(Dispatchers.IO) {
        try {
            val prefs = AppPrefs(context)
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val db = AppDatabase.getDatabase(context)

            val root = JSONObject()
            root.put("vault_version", VAULT_VERSION)
            root.put("timestamp", System.currentTimeMillis())
            root.put("app_version", prefs.appVersionStr)

            // 1. User & Profile
            val userEmail = authPrefs.getString("user_email", "")?.takeIf { it.isNotBlank() }
                ?: prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()

            val profileJson = JSONObject().apply {
                put("user_email", userEmail)
                put("recovery_email", prefs.recoveryEmail)
                put("display_name", prefs.displayName)
                put("profile_picture_uri", prefs.profilePictureUri)
                put("profile_updated_at", prefs.profileUpdatedAt)
                val avatarBase64 = ProfileManager.getAvatarBase64(context)
                if (!avatarBase64.isNullOrBlank()) {
                    put("profile_avatar_base64", avatarBase64)
                }
            }
            root.put("profile", profileJson)

            // 2. Master AES-256 Security & Encryption
            val securityJson = JSONObject().apply {
                put("encryption_enabled", prefs.encryptionEnabled)
                put("encryption_key", prefs.encryptionKey)
                put("is_biometric_enabled", prefs.isBiometricEnabled)
                put("screen_privacy_enabled", prefs.screenPrivacyEnabled)
                put("biometric_lock_for_safe", prefs.biometricLockForSafe)
                put("credentials_verified", prefs.credentialsVerified)
            }
            root.put("security", securityJson)

            // 3. Google Gemini AI & Vision Keys
            val aiJson = JSONObject().apply {
                put("gemini_api_keys", prefs.geminiApiKeys)
                put("active_ai_model", prefs.activeAiModel)
                put("ai_document_scanner_enabled", prefs.isAiDocumentScannerEnabled)
            }
            root.put("ai", aiJson)

            // 4. Bot Fleet
            val bots = db.telegramBotDao().getAllBots()
            val botsArray = JSONArray()
            if (bots.isNotEmpty()) {
                bots.forEach { bot ->
                    val token = bot.botToken.ifBlank { if (bot.isPrimary) prefs.botToken else "" }
                    botsArray.put(JSONObject().apply {
                        put("bot_id", bot.botId)
                        put("bot_token", token)
                        put("display_name", bot.displayName)
                        put("bot_username", bot.botUsername)
                        put("is_primary", bot.isPrimary)
                        put("is_active", bot.isActive)
                    })
                }
            } else if (prefs.botToken.isNotEmpty()) {
                val derivedId = TelegramBotEntity.deriveBotId(prefs.botToken)
                botsArray.put(JSONObject().apply {
                    put("bot_id", derivedId)
                    put("bot_token", prefs.botToken)
                    put("display_name", "Primary Bot")
                    put("bot_username", "")
                    put("is_primary", true)
                    put("is_active", true)
                })
            }
            root.put("bot_fleet", botsArray)

            // 5. Channels
            val channels = db.telegramChannelDao().getAllChannels()
            val channelsArray = JSONArray()
            if (channels.isNotEmpty()) {
                channels.forEach { ch ->
                    channelsArray.put(JSONObject().apply {
                        put("chat_id", ch.chatId)
                        put("title", ch.title)
                        put("is_primary", ch.isPrimary)
                        put("category_target", ch.categoryTarget)
                    })
                }
            } else if (prefs.chatId.isNotEmpty()) {
                channelsArray.put(JSONObject().apply {
                    put("chat_id", prefs.chatId)
                    put("title", "Storage Channel")
                    put("is_primary", true)
                    put("category_target", "ALL")
                })
            }
            root.put("channels", channelsArray)
            root.put("primary_chat_id", prefs.chatId)

            // 6. Sub-Settings & Backup Policies
            val settingsJson = JSONObject().apply {
                put("fleet_mode", prefs.fleetMode)
                put("boost_target", prefs.boostTarget)
                put("auto_backup_min_bots", prefs.autoBackupMinBots)
                put("drive_min_bots", prefs.driveMinBots)
                put("master_cloud_sync_enabled", prefs.masterCloudSyncEnabled)
                put("drive_sync_enabled", prefs.driveSyncEnabled)
                put("auto_backup_enabled", prefs.autoBackupEnabled)
                put("smart_backup_mode", prefs.smartBackupMode)
                put("night_backup_hour", prefs.nightBackupHour)
                put("smart_scan_interval_hours", prefs.smartScanIntervalHours)
                put("min_file_size_mb", prefs.minFileSizeMB)
                put("max_file_size_mb", prefs.maxFileSizeMB)
                put("max_photos_size_mb", prefs.maxPhotosSizeMB)
                put("min_photos_size_mb", prefs.minPhotosSizeMB)
                put("max_videos_size_mb", prefs.maxVideosSizeMB)
                put("min_videos_size_mb", prefs.minVideosSizeMB)
                put("max_docs_size_mb", prefs.maxDocsSizeMB)
                put("min_docs_size_mb", prefs.minDocsSizeMB)
                put("max_audio_size_mb", prefs.maxAudioSizeMB)
                put("min_audio_size_mb", prefs.minAudioSizeMB)
                put("max_archives_size_mb", prefs.maxArchivesSizeMB)
                put("min_archives_size_mb", prefs.minArchivesSizeMB)
                put("max_apks_size_mb", prefs.maxApksSizeMB)
                put("min_apks_size_mb", prefs.minApksSizeMB)
                put("max_other_size_mb", prefs.maxOtherSizeMB)
                put("min_other_size_mb", prefs.minOtherSizeMB)
                put("daily_data_limit_mb", prefs.dailyDataLimitMB)
                put("backup_camera", prefs.backupCamera)
                put("backup_pictures", prefs.backupPictures)
                put("backup_downloads", prefs.backupDownloads)
                put("backup_documents", prefs.backupDocuments)
                put("backup_videos", prefs.backupVideos)
                put("backup_audio", prefs.backupAudio)
                put("backup_archives", prefs.backupArchives)
                put("backup_apks", prefs.backupApks)
                put("backup_all_other", prefs.backupAllOther)
                put("wifi_only", prefs.wifiOnly)
                put("upload_threads", prefs.uploadThreads)
                put("upload_speed_limit", prefs.uploadSpeedLimit)
                put("enable_data_compression", prefs.enableDataCompression)
                put("app_theme", prefs.appTheme)
                put("bottom_nav_visible_tabs", prefs.bottomNavVisibleTabs)
                put("bottom_nav_label_mode", prefs.bottomNavLabelMode)
                put("bottom_nav_height_mode", prefs.bottomNavHeightMode)
                put("bottom_nav_indicator_shape", prefs.bottomNavIndicatorShape)
                put("bottom_nav_haptic", prefs.bottomNavHapticFeedback)
                put("settings_updated_at", prefs.settingsUpdatedAt)
                put("trash_auto_clean_days", prefs.trashAutoCleanDays)
                put("server_url", prefs.serverUrl)
                put("custom_bot_api_url", prefs.customBotApiUrl)
                put("selected_app_icon", prefs.appIcon)
            }
            root.put("sub_settings", settingsJson)

            // 7. Starred & Safe Vault Paths
            val starredArr = JSONArray()
            prefs.starredFilePaths.forEach { starredArr.put(it) }
            root.put("starred_file_paths", starredArr)

            val safeArr = JSONArray()
            prefs.safeVaultFilePaths.forEach { safeArr.put(it) }
            root.put("safe_vault_file_paths", safeArr)

            val payloadStr = root.toString(2)

            // Sanitize sensitive credentials before writing to public external storage directories (Finding #10)
            val publicSafeRoot = JSONObject(root.toString()).apply {
                optJSONObject("security")?.remove("encryption_key")
                optJSONArray("bot_fleet")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.remove("bot_token")
                    }
                }
            }
            val publicPayloadStr = publicSafeRoot.toString(2)

            // Write to all target external storage locations atomically (without raw credentials)
            for (dir in getStorageDirs()) {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    val hiddenTarget = File(dir, HIDDEN_FILE_NAME)
                    val backupTarget = File(dir, BACKUP_FILE_NAME)

                    val tempHidden = File(dir, "${HIDDEN_FILE_NAME}.tmp")
                    tempHidden.writeText(publicPayloadStr)
                    tempHidden.renameTo(hiddenTarget)

                    val tempBackup = File(dir, "${BACKUP_FILE_NAME}.tmp")
                    tempBackup.writeText(publicPayloadStr)
                    tempBackup.renameTo(backupTarget)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed writing vault snapshot to ${dir.absolutePath}: ${e.message}")
                }
            }

            // Also keep internal app copy atomically
            try {
                val internalTarget = File(context.filesDir, HIDDEN_FILE_NAME)
                val tempInternal = File(context.filesDir, "${HIDDEN_FILE_NAME}.tmp")
                tempInternal.writeText(payloadStr)
                tempInternal.renameTo(internalTarget)
            } catch (_: Exception) {}

            Log.d(TAG, "✓ Successfully saved local config vault snapshot (Version $VAULT_VERSION)")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local config vault snapshot", e)
        }
    }

    /**
     * Checks if local credentials or settings are missing (as happens after an uninstall).
     * If so, loads the persistent snapshot from external storage and hydrates all settings,
     * bots, channels, master encryption key, and Gemini API keys.
     */
    suspend fun autoRestoreIfLocalEmpty(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = AppPrefs(context)
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val db = AppDatabase.getDatabase(context)

            val localEmail = authPrefs.getString("user_email", "")?.trim() ?: ""
            val localBot = prefs.botToken.trim()
            val localGemini = prefs.geminiApiKeys.trim()
            val localKey = prefs.encryptionKey.trim()

            val isLocalBlank = (localEmail.isEmpty() && localBot.isEmpty()) &&
                    (authPrefs.all.isEmpty() && db.appSettingDao().getAllSettings().isEmpty())

            // Find best candidate snapshot file
            var snapshotJson: JSONObject? = null
            val candidateFiles = mutableListOf<File>()

            for (dir in getStorageDirs()) {
                candidateFiles.add(File(dir, HIDDEN_FILE_NAME))
                candidateFiles.add(File(dir, BACKUP_FILE_NAME))
            }
            candidateFiles.add(File(context.filesDir, HIDDEN_FILE_NAME))

            for (file in candidateFiles) {
                if (file.exists() && file.length() > 20) {
                    try {
                        val text = file.readText().trim()
                        if (text.startsWith("{") && text.endsWith("}")) {
                            val obj = JSONObject(text)
                            if (obj.has("profile") || obj.has("bot_fleet") || obj.has("security") || obj.has("ai")) {
                                snapshotJson = obj
                                Log.d(TAG, "Found valid persistent vault at: ${file.absolutePath}")
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (snapshotJson == null) {
                Log.d(TAG, "No persistent vault found on external storage.")
                return@withContext false
            }

            // Restore Profile
            snapshotJson.optJSONObject("profile")?.let { prof ->
                val email = prof.optString("user_email", "").trim()
                if (email.isNotEmpty() && localEmail.isEmpty()) {
                    authPrefs.edit().putString("user_email", email).apply()
                    prefs.userEmail = email
                }
                val recovery = prof.optString("recovery_email", "").trim()
                if (recovery.isNotEmpty() && prefs.recoveryEmail.isEmpty()) {
                    prefs.recoveryEmail = recovery
                }
                val name = prof.optString("display_name", "").trim()
                if (name.isNotEmpty() && (prefs.displayName.isBlank() || prefs.displayName == "My Cloud Account")) {
                    prefs.displayName = name
                }
                val avatarBase64 = prof.optString("profile_avatar_base64", "")
                if (avatarBase64.isNotEmpty()) {
                    ProfileManager.restoreAvatarFromBase64(context, avatarBase64)
                }
            }

            // Restore Master Security & Encryption Key
            snapshotJson.optJSONObject("security")?.let { sec ->
                if (sec.has("encryption_enabled")) prefs.encryptionEnabled = sec.optBoolean("encryption_enabled", true)
                val key = sec.optString("encryption_key", "")
                if (key.isNotEmpty() && (localKey.isEmpty() || localKey == "default_aes_key")) {
                    prefs.forceSetEncryptionKey(key)
                }
                if (sec.has("is_biometric_enabled")) prefs.isBiometricEnabled = sec.optBoolean("is_biometric_enabled", false)
                if (sec.has("screen_privacy_enabled")) prefs.screenPrivacyEnabled = sec.optBoolean("screen_privacy_enabled", false)
                if (sec.has("credentials_verified")) prefs.credentialsVerified = sec.optBoolean("credentials_verified", true)
            }

            // Restore Google Gemini AI & Vision Keys
            snapshotJson.optJSONObject("ai")?.let { ai ->
                val keys = ai.optString("gemini_api_keys", "").trim()
                if (keys.isNotEmpty() && localGemini.isEmpty()) {
                    prefs.geminiApiKeys = keys
                }
                if (ai.has("active_ai_model")) {
                    prefs.activeAiModel = ai.optString("active_ai_model", "auto")
                }
                if (ai.has("ai_document_scanner_enabled")) {
                    prefs.isAiDocumentScannerEnabled = ai.optBoolean("ai_document_scanner_enabled", true)
                }
            }

            // Restore Bot Fleet
            val botsArray = snapshotJson.optJSONArray("bot_fleet")
            if (botsArray != null && botsArray.length() > 0) {
                val botDao = db.telegramBotDao()
                val existingBots = botDao.getAllBots().associateBy { it.botId }
                for (i in 0 until botsArray.length()) {
                    val b = botsArray.optJSONObject(i) ?: continue
                    val token = b.optString("bot_token", "").trim()
                    val botId = b.optString("bot_id", "").ifBlank {
                        if (token.isNotEmpty()) TelegramBotEntity.deriveBotId(token) else ""
                    }
                    if (botId.isNotEmpty()) {
                        val isPrimary = b.optBoolean("is_primary", i == 0)
                        if (!existingBots.containsKey(botId)) {
                            val entity = TelegramBotEntity(
                                botId = botId,
                                displayName = b.optString("display_name", "Telegram Bot"),
                                botUsername = b.optString("bot_username", ""),
                                isPrimary = isPrimary,
                                isActive = b.optBoolean("is_active", true)
                            )
                            if (token.isNotEmpty()) {
                                entity.persistToken(context, token)
                            }
                            botDao.insert(entity)
                        }
                        if (token.isNotEmpty() && (isPrimary || prefs.botToken.isEmpty())) {
                            prefs.botToken = token
                        }
                    }
                }
            } else {
                val primaryBot = snapshotJson.optString("primary_bot_token", "").trim()
                if (primaryBot.isNotEmpty() && prefs.botToken.isEmpty()) {
                    prefs.botToken = primaryBot
                }
            }

            // Restore Channels
            val channelsArray = snapshotJson.optJSONArray("channels")
            if (channelsArray != null && channelsArray.length() > 0) {
                val chDao = db.telegramChannelDao()
                val existingChs = chDao.getAllChannels().associateBy { it.chatId }
                for (i in 0 until channelsArray.length()) {
                    val c = channelsArray.optJSONObject(i) ?: continue
                    val cId = c.optString("chat_id", "").trim()
                    if (cId.isNotEmpty()) {
                        val isPrimary = c.optBoolean("is_primary", i == 0)
                        if (!existingChs.containsKey(cId)) {
                            chDao.insert(
                                TelegramChannelEntity(
                                    chatId = cId,
                                    title = c.optString("title", "Storage Channel"),
                                    isPrimary = isPrimary,
                                    categoryTarget = c.optString("category_target", "ALL")
                                )
                            )
                        }
                        if (isPrimary || prefs.chatId.isEmpty()) {
                            prefs.chatId = cId
                        }
                    }
                }
            } else {
                val primaryChat = snapshotJson.optString("primary_chat_id", "").trim()
                if (primaryChat.isNotEmpty() && prefs.chatId.isEmpty()) {
                    prefs.chatId = primaryChat
                }
            }

            // Restore Sub-Settings
            snapshotJson.optJSONObject("sub_settings")?.let { sub ->
                if (sub.has("fleet_mode")) prefs.fleetMode = sub.optString("fleet_mode", "BOOST")
                if (sub.has("boost_target")) prefs.boostTarget = sub.optString("boost_target", "ALL")
                if (sub.has("auto_backup_min_bots")) prefs.autoBackupMinBots = sub.optInt("auto_backup_min_bots", 2)
                if (sub.has("drive_min_bots")) prefs.driveMinBots = sub.optInt("drive_min_bots", 3)
                if (sub.has("master_cloud_sync_enabled")) prefs.masterCloudSyncEnabled = sub.optBoolean("master_cloud_sync_enabled", true)
                if (sub.has("drive_sync_enabled")) prefs.driveSyncEnabled = sub.optBoolean("drive_sync_enabled", true)
                if (sub.has("auto_backup_enabled")) prefs.autoBackupEnabled = sub.optBoolean("auto_backup_enabled", false)
                if (sub.has("smart_backup_mode")) prefs.smartBackupMode = sub.optInt("smart_backup_mode", 0)
                if (sub.has("night_backup_hour")) prefs.nightBackupHour = sub.optInt("night_backup_hour", 2)
                if (sub.has("smart_scan_interval_hours")) prefs.smartScanIntervalHours = sub.optInt("smart_scan_interval_hours", 1)
                if (sub.has("min_file_size_mb")) prefs.minFileSizeMB = sub.optDouble("min_file_size_mb", 0.0)
                if (sub.has("max_file_size_mb")) prefs.maxFileSizeMB = sub.optDouble("max_file_size_mb", 2000.0)
                if (sub.has("max_photos_size_mb")) prefs.maxPhotosSizeMB = sub.optDouble("max_photos_size_mb", 0.0)
                if (sub.has("min_photos_size_mb")) prefs.minPhotosSizeMB = sub.optDouble("min_photos_size_mb", 0.0)
                if (sub.has("max_videos_size_mb")) prefs.maxVideosSizeMB = sub.optDouble("max_videos_size_mb", 0.0)
                if (sub.has("min_videos_size_mb")) prefs.minVideosSizeMB = sub.optDouble("min_videos_size_mb", 0.0)
                if (sub.has("max_docs_size_mb")) prefs.maxDocsSizeMB = sub.optDouble("max_docs_size_mb", 0.0)
                if (sub.has("min_docs_size_mb")) prefs.minDocsSizeMB = sub.optDouble("min_docs_size_mb", 0.0)
                if (sub.has("max_audio_size_mb")) prefs.maxAudioSizeMB = sub.optDouble("max_audio_size_mb", 0.0)
                if (sub.has("min_audio_size_mb")) prefs.minAudioSizeMB = sub.optDouble("min_audio_size_mb", 0.0)
                if (sub.has("max_archives_size_mb")) prefs.maxArchivesSizeMB = sub.optDouble("max_archives_size_mb", 0.0)
                if (sub.has("min_archives_size_mb")) prefs.minArchivesSizeMB = sub.optDouble("min_archives_size_mb", 0.0)
                if (sub.has("max_apks_size_mb")) prefs.maxApksSizeMB = sub.optDouble("max_apks_size_mb", 0.0)
                if (sub.has("min_apks_size_mb")) prefs.minApksSizeMB = sub.optDouble("min_apks_size_mb", 0.0)
                if (sub.has("max_other_size_mb")) prefs.maxOtherSizeMB = sub.optDouble("max_other_size_mb", 0.0)
                if (sub.has("min_other_size_mb")) prefs.minOtherSizeMB = sub.optDouble("min_other_size_mb", 0.0)
                if (sub.has("daily_data_limit_mb")) prefs.dailyDataLimitMB = sub.optLong("daily_data_limit_mb", 0L)
                if (sub.has("backup_camera")) prefs.backupCamera = sub.optBoolean("backup_camera", true)
                if (sub.has("backup_pictures")) prefs.backupPictures = sub.optBoolean("backup_pictures", true)
                if (sub.has("backup_downloads")) prefs.backupDownloads = sub.optBoolean("backup_downloads", true)
                if (sub.has("backup_documents")) prefs.backupDocuments = sub.optBoolean("backup_documents", true)
                if (sub.has("backup_videos")) prefs.backupVideos = sub.optBoolean("backup_videos", true)
                if (sub.has("backup_audio")) prefs.backupAudio = sub.optBoolean("backup_audio", true)
                if (sub.has("backup_archives")) prefs.backupArchives = sub.optBoolean("backup_archives", true)
                if (sub.has("backup_apks")) prefs.backupApks = sub.optBoolean("backup_apks", true)
                if (sub.has("backup_all_other")) prefs.backupAllOther = sub.optBoolean("backup_all_other", true)
                if (sub.has("wifi_only")) prefs.wifiOnly = sub.optBoolean("wifi_only", false)
                if (sub.has("upload_threads")) prefs.uploadThreads = sub.optInt("upload_threads", 4)
                if (sub.has("upload_speed_limit")) prefs.uploadSpeedLimit = sub.optString("upload_speed_limit", "Unlimited")
                if (sub.has("enable_data_compression")) prefs.enableDataCompression = sub.optBoolean("enable_data_compression", false)

                val snapshotSettingsTime = sub.optLong("settings_updated_at", snapshotJson.optLong("timestamp", 0L))
                val localSettingsTime = prefs.settingsUpdatedAt
                val isLocalNewer = localSettingsTime > 0L && localSettingsTime > snapshotSettingsTime

                if (!isLocalNewer) {
                    if (sub.has("app_theme")) prefs.appTheme = sub.optInt("app_theme", 0)
                    if (sub.has("bottom_nav_visible_tabs")) prefs.bottomNavVisibleTabs = sub.optString("bottom_nav_visible_tabs", prefs.bottomNavVisibleTabs)
                    if (sub.has("bottom_nav_label_mode")) prefs.bottomNavLabelMode = sub.optInt("bottom_nav_label_mode", prefs.bottomNavLabelMode)
                    if (sub.has("bottom_nav_height_mode")) prefs.bottomNavHeightMode = sub.optInt("bottom_nav_height_mode", prefs.bottomNavHeightMode)
                    if (sub.has("bottom_nav_indicator_shape")) prefs.bottomNavIndicatorShape = sub.optInt("bottom_nav_indicator_shape", prefs.bottomNavIndicatorShape)
                    if (sub.has("bottom_nav_haptic")) prefs.bottomNavHapticFeedback = sub.optBoolean("bottom_nav_haptic", prefs.bottomNavHapticFeedback)
                    if (snapshotSettingsTime > 0L) {
                        prefs.settingsUpdatedAt = snapshotSettingsTime
                    }
                }

                if (sub.has("trash_auto_clean_days")) prefs.trashAutoCleanDays = sub.optInt("trash_auto_clean_days", 30)
                if (sub.has("server_url")) prefs.serverUrl = sub.optString("server_url", prefs.serverUrl)
                if (sub.has("custom_bot_api_url")) prefs.customBotApiUrl = sub.optString("custom_bot_api_url", "")
                if (sub.has("selected_app_icon")) prefs.appIcon = sub.optString("selected_app_icon", "green")
            }

            // Restore Starred & Safe Vault Paths
            snapshotJson.optJSONArray("starred_file_paths")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i, "")
                    if (p.isNotBlank()) set.add(p)
                }
                if (set.isNotEmpty()) prefs.starredFilePaths = set
            }
            snapshotJson.optJSONArray("safe_vault_file_paths")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i, "")
                    if (p.isNotBlank()) set.add(p)
                }
                if (set.isNotEmpty()) prefs.safeVaultFilePaths = set
            }

            // Hydrate Room Database table app_settings
            val appSettingDao = db.appSettingDao()
            val now = System.currentTimeMillis()
            val entities = listOf(
                AppSettingEntity("user_email", prefs.userEmail, now),
                AppSettingEntity("recovery_email", prefs.recoveryEmail, now),
                AppSettingEntity("display_name", prefs.displayName, now),
                AppSettingEntity("profile_picture_uri", prefs.profilePictureUri, now),
                AppSettingEntity("encryption_enabled", prefs.encryptionEnabled.toString(), now),
                AppSettingEntity("gemini_api_keys", prefs.geminiApiKeys, now),
                AppSettingEntity("active_ai_model", prefs.activeAiModel, now),
                AppSettingEntity("ai_document_scanner_enabled", prefs.isAiDocumentScannerEnabled.toString(), now),
                AppSettingEntity("fleet_mode", prefs.fleetMode, now),
                AppSettingEntity("boost_target", prefs.boostTarget, now),
                AppSettingEntity("auto_backup_min_bots", prefs.autoBackupMinBots.toString(), now),
                AppSettingEntity("drive_min_bots", prefs.driveMinBots.toString(), now),
                AppSettingEntity("master_cloud_sync_enabled", prefs.masterCloudSyncEnabled.toString(), now),
                AppSettingEntity("drive_sync_enabled", prefs.driveSyncEnabled.toString(), now),
                AppSettingEntity("auto_backup_enabled", prefs.autoBackupEnabled.toString(), now),
                AppSettingEntity("selected_app_icon", prefs.appIcon, now),
                AppSettingEntity("credentials_verified", "true", now)
            )
            appSettingDao.saveSettings(entities)

            Log.d(TAG, "✓ Successfully auto-restored configuration from persistent external vault!")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error in autoRestoreIfLocalEmpty", e)
            return@withContext false
        }
    }

    val MAGIC_HEADER_RECOVERY = "VAULTR01".toByteArray(Charsets.US_ASCII) // 8 bytes
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    suspend fun exportEncryptedRecoveryPackage(
        context: Context,
        passphrase: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (passphrase.length < 8) {
            Log.e(TAG, "Passphrase too short for recovery package")
            return@withContext false
        }
        try {
            val prefs = AppPrefs(context)
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val db = AppDatabase.getDatabase(context)

            val root = JSONObject()
            root.put("vault_version", VAULT_VERSION)
            root.put("timestamp", System.currentTimeMillis())
            root.put("app_version", prefs.appVersionStr)

            // 1. Profile
            val userEmail = authPrefs.getString("user_email", "")?.takeIf { it.isNotBlank() }
                ?: prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()

            val profileJson = JSONObject().apply {
                put("user_email", userEmail)
                put("recovery_email", prefs.recoveryEmail)
                put("display_name", prefs.displayName)
                put("profile_picture_uri", prefs.profilePictureUri)
                put("profile_updated_at", prefs.profileUpdatedAt)
                val avatarBase64 = ProfileManager.getAvatarBase64(context)
                if (!avatarBase64.isNullOrBlank()) {
                    put("profile_avatar_base64", avatarBase64)
                }
            }
            root.put("profile", profileJson)

            // 2. Security (including master encryption key)
            val securityJson = JSONObject().apply {
                put("encryption_enabled", prefs.encryptionEnabled)
                put("encryption_key", prefs.encryptionKey)
                put("is_biometric_enabled", prefs.isBiometricEnabled)
                put("screen_privacy_enabled", prefs.screenPrivacyEnabled)
                put("biometric_lock_for_safe", prefs.biometricLockForSafe)
                put("credentials_verified", prefs.credentialsVerified)
            }
            root.put("security", securityJson)

            // 3. AI
            val aiJson = JSONObject().apply {
                put("gemini_api_keys", prefs.geminiApiKeys)
                put("active_ai_model", prefs.activeAiModel)
                put("ai_document_scanner_enabled", prefs.isAiDocumentScannerEnabled)
            }
            root.put("ai", aiJson)

            // 4. Bot Fleet (including full raw bot tokens)
            val bots = db.telegramBotDao().getAllBots()
            val botsArray = JSONArray()
            if (bots.isNotEmpty()) {
                bots.forEach { bot ->
                    val resolvedToken = bot.resolveToken(context)
                    botsArray.put(JSONObject().apply {
                        put("bot_id", bot.botId)
                        put("bot_token", resolvedToken)
                        put("display_name", bot.displayName)
                        put("bot_username", bot.botUsername)
                        put("is_primary", bot.isPrimary)
                        put("is_active", bot.isActive)
                    })
                }
            }
            root.put("bot_fleet", botsArray)
            root.put("primary_bot_token", prefs.botToken)

            // 5. Channels
            val channels = db.telegramChannelDao().getAllChannels()
            val channelsArray = JSONArray()
            channels.forEach { ch ->
                channelsArray.put(JSONObject().apply {
                    put("chat_id", ch.chatId)
                    put("title", ch.title)
                    put("is_primary", ch.isPrimary)
                    put("category_target", ch.categoryTarget)
                })
            }
            root.put("channels", channelsArray)
            root.put("primary_chat_id", prefs.chatId)

            // 6. Sub Settings
            val settingsJson = JSONObject().apply {
                put("fleet_mode", prefs.fleetMode)
                put("boost_target", prefs.boostTarget)
                put("auto_backup_min_bots", prefs.autoBackupMinBots)
                put("drive_min_bots", prefs.driveMinBots)
                put("master_cloud_sync_enabled", prefs.masterCloudSyncEnabled)
                put("drive_sync_enabled", prefs.driveSyncEnabled)
                put("auto_backup_enabled", prefs.autoBackupEnabled)
                put("smart_backup_mode", prefs.smartBackupMode)
                put("night_backup_hour", prefs.nightBackupHour)
                put("smart_scan_interval_hours", prefs.smartScanIntervalHours)
                put("min_file_size_mb", prefs.minFileSizeMB)
                put("max_file_size_mb", prefs.maxFileSizeMB)
                put("max_photos_size_mb", prefs.maxPhotosSizeMB)
                put("min_photos_size_mb", prefs.minPhotosSizeMB)
                put("max_videos_size_mb", prefs.maxVideosSizeMB)
                put("min_videos_size_mb", prefs.minVideosSizeMB)
                put("max_docs_size_mb", prefs.maxDocsSizeMB)
                put("min_docs_size_mb", prefs.minDocsSizeMB)
                put("max_audio_size_mb", prefs.maxAudioSizeMB)
                put("min_audio_size_mb", prefs.minAudioSizeMB)
                put("max_archives_size_mb", prefs.maxArchivesSizeMB)
                put("min_archives_size_mb", prefs.minArchivesSizeMB)
                put("max_apks_size_mb", prefs.maxApksSizeMB)
                put("min_apks_size_mb", prefs.minApksSizeMB)
                put("max_other_size_mb", prefs.maxOtherSizeMB)
                put("min_other_size_mb", prefs.minOtherSizeMB)
                put("daily_data_limit_mb", prefs.dailyDataLimitMB)
                put("backup_camera", prefs.backupCamera)
                put("backup_pictures", prefs.backupPictures)
                put("backup_downloads", prefs.backupDownloads)
                put("backup_documents", prefs.backupDocuments)
                put("backup_videos", prefs.backupVideos)
                put("backup_audio", prefs.backupAudio)
                put("backup_archives", prefs.backupArchives)
                put("backup_apks", prefs.backupApks)
                put("backup_all_other", prefs.backupAllOther)
                put("wifi_only", prefs.wifiOnly)
                put("upload_threads", prefs.uploadThreads)
                put("upload_speed_limit", prefs.uploadSpeedLimit)
                put("enable_data_compression", prefs.enableDataCompression)
                put("app_theme", prefs.appTheme)
                put("bottom_nav_visible_tabs", prefs.bottomNavVisibleTabs)
                put("bottom_nav_label_mode", prefs.bottomNavLabelMode)
                put("bottom_nav_height_mode", prefs.bottomNavHeightMode)
                put("bottom_nav_indicator_shape", prefs.bottomNavIndicatorShape)
                put("bottom_nav_haptic", prefs.bottomNavHapticFeedback)
                put("settings_updated_at", prefs.settingsUpdatedAt)
                put("trash_auto_clean_days", prefs.trashAutoCleanDays)
                put("server_url", prefs.serverUrl)
                put("custom_bot_api_url", prefs.customBotApiUrl)
                put("selected_app_icon", prefs.appIcon)
            }
            root.put("sub_settings", settingsJson)

            val plaintextBytes = root.toString().toByteArray(Charsets.UTF_8)

            // Derive key via PBKDF2
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES)
            random.nextBytes(salt)
            val iv = ByteArray(GCM_IV_LENGTH_BYTES)
            random.nextBytes(iv)

            val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = skf.generateSecret(spec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(MAGIC_HEADER_RECOVERY)
            val ciphertext = cipher.doFinal(plaintextBytes)

            outputFile.parentFile?.mkdirs()
            val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
            tempFile.outputStream().use { fos ->
                fos.write(MAGIC_HEADER_RECOVERY) // 8B
                fos.write(salt)                  // 16B
                fos.write(iv)                    // 12B
                fos.write(ciphertext)            // ciphertext + 16B tag
            }
            if (outputFile.exists()) outputFile.delete()
            tempFile.renameTo(outputFile)
            Log.d(TAG, "✓ Successfully exported encrypted recovery package to ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting encrypted recovery package", e)
            false
        }
    }

    suspend fun importEncryptedRecoveryPackage(
        context: Context,
        passphrase: String,
        inputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inputFile.exists() || inputFile.length() < (8 + SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + 16)) {
            Log.e(TAG, "Recovery package file invalid or too small")
            return@withContext false
        }
        try {
            val fileBytes = inputFile.readBytes()
            val header = fileBytes.copyOfRange(0, 8)
            if (!header.contentEquals(MAGIC_HEADER_RECOVERY)) {
                Log.e(TAG, "Recovery package header mismatch")
                return@withContext false
            }

            var offset = 8
            val salt = fileBytes.copyOfRange(offset, offset + SALT_LENGTH_BYTES)
            offset += SALT_LENGTH_BYTES
            val iv = fileBytes.copyOfRange(offset, offset + GCM_IV_LENGTH_BYTES)
            offset += GCM_IV_LENGTH_BYTES
            val ciphertext = fileBytes.copyOfRange(offset, fileBytes.size)

            val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = skf.generateSecret(spec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(MAGIC_HEADER_RECOVERY)
            val decryptedBytes = cipher.doFinal(ciphertext)

            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val snapshotJson = JSONObject(jsonStr)

            val prefs = AppPrefs(context)
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val db = AppDatabase.getDatabase(context)

            // Restore Master Security & Encryption Key
            snapshotJson.optJSONObject("security")?.let { sec ->
                if (sec.has("encryption_enabled")) prefs.encryptionEnabled = sec.optBoolean("encryption_enabled", true)
                val key = sec.optString("encryption_key", "")
                if (key.isNotBlank()) {
                    prefs.forceSetEncryptionKey(key)
                }
                if (sec.has("is_biometric_enabled")) prefs.isBiometricEnabled = sec.optBoolean("is_biometric_enabled", false)
                if (sec.has("screen_privacy_enabled")) prefs.screenPrivacyEnabled = sec.optBoolean("screen_privacy_enabled", false)
                if (sec.has("credentials_verified")) prefs.credentialsVerified = sec.optBoolean("credentials_verified", true)
            }

            // Restore Bot Fleet with tokens into Keystore
            val botsArray = snapshotJson.optJSONArray("bot_fleet")
            if (botsArray != null && botsArray.length() > 0) {
                val botDao = db.telegramBotDao()
                for (i in 0 until botsArray.length()) {
                    val b = botsArray.optJSONObject(i) ?: continue
                    val token = b.optString("bot_token", "").trim()
                    val botId = b.optString("bot_id", "").ifBlank {
                        if (token.isNotBlank()) TelegramBotEntity.deriveBotId(token) else ""
                    }
                    if (botId.isNotBlank()) {
                        val isPrimary = b.optBoolean("is_primary", i == 0)
                        val entity = TelegramBotEntity(
                            botId = botId,
                            displayName = b.optString("display_name", "Telegram Bot"),
                            botUsername = b.optString("bot_username", ""),
                            isPrimary = isPrimary,
                            isActive = b.optBoolean("is_active", true)
                        )
                        if (token.isNotBlank()) {
                            entity.persistToken(context, token)
                        }
                        botDao.insert(entity)
                        if (isPrimary && token.isNotBlank()) {
                            prefs.botToken = token
                        }
                    }
                }
            } else {
                val primaryBot = snapshotJson.optString("primary_bot_token", "").trim()
                if (primaryBot.isNotEmpty()) {
                    prefs.botToken = primaryBot
                }
            }

            // Restore Profile
            snapshotJson.optJSONObject("profile")?.let { prof ->
                val email = prof.optString("user_email", "").trim()
                if (email.isNotEmpty()) {
                    authPrefs.edit().putString("user_email", email).apply()
                    prefs.userEmail = email
                }
                val recovery = prof.optString("recovery_email", "").trim()
                if (recovery.isNotEmpty()) {
                    prefs.recoveryEmail = recovery
                }
                val name = prof.optString("display_name", "").trim()
                if (name.isNotEmpty()) {
                    prefs.displayName = name
                }
            }

            // Restore Channels
            val channelsArray = snapshotJson.optJSONArray("channels")
            if (channelsArray != null && channelsArray.length() > 0) {
                val chDao = db.telegramChannelDao()
                for (i in 0 until channelsArray.length()) {
                    val c = channelsArray.optJSONObject(i) ?: continue
                    val cId = c.optString("chat_id", "").trim()
                    if (cId.isNotEmpty()) {
                        val isPrimary = c.optBoolean("is_primary", i == 0)
                        chDao.insert(
                            TelegramChannelEntity(
                                chatId = cId,
                                title = c.optString("title", "Storage Channel"),
                                isPrimary = isPrimary,
                                categoryTarget = c.optString("category_target", "ALL")
                            )
                        )
                        if (isPrimary) {
                            prefs.chatId = cId
                        }
                    }
                }
            }

            saveVaultSnapshot(context)
            Log.d(TAG, "✓ Successfully imported encrypted recovery package")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing encrypted recovery package", e)
            false
        }
    }
}
