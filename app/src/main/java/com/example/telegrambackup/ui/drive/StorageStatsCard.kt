package com.example.telegrambackup.ui.drive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.*
import java.util.Locale

private fun formatFileSizeLocal(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun StorageStatsCard(
    totalFilesCount: Int,
    totalStorageBytes: Long,
    photosBytes: Long = 0L,
    videosBytes: Long = 0L,
    audioBytes: Long = 0L,
    docsBytes: Long = 0L,
    onFreeUpSpaceClick: (() -> Unit)? = null,
    onCategoryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val totalSizeFormatted = formatFileSizeLocal(totalStorageBytes)

    val safeTotal = totalStorageBytes.coerceAtLeast(1L).toFloat()
    val photosFraction = (photosBytes / safeTotal).coerceIn(0f, 1f)
    val videosFraction = (videosBytes / safeTotal).coerceIn(0f, 1f)
    val audioFraction = (audioBytes / safeTotal).coerceIn(0f, 1f)
    val docsFraction = (docsBytes / safeTotal).coerceIn(0f, 1f)

    val isGlass = MaterialTheme.isLiquidGlass
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isGlass) {
                    Modifier.liquidGlassCard(shape = RoundedCornerShape(16.dp), elevation = 8.dp)
                } else Modifier
            )
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isGlass) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (isExpanded) 14.dp else 10.dp)
        ) {
            // Header Row (Always visible, serves as compact summary or expanded title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Cloud Storage",
                            tint = TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isExpanded) "Telegram Cloud Drive" else "Cloud Storage",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "$totalFilesCount Files • $totalSizeFormatted",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TelegramEmerald.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "∞ Unlimited",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TelegramEmerald,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

            // Multi-segment Storage Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (photosFraction > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(photosFraction)
                            .fillMaxHeight()
                            .background(TelegramCyan)
                    )
                }
                if (videosFraction > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(videosFraction)
                            .fillMaxHeight()
                            .background(AccentPurple)
                    )
                }
                if (audioFraction > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(audioFraction)
                            .fillMaxHeight()
                            .background(TelegramEmerald)
                    )
                }
                if (docsFraction > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(docsFraction)
                            .fillMaxHeight()
                            .background(AccentAmber)
                    )
                }
                if (photosFraction <= 0.01f && videosFraction <= 0.01f && audioFraction <= 0.01f && docsFraction <= 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(TelegramBlue)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Breakdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageLegendItem(color = TelegramCyan, label = "Photos", onClick = { onCategoryClick?.invoke("PHOTOS") })
                StorageLegendItem(color = AccentPurple, label = "Videos", onClick = { onCategoryClick?.invoke("VIDEOS") })
                StorageLegendItem(color = TelegramEmerald, label = "Audio", onClick = { onCategoryClick?.invoke("AUDIO") })
                StorageLegendItem(color = AccentAmber, label = "Docs", onClick = { onCategoryClick?.invoke("DOCUMENTS") })
            }

            // Optional 1-Tap Free Up Phone Space Assistant
            if (onFreeUpSpaceClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onFreeUpSpaceClick() },
                    color = TelegramEmerald.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = TelegramEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Free up phone storage",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Clean Local",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TelegramEmerald
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StorageLegendItem(
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
