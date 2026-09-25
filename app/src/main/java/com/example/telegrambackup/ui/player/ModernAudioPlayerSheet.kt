package com.example.telegrambackup.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.theme.labelMonoSmall
import java.util.Locale

/**
 * =====================================================================================
 * BUILT-IN LIGHTWEIGHT & MODERN AUDIO PLAYER SHEET
 * =====================================================================================
 * Powered by AudioPlayerManager: supports background playback across app tabs,
 * floating mini-player pill capsule, and instant speed controls (0.5x - 2.0x).
 * =====================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAudioPlayerSheet(
    filePathOrUrl: String,
    fileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(filePathOrUrl, fileName) {
        if (filePathOrUrl.isNotBlank() && fileName.isNotBlank()) {
            AudioPlayerManager.playTrack(context, filePathOrUrl, fileName)
        }
    }

    val isPlaying = AudioPlayerManager.isPlaying
    val currentPosition = AudioPlayerManager.currentPositionMs
    val totalDuration = AudioPlayerManager.totalDurationMs
    val isBuffering = AudioPlayerManager.isBuffering
    val isLooping = AudioPlayerManager.isLooping
    val isMuted = AudioPlayerManager.isMuted
    val currentSpeed = AudioPlayerManager.playbackSpeed

    // Disc rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    fun formatTime(ms: Float): String {
        val totalSecs = (ms / 1000).toInt().coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    val speedOptions = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TelegramEmerald.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "🎵 Built-in Audio Player",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Modern Vinyl / Music Artwork Disc
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                TelegramBlue.copy(alpha = 0.8f),
                                Color(0xFF1E1B4B),
                                Color(0xFF0F172A)
                            )
                        )
                    )
                    .rotate(if (isPlaying) spinRotation else 0f),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Grooves
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                )

                // Album Art Center Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(TelegramEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Track Title & File Info
            Text(
                text = AudioPlayerManager.activeTrackName ?: fileName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isBuffering) "Buffering audio..." else "Playing with background & speed controls",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Speed Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                speedOptions.forEach { spd ->
                    val isSelected = currentSpeed == spd
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) TelegramEmerald else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (isSelected) TelegramEmerald else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { AudioPlayerManager.setSpeed(spd) }
                    ) {
                        Text(
                            text = "${spd}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Seek Bar Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentPosition.coerceIn(0f, totalDuration),
                    onValueChange = { newValue ->
                        AudioPlayerManager.seekTo(newValue)
                    },
                    valueRange = 0f..totalDuration,
                    colors = SliderDefaults.colors(
                        thumbColor = TelegramEmerald,
                        activeTrackColor = TelegramEmerald,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.labelMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(totalDuration),
                        style = MaterialTheme.typography.labelMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop Mode Button
                IconButton(onClick = { AudioPlayerManager.toggleLoop() }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Loop",
                        tint = if (isLooping) TelegramEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rewind 10s Button
                IconButton(
                    onClick = {
                        val newPos = (currentPosition - 10000).coerceAtLeast(0f)
                        AudioPlayerManager.seekTo(newPos)
                    }
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(28.dp))
                }

                // Main Play / Pause Floating Button
                Surface(
                    shape = CircleShape,
                    color = TelegramBlue,
                    modifier = Modifier.size(56.dp)
                ) {
                    IconButton(onClick = { AudioPlayerManager.togglePlayPause() }) {
                        if (isBuffering) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Fast Forward 10s Button
                IconButton(
                    onClick = {
                        val newPos = (currentPosition + 10000).coerceAtMost(totalDuration)
                        AudioPlayerManager.seekTo(newPos)
                    }
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(28.dp))
                }

                // Mute / Unmute Button
                IconButton(onClick = { AudioPlayerManager.toggleMute() }) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
