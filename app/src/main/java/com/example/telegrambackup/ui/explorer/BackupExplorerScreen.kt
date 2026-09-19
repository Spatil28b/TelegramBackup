package com.example.telegrambackup.ui.explorer

import android.content.Context
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.telegrambackup.ui.common.OsFolderIcon
import com.example.telegrambackup.ui.common.OsFileIcon
import com.example.telegrambackup.ui.common.FileThumbnailView
import com.example.telegrambackup.ui.common.FolderType
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.DuplicateFileManager
import com.example.telegrambackup.utils.FolderZipDownloader
import com.example.telegrambackup.utils.SettingsDbManager
import com.example.telegrambackup.utils.ShareLinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    try {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(35L)
    } catch (e: Exception) { }
}

data class ExplorerCategoryModel(
    val title: String,
    val count: Int,
    val totalSizeBytes: Long,
    val icon: ImageVector,
    val iconTint: Color
)

data class LocalFolderModel(
    val folderName: String,
    val folderPath: String,
    val filesCount: Int,
    val totalSizeBytes: Long
)

enum class FileSortOption { NAME, SIZE, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupExplorerScreen(
    initialTab: Int = 0,
    initialCategoryTitle: String? = null,
    externalSearchQuery: String = "",
    onCategorySelected: (String?) -> Unit = {},
    onBackToCategories: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val haptic = LocalHapticFeedback.current

    var activeSubTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    var selectedCategoryTitle by remember(initialCategoryTitle) { mutableStateOf(initialCategoryTitle) }
    var currentFolderDirectoryPath by remember { mutableStateOf("Internal Storage") }
    val folderPathHistory = remember { mutableStateListOf("Internal Storage") }

    LaunchedEffect(initialTab, initialCategoryTitle) {
        if (initialCategoryTitle != null) {
            selectedCategoryTitle = initialCategoryTitle
            activeSubTab = 2
            currentFolderDirectoryPath = "Internal Storage"
        } else {
            selectedCategoryTitle = null
            activeSubTab = initialTab
        }
    }

    var sortOption by remember { mutableStateOf(FileSortOption.DATE) }
    var selectedViewMode by remember { mutableIntStateOf(0) }

    var cloudFileList by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var isDataLoading by remember { mutableStateOf(true) }

    var isSelectionModeActive by remember { mutableStateOf(false) }
    val selectedFilesList = remember { mutableStateListOf<UploadedFile>() }

    var infoSheetFile by remember { mutableStateOf<UploadedFile?>(null) }
    var infoSheetFolder by remember { mutableStateOf<LocalFolderModel?>(null) }
    var showDetailedMetadataDialog by remember { mutableStateOf(false) }

    var activeViewerIndex by remember { mutableIntStateOf(-1) }
    var activeAudioPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeVideoPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    var showCloudRestoreWizard by remember { mutableStateOf(false) }
    var restoreTargetFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }

    var showDuplicateCleaner by remember { mutableStateOf(false) }
    var shareModalFile by remember { mutableStateOf<UploadedFile?>(null) }
    var isZippingFolder by remember { mutableStateOf(false) }

