package com.example.telegrambackup.ui.main

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.explorer.formatFileSize
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.ZipBackupDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class MonthGroup(
    val year: Int,
    val month: Int,
    val label: String,
    val files: List<UploadedFile>,
    val totalSize: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MonthlyArchiveScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var monthGroups by remember { mutableStateOf<List<MonthGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Filter state
    var filterYear by remember { mutableIntStateOf(-1) } // -1 = All years by default
    var filterMonth by remember { mutableIntStateOf(-1) } // -1 = All months by default
    var showFilterMenu by remember { mutableStateOf(false) }

    // Multi-select state
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    // Expanded months
    var expandedMonths by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadFiles() {
        scope.launch(Dispatchers.IO) {
            val files = try {
                AppDatabase.getDatabase(context).uploadedFileDao().getAllFiles()
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                allFiles = files
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    // Group files by month/year whenever allFiles or filter changes
    LaunchedEffect(allFiles, filterYear, filterMonth) {
        val cal = Calendar.getInstance()
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val grouped = allFiles
            .groupBy { file ->
                cal.timeInMillis = file.timestamp
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                Pair(y, m)
            }
            .filter { (key, _) ->
                (filterYear == -1 || key.first == filterYear) && (filterMonth == -1 || key.second == filterMonth)
            }
            .map { (key, files) ->
                val sortedFiles = files.sortedByDescending { it.timestamp }
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
        // Auto-expand the current month
        if (expandedMonths.isEmpty() && grouped.isNotEmpty()) {
            expandedMonths = setOf(grouped.first().label)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = TelegramBlue.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(TelegramBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly Backup Archive", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "${monthGroups.sumOf { it.files.size }} files across ${monthGroups.size} months",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Filter Button
                Box {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = TelegramBlue)
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        // Year selector
                        Text("Year", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        listOf(2026, 2025, 2024).forEach { year ->
                            DropdownMenuItem(
                                text = { Text("$year", fontWeight = if (year == filterYear) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { filterYear = year },
                                leadingIcon = {
                                    if (year == filterYear) Icon(Icons.Default.Check, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        HorizontalDivider()
                        // Month selector
                        Text("Month", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("All Months", fontWeight = if (filterMonth == -1) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { filterMonth = -1; showFilterMenu = false },
                            leadingIcon = {
                                if (filterMonth == -1) Icon(Icons.Default.Check, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                            }
                        )
                        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        monthNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name, fontWeight = if (index == filterMonth) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { filterMonth = index; showFilterMenu = false },
                                leadingIcon = {
                                    if (index == filterMonth) Icon(Icons.Default.Check, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }

        // Back handler for selection mode
        androidx.activity.compose.BackHandler(enabled = isSelectionMode) {
            selectedPaths = emptySet()
            isSelectionMode = false
        }

        // Multi-select action bar
        if (isSelectionMode && selectedPaths.isNotEmpty()) {
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
                        "${selectedPaths.size} selected",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Download ZIP
                        Button(
                            onClick = {
                                val selectedFilesList = allFiles.filter { selectedPaths.contains(it.path) }
                                val count = selectedFilesList.size
                                Toast.makeText(context, "📦 Starting ZIP download for $count selected files...", Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
                                    ZipBackupDownloader.downloadFilesAsZip(
                                        context = context,
                                        filesToDownload = selectedFilesList,
                                        archiveTitle = "Selected_Backup_${System.currentTimeMillis()}"
                                    )
                                }
                                selectedPaths = emptySet()
                                isSelectionMode = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download ZIP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        // Delete Selected
                        Button(
                            onClick = {
                                val count = selectedPaths.size
                                val pathsToDelete = selectedPaths.toList()
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val dao = AppDatabase.getDatabase(context).uploadedFileDao()
                                        val now = System.currentTimeMillis()
                                        pathsToDelete.forEach { path ->
                                            dao.softDelete(path, "", now)
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Moved $count items to Trash Bin", Toast.LENGTH_SHORT).show()
                                            selectedPaths = emptySet()
                                            isSelectionMode = false
                                            loadFiles()
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
                                selectedPaths = emptySet()
                                isSelectionMode = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TelegramBlue)
            }
        } else if (monthGroups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Backups for $filterYear", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("Files backed up each month will appear here organized by date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                monthGroups.forEach { group ->
                    val isExpanded = expandedMonths.contains(group.label)

                    // Month Section Header
                    item(key = "header_${group.label}") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                expandedMonths = if (isExpanded) expandedMonths - group.label else expandedMonths + group.label
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(TelegramBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(group.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        "${group.files.size} files • ${formatFileSize(group.totalSize)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // Download entire month as ZIP
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
                                    }
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download Month ZIP", tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                }

                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // File Items (if expanded)
                    if (isExpanded) {
                        itemsIndexed(group.files, key = { idx, file -> "${group.label}_${file.path}_$idx" }) { _, file ->
                            val isSelected = selectedPaths.contains(file.path)
                            val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedPaths = if (isSelected) selectedPaths - file.path else selectedPaths + file.path
                                                if (selectedPaths.isEmpty()) isSelectionMode = false
                                            } else {
                                                if (file.path.isNotEmpty()) {
                                                    FileViewerHelper.openFile(context, file.path, file.fileName)
                                                } else {
                                                    Toast.makeText(context, "Opening ${file.fileName}...", Toast.LENGTH_SHORT).show()
                                                }
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
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(getFileIconColor(file.fileName).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(getFileIcon(file.fileName), contentDescription = null, tint = getFileIconColor(file.fileName), modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.fileName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                                        Text(
                                            "${formatFileSize(file.size)} • ${dateFormat.format(Date(file.timestamp))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
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

private fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") -> Icons.Default.Image
        ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "webm") -> Icons.Default.Videocam
        ext in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a") -> Icons.Default.MusicNote
        ext in listOf("pdf") -> Icons.Default.PictureAsPdf
        ext in listOf("doc", "docx", "txt", "rtf", "odt") -> Icons.Default.Description
        ext in listOf("xls", "xlsx", "csv") -> Icons.Default.TableChart
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.FolderZip
        ext in listOf("apk") -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileIconColor(fileName: String): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") -> Color(0xFF10B981)
        ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "webm") -> Color(0xFFEF4444)
        ext in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a") -> Color(0xFFF59E0B)
        ext in listOf("pdf") -> Color(0xFFEF4444)
        ext in listOf("doc", "docx", "txt", "rtf", "odt") -> Color(0xFF3B82F6)
        ext in listOf("xls", "xlsx", "csv") -> Color(0xFF10B981)
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> Color(0xFF8B5CF6)
        ext in listOf("apk") -> Color(0xFF10B981)
        else -> Color(0xFF64748B)
    }
}
