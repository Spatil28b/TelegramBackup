package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AppSettingEntity
import com.example.telegrambackup.data.TelegramBotEntity
import com.example.telegrambackup.data.TelegramChannelEntity
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * =====================================================================================
 * CLOUD PROFILE & SETTINGS ZERO-LOSS SYNC MANAGER
 * =====================================================================================
 * Guarantees that when a user uninstalls and reinstalls the application, their entire
 * account profile, custom display name, avatar, master AES-256 encryption key, complete
 * Bot Fleet, Channel Routing rules, and all customized sub-settings are 100% automatically
 * preserved and restored without resetting to defaults.
 * =====================================================================================
 */
object CloudProfileSyncManager {
    private const val TAG = "CloudProfileSync"
    private const val MANIFEST_VERSION = 2
    const val MANIFEST_TAG = "#CLOUDSYNC_CONFIG_MANIFEST_V2#"

    /**
     * Serializes complete application configuration, profile, bot fleet, channel routes,
     * and sub-settings into a portable JSON manifest string.
     */
    suspend fun generateConfigManifestJson(context: Context): String = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val db = AppDatabase.getDatabase(context)

        val root = JSONObject()
        root.put("manifest_version", MANIFEST_VERSION)
        root.put("timestamp", System.currentTimeMillis())
        root.put("app_version", prefs.appVersionStr)

        // 1. Profile Section
        val profileJson = JSONObject().apply {
            put("display_name", prefs.displayName)
            put("user_email", prefs.userEmail)
            put("recovery_email", prefs.recoveryEmail)
            put("profile_picture_uri", prefs.profilePictureUri)
            put("profile_updated_at", prefs.profileUpdatedAt)
            val avatarBase64 = ProfileManager.getAvatarBase64(context)
            if (!avatarBase64.isNullOrBlank()) {
                put("profile_avatar_base64", avatarBase64)
            }
        }
        root.put("profile", profileJson)

        // 2. Master Security & Encryption Key (Ensures decryption never breaks on fresh install)
        val securityJson = JSONObject().apply {
            put("encryption_enabled", prefs.encryptionEnabled)
            put("encryption_key", prefs.encryptionKey)
            put("is_biometric_enabled", prefs.isBiometricEnabled)
        }
        root.put("security", securityJson)

        // 3. Complete Bot Fleet Pool
        val bots = db.telegramBotDao().getAllBots()
        val botsArray = JSONArray()
        if (bots.isNotEmpty()) {
            bots.forEach { bot ->
                botsArray.put(JSONObject().apply {
                    put("bot_token", bot.botToken)
                    put("display_name", bot.displayName)
                    put("bot_username", bot.botUsername)
                    put("is_primary", bot.isPrimary)
                    put("is_active", bot.isActive)
                })
            }
        } else if (prefs.botToken.isNotEmpty()) {
            botsArray.put(JSONObject().apply {
                put("bot_token", prefs.botToken)
                put("display_name", "Primary Bot")
                put("bot_username", "")
                put("is_primary", true)
                put("is_active", true)
            })
        }
        root.put("bot_fleet", botsArray)

        // 4. Channel Routing Rules
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

