package com.example.telegrambackup.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.explorer.ShareLinkModal
import com.example.telegrambackup.ui.explorer.formatFileSize
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.TelegramMediaResolver
import com.example.telegrambackup.utils.ZipBackupDownloader
import com.example.telegrambackup.utils.FolderRestoreManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * =====================================================================================
 * MATERIAL 3 UNIFIED FILE ACTION MENU (3-DOTS OVERFLOW MODAL)
 * =====================================================================================
 * High-performance, comprehensive bottom sheet modal triggered whenever 3 dots are tapped
 * on any file card across the app.
 *
 * Supported Actions:
 * - 👁️ Preview / Open
 * - 🔗 Generate Cloud Share Link & PIN (via ShareLinkModal)
 * - 📤 Send to App (Standard Android system chooser with FileProvider)
 * - ⬇️ Download / Save to Device (Downloads/TelegramBackup/)
 * - ✏️ Rename File (Room DB + chunkProgressDao + Cloud Backend)
 * - ℹ️ Technical Properties & Metadata Inspector
 * - 📋 Copy Local/Cloud File Path
 * - 🗑️ Move to Trash Bin / Delete Permanently
 * - 🔄 Restore from Trash (when invoked in Trash Bin)
 * =====================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionMenuModal(
    file: UploadedFile,
    isTrashItem: Boolean = false,
    onDismiss: () -> Unit,
    onFileRenamed: ((oldPath: String, newName: String, newPath: String) -> Unit)? = null,
    onFileDeleted: ((file: UploadedFile) -> Unit)? = null,
    onFileRestored: ((file: UploadedFile) -> Unit)? = null,
    onSelectMode: (() -> Unit)? = null,
    folderFiles: List<UploadedFile>? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sub-dialogs state
    var showShareModal by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showAskAiSheet by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val ext = remember(file.fileName) { file.fileName.substringAfterLast('.', "").uppercase() }
    val isFolder = file.telegramFileId == "DIR_MARKER"
    val prefs = remember { AppPrefs(context) }
    var isStarred by remember(file.path) { mutableStateOf(prefs.isFileStarred(file.path)) }
    var isSafeVault by remember(file.path) { mutableStateOf(prefs.isSafeVaultFile(file.path)) }

    // Dynamic folder stats if file is a directory
    var folderItemCount by remember(file.path) { mutableIntStateOf(folderFiles?.size ?: file.totalChunks.coerceAtLeast(0)) }
    var folderTotalSize by remember(file.path) { mutableLongStateOf(folderFiles?.sumOf { it.size } ?: file.size.coerceAtLeast(0L)) }

    LaunchedEffect(file.path, isFolder) {
        if (isFolder && folderFiles == null && (folderItemCount == 0 || folderTotalSize == 0L)) {
            withContext(Dispatchers.IO) {
                try {
                    val dbFiles = AppDatabase.getDatabase(context).uploadedFileDao().getAllRawFiles()
                    val children = dbFiles.filter { it.path.startsWith("${file.path}/") && it.telegramFileId != "DIR_MARKER" && !it.isDeleted }
                    if (children.isNotEmpty()) {
                        folderItemCount = children.size
                        folderTotalSize = children.sumOf { it.size }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // HEADER: File preview thumbnail, file name, size & badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFolder) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        FileThumbnailView(
                            fileName = file.fileName,
                            filePath = file.path,
                            telegramFileId = file.telegramFileId,
                            iconSize = 36.dp,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isFolder) "$folderItemCount files • ${formatFileSize(folderTotalSize)}" else formatFileSize(file.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (ext.isNotBlank() && !isFolder) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = ext,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (file.totalChunks > 1 && !isFolder) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${file.totalChunks} Chunks",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (isDownloading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TelegramBlue.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Downloading & Decrypting...",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TelegramBlue
                            )
                            Text(
                                "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelegramBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = TelegramBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // ACTION ITEMS
            if (!isTrashItem) {
                if (isFolder) {
                    // ================= FOLDER ACTIONS (GOOGLE FILES STYLE) =================
                    // 1. Download Folder as ZIP
                    FileActionRow(
                        icon = Icons.Default.Archive,
                        iconTint = TelegramEmerald,
                        title = "Download Folder as ZIP",
                        subtitle = "Compresses and saves directory to Downloads/TelegramBackup_Archives",
                        onClick = {
                            onDismiss()
                            Toast.makeText(context, "📦 Compressing folder '${file.fileName}' into ZIP...", Toast.LENGTH_SHORT).show()
                            scope.launch(Dispatchers.IO) {
                                val targetFiles = folderFiles ?: run {
                                    val dbFiles = AppDatabase.getDatabase(context).uploadedFileDao().getAllRawFiles()
                                    dbFiles.filter { it.path.startsWith("${file.path}/") && it.telegramFileId != "DIR_MARKER" && !it.isDeleted }
                                }
                                if (targetFiles.isNotEmpty()) {
                                    ZipBackupDownloader.downloadFilesAsZip(context, targetFiles, file.fileName)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Folder '${file.fileName}' has no files to download", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )

                    // 1b. Restore Folder to Device (Direct Tree Diff Sync)
                    FileActionRow(
                        icon = Icons.Default.CloudDownload,
                        iconTint = TelegramBlue,
                        title = "Restore Folder to Device",
                        subtitle = "Restores entire folder tree directly to Downloads/TelegramBackup/",
                        onClick = {
                            onDismiss()
                            Toast.makeText(context, "⏳ Restoring folder '${file.fileName}' directly to storage...", Toast.LENGTH_SHORT).show()
                            scope.launch(Dispatchers.IO) {
                                val summary = FolderRestoreManager.restoreFolderTree(context, file) { curr, tot, name, _ ->
                                    if (curr % 10 == 0 || curr == tot) {
                                        Log.d("FolderRestore", "Restoring $curr/$tot: $name")
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (summary.totalFiles == 0) {
                                        Toast.makeText(context, "Folder '${file.fileName}' has no files to restore", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "✅ Restored ${summary.restoredCount} files (${summary.skippedCount} matched) to Downloads/TelegramBackup/${file.fileName}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    )

                    // 2. Rename Folder
                    FileActionRow(
                        icon = Icons.Default.Edit,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        title = "Rename Folder",
                        subtitle = "Update folder name and relocate sub-items in library",
                        onClick = { showRenameDialog = true }
                    )

                    // 3. Folder Properties & Info
                    FileActionRow(
                        icon = Icons.Default.Info,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Folder Info & Properties",
                        subtitle = "Inspect items count ($folderItemCount), total size, and location",
                        onClick = { showPropertiesDialog = true }
                    )

                    // 4. Copy Folder Path
                    FileActionRow(
                        icon = Icons.Default.ContentCopy,
                        iconTint = MaterialTheme.colorScheme.outline,
                        title = "Copy Folder Path",
                        subtitle = "Copies the complete storage path to system clipboard",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Folder Path", file.path))
                            Toast.makeText(context, "Folder path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 5. Star / Favorite Folder
                    FileActionRow(
                        icon = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        iconTint = Color(0xFFF9AB00),
                        title = if (isStarred) "Remove from Starred" else "Add to Starred",
                        subtitle = if (isStarred) "Remove folder from Starred collection" else "Pin folder to Starred collection",
                        onClick = {
                            isStarred = prefs.toggleStarredFile(file.path)
                            Toast.makeText(context, if (isStarred) "⭐ Added folder to Starred" else "Removed folder from Starred", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 6. Safe Folder Vault
                    FileActionRow(
                        icon = if (isSafeVault) Icons.Default.LockOpen else Icons.Default.Lock,
                        iconTint = if (isSafeVault) TelegramEmerald else Color(0xFF8B5CF6),
                        title = if (isSafeVault) "Remove from Safe Folder" else "Move to Safe Folder",
                        subtitle = if (isSafeVault) "Remove biometric/PIN protection" else "Protect folder with biometric / PIN lock",
                        onClick = {
                            isSafeVault = prefs.toggleSafeVaultFile(file.path)
                            Toast.makeText(context, if (isSafeVault) "🔒 Folder moved to Safe Folder" else "Removed from Safe Folder", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 7. Multi-Select Mode Trigger
                    if (onSelectMode != null) {
                        FileActionRow(
                            icon = Icons.Default.CheckCircleOutline,
                            iconTint = TelegramBlue,
                            title = "Select",
                            subtitle = "Enter multi-select mode to choose multiple items",
                            onClick = {
                                onDismiss()
                                onSelectMode()
                            }
                        )
                    }

                    // 8. Delete Folder (Move to Trash)
                    FileActionRow(
                        icon = Icons.Default.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "Move Folder to Trash Bin",
                        subtitle = "Moves folder and all its contents to Recycle Bin",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteConfirmDialog = true }
                    )
                } else {
                    // ================= FILE ACTIONS (GOOGLE FILES STYLE) =================
                    if (ext == "APK") {
                        FileActionRow(
                            icon = Icons.Default.Android,
                            iconTint = TelegramEmerald,
                            title = "Install Android App",
                            subtitle = "Decrypt, verify, and launch system package installer",
                            onClick = {
                                onDismiss()
                                FileViewerHelper.openFile(context, file.path, file.fileName, file.telegramFileId)
                            }
                        )
                    }

                    // 1. Open / Preview
                    FileActionRow(
                        icon = Icons.Default.Visibility,
                        iconTint = TelegramBlue,
                        title = "Preview / Open File",
                        subtitle = "Open in built-in media viewer or system application",
                        onClick = {
                            onDismiss()
                            FileViewerHelper.openFile(context, file.path, file.fileName, file.telegramFileId)
                        }
                    )

                    // 1b. Ask AI Document Assistant
                    val isImageOrPdf = ext in listOf("JPG", "JPEG", "PNG", "WEBP", "BMP", "HEIC", "PDF")
                    if (isImageOrPdf) {
                        FileActionRow(
                            icon = Icons.Default.AutoAwesome,
                            iconTint = Color(0xFFFBBF24),
                            title = "Ask AI Document Assistant",
                            subtitle = "Extract marksheet data, 10k receipts, IDs, and answer questions",
                            onClick = {
                                showAskAiSheet = true
                            }
                        )
                    }

                    // 2. Download to Device
                    FileActionRow(
                        icon = Icons.Default.Download,
                        iconTint = TelegramEmerald,
                        title = "Download to Device",
                        subtitle = "Saves copy to Downloads/TelegramBackup/",
                        onClick = {
                            isDownloading = true
                            downloadProgress = 0f
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val targetDir = File(downloadsDir, "TelegramBackup").apply { if (!exists()) mkdirs() }
                                    val targetFile = File(targetDir, file.fileName)
                                    val ok = TelegramMediaResolver.restoreFileToTarget(
                                        context = context,
                                        file = file,
                                        targetFile = targetFile,
                                        onProgress = { p -> downloadProgress = p }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        if (ok && targetFile.exists()) {
                                            Toast.makeText(context, "✅ Downloaded to Downloads/TelegramBackup/${targetFile.name}", Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Download failed for ${file.fileName}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )

                    // 3. Download as ZIP (Single File Archive)
                    FileActionRow(
                        icon = Icons.Default.Archive,
                        iconTint = TelegramEmerald,
                        title = "Download as ZIP",
                        subtitle = "Compresses and saves file into a ZIP archive",
                        onClick = {
                            onDismiss()
                            val zipName = file.fileName.substringBeforeLast('.') + "_Archive"
                            Toast.makeText(context, "📦 Compressing '${file.fileName}' into ZIP...", Toast.LENGTH_SHORT).show()
                            scope.launch(Dispatchers.IO) {
                                ZipBackupDownloader.downloadFilesAsZip(context, listOf(file), zipName)
                            }
                        }
                    )

                    // 4. Send to App (System Share)
                    FileActionRow(
                        icon = Icons.Default.Share,
                        iconTint = Color(0xFF6366F1),
                        title = "Send to App (System Share)",
                        subtitle = "Send directly to WhatsApp, Telegram, Gmail, Bluetooth",
                        onClick = {
                            onDismiss()
                            FileViewerHelper.shareFile(context, file)
                        }
                    )

                    // 5. Share Cloud Link
                    FileActionRow(
                        icon = Icons.Default.Link,
                        iconTint = TelegramBlue,
                        title = "Share Cloud Link & PIN",
                        subtitle = "Opaque shareable link with optional PIN, expiry & QR code",
                        onClick = {
                            showShareModal = true
                        }
                    )

                    // 6. Rename File
                    FileActionRow(
                        icon = Icons.Default.Edit,
                        iconTint = MaterialTheme.colorScheme.onSurface,
                        title = "Rename File",
                        subtitle = "Update filename in local database and cloud index",
                        onClick = {
                            showRenameDialog = true
                        }
                    )

                    // 7. Properties & Info
                    FileActionRow(
                        icon = Icons.Default.Info,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "View Properties & Info",
                        subtitle = "Inspect file path, Telegram File ID, chunks, and timestamp",
                        onClick = {
                            showPropertiesDialog = true
                        }
                    )

                    // 8. Copy File Path
                    FileActionRow(
                        icon = Icons.Default.ContentCopy,
                        iconTint = MaterialTheme.colorScheme.outline,
                        title = "Copy File Path",
                        subtitle = "Copies the complete storage path to system clipboard",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("File Path", file.path))
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 9. Star / Favorite File
                    FileActionRow(
                        icon = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        iconTint = Color(0xFFF9AB00),
                        title = if (isStarred) "Remove from Starred" else "Add to Starred",
                        subtitle = if (isStarred) "Remove from Starred collection" else "Pin to Google Files Starred collection",
                        onClick = {
                            isStarred = prefs.toggleStarredFile(file.path)
                            Toast.makeText(context, if (isStarred) "⭐ Added to Starred" else "Removed from Starred", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 10. Safe Folder Vault
                    FileActionRow(
                        icon = if (isSafeVault) Icons.Default.LockOpen else Icons.Default.Lock,
                        iconTint = if (isSafeVault) TelegramEmerald else Color(0xFF8B5CF6),
                        title = if (isSafeVault) "Remove from Safe Folder" else "Move to Safe Folder",
                        subtitle = if (isSafeVault) "Remove encryption lock from file" else "Protect with biometric fingerprint / PIN",
                        onClick = {
                            isSafeVault = prefs.toggleSafeVaultFile(file.path)
                            Toast.makeText(context, if (isSafeVault) "🔒 Moved to Safe Folder (Protected)" else "Removed from Safe Folder", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 11. Multi-Select Mode Trigger
                    if (onSelectMode != null) {
                        FileActionRow(
                            icon = Icons.Default.CheckCircleOutline,
                            iconTint = TelegramBlue,
                            title = "Select",
                            subtitle = "Enter multi-select mode to choose multiple items",
                            onClick = {
                                onDismiss()
                                onSelectMode()
                            }
                        )
                    }

                    // 12. Delete / Move to Trash
                    FileActionRow(
                        icon = Icons.Default.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "Move to Trash Bin",
                        subtitle = "Can be restored anytime from Recycle Bin",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            } else {
                // ================= TRASH ITEM ACTIONS =================
                // 1. Restore
                FileActionRow(
                    icon = Icons.Default.Restore,
                    iconTint = TelegramEmerald,
                    title = if (isFolder) "Restore Folder from Trash" else "Restore from Trash",
                    subtitle = "Recover item back to cloud storage and active library",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val dao = AppDatabase.getDatabase(context).uploadedFileDao()
                            if (isFolder) {
                                val targetFiles = folderFiles ?: dao.getAllRawFiles().filter { it.path.startsWith("${file.path}/") }
                                targetFiles.forEach { dao.restoreFile(it.path, it.telegramFileId) }
                                dao.restoreFile(file.path, file.telegramFileId)
                            } else {
                                dao.restoreFile(file.path, file.telegramFileId)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Restored ${file.fileName}", Toast.LENGTH_SHORT).show()
                                onFileRestored?.invoke(file)
                                onDismiss()
                            }
                        }
                    }
                )

                // 2. Download / Recover
                if (!isFolder) {
                    FileActionRow(
                        icon = Icons.Default.Download,
                        iconTint = TelegramEmerald,
                        title = "Download / Recover to Device",
                        subtitle = "Saves copy to Downloads/TelegramBackup/",
                        onClick = {
                            isDownloading = true
                            downloadProgress = 0f
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val targetDir = File(downloadsDir, "TelegramBackup").apply { if (!exists()) mkdirs() }
                                    val targetFile = File(targetDir, file.fileName)
                                    val ok = TelegramMediaResolver.restoreFileToTarget(
                                        context = context,
                                        file = file,
                                        targetFile = targetFile,
                                        onProgress = { p -> downloadProgress = p }
                                    )
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        if (ok && targetFile.exists()) {
                                            Toast.makeText(context, "✅ Downloaded to Downloads/TelegramBackup/${targetFile.name}", Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Download failed for ${file.fileName}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isDownloading = false
                                        Toast.makeText(context, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }

                // 3. Properties & Info
                FileActionRow(
                    icon = Icons.Default.Info,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "View Properties & Info",
                    subtitle = "Inspect item path, size, and deletion status",
                    onClick = { showPropertiesDialog = true }
                )

                // 4. Copy Path
                FileActionRow(
                    icon = Icons.Default.ContentCopy,
                    iconTint = MaterialTheme.colorScheme.outline,
                    title = "Copy Storage Path",
                    subtitle = "Copies the complete storage path to system clipboard",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Path", file.path))
                        Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                // 5. Delete Permanently
                FileActionRow(
                    icon = Icons.Default.DeleteForever,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = if (isFolder) "Delete Folder Permanently" else "Delete Permanently",
                    subtitle = "Irreversibly delete item from cloud and database",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteConfirmDialog = true }
                )
            }
        }
    }

    // SUB-DIALOG 1: SHARE LINK MODAL
    if (showShareModal) {
        ShareLinkModal(
            file = file,
            onDismiss = {
                showShareModal = false
                onDismiss()
            }
        )
    }

    if (showAskAiSheet) {
        val localDisk = resolveLocalDiskFile(context, file.path, file.telegramFileId, file.fileName)
        if (localDisk != null && localDisk.exists() && localDisk.length() > 0) {
            com.example.telegrambackup.ui.ai.AskPhotoSheet(
                targetFile = localDisk,
                fileName = file.fileName,
                filePath = file.path,
                telegramFileId = file.telegramFileId,
                onDismiss = { showAskAiSheet = false }
            )
        } else {
            var cachedTarget by remember { mutableStateOf<File?>(null) }
            LaunchedEffect(file.telegramFileId) {
                withContext(Dispatchers.IO) {
                    val fetched = TelegramMediaResolver.fetchToTempCache(context, file.telegramFileId, file.fileName)
                    withContext(Dispatchers.Main) {
                        cachedTarget = fetched
                        if (fetched == null) {
                            Toast.makeText(context, "Could not load file for AI analysis", Toast.LENGTH_SHORT).show()
                            showAskAiSheet = false
                        }
                    }
                }
            }
            if (cachedTarget != null) {
                com.example.telegrambackup.ui.ai.AskPhotoSheet(
                    targetFile = cachedTarget!!,
                    fileName = file.fileName,
                    filePath = file.path,
                    telegramFileId = file.telegramFileId,
                    onDismiss = { showAskAiSheet = false }
                )
            }
        }
    }

    // SUB-DIALOG 2: RENAME DIALOG
    if (showRenameDialog) {
        var newNameInput by remember { mutableStateOf(file.fileName) }
        var isRenaming by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isRenaming) showRenameDialog = false },
            title = {
                Text(
                    text = if (isFolder) "Rename Folder" else "Rename File",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        "Enter the new name for this item:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newNameInput.trim()
                        if (trimmed.isEmpty() || trimmed == file.fileName) {
                            showRenameDialog = false
                            return@Button
                        }
                        isRenaming = true
                        scope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            val dao = db.uploadedFileDao()
                            val chunkDao = db.chunkProgressDao()
                            val oldPath = file.path
                            val parent = File(oldPath).parent?.replace("\\", "/") ?: ""
                            val newPath = if (parent.isNotEmpty()) "$parent/$trimmed" else trimmed

                            if (isFolder) {
                                val allFiles = dao.getAllRawFiles()
                                val children = allFiles.filter { it.path.startsWith("$oldPath/") }
                                children.forEach { child ->
                                    val newChildPath = if (child.path.startsWith(oldPath)) {
                                        newPath + child.path.substring(oldPath.length)
                                    } else {
                                        child.path
                                    }
                                    dao.deleteByPath(child.path)
                                    dao.insert(child.copy(path = newChildPath))
                                    try { chunkDao.updateSourcePath(child.path, newChildPath) } catch (_: Exception) {}
                                }
                            }

                            val updated = file.copy(
                                path = newPath,
                                fileName = trimmed
                            )
                            dao.deleteByPath(oldPath)
                            dao.insert(updated)
                            try { chunkDao.updateSourcePath(oldPath, newPath) } catch (_: Exception) {}

                            val userEmail = AppPrefs(context).userEmail
                            val serverUrl = AppPrefs(context).serverUrl
                            if (userEmail.isNotEmpty()) {
                                try {
                                    val serverApi = ServerApi()
                                    serverApi.deleteFile(serverUrl, userEmail, file.telegramFileId, oldPath)
                                    serverApi.indexFile(
                                        serverUrl = serverUrl,
                                        email = userEmail,
                                        fileName = trimmed,
                                        telegramFileId = file.telegramFileId,
                                        size = file.size,
                                        path = newPath,
                                        timestamp = file.timestamp,
                                        messageId = file.messageId,
                                        isDriveUpload = file.isDriveUpload,
                                        chunksJson = file.chunksJson,
                                        channelId = file.channelId,
                                        botTokenUsed = file.botTokenUsed,
                                        totalChunks = file.totalChunks
                                    )
                                } catch (_: Exception) {}
                            }

                            withContext(Dispatchers.Main) {
                                isRenaming = false
                                showRenameDialog = false
                                Toast.makeText(context, "Renamed to $trimmed", Toast.LENGTH_SHORT).show()
                                onFileRenamed?.invoke(oldPath, trimmed, newPath)
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }, enabled = !isRenaming) {
                    Text("Cancel")
                }
            }
        )
    }

    // SUB-DIALOG 3: TECHNICAL PROPERTIES & INFO
    if (showPropertiesDialog) {
        val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.Info,
                        contentDescription = null,
                        tint = TelegramBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isFolder) "Folder Properties" else "File Properties",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isFolder) {
                        PropertyRow(label = "Folder Name", value = file.fileName)
                        PropertyRow(label = "Total Size", value = "${formatFileSize(folderTotalSize)} (${numberFormat.format(folderTotalSize)} bytes)")
                        PropertyRow(label = "Contained Files", value = "$folderItemCount files inside folder")
                        PropertyRow(label = "Type", value = "Folder Directory")
                        PropertyRow(label = "Storage Path", value = file.path, canCopy = true, context = context)
                        PropertyRow(
                            label = "Encryption",
                            value = "AES-256-GCM Zero-Knowledge",
                            color = TelegramEmerald
                        )
                        PropertyRow(
                            label = "Storage Status",
                            value = if (file.isDeleted) "In Trash Bin" else if (file.isDriveUpload) "Personal Cloud Drive" else "Encrypted Telegram Storage",
                            color = if (file.isDeleted) MaterialTheme.colorScheme.error else TelegramBlue
                        )
                    } else {
                        PropertyRow(label = "File Name", value = file.fileName)
                        PropertyRow(label = "File Size", value = "${formatFileSize(file.size)} (${numberFormat.format(file.size)} bytes)")
                        PropertyRow(label = "Type", value = ext.ifEmpty { "Binary Data" })
                        PropertyRow(label = "Storage Path", value = file.path, canCopy = true, context = context)
                        PropertyRow(label = "Cloud File ID", value = file.telegramFileId.ifEmpty { "Local Unsynced" }, canCopy = file.telegramFileId.isNotEmpty(), context = context)
                        if (file.messageId > 0) {
                            PropertyRow(label = "Telegram Message ID", value = file.messageId.toString())
                        }
                        PropertyRow(
                            label = "Multi-Chunk Status",
                            value = if (file.totalChunks > 1) "Multi-part (${file.totalChunks} chunks assembled)" else "Single part (Direct stream)"
                        )
                        PropertyRow(label = "Date Added", value = dateFormat.format(Date(if (file.timestamp > 0) file.timestamp else System.currentTimeMillis())))
                        PropertyRow(
                            label = "Encryption",
                            value = "AES-256-GCM Zero-Knowledge",
                            color = TelegramEmerald
                        )
                        PropertyRow(
                            label = "Storage Status",
                            value = if (file.isDeleted) "In Trash Bin" else if (file.isDriveUpload) "Personal Cloud Drive" else "Encrypted Telegram Backup",
                            color = if (file.isDeleted) MaterialTheme.colorScheme.error else TelegramBlue
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPropertiesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // SUB-DIALOG 4: DELETE CONFIRMATION DIALOG
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = when {
                        isTrashItem && isFolder -> "Permanently Delete Folder?"
                        isTrashItem -> "Permanently Delete File?"
                        isFolder -> "Move Folder to Trash Bin?"
                        else -> "Move to Trash Bin?"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = when {
                        isTrashItem && isFolder ->
                            "Are you sure you want to permanently delete folder '${file.fileName}' and all its $folderItemCount contained files? This cannot be undone."
                        isTrashItem ->
                            "Are you sure you want to permanently delete '${file.fileName}'? This will delete the file from the Telegram cloud and local database permanently. This action cannot be undone."
                        isFolder ->
                            "Move folder '${file.fileName}' and its $folderItemCount files to Recycle Bin? You can restore it anytime."
                        else ->
                            "Move '${file.fileName}' to Recycle Bin? You can restore it anytime or let auto-clean manage it."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        scope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(context)
                            val dao = db.uploadedFileDao()
                            val now = System.currentTimeMillis()
                            val prefs = AppPrefs(context)
                            val userEmail = prefs.userEmail
                            val serverUrl = prefs.serverUrl
                            val botToken = prefs.botToken
                            val chatId = prefs.chatId
                            val serverApi = ServerApi()

                            if (isFolder) {
                                val targetFiles = folderFiles ?: dao.getAllRawFiles().filter { it.path.startsWith("${file.path}/") }
                                targetFiles.forEach { child ->
                                    if (isTrashItem) {
                                        try {
                                            serverApi.purgeFileCompleteFromTelegram(
                                                context = context,
                                                botToken = botToken,
                                                chatId = chatId,
                                                file = child,
                                                forceLocalDelete = true
                                            )
                                        } catch (_: Exception) {}
                                        dao.deleteByPath(child.path)
                                        if (child.telegramFileId.isNotBlank() && child.telegramFileId != "DIR_MARKER") {
                                            dao.deleteByTelegramFileId(child.telegramFileId)
                                        }
                                        db.chunkProgressDao().deleteChunksForFile(child.path)
                                        if (userEmail.isNotEmpty() && child.telegramFileId.isNotEmpty()) {
                                            try { serverApi.deleteFile(serverUrl, userEmail, child.telegramFileId, child.path) } catch (_: Exception) {}
                                        }
                                    } else {
                                        dao.softDelete(child.path, child.telegramFileId, now)
                                        if (userEmail.isNotEmpty() && child.telegramFileId.isNotEmpty()) {
                                            try { serverApi.deleteFile(serverUrl, userEmail, child.telegramFileId, child.path) } catch (_: Exception) {}
                                        }
                                    }
                                }
                                if (isTrashItem) {
                                    dao.deleteByPath(file.path)
                                } else {
                                    dao.softDelete(file.path, file.telegramFileId, now)
                                }
                            } else {
                                if (isTrashItem) {
                                    try {
                                        serverApi.purgeFileCompleteFromTelegram(
                                            context = context,
                                            botToken = botToken,
                                            chatId = chatId,
                                            file = file,
                                            forceLocalDelete = true
                                        )
                                    } catch (_: Exception) {}
                                    dao.deleteByPath(file.path)
                                    if (file.telegramFileId.isNotBlank() && file.telegramFileId != "DIR_MARKER") {
                                        dao.deleteByTelegramFileId(file.telegramFileId)
                                    }
                                    db.chunkProgressDao().deleteChunksForFile(file.path)
                                    if (userEmail.isNotEmpty() && file.telegramFileId.isNotEmpty()) {
                                        try { serverApi.deleteFile(serverUrl, userEmail, file.telegramFileId, file.path) } catch (_: Exception) {}
                                    }
                                } else {
                                    dao.softDelete(file.path, file.telegramFileId, now)
                                    if (userEmail.isNotEmpty() && file.telegramFileId.isNotEmpty()) {
                                        try { serverApi.deleteFile(serverUrl, userEmail, file.telegramFileId, file.path) } catch (_: Exception) {}
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (isTrashItem) "Permanently deleted ${file.fileName}" else "Moved to Trash Bin",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onFileDeleted?.invoke(file)
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isTrashItem) "Delete Forever" else "Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Clean Material 3 Row for each item inside the Action Menu Modal
 */
@Composable
private fun FileActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Property inspector row with copy support
 */
@Composable
private fun PropertyRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    canCopy: Boolean = false,
    context: Context? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline
            )
            if (canCopy && context != null && value.isNotBlank()) {
                Text(
                    text = "COPY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = TelegramBlue,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
                        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (canCopy) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}
