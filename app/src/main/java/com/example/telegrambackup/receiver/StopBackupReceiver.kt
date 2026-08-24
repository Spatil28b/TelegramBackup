package com.example.telegrambackup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.telegrambackup.utils.AppNotificationHelper
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupProgressTracker

class StopBackupReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_BACKUP = "com.example.telegrambackup.ACTION_STOP_BACKUP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_BACKUP) {
            val prefs = AppPrefs(context)
            prefs.stopAllBackups = true
            BackupProgressTracker.finishBackupSession("Backup Paused by User")
            AppNotificationHelper.showBackupPausedNotification(context)
            Toast.makeText(context, "⏸️ Cloud Backup Paused", Toast.LENGTH_SHORT).show()
        }
    }
}
