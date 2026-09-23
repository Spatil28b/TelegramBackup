package com.example.telegrambackup.ui.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuickActionItem(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val categoryIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var fileForMenu by remember { mutableStateOf<UploadedFile?>(null) }

    BackHandler(enabled = isSelectionMode) {
        selectedPaths = emptySet()
        isSelectionMode = false
    }

    val quickActions = remember {
        listOf(
            QuickActionItem("Camera & Photos", Icons.Default.PhotoCamera, Color(0xFF10B981), 0),
            QuickActionItem("Videos", Icons.Default.Videocam, Color(0xFF8B5CF6), 1),
            QuickActionItem("Music", Icons.Default.MusicNote, Color(0xFF06B6D4), 5),
            QuickActionItem("Documents", Icons.Default.Description, Color(0xFFF59E0B), 2),
            QuickActionItem("APKs & Apps", Icons.Default.Android, Color(0xFFEC4899), 4),
            QuickActionItem("Downloads", Icons.Default.Download, Color(0xFF3B82F6), 3)
        )
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    val totalBytes = remember(recentFiles) { recentFiles.sumOf { it.size } }

    val storageGranted = remember { com.example.telegrambackup.ui.auth.isStoragePermissionGranted(context) }

    val storageLegacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!storageGranted) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { requestStorageDirectly() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage Access Required", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD97706)))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Grant storage access so the app can scan and back up your device photos, videos & files.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { requestStorageDirectly() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(TelegramEmerald)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Cloud Storage",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TelegramBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "∞ Unlimited",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = com.example.telegrambackup.ui.explorer.formatFileSize(totalBytes),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Multi-category segmented storage progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().weight(0.4f).background(Color(0xFF10B981)))
                        Box(modifier = Modifier.fillMaxHeight().weight(0.25f).background(Color(0xFF8B5CF6)))
                        Box(modifier = Modifier.fillMaxHeight().weight(0.15f).background(Color(0xFFF59E0B)))
                        Box(modifier = Modifier.fillMaxHeight().weight(0.1f).background(Color(0xFF06B6D4)))
                        Box(modifier = Modifier.fillMaxHeight().weight(0.1f).background(Color(0xFF3B82F6)))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${recentFiles.size} items protected",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "256-bit AES Encrypted",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = TelegramEmerald
                        )
                    }
                }
            }
        }

        item {
            var showFreeUpSpaceDialog by remember { mutableStateOf(false) }

            if (showFreeUpSpaceDialog) {
                com.example.telegrambackup.ui.settings.FreeUpSpaceDialog(
                    onDismiss = {
                        showFreeUpSpaceDialog = false
                        onRefresh()
                    }
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showFreeUpSpaceDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = TelegramEmerald.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TelegramEmerald.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TelegramEmerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = TelegramEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Free Up Device Space",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Delete local copies of backed up media safely",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TelegramEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                quickActions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onQuickActionClick(item.categoryIndex) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(item.iconTint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(26.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "View Cloud Files",
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Files", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = onViewAllClick) { Text("View All", color = TelegramBlue) }
            }

            // Multi-select action bar for recent files
            if (isSelectionMode && selectedPaths.isNotEmpty()) {
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
                            "${selectedPaths.size} selected",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Download
                            Button(
                                onClick = {
                                    val count = selectedPaths.size
                                    val filesToDownload = recentFiles.filter { selectedPaths.contains(it.path) }
                                    if (filesToDownload.isNotEmpty()) {
                                        Toast.makeText(context, "Starting download of $count files...", Toast.LENGTH_SHORT).show()
                                        scope.launch(Dispatchers.IO) {
                                            com.example.telegrambackup.utils.ZipBackupDownloader.downloadFilesAsZip(
                                                context = context,
                                                filesToDownload = filesToDownload,
                                                archiveTitle = "Recent_Downloads"
                                            )
                                        }
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
                                Text("Download", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            // Delete
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
                                                onRefresh()
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
        }

        if (recentFiles.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Cloud Backup Activity Yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Backed up files will appear here automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(recentFiles.take(6), key = { "${it.path}_${it.timestamp}" }) { file ->
                val isSelected = selectedPaths.contains(file.path)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) TelegramBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    selectedPaths = if (isSelected) selectedPaths - file.path else selectedPaths + file.path
                                    if (selectedPaths.isEmpty()) isSelectionMode = false
                                } else {
                                    onFileClick(file)
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
                            modifier = Modifier.size(46.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            com.example.telegrambackup.ui.common.FileThumbnailView(
                                fileName = file.fileName,
                                filePath = file.path,
                                telegramFileId = file.telegramFileId,
                                iconSize = 38.dp,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.fileName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${com.example.telegrambackup.ui.explorer.formatFileSize(file.size)} • ${dateFormat.format(Date(file.timestamp))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (!isSelectionMode) {
                            IconButton(
                                onClick = { fileForMenu = file },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.outline
                                )
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
                onRefresh()
            },
            onFileDeleted = { delFile ->
                selectedPaths = selectedPaths - delFile.path
                fileForMenu = null
                onRefresh()
            }
        )
    }
}
