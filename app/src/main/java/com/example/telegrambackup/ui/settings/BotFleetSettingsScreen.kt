package com.example.telegrambackup.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.TelegramBotEntity
import com.example.telegrambackup.data.TelegramChannelEntity
import com.example.telegrambackup.network.BotFleetManager
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.CloudProfileSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun formatBytes(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun sanitizeBotToken(raw: String): String {
    var cleaned = raw.trim()
    if (cleaned.startsWith("https://api.telegram.org/bot", ignoreCase = true)) {
        cleaned = cleaned.substring(28).substringBefore("/").substringBefore("?")
    } else if (cleaned.startsWith("http://api.telegram.org/bot", ignoreCase = true)) {
        cleaned = cleaned.substring(27).substringBefore("/").substringBefore("?")
    } else if (cleaned.startsWith("bot", ignoreCase = true) && cleaned.contains(":")) {
        cleaned = cleaned.substring(3)
    }
    return cleaned.trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotFleetSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val prefs = remember { AppPrefs(context) }

    var botsList by remember { mutableStateOf<List<TelegramBotEntity>>(emptyList()) }
    var channelsList by remember { mutableStateOf<List<TelegramChannelEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showAddBotDialog by remember { mutableStateOf(false) }
    var newBotTokenInput by remember { mutableStateOf("") }
    var newBotNameInput by remember { mutableStateOf("") }
    var isVerifyingBot by remember { mutableStateOf(false) }
    var verifiedBotUsername by remember { mutableStateOf("") }
    var verificationErrorMsg by remember { mutableStateOf<String?>(null) }

    var showAddChannelDialog by remember { mutableStateOf(false) }
    var newChatIdInput by remember { mutableStateOf("") }
    var newChannelTitleInput by remember { mutableStateOf("") }
    var newChannelCategory by remember { mutableStateOf("ALL") }

    var showDiagnosticResults by remember { mutableStateOf<String?>(null) }
    var isRunningFleetDiagnostics by remember { mutableStateOf(false) }

    var showImportExportDialog by remember { mutableStateOf(false) }
    var importExportMode by remember { mutableIntStateOf(0) } // 0 = Export, 1 = Import

    var selectedFleetMode by remember { mutableStateOf(prefs.fleetMode) }
    var autoBackupMinBotsState by remember { mutableIntStateOf(prefs.autoBackupMinBots) }
    var driveMinBotsState by remember { mutableIntStateOf(prefs.driveMinBots) }

    fun loadFleetData() {
        scope.launch(Dispatchers.IO) {
            try {
                if (db.telegramBotDao().getAllBots().isEmpty()) {
                    CloudProfileSyncManager.autoRestoreConfigFromCloud(context)
                }
                BotFleetManager.ensureInitialized(context)
                val bots = db.telegramBotDao().getAllBots()
                val channels = db.telegramChannelDao().getAllChannels()
                withContext(Dispatchers.Main) {
                    botsList = bots
                    channelsList = channels
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("BotFleetSettings", "Error loading fleet data", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFleetData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Fleet & Channels", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TelegramBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = TelegramBlue.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(TelegramBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Multi-Bot Fleet & High-Speed Routing", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    "Add extra Telegram bots to eliminate HTTP 429 upload rate-limits, parallelize chunk uploads, and distribute files to specific channels.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(TelegramBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Fleet Concurrency Engine", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Select how multiple bots distribute upload throughput", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("BOOST", "⚡ Boost", "Dedicated quota"),
                                    Triple("BALANCED", "⚖️ Balanced", "Guaranteed floor"),
                                    Triple("MAX", "🚀 Max Speed", "Full parallel pool")
                                ).forEach { (mode, title, _) ->
                                    val isSelected = selectedFleetMode == mode
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) TelegramBlue else MaterialTheme.colorScheme.surface,
                                        tonalElevation = if (isSelected) 4.dp else 1.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedFleetMode = mode
                                                prefs.fleetMode = mode
                                                scope.launch(Dispatchers.IO) { CloudProfileSyncManager.syncLocalConfigToCloud(context) }
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                title,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            when (selectedFleetMode) {
                                "BOOST" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "⚡ Dedicated Bot Allocation: Assign dedicated bots to specific services for zero interference:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🔄 Auto-Backup Dedicated Bots", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (autoBackupMinBotsState > 1) {
                                                            autoBackupMinBotsState--
                                                            prefs.autoBackupMinBots = autoBackupMinBotsState
                                                            scope.launch(Dispatchers.IO) { CloudProfileSyncManager.syncLocalConfigToCloud(context) }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = TelegramBlue) }
                                                Text(
                                                    "$autoBackupMinBotsState",
                                                    fontWeight = FontWeight.Bold,
                                                    color = TelegramBlue,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        autoBackupMinBotsState++
                                                        prefs.autoBackupMinBots = autoBackupMinBotsState
                                                        scope.launch(Dispatchers.IO) { CloudProfileSyncManager.syncLocalConfigToCloud(context) }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) { Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = TelegramBlue) }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("☁️ Cloud Drive Dedicated Bots", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (driveMinBotsState > 1) {
                                                            driveMinBotsState--
                                                            prefs.driveMinBots = driveMinBotsState
                                                            scope.launch(Dispatchers.IO) { CloudProfileSyncManager.syncLocalConfigToCloud(context) }
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = Color(0xFF8B5CF6)) }
                                                Text(
                                                    "$driveMinBotsState",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF8B5CF6),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        driveMinBotsState++
                                                        prefs.driveMinBots = driveMinBotsState
                                                        scope.launch(Dispatchers.IO) { CloudProfileSyncManager.syncLocalConfigToCloud(context) }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) { Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color(0xFF8B5CF6)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                importExportMode = 1
                                showImportExportDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TelegramEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📥 Import Fleet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                importExportMode = 0
                                showImportExportDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TelegramBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📤 Export Fleet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ACTIVE BOT POOL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp))
                            Text("${botsList.count { it.isActive }} of ${botsList.size} bots active in fleet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Button(
                            onClick = {
                                newBotTokenInput = ""
                                newBotNameInput = ""
                                verifiedBotUsername = ""
                                verificationErrorMsg = null
                                showAddBotDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Bot", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                itemsIndexed(botsList, key = { index, bot -> "${bot.botToken}_$index" }) { _, bot ->
                    val isCooldown = bot.rateLimitUntil > System.currentTimeMillis()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (bot.isActive) TelegramBlue.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = if (bot.isActive) TelegramBlue else Color.Gray, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            bot.displayName.ifEmpty { "Telegram Bot" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        if (bot.isPrimary) {
                                             Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = TelegramEmerald.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "PRIMARY",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald, fontSize = 9.sp),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        if (bot.botUsername.isNotEmpty()) "@${bot.botUsername}" else "${bot.botToken.take(15)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Uploaded: ${formatBytes(bot.totalBytesUploaded)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!bot.isPrimary && bot.isActive) {
                                        TextButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    db.telegramBotDao().setPrimaryBot(bot.botToken)
                                                    prefs.botToken = bot.botToken
                                                    CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                                    loadFleetData()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Set Primary", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    Switch(
                                        checked = bot.isActive,
                                        onCheckedChange = { active ->
                                            scope.launch(Dispatchers.IO) {
                                                db.telegramBotDao().setBotActive(bot.botToken, active)
                                                CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                                loadFleetData()
                                            }
                                        },
                                        modifier = Modifier.height(28.dp)
                                    )

                                    if (botsList.size > 1) {
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    db.telegramBotDao().deleteByToken(bot.botToken)
                                                    CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                                    loadFleetData()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            newBotTokenInput = ""
                            newBotNameInput = ""
                            verifiedBotUsername = ""
                            verificationErrorMsg = null
                            showAddBotDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("➕ Add Another Bot to Fleet", fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("STORAGE DESTINATION CHANNELS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp))
                            Text("${channelsList.size} channels configured", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Button(
                            onClick = {
                                newChatIdInput = ""
                                newChannelTitleInput = ""
                                newChannelCategory = "ALL"
                                showAddChannelDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Channel", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                itemsIndexed(channelsList, key = { index, channel -> "${channel.chatId}_$index" }) { _, channel ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(TelegramEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            channel.title.ifEmpty { "Backup Channel" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Chat ID: ${channel.chatId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("ALL", "PHOTOS", "VIDEOS", "DOCUMENTS", "DRIVE").forEach { cat ->
                                        FilterChip(
                                            selected = channel.categoryTarget == cat,
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    db.telegramChannelDao().updateCategoryTarget(channel.chatId, cat)
                                                    CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                                    loadFleetData()
                                                }
                                            },
                                            label = { Text(cat, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                if (channelsList.size > 1) {
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                db.telegramChannelDao().deleteById(channel.chatId)
                                                CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                                loadFleetData()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            newChatIdInput = ""
                            newChannelTitleInput = ""
                            newChannelCategory = "ALL"
                            showAddChannelDialog = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("➕ Add Another Destination Channel", fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (isRunningFleetDiagnostics) return@Button
                            isRunningFleetDiagnostics = true
                            showDiagnosticResults = "Testing all bots and channels in fleet..."
                            scope.launch(Dispatchers.IO) {
                                val report = StringBuilder()
                                report.append("🔍 FLEET DIAGNOSTIC RESULTS:\n\n")

                                val currentBots = db.telegramBotDao().getAllBots()
                                val currentChannels = db.telegramChannelDao().getAllChannels()

                                report.append("🤖 BOTS (${currentBots.size}):\n")
                                currentBots.forEachIndexed { i, bot ->
                                    val testRes = BotFleetManager.testBotToken(bot.botToken)
                                    if (testRes.first) {
                                        report.append("  ✓ Bot ${i + 1}: ${testRes.second} — ONLINE\n")
                                    } else {
                                        report.append("  ✗ Bot ${i + 1}: Error (${testRes.second})\n")
                                    }
                                }

                                report.append("\n📡 CHANNELS (${currentChannels.size}):\n")
                                val primaryBotToken = currentBots.firstOrNull { it.isActive }?.botToken ?: prefs.botToken
                                currentChannels.forEachIndexed { i, ch ->
                                    val chInfo = ServerApi().getTelegramChatInfo(primaryBotToken, ch.chatId)
                                    if (chInfo.success) {
                                        val adminText = if (chInfo.isBotAdmin) "Admin Verified" else "Not Admin"
                                        val delText = if (chInfo.canDeleteMessages) "Delete OK" else "Cannot Delete"
                                        report.append("  ✓ Channel ${i + 1} '${chInfo.title}': $adminText, $delText\n")
                                    } else {
                                        report.append("  ✗ Channel ${i + 1} (${ch.chatId}): ${chInfo.errorMessage}\n")
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    isRunningFleetDiagnostics = false
                                    showDiagnosticResults = report.toString()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isRunningFleetDiagnostics) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Fleet Health Diagnostics", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showDiagnosticResults != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Diagnostic Report", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(showDiagnosticResults!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBotDialog) {
        AlertDialog(
            onDismissRequest = { showAddBotDialog = false },
            title = { Text("Add Telegram Bot to Pool", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Paste HTTP API Bot Token from @BotFather:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newBotTokenInput,
                        onValueChange = { 
                            newBotTokenInput = it
                            verificationErrorMsg = null
                        },
                        label = { Text("Bot API Token") },
                        placeholder = { Text("123456789:ABCdefGhIJKlmNoP...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newBotNameInput,
                        onValueChange = { newBotNameInput = it },
                        label = { Text("Bot Label / Display Name (Optional)") },
                        placeholder = { Text("e.g. Media Bot 2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (verifiedBotUsername.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TelegramEmerald.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✓ Verified: $verifiedBotUsername",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (verificationErrorMsg != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "⚠️ $verificationErrorMsg",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Network issue or rate limit? You can still add the bot directly below.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cleanToken = sanitizeBotToken(newBotTokenInput)
                    if (verificationErrorMsg != null && cleanToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val newEntity = TelegramBotEntity.fromToken(
                                        token = cleanToken,
                                        botUsername = "",
                                        displayName = newBotNameInput.ifBlank { "Telegram Bot ${botsList.size + 1}" },
                                        isPrimary = botsList.isEmpty(),
                                        isActive = true
                                    )
                                    newEntity.persistToken(context, cleanToken)
                                    db.telegramBotDao().insert(newEntity)
                                    if (newEntity.isPrimary) {
                                        prefs.botToken = cleanToken
                                    }
                                    CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                    loadFleetData()
                                    withContext(Dispatchers.Main) {
                                        showAddBotDialog = false
                                        Toast.makeText(context, "✓ Bot Added to Pool!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Force Add")
                        }
                    }

                    Button(
                        onClick = {
                            val token = sanitizeBotToken(newBotTokenInput)
                            if (token.isBlank()) {
                                Toast.makeText(context, "Enter a bot token", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isVerifyingBot = true
                            verificationErrorMsg = null
                            scope.launch(Dispatchers.IO) {
                                val res = BotFleetManager.testBotToken(token)
                                withContext(Dispatchers.Main) {
                                    isVerifyingBot = false
                                    if (res.first) {
                                        verifiedBotUsername = res.second
                                        scope.launch(Dispatchers.IO) {
                                            val newEntity = TelegramBotEntity.fromToken(
                                                token = token,
                                                botUsername = res.second.substringAfter('@').substringBefore(' '),
                                                displayName = newBotNameInput.ifBlank { res.second },
                                                isPrimary = botsList.isEmpty(),
                                                isActive = true
                                            )
                                            newEntity.persistToken(context, token)
                                            db.telegramBotDao().insert(newEntity)
                                            if (newEntity.isPrimary) {
                                                prefs.botToken = token
                                            }
                                            CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                            loadFleetData()
                                            withContext(Dispatchers.Main) {
                                                showAddBotDialog = false
                                                Toast.makeText(context, "✓ Bot Added: ${res.second}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        verificationErrorMsg = res.second
                                        Toast.makeText(context, "Verification note: ${res.second}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                    ) {
                        if (isVerifyingBot) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Verify & Add")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBotDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddChannelDialog) {
        AlertDialog(
            onDismissRequest = { showAddChannelDialog = false },
            title = { Text("Add Destination Channel", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter Chat ID (e.g. -1001234567890) and category target:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newChatIdInput,
                        onValueChange = { newChatIdInput = it.trim() },
                        label = { Text("Channel Chat ID") },
                        placeholder = { Text("-1001234567890") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newChannelTitleInput,
                        onValueChange = { newChannelTitleInput = it },
                        label = { Text("Channel Title / Name") },
                        placeholder = { Text("e.g. 📷 Photos & Media") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Route which files to this channel?", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("ALL", "PHOTOS", "VIDEOS", "DOCUMENTS", "DRIVE").forEach { cat ->
                            FilterChip(
                                selected = newChannelCategory == cat,
                                onClick = { newChannelCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanChatId = newChatIdInput.trim()
                        if (cleanChatId.isBlank()) {
                            Toast.makeText(context, "Enter a chat ID", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch(Dispatchers.IO) {
                            val newChannel = TelegramChannelEntity(
                                chatId = cleanChatId,
                                title = newChannelTitleInput.ifBlank { "Storage Channel" },
                                categoryTarget = newChannelCategory,
                                isPrimary = channelsList.isEmpty(),
                                isChannelAdminVerified = true,
                                canDeleteMessages = true
                            )
                            db.telegramChannelDao().insert(newChannel)
                            if (newChannel.isPrimary) {
                                prefs.chatId = cleanChatId
                            }
                            CloudProfileSyncManager.syncLocalConfigToCloud(context)
                            loadFleetData()
                            withContext(Dispatchers.Main) {
                                showAddChannelDialog = false
                                Toast.makeText(context, "✓ Channel Added Successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald)
                ) {
                    Text("Add Channel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChannelDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportExportDialog) {
        BotFleetImportExportDialog(
            initialMode = importExportMode,
            onDismiss = { showImportExportDialog = false },
            onImportSuccess = {
                loadFleetData()
            }
        )
    }
}