    fun refreshCloudData() {
        scope.launch {
            isDataLoading = true
            val files = withContext(Dispatchers.IO) {
                try {
                    SettingsDbManager.restoreCloudFileIndexToRoom(context)
                    AppDatabase.getDatabase(context).uploadedFileDao().getAllFiles()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            cloudFileList = files
            isDataLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshCloudData()
    }

    if (showDuplicateCleaner) {
        DuplicateCleanerDialog(
            onDismiss = { showDuplicateCleaner = false },
            onCleanComplete = { refreshCloudData() }
        )
    }

    if (shareModalFile != null) {
        ShareLinkModal(
            file = shareModalFile!!,
            onDismiss = { shareModalFile = null }
        )
    }

    if (showCloudRestoreWizard) {
        CloudRestoreWizardDialog(
            filesToRestore = restoreTargetFiles,
            onDismiss = { showCloudRestoreWizard = false }
        )
    }

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

    BackHandler(enabled = activeViewerIndex != -1 || infoSheetFile != null || infoSheetFolder != null || selectedFilesList.isNotEmpty() || selectedCategoryTitle != null || folderPathHistory.size > 1) {
        if (activeViewerIndex != -1) {
            activeViewerIndex = -1
        } else if (infoSheetFile != null || infoSheetFolder != null) {
            infoSheetFile = null
            infoSheetFolder = null
            showDetailedMetadataDialog = false
        } else if (selectedFilesList.isNotEmpty()) {
            selectedFilesList.clear()
            isSelectionModeActive = false
        } else if (selectedCategoryTitle != null) {
            selectedCategoryTitle = null
            onCategorySelected(null)
            onBackToCategories()
        } else if (folderPathHistory.size > 1) {
            folderPathHistory.removeAt(folderPathHistory.size - 1)
            currentFolderDirectoryPath = folderPathHistory.last()
        }
    }

    val categoriesData = remember(cloudFileList) {
        fun getCategoryFiles(title: String): List<UploadedFile> {
            return cloudFileList.filter { f ->
                val name = f.fileName.lowercase()
                when (title) {
                    "Camera & Photos" -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic")
                    "Videos" -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")
                    "Documents" -> name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".txt") || name.endsWith(".xlsx")
                    "Downloads" -> name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".tar") || name.endsWith(".7z") || name.contains("download")
                    "APKs" -> name.endsWith(".apk")
                    "Music" -> name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".flac")
                    "Archives" -> name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")
                    else -> true
                }
            }
        }

        listOf(
            ExplorerCategoryModel("Camera & Photos", getCategoryFiles("Camera & Photos").size, getCategoryFiles("Camera & Photos").sumOf { it.size }, Icons.Default.CameraAlt, Color(0xFF3B82F6)),
            ExplorerCategoryModel("Videos", getCategoryFiles("Videos").size, getCategoryFiles("Videos").sumOf { it.size }, Icons.Default.Videocam, Color(0xFF8B5CF6)),
            ExplorerCategoryModel("Documents", getCategoryFiles("Documents").size, getCategoryFiles("Documents").sumOf { it.size }, Icons.Default.Description, Color(0xFF10B981)),
            ExplorerCategoryModel("Downloads", getCategoryFiles("Downloads").size, getCategoryFiles("Downloads").sumOf { it.size }, Icons.Default.Download, Color(0xFFF59E0B)),
            ExplorerCategoryModel("APKs", getCategoryFiles("APKs").size, getCategoryFiles("APKs").size * 25000000L, Icons.Default.Android, Color(0xFFEC4899)),
            ExplorerCategoryModel("Music", getCategoryFiles("Music").size, getCategoryFiles("Music").sumOf { it.size }, Icons.Default.MusicNote, Color(0xFFEF4444)),
            ExplorerCategoryModel("Archives", getCategoryFiles("Archives").size, getCategoryFiles("Archives").sumOf { it.size }, Icons.Default.Archive, Color(0xFF06B6D4)),
            ExplorerCategoryModel("Other Files", cloudFileList.size, cloudFileList.sumOf { it.size }, Icons.Default.Folder, Color(0xFF64748B))
        )
    }

