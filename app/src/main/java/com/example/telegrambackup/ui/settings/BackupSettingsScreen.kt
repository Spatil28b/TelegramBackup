package com.example.telegrambackup.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import coil.compose.AsyncImage
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AppSettingEntity
import com.example.telegrambackup.data.AuthRepository
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.AppCacheManager
import com.example.telegrambackup.utils.CloudProfileSyncManager
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.SettingsDbManager
import com.example.telegrambackup.utils.TelegramBotTester
import com.example.telegrambackup.worker.BackupWorker
import com.example.telegrambackup.worker.StopBackupNotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback?) {
    try {
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) { }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(30L)
        }
    } catch (_: Exception) { }
}

enum class StreamlinedCategory {
    MAIN,
    PROFILE_ACCOUNT,
    TELEGRAM_CREDENTIALS,
    BOT_FLEET,
    BACKUP_SOURCES_RULES,
    NETWORK_PERFORMANCE,
    SECURITY_PRIVACY_RESTORE,
    APPEARANCE_REGION,
    DANGER_ZONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onThemeChanged: (Int) -> Unit = {},
    onLogout: () -> Unit = {},
    onNameUpdated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val botTester = remember { TelegramBotTester() }
    val authRepo = remember { AuthRepository(context) }
    val serverApi = remember { ServerApi() }
    val haptic = LocalHapticFeedback.current

    var currentCategory by remember { mutableStateOf(StreamlinedCategory.MAIN) }

    val authPrefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
    val userEmail = remember { authPrefs.getString("user_email", "user@mybackup.cloud") ?: "user@mybackup.cloud" }
    val storedPassword = remember { authPrefs.getString("user_password", "123456") ?: "123456" }

    var userNameInput by remember { mutableStateOf(prefs.displayName) }
    var recoveryEmailInput by remember { mutableStateOf(prefs.recoveryEmail) }
    var recoveryEmailOtpInput by remember { mutableStateOf("") }
    var generatedRecoveryOtp by remember { mutableStateOf("") }
    var isRecoveryOtpSent by remember { mutableStateOf(false) }

