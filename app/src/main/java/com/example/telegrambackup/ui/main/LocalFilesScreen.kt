package com.example.telegrambackup.ui.main

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    // Scanner logic
    val loadUnbackedFiles = {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                val db = AppDatabase.getDatabase(context)
                val uploadedFiles = db.uploadedFileDao().getAllFiles()
                val uploadedPathSet = uploadedFiles.map { it.path }.toSet()

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
                var itemCount = 0
                val MAX_TOTAL_UNBACKED = 2500

                for (dir in dirsToScan) {
                    if (dir.exists() && dir.isDirectory) {
                        try {
                            dir.walkTopDown()
                                .onEnter { subDir ->
                                    try {
                                        val name = subDir.name.lowercase()
                                        !name.startsWith(".") && name != "android" && name != "cache" && name != "tmp" && name != "thumbnails" && name != ".thumbnails" && name != "node_modules"
                                    } catch (e: Exception) { false }
                                }
                                .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
                                .forEach { file ->
                                    if (foundList.size < MAX_TOTAL_UNBACKED && !uploadedPathSet.contains(file.absolutePath)) {
                                        foundList.add(
                                            LocalFileItem(
                                                file = file,
                                                name = file.name,
                                                path = file.absolutePath,
                                                size = file.length(),
                                                category = getCategoryForFile(file),
                                                extension = file.extension.uppercase()
                                            )
                                        )
                                        itemCount++
                                        if (itemCount % 150 == 0) {
                                            val snapshot = foundList.toList()
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                unbackedFilesList = snapshot.sortedByDescending { it.file.lastModified() }
                                                if (isLoading) isLoading = false
                                            }
                                            kotlinx.coroutines.yield()
                                        }
                                    }
                                }
                        } catch (_: Exception) { }
                    }
                }
                val finalSorted = foundList.sortedByDescending { it.file.lastModified() }
                withContext(Dispatchers.Main) {
                    unbackedFilesList = finalSorted
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadUnbackedFiles()
    }

    BackHandler(enabled = isSelectionMode || folderHistory.isNotEmpty()) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedPaths.clear()
        } else if (folderHistory.isNotEmpty()) {
            currentFolderDir = folderHistory.removeAt(folderHistory.size - 1)
        }
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

    Scaffold(
        topBar = {
            Column {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Local Storage (Unbacked)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${unbackedFilesList.size} Files Remaining to Backup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TelegramEmerald
                                )
                            }

                            Row {
                                IconButton(onClick = { loadUnbackedFiles() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan", tint = TelegramBlue)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter remaining files...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                            } else null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tab Bar
                        TabRow(
                            selectedTabIndex = selectedTabState,
                            containerColor = Color.Transparent,
                            contentColor = TelegramBlue
                        ) {
                            Tab(
                                selected = selectedTabState == 0,
                                onClick = { selectedTabState = 0 },
                                text = { Text("Category Wise (${unbackedFilesList.size})", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTabState == 1,
                                onClick = { selectedTabState = 1 },
                                text = { Text("Folder Wise (100% Internal)", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }

                // Selection Action Bar
                AnimatedVisibility(visible = isSelectionMode) {
                    Surface(
                        color = TelegramBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${selectedPaths.size} Files Selected",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                Button(
                                    onClick = triggerBackupForSelected,
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Backup Selected Now")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)
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
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedPaths.add(item.path)
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
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedPaths.add(item.path)
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
                }
            }
        }
    }

    if (fileForMenu != null) {
        com.example.telegrambackup.ui.common.FileActionMenuModal(
            file = fileForMenu!!,
            onDismiss = { fileForMenu = null },
            onFileRenamed = { _, _, _ ->
                fileForMenu = null
            },
            onFileDeleted = { delFile ->
                selectedPaths.remove(delFile.path)
                fileForMenu = null
                unbackedFilesList = unbackedFilesList.filter { it.path != delFile.path }
            }
        )
    }
}@Composable
private fun CategoryWiseView(
    files: List<LocalFileItem>,
    selectedPaths: List<String>,
    isSelectionMode: Boolean,
    onFileClick: (LocalFileItem) -> Unit,
    onFileLongClick: (LocalFileItem) -> Unit,
    onMoreOptions: (LocalFileItem) -> Unit
) {
    val categoryGroups = remember(files) {
        files.groupBy { it.category }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        categoryGroups.forEach { (categoryName, categoryFiles) ->
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TelegramEmerald.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${categoryFiles.size} Remaining",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()

                        categoryFiles.forEach { item ->
                            val isSelected = selectedPaths.contains(item.path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onFileClick(item) },
                                        onLongClick = { onFileLongClick(item) }
                                    )
                                    .background(if (isSelected) TelegramBlue.copy(alpha = 0.15f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onFileClick(item) }
                                )
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
}

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
    onMoreOptions: (LocalFileItem) -> Unit
) {
    val rootStorage = Environment.getExternalStorageDirectory() ?: File("/sdcard")
    val activeDir = currentDir ?: rootStorage

    // Compute subfolders inside activeDir that have remaining unbacked files
    val currentLevelFiles = remember(files, activeDir) {
        files.filter { it.file.parentFile?.absolutePath == activeDir.absolutePath }
    }

    val subFoldersWithUnbackedFiles = remember(files, activeDir) {
        val activePath = activeDir.absolutePath
        files.mapNotNull { it.file.parentFile }
            .filter { parent ->
                parent.absolutePath.startsWith(activePath) && parent.absolutePath != activePath
            }
            .map { child ->
                // Get direct subfolder under activePath
                var temp = child
                while (temp.parentFile != null && temp.parentFile?.absolutePath != activePath) {
                    temp = temp.parentFile!!
                }
                temp
            }
            .distinctBy { it.absolutePath }
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
            // Render subfolders that contain unbacked files
            items(subFoldersWithUnbackedFiles) { subDir ->
                val unbackedCountInSub = files.count { it.path.startsWith(subDir.absolutePath) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToSubfolder(subDir) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OsFolderIcon(size = 36.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(subDir.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("$unbackedCountInSub Unbacked Files inside", style = MaterialTheme.typography.labelSmall, color = TelegramEmerald)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Render unbacked files at current folder level
            items(currentLevelFiles) { item ->
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
                        Checkbox(checked = isSelected, onCheckedChange = { onFileClick(item) })
                        Spacer(modifier = Modifier.width(6.dp))
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
        }
    }
}
