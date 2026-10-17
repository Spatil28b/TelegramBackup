package com.example.telegrambackup.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * =====================================================================================
 * SELECTIVE CLOUD FOLDER RESTORE & DIFF SYNC ENGINE (P4)
 * =====================================================================================
 * Directly restores a cloud folder hierarchy into local storage without ZIP wrapping.
 * Performs intelligent diff sync (skips existing identical files, overwrites corruptions).
 * =====================================================================================
 */
object FolderRestoreManager {
    private const val TAG = "FolderRestoreManager"

    data class RestoreSummary(
        val totalFiles: Int = 0,
        val restoredCount: Int = 0,
        val skippedCount: Int = 0,
        val failedCount: Int = 0,
        val destinationDir: File? = null
    )

    suspend fun restoreFolderTree(
        context: Context,
        folderFile: UploadedFile,
        onProgress: (current: Int, total: Int, fileName: String, percent: Float) -> Unit
    ): RestoreSummary = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val allFiles = try {
            db.uploadedFileDao().getAllRawFiles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load database files for folder restore", e)
            return@withContext RestoreSummary()
        }

        val prefix = if (folderFile.path.endsWith("/")) folderFile.path else "${folderFile.path}/"
        val folderChildren = allFiles.filter {
            it.path.startsWith(prefix) && it.telegramFileId != "DIR_MARKER" && !it.isDeleted && it.telegramFileId.isNotBlank()
        }

        if (folderChildren.isEmpty()) {
            return@withContext RestoreSummary(totalFiles = 0)
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val safeFolderName = folderFile.fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "_").ifBlank { "Restored_Folder" }
        val targetBaseDir = File(File(downloadsDir, "TelegramBackup"), safeFolderName).apply {
            if (!exists()) mkdirs()
        }

        var restored = 0
        var skipped = 0
        var failed = 0
        val total = folderChildren.size

        for ((index, child) in folderChildren.withIndex()) {
            val relativePath = child.path.removePrefix(prefix).trimStart('/')
            val destFile = File(targetBaseDir, relativePath)
            destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            val progressPct = if (total > 0) index.toFloat() / total.toFloat() else 0f
            onProgress(index + 1, total, child.fileName, progressPct)

            // Diff Sync Check: Skip file if it already exists locally and byte size matches
            if (destFile.exists() && destFile.length() > 0 && child.size > 0 && destFile.length() == child.size) {
                skipped++
                Log.d(TAG, "Diff sync: Skipped identical local file '${child.fileName}'")
                continue
            }

            try {
                val ok = TelegramMediaResolver.restoreFileToTarget(
                    context = context,
                    file = child,
                    targetFile = destFile,
                    onProgress = { /* chunk progress */ }
                )
                if (ok && destFile.exists() && destFile.length() > 0) {
                    restored++
                } else {
                    failed++
                    Log.w(TAG, "Failed restoring '${child.fileName}'")
                }
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "Error restoring '${child.fileName}'", e)
            }
        }

        onProgress(total, total, "Complete", 1.0f)
        val summary = RestoreSummary(
            totalFiles = total,
            restoredCount = restored,
            skippedCount = skipped,
            failedCount = failed,
            destinationDir = targetBaseDir
        )
        Log.d(TAG, "Folder restore completed: $summary")
        summary
    }
}
