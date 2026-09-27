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
        val prefs = AppPrefs(context)
        val chunkDao = db.chunkProgressDao()

        // 1. Proactive auto-heal: pull healed chunks from cloud if local Room records are missing them
        val userEmail = prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()
        if (userEmail.isNotBlank()) {
            try {
                val rawFiles = db.uploadedFileDao().getAllRawFiles()
                val missingChunks = rawFiles.filter { it.totalChunks > 1 && it.parseChunks().size < it.totalChunks }
                if (missingChunks.isNotEmpty()) {
                    val cloudFiles = ServerApi().listFiles(prefs.serverUrl, userEmail).getOrDefault(emptyList())
                    if (cloudFiles.isNotEmpty()) {
                        val cloudMap = cloudFiles.associateBy { it.path }
                        val toUpdate = mutableListOf<UploadedFile>()
                        for (localFile in missingChunks) {
                            val cloudMatch = cloudMap[localFile.path]
                                ?: cloudFiles.firstOrNull { it.fileName == localFile.fileName && it.size == localFile.size }
                            if (cloudMatch != null && cloudMatch.parseChunks().size > localFile.parseChunks().size) {
                                val updated = localFile.copy(
                                    chunksJson = cloudMatch.chunksJson,
                                    totalChunks = maxOf(localFile.totalChunks, cloudMatch.totalChunks),
                                    telegramFileId = if (cloudMatch.telegramFileId.isNotBlank()) cloudMatch.telegramFileId else localFile.telegramFileId
                                )
                                toUpdate.add(updated)
                                for (c in cloudMatch.parseChunks()) {
                                    try {
                                        chunkDao.insert(
                                            com.example.telegrambackup.data.ChunkProgressEntity(
                                                sourcePath = localFile.path,
                                                chunkIndex = c.first,
                                                totalChunks = updated.totalChunks,
                                                telegramFileId = c.second,
                                                messageId = 0L,
                                                chunkSize = 0L,
                                                status = "COMPLETED",
                                                botTokenUsed = c.third.ifBlank { localFile.botTokenUsed },
                                                channelId = localFile.channelId,
                                                uploadedAt = localFile.timestamp
                                            )
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        if (toUpdate.isNotEmpty()) {
                            db.uploadedFileDao().insertAll(toUpdate)
                            Log.d(TAG, "⚡ Auto-healed ${toUpdate.size} local files with complete cloud chunks!")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Proactive cloud chunk check skipped: ${e.message}")
            }
        }

        val files = db.uploadedFileDao().getAllRawFiles()
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
     * Bidirectional chunk sync: pulls complete chunks from cloud and pushes local chunks to cloud.
     */
    suspend fun syncLocalChunksToCloud(context: Context): Int = withContext(Dispatchers.IO) {
        var healed = 0
        try {
            val db = AppDatabase.getDatabase(context)
            val prefs = AppPrefs(context)
            val userEmail = prefs.userEmail.ifBlank { prefs.recoveryEmail }.trim().lowercase()
            val chunkDao = db.chunkProgressDao()
            val files = db.uploadedFileDao().getAllRawFiles()
            val toUpdate = mutableListOf<UploadedFile>()

            // 1. Pull from cloud for any files missing local chunks
            if (userEmail.isNotBlank()) {
                val cloudFiles = ServerApi().listFiles(prefs.serverUrl, userEmail).getOrDefault(emptyList())
                val cloudMap = cloudFiles.associateBy { it.path }
                for (file in files) {
                    if (file.totalChunks > 1 && (file.chunksJson.isBlank() || file.chunksJson == "[]")) {
                        val cloudMatch = cloudMap[file.path] ?: cloudFiles.firstOrNull { it.fileName == file.fileName && it.size == file.size }
                        if (cloudMatch != null && cloudMatch.parseChunks().isNotEmpty()) {
                            val updated = file.copy(
                                chunksJson = cloudMatch.chunksJson,
                                totalChunks = maxOf(file.totalChunks, cloudMatch.totalChunks),
                                telegramFileId = if (cloudMatch.telegramFileId.isNotBlank()) cloudMatch.telegramFileId else file.telegramFileId
                            )
                            toUpdate.add(updated)
                            for (c in cloudMatch.parseChunks()) {
                                try {
                                    chunkDao.insert(
                                        com.example.telegrambackup.data.ChunkProgressEntity(
                                            sourcePath = file.path,
                                            chunkIndex = c.first,
                                            totalChunks = updated.totalChunks,
                                            telegramFileId = c.second,
                                            messageId = 0L,
                                            chunkSize = 0L,
                                            status = "COMPLETED",
                                            botTokenUsed = c.third.ifBlank { file.botTokenUsed },
                                            channelId = file.channelId,
                                            uploadedAt = file.timestamp
                                        )
                                    )
                                } catch (_: Exception) {}
                            }
                            healed++
                        }
                    }
                }
            }

            // 2. Push from local chunk_upload_progress for any remaining files
            for (file in files) {
                if (file.totalChunks > 1 && (file.chunksJson.isBlank() || file.chunksJson == "[]") && toUpdate.none { it.path == file.path }) {
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
                if (userEmail.isNotBlank()) {
                    ServerApi().indexFilesBatch(prefs.serverUrl, userEmail, toUpdate)
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
