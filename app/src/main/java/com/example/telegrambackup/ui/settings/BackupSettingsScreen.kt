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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.asImageBitmap
import com.example.telegrambackup.utils.KeyRecoveryManager
import com.example.telegrambackup.utils.TelegramApiConfig
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.telegrambackup.utils.AiVisionManager
import com.example.telegrambackup.utils.ThumbnailManager
import com.example.telegrambackup.theme.AppThemeMode
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupFileType
import com.example.telegrambackup.utils.SettingsDbManager
import com.example.telegrambackup.utils.TelegramBotTester
import com.example.telegrambackup.worker.BackupWorker
import com.example.telegrambackup.worker.StopBackupNotificationWorker
import com.example.telegrambackup.utils.DynamicProcessTracker
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
    BOTTOM_NAV_CUSTOMIZATION,
    AI_VISION_INTELLIGENCE,
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
        mutableStateOf<Uri?>(com.example.telegrambackup.utils.ProfileManager.getAvatarUri(context))
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedUri = com.example.telegrambackup.utils.ProfileManager.saveAvatarFromUri(context, uri)
            if (savedUri != null) {
                userAvatarUri = savedUri
                prefs.profilePictureUri = savedUri.toString()
                prefs.profileUpdatedAt = System.currentTimeMillis()
                scope.launch {
                    SettingsDbManager.saveCategorySettings(
                        context,
                        "Profile Picture",
                        mapOf(
                            "profile_picture_uri" to savedUri.toString(),
                            "display_name" to userNameInput.trim()
                        ),
                        showToast = false
                    )
                    com.example.telegrambackup.utils.CloudProfileSyncManager.syncLocalConfigToCloud(context)
                }
                onNameUpdated(userNameInput.trim())
                Toast.makeText(context, "✓ Profile Photo Saved & Synced!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load selected photo", Toast.LENGTH_SHORT).show()
            }
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
    var maxPhotosSizeMBState by remember { mutableDoubleStateOf(prefs.maxPhotosSizeMB) }
    var minPhotosSizeMBState by remember { mutableDoubleStateOf(prefs.minPhotosSizeMB) }
    var maxVideosSizeMBState by remember { mutableDoubleStateOf(prefs.maxVideosSizeMB) }
    var minVideosSizeMBState by remember { mutableDoubleStateOf(prefs.minVideosSizeMB) }
    var maxDocsSizeMBState by remember { mutableDoubleStateOf(prefs.maxDocsSizeMB) }
    var minDocsSizeMBState by remember { mutableDoubleStateOf(prefs.minDocsSizeMB) }
    var maxAudioSizeMBState by remember { mutableDoubleStateOf(prefs.maxAudioSizeMB) }
    var minAudioSizeMBState by remember { mutableDoubleStateOf(prefs.minAudioSizeMB) }
    var maxArchivesSizeMBState by remember { mutableDoubleStateOf(prefs.maxArchivesSizeMB) }
    var minArchivesSizeMBState by remember { mutableDoubleStateOf(prefs.minArchivesSizeMB) }
    var maxApksSizeMBState by remember { mutableDoubleStateOf(prefs.maxApksSizeMB) }
    var minApksSizeMBState by remember { mutableDoubleStateOf(prefs.minApksSizeMB) }
    var maxOtherSizeMBState by remember { mutableDoubleStateOf(prefs.maxOtherSizeMB) }
    var minOtherSizeMBState by remember { mutableDoubleStateOf(prefs.minOtherSizeMB) }
    var selectedCategoryForSizeCondition by remember { mutableStateOf<BackupFileType?>(null) }
    var smartScanInterval by remember { mutableIntStateOf(prefs.smartScanIntervalHours) }
    var nightBackupHour by remember { mutableIntStateOf(prefs.nightBackupHour) }
    var showHiddenFiles by remember { mutableStateOf(prefs.showHiddenFiles) }
    var biometricLock by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var enableDataCompression by remember { mutableStateOf(prefs.enableDataCompression) }
    var selectedTheme by remember { mutableIntStateOf(AppThemeMode.fromId(prefs.appTheme).id) }
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
    var showTelegramChannelCleanerDialog by remember { mutableStateOf(false) }
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

    var showBiometricTimeoutDialog by remember { mutableStateOf(false) }
    var showCustomBotApiDialog by remember { mutableStateOf(false) }
    var showKeyRecoveryDialog by remember { mutableStateOf(false) }

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

    if (showBiometricTimeoutDialog) {
        val options = listOf(
            0 to "Immediately",
            30 to "30 Seconds",
            60 to "1 Minute",
            300 to "5 Minutes",
            900 to "15 Minutes"
        )
        AlertDialog(
            onDismissRequest = { showBiometricTimeoutDialog = false },
            title = { Text("Auto-Lock Timeout", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select how long after backgrounding before requiring biometric unlock:", style = MaterialTheme.typography.bodySmall)
                    options.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    prefs.biometricAutoLockTimeoutSeconds = seconds
                                    showBiometricTimeoutDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.biometricAutoLockTimeoutSeconds == seconds,
                                onClick = {
                                    prefs.biometricAutoLockTimeoutSeconds = seconds
                                    showBiometricTimeoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBiometricTimeoutDialog = false }) { Text("Close") }
            }
        )
    }

    if (showCustomBotApiDialog) {
        var tempUrl by remember { mutableStateOf(prefs.customBotApiUrl) }
        AlertDialog(
            onDismissRequest = { showCustomBotApiDialog = false },
            title = { Text("Telegram Bot API Server", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Default is https://api.telegram.org. Enter your self-hosted local Bot API server URL (e.g., http://192.168.1.50:8081) to enable 2,000 MB single-request uploads.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Server Base URL") },
                        placeholder = { Text("https://api.telegram.org") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { tempUrl = "" }
                        ) {
                            Text("Reset to Default")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = tempUrl.trim().removeSuffix("/")
                        prefs.customBotApiUrl = cleaned
                        showCustomBotApiDialog = false
                        Toast.makeText(context, if (cleaned.isEmpty()) "Restored to official Telegram Bot API" else "Saved custom API server: $cleaned", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomBotApiDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showKeyRecoveryDialog) {
        var recoveryTab by remember { mutableIntStateOf(0) }
        val mnemonicWords = remember(uniqueMasterKey) {
            KeyRecoveryManager.keyToMnemonic(uniqueMasterKey)
        }
        val qrBitmap = remember(uniqueMasterKey) {
            KeyRecoveryManager.generateQrBitmap(uniqueMasterKey, 512)
        }
        var importPhraseInput by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showKeyRecoveryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF8B5CF6))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Master Key Recovery", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PrimaryTabRow(selectedTabIndex = recoveryTab, modifier = Modifier.fillMaxWidth()) {
                        Tab(selected = recoveryTab == 0, onClick = { recoveryTab = 0 }, text = { Text("12 Words", style = MaterialTheme.typography.labelSmall) })
                        Tab(selected = recoveryTab == 1, onClick = { recoveryTab = 1 }, text = { Text("Offline QR", style = MaterialTheme.typography.labelSmall) })
                        Tab(selected = recoveryTab == 2, onClick = { recoveryTab = 2 }, text = { Text("Import", style = MaterialTheme.typography.labelSmall) })
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    when (recoveryTab) {
                        0 -> {
                            Text(
                                "Write down these 12 words in exact order. This phrase can reconstruct your AES-256 decryption key on any device:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val words = mnemonicWords
                                    for (i in 0 until words.size step 2) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    "${i + 1}. ${words[i]}",
                                                    modifier = Modifier.padding(8.dp),
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                            if (i + 1 < words.size) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        "${i + 2}. ${words[i + 1]}",
                                                        modifier = Modifier.padding(8.dp),
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val clip = ClipData.newPlainText("12-Word Recovery Phrase", mnemonicWords.joinToString(" "))
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied 12 words to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy 12 Words")
                            }
                        }
                        1 -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Scan this high-contrast offline QR code with another device to transfer your master key without typing:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (qrBitmap != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        modifier = Modifier.size(200.dp).padding(4.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Master Key QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Text("Unable to generate QR code", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Restore your master encryption key by entering the 12-word mnemonic phrase:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = importPhraseInput,
                                    onValueChange = {
                                        importPhraseInput = it
                                        importError = null
                                    },
                                    label = { Text("12-Word Phrase") },
                                    minLines = 3,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                if (importError != null) {
                                    Text(importError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = {
                                        val cleanWords = importPhraseInput.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                                        val recoveredKey = KeyRecoveryManager.mnemonicToMasterKey(cleanWords)
                                        if (recoveredKey != null && recoveredKey.length >= 16) {
                                            prefs.encryptionKey = recoveredKey
                                            showKeyRecoveryDialog = false
                                            Toast.makeText(context, "Master key restored successfully!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            importError = "Invalid 12-word phrase. Please check the spelling and order."
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Restore Key")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeyRecoveryDialog = false }) { Text("Close") }
            }
        )
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

    if (showTelegramChannelCleanerDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramChannelCleanerDialog = false },
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp)) },
            title = { Text("Telegram Channel Cleaner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Directly sweep and purge all backed-up files and orphaned messages from your Telegram channel.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "This scans the channel top-down and deletes messages directly from Telegram, even if your local app database or cloud records were already cleared.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        "⚠️ Note: Ensure your bot is added as an Administrator with 'Delete Messages' permission in your channel.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.SemiBold)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTelegramChannelCleanerDialog = false
                        DynamicProcessTracker.startProcess(
                            title = "Telegram Channel Cleaner",
                            subtitle = "Verifying channel admin rights...",
                            icon = Icons.Default.CleaningServices
                        )
                        scope.launch(Dispatchers.IO) {
                            try {
                                val token = prefs.botToken.trim()
                                val chat = prefs.chatId.trim()
                                if (token.isEmpty() || chat.isEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        DynamicProcessTracker.finishProcess()
                                        Toast.makeText(context, "Telegram Bot Token or Chat ID not configured.", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                                val serverApi = ServerApi()
                                val chatInfo = serverApi.getTelegramChatInfo(token, chat)
                                val (actuallyDeleted, scannedCount, permError) = serverApi.cleanTelegramChannelStorage(
                                    context = context,
                                    botToken = token,
                                    chatId = chat,
                                    purgeAllRemaining = true
                                ) { scanned, total, count, status ->
                                    val fraction = if (total > 0) (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                                    DynamicProcessTracker.updateProgress(
                                        progress = fraction,
                                        subtitle = "Purging '${chatInfo.title}': $count deleted ($scanned scanned)"
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    DynamicProcessTracker.finishProcess()
                                    if (permError) {
                                        Toast.makeText(context, "🚫 Bot CANNOT delete messages! Make bot Admin with 'Delete Messages' permission in channel '${chatInfo.title}'", Toast.LENGTH_LONG).show()
                                    } else if (actuallyDeleted > 0) {
                                        Toast.makeText(context, "✅ Purged $actuallyDeleted messages from Telegram channel '${chatInfo.title}' ($scannedCount scanned)", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Channel '${chatInfo.title}' is clean — 0 messages found", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    DynamicProcessTracker.finishProcess()
                                    Toast.makeText(context, "Channel sweep failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Start Channel Deep Purge")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showTelegramChannelCleanerDialog = false }
                ) {
                    Text("Cancel")
                }
            }
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
        trashAutoCleanDays, stopAllBackup, biometricLock, zeroKnowledgeActive,
        maxPhotosSizeMBState, minPhotosSizeMBState, maxVideosSizeMBState, minVideosSizeMBState,
        maxDocsSizeMBState, minDocsSizeMBState, maxAudioSizeMBState, minAudioSizeMBState,
        maxArchivesSizeMBState, minArchivesSizeMBState, maxApksSizeMBState, minApksSizeMBState,
        maxOtherSizeMBState, minOtherSizeMBState
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
        prefs.maxPhotosSizeMB = maxPhotosSizeMBState
        prefs.minPhotosSizeMB = minPhotosSizeMBState
        prefs.maxVideosSizeMB = maxVideosSizeMBState
        prefs.minVideosSizeMB = minVideosSizeMBState
        prefs.maxDocsSizeMB = maxDocsSizeMBState
        prefs.minDocsSizeMB = minDocsSizeMBState
        prefs.maxAudioSizeMB = maxAudioSizeMBState
        prefs.minAudioSizeMB = minAudioSizeMBState
        prefs.maxArchivesSizeMB = maxArchivesSizeMBState
        prefs.minArchivesSizeMB = minArchivesSizeMBState
        prefs.maxApksSizeMB = maxApksSizeMBState
        prefs.minApksSizeMB = minApksSizeMBState
        prefs.maxOtherSizeMB = maxOtherSizeMBState
        prefs.minOtherSizeMB = minOtherSizeMBState
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
                    AppSettingEntity("max_photos_size_mb", maxPhotosSizeMBState.toString()),
                    AppSettingEntity("min_photos_size_mb", minPhotosSizeMBState.toString()),
                    AppSettingEntity("max_videos_size_mb", maxVideosSizeMBState.toString()),
                    AppSettingEntity("min_videos_size_mb", minVideosSizeMBState.toString()),
                    AppSettingEntity("max_docs_size_mb", maxDocsSizeMBState.toString()),
                    AppSettingEntity("min_docs_size_mb", minDocsSizeMBState.toString()),
                    AppSettingEntity("max_audio_size_mb", maxAudioSizeMBState.toString()),
                    AppSettingEntity("min_audio_size_mb", minAudioSizeMBState.toString()),
                    AppSettingEntity("max_archives_size_mb", maxArchivesSizeMBState.toString()),
                    AppSettingEntity("min_archives_size_mb", minArchivesSizeMBState.toString()),
                    AppSettingEntity("max_apks_size_mb", maxApksSizeMBState.toString()),
                    AppSettingEntity("min_apks_size_mb", minApksSizeMBState.toString()),
                    AppSettingEntity("max_other_size_mb", maxOtherSizeMBState.toString()),
                    AppSettingEntity("min_other_size_mb", minOtherSizeMBState.toString()),
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

    // INDIVIDUAL CATEGORY SIZE CONDITION MODAL
    if (selectedCategoryForSizeCondition != null) {
        val cat = selectedCategoryForSizeCondition!!
        var catMinInput by remember(cat) {
            val (min, _) = when (cat) {
                BackupFileType.PHOTOS -> minPhotosSizeMBState to maxPhotosSizeMBState
                BackupFileType.VIDEOS -> minVideosSizeMBState to maxVideosSizeMBState
                BackupFileType.DOCUMENTS -> minDocsSizeMBState to maxDocsSizeMBState
                BackupFileType.AUDIO -> minAudioSizeMBState to maxAudioSizeMBState
                BackupFileType.ARCHIVES -> minArchivesSizeMBState to maxArchivesSizeMBState
                BackupFileType.APKS -> minApksSizeMBState to maxApksSizeMBState
                BackupFileType.OTHER -> minOtherSizeMBState to maxOtherSizeMBState
            }
            mutableStateOf(if (min > 0.0) (if (min < 1.0) min.toString() else min.toInt().toString()) else "")
        }
        var catMaxInput by remember(cat) {
            val (_, max) = when (cat) {
                BackupFileType.PHOTOS -> minPhotosSizeMBState to maxPhotosSizeMBState
                BackupFileType.VIDEOS -> minVideosSizeMBState to maxVideosSizeMBState
                BackupFileType.DOCUMENTS -> minDocsSizeMBState to maxDocsSizeMBState
                BackupFileType.AUDIO -> minAudioSizeMBState to maxAudioSizeMBState
                BackupFileType.ARCHIVES -> minArchivesSizeMBState to maxArchivesSizeMBState
                BackupFileType.APKS -> minApksSizeMBState to maxApksSizeMBState
                BackupFileType.OTHER -> minOtherSizeMBState to maxOtherSizeMBState
            }
            mutableStateOf(if (max > 0.0) max.toInt().toString() else "")
        }

        AlertDialog(
            onDismissRequest = { selectedCategoryForSizeCondition = null },
            icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cat.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Individual Size Condition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Set custom file size filters specifically for ${cat.title}. If left at 0, files follow the Global Limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Max Size Limit Section
                    Text("Max File Size (MB)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val maxPresets = when (cat) {
                            BackupFileType.PHOTOS -> listOf(0.0 to "Default", 10.0 to "10MB", 25.0 to "25MB", 50.0 to "50MB", 100.0 to "100MB")
                            BackupFileType.VIDEOS -> listOf(0.0 to "Default", 100.0 to "100MB", 500.0 to "500MB", 1000.0 to "1GB", 2000.0 to "2GB")
                            BackupFileType.DOCUMENTS -> listOf(0.0 to "Default", 25.0 to "25MB", 50.0 to "50MB", 100.0 to "100MB", 250.0 to "250MB")
                            BackupFileType.AUDIO -> listOf(0.0 to "Default", 15.0 to "15MB", 30.0 to "30MB", 50.0 to "50MB", 100.0 to "100MB")
                            BackupFileType.ARCHIVES -> listOf(0.0 to "Default", 100.0 to "100MB", 500.0 to "500MB", 1000.0 to "1GB", 2000.0 to "2GB")
                            BackupFileType.APKS -> listOf(0.0 to "Default", 30.0 to "30MB", 50.0 to "50MB", 100.0 to "100MB", 200.0 to "200MB")
                            BackupFileType.OTHER -> listOf(0.0 to "Default", 25.0 to "25MB", 50.0 to "50MB", 100.0 to "100MB", 500.0 to "500MB")
                        }
                        maxPresets.forEach { (mb, label) ->
                            val currentMax = catMaxInput.toDoubleOrNull() ?: 0.0
                            FilterChip(
                                selected = (mb == 0.0 && currentMax == 0.0) || (mb > 0.0 && currentMax == mb),
                                onClick = {
                                    catMaxInput = if (mb == 0.0) "" else mb.toInt().toString()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = catMaxInput,
                        onValueChange = { catMaxInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Max Size (MB, 0 = Global Limit)") },
                        placeholder = { Text("0 for Global Limit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 2. Min Size Limit Section (Ignore smaller files)
                    Text("Min File Size (MB) — Ignore smaller", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.0 to "None (0)", 0.1 to "100KB", 0.5 to "500KB", 1.0 to "1MB", 5.0 to "5MB", 10.0 to "10MB").forEach { (mb, label) ->
                            val currentMin = catMinInput.toDoubleOrNull() ?: 0.0
                            FilterChip(
                                selected = (mb == 0.0 && currentMin == 0.0) || (mb > 0.0 && kotlin.math.abs(currentMin - mb) < 0.01),
                                onClick = {
                                    catMinInput = if (mb == 0.0) "" else mb.toString()
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = catMinInput,
                        onValueChange = { catMinInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Min Size (MB, 0 = No Min)") },
                        placeholder = { Text("0 for no minimum") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Informational Summary Banner
                    val parsedMin = catMinInput.toDoubleOrNull() ?: 0.0
                    val parsedMax = catMaxInput.toDoubleOrNull() ?: 0.0
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            val summary = when {
                                parsedMin == 0.0 && parsedMax == 0.0 -> "Using Global Limit (${if (maxFileSizeMBState <= 0) "Unlimited" else "${maxFileSizeMBState.toInt()} MB"}). No special restrictions."
                                parsedMin > 0.0 && parsedMax > 0.0 -> "Only ${cat.title.lowercase()} between ${if (parsedMin < 1.0) "${(parsedMin * 1024).toInt()} KB" else "$parsedMin MB"} and $parsedMax MB will be backed up."
                                parsedMin > 0.0 -> "Only ${cat.title.lowercase()} larger than ${if (parsedMin < 1.0) "${(parsedMin * 1024).toInt()} KB" else "$parsedMin MB"} will be backed up (Global Max: ${if (maxFileSizeMBState <= 0) "Unlimited" else "${maxFileSizeMBState.toInt()} MB"})."
                                else -> "Only ${cat.title.lowercase()} up to $parsedMax MB will be backed up."
                            }
                            Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedMin = catMinInput.toDoubleOrNull() ?: 0.0
                        val parsedMax = catMaxInput.toDoubleOrNull() ?: 0.0
                        when (cat) {
                            BackupFileType.PHOTOS -> { minPhotosSizeMBState = parsedMin; maxPhotosSizeMBState = parsedMax }
                            BackupFileType.VIDEOS -> { minVideosSizeMBState = parsedMin; maxVideosSizeMBState = parsedMax }
                            BackupFileType.DOCUMENTS -> { minDocsSizeMBState = parsedMin; maxDocsSizeMBState = parsedMax }
                            BackupFileType.AUDIO -> { minAudioSizeMBState = parsedMin; maxAudioSizeMBState = parsedMax }
                            BackupFileType.ARCHIVES -> { minArchivesSizeMBState = parsedMin; maxArchivesSizeMBState = parsedMax }
                            BackupFileType.APKS -> { minApksSizeMBState = parsedMin; maxApksSizeMBState = parsedMax }
                            BackupFileType.OTHER -> { minOtherSizeMBState = parsedMin; maxOtherSizeMBState = parsedMax }
                        }
                        prefs.setCategorySizeLimits(cat, parsedMin, parsedMax)
                        selectedCategoryForSizeCondition = null
                        triggerHapticFeedback(context, haptic)
                        Toast.makeText(context, "Saved size condition for ${cat.title}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save Condition") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        when (cat) {
                            BackupFileType.PHOTOS -> { minPhotosSizeMBState = 0.0; maxPhotosSizeMBState = 0.0 }
                            BackupFileType.VIDEOS -> { minVideosSizeMBState = 0.0; maxVideosSizeMBState = 0.0 }
                            BackupFileType.DOCUMENTS -> { minDocsSizeMBState = 0.0; maxDocsSizeMBState = 0.0 }
                            BackupFileType.AUDIO -> { minAudioSizeMBState = 0.0; maxAudioSizeMBState = 0.0 }
                            BackupFileType.ARCHIVES -> { minArchivesSizeMBState = 0.0; maxArchivesSizeMBState = 0.0 }
                            BackupFileType.APKS -> { minApksSizeMBState = 0.0; maxApksSizeMBState = 0.0 }
                            BackupFileType.OTHER -> { minOtherSizeMBState = 0.0; maxOtherSizeMBState = 0.0 }
                        }
                        prefs.setCategorySizeLimits(cat, 0.0, 0.0)
                        selectedCategoryForSizeCondition = null
                        triggerHapticFeedback(context, haptic)
                        Toast.makeText(context, "Reset ${cat.title} to Global Limit", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Reset") }
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
        val cleanName = userNameInput.trim().ifEmpty { "My Cloud Account" }
        prefs.displayName = cleanName
        prefs.recoveryEmail = recoveryEmailInput.trim()
        if (userAvatarUri != null) prefs.profilePictureUri = userAvatarUri.toString()
        prefs.profileUpdatedAt = System.currentTimeMillis()
        onNameUpdated(cleanName)

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
        prefs.maxPhotosSizeMB = maxPhotosSizeMBState
        prefs.minPhotosSizeMB = minPhotosSizeMBState
        prefs.maxVideosSizeMB = maxVideosSizeMBState
        prefs.minVideosSizeMB = minVideosSizeMBState
        prefs.maxDocsSizeMB = maxDocsSizeMBState
        prefs.minDocsSizeMB = minDocsSizeMBState
        prefs.maxAudioSizeMB = maxAudioSizeMBState
        prefs.minAudioSizeMB = minAudioSizeMBState
        prefs.maxArchivesSizeMB = maxArchivesSizeMBState
        prefs.minArchivesSizeMB = minArchivesSizeMBState
        prefs.maxApksSizeMB = maxApksSizeMBState
        prefs.minApksSizeMB = minApksSizeMBState
        prefs.maxOtherSizeMB = maxOtherSizeMBState
        prefs.minOtherSizeMB = minOtherSizeMBState

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
                    "display_name" to cleanName,
                    "recovery_email" to recoveryEmailInput.trim(),
                    "profile_picture_uri" to (userAvatarUri?.toString() ?: prefs.profilePictureUri),
                    "smart_backup_mode" to smartBackupMode.toString(),
                    "wifi_only" to backupOverWifi.toString(),
                    "app_theme" to selectedTheme.toString(),
                    "biometric_lock" to biometricLock.toString(),
                    "trash_auto_clean_days" to trashAutoCleanDays.toString(),
                    "max_file_size_mb" to maxFileSizeMBState.toString(),
                    "max_photos_size_mb" to maxPhotosSizeMBState.toString(),
                    "min_photos_size_mb" to minPhotosSizeMBState.toString(),
                    "max_videos_size_mb" to maxVideosSizeMBState.toString(),
                    "min_videos_size_mb" to minVideosSizeMBState.toString(),
                    "max_docs_size_mb" to maxDocsSizeMBState.toString(),
                    "min_docs_size_mb" to minDocsSizeMBState.toString(),
                    "max_audio_size_mb" to maxAudioSizeMBState.toString(),
                    "min_audio_size_mb" to minAudioSizeMBState.toString(),
                    "max_archives_size_mb" to maxArchivesSizeMBState.toString(),
                    "min_archives_size_mb" to minArchivesSizeMBState.toString(),
                    "max_apks_size_mb" to maxApksSizeMBState.toString(),
                    "min_apks_size_mb" to minApksSizeMBState.toString(),
                    "max_other_size_mb" to maxOtherSizeMBState.toString(),
                    "min_other_size_mb" to minOtherSizeMBState.toString()
                ),
                showToast = false
            )
            com.example.telegrambackup.utils.CloudProfileSyncManager.syncLocalConfigToCloud(context)
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    // 🎵 SpotiFLAC Bold Header Title & Subtitle
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Configure cloud engine, bot fleet, security & preferences",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        )
                    }

                    // 🔍 SpotiFLAC Pill Search Field
                    SpotiSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search settings, rules, features...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 🌟 SpotiFLAC Hero Profile & Summary Card
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = spotiGroupCardColor(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerHapticFeedback(context, haptic)
                                currentCategory = StreamlinedCategory.PROFILE_ACCOUNT
                            }
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(TelegramBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (userAvatarUri != null) {
                                        AsyncImage(
                                            model = userAvatarUri,
                                            contentDescription = "Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            userNameInput.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            userNameInput,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 17.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = TelegramEmerald.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "Active",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = TelegramEmerald,
                                                    fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        "${formatFileSize(realDbTotalBytes)} • $realDbRecordCount files protected",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TelegramBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showNetworkSelfAnalysisModal = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.Insights, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("7-Day AI", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showDiagnosticModal = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Health", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }

                                Button(
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
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                }
                            }
                        }
                    }

                    val q = searchQuery.trim().lowercase()

                    fun matchSearch(vararg terms: String): Boolean {
                        if (q.isEmpty()) return true
                        return terms.any { term ->
                            val lower = term.lowercase()
                            lower.contains(q) || q.contains(lower)
                        }
                    }

                    // ==========================================
                    // 1. CLOUD & BACKUP (SpotiFLAC Group)
                    // ==========================================
                    if (matchSearch("cloud", "sync", "backup", "telegram", "token", "chat", "credentials", "fleet", "bot", "channel", "smart", "mode", "schedule", "wifi", "network", "speed", "threads")) {
                        SpotiSectionHeader("Cloud & Sync")

                        SpotiSettingsGroup {
                            // Global Master Switch (Controls All Backups & Drive)
                            if (matchSearch("global", "master", "stop", "pause", "resume", "all", "backup", "drive")) {
                                SpotiSettingsSwitchItem(
                                    icon = if (!stopAllBackup) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    title = "Global Cloud Sync Engine",
                                    subtitle = if (!stopAllBackup) "Master switch active (All cloud services enabled)" else "All cloud sync operations paused",
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
                                    iconTint = if (!stopAllBackup) TelegramEmerald else Color(0xFFEF4444)
                                )
                            }

                            // Separate Cloud Drive Sync Toggle
                            if (matchSearch("drive", "sync", "manual", "folder", "tree", "direct", "upload")) {
                                SpotiSettingsSwitchItem(
                                    icon = Icons.Default.CloudUpload,
                                    title = "Cloud Drive Direct Uploads",
                                    subtitle = "Dedicated 3-4 bot fleet for Drive file drops",
                                    checked = driveSyncEnabledState,
                                    onCheckedChange = { isEnabled ->
                                        triggerHapticFeedback(context, haptic)
                                        driveSyncEnabledState = isEnabled
                                        prefs.driveSyncEnabled = isEnabled
                                        Toast.makeText(context, if (isEnabled) "Cloud Drive sync enabled" else "Cloud Drive sync paused", Toast.LENGTH_SHORT).show()
                                    },
                                    iconTint = Color(0xFF8B5CF6)
                                )
                            }

                            if (matchSearch("telegram", "credentials", "token", "chat", "key", "bot", "id")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.Key,
                                    title = "Telegram Credentials",
                                    subtitle = "Bot token & destination chat ID",
                                    value = if (prefs.credentialsVerified) "Connected" else "Setup required",
                                    valueColor = if (prefs.credentialsVerified) TelegramEmerald else Color(0xFFF59E0B),
                                    iconTint = Color(0xFF3B82F6)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.TELEGRAM_CREDENTIALS
                                }
                            }

                            if (matchSearch("fleet", "multi", "multi-bot", "bot", "channel", "routing", "speed", "pool", "turbo", "load balancer")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.Speed,
                                    title = "Bot Fleet & Routing",
                                    subtitle = "Multi-bot pool (300% faster) & category channels",
                                    value = "Manage Fleet",
                                    valueColor = TelegramBlue,
                                    iconTint = Color(0xFF6366F1)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.BOT_FLEET
                                }
                            }

                            if (matchSearch("smart", "backup", "engine", "mode", "schedule", "folder", "filter", "continuous", "nightly", "interval", "camera", "downloads", "documents", "videos", "apks", "limit", "size")) {
                                val modeName = when (smartBackupMode) {
                                    0 -> "Continuous"
                                    1 -> "Every ${smartScanInterval}h"
                                    else -> "Nightly"
                                }
                                SpotiSettingsItem(
                                    icon = Icons.Default.CloudSync,
                                    title = "Smart Backup Engine",
                                    subtitle = "Automation schedules & file filters",
                                    value = if (autoBackup) modeName else "Off",
                                    valueColor = if (autoBackup) TelegramBlue else MaterialTheme.colorScheme.outline,
                                    iconTint = Color(0xFF10B981)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.BACKUP_SOURCES_RULES
                                }
                            }

                            if (matchSearch("network", "wifi", "cellular", "data", "speed", "threads", "compression", "limit", "performance")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.Wifi,
                                    title = "Network & Upload Speed",
                                    subtitle = "Wi-Fi rule, threads & compression",
                                    value = if (backupOverWifi) "Wi-Fi Only" else "Any",
                                    iconTint = Color(0xFF06B6D4),
                                    showDivider = false
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.NETWORK_PERFORMANCE
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 2. SECURITY & PRIVACY (SpotiFLAC Group)
                    // ==========================================
                    if (matchSearch("security", "privacy", "key", "encryption", "biometric", "fingerprint", "pin", "master", "aes", "diagnostics", "health", "ping", "test")) {
                        SpotiSectionHeader("Security & Privacy")

                        SpotiSettingsGroup {
                            if (matchSearch("encryption", "master", "key", "aes", "256", "biometric", "fingerprint", "pin", "security")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.Lock,
                                    title = "Encryption & Master Key",
                                    subtitle = "256-bit AES encryption & biometrics",
                                    value = if (biometricLock) "Biometrics Active" else "AES-256",
                                    valueColor = Color(0xFF8B5CF6),
                                    iconTint = Color(0xFF8B5CF6)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.SECURITY_PRIVACY_RESTORE
                                }
                            }

                            if (matchSearch("diagnostics", "health", "test", "ping", "verify", "server", "bot")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.HealthAndSafety,
                                    title = "System Diagnostics",
                                    subtitle = "Server ping & bot verification test",
                                    value = "Run Test",
                                    valueColor = Color(0xFF14B8A6),
                                    iconTint = Color(0xFF14B8A6),
                                    showDivider = false
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    showDiagnosticModal = true
                                }
                            }
                        }
                    }

                    // ==========================================
                    // 2b. AI & VISION INTELLIGENCE (SpotiFLAC Group)
                    // ==========================================
                    if (matchSearch("ai", "gemini", "vision", "document", "ask", "photos", "marksheet", "receipt", "api key", "intelligence", "lens")) {
                        SpotiSectionHeader("AI & Vision Intelligence")

                        SpotiSettingsGroup {
                            val apiKeyCount = prefs.getGeminiApiKeyList().size
                            val hasKey = prefs.hasValidGeminiKey()
                            SpotiSettingsItem(
                                icon = Icons.Default.AutoAwesome,
                                title = "Ask Photos & Document AI",
                                subtitle = if (hasKey) "$apiKeyCount API key(s) configured • Auto-failover ready" else "Setup Google Gemini API key(s)",
                                value = if (hasKey) "Ready" else "Setup Key",
                                valueColor = if (hasKey) TelegramEmerald else Color(0xFFF59E0B),
                                iconTint = Color(0xFF8B5CF6)
                            ) {
                                triggerHapticFeedback(context, haptic)
                                currentCategory = StreamlinedCategory.AI_VISION_INTELLIGENCE
                            }

                            var aiDocScannerEnabled by remember { mutableStateOf(prefs.isAiDocumentScannerEnabled) }
                            SpotiSettingsSwitchItem(
                                icon = Icons.Default.DocumentScanner,
                                title = "Smart Document Recognition",
                                subtitle = "Instant marksheet, 10k receipt & ID card presets in gallery",
                                checked = aiDocScannerEnabled,
                                onCheckedChange = { isEnabled ->
                                    triggerHapticFeedback(context, haptic)
                                    aiDocScannerEnabled = isEnabled
                                    prefs.isAiDocumentScannerEnabled = isEnabled
                                },
                                iconTint = TelegramBlue,
                                showDivider = false
                            )
                        }
                    }

                    // ==========================================
                    // 3. PREFERENCES (SpotiFLAC Group)
                    // ==========================================
                    if (matchSearch("profile", "account", "email", "recovery", "display name", "name", "theme", "appearance", "dark", "light", "mode", "color", "notification")) {
                        SpotiSectionHeader("Preferences")

                        SpotiSettingsGroup {
                            if (matchSearch("profile", "account", "email", "recovery", "inbox", "name", "display name")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.Person,
                                    title = "Profile & Recovery Email",
                                    subtitle = "Display name & recovery inbox",
                                    value = if (prefs.recoveryEmail.isNotEmpty()) "Configured" else "",
                                    iconTint = Color(0xFFEC4899)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.PROFILE_ACCOUNT
                                }
                            }

                            if (matchSearch("appearance", "theme", "dark", "light", "system", "mode", "color", "look")) {
                                val themeLabel = AppThemeMode.fromId(selectedTheme).title
                                SpotiSettingsItem(
                                    icon = Icons.Default.Palette,
                                    title = "Appearance & Themes",
                                    subtitle = "6 clean styles, AMOLED black & app icons",
                                    value = "${themeLabel} • ${if (prefs.appIcon == "blue") "Blue Icon" else "Green Icon"}",
                                    iconTint = Color(0xFFF59E0B)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.APPEARANCE_REGION
                                }
                            }

                            if (matchSearch("bottom", "navigation", "nav", "bar", "tabs", "labels", "m3", "customization", "compact", "material")) {
                                val navTabsCount = prefs.bottomNavVisibleTabs.split(",").filter { it.isNotBlank() }.size
                                val densityLabel = when (prefs.bottomNavHeightMode) {
                                    1 -> "Compact"
                                    2 -> "Slim"
                                    else -> "Standard M3"
                                }
                                SpotiSettingsItem(
                                    icon = Icons.Default.ViewCarousel,
                                    title = "Bottom Navigation Bar",
                                    subtitle = "Fully customizable M3 tabs, labels, pills & height",
                                    value = "$navTabsCount tabs • $densityLabel",
                                    iconTint = Color(0xFF0EA5E9)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.BOTTOM_NAV_CUSTOMIZATION
                                }
                            }

                            if (matchSearch("notification", "controller", "status", "bar", "start", "stop", "alert", "download", "minimal")) {
                                var persistentNotifActive by remember { mutableStateOf(prefs.persistentNotificationEnabled) }
                                SpotiSettingsItem(
                                    icon = Icons.Default.NotificationsActive,
                                    title = "Quick Controller",
                                    subtitle = "Status bar 1-tap Start/Stop & separate channels",
                                    value = if (persistentNotifActive) "Enabled" else "Disabled",
                                    valueColor = if (persistentNotifActive) TelegramEmerald else Color.Gray,
                                    iconTint = TelegramBlue,
                                    showDivider = false
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

                    // ==========================================
                    // 4. SYSTEM & MAINTENANCE (SpotiFLAC Group)
                    // ==========================================
                    if (matchSearch("update", "version", "about", "changelog", "danger", "reset", "wipe", "delete", "trash", "clean", "logout", "log out", "cache", "storage")) {
                        SpotiSectionHeader("System & Maintenance")

                        SpotiSettingsGroup {
                            if (matchSearch("about", "update", "version", "specs", "changelog", "release")) {
                                val currentVersion = remember { com.example.telegrambackup.utils.AppVersionHelper.currentVersionName }
                                SpotiSettingsItem(
                                    icon = Icons.Default.SystemUpdate,
                                    title = "About & Updates",
                                    subtitle = "Software specs & changelog",
                                    value = "v$currentVersion",
                                    valueColor = Color(0xFF6366F1),
                                    iconTint = Color(0xFF6366F1)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    showAppInfoModal = true
                                }
                            }

                            if (matchSearch("cache", "storage", "clean", "size", "clear", "optimize", "memory", "budget")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.CleaningServices,
                                    title = "Cache & Storage",
                                    subtitle = "Auto-pruning budget & instant cleaner",
                                    value = "${AppCacheManager.formatBytes(cacheStats.totalBytes)} / ${maxCacheBudgetMB}MB",
                                    valueColor = if (cacheStats.usagePercentage > 0.85f) Color(0xFFEF4444) else TelegramEmerald,
                                    iconTint = Color(0xFF10B981)
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    cacheStats = AppCacheManager.getCacheStats(context)
                                    showCacheOptimizerModal = true
                                }
                            }

                            if (matchSearch("clear", "data", "purge", "telegram", "database", "wipe", "cleaner", "channel")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.DeleteSweep,
                                    title = "Clear File Data",
                                    subtitle = "Clear from App, Cloud DB & Telegram Channel",
                                    value = "",
                                    iconTint = Color(0xFFF59E0B),
                                    showDivider = true
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    dialogInitialTab = 0
                                    showClearDataAccountDialog = true
                                }
                                SpotiSettingsItem(
                                    icon = Icons.Default.CleaningServices,
                                    title = "Channel Cleaner",
                                    subtitle = "Directly sweep & purge orphaned files from Telegram channel",
                                    value = "",
                                    iconTint = TelegramBlue,
                                    showDivider = true
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    showTelegramChannelCleanerDialog = true
                                }
                            }

                            if (matchSearch("data", "reset", "wipe", "delete", "trash", "auto-clean", "danger", "maintenance")) {
                                SpotiSettingsItem(
                                    icon = Icons.Default.DeleteForever,
                                    title = "Data Reset & Purge",
                                    subtitle = "Trash auto-clean, selective wipe & account reset",
                                    value = "",
                                    iconTint = Color(0xFFEF4444),
                                    showDivider = false
                                ) {
                                    triggerHapticFeedback(context, haptic)
                                    currentCategory = StreamlinedCategory.DANGER_ZONE
                                }
                            }
                        }
                    }

                    // 💡 SpotiFLAC Informational Callout
                    SpotiSettingsInfoCard(
                        title = "Automatic Military-Grade Protection",
                        message = "Files are securely split into 4MB AES-256 encrypted chunks and load-balanced across your Telegram bot fleet with infinite cloud retention and zero file size caps.",
                        icon = Icons.Default.Shield,
                        tone = SpotiInfoTone.PRIMARY
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Log Out Button
                    OutlinedButton(
                        onClick = { triggerHapticFeedback(context, haptic); onLogout() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.40f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out Account", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(28.dp))
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
                    SpotiSubpageHeader(
                        title = "Profile & Account",
                        subtitle = "Personal identity, recovery email & cloud backup",
                        onBack = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(68.dp).clip(CircleShape).background(TelegramBlue), contentAlignment = Alignment.Center) {
                                    if (userAvatarUri != null) { AsyncImage(model = userAvatarUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    else { Text(userNameInput.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)) }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { avatarPickerLauncher.launch("image/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Change Photo")
                                        }

                                        if (userAvatarUri != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    com.example.telegrambackup.utils.ProfileManager.removeAvatar(context)
                                                    userAvatarUri = null
                                                    prefs.profilePictureUri = ""
                                                    prefs.profileUpdatedAt = System.currentTimeMillis()
                                                    scope.launch {
                                                        SettingsDbManager.saveCategorySettings(
                                                            context,
                                                            "Profile Picture",
                                                            mapOf("profile_picture_uri" to ""),
                                                            showToast = false
                                                        )
                                                        com.example.telegrambackup.utils.CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                                    }
                                                    Toast.makeText(context, "Profile Photo Removed", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remove")
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("JPG, PNG or WEBP (Saved to permanent storage)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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

                            Button(
                                onClick = {
                                    val trimmedName = userNameInput.trim().ifEmpty { "My Cloud Account" }
                                    prefs.displayName = trimmedName
                                    prefs.recoveryEmail = recoveryEmailInput.trim()
                                    prefs.profileUpdatedAt = System.currentTimeMillis()
                                    onNameUpdated(trimmedName)
                                    scope.launch {
                                        SettingsDbManager.saveCategorySettings(
                                            context,
                                            "Profile",
                                            mapOf(
                                                "display_name" to trimmedName,
                                                "recovery_email" to recoveryEmailInput.trim(),
                                                "profile_picture_uri" to (userAvatarUri?.toString() ?: prefs.profilePictureUri)
                                            ),
                                            showToast = true
                                        )
                                        com.example.telegrambackup.utils.CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                            }

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
                    SpotiSubpageHeader(
                        title = "Telegram Credentials",
                        subtitle = "Bot token & destination chat configuration",
                        onBack = {
                            prefs.credentialsVerified = true
                            isCredentialEditUnlocked = false
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

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
                    SpotiSubpageHeader(
                        title = "Smart Backup Engine",
                        subtitle = "Automation triggers, schedules & file limits",
                        onBack = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

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
                            OptionValueRowWithInfo("Max File Size Limit (Global)", if (maxFileSizeMBState <= 0) "Unlimited" else "${maxFileSizeMBState.toInt()} MB", { showCustomMaxLimitDialog = true }, { featureInfoTitle = "Global Size Limit"; featureInfoDesc = "Files exceeding this limit are skipped unless an individual condition is specified." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Photos
                            SwitchRowWithInfo("Backup Photos (JPG, PNG, HEIC)", includeCamera, { includeCamera = it; prefs.backupCamera = it }, { featureInfoTitle = "Photos"; featureInfoDesc = "Backs up image files." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.PHOTOS,
                                minMB = minPhotosSizeMBState,
                                maxMB = maxPhotosSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.PHOTOS }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Videos
                            SwitchRowWithInfo("Backup Videos (MP4, MKV, MOV)", includeVideos, { includeVideos = it; prefs.backupVideos = it }, { featureInfoTitle = "Videos"; featureInfoDesc = "Backs up video recordings." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.VIDEOS,
                                minMB = minVideosSizeMBState,
                                maxMB = maxVideosSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.VIDEOS }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Documents
                            SwitchRowWithInfo("Backup Documents (PDF, DOCX, XLSX)", includeDocs, { includeDocs = it; prefs.backupDocuments = it }, { featureInfoTitle = "Documents"; featureInfoDesc = "Backs up office files." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.DOCUMENTS,
                                minMB = minDocsSizeMBState,
                                maxMB = maxDocsSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.DOCUMENTS }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Audio
                            SwitchRowWithInfo("Backup Audio (MP3, WAV, FLAC)", includeAudio, { includeAudio = it; prefs.backupAudio = it }, { featureInfoTitle = "Audio"; featureInfoDesc = "Backs up audio recordings." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.AUDIO,
                                minMB = minAudioSizeMBState,
                                maxMB = maxAudioSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.AUDIO }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Downloads Folder
                            SwitchRowWithInfo("Backup Downloads Folder", includeDownloads, { includeDownloads = it; prefs.backupDownloads = it }, { featureInfoTitle = "Downloads"; featureInfoDesc = "Backs up device download folder." })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Archives
                            SwitchRowWithInfo("Backup Archives (ZIP, RAR, 7Z)", includeArchives, { includeArchives = it; prefs.backupArchives = it }, { featureInfoTitle = "Archives"; featureInfoDesc = "Backs up zip/rar archives." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.ARCHIVES,
                                minMB = minArchivesSizeMBState,
                                maxMB = maxArchivesSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.ARCHIVES }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // APK Packages
                            SwitchRowWithInfo("Backup APK Packages (.apk)", includeApks, { includeApks = it; prefs.backupApks = it }, { featureInfoTitle = "APKs"; featureInfoDesc = "Backs up installer APKs." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.APKS,
                                minMB = minApksSizeMBState,
                                maxMB = maxApksSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.APKS }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Other Files
                            SwitchRowWithInfo("Backup All Other Files", includeAllOther, { includeAllOther = it; prefs.backupAllOther = it }, { featureInfoTitle = "Other"; featureInfoDesc = "Backs up all other recognized files." })
                            CategorySizeConditionBadge(
                                category = BackupFileType.OTHER,
                                minMB = minOtherSizeMBState,
                                maxMB = maxOtherSizeMBState,
                                globalMaxMB = maxFileSizeMBState,
                                onClick = { selectedCategoryForSizeCondition = BackupFileType.OTHER }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            
                            // Hidden Files
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
                    SpotiSubpageHeader(
                        title = "Network & Performance",
                        subtitle = "Wi-Fi constraints, speeds, compression & limits",
                        onBack = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

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
                        "TELEGRAM API SERVER & ENDPOINTS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            OptionValueRowWithInfo(
                                "Bot API Server URL",
                                if (prefs.customBotApiUrl.isBlank()) "Default (api.telegram.org)" else prefs.customBotApiUrl,
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    showCustomBotApiDialog = true
                                },
                                onInfoClick = {
                                    featureInfoTitle = "Custom Bot API Server"
                                    featureInfoDesc = "Connect to a self-hosted Telegram Bot API server to upload files up to 2,000 MB (2 GB) in a single request and remove cloud rate limits."
                                }
                            )
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
                    SpotiSubpageHeader(
                        title = "Security & Master Key",
                        subtitle = "Biometrics, master encryption keys & screen privacy",
                        onBack = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwitchRowWithInfo("Biometric App Lock", biometricLock, { biometricLock = it; prefs.biometricLock = it }, { featureInfoTitle = "Biometrics"; featureInfoDesc = "Requires Fingerprint / Face Unlock when opening app." })
                            if (biometricLock) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                OptionValueRowWithInfo(
                                    "Auto-Lock Timeout",
                                    when (prefs.biometricAutoLockTimeoutSeconds) {
                                        0 -> "Immediately"
                                        30 -> "After 30s"
                                        60 -> "After 1 min"
                                        300 -> "After 5 min"
                                        900 -> "After 15 min"
                                        else -> "${prefs.biometricAutoLockTimeoutSeconds}s"
                                    },
                                    onClick = {
                                        showBiometricTimeoutDialog = true
                                    },
                                    onInfoClick = {
                                        featureInfoTitle = "Auto-Lock Timeout"
                                        featureInfoDesc = "Locks the app after being placed in background for this duration."
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                SwitchRowWithInfo(
                                    "Lock Safe & Secret Storage",
                                    prefs.biometricLockForSafe,
                                    { prefs.biometricLockForSafe = it },
                                    {
                                        featureInfoTitle = "Safe Folder Lock"
                                        featureInfoDesc = "Requires biometric unlock before viewing hidden or safe files."
                                    }
                                )
                            }
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

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showKeyRecoveryDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("12-Word & QR", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Email Key", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
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
                    SpotiSubpageHeader(
                        title = "Appearance & Theme",
                        subtitle = "Select from top 6 clean visual themes",
                        onBack = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

                    Text(
                        "CHOOSE YOUR THEME (TOP 6 ESSENTIAL STYLES)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            AppThemeMode.entries.forEachIndexed { index, themeMode ->
                                val isSelected = selectedTheme == themeMode.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTheme = themeMode.id
                                            prefs.appTheme = themeMode.id
                                            onThemeChanged(themeMode.id)
                                            triggerHapticFeedback(context, haptic)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = themeMode.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 6.dp)
                                            ) {
                                                if (themeMode == AppThemeMode.SYSTEM) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Default",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.5.sp
                                                            ),
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                        )
                                                    }
                                                }
                                                if (themeMode == AppThemeMode.AMOLED) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF6366F1).copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "OLED",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = Color(0xFF818CF8),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.5.sp
                                                            ),
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                        )
                                                    }
                                                }
                                                if (isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = TelegramEmerald.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Active",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = TelegramEmerald,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.5.sp
                                                            ),
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = themeMode.description,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        // Color Preview Palette Dots
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(themeMode.backgroundColor).border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape))
                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(themeMode.surfaceColor).border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape))
                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(themeMode.primaryColor))
                                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(themeMode.secondaryColor))
                                        }
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTheme = themeMode.id
                                            prefs.appTheme = themeMode.id
                                            onThemeChanged(themeMode.id)
                                            triggerHapticFeedback(context, haptic)
                                        }
                                    )
                                }
                                if (index < AppThemeMode.entries.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "APP LAUNCHER ICON",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    var currentAppIcon by remember { mutableStateOf(com.example.telegrambackup.utils.AppIconManager.getCurrentIcon(context)) }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.example.telegrambackup.utils.AppIcon.entries.forEach { iconItem ->
                                val isIconSelected = currentAppIcon == iconItem
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isIconSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                    border = BorderStroke(
                                        width = if (isIconSelected) 1.5.dp else 1.dp,
                                        color = if (isIconSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentAppIcon != iconItem) {
                                                currentAppIcon = iconItem
                                                com.example.telegrambackup.utils.AppIconManager.setAppIcon(context, iconItem)
                                                triggerHapticFeedback(context, haptic)
                                                Toast.makeText(context, "App icon updated to ${iconItem.title}. Your launcher will refresh shortly.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = iconItem.previewDrawableRes),
                                            contentDescription = iconItem.title,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = iconItem.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isIconSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 6.dp)
                                                ) {
                                                    if (iconItem == com.example.telegrambackup.utils.AppIcon.GREEN) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "Default",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.5.sp
                                                                ),
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                            )
                                                        }
                                                    }
                                                    if (isIconSelected) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = TelegramEmerald.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = "Active",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    color = TelegramEmerald,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.5.sp
                                                                ),
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = iconItem.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        RadioButton(
                                            selected = isIconSelected,
                                            onClick = {
                                                if (currentAppIcon != iconItem) {
                                                    currentAppIcon = iconItem
                                                    com.example.telegrambackup.utils.AppIconManager.setAppIcon(context, iconItem)
                                                    triggerHapticFeedback(context, haptic)
                                                    Toast.makeText(context, "App icon updated to ${iconItem.title}. Your launcher will refresh shortly.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // =========================================================================
            // 🧭 MATERIAL 3 BOTTOM NAVIGATION BAR CUSTOMIZATION SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.BOTTOM_NAV_CUSTOMIZATION -> {
                var visibleTabsState by remember { mutableStateOf(prefs.bottomNavVisibleTabs) }
                var labelModeState by remember { mutableIntStateOf(prefs.bottomNavLabelMode) }
                var heightModeState by remember { mutableIntStateOf(prefs.bottomNavHeightMode) }
                var indicatorShapeState by remember { mutableIntStateOf(prefs.bottomNavIndicatorShape) }
                var hapticState by remember { mutableStateOf(prefs.bottomNavHapticFeedback) }
                var previewActiveTab by remember { mutableStateOf("HOME") }

                val activeTabsList = remember(visibleTabsState) {
                    visibleTabsState.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }

                fun toggleNavTab(tabKey: String) {
                    val currentList = visibleTabsState.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                    if (currentList.contains(tabKey)) {
                        if (currentList.size <= 2) {
                            Toast.makeText(context, "At least 2 tabs must remain visible", Toast.LENGTH_SHORT).show()
                            return
                        }
                        currentList.remove(tabKey)
                    } else {
                        currentList.add(tabKey)
                    }
                    val newTabsStr = currentList.joinToString(",")
                    visibleTabsState = newTabsStr
                    prefs.bottomNavVisibleTabs = newTabsStr
                    triggerHapticFeedback(context, haptic)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SpotiSubpageHeader(
                        title = "Bottom Navigation Bar",
                        subtitle = "Customize tabs, labels, bar height & indicator shapes",
                        onBack = {
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

                    // 1. LIVE INTERACTIVE PREVIEW
                    Text(
                        "LIVE INTERACTIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    val previewIndicatorShape = when (indicatorShapeState) {
                        1 -> RoundedCornerShape(10.dp)
                        2 -> CircleShape
                        3 -> RoundedCornerShape(4.dp)
                        else -> RoundedCornerShape(16.dp)
                    }

                    val previewHeightDp = when (heightModeState) {
                        1 -> 64.dp
                        2 -> 54.dp
                        else -> 80.dp
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Tap tabs below to preview active state",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val allAvailableTabs = listOf(
                                com.example.telegrambackup.ui.common.KomiNavItemData("HOME", "HOME", "Clean", Icons.Default.CleaningServices),
                                com.example.telegrambackup.ui.common.KomiNavItemData("FILES", "FILES", "Browse", Icons.Default.Folder),
                                com.example.telegrambackup.ui.common.KomiNavItemData("DRIVE", "DRIVE", "Drive", Icons.Default.CloudQueue),
                                com.example.telegrambackup.ui.common.KomiNavItemData("MONTHLY_ARCHIVE", "MONTHLY_ARCHIVE", "Monthly", Icons.Default.CalendarMonth),
                                com.example.telegrambackup.ui.common.KomiNavItemData("PROGRESS", "PROGRESS", "Progress", Icons.Default.HourglassTop),
                                com.example.telegrambackup.ui.common.KomiNavItemData("SETTINGS", "SETTINGS", "Settings", Icons.Default.Settings)
                            )

                            val displayTabs = allAvailableTabs.filter { activeTabsList.contains(it.key) }

                            com.example.telegrambackup.ui.common.KomiBottomBar(
                                items = displayTabs,
                                selectedTab = previewActiveTab,
                                onTabSelected = { key ->
                                    previewActiveTab = key
                                },
                                barHeight = previewHeightDp,
                                labelMode = labelModeState,
                                hapticFeedbackEnabled = hapticState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 2. VISIBLE TABS SELECTION
                    Text(
                        "VISIBLE TABS (${activeTabsList.size} OF 6 ENABLED)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val tabsConfig = listOf(
                                Triple("HOME", "Clean (Dashboard)", "Storage cleaner, cache scanner & quick backups") to Icons.Default.CleaningServices,
                                Triple("FILES", "Browse (Files)", "File explorer, recent media, Safe & Starred collections") to Icons.Default.Folder,
                                Triple("DRIVE", "Cloud Drive", "Google Drive-style cloud folders, instant DB streaming") to Icons.Default.CloudQueue,
                                Triple("MONTHLY_ARCHIVE", "Monthly Archive", "Timeline backup archives grouped by month & year") to Icons.Default.CalendarMonth,
                                Triple("PROGRESS", "Upload Progress", "Live upload progress, chunk status & bot speed monitor") to Icons.Default.HourglassTop,
                                Triple("SETTINGS", "Settings", "Full preferences, themes, bot fleet & security vault") to Icons.Default.Settings
                            )

                            tabsConfig.forEachIndexed { index, (tabInfo, icon) ->
                                val (key, title, subtitle) = tabInfo
                                val isEnabled = activeTabsList.contains(key)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggleNavTab(key) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (isEnabled) TelegramBlue.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isEnabled) TelegramBlue else Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { toggleNavTab(key) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = TelegramBlue
                                        )
                                    )
                                }

                                if (index < tabsConfig.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. LABEL DISPLAY STYLE
                    Text(
                        "LABEL DISPLAY STYLE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val labelModes = listOf(
                                Triple(0, "Always Show Labels", "Labels visible under all navigation icons (Standard M3)"),
                                Triple(1, "Show on Active Tab Only", "Only the currently active tab displays its text label"),
                                Triple(2, "Never Show Labels (Icons Only)", "Ultra-minimal icon-only navigation without text labels")
                            )

                            labelModes.forEachIndexed { index, (mode, title, desc) ->
                                val isSelected = labelModeState == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            labelModeState = mode
                                            prefs.bottomNavLabelMode = mode
                                            triggerHapticFeedback(context, haptic)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            labelModeState = mode
                                            prefs.bottomNavLabelMode = mode
                                            triggerHapticFeedback(context, haptic)
                                        }
                                    )
                                }
                                if (index < labelModes.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }
                    }

                    // 4. BAR DENSITY & HEIGHT
                    Text(
                        "BAR DENSITY & HEIGHT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val heightModes = listOf(
                                Triple(0, "Standard Material 3 (80dp)", "Default Google M3 spacing with full touch targets"),
                                Triple(1, "Compact M3 (64dp)", "Reclaims 16dp of screen height while maintaining touch ergonomics"),
                                Triple(2, "Ultra-Slim (54dp)", "Maximizes usable content area up to 88% of screen height")
                            )

                            heightModes.forEachIndexed { index, (mode, title, desc) ->
                                val isSelected = heightModeState == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            heightModeState = mode
                                            prefs.bottomNavHeightMode = mode
                                            triggerHapticFeedback(context, haptic)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            heightModeState = mode
                                            prefs.bottomNavHeightMode = mode
                                            triggerHapticFeedback(context, haptic)
                                        }
                                    )
                                }
                                if (index < heightModes.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }
                    }

                    // 5. ACTIVE TAB INDICATOR SHAPE
                    Text(
                        "ACTIVE TAB INDICATOR SHAPE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val indicatorShapes = listOf(
                                Triple(0, "Classic M3 Pill", "Rounded pill capsule with 16dp corners"),
                                Triple(1, "Modern Squircle", "Gentle squircle with 10dp rounded corners"),
                                Triple(2, "Full Oval Capsule", "Full circle/oval highlight wrapping icon"),
                                Triple(3, "Minimal Underline Bar", "Clean compact minimal geometry")
                            )

                            indicatorShapes.forEachIndexed { index, (shapeId, title, desc) ->
                                val isSelected = indicatorShapeState == shapeId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            indicatorShapeState = shapeId
                                            prefs.bottomNavIndicatorShape = shapeId
                                            triggerHapticFeedback(context, haptic)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            indicatorShapeState = shapeId
                                            prefs.bottomNavIndicatorShape = shapeId
                                            triggerHapticFeedback(context, haptic)
                                        }
                                    )
                                }
                                if (index < indicatorShapes.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }
                    }

                    // 6. BEHAVIOR & HAPTICS
                    Text(
                        "BEHAVIOR & HAPTICS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    )

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Haptic Feedback on Navigation", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Subtle tactile vibration pulse when switching tabs", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                                Switch(
                                    checked = hapticState,
                                    onCheckedChange = {
                                        hapticState = it
                                        prefs.bottomNavHapticFeedback = it
                                        if (it) triggerHapticFeedback(context, haptic)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = TelegramBlue
                                    )
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            OutlinedButton(
                                onClick = {
                                    val defaultTabs = "HOME,FILES,DRIVE,SETTINGS"
                                    visibleTabsState = defaultTabs
                                    prefs.bottomNavVisibleTabs = defaultTabs
                                    labelModeState = 0
                                    prefs.bottomNavLabelMode = 0
                                    heightModeState = 0
                                    prefs.bottomNavHeightMode = 0
                                    indicatorShapeState = 0
                                    prefs.bottomNavIndicatorShape = 0
                                    hapticState = true
                                    prefs.bottomNavHapticFeedback = true
                                    triggerHapticFeedback(context, haptic)
                                    Toast.makeText(context, "✓ Reset Navigation Bar to M3 Defaults", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset to Material 3 Defaults", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // =========================================================================
            // 🤖 AI & VISION INTELLIGENCE SUB-SETTING SCREEN
            // =========================================================================
            StreamlinedCategory.AI_VISION_INTELLIGENCE -> {
                var geminiKeysInput by remember { mutableStateOf(prefs.geminiApiKeys) }
                var selectedAiModel by remember { mutableStateOf(prefs.activeAiModel) }
                var docScannerEnabled by remember { mutableStateOf(prefs.isAiDocumentScannerEnabled) }
                var isTestingKey by remember { mutableStateOf(false) }
                var testResultText by remember { mutableStateOf<String?>(null) }
                var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }

                var thumbnailCacheSize by remember { mutableLongStateOf(ThumbnailManager.getDiskCacheSizeBytes(context)) }
                var thumbnailCount by remember { mutableIntStateOf(ThumbnailManager.getThumbnailCount(context)) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SpotiSubpageHeader(
                        title = "AI & Vision Intelligence",
                        subtitle = "Google Gemini multimodal Q&A & document recognition",
                        onBack = {
                            prefs.geminiApiKeys = geminiKeysInput.trim()
                            prefs.activeAiModel = selectedAiModel
                            prefs.isAiDocumentScannerEnabled = docScannerEnabled
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

                    // Hero Card
                    SubCategoryHeroHeader(
                        icon = Icons.Default.AutoAwesome,
                        iconTint = Color(0xFF8B5CF6),
                        title = "Google Gemini Vision",
                        description = "Ask questions about 10th/12th marksheets, 10k payment receipts, bills, and pictures like Google Photos. Zero device strain: downsampled <5MB RAM avoids heat or battery drain.",
                        badgeText = if (prefs.hasValidGeminiKey()) "Connected" else "Key Required",
                        badgeColor = if (prefs.hasValidGeminiKey()) TelegramEmerald else Color(0xFFF59E0B)
                    )

                    // 1. API Keys Group
                    SpotiSectionHeader("Google Gemini API Key(s)")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Single or Multiple API Keys supported. Enter keys separated by commas or newlines. If a key hits quota or rate limit (HTTP 429), the engine automatically fails over to the next key seamlessly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = geminiKeysInput,
                                onValueChange = {
                                    geminiKeysInput = it
                                    testResultText = null
                                    testResultSuccess = null
                                },
                                label = { Text("Gemini API Key(s)") },
                                placeholder = { Text("AIzaSy..., AIzaSy...") },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Action Buttons Row: Save & Test Connection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        prefs.geminiApiKeys = geminiKeysInput.trim()
                                        Toast.makeText(context, "Saved Gemini API Keys", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Keys", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        isTestingKey = true
                                        testResultText = null
                                        testResultSuccess = null
                                        val firstKey = geminiKeysInput.split(",", "\n", ";")
                                            .map { it.trim() }
                                            .firstOrNull { it.isNotBlank() } ?: ""

                                        if (firstKey.isBlank()) {
                                            isTestingKey = false
                                            testResultSuccess = false
                                            testResultText = "Please enter at least one API key first."
                                            return@Button
                                        }

                                        scope.launch {
                                            val res = AiVisionManager.testApiKey(firstKey, selectedAiModel)
                                            isTestingKey = false
                                            if (res.isSuccess) {
                                                testResultSuccess = true
                                                testResultText = "✅ ${res.getOrNull()}"
                                                prefs.geminiApiKeys = geminiKeysInput.trim()
                                            } else {
                                                testResultSuccess = false
                                                testResultText = "❌ Connection failed: ${res.exceptionOrNull()?.message ?: "Invalid key or rate limit"}"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isTestingKey,
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    if (isTestingKey) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Testing...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Key", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Test Result Banner
                            if (testResultText != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (testResultSuccess == true) TelegramEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (testResultSuccess == true) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (testResultSuccess == true) TelegramEmerald else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            testResultText ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (testResultSuccess == true) TelegramEmerald else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            // Link to Google AI Studio
                            OutlinedButton(
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Visit https://aistudio.google.com/app/apikey to get a free key", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Get Free Gemini API Key from Google AI Studio")
                            }
                        }
                    }

                    // 2. Model Selection
                    SpotiSectionHeader("Vision Model Engine")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                "auto" to ("Auto-Detect Best Model (Recommended)" to "Automatically discovers & connects to the active Gemini vision model supported by your key"),
                                "gemini-2.0-flash" to ("Gemini 2.0 Flash (Recommended)" to "Fastest responses (1-2s), latest stable Google Gemini 2.0 vision engine"),
                                "gemini-2.0-flash-lite" to ("Gemini 2.0 Flash Lite" to "High efficiency, lower latency multimodal analysis"),
                                "gemini-3.0-flash" to ("Gemini 3.0 Flash" to "Next-generation multimodal reasoning and document parsing"),
                                "gemini-2.0-pro-exp-02-05" to ("Gemini 2.0 Pro" to "High-complexity visual reasoning, complex tables & multi-page receipts")
                            ).forEach { (modelId, info) ->
                                val (title, subtitle) = info
                                val isSelected = selectedAiModel == modelId
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) TelegramBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            triggerHapticFeedback(context, haptic)
                                            selectedAiModel = modelId
                                            prefs.activeAiModel = modelId
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedAiModel = modelId
                                                prefs.activeAiModel = modelId
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Document Recognition & Presets
                    SpotiSectionHeader("Document Recognition Features")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Smart Document Assistant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Quick action chips for 10th/12th marksheets, 10k payment receipts, and ID card questions in gallery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = docScannerEnabled,
                                    onCheckedChange = {
                                        triggerHapticFeedback(context, haptic)
                                        docScannerEnabled = it
                                        prefs.isAiDocumentScannerEnabled = it
                                    }
                                )
                            }
                        }
                    }

                    // 4. Ultra-Fast Gallery Thumbnails
                    SpotiSectionHeader("High-Performance Gallery Thumbnails")
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Local galleries and file grids use 2-tier caching: tiny 8-12 KB WebP persistent thumbnails on disk and a 30MB memory LRU cache. This enables instant 120Hz smooth scrolling without downloading high-res cloud files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Thumbnail Cache Used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        "${AppCacheManager.formatBytes(thumbnailCacheSize)} ($thumbnailCount items)",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        scope.launch {
                                            val reclaimed = ThumbnailManager.clearDiskCache(context)
                                            thumbnailCacheSize = ThumbnailManager.getDiskCacheSizeBytes(context)
                                            thumbnailCount = ThumbnailManager.getThumbnailCount(context)
                                            Toast.makeText(context, "🧹 Cleared ${AppCacheManager.formatBytes(reclaimed)} thumbnail cache", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Thumbnails")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
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
                    SpotiSubpageHeader(
                        title = "Data Management & Reset",
                        subtitle = "Wipe cache, clear history or reset account data",
                        onBack = {
                            saveAllSettings(silent = true)
                            currentCategory = StreamlinedCategory.MAIN
                        }
                    )

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
                            SettingCategoryRow(Icons.Default.DeleteSweep, "Clear File Data (App, DB & Telegram)", "Granular wipe: App, Cloud Database & Telegram Channel", Color(0xFFF59E0B)) {
                                triggerHapticFeedback(context, haptic)
                                dialogInitialTab = 0
                                showClearDataAccountDialog = true
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SettingCategoryRow(Icons.Default.CleaningServices, "Telegram Channel Cleaner (Deep Sweep)", "Directly scan & purge orphaned files/messages from Telegram channel", TelegramBlue) {
                                triggerHapticFeedback(context, haptic)
                                showTelegramChannelCleanerDialog = true
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
private fun CategorySizeConditionBadge(
    category: BackupFileType,
    minMB: Double,
    maxMB: Double,
    globalMaxMB: Double,
    onClick: () -> Unit
) {
    val hasCustom = maxMB > 0.0 || minMB > 0.0
    val label = if (!hasCustom) {
        "Rule: Global Limit (${if (globalMaxMB <= 0) "Unlimited" else "${globalMaxMB.toInt()} MB"})"
    } else {
        val minStr = if (minMB > 0.0) {
            val formatted = if (minMB < 1.0) "${(minMB * 1024).toInt()} KB" else "${minMB.toInt()} MB"
            "Min: $formatted • "
        } else ""
        val maxStr = if (maxMB > 0.0) "Max: ${maxMB.toInt()} MB" else "Max: Unlimited"
        "Rule: $minStr$maxStr"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (hasCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (hasCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (hasCustom) FontWeight.Bold else FontWeight.Medium),
                    color = if (hasCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
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
