package com.example.telegrambackup.ui.main

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.explorer.formatFileSize
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.DynamicProcessTracker
import com.example.telegrambackup.utils.SettingsDbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashBinScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = remember { db.uploadedFileDao() }

    // Real-time reactive flow from Room DB — updates immediately whenever any file is trashed/restored/deleted
    val trashFiles by dao.getTrashFilesFlow().collectAsState(initial = emptyList())
    var autoCleanedCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-select state
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    // Dialog state
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var fileToDeletePermanently by remember { mutableStateOf<UploadedFile?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var fileForMenu by remember { mutableStateOf<UploadedFile?>(null) }

    BackHandler(enabled = isSelectionMode) {
        selectedPaths = emptySet()
        isSelectionMode = false
    }

    // Startup: Heal missing timestamps and execute background auto-clean policy
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Ensure all trashed files have valid deletedAt timestamps
                dao.fixMissingDeletedAt(System.currentTimeMillis())

                // Execute auto-clean if configured (P2)
                val cleaned = com.example.telegrambackup.utils.TrashRetentionDaemon.sweepExpiredTrash(context)
                if (cleaned > 0) {
                    withContext(Dispatchers.Main) {
                        autoCleanedCount = cleaned
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Show auto-clean feedback
    LaunchedEffect(autoCleanedCount) {
        if (autoCleanedCount > 0) {
            Toast.makeText(context, "🗑️ Auto-cleaned $autoCleanedCount expired items (${prefs.trashAutoCleanDays}-day policy)", Toast.LENGTH_LONG).show()
        }
    }

    val totalTrashSize = remember(trashFiles) { trashFiles.sumOf { it.size } }

    val filteredTrashFiles = remember(trashFiles, searchQuery) {
        if (searchQuery.isBlank()) trashFiles
        else trashFiles.filter {
            it.fileName.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Permanent delete worker with bounded progress and forceLocalDelete
    fun permanentlyDeleteFiles(filesToDelete: List<UploadedFile>) {
        if (filesToDelete.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val total = filesToDelete.size
            DynamicProcessTracker.startProcess(
                title = "Emptying Trash Bin",
                subtitle = "Permanently deleting $total item(s)...",
                icon = Icons.Default.DeleteForever
            )
            try {
                val serverApi = ServerApi()
                val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val userEmail = authPrefs.getString("user_email", "") ?: prefs.recoveryEmail

                filesToDelete.forEachIndexed { index, file ->
                    DynamicProcessTracker.updateProgress(
                        progress = if (total > 0) (index + 1).toFloat() / total.toFloat() else 0f,
                        subtitle = "Purging ${index + 1}/$total: ${file.fileName}"
                    )

                    // 1. Purge from Telegram Cloud Chat (with forceLocalDelete = true so Room DB & chunks are reliably freed)
                    try {
                        serverApi.purgeFileCompleteFromTelegram(
                            context = context,
                            botToken = prefs.botToken,
                            chatId = prefs.chatId,
                            file = file,
                            forceLocalDelete = true
                        )
                    } catch (_: Exception) {}

                    // 2. Delete from PostgreSQL Server DB
                    if (userEmail.isNotEmpty()) {
                        try {
                            serverApi.deleteFile(prefs.serverUrl, userEmail, file.telegramFileId, file.path)
                        } catch (_: Exception) {}
                    }
                }

                // 3. Guarantee local Room DB and chunks are 100% wiped
                val paths = filesToDelete.map { it.path }
                try {
                    dao.deleteByPaths(paths)
                    paths.forEach { db.chunkProgressDao().deleteChunksForFile(it) }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    DynamicProcessTracker.finishProcess()
                    Toast.makeText(context, "✅ Permanently deleted $total item(s)", Toast.LENGTH_SHORT).show()
                    selectedPaths = emptySet()
                    isSelectionMode = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    DynamicProcessTracker.finishProcess()
                    Toast.makeText(context, "Delete failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Restore worker with cloud re-indexing
    fun restoreFiles(filesToRestore: List<UploadedFile>) {
        if (filesToRestore.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val paths = filesToRestore.map { it.path }
                dao.restoreFilesByPaths(paths)

                // Re-index to PostgreSQL cloud server if user is authenticated
                val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val userEmail = authPrefs.getString("user_email", "") ?: prefs.recoveryEmail
                if (userEmail.isNotEmpty()) {
                    try {
                        val serverApi = ServerApi()
                        serverApi.indexFilesBatch(prefs.serverUrl, userEmail, filesToRestore)
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ Restored ${filesToRestore.size} item(s) to Backup!", Toast.LENGTH_SHORT).show()
                    selectedPaths = emptySet()
                    isSelectionMode = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Restore failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto-clean Retention Policy Selector Dialog
    if (showPolicyDialog) {
        val options = listOf(
            0 to "Never (Keep files until emptied manually)",
            7 to "7 Days",
            14 to "14 Days",
            30 to "30 Days (Recommended)",
            60 to "60 Days",
            90 to "90 Days"
        )
        AlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = TelegramBlue) },
            title = { Text("Auto-Clean Retention Policy", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Choose how long trashed items remain in Recycle Bin before automatic permanent purge:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    options.forEach { (days, label) ->
                        val isSelected = prefs.trashAutoCleanDays == days
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.trashAutoCleanDays = days
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            SettingsDbManager.saveCategorySettings(
                                                context,
                                                "General",
                                                mapOf("trash_auto_clean_days" to days.toString()),
                                                showToast = false
                                            )
                                        } catch (_: Exception) {}
                                    }
                                    Toast.makeText(context, "Policy updated: $label", Toast.LENGTH_SHORT).show()
                                    showPolicyDialog = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = TelegramBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TelegramBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPolicyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Confirmation: Empty Trash
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Empty Recycle Bin?", fontWeight = FontWeight.Bold) },
            text = {
                Text("All ${trashFiles.size} item(s) (${formatFileSize(totalTrashSize)}) will be permanently deleted from local cache, cloud backup, and Telegram.\n\n⚠️ This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        permanentlyDeleteFiles(trashFiles)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Trash Forever", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation: Single Item Permanent Delete
    if (fileToDeletePermanently != null) {
        val file = fileToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { fileToDeletePermanently = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete Forever?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Permanently delete '${file.fileName}' (${formatFileSize(file.size)})?\n\nThis will remove the file completely from your backup and Telegram channel. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = file
                        fileToDeletePermanently = null
                        permanentlyDeleteFiles(listOf(toDelete))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation: Multi-select Batch Delete
    if (showBatchDeleteDialog) {
        val count = selectedPaths.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete $count Items?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to permanently delete $count selected file(s) from your backup and Telegram?\n\n⚠️ This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteDialog = false
                        val filesToDelete = trashFiles.filter { selectedPaths.contains(it.path) }
                        permanentlyDeleteFiles(filesToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Minimal Modern Trash Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${trashFiles.size} items • ${formatFileSize(totalTrashSize)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val policyLabel = if (prefs.trashAutoCleanDays > 0) "${prefs.trashAutoCleanDays}d Auto-clean" else "Manual retention"
                        Text(
                            text = policyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Policy Selector Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.clickable { showPolicyDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (prefs.trashAutoCleanDays > 0) "${prefs.trashAutoCleanDays}d" else "Off",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                            )
                        }
                    }

                    // Deep Clean / Purge Telegram channel button
                    OutlinedButton(
                        onClick = {
                            val token = prefs.botToken
                            val chat = prefs.chatId
                            if (token.isEmpty() || chat.isEmpty()) {
                                Toast.makeText(context, "Telegram Bot Token & Chat ID required!", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            DynamicProcessTracker.startProcess(
                                title = "Purging Telegram Channel",
                                subtitle = "Inspecting Telegram channel & permissions...",
                                icon = Icons.Default.CleaningServices
                            )
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val serverApi = ServerApi()
                                    val chatInfo = serverApi.getTelegramChatInfo(token, chat)

                                    if (!chatInfo.success) {
                                        withContext(Dispatchers.Main) {
                                            DynamicProcessTracker.finishProcess()
                                            Toast.makeText(context, "Telegram Error: ${chatInfo.errorMessage}", Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    }

                                    if (chatInfo.isBotAdmin && !chatInfo.canDeleteMessages) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "⚠️ Please give the bot 'Delete Messages' permission in channel '${chatInfo.title}'", Toast.LENGTH_LONG).show()
                                        }
                                    }

                                    val targetChatId = if (chatInfo.canonicalId != null) chatInfo.canonicalId.toString() else chat
                                    val trashMsgIds = trashFiles.flatMap { it.getAllMessageIds() }

                                    val (actuallyDeleted, scannedCount, permError) = serverApi.cleanTelegramChannelStorage(
                                        context = context,
                                        botToken = token,
                                        chatId = targetChatId,
                                        explicitMessageIds = trashMsgIds,
                                        purgeAllRemaining = true
                                    ) { scanned, total, count, status ->
                                        val fraction = if (total > 0) (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                                        DynamicProcessTracker.updateProgress(
                                            progress = fraction,
                                            subtitle = "Purging '${chatInfo.title}': $count deleted ($scanned scanned)"
                                        )
                                    }

                                    if (!permError) {
                                        try {
                                            dao.deleteTrashFiles()
                                        } catch (_: Exception) {}
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
                                        Toast.makeText(context, "Deep Clean failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🧹 Purge", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    // Empty Trash
                    if (trashFiles.isNotEmpty()) {
                        Button(
                            onClick = { showEmptyTrashDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Empty", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Search Bar (Shown when items exist in trash)
        if (trashFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                placeholder = { Text("Search trash items...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TelegramBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (trashFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(TelegramEmerald.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(44.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Recycle Bin is Empty", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(modifier = Modifier.height(4.dp))
                    val policyMsg = if (prefs.trashAutoCleanDays > 0) {
                        "Deleted files are automatically cleaned after ${prefs.trashAutoCleanDays} days."
                    } else {
                        "Deleted files stay here until you manually empty the trash."
                    }
                    Text(policyMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { showPolicyDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure Auto-Clean")
                    }
                }
            }
        } else if (filteredTrashFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No matching files found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("Try a different search query", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(filteredTrashFiles, key = { index, file -> "${file.path}_$index" }) { _, file ->
                    val isSelected = selectedPaths.contains(file.path)
                    val fileIcon = remember(file.fileName) { getFileTypeIcon(file.fileName) }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedPaths = if (isSelected) selectedPaths - file.path else selectedPaths + file.path
                                        if (selectedPaths.isEmpty()) isSelectionMode = false
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedPaths = selectedPaths + file.path
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedPaths = if (checked) selectedPaths + file.path else selectedPaths - file.path
                                        if (selectedPaths.isEmpty()) isSelectionMode = false
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = fileIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.fileName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(file.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                // Deletion timestamp and remaining days countdown badge
                                if (file.deletedAt > 0) {
                                    val deletedDate = dateFormat.format(Date(file.deletedAt))
                                    val daysInTrash = ((System.currentTimeMillis() - file.deletedAt) / (24L * 60L * 60L * 1000L)).toInt()
                                    val remainingDays = if (prefs.trashAutoCleanDays > 0) (prefs.trashAutoCleanDays - daysInTrash).coerceAtLeast(0) else -1
                                    val remainText = when {
                                        remainingDays == 0 -> " • ⚠️ Expires today"
                                        remainingDays in 1..3 -> " • ⚠️ ${remainingDays}d left"
                                        remainingDays > 3 -> " • ${remainingDays}d left"
                                        else -> ""
                                    }
                                    Text(
                                        text = "Deleted: $deletedDate$remainText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            remainingDays in 0..3 -> Color(0xFFEF4444)
                                            remainingDays in 4..7 -> Color(0xFFF59E0B)
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                    )
                                }
                            }

                            if (!isSelectionMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { restoreFiles(listOf(file)) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = "Restore File",
                                            tint = TelegramEmerald
                                        )
                                    }

                                    IconButton(
                                        onClick = { fileToDeletePermanently = file }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = "Delete Permanently",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    IconButton(
                                        onClick = { fileForMenu = file }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More Options",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (fileForMenu != null) {
        com.example.telegrambackup.ui.common.FileActionMenuModal(
            file = fileForMenu!!,
            isTrashItem = true,
            onDismiss = { fileForMenu = null },
            onSelectMode = {
                val f = fileForMenu
                if (f != null) {
                    isSelectionMode = true
                    selectedPaths = selectedPaths + f.path
                }
            },
            onFileDeleted = { delFile ->
                selectedPaths = selectedPaths - delFile.path
                fileForMenu = null
            },
            onFileRestored = { resFile ->
                selectedPaths = selectedPaths - resFile.path
                fileForMenu = null
            }
        )
    }
}

/**
 * Returns a contextual icon for the given filename extension
 */
private fun getFileTypeIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov", "webm", "3gp" -> Icons.Default.Videocam
        "mp3", "m4a", "flac", "wav", "ogg", "aac" -> Icons.Default.AudioFile
        "pdf", "doc", "docx", "txt", "xlsx", "csv", "ppt", "pptx" -> Icons.Default.Description
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Folder
        "apk" -> Icons.Default.Android
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