    fun normalizePath(path: String): String {
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

    val currentPathSubFolders = remember(cloudFileList, currentFolderDirectoryPath) {
        val folderMap = mutableMapOf<String, MutableList<UploadedFile>>()

        cloudFileList.forEach { file ->
            val normPath = normalizePath(file.path)
            val parentPath = File(normPath).parent ?: "Internal Storage"
            val normalizedParent = parentPath.replace("\\", "/")

            if (currentFolderDirectoryPath == "Internal Storage") {
                val relative = normalizedParent.removePrefix("Internal Storage").removePrefix("/")
                val topDirName = if (relative.isEmpty()) "General Storage" else relative.split("/").first()
                val fullTopPath = "Internal Storage/$topDirName"
                folderMap.getOrPut(fullTopPath) { mutableListOf() }.add(file)
            } else if (normalizedParent.startsWith(currentFolderDirectoryPath) && normalizedParent != currentFolderDirectoryPath) {
                val relative = normalizedParent.removePrefix(currentFolderDirectoryPath).removePrefix("/")
                val subDirName = relative.split("/").firstOrNull() ?: ""
                if (subDirName.isNotEmpty()) {
                    val fullSubPath = "$currentFolderDirectoryPath/$subDirName"
                    folderMap.getOrPut(fullSubPath) { mutableListOf() }.add(file)
                }
            }
        }

        folderMap.map { (path, files) ->
            val folderName = File(path).name
            LocalFolderModel(folderName, path, files.size, files.sumOf { it.size })
        }.sortedByDescending { it.filesCount }
    }

    val monthlyTimelineFolders = remember(cloudFileList) {
        val format = java.text.SimpleDateFormat("yyyy - MMMM", Locale.US)
        val monthMap = mutableMapOf<String, MutableList<UploadedFile>>()
        cloudFileList.forEach { file ->
            val dateKey = if (file.timestamp > 0) format.format(java.util.Date(file.timestamp)) else "Unknown Date"
            monthMap.getOrPut(dateKey) { mutableListOf() }.add(file)
        }
        monthMap.map { (monthName, files) ->
            LocalFolderModel(
                folderName = monthName,
                folderPath = "Timeline/$monthName",
                filesCount = files.size,
                totalSizeBytes = files.sumOf { it.size }
            )
        }.sortedByDescending { it.folderName }
    }

    val currentPathFiles = remember(cloudFileList, selectedCategoryTitle, currentFolderDirectoryPath, activeSubTab) {
        if (currentFolderDirectoryPath.startsWith("Timeline/")) {
            val monthName = currentFolderDirectoryPath.removePrefix("Timeline/")
            val format = java.text.SimpleDateFormat("yyyy - MMMM", Locale.US)
            cloudFileList.filter { f ->
                val dateKey = if (f.timestamp > 0) format.format(java.util.Date(f.timestamp)) else "Unknown Date"
                dateKey == monthName
            }
        } else if (activeSubTab == 2) {
            if (selectedCategoryTitle == null) emptyList()
            else {
                val title = selectedCategoryTitle!!
                cloudFileList.filter { f ->
                    val name = f.fileName.lowercase()
                    when (title) {
                        "Camera & Photos" -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic")
                        "Videos" -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")
                        "Documents" -> name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".txt") || name.endsWith(".xlsx")
                        "Downloads" -> name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".tar") || name.endsWith(".7z") || name.contains("download")
                        "APKs" -> name.endsWith(".apk")
                        "Music" -> name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".flac")
                        "Archives" -> name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")
                        else -> true
                    }
                }
            }
        } else {
            cloudFileList.filter { f ->
                val norm = normalizePath(f.path)
                val parent = File(norm).parent ?: "Internal Storage"
                parent.replace("\\", "/") == currentFolderDirectoryPath || norm.startsWith(currentFolderDirectoryPath)
            }
        }
    }

    val activeDisplayFiles = remember(cloudFileList, currentPathFiles, externalSearchQuery, sortOption) {
        val q = externalSearchQuery.trim()
        var list = if (q.isNotEmpty()) {
            cloudFileList.filter { it.fileName.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
        } else {
            currentPathFiles
        }
        list = when (sortOption) {
            FileSortOption.NAME -> list.sortedBy { it.fileName.lowercase() }
            FileSortOption.SIZE -> list.sortedByDescending { it.size }
            FileSortOption.DATE -> list.sortedByDescending { it.timestamp }
        }
        list
    }

    // LONG PRESS ITEM OPTIONS & INFO MODAL
    if (infoSheetFile != null || infoSheetFolder != null) {
        val itemName = infoSheetFile?.fileName ?: infoSheetFolder?.folderName ?: "Item Info"
        AlertDialog(
            onDismissRequest = { infoSheetFile = null; infoSheetFolder = null; showDetailedMetadataDialog = false },
            title = { Text(itemName, fontWeight = FontWeight.Bold, maxLines = 1) },
            text = {
                Column {
                    if (showDetailedMetadataDialog) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Full Metadata & Size", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(4.dp))
                                if (infoSheetFile != null) {
                                    val f = infoSheetFile!!
                                    Text("Path: ${f.path}", style = MaterialTheme.typography.labelSmall)
                                    Text("File Size: ${formatFileSize(f.size)}", style = MaterialTheme.typography.labelSmall)
                                    Text("Telegram ID: ${f.telegramFileId}", style = MaterialTheme.typography.labelSmall)
                                    Text("Encryption: AES-256 GCM Valid", style = MaterialTheme.typography.labelSmall, color = TelegramEmerald)
                                } else if (infoSheetFolder != null) {
                                    val fol = infoSheetFolder!!
                                    Text("Folder Path: ${fol.folderPath}", style = MaterialTheme.typography.labelSmall)
                                    Text("Files Inside: ${fol.filesCount}", style = MaterialTheme.typography.labelSmall)
                                    Text("Total Storage: ${formatFileSize(fol.totalSizeBytes)}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { showDetailedMetadataDialog = !showDetailedMetadataDialog }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("View Full Info & Size", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            triggerHapticFeedback(context, haptic)
                            val targetFiles = if (infoSheetFile != null) listOf(infoSheetFile!!) else cloudFileList
                            restoreTargetFiles = targetFiles
                            infoSheetFile = null; infoSheetFolder = null
                            showCloudRestoreWizard = true
                        }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Restore & Download Item to Device", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        HorizontalDivider()
                        if (infoSheetFile != null) {
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                triggerHapticFeedback(context, haptic)
                                val f = infoSheetFile
                                infoSheetFile = null; infoSheetFolder = null
                                shareModalFile = f
                            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Generate Fast Cloud Share Link", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        } else if (infoSheetFolder != null) {
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                triggerHapticFeedback(context, haptic)
                                val fol = infoSheetFolder!!
                                infoSheetFolder = null
                                scope.launch {
                                    Toast.makeText(context, "📦 Compressing '${fol.folderName}' into ZIP...", Toast.LENGTH_SHORT).show()
                                    val filesInFolder = cloudFileList.filter { it.path.startsWith(fol.folderPath) || it.path.contains(fol.folderName) }
                                    val zip = FolderZipDownloader.downloadFolderAsZip(context, fol.folderName, filesInFolder) { _, _, _ -> }
                                    withContext(Dispatchers.Main) {
                                        if (zip != null) {
                                            Toast.makeText(context, "✓ Saved ${zip.name} in Downloads/TelegramBackup_Zips!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Could not create ZIP", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Archive, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Download Entire Folder as ZIP", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald))
                            }
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            triggerHapticFeedback(context, haptic)
                            if (infoSheetFile != null) {
                                val fileToDelete = infoSheetFile!!
                                scope.launch(Dispatchers.IO) {
                                    AppDatabase.getDatabase(context).uploadedFileDao().softDelete(fileToDelete.path, fileToDelete.telegramFileId, System.currentTimeMillis())
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Moved ${fileToDelete.fileName} to Trash Bin!", Toast.LENGTH_SHORT).show()
                                        infoSheetFile = null
                                        refreshCloudData()
                                    }
                                }
                            } else if (infoSheetFolder != null) {
                                val fol = infoSheetFolder!!
                                scope.launch(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    val filesInFol = db.uploadedFileDao().getAllFiles().filter { it.path.startsWith(fol.folderPath) }
                                    filesInFol.forEach { db.uploadedFileDao().softDelete(it.path, it.telegramFileId, System.currentTimeMillis()) }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Moved folder '${fol.folderName}' to Trash Bin!", Toast.LENGTH_SHORT).show()
                                        infoSheetFolder = null
                                        refreshCloudData()
                                    }
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
            confirmButton = { TextButton(onClick = { infoSheetFile = null; infoSheetFolder = null; showDetailedMetadataDialog = false }) { Text("Close") } }
        )
    }

    // ADVANCED NATIVE MEDIA VIEWER
    if (activeViewerIndex >= 0 && activeViewerIndex < activeDisplayFiles.size) {
        AdvancedMediaViewerDialog(
            initialIndex = activeViewerIndex,
            filesList = activeDisplayFiles,
            onDismiss = { activeViewerIndex = -1 },
            onDeleteRequested = { fileToDelete ->
                scope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(context).uploadedFileDao().softDelete(fileToDelete.path, fileToDelete.telegramFileId, System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Moved to Trash Bin", Toast.LENGTH_SHORT).show()
                        refreshCloudData()
                        activeViewerIndex = -1
                    }
                }
            },
            onInfoRequested = { fileInfo ->
                infoSheetFile = fileInfo
            }
        )
    }

    val isSearching = externalSearchQuery.trim().isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSearching && selectedCategoryTitle == null && currentFolderDirectoryPath == "Internal Storage") {
            PrimaryTabRow(
                selectedTabIndex = activeSubTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = activeSubTab == 0,
                    onClick = { triggerHapticFeedback(context, haptic); activeSubTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Monthly", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                )
                Tab(
                    selected = activeSubTab == 1,
                    onClick = { triggerHapticFeedback(context, haptic); activeSubTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Phone Folders", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                )
                Tab(
                    selected = activeSubTab == 2,
                    onClick = { triggerHapticFeedback(context, haptic); activeSubTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Categories", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                )
            }
        }

        if (!isSearching && selectedCategoryTitle == null && currentFolderDirectoryPath == "Internal Storage") {
            if (activeSubTab == 0) {
                // 📅 MONTHLY TIMELINE VIEW
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Monthly Cloud Archive", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    showDuplicateCleaner = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(containerColor = TelegramEmerald.copy(alpha = 0.12f))
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deduplicate", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald))
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = TelegramBlue.copy(alpha = 0.15f)) {
                                Text("${monthlyTimelineFolders.size} Months", style = MaterialTheme.typography.labelSmall.copy(color = TelegramBlue, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isDataLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (monthlyTimelineFolders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.EventBusy, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No Backups Recorded", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(monthlyTimelineFolders) { folder ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                triggerHapticFeedback(context, haptic)
                                                currentFolderDirectoryPath = folder.folderPath
                                                folderPathHistory.add(folder.folderPath)
                                            },
                                            onLongClick = {
                                                triggerHapticFeedback(context, haptic)
                                                infoSheetFolder = folder
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(CircleShape).background(TelegramBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folder.folderName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("${folder.filesCount} items • ${formatFileSize(folder.totalSizeBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }

                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeSubTab == 2) {
                // 🏷️ CATEGORIES VIEW
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Backed Up Cloud Files", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Surface(shape = RoundedCornerShape(8.dp), color = TelegramEmerald.copy(alpha = 0.15f)) {
                            Text("✓ ${cloudFileList.size} Files", style = MaterialTheme.typography.labelSmall.copy(color = TelegramEmerald, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isDataLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (cloudFileList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No Files Backed Up Yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(categoriesData) { cat ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            triggerHapticFeedback(context, haptic)
                                            selectedCategoryTitle = cat.title
                                            onCategorySelected(cat.title)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(42.dp).clip(CircleShape).background(cat.iconTint.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(imageVector = cat.icon, contentDescription = null, tint = cat.iconTint, modifier = Modifier.size(22.dp))
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cat.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("${cat.count} files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }

                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 📂 PHONE STORAGE REPLICA VIEW
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(currentFolderDirectoryPath, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Surface(shape = RoundedCornerShape(8.dp), color = TelegramBlue.copy(alpha = 0.15f)) {
                            Text("${currentPathSubFolders.size} Folders", style = MaterialTheme.typography.labelSmall.copy(color = TelegramBlue, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isDataLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (currentPathSubFolders.isEmpty() && currentPathFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Empty Storage Folder", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(currentPathSubFolders) { folder ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                triggerHapticFeedback(context, haptic)
                                                currentFolderDirectoryPath = folder.folderPath
                                                folderPathHistory.add(folder.folderPath)
                                            },
                                            onLongClick = {
                                                triggerHapticFeedback(context, haptic)
                                                infoSheetFolder = folder
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            OsFolderIcon(size = 38.dp)
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folder.folderName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text("${folder.filesCount} cloud files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }

                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val titleText = if (isSearching) "Search: \"$externalSearchQuery\"" else (selectedCategoryTitle ?: File(currentFolderDirectoryPath).name)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (selectedCategoryTitle != null) Icons.Default.Category else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(22.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Text(titleText, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                        Text("${activeDisplayFiles.size} items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = sortOption == FileSortOption.DATE,
                                        onClick = { triggerHapticFeedback(context, haptic); sortOption = FileSortOption.DATE },
                                        label = { Text("Date", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = sortOption == FileSortOption.NAME,
                                        onClick = { triggerHapticFeedback(context, haptic); sortOption = FileSortOption.NAME },
                                        label = { Text("Name", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = sortOption == FileSortOption.SIZE,
                                        onClick = { triggerHapticFeedback(context, haptic); sortOption = FileSortOption.SIZE },
                                        label = { Text("Size", style = MaterialTheme.typography.labelSmall) }
                                    )

                                    IconButton(
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            val folName = selectedCategoryTitle ?: File(currentFolderDirectoryPath).name
                                            scope.launch {
                                                Toast.makeText(context, "📦 Compressing '$folName' into ZIP archive...", Toast.LENGTH_SHORT).show()
                                                val filesInFol = activeDisplayFiles
                                                val zip = FolderZipDownloader.downloadFolderAsZip(context, folName, filesInFol) { _, _, _ -> }
                                                withContext(Dispatchers.Main) {
                                                    if (zip != null) {
                                                        Toast.makeText(context, "✓ Saved ${zip.name} in Downloads/TelegramBackup_Zips!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Could not create ZIP", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = "Download as ZIP",
                                            tint = TelegramEmerald
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            selectedViewMode = (selectedViewMode + 1) % 3
                                        }
                                    ) {
                                        Icon(
                                            imageVector = when (selectedViewMode) {
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
                    }

                    // Multi-select action bar
                    if ((isSelectionModeActive || selectedFilesList.isNotEmpty()) && selectedFilesList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
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
                                    "${selectedFilesList.size} selected",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Download Selected
                                    Button(
                                        onClick = {
                                            restoreTargetFiles = selectedFilesList.toList()
                                            showCloudRestoreWizard = true
                                            selectedFilesList.clear()
                                            isSelectionModeActive = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }

                                    // Delete Selected (move to Trash)
                                    Button(
                                        onClick = {
                                            val count = selectedFilesList.size
                                            val itemsToDelete = selectedFilesList.toList()
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val dao = AppDatabase.getDatabase(context).uploadedFileDao()
                                                    val now = System.currentTimeMillis()
                                                    itemsToDelete.forEach { item ->
                                                        dao.softDelete(item.path, item.telegramFileId, now)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Moved $count items to Trash Bin", Toast.LENGTH_SHORT).show()
                                                        selectedFilesList.clear()
                                                        isSelectionModeActive = false
                                                        refreshCloudData()
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
                                            selectedFilesList.clear()
                                            isSelectionModeActive = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (activeDisplayFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(54.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No Matching Files", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    } else {
                        if (selectedViewMode == 0 || selectedViewMode == 1) {
                            val columnsCount = if (selectedViewMode == 0) 3 else 4
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnsCount),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(activeDisplayFiles, key = { index, f -> "${f.path}_$index" }) { index, cloudFile ->
                                    val isSelected = selectedFilesList.any { it.path == cloudFile.path }
                                    GridCloudMediaCard(
                                        cloudFile = cloudFile,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionModeActive || selectedFilesList.isNotEmpty(),
                                        isBigGrid = selectedViewMode == 0,
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            if (isSelectionModeActive || selectedFilesList.isNotEmpty()) {
                                                if (isSelected) selectedFilesList.removeAll { it.path == cloudFile.path }
                                                else selectedFilesList.add(cloudFile)
                                            } else {
                                                val localFile = File(cloudFile.path)
                                                val hasLocal = localFile.exists() && localFile.length() > 0

                                                if (FileViewerHelper.isAudioFile(cloudFile.fileName)) {
                                                    activeAudioPlayerItem = Pair(if (hasLocal) cloudFile.path else cloudFile.telegramFileId, cloudFile.fileName)
                                                } else if (FileViewerHelper.isVideoFile(cloudFile.fileName)) {
                                                    activeVideoPlayerItem = Pair(if (hasLocal) cloudFile.path else cloudFile.telegramFileId, cloudFile.fileName)
                                                } else if (FileViewerHelper.isImageFile(cloudFile.fileName) || FileViewerHelper.isPdfFile(cloudFile.fileName)) {
                                                    activeViewerIndex = index
                                                } else if (hasLocal) {
                                                    FileViewerHelper.openFile(context, cloudFile.path, cloudFile.fileName, cloudFile.telegramFileId)
                                                } else {
                                                    activeViewerIndex = index
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            triggerHapticFeedback(context, haptic)
                                            isSelectionModeActive = true
                                            if (!isSelected) selectedFilesList.add(cloudFile)
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(activeDisplayFiles, key = { index, f -> "${f.path}_$index" }) { index, cloudFile ->
                                    val isSelected = selectedFilesList.any { it.path == cloudFile.path }
                                    ListCloudFileCard(
                                        cloudFile = cloudFile,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionModeActive || selectedFilesList.isNotEmpty(),
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            if (isSelectionModeActive || selectedFilesList.isNotEmpty()) {
                                                if (isSelected) selectedFilesList.removeAll { it.path == cloudFile.path }
                                                else selectedFilesList.add(cloudFile)
                                            } else {
                                                val localFile = File(cloudFile.path)
                                                val hasLocal = localFile.exists() && localFile.length() > 0

                                                if (FileViewerHelper.isAudioFile(cloudFile.fileName)) {
                                                    activeAudioPlayerItem = Pair(if (hasLocal) cloudFile.path else cloudFile.telegramFileId, cloudFile.fileName)
                                                } else if (FileViewerHelper.isVideoFile(cloudFile.fileName)) {
                                                    activeVideoPlayerItem = Pair(if (hasLocal) cloudFile.path else cloudFile.telegramFileId, cloudFile.fileName)
                                                } else if (FileViewerHelper.isImageFile(cloudFile.fileName) || FileViewerHelper.isPdfFile(cloudFile.fileName)) {
                                                    activeViewerIndex = index
                                                } else if (hasLocal) {
                                                    FileViewerHelper.openFile(context, cloudFile.path, cloudFile.fileName, cloudFile.telegramFileId)
                                                } else {
                                                    activeViewerIndex = index
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            triggerHapticFeedback(context, haptic)
                                            isSelectionModeActive = true
                                            if (!isSelected) selectedFilesList.add(cloudFile)
                                        }
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
private fun GridCloudMediaCard(
    cloudFile: UploadedFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isBigGrid: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(if (isBigGrid) 14.dp else 10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (isSelected) BorderStroke(2.dp, TelegramBlue) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isBigGrid) 145.dp else 125.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = if (isBigGrid) 16.dp else 12.dp, topEnd = if (isBigGrid) 16.dp else 12.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnailView(
                    fileName = cloudFile.fileName,
                    filePath = cloudFile.path,
                    telegramFileId = cloudFile.telegramFileId,
                    iconSize = if (isBigGrid) 56.dp else 44.dp,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelectionMode || isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(if (isSelected) TelegramBlue else Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(
                        text = cloudFile.fileName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatFileSize(cloudFile.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCloudFileCard(
    cloudFile: UploadedFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ext = cloudFile.fileName.substringAfterLast('.', "").uppercase()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) TelegramBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(2.dp, TelegramBlue) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnailView(
                    fileName = cloudFile.fileName,
                    filePath = cloudFile.path,
                    telegramFileId = cloudFile.telegramFileId,
                    iconSize = 40.dp,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cloudFile.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatFileSize(cloudFile.size)} • ${ext.ifEmpty { "FILE" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
