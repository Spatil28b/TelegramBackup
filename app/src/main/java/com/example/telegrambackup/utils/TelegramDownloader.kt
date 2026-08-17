package com.example.telegrambackup.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import com.example.telegrambackup.network.HttpClientProvider

class TelegramDownloader(private val context: Context) {
    private val client = HttpClientProvider.client

    suspend fun getDownloadUrl(botToken: String, fileId: String): String? = withContext(Dispatchers.IO) {
        if (botToken.isEmpty() || fileId.isEmpty()) return@withContext null
        try {
            val requestUrl = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
            val request = Request.Builder().url(requestUrl).build()
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: return@withContext null
            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                val filePath = json.optJSONObject("result")?.optString("file_path")
                if (!filePath.isNullOrEmpty()) {
                    return@withContext "https://api.telegram.org/file/bot$botToken/$filePath"
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramDownloader", "Error getting file URL", e)
        }
        return@withContext null
    }

    fun downloadFileWithManager(downloadUrl: String, fileName: String): Boolean {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(downloadUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("Downloading $fileName")
                setDescription("Fetching backup from Telegram...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            downloadManager.enqueue(request)
            true
        } catch (e: Exception) {
            Log.e("TelegramDownloader", "Failed to enqueue download", e)
            false
        }
    }
}
