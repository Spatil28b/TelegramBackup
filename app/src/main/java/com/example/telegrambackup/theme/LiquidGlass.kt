package com.example.telegrambackup.theme

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * =====================================================================================
 * 💎 LIQUID GLASS DESIGN SYSTEM v2.2 — iOS 26 / VisionOS Spatial Aesthetics for Android
 * =====================================================================================
 * Refined for authentic dark obsidian frosted glass:
 * - Cohesive Ethereal Ambient Canvas: 3 wide-feathered celestial light fields (Sapphire, Indigo, Azure)
 *   with organic multi-stop radial gradient decay ensuring zero hard disc edges across all Android versions
 * - Frosted Dark Obsidian Slate Surface: Slate-800 to Slate-900 frosted glass (56-72% opacity)
 *   replaces murky saturated navy blue plastic; eliminates orb wash-out with 100% crisp text contrast
 * - Precision Specular Crystalline Bevel: Hairline 0.75dp border with calibrated 0.28f specular catch
 *   replaces harsh 0.55f white chalk outlines
 * - Calibrated Ambient Occlusion: Natural soft shadow map eliminates GPU overdraw and darkens edges
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
 * v2.2: GPU-accelerated celestial light fields using graphicsLayer transforms with hardware blur on API 31+.
 * Multi-stop feathered radial brushes ensure smooth atmospheric diffusion with zero hard edges even on API < 31.
 */
@Composable
fun LiquidGlassAmbientCanvas(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isGlass = LocalIsLiquidGlass.current
    if (isGlass) {
        val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlassFluidTransition")

        // Smooth orbital phase 1 (Primary Sapphire/Sky flow)
        val phase1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(38000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluidPhase1"
        )

        // Smooth orbital phase 2 (Counter-flow Twilight Indigo)
        val phase2 by infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(44000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluidPhase2"
        )

        // Gentle breathing pulse
        val breathingScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "fluidBreathing"
        )

        // Feathered multi-stop gradient fields — seamless organic decay into obsidian background
        val sapphireField = remember {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF0284C7).copy(alpha = 0.22f),
                    0.35f to Color(0xFF0EA5E9).copy(alpha = 0.12f),
                    0.70f to Color(0xFF0369A1).copy(alpha = 0.04f),
                    1.0f to Color.Transparent
                )
            )
        }

        val indigoField = remember {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF4F46E5).copy(alpha = 0.18f),
                    0.35f to Color(0xFF6366F1).copy(alpha = 0.10f),
                    0.70f to Color(0xFF312E81).copy(alpha = 0.03f),
                    1.0f to Color.Transparent
                )
            )
        }

        val azureOceanField = remember {
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF0284C7).copy(alpha = 0.16f),
                    0.35f to Color(0xFF0C4A6E).copy(alpha = 0.08f),
                    0.70f to Color(0xFF082F49).copy(alpha = 0.03f),
                    1.0f to Color.Transparent
                )
            )
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(LiquidGlassBackground)
        ) {
            // Fluid Caustic Layer with Hardware Blur on Android 12+
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(radius = 64.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        } else Modifier
                    )
            ) {
                // 1. Top-Right: Ethereal Sapphire Aura
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 80.dp, y = (-60).dp)
                        .size(520.dp)
                        .graphicsLayer {
                            val rad = Math.toRadians(phase1.toDouble())
                            translationX = (cos(rad) * 25).toFloat()
                            translationY = (sin(rad) * 20).toFloat()
                            scaleX = breathingScale
                            scaleY = breathingScale
                        }
                        .background(sapphireField, shape = CircleShape)
                )

                // 2. Center-Left: Twilight Indigo Glow
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-90).dp, y = (-30).dp)
                        .size(560.dp)
                        .graphicsLayer {
                            val rad = Math.toRadians(phase2.toDouble())
                            translationX = (sin(rad) * 20).toFloat()
                            translationY = (cos(rad) * 25).toFloat()
                            scaleX = 2f - breathingScale
                            scaleY = 2f - breathingScale
                        }
                        .background(indigoField, shape = CircleShape)
                )

                // 3. Bottom-Center: Deep Azure Oceanic Foundation
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 120.dp)
                        .size(600.dp)
                        .graphicsLayer {
                            val rad = Math.toRadians((phase1 * 0.7).toDouble())
                            translationX = (cos(rad) * 20).toFloat()
                            translationY = (sin(rad) * 15).toFloat()
                        }
                        .background(azureOceanField, shape = CircleShape)
                )
            }

            // Foreground Content (Crisp, unblurred text and touch controls)
            content()
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * Specular border gradient for crystalline glass edges.
 * v2.2: Calibrated specular light catch (0.28f default) that catches directional light
 * without overpowering content with harsh white chalk lines.
 */
