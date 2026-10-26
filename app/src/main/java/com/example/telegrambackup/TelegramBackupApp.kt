package com.example.telegrambackup

import android.app.Application
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.AppCrashShield
import com.example.telegrambackup.utils.AppNotificationHelper
import com.example.telegrambackup.utils.AppPrefs

class TelegramBackupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Install Global Crash Shield first before any background workers or DB access
        AppCrashShield.install(this)

        // 2. Initialize active auth token and backend host from persisted preferences
        try {
            val prefs = AppPrefs(this)
            val token = prefs.authToken
            if (token.isNotBlank()) {
                ServerApi.activeAuthToken = token
            }
            val parsedHost = try { java.net.URI(prefs.serverUrl).host } catch (_: Throwable) { null }
            if (!parsedHost.isNullOrBlank()) {
                com.example.telegrambackup.network.HttpClientProvider.backendHost = parsedHost
            }
        } catch (_: Throwable) {}

        // 3. Initialize notification channels safely
        try {
            AppNotificationHelper.initNotificationChannels(this)
        } catch (_: Throwable) {}

        // 4. Ensure launcher icon state is consistent after app update
        try {
            com.example.telegrambackup.utils.AppIconManager.syncLauncherState(this)
        } catch (_: Throwable) {}
    }
}
