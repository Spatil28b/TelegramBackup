package com.example.telegrambackup.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

/**
 * =====================================================================================
 * 🩵 KOMI-STORE STYLE CHANGELOG & VERSION HISTORY DIALOG
 * =====================================================================================
 * Inspired by Komi-Store release view:
 * - Horizontal version selector chip bar (Latest, v2.2.1, v2.2.00, etc.)
 * - Card-based release notes with category badge tags (DESIGN, PERFORMANCE, CLEAN UI)
 * - Concise, modern layout with clean typography
 * =====================================================================================
 */
@Composable
fun WhatsNewDialog(
    versionName: String,
    rawReleaseNotes: String = "",
    onDismiss: () -> Unit
) {
    val allReleases = remember {
        try {
            VersionChangelogProvider.getAllReleases().ifEmpty {
                listOf(
                    VersionRelease(
                        versionName = versionName,
                        versionCode = 135,
                        releaseDate = "Current Release",
                        highlights = "Stability & Performance Update",
                        entries = emptyList()
                    )
                )
            }
        } catch (_: Throwable) {
            listOf(
                VersionRelease(
                    versionName = versionName,
                    versionCode = 135,
                    releaseDate = "Current Release",
                    highlights = "Stability & Performance Update",
                    entries = emptyList()
                )
            )
        }
    }
    var selectedReleaseIndex by remember { mutableIntStateOf(0) }

    val activeRelease = remember(selectedReleaseIndex, allReleases, rawReleaseNotes) {
        try {
            if (selectedReleaseIndex == 0 && rawReleaseNotes.isNotBlank() && rawReleaseNotes.contains("-")) {
                val parsedEntries = VersionChangelogProvider.parseRawReleaseNotes(rawReleaseNotes, versionName)
                VersionRelease(
                    versionName = versionName,
                    versionCode = 135,
                    releaseDate = "Current Release",
                    highlights = "Latest Updates & Optimizations",
                    entries = parsedEntries
                )
            } else {
                allReleases.getOrElse(selectedReleaseIndex) {
                    allReleases.firstOrNull() ?: VersionRelease(
                        versionName = versionName,
                        versionCode = 135,
                        releaseDate = "Current Release",
                        highlights = "Stability & Performance Update",
                        entries = emptyList()
                    )
                }
            }
        } catch (_: Throwable) {
            allReleases.firstOrNull() ?: VersionRelease(
                versionName = versionName,
                versionCode = 135,
                releaseDate = "Current Release",
                highlights = "Stability & Performance Update",
                entries = emptyList()
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
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
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(TelegramEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Release Changelog",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "v${activeRelease.versionName} • ${activeRelease.releaseDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Komi-Store Style Horizontal Version Selector Chip Bar
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(allReleases) { index, release ->
                        val isSelected = selectedReleaseIndex == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) TelegramBlue else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { selectedReleaseIndex = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (index == 0) {
                                    Text("✨ ", fontSize = 11.sp)
                                }
                                Text(
                                    text = "v${release.versionName}${if (index == 0) " (Latest)" else ""}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Release Highlights Summary Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TelegramBlue.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeRelease.highlights,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = TelegramBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Release Entries Card List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeRelease.entries) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(item.iconTint.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(18.dp))
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = item.iconTint.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            item.tag,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = item.iconTint,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Dismiss Action Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Got It, Continue", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
