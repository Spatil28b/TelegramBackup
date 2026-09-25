package com.example.telegrambackup.utils

import android.content.Context
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DuplicateCluster(
    val sizeBytes: Long,
    val files: List<UploadedFile>,
    val wastedBytes: Long = (files.size - 1).coerceAtLeast(0) * sizeBytes
)

object DuplicateFileManager {

    suspend fun findDuplicates(context: Context): List<DuplicateCluster> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val allFiles = db.uploadedFileDao().getAllFiles()

        // Pass 1: Group by exact byte size
        val sizeMap = allFiles.groupBy { it.size }
            .filter { it.key > 1024L && it.value.size > 1 }

        val duplicateClusters = mutableListOf<DuplicateCluster>()

        for ((size, fileList) in sizeMap) {
            // Pass 2: Sub-group by matching filename or telegram file ID
            val subGroups = fileList.groupBy { it.fileName.lowercase() }
            for ((_, matches) in subGroups) {
                if (matches.size > 1) {
                    duplicateClusters.add(DuplicateCluster(sizeBytes = size, files = matches))
                }
            }
        }

        return@withContext duplicateClusters.sortedByDescending { it.wastedBytes }
    }

    suspend fun removeDuplicateCopies(
        context: Context,
        cluster: DuplicateCluster,
        keepIndex: Int = 0
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefs = AppPrefs(context)
        val filesToDelete = cluster.files.filterIndexed { index, _ -> index != keepIndex }

        val serverApi = ServerApi()
        for (file in filesToDelete) {
            try {
                db.uploadedFileDao().deleteByPath(file.path)
                if (prefs.userEmail.isNotBlank() && file.telegramFileId.isNotBlank()) {
                    serverApi.deleteFile(prefs.serverUrl, prefs.userEmail, file.telegramFileId, file.path)
                }
            } catch (_: Exception) {}
        }
    }

    fun findBestIndexToKeep(files: List<UploadedFile>): Int {
        if (files.isEmpty()) return 0
        var bestIndex = 0
        var bestScore = -100

        files.forEachIndexed { index, file ->
            var score = 0
            val pathLower = file.path.replace("\\", "/").lowercase()
            when {
                pathLower.contains("/dcim/") || pathLower.contains("/camera/") -> score += 100
                pathLower.contains("/pictures/") -> score += 60
                pathLower.contains("/documents/") -> score += 50
                pathLower.contains("/download/") || pathLower.contains("/downloads/") -> score += 30
                pathLower.contains("/telegram") || pathLower.contains("/whatsapp") -> score += 10
                else -> score += 20
            }

            // If timestamp exists, prefer earliest original creation
            if (file.timestamp > 0) {
                // Modest bonus for having timestamp
                score += 5
            }

            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return bestIndex
    }

    suspend fun cleanSelectedDuplicates(
        context: Context,
        clusters: List<DuplicateCluster>,
        keepMap: Map<Int, Int>
    ): Long = withContext(Dispatchers.IO) {
        var reclaimedBytes = 0L
        for ((idx, cluster) in clusters.withIndex()) {
            val keepIdx = keepMap[idx] ?: findBestIndexToKeep(cluster.files)
            val filesToDelete = cluster.files.filterIndexed { fIdx, _ -> fIdx != keepIdx }
            reclaimedBytes += (filesToDelete.size * cluster.sizeBytes)
            removeDuplicateCopies(context, cluster, keepIndex = keepIdx)
        }
        return@withContext reclaimedBytes
    }

    suspend fun autoCleanAllDuplicates(context: Context): Long = withContext(Dispatchers.IO) {
        val clusters = findDuplicates(context)
        var reclaimedBytes = 0L
        for (cluster in clusters) {
            val bestIdx = findBestIndexToKeep(cluster.files)
            reclaimedBytes += cluster.wastedBytes
            removeDuplicateCopies(context, cluster, keepIndex = bestIdx)
        }
        return@withContext reclaimedBytes
    }
}

