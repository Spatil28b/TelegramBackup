package com.example.telegrambackup.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.worker.BackupWorker
import java.io.File

/**
 * =====================================================================================
 * LIVE CAMERA & DOWNLOAD INOTIFY OBSERVER SERVICE
 * =====================================================================================
 * Uses Linux inotify FileObserver to instantly detect new camera photos/videos and downloads
 * the millisecond they are written to disk, and triggers instant background sync.
 * =====================================================================================
 */
class LiveCameraObserverService : Service() {

    companion object {
        private const val TAG = "LiveCameraObserver"
        private const val CHANNEL_ID = "live_observer_channel"
        private const val NOTIFICATION_ID = 4040

        fun start(context: Context) {
            val prefs = AppPrefs(context)
            if (!prefs.liveCameraObserverEnabled || !prefs.autoBackupEnabled) return
            val intent = Intent(context, LiveCameraObserverService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start LiveCameraObserverService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveCameraObserverService::class.java)
            context.stopService(intent)
        }
    }

    private val observers = mutableListOf<FileObserver>()
    private var lastTriggerTimeMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        setupFileObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Auto-Backup Watcher",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitors camera and downloads for instant backup"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloud Auto-Backup Active")
            .setContentText("Watching camera & downloads for instant sync")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupFileObservers() {
        val watchPaths = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in watchPaths) {
            if (dir.exists()) {
                try {
                    val observer = createFileObserver(dir)
                    observer.startWatching()
                    observers.add(observer)
                    Log.d(TAG, "Watching folder: ${dir.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not watch folder: ${dir.absolutePath} - ${e.message}")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun createFileObserver(dir: File): FileObserver {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(dir, path)
                }
            }
        } else {
            object : FileObserver(dir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(dir, path)
                }
            }
        }
    }

    private fun handleFileEvent(parentDir: File, fileName: String?) {
        if (fileName.isNullOrBlank() || fileName.startsWith(".")) return
        val now = System.currentTimeMillis()
        // Debounce triggers by at least 4 seconds to allow multi-shot camera saves
        if (now - lastTriggerTimeMs < 4000) return
        lastTriggerTimeMs = now

        Log.d(TAG, "⚡ Live change detected: $fileName in ${parentDir.name}. Scheduling Instant Sync...")
        val prefs = AppPrefs(applicationContext)
        if (prefs.autoBackupEnabled && !prefs.stopAllBackups && prefs.liveCameraObserverEnabled) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "LiveCameraSyncWork",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error enqueuing live camera work", e)
            }
        }
    }

    override fun onDestroy() {
        observers.forEach {
            try { it.stopWatching() } catch (_: Exception) {}
        }
        observers.clear()
        super.onDestroy()
    }
}
