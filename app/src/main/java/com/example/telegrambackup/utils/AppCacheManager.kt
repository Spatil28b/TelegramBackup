package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * =====================================================================================
 * APP CACHE & STORAGE OPTIMIZATION ENGINE
 * =====================================================================================
 * Manages device storage footprint, preventing runaway cache growth (e.g. 33 GB issues).
 * Enforces a user-configurable maximum cache budget (e.g. 512 MB or 1 GB) using an
 * intelligent Least-Recently-Used (LRU) auto-eviction policy and cleans transient chunk
 * files immediately after upload/download.
 * =====================================================================================
 */
object AppCacheManager {
    private const val TAG = "AppCacheManager"

    data class CacheStats(
        val totalBytes: Long,
        val previewsBytes: Long,
        val tempChunksBytes: Long,
        val archiveBytes: Long,
        val otherBytes: Long,
        val maxBudgetBytes: Long
    ) {
        val totalMB: Double get() = totalBytes / (1024.0 * 1024.0)
        val budgetMB: Double get() = maxBudgetBytes / (1024.0 * 1024.0)
        val usagePercentage: Float get() = if (maxBudgetBytes > 0) (totalBytes.toFloat() / maxBudgetBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getFolderSize(file) else file.length()
            }
        } catch (_: Exception) {}
        return size
    }

    /**
     * Computes the exact storage breakdown of all cache directories.
     */
    fun getCacheStats(context: Context): CacheStats {
        val prefs = AppPrefs(context)
        val cacheBase = context.cacheDir

        val previewDir = File(cacheBase, "cloud_preview_cache")
        val pdfWorkDir = File(cacheBase, "pdf_preview_work")
        val pdfThumbDir = File(cacheBase, "pdf_thumb_work")
        val sharedProviderDir = File(cacheBase, "shared_provider_cache")
        val backupTempDir = File(cacheBase, "backup_temp")
        val driveProcTempDir = File(cacheBase, "drive_proc_temp")
        val driveUploadTempDir = File(cacheBase, "drive_upload_temp")
        val driveFolderTempDir = File(cacheBase, "drive_folder_temp")
        val zipArchiveDir = File(cacheBase, "zip_archive_cache")
        val updatesDir = File(cacheBase, "updates")

        val previewsBytes = getFolderSize(previewDir) + getFolderSize(pdfWorkDir) + getFolderSize(pdfThumbDir) + getFolderSize(sharedProviderDir)
        val tempChunksBytes = getFolderSize(backupTempDir) + getFolderSize(driveProcTempDir) + 
                              getFolderSize(driveUploadTempDir) + getFolderSize(driveFolderTempDir)
        val archiveBytes = getFolderSize(zipArchiveDir) + getFolderSize(updatesDir)
        val totalBytes = getFolderSize(cacheBase)
        val otherBytes = (totalBytes - previewsBytes - tempChunksBytes - archiveBytes).coerceAtLeast(0L)

        val maxBudgetBytes = prefs.maxCacheSizeMB * 1024L * 1024L

        return CacheStats(
            totalBytes = totalBytes,
            previewsBytes = previewsBytes,
            tempChunksBytes = tempChunksBytes,
            archiveBytes = archiveBytes,
            otherBytes = otherBytes,
            maxBudgetBytes = maxBudgetBytes
        )
    }

    /**
     * Cleans stale transient chunk files and performs LRU pruning if cache exceeds the budget.
     */
    suspend fun autoPruneCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            val cacheBase = context.cacheDir
            val prefs = AppPrefs(context)
            val maxBudgetBytes = (prefs.maxCacheSizeMB.coerceAtLeast(128L)) * 1024L * 1024L
            val targetBudgetBytes = (maxBudgetBytes * 0.70).toLong() // Prune down to 70% of budget

            // 1. Purge stale transient temporary directories immediately
            val tempDirs = listOf(
                File(cacheBase, "backup_temp"),
                File(cacheBase, "drive_proc_temp"),
                File(cacheBase, "drive_upload_temp"),
                File(cacheBase, "drive_folder_temp"),
                File(cacheBase, "pdf_preview_work"),
                File(cacheBase, "pdf_thumb_work"),
                File(cacheBase, "shared_provider_cache"),
                File(cacheBase, "updates")
            )
            val now = System.currentTimeMillis()
            val fifteenMinutesAgo = now - (15 * 60 * 1000L)

            tempDirs.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.lastModified() < fifteenMinutesAgo) {
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        }
                    }
                }
            }

            // 2. Check preview cache and apply LRU eviction
            val previewDir = File(cacheBase, "cloud_preview_cache")
            val zipDir = File(cacheBase, "zip_archive_cache")

            var currentTotal = getFolderSize(cacheBase)
            if (currentTotal > maxBudgetBytes) {
                Log.d(TAG, "Cache size (${formatBytes(currentTotal)}) exceeds budget (${formatBytes(maxBudgetBytes)}). Performing LRU Pruning...")

                // Collect all preview files and sort by last modified ascending (oldest first)
                val candidateFiles = mutableListOf<File>()
                if (previewDir.exists()) previewDir.listFiles()?.let { candidateFiles.addAll(it) }
                if (zipDir.exists()) zipDir.listFiles()?.let { candidateFiles.addAll(it) }

                candidateFiles.sortBy { it.lastModified() }

                for (file in candidateFiles) {
                    if (currentTotal <= targetBudgetBytes) break
                    val len = file.length()
                    if (file.delete()) {
                        currentTotal -= len
                    }
                }
                Log.d(TAG, "LRU Pruning complete. New cache size: ${formatBytes(currentTotal)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "autoPruneCache failed", e)
        }
    }

    /**
     * Completely purges all cached preview media, zip archives, and temp files.
     * Returns the total amount of storage space freed in bytes.
     */
    suspend fun clearAllCache(context: Context): Long = withContext(Dispatchers.IO) {
        var reclaimedBytes = 0L
        try {
            val cacheBase = context.cacheDir
            reclaimedBytes = getFolderSize(cacheBase)

            val dirsToPurge = listOf(
                File(cacheBase, "cloud_preview_cache"),
                File(cacheBase, "pdf_preview_work"),
                File(cacheBase, "pdf_thumb_work"),
                File(cacheBase, "shared_provider_cache"),
                File(cacheBase, "backup_temp"),
                File(cacheBase, "drive_proc_temp"),
                File(cacheBase, "drive_upload_temp"),
                File(cacheBase, "drive_folder_temp"),
                File(cacheBase, "zip_archive_cache"),
                File(cacheBase, "updates"),
                File(cacheBase, "image_cache")
            )

            dirsToPurge.forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
            }

            // Also delete top-level temp files in cacheBase
            cacheBase.listFiles()?.forEach { f ->
                if (f.isFile && !f.name.startsWith("webview")) {
                    f.delete()
                }
            }

            Log.d(TAG, "Cleared all cache! Reclaimed ${formatBytes(reclaimedBytes)}")
        } catch (e: Exception) {
            Log.e(TAG, "clearAllCache error", e)
        }
        return@withContext reclaimedBytes
    }
}