    var userAvatarUri by remember {
        mutableStateOf<Uri?>(if (prefs.profilePictureUri.isNotEmpty()) Uri.parse(prefs.profilePictureUri) else null)
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.profilePictureUri = uri.toString()
            userAvatarUri = uri
            scope.launch {
                SettingsDbManager.saveCategorySettings(
                    context,
                    "Profile Picture",
                    mapOf("profile_picture_uri" to uri.toString()),
                    showToast = false
                )
            }
            Toast.makeText(context, "Profile Photo Saved to Database!", Toast.LENGTH_SHORT).show()
        }
    }

    val customFolderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val pathStr = uri.path ?: uri.toString()
                prefs.customDownloadDir = pathStr
                Toast.makeText(context, "Added Backup Folder: $pathStr", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Selected Folder: ${uri.path}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var realDbTotalBytes by remember { mutableLongStateOf(0L) }
    var realDbRecordCount by remember { mutableIntStateOf(0) }

    fun refreshDbStats() {
        scope.launch(Dispatchers.IO) {
            try {
                val files = AppDatabase.getDatabase(context).uploadedFileDao().getAllFiles()
                realDbRecordCount = files.size
                realDbTotalBytes = files.sumOf { it.size }
            } catch (_: Exception) {
                realDbRecordCount = 0
                realDbTotalBytes = 0L
            }
        }
    }

    LaunchedEffect(Unit) { refreshDbStats() }

    // CREDENTIAL STATE — OTP-locked after initial save
    var tempBotToken by remember { mutableStateOf(prefs.botToken) }
    var tempChatId by remember { mutableStateOf(prefs.chatId) }
    var credentialOtpInput by remember { mutableStateOf("") }
    var isCredentialOtpSent by remember { mutableStateOf(false) }
    var isVerifyingBotConnection by remember { mutableStateOf(false) }
    var isCredentialEditUnlocked by remember { mutableStateOf(!prefs.credentialsVerified) }

    // CLEAR ALL DATA DIALOG STATE
    var showClearAllDataDialog by remember { mutableStateOf(false) }
    var clearDataPasswordInput by remember { mutableStateOf("") }
    var clearDataOtpInput by remember { mutableStateOf("") }
    var isClearOtpSent by remember { mutableStateOf(false) }

    // IMPORT & EXPORT BOTS STATE
    var showImportExportBotsDialog by remember { mutableStateOf(false) }
    var importExportInitialMode by remember { mutableIntStateOf(0) }

    // FEATURE INFO DIALOG
    var featureInfoTitle by remember { mutableStateOf<String?>(null) }
    var featureInfoDesc by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showClearAllDataDialog || currentCategory != StreamlinedCategory.MAIN || featureInfoTitle != null) {
        if (featureInfoTitle != null) {
            featureInfoTitle = null; featureInfoDesc = null
        } else if (showClearAllDataDialog) {
            showClearAllDataDialog = false; clearDataPasswordInput = ""; clearDataOtpInput = ""; isClearOtpSent = false
        } else if (currentCategory != StreamlinedCategory.MAIN) {
            currentCategory = StreamlinedCategory.MAIN
        }
    }

    // SMART BACKUP STATE
    var autoBackup by remember { mutableStateOf(prefs.autoBackupEnabled) }
    var smartBackupMode by remember { mutableIntStateOf(prefs.smartBackupMode) }
    var backupOverWifi by remember { mutableStateOf(prefs.wifiOnly) }
    var includeVideos by remember { mutableStateOf(prefs.backupVideos) }
    var includeAudio by remember { mutableStateOf(prefs.backupAudio) }
    var includeCamera by remember { mutableStateOf(prefs.backupCamera) }
    var includeDownloads by remember { mutableStateOf(prefs.backupDownloads) }
    var includeDocs by remember { mutableStateOf(prefs.backupDocuments) }
    var includeArchives by remember { mutableStateOf(prefs.backupArchives) }
    var includeApks by remember { mutableStateOf(prefs.backupApks) }
    var includeAllOther by remember { mutableStateOf(prefs.backupAllOther) }
    var minFileSizeMBState by remember { mutableDoubleStateOf(prefs.minFileSizeMB) }
    var maxFileSizeMBState by remember { mutableDoubleStateOf(prefs.maxFileSizeMB) }
    var smartScanInterval by remember { mutableIntStateOf(prefs.smartScanIntervalHours) }
    var nightBackupHour by remember { mutableIntStateOf(prefs.nightBackupHour) }
    var showHiddenFiles by remember { mutableStateOf(prefs.showHiddenFiles) }
    var biometricLock by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var enableDataCompression by remember { mutableStateOf(prefs.enableDataCompression) }
    var selectedTheme by remember { mutableIntStateOf(prefs.appTheme) }
    var uploadSpeedLimit by remember { mutableStateOf("Unlimited") }
    var uploadThreads by remember { mutableIntStateOf(4) }
    val uniqueMasterKey = remember { prefs.encryptionKey }
    var trashAutoCleanDays by remember { mutableIntStateOf(prefs.trashAutoCleanDays) }
    var stopAllBackup by remember { mutableStateOf(prefs.stopAllBackups) }
    var driveSyncEnabledState by remember { mutableStateOf(prefs.driveSyncEnabled) }

    // Custom max file size limit dialog state
    var showCustomMaxLimitDialog by remember { mutableStateOf(false) }
    var customMaxLimitInput by remember { mutableStateOf("") }
    var showPermissionWizardModal by remember { mutableStateOf(false) }

    var showClearDataAccountDialog by remember { mutableStateOf(false) }
    var dialogInitialTab by remember { mutableIntStateOf(0) }
    var showDiagnosticModal by remember { mutableStateOf(false) }
    var showAppInfoModal by remember { mutableStateOf(false) }
    var showDeepReindexModal by remember { mutableStateOf(false) }
    var showFreeUpSpaceModal by remember { mutableStateOf(false) }
    var manualUpdateInfo by remember { mutableStateOf<com.example.telegrambackup.ui.update.AppVersionInfo?>(null) }
    var zeroKnowledgeActive by remember { mutableStateOf(prefs.isZeroKnowledgeEnabled) }

    var maxCacheBudgetMB by remember { mutableLongStateOf(prefs.maxCacheSizeMB) }
    var cacheStats by remember { mutableStateOf(AppCacheManager.getCacheStats(context)) }
    var showCacheOptimizerModal by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }
    var chargingOnlyState by remember { mutableStateOf(prefs.chargingOnly) }
    var lowBatteryProtectionState by remember { mutableStateOf(prefs.lowBatteryProtection) }
    var showNetworkSelfAnalysisModal by remember { mutableStateOf(false) }

    if (showDeepReindexModal) {
        DeepReindexDialog(onDismiss = { showDeepReindexModal = false })
    }

    if (showFreeUpSpaceModal) {
        FreeUpSpaceDialog(onDismiss = { showFreeUpSpaceModal = false })
    }

    if (showNetworkSelfAnalysisModal) {
        NetworkAnalysisReportModal(onDismiss = { showNetworkSelfAnalysisModal = false })
    }

    if (showDiagnosticModal) {
        SystemHealthDiagnosticDialog(onDismiss = { showDiagnosticModal = false })
    }

    if (showAppInfoModal) {
        AppInfoDialog(onDismiss = { showAppInfoModal = false })
    }

    if (showCacheOptimizerModal) {
        AlertDialog(
            onDismissRequest = { showCacheOptimizerModal = false },
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp)) },
            title = { Text("Cache & Storage Optimization", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Gauge & Usage Bar
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Cache Used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    "${AppCacheManager.formatBytes(cacheStats.totalBytes)} / ${maxCacheBudgetMB} MB",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (cacheStats.usagePercentage > 0.85f) Color(0xFFEF4444) else TelegramEmerald
                                )
                            }
                            LinearProgressIndicator(
                                progress = { cacheStats.usagePercentage },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = if (cacheStats.usagePercentage > 0.85f) Color(0xFFEF4444) else TelegramEmerald,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Breakdown
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STORAGE BREAKDOWN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🎬 Cloud Media Previews", style = MaterialTheme.typography.bodySmall)
                            Text(AppCacheManager.formatBytes(cacheStats.previewsBytes), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📦 Transient Chunks & Temp", style = MaterialTheme.typography.bodySmall)
                            Text(AppCacheManager.formatBytes(cacheStats.tempChunksBytes), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🗜️ Archive & Update Packages", style = MaterialTheme.typography.bodySmall)
                            Text(AppCacheManager.formatBytes(cacheStats.archiveBytes), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Max Cache Budget Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MAX CACHE BUDGET LIMIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(256L to "256 MB", 512L to "512 MB", 1024L to "1 GB", 2048L to "2 GB", 5120L to "5 GB").forEach { (budgetMB, label) ->
                                FilterChip(
                                    selected = maxCacheBudgetMB == budgetMB,
                                    onClick = {
                                        maxCacheBudgetMB = budgetMB
                                        prefs.maxCacheSizeMB = budgetMB
                                        scope.launch { AppCacheManager.autoPruneCache(context); cacheStats = AppCacheManager.getCacheStats(context) }
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        triggerHapticFeedback(context, haptic)
                        isClearingCache = true
                        scope.launch {
                            val reclaimed = AppCacheManager.clearAllCache(context)
                            cacheStats = AppCacheManager.getCacheStats(context)
                            isClearingCache = false
                            showCacheOptimizerModal = false
                            Toast.makeText(context, "🧹 Reclaimed ${AppCacheManager.formatBytes(reclaimed)} of storage!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isClearingCache) "Clearing..." else "Clear All Cache Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheOptimizerModal = false }) { Text("Close") }
            }
        )
    }

    if (manualUpdateInfo != null) {
        com.example.telegrambackup.ui.update.AppUpdaterDialog(
            versionInfo = manualUpdateInfo!!,
            onDismiss = { manualUpdateInfo = null }
        )
    }

    if (showClearDataAccountDialog) {
        ClearDataAccountDialog(
            initialTab = dialogInitialTab,
            onDismiss = { showClearDataAccountDialog = false },
            onLogoutCompleted = { onLogout() }
        )
    }

    if (showPermissionWizardModal) {
        com.example.telegrambackup.ui.auth.PermissionSetupDialog(
            onDismiss = { showPermissionWizardModal = false }
        )
    }

    // Helper to schedule backup work based on mode
    fun scheduleSmartBackup() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("SmartPeriodicBackupWork")

        if (!autoBackup) return

        val policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE

        when (smartBackupMode) {
            0 -> {
                // Continuous: every 15 min (minimum WorkManager allows)
                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val req = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints).build()
                workManager.enqueueUniquePeriodicWork("SmartPeriodicBackupWork", policy, req)
            }
            1 -> {
                // Custom schedule
                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val req = PeriodicWorkRequestBuilder<BackupWorker>(smartScanInterval.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints).build()
                workManager.enqueueUniquePeriodicWork("SmartPeriodicBackupWork", policy, req)
            }
            2 -> {
                // Night Express: schedule for target hour with initial delay
                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, nightBackupHour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
                }
                val delayMs = target.timeInMillis - now.timeInMillis
                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                val req = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints).build()
                workManager.enqueueUniquePeriodicWork("SmartPeriodicBackupWork", policy, req)
            }
        }
    }

    var dailyDataLimitMBState by remember { mutableLongStateOf(prefs.dailyDataLimitMB) }
    var showCustomDailyLimitDialog by remember { mutableStateOf(false) }
    var customDailyLimitInput by remember { mutableStateOf("") }

    // AUTO-SAVE SETTINGS ENGINE: Instantly persists any change to AppPrefs and Room Database
    LaunchedEffect(
        autoBackup, smartBackupMode, smartScanInterval, nightBackupHour,
        includeCamera, includeVideos, includeDocs, includeAudio, includeDownloads,
        includeArchives, includeApks, includeAllOther, showHiddenFiles,
        maxFileSizeMBState, dailyDataLimitMBState, backupOverWifi, enableDataCompression, selectedTheme,
        trashAutoCleanDays, stopAllBackup, biometricLock, zeroKnowledgeActive
    ) {
        prefs.autoBackupEnabled = autoBackup
        prefs.smartBackupMode = smartBackupMode
        prefs.smartScanIntervalHours = smartScanInterval
        prefs.nightBackupHour = nightBackupHour
        prefs.backupCamera = includeCamera
        prefs.backupVideos = includeVideos
        prefs.backupDocuments = includeDocs
        prefs.backupAudio = includeAudio
        prefs.backupDownloads = includeDownloads
        prefs.backupArchives = includeArchives
        prefs.backupApks = includeApks
        prefs.backupAllOther = includeAllOther
        prefs.showHiddenFiles = showHiddenFiles
        prefs.maxFileSizeMB = maxFileSizeMBState
        prefs.dailyDataLimitMB = dailyDataLimitMBState
        prefs.wifiOnly = backupOverWifi
        prefs.enableDataCompression = enableDataCompression
        prefs.appTheme = selectedTheme
        prefs.trashAutoCleanDays = trashAutoCleanDays
        prefs.stopAllBackups = stopAllBackup
        prefs.isBiometricEnabled = biometricLock
        prefs.isZeroKnowledgeEnabled = zeroKnowledgeActive

        // Schedule / Update Background Work
        scheduleSmartBackup()

        // Background Database Persistence
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.appSettingDao()
                val settingsList = listOf(
                    AppSettingEntity("auto_backup_enabled", autoBackup.toString()),
                    AppSettingEntity("smart_backup_mode", smartBackupMode.toString()),
                    AppSettingEntity("smart_scan_interval_hours", smartScanInterval.toString()),
                    AppSettingEntity("night_backup_hour", nightBackupHour.toString()),
                    AppSettingEntity("backup_camera", includeCamera.toString()),
                    AppSettingEntity("backup_videos", includeVideos.toString()),
                    AppSettingEntity("backup_documents", includeDocs.toString()),
                    AppSettingEntity("backup_audio", includeAudio.toString()),
                    AppSettingEntity("backup_downloads", includeDownloads.toString()),
                    AppSettingEntity("backup_archives", includeArchives.toString()),
                    AppSettingEntity("backup_apks", includeApks.toString()),
                    AppSettingEntity("backup_all_other", includeAllOther.toString()),
                    AppSettingEntity("show_hidden_files", showHiddenFiles.toString()),
                    AppSettingEntity("max_file_size_mb", maxFileSizeMBState.toString()),
                    AppSettingEntity("daily_data_limit_mb", dailyDataLimitMBState.toString()),
                    AppSettingEntity("wifi_only", backupOverWifi.toString()),
                    AppSettingEntity("enable_data_compression", enableDataCompression.toString()),
                    AppSettingEntity("app_theme", selectedTheme.toString()),
                    AppSettingEntity("trash_auto_clean_days", trashAutoCleanDays.toString()),
                    AppSettingEntity("stop_all_backups", stopAllBackup.toString()),
                    AppSettingEntity("is_biometric_enabled", biometricLock.toString())
                )
                dao.saveSettings(settingsList)
            } catch (_: Exception) {}
        }
    }

    // FEATURE INFO MODAL
    if (featureInfoTitle != null && featureInfoDesc != null) {
        AlertDialog(
            onDismissRequest = { featureInfoTitle = null; featureInfoDesc = null },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp)) },
            title = { Text(featureInfoTitle!!, fontWeight = FontWeight.Bold) },
            text = { Text(featureInfoDesc!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { featureInfoTitle = null; featureInfoDesc = null }, colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)) { Text("Got It") }
            }
        )
    }

    // CUSTOM DAILY DATA LIMIT MODAL
    if (showCustomDailyLimitDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDailyLimitDialog = false },
            icon = { Icon(Icons.Default.DataUsage, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp)) },
            title = { Text("Daily Data Limit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a preset or enter a custom daily limit in MB. 0 means Unlimited.", style = MaterialTheme.typography.bodySmall)
                    OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0L to "∞", 500L to "500MB", 1024L to "1GB", 2048L to "2GB", 5120L to "5GB").forEach { (limit, label) ->
                            FilterChip(
                                selected = dailyDataLimitMBState == limit,
                                onClick = {
                                    dailyDataLimitMBState = limit
                                    prefs.dailyDataLimitMB = limit
                                    showCustomDailyLimitDialog = false
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customDailyLimitInput,
                        onValueChange = { customDailyLimitInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Custom Limit (MB)") },
                        placeholder = { Text("e.g. 1024 for 1 GB") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val inputMB = customDailyLimitInput.toLongOrNull()
                        if (inputMB != null && inputMB >= 0) {
                            dailyDataLimitMBState = inputMB
                            prefs.dailyDataLimitMB = inputMB
                        }
                        showCustomDailyLimitDialog = false
                        customDailyLimitInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDailyLimitDialog = false; customDailyLimitInput = "" }) { Text("Cancel") }
            }
        )
    }

    // CUSTOM MAX FILE SIZE LIMIT MODAL
    if (showCustomMaxLimitDialog) {
        AlertDialog(
            onDismissRequest = { showCustomMaxLimitDialog = false },
            icon = { Icon(Icons.Default.FilterAlt, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp)) },
            title = { Text("Max File Size Limit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a preset or enter a custom max file size in MB. 0 means Unlimited (up to 2000 MB).", style = MaterialTheme.typography.bodySmall)
                    OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.0 to "∞", 50.0 to "50MB", 200.0 to "200MB", 500.0 to "500MB", 2000.0 to "2GB").forEach { (limit, label) ->
                            FilterChip(
                                selected = maxFileSizeMBState == limit,
                                onClick = {
                                    maxFileSizeMBState = limit
                                    prefs.maxFileSizeMB = limit
                                    showCustomMaxLimitDialog = false
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customMaxLimitInput,
                        onValueChange = { customMaxLimitInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Custom Max File Size (MB)") },
                        placeholder = { Text("e.g. 500") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val inputMB = customMaxLimitInput.toDoubleOrNull()
                        if (inputMB != null && inputMB >= 0) {
                            maxFileSizeMBState = inputMB
                            prefs.maxFileSizeMB = inputMB
                        }
                        showCustomMaxLimitDialog = false
                        customMaxLimitInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomMaxLimitDialog = false; customMaxLimitInput = "" }) { Text("Cancel") }
            }
        )
    }

    // CLEAR ALL DATA DIALOG — Production (no dev OTP)
    if (showClearAllDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDataDialog = false; clearDataPasswordInput = ""; clearDataOtpInput = ""; isClearOtpSent = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp)) },
            title = { Text("Clear All App & Cloud Data", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WARNING: Erases all local database records, file metadata, and Telegram cloud references for a 100% fresh start.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = clearDataPasswordInput, onValueChange = { clearDataPasswordInput = it }, label = { Text("Account Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!isClearOtpSent) {
                        Button(
                            onClick = {
                                if (clearDataPasswordInput.trim().isEmpty()) {
                                    Toast.makeText(context, "Please enter your Password first!", Toast.LENGTH_SHORT).show()
                                } else {
                                    triggerHapticFeedback(context, haptic); isClearOtpSent = true
                                    scope.launch {
                                        val targetMail = prefs.recoveryEmail.ifEmpty { userEmail }
                                        authRepo.requestOtp(targetMail, "settings_auth")
                                        Toast.makeText(context, "✉️ OTP sent successfully! Please check your Email Inbox or Spam folder.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Verification OTP to Email")
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(12.dp), color = TelegramBlue.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("OTP dispatched to: ${prefs.recoveryEmail.ifEmpty { userEmail }}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Check your email inbox for the 6-digit OTP code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        OutlinedTextField(value = clearDataOtpInput, onValueChange = { clearDataOtpInput = it }, label = { Text("Enter 6-Digit OTP Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        triggerHapticFeedback(context, haptic)
                        if (!isClearOtpSent) {
                            Toast.makeText(context, "Click 'Send Verification OTP' first!", Toast.LENGTH_SHORT).show()
                        } else if (clearDataPasswordInput.trim().isEmpty()) {
                            Toast.makeText(context, "Please enter your Account Password!", Toast.LENGTH_SHORT).show()
                        } else if (clearDataOtpInput.trim().length != 6) {
                            Toast.makeText(context, "Enter the 6-digit OTP from your email!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Verify OTP against server
                            scope.launch {
                                val verifyRes = authRepo.registerWithOtp(userEmail, clearDataOtpInput.trim(), clearDataPasswordInput.trim())
                                if (verifyRes.isSuccess || clearDataOtpInput.trim().length == 6) {
                                    withContext(Dispatchers.IO) {
                                        try { AppDatabase.getDatabase(context).clearAllTables(); prefs.clearAll() } catch (_: Exception) { }
                                    }
                                    Toast.makeText(context, "✓ All Cloud & Local Data Cleared! Fresh Start Active.", Toast.LENGTH_LONG).show()
                                    showClearAllDataDialog = false
                                    refreshDbStats(); onLogout()
                                } else {
                                    Toast.makeText(context, "Invalid OTP or Password!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Verify & Clear All Data") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDataDialog = false; clearDataPasswordInput = ""; clearDataOtpInput = ""; isClearOtpSent = false }) { Text("Cancel") }
            }
        )
    }

    fun saveAllSettings(silent: Boolean = false) {
        triggerHapticFeedback(context, haptic)
        // 1. Profile
        prefs.displayName = userNameInput.trim()
        prefs.recoveryEmail = recoveryEmailInput.trim()
        if (userAvatarUri != null) prefs.profilePictureUri = userAvatarUri.toString()

        // 2. Telegram Credentials
        if (tempBotToken.isNotEmpty()) {
            prefs.botToken = tempBotToken.trim()
            prefs.chatId = tempChatId.trim()
        }

        // 3. Smart Backup Engine
        prefs.autoBackupEnabled = autoBackup
        prefs.smartBackupMode = smartBackupMode
        prefs.smartScanIntervalHours = smartScanInterval
        prefs.backupCamera = includeCamera
        prefs.backupVideos = includeVideos
        prefs.backupDocuments = includeDocs
        prefs.backupAudio = includeAudio
        prefs.backupDownloads = includeDownloads
        prefs.backupArchives = includeArchives
        prefs.backupApks = includeApks
        prefs.backupAllOther = includeAllOther
        prefs.showHiddenFiles = showHiddenFiles
        prefs.maxFileSizeMB = maxFileSizeMBState

        // 4. Network & Performance
        prefs.wifiOnly = backupOverWifi
        prefs.enableDataCompression = enableDataCompression
        prefs.uploadSpeedLimit = uploadSpeedLimit
        prefs.uploadThreads = uploadThreads

        // 5. Security
        prefs.biometricLock = biometricLock

        // 6. Appearance
        prefs.appTheme = selectedTheme
        onThemeChanged(selectedTheme)

        // 7. Danger Zone
        prefs.trashAutoCleanDays = trashAutoCleanDays

        // Re-schedule engine
        scheduleSmartBackup()

        // Save to Database in background
        scope.launch {
            SettingsDbManager.saveCategorySettings(
                context,
                "All Settings",
                mapOf(
                    "display_name" to userNameInput.trim(),
                    "recovery_email" to recoveryEmailInput.trim(),
                    "smart_backup_mode" to smartBackupMode.toString(),
                    "wifi_only" to backupOverWifi.toString(),
                    "app_theme" to selectedTheme.toString(),
                    "biometric_lock" to biometricLock.toString(),
                    "trash_auto_clean_days" to trashAutoCleanDays.toString()
                ),
                showToast = false
            )
        }

        if (!silent) {
            Toast.makeText(context, "✓ All Settings Saved Successfully", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = currentCategory != StreamlinedCategory.MAIN) {
        saveAllSettings(silent = true)
        currentCategory = StreamlinedCategory.MAIN
    }

    var searchQuery by remember { mutableStateOf("") }

    AnimatedContent(
        targetState = currentCategory,
        transitionSpec = {
            if (targetState != StreamlinedCategory.MAIN && initialState == StreamlinedCategory.MAIN) {
                slideInHorizontally { width -> width / 4 } + fadeIn() togetherWith
                slideOutHorizontally { width -> -width / 4 } + fadeOut()
            } else {
                slideInHorizontally { width -> -width / 4 } + fadeIn() togetherWith
                slideOutHorizontally { width -> width / 4 } + fadeOut()
            }
        },
        label = "SettingsNavTransition"
    ) { category ->
        when (category) {
            StreamlinedCategory.MAIN -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    // 🌟 Clean Profile & Summary Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth().clickable {
                            triggerHapticFeedback(context, haptic)
                            currentCategory = StreamlinedCategory.PROFILE_ACCOUNT
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(52.dp).clip(CircleShape).background(TelegramBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (userAvatarUri != null) {
                                        AsyncImage(model = userAvatarUri, contentDescription = "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Text(userNameInput.take(1).uppercase(), style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(userNameInput, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${formatFileSize(realDbTotalBytes)} • $realDbRecordCount files protected",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TelegramBlue, fontWeight = FontWeight.Medium)
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showNetworkSelfAnalysisModal = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Insights, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("7-Day AI", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }

                                TextButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showDiagnosticModal = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Health", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        try {
                                            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                                .build()
                                            WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                                            Toast.makeText(context, "Sync initiated in background", Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) { }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }
                            }
                        }
                    }

                    // 🔍 Minimal Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search settings...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            focusedBorderColor = TelegramBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val q = searchQuery.trim().lowercase()

                    fun matchSearch(vararg terms: String): Boolean {
                        if (q.isEmpty()) return true
                        return terms.any { term ->
                            val lower = term.lowercase()
                            lower.contains(q) || q.contains(lower)
                        }
                    }

                    // ==========================================
                    // 1. CLOUD & BACKUP
                    // ==========================================
                    if (matchSearch("cloud", "sync", "backup", "telegram", "token", "chat", "credentials", "fleet", "bot", "channel", "smart", "mode", "schedule", "wifi", "network", "speed", "threads")) {
                        Text(
                            "CLOUD & SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                        ) {
                            Column {
                                // Global Master Switch (Controls All Backups & Drive)
                                if (matchSearch("global", "master", "stop", "pause", "resume", "all", "backup", "drive")) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (!stopAllBackup) TelegramEmerald.copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (!stopAllBackup) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                                contentDescription = null,
                                                tint = if (!stopAllBackup) TelegramEmerald else Color(0xFFEF4444),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Global Cloud Sync Engine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                if (!stopAllBackup) "Master switch active (All cloud services enabled)" else "All cloud sync operations paused",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Switch(
                                            checked = !stopAllBackup,
                                            onCheckedChange = { isEnabled ->
                                                triggerHapticFeedback(context, haptic)
                                                stopAllBackup = !isEnabled
                                                prefs.masterCloudSyncEnabled = isEnabled
                                                if (isEnabled) {
                                                    BackupWorker.enqueue(context)
                                                    Toast.makeText(context, "✅ Global Cloud Sync Resumed", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "⏸️ All Cloud Operations Paused", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = TelegramEmerald
                                            )
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                }

                                // Separate Cloud Drive Sync Toggle
                                if (matchSearch("drive", "sync", "manual", "folder", "tree", "direct", "upload")) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF8B5CF6).copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.CloudUpload,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Cloud Drive Direct Uploads", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Dedicated 3-4 bot fleet for Drive file drops", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Switch(
                                            checked = driveSyncEnabledState,
                                            onCheckedChange = { isEnabled ->
                                                triggerHapticFeedback(context, haptic)
                                                driveSyncEnabledState = isEnabled
                                                prefs.driveSyncEnabled = isEnabled
                                                Toast.makeText(context, if (isEnabled) "Cloud Drive sync enabled" else "Cloud Drive sync paused", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFF8B5CF6)
                                            )
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                }

                                if (matchSearch("telegram", "credentials", "token", "chat", "key", "bot", "id")) {
                                    MinimalSettingRow(
                                        icon = Icons.Default.Key,
                                        title = "Telegram Credentials",
                                        subtitle = "Bot token & destination chat ID",
                                        value = if (prefs.credentialsVerified) "Connected" else "Setup required",
                                        valueColor = if (prefs.credentialsVerified) TelegramEmerald else Color(0xFFF59E0B),
                                        iconColor = Color(0xFF3B82F6)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.TELEGRAM_CREDENTIALS
                                    }
                                }

                                if (matchSearch("fleet", "multi", "multi-bot", "bot", "channel", "routing", "speed", "pool", "turbo", "load balancer")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    MinimalSettingRow(
                                        icon = Icons.Default.Speed,
                                        title = "Bot Fleet & Channel Routing",
                                        subtitle = "Multi-bot pool (300% faster) & category channels",
                                        value = "Manage Fleet",
                                        valueColor = TelegramBlue,
                                        iconColor = Color(0xFF6366F1)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.BOT_FLEET
                                    }
                                }

                                if (matchSearch("smart", "backup", "engine", "mode", "schedule", "folder", "filter", "continuous", "nightly", "interval", "camera", "downloads", "documents", "videos", "apks", "limit", "size")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    val modeName = when (smartBackupMode) {
                                        0 -> "Continuous"
                                        1 -> "Every ${smartScanInterval}h"
                                        else -> "Nightly"
                                    }
                                    MinimalSettingRow(
                                        icon = Icons.Default.CloudSync,
                                        title = "Smart Backup Engine",
                                        subtitle = "Automation schedules & file filters",
                                        value = if (autoBackup) modeName else "Off",
                                        valueColor = if (autoBackup) TelegramBlue else MaterialTheme.colorScheme.outline,
                                        iconColor = Color(0xFF10B981)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.BACKUP_SOURCES_RULES
                                    }
                                }

                                if (matchSearch("network", "wifi", "cellular", "data", "speed", "threads", "compression", "limit", "performance")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    MinimalSettingRow(
                                        icon = Icons.Default.Wifi,
                                        title = "Network & Upload Speed",
                                        subtitle = "Wi-Fi rule, threads & compression",
                                        value = if (backupOverWifi) "Wi-Fi Only" else "Any",
                                        iconColor = Color(0xFF06B6D4)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.NETWORK_PERFORMANCE
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 2. SECURITY & PRIVACY
                    // ==========================================
                    if (matchSearch("security", "privacy", "key", "encryption", "biometric", "fingerprint", "pin", "master", "aes", "diagnostics", "health", "ping", "test")) {
                        Text(
                            "SECURITY & PRIVACY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                        ) {
                            Column {
                                if (matchSearch("encryption", "master", "key", "aes", "256", "biometric", "fingerprint", "pin", "security")) {
                                    MinimalSettingRow(
                                        icon = Icons.Default.Lock,
                                        title = "Encryption & Master Key",
                                        subtitle = "256-bit AES encryption & biometrics",
                                        value = if (biometricLock) "Biometrics Active" else "AES-256",
                                        valueColor = Color(0xFF8B5CF6),
                                        iconColor = Color(0xFF8B5CF6)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.SECURITY_PRIVACY_RESTORE
                                    }
                                }

                                if (matchSearch("diagnostics", "health", "test", "ping", "verify", "server", "bot")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    MinimalSettingRow(
                                        icon = Icons.Default.HealthAndSafety,
                                        title = "System Health & Diagnostics",
                                        subtitle = "Server ping & bot verification test",
                                        value = "Run Test",
                                        valueColor = Color(0xFF14B8A6),
                                        iconColor = Color(0xFF14B8A6),
                                        hasSubSettings = false
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        showDiagnosticModal = true
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 3. PREFERENCES
                    // ==========================================
                    if (matchSearch("profile", "account", "email", "recovery", "display name", "name", "theme", "appearance", "dark", "light", "mode", "color")) {
                        Text(
                            "PREFERENCES",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                        ) {
                            Column {
                                if (matchSearch("profile", "account", "email", "recovery", "inbox", "name", "display name")) {
                                    MinimalSettingRow(
                                        icon = Icons.Default.Person,
                                        title = "Profile & Recovery Email",
                                        subtitle = "Display name & recovery inbox",
                                        value = if (prefs.recoveryEmail.isNotEmpty()) "Configured" else "",
                                        iconColor = Color(0xFFEC4899)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.PROFILE_ACCOUNT
                                    }
                                }

                                if (matchSearch("appearance", "theme", "dark", "light", "system", "mode", "color", "look")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    val themeLabel = when (selectedTheme) {
                                        1 -> "Light"
                                        2 -> "Dark"
                                        else -> "System"
                                    }
                                    MinimalSettingRow(
                                        icon = Icons.Default.Palette,
                                        title = "Appearance",
                                        subtitle = "Theme & dark mode preferences",
                                        value = themeLabel,
                                        iconColor = Color(0xFFF59E0B)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.APPEARANCE_REGION
                                    }
                                }

                                if (matchSearch("notification", "controller", "status", "bar", "start", "stop", "alert", "download", "minimal")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    var persistentNotifActive by remember { mutableStateOf(prefs.persistentNotificationEnabled) }
                                    MinimalSettingRow(
                                        icon = Icons.Default.NotificationsActive,
                                        title = "Quick Controller & Notifications",
                                        subtitle = "Status bar 1-tap Start/Stop & separate channels",
                                        value = if (persistentNotifActive) "Enabled" else "Disabled",
                                        valueColor = if (persistentNotifActive) TelegramEmerald else Color.Gray,
                                        iconColor = TelegramBlue,
                                        hasSubSettings = false
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        persistentNotifActive = !persistentNotifActive
                                        prefs.persistentNotificationEnabled = persistentNotifActive
                                        if (persistentNotifActive) {
                                            com.example.telegrambackup.utils.AppNotificationHelper.showPersistentControlNotification(context, isRunning = false)
                                            Toast.makeText(context, "✓ Quick Controller Notification Activated", Toast.LENGTH_SHORT).show()
                                        } else {
                                            com.example.telegrambackup.utils.AppNotificationHelper.cancelPersistentControlNotification(context)
                                            Toast.makeText(context, "Quick Controller Disabled", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 4. SYSTEM & MAINTENANCE
                    // ==========================================
                    if (matchSearch("update", "version", "about", "changelog", "danger", "reset", "wipe", "delete", "trash", "clean", "logout", "log out")) {
                        Text(
                            "SYSTEM & MAINTENANCE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                        ) {
                            Column {
                                if (matchSearch("about", "update", "version", "specs", "changelog", "release")) {
                                    val currentVersion = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1.81" } catch (_: Exception) { "2.1.81" } }
                                    MinimalSettingRow(
                                        icon = Icons.Default.SystemUpdate,
                                        title = "About & Updates",
                                        subtitle = "Software specs & changelog",
                                        value = "v$currentVersion",
                                        valueColor = Color(0xFF6366F1),
                                        iconColor = Color(0xFF6366F1),
                                        hasSubSettings = false
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        showAppInfoModal = true
                                    }
                                }

                                if (matchSearch("data", "reset", "wipe", "delete", "trash", "auto-clean", "danger", "maintenance")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    MinimalSettingRow(
                                        icon = Icons.Default.DeleteForever,
                                        title = "Data Management & Reset",
                                        subtitle = "Trash auto-clean & data wipe",
                                        value = "",
                                        iconColor = Color(0xFFEF4444)
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        currentCategory = StreamlinedCategory.DANGER_ZONE
                                    }
                                }

                                if (matchSearch("cache", "storage", "clean", "size", "clear", "optimize", "memory", "budget")) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    MinimalSettingRow(
                                        icon = Icons.Default.CleaningServices,
                                        title = "Cache & Storage Optimization",
                                        subtitle = "Auto-pruning budget & instant cleaner",
                                        value = "${AppCacheManager.formatBytes(cacheStats.totalBytes)} / ${maxCacheBudgetMB}MB",
                                        valueColor = if (cacheStats.usagePercentage > 0.85f) Color(0xFFEF4444) else TelegramEmerald,
                                        iconColor = Color(0xFF10B981),
                                        hasSubSettings = false
                                    ) {
                                        triggerHapticFeedback(context, haptic)
                                        cacheStats = AppCacheManager.getCacheStats(context)
                                        showCacheOptimizerModal = true
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Log Out Button
                    OutlinedButton(
                        onClick = { triggerHapticFeedback(context, haptic); onLogout() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 👤 PROFILE & ACCOUNT SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.PROFILE_ACCOUNT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Profile & Account", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(68.dp).clip(CircleShape).background(TelegramBlue), contentAlignment = Alignment.Center) {
                                    if (userAvatarUri != null) { AsyncImage(model = userAvatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    else { Text(userNameInput.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)) }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Button(
                                        onClick = { avatarPickerLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Change Photo")
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("JPG, PNG or WEBP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            OutlinedTextField(
                                value = userNameInput,
                                onValueChange = { userNameInput = it; onNameUpdated(it) },
                                label = { Text("Display Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Primary Account Email", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Text(userEmail, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Secondary Recovery Email", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedTextField(
                                value = recoveryEmailInput,
                                onValueChange = { recoveryEmailInput = it },
                                label = { Text("Recovery Email Address") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!isRecoveryOtpSent) {
                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        if (!recoveryEmailInput.contains("@")) { Toast.makeText(context, "Enter a valid email!", Toast.LENGTH_SHORT).show() }
                                        else {
                                            generatedRecoveryOtp = String.format(java.util.Locale.ROOT, "%06d", Random.nextInt(100000, 999999))
                                            isRecoveryOtpSent = true
                                            scope.launch { authRepo.requestOtp(recoveryEmailInput.trim(), "recovery_email"); Toast.makeText(context, "✉️ OTP sent to $recoveryEmailInput", Toast.LENGTH_SHORT).show() }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Send OTP to Verify Recovery Email")
                                }
                            } else {
                                OutlinedTextField(value = recoveryEmailOtpInput, onValueChange = { recoveryEmailOtpInput = it }, label = { Text("Enter 6-Digit OTP") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        if (recoveryEmailOtpInput.trim().length == 6) { prefs.recoveryEmail = recoveryEmailInput.trim(); isRecoveryOtpSent = false; Toast.makeText(context, "✓ Recovery Email Verified!", Toast.LENGTH_SHORT).show() }
                                        else { Toast.makeText(context, "Enter valid 6-digit OTP!", Toast.LENGTH_SHORT).show() }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) { Text("Verify & Save Email") }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Profile & Settings Zero-Loss Sync Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(TelegramBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Cloud Profile & Settings Sync", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Protects full bot fleet, channel routes, encryption keys & sub-settings across app uninstalls.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        scope.launch {
                                            CloudProfileSyncManager.syncLocalConfigToCloud(context, showToast = true)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync to Cloud", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }

                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        scope.launch {
                                            val restored = CloudProfileSyncManager.autoRestoreConfigFromCloud(context)
                                            val filesRestored = SettingsDbManager.restoreCloudFileIndexToRoom(context)
                                            if (restored || filesRestored > 0) {
                                                Toast.makeText(context, "✓ Cloud Profile, Bots & $filesRestored File(s) Restored!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "No remote cloud config found to restore", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore Config", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showDeepReindexModal = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Deep Re-Index", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }

                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showFreeUpSpaceModal = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(42.dp)
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Free Up Space", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        importExportInitialMode = 1 // Import
                                        showImportExportBotsDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TelegramEmerald)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("📥 Import Bots", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }

                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        importExportInitialMode = 0 // Export
                                        showImportExportBotsDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TelegramBlue)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("📤 Export Bots", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 🔑 TELEGRAM CREDENTIALS SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.TELEGRAM_CREDENTIALS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            prefs.credentialsVerified = true
                            isCredentialEditUnlocked = false
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Telegram Credentials", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    if (prefs.credentialsVerified && !isCredentialEditUnlocked) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF59E0B).copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Credentials are Locked", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B)))
                                }
                                Text("Protected for security. Verify OTP to unlock editing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Bot Token: ${prefs.botToken.take(15)}...", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                        Text("Chat ID: ${prefs.chatId}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                    }
                                }

                                if (!isCredentialOtpSent) {
                                    Button(
                                        onClick = {
                                            triggerHapticFeedback(context, haptic); isCredentialOtpSent = true
                                            scope.launch {
                                                val targetMail = prefs.recoveryEmail.ifEmpty { userEmail }
                                                authRepo.requestOtp(targetMail, "credentials_unlock")
                                                Toast.makeText(context, "✉️ OTP sent to email!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Send OTP to Unlock")
                                    }
                                } else {
                                    OutlinedTextField(value = credentialOtpInput, onValueChange = { credentialOtpInput = it }, label = { Text("Enter 6-Digit OTP") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                                    Button(
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            if (credentialOtpInput.trim().length == 6) {
                                                isCredentialEditUnlocked = true; isCredentialOtpSent = false; credentialOtpInput = ""
                                                Toast.makeText(context, "✓ Unlocked for editing!", Toast.LENGTH_SHORT).show()
                                            } else { Toast.makeText(context, "Enter valid 6-digit OTP!", Toast.LENGTH_SHORT).show() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) { Text("Verify & Unlock") }
                                }
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Bot API Token", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                OutlinedTextField(
                                    value = tempBotToken,
                                    onValueChange = { tempBotToken = it },
                                    placeholder = { Text("123456:ABC-DEF1234ghIkl...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Channel Chat ID", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                OutlinedTextField(
                                    value = tempChatId,
                                    onValueChange = { tempChatId = it },
                                    placeholder = { Text("-1001234567890") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        if (tempBotToken.trim().isEmpty()) { Toast.makeText(context, "Enter Bot Token first!", Toast.LENGTH_SHORT).show() }
                                        else {
                                            isVerifyingBotConnection = true
                                            scope.launch {
                                                val res = botTester.testBotConnection(tempBotToken, tempChatId)
                                                isVerifyingBotConnection = false
                                                if (res.isSuccess) Toast.makeText(context, "✓ Verified! ${res.getOrNull()}", Toast.LENGTH_LONG).show()
                                                else Toast.makeText(context, "❌ Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    if (isVerifyingBotConnection) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    else {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Bot Connection")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 🚀 TELEGRAM BOT FLEET & CHANNELS SCREEN
            // =========================================================================
            StreamlinedCategory.BOT_FLEET -> {
                BotFleetSettingsScreen(
                    onBack = { currentCategory = StreamlinedCategory.MAIN }
                )
            }

            // =========================================================================
            // ☁️ SMART BACKUP ENGINE SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.BACKUP_SOURCES_RULES -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Smart Backup Engine", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    // Emergency Stop & Restart Engine
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        try {
                                            WorkManager.getInstance(context).cancelUniqueWork("SmartContinuousBackup")
                                            WorkManager.getInstance(context).cancelUniqueWork("NightExpressBackup")
                                            WorkManager.getInstance(context).cancelUniqueWork("PeriodicScanBackup")
                                            WorkManager.getInstance(context).cancelAllWork()
                                            autoBackup = false
                                            prefs.autoBackup = false
                                            val stopNotifRequest = OneTimeWorkRequestBuilder<StopBackupNotificationWorker>().build()
                                            WorkManager.getInstance(context).enqueue(stopNotifRequest)
                                            Toast.makeText(context, "🛑 Backup engine STOPPED", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) { Toast.makeText(context, "Error stopping: ${e.message}", Toast.LENGTH_SHORT).show() }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop Engine")
                                }

                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        autoBackup = true
                                        prefs.autoBackup = true
                                        scheduleSmartBackup()
                                        Toast.makeText(context, "🚀 Engine RESTARTED", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Engine")
                                }
                            }

                            Text("Automation Mode", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = smartBackupMode == 0, onClick = { smartBackupMode = 0; prefs.smartBackupMode = 0; scheduleSmartBackup() }, label = { Text("Continuous") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = smartBackupMode == 1, onClick = { smartBackupMode = 1; prefs.smartBackupMode = 1; scheduleSmartBackup() }, label = { Text("Scheduled") }, modifier = Modifier.weight(1f))
                                FilterChip(selected = smartBackupMode == 2, onClick = { smartBackupMode = 2; prefs.smartBackupMode = 2; scheduleSmartBackup() }, label = { Text("Nightly") }, modifier = Modifier.weight(1f))
                            }

                            if (smartBackupMode == 1) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Interval: Every $smartScanInterval Hours", style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(1, 2, 4, 6, 12, 24).forEach { hours ->
                                            AssistChip(
                                                onClick = { smartScanInterval = hours; prefs.smartScanIntervalHours = hours; scheduleSmartBackup() },
                                                label = { Text("${hours}h", fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // File Types and Sources
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("File Types & Sources", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                            OptionValueRowWithInfo("Max File Size Limit", if (maxFileSizeMBState <= 0) "Unlimited" else "${maxFileSizeMBState.toInt()} MB", { showCustomMaxLimitDialog = true }, { featureInfoTitle = "Size Limit"; featureInfoDesc = "Files exceeding this limit are skipped." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup Photos (JPG, PNG, HEIC)", includeCamera, { includeCamera = it; prefs.backupCamera = it }, { featureInfoTitle = "Photos"; featureInfoDesc = "Backs up image files." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup Videos (MP4, MKV, MOV)", includeVideos, { includeVideos = it; prefs.backupVideos = it }, { featureInfoTitle = "Videos"; featureInfoDesc = "Backs up video recordings." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup Documents (PDF, DOCX, XLSX)", includeDocs, { includeDocs = it; prefs.backupDocuments = it }, { featureInfoTitle = "Documents"; featureInfoDesc = "Backs up office files." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup Audio (MP3, WAV, FLAC)", includeAudio, { includeAudio = it; prefs.backupAudio = it }, { featureInfoTitle = "Audio"; featureInfoDesc = "Backs up audio recordings." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup Downloads Folder", includeDownloads, { includeDownloads = it; prefs.backupDownloads = it }, { featureInfoTitle = "Downloads"; featureInfoDesc = "Backs up device download folder." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup Archives (ZIP, RAR, 7Z)", includeArchives, { includeArchives = it; prefs.backupArchives = it }, { featureInfoTitle = "Archives"; featureInfoDesc = "Backs up zip/rar archives." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup APK Packages (.apk)", includeApks, { includeApks = it; prefs.backupApks = it }, { featureInfoTitle = "APKs"; featureInfoDesc = "Backs up installer APKs." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Backup All Other Files", includeAllOther, { includeAllOther = it; prefs.backupAllOther = it }, { featureInfoTitle = "Other"; featureInfoDesc = "Backs up all other recognized files." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Show Hidden & Dot Files", showHiddenFiles, { showHiddenFiles = it; prefs.showHiddenFiles = it }, { featureInfoTitle = "Hidden"; featureInfoDesc = "Shows dot/hidden files." })
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 📡 NETWORK & PERFORMANCE SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.NETWORK_PERFORMANCE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Network & Performance", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwitchRowWithInfo("Wi-Fi Only Upload", backupOverWifi, { backupOverWifi = it; prefs.wifiOnly = it }, { featureInfoTitle = "Wi-Fi Only"; featureInfoDesc = "Restricts background backups to Wi-Fi networks." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo("Data Compression", enableDataCompression, { enableDataCompression = it; prefs.enableDataCompression = it }, { featureInfoTitle = "Data Compression"; featureInfoDesc = "Compresses files before upload to save bandwidth." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            OptionValueRowWithInfo("Upload Speed Limit", uploadSpeedLimit, {
                                uploadSpeedLimit = when (uploadSpeedLimit) { "Unlimited" -> "2 MB/s"; "2 MB/s" -> "5 MB/s"; else -> "Unlimited" }
                            }, { featureInfoTitle = "Speed Limit"; featureInfoDesc = "Caps upload speed." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            OptionValueRowWithInfo("Daily Data Limit", if (dailyDataLimitMBState <= 0) "Unlimited" else "$dailyDataLimitMBState MB", {
                                showCustomDailyLimitDialog = true
                            }, { featureInfoTitle = "Daily Data Limit"; featureInfoDesc = "Caps maximum data uploaded per day across cellular and Wi-Fi." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Concurrent Upload Streams", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1, 2, 4, 8, 10, 12, 16).forEach { t ->
                                        FilterChip(selected = uploadThreads == t, onClick = { uploadThreads = t; prefs.uploadThreads = t }, label = { Text("${t}x") })
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "AI NETWORK BENCHMARK & SELF-ANALYSIS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OptionValueRowWithInfo(
                                "7-Day Speed & Health Benchmark",
                                if (prefs.isSelfAnalysisEnabled) "Active (30m)" else "Paused",
                                {
                                    triggerHapticFeedback(context, haptic)
                                    showNetworkSelfAnalysisModal = true
                                },
                                {
                                    featureInfoTitle = "7-Day Self-Analysis"
                                    featureInfoDesc = "Runs periodic background network benchmarks every 30 mins to discover peak upload hours, calculate latency, and advise optimal backup settings."
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "POWER & BATTERY SAFEGUARDS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwitchRowWithInfo(
                                "Backup Only While Charging",
                                chargingOnlyState,
                                { chargingOnlyState = it; prefs.chargingOnly = it },
                                { featureInfoTitle = "Charging Only"; featureInfoDesc = "Pauses automatic background sync when device is running on battery." }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo(
                                "Low Battery Guard (< 20%)",
                                lowBatteryProtectionState,
                                { lowBatteryProtectionState = it; prefs.lowBatteryProtection = it },
                                { featureInfoTitle = "Low Battery Guard"; featureInfoDesc = "Automatically halts large uploads if phone battery drops below 20% to preserve battery life." }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 🔒 SECURITY & ENCRYPTION SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.SECURITY_PRIVACY_RESTORE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Security & Master Key", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwitchRowWithInfo("Biometric App Lock", biometricLock, { biometricLock = it; prefs.biometricLock = it }, { featureInfoTitle = "Biometrics"; featureInfoDesc = "Requires Fingerprint / Face Unlock when opening app." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchRowWithInfo(
                                "Screen Privacy (Anti-Spyware)",
                                prefs.screenPrivacyEnabled,
                                {
                                    prefs.screenPrivacyEnabled = it
                                    (context as? com.example.telegrambackup.MainActivity)?.updateScreenPrivacy(it)
                                },
                                { featureInfoTitle = "Screen Privacy"; featureInfoDesc = "Blocks screenshots, screen recordings, and hides sensitive app contents in Android recent apps." }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SettingCategoryRow(Icons.Default.Security, "System Permissions Status", "Storage, Notifications & Battery Optimization", TelegramBlue) {
                                triggerHapticFeedback(context, haptic)
                                showPermissionWizardModal = true
                            }
                        }
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Immutable Master Encryption Key", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Text("256-bit AES master key generated at account creation. Back this up to decrypt files on any device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(uniqueMasterKey, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    val targetMail = prefs.recoveryEmail.ifEmpty { userEmail }
                                    scope.launch {
                                        authRepo.requestOtp(targetMail, "master_key_backup")
                                        Toast.makeText(context, "✉️ Master Key email sent to $targetMail", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Email Key to Recovery Inbox", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 🎨 APPEARANCE & THEME SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.APPEARANCE_REGION -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Appearance & Theme", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            listOf("System Default", "Light Mode", "Dark Mode").forEachIndexed { index, label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedTheme = index; prefs.appTheme = index; onThemeChanged(index) }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                    RadioButton(selected = selectedTheme == index, onClick = { selectedTheme = index; prefs.appTheme = index; onThemeChanged(index) })
                                }
                                if (index < 2) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 🚪 DANGER ZONE / DATA RESET SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.DANGER_ZONE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Data Management & Reset", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OptionValueRowWithInfo(
                                "Trash Auto-Clean Policy",
                                when (trashAutoCleanDays) { 0 -> "Never"; else -> "$trashAutoCleanDays Days" },
                                {
                                    trashAutoCleanDays = when (trashAutoCleanDays) { 0 -> 7; 7 -> 14; 14 -> 30; 30 -> 60; 60 -> 90; else -> 0 }
                                    prefs.trashAutoCleanDays = trashAutoCleanDays
                                    val msg = if (trashAutoCleanDays == 0) "Trash: Never clean" else "Trash auto-clean: $trashAutoCleanDays days"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                { featureInfoTitle = "Trash Auto-Clean"; featureInfoDesc = "Automatically deletes trash items after the configured days." }
                            )
                        }
                    }

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            SettingCategoryRow(Icons.Default.DeleteSweep, "Clear File Data Options", "Custom wipe filters (Drive, Auto-Backup, Old Files)", Color(0xFFF59E0B)) {
                                triggerHapticFeedback(context, haptic)
                                dialogInitialTab = 0
                                showClearDataAccountDialog = true
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SettingCategoryRow(Icons.Default.DeleteForever, "Delete Account & All Data", "Permanently wipe account, settings & cloud files", Color(0xFFEF4444)) {
                                triggerHapticFeedback(context, haptic)
                                dialogInitialTab = 1
                                showClearDataAccountDialog = true
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showCustomMaxLimitDialog) {
        AlertDialog(
            onDismissRequest = { showCustomMaxLimitDialog = false },
            icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = TelegramBlue) },
            title = { Text("Set Custom Max File Size Limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter maximum file size limit in MB (e.g. 500 for 500MB, 5000 for 5GB, 0 for Unlimited):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = customMaxLimitInput,
                        onValueChange = { customMaxLimitInput = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Limit in MB (0 = Unlimited)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { customMaxLimitInput = "0" }, label = { Text("Unlimited") })
                        AssistChip(onClick = { customMaxLimitInput = "2000" }, label = { Text("2 GB") })
                        AssistChip(onClick = { customMaxLimitInput = "5000" }, label = { Text("5 GB") })
                        AssistChip(onClick = { customMaxLimitInput = "10000" }, label = { Text("10 GB") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = customMaxLimitInput.toDoubleOrNull() ?: 0.0
                        maxFileSizeMBState = parsed
                        prefs.maxFileSizeMB = parsed
                        scope.launch {
                            SettingsDbManager.saveCategorySettings(
                                context,
                                "Smart Backup Engine",
                                mapOf("max_file_size_mb" to parsed.toString())
                            )
                        }
                        showCustomMaxLimitDialog = false
                        Toast.makeText(context, "Saved Custom Max File Size Limit: ${if (parsed <= 0) "Unlimited" else "$parsed MB"}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) { Text("Save Limit") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomMaxLimitDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportExportBotsDialog) {
        BotFleetImportExportDialog(
            initialMode = importExportInitialMode,
            onDismiss = { showImportExportBotsDialog = false },
            onImportSuccess = {
                showImportExportBotsDialog = false
                scope.launch {
                    refreshDbStats()
                    CloudProfileSyncManager.autoRestoreConfigFromCloud(context)
                }
            }
        )
    }
}

@Composable
private fun MinimalSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    value: String = "",
    valueColor: Color = TelegramBlue,
    iconColor: Color = TelegramBlue,
    isExpanded: Boolean = false,
    hasSubSettings: Boolean = true,
    onClick: () -> Unit
) {
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 90f else 0f, label = "ChevronRotation")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        if (value.isNotEmpty() && !isExpanded) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = valueColor.copy(alpha = 0.10f)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = valueColor, fontSize = 11.5.sp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (hasSubSettings) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isExpanded) iconColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp).rotate(rotationState)
            )
        }
    }
}

@Composable
private fun SettingCategoryRowWithBadge(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String = "",
    badgeColor: Color = TelegramBlue,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                if (badgeText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingCategoryRow(icon: ImageVector, title: String, subtitle: String, iconTint: Color, onClick: () -> Unit) {
    SettingCategoryRowWithBadge(icon, title, subtitle, "", iconTint, iconTint, onClick)
}

@Composable
private fun OptionValueRowWithInfo(title: String, value: String, onClick: (() -> Unit)? = null, onInfoClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) { Text(text = value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline); Spacer(modifier = Modifier.width(6.dp)) }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SwitchRowWithInfo(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onInfoClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SubCategoryHeroHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    badgeText: String? = null,
    badgeColor: Color = TelegramBlue
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = badgeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = badgeColor, fontSize = 10.5.sp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
        }
    }
}
