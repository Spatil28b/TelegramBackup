package com.example.telegrambackup.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.VersionChangelogProvider
import com.example.telegrambackup.utils.VersionRelease

@Composable
fun WhatsNewDialog(
    versionName: String,
    rawReleaseNotes: String = "",
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Latest Version, 1 = Version History
    val latestRelease = remember(versionName, rawReleaseNotes) {
        if (rawReleaseNotes.isNotBlank() && rawReleaseNotes.contains("-")) {
            val parsedEntries = VersionChangelogProvider.parseRawReleaseNotes(rawReleaseNotes, versionName)
            VersionRelease(
                versionName = versionName,
                versionCode = 0,
                releaseDate = "Current Release",
                highlights = "Latest Updates & Optimizations",
                entries = parsedEntries
            )
        } else {
            VersionChangelogProvider.getReleaseForVersion(versionName)
        }
    }
    val allReleases = remember { VersionChangelogProvider.getAllReleases() }

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
                                .background(TelegramEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("What's New in v$versionName", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(latestRelease.highlights, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs (Latest vs All Releases)
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
                            .background(if (selectedTab == 0) TelegramBlue else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✨ Latest (v$versionName)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 1) TelegramBlue else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📜 Full History",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                if (selectedTab == 0) {
                    // Latest Features List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(latestRelease.entries) { item ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = item.iconTint.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    item.tag,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = item.iconTint),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Version History
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allReleases) { release ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Version v${release.versionName}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                                        Text(release.releaseDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(release.highlights, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    release.entries.forEach { entry ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text("• ", style = MaterialTheme.typography.bodySmall.copy(color = TelegramEmerald, fontWeight = FontWeight.Bold))
                                            Text("${entry.title}: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                            Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Got It, Explore Now!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

