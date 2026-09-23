package com.example.telegrambackup.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupProgressTracker
import com.example.telegrambackup.worker.BackupWorker
import java.util.Locale

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
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
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
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

@Composable
fun LiveProgressScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { AppPrefs(context) }

    val isBackupActive by BackupProgressTracker.isBackupActive.collectAsState()
    val statusMessage by BackupProgressTracker.statusMessage.collectAsState()
    val currentFileName by BackupProgressTracker.currentFileName.collectAsState()
    val currentFilePath by BackupProgressTracker.currentFilePath.collectAsState()
    val currentFileProgress by BackupProgressTracker.currentFileProgress.collectAsState()
    val completedCount by BackupProgressTracker.completedCount.collectAsState()
    val totalCount by BackupProgressTracker.totalCount.collectAsState()
    val pendingQueue by BackupProgressTracker.pendingFilesQueue.collectAsState()
    val activeUploads by BackupProgressTracker.activeUploadsList.collectAsState()
    val recentCompleted by BackupProgressTracker.recentCompletedList.collectAsState()
    val workingBots by BackupProgressTracker.workingBotsCount.collectAsState()
    val totalBots by BackupProgressTracker.totalBotsCount.collectAsState()

    var stopAllBackupsState by remember { mutableStateOf(prefs.stopAllBackups) }

    LaunchedEffect(isBackupActive) {
        stopAllBackupsState = prefs.stopAllBackups
    }

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

    // Auto-hydrate recent completed files from Room DB on initial display
    LaunchedEffect(Unit) {
        BackupProgressTracker.hydrateRecentCompleted(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status Overview Header Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBackupActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isBackupActive) TelegramBlue else TelegramEmerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBackupActive) Icons.Default.Sync else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBackupActive) "Syncing Files to Telegram Cloud..." else "Cloud Protection Active",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isBackupActive) TelegramBlue else TelegramEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (isBackupActive) TelegramBlue else TelegramEmerald).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isBackupActive) "LIVE SYNCING" else "IDLE / PROTECTED",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (isBackupActive) TelegramBlue else TelegramEmerald),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Overall Queue Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overall Session Progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text("$completedCount of $totalCount Files Done (${(animatedOverallProgress * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedOverallProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = TelegramBlue,
                        trackColor = TelegramBlue.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                        .build()
                                    // Use KEEP so manual trigger attaches smoothly without cancelling active worker
                                    WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                                    Toast.makeText(context, "⚡ Backup Scan Started!", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) { }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Trigger Backup Now", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        OutlinedButton(
                            onClick = {
                                stopAllBackupsState = !stopAllBackupsState
                                prefs.stopAllBackups = stopAllBackupsState
                                val msg = if (stopAllBackupsState) "🛑 Backups Paused" else "✓ Backups Resumed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(
                                imageVector = if (stopAllBackupsState) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = if (stopAllBackupsState) TelegramEmerald else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (stopAllBackupsState) "Resume Backup" else "Pause Backup", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // 2. Real-Time Network Speed & Multi-Bot Stream Metrics
        item {
            val uploadSpeed by BackupProgressTracker.formattedUploadSpeed.collectAsState()
            val downloadSpeed by BackupProgressTracker.formattedDownloadSpeed.collectAsState()
            val isDownloadActive by BackupProgressTracker.isDownloadActive.collectAsState()
            val dailyLimitMB = prefs.dailyDataLimitMB
            val dailyUsedBytes = prefs.dailyDataTransferredBytes
            val dailyUsedMB = dailyUsedBytes / (1024.0 * 1024.0)

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "LIVE TRANSFER METRICS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Upload Speed Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = TelegramBlue.copy(alpha = 0.10f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload Speed", style = MaterialTheme.typography.labelSmall, color = TelegramBlue)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBackupActive) uploadSpeed else "0 KB/s",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                                )
                            }
                        }

                        // Download Speed Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.10f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download Speed", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B5CF6))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isDownloadActive) downloadSpeed else "0 KB/s",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                                )
                            }
                        }
                    }

                    if (totalBots > 1 || workingBots > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🤖 Fleet Utilization", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (workingBots > 0) TelegramEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    if (workingBots > 0) "⚡ $workingBots of $totalBots Bots Active in Parallel" else "🟢 $totalBots Bots Ready",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (workingBots > 0) TelegramEmerald else MaterialTheme.colorScheme.outline,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (dailyLimitMB > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Daily Data Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                String.format(Locale.US, "%.1f MB / %d MB (%.0f%%)", dailyUsedMB, dailyLimitMB, (dailyUsedMB / dailyLimitMB.toDouble()) * 100.0),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (dailyUsedMB.toFloat() / dailyLimitMB.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = if (dailyUsedMB >= dailyLimitMB) Color(0xFFEF4444) else TelegramEmerald,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // 2. Active File Upload Cards (Multi-File Parallel Support)
        if (activeUploads.isNotEmpty()) {
            items(activeUploads, key = { it.filePath }) { activeItem ->
                val isDriveItem = activeItem.fileName.startsWith("[DRIVE]")
                val cleanDisplayName = activeItem.fileName.removePrefix("[DRIVE]").trim()

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDriveItem) Color(0xFF8B5CF6).copy(alpha = 0.15f) else TelegramBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        if (isDriveItem) "☁️ Cloud Drive" else "📱 Auto-Backup",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDriveItem) Color(0xFF8B5CF6) else TelegramBlue,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                if (activeItem.totalChunks > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = TelegramEmerald.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "⚡ Multi-Bot [Part ${activeItem.currentChunk}/${activeItem.totalChunks}]",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TelegramEmerald,
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "${(activeItem.progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = if (isDriveItem) Color(0xFF8B5CF6) else TelegramBlue,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDriveItem) Color(0xFF8B5CF6).copy(alpha = 0.15f) else getFileIconColor(cleanDisplayName).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isDriveItem) Icons.Default.CloudUpload else getFileIcon(cleanDisplayName),
                                    contentDescription = null,
                                    tint = if (isDriveItem) Color(0xFF8B5CF6) else getFileIconColor(cleanDisplayName),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cleanDisplayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(activeItem.filePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { activeItem.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = if (isDriveItem) Color(0xFF8B5CF6) else TelegramEmerald,
                            trackColor = (if (isDriveItem) Color(0xFF8B5CF6) else TelegramEmerald).copy(alpha = 0.2f)
                        )
                    }
                }
            }
        } else if (currentFileName.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active File Upload", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                            Text("${(animatedFileProgress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = TelegramBlue, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(getFileIconColor(currentFileName).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(getFileIcon(currentFileName), contentDescription = null, tint = getFileIconColor(currentFileName), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentFileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(currentFilePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { animatedFileProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = TelegramEmerald,
                            trackColor = TelegramEmerald.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        // 3. Upcoming Files Queue Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Upcoming Files Queue", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
                        Text(
                            text = if (isBackupActive) "Processing active uploads..." else "Queue is empty. All device files are backed up!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            itemsIndexed(pendingQueue.take(15), key = { index, item -> "pending_${index}_${item.filePath}" }) { _, item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(getFileIconColor(item.fileName).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(getFileIcon(item.fileName), contentDescription = null, tint = getFileIconColor(item.fileName), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.fileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatFileSize(item.fileSize)} • Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.HourglassTop, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 4. Recently Completed Uploads Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recently Completed Uploads", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text("${recentCompleted.size} Backed Up", style = MaterialTheme.typography.labelSmall, color = TelegramEmerald)
            }
        }

        if (recentCompleted.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No recent upload activity.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(recentCompleted, key = { "completed_${it.filePath}_${it.timestamp}" }) { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TelegramEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(getFileIcon(item.fileName), contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.fileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatFileSize(item.fileSize)} • Telegram Cloud", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
