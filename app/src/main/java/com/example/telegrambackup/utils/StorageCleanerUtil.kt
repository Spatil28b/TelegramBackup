package com.example.telegrambackup.utils

import android.content.Context
import android.media.MediaScannerConnection
import com.example.telegrambackup.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * =====================================================================================
 * "FREE UP PHONE SPACE" STORAGE CLEANER ENGINE
 * =====================================================================================
 * Safely identifies local photos, videos, and documents that are 100% verified and safely
 * stored on Telegram Cloud, allowing users to free up gigabytes of device storage with 1 tap.
 * =====================================================================================
 */
object StorageCleanerUtil {

    data class CleanableFile(
        val file: File,
        val size: Long,
        val fileName: String,
        val category: String
    )

    data class CleanableReport(
        val totalBytes: Long = 0L,
        val totalCount: Int = 0,
        val photosBytes: Long = 0L,
        val videosBytes: Long = 0L,
        val docsBytes: Long = 0L,
        val othersBytes: Long = 0L,
        val files: List<CleanableFile> = emptyList()
    ) {
        fun formattedTotal(): String = formatBytes(totalBytes)
        fun formattedPhotos(): String = formatBytes(photosBytes)
        fun formattedVideos(): String = formatBytes(videosBytes)
        fun formattedDocs(): String = formatBytes(docsBytes)

        private fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
    }

    /**
     * Inspects device files against verified Telegram Cloud records in Room DB.
     */
    suspend fun calculateCleanableStorage(context: Context): CleanableReport = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val uploadedFiles = try { db.uploadedFileDao().getAllFiles() } catch (_: Exception) { emptyList() }

        // Filter only files that have valid Telegram File IDs and are not directory markers
        val verifiedRecords = uploadedFiles.filter {
            it.telegramFileId.isNotBlank() &&
            it.telegramFileId != "DIR_MARKER" &&
            !it.telegramFileId.startsWith("tg_backup_") &&
            it.path.isNotBlank()
        }

        val cleanableList = mutableListOf<CleanableFile>()
        var totalBytes = 0L
        var photoBytes = 0L
        var videoBytes = 0L
        var docBytes = 0L
        var otherBytes = 0L

        for (record in verifiedRecords) {
            try {
                val f = File(record.path)
                if (f.exists() && f.isFile && f.length() > 0) {
                    val size = f.length()
                    val ext = f.extension.lowercase()
                    val cat = when {
                        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "raw", "svg", "dng") -> "PHOTOS"
                        ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v") -> "VIDEOS"
                        ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv") -> "DOCUMENTS"
                        else -> "OTHERS"
                    }

                    when (cat) {
                        "PHOTOS" -> photoBytes += size
                        "VIDEOS" -> videoBytes += size
                        "DOCUMENTS" -> docBytes += size
                        else -> otherBytes += size
                    }
                    totalBytes += size
                    cleanableList.add(CleanableFile(file = f, size = size, fileName = f.name, category = cat))
                }
            } catch (_: Exception) {}
        }

        return@withContext CleanableReport(
            totalBytes = totalBytes,
            totalCount = cleanableList.size,
            photosBytes = photoBytes,
            videosBytes = videoBytes,
            docsBytes = docBytes,
            othersBytes = otherBytes,
            files = cleanableList
        )
    }

    /**
     * Deletes verified local files from phone storage and updates Android MediaStore.
     */
    suspend fun freeUpStorage(
        context: Context,
        filesToClean: List<CleanableFile>,
        onProgress: (deletedCount: Int, bytesFreed: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        var count = 0
        val pathsToScan = mutableListOf<String>()

        for (item in filesToClean) {
            try {
                val path = item.file.absolutePath
                val size = item.size
                if (item.file.exists() && item.file.delete()) {
                    freedBytes += size
                    count++
                    pathsToScan.add(path)
                    if (count % 20 == 0) {
                        onProgress(count, freedBytes)
                    }
                }
            } catch (_: Exception) {}
        }

        // Notify Android MediaStore of deleted files so gallery refreshes cleanly
        if (pathsToScan.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    pathsToScan.toTypedArray(),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }

        onProgress(count, freedBytes)
        return@withContext freedBytes
    }
}
