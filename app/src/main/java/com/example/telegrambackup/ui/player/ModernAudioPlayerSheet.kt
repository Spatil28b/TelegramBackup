package com.example.telegrambackup.ui.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import com.example.telegrambackup.utils.TelegramMediaResolver
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * =====================================================================================
 * BUILT-IN LIGHTWEIGHT & MODERN MP3 AUDIO PLAYER MODAL
 * =====================================================================================
 * Sleek, modern bottom sheet audio player with album art animation, live seekbar,
 * volume controls, and lightweight Android MediaPlayer integration.
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

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    var totalDuration by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }

    val mediaPlayer = remember { MediaPlayer() }

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

    // Format milliseconds to MM:SS
    fun formatTime(ms: Float): String {
        val totalSecs = (ms / 1000).toInt().coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    // Initialize MediaPlayer safely
    LaunchedEffect(filePathOrUrl) {
        try {
            isBuffering = true
            mediaPlayer.reset()

            var activeSource: String? = null
            if (filePathOrUrl.startsWith("http://") || filePathOrUrl.startsWith("https://")) {
                activeSource = filePathOrUrl
            } else if (File(filePathOrUrl).exists()) {
                activeSource = File(filePathOrUrl).absolutePath
            } else {
                val directUrl = TelegramMediaResolver.resolveDirectUrl(context, filePathOrUrl)
                if (directUrl != null) {
                    activeSource = directUrl
                } else {
                    val cached = TelegramMediaResolver.fetchToTempCache(context, filePathOrUrl, fileName)
                    activeSource = cached?.absolutePath
                }
            }

            if (activeSource != null) {
                if (activeSource.startsWith("http://") || activeSource.startsWith("https://")) {
                    mediaPlayer.setDataSource(context, Uri.parse(activeSource))
                } else {
                    mediaPlayer.setDataSource(activeSource)
                }

                mediaPlayer.setOnPreparedListener { mp ->
                    isBuffering = false
                    totalDuration = mp.duration.toFloat().coerceAtLeast(1f)
                    mp.isLooping = isLooping
                    mp.start()
                    isPlaying = true
                }

                mediaPlayer.setOnCompletionListener {
                    if (!isLooping) {
                        isPlaying = false
                        currentPosition = totalDuration
                    }
                }

                mediaPlayer.setOnErrorListener { _, _, _ ->
                    isBuffering = false
                    Toast.makeText(context, "Error playing audio file", Toast.LENGTH_SHORT).show()
                    true
                }

                mediaPlayer.prepareAsync()
            } else {
                isBuffering = false
                Toast.makeText(context, "Audio file not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            isBuffering = false
            Toast.makeText(context, "Failed to load audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) { }
        }
    }

    // Update playback progress slider periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (mediaPlayer.isPlaying) {
                    currentPosition = mediaPlayer.currentPosition.toFloat()
                }
            } catch (_: Exception) { }
            delay(250)
        }
    }

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
                text = fileName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isBuffering) "Buffering audio..." else "Playing MP3 Audio",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Live Seek Bar Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentPosition.coerceIn(0f, totalDuration),
                    onValueChange = { newValue ->
                        currentPosition = newValue
                        try {
                            mediaPlayer.seekTo(newValue.toInt())
                        } catch (_: Exception) { }
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(totalDuration),
                        style = MaterialTheme.typography.labelSmall,
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
                IconButton(
                    onClick = {
                        isLooping = !isLooping
                        try { mediaPlayer.isLooping = isLooping } catch (_: Exception) { }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Loop",
                        tint = if (isLooping) TelegramEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rewind 10s Button
                IconButton(
                    onClick = {
                        try {
                            val newPos = (mediaPlayer.currentPosition - 10000).coerceAtLeast(0)
                            mediaPlayer.seekTo(newPos)
                            currentPosition = newPos.toFloat()
                        } catch (_: Exception) { }
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
                    IconButton(
                        onClick = {
                            try {
                                if (mediaPlayer.isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer.start()
                                    isPlaying = true
                                }
                            } catch (_: Exception) { }
                        }
                    ) {
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
                        try {
                            val newPos = (mediaPlayer.currentPosition + 10000).coerceAtMost(totalDuration.toInt())
                            mediaPlayer.seekTo(newPos)
                            currentPosition = newPos.toFloat()
                        } catch (_: Exception) { }
                    }
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(28.dp))
                }

                // Mute / Unmute Button
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        try {
                            if (isMuted) mediaPlayer.setVolume(0f, 0f)
                            else mediaPlayer.setVolume(1f, 1f)
                        } catch (_: Exception) { }
                    }
                ) {
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
