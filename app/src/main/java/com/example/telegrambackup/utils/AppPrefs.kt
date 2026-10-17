package com.example.telegrambackup.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

enum class SafeFolderUnlockResult {
    UNLOCKED_PRIMARY,
    UNLOCKED_DECOY,
    INVALID_PIN,
    NOT_CONFIGURED
}

class AppPrefs(private val context: Context) {
    companion object {
        @Volatile
        var isDecoyVaultActive: Boolean = false
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("telegram_backup_prefs", Context.MODE_PRIVATE)
    private val authPrefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun clearAll() {
        prefs.edit().clear().apply()
        authPrefs.edit().clear().apply()
        KeystoreVault.deleteEncryptedString(context, "bot_token")
        KeystoreVault.deleteEncryptedString(context, "chat_id")
        KeystoreVault.deleteEncryptedString(context, "gemini_api_keys")
        KeystoreVault.deleteEncryptedString(context, "auth_token")
    }

    var serverUrl: String
        get() {
            val defaultUrl = "https://telegram-backup-backend.vercel.app"
            val stored = prefs.getString("server_url", defaultUrl) ?: defaultUrl
            val cleaned = stored.trim().trim('"', '\'', ' ', '\t', '\n', '\r')
            if (cleaned.contains("server-alpha-black") || cleaned.contains("gotrends") || cleaned.contains("server-eight-khaki") || cleaned.isBlank()) {
                prefs.edit().putString("server_url", defaultUrl).apply()
                return defaultUrl
            }
            return cleaned
        }
        set(value) {
            val sanitized = value.trim().trim('"', '\'', ' ', '\t', '\n', '\r')
            prefs.edit().putString("server_url", sanitized).apply()
        }

    var authToken: String
        get() {
            val enc = KeystoreVault.retrieveEncryptedString(context, "auth_token")
            if (!enc.isNullOrBlank()) return enc
            val plain = authPrefs.getString("auth_token", "") ?: ""
            if (plain.isNotBlank()) {
                KeystoreVault.storeEncryptedString(context, "auth_token", plain)
                authPrefs.edit().remove("auth_token").apply()
            }
            return plain
        }
        set(value) {
            KeystoreVault.storeEncryptedString(context, "auth_token", value)
            authPrefs.edit().remove("auth_token").apply()
        }

    var encryptionEnabled: Boolean
        get() = prefs.getBoolean("encryption_enabled", true)
        set(value) = prefs.edit().putBoolean("encryption_enabled", value).apply()

    var backupOverWifi: Boolean
        get() = prefs.getBoolean("backup_over_wifi", true)
        set(value) = prefs.edit().putBoolean("backup_over_wifi", value).apply()

    var encryptionKey: String
        get() {
            var key = KeystoreVault.retrieveMasterKey(context) ?: ""
            if (key.isEmpty() || key == "default_aes_key") {
                val plainKey = prefs.getString("encryption_key", null)
                if (!plainKey.isNullOrBlank() && plainKey != "default_aes_key") {
                    key = plainKey
                    KeystoreVault.storeMasterKey(context, key)
                    prefs.edit().remove("encryption_key").apply()
                } else {
                    val dbKey = try {
                        com.example.telegrambackup.data.AppDatabase.getDatabase(context).appSettingDao().getSetting("encryption_key")?.value
                    } catch (_: Exception) { null }
                    if (!dbKey.isNullOrBlank() && dbKey != "default_aes_key") {
                        key = dbKey
                        KeystoreVault.storeMasterKey(context, key)
                    } else {
                        key = "AES256-" + java.util.UUID.randomUUID().toString().replace("-", "")
                        KeystoreVault.storeMasterKey(context, key)
                    }
                    prefs.edit().remove("encryption_key").apply()
                }
            } else {
                if (prefs.contains("encryption_key")) {
                    prefs.edit().remove("encryption_key").apply()
                }
            }
            return key
        }
        set(value) {
            if (value.isNotBlank() && value != "default_aes_key") {
                KeystoreVault.storeMasterKey(context, value)
                prefs.edit().remove("encryption_key").apply()
            }
        }

    @Volatile
    private var cachedStarredPaths: Set<String>? = null

    var starredFilePaths: Set<String>
        get() {
            var cached = cachedStarredPaths
            if (cached == null) {
                cached = prefs.getStringSet("starred_file_paths", emptySet())?.toSet() ?: emptySet()
                cachedStarredPaths = cached
            }
            return cached
        }
        set(value) {
            cachedStarredPaths = value.toSet()
            prefs.edit().putStringSet("starred_file_paths", value).apply()
        }

    fun toggleStarredFile(filePath: String): Boolean {
        val current = starredFilePaths.toMutableSet()
        val isNowStarred = if (current.contains(filePath)) {
            current.remove(filePath)
            false
        } else {
            current.add(filePath)
            true
        }
        starredFilePaths = current
        return isNowStarred
    }

    fun isFileStarred(filePath: String): Boolean {
        return starredFilePaths.contains(filePath)
    }

    fun forceSetEncryptionKey(key: String) {
        if (key.isNotBlank()) {
            KeystoreVault.storeMasterKey(context, key)
            prefs.edit().remove("encryption_key").apply()
        }
    }

    var recoveryEmail: String
        get() = prefs.getString("recovery_email", "") ?: ""
        set(value) = prefs.edit().putString("recovery_email", value).apply()

    var appIcon: String
        get() = prefs.getString("selected_app_icon", "green") ?: "green"
        set(value) = prefs.edit().putString("selected_app_icon", value).apply()

    var userEmail: String
        get() {
            val email = authPrefs.getString("user_email", "") ?: ""
            return if (email.isNotEmpty()) email else recoveryEmail
        }
        set(value) {
            authPrefs.edit().putString("user_email", value).apply()
        }

    var profileUpdatedAt: Long
        get() = prefs.getLong("profile_updated_at", 0L)
        set(value) = prefs.edit().putLong("profile_updated_at", value).apply()

    var profilePictureUri: String
        get() {
            val file = java.io.File(context.filesDir, "user_profile_avatar.jpg")
            if (file.exists() && file.length() > 0) {
                return android.net.Uri.fromFile(file).toString()
            }
            return prefs.getString("profile_picture_uri", "") ?: ""
        }
        set(value) = prefs.edit().putString("profile_picture_uri", value).apply()

    var displayName: String
        get() {
            val stored = prefs.getString("display_name", "My Cloud Account") ?: "My Cloud Account"
            return if (stored.isBlank()) "My Cloud Account" else stored
        }
        set(value) {
            val cleaned = value.trim()
            prefs.edit().putString("display_name", if (cleaned.isBlank()) "My Cloud Account" else cleaned).apply()
        }

    var backupExceptions: Set<String>
        get() = prefs.getStringSet("backup_exceptions", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("backup_exceptions", value).apply()

    var smartScanIntervalHours: Int
        get() = prefs.getInt("smart_scan_interval_hours", 1)
        set(value) = prefs.edit().putInt("smart_scan_interval_hours", value).apply()

    var minFileSizeMB: Double
        get() = prefs.getFloat("min_file_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_file_size_mb", value.toFloat()).apply()

    var maxFileSizeMB: Double
        get() = prefs.getFloat("max_file_size_mb", 2000.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_file_size_mb", value.toFloat()).apply()

    // --- Individual File Size Conditions (MB, 0.0 = Use Global Limit or No Limit) ---
    var maxPhotosSizeMB: Double
        get() = prefs.getFloat("max_photos_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_photos_size_mb", value.toFloat()).apply()

    var minPhotosSizeMB: Double
        get() = prefs.getFloat("min_photos_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_photos_size_mb", value.toFloat()).apply()

    var maxVideosSizeMB: Double
        get() = prefs.getFloat("max_videos_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_videos_size_mb", value.toFloat()).apply()

    var minVideosSizeMB: Double
        get() = prefs.getFloat("min_videos_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_videos_size_mb", value.toFloat()).apply()

    var maxDocsSizeMB: Double
        get() = prefs.getFloat("max_docs_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_docs_size_mb", value.toFloat()).apply()

    var minDocsSizeMB: Double
        get() = prefs.getFloat("min_docs_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_docs_size_mb", value.toFloat()).apply()

    var maxAudioSizeMB: Double
        get() = prefs.getFloat("max_audio_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_audio_size_mb", value.toFloat()).apply()

    var minAudioSizeMB: Double
        get() = prefs.getFloat("min_audio_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_audio_size_mb", value.toFloat()).apply()

    var maxArchivesSizeMB: Double
        get() = prefs.getFloat("max_archives_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_archives_size_mb", value.toFloat()).apply()

    var minArchivesSizeMB: Double
        get() = prefs.getFloat("min_archives_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_archives_size_mb", value.toFloat()).apply()

    var maxApksSizeMB: Double
        get() = prefs.getFloat("max_apks_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_apks_size_mb", value.toFloat()).apply()

    var minApksSizeMB: Double
        get() = prefs.getFloat("min_apks_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_apks_size_mb", value.toFloat()).apply()

    var maxOtherSizeMB: Double
        get() = prefs.getFloat("max_other_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("max_other_size_mb", value.toFloat()).apply()

    var minOtherSizeMB: Double
        get() = prefs.getFloat("min_other_size_mb", 0.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_other_size_mb", value.toFloat()).apply()

    fun getCategorySizeLimits(type: BackupFileType): Pair<Double, Double> = when (type) {
        BackupFileType.PHOTOS -> minPhotosSizeMB to maxPhotosSizeMB
        BackupFileType.VIDEOS -> minVideosSizeMB to maxVideosSizeMB
        BackupFileType.DOCUMENTS -> minDocsSizeMB to maxDocsSizeMB
        BackupFileType.AUDIO -> minAudioSizeMB to maxAudioSizeMB
        BackupFileType.ARCHIVES -> minArchivesSizeMB to maxArchivesSizeMB
        BackupFileType.APKS -> minApksSizeMB to maxApksSizeMB
        BackupFileType.OTHER -> minOtherSizeMB to maxOtherSizeMB
    }

    fun setCategorySizeLimits(type: BackupFileType, minMB: Double, maxMB: Double) {
        when (type) {
            BackupFileType.PHOTOS -> { minPhotosSizeMB = minMB; maxPhotosSizeMB = maxMB }
            BackupFileType.VIDEOS -> { minVideosSizeMB = minMB; maxVideosSizeMB = maxMB }
            BackupFileType.DOCUMENTS -> { minDocsSizeMB = minMB; maxDocsSizeMB = maxMB }
            BackupFileType.AUDIO -> { minAudioSizeMB = minMB; maxAudioSizeMB = maxMB }
            BackupFileType.ARCHIVES -> { minArchivesSizeMB = minMB; maxArchivesSizeMB = maxMB }
            BackupFileType.APKS -> { minApksSizeMB = minMB; maxApksSizeMB = maxMB }
            BackupFileType.OTHER -> { minOtherSizeMB = minMB; maxOtherSizeMB = maxMB }
        }
    }

    fun isCategoryEnabled(type: BackupFileType): Boolean = when (type) {
        BackupFileType.PHOTOS -> backupCamera || backupPictures
        BackupFileType.VIDEOS -> backupVideos
        BackupFileType.DOCUMENTS -> backupDocuments
        BackupFileType.AUDIO -> backupAudio
        BackupFileType.ARCHIVES -> backupArchives
        BackupFileType.APKS -> backupApks
        BackupFileType.OTHER -> backupAllOther
    }

    fun getEffectiveByteRange(type: BackupFileType): LongRange {
        val (typeMinMB, typeMaxMB) = getCategorySizeLimits(type)
        val globalMin = (minFileSizeMB * 1024.0 * 1024.0).toLong()
        val globalMax = if (maxFileSizeMB <= 0) Long.MAX_VALUE else (maxFileSizeMB * 1024.0 * 1024.0).toLong()

        val effectiveMin = if (typeMinMB > 0) (typeMinMB * 1024.0 * 1024.0).toLong() else globalMin
        val effectiveMax = if (typeMaxMB > 0) (typeMaxMB * 1024.0 * 1024.0).toLong() else globalMax

        return effectiveMin..effectiveMax
    }

    var botToken: String
        get() {
            val enc = KeystoreVault.retrieveEncryptedString(context, "bot_token")
            if (!enc.isNullOrBlank()) return enc
            val plain = prefs.getString("bot_token", "") ?: ""
            if (plain.isNotBlank()) {
                KeystoreVault.storeEncryptedString(context, "bot_token", plain)
                prefs.edit().remove("bot_token").apply()
            }
            return plain
        }
        set(value) {
            val trimmed = value.trim()
            KeystoreVault.storeEncryptedString(context, "bot_token", trimmed)
            prefs.edit().remove("bot_token").apply()
        }

    var chatId: String
        get() {
            val enc = KeystoreVault.retrieveEncryptedString(context, "chat_id")
            if (!enc.isNullOrBlank()) return enc
            val plain = prefs.getString("chat_id", "") ?: ""
            if (plain.isNotBlank()) {
                KeystoreVault.storeEncryptedString(context, "chat_id", plain)
                prefs.edit().remove("chat_id").apply()
            }
            return plain
        }
        set(value) {
            val trimmed = value.trim()
            KeystoreVault.storeEncryptedString(context, "chat_id", trimmed)
            prefs.edit().remove("chat_id").apply()
        }

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_backup_enabled", value).apply()

    var backupMode: Int
        get() = prefs.getInt("backup_mode", 1)
        set(value) = prefs.edit().putInt("backup_mode", value).apply()

    var backupPriority: Int
        get() = prefs.getInt("backup_priority", 0)
        set(value) = prefs.edit().putInt("backup_priority", value).apply()

    var backupCamera: Boolean
        get() = prefs.getBoolean("backup_camera", true)
        set(value) = prefs.edit().putBoolean("backup_camera", value).apply()

    var backupPictures: Boolean
        get() = prefs.getBoolean("backup_pictures", true)
        set(value) = prefs.edit().putBoolean("backup_pictures", value).apply()

    var backupDownloads: Boolean
        get() = prefs.getBoolean("backup_downloads", true)
        set(value) = prefs.edit().putBoolean("backup_downloads", value).apply()

    var backupDocuments: Boolean
        get() = prefs.getBoolean("backup_documents", true)
        set(value) = prefs.edit().putBoolean("backup_documents", value).apply()

    var backupApks: Boolean
        get() = prefs.getBoolean("backup_apks", true)
        set(value) = prefs.edit().putBoolean("backup_apks", value).apply()

    var backupAllOther: Boolean
        get() = prefs.getBoolean("backup_all_other", true)
        set(value) = prefs.edit().putBoolean("backup_all_other", value).apply()

    var backupVideos: Boolean
        get() = prefs.getBoolean("backup_videos", true)
        set(value) = prefs.edit().putBoolean("backup_videos", value).apply()

    var backupAudio: Boolean
        get() = prefs.getBoolean("backup_audio", true)
        set(value) = prefs.edit().putBoolean("backup_audio", value).apply()

    var backupArchives: Boolean
        get() = prefs.getBoolean("backup_archives", true)
        set(value) = prefs.edit().putBoolean("backup_archives", value).apply()

    var backupLargeFiles: Boolean
        get() = prefs.getBoolean("backup_large_files", true)
        set(value) = prefs.edit().putBoolean("backup_large_files", value).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", false)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    var showHiddenFiles: Boolean
        get() = prefs.getBoolean("show_hidden_files", false)
        set(value) = prefs.edit().putBoolean("show_hidden_files", value).apply()

    var hapticFeedback: Boolean
        get() = prefs.getBoolean("haptic_feedback", true)
        set(value) = prefs.edit().putBoolean("haptic_feedback", value).apply()

    var enableDataCompression: Boolean
        get() = prefs.getBoolean("enable_data_compression", true)
        set(value) = prefs.edit().putBoolean("enable_data_compression", value).apply()

    var optimizeBattery: Boolean
        get() = prefs.getBoolean("optimize_battery", true)
        set(value) = prefs.edit().putBoolean("optimize_battery", value).apply()

    var enableDevMode: Boolean
        get() = prefs.getBoolean("enable_dev_mode", false)
        set(value) = prefs.edit().putBoolean("enable_dev_mode", value).apply()

    var appTheme: Int
        get() = prefs.getInt("app_theme", 0)
        set(value) = prefs.edit().putInt("app_theme", value).apply()

    var isZeroKnowledgeEnabled: Boolean
        get() = prefs.getBoolean("is_zero_knowledge_enabled", false)
        set(value) = prefs.edit().putBoolean("is_zero_knowledge_enabled", value).apply()

    var zeroKnowledgePassphrase: String
        get() {
            // Try KeystoreVault first (new secure storage)
            val vaultVal = try {
                KeystoreVault.retrieveEncryptedString(context, "zero_knowledge_passphrase")
            } catch (_: Exception) { null }
            if (!vaultVal.isNullOrBlank()) return vaultVal

            // Migrate legacy plaintext value if present
            val legacy = prefs.getString("zero_knowledge_passphrase", "") ?: ""
            if (legacy.isNotBlank()) {
                try {
                    KeystoreVault.storeEncryptedString(context, "zero_knowledge_passphrase", legacy)
                    prefs.edit().remove("zero_knowledge_passphrase").apply()
                } catch (_: Exception) {}
                return legacy
            }
            return ""
        }
        set(value) {
            try {
                KeystoreVault.storeEncryptedString(context, "zero_knowledge_passphrase", value)
                // Remove any legacy plaintext entry
                prefs.edit().remove("zero_knowledge_passphrase").apply()
            } catch (_: Exception) {
                // Fallback to SharedPreferences only if KeystoreVault fails
                prefs.edit().putString("zero_knowledge_passphrase", value).apply()
            }
        }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("is_biometric_enabled", false)
        set(value) = prefs.edit().putBoolean("is_biometric_enabled", value).apply()

    var screenPrivacyEnabled: Boolean
        get() = prefs.getBoolean("screen_privacy_enabled", false)
        set(value) = prefs.edit().putBoolean("screen_privacy_enabled", value).apply()

    var masterCloudSyncEnabled: Boolean
        get() = !prefs.getBoolean("stop_all_backups", false)
        set(value) {
            stopAllBackups = !value
        }

    var driveSyncEnabled: Boolean
        get() = prefs.getBoolean("drive_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("drive_sync_enabled", value).apply()

    var stopAllBackups: Boolean
        get() = prefs.getBoolean("stop_all_backups", false)
        set(value) {
            val editor = prefs.edit()
            editor.putBoolean("stop_all_backups", value)
            if (value) editor.putLong("stop_all_backups_time", System.currentTimeMillis())
            else editor.remove("stop_all_backups_time")
            editor.apply()
        }

    var stopAllBackupsTimestamp: Long
        get() = prefs.getLong("stop_all_backups_time", 0L)
        set(value) = prefs.edit().putLong("stop_all_backups_time", value).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "English (US)") ?: "English (US)"
        set(value) = prefs.edit().putString("app_language", value).apply()

    var customDownloadDir: String
        get() = prefs.getString("custom_download_dir", "Internal Storage/Downloads") ?: "Internal Storage/Downloads"
        set(value) = prefs.edit().putString("custom_download_dir", value).apply()

    var isSelfAnalysisEnabled: Boolean
        get() = prefs.getBoolean("is_self_analysis_enabled", true)
        set(value) = prefs.edit().putBoolean("is_self_analysis_enabled", value).apply()

    var liveCameraObserverEnabled: Boolean
        get() = prefs.getBoolean("live_camera_observer_enabled", true)
        set(value) = prefs.edit().putBoolean("live_camera_observer_enabled", value).apply()

    var lastAnalysisTimestamp: Long
        get() = prefs.getLong("last_analysis_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_analysis_timestamp", value).apply()

    var autoLockDuration: String
        get() = prefs.getString("auto_lock_duration", "5 Minutes") ?: "5 Minutes"
        set(value) = prefs.edit().putString("auto_lock_duration", value).apply()

    var persistentNotificationEnabled: Boolean
        get() = prefs.getBoolean("persistent_notification_enabled", true)
        set(value) = prefs.edit().putBoolean("persistent_notification_enabled", value).apply()

    var backupScheduleTime: String
        get() = prefs.getString("backup_schedule_time", "Daily at 10:00 PM") ?: "Daily at 10:00 PM"
        set(value) = prefs.edit().putString("backup_schedule_time", value).apply()

    var appVersionStr: String
        get() = prefs.getString("app_version_str", AppVersionHelper.currentVersionName) ?: AppVersionHelper.currentVersionName
        set(value) = prefs.edit().putString("app_version_str", value).apply()

    var lastSeenWhatsNewVersion: String
        get() = prefs.getString("last_seen_whats_new_version", "") ?: ""
        set(value) = prefs.edit().putString("last_seen_whats_new_version", value).apply()

    // Smart Backup Mode: 0 = Continuous, 1 = Custom Schedule, 2 = Night Express
    var smartBackupMode: Int
        get() = prefs.getInt("smart_backup_mode", 0)
        set(value) = prefs.edit().putInt("smart_backup_mode", value).apply()

    // Night Express backup hour (1-5 AM range, default 2 AM)
    var nightBackupHour: Int
        get() = prefs.getInt("night_backup_hour", 2)
        set(value) = prefs.edit().putInt("night_backup_hour", value).apply()

    // Once credentials are saved + verified, lock them behind OTP
    var credentialsVerified: Boolean
        get() = prefs.getBoolean("credentials_verified", false)
        set(value) = prefs.edit().putBoolean("credentials_verified", value).apply()

    // Trash auto-clean: 0 = Never, 7/14/30/60/90 days
    var trashAutoCleanDays: Int
        get() = prefs.getInt("trash_auto_clean_days", 30)
        set(value) = prefs.edit().putInt("trash_auto_clean_days", value).apply()

    // Dismissed update version (to prevent repeated popups in same version)
    var dismissedUpdateVersion: String
        get() = prefs.getString("dismissed_update_version", "") ?: ""
        set(value) = prefs.edit().putString("dismissed_update_version", value).apply()

    // Fleet Strategy Mode: "BOOST", "DEDICATED", "BALANCED", "MAX"
    var fleetMode: String
        get() = prefs.getString("fleet_mode", "BOOST") ?: "BOOST"
        set(value) = prefs.edit().putString("fleet_mode", value).apply()

    // Boost Target Task: "ALL", "AUTO_BACKUP", "DRIVE"
    var boostTarget: String
        get() = prefs.getString("boost_target", "ALL") ?: "ALL"
        set(value) = prefs.edit().putString("boost_target", value).apply()

    // Minimum / Dedicated bots for Auto-Backup
    var autoBackupMinBots: Int
        get() = prefs.getInt("auto_backup_min_bots", 2)
        set(value) = prefs.edit().putInt("auto_backup_min_bots", value).apply()

    // Minimum / Dedicated bots for Cloud Drive
    var driveMinBots: Int
        get() = prefs.getInt("drive_min_bots", 3)
        set(value) = prefs.edit().putInt("drive_min_bots", value).apply()

    // Maximum App Cache Budget in MB (default 512 MB)
    var maxCacheSizeMB: Long
        get() = prefs.getLong("max_cache_size_mb", 512L)
        set(value) = prefs.edit().putLong("max_cache_size_mb", value).apply()

    // Smart Battery & Power Guards
    var chargingOnly: Boolean
        get() = prefs.getBoolean("charging_only", false)
        set(value) = prefs.edit().putBoolean("charging_only", value).apply()

    var lowBatteryProtection: Boolean
        get() = prefs.getBoolean("low_battery_protection", true)
        set(value) = prefs.edit().putBoolean("low_battery_protection", value).apply()

    var uploadSpeedLimit: String
        get() = prefs.getString("upload_speed_limit", "Unlimited") ?: "Unlimited"
        set(value) = prefs.edit().putString("upload_speed_limit", value).apply()

    var uploadThreads: Int
        get() = prefs.getInt("upload_threads", 4)
        set(value) = prefs.edit().putInt("upload_threads", value).apply()

    // Daily Data Limit: 0 = Unlimited, or limit in MB (e.g. 500, 1024, 2048, 5120)
    var dailyDataLimitMB: Long
        get() = prefs.getLong("daily_data_limit_mb", 0L)
        set(value) = prefs.edit().putLong("daily_data_limit_mb", value).apply()

    var dailyDataTransferredBytes: Long
        get() = prefs.getLong("daily_data_transferred_bytes", 0L)
        set(value) = prefs.edit().putLong("daily_data_transferred_bytes", value).apply()

    var dailyDataDate: String
        get() = prefs.getString("daily_data_date", "") ?: ""
        set(value) = prefs.edit().putString("daily_data_date", value).apply()

    var autoBackup: Boolean
        get() = autoBackupEnabled
        set(value) { autoBackupEnabled = value }

    var biometricLock: Boolean
        get() = isBiometricEnabled
        set(value) { isBiometricEnabled = value }

    // Custom Local Telegram Bot API Server (e.g. http://192.168.1.100:8081)
    var customBotApiUrl: String
        get() = prefs.getString("custom_bot_api_url", "") ?: ""
        set(value) = prefs.edit().putString("custom_bot_api_url", value.trim()).apply()

    // Auto-lock timeout in seconds: 0 = immediate, 30 = 30s, 60 = 1m, 300 = 5m
    var biometricAutoLockTimeoutSeconds: Int
        get() = prefs.getInt("biometric_autolock_timeout", 30)
        set(value) = prefs.edit().putInt("biometric_autolock_timeout", value).apply()

    // Require biometric specifically to open Private Safe Vault
    var biometricLockForSafe: Boolean
        get() = prefs.getBoolean("biometric_lock_for_safe", true)
        set(value) = prefs.edit().putBoolean("biometric_lock_for_safe", value).apply()

    // =========================================================================
    // 🎨 MATERIAL 3 BOTTOM NAVIGATION BAR CUSTOMIZATION PREFERENCES
    // =========================================================================
    // Enabled tabs in order. Comma-separated: "HOME,FILES,DRIVE,SETTINGS" (Modern M3 Default)
    var bottomNavVisibleTabs: String
        get() = prefs.getString("bottom_nav_visible_tabs", "HOME,FILES,DRIVE,SETTINGS") ?: "HOME,FILES,DRIVE,SETTINGS"
        set(value) = prefs.edit().putString("bottom_nav_visible_tabs", value).apply()

    // 0 = Always show labels, 1 = Show on active tab only, 2 = Never show labels (compact icon-only)
    var bottomNavLabelMode: Int
        get() = prefs.getInt("bottom_nav_label_mode", 0)
        set(value) = prefs.edit().putInt("bottom_nav_label_mode", value).apply()

    // 0 = Standard M3 (80dp), 1 = Compact M3 (64dp), 2 = Ultra-Slim (54dp)
    var bottomNavHeightMode: Int
        get() = prefs.getInt("bottom_nav_height_mode", 0)
        set(value) = prefs.edit().putInt("bottom_nav_height_mode", value).apply()

    // 0 = Classic Pill (16dp rounded), 1 = Squircle (10dp), 2 = Oval Capsule (CircleShape), 3 = Minimal Underline
    var bottomNavIndicatorShape: Int
        get() = prefs.getInt("bottom_nav_indicator_shape", 0)
        set(value) = prefs.edit().putInt("bottom_nav_indicator_shape", value).apply()

    // Enable tactile haptic response on navigation tab change
    var bottomNavHapticFeedback: Boolean
        get() = prefs.getBoolean("bottom_nav_haptic", true)
        set(value) = prefs.edit().putBoolean("bottom_nav_haptic", value).apply()

    // Dedicated Safe Folder Vault Files Set
    @Volatile
    private var cachedSafeVaultPaths: Set<String>? = null

    var safeVaultFilePaths: Set<String>
        get() {
            var cached = cachedSafeVaultPaths
            if (cached == null) {
                cached = prefs.getStringSet("safe_vault_file_paths", emptySet())?.toSet() ?: emptySet()
                cachedSafeVaultPaths = cached
            }
            return cached
        }
        set(value) {
            cachedSafeVaultPaths = value.toSet()
            prefs.edit().putStringSet("safe_vault_file_paths", value).apply()
        }

    fun toggleSafeVaultFile(filePath: String): Boolean {
        val current = safeVaultFilePaths.toMutableSet()
        val isNowSafe = if (current.contains(filePath)) {
            current.remove(filePath)
            false
        } else {
            current.add(filePath)
            true
        }
        safeVaultFilePaths = current
        return isNowSafe
    }

    var safeFolderPin: String
        get() {
            val enc = KeystoreVault.retrieveEncryptedString(context, "safe_folder_pin")
            if (!enc.isNullOrBlank()) return enc
            val plain = prefs.getString("safe_folder_pin", "") ?: ""
            if (plain.isNotBlank()) {
                KeystoreVault.storeEncryptedString(context, "safe_folder_pin", plain)
                prefs.edit().remove("safe_folder_pin").apply()
            }
            return plain
        }
        set(value) {
            val trimmed = value.trim()
            KeystoreVault.storeEncryptedString(context, "safe_folder_pin", trimmed)
            prefs.edit().remove("safe_folder_pin").apply()
        }

    var safeFolderDecoyPin: String
        get() {
            val enc = KeystoreVault.retrieveEncryptedString(context, "safe_folder_decoy_pin")
            if (!enc.isNullOrBlank()) return enc
            val plain = prefs.getString("safe_folder_decoy_pin", "") ?: ""
            if (plain.isNotBlank()) {
                KeystoreVault.storeEncryptedString(context, "safe_folder_decoy_pin", plain)
                prefs.edit().remove("safe_folder_decoy_pin").apply()
            }
            return plain
        }
        set(value) {
            val trimmed = value.trim()
            KeystoreVault.storeEncryptedString(context, "safe_folder_decoy_pin", trimmed)
            prefs.edit().remove("safe_folder_decoy_pin").apply()
        }

    var decoyVaultFilePaths: Set<String>
        get() = prefs.getStringSet("decoy_vault_file_paths", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("decoy_vault_file_paths", value).apply()

    fun isRealSafeVaultFile(filePath: String): Boolean {
        return safeVaultFilePaths.contains(filePath)
    }

    fun isSafeVaultFile(filePath: String): Boolean {
        if (isDecoyVaultActive) {
            return decoyVaultFilePaths.contains(filePath)
        }
        return safeVaultFilePaths.contains(filePath)
    }

    fun ensureDecoyVaultPopulated(context: Context) {
        if (decoyVaultFilePaths.isNotEmpty()) return
        try {
            val decoyDir = java.io.File(context.filesDir, "decoy_vault").apply { if (!exists()) mkdirs() }
            val paths = mutableSetOf<String>()

            val f1 = java.io.File(decoyDir, "Travel_Packing_List.txt")
            if (!f1.exists()) {
                f1.writeText("Passport, Boarding Pass, Charger, Power Bank, Headphones, Toothbrush, Jacket")
            }
            paths.add(f1.absolutePath)

            val f2 = java.io.File(decoyDir, "Grocery_Receipt_Sample.txt")
            if (!f2.exists()) {
                f2.writeText("SUPERMARKET RECEIPT\nDate: 14-Oct-2026\nApples - $4.50\nMilk 1L - $2.20\nBread - $1.80\nTotal: $8.50\nPAID VIA CASH")
            }
            paths.add(f2.absolutePath)

            val f3 = java.io.File(decoyDir, "Apartment_Maintenance_Note.txt")
            if (!f3.exists()) {
                f3.writeText("Maintenance society note: Water tank cleaning scheduled for Wednesday 9:00 AM to 1:00 PM.")
            }
            paths.add(f3.absolutePath)

            decoyVaultFilePaths = paths
        } catch (_: Exception) {}
    }

    fun verifySafeFolderPin(enteredPin: String): SafeFolderUnlockResult {
        val master = safeFolderPin.trim()
        val decoy = safeFolderDecoyPin.trim()
        val candidate = enteredPin.trim()

        if (master.isBlank() && decoy.isBlank()) {
            return SafeFolderUnlockResult.NOT_CONFIGURED
        }
        if (master.isNotBlank() && candidate == master) {
            isDecoyVaultActive = false
            return SafeFolderUnlockResult.UNLOCKED_PRIMARY
        }
        if (decoy.isNotBlank() && candidate == decoy) {
            isDecoyVaultActive = true
            ensureDecoyVaultPopulated(context)
            return SafeFolderUnlockResult.UNLOCKED_DECOY
        }
        return SafeFolderUnlockResult.INVALID_PIN
    }

    var compressVideosBeforeUpload: Boolean
        get() = prefs.getBoolean("compress_videos_before_upload", false)
        set(value) = prefs.edit().putBoolean("compress_videos_before_upload", value).apply()

    var geminiApiKeys: String
        get() {
            val enc = KeystoreVault.retrieveEncryptedString(context, "gemini_api_keys")
            if (!enc.isNullOrBlank()) return enc
            val plain = prefs.getString("gemini_api_keys", "") ?: ""
            if (plain.isNotBlank()) {
                KeystoreVault.storeEncryptedString(context, "gemini_api_keys", plain)
                prefs.edit().remove("gemini_api_keys").apply()
            }
            return plain
        }
        set(value) {
            val trimmed = value.trim()
            KeystoreVault.storeEncryptedString(context, "gemini_api_keys", trimmed)
            prefs.edit().remove("gemini_api_keys").apply()
        }

    var activeAiModel: String
        get() {
            val saved = prefs.getString("active_ai_model", "auto") ?: "auto"
            return if (saved.contains("1.5")) "auto" else saved
        }
        set(value) = prefs.edit().putString("active_ai_model", value).apply()

    var isAiDocumentScannerEnabled: Boolean
        get() = prefs.getBoolean("ai_document_scanner_enabled", true)
        set(value) = prefs.edit().putBoolean("ai_document_scanner_enabled", value).apply()

    fun getGeminiApiKeyList(): List<String> {
        val raw = geminiApiKeys.trim()
        if (raw.isBlank()) return emptyList()
        return raw.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun hasValidGeminiKey(): Boolean {
        return getGeminiApiKeyList().isNotEmpty()
    }
}

enum class BackupFileType(
    val key: String,
    val title: String,
    val subtitle: String,
    val extensions: Set<String>
) {
    PHOTOS(
        key = "photos",
        title = "Photos & Images",
        subtitle = "JPG, PNG, WEBP, GIF, HEIC, RAW, SVG",
        extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "raw", "svg", "tiff", "dng")
    ),
    VIDEOS(
        key = "videos",
        title = "Videos & Recordings",
        subtitle = "MP4, MKV, AVI, MOV, WEBM, 3GP, FLV, WMV",
        extensions = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v", "ts")
    ),
    DOCUMENTS(
        key = "documents",
        title = "Documents & PDFs",
        subtitle = "PDF, DOCX, XLSX, PPTX, TXT, CSV, EPUB",
        extensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "rtf", "odt", "ods")
    ),
    AUDIO(
        key = "audio",
        title = "Audio & Music",
        subtitle = "MP3, FLAC, WAV, AAC, OGG, M4A, OPUS",
        extensions = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma", "amr", "mid")
    ),
    ARCHIVES(
        key = "archives",
        title = "Archives & Compressed",
        subtitle = "ZIP, RAR, 7Z, TAR, GZ, ISO, BZ2, XZ",
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "iso", "bz2", "xz")
    ),
    APKS(
        key = "apks",
        title = "APK Installers",
        subtitle = "APK, XAPK, APKS",
        extensions = setOf("apk", "xapk", "apks")
    ),
    OTHER(
        key = "other",
        title = "Other Recognized Files",
        subtitle = "All other non-excluded file formats",
        extensions = emptySet()
    );

    companion object {
        fun fromExtension(ext: String): BackupFileType {
            val lower = ext.lowercase()
            return when {
                lower in PHOTOS.extensions -> PHOTOS
                lower in VIDEOS.extensions -> VIDEOS
                lower in AUDIO.extensions -> AUDIO
                lower in DOCUMENTS.extensions -> DOCUMENTS
                lower in ARCHIVES.extensions -> ARCHIVES
                lower in APKS.extensions -> APKS
                else -> OTHER
            }
        }
    }
}

