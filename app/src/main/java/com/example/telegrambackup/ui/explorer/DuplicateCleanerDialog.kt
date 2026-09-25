package com.example.telegrambackup.ui.explorer

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.common.FileThumbnailView
import com.example.telegrambackup.utils.DuplicateCluster
import com.example.telegrambackup.utils.DuplicateFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DuplicateCleanerDialog(
    onDismiss: () -> Unit,
    onCleanComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var isCleaning by remember { mutableStateOf(false) }
    var duplicateClusters by remember { mutableStateOf<List<DuplicateCluster>>(emptyList()) }
    
    // Map of cluster index -> index of file to keep within that cluster
    val keepIndexMap = remember { mutableStateMapOf<Int, Int>() }

    fun refreshDuplicates() {
        scope.launch {
            isScanning = true
            val clusters = DuplicateFileManager.findDuplicates(context)
            duplicateClusters = clusters
            keepIndexMap.clear()
            // Default: Auto-pick the best copy for each cluster
            clusters.forEachIndexed { idx, cluster ->
                keepIndexMap[idx] = DuplicateFileManager.findBestIndexToKeep(cluster.files)
            }
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        refreshDuplicates()
    }

    val totalWastedBytes = remember(duplicateClusters, keepIndexMap.toMap()) {
        duplicateClusters.mapIndexed { idx, cluster ->
            val keepIdx = keepIndexMap[idx] ?: 0
            val deleteCount = (cluster.files.size - 1).coerceAtLeast(0)
            deleteCount * cluster.sizeBytes
        }.sum()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(TelegramEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = TelegramEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Visual Duplicate Cleaner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Side-by-Side Review • Auto-Keep Best",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isScanning) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TelegramEmerald)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Scanning for duplicate files...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else if (duplicateClusters.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TelegramEmerald,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No Duplicates Found!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Your cloud vault is 100% clean and optimized.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // Summary Banner & Action Buttons
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "RECLAIMABLE STORAGE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatBytes(totalWastedBytes),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = TelegramEmerald
                                        )
                                    )
                                    Text(
                                        "${duplicateClusters.size} duplicate groups found",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Auto-Keep Best Button
                                    OutlinedButton(
                                        onClick = {
                                            duplicateClusters.forEachIndexed { idx, cluster ->
                                                keepIndexMap[idx] = DuplicateFileManager.findBestIndexToKeep(cluster.files)
                                            }
                                            Toast.makeText(context, "✨ Selected best quality & camera originals", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, TelegramEmerald.copy(alpha = 0.6f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = TelegramEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Auto-Best",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = TelegramEmerald
                                        )
                                    }

                                    // Clean Button
                                    Button(
                                        onClick = {
                                            if (!isCleaning) {
                                                isCleaning = true
                                                scope.launch {
                                                    val reclaimed = DuplicateFileManager.cleanSelectedDuplicates(
                                                        context,
                                                        duplicateClusters,
                                                        keepIndexMap.toMap()
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        isCleaning = false
                                                        Toast.makeText(
                                                            context,
                                                            "🧹 Reclaimed ${formatBytes(reclaimed)} of storage!",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        onCleanComplete()
                                                        onDismiss()
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                                        enabled = !isCleaning,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        if (isCleaning) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.DeleteSweep,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Clean",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(duplicateClusters) { clusterIdx, cluster ->
                            val currentKeepIdx = keepIndexMap[clusterIdx] ?: 0
                            val primaryFile = cluster.files.firstOrNull()

                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Cluster title row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.FolderZip,
                                                contentDescription = null,
                                                tint = TelegramBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                primaryFile?.fileName ?: "File",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = TelegramEmerald.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "${formatBytes(cluster.sizeBytes)} each • ${cluster.files.size} copies",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = TelegramEmerald,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Visual Side-by-side cards
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(cluster.files) { fIdx, file ->
                                            val isKeep = (fIdx == currentKeepIdx)
                                            val cleanFolder = file.path
                                                .replace("\\", "/")
                                                .substringBeforeLast('/', "")
                                                .substringAfterLast('/', "Storage")

                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isKeep) {
                                                        TelegramEmerald.copy(alpha = 0.10f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    }
                                                ),
                                                border = BorderStroke(
                                                    width = if (isKeep) 2.dp else 1.dp,
                                                    color = if (isKeep) TelegramEmerald else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier
                                                    .width(135.dp)
                                                    .clickable {
                                                        keepIndexMap[clusterIdx] = fIdx
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    // Visual Thumbnail Box
                                                    Box(
                                                        modifier = Modifier
                                                            .size(119.dp, 84.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        FileThumbnailView(
                                                            fileName = file.fileName,
                                                            filePath = file.path,
                                                            telegramFileId = file.telegramFileId,
                                                            iconSize = 56.dp,
                                                            shape = RoundedCornerShape(10.dp),
                                                            modifier = Modifier.fillMaxSize()
                                                        )

                                                        // Top Pill Status Tag
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopStart)
                                                                .padding(4.dp)
                                                        ) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (isKeep) TelegramEmerald else Color.Black.copy(alpha = 0.65f)
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        if (isKeep) Icons.Default.Check else Icons.Default.DeleteOutline,
                                                                        contentDescription = null,
                                                                        tint = Color.White,
                                                                        modifier = Modifier.size(11.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text(
                                                                        if (isKeep) "KEEP" else "REMOVE",
                                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        ),
                                                                        color = Color.White
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Location folder
                                                    Text(
                                                        text = cleanFolder.ifBlank { "Root" },
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                        color = if (isKeep) TelegramEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    // Path preview
                                                    Text(
                                                        text = file.path.takeLast(22),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                        color = MaterialTheme.colorScheme.outline,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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
        }
    }
}

