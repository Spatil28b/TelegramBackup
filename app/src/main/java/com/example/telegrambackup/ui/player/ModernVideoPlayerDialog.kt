package com.example.telegrambackup.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.TelegramMediaResolver
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * =====================================================================================
 * BUILT-IN PREMIUM VIDEO PLAYER MODAL (v2.7.1 HARDENED)
 * =====================================================================================
 * Full-screen video player with live timeline scrub seekbar, gesture brightness/volume
 * controls, double-tap seek, playback speed selector (0.5x - 2.0x), and HUD indicators.
 * =====================================================================================
 */
@Composable
fun ModernVideoPlayerDialog(
    filePathOrUrl: String,
    fileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var brightnessLevel by remember { mutableFloatStateOf(1.0f) }
    var hudText by remember { mutableStateOf<String?>(null) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    fun formatTime(ms: Int): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    // Auto-hide controls after 3.5 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Auto-hide HUD after 1.2 seconds
    LaunchedEffect(hudText) {
        if (hudText != null) {
            delay(1200)
            hudText = null
        }
    }

    // Live progress tracker loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                try {
                    if (vv.isPlaying) {
                        currentPositionMs = vv.currentPosition
                        durationMs = vv.duration.coerceAtLeast(1)
                    }
                } catch (_: Throwable) {}
            }
            delay(300)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
                videoViewRef?.suspend()
                videoViewRef = null
                mediaPlayerRef = null
            } catch (_: Throwable) {}
        }
    }

    Dialog(
        onDismissRequest = {
            try {
                videoViewRef?.stopPlayback()
                videoViewRef?.suspend()
                videoViewRef = null
                mediaPlayerRef = null
            } catch (_: Throwable) {}
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            val isLeftSide = offset.x < size.width / 2
                            videoViewRef?.let { vv ->
                                if (isLeftSide) {
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                    hudText = "⏪ -10s"
                                } else {
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(durationMs)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                    hudText = "⏩ +10s"
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var isLeft = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isLeft = offset.x < size.width / 2
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (isLeft) {
                                // Adjust brightness scrim
                                brightnessLevel = (brightnessLevel - dragAmount / 350f).coerceIn(0.15f, 1.0f)
                                hudText = "☀️ Brightness ${(brightnessLevel * 100).toInt()}%"
                            } else {
                                // Adjust volume
                                audioManager?.let { am ->
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val direction = if (dragAmount < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                                    val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val pct = (currentVol * 100 / maxVol)
                                    hudText = "🔊 Volume $pct%"
                                }
                            }
                        }
                    )
                }
        ) {
            var activeVideoSource by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(filePathOrUrl) {
                val localResolved = FileViewerHelper.findLocalFile(filePathOrUrl, fileName)
                if (filePathOrUrl.startsWith("http://") || filePathOrUrl.startsWith("https://")) {
                    activeVideoSource = filePathOrUrl
                } else if (localResolved != null && localResolved.exists() && localResolved.length() > 0) {
                    activeVideoSource = localResolved.absolutePath
                } else {
                    val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                    val fileRecord = db.uploadedFileDao().getFileByPath(filePathOrUrl)
                        ?: db.uploadedFileDao().getFileByName(fileName)
                        ?: db.uploadedFileDao().getFileByTelegramFileId(filePathOrUrl)
                    val tgId = fileRecord?.telegramFileId ?: filePathOrUrl

                    val directUrl = if (tgId.isNotBlank() && tgId != "DIR_MARKER") TelegramMediaResolver.resolveDirectUrl(context, tgId) else null
                    if (directUrl != null) {
                        activeVideoSource = directUrl
                    } else {
                        val cached = if (fileRecord != null) {
                            TelegramMediaResolver.fetchToTempCache(context, fileRecord)
                        } else {
                            TelegramMediaResolver.fetchToTempCache(context, tgId, fileName)
                        }
                        activeVideoSource = cached?.absolutePath
                    }
                }
            }

            if (activeVideoSource != null) {
                key(activeVideoSource) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val source = activeVideoSource!!
                                if (source.startsWith("http://") || source.startsWith("https://")) {
                                    setVideoURI(Uri.parse(source))
                                } else {
                                    setVideoPath(source)
                                }

                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    isBuffering = false
                                    durationMs = mp.duration.coerceAtLeast(1)
                                    mp.isLooping = true
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
                                            val params = mp.playbackParams ?: PlaybackParams()
                                            params.speed = playbackSpeed
                                            mp.playbackParams = params
                                        }
                                        start()
                                        isPlaying = true
                                    } catch (_: Throwable) {
                                        isPlaying = false
                                    }
                                }

                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPositionMs = durationMs
                                }

                                setOnErrorListener { _, _, _ ->
                                    isBuffering = false
                                    try {
                                        Toast.makeText(ctx.applicationContext, "Cannot decode video. Opening in external player...", Toast.LENGTH_SHORT).show()
                                        FileViewerHelper.openFile(ctx, source, fileName)
                                    } catch (_: Throwable) {}
                                    onDismiss()
                                    true
                                }

                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TelegramBlue)
                }
            }

            // Software Brightness Scrim Overlay
            if (brightnessLevel < 0.98f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - brightnessLevel).coerceIn(0f, 0.82f)))
                )
            }

            // Buffering Spinner
            if (isBuffering) {
                CircularProgressIndicator(
                    color = TelegramBlue,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // HUD Indicator (Volume, Brightness, Seek)
            AnimatedVisibility(
                visible = hudText != null,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(250)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = hudText ?: "",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
            }

            // CONTROLS OVERLAY
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp, start = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                videoViewRef?.stopPlayback()
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )

                        // Playback Speed Toggle Pill
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .clickable {
                                    playbackSpeed = when (playbackSpeed) {
                                        0.5f -> 1.0f
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 0.5f
                                    }
                                    mediaPlayerRef?.let { mp ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            try {
                                                val p = mp.playbackParams ?: PlaybackParams()
                                                p.speed = playbackSpeed
                                                mp.playbackParams = p
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    hudText = "⚡ Speed: ${playbackSpeed}x"
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                FileViewerHelper.openFile(context, filePathOrUrl, fileName)
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "External Player", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    // CENTER PLAYBACK CONTROLS
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition - 10000).coerceAtLeast(0)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Play / Pause
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                videoViewRef?.let { vv ->
                                    val newPos = (vv.currentPosition + 10000).coerceAtMost(durationMs)
                                    vv.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // BOTTOM TIMELINE BAR
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPositionMs),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = formatTime(durationMs),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f))
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val safeDuration = durationMs.coerceAtLeast(1).toFloat()
                        Slider(
                            value = (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                val target = (frac * durationMs).toInt()
                                currentPositionMs = target
                                try {
                                    videoViewRef?.seekTo(target)
                                } catch (_: Throwable) {}
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = TelegramBlue,
                                activeTrackColor = TelegramBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
