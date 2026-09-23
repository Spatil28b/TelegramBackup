package com.example.telegrambackup.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.common.FileThumbnailView
import com.example.telegrambackup.ui.common.OsFolderIcon
import com.example.telegrambackup.ui.common.FolderType
import com.example.telegrambackup.ui.explorer.formatFileSize
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.ZipBackupDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class MonthGroup(
    val year: Int,
    val month: Int,
    val label: String,
    val files: List<UploadedFile>,
    val totalSize: Long
)

enum class MonthlyViewMode {
    FOLDER_WISE,
    CATEGORY_WISE
}

const val SANITIZED_DEFAULT_TIMESTAMP = 1790213700000L // September 2026 default baseline

fun getSafeTimestamp(file: UploadedFile): Long {
    if (file.timestamp > 946684800000L && file.timestamp != 1784030400000L) {
        return file.timestamp
    }
    val local = FileViewerHelper.findLocalFile(file.path, file.fileName)
    if (local != null && local.exists() && local.lastModified() > 946684800000L) {
        return local.lastModified()
    }
    return SANITIZED_DEFAULT_TIMESTAMP
}

/**
 * Normalizes an uploaded file path to relative path segments from internal storage root.
 * E.g.: "/storage/emulated/0/DCIM/Camera/IMG_01.jpg" -> ["DCIM", "Camera"]
 *       "Internal Storage/Download/Report.pdf" -> ["Download"]
 *       "DCIM/Screenshots/shot.png" -> ["DCIM", "Screenshots"]
 * If the path has no directory information (e.g. just "IMG_001.jpg"), it categorizes
 * by file extension into standard internal storage folders (Pictures, Movies, Documents, Music, Download)
 * to ensure a 100% structured file manager replica without flat dumps.
 */
