package com.example.telegrambackup.ui.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.common.*
import com.example.telegrambackup.ui.explorer.AdvancedMediaViewerDialog
import com.example.telegrambackup.ui.explorer.ShareLinkModal
import com.example.telegrambackup.ui.settings.FreeUpSpaceDialog
import com.example.telegrambackup.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * =====================================================================================
 * TIER-1 HIGH PERFORMANCE CLOUD DRIVE EXPLORER
 * =====================================================================================
 * Smooth, glitch-free personal cloud storage with instant local DB Flow rendering,
 * unified multi-format media streaming, recursive folder navigation, and cloud sync.
 * =====================================================================================
 */

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Suppress("DEPRECATION")
private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback?) {
    try {
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) { }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(25L)
        }
    } catch (_: Exception) { }
}

private fun matchesDriveCategory(file: UploadedFile, cat: String): Boolean {
    if (cat == "ALL") return true
    val ext = file.fileName.substringAfterLast('.', "").lowercase()
    return when (cat) {
        "PHOTOS" -> ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg")
        "VIDEOS" -> ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv")
        "AUDIO" -> ext in setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")
        "DOCUMENTS" -> ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
        "ARCHIVES" -> ext in setOf("zip", "rar", "7z", "tar", "gz")
        else -> true
    }
}

enum class DriveSortOption { NAME, SIZE, DATE }

data class DriveStorageBreakdown(
    val count: Int = 0,
    val totalBytes: Long = 0L,
    val photosBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val docsBytes: Long = 0L
)

