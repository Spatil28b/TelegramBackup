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
import androidx.compose.ui.unit.sp
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
    val haptic = LocalHapticFeedback.current

    var allFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var monthGroups by remember { mutableStateOf<List<MonthGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Year Filter state
    var selectedYear by remember { mutableIntStateOf(-1) } // -1 = All Years

    // Multi-select state
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    // Expanded months
    var expandedMonths by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadFiles() {
        scope.launch(Dispatchers.IO) {
            val files = try {
                AppDatabase.getDatabase(context).uploadedFileDao().getAllFiles()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                allFiles = files
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadFiles() }

    // Available years from files
    val availableYears = remember(allFiles) {
        val cal = Calendar.getInstance()
        val years = allFiles.map {
            cal.timeInMillis = it.timestamp
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
        val grouped = allFiles
            .groupBy { file ->
                cal.timeInMillis = file.timestamp
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                Pair(y, m)
            }
            .filter { (key, _) ->
                selectedYear == -1 || key.first == selectedYear
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
        if (expandedMonths.isEmpty() && grouped.isNotEmpty()) {
            expandedMonths = setOf(grouped.first().label)
        }
    }

    BackHandler(enabled = isSelectionMode) {
        selectedPaths = emptySet()
        isSelectionMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // 🌟 Hero Overview Header
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

        // 📅 Year Filter Carousel Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedYear == -1,
                    onClick = {
                        selectedYear = -1
                    },
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
                    onClick = {
                        selectedYear = year
                    },
                    label = { Text("$year", fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelegramBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Multi-select action bar
        if (isSelectionMode && selectedPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TelegramBlue.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                monthGroups.forEach { group ->
                    val isExpanded = expandedMonths.contains(group.label)

                    item(key = "month_${group.label}") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedMonths = if (isExpanded) expandedMonths - group.label else expandedMonths + group.label
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(TelegramBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
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
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Expandable File List
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(4.dp))

                                        group.files.forEachIndexed { fIdx, file ->
                                            val isSelected = selectedPaths.contains(file.path)
                                            val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

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

                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(getFileIconColor(file.fileName).copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            getFileIcon(file.fileName),
                                                            contentDescription = null,
                                                            tint = getFileIconColor(file.fileName),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            file.fileName,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            "${formatFileSize(file.size)} • ${dateFormat.format(Date(file.timestamp))}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
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
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic") -> Icons.Default.Image
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
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic") -> Color(0xFF10B981)
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
