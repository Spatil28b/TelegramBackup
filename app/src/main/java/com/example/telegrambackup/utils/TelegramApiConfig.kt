package com.example.telegrambackup.utils

import android.content.Context

/**
 * =====================================================================================
 * TELEGRAM API ENDPOINT CONFIGURATOR
 * =====================================================================================
 * Supports standard cloud Telegram API (api.telegram.org) as well as self-hosted
 * local Telegram Bot API servers (e.g. docker run telegram-bot-api on local network).
 *
 * Benefits of self-hosted local server:
 * - Increases single file upload limit from 50 MB to 2,000 MB (2 GB) without chunking
 * - Maximum LAN download/upload speeds with 0 Telegram Cloud rate limits
 * =====================================================================================
 */
object TelegramApiConfig {
    const val DEFAULT_TELEGRAM_API_BASE = "https://api.telegram.org"

    fun getBaseUrl(context: Context): String {
        val custom = AppPrefs(context).customBotApiUrl.trim()
        return if (custom.isNotBlank() && (custom.startsWith("http://") || custom.startsWith("https://"))) {
            custom.trimEnd('/')
        } else {
            DEFAULT_TELEGRAM_API_BASE
        }
    }

    fun isLocalServer(context: Context): Boolean {
        val base = getBaseUrl(context)
        return !base.contains("api.telegram.org")
    }

    /**
     * Maximum upload size supported by the current endpoint:
     * - Official Cloud: 50 MB
     * - Local Bot API Server: 2,000 MB (2 GB)
     */
    fun getMaxUploadBytes(context: Context): Long {
        return if (isLocalServer(context)) {
            2000L * 1024L * 1024L
        } else {
            50L * 1024L * 1024L
        }
    }

    /**
     * Builds standard Bot API method URL (e.g. sendDocument, getFile, getMe)
     */
    fun getMethodUrl(context: Context, botToken: String, method: String): String {
        val base = getBaseUrl(context)
        return "$base/bot${botToken.trim()}/$method"
    }

    /**
     * Builds direct file download streaming URL
     */
    fun getFileUrl(context: Context, botToken: String, filePath: String): String {
        val base = getBaseUrl(context)
        val cleanPath = filePath.trimStart('/')
        return "$base/file/bot${botToken.trim()}/$cleanPath"
    }
}
