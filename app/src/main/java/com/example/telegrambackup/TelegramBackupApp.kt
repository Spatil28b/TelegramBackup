package com.example.telegrambackup

import android.app.Application
import com.example.telegrambackup.utils.AppCrashShield
import com.example.telegrambackup.utils.AppNotificationHelper

class TelegramBackupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Install Global Crash Shield first before any background workers or DB access
        AppCrashShield.install(this)

        // 2. Initialize notification channels safely
        try {
            AppNotificationHelper.initNotificationChannels(this)
        } catch (_: Throwable) {}
    }
}
