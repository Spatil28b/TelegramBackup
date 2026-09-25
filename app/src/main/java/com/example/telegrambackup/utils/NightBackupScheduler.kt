package com.example.telegrambackup.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.telegrambackup.receiver.NightBackupAlarmReceiver
import java.util.Calendar

object NightBackupScheduler {
    private const val TAG = "NightBackupScheduler"
    const val REQUEST_CODE_NIGHT_ALARM = 9021

    fun scheduleStrictNightBackup(context: Context) {
        val prefs = AppPrefs(context)
        if (!prefs.autoBackupEnabled || prefs.stopAllBackups || prefs.smartBackupMode != 2) {
            cancelNightBackup(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val targetHour = prefs.nightBackupHour.coerceIn(0, 23)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, NightBackupAlarmReceiver::class.java).apply {
            action = NightBackupAlarmReceiver.ACTION_STRICT_NIGHT_BACKUP
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NIGHT_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // AlarmClock is immune to Doze mode and manufacturer power savers
                val alarmClockInfo = AlarmManager.AlarmClockInfo(target.timeInMillis, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            }
            Log.d(TAG, "Strict Night Backup scheduled exactly for ${target.time} (Hour: $targetHour:00)")
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock not permitted, falling back to setExactAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule night alarm", ex)
            }
        }
    }

    fun cancelNightBackup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NightBackupAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_NIGHT_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Strict Night Backup alarm cancelled.")
        }
    }
}
