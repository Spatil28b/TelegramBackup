package com.example.telegrambackup.ui.main

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.ui.common.OsFolderIcon
import com.example.telegrambackup.ui.common.OsFileIcon
import com.example.telegrambackup.ui.common.FileThumbnailView
import com.example.telegrambackup.ui.common.FolderType
import com.example.telegrambackup.worker.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class LocalFileItem(
    val file: File,
    val name: String,
    val path: String,
    val size: Long,
    val category: String,
    val extension: String
)

data class FolderNode(
    val folder: File,
    val folderName: String,
    val relativePath: String,
    val unbackedFiles: List<LocalFileItem>,
    val subFolders: List<FolderNode>
)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun getCategoryForFile(file: File): String {
    val ext = file.extension.lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg" -> "Images"
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> "Videos"
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> "Documents"
        "apk" -> "APKs"
        "mp3", "m4a", "wav", "flac", "aac", "ogg" -> "Audio & Music"
        "zip", "rar", "7z", "tar", "gz" -> "Archives & Downloads"
        else -> "Other Files"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFilesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedTabState by remember { mutableIntStateOf(0) } // 0 = Category Wise, 1 = Folder Wise
    var isLoading by remember { mutableStateOf(true) }
    var unbackedFilesList by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { mutableStateListOf<String>() }

    var activeAudioPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeVideoPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fileForMenu by remember { mutableStateOf<UploadedFile?>(null) }
    var folderFilesForMenu by remember { mutableStateOf<List<UploadedFile>?>(null) }

    if (activeAudioPlayerItem != null) {
        com.example.telegrambackup.ui.player.ModernAudioPlayerSheet(
            filePathOrUrl = activeAudioPlayerItem!!.first,
            fileName = activeAudioPlayerItem!!.second,
            onDismiss = { activeAudioPlayerItem = null }
        )
    }

    if (activeVideoPlayerItem != null) {
        com.example.telegrambackup.ui.player.ModernVideoPlayerDialog(
            filePathOrUrl = activeVideoPlayerItem!!.first,
            fileName = activeVideoPlayerItem!!.second,
            onDismiss = { activeVideoPlayerItem = null }
        )
    }

    val handleFileClick: (LocalFileItem) -> Unit = { item ->
        if (isSelectionMode) {
            if (selectedPaths.contains(item.path)) selectedPaths.remove(item.path)
            else selectedPaths.add(item.path)
            if (selectedPaths.isEmpty()) isSelectionMode = false
        } else {
            if (FileViewerHelper.isAudioFile(item.name)) {
                activeAudioPlayerItem = Pair(item.path, item.name)
            } else if (FileViewerHelper.isVideoFile(item.name)) {
                activeVideoPlayerItem = Pair(item.path, item.name)
            } else {
                FileViewerHelper.openFile(context, item.path, item.name)
            }
        }
    }

    // Folder navigation state for Folder Wise view
    var currentFolderDir by remember { mutableStateOf<File?>(null) }
    val folderHistory = remember { mutableStateListOf<File>() }

    BackHandler(enabled = isSelectionMode || (selectedTabState == 1 && currentFolderDir != null)) {
        if (isSelectionMode) {
            selectedPaths.clear()
            isSelectionMode = false
        } else if (selectedTabState == 1 && currentFolderDir != null) {
            if (folderHistory.isNotEmpty()) {
                currentFolderDir = folderHistory.removeAt(folderHistory.size - 1)
            } else {
                currentFolderDir = null
            }
        }
    }

    var scanErrorMessage by remember { mutableStateOf<String?>(null) }

    // Scanner logic
    val loadUnbackedFiles = {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            scanErrorMessage = null
            try {
                val db = AppDatabase.getDatabase(context)
                val uploadedPathSet = try {
                    db.uploadedFileDao().getAllUploadedPaths().toHashSet()
                } catch (_: Exception) {
                    db.uploadedFileDao().getAllFiles().map { it.path }.toHashSet()
                }

                val dirsToScan = mutableListOf<File>()
                val rootStorage = Environment.getExternalStorageDirectory()
                if (rootStorage != null && rootStorage.exists()) {
                    dirsToScan.add(rootStorage)
                } else {
                    dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                    dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
                    dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                    dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
                }

                val foundList = mutableListOf<LocalFileItem>()
                val seenPaths = HashSet<String>()
                val MAX_TOTAL_UNBACKED = 2500

                for (dir in dirsToScan) {
                    if (dir.exists() && dir.isDirectory) {
                        try {
                            dir.walkTopDown()
                                .onEnter { subDir ->
                                    try {
                                        val name = subDir.name.lowercase()
                                        val path = subDir.absolutePath
                                        if (name.startsWith(".") || name == "android" || name == "cache" || name == "tmp" ||
                                            name == "thumbnails" || name == ".thumbnails" || name == "node_modules") return@onEnter false
                                        if (path.contains("/Android/data", ignoreCase = true) || path.contains("/Android/obb", ignoreCase = true)) return@onEnter false
                                        subDir.canRead()
                                    } catch (_: Exception) { false }
                                }
                                .onFail { _, _ ->
                                    kotlin.io.OnErrorAction.SKIP
                                }
                                .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
                                .forEach { file ->
                                    val absPath = file.absolutePath
                                    if (foundList.size < MAX_TOTAL_UNBACKED && !uploadedPathSet.contains(absPath) && seenPaths.add(absPath)) {
                                        foundList.add(
                                            LocalFileItem(
                                                file = file,
                                                name = file.name,
                                                path = absPath,
                                                size = file.length(),
                                                category = getCategoryForFile(file),
                                                extension = file.extension.uppercase()
                                            )
                                        )
                                    }
                                }
                        } catch (_: Exception) { }
                    }
                }
                val finalSorted = foundList.distinctBy { it.path }.sortedByDescending { it.file.lastModified() }
                withContext(Dispatchers.Main) {
                    unbackedFilesList = finalSorted
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    scanErrorMessage = "Storage read error: ${e.localizedMessage ?: "Permission or access restricted"}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadUnbackedFiles()
    }

    val triggerBackupForSelected = {
        val pathsToBackup = selectedPaths.toList()
        if (pathsToBackup.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val count = pathsToBackup.size
                // Trigger background worker to scan & upload real files to Telegram Cloud
                try {
                    val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                } catch (_: Exception) { }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "⚡ Started backup for $count selected file(s) to Telegram Cloud!", Toast.LENGTH_LONG).show()
                    isSelectionMode = false
                    selectedPaths.clear()
                    loadUnbackedFiles()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Modern Minimal Control Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unbacked status pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TelegramEmerald.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TelegramEmerald.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(TelegramEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${unbackedFilesList.size} unbacked",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald)
                    )
                }
            }

            // Segmented Tab Switcher (Category vs Folder)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    val catSelected = selectedTabState == 0
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (catSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (catSelected) 2.dp else 0.dp,
                        modifier = Modifier.clickable { selectedTabState = 0 }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (catSelected) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Categories",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (catSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (catSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val folderSelected = selectedTabState == 1
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (folderSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (folderSelected) 2.dp else 0.dp,
                        modifier = Modifier.clickable { selectedTabState = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (folderSelected) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Folders",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (folderSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (folderSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Refresh Button
            IconButton(
                onClick = { loadUnbackedFiles() },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TelegramBlue, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search local files...", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) } }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TelegramBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        )

        // Selection Action Pill Bar
        AnimatedVisibility(visible = isSelectionMode) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TelegramBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedPaths.size} selected",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = triggerBackupForSelected,
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup Now", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            TextButton(
                                onClick = {
                                    selectedPaths.clear()
                                    isSelectionMode = false
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = TelegramBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Scanning local storage for unbacked files...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (scanErrorMessage != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Storage Access Notice", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(scanErrorMessage ?: "Permission or storage read restricted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { loadUnbackedFiles() }, colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)) {
                        Text("Retry Scan")
                    }
                }
            } else if (unbackedFilesList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Everything is Backed Up!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "0 remaining files found on your device. All your photos, videos, and documents are safely synced to Telegram Cloud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                val filteredFiles = if (searchQuery.isBlank()) unbackedFilesList else unbackedFilesList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
                }

                if (selectedTabState == 0) {
                    // CATEGORY WISE VIEW
                    CategoryWiseView(
                        files = filteredFiles,
                        selectedPaths = selectedPaths,
                        isSelectionMode = isSelectionMode,
                        onFileClick = { item ->
                            if (isSelectionMode) {
                                if (selectedPaths.contains(item.path)) selectedPaths.remove(item.path)
                                else selectedPaths.add(item.path)
                                if (selectedPaths.isEmpty()) isSelectionMode = false
                            } else {
                                handleFileClick(item)
                            }
                        },
                        onFileLongClick = { item ->
                            try { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } catch (_: Exception) {}
                            if (!isSelectionMode) {
                                scope.launch(Dispatchers.IO) {
                                    val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                                    val existing = db.uploadedFileDao().getFileByPath(item.path)
                                        ?: db.uploadedFileDao().getFileByName(item.name)
                                    val fileObj = existing ?: UploadedFile(
                                        path = item.path,
                                        fileName = item.name,
                                        size = item.size,
                                        timestamp = item.file.lastModified()
                                    )
                                    withContext(Dispatchers.Main) {
                                        folderFilesForMenu = null
                                        fileForMenu = fileObj
                                    }
                                }
                            } else {
                                if (selectedPaths.contains(item.path)) selectedPaths.remove(item.path)
                                else selectedPaths.add(item.path)
                                if (selectedPaths.isEmpty()) isSelectionMode = false
                            }
                        },
                        onMoreOptions = { item ->
                            fileForMenu = UploadedFile(
                                path = item.path,
                                fileName = item.name,
                                size = item.size,
                                timestamp = item.file.lastModified()
                            )
                        }
                    )
                } else {
                    // FOLDER WISE VIEW (Only shows folders containing unbacked files)
                    FolderWiseView(
                        files = filteredFiles,
                        currentDir = currentFolderDir,
                        onNavigateToSubfolder = { folder ->
                            folderHistory.add(currentFolderDir ?: Environment.getExternalStorageDirectory())
                            currentFolderDir = folder
                        },
                        onNavigateBack = {
                            if (folderHistory.isNotEmpty()) {
                                currentFolderDir = folderHistory.removeAt(folderHistory.size - 1)
                            } else {
                                currentFolderDir = null
                            }
                        },
                        selectedPaths = selectedPaths,
                        isSelectionMode = isSelectionMode,
                        onFileClick = { item ->
                            if (isSelectionMode) {
                                if (selectedPaths.contains(item.path)) selectedPaths.remove(item.path)
                                else selectedPaths.add(item.path)
                                if (selectedPaths.isEmpty()) isSelectionMode = false
                            } else {
                                handleFileClick(item)
                            }
                        },
                        onFileLongClick = { item ->
                            try { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } catch (_: Exception) {}
                            if (!isSelectionMode) {
                                scope.launch(Dispatchers.IO) {
                                    val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                                    val existing = db.uploadedFileDao().getFileByPath(item.path)
                                        ?: db.uploadedFileDao().getFileByName(item.name)
                                    val fileObj = existing ?: UploadedFile(
                                        path = item.path,
                                        fileName = item.name,
                                        size = item.size,
                                        timestamp = item.file.lastModified()
                                    )
                                    withContext(Dispatchers.Main) {
                                        folderFilesForMenu = null
                                        fileForMenu = fileObj
                                    }
                                }
                            } else {
                                if (selectedPaths.contains(item.path)) selectedPaths.remove(item.path)
                                else selectedPaths.add(item.path)
                                if (selectedPaths.isEmpty()) isSelectionMode = false
                            }
                        },
                        onMoreOptions = { item ->
                            scope.launch(Dispatchers.IO) {
                                val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                                val existing = db.uploadedFileDao().getFileByPath(item.path)
                                    ?: db.uploadedFileDao().getFileByName(item.name)
                                val fileObj = existing ?: UploadedFile(
                                    path = item.path,
                                    fileName = item.name,
                                    size = item.size,
                                    timestamp = item.file.lastModified()
                                )
                                withContext(Dispatchers.Main) {
                                    fileForMenu = fileObj
                                }
                            }
                        },
                        onFolderMoreOptions = { subDir ->
                            val subFiles = filteredFiles.filter { it.path.startsWith(subDir.absolutePath) }.map {
                                UploadedFile(
                                    path = it.path,
                                    fileName = it.name,
                                    size = it.size,
                                    timestamp = it.file.lastModified()
                                )
                            }
                            val pseudo = UploadedFile(
                                path = subDir.absolutePath,
                                fileName = subDir.name,
                                telegramFileId = "DIR_MARKER",
                                size = subFiles.sumOf { it.size },
                                totalChunks = subFiles.size
                            )
                            folderFilesForMenu = subFiles
                            fileForMenu = pseudo
                        }
                    )
                }
            }
        }
    }

    if (fileForMenu != null) {
        com.example.telegrambackup.ui.common.FileActionMenuModal(
            file = fileForMenu!!,
            folderFiles = folderFilesForMenu,
            onDismiss = {
                fileForMenu = null
                folderFilesForMenu = null
            },
            onSelectMode = {
                val f = fileForMenu
                if (f != null && f.telegramFileId != "DIR_MARKER") {
                    if (!selectedPaths.contains(f.path)) {
                        selectedPaths.add(f.path)
                    }
                    isSelectionMode = true
                }
            },
            onFileRenamed = { _, _, _ ->
                fileForMenu = null
                folderFilesForMenu = null
                loadUnbackedFiles()
            },
            onFileDeleted = { delFile ->
                selectedPaths.remove(delFile.path)
                fileForMenu = null
                folderFilesForMenu = null
                unbackedFilesList = unbackedFilesList.filter { it.path != delFile.path }
                loadUnbackedFiles()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryWiseView(
    files: List<LocalFileItem>,
    selectedPaths: List<String>,
    isSelectionMode: Boolean,
    onFileClick: (LocalFileItem) -> Unit,
    onFileLongClick: (LocalFileItem) -> Unit,
    onMoreOptions: (LocalFileItem) -> Unit
) {
    val categoryGroups = remember(files) {
        files.distinctBy { it.path }.groupBy { it.category }
    }
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categoryGroups.forEach { (categoryName, categoryFiles) ->
            val isExpanded = expandedCategories[categoryName] ?: true
            val totalBytes = categoryFiles.sumOf { it.size }

            item(key = "header_${categoryName.hashCode()}") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedCategories[categoryName] = !isExpanded }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(TelegramBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (categoryName) {
                                        "Images" -> Icons.Default.Image
                                        "Videos" -> Icons.Default.VideoFile
                                        "Documents" -> Icons.Default.Description
                                        "APKs" -> Icons.Default.Android
                                        "Audio & Music" -> Icons.Default.AudioFile
                                        else -> Icons.Default.FolderZip
                                    },
                                    contentDescription = null,
                                    tint = TelegramBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${formatFileSize(totalBytes)} • ${categoryFiles.size} unbacked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TelegramEmerald.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${categoryFiles.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                itemsIndexed(
                    items = categoryFiles,
                    key = { index, item -> "cat_${categoryName}_${item.path.hashCode()}_$index" }
                ) { _, item ->
                    val isSelected = selectedPaths.contains(item.path)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) TelegramBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onFileClick(item) },
                                onLongClick = { onFileLongClick(item) }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onFileClick(item) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                FileThumbnailView(fileName = item.name, filePath = item.path, iconSize = 34.dp, modifier = Modifier.size(38.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(item.size)} • ${item.path}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!isSelectionMode) {
                                IconButton(
                                    onClick = { onMoreOptions(item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderWiseView(
    files: List<LocalFileItem>,
    currentDir: File?,
    onNavigateToSubfolder: (File) -> Unit,
    onNavigateBack: () -> Unit,
    selectedPaths: List<String>,
    isSelectionMode: Boolean,
    onFileClick: (LocalFileItem) -> Unit,
    onFileLongClick: (LocalFileItem) -> Unit,
    onMoreOptions: (LocalFileItem) -> Unit,
    onFolderMoreOptions: (File) -> Unit
) {
    val rootStorage = Environment.getExternalStorageDirectory() ?: File("/sdcard")
    val activeDir = currentDir ?: rootStorage

    // Safe canonical path helper that never throws
    fun File.safeCanonicalPath(): String = try { canonicalPath } catch (_: Exception) { absolutePath }

    // Compute subfolders and current-level files off Main thread to prevent crashes and ANR
    var currentLevelFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var subFoldersWithUnbackedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isComputing by remember { mutableStateOf(true) }

    LaunchedEffect(files, activeDir) {
        isComputing = true
        withContext(Dispatchers.Default) {
            try {
                val activePath = activeDir.safeCanonicalPath()

                val levelFiles = files.filter { item ->
                    try {
                        val parentPath = item.file.parentFile?.safeCanonicalPath()
                        parentPath == activePath
                    } catch (_: Exception) { false }
                }

                val subFolders = files.mapNotNull { item ->
                    try { item.file.parentFile } catch (_: Exception) { null }
                }.mapNotNull { parent ->
                    try {
                        val parentCanon = parent.safeCanonicalPath()
                        if (parentCanon.startsWith(activePath) && parentCanon != activePath) {
                            val rel = parentCanon.removePrefix(activePath).removePrefix(File.separator)
                            val firstSegment = rel.split(File.separatorChar).firstOrNull()
                            if (!firstSegment.isNullOrBlank()) {
                                File(activePath, firstSegment)
                            } else null
                        } else null
                    } catch (_: Exception) { null }
                }.distinctBy { it.absolutePath }

                withContext(Dispatchers.Main) {
                    currentLevelFiles = levelFiles
                    subFoldersWithUnbackedFiles = subFolders
                    isComputing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    currentLevelFiles = emptyList()
                    subFoldersWithUnbackedFiles = emptyList()
                    isComputing = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Breadcrumb header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentDir != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            OsFolderIcon(size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = activeDir.name.ifEmpty { "Internal Storage" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isComputing) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TelegramBlue, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
            // Render subfolders that contain unbacked files
            itemsIndexed(
                items = subFoldersWithUnbackedFiles,
                key = { index, subDir -> "subfolder_${subDir.absolutePath.hashCode()}_$index" }
            ) { _, subDir ->
                val unbackedCountInSub = files.count { it.path.startsWith(subDir.absolutePath) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onNavigateToSubfolder(subDir) },
                            onLongClick = { onFolderMoreOptions(subDir) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            OsFolderIcon(size = 36.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(subDir.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("$unbackedCountInSub Unbacked Files inside", style = MaterialTheme.typography.labelSmall, color = TelegramEmerald)
                            }
                        }
                        IconButton(
                            onClick = { onFolderMoreOptions(subDir) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Folder Options",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Render unbacked files at current folder level
            itemsIndexed(
                items = currentLevelFiles,
                key = { index, item -> "lvl_file_${item.path.hashCode()}_$index" }
            ) { _, item ->
                val isSelected = selectedPaths.contains(item.path)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) TelegramBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onFileClick(item) },
                            onLongClick = { onFileLongClick(item) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            Checkbox(checked = isSelected, onCheckedChange = { onFileClick(item) })
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            FileThumbnailView(fileName = item.name, filePath = item.path, iconSize = 34.dp, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatFileSize(item.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (!isSelectionMode) {
                            IconButton(
                                onClick = { onMoreOptions(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            } // end else !isComputing
        }
    }
}
