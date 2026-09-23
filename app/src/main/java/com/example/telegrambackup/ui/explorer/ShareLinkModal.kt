package com.example.telegrambackup.ui.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.utils.ShareLinkManager
import com.example.telegrambackup.utils.ShareLinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * =====================================================================================
 * GOOGLE MATERIAL 3 CLOUD SHARE SHEET & QR PORTAL
 * =====================================================================================
 * Provides an ultra-secure, Material 3 dialog for generating opaque file sharing links,
 * setting dynamic expiration limits, adding PIN access protection, and instant QR generation.
 * =====================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLinkModal(
    file: UploadedFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expiryHours by remember { mutableIntStateOf(24) }
    var passwordPin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<ShareLinkResult?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var copiedFeedback by remember { mutableStateOf(false) }

    fun generateLink() {
        scope.launch {
            isGenerating = true
            copiedFeedback = false
            val res = ShareLinkManager.generateShareLink(context, file, expiryHours, passwordPin)
            shareResult = res

            if (showQrCode && res.shareUrl.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    qrBitmap = ShareLinkManager.generateQrBitmap(res.shareUrl, 512)
                }
            }
            isGenerating = false
        }
    }

    LaunchedEffect(expiryHours, passwordPin) {
        generateLink()
    }

    LaunchedEffect(showQrCode, shareResult) {
        val url = shareResult?.shareUrl
        if (showQrCode && !url.isNullOrBlank() && qrBitmap == null) {
            withContext(Dispatchers.IO) {
                qrBitmap = ShareLinkManager.generateQrBitmap(url, 512)
            }
        }
    }

    val ext = file.fileName.substringAfterLast('.', "").lowercase()
    val isVideo = listOf("mp4", "mkv", "mov", "avi", "webm").contains(ext)
    val isAudio = listOf("mp3", "m4a", "wav", "flac", "ogg", "aac").contains(ext)
    val isImage = listOf("jpg", "jpeg", "png", "webp", "gif", "svg").contains(ext)
    val isPdf = ext == "pdf"

    val fileCategoryIcon = when {
        isVideo -> Icons.Default.Videocam
        isAudio -> Icons.Default.Audiotrack
        isImage -> Icons.Default.Image
        isPdf -> Icons.Default.PictureAsPdf
        else -> Icons.Default.Description
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = fileCategoryIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Secure Cloud Share",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = file.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // File Metadata Pill Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(".${ext.uppercase()}", fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = null
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(formatFileSize(file.size)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                    if (file.totalChunks > 1) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${file.totalChunks} Chunks") },
                            leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = null
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Expiration Limit Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "LINK EXPIRATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair(1, "1 Hour"),
                            Pair(24, "24 Hours"),
                            Pair(168, "7 Days"),
                            Pair(0, "Permanent")
                        ).forEach { (hrs, label) ->
                            FilterChip(
                                selected = expiryHours == hrs,
                                onClick = { expiryHours = hrs },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (expiryHours == hrs) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Security Access PIN
                OutlinedTextField(
                    value = passwordPin,
                    onValueChange = { passwordPin = it },
                    label = { Text("Optional Access PIN / Password") },
                    placeholder = { Text("Leave empty for public link") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (passwordPin.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingIcon = {
                        if (passwordPin.isNotBlank()) {
                            IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                Icon(
                                    imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle PIN visibility",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Mode Toggle: URL Link vs QR Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showQrCode) "SCAN QR CODE" else "DIRECT CLOUD LINK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { showQrCode = !showQrCode },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (showQrCode) Icons.Default.Link else Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showQrCode) "Switch to Link" else "Show QR Code", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Loading State or Content View
                if (isGenerating) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Generating verified secure cloud link...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    val currentResult = shareResult
                    if (currentResult != null) {
                        if (showQrCode) {
                            // QR Code Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val bmp = qrBitmap
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Share QR Code",
                                            modifier = Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(200.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = TelegramBlue)
                                        }
                                    }
                                    Text(
                                        text = "Scan with any phone camera to preview or download",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Link Card
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (currentResult.isOpaqueSecure) Icons.Default.Shield else Icons.Default.Link,
                                                contentDescription = null,
                                                tint = if (currentResult.isOpaqueSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (currentResult.isOpaqueSecure) "PROTECTED OPAQUE URL" else "CLOUD ACCESS URL",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                                color = if (currentResult.isOpaqueSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentResult.shareUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Share Link", currentResult.shareUrl))
                                            copiedFeedback = true
                                            Toast.makeText(context, "✓ Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (copiedFeedback) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = if (copiedFeedback) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Security & Privacy Badges
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Zero-Knowledge Security: Master Keys & Bot Tokens remain hidden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Dynamic CDN Paths: Dynamic resolution prevents link expiration",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Bottom Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val res = shareResult
                            if (res != null) {
                                ShareLinkManager.launchShareSheet(context, res.formattedShareText, "Share ${file.fileName}")
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(9999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isGenerating && shareResult != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share via Apps (WhatsApp / Telegram)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(9999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
