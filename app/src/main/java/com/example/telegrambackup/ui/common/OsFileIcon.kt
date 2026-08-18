package com.example.telegrambackup.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern Windows 11 / Fluent Style Blue File Icon.
 * Features:
 * - Geometric 3D document sheet with a dog-ear folded top-right corner
 * - Authentic Windows 11 Fluent Blue Gradient (#38BDF8 -> #0078D4 -> #005A9E)
 * - Inner document preview lines
 * - Bold extension badge chip (PDF, JPG, MP4, APK, ZIP, DOCX, TXT, etc.)
 */
@Composable
fun OsFileIcon(
    modifier: Modifier = Modifier,
    fileName: String = "",
    size: Dp = 48.dp,
    customExtension: String? = null
) {
    val ext = (customExtension ?: fileName.substringAfterLast('.', "")).uppercase().take(5)
    val displayExt = if (ext.isEmpty() || ext == fileName.uppercase()) "FILE" else ext

    // Windows 11 Fluent Blue Palette
    val blueLight = Color(0xFF38BDF8)
    val bluePrimary = Color(0xFF0078D4)
    val blueDark = Color(0xFF005A9E)
    val foldColor = Color(0xFFBAE6FD)
    val foldShadow = Color(0x33000000)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val cornerR = w * 0.08f
            val foldSize = w * 0.28f

            // 1. Main Document Body with Top-Right Fold Notch
            val docPath = Path().apply {
                moveTo(w * 0.12f + cornerR, h * 0.06f)
                // Top edge towards the fold
                lineTo(w * 0.88f - foldSize, h * 0.06f)
                // Diagonal fold edge
                lineTo(w * 0.88f, h * 0.06f + foldSize)
                // Right edge down
                lineTo(w * 0.88f, h * 0.94f - cornerR)
                quadraticBezierTo(w * 0.88f, h * 0.94f, w * 0.88f - cornerR, h * 0.94f)
                // Bottom edge
                lineTo(w * 0.12f + cornerR, h * 0.94f)
                quadraticBezierTo(w * 0.12f, h * 0.94f, w * 0.12f, h * 0.94f - cornerR)
                // Left edge up
                lineTo(w * 0.12f, h * 0.06f + cornerR)
                quadraticBezierTo(w * 0.12f, h * 0.06f, w * 0.12f + cornerR, h * 0.06f)
                close()
            }

            // Draw Drop Shadow
            drawPath(
                path = docPath,
                color = Color(0x22000000)
            )

            // Draw Main Blue Document Body
            drawPath(
                path = docPath,
                brush = Brush.verticalGradient(
                    colors = listOf(blueLight, bluePrimary, blueDark),
                    startY = 0f,
                    endY = h
                )
            )

            // 2. Folded Top-Right Corner Triangle Shadow & Flap
            val foldShadowPath = Path().apply {
                moveTo(w * 0.88f - foldSize, h * 0.06f)
                lineTo(w * 0.88f - foldSize, h * 0.06f + foldSize)
                lineTo(w * 0.88f, h * 0.06f + foldSize)
                close()
            }
            drawPath(path = foldShadowPath, color = foldShadow)

            val foldFlapPath = Path().apply {
                moveTo(w * 0.88f - foldSize, h * 0.06f)
                lineTo(w * 0.88f - foldSize, h * 0.06f + foldSize - (cornerR * 0.5f))
                quadraticBezierTo(
                    w * 0.88f - foldSize,
                    h * 0.06f + foldSize,
                    w * 0.88f - foldSize + (cornerR * 0.5f),
                    h * 0.06f + foldSize
                )
                lineTo(w * 0.88f, h * 0.06f + foldSize)
                close()
            }
            drawPath(
                path = foldFlapPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, foldColor),
                    start = Offset(w * 0.88f - foldSize, h * 0.06f),
                    end = Offset(w * 0.88f, h * 0.06f + foldSize)
                )
            )

            // 3. Subtle Inner Document Content Lines (Fluent aesthetic)
            val lineAlpha = 0.40f
            val lineStroke = w * 0.028f

            // Line 1
            drawLine(
                color = Color.White.copy(alpha = lineAlpha),
                start = Offset(w * 0.24f, h * 0.22f),
                end = Offset(w * 0.55f, h * 0.22f),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            // Line 2
            drawLine(
                color = Color.White.copy(alpha = lineAlpha),
                start = Offset(w * 0.24f, h * 0.32f),
                end = Offset(w * 0.76f, h * 0.32f),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            // Line 3
            drawLine(
                color = Color.White.copy(alpha = lineAlpha),
                start = Offset(w * 0.24f, h * 0.42f),
                end = Offset(w * 0.70f, h * 0.42f),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
        }

        // 4. Centered Extension Badge Chip
        Surface(
            shape = RoundedCornerShape(size * 0.08f),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(top = size * 0.38f)
                .wrapContentSize()
        ) {
            Text(
                text = displayExt,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF005A9E),
                    fontSize = (size.value * 0.18f).coerceIn(8f, 13f).sp,
                    letterSpacing = 0.4.sp
                ),
                modifier = Modifier.padding(horizontal = size * 0.08f, vertical = size * 0.02f)
            )
        }
    }
}
