package com.example.telegrambackup.utils

import android.content.Context
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

data class DuplicateCluster(
    val sizeBytes: Long,
    val files: List<UploadedFile>,
    val wastedBytes: Long = (files.size - 1).coerceAtLeast(0) * sizeBytes
)

object DuplicateFileManager {

    /**
     * Computes a fast byte-level fingerprint for a local file:
     * Hashes first 16KB + last 16KB + exact byte length.
     * Sub-millisecond execution time, immune to filename changes.
     */
    private fun computeFastFingerprint(file: File): String {
        return try {
            val length = file.length()
            if (length <= 0L) return ""
            val md = MessageDigest.getInstance("MD5")
            md.update(length.toString().toByteArray())

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(16 * 1024)
                // Read first 16KB
                val bytesReadHead = raf.read(buffer)
                if (bytesReadHead > 0) {
                    md.update(buffer, 0, bytesReadHead)
                }

                // If file is larger than 32KB, read last 16KB
                if (length > 32 * 1024L) {
                    raf.seek(length - (16 * 1024L))
                    val bytesReadTail = raf.read(buffer)
                    if (bytesReadTail > 0) {
                        md.update(buffer, 0, bytesReadTail)
                    }
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Normalizes a filename by stripping copy counters: " (1)", "_copy", " - copy", etc.
     */
    private fun normalizeFileName(name: String): String {
        val baseName = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val cleanBase = baseName
            .replace("\\s*\\(\\d+\\)$".toRegex(), "")
            .replace("_copy$".toRegex(), "")
            .replace("\\s*-\\s*copy$".toRegex(), "")
            .trim()
            .lowercase()
        return if (ext.isNotEmpty()) "$cleanBase.$ext" else cleanBase
    }

    suspend fun findDuplicates(context: Context): List<DuplicateCluster> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val allFiles = db.uploadedFileDao().getAllFiles().filter { !it.isDeleted && it.telegramFileId != "DIR_MARKER" }

        // Pass 1: Group by exact byte size (> 1024 bytes and > 1 file)
        val sizeMap = allFiles.groupBy { it.size }
            .filter { it.key > 1024L && it.value.size > 1 }

        val duplicateClusters = mutableListOf<DuplicateCluster>()

        for ((size, fileList) in sizeMap) {
            // Pass 2: Fingerprint local files if present; fallback to normalized name / telegramFileId
            val groupedMatches = fileList.groupBy { file ->
                val localFile = File(file.path)
                if (localFile.exists() && localFile.length() == size) {
                    val fp = computeFastFingerprint(localFile)
                    if (fp.isNotEmpty()) "fp:$fp" else "name:${normalizeFileName(file.fileName)}"
                } else if (file.telegramFileId.isNotBlank()) {
                    "tg:${file.telegramFileId}"
                } else {
                    "name:${normalizeFileName(file.fileName)}"
                }
            }

            for ((_, matches) in groupedMatches) {
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
                // 1. Delete from local Room database
                db.uploadedFileDao().deleteByPath(file.path)
                try {
                    db.chunkProgressDao().deleteChunksForFile(file.path)
                } catch (_: Exception) {}

                // 2. Cascade delete to Telegram Cloud Channel to prevent ghost chunks
                val messageIds = file.getAllMessageIds()
                if (messageIds.isNotEmpty()) {
                    val botToken = file.botTokenUsed.ifBlank { prefs.botToken }
                    val targetChatId = file.channelId.ifBlank { prefs.chatId }
                    if (botToken.isNotBlank() && targetChatId.isNotBlank()) {
                        serverApi.deleteTelegramMessagesBatch(botToken, targetChatId, messageIds)
                    }
                }

                // 3. Delete from backend database index
                val userEmail = prefs.userEmail.ifBlank { prefs.recoveryEmail }
                if (userEmail.isNotBlank() && file.telegramFileId.isNotBlank()) {
                    serverApi.deleteFile(prefs.serverUrl, userEmail, file.telegramFileId, file.path)
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

            // Prefer existing local file on disk
            try {
                if (File(file.path).exists()) {
                    score += 50
                }
            } catch (_: Exception) {}

            // Modest bonus for having timestamp
            if (file.timestamp > 0) {
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
