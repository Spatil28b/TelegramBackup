package com.example.telegrambackup.utils

import android.content.Context
import android.widget.Toast
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AppSettingEntity
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SettingsDbManager {

    /**
     * Called on App Launch (in MainActivity).
     * Fetches all saved settings from Room Database table `app_settings`.
     * Hydrates AppPrefs with database values. If Database is empty on fresh install,
     * seeds initial default settings into Database.
     */
    suspend fun fetchAndSyncSettingsOnAppOpen(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.appSettingDao()
                val prefs = AppPrefs(context)

                val savedSettings = dao.getAllSettings()

                if (savedSettings.isEmpty()) {
                    // Seed initial defaults into Room Database
                    val initialDefaults = listOf(
                        AppSettingEntity("server_url", prefs.serverUrl),
                        AppSettingEntity("recovery_email", prefs.recoveryEmail),
                        AppSettingEntity("profile_picture_uri", prefs.profilePictureUri),
                        AppSettingEntity("display_name", prefs.displayName),
                        AppSettingEntity("bot_token", prefs.botToken),
                        AppSettingEntity("chat_id", prefs.chatId),
                        AppSettingEntity("auto_backup_enabled", prefs.autoBackupEnabled.toString()),
                        AppSettingEntity("smart_backup_mode", prefs.smartBackupMode.toString()),
                        AppSettingEntity("night_backup_hour", prefs.nightBackupHour.toString()),
                        AppSettingEntity("smart_scan_interval_hours", prefs.smartScanIntervalHours.toString()),
                        AppSettingEntity("min_file_size_mb", prefs.minFileSizeMB.toString()),
                        AppSettingEntity("max_file_size_mb", prefs.maxFileSizeMB.toString()),
                        AppSettingEntity("max_photos_size_mb", prefs.maxPhotosSizeMB.toString()),
                        AppSettingEntity("min_photos_size_mb", prefs.minPhotosSizeMB.toString()),
                        AppSettingEntity("max_videos_size_mb", prefs.maxVideosSizeMB.toString()),
                        AppSettingEntity("min_videos_size_mb", prefs.minVideosSizeMB.toString()),
                        AppSettingEntity("max_docs_size_mb", prefs.maxDocsSizeMB.toString()),
                        AppSettingEntity("min_docs_size_mb", prefs.minDocsSizeMB.toString()),
                        AppSettingEntity("max_audio_size_mb", prefs.maxAudioSizeMB.toString()),
                        AppSettingEntity("min_audio_size_mb", prefs.minAudioSizeMB.toString()),
                        AppSettingEntity("max_archives_size_mb", prefs.maxArchivesSizeMB.toString()),
                        AppSettingEntity("min_archives_size_mb", prefs.minArchivesSizeMB.toString()),
                        AppSettingEntity("max_apks_size_mb", prefs.maxApksSizeMB.toString()),
                        AppSettingEntity("min_apks_size_mb", prefs.minApksSizeMB.toString()),
                        AppSettingEntity("max_other_size_mb", prefs.maxOtherSizeMB.toString()),
                        AppSettingEntity("min_other_size_mb", prefs.minOtherSizeMB.toString()),
                        AppSettingEntity("backup_camera", prefs.backupCamera.toString()),
                        AppSettingEntity("backup_downloads", prefs.backupDownloads.toString()),
                        AppSettingEntity("backup_documents", prefs.backupDocuments.toString()),
                        AppSettingEntity("backup_videos", prefs.backupVideos.toString()),
                        AppSettingEntity("backup_apks", prefs.backupApks.toString()),
                        AppSettingEntity("wifi_only", prefs.wifiOnly.toString()),
                        AppSettingEntity("enable_data_compression", prefs.enableDataCompression.toString()),
                        AppSettingEntity("is_biometric_enabled", prefs.isBiometricEnabled.toString()),
                        AppSettingEntity("encryption_key", prefs.encryptionKey),
                        AppSettingEntity("app_theme", prefs.appTheme.toString()),
                        AppSettingEntity("trash_auto_clean_days", prefs.trashAutoCleanDays.toString()),
                        AppSettingEntity("stop_all_backups", prefs.stopAllBackups.toString()),
                        AppSettingEntity("credentials_verified", prefs.credentialsVerified.toString())
                    )
                    dao.saveSettings(initialDefaults)
                } else {
                    // Hydrate AppPrefs from Room Database records
                    val settingsMap = savedSettings.associate { it.key to it.value }

                    settingsMap["recovery_email"]?.let { prefs.recoveryEmail = it }
                    settingsMap["profile_picture_uri"]?.takeIf { it.isNotBlank() }?.let { prefs.profilePictureUri = it }
                    settingsMap["display_name"]?.takeIf { it.isNotBlank() }?.let { dbName ->
                        val isCurrentDefault = prefs.displayName.isBlank() || prefs.displayName == "My Cloud Account"
                        if (dbName != "My Cloud Account" || isCurrentDefault) {
                            prefs.displayName = dbName
                        }
                    }
                    settingsMap["bot_token"]?.let { prefs.botToken = it }
                    settingsMap["chat_id"]?.let { prefs.chatId = it }
                    settingsMap["auto_backup_enabled"]?.let { prefs.autoBackupEnabled = it.toBoolean() }
                    settingsMap["smart_backup_mode"]?.let { prefs.smartBackupMode = it.toIntOrNull() ?: 0 }
                    settingsMap["night_backup_hour"]?.let { prefs.nightBackupHour = it.toIntOrNull() ?: 2 }
                    settingsMap["smart_scan_interval_hours"]?.let { prefs.smartScanIntervalHours = it.toIntOrNull() ?: 1 }
                    settingsMap["min_file_size_mb"]?.let { prefs.minFileSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_file_size_mb"]?.let { prefs.maxFileSizeMB = it.toDoubleOrNull() ?: 2000.0 }
                    settingsMap["max_photos_size_mb"]?.let { prefs.maxPhotosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_photos_size_mb"]?.let { prefs.minPhotosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_videos_size_mb"]?.let { prefs.maxVideosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_videos_size_mb"]?.let { prefs.minVideosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_docs_size_mb"]?.let { prefs.maxDocsSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_docs_size_mb"]?.let { prefs.minDocsSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_audio_size_mb"]?.let { prefs.maxAudioSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_audio_size_mb"]?.let { prefs.minAudioSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_archives_size_mb"]?.let { prefs.maxArchivesSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_archives_size_mb"]?.let { prefs.minArchivesSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_apks_size_mb"]?.let { prefs.maxApksSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_apks_size_mb"]?.let { prefs.minApksSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["max_other_size_mb"]?.let { prefs.maxOtherSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["min_other_size_mb"]?.let { prefs.minOtherSizeMB = it.toDoubleOrNull() ?: 0.0 }
                    settingsMap["backup_camera"]?.let { prefs.backupCamera = it.toBoolean() }
                    settingsMap["backup_downloads"]?.let { prefs.backupDownloads = it.toBoolean() }
                    settingsMap["backup_documents"]?.let { prefs.backupDocuments = it.toBoolean() }
                    settingsMap["backup_videos"]?.let { prefs.backupVideos = it.toBoolean() }
                    settingsMap["backup_apks"]?.let { prefs.backupApks = it.toBoolean() }
                    settingsMap["wifi_only"]?.let { prefs.wifiOnly = it.toBoolean() }
                    settingsMap["enable_data_compression"]?.let { prefs.enableDataCompression = it.toBoolean() }
                    settingsMap["is_biometric_enabled"]?.let { prefs.isBiometricEnabled = it.toBoolean() }
                    settingsMap["app_theme"]?.let { prefs.appTheme = it.toIntOrNull() ?: 0 }
                    settingsMap["trash_auto_clean_days"]?.let { prefs.trashAutoCleanDays = it.toIntOrNull() ?: 30 }
                    settingsMap["stop_all_backups"]?.let { prefs.stopAllBackups = it.toBoolean() }
                    settingsMap["credentials_verified"]?.let { prefs.credentialsVerified = it.toBoolean() }
                    settingsMap["encryption_key"]?.let {
                        if (it.isNotBlank() && it != "default_aes_key") {
                            prefs.forceSetEncryptionKey(it)
                        }
                    }
                }

                // Sync credentials to PostgreSQL DB if missing, or retrieve if local is completely blank
                val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val userEmail = authPrefs.getString("user_email", "") ?: prefs.recoveryEmail
                if (userEmail.isNotEmpty()) {
                    val serverApi = ServerApi()
                    try {
                        if (prefs.botToken.isEmpty()) {
                            val creds = serverApi.getCredentials(prefs.serverUrl, userEmail)
                            if (creds != null && creds.first.isNotEmpty()) {
                                prefs.botToken = creds.first
                                prefs.chatId = creds.second
                                prefs.credentialsVerified = true
                                dao.saveSettings(
                                    listOf(
                                        AppSettingEntity("bot_token", creds.first),
                                        AppSettingEntity("chat_id", creds.second),
                                        AppSettingEntity("credentials_verified", "true")
                                    )
                                )
                            }
                        } else {
                            serverApi.saveCredentials(prefs.serverUrl, userEmail, prefs.botToken, prefs.chatId)
                        }
                    } catch (_: Exception) { }
                }

                // Auto-restore complete profile, master encryption key, bot fleet, channel routes, and sub-settings from cloud
                CloudProfileSyncManager.autoRestoreConfigFromCloud(context)

                // Automatically restore cloud file index into local Room DB
                restoreCloudFileIndexToRoom(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Queries PostgreSQL Cloud Database for all files indexed under the user's email account.
     * Inserts any missing cloud files into local Room Database table `uploaded_files`.
     * Guarantees that app updates or database migrations NEVER lose previous backup history!
     */
    suspend fun restoreCloudFileIndexToRoom(context: Context, forceFullSync: Boolean = false): Int = withContext(Dispatchers.IO) {
        try {
            val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val prefs = AppPrefs(context)
            var userEmail = authPrefs.getString("user_email", "") ?: ""
            if (userEmail.isEmpty()) {
                userEmail = prefs.userEmail.trim().lowercase()
            }
            if (userEmail.isEmpty()) {
                userEmail = prefs.recoveryEmail.trim().lowercase()
            }
            if (userEmail.isEmpty()) return@withContext 0

            val db = AppDatabase.getDatabase(context)
            val dao = db.uploadedFileDao()

            // Calculate delta sync threshold if local database already has records
            val existingCount = try { dao.getCount() } catch (_: Exception) { 0 }
            val sinceTimestamp = if (existingCount > 0 && !forceFullSync) {
                val latestTime = try { dao.getAllFiles().firstOrNull()?.timestamp ?: 0L } catch (_: Exception) { 0L }
                (latestTime - 60_000L).coerceAtLeast(0L) // 1 min buffer
            } else {
                0L
            }

            val serverApi = ServerApi()
            val cloudFiles = serverApi.listFiles(prefs.serverUrl, userEmail, since = sinceTimestamp)

            if (cloudFiles.isNotEmpty()) {
                val existingTrashMap = try { dao.getTrashFiles().associateBy { it.path } } catch (_: Exception) { emptyMap() }
                val filesToInsert = cloudFiles.map { cloudFile ->
                    val trashRecord = existingTrashMap[cloudFile.path]
                    if (trashRecord != null) {
                        cloudFile.isDeleted = true
                        cloudFile.deletedAt = trashRecord.deletedAt
                    }
                    cloudFile
                }
                // Batch insert in chunks of 500 to avoid SQLite argument limits
                filesToInsert.chunked(500).forEach { batch ->
                    dao.insertAll(batch)
                }
            }

            // High-Speed Two-Way Synchronization: Push any unsynced local records using Batch API
            val localFiles = dao.getAllFiles()
            val cloudPathMap = cloudFiles.map { it.path }.toSet()
            val unsyncedLocal = localFiles.filter { !cloudPathMap.contains(it.path) }
            if (unsyncedLocal.isNotEmpty()) {
                serverApi.indexFilesBatch(prefs.serverUrl, userEmail, unsyncedLocal)
            }

            return@withContext (cloudFiles.size + unsyncedLocal.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext 0
    }

    /**
     * Saves a section/category of settings to Room Database and updates AppPrefs.
     * Displays a Toast feedback on main thread.
     */
    suspend fun saveCategorySettings(
        context: Context,
        categoryName: String,
        settingsMap: Map<String, String>,
        showToast: Boolean = true
    ) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.appSettingDao()
                val prefs = AppPrefs(context)
                val now = System.currentTimeMillis()

                val entities = settingsMap.map { (key, value) ->
                    AppSettingEntity(key = key, value = value, updatedAt = now)
                }

                dao.saveSettings(entities)

                // Sync keys to AppPrefs immediately
                settingsMap["profile_picture_uri"]?.let {
                    prefs.profilePictureUri = it
                    prefs.profileUpdatedAt = now
                }
                settingsMap["display_name"]?.let {
                    prefs.displayName = it
                    prefs.profileUpdatedAt = now
                }
                settingsMap["recovery_email"]?.let { prefs.recoveryEmail = it }
                settingsMap["bot_token"]?.let { prefs.botToken = it }
                settingsMap["chat_id"]?.let { prefs.chatId = it }
                settingsMap["auto_backup_enabled"]?.let { prefs.autoBackupEnabled = it.toBoolean() }
                settingsMap["smart_backup_mode"]?.let { prefs.smartBackupMode = it.toIntOrNull() ?: 0 }
                settingsMap["night_backup_hour"]?.let { prefs.nightBackupHour = it.toIntOrNull() ?: 2 }
                settingsMap["smart_scan_interval_hours"]?.let { prefs.smartScanIntervalHours = it.toIntOrNull() ?: 1 }
                settingsMap["min_file_size_mb"]?.let { prefs.minFileSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_file_size_mb"]?.let { prefs.maxFileSizeMB = it.toDoubleOrNull() ?: 2000.0 }
                settingsMap["max_photos_size_mb"]?.let { prefs.maxPhotosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_photos_size_mb"]?.let { prefs.minPhotosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_videos_size_mb"]?.let { prefs.maxVideosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_videos_size_mb"]?.let { prefs.minVideosSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_docs_size_mb"]?.let { prefs.maxDocsSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_docs_size_mb"]?.let { prefs.minDocsSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_audio_size_mb"]?.let { prefs.maxAudioSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_audio_size_mb"]?.let { prefs.minAudioSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_archives_size_mb"]?.let { prefs.maxArchivesSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_archives_size_mb"]?.let { prefs.minArchivesSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_apks_size_mb"]?.let { prefs.maxApksSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_apks_size_mb"]?.let { prefs.minApksSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["max_other_size_mb"]?.let { prefs.maxOtherSizeMB = it.toDoubleOrNull() ?: 0.0 }
                settingsMap["min_other_size_mb"]?.let { prefs.minOtherSizeMB = it.toDoubleOrNull() ?: 0.0 }

                // Trigger complete cloud profile, fleet, and settings manifest synchronization
                CloudProfileSyncManager.syncLocalConfigToCloud(context)

                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "💾 $categoryName Settings Saved & Synced!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (showToast) {
                        Toast.makeText(context, "Failed to save settings: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