private fun normalizeDrivePath(path: String): String {
    var clean = path.replace("\\", "/").trim()
    if (clean.contains("storage/emulated/0")) {
        clean = clean.substringAfter("storage/emulated/0").removePrefix("/")
    } else if (clean.matches(Regex("^[A-Za-z]:[/\\\\].*"))) {
        clean = clean.substring(2).removePrefix("/")
    } else if (clean.startsWith("primary:")) {
        clean = clean.removePrefix("primary:").removePrefix("/")
    } else if (clean.startsWith("raw:")) {
        clean = clean.removePrefix("raw:").removePrefix("/")
    }
    clean = clean.trimEnd('/')
    return if (clean.isEmpty()) "Internal Storage" else if (clean.startsWith("Internal Storage")) clean else "Internal Storage/$clean"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveExplorerScreen(
    externalSearchQuery: String = "",
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val haptic = LocalHapticFeedback.current

    // Directory navigation state
    var currentDriveDirectoryPath by remember { mutableStateOf("Internal Storage") }
    val driveFolderPathHistory = remember { mutableStateListOf("Internal Storage") }

    var driveSortOption by remember { mutableStateOf(DriveSortOption.DATE) }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedSizeFilter by remember { mutableStateOf(SizeFilter.ANY) }
    var gridViewMode by remember { mutableStateOf(GridViewMode.COMFORTABLE) }
    var showFreeUpSpaceDialog by remember { mutableStateOf(false) }

    val selectedDriveItems = remember { mutableStateListOf<UploadedFile>() }
    var isDriveSelectionActive by remember { mutableStateOf(false) }

    var itemOptionsFile by remember { mutableStateOf<UploadedFile?>(null) }
    var showDetailedMetadataDialog by remember { mutableStateOf(false) }

    var showCreateFolderModal by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    var showCreateNoteModal by remember { mutableStateOf(false) }
    var noteTitleInput by remember { mutableStateOf("") }
    var noteContentInput by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputName by remember { mutableStateOf("") }

    var showCreateActionSheet by remember { mutableStateOf(false) }
    var activeAudioPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeVideoPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeDriveMediaFile by remember { mutableStateOf<UploadedFile?>(null) }
    var shareModalFile by remember { mutableStateOf<UploadedFile?>(null) }

    // 1. Reactive DB Flow: 0ms Instant Local UI Rendering
    val db = remember { AppDatabase.getDatabase(context) }
    val driveFilesFlow = remember(db) { db.uploadedFileDao().getDriveFilesFlow() }
    val allDriveUploadedFiles by driveFilesFlow.collectAsState(initial = emptyList())

    // 2. Background Cloud Delta Sync
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                SettingsDbManager.restoreCloudFileIndexToRoom(context)
            } catch (_: Exception) {}
        }
    }

    // Free Up Space Dialog
    if (showFreeUpSpaceDialog) {
        FreeUpSpaceDialog(
            onDismiss = {
                showFreeUpSpaceDialog = false
            }
        )
    }

    // Share Modal
    if (shareModalFile != null) {
        ShareLinkModal(
            file = shareModalFile!!,
            onDismiss = { shareModalFile = null }
        )
    }

    // Audio Player Sheet
    if (activeAudioPlayerItem != null) {
        com.example.telegrambackup.ui.player.ModernAudioPlayerSheet(
            filePathOrUrl = activeAudioPlayerItem!!.first,
            fileName = activeAudioPlayerItem!!.second,
            onDismiss = { activeAudioPlayerItem = null }
        )
    }

    // Video Player Dialog
    if (activeVideoPlayerItem != null) {
        com.example.telegrambackup.ui.player.ModernVideoPlayerDialog(
            filePathOrUrl = activeVideoPlayerItem!!.first,
            fileName = activeVideoPlayerItem!!.second,
            onDismiss = { activeVideoPlayerItem = null }
        )
    }

    // File Upload Launchers
    val personalFileUploadLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "⚡ Uploading ${uris.size} files in 4x parallel streams...", Toast.LENGTH_SHORT).show()
            DriveUploadManager.enqueuePersonalFiles(context, uris, currentDriveDirectoryPath) {}
        }
    }

    val uploadFolderTreeLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri != null) {
            Toast.makeText(context, "📂 Uploading folder tree in 4x parallel streams...", Toast.LENGTH_SHORT).show()
            DriveUploadManager.enqueueFolderTree(context, treeUri, currentDriveDirectoryPath) {}
        }
    }

    // Back Handler
    BackHandler(enabled = showCreateActionSheet || activeDriveMediaFile != null || itemOptionsFile != null || selectedDriveItems.isNotEmpty() || showCreateFolderModal || showCreateNoteModal || showRenameDialog || driveFolderPathHistory.size > 1) {
        if (showCreateActionSheet) {
            showCreateActionSheet = false
        } else if (activeDriveMediaFile != null) {
            activeDriveMediaFile = null
        } else if (itemOptionsFile != null) {
            itemOptionsFile = null
            showDetailedMetadataDialog = false
        } else if (showCreateFolderModal) {
            showCreateFolderModal = false
            newFolderNameInput = ""
        } else if (showCreateNoteModal) {
            showCreateNoteModal = false
            noteTitleInput = ""
            noteContentInput = ""
        } else if (showRenameDialog) {
            showRenameDialog = false
            renameInputName = ""
        } else if (selectedDriveItems.isNotEmpty()) {
            selectedDriveItems.clear()
            isDriveSelectionActive = false
        } else if (driveFolderPathHistory.size > 1) {
            driveFolderPathHistory.removeAt(driveFolderPathHistory.lastIndex)
            currentDriveDirectoryPath = driveFolderPathHistory.last()
        }
    }

    // Current Folder Hierarchy computation
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

    val isSearching = externalSearchQuery.trim().isNotEmpty()

    // 1-Pass Storage Breakdown calculation
    val storageBreakdown = remember(allDriveUploadedFiles) {
        var count = 0
        var total = 0L
        var p = 0L
        var v = 0L
        var a = 0L
        var d = 0L
        for (file in allDriveUploadedFiles) {
            if (file.telegramFileId != "DIR_MARKER") {
                count++
                total += file.size
                val ext = file.fileName.substringAfterLast('.', "").lowercase()
                when {
                    ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") -> p += file.size
                    ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv") -> v += file.size
                    ext in setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus") -> a += file.size
                    ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> d += file.size
                }
            }
        }
        DriveStorageBreakdown(count, total, p, v, a, d)
    }

    // Active Display Items with Filter & Sorting
    val activeDisplayDriveItems = remember(allDriveUploadedFiles, currentFolderItems, externalSearchQuery, driveSortOption, selectedCategory, selectedSizeFilter) {
        val q = externalSearchQuery.trim()
        var list = if (q.isNotEmpty()) {
            allDriveUploadedFiles.filter { it.telegramFileId != "DIR_MARKER" && (it.fileName.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true)) }
        } else {
            currentFolderItems
        }
        if (selectedCategory != "ALL") {
            list = list.filter { it.telegramFileId == "DIR_MARKER" || matchesDriveCategory(it, selectedCategory) }
        }
        if (selectedSizeFilter != SizeFilter.ANY) {
            list = list.filter { it.telegramFileId == "DIR_MARKER" || (it.size >= selectedSizeFilter.minBytes && it.size <= selectedSizeFilter.maxBytes) }
        }
        list = when (driveSortOption) {
            DriveSortOption.NAME -> list.sortedWith(compareBy({ it.telegramFileId != "DIR_MARKER" }, { it.fileName.lowercase() }))
            DriveSortOption.SIZE -> list.sortedWith(compareBy({ it.telegramFileId != "DIR_MARKER" }, { -it.size }))
            DriveSortOption.DATE -> list.sortedWith(compareBy({ it.telegramFileId != "DIR_MARKER" }, { -it.timestamp }))
        }
        list
    }

    val activeDisplayFilesOnly = remember(activeDisplayDriveItems) {
        activeDisplayDriveItems.filter { it.telegramFileId != "DIR_MARKER" }
    }

    // Media Viewer Dialog
    if (activeDriveMediaFile != null) {
        val initialIdx = activeDisplayFilesOnly.indexOfFirst { it.path == activeDriveMediaFile?.path }.coerceAtLeast(0)
        AdvancedMediaViewerDialog(
            initialIndex = initialIdx,
            filesList = if (activeDisplayFilesOnly.isNotEmpty()) activeDisplayFilesOnly else listOf(activeDriveMediaFile!!),
            onDismiss = { activeDriveMediaFile = null },
            onDeleteRequested = { fileToDelete ->
                scope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(context).uploadedFileDao()
                    dao.softDelete(fileToDelete.path, fileToDelete.telegramFileId, System.currentTimeMillis())
                    val userEmail = AppPrefs(context).userEmail
                    if (userEmail.isNotEmpty() && fileToDelete.telegramFileId.isNotEmpty()) {
                        try { ServerApi().deleteFile(AppPrefs(context).serverUrl, userEmail, fileToDelete.telegramFileId, fileToDelete.path) } catch (_: Exception) {}
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Moved ${fileToDelete.fileName} to Trash Bin", Toast.LENGTH_SHORT).show()
                        activeDriveMediaFile = null
                    }
                }
            },
            onInfoRequested = { fileInfo ->
                itemOptionsFile = fileInfo
                showDetailedMetadataDialog = true
            }
        )
    }

    // CREATE NOTE DIALOG
    if (showCreateNoteModal) {
        AlertDialog(
            onDismissRequest = { showCreateNoteModal = false; noteTitleInput = ""; noteContentInput = "" },
            title = { Text("📝 Create Personal Note", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteTitleInput,
                        onValueChange = { noteTitleInput = it },
                        label = { Text("Note Title (e.g. Passwords.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteContentInput,
                        onValueChange = { noteContentInput = it },
                        label = { Text("Note Content") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rawTitle = noteTitleInput.trim()
                        if (rawTitle.isNotEmpty()) {
                            val fileName = if (rawTitle.contains(".")) rawTitle else "$rawTitle.txt"
                            val noteDir = File(context.cacheDir, "drive_notes")
                            if (!noteDir.exists()) noteDir.mkdirs()

                            val noteFile = File(noteDir, fileName)
                            try {
                                java.io.FileOutputStream(noteFile).use { fos ->
                                    fos.write(noteContentInput.toByteArray())
                                }
                            } catch (_: Exception) {}

                            val targetPath = "$currentDriveDirectoryPath/$fileName"
                            val noteEntity = UploadedFile(
                                targetPath,
                                fileName,
                                "NOTE_MARKER_${System.currentTimeMillis()}",
                                System.currentTimeMillis(),
                                noteFile.length(),
                                0L
                            ).apply {
                                isDriveUpload = true
                            }

                            scope.launch(Dispatchers.IO) {
                                AppDatabase.getDatabase(context).uploadedFileDao().insert(noteEntity)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved Note '$fileName' to Cloud Drive!", Toast.LENGTH_SHORT).show()
                                    showCreateNoteModal = false
                                    noteTitleInput = ""
                                    noteContentInput = ""
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) { Text("Save Note") }
            },
            dismissButton = { TextButton(onClick = { showCreateNoteModal = false; noteTitleInput = ""; noteContentInput = "" }) { Text("Cancel") } }
        )
    }

    // CREATE FOLDER DIALOG
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

    // RENAME DIALOG
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
                                val dbDao = AppDatabase.getDatabase(context).uploadedFileDao()
                                val oldPath = targetItem.path
                                val parent = File(oldPath).parent?.replace("\\", "/") ?: currentDriveDirectoryPath
                                val newPath = "$parent/$newName"

                                if (isFolder) {
                                    val allFiles = dbDao.getDriveFiles()
                                    val children = allFiles.filter { it.path.startsWith("$oldPath/") }
                                    children.forEach { child ->
                                        val newChildPath = child.path.replaceFirst(oldPath, newPath)
                                        dbDao.deleteByPath(child.path)
                                        dbDao.insert(child.apply { path = newChildPath; isDriveUpload = true })
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
                                dbDao.deleteByPath(oldPath)
                                dbDao.insert(updated)

                                val userEmail = AppPrefs(context).userEmail
                                if (userEmail.isNotEmpty()) {
                                    try {
                                        ServerApi().deleteFile(AppPrefs(context).serverUrl, userEmail, targetItem.telegramFileId, oldPath)
                                        ServerApi().indexFile(
                                            AppPrefs(context).serverUrl,
                                            userEmail,
                                            newName,
                                            targetItem.telegramFileId,
                                            targetItem.size,
                                            newPath,
                                            targetItem.timestamp,
                                            targetItem.messageId,
                                            true
                                        )
                                    } catch (_: Exception) {}
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                                    showRenameDialog = false
                                    selectedDriveItems.clear()
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

    // ITEM OPTIONS MODAL (MATERIAL 3 UNIFIED ACTIONS MODAL)
    if (itemOptionsFile != null) {
        FileActionMenuModal(
            file = itemOptionsFile!!,
            onDismiss = { itemOptionsFile = null },
            onFileRenamed = { _, _, _ ->
                selectedDriveItems.clear()
                itemOptionsFile = null
            },
            onFileDeleted = { delFile ->
                selectedDriveItems.removeAll { it.path == delFile.path }
                itemOptionsFile = null
            }
        )
    }

    // Universal Item Click Handler
    fun handleDriveItemClick(driveItem: UploadedFile) {
        val isDir = driveItem.telegramFileId == "DIR_MARKER"
        if (isDriveSelectionActive) {
            val isSelected = selectedDriveItems.any { it.path == driveItem.path }
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
                val localFile = File(driveItem.path)
                val hasLocal = localFile.exists() && localFile.length() > 0

                when {
                    FileViewerHelper.isAudioFile(driveItem.fileName) -> {
                        activeAudioPlayerItem = Pair(if (hasLocal) driveItem.path else driveItem.telegramFileId, driveItem.fileName)
                    }
                    FileViewerHelper.isVideoFile(driveItem.fileName) -> {
                        activeVideoPlayerItem = Pair(if (hasLocal) driveItem.path else driveItem.telegramFileId, driveItem.fileName)
                    }
                    FileViewerHelper.isImageFile(driveItem.fileName) || FileViewerHelper.isPdfFile(driveItem.fileName) -> {
                        activeDriveMediaFile = driveItem
                    }
                    else -> {
                        FileViewerHelper.openFile(context, driveItem.path, driveItem.fileName, driveItem.telegramFileId)
                    }
                }
            }
        }
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
                                IconButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        driveFolderPathHistory.removeAt(driveFolderPathHistory.size - 1)
                                        currentDriveDirectoryPath = driveFolderPathHistory.last()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        onMenuClick()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu", tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                }
                            }

                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))

                            // Interactive Breadcrumbs Row
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pathSegments = currentDriveDirectoryPath.split("/").filter { it.isNotBlank() }
                                var pathAccumulator = ""

                                pathSegments.forEachIndexed { sIdx, segment ->
                                    pathAccumulator = if (sIdx == 0) segment else "$pathAccumulator/$segment"
                                    val targetLevelPath = pathAccumulator

                                    val displayName = when {
                                        segment == "Internal Storage" -> "Cloud Drive"
                                        segment == "Private Safe Vault" -> "🔒 Safe Vault"
                                        else -> segment
                                    }

                                    Text(
                                        text = displayName,
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

                        // Private Safe Vault Button
                        val isVaultActive = currentDriveDirectoryPath == "Internal Storage/Private Safe Vault"
                        IconButton(
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                if (isVaultActive) {
                                    currentDriveDirectoryPath = "Internal Storage"
                                    driveFolderPathHistory.clear()
                                    driveFolderPathHistory.add("Internal Storage")
                                } else {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    val isBiometricOn = prefs.isBiometricEnabled
                                    if (activity != null && isBiometricOn) {
                                        BiometricHelper.showBiometricPrompt(
                                            activity = activity,
                                            title = "Unlock Private Safe Vault",
                                            subtitle = "Verify fingerprint or face to access encrypted safe folder",
                                            onSuccess = {
                                                currentDriveDirectoryPath = "Internal Storage/Private Safe Vault"
                                                if (!driveFolderPathHistory.contains("Internal Storage/Private Safe Vault")) {
                                                    driveFolderPathHistory.add("Internal Storage/Private Safe Vault")
                                                }
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        currentDriveDirectoryPath = "Internal Storage/Private Safe Vault"
                                        if (!driveFolderPathHistory.contains("Internal Storage/Private Safe Vault")) {
                                            driveFolderPathHistory.add("Internal Storage/Private Safe Vault")
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isVaultActive) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Private Safe Vault",
                                tint = if (isVaultActive) TelegramEmerald else Color(0xFF8B5CF6)
                            )
                        }
                    }
                }
            }

            // Interactive Root Storage Card with Category Breakdown
            if (currentDriveDirectoryPath == "Internal Storage" && !isSearching) {
                Spacer(modifier = Modifier.height(10.dp))
                StorageStatsCard(
                    totalFilesCount = storageBreakdown.count,
                    totalStorageBytes = storageBreakdown.totalBytes,
                    photosBytes = storageBreakdown.photosBytes,
                    videosBytes = storageBreakdown.videosBytes,
                    audioBytes = storageBreakdown.audioBytes,
                    docsBytes = storageBreakdown.docsBytes,
                    onFreeUpSpaceClick = { showFreeUpSpaceDialog = true },
                    onCategoryClick = { selectedCategory = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Smart Multi-Facet Filter Bar
            SmartFilterBar(
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                selectedSizeFilter = selectedSizeFilter,
                onSelectSizeFilter = { selectedSizeFilter = it },
                gridViewMode = gridViewMode,
                onChangeGridViewMode = { gridViewMode = it },
                totalResultsCount = activeDisplayDriveItems.size
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (activeDisplayDriveItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (isSearching) "No Files Matching '$externalSearchQuery'" else "This Folder is Empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tap '+ New' to upload files or create folders in this cloud location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedCategory != "ALL" || selectedSizeFilter != SizeFilter.ANY) {
                                Button(
                                    onClick = { selectedCategory = "ALL"; selectedSizeFilter = SizeFilter.ANY },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                            OutlinedButton(onClick = { personalFileUploadLauncher.launch("*/*") }) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Files")
                            }
                        }
                    }
                }
            } else {
                if (gridViewMode != GridViewMode.LIST) {
                    val cols = gridViewMode.columns
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(activeDisplayDriveItems, key = { _, item -> "${item.path}_${item.telegramFileId}" }) { _, driveItem ->
                            val isDir = driveItem.telegramFileId == "DIR_MARKER"
                            val isSelected = selectedDriveItems.any { it.path == driveItem.path }

                            GridDriveItemCard(
                                item = driveItem,
                                isDir = isDir,
                                isSelected = isSelected,
                                isBigGrid = gridViewMode == GridViewMode.LARGE,
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    handleDriveItemClick(driveItem)
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
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(activeDisplayDriveItems, key = { _, item -> "${item.path}_${item.telegramFileId}" }) { _, driveItem ->
                            val isDir = driveItem.telegramFileId == "DIR_MARKER"
                            val isSelected = selectedDriveItems.any { it.path == driveItem.path }

                            ListDriveItemCard(
                                item = driveItem,
                                isDir = isDir,
                                isSelected = isSelected,
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    handleDriveItemClick(driveItem)
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

        // MATERIAL 3 MINIMAL FLOATING ACTION BUTTON (+) & ACTION SHEET
        AnimatedVisibility(
            visible = !isDriveSelectionActive && selectedDriveItems.isEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    triggerHapticFeedback(context, haptic)
                    showCreateActionSheet = true
                },
                containerColor = TelegramBlue,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create or Upload", modifier = Modifier.size(28.dp))
            }
        }

        if (showCreateActionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCreateActionSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Create new",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        DriveActionItem(
                            label = "Folder",
                            icon = Icons.Default.CreateNewFolder,
                            tint = TelegramBlue,
                            onClick = {
                                showCreateActionSheet = false
                                showCreateFolderModal = true
                            }
                        )
                        DriveActionItem(
                            label = "Upload",
                            icon = Icons.Default.FileUpload,
                            tint = TelegramEmerald,
                            onClick = {
                                showCreateActionSheet = false
                                personalFileUploadLauncher.launch("*/*")
                            }
                        )
                        DriveActionItem(
                            label = "Folder Upload",
                            icon = Icons.Default.FolderZip,
                            tint = Color(0xFFF59E0B),
                            onClick = {
                                showCreateActionSheet = false
                                uploadFolderTreeLauncher.launch(null)
                            }
                        )
                        DriveActionItem(
                            label = "Note",
                            icon = Icons.AutoMirrored.Filled.NoteAdd,
                            tint = Color(0xFF8B5CF6),
                            onClick = {
                                showCreateActionSheet = false
                                showCreateNoteModal = true
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // FLOATING GLASS BULK ACTION BAR
        FloatingBulkActionBar(
            selectedCount = selectedDriveItems.size,
            totalCount = activeDisplayDriveItems.size,
            onSelectAllToggle = {
                if (selectedDriveItems.size == activeDisplayDriveItems.size) {
                    selectedDriveItems.clear()
                    isDriveSelectionActive = false
                } else {
                    selectedDriveItems.clear()
                    selectedDriveItems.addAll(activeDisplayDriveItems)
                    isDriveSelectionActive = true
                }
            },
            onShare = {
                val first = selectedDriveItems.firstOrNull { it.telegramFileId != "DIR_MARKER" }
                if (first != null) {
                    shareModalFile = first
                } else {
                    Toast.makeText(context, "Select a file to share link", Toast.LENGTH_SHORT).show()
                }
            },
            onDownload = {
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
                    }
                    withContext(Dispatchers.Main) {
                        selectedDriveItems.clear()
                        isDriveSelectionActive = false
                    }
                }
            },
            onFreeUpSpace = {
                showFreeUpSpaceDialog = true
            },
            onTrash = {
                val count = selectedDriveItems.size
                val itemsToDelete = selectedDriveItems.toList()
                scope.launch(Dispatchers.IO) {
                    try {
                        val dao = AppDatabase.getDatabase(context).uploadedFileDao()
                        val now = System.currentTimeMillis()
                        val userEmail = AppPrefs(context).userEmail
                        val serverUrl = AppPrefs(context).serverUrl
                        val serverApi = ServerApi()

                        itemsToDelete.forEach { item ->
                            if (item.telegramFileId == "DIR_MARKER") {
                                val children = dao.getDriveFiles().filter { it.path.startsWith("${item.path}/") }
                                children.forEach { child ->
                                    dao.softDelete(child.path, child.telegramFileId, now)
                                    if (userEmail.isNotEmpty() && child.telegramFileId.isNotEmpty()) {
                                        try { serverApi.deleteFile(serverUrl, userEmail, child.telegramFileId, child.path) } catch (_: Exception) {}
                                    }
                                }
                            }
                            dao.softDelete(item.path, item.telegramFileId, now)
                            if (userEmail.isNotEmpty() && item.telegramFileId.isNotEmpty()) {
                                try { serverApi.deleteFile(serverUrl, userEmail, item.telegramFileId, item.path) } catch (_: Exception) {}
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Moved $count items to Trash Bin", Toast.LENGTH_SHORT).show()
                            selectedDriveItems.clear()
                            isDriveSelectionActive = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = {
                selectedDriveItems.clear()
                isDriveSelectionActive = false
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * WINDOWS 11 FLUENT STYLE DRIVE GRID CARD
 * Uses universal Windows 11 Blue File Icons & Folder Icons with 100% visible, guaranteed file names!
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isSelected) BorderStroke(2.dp, TelegramBlue) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isBigGrid) 175.dp else 145.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP ICON DISPLAY AREA (65% HEIGHT) - Clean Icon Only / Smart Image Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isDir) {
                    OsFolderIcon(size = if (isBigGrid) 72.dp else 56.dp)
                } else {
                    FileThumbnailView(
                        fileName = item.fileName,
                        filePath = item.path,
                        telegramFileId = item.telegramFileId,
                        iconSize = if (isBigGrid) 60.dp else 48.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Selection Checkmark Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // BOTTOM 2-LINE TITLE & METADATA BAR (35% HEIGHT) — 100% VISIBLE FILE NAMES
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
                        Spacer(modifier = Modifier.height(1.dp))
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
    val ext = item.fileName.substringAfterLast('.', "").uppercase()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) TelegramBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(2.dp, TelegramBlue) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(TelegramBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else if (isDir) {
                    OsFolderIcon(size = 44.dp)
                } else {
                    FileThumbnailView(
                        fileName = item.fileName,
                        filePath = item.path,
                        telegramFileId = item.telegramFileId,
                        iconSize = 40.dp,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isDir) "Directory Folder" else "${formatFileSize(item.size)} • ${ext.ifEmpty { "FILE" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onOverflowClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DriveActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
