package com.example.telegrambackup.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue

@Composable
fun TimelineHeader(
    title: String,
    itemCount: Int,
    totalSizeBytes: Long = 0L,
    isSelectionMode: Boolean = false,
    isAllSelected: Boolean = false,
    onToggleSelectDay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sizeText = if (totalSizeBytes > 0) {
        val mb = totalSizeBytes / (1024.0 * 1024.0)
        if (mb >= 1024) {
            String.format("%.1f GB", mb / 1024.0)
        } else {
            String.format("%.0f MB", mb)
        }
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = if (sizeText != null) "$itemCount ($sizeText)" else "$itemCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        if (isSelectionMode && onToggleSelectDay != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleSelectDay() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isAllSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = "Select all in section",
                    tint = if (isAllSelected) TelegramBlue else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isAllSelected) "Deselect" else "Select day",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isAllSelected) TelegramBlue else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
