package com.example.telegrambackup.ui.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Vibrator
import android.widget.Toast
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.DriveUploadManager
import com.example.telegrambackup.utils.SettingsDbManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.telegrambackup.ui.common.OsFolderIcon
import com.example.telegrambackup.ui.common.FolderType
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.explorer.AdvancedMediaViewerDialog
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.ZipBackupDownloader
import com.example.telegrambackup.worker.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * =====================================================================================
 * GOOGLE DRIVE ARCHITECTURE CLOUD EXPLORER
 * =====================================================================================
 * Personal Cloud Storage with rich document previews, 2-line visible titles, multi-file
 * and recursive folder uploads, closed-app background backup sync, and ZIP archiving.
 * =====================================================================================
 */

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups.coerceIn(0, 4)])
}

private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    try {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(35L)
    } catch (_: Exception) { }
}

enum class DriveSortOption { NAME, SIZE, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveExplorerScreen(
    externalSearchQuery: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val haptic = LocalHapticFeedback.current

    var currentDriveDirectoryPath by remember { mutableStateOf("Internal Storage") }
    val driveFolderPathHistory = remember { mutableStateListOf("Internal Storage") }

    var driveSortOption by remember { mutableStateOf(DriveSortOption.DATE) }
    var selectedDriveViewMode by remember { mutableIntStateOf(0) } // 0 = Big Grid, 1 = Small Grid, 2 = List

    var allDriveUploadedFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var isDriveDataLoading by remember { mutableStateOf(true) }

    val selectedDriveItems = remember { mutableStateListOf<UploadedFile>() }
    var isDriveSelectionActive by remember { mutableStateOf(false) }

    var itemOptionsFile by remember { mutableStateOf<UploadedFile?>(null) }
    var showDetailedMetadataDialog by remember { mutableStateOf(false) }

    var showCreateFolderModal by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputName by remember { mutableStateOf("") }

    var showFabSpeedDial by remember { mutableStateOf(false) }
    var activeDriveMediaIndex by remember { mutableIntStateOf(-1) }
    var activeAudioPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (activeAudioPlayerItem != null) {
        com.example.telegrambackup.ui.player.ModernAudioPlayerSheet(
            filePathOrUrl = activeAudioPlayerItem!!.first,
            fileName = activeAudioPlayerItem!!.second,
            onDismiss = { activeAudioPlayerItem = null }
        )
    }

    fun loadDriveFiles() {
        scope.launch {
            isDriveDataLoading = true
            val files = withContext(Dispatchers.IO) {
                try {
                    SettingsDbManager.restoreCloudFileIndexToRoom(context)
                    AppDatabase.getDatabase(context).uploadedFileDao().getDriveFiles()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            allDriveUploadedFiles = files
            isDriveDataLoading = false
        }
    }

    // Launcher for uploading multiple personal files directly into Cloud Drive (4x Parallel Stream Engine)
    val personalFileUploadLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "⚡ Uploading ${uris.size} files in 4x parallel streams...", Toast.LENGTH_SHORT).show()
            DriveUploadManager.enqueuePersonalFiles(context, uris, currentDriveDirectoryPath) {
                loadDriveFiles()
            }
        }
    }

