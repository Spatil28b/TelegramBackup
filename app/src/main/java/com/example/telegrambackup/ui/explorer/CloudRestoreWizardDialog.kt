package com.example.telegrambackup.ui.explorer

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.BotFleetManager
import com.example.telegrambackup.network.HttpClientProvider
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.DynamicProcessTracker
import com.example.telegrambackup.utils.FileEncryptor
import com.example.telegrambackup.utils.TelegramMediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudRestoreWizardDialog(
    filesToRestore: List<UploadedFile>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    var hasBotToken by remember { mutableStateOf(prefs.botToken.isNotBlank()) }
    LaunchedEffect(Unit) {
        val tokens = TelegramMediaResolver.getCandidateBotTokens(context)
        hasBotToken = tokens.isNotEmpty()
    }

    var isRestoring by remember { mutableStateOf(false) }
    var currentFileIndex by remember { mutableIntStateOf(0) }
    var currentFileName by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Ready to restore") }

    val restoreFolder = remember {
        val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(dlDir, "TelegramRestored")
        if (!target.exists()) target.mkdirs()
        target
    }

    if (DynamicProcessTracker.isMinimized) {
        return
    }

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = TelegramBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Cloud Restore Wizard",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Restore selected files from your Telegram Cloud back to your device storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Info Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Files to Restore:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("${filesToRestore.size} items", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Folder:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("Downloads/TelegramRestored", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        if (prefs.encryptionKey.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Decryption:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("🔒 Auto (AES-256)", style = MaterialTheme.typography.bodySmall, color = TelegramEmerald)
                            }
                        }
                    }
                }

                if (isRestoring) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Restoring ${currentFileIndex + 1}/${filesToRestore.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${(progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelegramBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = TelegramBlue
                        )
                        Text(
                            currentFileName.ifEmpty { statusMessage },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!hasBotToken) {
                        Toast.makeText(context, "Telegram Bot Token required to download files!", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    isRestoring = true
                    com.example.telegrambackup.utils.DynamicProcessTracker.startProcess(
                        title = "Restoring Files (${filesToRestore.size})",
                        subtitle = "Starting Cloud Restore...",
                        icon = androidx.compose.material.icons.Icons.Default.CloudDownload
                    )

                    scope.launch(Dispatchers.IO) {
                        val concurrency = (prefs.uploadThreads).coerceIn(1, 16)
                        val restoreSemaphore = Semaphore(concurrency)
                        val successCounter = java.util.concurrent.atomic.AtomicInteger(0)
                        val processedCounter = java.util.concurrent.atomic.AtomicInteger(0)

                        kotlinx.coroutines.coroutineScope {
                            val jobs = filesToRestore.mapIndexed { idx, item ->
                                launch {
                                    restoreSemaphore.withPermit {
                                        try {
                                            val targetFile = File(restoreFolder, item.fileName)
                                            if (item.telegramFileId.isNotEmpty() && item.telegramFileId != "DIR_MARKER") {
                                                val ok = TelegramMediaResolver.restoreFileToTarget(context, item, targetFile)
                                                if (ok && targetFile.exists() && targetFile.length() > 0) {
                                                    successCounter.incrementAndGet()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("CloudRestore", "Failed to restore ${item.fileName}", e)
                                        } finally {
                                            val count = processedCounter.incrementAndGet()
                                            val frac = count.toFloat() / filesToRestore.size.toFloat()
                                            withContext(Dispatchers.Main) {
                                                currentFileIndex = count - 1
                                                currentFileName = item.fileName
                                                progressFraction = frac
                                                statusMessage = "Restored $count of ${filesToRestore.size} files"
                                                com.example.telegrambackup.utils.DynamicProcessTracker.updateProgress(
                                                    progress = frac,
                                                    subtitle = "Restoring $count/${filesToRestore.size}: ${item.fileName}"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            jobs.joinAll()
                        }

                        val totalRestored = successCounter.get()
                        withContext(Dispatchers.Main) {
                            isRestoring = false
                            com.example.telegrambackup.utils.DynamicProcessTracker.finishProcess()
                            Toast.makeText(context, "✅ Successfully restored $totalRestored of ${filesToRestore.size} files to ${restoreFolder.name}", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !isRestoring && filesToRestore.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald)
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Start Restore", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isRestoring) onDismiss() }) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
