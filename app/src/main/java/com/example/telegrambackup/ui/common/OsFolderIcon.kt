package com.example.telegrambackup.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern OS-style Folder Icon (Windows 11 / Linux Fluent / Papirus style).
 * Features:
 * - 3D layered folder geometry (rear tab, inner paper sheet, front glossy flap)
 * - Authentic warm golden/amber or azure gradients
 * - Contextual badges for Photos, Videos, Music, Downloads, Documents, Zip, etc.
 */
enum class FolderType {
    GENERIC,
    PHOTOS,
    VIDEOS,
    MUSIC,
    DOWNLOADS,
    DOCUMENTS,
    ARCHIVES,
    FAVORITES,
    CLOUD
}

@Composable
fun OsFolderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    type: FolderType = FolderType.GENERIC,
    isLinuxBlue: Boolean = false
) {
    // Curated OS Gradient Palette
    val tabColorStart = if (isLinuxBlue) Color(0xFF0078D4) else Color(0xFFF59E0B)
    val tabColorEnd = if (isLinuxBlue) Color(0xFF005A9E) else Color(0xFFD97706)

    val frontColorStart = if (isLinuxBlue) Color(0xFF38BDF8) else Color(0xFFFDE047)
    val frontColorEnd = if (isLinuxBlue) Color(0xFF0284C7) else Color(0xFFF59E0B)

    val innerPaperColor = Color(0xFFF8FAFC)
    val shadowColor = Color(0x33000000)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Rear Folder Tab & Backing Plate
            val tabWidth = w * 0.42f
            val tabHeight = h * 0.28f
            val cornerRadius = w * 0.08f

            val backPath = Path().apply {
                // Tab top-left
                moveTo(w * 0.06f, tabHeight)
                lineTo(w * 0.06f, cornerRadius)
                quadraticBezierTo(w * 0.06f, 0f, w * 0.06f + cornerRadius, 0f)
                lineTo(tabWidth - cornerRadius, 0f)
                quadraticBezierTo(tabWidth, 0f, tabWidth + cornerRadius * 0.5f, tabHeight * 0.5f)
                lineTo(tabWidth + cornerRadius * 1.2f, tabHeight)
                lineTo(w * 0.94f - cornerRadius, tabHeight)
                quadraticBezierTo(w * 0.94f, tabHeight, w * 0.94f, tabHeight + cornerRadius)
                lineTo(w * 0.94f, h * 0.88f - cornerRadius)
                quadraticBezierTo(w * 0.94f, h * 0.88f, w * 0.94f - cornerRadius, h * 0.88f)
                lineTo(w * 0.06f + cornerRadius, h * 0.88f)
                quadraticBezierTo(w * 0.06f, h * 0.88f, w * 0.06f, h * 0.88f - cornerRadius)
                close()
            }

            drawPath(
                path = backPath,
                brush = Brush.verticalGradient(
                    colors = listOf(tabColorStart, tabColorEnd),
                    startY = 0f,
                    endY = h
                )
            )

            // 2. Inner Document/Paper Sheet (gives 3D depth)
            val paperPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = w * 0.16f,
                        top = h * 0.18f,
                        right = w * 0.84f,
                        bottom = h * 0.65f,
                        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                    )
                )
            }
            drawPath(
                path = paperPath,
                brush = Brush.verticalGradient(
                    colors = listOf(innerPaperColor, Color(0xFFE2E8F0)),
                    startY = h * 0.18f,
                    endY = h * 0.65f
                )
            )

            // Subtle lines on paper
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(w * 0.24f, h * 0.28f),
                end = Offset(w * 0.76f, h * 0.28f),
                strokeWidth = w * 0.025f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(w * 0.24f, h * 0.36f),
                end = Offset(w * 0.60f, h * 0.36f),
                strokeWidth = w * 0.025f,
                cap = StrokeCap.Round
            )

            // 3. Drop Shadow under Front Flap
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(w * 0.04f, h * 0.36f),
                size = Size(w * 0.92f, h * 0.60f),
                cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
            )

            // 4. Front Folder Flap (Slight downward slope & rounded corners)
            val frontPath = Path().apply {
                moveTo(w * 0.04f, h * 0.42f)
                // curved front top line
                quadraticBezierTo(w * 0.50f, h * 0.36f, w * 0.96f, h * 0.42f)
                lineTo(w * 0.96f, h * 0.94f - cornerRadius)
                quadraticBezierTo(w * 0.96f, h * 0.94f, w * 0.96f - cornerRadius, h * 0.94f)
                lineTo(w * 0.04f + cornerRadius, h * 0.94f)
                quadraticBezierTo(w * 0.04f, h * 0.94f, w * 0.04f, h * 0.94f - cornerRadius)
                close()
            }

            drawPath(
                path = frontPath,
                brush = Brush.verticalGradient(
                    colors = listOf(frontColorStart, frontColorEnd),
                    startY = h * 0.36f,
                    endY = h * 0.94f
                )
            )

            // 5. Top Rim Highlight on Front Flap
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.06f, h * 0.42f)
                    quadraticBezierTo(w * 0.50f, h * 0.365f, w * 0.94f, h * 0.42f)
                },
                color = Color.White.copy(alpha = 0.55f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * 0.03f,
                    cap = StrokeCap.Round
                )
            )
        }

        // 6. Contextual Badge Icon (if not generic)
        val badgeIcon: ImageVector? = when (type) {
            FolderType.PHOTOS -> Icons.Default.Image
            FolderType.VIDEOS -> Icons.Default.PlayCircleFilled
            FolderType.MUSIC -> Icons.Default.MusicNote
            FolderType.DOWNLOADS -> Icons.Default.Download
            FolderType.DOCUMENTS -> Icons.Default.Description
            FolderType.ARCHIVES -> Icons.Default.FolderZip
            FolderType.FAVORITES -> Icons.Default.Star
            FolderType.CLOUD -> Icons.Default.Cloud
            FolderType.GENERIC -> null
        }

        if (badgeIcon != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = size * 0.28f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = Color(0xFF78350F).copy(alpha = 0.75f),
                    modifier = Modifier.size(size * 0.36f)
                )
            }
        }
    }
}
