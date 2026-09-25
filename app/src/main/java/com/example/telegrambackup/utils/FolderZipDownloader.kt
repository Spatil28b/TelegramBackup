package com.example.telegrambackup.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
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
        val validFiles = files.filter { it.telegramFileId != "DIR_MARKER" && !it.isDeleted }
        if (validFiles.isEmpty()) return@withContext null

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFolder = File(downloadsDir, "TelegramBackup_Zips")
        if (!targetFolder.exists()) targetFolder.mkdirs()

        val safeName = folderName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val zipFile = File(targetFolder, "${safeName}_${System.currentTimeMillis()}.zip")

        try {
            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), 256 * 1024))
            zos.setLevel(Deflater.BEST_SPEED)
            val buffer = ByteArray(128 * 1024)
            val addedEntryNames = mutableSetOf<String>()
            val totalFiles = validFiles.size
            var successCount = 0

            coroutineScope {
                val prefetchMap = mutableMapOf<Int, Deferred<File?>>()
                fun queuePrefetch(index: Int) {
                    if (index !in validFiles.indices) return
                    val item = validFiles[index]
                    val local = FileViewerHelper.findLocalFile(item.path, item.fileName)
                        ?: if (item.path.isNotBlank() && File(item.path).exists()) File(item.path) else null
                    if (local != null && local.length() > 0) return

                    if (item.telegramFileId.isNotBlank() || item.chunksJson.isNotBlank() || item.totalChunks > 0) {
                        synchronized(prefetchMap) {
                            if (!prefetchMap.containsKey(index)) {
                                prefetchMap[index] = async(Dispatchers.IO) {
                                    try {
                                        TelegramMediaResolver.fetchToTempCache(context, item)
                                    } catch (_: Exception) { null }
                                }
                            }
                        }
                    }
                }

                for ((index, file) in validFiles.withIndex()) {
                    onProgress(index + 1, totalFiles, (index.toFloat() / totalFiles.toFloat()).coerceIn(0.05f, 0.98f))

                    // Lookahead prefetch next 2 items concurrently
                    queuePrefetch(index + 1)
                    queuePrefetch(index + 2)

                    try {
                        var resolvedFile: File? = FileViewerHelper.findLocalFile(file.path, file.fileName)
                        if (resolvedFile == null || !resolvedFile.exists() || resolvedFile.length() == 0L) {
                            if (file.path.isNotBlank()) {
                                val direct = File(file.path)
                                if (direct.exists() && direct.length() > 0L) resolvedFile = direct
                            }
                        }
                        if (resolvedFile == null) {
                            val prefetchedDeferred = synchronized(prefetchMap) { prefetchMap.remove(index) }
                            resolvedFile = prefetchedDeferred?.await() ?: TelegramMediaResolver.fetchToTempCache(context, file)
                        }

                        if (resolvedFile != null && resolvedFile.exists() && resolvedFile.length() > 0) {
                            FileEncryptor.ensureDecrypted(resolvedFile, context, file.fileName)

                            var uniqueName = file.fileName.ifBlank { "file_${index + 1}" }
                            var dup = 1
                            while (addedEntryNames.contains(uniqueName)) {
                                val ext = uniqueName.substringAfterLast('.', "")
                                val base = if (ext.isNotEmpty()) uniqueName.substringBeforeLast('.') else uniqueName
                                uniqueName = if (ext.isNotEmpty()) "${base}_($dup).$ext" else "${base}_($dup)"
                                dup++
                            }
                            addedEntryNames.add(uniqueName)

                            val entry = ZipEntry(uniqueName).apply {
                                time = if (file.timestamp > 946684800000L) file.timestamp else resolvedFile.lastModified()
                                size = resolvedFile.length()
                            }
                            zos.putNextEntry(entry)
                            FileInputStream(resolvedFile).use { fis ->
                                var bytesRead: Int
                                while (fis.read(buffer).also { bytesRead = it } != -1) {
                                    zos.write(buffer, 0, bytesRead)
                                }
                            }
                            zos.closeEntry()
                            successCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing zip entry for ${file.fileName}", e)
                    }
                }
            }

            zos.close()
            onProgress(totalFiles, totalFiles, 1.0f)
            if (successCount > 0 && zipFile.exists() && zipFile.length() > 0) {
                return@withContext zipFile
            } else {
                if (zipFile.exists()) zipFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating folder zip", e)
            if (zipFile.exists()) zipFile.delete()
            return@withContext null
        }
    }
}
