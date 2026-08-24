package com.example.telegrambackup.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.BotFleetImportExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BotFleetImportExportDialog(
    initialMode: Int = 0, // 0 = Export, 1 = Import
    onDismiss: () -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var activeTab by remember { mutableIntStateOf(initialMode) }
    var exportJsonText by remember { mutableStateOf("") }
    var importInputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        exportJsonText = BotFleetImportExportManager.exportBotsToJson(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (activeTab == 0) TelegramBlue.copy(alpha = 0.15f) else TelegramEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (activeTab == 0) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (activeTab == 0) TelegramBlue else TelegramEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                if (activeTab == 0) "Export Bot Fleet & Info" else "Import Bot Tokens & Fleet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Database & Multi-Bot Backup",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (activeTab == 0) TelegramBlue else Color.Transparent)
                            .clickable {
                                activeTab = 0
                                statusMessage = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📤 Export Fleet",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (activeTab == 1) TelegramEmerald else Color.Transparent)
                            .clickable {
                                activeTab = 1
                                statusMessage = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📥 Import Fleet",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeTab == 0) {
                    // EXPORT TAB CONTENT
                    Text(
                        "All configured bots, tokens, usernames, and stats formatted as portable JSON backup:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = exportJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                BotFleetImportExportManager.copyToClipboard(context, exportJsonText, "Bot Fleet JSON")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy JSON", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                BotFleetImportExportManager.shareExportText(context, exportJsonText, "Telegram Bot Fleet Backup")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            BotFleetImportExportManager.saveToDownloads(context, exportJsonText)
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Downloads Folder (.json)", fontSize = 13.sp)
                    }
                } else {
                    // IMPORT TAB CONTENT
                    Text(
                        "Paste JSON export data or a list of bot tokens (e.g. 123456:ABC...). All imported bots will be verified, stored in Room DB, and synced to cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = {
                            importInputText = it
                            statusMessage = null
                        },
                        placeholder = { Text("Paste JSON export or bot tokens here...\n\nExample:\n123456789:ABCDefGHIJKlmnoPQRstuVWxyz...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (statusMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (isError) MaterialTheme.colorScheme.error else TelegramEmerald).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isError) MaterialTheme.colorScheme.error else TelegramEmerald
                                ),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboardManager.getText()
                                if (clip != null && clip.text.isNotBlank()) {
                                    importInputText = clip.text
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste Clip", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (importInputText.isBlank()) {
                                    isError = true
                                    statusMessage = "Please paste bot tokens or JSON data first."
                                    return@Button
                                }
                                isProcessing = true
                                isError = false
                                statusMessage = "Importing and syncing to database..."

                                scope.launch(Dispatchers.IO) {
                                    val (count, msg) = BotFleetImportExportManager.importBotsFromJson(context, importInputText)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (count > 0) {
                                            isError = false
                                            statusMessage = "✓ $msg"
                                            onImportSuccess()
                                        } else {
                                            isError = true
                                            statusMessage = "⚠️ $msg"
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing && importInputText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import & Sync", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
