package com.example.telegrambackup.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.telegrambackup.R
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * =====================================================================================
 * MONTHLY ZIP BACKUP DOWNLOADER & RESUMABLE ARCHIVER
 * =====================================================================================
 * Utility responsible for downloading monthly cloud backups from Telegram, assembling
 * chunked file parts (even multi-gigabyte files), and packaging them into clean `.zip`
 * archives directly saved into the user's Downloads directory (`/sdcard/Download/`).
 *
 * Resumability & Integrity Protections:
 * 1. Persistent Download Cache (`zip_archive_cache/`):
 *    Already downloaded files/chunks are preserved in cache. Re-launching download
 *    resumes from the last un-downloaded file instead of starting from scratch.
 * 2. Multi-Part Chunk Assembly:
 *    Fetches all sequential chunk parts for large files and merges them via `FileChunker`
 *    before writing to the ZIP stream.
 * 3. 500 MB Split Part Safeguard:
 *    Automatically splits ZIP files exceeding 500 MB into `_part1.zip`, `_part2.zip`, etc.
 * =====================================================================================
 */
object ZipBackupDownloader {
    private const val TAG = "ZipBackupDownloader"
    private const val MAX_ZIP_PART_SIZE = 500L * 1024L * 1024L // 500 MB per ZIP part