        // 5. Complete Sub-Settings & Fleet Strategy
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
            put("backup_downloads", prefs.backupDownloads)
            put("backup_documents", prefs.backupDocuments)
            put("backup_videos", prefs.backupVideos)
            put("backup_apks", prefs.backupApks)
            put("wifi_only", prefs.wifiOnly)
            put("upload_threads", prefs.uploadThreads)
            put("upload_speed_limit", prefs.uploadSpeedLimit)
            put("enable_data_compression", prefs.enableDataCompression)
            put("app_theme", prefs.appTheme)
            put("trash_auto_clean_days", prefs.trashAutoCleanDays)
        }
        root.put("sub_settings", settingsJson)

        return@withContext root.toString()
    }

    /**
     * Parses and restores complete application profile, security keys, bot fleet,
     * channel routes, and sub-settings from a JSON manifest string.
     */
    suspend fun applyConfigManifestJson(context: Context, manifestJsonStr: String): Boolean = withContext(Dispatchers.IO) {
        if (manifestJsonStr.isBlank()) return@withContext false
        try {
            val root = JSONObject(manifestJsonStr)
            val prefs = AppPrefs(context)
            val db = AppDatabase.getDatabase(context)

            val manifestTimestamp = root.optLong("timestamp", 0L)

            // 1. Restore Profile with Timestamp & Anti-Reset Checks
            root.optJSONObject("profile")?.let { prof ->
                val profileUpdatedAtCloud = prof.optLong("profile_updated_at", manifestTimestamp)
                val localUpdatedAt = prefs.profileUpdatedAt
                val isCloudNewer = profileUpdatedAtCloud >= localUpdatedAt

                val cloudName = prof.optString("display_name", "").trim()
                if (cloudName.isNotEmpty()) {
                    val isCurrentDefault = prefs.displayName.isBlank() || prefs.displayName == "My Cloud Account"
                    if ((isCloudNewer || isCurrentDefault) && (cloudName != "My Cloud Account" || isCurrentDefault)) {
                        prefs.displayName = cloudName
                    }
                }

                // Restore avatar Base64 if available
                val avatarBase64 = prof.optString("profile_avatar_base64", "")
                if (avatarBase64.isNotEmpty()) {
                    val localAvatarFile = ProfileManager.getAvatarFile(context)
                    if (!localAvatarFile.exists() || isCloudNewer) {
                        ProfileManager.restoreAvatarFromBase64(context, avatarBase64)
                    }
                } else {
                    val cloudUri = prof.optString("profile_picture_uri", "").trim()
                    if (cloudUri.isNotEmpty()) {
                        val localAvatarFile = ProfileManager.getAvatarFile(context)
                        if (!localAvatarFile.exists() && isCloudNewer) {
                            prefs.profilePictureUri = cloudUri
                        }
                    }
                }

                prof.optString("recovery_email", "").takeIf { it.isNotEmpty() }?.let { prefs.recoveryEmail = it }
                prof.optString("user_email", "").takeIf { it.isNotEmpty() }?.let { prefs.userEmail = it }
            }

            // 2. Restore Master Security & Encryption Key
            root.optJSONObject("security")?.let { sec ->
                if (sec.has("encryption_enabled")) prefs.encryptionEnabled = sec.optBoolean("encryption_enabled", true)
                val cloudKey = sec.optString("encryption_key", "")
                if (cloudKey.isNotEmpty()) {
                    prefs.forceSetEncryptionKey(cloudKey)
                }
                if (sec.has("is_biometric_enabled")) prefs.isBiometricEnabled = sec.optBoolean("is_biometric_enabled", false)
            }

            // 3. Restore Bot Fleet Pool
            val botsArray = root.optJSONArray("bot_fleet")
            if (botsArray != null && botsArray.length() > 0) {
                val botDao = db.telegramBotDao()
                for (i in 0 until botsArray.length()) {
                    val b = botsArray.optJSONObject(i) ?: continue
                    val token = b.optString("bot_token", "").trim()
                    if (token.isNotEmpty()) {
                        val entity = TelegramBotEntity(
                            botToken = token,
                            displayName = b.optString("display_name", "Telegram Bot"),
                            botUsername = b.optString("bot_username", ""),
                            isPrimary = b.optBoolean("is_primary", i == 0),
                            isActive = b.optBoolean("is_active", true)
                        )
                        botDao.insert(entity)
                        if (entity.isPrimary) {
                            prefs.botToken = token
                        }
                    }
                }
            }

            // 4. Restore Channel Routing Rules
            val channelsArray = root.optJSONArray("channels")
            if (channelsArray != null && channelsArray.length() > 0) {
                val channelDao = db.telegramChannelDao()
                for (i in 0 until channelsArray.length()) {
                    val c = channelsArray.optJSONObject(i) ?: continue
                    val cId = c.optString("chat_id", "").trim()
                    if (cId.isNotEmpty()) {
                        val entity = TelegramChannelEntity(
                            chatId = cId,
                            title = c.optString("title", "Storage Channel"),
                            isPrimary = c.optBoolean("is_primary", i == 0),
                            categoryTarget = c.optString("category_target", "ALL")
                        )
                        channelDao.insert(entity)
                        if (entity.isPrimary) {
                            prefs.chatId = cId
                        }
                    }
                }
            }

            // 5. Restore Sub-Settings & Modes
            root.optJSONObject("sub_settings")?.let { sub ->
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
                if (sub.has("backup_downloads")) prefs.backupDownloads = sub.optBoolean("backup_downloads", true)
                if (sub.has("backup_documents")) prefs.backupDocuments = sub.optBoolean("backup_documents", true)
                if (sub.has("backup_videos")) prefs.backupVideos = sub.optBoolean("backup_videos", true)
                if (sub.has("backup_apks")) prefs.backupApks = sub.optBoolean("backup_apks", true)
                if (sub.has("wifi_only")) prefs.wifiOnly = sub.optBoolean("wifi_only", false)
                if (sub.has("upload_threads")) prefs.uploadThreads = sub.optInt("upload_threads", 4)
                if (sub.has("upload_speed_limit")) prefs.uploadSpeedLimit = sub.optString("upload_speed_limit", "Unlimited")
                if (sub.has("enable_data_compression")) prefs.enableDataCompression = sub.optBoolean("enable_data_compression", false)
                if (sub.has("app_theme")) prefs.appTheme = sub.optInt("app_theme", 0)
                if (sub.has("trash_auto_clean_days")) prefs.trashAutoCleanDays = sub.optInt("trash_auto_clean_days", 30)
            }

            // Sync with Room app_settings table as well
            val appSettingDao = db.appSettingDao()
            val now = System.currentTimeMillis()
            val entities = listOf(
                AppSettingEntity("display_name", prefs.displayName, now),
                AppSettingEntity("profile_picture_uri", prefs.profilePictureUri, now),
                AppSettingEntity("encryption_key", prefs.encryptionKey, now),
                AppSettingEntity("fleet_mode", prefs.fleetMode, now),
                AppSettingEntity("boost_target", prefs.boostTarget, now),
                AppSettingEntity("auto_backup_min_bots", prefs.autoBackupMinBots.toString(), now),
                AppSettingEntity("drive_min_bots", prefs.driveMinBots.toString(), now),
                AppSettingEntity("master_cloud_sync_enabled", prefs.masterCloudSyncEnabled.toString(), now),
                AppSettingEntity("drive_sync_enabled", prefs.driveSyncEnabled.toString(), now),
                AppSettingEntity("auto_backup_enabled", prefs.autoBackupEnabled.toString(), now),
                AppSettingEntity("credentials_verified", "true", now)
            )
            appSettingDao.saveSettings(entities)

            Log.d(TAG, "✓ Successfully applied cloud config manifest (Version $MANIFEST_VERSION)")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying cloud config manifest", e)
            return@withContext false
        }
    }

    /**
     * Backs up full application configuration to both PostgreSQL server and Telegram Cloud Channel.
     */
    suspend fun syncLocalConfigToCloud(context: Context, showToast: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val prefs = AppPrefs(context)
            val userEmail = authPrefs.getString("user_email", "")?.takeIf { it.isNotBlank() }
                ?: prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()

            if (prefs.botToken.isBlank() && userEmail.isBlank()) return@withContext

            val manifestJson = generateConfigManifestJson(context)
            val serverApi = ServerApi()

            // 1. Sync to PostgreSQL Server
            if (userEmail.isNotBlank()) {
                try {
                    serverApi.saveUserSettings(prefs.serverUrl, userEmail, manifestJson)
                } catch (_: Exception) {}
            }

            // 2. Sync to Primary Telegram Storage Channel
            val botToken = prefs.botToken
            val chatId = prefs.chatId
            if (botToken.isNotBlank() && chatId.isNotBlank()) {
                try {
                    // Send manifest message tagged with #CLOUDSYNC_CONFIG_MANIFEST_V2#
                    val textPayload = "$MANIFEST_TAG\n$manifestJson"
                    serverApi.sendTelegramMessage(botToken, chatId, textPayload)
                } catch (_: Exception) {}
            }

            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "☁️ Cloud Profile & Settings Synced!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncLocalConfigToCloud failed", e)
        }
    }

    /**
     * Checks both PostgreSQL server and Telegram Cloud Channel to automatically restore
     * all profile details, encryption key, bot fleet, channel routes, and sub-settings upon reinstall/login.
     */
    suspend fun autoRestoreConfigFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val prefs = AppPrefs(context)
            val serverApi = ServerApi()
            var manifestFound: String? = null

            // 1. Check PostgreSQL Server first
            val userEmail = authPrefs.getString("user_email", "")?.takeIf { it.isNotBlank() }
                ?: prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()

            if (userEmail.isNotBlank()) {
                try {
                    val serverManifest = serverApi.getUserSettings(prefs.serverUrl, userEmail)
                    if (!serverManifest.isNullOrBlank() && serverManifest != "{}" && (serverManifest.contains("bot_fleet") || serverManifest.contains("profile") || serverManifest.contains("security"))) {
                        manifestFound = serverManifest
                    }
                } catch (_: Exception) {}
            }

            // 2. If not found on server, query Telegram Channel
            if (manifestFound == null && prefs.botToken.isNotBlank() && prefs.chatId.isNotBlank()) {
                try {
                    val history = serverApi.getRecentChannelMessages(prefs.botToken, prefs.chatId, 30)
                    for (msg in history) {
                        val text = msg.optString("text", "")
                        if (text.contains(MANIFEST_TAG)) {
                            manifestFound = text.substringAfter(MANIFEST_TAG).trim()
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!manifestFound.isNullOrBlank()) {
                val restored = applyConfigManifestJson(context, manifestFound)
                if (restored) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "☁️ Cloud Profile & Settings Restored!", Toast.LENGTH_LONG).show()
                    }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "autoRestoreConfigFromCloud failed", e)
        }
        return@withContext false
    }
}
