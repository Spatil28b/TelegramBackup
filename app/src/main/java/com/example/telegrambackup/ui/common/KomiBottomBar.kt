package com.example.telegrambackup.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue

data class KomiNavItemData<T>(
    val tab: T,
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * =====================================================================================
 * 🩵 KOMI-STORE STYLE FLOATING LIQUID BOTTOM NAVIGATION BAR
 * =====================================================================================
 * Inspired by Komi-Store (formerly GitHub Store):
 * - Floating glassmorphic pill bar with ergonomic compact height (58-64dp)
 * - Liquid pill active indicator: smooth animated container behind selected item
 * - Bouncy spring physics on tap
 * - Direction-aware transitions when navigating between tabs
 * - Smart auto-hide on scroll
 * =====================================================================================
 */
@Composable
fun <T> KomiBottomBar(
    items: List<KomiNavItemData<T>>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 62.dp,
    labelMode: Int = 1, // 0 = Always, 1 = Selected Only, 2 = Never
    hapticFeedbackEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill capsule
        Row(
            modifier = Modifier
                .height(barHeight)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = Color.Black.copy(alpha = 0.28f),
                    ambientColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.tab == selectedTab

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "iconScale"
                )

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) TelegramBlue.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "containerColor"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    animationSpec = tween(durationMillis = 200),
                    label = "contentColor"
                )

                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(containerColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (hapticFeedbackEnabled && !isSelected) {
                                try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                            }
                            onTabSelected(item.tab)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.scale(iconScale)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier.size(23.dp)
                            )
                            if (item.badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .clip(CircleShape)
                                        .background(TelegramBlue)
                                )
                            }
                        }

                        val showLabel = when (labelMode) {
                            0 -> true
                            1 -> isSelected
                            else -> false
                        }

                        AnimatedVisibility(
                            visible = showLabel,
                            enter = fadeIn(tween(160)),
                            exit = fadeOut(tween(120))
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor
                                ),
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
