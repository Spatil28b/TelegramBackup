package com.example.telegrambackup.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.BackupProgressTracker

/**
 * Compact, non-intrusive floating or docked live sync activity pill.
 * Placed in the top action bar or free screen corner to display live transfer speeds
 * and sync status without blocking any user action.
 */
@Composable
fun SyncStatusPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isBackupActive by BackupProgressTracker.isBackupActive.collectAsState()
    val isDownloadActive by BackupProgressTracker.isDownloadActive.collectAsState()
    val uploadSpeed by BackupProgressTracker.formattedUploadSpeed.collectAsState()
    val downloadSpeed by BackupProgressTracker.formattedDownloadSpeed.collectAsState()
    val completedCount by BackupProgressTracker.completedCount.collectAsState()
    val totalCount by BackupProgressTracker.totalCount.collectAsState()

    val workingBots by BackupProgressTracker.workingBotsCount.collectAsState()
    val totalBots by BackupProgressTracker.totalBotsCount.collectAsState()

    // Subtle breathing animation for active transfers
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally()
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = when {
                isBackupActive -> TelegramBlue.copy(alpha = 0.16f)
                isDownloadActive -> Color(0xFF8B5CF6).copy(alpha = 0.16f)
                else -> TelegramEmerald.copy(alpha = 0.12f)
            },
            tonalElevation = 1.dp,
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isBackupActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(TelegramBlue)
                    )
                    Text(
                        text = "⚡ $uploadSpeed",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
                            fontSize = 11.sp
                        )
                    )
                    if (totalBots > 1 && workingBots > 0) {
                        Text(
                            text = "• 🤖 $workingBots/$totalBots",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TelegramEmerald,
                                fontSize = 10.sp
                            )
                        )
                    } else if (totalCount > 0) {
                        Text(
                            text = "($completedCount/$totalCount)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        )
                    }
                } else if (isDownloadActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFF8B5CF6))
                    )
                    Text(
                        text = "⬇️ $downloadSpeed",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6),
                            fontSize = 11.sp
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(TelegramEmerald)
                    )
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = TelegramEmerald,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
