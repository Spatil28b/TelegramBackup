package com.example.telegrambackup.ui.explorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.utils.ShareLinkManager
import com.example.telegrambackup.utils.ShareLinkResult
import kotlinx.coroutines.launch

@Composable
fun ShareLinkModal(
    file: UploadedFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expiryHours by remember { mutableIntStateOf(24) }
    var passwordPin by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<ShareLinkResult?>(null) }

    fun generateLink() {
        scope.launch {
            isGenerating = true
            val res = ShareLinkManager.generateShareLink(context, file, expiryHours, passwordPin)
            shareResult = res
            isGenerating = false
        }
    }

    LaunchedEffect(expiryHours, passwordPin) {
        generateLink()
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
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Fast Cloud Share Link", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(file.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Expiry Selector
                Column {
                    Text("Link Expiration", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(Pair(1, "1 Hour"), Pair(24, "24 Hours"), Pair(168, "7 Days"), Pair(0, "Permanent")).forEach { (hrs, label) ->
                            FilterChip(
                                selected = expiryHours == hrs,
                                onClick = { expiryHours = hrs },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Optional Passcode Protection
                OutlinedTextField(
                    value = passwordPin,
                    onValueChange = { passwordPin = it },
                    label = { Text("Optional Access PIN / Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Generated URL Preview & Copy Box
                val currentResult = shareResult
                if (currentResult != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("STREAMING URL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = TelegramBlue)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(currentResult.shareUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Share Link", currentResult.shareUrl))
                                Toast.makeText(context, "🔗 Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TelegramBlue)
                            }
                        }
                    }
                }

                // Share Sheet Action Button
                Button(
                    onClick = {
                        val res = shareResult
                        if (res != null) {
                            ShareLinkManager.launchShareSheet(context, res.formattedShareText, "Share ${file.fileName}")
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isGenerating && shareResult != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share via WhatsApp / Telegram", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
