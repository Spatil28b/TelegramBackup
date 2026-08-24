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

data class GuideSection(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val steps: List<String>
)

@Composable
fun GuideScreen() {
    val sections = remember {
        listOf(
            GuideSection(
                Icons.Default.SmartToy, TelegramBlue,
                "Step 1: Create a Telegram Bot",
                listOf(
                    "1. Open Telegram and search for @BotFather",
                    "2. Start a chat and send the command /newbot",
                    "3. Give your bot a display name (e.g., 'My Backup Bot')",
                    "4. Give your bot a username ending in 'bot' (e.g., 'mybackup_bot')",
                    "5. BotFather will reply with your Bot API Token — copy it!",
                    "6. IMPORTANT: Keep this token private. Anyone with it can control your bot."
                )
            ),
            GuideSection(
                Icons.Default.Chat, TelegramEmerald,
                "Step 2: Get Your Chat ID (Instant & Easy)",
                listOf(
                    "Option A — Direct Chat ID (Easiest in 5 seconds):",
                    "   1. Open Telegram and search for @userinfobot or @getmyid_bot",
                    "   2. Tap 'Start' or send /start",
                    "   3. The bot will instantly reply with your Chat ID (e.g. 123456789) — copy it!",
                    "",
                    "Option B — Private Backup Channel (Best for organized storage):",
                    "   1. Create a new Private Channel in Telegram (e.g. 'My Cloud Backup')",
                    "   2. Add your Bot as Administrator with 'Post Messages' & 'Delete Messages' enabled",
                    "   3. Send any text message in your new channel",
                    "   4. Forward that message to @userinfobot or @getmyid_bot",
                    "   5. The bot will reply with your Channel ID starting with '-100' (e.g. -1001234567890) — copy it!"
                )
            ),
            GuideSection(
                Icons.Default.Key, Color(0xFFF59E0B),
                "Step 3: Set Up Credentials in App",
                listOf(
                    "1. Open Settings → Telegram Cloud Credentials",
                    "2. Paste your Bot API Token in the first field",
                    "3. Paste your Chat ID in the second field",
                    "4. Tap 'Verify Credentials Now' to test the connection",
                    "5. If verification succeeds, tap 'Save & Lock Credentials'",
                    "6. After saving, credentials are locked — you'll need OTP to modify them later"
                )
            ),
            GuideSection(
                Icons.Default.CloudSync, Color(0xFF8B5CF6),
                "Step 4: How Smart Backup Works",
                listOf(
                    "1. Go to Settings → Smart Backup Engine",
                    "2. Enable 'Smart Backup' toggle",
                    "3. Choose your backup mode:",
                    "   • 🔄 Continuous — Checks every 15 min automatically",
                    "   • ⏰ Custom — Pick interval (1h, 3h, 6h, 12h, 24h)",
                    "   • 🌙 Night Express — Runs at 1-5 AM with max speed",
                    "4. Set file size filters (Min/Max) to control what gets backed up",
                    "5. Toggle folder sources ON/OFF (Camera, Downloads, Documents, etc.)",
                    "6. Backup runs in the background even when the app is closed!"
                )
            ),
            GuideSection(
                Icons.Default.Security, Color(0xFFEC4899),
                "Step 5: Encryption & Security",
                listOf(
                    "1. Every file is encrypted with AES-256 before leaving your device",
                    "2. Your unique Master Key is generated once and never changes",
                    "3. Go to Settings → Security to see your Master Key",
                    "4. Tap 'Email Encryption Key to Me' to receive a backup via email",
                    "5. Store the key safely — without it, files cannot be decrypted!",
                    "6. Enable Biometric Lock for extra app security",
                    "7. IMPORTANT: No one (not even us) can read your encrypted files"
                )
            ),
            GuideSection(
                Icons.Default.CalendarMonth, Color(0xFF06B6D4),
                "Step 6: Monthly Archive",
                listOf(
                    "1. Open the sidebar → Monthly Archive",
                    "2. Files are grouped by month (e.g., 'August 2026 — 15 files')",
                    "3. Use the filter icon to select a specific month/year",
                    "4. Tap a month header to expand and see individual files",
                    "5. Tap the download icon on a month to get the entire month as ZIP",
                    "6. Long-press files to select multiple → Download as ZIP",
                    "7. Each month starts fresh — perfect for organized backups!"
                )
            ),
            GuideSection(
                Icons.Default.DeleteSweep, Color(0xFFEF4444),
                "Step 7: Trash Bin & Auto-Clean",
                listOf(
                    "1. Deleted files go to the Trash Bin (sidebar → Trash Bin)",
                    "2. You can restore or permanently delete items from trash",
                    "3. Go to Settings → Danger Zone → Trash Auto-Clean Policy",
                    "4. Choose auto-clean interval: Never, 7, 14, 30, 60, or 90 days",
                    "5. Items older than the policy are auto-deleted when you open Trash",
                    "6. The Trash header shows remaining days for each item"
                )
            ),
            GuideSection(
                Icons.Default.BugReport, Color(0xFF64748B),
                "Step 8: Troubleshooting",
                listOf(
                    "1. OTP not received? Check spam folder and wait 2 minutes",
                    "2. Bot not sending messages? Verify bot is Admin in the channel",
                    "3. Backup not working? Check: Wi-Fi only toggle, Stop Backup toggle",
                    "4. Files not showing? Pull down to refresh on any screen",
                    "5. Encryption key lost? Check your email for the key backup",
                    "6. App crashing? Clear app data in Android Settings (last resort)",
                    "7. Need to change credentials? Settings → Credentials → OTP unlock"
                )
            )
        )
    }

    var expandedIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Header Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = TelegramBlue.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        "Complete Setup Guide",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Follow these 8 steps to set up your cloud backup",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accordion Sections
        sections.forEachIndexed { index, section ->
            val isExpanded = expandedIndex == index

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (isExpanded) 3.dp else 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column {
                    // Section Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedIndex = if (isExpanded) -1 else index
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(section.iconTint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(section.icon, contentDescription = null, tint = section.iconTint, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "${section.steps.size} steps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Expanded Steps
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 66.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            section.steps.forEach { step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    if (!step.startsWith("   ")) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(section.iconTint)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        step.trimStart(),
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

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom tip
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = TelegramEmerald.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Tip: Once you complete setup, the app handles everything automatically in the background!",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TelegramEmerald
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
