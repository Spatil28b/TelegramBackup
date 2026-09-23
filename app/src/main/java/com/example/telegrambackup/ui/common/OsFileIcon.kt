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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class IconColorPalette(
    val light: Color,
    val primary: Color,
    val dark: Color,
    val fold: Color
)

/**
 * Modern Windows 11 / Fluent Style Format-Specific File Icon.
 * Features:
 * - Geometric 3D document sheet with a dog-ear folded top-right corner
 * - Authentic Fluent Gradients tailored to format (PDF red, Video rose, Audio amber, Zip purple, APK green, etc.)
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
    val rawExt = (customExtension ?: fileName.substringAfterLast('.', "")).uppercase().take(5)
    val displayExt = if (rawExt.isEmpty() || rawExt == fileName.uppercase()) "FILE" else rawExt
    val extLower = rawExt.lowercase()

    // Format-tailored Fluent Palettes
    val palette = when {
        extLower in listOf("pdf") -> IconColorPalette(
            light = Color(0xFFFCA5A5), primary = Color(0xFFEF4444), dark = Color(0xFFB91C1C), fold = Color(0xFFFEE2E2)
        )
        extLower in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic") -> IconColorPalette(
            light = Color(0xFF67E8F9), primary = Color(0xFF06B6D4), dark = Color(0xFF0E7490), fold = Color(0xFFCFFAFE)
        )
        extLower in listOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp") -> IconColorPalette(
            light = Color(0xFFFDA4AF), primary = Color(0xFFF43F5E), dark = Color(0xFFBE123C), fold = Color(0xFFFFE4E6)
        )
        extLower in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "opus") -> IconColorPalette(
            light = Color(0xFFFDE68A), primary = Color(0xFFF59E0B), dark = Color(0xFFB45309), fold = Color(0xFFFEF3C7)
        )
        extLower in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> IconColorPalette(
            light = Color(0xFFC4B5FD), primary = Color(0xFF8B5CF6), dark = Color(0xFF6D28D9), fold = Color(0xFFEDE9FE)
        )
        extLower in listOf("apk", "xapk", "apks") -> IconColorPalette(
            light = Color(0xFF6EE7B7), primary = Color(0xFF10B981), dark = Color(0xFF047857), fold = Color(0xFFD1FAE5)
        )
        extLower in listOf("xls", "xlsx", "csv") -> IconColorPalette(
            light = Color(0xFF5EEAD4), primary = Color(0xFF14B8A6), dark = Color(0xFF0F766E), fold = Color(0xFFCCFBF1)
        )
        extLower in listOf("ppt", "pptx") -> IconColorPalette(
            light = Color(0xFFFDBA74), primary = Color(0xFFF97316), dark = Color(0xFFC2410C), fold = Color(0xFFFFEDD5)
        )
        extLower in listOf("doc", "docx", "txt", "rtf", "odt") -> IconColorPalette(
            light = Color(0xFF93C5FD), primary = Color(0xFF3B82F6), dark = Color(0xFF1D4ED8), fold = Color(0xFFDBEAFE)
        )
        extLower in listOf("json", "js", "ts", "kt", "java", "py", "html", "css", "c", "cpp", "xml") -> IconColorPalette(
            light = Color(0xFFA5B4FC), primary = Color(0xFF6366F1), dark = Color(0xFF4338CA), fold = Color(0xFFE0E7FF)
        )
        else -> IconColorPalette(
            light = Color(0xFF94A3B8), primary = Color(0xFF64748B), dark = Color(0xFF334155), fold = Color(0xFFE2E8F0)
        )
    }

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

            // Draw Format-Tailored Fluent Document Body
            drawPath(
                path = docPath,
                brush = Brush.verticalGradient(
                    colors = listOf(palette.light, palette.primary, palette.dark),
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
                    colors = listOf(Color.White, palette.fold),
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
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .padding(top = size * 0.38f)
                .wrapContentSize()
        ) {
            Text(
                text = displayExt,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    color = palette.dark,
                    fontSize = (size.value * 0.18f).coerceIn(8f, 13f).sp,
                    letterSpacing = 0.4.sp
                ),
                modifier = Modifier.padding(horizontal = size * 0.08f, vertical = size * 0.02f)
            )
        }
    }
}
