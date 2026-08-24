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
 * MODERN MINIMAL NOTIFICATION ENGINE
 * =====================================================================================
 * Fully separated channels & notifications:
 * 1. PERSISTENT CONTROLLER: Always-visible quick-access tile with 1-tap Start/Stop & Drive
 * 2. LIVE UPLOADS: Dedicated upload progress stream with chunk meter & speed stats
 * 3. DOWNLOADS: Dedicated downloads stream with completion triggers & file openers
 * 4. ALERTS: High-priority system alerts, session completion reports, and storage warnings
 * =====================================================================================
 */
object AppNotificationHelper {

    // 4 Distinct Notification Channels
    const val CHANNEL_ID_CONTROLLER = "telegram_backup_control_v3"
    const val CHANNEL_ID_UPLOADS = "telegram_backup_uploads_v3"
    const val CHANNEL_ID_DOWNLOADS = "telegram_backup_downloads_v3"
    const val CHANNEL_ID_ALERTS = "telegram_backup_alerts_v3"

    // Distinct Notification IDs
    const val NOTIFICATION_ID_CONTROLLER = 1000
    const val NOTIFICATION_ID_BACKUP_ACTIVE = 1001
    const val NOTIFICATION_ID_BACKUP_COMPLETED = 2001
    const val NOTIFICATION_ID_BACKUP_PAUSED = 2002
    const val NOTIFICATION_ID_SYSTEM_ALERT = 2003
    const val NOTIFICATION_ID_DOWNLOAD_ACTIVE = 3001
    const val NOTIFICATION_ID_DOWNLOAD_COMPLETED = 3002

    const val EXTRA_NAVIGATE_TAB = "extra_navigate_tab"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

            manager.createNotificationChannel(controllerChannel)
            manager.createNotificationChannel(uploadsChannel)
            manager.createNotificationChannel(downloadsChannel)
            manager.createNotificationChannel(alertsChannel)
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
        val startPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_START_BACKUP, 101)
        val stopPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_STOP_BACKUP, 102)

        val title = when {
            isRunning -> "☁️ Cloud Backup • Active"
            prefs.stopAllBackups -> "⏸️ Cloud Backup • Paused"
            else -> "☁️ Cloud Backup • Standby"
        }

        val content = when {
            statusText.isNotBlank() -> statusText
            isRunning -> "Syncing files to Telegram Cloud in background"
            prefs.stopAllBackups -> "Paused by user • Tap 'Start' to resume"
            else -> "Ready • Tap 'Start' to trigger instant backup"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CONTROLLER)
            .setSmallIcon(if (isRunning) R.drawable.ic_cloud_upload else R.drawable.ic_cloud_done)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (isRunning) {
            builder.addAction(R.drawable.ic_pause, "⏹ Stop Backup", stopPendingIntent)
            builder.addAction(R.drawable.ic_cloud_upload, "📊 Progress", openAppPendingIntent)
        } else {
            builder.addAction(R.drawable.ic_play, "▶ Start Backup", startPendingIntent)
            builder.addAction(R.drawable.ic_cloud_done, "📁 Open Drive", openAppPendingIntent)
        }

        manager.notify(NOTIFICATION_ID_CONTROLLER, builder.build())
    }

    fun cancelPersistentControlNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_CONTROLLER)
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
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppPendingIntent = createNavPendingIntent(context, "PROGRESS", 200)
        val stopPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_STOP_BACKUP, 201)

        val displayTitle = if (totalFiles > 0) "☁️ Syncing ($completedFiles/$totalFiles) • $speedStr" else "☁️ Uploading to Telegram Cloud • $speedStr"
        val displayContent = if (fileName.isNotEmpty()) "$fileName ($progressPercent%)" else "Preparing file chunks..."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPLOADS)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(displayTitle)
            .setContentText(displayContent)
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent <= 0)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_pause, "⏹ Stop", stopPendingIntent)
            .addAction(R.drawable.ic_cloud_upload, "📊 View Progress", openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(NOTIFICATION_ID_BACKUP_ACTIVE, notification)
    }

    fun cancelBackupNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_BACKUP_ACTIVE)
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
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppPendingIntent = createNavPendingIntent(context, "DRIVE", 300)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("⬇️ Downloading • $speedStr")
            .setContentText("$fileName ($progressPercent%)")
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent <= 0)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(NOTIFICATION_ID_DOWNLOAD_ACTIVE, notification)
    }

    fun showDownloadFinishedNotification(
        context: Context,
        fileName: String,
        filePath: String
    ) {
        initNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOAD_ACTIVE)

        val file = File(filePath)
        val intent = if (file.exists()) {
            Intent(Intent.ACTION_VIEW).apply {
                val uri = try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (_: Exception) { Uri.fromFile(file) }
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_TAB, "DRIVE")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            301,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_cloud_done)
            .setContentTitle("✓ Download Complete")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID_DOWNLOAD_COMPLETED, notification)
    }

    fun cancelDownloadNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOAD_ACTIVE)
    }

    // =========================================================================
    // 4. SEPARATE SYSTEM ALERTS & SUMMARY NOTIFICATIONS
    // =========================================================================
    fun showBackupFinishedNotification(
        context: Context,
        totalCount: Int,
        summary: String = "All files successfully backed up to Telegram Cloud!"
    ) {
        initNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_BACKUP_ACTIVE)

        val openAppPendingIntent = createNavPendingIntent(context, "DRIVE", 400)
        val startPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_START_BACKUP, 401)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_cloud_done)
            .setContentTitle("✅ Backup Complete")
            .setContentText(if (totalCount > 0) "✓ Synced $totalCount files ($summary)" else summary)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_play, "▶ Sync Again", startPendingIntent)
            .addAction(R.drawable.ic_cloud_done, "📁 Open Drive", openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID_BACKUP_COMPLETED, notification)
    }

    fun showBackupPausedNotification(context: Context) {
        initNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_BACKUP_ACTIVE)

        val openAppPendingIntent = createNavPendingIntent(context, "PROGRESS", 410)
        val startPendingIntent = createControlPendingIntent(context, BackupControlReceiver.ACTION_START_BACKUP, 411)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_pause)
            .setContentTitle("⏸️ Backup Paused")
            .setContentText("Cloud upload paused. Tap 'Start' to resume anytime.")
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_play, "▶ Resume Backup", startPendingIntent)
            .addAction(R.drawable.ic_cloud_upload, "📊 View Progress", openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID_BACKUP_PAUSED, notification)
    }

    fun showSystemAlertNotification(context: Context, title: String, message: String) {
        initNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppPendingIntent = createNavPendingIntent(context, "SETTINGS", 420)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIFICATION_ID_SYSTEM_ALERT, notification)
    }
}

