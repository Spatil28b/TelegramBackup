package com.example.telegrambackup.ui.upload

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupProgressTracker
import java.util.Locale

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun getFileIcon(fileName: String): ImageVector {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressScreen(
    onPauseClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }

    val isBackupActive by BackupProgressTracker.isBackupActive.collectAsState()
    val statusMessage by BackupProgressTracker.statusMessage.collectAsState()
    val currentFileName by BackupProgressTracker.currentFileName.collectAsState()
    val currentFilePath by BackupProgressTracker.currentFilePath.collectAsState()
    val currentFileProgress by BackupProgressTracker.currentFileProgress.collectAsState()
    val completedCount by BackupProgressTracker.completedCount.collectAsState()
    val totalCount by BackupProgressTracker.totalCount.collectAsState()
    val pendingQueue by BackupProgressTracker.pendingFilesQueue.collectAsState()
    val recentCompleted by BackupProgressTracker.recentCompletedList.collectAsState()

    val targetOverallProgress = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedOverallProgress by animateFloatAsState(
        targetValue = targetOverallProgress,
        animationSpec = tween(durationMillis = 400),
        label = "overallProgress"
    )

    val animatedFileProgress by animateFloatAsState(
        targetValue = currentFileProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "fileProgress"
    )

    LaunchedEffect(Unit) {
        BackupProgressTracker.hydrateRecentCompleted(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Cloud Backup Engine",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Overall Progress Header Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background((if (isBackupActive) TelegramBlue else TelegramEmerald).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isBackupActive) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = if (isBackupActive) TelegramBlue else TelegramEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = if (isBackupActive) "Backing Up Files..." else "Protection Active",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "$completedCount of $totalCount files backed up",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            IconButton(onClick = onCancelClick) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Progress Meter
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Overall Batch Progress", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("${(animatedOverallProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedOverallProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = TelegramBlue,
                                    trackColor = TelegramBlue.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Current File Section
            item {
                AnimatedVisibility(
                    visible = currentFileName.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        Text(
                            text = "Current Active File",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(getFileIconColor(currentFileName).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(getFileIcon(currentFileName), contentDescription = null, tint = getFileIconColor(currentFileName), modifier = Modifier.size(26.dp))
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentFileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentFilePath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { animatedFileProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = TelegramEmerald,
                                        trackColor = TelegramEmerald.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Batch Queue List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upload Queue",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text("${pendingQueue.size} Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            if (pendingQueue.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("Queue is empty. No files pending upload.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                items(pendingQueue.take(15), key = { "up_queue_${it.filePath}" }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(getFileIconColor(item.fileName).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(getFileIcon(item.fileName), contentDescription = null, tint = getFileIconColor(item.fileName), modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(item.fileSize)} • Pending",
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
