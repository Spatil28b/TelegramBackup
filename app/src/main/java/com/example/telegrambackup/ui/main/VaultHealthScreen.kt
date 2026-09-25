package com.example.telegrambackup.ui.main

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.DateRecoveryHelper
import com.example.telegrambackup.utils.FileHealthReport
import com.example.telegrambackup.utils.FileHealthStatus
import com.example.telegrambackup.utils.HealthCategory
import com.example.telegrambackup.utils.VaultDiagnosticSummary
import com.example.telegrambackup.utils.VaultHealthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHealthScreen(
    onNavigateToProgress: () -> Unit = {},
    onFileClick: (UploadedFile) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var summary by remember { mutableStateOf<VaultDiagnosticSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRepairing by remember { mutableStateOf(false) }
    var repairMessage by remember { mutableStateOf<String?>(null) }

    var selectedFilter by remember { mutableStateOf(HealthCategory.ALL) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    var inspectReport by remember { mutableStateOf<FileHealthReport?>(null) }
    val sheetState = rememberModalBottomSheetState()

    fun loadDiagnostics() {
        scope.launch {
            isLoading = true
            val res = VaultHealthManager.diagnoseVault(context)
            summary = res
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadDiagnostics()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TelegramBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analyzing Vault Integrity & Chunks...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                val currentSummary = summary
                if (currentSummary != null) {
                    val filteredReports = remember(currentSummary, selectedFilter, selectedCategory, searchQuery) {
                        currentSummary.reports.filter { r ->
                            // Issue filter
                            val matchesFilter = when (selectedFilter) {
                                HealthCategory.ALL -> true
                                HealthCategory.INCOMPLETE_CHUNKS -> !r.isComplete
                                HealthCategory.DATE_ISSUES -> r.hasDateIssue
                                HealthCategory.HEALTHY -> r.status == FileHealthStatus.HEALTHY
                            }

                            // File type category
                            val matchesCategory = when (selectedCategory) {
                                "All" -> true
                                "Videos" -> r.file.fileName.endsWith(".mp4", true) || r.file.fileName.endsWith(".mkv", true) || r.file.fileName.endsWith(".mov", true)
                                "Photos" -> r.file.fileName.endsWith(".jpg", true) || r.file.fileName.endsWith(".jpeg", true) || r.file.fileName.endsWith(".png", true) || r.file.fileName.endsWith(".webp", true)
                                "Docs" -> r.file.fileName.endsWith(".pdf", true) || r.file.fileName.endsWith(".docx", true) || r.file.fileName.endsWith(".txt", true) || r.file.fileName.endsWith(".xlsx", true)
                                "Audio" -> r.file.fileName.endsWith(".mp3", true) || r.file.fileName.endsWith(".m4a", true) || r.file.fileName.endsWith(".ogg", true) || r.file.fileName.endsWith(".opus", true)
                                "APKs" -> r.file.fileName.endsWith(".apk", true)
                                "Archives" -> r.file.fileName.endsWith(".zip", true) || r.file.fileName.endsWith(".rar", true) || r.file.fileName.endsWith(".tar", true)
                                else -> true
                            }

                            // Search query
                            val matchesSearch = if (searchQuery.isBlank()) true else {
                                r.file.fileName.contains(searchQuery, ignoreCase = true) || r.file.path.contains(searchQuery, ignoreCase = true)
                            }

                            matchesFilter && matchesCategory && matchesSearch
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        // 1. Health Hero Card
                        item {
                            VaultHealthHeroCard(
                                summary = currentSummary,
                                isRepairing = isRepairing,
                                onAutoRepairAll = {
                                    scope.launch {
                                        isRepairing = true
                                        repairMessage = "Restoring authentic dates across all files..."
                                        val restoredDates = DateRecoveryHelper.recoverAuthenticDates(context)
                                        repairMessage = "Syncing local chunk mappings..."
                                        val syncedChunks = VaultHealthManager.syncLocalChunksToCloud(context)
                                        repairMessage = null
                                        isRepairing = false
                                        Toast.makeText(context, "Restored $restoredDates dates and synced $syncedChunks chunk mappings!", Toast.LENGTH_LONG).show()
                                        loadDiagnostics()
                                    }
                                },
                                onSyncChunks = {
                                    scope.launch {
                                        isRepairing = true
                                        val count = VaultHealthManager.syncLocalChunksToCloud(context)
                                        isRepairing = false
                                        Toast.makeText(context, "Synced $count local chunk sets to cloud!", Toast.LENGTH_SHORT).show()
                                        loadDiagnostics()
                                    }
                                }
                            )
                        }

                        // 2. Search Bar
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                placeholder = { Text("Search files by name or folder...", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // 3. Status Filters (Chips)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedFilter == HealthCategory.ALL,
                                    onClick = { selectedFilter = HealthCategory.ALL },
                                    label = { Text("All (${currentSummary.totalFiles})") },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = selectedFilter == HealthCategory.INCOMPLETE_CHUNKS,
                                    onClick = { selectedFilter = HealthCategory.INCOMPLETE_CHUNKS },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF59E0B))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Incomplete Chunks (${currentSummary.incompleteChunksCount})")
                                        }
                                    },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = selectedFilter == HealthCategory.DATE_ISSUES,
                                    onClick = { selectedFilter = HealthCategory.DATE_ISSUES },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Date Desync (${currentSummary.dateIssueCount})")
                                        }
                                    },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = selectedFilter == HealthCategory.HEALTHY,
                                    onClick = { selectedFilter = HealthCategory.HEALTHY },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramEmerald)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Healthy (${currentSummary.healthyCount})")
                                        }
                                    },
                                    shape = CircleShape
                                )
                            }
                        }

                        // 4. File Type Chips
                        item {
                            val cats = listOf("All", "Videos", "Photos", "Docs", "Audio", "APKs", "Archives")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                cats.forEach { cat ->
                                    val isSelected = selectedCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedCategory = cat }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Results Counter & Summary
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Showing ${filteredReports.size} items",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (currentSummary.resumableCount > 0) {
                                    Text(
                                        text = "⚡ ${currentSummary.resumableCount} files present on device can be resumed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TelegramEmerald,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }

                        // 6. List of Files
                        if (filteredReports.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = TelegramEmerald
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "No issues found in this filter!",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            "All checked files are healthy and complete.",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredReports, key = { it.file.path }) { report ->
                                FileHealthCard(
                                    report = report,
                                    onInspect = { inspectReport = report },
                                    onFixDate = {
                                        scope.launch {
                                            val trueTs = DateRecoveryHelper.getAuthenticTimestamp(report.file)
                                            val db = AppDatabase.getDatabase(context)
                                            db.uploadedFileDao().insert(report.file.copy(timestamp = trueTs))
                                            Toast.makeText(context, "Restored date for ${report.file.fileName}", Toast.LENGTH_SHORT).show()
                                            loadDiagnostics()
                                        }
                                    },
                                    onResumeUpload = {
                                        val enqueued = VaultHealthManager.resumeUploadForFile(context)
                                        if (enqueued) {
                                            Toast.makeText(context, "Resuming backup worker in background...", Toast.LENGTH_SHORT).show()
                                            onNavigateToProgress()
                                        } else {
                                            Toast.makeText(context, "Failed to start resume task", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOpenFile = {
                                        onFileClick(report.file)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chunk Inspector Bottom Sheet
        if (inspectReport != null) {
            val rep = inspectReport!!
            ModalBottomSheet(
                onDismissRequest = { inspectReport = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                ChunkInspectorSheet(
                    report = rep,
                    onDismiss = { inspectReport = null },
                    onCopyFileId = { id ->
                        clipboardManager.setText(AnnotatedString(id))
                        Toast.makeText(context, "Copied Telegram File ID!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun VaultHealthHeroCard(
    summary: VaultDiagnosticSummary,
    isRepairing: Boolean,
    onAutoRepairAll: () -> Unit,
    onSyncChunks: () -> Unit
) {
    val total = summary.totalFiles
    val healthyPct = if (total > 0) Math.round((summary.healthyCount.toDouble() / total.toDouble()) * 100).toInt() else 100

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Vault Health & Recovery",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Diagnosis across 100% of your cloud files",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (healthyPct >= 90) TelegramEmerald.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$healthyPct%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (healthyPct >= 90) TelegramEmerald else Color(0xFFF59E0B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4 Mini Stat Badges
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthStatPill(
                    label = "Total Files",
                    value = "${summary.totalFiles}",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                HealthStatPill(
                    label = "Healthy",
                    value = "${summary.healthyCount}",
                    color = TelegramEmerald,
                    modifier = Modifier.weight(1f)
                )
                HealthStatPill(
                    label = "Incomplete",
                    value = "${summary.incompleteChunksCount}",
                    color = if (summary.incompleteChunksCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                HealthStatPill(
                    label = "Date Desync",
                    value = "${summary.dateIssueCount}",
                    color = if (summary.dateIssueCount > 0) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAutoRepairAll,
                    enabled = !isRepairing,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isRepairing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Repairing...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Repair All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onSyncChunks,
                    enabled = !isRepairing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Chunks", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HealthStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = color))
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
        }
    }
}

@Composable
fun FileHealthCard(
    report: FileHealthReport,
    onInspect: () -> Unit,
    onFixDate: () -> Unit,
    onResumeUpload: () -> Unit,
    onOpenFile: () -> Unit
) {
    val file = report.file
    val sizeStr = formatFileSize(file.size)
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    val currentFormattedDate = sdf.format(Date(file.timestamp))
    val authenticFormattedDate = sdf.format(Date(report.authenticDate))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onOpenFile() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Type Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(getFileColor(file.fileName).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        getFileIcon(file.fileName),
                        contentDescription = null,
                        tint = getFileColor(file.fileName),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$sizeStr • ${file.path.substringBeforeLast('/', "Storage")}",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Badge
                when (report.status) {
                    FileHealthStatus.HEALTHY -> {
                        Surface(shape = CircleShape, color = TelegramEmerald.copy(alpha = 0.15f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = TelegramEmerald)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Healthy", fontSize = 11.sp, color = TelegramEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    FileHealthStatus.INCOMPLETE_CHUNKS -> {
                        Surface(shape = CircleShape, color = Color(0xFFEF4444).copy(alpha = 0.15f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${report.availableChunksCount}/${report.totalChunks} Chunks", fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    FileHealthStatus.MISSING_CLOUD_CHUNKS -> {
                        Surface(shape = CircleShape, color = Color(0xFFF59E0B).copy(alpha = 0.15f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFF59E0B))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Missing Chunks", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    FileHealthStatus.DATE_DESYNC -> {
                        Surface(shape = CircleShape, color = TelegramBlue.copy(alpha = 0.15f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(12.dp), tint = TelegramBlue)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Date Desync", fontSize = 11.sp, color = TelegramBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Chunk Progress Meter (if multi-chunk)
            if (report.totalChunks > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                val pct = (report.availableChunksCount.toFloat() / report.totalChunks.toFloat()).coerceIn(0f, 1f)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Chunk Integrity: ${report.availableChunksCount} of ${report.totalChunks} parts",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = "${(pct * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pct == 1f) TelegramEmerald else Color(0xFFF59E0B))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (pct == 1f) TelegramEmerald else Color(0xFFF59E0B),
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
            }

            // Date desync banner
            if (report.hasDateIssue) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TelegramBlue.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Current Date: $currentFormattedDate", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Text("True Capture Date: $authenticFormattedDate", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TelegramBlue))
                        }
                    }
                }
            }

            // Action Row
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (report.totalChunks > 1) {
                    TextButton(
                        onClick = onInspect,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Inspect Chunks", fontSize = 12.sp)
                    }
                }

                if (report.hasDateIssue) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onFixDate,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = TelegramBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore Date", fontSize = 12.sp, color = TelegramBlue)
                    }
                }

                if (!report.isComplete && report.localFileExists) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onResumeUpload,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume Upload", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChunkInspectorSheet(
    report: FileHealthReport,
    onDismiss: () -> Unit,
    onCopyFileId: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Chunk Diagnostic Details",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = report.file.fileName,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Total Parts Required: ${report.totalChunks} • Available in Cloud: ${report.availableChunksCount}",
            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val chunksMap = remember(report.chunks) { report.chunks.associateBy { it.first } }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items((1..report.totalChunks).toList()) { idx ->
                val chunk = chunksMap[idx]
                val isPresent = chunk != null && chunk.second.isNotBlank()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPresent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPresent) TelegramEmerald.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$idx",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresent) TelegramEmerald else Color(0xFFEF4444)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Part $idx of ${report.totalChunks} ${if (idx == 1) "(Primary Chunk)" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (isPresent) {
                                Text(
                                    text = "File ID: ${chunk!!.second.take(24)}...",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                )
                            } else {
                                Text(
                                    text = "Chunk missing from cloud index",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFEF4444), fontSize = 10.sp)
                                )
                            }
                        }

                        if (isPresent) {
                            IconButton(onClick = { onCopyFileId(chunk!!.second) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Surface(shape = CircleShape, color = Color(0xFFEF4444).copy(alpha = 0.15f)) {
                                Text("Missing", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun getFileColor(fileName: String): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "mov", "avi" -> Color(0xFF8B5CF6)
        "jpg", "jpeg", "png", "webp", "gif" -> Color(0xFF10B981)
        "pdf", "docx", "txt", "xlsx" -> Color(0xFF3B82F6)
        "mp3", "m4a", "ogg", "opus" -> Color(0xFFEC4899)
        "apk" -> Color(0xFF14B8A6)
        "zip", "rar", "7z", "tar" -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }
}

private fun getFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "mkv", "mov", "avi" -> Icons.Default.Movie
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "pdf", "docx", "txt", "xlsx" -> Icons.Default.Description
        "mp3", "m4a", "ogg", "opus" -> Icons.Default.MusicNote
        "apk" -> Icons.Default.Android
        "zip", "rar", "7z", "tar" -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}
