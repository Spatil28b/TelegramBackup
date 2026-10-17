package com.example.telegrambackup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.utils.AppNotificationHelper
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupProgressTracker
import com.example.telegrambackup.worker.BackupWorker

class BackupControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_BACKUP = "com.example.telegrambackup.ACTION_START_BACKUP"
        const val ACTION_STOP_BACKUP = "com.example.telegrambackup.ACTION_STOP_BACKUP"
        const val ACTION_AUDIO_TOGGLE = "com.example.telegrambackup.ACTION_AUDIO_TOGGLE"
        const val ACTION_AUDIO_STOP = "com.example.telegrambackup.ACTION_AUDIO_STOP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val prefs = AppPrefs(context)

        when (action) {
            ACTION_START_BACKUP -> {
                prefs.stopAllBackups = false
                try {
                    val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "ManualSyncWork",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                    BackupProgressTracker.startBackupSession(0, "Initiating Cloud Backup...")
                    AppNotificationHelper.showPersistentControlNotification(context, isRunning = true, "Scanning & uploading files...")
                    AppNotificationHelper.showBackupActiveNotification(
                        context = context,
                        fileName = "Scanning Storage...",
                        completedFiles = 0,
                        totalFiles = 0,
                        progressPercent = 0,
                        speedStr = "0 KB/s"
                    )
                    Toast.makeText(context, "⚡ Cloud Backup Started!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Start error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_STOP_BACKUP -> {
                prefs.stopAllBackups = true
                BackupProgressTracker.finishBackupSession("Backup Paused by User")
                AppNotificationHelper.cancelBackupNotification(context)
                AppNotificationHelper.showPersistentControlNotification(context, isRunning = false)
                AppNotificationHelper.showBackupPausedNotification(context)
                Toast.makeText(context, "⏸️ Cloud Backup Paused", Toast.LENGTH_SHORT).show()
            }
            ACTION_AUDIO_TOGGLE -> {
                com.example.telegrambackup.ui.player.AudioPlayerManager.togglePlayPause(context)
            }
            ACTION_AUDIO_STOP -> {
                com.example.telegrambackup.ui.player.AudioPlayerManager.stop(context)
            }
        }
    }
}
