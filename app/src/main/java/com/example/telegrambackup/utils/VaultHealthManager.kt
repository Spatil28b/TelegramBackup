package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.worker.BackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class HealthCategory {
    ALL,
    INCOMPLETE_CHUNKS,
    DATE_ISSUES,
    HEALTHY
}

enum class FileHealthStatus {
    HEALTHY,
    INCOMPLETE_CHUNKS,
    MISSING_CLOUD_CHUNKS,
    DATE_DESYNC
}

data class FileHealthReport(
    val file: UploadedFile,
    val totalChunks: Int,
    val availableChunksCount: Int,
    val chunks: List<Triple<Int, String, String>>, // chunkIndex, fileId, botToken
    val isComplete: Boolean,
    val hasDateIssue: Boolean,
    val authenticDate: Long,
    val localFileExists: Boolean,
    val localFile: File?,
    val status: FileHealthStatus
)

data class VaultDiagnosticSummary(
    val totalFiles: Int,
    val healthyCount: Int,
    val incompleteChunksCount: Int,
    val dateIssueCount: Int,
    val resumableCount: Int,
    val reports: List<FileHealthReport>
)

object VaultHealthManager {
    private const val TAG = "VaultHealthManager"

    /**
     * Performs a comprehensive diagnostic scan across all files in Room DB,
     * cross-referencing local files on disk and chunk upload progress.
     */
    suspend fun diagnoseVault(context: Context): VaultDiagnosticSummary = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val files = db.uploadedFileDao().getAllRawFiles()
        val chunkDao = db.chunkProgressDao()

        val reports = mutableListOf<FileHealthReport>()
        var healthy = 0
        var incomplete = 0
        var dateIssues = 0
        var resumable = 0

        for (file in files) {
            val total = if (file.totalChunks > 0) file.totalChunks else 1
            var chunks = file.parseChunks()

            // If empty in UploadedFile, check local chunk_upload_progress table
            if (chunks.isEmpty() && total > 1) {
                val dbChunks = chunkDao.getUploadedChunksForFile(file.path)
                if (dbChunks.isNotEmpty()) {
                    chunks = dbChunks.map { Triple(it.chunkIndex, it.telegramFileId, it.botTokenUsed) }
                }
            }

            val isComplete = if (total > 1) chunks.size >= total else true
            val hasDateIssue = DateRecoveryHelper.isDateDesynced(file)
            val authenticDate = DateRecoveryHelper.getAuthenticTimestamp(file)

            val local = FileViewerHelper.findLocalFile(file.path, file.fileName)
            val localExists = local != null && local.exists() && local.length() > 0

            val status = when {
                !isComplete && chunks.isNotEmpty() -> FileHealthStatus.INCOMPLETE_CHUNKS
                !isComplete && chunks.isEmpty() -> FileHealthStatus.MISSING_CLOUD_CHUNKS
                hasDateIssue -> FileHealthStatus.DATE_DESYNC
                else -> FileHealthStatus.HEALTHY
            }

            if (status == FileHealthStatus.HEALTHY) {
                healthy++
            } else {
                if (!isComplete) {
                    incomplete++
                    if (localExists) resumable++
                }
                if (hasDateIssue) {
                    dateIssues++
                }
            }

            reports.add(
                FileHealthReport(
                    file = file,
                    totalChunks = total,
                    availableChunksCount = chunks.size,
                    chunks = chunks,
                    isComplete = isComplete,
                    hasDateIssue = hasDateIssue,
                    authenticDate = authenticDate,
                    localFileExists = localExists,
                    localFile = local,
                    status = status
                )
            )
        }

        VaultDiagnosticSummary(
            totalFiles = files.size,
            healthyCount = healthy,
            incompleteChunksCount = incomplete,
            dateIssueCount = dateIssues,
            resumableCount = resumable,
            reports = reports
        )
    }

    /**
     * Restores chunk metadata from local chunk_upload_progress into UploadedFile
     * and pushes complete mappings to the cloud database.
     */
    suspend fun syncLocalChunksToCloud(context: Context): Int = withContext(Dispatchers.IO) {
        var healed = 0
        try {
            val db = AppDatabase.getDatabase(context)
            val files = db.uploadedFileDao().getAllRawFiles()
            val chunkDao = db.chunkProgressDao()
            val toUpdate = mutableListOf<UploadedFile>()

            for (file in files) {
                if (file.totalChunks > 1 && (file.chunksJson.isBlank() || file.chunksJson == "[]")) {
                    val localChunks = chunkDao.getUploadedChunksForFile(file.path)
                    if (localChunks.isNotEmpty()) {
                        val arr = JSONArray()
                        localChunks.sortedBy { it.chunkIndex }.forEach { c ->
                            val obj = JSONObject().apply {
                                put("chunkIndex", c.chunkIndex)
                                put("telegramFileId", c.telegramFileId)
                                put("fileId", c.telegramFileId)
                                put("botTokenUsed", c.botTokenUsed)
                            }
                            arr.put(obj)
                        }
                        val updated = file.copy(chunksJson = arr.toString())
                        toUpdate.add(updated)
                        healed++
                    }
                }
            }

            if (toUpdate.isNotEmpty()) {
                db.uploadedFileDao().insertAll(toUpdate)
                val prefs = AppPrefs(context)
                if (prefs.userEmail.isNotBlank()) {
                    ServerApi().indexFilesBatch(prefs.serverUrl, prefs.userEmail, toUpdate)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncLocalChunksToCloud: ${e.message}")
        }
        healed
    }

    /**
     * Resumes upload for an incomplete file by triggering BackupWorker with immediate network constraint.
     */
    fun resumeUploadForFile(context: Context): Boolean {
        return try {
            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("VaultHealthResumeWork", ExistingWorkPolicy.KEEP, workRequest)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue resume work: ${e.message}")
            false
        }
    }
}