    // Launcher for uploading ENTIRE FOLDER TREES recursively into Cloud Drive (4x Parallel Stream Engine)
    val uploadFolderTreeLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri != null) {
            Toast.makeText(context, "📂 Uploading folder tree in 4x parallel streams...", Toast.LENGTH_SHORT).show()
            DriveUploadManager.enqueueFolderTree(context, treeUri, currentDriveDirectoryPath) {
                loadDriveFiles()
            }
        }
    }

    BackHandler(enabled = showFabSpeedDial || activeDriveMediaIndex != -1 || itemOptionsFile != null || selectedDriveItems.isNotEmpty() || showCreateFolderModal || showRenameDialog || driveFolderPathHistory.size > 1) {
        if (showFabSpeedDial) {
            showFabSpeedDial = false
        } else if (activeDriveMediaIndex != -1) {
            activeDriveMediaIndex = -1
        } else if (itemOptionsFile != null) {
            itemOptionsFile = null
            showDetailedMetadataDialog = false
        } else if (showCreateFolderModal) {
            showCreateFolderModal = false
            newFolderNameInput = ""
        } else if (showRenameDialog) {
            showRenameDialog = false
            renameInputName = ""
        } else if (selectedDriveItems.isNotEmpty()) {
            selectedDriveItems.clear()
            isDriveSelectionActive = false
        } else if (driveFolderPathHistory.size > 1) {
            driveFolderPathHistory.removeAt(driveFolderPathHistory.size - 1)
            currentDriveDirectoryPath = driveFolderPathHistory.last()
        }
    }

    LaunchedEffect(Unit) {
        loadDriveFiles()
    }

    fun normalizeDrivePath(path: String): String {
        var clean = path.replace("\\", "/").trim()
        if (clean.contains("storage/emulated/0")) {
            clean = clean.substringAfter("storage/emulated/0").removePrefix("/")
        } else if (clean.contains(":")) {
            clean = clean.substringAfter(":").removePrefix("/")
        }
        clean = clean.trimEnd('/')
        return if (clean.isEmpty()) "Internal Storage" else if (clean.startsWith("Internal Storage")) clean else "Internal Storage/$clean"
    }

    val currentFolderItems = remember(allDriveUploadedFiles, currentDriveDirectoryPath) {
        val directoriesMap = mutableMapOf<String, MutableList<UploadedFile>>()
        val directFilesList = mutableListOf<UploadedFile>()

        allDriveUploadedFiles.forEach { file ->
            val normPath = normalizeDrivePath(file.path)

            if (normPath == currentDriveDirectoryPath) {
                if (file.telegramFileId != "DIR_MARKER") {
                    directFilesList.add(file)
                }
            } else if (normPath.startsWith("$currentDriveDirectoryPath/")) {
                val relative = normPath.removePrefix("$currentDriveDirectoryPath/")
                val parts = relative.split("/")
                if (parts.size == 1 && file.telegramFileId != "DIR_MARKER") {
                    directFilesList.add(file)
                } else if (parts.size >= 1) {
                    val subDirName = parts.first()
                    val fullSubPath = "$currentDriveDirectoryPath/$subDirName"
                    directoriesMap.getOrPut(fullSubPath) { mutableListOf() }.add(file)
                }
            }
        }

        allDriveUploadedFiles.filter { it.telegramFileId == "DIR_MARKER" }.forEach { dirMarker ->
            val normDir = normalizeDrivePath(dirMarker.path)
            val parentDir = File(normDir).parent?.replace("\\", "/") ?: "Internal Storage"
            if (parentDir == currentDriveDirectoryPath) {
                directoriesMap.putIfAbsent(normDir, mutableListOf())
            }
        }

        val resultList = mutableListOf<UploadedFile>()

        directoriesMap.forEach { (dirPath, filesInDir) ->
            val folderName = File(dirPath).name
            val dirVirtualEntity = UploadedFile(
                dirPath,
                folderName,
                "DIR_MARKER",
                if (filesInDir.isNotEmpty()) filesInDir.maxOf { it.timestamp } else System.currentTimeMillis(),
                filesInDir.sumOf { it.size },
                0L
            ).apply {
                isDriveUpload = true
            }
            resultList.add(dirVirtualEntity)
        }

        resultList.addAll(directFilesList)
        resultList
    }

    val activeDisplayDriveItems = remember(currentFolderItems, externalSearchQuery, driveSortOption) {
        var list = currentFolderItems.filter { externalSearchQuery.isEmpty() || it.fileName.contains(externalSearchQuery, true) }
        list = when (driveSortOption) {
            DriveSortOption.NAME -> list.sortedBy { it.fileName.lowercase() }
            DriveSortOption.SIZE -> list.sortedByDescending { it.size }
            DriveSortOption.DATE -> list.sortedByDescending { it.timestamp }
        }
        list
    }

    val activeDisplayFilesOnly = remember(activeDisplayDriveItems) {
        activeDisplayDriveItems.filter { it.telegramFileId != "DIR_MARKER" }
    }

    // CREATE NEW FOLDER DIALOG
    if (showCreateFolderModal) {
        AlertDialog(
            onDismissRequest = { showCreateFolderModal = false; newFolderNameInput = "" },
            title = { Text("New Folder", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderNameInput.trim()
                        if (name.isNotEmpty()) {
                            val newFolderPath = "$currentDriveDirectoryPath/$name"
                            val dirMarker = UploadedFile(
                                newFolderPath,
                                name,
                                "DIR_MARKER",
                                System.currentTimeMillis(),
                                0L,
                                0L
                            ).apply {
                                isDriveUpload = true
                            }
                            scope.launch(Dispatchers.IO) {
                                AppDatabase.getDatabase(context).uploadedFileDao().insert(dirMarker)
                                val userEmail = AppPrefs(context).userEmail
                                if (userEmail.isNotEmpty()) {
                                    try {
                                        ServerApi().indexFile(
                                            AppPrefs(context).serverUrl,
                                            userEmail,
                                            name,
                                            "DIR_MARKER",
                                            0L,
                                            newFolderPath,
                                            System.currentTimeMillis(),
                                            0L,
                                            true
                                        )
                                    } catch (_: Exception) {}
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Created Folder '$name'", Toast.LENGTH_SHORT).show()
                                    showCreateFolderModal = false
                                    newFolderNameInput = ""
                                    loadDriveFiles()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderModal = false; newFolderNameInput = "" }) { Text("Cancel") } }
        )
    }

    // RENAME ITEM DIALOG
    if (showRenameDialog && selectedDriveItems.isNotEmpty()) {
        val targetItem = selectedDriveItems.first()
        val isFolder = targetItem.telegramFileId == "DIR_MARKER"

        AlertDialog(
            onDismissRequest = { showRenameDialog = false; renameInputName = "" },
            title = { Text(if (isFolder) "Rename Folder" else "Rename File", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputName,
                    onValueChange = { renameInputName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInputName.trim()
                        if (newName.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                val oldPath = targetItem.path
                                val parent = File(oldPath).parent?.replace("\\", "/") ?: currentDriveDirectoryPath
                                val newPath = "$parent/$newName"

                                if (isFolder) {
                                    val allFiles = db.uploadedFileDao().getDriveFiles()
                                    val children = allFiles.filter { it.path.startsWith("$oldPath/") }
                                    children.forEach { child ->
                                        val newChildPath = child.path.replaceFirst(oldPath, newPath)
                                        db.uploadedFileDao().deleteByPath(child.path)
                                        db.uploadedFileDao().insert(child.apply { path = newChildPath; isDriveUpload = true })
                                    }
                                }

                                val updated = UploadedFile(
                                    newPath,
                                    newName,
                                    targetItem.telegramFileId,
                                    targetItem.timestamp,
                                    targetItem.size,
                                    targetItem.messageId
                                ).apply {
                                    isDriveUpload = true
                                }
                                db.uploadedFileDao().deleteByPath(oldPath)
                                db.uploadedFileDao().insert(updated)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                                    showRenameDialog = false
                                    selectedDriveItems.clear()
                                    loadDriveFiles()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false; renameInputName = "" }) { Text("Cancel") } }
        )
    }

    // ITEM OPTIONS MODAL
    if (itemOptionsFile != null) {
        val file = itemOptionsFile!!
        val isFolder = file.telegramFileId == "DIR_MARKER"

        AlertDialog(
            onDismissRequest = { itemOptionsFile = null; showDetailedMetadataDialog = false },
            title = { Text(file.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (showDetailedMetadataDialog) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Cloud Item Properties", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Path: ${file.path}", style = MaterialTheme.typography.labelSmall)
                                Text("Type: ${if (isFolder) "Directory Folder" else "Personal Cloud File"}", style = MaterialTheme.typography.labelSmall)
                                if (!isFolder) Text("File Size: ${formatFileSize(file.size)}", style = MaterialTheme.typography.labelSmall)
                                Text("Status: Dedicated Personal Cloud Storage", style = MaterialTheme.typography.labelSmall, color = TelegramEmerald)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { showDetailedMetadataDialog = !showDetailedMetadataDialog }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("View Properties & Info", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()

                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            triggerHapticFeedback(context, haptic)
                            scope.launch(Dispatchers.IO) {
                                if (isFolder) {
                                    val filesInFolder = AppDatabase.getDatabase(context).uploadedFileDao().getDriveFiles().filter { it.path.startsWith("${file.path}/") && it.telegramFileId != "DIR_MARKER" }
                                    if (filesInFolder.isNotEmpty()) {
                                        ZipBackupDownloader.downloadFilesAsZip(context, filesInFolder, file.fileName)
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Folder '${file.fileName}' has no files to download", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    ZipBackupDownloader.downloadFilesAsZip(context, listOf(file), file.fileName)
                                }
                            }
                            itemOptionsFile = null
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(if (isFolder) "Download Folder as ZIP" else "Download File to Device", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()

                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            triggerHapticFeedback(context, haptic)
                            renameInputName = file.fileName
                            selectedDriveItems.clear()
                            selectedDriveItems.add(file)
                            itemOptionsFile = null
                            showRenameDialog = true
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Rename Item", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()

                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            triggerHapticFeedback(context, haptic)
                            scope.launch(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                val now = System.currentTimeMillis()
                                if (isFolder) {
                                    val filesInFol = db.uploadedFileDao().getDriveFiles().filter { it.path.startsWith("${file.path}/") }
                                    filesInFol.forEach { db.uploadedFileDao().softDelete(it.path, it.telegramFileId, now) }
                                    db.uploadedFileDao().softDelete(file.path, file.telegramFileId, now)
                                } else {
                                    db.uploadedFileDao().softDelete(file.path, file.telegramFileId, now)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Moved to Trash Bin", Toast.LENGTH_SHORT).show()
                                    itemOptionsFile = null
                                    loadDriveFiles()
                                }
                            }
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Move to Trash Bin", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { itemOptionsFile = null; showDetailedMetadataDialog = false }) { Text("Close") } }
        )
    }

    // ADVANCED MEDIA VIEWER FOR DRIVE FILES
    if (activeDriveMediaIndex >= 0 && activeDriveMediaIndex < activeDisplayFilesOnly.size) {
        AdvancedMediaViewerDialog(
            initialIndex = activeDriveMediaIndex,
            filesList = activeDisplayFilesOnly,
            onDismiss = { activeDriveMediaIndex = -1 },
            onDeleteRequested = { fileToDelete ->
                scope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(context).uploadedFileDao().softDelete(fileToDelete.path, fileToDelete.telegramFileId, System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Moved to Trash Bin", Toast.LENGTH_SHORT).show()
                        loadDriveFiles()
                        activeDriveMediaIndex = -1
                    }
                }
            },
            onInfoRequested = { fileInfo -> itemOptionsFile = fileInfo }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(10.dp))

            // INTERACTIVE BREADCRUMB PATH HEADER
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (driveFolderPathHistory.size > 1) {
                                IconButton(onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    driveFolderPathHistory.removeAt(driveFolderPathHistory.size - 1)
                                    currentDriveDirectoryPath = driveFolderPathHistory.last()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TelegramBlue)
                                }
                            }

                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))

                            // Interactive Breadcrumbs Row
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pathSegments = currentDriveDirectoryPath.split("/")
                                var pathAccumulator = ""

                                pathSegments.forEachIndexed { sIdx, segment ->
                                    pathAccumulator = if (sIdx == 0) segment else "$pathAccumulator/$segment"
                                    val targetLevelPath = pathAccumulator

                                    Text(
                                        text = segment,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (sIdx == pathSegments.lastIndex) FontWeight.Bold else FontWeight.Medium,
                                            color = if (sIdx == pathSegments.lastIndex) MaterialTheme.colorScheme.onSurface else TelegramBlue
                                        ),
                                        modifier = Modifier.clickable {
                                            if (targetLevelPath != currentDriveDirectoryPath) {
                                                triggerHapticFeedback(context, haptic)
                                                currentDriveDirectoryPath = targetLevelPath
                                                while (driveFolderPathHistory.size > 1 && driveFolderPathHistory.last() != targetLevelPath) {
                                                    driveFolderPathHistory.removeAt(driveFolderPathHistory.size - 1)
                                                }
                                            }
                                        }
                                    )

                                    if (sIdx < pathSegments.lastIndex) {
                                        Text(" / ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }

                        // View Mode Toggle Button
                        IconButton(
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedDriveViewMode = (selectedDriveViewMode + 1) % 3
                            }
                        ) {
                            Icon(
                                imageVector = when (selectedDriveViewMode) {
                                    0 -> Icons.Default.GridView
                                    1 -> Icons.Default.GridOn
                                    else -> Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = "Toggle View Mode",
                                tint = TelegramBlue
                            )
                        }
                    }
                }
            }

            // Multi-select action bar
            if (isDriveSelectionActive && selectedDriveItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TelegramBlue.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${selectedDriveItems.size} selected",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val filesToDl = mutableListOf<UploadedFile>()
                                        selectedDriveItems.forEach { item ->
                                            if (item.telegramFileId == "DIR_MARKER") {
                                                val children = AppDatabase.getDatabase(context).uploadedFileDao().getDriveFiles().filter { it.path.startsWith("${item.path}/") && it.telegramFileId != "DIR_MARKER" }
                                                filesToDl.addAll(children)
                                            } else {
                                                filesToDl.add(item)
                                            }
                                        }

                                        if (filesToDl.isNotEmpty()) {
                                            ZipBackupDownloader.downloadFilesAsZip(context, filesToDl.distinctBy { it.path }, "Drive_Selection")
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "No files inside selection to download", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        withContext(Dispatchers.Main) {
                                            selectedDriveItems.clear()
                                            isDriveSelectionActive = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download ZIP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            Button(
                                onClick = {
                                    val count = selectedDriveItems.size
                                    val itemsToDelete = selectedDriveItems.toList()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val dao = AppDatabase.getDatabase(context).uploadedFileDao()
                                            val now = System.currentTimeMillis()
                                            itemsToDelete.forEach { item ->
                                                if (item.telegramFileId == "DIR_MARKER") {
                                                    val children = dao.getDriveFiles().filter { it.path.startsWith("${item.path}/") }
                                                    children.forEach { dao.softDelete(it.path, it.telegramFileId, now) }
                                                }
                                                dao.softDelete(item.path, item.telegramFileId, now)
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Moved $count items to Trash Bin", Toast.LENGTH_SHORT).show()
                                                selectedDriveItems.clear()
                                                isDriveSelectionActive = false
                                                loadDriveFiles()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Error deleting items: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            TextButton(
                                onClick = {
                                    selectedDriveItems.clear()
                                    isDriveSelectionActive = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isDriveDataLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TelegramBlue) }
            } else if (activeDisplayDriveItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your Personal Cloud Drive is Empty", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Store private notes, upload custom files or entire folders here. Automatic phone storage backups are kept separately in Dashboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { personalFileUploadLauncher.launch("*/*") }, colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Files")
                            }
                            OutlinedButton(onClick = { uploadFolderTreeLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Folder")
                            }
                        }
                    }
                }
            } else {
                if (selectedDriveViewMode == 0 || selectedDriveViewMode == 1) {
                    val cols = if (selectedDriveViewMode == 0) 2 else 3
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(activeDisplayDriveItems, key = { index, item -> "${item.path}_$index" }) { _, driveItem ->
                            val isDir = driveItem.telegramFileId == "DIR_MARKER"
                            val isSelected = selectedDriveItems.any { it.path == driveItem.path }

                            GridDriveItemCard(
                                item = driveItem,
                                isDir = isDir,
                                isSelected = isSelected,
                                isBigGrid = selectedDriveViewMode == 0,
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    if (isDriveSelectionActive) {
                                        if (isSelected) {
                                            selectedDriveItems.removeAll { it.path == driveItem.path }
                                            if (selectedDriveItems.isEmpty()) isDriveSelectionActive = false
                                        } else {
                                            selectedDriveItems.add(driveItem)
                                        }
                                    } else {
                                        if (isDir) {
                                            currentDriveDirectoryPath = driveItem.path
                                            driveFolderPathHistory.add(driveItem.path)
                                        } else {
                                            if (FileViewerHelper.isAudioFile(driveItem.fileName)) {
                                                activeAudioPlayerItem = Pair(driveItem.path, driveItem.fileName)
                                            } else {
                                                val idx = activeDisplayFilesOnly.indexOfFirst { it.path == driveItem.path }
                                                if (idx != -1) {
                                                    activeDriveMediaIndex = idx
                                                } else {
                                                    FileViewerHelper.openFile(context, driveItem.path, driveItem.fileName)
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    triggerHapticFeedback(context, haptic)
                                    isDriveSelectionActive = true
                                    if (!isSelected) selectedDriveItems.add(driveItem)
                                },
                                onOverflowClick = {
                                    itemOptionsFile = driveItem
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(activeDisplayDriveItems, key = { index, item -> "${item.path}_$index" }) { _, driveItem ->
                            val isDir = driveItem.telegramFileId == "DIR_MARKER"
                            val isSelected = selectedDriveItems.any { it.path == driveItem.path }

                            ListDriveItemCard(
                                item = driveItem,
                                isDir = isDir,
                                isSelected = isSelected,
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    if (isDriveSelectionActive) {
                                        if (isSelected) {
                                            selectedDriveItems.removeAll { it.path == driveItem.path }
                                            if (selectedDriveItems.isEmpty()) isDriveSelectionActive = false
                                        } else {
                                            selectedDriveItems.add(driveItem)
                                        }
                                    } else {
                                        if (isDir) {
                                            currentDriveDirectoryPath = driveItem.path
                                            driveFolderPathHistory.add(driveItem.path)
                                        } else {
                                            if (FileViewerHelper.isAudioFile(driveItem.fileName)) {
                                                activeAudioPlayerItem = Pair(driveItem.path, driveItem.fileName)
                                            } else {
                                                val idx = activeDisplayFilesOnly.indexOfFirst { it.path == driveItem.path }
                                                if (idx != -1) {
                                                    activeDriveMediaIndex = idx
                                                } else {
                                                    FileViewerHelper.openFile(context, driveItem.path, driveItem.fileName)
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    triggerHapticFeedback(context, haptic)
                                    isDriveSelectionActive = true
                                    if (!isSelected) selectedDriveItems.add(driveItem)
                                },
                                onOverflowClick = {
                                    itemOptionsFile = driveItem
                                }
                            )
                        }
                    }
                }
            }
        }

        // GOOGLE DRIVE STYLE "+ NEW" FLOATING ACTION BUTTON (FAB)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(
                    visible = showFabSpeedDial,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            text = { Text("New Folder", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showFabSpeedDial = false
                                showCreateFolderModal = true
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = TelegramBlue
                        )

                        ExtendedFloatingActionButton(
                            text = { Text("Upload Files", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                            onClick = {
                                showFabSpeedDial = false
                                personalFileUploadLauncher.launch("*/*")
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = TelegramBlue
                        )

                        ExtendedFloatingActionButton(
                            text = { Text("Upload Folder", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                            onClick = {
                                showFabSpeedDial = false
                                uploadFolderTreeLauncher.launch(null)
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = TelegramBlue
                        )
                    }
                }

                ExtendedFloatingActionButton(
                    text = { Text(if (showFabSpeedDial) "Close" else "New", fontWeight = FontWeight.Bold) },
                    icon = { Icon(if (showFabSpeedDial) Icons.Default.Close else Icons.Default.Add, contentDescription = "New") },
                    onClick = {
                        triggerHapticFeedback(context, haptic)
                        showFabSpeedDial = !showFabSpeedDial
                    },
                    containerColor = TelegramBlue,
                    contentColor = Color.White
                )
            }
        }
    }
}

/**
 * REDESIGNED GOOGLE DRIVE STYLE GRID CARD
 * Provides rich document previews and guaranteed 2-line visible titles!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridDriveItemCard(
    item: UploadedFile,
    isDir: Boolean,
    isSelected: Boolean,
    isBigGrid: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOverflowClick: () -> Unit
) {
    val nameLower = item.fileName.lowercase()
    val ext = item.fileName.substringAfterLast('.', "").uppercase()
    val isImg = !isDir && (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") || nameLower.endsWith(".gif"))
    val isNote = !isDir && (nameLower.endsWith(".txt") || nameLower.endsWith(".md"))
    val isPdf = !isDir && nameLower.endsWith(".pdf")
    val isVideo = !isDir && (nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi"))
    val isApk = !isDir && nameLower.endsWith(".apk")
    val isZip = !isDir && (nameLower.endsWith(".zip") || nameLower.endsWith(".rar") || nameLower.endsWith(".7z"))
    val localFile = File(item.path)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isSelected) BorderStroke(2.dp, TelegramBlue) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isBigGrid) 180.dp else 150.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP PREVIEW BANNER (70% HEIGHT)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        when {
                            isDir -> TelegramBlue.copy(alpha = 0.12f)
                            isImg && localFile.exists() -> Color.Transparent
                            isPdf -> Color(0xFFFEF2F2)
                            isVideo -> Color(0xFFF3E8FF)
                            isApk -> Color(0xFFECFDF5)
                            isZip -> Color(0xFFFEF3C7)
                            isNote -> Color(0xFFF0F9FF)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDir) {
                    OsFolderIcon(size = if (isBigGrid) 52.dp else 40.dp)
                } else if (isImg && localFile.exists()) {
                    AsyncImage(
                        model = localFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val (bannerIcon, bannerColor, badgeText) = when {
                        isPdf -> Triple(Icons.Default.PictureAsPdf, Color(0xFFEF4444), "PDF")
                        isVideo -> Triple(Icons.Default.Movie, Color(0xFF8B5CF6), "VIDEO")
                        isApk -> Triple(Icons.Default.Android, Color(0xFF10B981), "APK")
                        isZip -> Triple(Icons.Default.FolderZip, Color(0xFFF59E0B), "ZIP")
                        isNote -> Triple(Icons.Default.Description, TelegramBlue, "NOTE")
                        else -> Triple(Icons.AutoMirrored.Filled.InsertDriveFile, MaterialTheme.colorScheme.primary, ext.ifEmpty { "FILE" })
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(imageVector = bannerIcon, contentDescription = null, tint = bannerColor, modifier = Modifier.size(if (isBigGrid) 38.dp else 28.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = bannerColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = bannerColor),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Selection Checkmark Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // BOTTOM 2-LINE TITLE & METADATA BAR (30% HEIGHT) — GUARANTEED VISIBLE FILE NAMES
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDir) "Folder" else formatFileSize(item.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(
                        onClick = onOverflowClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListDriveItemCard(
    item: UploadedFile,
    isDir: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOverflowClick: () -> Unit
) {
    val isNote = item.fileName.endsWith(".txt") || item.fileName.endsWith(".md")
    val ext = item.fileName.substringAfterLast('.', "").uppercase()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(2.dp, TelegramBlue) else null,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(if (isSelected) TelegramBlue else if (isDir) Color.Transparent else if (isNote) Color(0xFF8B5CF6).copy(alpha = 0.15f) else TelegramEmerald.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                } else if (isDir) {
                    OsFolderIcon(size = 38.dp)
                } else {
                    Icon(
                        imageVector = if (isNote) Icons.Default.Description else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = if (isNote) Color(0xFF8B5CF6) else TelegramEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (isDir) "Directory Folder" else if (isNote) "Personal Text Note ($ext)" else "${formatFileSize(item.size)} • $ext", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            IconButton(onClick = onOverflowClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