    suspend fun downloadFilesAsZip(
        context: Context,
        filesToDownload: List<UploadedFile>,
        archiveTitle: String
    ) = withContext(Dispatchers.IO) {
        if (filesToDownload.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No files selected for ZIP download", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val prefs = AppPrefs(context)
        val botToken = prefs.botToken
        val telegramDownloader = TelegramDownloader(context)

        val db = AppDatabase.getDatabase(context)
        val chunkDao = db.chunkProgressDao()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zip_download_channel"
        val notificationId = 8801

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Archive Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for Monthly Archive ZIP downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notifBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📦 Creating Archive: $archiveTitle")
            .setContentText("Preparing ${filesToDownload.size} files...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(filesToDownload.size, 0, false)

        notificationManager.notify(notificationId, notifBuilder.build())

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val sanitizeName = archiveTitle.replace(" ", "_").replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val persistentCacheDir = File(context.cacheDir, "zip_archive_cache/$sanitizeName")
        if (!persistentCacheDir.exists()) persistentCacheDir.mkdirs()

        val targetZipFile = File(downloadsDir, "$sanitizeName.zip")

        try {
            var currentPartIndex = 1
            var activeZipFile = if (targetZipFile.exists()) File(downloadsDir, "${sanitizeName}_${System.currentTimeMillis()}.zip") else targetZipFile
            var currentZipSize = 0L

            var zipOutputStream = ZipOutputStream(FileOutputStream(activeZipFile))
            val buffer = ByteArray(32768)

            var successCount = 0

            for ((idx, item) in filesToDownload.withIndex()) {
                notifBuilder.setContentText("Processing ${idx + 1}/${filesToDownload.size}: ${item.fileName}")
                notifBuilder.setProgress(filesToDownload.size, idx + 1, false)
                notificationManager.notify(notificationId, notifBuilder.build())

                var fileToCompress: File? = null

                // Step 1: Check if local source file exists and is readable
                if (item.path.isNotEmpty()) {
                    val localFile = File(item.path)
                    if (localFile.exists() && localFile.length() > 0) {
                        fileToCompress = localFile
                    }
                }

                // Step 2: Check if file was already cached in persistent cache
                val cachedFile = File(persistentCacheDir, item.fileName.ifEmpty { "file_${idx + 1}" })
                if (fileToCompress == null && cachedFile.exists() && cachedFile.length() > 0) {
                    fileToCompress = cachedFile
                    Log.d(TAG, "⚡ Resuming from Cache: ${item.fileName}")
                }

                // Step 3: If missing from cache & local storage, download from Telegram Cloud
                if (fileToCompress == null && item.telegramFileId.isNotEmpty() && botToken.isNotEmpty()) {
                    val totalChunks = item.totalChunks
                    if (totalChunks > 1) {
                        // Multi-part chunked file: fetch all chunk parts from DB / Telegram
                        val chunkParts = mutableListOf<File>()
                        var allChunksOk = true

                        for (cIdx in 1..totalChunks) {
                            val chunkCacheFile = File(persistentCacheDir, "${item.fileName}.part%03d".format(cIdx))
                            if (chunkCacheFile.exists() && chunkCacheFile.length() > 0) {
                                chunkParts.add(chunkCacheFile)
                                continue
                            }

                            // Fetch chunk record from Room DB
                            val chunkRecord = chunkDao.getChunk(item.path, cIdx)
                            val fileIdToFetch = chunkRecord?.telegramFileId ?: if (cIdx == 1) item.telegramFileId else ""

                            if (fileIdToFetch.isNotEmpty()) {
                                val downloadUrl = telegramDownloader.getDownloadUrl(botToken, fileIdToFetch)
                                if (downloadUrl != null) {
                                    val ok = downloadDirectToFile(downloadUrl, chunkCacheFile)
                                    if (ok) {
                                        chunkParts.add(chunkCacheFile)
                                    } else {
                                        allChunksOk = false
                                        break
                                    }
                                } else {
                                    allChunksOk = false
                                    break
                                }
                            } else {
                                allChunksOk = false
                                break
                            }
                        }

                        if (allChunksOk && chunkParts.size == totalChunks) {
                            // Merge all chunks into single file
                            FileChunker.mergeChunks(chunkParts, cachedFile)
                            if (cachedFile.exists() && cachedFile.length() > 0) {
                                fileToCompress = cachedFile
                            }
                        }
                    } else {
                        // Single-file download from Telegram Cloud
                        val downloadUrl = telegramDownloader.getDownloadUrl(botToken, item.telegramFileId)
                        if (downloadUrl != null) {
                            val success = downloadDirectToFile(downloadUrl, cachedFile)
                            if (success) {
                                fileToCompress = cachedFile
                            }
                        }
                    }
                }

                if (fileToCompress != null && fileToCompress.exists()) {
                    val fileSize = fileToCompress.length()

                    // Check if current zip part exceeds 500 MB threshold -> split into part files
                    if (currentZipSize + fileSize > MAX_ZIP_PART_SIZE && currentZipSize > 0) {
                        zipOutputStream.close()
                        currentPartIndex++
                        activeZipFile = File(downloadsDir, "${sanitizeName}_part$currentPartIndex.zip")
                        zipOutputStream = ZipOutputStream(FileOutputStream(activeZipFile))
                        currentZipSize = 0L
                    }

                    try {
                        val entryName = item.fileName.ifEmpty { fileToCompress.name }
                        val zipEntry = ZipEntry(entryName)
                        zipOutputStream.putNextEntry(zipEntry)

                        FileInputStream(fileToCompress).use { fis ->
                            var read: Int
                            while (fis.read(buffer).also { read = it } != -1) {
                                zipOutputStream.write(buffer, 0, read)
                            }
                        }
                        zipOutputStream.closeEntry()
                        currentZipSize += fileSize
                        successCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing ${item.fileName} to ZIP", e)
                    }
                }
            }

            zipOutputStream.close()

            // Remove persistent cache directory on full archive completion
            try { persistentCacheDir.listFiles()?.forEach { it.delete() }; persistentCacheDir.delete() } catch (_: Exception) {}

            // Success notification
            notifBuilder
                .setContentTitle("✅ ZIP Download Complete!")
                .setContentText("Saved $successCount files to Downloads/$sanitizeName.zip")
                .setOngoing(false)
                .setProgress(0, 0, false)

            notificationManager.notify(notificationId, notifBuilder.build())

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✅ Downloaded $successCount files to Downloads folder!", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ZIP archive", e)
            notificationManager.cancel(notificationId)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "❌ Error creating ZIP: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadDirectToFile(url: String, targetFile: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            val response = HttpClientProvider.uploadClient.newCall(request).execute()
            if (response.isSuccessful) {
                val inputStream = response.body?.byteStream() ?: return false
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                return targetFile.exists() && targetFile.length() > 0
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading direct file", e)
            false
        }
    }
}
