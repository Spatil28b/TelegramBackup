package com.example.telegrambackup.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.telegrambackup.MainActivity
import com.example.telegrambackup.R
import com.example.telegrambackup.receiver.BackupControlReceiver
import java.io.File

/**
 * =====================================================================================
 * MODERN MATERIAL 3 NOTIFICATION ENGINE
 * =====================================================================================
 * Sleek, branded Google M3 notifications with Telegram Blue tint, rich subtext badges,
 * expandable BigTextStyle, and clean single-line action buttons.
 *
 * 1. PERSISTENT CONTROLLER: Always-visible quick-access tile with 1-tap Start/Stop & Drive
 * 2. LIVE UPLOADS: Dedicated upload progress stream with chunk meter & speed stats
 * 3. DOWNLOADS: Dedicated downloads stream with completion triggers & file openers
 * 4. ALERTS: High-priority system alerts, session completion reports, and storage warnings
 * =====================================================================================
 */
object AppNotificationHelper {

    // 5 Distinct Notification Channels
    const val CHANNEL_ID_CONTROLLER = "telegram_backup_control_v3"
    const val CHANNEL_ID_UPLOADS = "telegram_backup_uploads_v3"
    const val CHANNEL_ID_DOWNLOADS = "telegram_backup_downloads_v3"
    const val CHANNEL_ID_ALERTS = "telegram_backup_alerts_v3"
    const val CHANNEL_ID_AUDIO = "telegram_backup_audio_v3"

    // Distinct Notification IDs
    const val NOTIFICATION_ID_CONTROLLER = 1000
    const val NOTIFICATION_ID_BACKUP_ACTIVE = 1001
    const val NOTIFICATION_ID_BACKUP_COMPLETED = 2001
    const val NOTIFICATION_ID_BACKUP_PAUSED = 2002
    const val NOTIFICATION_ID_SYSTEM_ALERT = 2003
    const val NOTIFICATION_ID_DOWNLOAD_ACTIVE = 3001
    const val NOTIFICATION_ID_DOWNLOAD_COMPLETED = 3002
    const val NOTIFICATION_ID_AUDIO = 4001

    const val EXTRA_NAVIGATE_TAB = "extra_navigate_tab"

    // Brand Colors
    val COLOR_PRIMARY = 0xFF0088CC.toInt()   // Telegram Blue
    val COLOR_SUCCESS = 0xFF10B981.toInt()   // Telegram Emerald
    val COLOR_WARNING = 0xFFF59E0B.toInt()   // Amber

    private var cachedLargeIcon: android.graphics.Bitmap? = null

