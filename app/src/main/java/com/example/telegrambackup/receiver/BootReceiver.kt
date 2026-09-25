package com.example.telegrambackup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.worker.BackupWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * =====================================================================================
 * BOOT & DEVICE RESTART BROADCAST RECEIVER
 * =====================================================================================
 * Reschedules background WorkManager backup tasks automatically when device finishes booting
 * (`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `QUICKBOOT_POWERON`).
 * Guarantees background backups run according to the user's selected Smart Backup Mode
 * (Continuous, Custom Interval, or Night Express).
 * =====================================================================================
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device reboot / package updated received ($action). Rescheduling Smart Backup Engine...")

            val prefs = AppPrefs(context)
            if (prefs.autoBackupEnabled && !prefs.stopAllBackups) {
                try {
                    val workManager = WorkManager.getInstance(context)
                    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    val policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE

                    when (prefs.smartBackupMode) {
                        0 -> {
                            val req = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                                .setConstraints(constraints).build()
                            workManager.enqueueUniquePeriodicWork("SmartPeriodicBackupWork", policy, req)
                        }
                        1 -> {
                            val interval = prefs.smartScanIntervalHours.coerceAtLeast(1)
                            val req = PeriodicWorkRequestBuilder<BackupWorker>(interval.toLong(), TimeUnit.HOURS)
                                .setConstraints(constraints).build()
                            workManager.enqueueUniquePeriodicWork("SmartPeriodicBackupWork", policy, req)
                        }
                        2 -> {
                            com.example.telegrambackup.utils.NightBackupScheduler.scheduleStrictNightBackup(context)
                        }
                    }

                    Log.d(TAG, "⚡ Smart Backup Engine successfully rescheduled on boot with mode ${prefs.smartBackupMode}!")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule WorkManager on boot", e)
                }
            }
        }
    }
}
