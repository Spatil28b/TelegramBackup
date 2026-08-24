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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * =====================================================================================
 * MODERN OS FOLDER ICON (Ubuntu Fluent Blue / Yaru Modern Style)
 * =====================================================================================
 * - Clean transparent background (no solid box)
 * - Layered 3D geometry: Rear Backplate Tab, Inner Paper/Document Sheet, Front Pocket Flap
 * - Modern curated Ubuntu Blue gradient (#0284C7 -> #2563EB -> #1D4ED8)
 * - Subtle inner paper shadow and glossy top rim highlight
 * - Contextual badges for Photos, Videos, Music, Downloads, Documents, etc.
 * =====================================================================================
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
    isLinuxBlue: Boolean = true
) {
    // Curated Modern Ubuntu / Fluent Blue Palette
    val tabColorStart = Color(0xFF1D4ED8)   // Deep royal blue
    val tabColorEnd = Color(0xFF2563EB)     // Vibrant electric blue

    val frontColorStart = Color(0xFF38BDF8) // Soft azure highlight
    val frontColorMid = Color(0xFF0284C7)   // Ocean blue
    val frontColorEnd = Color(0xFF1E40AF)   // Rich dark blue

    val innerPaperColorStart = Color(0xFFFFFFFF)
    val innerPaperColorEnd = Color(0xFFE2E8F0)
    val shadowColor = Color(0x380F172A)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Rear Folder Tab & Backplate
            val tabWidth = w * 0.44f
            val tabHeight = h * 0.28f
            val cornerRadius = w * 0.09f

            val backPath = Path().apply {
                moveTo(w * 0.05f, tabHeight)
                lineTo(w * 0.05f, cornerRadius)
                quadraticBezierTo(w * 0.05f, 0f, w * 0.05f + cornerRadius, 0f)
                lineTo(tabWidth - cornerRadius, 0f)
                quadraticBezierTo(tabWidth, 0f, tabWidth + cornerRadius * 0.5f, tabHeight * 0.5f)
                lineTo(tabWidth + cornerRadius * 1.2f, tabHeight)
                lineTo(w * 0.95f - cornerRadius, tabHeight)
                quadraticBezierTo(w * 0.95f, tabHeight, w * 0.95f, tabHeight + cornerRadius)
                lineTo(w * 0.95f, h * 0.88f - cornerRadius)
                quadraticBezierTo(w * 0.95f, h * 0.88f, w * 0.95f - cornerRadius, h * 0.88f)
                lineTo(w * 0.05f + cornerRadius, h * 0.88f)
                quadraticBezierTo(w * 0.05f, h * 0.88f, w * 0.05f, h * 0.88f - cornerRadius)
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
                        left = w * 0.14f,
                        top = h * 0.16f,
                        right = w * 0.86f,
                        bottom = h * 0.62f,
                        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                    )
                )
            }
            drawPath(
                path = paperPath,
                brush = Brush.verticalGradient(
                    colors = listOf(innerPaperColorStart, innerPaperColorEnd),
                    startY = h * 0.16f,
                    endY = h * 0.62f
                )
            )

            // Subtle lines on inner paper sheet
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(w * 0.22f, h * 0.26f),
                end = Offset(w * 0.78f, h * 0.26f),
                strokeWidth = w * 0.025f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(w * 0.22f, h * 0.34f),
                end = Offset(w * 0.62f, h * 0.34f),
                strokeWidth = w * 0.025f,
                cap = StrokeCap.Round
            )

            // 3. Drop Shadow under Front Flap
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(w * 0.03f, h * 0.36f),
                size = Size(w * 0.94f, h * 0.60f),
                cornerRadius = CornerRadius(w * 0.09f, w * 0.09f)
            )

            // 4. Front Folder Pocket Flap (Curved Ubuntu modern flap)
            val frontPath = Path().apply {
                moveTo(w * 0.03f, h * 0.40f)
                quadraticBezierTo(w * 0.50f, h * 0.34f, w * 0.97f, h * 0.40f)
                lineTo(w * 0.97f, h * 0.94f - cornerRadius)
                quadraticBezierTo(w * 0.97f, h * 0.94f, w * 0.97f - cornerRadius, h * 0.94f)
                lineTo(w * 0.03f + cornerRadius, h * 0.94f)
                quadraticBezierTo(w * 0.03f, h * 0.94f, w * 0.03f, h * 0.94f - cornerRadius)
                close()
            }

            drawPath(
                path = frontPath,
                brush = Brush.verticalGradient(
                    colors = listOf(frontColorStart, frontColorMid, frontColorEnd),
                    startY = h * 0.34f,
                    endY = h * 0.94f
                )
            )

            // 5. Glossy Top Rim Highlight along Front Flap
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.05f, h * 0.40f)
                    quadraticBezierTo(w * 0.50f, h * 0.345f, w * 0.95f, h * 0.40f)
                },
                color = Color.White.copy(alpha = 0.60f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * 0.032f,
                    cap = StrokeCap.Round
                )
            )
        }

        // 6. Contextual Badge Icon (in pure white)
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
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(size * 0.36f)
                )
            }
        }
    }
}