    fun getAppLargeIcon(context: Context): android.graphics.Bitmap? {
        val cached = cachedLargeIcon
        if (cached != null && !cached.isRecycled) return cached
        return try {
            val prefs = AppPrefs(context)
            val iconRes = if (prefs.appIcon == "blue") R.drawable.app_icon_blue else R.drawable.app_icon_green
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, iconRes) ?: return null
            val density = context.resources.displayMetrics.density
            val sizePx = (64 * density).toInt().coerceAtLeast(64)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
            cachedLargeIcon = scaled
            scaled
        } catch (_: Throwable) {
            null
        }
    }

    fun safeNotify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.notify(id, notification)
        } catch (t: Throwable) {
            android.util.Log.w("AppNotificationHelper", "Failed posting notification $id: ${t.message}")
        }
    }

    fun safeCancel(context: Context, id: Int) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.cancel(id)
        } catch (t: Throwable) {
            android.util.Log.w("AppNotificationHelper", "Failed cancelling notification $id: ${t.message}")
        }
    }

    fun initNotificationChannels(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

                // 1. Persistent Controller (Low importance = silent, non-intrusive)
                val controllerChannel = NotificationChannel(
                    CHANNEL_ID_CONTROLLER,
                    "Backup Quick Controller",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent status bar quick controls to Start, Stop, or open Cloud Drive"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

                // 2. Live Upload Progress (Low importance = silent updates)
                val uploadsChannel = NotificationChannel(
                    CHANNEL_ID_UPLOADS,
                    "Live Upload Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live upload progress meters and transfer speeds"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

                // 3. File Downloads (Low importance = silent transfer)
                val downloadsChannel = NotificationChannel(
                    CHANNEL_ID_DOWNLOADS,
                    "File & Folder Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Live download progress and download completed notifications"
                    setShowBadge(true)
                    enableLights(false)
                    enableVibration(false)
                }

                // 4. System Alerts & Summaries (Default importance = visible toast alert)
                val alertsChannel = NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "System Alerts & Completion Reports",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Backup session completion summaries, storage warnings, and alerts"
                    setShowBadge(true)
                    enableLights(true)
                }

                // 5. Audio Playback (Low importance = silent background playback)
                val audioChannel = NotificationChannel(
                    CHANNEL_ID_AUDIO,
                    "Audio Playback Controls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls and track information for background audio"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }

                manager.createNotificationChannel(controllerChannel)
                manager.createNotificationChannel(uploadsChannel)
                manager.createNotificationChannel(downloadsChannel)
                manager.createNotificationChannel(alertsChannel)
                manager.createNotificationChannel(audioChannel)
            }
        } catch (t: Throwable) {
            android.util.Log.e("AppNotificationHelper", "Error initializing notification channels", t)
        }
    }

    private fun createNavPendingIntent(context: Context, tabName: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TAB, tabName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, reqCode, intent, flags)
    }

    private fun createControlPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, BackupControlReceiver::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, reqCode, intent, flags)
    }

    // =========================================================================
    // 1. ALWAYS-VISIBLE PERSISTENT QUICK CONTROLLER NOTIFICATION
    // =========================================================================
    fun showPersistentControlNotification(
        context: Context,
        isRunning: Boolean,
        statusText: String = ""
    ) {
        val prefs = AppPrefs(context)
        if (!prefs.persistentNotificationEnabled) {
            cancelPersistentControlNotification(context)
            return
        }

        initNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppPendingIntent = createNavPendingIntent(context, if (isRunning) "PROGRESS" else "DRIVE", 100)
        val openDrivePendingIntent = createNavPendingIntent(context, "DRIVE", 103)
        val startPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_START_BACKUP, 101)
        val stopPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_STOP_BACKUP, 102)

        val title = when {
            isRunning -> "TSpace Vault Syncing"
            prefs.stopAllBackups -> "TSpace Sync Paused"
            else -> "TSpace Vault Ready"
        }

        val content = when {
            statusText.isNotBlank() -> statusText
            isRunning -> "Active encrypted backup to Telegram Cloud"
            prefs.stopAllBackups -> "Sync paused by user • Storage secure"
            else -> "Cloud vault protected • Auto-backup ready"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CONTROLLER)
            .setSmallIcon(if (isRunning) R.drawable.ic_notif_cloud_upload else R.drawable.ic_notif_cloud_done)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(if (isRunning) COLOR_PRIMARY else COLOR_SUCCESS)
            .setSubText("TSpace Cloud Vault")
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (isRunning) {
            builder.addAction(R.drawable.ic_pause, "Pause", stopPendingIntent)
            builder.addAction(R.drawable.ic_cloud_upload, "View Progress", openAppPendingIntent)
            builder.addAction(R.drawable.ic_folder, "Open Vault", openDrivePendingIntent)
        } else {
            builder.addAction(R.drawable.ic_play, "Sync Now", startPendingIntent)
            builder.addAction(R.drawable.ic_folder, "Open Vault", openDrivePendingIntent)
        }

        safeNotify(context, NOTIFICATION_ID_CONTROLLER, builder.build())
    }

    fun cancelPersistentControlNotification(context: Context) {
        safeCancel(context, NOTIFICATION_ID_CONTROLLER)
    }

    // =========================================================================
    // 2. SEPARATE LIVE UPLOADS PROGRESS NOTIFICATION
    // =========================================================================
    fun showBackupActiveNotification(
        context: Context,
        fileName: String,
        completedFiles: Int,
        totalFiles: Int,
        progressPercent: Int,
        speedStr: String
    ) {
        initNotificationChannels(context)

        val openAppPendingIntent = createNavPendingIntent(context, "PROGRESS", 200)
        val openDrivePendingIntent = createNavPendingIntent(context, "DRIVE", 202)
        val stopPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_STOP_BACKUP, 201)

        val speedBps = BackupProgressTracker.uploadSpeedBps.value
        val activeItem = BackupProgressTracker.activeUploadsList.value.firstOrNull()
        val etaStr = if (speedBps > 1024L && activeItem != null && activeItem.fileSize > 0 && progressPercent in 1..99) {
            val remainingBytes = ((1f - activeItem.progress.coerceIn(0f, 1f)) * activeItem.fileSize).toLong()
            val sec = if (remainingBytes > 0) remainingBytes / speedBps else 0L
            if (sec in 1..86400) {
                if (sec < 60) "${sec}s left" else "${sec / 60}m ${sec % 60}s left"
            } else ""
        } else ""

        val displayTitle = if (totalFiles > 0) "Backing Up ($completedFiles of $totalFiles) • $progressPercent%" else "Backing Up • $progressPercent%"
        val cleanName = if (fileName.length > 28) fileName.take(16) + "…" + fileName.takeLast(10) else fileName
        val displayContent = if (fileName.isNotEmpty()) {
            when {
                speedStr.isNotBlank() && etaStr.isNotBlank() -> "$cleanName • $speedStr • $etaStr"
                speedStr.isNotBlank() -> "$cleanName • $speedStr"
                else -> cleanName
            }
        } else "Transferring files to vault..."

        val subtextSpeed = when {
            speedStr.isNotBlank() && etaStr.isNotBlank() -> "TSpace • $speedStr • $etaStr"
            speedStr.isNotBlank() -> "TSpace • $speedStr"
            else -> "TSpace Cloud Sync"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPLOADS)
            .setSmallIcon(R.drawable.ic_notif_cloud_upload)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(COLOR_PRIMARY)
            .setSubText(subtextSpeed)
            .setContentTitle(displayTitle)
            .setContentText(displayContent)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Transferring $fileName\n$completedFiles of $totalFiles files transferred ($progressPercent%)\n${if (speedStr.isNotBlank()) "Transfer Speed: $speedStr" else "Optimizing transfer..."}${if (etaStr.isNotBlank()) " • $etaStr" else ""}"
                )
            )
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent <= 0)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(R.drawable.ic_pause, "Pause", stopPendingIntent)
            .addAction(R.drawable.ic_cloud_upload, "Progress", openAppPendingIntent)
            .addAction(R.drawable.ic_folder, "Vault", openDrivePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()

        safeNotify(context, NOTIFICATION_ID_BACKUP_ACTIVE, notification)
    }

    fun cancelBackupNotification(context: Context) {
        safeCancel(context, NOTIFICATION_ID_BACKUP_ACTIVE)
    }

    // =========================================================================
    // 3. SEPARATE DOWNLOADS NOTIFICATION
    // =========================================================================
    fun showDownloadActiveNotification(
        context: Context,
        fileName: String,
        progressPercent: Int,
        speedStr: String
    ) {
        initNotificationChannels(context)
        val openAppPendingIntent = createNavPendingIntent(context, "DRIVE", 300)

        val cleanName = if (fileName.length > 26) fileName.take(14) + "…" + fileName.takeLast(10) else fileName
        val displayContent = if (speedStr.isNotBlank()) "$progressPercent% • $speedStr" else "$progressPercent% downloaded"
        val subtext = if (speedStr.isNotBlank()) "TSpace • $speedStr" else "TSpace Download"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notif_download)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(COLOR_PRIMARY)
            .setSubText(subtext)
            .setContentTitle("Downloading: $cleanName")
            .setContentText(displayContent)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Downloading $fileName from cloud vault.\nProgress: $progressPercent%\n${if (speedStr.isNotBlank()) "Speed: $speedStr" else ""}"
                )
            )
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent <= 0)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(R.drawable.ic_folder, "Open Vault", openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()

        safeNotify(context, NOTIFICATION_ID_DOWNLOAD_ACTIVE, notification)
    }

    fun showDownloadFinishedNotification(
        context: Context,
        fileName: String,
        filePath: String
    ) {
        initNotificationChannels(context)
        safeCancel(context, NOTIFICATION_ID_DOWNLOAD_ACTIVE)

        val file = File(filePath)
        val viewIntent = if (file.exists()) {
            FileEncryptor.ensureDecrypted(file, context, fileName)
            val mimeType = FileViewerHelper.getMimeType(fileName)
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) { Uri.fromFile(file) }

            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                clipData = android.content.ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_TAB, "DRIVE")
            }
        }

        val openFilePendingIntent = PendingIntent.getActivity(
            context,
            301,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = if (file.exists()) {
            val mimeType = FileViewerHelper.getMimeType(fileName)
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: Exception) { Uri.fromFile(file) }

            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TAB, "DRIVE")
            }
        }
        val shareChooser = Intent.createChooser(shareIntent, "Share file via...")
        shareChooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val sharePendingIntent = PendingIntent.getActivity(
            context,
            302,
            shareChooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openDrivePendingIntent = createNavPendingIntent(context, "DRIVE", 303)

        val cleanName = if (fileName.length > 26) fileName.take(14) + "…" + fileName.takeLast(10) else fileName

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notif_cloud_done)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(COLOR_SUCCESS)
            .setSubText("⬇️ ✓ Done")
            .setContentTitle("✓ $cleanName")
            .setContentText("📁 Downloads • 💾 Saved")
            .setAutoCancel(true)
            .setContentIntent(openFilePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(R.drawable.ic_play, "📂 Open", openFilePendingIntent)
            .addAction(R.drawable.ic_share, "📤 Share", sharePendingIntent)
            .addAction(R.drawable.ic_folder, "📁 Drive", openDrivePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        safeNotify(context, NOTIFICATION_ID_DOWNLOAD_COMPLETED, notification)
    }

    fun cancelDownloadNotification(context: Context) {
        safeCancel(context, NOTIFICATION_ID_DOWNLOAD_ACTIVE)
    }

    // =========================================================================
    // 4. SEPARATE SYSTEM ALERTS & SUMMARY NOTIFICATIONS
    // =========================================================================
    fun showBackupFinishedNotification(
        context: Context,
        totalCount: Int,
        summary: String = "✓ Synced"
    ) {
        initNotificationChannels(context)
        safeCancel(context, NOTIFICATION_ID_BACKUP_ACTIVE)

        val openDrivePendingIntent = createNavPendingIntent(context, "DRIVE", 400)
        val openCleanPendingIntent = createNavPendingIntent(context, "HOME", 402)
        val startPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_START_BACKUP, 401)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notif_cloud_done)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(COLOR_SUCCESS)
            .setSubText("☁️ ✓ Done")
            .setContentTitle(if (totalCount > 0) "✓ Synced • ☁️ $totalCount Files" else "✓ Synced • ☁️ Cloud")
            .setContentText("🛡️ Vault Updated • ⏱️ Just Now")
            .setAutoCancel(true)
            .setContentIntent(openDrivePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(R.drawable.ic_folder, "📁 Drive", openDrivePendingIntent)
            .addAction(R.drawable.ic_cloud_upload, "🧹 Clean", openCleanPendingIntent)
            .addAction(R.drawable.ic_play, "▶️ Sync", startPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        safeNotify(context, NOTIFICATION_ID_BACKUP_COMPLETED, notification)
    }

    fun showBackupPausedNotification(context: Context) {
        initNotificationChannels(context)
        safeCancel(context, NOTIFICATION_ID_BACKUP_ACTIVE)

        val openAppPendingIntent = createNavPendingIntent(context, "PROGRESS", 410)
        val openDrivePendingIntent = createNavPendingIntent(context, "DRIVE", 412)
        val startPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_START_BACKUP, 411)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notif_pause)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(COLOR_WARNING)
            .setSubText("⏸️ Paused")
            .setContentTitle("⏸️ Uploads Paused")
            .setContentText("⏱️ On Standby • 🛡️ Ready")
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(R.drawable.ic_play, "▶️ Resume", startPendingIntent)
            .addAction(R.drawable.ic_folder, "📁 Drive", openDrivePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        safeNotify(context, NOTIFICATION_ID_BACKUP_PAUSED, notification)
    }

    fun showSystemAlertNotification(context: Context, title: String, message: String) {
        initNotificationChannels(context)
        val openAppPendingIntent = createNavPendingIntent(context, "SETTINGS", 420)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notif_cloud_upload)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setColor(COLOR_PRIMARY)
            .setSubText("⚠️ System")
            .setContentTitle("⚠️ $title")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        safeNotify(context, NOTIFICATION_ID_SYSTEM_ALERT, notification)
    }

    fun showAudioPlaybackNotification(context: Context, trackName: String, isPlaying: Boolean) {
        initNotificationChannels(context)
        val toggleAction = if (isPlaying) "Pause" else "Play"
        val toggleIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val togglePendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_AUDIO_TOGGLE, 9001)
        val stopPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_AUDIO_STOP, 9002)
        val openAppIntent = createNavPendingIntent(context, "Browse", 9000)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_AUDIO)
            .setSmallIcon(R.drawable.ic_notif_download)
            .apply { getAppLargeIcon(context)?.let { setLargeIcon(it) } }
            .setContentTitle(trackName)
            .setContentText(if (isPlaying) "Playing in background • TSpace Player" else "Paused • TSpace Player")
            .setSubText("Audio Player")
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .setColor(COLOR_PRIMARY)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(toggleIcon, toggleAction, togglePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()

        safeNotify(context, NOTIFICATION_ID_AUDIO, notification)
    }

    fun dismissAudioNotification(context: Context) {
        safeCancel(context, NOTIFICATION_ID_AUDIO)
    }
}
