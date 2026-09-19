package com.example.telegrambackup.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FolderZipDownloader {
    private const val TAG = "FolderZipDownloader"

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

                    try {
                        val decryptedFile = TelegramMediaResolver.fetchToTempCache(context, file)
                        if (decryptedFile != null && decryptedFile.exists() && decryptedFile.length() > 0) {
                            FileEncryptor.ensureDecrypted(decryptedFile, context, file.fileName)
                            val entry = ZipEntry(file.fileName.ifBlank { "file_${index + 1}" })
                            zos.putNextEntry(entry)
                            FileInputStream(decryptedFile).use { fis ->
                                val buffer = ByteArray(32 * 1024)
                                var bytesRead: Int
                                while (fis.read(buffer).also { bytesRead = it } != -1) {
                                    zos.write(buffer, 0, bytesRead)
                                }
                            }
                            zos.closeEntry()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing zip entry for ${file.fileName}", e)
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
