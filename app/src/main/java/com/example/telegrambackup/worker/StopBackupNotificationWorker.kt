package com.example.telegrambackup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.telegrambackup.R
import com.example.telegrambackup.utils.AppPrefs

class StopBackupNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPrefs(applicationContext)

        // Only notify if backups are still stopped
        if (!prefs.stopAllBackups) return Result.success()

        val stoppedSinceMs = System.currentTimeMillis() - prefs.stopAllBackupsTimestamp
        val stoppedHours = stoppedSinceMs / (1000L * 60L * 60L)

        if (stoppedHours < 3) return Result.success()

        val channelId = "backup_alert_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Backup Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when backups are paused for too long"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Backups Paused for ${stoppedHours}+ Hours")
            .setContentText("Your files are not being backed up. Open the app to resume backup protection.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("All backups have been paused for over $stoppedHours hours. Your new files, photos, and documents are NOT being protected. Open Settings → Smart Backup to resume."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9001, notification)

        return Result.success()
    }
}
