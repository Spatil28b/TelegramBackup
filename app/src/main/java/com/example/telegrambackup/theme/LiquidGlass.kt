package com.example.telegrambackup.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * =====================================================================================
 * 💎 LIQUID GLASS DESIGN SYSTEM (iOS 26 / VisionOS Spatial Aesthetics for Android)
 * =====================================================================================
 * High-performance, GPU-accelerated liquid glass material system:
 * - Dynamic CompositionLocal tracking system-wide liquid glass activation
 * - Ambient spatial illumination canvas with deep cosmic radiant light orbs
 * - True translucent frosted glass surfaces (17% - 35% alpha) revealing underlying light
 * - Specular highlight gradient borders simulating refraction of directional light
 * - Zero software blur overhead & zero GC allocation for 120 FPS fluid scrolling
 * =====================================================================================
 */

/**
 * CompositionLocal providing whether the active theme is Liquid Glass.
 */
val LocalIsLiquidGlass = staticCompositionLocalOf { false }

/**
 * Extension property on MaterialTheme for clean, readable checks:
 * `if (MaterialTheme.isLiquidGlass) { ... }`
 */
val MaterialTheme.isLiquidGlass: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalIsLiquidGlass.current

/**
 * Ambient spatial lighting canvas that sits behind all views in Dark Liquid Glass mode.
 * Illuminates the background with soft, deep radial gradients (electric cyan, radiant violet,
 * and oceanic azure) so that translucent glass cards, sheets, and docks visibly refract light.
 */
@Composable
fun LiquidGlassAmbientCanvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isGlass = LocalIsLiquidGlass.current
    if (isGlass) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(LiquidGlassBackground)
                .drawBehind {
                    // Deep cosmic spatial light sources
                    // 1. Top-right electric cyan luminescence
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.16f),
                                Color(0xFF0088CC).copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.10f),
                            radius = size.width * 0.85f
                        )
                    )
                    // 2. Middle-left radiant prismatic violet
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFA855F7).copy(alpha = 0.12f),
                                Color(0xFF6366F1).copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.08f, size.height * 0.42f),
                            radius = size.width * 0.90f
                        )
                    )
                    // 3. Bottom-center deep oceanic azure pool
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0284C7).copy(alpha = 0.14f),
                                Color(0xFF0369A1).copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.55f, size.height * 0.90f),
                            radius = size.width * 0.80f
                        )
                    )
                }
        ) {
            content()
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * Returns a high-refraction specular border gradient brush for glass elements.
 * Illuminates the top-left edge with a crisp diamond-white highlight fading into luminous crystal cyan.
 */
@Composable
fun liquidGlassBorderBrush(
    highlightAlpha: Float = 0.60f,
    accentAlpha: Float = 0.35f,
    baseAlpha: Float = 0.15f
): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return Brush.linearGradient(
        colors = listOf(
            LiquidGlassSpecularHighlight.copy(alpha = highlightAlpha),
            primary.copy(alpha = accentAlpha),
            Color(0xFF1E355B).copy(alpha = baseAlpha)
        )
    )
}

/**
 * Returns an authentic multi-stop frosted glass refraction brush for card surfaces.
 * Uses true translucency (top specular sheen 35% fading to 20% deep obsidian glass).
 */
@Composable
fun liquidGlassSurfaceBrush(
    elevation: Dp = 2.dp
): Brush {
    val topAlpha = (0.35f + (elevation.value * 0.008f)).coerceIn(0.24f, 0.48f)
    val bottomAlpha = (0.18f + (elevation.value * 0.006f)).coerceIn(0.14f, 0.32f)
    val topTint = Color(0xFF1E325A).copy(alpha = topAlpha)
    val bottomTint = Color(0xFF0C172E).copy(alpha = bottomAlpha)
    return Brush.verticalGradient(
        colors = listOf(topTint, bottomTint)
    )
}

/**
 * High-performance modifier that applies authentic liquid glassmorphism to any container:
 * - Ambient drop shadow with subtle glowing tint
 * - Continuous smooth corner clipping
 * - Luminous vertical translucent refraction gradient
 * - Specular beveled highlight border
 */
@Composable
fun Modifier.liquidGlassCard(
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    elevation: Dp = 8.dp,
    highlightAlpha: Float = 0.55f
): Modifier {
    val isGlass = LocalIsLiquidGlass.current
    return if (isGlass) {
        this
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = Color(0xFF00E5FF).copy(alpha = 0.22f),
                ambientColor = Color(0xFFA78BFA).copy(alpha = 0.14f)
            )
            .clip(shape)
            .background(liquidGlassSurfaceBrush(elevation))
            .border(
                width = borderWidth,
                brush = liquidGlassBorderBrush(highlightAlpha = highlightAlpha),
                shape = shape
            )
    } else {
        this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = borderWidth,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = shape
            )
    }
}

/**
 * Capsule / Pill variant of the liquid glass modifier for floating navigation bars,
 * search bars, and interactive floating action buttons.
 */
@Composable
fun Modifier.liquidGlassPill(
    elevation: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    highlightAlpha: Float = 0.60f
): Modifier = liquidGlassCard(
    shape = CircleShape,
    borderWidth = borderWidth,
    elevation = elevation,
    highlightAlpha = highlightAlpha
)
