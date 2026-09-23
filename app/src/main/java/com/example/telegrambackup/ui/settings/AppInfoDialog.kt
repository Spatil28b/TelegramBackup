package com.example.telegrambackup.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.R
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.update.AppUpdaterDialog
import com.example.telegrambackup.ui.update.AppVersionInfo
import com.example.telegrambackup.ui.update.WhatsNewDialog
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoAvailable by remember { mutableStateOf<AppVersionInfo?>(null) }
    var upToDateText by remember { mutableStateOf("") }
    var showWhatsNewModal by remember { mutableStateOf(false) }

    val currentAppInfo = remember {
        com.example.telegrambackup.utils.AppVersionHelper.getInstalledVersion(context)
    }

    if (showWhatsNewModal) {
        WhatsNewDialog(
            versionName = currentAppInfo.first,
            onDismiss = { showWhatsNewModal = false }
        )
    }

    if (updateInfoAvailable != null) {
        AppUpdaterDialog(
            versionInfo = updateInfoAvailable!!,
            onDismiss = { updateInfoAvailable = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Telegram Backup",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        "Cloud Storage & Vault",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TelegramEmerald,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Minimal Google M3 Version Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Installed Version",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TelegramBlue.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "v${currentAppInfo.first} (${currentAppInfo.second})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TelegramBlue,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Option to view past updates & changelog
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showWhatsNewModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = TelegramEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Past Updates & Changelog",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                "View features & release history",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.outline
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (upToDateText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (upToDateText.startsWith("✅")) TelegramEmerald.copy(alpha = 0.12f) else TelegramBlue.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            upToDateText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (upToDateText.startsWith("✅")) TelegramEmerald else TelegramBlue,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCheckingUpdate) return@Button
                    isCheckingUpdate = true
                    upToDateText = "Checking online server for updates..."
                    scope.launch(Dispatchers.IO) {
                        val serverUrl = prefs.serverUrl.ifEmpty { "https://telegram-backup-backend.vercel.app" }
                        val versionInfo = ServerApi().getLatestAppVersion(serverUrl)
                        withContext(Dispatchers.Main) {
                            isCheckingUpdate = false
                            val isUpdateAvailable = com.example.telegrambackup.utils.AppVersionHelper.isUpdateAvailable(
                                serverVersionName = versionInfo.versionName,
                                serverVersionCode = versionInfo.versionCode,
                                context = context
                            )
                            if (versionInfo.success && isUpdateAvailable && versionInfo.downloadUrl.isNotEmpty()) {
                                updateInfoAvailable = versionInfo
                                upToDateText = ""
                            } else {
                                upToDateText = "✅ App is up to date (v${currentAppInfo.first})"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Checking...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Check for Updates",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    "Close",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    )
}