fun extractPathSegments(fullPath: String, fileName: String = ""): List<String> {
    var cleaned = fullPath.replace('\\', '/').trim()
    val roots = listOf(
        "/storage/emulated/0/",
        "storage/emulated/0/",
        "/sdcard/",
        "sdcard/",
        "Internal Storage/",
        "internal storage/",
        "/Internal Storage/",
        "/storage/",
        "storage/",
        "primary:",
        "raw:"
    )
    for (root in roots) {
        if (cleaned.startsWith(root, ignoreCase = true)) {
            cleaned = cleaned.substring(root.length)
            break
        }
    }
    // Remove Windows drive letters like C:/ or D:/
    if (cleaned.matches(Regex("^[A-Za-z]:/.*"))) {
        cleaned = cleaned.substring(3)
    }
    cleaned = cleaned.trimStart('/')

    val parts = cleaned.split('/').filter { it.isNotBlank() }
    val effectiveFileName = if (fileName.isNotBlank()) fileName else parts.lastOrNull() ?: ""

    if (parts.size > 1) {
        // Path contains directory components (e.g. ["DCIM", "Camera", "IMG_01.jpg"])
        return parts.dropLast(1)
    } else {
        // Path has no subfolders or is just a single file name.
        // Check if the original path explicitly specified the root storage:
        val wasExplicitRoot = fullPath.startsWith("/storage/emulated/0/") && !fullPath.removePrefix("/storage/emulated/0/").contains('/')
        if (wasExplicitRoot) {
            return emptyList()
        }

        // Intelligently categorize into standard Android internal storage folders:
        val ext = effectiveFileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif" -> listOf("Pictures")
            "mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "flv", "m4v" -> listOf("Movies")
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma" -> listOf("Music")
            "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml" -> listOf("Documents")
            "apk", "xapk", "apks" -> listOf("Download", "APKs")
            "zip", "rar", "7z", "tar", "gz", "bz2" -> listOf("Download")
            else -> listOf("Download")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MonthlyArchiveScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var allFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var monthGroups by remember { mutableStateOf<List<MonthGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Year Filter state (-1 = All Years)
    var selectedYear by remember { mutableIntStateOf(-1) }

    // Active Drilled-Down Month
    var activeMonthGroup by remember { mutableStateOf<MonthGroup?>(null) }
    var monthlyViewMode by remember { mutableStateOf(MonthlyViewMode.FOLDER_WISE) }

    // Path segments for drilldown in FOLDER_WISE mode (e.g. ["DCIM", "Camera"])
    var currentPathSegments by remember { mutableStateOf<List<String>>(emptyList()) }

    // Selected Category in CATEGORY_WISE mode
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    // Multi-select state
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    var fileForMenu by remember { mutableStateOf<UploadedFile?>(null) }
    val itemDateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    var activeAudioPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeVideoPlayerItem by remember { mutableStateOf<Pair<String, String>?>(null) }

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

    fun loadFiles() {
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.uploadedFileDao()
                dao.sanitizeCorruptedTimestamps(System.currentTimeMillis())

                val legacyFiles = dao.getAllRawFiles().filter {
                    it.timestamp == 1784030400000L || it.timestamp < 946684800000L
                }
                if (legacyFiles.isNotEmpty()) {
                    val fixed = legacyFiles.map { f ->
                        val local = FileViewerHelper.findLocalFile(f.path, f.fileName)
                        val ts = if (local != null && local.exists() && local.lastModified() > 946684800000L) {
                            local.lastModified()
                        } else {
                            System.currentTimeMillis()
                        }
                        f.copy(timestamp = ts)
                    }
                    dao.insertAll(fixed)
                }
            } catch (_: Exception) {}

            val files = try {
                AppDatabase.getDatabase(context).uploadedFileDao().getAllFiles()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                allFiles = files.map { f ->
                    val safe = getSafeTimestamp(f)
                    if (f.timestamp != safe) f.copy(timestamp = safe) else f
                }
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    // Available years from files
    val availableYears = remember(allFiles) {
        val cal = Calendar.getInstance()
        val years = allFiles
            .filter { it.telegramFileId != "DIR_MARKER" && !it.isDeleted }
            .map {
                cal.timeInMillis = getSafeTimestamp(it)
                cal.get(Calendar.YEAR)
            }.distinct().sortedDescending()
        if (years.isEmpty()) listOf(Calendar.getInstance().get(Calendar.YEAR)) else years
    }

    // Group files by month/year whenever allFiles or filter changes
    LaunchedEffect(allFiles, selectedYear) {
        val cal = Calendar.getInstance()
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val validFiles = allFiles.filter { it.telegramFileId != "DIR_MARKER" && !it.isDeleted }
        val grouped = validFiles
            .groupBy { file ->
                cal.timeInMillis = getSafeTimestamp(file)
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                Pair(y, m)
            }
            .filter { (key, _) ->
                selectedYear == -1 || key.first == selectedYear
            }
            .map { (key, files) ->
                val sortedFiles = files.map { f ->
                    val safe = getSafeTimestamp(f)
                    if (f.timestamp != safe) f.copy(timestamp = safe) else f
                }.sortedByDescending { it.timestamp }
                MonthGroup(
                    year = key.first,
                    month = key.second,
                    label = "${monthNames[key.second]} ${key.first}",
                    files = sortedFiles,
                    totalSize = sortedFiles.sumOf { it.size }
                )
            }
            .sortedByDescending { it.year * 100 + it.month }

        monthGroups = grouped
    }

    // Handle back button for selection mode and hierarchical folder navigation
    BackHandler(enabled = isSelectionMode || (activeMonthGroup != null)) {
        if (isSelectionMode) {
            selectedPaths = emptySet()
            isSelectionMode = false
        } else if (activeMonthGroup != null) {
            if (monthlyViewMode == MonthlyViewMode.FOLDER_WISE && currentPathSegments.isNotEmpty()) {
                currentPathSegments = currentPathSegments.dropLast(1)
            } else {
                activeMonthGroup = null
                currentPathSegments = emptyList()
                selectedCategoryFilter = "ALL"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Multi-select action bar (Google Files Style)
        if (isSelectionMode && selectedPaths.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TelegramBlue.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${selectedPaths.size} selected",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val selectedFilesList = allFiles.filter { selectedPaths.contains(it.path) }
                                val count = selectedFilesList.size
                                Toast.makeText(context, "📦 Starting ZIP download for $count files...", Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
                                    ZipBackupDownloader.downloadFilesAsZip(
                                        context = context,
                                        filesToDownload = selectedFilesList,
                                        archiveTitle = "Monthly_Selection_${System.currentTimeMillis()}"
                                    )
                                }
                                selectedPaths = emptySet()
                                isSelectionMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ZIP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        TextButton(
                            onClick = {
                                selectedPaths = emptySet()
                                isSelectionMode = false
                            }
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (activeMonthGroup == null) {
            // =========================================================================
            // 📅 ROOT LEVEL: MONTH LIST OVERVIEW
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TelegramBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monthly Archive", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "${monthGroups.sumOf { it.files.size }} files • ${formatFileSize(monthGroups.sumOf { it.totalSize })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TelegramEmerald.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${monthGroups.size} Months",
                            style = MaterialTheme.typography.labelSmall.copy(color = TelegramEmerald, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Year Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedYear == -1,
                        onClick = { selectedYear = -1 },
                        label = { Text("All Time", fontWeight = FontWeight.Medium) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelegramBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(availableYears) { year ->
                    FilterChip(
                        selected = selectedYear == year,
                        onClick = { selectedYear = year },
                        label = { Text("$year", fontWeight = FontWeight.Medium) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelegramBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TelegramBlue)
                }
            } else if (monthGroups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TelegramBlue.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No Files for ${if (selectedYear == -1) "Selected Period" else "$selectedYear"}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Files backed up in each month will appear here in chronological timeline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(monthGroups, key = { it.label }) { group ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeMonthGroup = group
                                    currentPathSegments = emptyList()
                                    selectedCategoryFilter = "ALL"
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TelegramBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${group.files.size} files • ${formatFileSize(group.totalSize)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // 1-Tap Download Month ZIP
                                IconButton(
                                    onClick = {
                                        Toast.makeText(context, "📦 Starting ZIP download for ${group.label} (${group.files.size} files)...", Toast.LENGTH_SHORT).show()
                                        scope.launch(Dispatchers.IO) {
                                            ZipBackupDownloader.downloadFilesAsZip(
                                                context = context,
                                                filesToDownload = group.files,
                                                archiveTitle = "Monthly_Backup_${group.label.replace(" ", "_")}"
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download Month ZIP", tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open Folder",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // 📂 MONTH DRILL-DOWN: 100% EXACT INTERNAL STORAGE FOLDER TREE + CATEGORIES
            // =========================================================================
            val currentGroup = activeMonthGroup!!

            // Navigation Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (monthlyViewMode == MonthlyViewMode.FOLDER_WISE && currentPathSegments.isNotEmpty()) {
                            currentPathSegments = currentPathSegments.dropLast(1)
                        } else {
                            activeMonthGroup = null
                            currentPathSegments = emptyList()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentGroup.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentGroup.files.size} files • ${formatFileSize(currentGroup.totalSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Download Whole Month ZIP
                IconButton(
                    onClick = {
                        Toast.makeText(context, "📦 Starting ZIP download for ${currentGroup.label}...", Toast.LENGTH_SHORT).show()
                        scope.launch(Dispatchers.IO) {
                            ZipBackupDownloader.downloadFilesAsZip(
                                context = context,
                                filesToDownload = currentGroup.files,
                                archiveTitle = "Monthly_Backup_${currentGroup.label.replace(" ", "_")}"
                            )
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download ZIP", tint = TelegramBlue, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Toggle: [Folder Structure | Category Wise]
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (monthlyViewMode == MonthlyViewMode.FOLDER_WISE) TelegramBlue else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                monthlyViewMode = MonthlyViewMode.FOLDER_WISE
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (monthlyViewMode == MonthlyViewMode.FOLDER_WISE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Folder Structure",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (monthlyViewMode == MonthlyViewMode.FOLDER_WISE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (monthlyViewMode == MonthlyViewMode.CATEGORY_WISE) TelegramBlue else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                monthlyViewMode = MonthlyViewMode.CATEGORY_WISE
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = null,
                                tint = if (monthlyViewMode == MonthlyViewMode.CATEGORY_WISE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Category Wise",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (monthlyViewMode == MonthlyViewMode.CATEGORY_WISE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (monthlyViewMode == MonthlyViewMode.FOLDER_WISE) {
                // =====================================================================
                // 100% EXACT INTERNAL STORAGE FOLDER STRUCTURE DRILLDOWN
                // =====================================================================
                // Breadcrumbs bar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                text = "Internal Storage",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (currentPathSegments.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (currentPathSegments.isEmpty()) TelegramBlue else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.clickable {
                                    currentPathSegments = emptyList()
                                }
                            )
                        }

                        itemsIndexed(currentPathSegments) { idx, segment ->
                            Text(" > ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            val isLast = idx == currentPathSegments.size - 1
                            Text(
                                text = segment,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isLast) TelegramBlue else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.clickable {
                                    currentPathSegments = currentPathSegments.take(idx + 1)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Parse files at current folder level
                val depth = currentPathSegments.size

                // Map files with their relative segments
                val filesWithSegments = remember(currentGroup.files) {
                    currentGroup.files
                        .filter { it.telegramFileId != "DIR_MARKER" && !it.isDeleted }
                        .map { file ->
                            Pair(file, extractPathSegments(file.path, file.fileName))
                        }
                }

                // Child folders directly under currentPathSegments
                val childFolders = remember(filesWithSegments, currentPathSegments) {
                    val matching = filesWithSegments.filter { (_, segs) ->
                        segs.size > depth && segs.take(depth) == currentPathSegments
                    }
                    matching
                        .groupBy { (_, segs) -> segs[depth] }
                        .map { (folderName, items) ->
                            val folderFiles = items.map { it.first }
                            Triple(folderName, folderFiles.size, folderFiles.sumOf { it.size })
                        }
                        .sortedBy { it.first.lowercase() }
                }

                // Files directly at this directory level
                val directFiles = remember(filesWithSegments, currentPathSegments) {
                    filesWithSegments
                        .filter { (_, segs) ->
                            segs == currentPathSegments
                        }
                        .map { it.first }
                        .sortedByDescending { it.timestamp }
                }

                if (childFolders.isEmpty() && directFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            OsFolderIcon(size = 56.dp, type = FolderType.GENERIC)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("This folder is empty.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Render Subfolders
                        items(childFolders, key = { "folder_${it.first}" }) { (folderName, fileCount, folderSize) ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPathSegments = currentPathSegments + folderName
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OsFolderIcon(
                                        size = 46.dp,
                                        type = when (folderName.lowercase()) {
                                            "dcim", "pictures", "photos" -> FolderType.PHOTOS
                                            "movies", "videos" -> FolderType.VIDEOS
                                            "music", "audio" -> FolderType.MUSIC
                                            "download", "downloads" -> FolderType.DOWNLOADS
                                            "documents" -> FolderType.DOCUMENTS
                                            else -> FolderType.GENERIC
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$fileCount files • ${formatFileSize(folderSize)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Render Direct Files (Google Files Style Selection UX)
                        items(directFiles, key = { it.path }) { file ->
                            val isSelected = selectedPaths.contains(file.path)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedPaths = if (isSelected) selectedPaths - file.path else selectedPaths + file.path
                                                if (selectedPaths.isEmpty()) isSelectionMode = false
                                            } else {
                                                val resolvedLocal = FileViewerHelper.findLocalFile(file.path, file.fileName)
                                                val hasLocal = resolvedLocal != null && resolvedLocal.exists() && resolvedLocal.length() > 0
                                                val effectivePath = resolvedLocal?.absolutePath ?: file.path

                                                if (hasLocal) {
                                                    if (FileViewerHelper.isAudioFile(file.fileName)) {
                                                        activeAudioPlayerItem = Pair(effectivePath, file.fileName)
                                                    } else if (FileViewerHelper.isVideoFile(file.fileName)) {
                                                        activeVideoPlayerItem = Pair(effectivePath, file.fileName)
                                                    } else {
                                                        FileViewerHelper.openFile(context, effectivePath, file.fileName, file.telegramFileId)
                                                    }
                                                } else {
                                                    // Cloud file or moved file: open via FileViewerHelper which fetches all chunks cleanly
                                                    FileViewerHelper.openFile(context, file.path, file.fileName, file.telegramFileId)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isSelectionMode = true
                                            selectedPaths = selectedPaths + file.path
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedPaths = if (checked) selectedPaths + file.path else selectedPaths - file.path
                                                if (selectedPaths.isEmpty()) isSelectionMode = false
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                        FileThumbnailView(
                                            fileName = file.fileName,
                                            filePath = file.path,
                                            telegramFileId = file.telegramFileId,
                                            iconSize = 34.dp,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.fileName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${formatFileSize(file.size)} • ${itemDateFormat.format(Date(file.timestamp))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (!isSelectionMode) {
                                        IconButton(
                                            onClick = { fileForMenu = file },
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
            } else {
                // =====================================================================
                // 🏷️ CATEGORY WISE VIEW
                // =====================================================================
                val categories = listOf("ALL", "Images", "Videos", "Audio", "Documents", "APKs", "Archives", "Others")

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat, fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelegramBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                val filteredCategoryFiles = remember(currentGroup.files, selectedCategoryFilter) {
                    if (selectedCategoryFilter == "ALL") {
                        currentGroup.files
                    } else {
                        currentGroup.files.filter { file ->
                            val ext = file.fileName.substringAfterLast('.', "").lowercase()
                            when (selectedCategoryFilter) {
                                "Images" -> ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic")
                                "Videos" -> ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp")
                                "Audio" -> ext in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "opus")
                                "Documents" -> ext in listOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv")
                                "APKs" -> ext in listOf("apk", "xapk", "apks")
                                "Archives" -> ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2")
                                "Others" -> ext !in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "mp3", "wav", "aac", "flac", "ogg", "m4a", "opus", "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "apk", "xapk", "apks", "zip", "rar", "7z", "tar", "gz", "bz2")
                                else -> true
                            }
                        }
                    }
                }

                if (filteredCategoryFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files in this category.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCategoryFiles, key = { it.path }) { file ->
                            val isSelected = selectedPaths.contains(file.path)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedPaths = if (isSelected) selectedPaths - file.path else selectedPaths + file.path
                                                if (selectedPaths.isEmpty()) isSelectionMode = false
                                            } else {
                                                val localFile = File(file.path)
                                                val hasLocal = localFile.exists() && localFile.length() > 0

                                                if (FileViewerHelper.isAudioFile(file.fileName)) {
                                                    activeAudioPlayerItem = Pair(file.path, file.fileName)
                                                } else if (FileViewerHelper.isVideoFile(file.fileName) && hasLocal) {
                                                    activeVideoPlayerItem = Pair(file.path, file.fileName)
                                                } else if (hasLocal || file.telegramFileId.isNotEmpty()) {
                                                    FileViewerHelper.openFile(context, file.path, file.fileName, file.telegramFileId)
                                                } else {
                                                    Toast.makeText(context, "Opening ${file.fileName}...", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isSelectionMode = true
                                            selectedPaths = selectedPaths + file.path
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedPaths = if (checked) selectedPaths + file.path else selectedPaths - file.path
                                                if (selectedPaths.isEmpty()) isSelectionMode = false
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                        FileThumbnailView(
                                            fileName = file.fileName,
                                            filePath = file.path,
                                            telegramFileId = file.telegramFileId,
                                            iconSize = 34.dp,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.fileName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${formatFileSize(file.size)} • ${extractPathSegments(file.path).joinToString("/")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!isSelectionMode) {
                                        IconButton(
                                            onClick = { fileForMenu = file },
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

    if (fileForMenu != null) {
        com.example.telegrambackup.ui.common.FileActionMenuModal(
            file = fileForMenu!!,
            onDismiss = { fileForMenu = null },
            onFileRenamed = { _, _, _ ->
                fileForMenu = null
                loadFiles()
            },
            onFileDeleted = { delFile ->
                selectedPaths = selectedPaths - delFile.path
                fileForMenu = null
                loadFiles()
            }
        )
    }
}
