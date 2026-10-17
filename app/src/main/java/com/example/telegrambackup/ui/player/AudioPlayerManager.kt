package com.example.telegrambackup.ui.player

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.*
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.utils.AppNotificationHelper
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.TelegramMediaResolver
import kotlinx.coroutines.*

/**
 * Global audio playback manager supporting background playback,
 * lockscreen/status-bar notification controls, speed adjustment,
 * and floating mini-player pill integration.
 */
object AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null
    private var playJob: Job? = null
    private var applicationContext: Context? = null

    var activeTrackPath by mutableStateOf<String?>(null)
        private set
    var activeTrackName by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var currentPositionMs by mutableFloatStateOf(0f)
        private set
    var totalDurationMs by mutableFloatStateOf(1f)
        private set
    var playbackSpeed by mutableFloatStateOf(1.0f)
        private set
    var isLooping by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var isExpandedSheetOpen by mutableStateOf(false)

    val hasActiveTrack: Boolean
        get() = !activeTrackPath.isNullOrBlank()

    fun playTrack(context: Context, filePathOrUrl: String, fileName: String) {
        applicationContext = context.applicationContext
        if (activeTrackPath == filePathOrUrl && mediaPlayer != null) {
            if (!isPlaying) {
                resume(context)
            }
            isExpandedSheetOpen = true
            return
        }

        playJob?.cancel()
        stop(context)
        activeTrackPath = filePathOrUrl
        activeTrackName = fileName
        isBuffering = true
        currentPositionMs = 0f
        totalDurationMs = 1f

        playJob = scope.launch(Dispatchers.IO) {
            try {
                var activeSource: String? = null
                val localResolved = FileViewerHelper.findLocalFile(filePathOrUrl, fileName)
                if (filePathOrUrl.startsWith("http://") || filePathOrUrl.startsWith("https://")) {
                    activeSource = filePathOrUrl
                } else if (localResolved != null && localResolved.exists() && localResolved.length() > 0) {
                    activeSource = localResolved.absolutePath
                } else {
                    val db = AppDatabase.getDatabase(context)
                    val fileRecord = db.uploadedFileDao().getFileByPath(filePathOrUrl)
                        ?: db.uploadedFileDao().getFileByName(fileName)
                        ?: db.uploadedFileDao().getFileByTelegramFileId(filePathOrUrl)
                    val tgId = fileRecord?.telegramFileId ?: filePathOrUrl

                    val directUrl = if (tgId.isNotBlank() && tgId != "DIR_MARKER") TelegramMediaResolver.resolveDirectUrl(context, tgId) else null
                    if (directUrl != null) {
                        activeSource = directUrl
                    } else {
                        val cached = if (fileRecord != null) {
                            TelegramMediaResolver.fetchToTempCache(context, fileRecord)
                        } else {
                            TelegramMediaResolver.fetchToTempCache(context, tgId, fileName)
                        }
                        activeSource = cached?.absolutePath
                    }
                }

                if (!isActive) return@launch

                withContext(Dispatchers.Main) {
                    if (!isActive) return@withContext
                    if (activeSource != null) {
                        try {
                            mediaPlayer?.release()
                        } catch (_: Throwable) {}

                        val player = MediaPlayer()
                        mediaPlayer = player

                        if (activeSource.startsWith("http://") || activeSource.startsWith("https://")) {
                            player.setDataSource(context.applicationContext, Uri.parse(activeSource))
                        } else {
                            player.setDataSource(activeSource)
                        }

                        player.setOnPreparedListener { mp ->
                            if (!isActive) {
                                try { mp.release() } catch (_: Throwable) {}
                                return@setOnPreparedListener
                            }
                            isBuffering = false
                            totalDurationMs = mp.duration.toFloat().coerceAtLeast(1f)
                            mp.isLooping = isLooping
                            applySpeed(playbackSpeed)
                            try {
                                mp.start()
                                isPlaying = true
                                startProgressTicker()
                                AppNotificationHelper.showAudioPlaybackNotification(context.applicationContext, fileName, isPlaying = true)
                            } catch (_: Throwable) {
                                isPlaying = false
                            }
                        }

                        player.setOnCompletionListener {
                            if (!isLooping) {
                                isPlaying = false
                                currentPositionMs = totalDurationMs
                                AppNotificationHelper.dismissAudioNotification(context.applicationContext)
                            }
                        }

                        player.setOnErrorListener { _, _, _ ->
                            isBuffering = false
                            isPlaying = false
                            AppNotificationHelper.dismissAudioNotification(context.applicationContext)
                            try {
                                Toast.makeText(context.applicationContext, "Error playing audio", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {}
                            true
                        }

                        player.prepareAsync()
                    } else {
                        isBuffering = false
                        try {
                            Toast.makeText(context.applicationContext, "Audio file not available", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {}
                    }
                }
            } catch (t: Throwable) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        isBuffering = false
                        try {
                            Toast.makeText(context.applicationContext, "Failed to load audio: ${t.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    fun togglePlayPause(context: Context? = null) {
        if (isPlaying) {
            pause(context)
        } else {
            resume(context)
        }
    }

    fun pause(context: Context? = null) {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            val ctx = context?.applicationContext ?: applicationContext
            if (ctx != null && activeTrackName != null) {
                AppNotificationHelper.showAudioPlaybackNotification(ctx, activeTrackName!!, isPlaying = false)
            }
        } catch (_: Exception) { }
    }

    fun resume(context: Context? = null) {
        try {
            mediaPlayer?.start()
            isPlaying = true
            startProgressTicker()
            val ctx = context?.applicationContext ?: applicationContext
            if (ctx != null && activeTrackName != null) {
                AppNotificationHelper.showAudioPlaybackNotification(ctx, activeTrackName!!, isPlaying = true)
            }
        } catch (_: Exception) { }
    }

    fun seekTo(positionMs: Float) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            currentPositionMs = positionMs
        } catch (_: Exception) { }
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
                val params = mediaPlayer?.playbackParams ?: PlaybackParams()
                params.speed = speed
                mediaPlayer?.playbackParams = params
            }
        } catch (_: Exception) { }
    }

    fun toggleLoop() {
        isLooping = !isLooping
        try {
            mediaPlayer?.isLooping = isLooping
        } catch (_: Exception) { }
    }

    fun toggleMute() {
        isMuted = !isMuted
        try {
            if (isMuted) mediaPlayer?.setVolume(0f, 0f)
            else mediaPlayer?.setVolume(1f, 1f)
        } catch (_: Exception) { }
    }

    fun stop(context: Context? = null) {
        playJob?.cancel()
        playJob = null
        tickerJob?.cancel()
        tickerJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) { }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) { }
        mediaPlayer = null
        val ctx = context?.applicationContext ?: applicationContext
        if (ctx != null) {
            AppNotificationHelper.dismissAudioNotification(ctx)
        }
        activeTrackPath = null
        activeTrackName = null
        isPlaying = false
        isBuffering = false
        isExpandedSheetOpen = false
    }

    private fun startProgressTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && isPlaying) {
                try {
                    val pos = mediaPlayer?.currentPosition ?: 0
                    currentPositionMs = pos.toFloat()
                } catch (_: Throwable) { }
                delay(250)
            }
        }
    }
}
