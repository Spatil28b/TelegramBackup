package com.example.telegrambackup.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * High-performance animated shimmer effect modifier for loading states, thumbnails, and skeleton UI.
 */
fun Modifier.shimmerEffect(
    baseColor: Color? = null,
    highlightColor: Color? = null
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val isDark = MaterialTheme.colorScheme.surface.let {
        // Luminance check
        val lum = 0.2126 * it.red + 0.7152 * it.green + 0.0722 * it.blue
        lum < 0.5
    }

    val defaultBase = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val defaultHighlight = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)

    val actualBase = baseColor ?: defaultBase
    val actualHighlight = highlightColor ?: defaultHighlight

    val brush = Brush.linearGradient(
        colors = listOf(
            actualBase,
            actualHighlight,
            actualBase
        ),
        start = Offset(translateAnim.value - 300f, translateAnim.value - 300f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    background(brush)
}

/**
 * Ready-to-use rounded skeleton box with shimmer animation.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    baseColor: Color? = null,
    highlightColor: Color? = null
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect(baseColor, highlightColor)
    )
}
