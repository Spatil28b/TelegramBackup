package com.example.telegrambackup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.*
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.NightBackupScheduler
import com.example.telegrambackup.worker.BackupWorker

class NightBackupAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STRICT_NIGHT_BACKUP = "com.example.telegrambackup.ACTION_STRICT_NIGHT_BACKUP"
        private const val TAG = "NightBackupAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⚡ Strict Night Backup Alarm triggered! Action: ${intent.action}")

        val prefs = AppPrefs(context)
        if (!prefs.autoBackupEnabled || prefs.stopAllBackups) {
            Log.d(TAG, "Backup is disabled or paused by user. Skipping.")
            return
        }

        // Strict Wi-Fi verification if user selected Wi-Fi Only
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = connManager?.let { cm ->
            val network = cm.activeNetwork ?: return@let false
            val caps = cm.getNetworkCapabilities(network) ?: return@let false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    (!prefs.backupOverWifi || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        } ?: false

        val requiredNetwork = if (prefs.backupOverWifi) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .addTag("NightExpressBackup")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "StrictNightBackupExecution",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Dispatched WorkManager OneTimeWork for BackupWorker with network: $requiredNetwork (On Wi-Fi: $isWifi)")

        // Re-arm for the next night strictly
        NightBackupScheduler.scheduleStrictNightBackup(context)
    }
}
