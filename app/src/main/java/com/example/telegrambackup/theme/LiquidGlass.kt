package com.example.telegrambackup.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * =====================================================================================
 * 💎 LIQUID GLASS DESIGN SYSTEM v2 — iOS 26 / VisionOS Spatial Aesthetics for Android
 * =====================================================================================
 * Revised for authentic frosted glass:
 * - Richer, more visible ambient light orbs (deeper colors, higher alpha)
 * - Warmer specular highlights with neutral silver (not cyan) borders
 * - Higher surface container alphas so glass layers are actually visible
 * - Softer shadow glow that feels like glass refraction, not neon
 * - Zero software blur overhead & zero GC allocation for 120 FPS scrolling
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
 * Ambient spatial lighting canvas — sits behind all views in Liquid Glass mode.
 * v2: Richer orbs with higher alpha, more color variety (warm rose accent added),
 * slower drift for more serene feel. Glass cards refract these, not compete.
 */
@Composable
fun LiquidGlassAmbientCanvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isGlass = LocalIsLiquidGlass.current
    if (isGlass) {
        val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlassAmbientTransition")

        // Slow fluid oscillations — underwater caustics, not disco
        val cyanPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "cyanPhase"
        )

        val violetPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(26000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "violetPhase"
        )

        val azurePhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(30000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "azurePhase"
        )

        val rosePhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(24000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rosePhase"
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(LiquidGlassBackground)
                .drawBehind {
                    val w = size.width
                    val h = size.height

                    // 1. Top-Right: Rich cyan luminescence — primary glass illumination
                    val cyanCX = w * (0.80f + 0.08f * cos(cyanPhase.toDouble()).toFloat())
                    val cyanCY = h * (0.08f + 0.05f * sin(cyanPhase.toDouble()).toFloat())
                    val cyanR = w * (0.78f + 0.06f * sin((cyanPhase * 2).toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7DD3FC).copy(alpha = 0.28f),
                                Color(0xFF0EA5E9).copy(alpha = 0.16f),
                                Color(0xFF0C4A6E).copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(cyanCX, cyanCY),
                            radius = cyanR
                        )
                    )

                    // 2. Left-Center: Deep indigo-violet — prismatic depth
                    val violetCX = w * (0.10f + 0.07f * sin(violetPhase.toDouble()).toFloat())
                    val violetCY = h * (0.38f + 0.06f * cos(violetPhase.toDouble()).toFloat())
                    val violetR = w * (0.82f + 0.07f * cos((violetPhase * 2).toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6).copy(alpha = 0.22f),
                                Color(0xFF6366F1).copy(alpha = 0.12f),
                                Color(0xFF312E81).copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(violetCX, violetCY),
                            radius = violetR
                        )
                    )

                    // 3. Center-Right: Warm rose accent — chromatic variety
                    val roseCX = w * (0.85f + 0.05f * cos(rosePhase.toDouble()).toFloat())
                    val roseCY = h * (0.55f + 0.04f * sin(rosePhase.toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF9A8D4).copy(alpha = 0.10f),
                                Color(0xFFC084FC).copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(roseCX, roseCY),
                            radius = w * 0.50f
                        )
                    )

                    // 4. Bottom-Center: Deep ocean azure — dock area illumination
                    val azureCX = w * (0.50f + 0.06f * cos(azurePhase.toDouble()).toFloat())
                    val azureCY = h * (0.88f + 0.03f * sin(azurePhase.toDouble()).toFloat())
                    val azureR = w * (0.75f + 0.07f * sin((azurePhase * 2).toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0EA5E9).copy(alpha = 0.24f),
                                Color(0xFF0284C7).copy(alpha = 0.12f),
                                Color(0xFF0C4A6E).copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(azureCX, azureCY),
                            radius = azureR
                        )
                    )

                    // 5. Top-Left: Subtle teal accent — prevents flat top-left corner
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2DD4BF).copy(alpha = 0.08f),
                                Color(0xFF14B8A6).copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.15f, h * 0.12f),
                            radius = w * 0.45f
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
 * Specular border gradient for glass edges.
 * v2: More neutral silver-to-white gradient. Top-left catches a bright white
 * highlight that transitions through a subtle lavender tint into cool silver.
 * No more "neon outline" look — feels like real beveled glass.
 */
@Composable
fun liquidGlassBorderBrush(
    highlightAlpha: Float = 0.45f,
    accentAlpha: Float = 0.18f,
    baseAlpha: Float = 0.08f
): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = highlightAlpha),                // Pure white specular catch
            Color(0xFFD4D4F7).copy(alpha = accentAlpha * 1.2f),     // Very subtle lavender mid
            Color(0xFFCBD5E1).copy(alpha = accentAlpha),             // Cool slate silver
            Color(0xFFE2E8F0).copy(alpha = baseAlpha)                // Faint silver tail
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
}

/**
 * Multi-stop frosted glass surface brush.
 * v2: Higher overall alpha so glass layers are actually visible against background.
 * Top sheen is brighter, mid-section retains readable depth, bottom uses rich
 * navy-indigo that lets ambient orbs glow through without washing out text.
 */
@Composable
fun liquidGlassSurfaceBrush(
    elevation: Dp = 8.dp
): Brush {
    val extraAlpha = (elevation.value * 0.005f).coerceIn(0f, 0.08f)
    return Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.14f + extraAlpha),       // Brighter ceiling sheen
        0.08f to Color.White.copy(alpha = 0.08f + extraAlpha * 0.5f),
        0.35f to Color(0xFF1E2D55).copy(alpha = 0.45f + extraAlpha), // Mid frosted navy
        0.70f to Color(0xFF152040).copy(alpha = 0.50f + extraAlpha), // Deep translucent
        1.0f to Color(0xFF0F1830).copy(alpha = 0.48f + extraAlpha)   // Bottom — still translucent, not opaque
    )
}

/**
 * High-performance modifier: authentic liquid glassmorphism for any container.
 * v2: Softer shadow (warm indigo glow instead of harsh cyan), thinner border,
 * lower default highlight so glass feels premium and not like a neon sign.
 */
@Composable
fun Modifier.liquidGlassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    borderWidth: Dp = 0.6.dp,
    elevation: Dp = 8.dp,
    highlightAlpha: Float = 0.45f
): Modifier {
    val isGlass = LocalIsLiquidGlass.current
    return if (isGlass) {
        this
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = Color(0xFF6366F1).copy(alpha = 0.14f),   // Warm indigo spot (was harsh cyan)
                ambientColor = Color(0xFF818CF8).copy(alpha = 0.08f) // Soft violet ambient
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = borderWidth,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = shape
            )
    }
}

/**
 * Capsule / Pill variant for floating nav bars, search bars, FABs.
 */
@Composable
fun Modifier.liquidGlassPill(
    elevation: Dp = 10.dp,
    borderWidth: Dp = 0.6.dp,
    highlightAlpha: Float = 0.50f
): Modifier = liquidGlassCard(
    shape = CircleShape,
    borderWidth = borderWidth,
    elevation = elevation,
    highlightAlpha = highlightAlpha
)

/**
 * Composable container that automatically wraps its content with liquid glass styling.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderWidth: Dp = 0.6.dp,
    elevation: Dp = 8.dp,
    highlightAlpha: Float = 0.45f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.liquidGlassCard(
            shape = shape,
            borderWidth = borderWidth,
            elevation = elevation,
            highlightAlpha = highlightAlpha
        )
    ) {
        content()
    }
}
