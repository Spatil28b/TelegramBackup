package com.example.telegrambackup.ui.common

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue

data class MacDockItemData<T>(
    val tab: T,
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * =====================================================================================
 * 🍏 MODERN macOS DOCK BOTTOM NAVIGATION BAR
 * =====================================================================================
 * A floating, glassmorphic capsule dock elevated above the system navigation bar:
 * - Floating Island Container: 32dp continuous curvature, ambient drop shadow & glass border
 * - macOS Active Indicator Dot: Animated glowing indicator pill directly beneath active app icon
 * - Bouncy Spring Magnification: Tactile physics-based scaling on tab selection & taps
 * - Elevated Squircle App Tiles: Smooth rounded pill background highlighting active item
 * - Micro-Labels: Ultra-clean typography with animated visibility based on user mode
 * =====================================================================================
 */
@Composable
fun <T> MacDockBottomBar(
    items: List<MacDockItemData<T>>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    dockHeight: Dp = 68.dp,
    labelMode: Int = 1, // 0 = Always, 1 = Selected Only, 2 = Never
    hapticFeedbackEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val isGlass = com.example.telegrambackup.theme.LocalIsLiquidGlass.current

        // macOS Dock Glass Capsule Container
        Box(
            modifier = Modifier
                .shadow(
                    elevation = if (isGlass) 14.dp else 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = if (isGlass) Color(0xFF6366F1).copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.35f),
                    ambientColor = if (isGlass) Color(0xFF818CF8).copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.15f)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    if (isGlass) {
                        com.example.telegrambackup.theme.liquidGlassSurfaceBrush(16.dp)
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)
                            )
                        )
                    }
                )
                .border(
                    width = if (isGlass) 0.6.dp else 1.dp,
                    brush = if (isGlass) {
                        com.example.telegrambackup.theme.liquidGlassBorderBrush(
                            highlightAlpha = 0.45f,
                            accentAlpha = 0.18f,
                            baseAlpha = 0.08f
                        )
                    } else {
                        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                    },
                    shape = RoundedCornerShape(32.dp)
                )
                .height(dockHeight)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = selectedTab == item.tab

                    MacDockItem(
                        item = item,
                        isSelected = isSelected,
                        labelMode = labelMode,
                        isGlass = isGlass,
                        onClick = {
                            if (hapticFeedbackEnabled) {
                                triggerDockHaptic(context, haptic)
                            }
                            onTabSelected(item.tab)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> MacDockItem(
    item: MacDockItemData<T>,
    isSelected: Boolean,
    labelMode: Int,
    isGlass: Boolean = false,
    onClick: () -> Unit
) {
    // macOS Dock Bouncy Spring Physics Scale
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dockScale"
    )

    // Active Indicator Dot Width & Alpha Animation
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "indicatorWidth"
    )

    val showLabel = when (labelMode) {
        0 -> true
        1 -> isSelected
        else -> false
    }

    val tileBgColor = if (isSelected) {
        if (isGlass) Color(0xFF7DD3FC).copy(alpha = 0.14f) else TelegramBlue.copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }

    val iconTint = if (isSelected) {
        if (isGlass) Color(0xFF7DD3FC) else TelegramBlue
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    val indicatorColor = if (isGlass) Color(0xFF7DD3FC) else TelegramBlue

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(tileBgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // App Icon Tile
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        // Minimal Micro-Label (macOS title or compact text)
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.2.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Signature macOS Active Indicator Dot / Glow Pill
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .height(3.5.dp)
                .width(indicatorWidth)
                .clip(CircleShape)
                .background(
                    if (isSelected) indicatorColor else Color.Transparent
                )
        )
    }
}

private fun triggerDockHaptic(context: Context, haptic: HapticFeedback) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                )
                return
            }
        }
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(15L)
            }
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
