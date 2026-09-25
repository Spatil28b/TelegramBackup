package com.example.telegrambackup.ui.settings

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AuthRepository
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.utils.AppCacheManager
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.DynamicProcessTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private val DangerRed = Color(0xFFEF4444)
private val WarningAmber = Color(0xFFF59E0B)
private val SuccessGreen = Color(0xFF10B981)
private val SurfaceDark = Color(0xFF1E293B)
private val CardSurface = Color(0xFF27354A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearDataAccountDialog(
    initialTab: Int = 0, // 0 = Clear File Data, 1 = Delete Account
    onDismiss: () -> Unit,
    onLogoutCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val authRepository = remember { AuthRepository(context) }

    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Storage Targets to Clear (User Request: Clear from App, Database, and Telegram Channel)
    var clearFromApp by remember { mutableStateOf(true) }
    var clearFromCloudDb by remember { mutableStateOf(true) }
    var deleteFromTelegram by remember { mutableStateOf(true) }

    // Filter Scope States (ALL, DRIVE_ONLY, AUTO_BACKUP_ONLY, OLDER_THAN_DAYS, TRASH_ONLY)
    var purgeMode by remember { mutableStateOf("ALL") }
    var selectedOlderDays by remember { mutableIntStateOf(30) }

    // Dynamic Preview Statistics
    var previewFileCount by remember { mutableIntStateOf(0) }
    var previewTotalBytes by remember { mutableLongStateOf(0L) }
    var previewChunkCount by remember { mutableIntStateOf(0) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Safety Inputs
    var clearConfirmInput by remember { mutableStateOf("") }
    var accountConfirmInput by remember { mutableStateOf("") }
    var accountPurgeTelegram by remember { mutableStateOf(true) }

    // Process & Progress States
    var isProcessing by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }

    // High Impact Check: Full Wipe of Cloud DB & Telegram Channel requires typing "CLEAR"
    val isHighImpactClear = selectedTab == 0 && purgeMode == "ALL" && clearFromCloudDb && deleteFromTelegram
    val canSubmitClear = selectedTab == 0 && (clearFromApp || clearFromCloudDb || deleteFromTelegram) &&
            (!isHighImpactClear || clearConfirmInput.trim().equals("CLEAR", ignoreCase = true))

    // Query Local Preview Statistics whenever filter changes
    LaunchedEffect(purgeMode, selectedOlderDays, selectedTab) {
        if (selectedTab == 0) {
            isLoadingPreview = true
            withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val allLocal = db.uploadedFileDao().getAllRawFiles()
                    val now = System.currentTimeMillis()
                    val cutoff = now - (selectedOlderDays * 86400000L)

                    val matched = when (purgeMode) {
                        "DRIVE_ONLY" -> allLocal.filter { !it.isDeleted && it.isDriveUpload }
                        "AUTO_BACKUP_ONLY" -> allLocal.filter { !it.isDeleted && !it.isDriveUpload }
                        "TRASH_ONLY" -> allLocal.filter { it.isDeleted }
                        "OLDER_THAN_DAYS" -> allLocal.filter { !it.isDeleted && it.timestamp < cutoff }
                        else -> allLocal.filter { !it.isDeleted }
                    }

                    var chunks = 0
                    for (f in matched) {
                        if (f.totalChunks > 1) {
                            chunks += f.totalChunks
                        } else {
                            chunks += 1
                        }
                    }

                    withContext(Dispatchers.Main) {
                        previewFileCount = matched.size
                        previewTotalBytes = matched.sumOf { it.size }
                        previewChunkCount = chunks
                        isLoadingPreview = false
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { isLoadingPreview = false }
                }
            }
        }
    }

    if (DynamicProcessTracker.isMinimized) {
        return
    }

    LaunchedEffect(isProcessing, statusMessage, progressFraction) {
        if (isProcessing) {
            if (!DynamicProcessTracker.isProcessActive) {
                DynamicProcessTracker.startProcess(
                    title = if (selectedTab == 0) "Clearing File Data" else "Deleting Account",
                    subtitle = statusMessage,
                    icon = if (selectedTab == 0) Icons.Default.DeleteSweep else Icons.Default.DeleteForever
                )
            }
            DynamicProcessTracker.updateProgress(progressFraction, statusMessage)
        } else {
            if (DynamicProcessTracker.isProcessActive) {
                DynamicProcessTracker.finishProcess()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.DeleteSweep else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (selectedTab == 0) WarningAmber else DangerRed,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (selectedTab == 0) "Clear File Data" else "Delete Account",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }

                    if (isProcessing) {
                        IconButton(onClick = { DynamicProcessTracker.minimize() }) {
                            Icon(Icons.Default.Remove, contentDescription = "Minimize", tint = TelegramBlue)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) WarningAmber.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable(enabled = !isProcessing) { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🧹 Clear File Data",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (selectedTab == 0) WarningAmber else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) DangerRed.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable(enabled = !isProcessing) { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🚨 Delete Account",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (selectedTab == 1) DangerRed else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedTab == 0) {
                    // =========================================================
                    // TAB 0: GRANULAR CLEAR FILE DATA
                    // =========================================================
                    Text(
                        "1. Select Storage Targets to Clear:",
                        style = MaterialTheme.typography.labelSmall.copy(color = WarningAmber, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Target 1: Clear from App
                    StorageTargetToggleItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Clear from Local App",
                        subtitle = "Wipes local database, chunk progress & caches",
                        checked = clearFromApp,
                        onCheckedChange = { clearFromApp = it },
                        enabled = !isProcessing
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Target 2: Clear from Cloud Database
                    StorageTargetToggleItem(
                        icon = Icons.Default.CloudQueue,
                        title = "Clear from Cloud Database",
                        subtitle = "Purges records from PostgreSQL server & share links",
                        checked = clearFromCloudDb,
                        onCheckedChange = { clearFromCloudDb = it },
                        enabled = !isProcessing
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Target 3: Delete from Telegram Channel
                    StorageTargetToggleItem(
                        icon = Icons.Default.Send,
                        title = "Delete from Telegram Channel",
                        subtitle = "Deletes original files and 4MB chunk parts in channel",
                        checked = deleteFromTelegram,
                        onCheckedChange = { deleteFromTelegram = it },
                        enabled = !isProcessing
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    )

                    Text(
                        "2. Select Files to Target:",
                        style = MaterialTheme.typography.labelSmall.copy(color = WarningAmber, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Mode 1: All Files
                    FilterModeRadioItem(
                        title = "All Files (Drive + Auto-Backup)",
                        subtitle = "Complete clean of all backup categories",
                        selected = purgeMode == "ALL",
                        onClick = { purgeMode = "ALL" },
                        enabled = !isProcessing
                    )

                    // Mode 2: Drive Only
                    FilterModeRadioItem(
                        title = "Cloud Drive Only",
                        subtitle = "Only manual uploads & cloud drive items",
                        selected = purgeMode == "DRIVE_ONLY",
                        onClick = { purgeMode = "DRIVE_ONLY" },
                        enabled = !isProcessing
                    )

                    // Mode 3: Auto-Backup Only
                    FilterModeRadioItem(
                        title = "Auto-Backup Only",
                        subtitle = "Only automatic background media backups",
                        selected = purgeMode == "AUTO_BACKUP_ONLY",
                        onClick = { purgeMode = "AUTO_BACKUP_ONLY" },
                        enabled = !isProcessing
                    )

                    // Mode 4: Older Than X Days
                    FilterModeRadioItem(
                        title = "Files Older Than $selectedOlderDays Days",
                        subtitle = "Safely prune older files to reclaim storage",
                        selected = purgeMode == "OLDER_THAN_DAYS",
                        onClick = { purgeMode = "OLDER_THAN_DAYS" },
                        enabled = !isProcessing
                    )

                    if (purgeMode == "OLDER_THAN_DAYS") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            listOf(30, 60, 90).forEach { days ->
                                FilterChip(
                                    selected = selectedOlderDays == days,
                                    onClick = { selectedOlderDays = days },
                                    label = { Text("$days Days", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = WarningAmber,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    // Mode 5: Trash Only
                    FilterModeRadioItem(
                        title = "Trash / Recycle Bin Only",
                        subtitle = "Permanently empty deleted trash files",
                        selected = purgeMode == "TRASH_ONLY",
                        onClick = { purgeMode = "TRASH_ONLY" },
                        enabled = !isProcessing
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Preview Summary Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Target Preview (Local Database):",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    if (isLoadingPreview) "Calculating matching items..."
                                    else if (previewFileCount == 0 && deleteFromTelegram)
                                        "0 local files • Telegram Channel Cleaner will scan & wipe orphaned channel messages directly"
                                    else "$previewFileCount files (~${Formatter.formatFileSize(context, previewTotalBytes)}) • ~$previewChunkCount Telegram parts",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (previewFileCount == 0 && deleteFromTelegram) WarningAmber else Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    // High Impact Confirmation (If All + Cloud DB + Telegram)
                    if (isHighImpactClear) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "High Impact: Full Wipe",
                                        style = MaterialTheme.typography.bodySmall.copy(color = DangerRed, fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    "This will permanently delete all files from your App, Cloud Database, and Telegram Channel. Type CLEAR to unlock:",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f)),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                OutlinedTextField(
                                    value = clearConfirmInput,
                                    onValueChange = { clearConfirmInput = it },
                                    placeholder = { Text("Type CLEAR", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DangerRed,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                )
                            }
                        }
                    }

                } else {
                    // =========================================================
                    // TAB 1: DELETE ACCOUNT PERMANENTLY
                    // =========================================================
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Permanent Deletion: Your account, cloud credentials, sync settings, and all backed-up files will be permanently wiped.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.9f))
                            )
                        }
                    }

                    StorageTargetToggleItem(
                        icon = Icons.Default.Send,
                        title = "Delete Files from Telegram Channel",
                        subtitle = "Permanently removes all uploaded files & chunks from Telegram",
                        checked = accountPurgeTelegram,
                        onCheckedChange = { accountPurgeTelegram = it },
                        enabled = !isProcessing
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "Type DELETE to confirm account deletion:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = accountConfirmInput,
                        onValueChange = { accountConfirmInput = it },
                        placeholder = { Text("Type DELETE", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DangerRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                }

                // Processing & Status Message Display
                if (isProcessing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (selectedTab == 0) WarningAmber else DangerRed,
                        trackColor = SurfaceDark
                    )
                }

                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (selectedTab == 0) WarningAmber else DangerRed,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isProcessing) return@Button

                    val email = prefs.userEmail.trim().lowercase()
                    val botToken = prefs.botToken.trim()
                    val chatId = prefs.chatId.trim()

                    if (selectedTab == 0) {
                        // =====================================================
                        // EXECUTE CLEAR FILE DATA ONLY
                        // =====================================================
                        if (!clearFromApp && !clearFromCloudDb && !deleteFromTelegram) {
                            Toast.makeText(context, "Select at least one storage target to clear.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (isHighImpactClear && !clearConfirmInput.trim().equals("CLEAR", ignoreCase = true)) {
                            Toast.makeText(context, "Please type CLEAR to proceed with full wipe.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessing = true
                        progressFraction = 0.1f
                        statusMessage = "Analyzing file records..."

                        scope.launch(Dispatchers.IO) {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val allLocal = db.uploadedFileDao().getAllRawFiles()
                                val now = System.currentTimeMillis()
                                val cutoff = now - (selectedOlderDays * 86400000L)

                                val targetLocalFiles = when (purgeMode) {
                                    "DRIVE_ONLY" -> allLocal.filter { !it.isDeleted && it.isDriveUpload }
                                    "AUTO_BACKUP_ONLY" -> allLocal.filter { !it.isDeleted && !it.isDriveUpload }
                                    "TRASH_ONLY" -> allLocal.filter { it.isDeleted }
                                    "OLDER_THAN_DAYS" -> allLocal.filter { !it.isDeleted && it.timestamp < cutoff }
                                    else -> allLocal.filter { !it.isDeleted }
                                }

                                // 1. Collect Message IDs from Local Files & Chunks
                                val localMessageIds = mutableSetOf<Long>()
                                for (file in targetLocalFiles) {
                                    if (file.messageId > 0) localMessageIds.add(file.messageId)
                                    if (file.chunksJson.isNotBlank() && file.chunksJson != "[]") {
                                        try {
                                            val cArr = JSONArray(file.chunksJson)
                                            for (i in 0 until cArr.length()) {
                                                val cObj = cArr.optJSONObject(i)
                                                val mId = cObj?.optLong("messageId", 0L) ?: 0L
                                                if (mId > 0) localMessageIds.add(mId)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    try {
                                        val chunkRows = db.chunkProgressDao().getUploadedChunksForFile(file.path)
                                        for (cr in chunkRows) {
                                            if (cr.messageId > 0) localMessageIds.add(cr.messageId)
                                        }
                                    } catch (_: Exception) {}
                                }

                                // 2. Purge from Cloud Database (if selected)
                                val serverMessageIds = mutableSetOf<Long>()
                                if (clearFromCloudDb) {
                                    withContext(Dispatchers.Main) {
                                        progressFraction = 0.3f
                                        statusMessage = "Purging records from cloud database..."
                                    }
                                    val (purgeSuccess, returnedMsgIds) = ServerApi().purgeFiles(
                                        prefs.serverUrl,
                                        email,
                                        mode = purgeMode,
                                        olderThanDays = selectedOlderDays
                                    )
                                    serverMessageIds.addAll(returnedMsgIds)
                                }

                                // 3. Delete from Telegram Channel (if selected)
                                var telegramDeletedCount = 0
                                var telegramPermissionError = false
                                if (deleteFromTelegram && chatId.isNotEmpty()) {
                                    val allMsgIds = (localMessageIds + serverMessageIds).filter { it > 0 }.distinct()
                                    val shouldPurgeAll = (purgeMode == "ALL") || (allMsgIds.isEmpty() && targetLocalFiles.isEmpty())

                                    withContext(Dispatchers.Main) {
                                        progressFraction = 0.5f
                                        statusMessage = if (shouldPurgeAll) "Deep cleaning Telegram channel files..." else "Deleting ${allMsgIds.size} messages from Telegram channel..."
                                    }
                                    val (actuallyDeleted, _, permError) = ServerApi().cleanTelegramChannelStorage(
                                        context = context,
                                        botToken = prefs.botToken,
                                        chatId = chatId,
                                        explicitMessageIds = allMsgIds,
                                        purgeAllRemaining = shouldPurgeAll
                                    ) { scanned, total, deleted, status ->
                                        val pct = 0.5f + (0.35f * (scanned.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f))
                                        progressFraction = pct
                                        statusMessage = status
                                    }
                                    telegramDeletedCount = actuallyDeleted
                                    telegramPermissionError = permError
                                }

                                // 4. Clear from Local App (if selected)
                                if (clearFromApp) {
                                    withContext(Dispatchers.Main) {
                                        progressFraction = 0.9f
                                        statusMessage = "Cleaning local database and app caches..."
                                    }
                                    when (purgeMode) {
                                        "ALL" -> {
                                            db.uploadedFileDao().deleteAll()
                                            db.chunkProgressDao().deleteAll()
                                        }
                                        "DRIVE_ONLY" -> {
                                            db.uploadedFileDao().deleteDriveFiles()
                                            db.chunkProgressDao().pruneOrphanedChunks()
                                        }
                                        "AUTO_BACKUP_ONLY" -> {
                                            db.uploadedFileDao().deleteAutoBackupFiles()
                                            db.chunkProgressDao().pruneOrphanedChunks()
                                        }
                                        "TRASH_ONLY" -> {
                                            db.uploadedFileDao().deleteTrashFiles()
                                        }
                                        "OLDER_THAN_DAYS" -> {
                                            db.uploadedFileDao().deleteOlderThan(cutoff)
                                            db.chunkProgressDao().pruneOrphanedChunks()
                                        }
                                    }
                                    // Deep cache purge
                                    AppCacheManager.clearAllCache(context)
                                }

                                withContext(Dispatchers.Main) {
                                    progressFraction = 1f
                                    isProcessing = false
                                    if (telegramPermissionError) {
                                        Toast.makeText(context, "⚠️ Telegram deletion failed: Bot is not an Admin with 'Delete Messages' right!", Toast.LENGTH_LONG).show()
                                    } else {
                                        val summaryMsg = buildString {
                                            append("✅ Cleanup finished:")
                                            if (clearFromApp) append(" App cache cleared.")
                                            if (clearFromCloudDb) append(" Cloud records purged.")
                                            if (deleteFromTelegram) append(" $telegramDeletedCount Telegram items deleted.")
                                        }
                                        Toast.makeText(context, summaryMsg, Toast.LENGTH_LONG).show()
                                    }
                                    onDismiss()
                                }

                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusMessage = "Error: ${e.localizedMessage ?: "Unknown failure"}"
                                }
                            }
                        }

                    } else {
                        // =====================================================
                        // EXECUTE DELETE ACCOUNT PERMANENTLY
                        // =====================================================
                        if (accountConfirmInput.trim().uppercase() != "DELETE") {
                            Toast.makeText(context, "Please type DELETE to confirm account deletion.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessing = true
                        progressFraction = 0.1f
                        statusMessage = "Collecting file records before account wipe..."

                        scope.launch(Dispatchers.IO) {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val allFiles = try { db.uploadedFileDao().getAllRawFiles() } catch (_: Exception) { emptyList() }
                                val localMsgIds = mutableSetOf<Long>()
                                for (f in allFiles) {
                                    if (f.messageId > 0) localMsgIds.add(f.messageId)
                                    if (f.chunksJson.isNotBlank() && f.chunksJson != "[]") {
                                        try {
                                            val cArr = JSONArray(f.chunksJson)
                                            for (i in 0 until cArr.length()) {
                                                val cObj = cArr.optJSONObject(i)
                                                val mId = cObj?.optLong("messageId", 0L) ?: 0L
                                                if (mId > 0) localMsgIds.add(mId)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    progressFraction = 0.35f
                                    statusMessage = "Deleting account and cloud records from server..."
                                }
                                val result = ServerApi().deleteAccount(prefs.serverUrl, email)
                                val serverMsgIds = result.second
                                val combinedIds = (localMsgIds + serverMsgIds).filter { it > 0 }.distinct()

                                var accountTelegramPermError = false
                                if (accountPurgeTelegram && chatId.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        progressFraction = 0.5f
                                        statusMessage = "Deep cleaning Telegram channel files..."
                                    }
                                    val (_, _, permError) = ServerApi().cleanTelegramChannelStorage(
                                        context = context,
                                        botToken = prefs.botToken,
                                        chatId = chatId,
                                        explicitMessageIds = combinedIds,
                                        purgeAllRemaining = true
                                    ) { scanned, total, deleted, status ->
                                        val pct = 0.5f + (0.4f * (scanned.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f))
                                        progressFraction = pct
                                        statusMessage = status
                                    }
                                    accountTelegramPermError = permError
                                }

                                withContext(Dispatchers.Main) {
                                    progressFraction = 0.95f
                                    statusMessage = "Wiping local session and cache..."
                                }

                                // Full Local Cleanup
                                db.uploadedFileDao().deleteAll()
                                db.chunkProgressDao().deleteAll()
                                AppCacheManager.clearAllCache(context)
                                authRepository.logout()

                                withContext(Dispatchers.Main) {
                                    progressFraction = 1f
                                    isProcessing = false
                                    if (accountTelegramPermError) {
                                        Toast.makeText(context, "⚠️ Account wiped, but Telegram files could not be deleted (bot lacks Delete Messages permission).", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "👋 Account and data deleted permanently.", Toast.LENGTH_LONG).show()
                                    }
                                    onLogoutCompleted()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusMessage = "Error: ${e.localizedMessage ?: "Unknown failure"}"
                                }
                            }
                        }
                    }
                },
                enabled = !isProcessing && (if (selectedTab == 0) canSubmitClear else accountConfirmInput.trim().uppercase() == "DELETE"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 0) WarningAmber else DangerRed
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(42.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (selectedTab == 0) "Wipe Selected Data" else "Delete Account",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isProcessing) onDismiss() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(
                    "Cancel",
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    )
}

@Composable
private fun StorageTargetToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) TelegramBlue else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TelegramBlue,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = SurfaceDark
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun FilterModeRadioItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            colors = RadioButtonDefaults.colors(selectedColor = WarningAmber)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
            )
        }
    }
}