@Composable
fun liquidGlassBorderBrush(
    highlightAlpha: Float = 0.28f,
    accentAlpha: Float = 0.12f,
    baseAlpha: Float = 0.04f
): Brush {
    return remember(highlightAlpha, accentAlpha, baseAlpha) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = highlightAlpha),                                      // Top-left specular crystalline rim
                Color(0xFFE2E8F0).copy(alpha = (accentAlpha * 1.25f).coerceAtMost(0.20f)),  // Silver-crystal bevel
                Color(0xFF64748B).copy(alpha = accentAlpha),                                  // Cool slate mid
                Color(0xFF334155).copy(alpha = baseAlpha)                                     // Soft bottom-right shadow bevel
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }
}

/**
 * Multi-stop frosted dark obsidian slate surface brush.
 * v2.2: Neutral dark frosted glass (56-72% opacity).
 * Replaces saturated murky navy blue with authentic Apple VisionOS obsidian glass:
 * allows ethereal lighting to softly radiate through while guaranteeing 100% crisp text readability.
 */
@Composable
fun liquidGlassSurfaceBrush(
    elevation: Dp = 8.dp
): Brush {
    val extraAlpha = (elevation.value * 0.003f).coerceIn(0f, 0.06f)
    return remember(elevation) {
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = (0.08f + extraAlpha * 0.3f).coerceAtMost(0.12f)),  // Subtle top rim specular catch
            0.04f to Color(0xFF1E293B).copy(alpha = 0.56f + extraAlpha),                        // Frosted dark slate-800
            0.45f to Color(0xFF0F172A).copy(alpha = 0.64f + extraAlpha),                        // Slate-900 mid body (crystal-clear contrast)
            1.0f to Color(0xFF080D18).copy(alpha = 0.72f + extraAlpha)                          // Obsidian glass base
        )
    }
}

/**
 * High-performance modifier: authentic liquid glassmorphism for any container.
 * v2.2: Natural subtle drop shadow, precision specular crystalline bevel,
 * and neutral frosted obsidian glass surface.
 */
@Composable
fun Modifier.liquidGlassCard(
    shape: Shape = RoundedCornerShape(22.dp),
    borderWidth: Dp = 0.75.dp,
    elevation: Dp = 6.dp,
    highlightAlpha: Float = 0.28f
): Modifier {
    val isGlass = LocalIsLiquidGlass.current
    return if (isGlass) {
        this
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = (elevation * 0.4f).coerceAtMost(5.dp),
                        shape = shape,
                        spotColor = Color(0xFF0284C7).copy(alpha = 0.06f),
                        ambientColor = Color(0xFF020408).copy(alpha = 0.35f)
                    )
                } else Modifier
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
    elevation: Dp = 8.dp,
    borderWidth: Dp = 0.75.dp,
    highlightAlpha: Float = 0.32f
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
    borderWidth: Dp = 0.75.dp,
    elevation: Dp = 6.dp,
    highlightAlpha: Float = 0.28f,
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
