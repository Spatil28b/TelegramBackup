package com.example.telegrambackup.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.DarkSurface
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald

@Composable
fun FloatingBulkActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAllToggle: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onFreeUpSpace: () -> Unit,
    onTrash: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
        ) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), spotColor = TelegramBlue.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Count pill with close icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(TelegramBlue.copy(alpha = 0.15f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Selection",
                        tint = TelegramBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$selectedCount",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = TelegramBlue
                    )
                }

                // Select All toggle
                IconButton(
                    onClick = onSelectAllToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (selectedCount == totalCount) Icons.Default.CheckCircle else Icons.Default.SelectAll,
                        contentDescription = "Select All",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Actions
                BulkActionIconButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    tint = TelegramBlue,
                    onClick = onShare
                )

                BulkActionIconButton(
                    icon = Icons.Default.Download,
                    label = "Download",
                    tint = TelegramEmerald,
                    onClick = onDownload
                )

                BulkActionIconButton(
                    icon = Icons.Default.CleaningServices,
                    label = "Free Space",
                    tint = Color(0xFF6366F1),
                    onClick = onFreeUpSpace
                )

                BulkActionIconButton(
                    icon = Icons.Default.DeleteOutline,
                    label = "Trash",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onTrash
                )
            }
        }
    }
}

@Composable
private fun BulkActionIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
