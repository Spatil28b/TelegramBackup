package com.example.telegrambackup.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.explorer.DuplicateCleanerDialog
import com.example.telegrambackup.ui.explorer.formatFileSize
import com.example.telegrambackup.ui.settings.FreeUpSpaceDialog
import com.example.telegrambackup.ui.settings.spotiGroupCardColor
import com.example.telegrambackup.utils.DeviceStorageInfo
import com.example.telegrambackup.utils.StorageCategoryUsage
import com.example.telegrambackup.utils.StorageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardHomeScreen(
    userName: String = "User",
    recentFiles: List<UploadedFile> = emptyList(),
    onQuickActionClick: (Int) -> Unit = {},
    onViewAllClick: () -> Unit = {},
    onFileClick: (UploadedFile) -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Real device storage info
    var storageInfo by remember { mutableStateOf(StorageStatsHelper.getDeviceStorageInfo()) }
    var junkCacheBytes by remember { mutableLongStateOf(0L) }
    var categoryUsage by remember { mutableStateOf(StorageCategoryUsage()) }
    var localBackedUpFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var trashCount by remember { mutableIntStateOf(0) }
    var isJunkCleaning by remember { mutableStateOf(false) }
    var isJunkCleaned by remember { mutableStateOf(false) }

    // Dialog states
    var showFreeUpSpaceDialog by remember { mutableStateOf(false) }
    var showDuplicateCleanerDialog by remember { mutableStateOf(false) }
    var showLargeFilesDialog by remember { mutableStateOf(false) }

    val storageGranted = remember { com.example.telegrambackup.ui.auth.isStoragePermissionGranted(context) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val storageLegacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun requestStorageDirectly() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                manageStorageLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            } catch (_: Exception) {
                try {
                    manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {}
            }
        } else {
            storageLegacyLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            storageInfo = StorageStatsHelper.getDeviceStorageInfo()
            junkCacheBytes = StorageStatsHelper.getAppCacheSizeBytes(context)
            categoryUsage = StorageStatsHelper.calculateCategoryUsage(recentFiles)
            localBackedUpFiles = StorageStatsHelper.getLocalBackedUpFiles(recentFiles)
            try {
                val db = AppDatabase.getDatabase(context)
                trashCount = db.uploadedFileDao().getTrashFiles().size
            } catch (_: Exception) {}
        }
    }

    if (showFreeUpSpaceDialog) {
        FreeUpSpaceDialog(
            onDismiss = {
                showFreeUpSpaceDialog = false
                onRefresh()
                scope.launch(Dispatchers.IO) {
                    storageInfo = StorageStatsHelper.getDeviceStorageInfo()
                    localBackedUpFiles = StorageStatsHelper.getLocalBackedUpFiles(recentFiles)
                }
            }
        )
    }

    if (showDuplicateCleanerDialog) {
        DuplicateCleanerDialog(
            onDismiss = {
                showDuplicateCleanerDialog = false
                onRefresh()
            },
            onCleanComplete = {
                onRefresh()
            }
        )
    }

    val totalCloudBytes = remember(recentFiles) { recentFiles.sumOf { it.size } }
    val localBackedUpBytes = remember(localBackedUpFiles) { localBackedUpFiles.sumOf { it.size } }
    val largeFiles = remember(recentFiles) { recentFiles.filter { it.size > 25 * 1024 * 1024 } }
    val largeFilesBytes = remember(largeFiles) { largeFiles.sumOf { it.size } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Storage Permission Alert if needed
        if (!storageGranted) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { requestStorageDirectly() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full Storage Access Needed", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD97706)))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Enable access to scan and clean junk files, duplicate media & downloads.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { requestStorageDirectly() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 📊 GOOGLE FILES SIGNATURE STORAGE GAUGE CARD
        // =========================================================================
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = spotiGroupCardColor(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(TelegramBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Internal Storage",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${storageInfo.freeFormatted} free",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TelegramBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${(storageInfo.usedPercentage * 100).toInt()}% Used",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Headline Size
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = storageInfo.usedFormatted,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 34.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "used of ${storageInfo.totalFormatted}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Multi-Color Segmented Google Files Storage Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        val usedRatio = storageInfo.usedPercentage.coerceIn(0.01f, 1f)
                        val imgRatio = (categoryUsage.imagesBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0.02f, 0.35f) * usedRatio
                        val vidRatio = (categoryUsage.videosBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0.03f, 0.40f) * usedRatio
                        val audRatio = (categoryUsage.audioBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0.01f, 0.15f) * usedRatio
                        val docRatio = (categoryUsage.docsBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0.01f, 0.15f) * usedRatio
                        val sysRatio = (usedRatio - imgRatio - vidRatio - audRatio - docRatio).coerceAtLeast(0.05f)

                        Box(modifier = Modifier.fillMaxHeight().weight(imgRatio.coerceAtLeast(0.02f)).background(Color(0xFFE91E63))) // Images Pink
                        Box(modifier = Modifier.fillMaxHeight().weight(vidRatio.coerceAtLeast(0.02f)).background(Color(0xFF9C27B0))) // Videos Purple
                        Box(modifier = Modifier.fillMaxHeight().weight(audRatio.coerceAtLeast(0.02f)).background(Color(0xFFFF9800))) // Audio Amber
                        Box(modifier = Modifier.fillMaxHeight().weight(docRatio.coerceAtLeast(0.02f)).background(Color(0xFF00BCD4))) // Docs Cyan
                        Box(modifier = Modifier.fillMaxHeight().weight(sysRatio.coerceAtLeast(0.05f)).background(Color(0xFF64748B))) // System Gray
                        Box(modifier = Modifier.fillMaxHeight().weight((1f - usedRatio).coerceAtLeast(0.01f)).background(Color.Transparent))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Storage Legend Dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StorageLegendDot(Color(0xFFE91E63), "Images")
                        StorageLegendDot(Color(0xFF9C27B0), "Videos")
                        StorageLegendDot(Color(0xFFFF9800), "Audio")
                        StorageLegendDot(Color(0xFF00BCD4), "Docs")
                        StorageLegendDot(Color(0xFF64748B), "System")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloud Storage Unlimited Indicator Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Telegram Cloud Backup",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TelegramEmerald.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "∞ Unlimited (${formatFileSize(totalCloudBytes)})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "CLEAN SUGGESTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.1.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        // =========================================================================
        // 1. JUNK FILES CLEANER CARD (Google Files Signature Card)
        // =========================================================================
        item {
            GoogleCleanCard(
                icon = Icons.Default.CleaningServices,
                iconTint = Color(0xFFF59E0B),
                title = "Junk files",
                subtitle = if (isJunkCleaned) "Your device cache is clean!" else "Temporary cache & log files taking up room",
                actionLabel = if (isJunkCleaned) "Cleaned ✓" else if (isJunkCleaning) "Cleaning..." else "Clean ${formatFileSize(junkCacheBytes)}",
                actionColor = Color(0xFFF59E0B),
                isCompleted = isJunkCleaned,
                isLoading = isJunkCleaning,
                onClick = {
                    if (!isJunkCleaned && !isJunkCleaning) {
                        scope.launch {
                            isJunkCleaning = true
                            val freed = StorageStatsHelper.clearAppJunkCache(context)
                            isJunkCleaning = false
                            isJunkCleaned = true
                            junkCacheBytes = 0L
                            Toast.makeText(context, "✨ Cleaned ${formatFileSize(freed)} of junk files!", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    }
                }
            )
        }

        // =========================================================================
        // 2. DELETE BACKED UP MEDIA (FREE UP PHONE SPACE)
        // =========================================================================
        item {
            GoogleCleanCard(
                icon = Icons.Default.CloudDone,
                iconTint = TelegramEmerald,
                title = "Delete backed-up media",
                subtitle = "Files already safely backed up to Telegram Cloud can be removed from phone",
                actionLabel = if (localBackedUpFiles.isNotEmpty()) "Free up ${formatFileSize(localBackedUpBytes)}" else "Free up space",
                actionColor = TelegramEmerald,
                onClick = {
                    showFreeUpSpaceDialog = true
                }
            )
        }

        // =========================================================================
        // 3. DUPLICATE FILES CLEANER
        // =========================================================================
        item {
            GoogleCleanCard(
                icon = Icons.Default.Difference,
                iconTint = Color(0xFF6366F1),
                title = "Duplicate files",
                subtitle = "Find and delete duplicate photos, videos & downloads",
                actionLabel = "Review duplicates",
                actionColor = Color(0xFF6366F1),
                onClick = {
                    showDuplicateCleanerDialog = true
                }
            )
        }

        // =========================================================================
        // 4. LARGE FILES CLEANER (>25 MB)
        // =========================================================================
        item {
            GoogleCleanCard(
                icon = Icons.Default.FolderZip,
                iconTint = Color(0xFF8B5CF6),
                title = "Large files",
                subtitle = if (largeFiles.isNotEmpty()) "${largeFiles.size} files larger than 25 MB taking ${formatFileSize(largeFilesBytes)}" else "Files larger than 25 MB",
                actionLabel = "Select & free up",
                actionColor = Color(0xFF8B5CF6),
                onClick = {
                    onViewAllClick()
                }
            )
        }

        // =========================================================================
        // 5. TRASH BIN STATUS CARD
        // =========================================================================
        item {
            GoogleCleanCard(
                icon = Icons.Default.DeleteOutline,
                iconTint = Color(0xFFEF4444),
                title = "Recycle bin",
                subtitle = if (trashCount > 0) "$trashCount items waiting in trash" else "Trash bin is currently empty",
                actionLabel = if (trashCount > 0) "Empty trash ($trashCount)" else "View trash",
                actionColor = Color(0xFFEF4444),
                onClick = {
                    onQuickActionClick(-1) // Signal to navigate to Trash
                }
            )
        }
    }
}

@Composable
private fun StorageLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Google Files Signature Clean Action Card
 */
@Composable
private fun GoogleCleanCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    actionColor: Color,
    isCompleted: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = spotiGroupCardColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (isCompleted) TelegramEmerald else iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onClick,
                enabled = !isCompleted && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionColor,
                    disabledContainerColor = if (isCompleted) TelegramEmerald.copy(alpha = 0.15f) else actionColor.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) TelegramEmerald else Color.White,
                        fontSize = 13.5.sp
                    )
                )
            }
        }
    }
}
