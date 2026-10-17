package com.example.telegrambackup.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import kotlinx.coroutines.delay

/**
 * =====================================================================================
 * ✨ SPATIAL LIQUID APP OPENING BRAND REVEAL ANIMATION (P0 UI/UX)
 * =====================================================================================
 * Liquid Glass and Material 3 Expressive cold-launch transition:
 * - Ambient dynamic glowing plasma aura with depth blur
 * - Coordinated spring scale & illumination entrance for cloud vault shield
 * - Elegant kinetic typography reveal with gradient specular sheen
 * - Seamless zero-jerk scale-and-fade dissolve into main dashboard
 * =====================================================================================
 */
@Composable
fun AppOpeningSplashScreen(
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
        // Allow user to marvel at smooth brand animation for 900ms before dissolving
        delay(950L)
        isVisible = false
        delay(350L) // Wait for exit animation to complete
        onDismiss()
    }

    val transition = updateTransition(targetState = startAnimation, label = "SplashTransition")

    val iconScale by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 0.68f,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "IconScale"
    ) { active -> if (active) 1.0f else 0.6f }

    val contentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = LinearOutSlowInEasing) },
        label = "ContentAlpha"
    ) { active -> if (active) 1.0f else 0.0f }

    val auraScale by rememberInfiniteTransition(label = "AuraPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300, easing = FastOutLinearInEasing)) + scaleOut(
            targetScale = 1.06f,
            animationSpec = tween(300, easing = FastOutLinearInEasing)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070B14),
                            Color(0xFF0F172A),
                            Color(0xFF060911)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap to skip immediately
                    isVisible = false
                    onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            // Ambient radial back-glow
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .scale(auraScale)
                    .blur(64.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0284C7).copy(alpha = 0.35f),
                                Color(0xFF10B981).copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(iconScale)
                    .alpha(contentAlpha)
                    .padding(horizontal = 24.dp)
            ) {
                // Outer Illuminated Glass Capsule
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = Color(0xFF0284C7).copy(alpha = 0.5f),
                            ambientColor = Color(0xFF10B981).copy(alpha = 0.25f)
                        )
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.60f),
                                    Color(0xFF38BDF8).copy(alpha = 0.40f),
                                    Color.White.copy(alpha = 0.10f)
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(54.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF10B981).copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(26.dp)
                                .offset(y = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Brand Headline
                Text(
                    text = "TSpace Cloud",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 0.8.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Zero-Knowledge Telegram Vault",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0),
                                letterSpacing = 0.4.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Sleek animated indeterminate shimmer line
                val shimmerOffset by rememberInfiniteTransition(label = "Shimmer").animateFloat(
                    initialValue = -1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ShimmerOffset"
                )

                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .offset(x = (130 * shimmerOffset).dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF38BDF8),
                                        Color(0xFF34D399),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}
