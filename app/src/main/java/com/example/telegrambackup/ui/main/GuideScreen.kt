package com.example.telegrambackup.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald

import androidx.compose.material.icons.automirrored.filled.Chat

data class GuideStepItem(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val subtitle: String,
    val details: List<String>
)

@Composable
fun GuideScreen() {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Setup Guide, 1 = User Manual & Features

    val setupSteps = remember {
        listOf(
            GuideStepItem(
                icon = Icons.Default.SmartToy,
                iconTint = TelegramBlue,
                title = "Step 1: Create a Telegram Bot",
                subtitle = "Takes ~30 seconds in Telegram",
                details = listOf(
                    "1. Open Telegram and search for @BotFather",
                    "2. Start the chat and send the command: /newbot",
                    "3. Enter a display name for your bot (e.g., 'My Cloud Backup')",
                    "4. Enter a unique username ending in 'bot' (e.g., 'my_cloud_backup_bot')",
                    "5. BotFather will reply with your Bot API Token — copy it!",
                    "6. 🔒 Keep your token private. Anyone with it can control your bot."
                )
            ),
            GuideStepItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                iconTint = TelegramEmerald,
                title = "Step 2: Get Your Chat ID or Channel ID",
                subtitle = "Two quick options available",
                details = listOf(
                    "Option A — Direct Chat ID (Easiest in 5 seconds):",
                    "   1. Search for @userinfobot or @getmyid_bot in Telegram",
                    "   2. Send /start",
                    "   3. Copy the numeric ID (e.g. 123456789)",
                    "",
                    "Option B — Private Channel (Best for organized cloud vault):",
                    "   1. Create a new Private Channel in Telegram",
                    "   2. Add your Bot as Administrator (Enable 'Post' & 'Delete' permissions)",
                    "   3. Send any message in the channel and forward it to @userinfobot",
                    "   4. Copy the channel ID starting with -100 (e.g. -1001234567890)"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.Key,
                iconTint = Color(0xFFF59E0B),
                title = "Step 3: Connect Credentials in App",
                subtitle = "Save and test your cloud storage connection",
                details = listOf(
                    "1. Go to Settings → Telegram Cloud Credentials",
                    "2. Paste your Bot API Token and Chat ID",
                    "3. Tap 'Verify Credentials Now' to test connection",
                    "4. Tap 'Save & Lock Credentials' to secure them",
                    "5. 🛡️ Credentials are cryptographically locked on your device"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.CheckCircle,
                iconTint = Color(0xFF8B5CF6),
                title = "Step 4: Grant Permissions & Start",
                subtitle = "All Files Access & Notifications",
                details = listOf(
                    "1. Grant 'All files access' when prompted so the app can scan your files",
                    "2. Enable Notifications to view live upload speeds and progress meters",
                    "3. Tap 'Manual Sync' anytime or turn on Smart Auto-Backup",
                    "4. Files are encrypted with AES-256 before leaving your device"
                )
            )
        )
    }

    val manualFeatures = remember {
        listOf(
            GuideStepItem(
                icon = Icons.Default.CloudQueue,
                iconTint = TelegramBlue,
                title = "Cloud Drive & Instant Streaming",
                subtitle = "Browse, search, and stream without downloading",
                details = listOf(
                    "• Browse your complete file hierarchy in real time",
                    "• Direct Video & Audio Streaming: tap any video or music file to stream instantly without saving to phone memory",
                    "• Full-text search by file name, extension, or folder path",
                    "• Chunked 2GB support: large files are split into secure chunks and reassembled on demand"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.Lock,
                iconTint = Color(0xFFEC4899),
                title = "Safe Folder & Master Key Encryption",
                subtitle = "Military-grade AES-256-GCM zero-knowledge vault",
                details = listOf(
                    "• Every file is encrypted using AES-256-GCM with PBKDF2 key derivation",
                    "• Your Master Key is generated once and stored in Android Keystore",
                    "• Safe Folder: hide sensitive documents behind Biometric/PIN lock",
                    "• Tap 'Email Encryption Key to Me' in Settings to keep a secure backup"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.Share,
                iconTint = Color(0xFF06B6D4),
                title = "Direct Cloud Share Links",
                subtitle = "Share any backed-up file with anyone via URL",
                details = listOf(
                    "• Open any file in Cloud Drive and tap 'Generate Share Link'",
                    "• Share links allow anyone with the link to download the file directly via high-speed CDN",
                    "• Set expiration limits or revoke shared links anytime in Settings"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.CalendarMonth,
                iconTint = Color(0xFF10B981),
                title = "Monthly Archives & ZIP Exporter",
                subtitle = "Auto-organized monthly backups",
                details = listOf(
                    "• Files are automatically partitioned into chronological monthly folders",
                    "• One-tap ZIP export: download any entire month as a consolidated ZIP file",
                    "• View cumulative storage used per month at a glance"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFFF59E0B),
                title = "Multi-Bot Fleet & Turbo Concurrency",
                subtitle = "Parallelize uploads for 5x transfer speed",
                details = listOf(
                    "• Add secondary bot tokens in Settings → Bot Fleet Manager",
                    "• Uploads are split across multiple bots simultaneously, bypassing Telegram per-bot rate limits",
                    "• Automatic failover if any bot token experiences network timeout"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.DeleteSweep,
                iconTint = Color(0xFFEF4444),
                title = "Smart Trash Bin & Retention Policies",
                subtitle = "Two-stage deletion with Telegram channel purge",
                details = listOf(
                    "• Deleted files stay in Recycle Bin for 7 to 90 days before automatic purge",
                    "• 1-tap Restore: return any trashed item back into active Cloud Drive",
                    "• Deep Purge: scan and delete orphaned messages directly from your Telegram channel"
                )
            ),
            GuideStepItem(
                icon = Icons.Default.Build,
                iconTint = Color(0xFF64748B),
                title = "Diagnostics & Troubleshooting",
                subtitle = "Quick fixes for common issues",
                details = listOf(
                    "• Bot Not Uploading: Ensure bot is Admin in the channel with 'Post Messages' permission",
                    "• Sync Paused: Check if 'Wi-Fi Only' or 'Stop All Backups' toggle is enabled in Settings",
                    "• Storage Full on Device: Use the Clean tab to delete local copies of files already verified in Cloud",
                    "• Need to Change Credentials: Go to Settings → Credentials → Request OTP unlock"
                )
            )
        )
    }

    var expandedSetupIndex by remember { mutableIntStateOf(-1) }
    var expandedManualIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Modern Segmented Tab Bar Switcher
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                // Setup Guide Tab
                val isSetup = activeTab == 0
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSetup) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSetup) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = 0 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚀 Setup Guide",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSetup) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSetup) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // User Manual & Features Tab
                val isManual = activeTab == 1
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isManual) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isManual) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = 1 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📖 Features & Manual",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isManual) FontWeight.Bold else FontWeight.Medium,
                                color = if (isManual) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content Display based on active tab
        if (activeTab == 0) {
            // SETUP GUIDE LIST
            setupSteps.forEachIndexed { index, item ->
                val isExpanded = expandedSetupIndex == index
                GuideStepCard(
                    item = item,
                    isExpanded = isExpanded,
                    onToggle = { expandedSetupIndex = if (isExpanded) -1 else index }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = TelegramEmerald.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TelegramEmerald.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Once setup is complete, your cloud vault runs continuously in the background.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, color = TelegramEmerald)
                    )
                }
            }
        } else {
            // USER MANUAL & FEATURES LIST
            manualFeatures.forEachIndexed { index, item ->
                val isExpanded = expandedManualIndex == index
                GuideStepCard(
                    item = item,
                    isExpanded = isExpanded,
                    onToggle = { expandedManualIndex = if (isExpanded) -1 else index }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun GuideStepCard(
    item: GuideStepItem,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(item.iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 64.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.details.forEach { detail ->
                        if (detail.isBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                if (!detail.startsWith("   ") && !detail.startsWith("•") && !detail.startsWith("Option")) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(item.iconTint)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = detail.trimStart(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
