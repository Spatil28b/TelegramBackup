package com.example.telegrambackup.ui.auth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald

/**
 * =====================================================================================
 * REQUIRED PERMISSION SETUP WIZARD DIALOG
 * =====================================================================================
 * Appears automatically after user account creation/login if permissions are missing.
 * Clearly explains why Notification, Storage, and Battery Optimization permissions are
 * required for automatic background Telegram backups.
 * =====================================================================================
 */

fun isNotificationPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun isStoragePermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

fun launchBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

    val packageName = context.packageName

    // Tier 1: Direct System Prompt Dialog (Requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission)
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) { }

    // Tier 2: Battery Optimization Settings List Screen
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
        return
    } catch (_: Exception) { }

    // Tier 3: App Info Details Page -> Battery Settings (For MIUI/Samsung/Realme OEMs)
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

fun areAllRequiredPermissionsGranted(context: Context): Boolean {
    return isNotificationPermissionGranted(context) && isStoragePermissionGranted(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var notifGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var storageGranted by remember { mutableStateOf(isStoragePermissionGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }

    // Launcher for Notification Permission (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notifGranted = isGranted
        if (isGranted) {
            Toast.makeText(context, "✓ Notification Permission Granted!", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-refresh permission state whenever user returns from Android Settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifGranted = isNotificationPermissionGranted(context)
                storageGranted = isStoragePermissionGranted(context)
                batteryIgnored = isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = TelegramBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = "Required Permissions for Cloud Backup",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Welcome to TSpace Cloud! To back up your photos, videos, and documents safely in the background, please grant the following permissions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. NOTIFICATION PERMISSION
                PermissionCardItem(
                    icon = Icons.Default.Notifications,
                    title = "System Notifications",
                    description = "Shows live backup progress, upload speed updates, and status notifications when the app is closed.",
                    isGranted = notifGranted,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } catch (_: Throwable) {}
                        } else {
                            notifGranted = true
                        }
                    }
                )

                // 2. STORAGE & MEDIA PERMISSION
                PermissionCardItem(
                    icon = Icons.Default.Folder,
                    title = "Storage & Files Access",
                    description = "Required to scan device folders (Camera, Downloads, Documents) and upload unbacked files to Telegram Cloud.",
                    isGranted = storageGranted,
                    onRequestPermission = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } catch (_: Throwable) {
                            try {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Throwable) {}
                        }
                    }
                )

                // 3. BATTERY OPTIMIZATION EXCLUSION
                PermissionCardItem(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Unrestricted Background Sync",
                    description = "Prevents Android OS from killing background backups when your phone screen is off.",
                    isGranted = batteryIgnored,
                    onRequestPermission = {
                        launchBatteryOptimizationSettings(context)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    notifGranted = isNotificationPermissionGranted(context)
                    storageGranted = isStoragePermissionGranted(context)
                    batteryIgnored = isBatteryOptimizationIgnored(context)

                    if (areAllRequiredPermissionsGranted(context)) {
                        Toast.makeText(context, "✓ All Permissions Configured! Ready for Cloud Backup.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Please grant Storage and Notification permissions to proceed.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(
                    "Continue to Cloud Backup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Text(
                    "Skip for Now",
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.outline),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    )
}

@Composable
private fun PermissionCardItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isGranted) TelegramEmerald.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = if (isGranted) androidx.compose.foundation.BorderStroke(1.dp, TelegramEmerald.copy(alpha = 0.4f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) TelegramEmerald.copy(alpha = 0.2f) else TelegramBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (isGranted) TelegramEmerald else TelegramBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isGranted) TelegramEmerald.copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isGranted) "✓ Active" else "Grant",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isGranted) TelegramEmerald else Color(0xFFD97706)
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (!isGranted) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Grant Permission", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                    }
                }
            }
        }
    }
}
