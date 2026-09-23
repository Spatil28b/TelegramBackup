package com.example.telegrambackup.ui.explorer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.telegrambackup.utils.DriveUploadManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.example.telegrambackup.ui.common.FileActionMenuModal
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
import androidx.fragment.app.FragmentActivity
import com.example.telegrambackup.utils.BiometricHelper
import com.example.telegrambackup.utils.StorageStatsHelper
import com.example.telegrambackup.utils.DeviceStorageInfo

fun formatFileSize(sizeBytes: Long): String {
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
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(35L)
        }
    } catch (_: Exception) { }
}

data class ExplorerCategoryModel(
    val title: String,
    val count: Int,
    val totalSizeBytes: Long,
    val icon: ImageVector,
    val iconTint: Color,
    val containerTint: Color = Color(0xFFE8F0FE)
)

data class LocalFolderModel(
    val folderName: String,
    val folderPath: String,
    val filesCount: Int,
    val totalSizeBytes: Long
)

enum class FileSortOption { NAME, SIZE, DATE }

enum class ExplorerFilterType(val label: String, val icon: ImageVector?) {
    ALL("All", null),
    PHOTOS("Photos", Icons.Default.CameraAlt),
    VIDEOS("Videos", Icons.Default.Videocam),
    DOCUMENTS("Docs", Icons.Default.Description),
    AUDIO("Audio", Icons.Default.MusicNote),
    ARCHIVES("Archives", Icons.Default.Archive)
}

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

    var localSearchQuery by remember { mutableStateOf("") }
    val effectiveSearchQuery = remember(externalSearchQuery, localSearchQuery) {
        if (externalSearchQuery.isNotBlank()) externalSearchQuery else localSearchQuery
    }
    val isSearching = effectiveSearchQuery.trim().isNotEmpty()

    var deviceStorage by remember { mutableStateOf(StorageStatsHelper.getDeviceStorageInfo()) }
    LaunchedEffect(Unit) {
        deviceStorage = StorageStatsHelper.getDeviceStorageInfo()
    }

    LaunchedEffect(initialTab, initialCategoryTitle) {
        if (initialCategoryTitle != null) {
            selectedCategoryTitle = initialCategoryTitle
            activeSubTab = 2
            currentFolderDirectoryPath = "Internal Storage"
        } else {
            selectedCategoryTitle = null
            activeSubTab = initialTab
            if (initialTab == 1) {
                currentFolderDirectoryPath = "Internal Storage/Phone Folders"
                folderPathHistory.clear()
                folderPathHistory.add("Internal Storage")
                folderPathHistory.add("Internal Storage/Phone Folders")
            } else if (initialTab == 0) {
                currentFolderDirectoryPath = "Internal Storage"
                folderPathHistory.clear()
                folderPathHistory.add("Internal Storage")
            }
        }
    }

    var sortOption by remember { mutableStateOf(FileSortOption.DATE) }
    var selectedFilterType by remember { mutableStateOf(ExplorerFilterType.ALL) }
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
    var actionMenuFile by remember { mutableStateOf<UploadedFile?>(null) }
    var isZippingFolder by remember { mutableStateOf(false) }

    val database = remember { AppDatabase.getDatabase(context) }
    val roomFiles by database.uploadedFileDao().getAllFilesFlow().collectAsState(initial = emptyList())

    LaunchedEffect(roomFiles) {
        if (roomFiles.isNotEmpty()) {
            cloudFileList = roomFiles
            isDataLoading = false
        }
    }

    fun refreshCloudData() {
        scope.launch {
            val localFiles = withContext(Dispatchers.IO) {
                try {
                    database.uploadedFileDao().getAllFiles()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            if (localFiles.isNotEmpty()) {
                cloudFileList = localFiles
                isDataLoading = false
            }

            withContext(Dispatchers.IO) {
                try {
                    SettingsDbManager.restoreCloudFileIndexToRoom(context)
                    val syncedFiles = database.uploadedFileDao().getAllFiles()
                    withContext(Dispatchers.Main) {
                        cloudFileList = syncedFiles
                        isDataLoading = false
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        isDataLoading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshCloudData()
    }

    val personalFileUploadLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "⚡ Uploading ${uris.size} files in parallel...", Toast.LENGTH_SHORT).show()
            DriveUploadManager.enqueuePersonalFiles(context, uris, currentFolderDirectoryPath) {
                refreshCloudData()
            }
        }
    }

    val safeFolderUploadLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "🔒 Adding ${uris.size} files to Safe Folder...", Toast.LENGTH_SHORT).show()
            DriveUploadManager.enqueuePersonalFiles(context, uris, "Internal Storage/Private Safe Vault") {
                uris.forEach { u ->
                    val path = u.path ?: u.toString()
                    prefs.toggleSafeVaultFile(path)
                }
                refreshCloudData()
            }
        }
    }

    if (actionMenuFile != null) {
        FileActionMenuModal(
            file = actionMenuFile!!,
            onDismiss = { actionMenuFile = null },
            onFileRenamed = { _, _, _ -> refreshCloudData() },
            onFileDeleted = { delFile ->
                selectedFilesList.removeAll { it.path == delFile.path }
                refreshCloudData()
            }
        )
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

    fun isFileMatchingCategory(f: UploadedFile, title: String): Boolean {
        if (title != "Safe folder" && title != "Safe Folder" && title != "Private Safe Vault" && prefs.isSafeVaultFile(f.path)) {
            return false
        }
        val name = f.fileName.lowercase()
        val path = f.path.lowercase()
        return when (title) {
            "Images", "Camera & Photos" -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic") || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".svg")
            "Videos" -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".flv")
            "Audio", "Music" -> name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".ogg") || name.endsWith(".opus")
            "Documents", "Documents & other" -> name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".txt") || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".pptx") || name.endsWith(".ppt") || name.endsWith(".csv")
            "Downloads" -> path.contains("download") || name.contains("download")
            "Apps", "APKs" -> name.endsWith(".apk") || name.endsWith(".xapk")
            "Archives" -> name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".bz2")
            "Starred" -> prefs.isFileStarred(f.path) && !prefs.isSafeVaultFile(f.path)
            "Safe folder", "Safe Folder", "Private Safe Vault" -> prefs.isSafeVaultFile(f.path) || f.path.contains("Safe") || f.path.contains(".private")
            else -> !prefs.isSafeVaultFile(f.path)
        }
    }

    BackHandler(enabled = activeViewerIndex != -1 || infoSheetFile != null || infoSheetFolder != null || selectedFilesList.isNotEmpty() || selectedCategoryTitle != null || folderPathHistory.size > 1 || currentFolderDirectoryPath != "Internal Storage" || isSearching) {
        if (activeViewerIndex != -1) {
            activeViewerIndex = -1
        } else if (infoSheetFile != null || infoSheetFolder != null) {
            infoSheetFile = null
            infoSheetFolder = null
            showDetailedMetadataDialog = false
        } else if (selectedFilesList.isNotEmpty()) {
            selectedFilesList.clear()
            isSelectionModeActive = false
        } else if (localSearchQuery.isNotEmpty()) {
            localSearchQuery = ""
        } else if (selectedCategoryTitle != null) {
            val wasSafe = selectedCategoryTitle == "Safe folder" || selectedCategoryTitle == "Safe Folder"
            selectedCategoryTitle = null
            onCategorySelected(null)
            onBackToCategories()
            if (wasSafe) {
                Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
            }
        } else if (folderPathHistory.size > 1) {
            val wasSafe = currentFolderDirectoryPath == "Internal Storage/Private Safe Vault"
            folderPathHistory.removeAt(folderPathHistory.size - 1)
            currentFolderDirectoryPath = folderPathHistory.last()
            if (currentFolderDirectoryPath == "Internal Storage") {
                onCategorySelected(null)
            }
            if (wasSafe) {
                Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
            }
        } else if (currentFolderDirectoryPath != "Internal Storage") {
            val wasSafe = currentFolderDirectoryPath == "Internal Storage/Private Safe Vault"
            currentFolderDirectoryPath = "Internal Storage"
            folderPathHistory.clear()
            folderPathHistory.add("Internal Storage")
            onCategorySelected(null)
            if (wasSafe) {
                Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
            }
        }
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

    val categoriesData = remember(cloudFileList, monthlyTimelineFolders) {
        fun getCategoryFiles(title: String): List<UploadedFile> {
            return cloudFileList.filter { f -> isFileMatchingCategory(f, title) }
        }

        listOf(
            ExplorerCategoryModel(
                title = "Downloads",
                count = getCategoryFiles("Downloads").size,
                totalSizeBytes = getCategoryFiles("Downloads").sumOf { it.size },
                icon = Icons.Default.Download,
                iconTint = Color(0xFF1A73E8),
                containerTint = Color(0xFFE8F0FE)
            ),
            ExplorerCategoryModel(
                title = "Images",
                count = getCategoryFiles("Images").size,
                totalSizeBytes = getCategoryFiles("Images").sumOf { it.size },
                icon = Icons.Default.Image,
                iconTint = Color(0xFF8430CE),
                containerTint = Color(0xFFF3E8FD)
            ),
            ExplorerCategoryModel(
                title = "Videos",
                count = getCategoryFiles("Videos").size,
                totalSizeBytes = getCategoryFiles("Videos").sumOf { it.size },
                icon = Icons.Default.Videocam,
                iconTint = Color(0xFFD93025),
                containerTint = Color(0xFFFCE8E6)
            ),
            ExplorerCategoryModel(
                title = "Audio",
                count = getCategoryFiles("Audio").size,
                totalSizeBytes = getCategoryFiles("Audio").sumOf { it.size },
                icon = Icons.Default.MusicNote,
                iconTint = Color(0xFFE37400),
                containerTint = Color(0xFFFEF7E0)
            ),
            ExplorerCategoryModel(
                title = "Documents & other",
                count = getCategoryFiles("Documents & other").size,
                totalSizeBytes = getCategoryFiles("Documents & other").sumOf { it.size },
                icon = Icons.Default.Description,
                iconTint = Color(0xFF188038),
                containerTint = Color(0xFFE6F4EA)
            ),
            ExplorerCategoryModel(
                title = "Apps",
                count = getCategoryFiles("Apps").size,
                totalSizeBytes = getCategoryFiles("Apps").size * 25_000_000L,
                icon = Icons.Default.Android,
                iconTint = Color(0xFF1E8E3E),
                containerTint = Color(0xFFE8F0FE)
            ),
            ExplorerCategoryModel(
                title = "Archives",
                count = getCategoryFiles("Archives").size,
                totalSizeBytes = getCategoryFiles("Archives").sumOf { it.size },
                icon = Icons.Default.Archive,
                iconTint = Color(0xFF00796B),
                containerTint = Color(0xFFE0F2F1)
            ),
            ExplorerCategoryModel(
                title = "Monthly Archive",
                count = monthlyTimelineFolders.size,
                totalSizeBytes = cloudFileList.sumOf { it.size },
                icon = Icons.Default.CalendarMonth,
                iconTint = Color(0xFF5E35B1),
                containerTint = Color(0xFFEDE7F6)
            )
        )
    }

    val recentCloudFiles = remember(cloudFileList) {
        cloudFileList.filter { it.telegramFileId != "DIR_MARKER" }
            .sortedByDescending { it.timestamp }
            .take(12)
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

    val currentPathFiles = remember(cloudFileList, selectedCategoryTitle, currentFolderDirectoryPath) {
        if (currentFolderDirectoryPath.startsWith("Timeline/")) {
            val monthName = currentFolderDirectoryPath.removePrefix("Timeline/")
            val format = java.text.SimpleDateFormat("yyyy - MMMM", Locale.US)
            cloudFileList.filter { f ->
                val dateKey = if (f.timestamp > 0) format.format(java.util.Date(f.timestamp)) else "Unknown Date"
                dateKey == monthName
            }
        } else if (selectedCategoryTitle != null) {
            val title = selectedCategoryTitle!!
            if (title == "All Files") {
                cloudFileList.filter { !prefs.isSafeVaultFile(it.path) }
            } else if (title == "Safe folder" || title == "Safe Folder" || title == "Private Safe Vault") {
                cloudFileList.filter { prefs.isSafeVaultFile(it.path) || it.path.contains("Safe") || it.path.contains(".private") }
            } else if (title == "Starred") {
                cloudFileList.filter { prefs.isFileStarred(it.path) && !prefs.isSafeVaultFile(it.path) }
            } else {
                cloudFileList.filter { f -> isFileMatchingCategory(f, title) && !prefs.isSafeVaultFile(f.path) }
            }
        } else if (currentFolderDirectoryPath == "Internal Storage/Private Safe Vault") {
            cloudFileList.filter { prefs.isSafeVaultFile(it.path) || it.path.contains("Safe") || it.path.contains(".private") }
        } else if (currentFolderDirectoryPath == "Internal Storage/Phone Folders" || currentFolderDirectoryPath == "Internal Storage") {
            cloudFileList.filter { !prefs.isSafeVaultFile(it.path) }
        } else {
            cloudFileList.filter { f ->
                if (prefs.isSafeVaultFile(f.path)) return@filter false
                val norm = normalizePath(f.path)
                val parent = File(norm).parent ?: "Internal Storage"
                parent.replace("\\", "/") == currentFolderDirectoryPath || norm.startsWith(currentFolderDirectoryPath)
            }
        }
    }

    val activeDisplayFiles = remember(cloudFileList, currentPathFiles, effectiveSearchQuery, sortOption, selectedFilterType) {
        val q = effectiveSearchQuery.trim()
        var list = if (q.isNotEmpty()) {
            cloudFileList.filter { it.fileName.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true) }
        } else {
            currentPathFiles
        }
        if (selectedFilterType != ExplorerFilterType.ALL) {
            list = list.filter { f ->
                val name = f.fileName.lowercase()
                when (selectedFilterType) {
                    ExplorerFilterType.PHOTOS -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic")
                    ExplorerFilterType.VIDEOS -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov")
                    ExplorerFilterType.DOCUMENTS -> name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".txt") || name.endsWith(".xlsx")
                    ExplorerFilterType.AUDIO -> name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".ogg")
                    ExplorerFilterType.ARCHIVES -> name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz")
                    else -> true
                }
            }
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSearching && selectedCategoryTitle == null && currentFolderDirectoryPath == "Internal Storage") {
            // GOOGLE FILES BROWSE HUB
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
            ) {
                // 1. Recent Files Horizontal Carousel
                if (recentCloudFiles.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(recentCloudFiles) { file ->
                                    RecentFileGoogleCard(
                                        cloudFile = file,
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            val idx = activeDisplayFiles.indexOfFirst { it.telegramFileId == file.telegramFileId }
                                            if (idx != -1) {
                                                activeViewerIndex = idx
                                            } else {
                                                FileViewerHelper.openFile(context, file.path, file.fileName)
                                            }
                                        },
                                        onMoreOptions = {
                                            triggerHapticFeedback(context, haptic)
                                            actionMenuFile = file
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Categories (2-Column Grid)
                item {
                    Column {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val pairs = categoriesData.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pairs.forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GoogleCategoryCard(
                                        category = pair[0],
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            if (pair[0].title == "Monthly Archive") {
                                                currentFolderDirectoryPath = "Timeline"
                                            } else {
                                                selectedCategoryTitle = pair[0].title
                                                onCategorySelected(pair[0].title)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (pair.size > 1) {
                                        GoogleCategoryCard(
                                            category = pair[1],
                                            onClick = {
                                                triggerHapticFeedback(context, haptic)
                                                if (pair[1].title == "Monthly Archive") {
                                                    currentFolderDirectoryPath = "Timeline"
                                                } else {
                                                    selectedCategoryTitle = pair[1].title
                                                    onCategorySelected(pair[1].title)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Collections (Starred & Safe folder)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Collections",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        GoogleCollectionItemCard(
                            title = "Starred",
                            subtitle = "${cloudFileList.count { prefs.isFileStarred(it.path) && !prefs.isSafeVaultFile(it.path) }} files",
                            icon = Icons.Default.Star,
                            iconTint = Color(0xFFF59E0B),
                            iconBg = Color(0xFFFEF3C7),
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedCategoryTitle = "Starred"
                                onCategorySelected("Starred")
                            }
                        )
                        GoogleCollectionItemCard(
                            title = "Safe folder",
                            subtitle = "${cloudFileList.count { prefs.isSafeVaultFile(it.path) }} files • PIN/Biometric",
                            icon = Icons.Default.Lock,
                            iconTint = TelegramBlue,
                            iconBg = TelegramBlue.copy(alpha = 0.12f),
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                val activity = context as? FragmentActivity
                                if (activity != null) {
                                    BiometricHelper.showBiometricPrompt(
                                        activity = activity,
                                        title = "Unlock Safe Folder",
                                        subtitle = "Verify your identity to access private safe",
                                        onSuccess = {
                                            selectedCategoryTitle = "Safe folder"
                                            currentFolderDirectoryPath = "Internal Storage/Private Safe Vault"
                                            folderPathHistory.add("Internal Storage/Private Safe Vault")
                                            onCategorySelected("Safe folder")
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    selectedCategoryTitle = "Safe folder"
                                    currentFolderDirectoryPath = "Internal Storage/Private Safe Vault"
                                    folderPathHistory.add("Internal Storage/Private Safe Vault")
                                    onCategorySelected("Safe folder")
                                }
                            }
                        )
                    }
                }

                // 5. Storage devices (Internal storage & Telegram Cloud Storage)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Storage devices",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        GoogleStorageDeviceCard(
                            title = "Internal storage",
                            subtitle = "${deviceStorage.freeFormatted} free • ${deviceStorage.totalFormatted}",
                            progress = deviceStorage.usedPercentage,
                            icon = Icons.Default.Smartphone,
                            iconTint = TelegramBlue,
                            iconBg = TelegramBlue.copy(alpha = 0.12f),
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                currentFolderDirectoryPath = "Internal Storage/Phone Folders"
                                folderPathHistory.add("Internal Storage/Phone Folders")
                            }
                        )
                        GoogleStorageDeviceCard(
                            title = "Telegram Cloud Storage",
                            subtitle = "${cloudFileList.size} files backed up • ${formatFileSize(cloudFileList.sumOf { it.size })}",
                            progress = null,
                            icon = Icons.Default.Cloud,
                            iconTint = TelegramEmerald,
                            iconBg = TelegramEmerald.copy(alpha = 0.12f),
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedCategoryTitle = "All Files"
                                onCategorySelected("All Files")
                            }
                        )
                    }
                }
            }
        } else if (currentFolderDirectoryPath == "Timeline") {
            // 📅 MONTHLY CLOUD ARCHIVE VIEW
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                currentFolderDirectoryPath = "Internal Storage"
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TelegramBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Monthly Cloud Archive", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
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
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
        } else if (currentFolderDirectoryPath == "Internal Storage/Phone Folders") {
            // 📂 PHONE STORAGE REPLICA VIEW
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                currentFolderDirectoryPath = "Internal Storage"
                                folderPathHistory.clear()
                                folderPathHistory.add("Internal Storage")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TelegramBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Internal Storage", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
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
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
        } else {
            val titleText = if (isSearching) "Search: \"$effectiveSearchQuery\"" else (selectedCategoryTitle ?: File(currentFolderDirectoryPath).name)
            val isSafeVault = selectedCategoryTitle == "Safe folder" || selectedCategoryTitle == "Safe Folder" || currentFolderDirectoryPath == "Internal Storage/Private Safe Vault"
            val isStarred = selectedCategoryTitle == "Starred"

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        if (localSearchQuery.isNotEmpty()) {
                                            localSearchQuery = ""
                                        } else if (selectedCategoryTitle != null) {
                                            val wasSafe = selectedCategoryTitle == "Safe folder" || selectedCategoryTitle == "Safe Folder"
                                            selectedCategoryTitle = null
                                            onCategorySelected(null)
                                            onBackToCategories()
                                            if (wasSafe) {
                                                Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
                                            }
                                        } else if (folderPathHistory.size > 1) {
                                            val wasSafe = currentFolderDirectoryPath == "Internal Storage/Private Safe Vault"
                                            folderPathHistory.removeAt(folderPathHistory.size - 1)
                                            currentFolderDirectoryPath = folderPathHistory.last()
                                            if (currentFolderDirectoryPath == "Internal Storage") {
                                                onCategorySelected(null)
                                            }
                                            if (wasSafe) {
                                                Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val wasSafe = currentFolderDirectoryPath == "Internal Storage/Private Safe Vault"
                                            currentFolderDirectoryPath = "Internal Storage"
                                            folderPathHistory.clear()
                                            folderPathHistory.add("Internal Storage")
                                            onCategorySelected(null)
                                            if (wasSafe) {
                                                Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSafeVault) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else if (isStarred) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = if (isSafeVault) "Safe folder" else if (isStarred) "Starred" else titleText,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${activeDisplayFiles.size} items" + if (isSafeVault) " • Protected" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (isSafeVault) {
                                    IconButton(
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            selectedCategoryTitle = null
                                            currentFolderDirectoryPath = "Internal Storage"
                                            folderPathHistory.clear()
                                            folderPathHistory.add("Internal Storage")
                                            onCategorySelected(null)
                                            onBackToCategories()
                                            Toast.makeText(context, "🔒 Safe Folder locked", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockReset,
                                            contentDescription = "Lock Safe Folder",
                                            tint = TelegramBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                var showSortDropdown by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            showSortDropdown = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortDropdown,
                                        onDismissRequest = { showSortDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Date" + if (sortOption == FileSortOption.DATE) " ✓" else "") },
                                            onClick = {
                                                triggerHapticFeedback(context, haptic)
                                                sortOption = FileSortOption.DATE
                                                showSortDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Name" + if (sortOption == FileSortOption.NAME) " ✓" else "") },
                                            onClick = {
                                                triggerHapticFeedback(context, haptic)
                                                sortOption = FileSortOption.NAME
                                                showSortDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Size" + if (sortOption == FileSortOption.SIZE) " ✓" else "") },
                                            onClick = {
                                                triggerHapticFeedback(context, haptic)
                                                sortOption = FileSortOption.SIZE
                                                showSortDropdown = false
                                            }
                                        )
                                    }
                                }

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
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = "Download as ZIP",
                                        tint = TelegramEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        selectedViewMode = (selectedViewMode + 1) % 3
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = when (selectedViewMode) {
                                            0 -> Icons.Default.GridView
                                            1 -> Icons.Default.GridOn
                                            else -> Icons.AutoMirrored.Filled.List
                                        },
                                        contentDescription = "Toggle View Mode",
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Smart Filter Chips Row
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        items(ExplorerFilterType.values()) { filter ->
                            FilterChip(
                                selected = selectedFilterType == filter,
                                onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    selectedFilterType = filter
                                },
                                leadingIcon = if (filter.icon != null) {
                                    { Icon(filter.icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) }
                            )
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
                                            if (isSelectionModeActive || selectedFilesList.isNotEmpty()) {
                                                isSelectionModeActive = true
                                                if (!isSelected) selectedFilesList.add(cloudFile)
                                            } else {
                                                actionMenuFile = cloudFile
                                            }
                                        },
                                        onMoreOptions = {
                                            triggerHapticFeedback(context, haptic)
                                            actionMenuFile = cloudFile
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
                                            if (isSelectionModeActive || selectedFilesList.isNotEmpty()) {
                                                isSelectionModeActive = true
                                                if (!isSelected) selectedFilesList.add(cloudFile)
                                            } else {
                                                actionMenuFile = cloudFile
                                            }
                                        },
                                        onMoreOptions = {
                                            triggerHapticFeedback(context, haptic)
                                            actionMenuFile = cloudFile
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isSelectionModeActive && selectedFilesList.isEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            triggerHapticFeedback(context, haptic)
                            if (isSafeVault) {
                                safeFolderUploadLauncher.launch("*/*")
                            } else {
                                personalFileUploadLauncher.launch("*/*")
                            }
                        },
                        containerColor = TelegramBlue,
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isSafeVault) "Add to Safe Folder" else "Upload Files",
                            modifier = Modifier.size(26.dp)
                        )
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
    onLongClick: () -> Unit,
    onMoreOptions: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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

                    IconButton(
                        onClick = onMoreOptions,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                    }
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
    onLongClick: () -> Unit,
    onMoreOptions: () -> Unit
) {
    val ext = cloudFile.fileName.substringAfterLast('.', "").uppercase()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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

            if (!isSelectionMode) {
                IconButton(
                    onClick = onMoreOptions,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "File Options",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleFilesSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search in Files",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentFileGoogleCard(
    cloudFile: UploadedFile,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(118.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnailView(
                    fileName = cloudFile.fileName,
                    filePath = cloudFile.path,
                    telegramFileId = cloudFile.telegramFileId,
                    iconSize = 44.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cloudFile.fileName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(cloudFile.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(
                    onClick = onMoreOptions,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleCategoryCard(
    category: ExplorerCategoryModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(category.containerTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if (category.totalSizeBytes > 0) {
                    formatFileSize(category.totalSizeBytes)
                } else {
                    "${category.count} files"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun GoogleCollectionItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun GoogleStorageDeviceCard(
    title: String,
    subtitle: String,
    progress: Float?,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            if (progress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = iconTint,
                    trackColor = iconTint.copy(alpha = 0.15f)
                )
            }
        }
    }
}

