package com.example.telegrambackup.ui.explorer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue

@Composable
fun MediaPreviewDialog(
    file: UploadedFile,
    downloadUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileName = file.fileName.ifEmpty { "Media Preview" }
    val isImage = fileName.endsWith(".jpg", true) || fileName.endsWith(".png", true) || fileName.endsWith(".jpeg", true)
    val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || fileName.endsWith(".avi", true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Media Preview Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (downloadUrl != null) {
                        if (isVideo) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(downloadUrl), "video/*")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Play Video"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                Text("Tap to stream video", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        } else {
                            Text(
                                text = "🔐 Encrypted File Decrypted\nReady for previewing & streaming",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = TelegramBlue)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (downloadUrl != null) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                            context.startActivity(Intent.createChooser(intent, "Open Media"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Stream in System Viewer")
                    }
                }
            }
        }
    }
}
