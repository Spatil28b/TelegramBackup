package com.example.telegrambackup.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FolderZipDownloader {
    private const val TAG = "FolderZipDownloader"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadFolderAsZip(
        context: Context,
        folderName: String,
        files: List<UploadedFile>,
        onProgress: (current: Int, total: Int, progress: Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext null

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFolder = File(downloadsDir, "TelegramBackup_Zips")
        if (!targetFolder.exists()) targetFolder.mkdirs()

        val safeName = folderName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val zipFile = File(targetFolder, "${safeName}_${System.currentTimeMillis()}.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val totalFiles = files.size
                files.forEachIndexed { index, file ->
                    onProgress(index + 1, totalFiles, (index.toFloat() / totalFiles.toFloat()).coerceIn(0.05f, 0.98f))

                    val downloadUrl = TelegramMediaResolver.resolveDirectUrl(context, file.telegramFileId.ifBlank { file.path })
                    if (downloadUrl != null) {
                        try {
                            val request = Request.Builder().url(downloadUrl).get().build()
                            val response = httpClient.newCall(request).execute()
                            if (response.isSuccessful) {
                                val bodyStream = response.body?.byteStream()
                                if (bodyStream != null) {
                                    val entry = ZipEntry(file.fileName.ifBlank { "file_${index + 1}" })
                                    zos.putNextEntry(entry)
                                    BufferedInputStream(bodyStream).use { bis ->
                                        val buffer = ByteArray(32 * 1024)
                                        var bytesRead: Int
                                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                                            zos.write(buffer, 0, bytesRead)
                                        }
                                    }
                                    zos.closeEntry()
                                }
                            }
                            response.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing zip entry for ${file.fileName}", e)
                        }
                    }
                }
            }
            onProgress(files.size, files.size, 1.0f)
            return@withContext zipFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating folder zip", e)
            if (zipFile.exists()) zipFile.delete()
            return@withContext null
        }
    }
}
