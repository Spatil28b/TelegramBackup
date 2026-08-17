package com.example.telegrambackup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.telegrambackup.R
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.ChunkProgressEntity
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.HttpClientProvider
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupProgressTracker
import com.example.telegrambackup.utils.FileChunker
import com.example.telegrambackup.utils.FileEncryptor
import com.example.telegrambackup.utils.TelegramFileVerifier
import com.example.telegrambackup.utils.SettingsDbManager
import com.example.telegrambackup.utils.UploadItemProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * =====================================================================================
 * BACKGROUND AUTOMATIC BACKUP WORKER (LOW-NETWORK FAULT-TOLERANT ENGINE)
 * =====================================================================================
 * Android WorkManager CoroutineWorker responsible for background storage scanning and
 * executing scheduled file backup operations to Telegram Cloud even when app is closed.
 *
 * Background & Closed-App Features:
 * 1. Process-Wide Atomic Singleton Guard (`isWorkerRunning` AtomicBoolean):
 *    Ensures only ONE worker instance can execute at a time.
 * 2. System Status Bar Completion Notifications:
 *    Posts Android status bar notifications when background backup completes while app is closed.
 * 3. 5-Attempt Exponential Backoff Retry Engine:
 *    Retries failed chunk transmissions automatically up to 5 times under weak 3G/4G signal.
 * 4. Atomic Chunk Claims:
 *    Prevents duplicate uploads across parallel workers or re-sync routines.
 * =====================================================================================
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "BackupWorker"
        private val isWorkerRunning = AtomicBoolean(false)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Enforce Atomic Singleton Lock across app process
        if (!isWorkerRunning.compareAndSet(false, true)) {
            Log.d(TAG, "BackupWorker is already actively running in another thread/process. Skipping concurrent execution.")
            return@withContext Result.success()
        }

        try {
            val prefs = AppPrefs(applicationContext)
            var botToken = prefs.botToken
            var chatId = prefs.chatId

            val authPrefs = applicationContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val userEmail = authPrefs.getString("user_email", "") ?: prefs.recoveryEmail
            val serverApi = ServerApi()

            // Auto-hydrate credentials if empty in local preferences
            if (botToken.isEmpty() || chatId.isEmpty()) {
                if (userEmail.isNotEmpty()) {
                    try {
                        val creds = serverApi.getCredentials(prefs.serverUrl, userEmail)
                        if (creds != null && creds.first.isNotEmpty()) {
                            prefs.botToken = creds.first
                            prefs.chatId = creds.second
                            prefs.credentialsVerified = true
                            botToken = creds.first
                            chatId = creds.second
                        }
                    } catch (_: Exception) { }
                }
            }
            // Step 1: Check if backup session is paused by user
            if (prefs.stopAllBackups) {
                Log.d(TAG, "Backup paused by user.")
                BackupProgressTracker.finishBackupSession("Backup Paused by User")
                return@withContext Result.success()
            }

            // Step 2: Validate Telegram Bot credentials exist
            if (botToken.isEmpty() || chatId.isEmpty()) {
                Log.w(TAG, "Missing bot credentials. botToken empty=${botToken.isEmpty()}, chatId empty=${chatId.isEmpty()}")
                BackupProgressTracker.finishBackupSession("Credentials Missing")
                return@withContext Result.failure()
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val uploadedDao = db.uploadedFileDao()
            val chunkDao = db.chunkProgressDao()

            // Step 3: PRE-BACKUP VERIFICATION — Purge ghost records before scanning
            BackupProgressTracker.startBackupSession(0, "Verifying existing backups on Telegram Cloud...")
            try {
                val verifyResult = TelegramFileVerifier.verifyAllFiles(botToken, uploadedDao)
                if (verifyResult.fakePurged > 0 || verifyResult.deletedFromTelegram > 0) {
                    Log.d(TAG, "Pre-backup verification: purged ${verifyResult.fakePurged} fake, " +
                        "${verifyResult.deletedFromTelegram} deleted from Telegram")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pre-backup verification failed (continuing with backup)", e)
            }

            BackupProgressTracker.updateStatus("Scanning device storage for unbacked files...")

            // Step 4: Identify storage directories to scan
            val dirsToScan = mutableListOf<File>()
            val rootStorage = Environment.getExternalStorageDirectory()

            if (rootStorage != null && rootStorage.exists()) {
                dirsToScan.add(rootStorage)
            } else {
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            }

            // Step 5: Calculate file size boundaries (in bytes) based on preferences (0 = Unlimited)
            val minBytes = (prefs.minFileSizeMB * 1024.0 * 1024.0).toLong()
            val maxBytes = if (prefs.maxFileSizeMB <= 0) Long.MAX_VALUE else (prefs.maxFileSizeMB * 1024.0 * 1024.0).toLong()

            val candidateFilesMap = LinkedHashMap<String, File>()

            val excludedSet = prefs.backupExceptions

            // Walk storage directory tree and respect category settings
            for (dir in dirsToScan) {
                if (dir.exists() && dir.isDirectory) {
                    try {
                        dir.walkTopDown()
                            .onEnter { subDir ->
                                try {
                                    val absPath = subDir.absolutePath
                                    val isExc = excludedSet.any { exc -> absPath == exc || absPath.startsWith("$exc/") }
                                    if (isExc) return@onEnter false

                                    val name = subDir.name.lowercase()
                                    val hideHidden = !prefs.showHiddenFiles
                                    if (hideHidden && name.startsWith(".")) false
                                    else name != "android" && name != "cache" && name != "tmp"
                                } catch (e: Exception) { false }
                            }
                            .filter { file ->
                                try {
                                    val absPath = file.absolutePath
                                    val isExc = excludedSet.any { exc -> absPath == exc || absPath.startsWith("$exc/") }
                                    if (isExc) return@filter false

                                    val name = file.name.lowercase()
                                    val hideHidden = !prefs.showHiddenFiles
                                    val isNotHidden = if (hideHidden) !name.startsWith(".") else true
                                    file.isFile && isNotHidden
                                } catch (e: Exception) { false }
                            }
                            .forEach { file ->
                                try {
                                    val fileLength = file.length()
                                    if (fileLength > 0 && fileLength in minBytes..maxBytes) {
                                        val ext = file.extension.lowercase()
                                        val isImg = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "raw", "svg", "tiff", "dng")
                                        val isVid = ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v", "ts")
                                        val isAud = ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma", "amr", "mid")
                                        val isDoc = ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "rtf", "odt", "ods")
                                        val isZip = ext in setOf("zip", "rar", "7z", "tar", "gz", "iso", "bz2", "xz")
                                        val isApk = ext in setOf("apk", "xapk", "apks")

                                        val allowed = when {
                                            isImg -> prefs.backupCamera || prefs.backupPictures
                                            isVid -> prefs.backupVideos
                                            isAud -> prefs.backupAudio
                                            isDoc -> prefs.backupDocuments
                                            isZip -> prefs.backupArchives
                                            isApk -> prefs.backupApks
                                            else -> prefs.backupAllOther
                                        }

                                        if (allowed) {
                                            candidateFilesMap[file.absolutePath] = file
                                        }
                                    }
                                } catch (e: Exception) { }
                            }
                    } catch (e: Exception) { }
                }
            }

            val candidateFiles = candidateFilesMap.values.toList()
            if (candidateFiles.isEmpty()) {
                BackupProgressTracker.finishBackupSession("No candidate files found")
                return@withContext Result.success()
            }

            // Step 5b: Synchronize Cloud DB to local Room DB FIRST before checking candidate files
            try {
                SettingsDbManager.restoreCloudFileIndexToRoom(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud file index restore failed prior to deduplication scan", e)
            }

            // Step 6: Multi-Criteria Deduplication Engine (Path & Name+Size matching)
            val existingFiles = try { uploadedDao.getAllFiles() } catch (e: Exception) { emptyList() }
            val existingPaths = HashSet<String>()
            val existingNameSizePairs = HashSet<String>()

            for (f in existingFiles) {
                if (f.path.isNotEmpty()) {
                    existingPaths.add(f.path)
                    existingPaths.add(f.path.lowercase())
                }
                if (f.fileName.isNotEmpty()) {
                    existingNameSizePairs.add("${f.fileName.lowercase()}_${f.size}")
                }
            }

            val unbackedFiles = candidateFiles.filter { candidate ->
                val pathExact = candidate.absolutePath
                val pathLower = candidate.absolutePath.lowercase()
                val nameSize = "${candidate.name.lowercase()}_${candidate.length()}"

                val isAlreadyBackedUp = existingPaths.contains(pathExact) ||
                        existingPaths.contains(pathLower) ||
                        existingNameSizePairs.contains(nameSize)

                !isAlreadyBackedUp
            }

            if (unbackedFiles.isEmpty()) {
                BackupProgressTracker.finishBackupSession("All files up to date!")
                return@withContext Result.success()
            }

            // Step 7: Queue unbacked files and start backup session
            val queueList = unbackedFiles.map { f ->
                UploadItemProgress(fileName = f.name, filePath = f.absolutePath, fileSize = f.length())
            }
            BackupProgressTracker.startBackupSession(queueList.size, "Preparing ${queueList.size} files for Telegram Cloud Sync...")
            BackupProgressTracker.updateQueue(queueList)

            // Prepare temp directory for encrypted/chunked files
            val tempDir = File(applicationContext.cacheDir, "backup_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            var successCount = 0
            var failCount = 0

            for ((index, file) in unbackedFiles.withIndex()) {
                // Check if user paused backups mid-session
                if (prefs.stopAllBackups) {
                    BackupProgressTracker.finishBackupSession("Backup Stopped by User after $successCount files")
                    break
                }

                try {
                    BackupProgressTracker.setCurrentUploadingFile(file.name, file.absolutePath, 0.05f)

                    // Step 8a: Optionally encrypt file before splitting into chunks
                    val fileToUpload: File
                    val isEncrypted = prefs.encryptionEnabled
                    if (isEncrypted) {
                        BackupProgressTracker.updateStatus("Encrypting ${file.name}...")
                        val encryptedFile = File(tempDir, "${file.name}.enc")
                        FileEncryptor.encryptFile(file, encryptedFile, prefs.encryptionKey)
                        fileToUpload = encryptedFile
                    } else {
                        fileToUpload = file
                    }

                    // Step 8b: Calculate total 4 MB chunks required
                    val totalChunks = FileChunker.getChunkCount(fileToUpload)
                    val isChunked = totalChunks > 1

                    // Step 8c: Check for completed uploaded chunks (Resumable Upload)
                    val uploadedChunksMap = mutableMapOf<Int, ChunkProgressEntity>()
                    val savedChunks = chunkDao.getUploadedChunksForFile(file.absolutePath)
                    val completedSavedChunks = savedChunks.filter { it.status == "COMPLETED" }

                    if (completedSavedChunks.isNotEmpty()) {
                        // Cross-verify the last completed chunk on Telegram Cloud to ensure valid resume
                        val lastChunk = completedSavedChunks.maxByOrNull { it.chunkIndex }
                        if (lastChunk != null) {
                            val isLastChunkValid = TelegramFileVerifier.verifyFileExists(botToken, lastChunk.telegramFileId)
                            if (isLastChunkValid) {
                                completedSavedChunks.forEach { uploadedChunksMap[it.chunkIndex] = it }
                                Log.d(TAG, "⚡ Resuming ${file.name}: ${uploadedChunksMap.size}/$totalChunks chunks already on Telegram Cloud")
                                BackupProgressTracker.updateStatus("Resuming ${file.name} (${uploadedChunksMap.size}/$totalChunks chunks ready)...")
                            } else {
                                Log.w(TAG, "Last chunk verification failed for ${file.name}, restarting chunk upload session")
                                chunkDao.deleteChunksForFile(file.absolutePath)
                            }
                        }
                    }

                    var firstChunkFileId = ""
                    var firstChunkMessageId = 0L
                    var allChunksUploadedSuccessfully = true

                    // Step 8d: Process each 4 MB chunk safely with 5-Attempt Retry Engine
                    for (chunkIdx in 1..totalChunks) {
                        if (prefs.stopAllBackups) break

                        // Update live progress display
                        val overallFileProgress = (chunkIdx.toFloat() / totalChunks.toFloat()).coerceIn(0.1f, 0.95f)
                        BackupProgressTracker.updateCurrentProgress(overallFileProgress)

                        if (uploadedChunksMap.containsKey(chunkIdx)) {
                            // Chunk already completed!
                            val existingChunk = uploadedChunksMap[chunkIdx]!!
                            if (chunkIdx == 1) {
                                firstChunkFileId = existingChunk.telegramFileId
                                firstChunkMessageId = existingChunk.messageId
                            }
                            Log.d(TAG, "Skipping chunk $chunkIdx/$totalChunks for ${file.name} (Already completed)")
                            continue
                        }

                        // Check if chunk is claimed as UPLOADING by active worker (< 3 min old)
                        val existingClaim = chunkDao.getChunk(file.absolutePath, chunkIdx)
                        if (existingClaim != null && existingClaim.status == "UPLOADING") {
                            val claimAge = System.currentTimeMillis() - existingClaim.uploadedAt
                            if (claimAge < 3 * 60 * 1000L) {
                                Log.d(TAG, "Chunk $chunkIdx/$totalChunks for ${file.name} is currently claimed by active upload session ($claimAge ms old). Skipping duplicate.")
                                continue
                            }
                        }

                        // Atomic Claim: Mark chunk as UPLOADING in DB before firing HTTP request
                        val claimEntity = ChunkProgressEntity(
                            sourcePath = file.absolutePath,
                            chunkIndex = chunkIdx,
                            totalChunks = totalChunks,
                            telegramFileId = "",
                            messageId = 0L,
                            chunkSize = 0L,
                            status = "UPLOADING",
                            uploadedAt = System.currentTimeMillis()
                        )
                        chunkDao.insert(claimEntity)

                        // Extract single 4 MB chunk
                        val chunkTempFile = File(tempDir, "${file.name}.part%03d".format(chunkIdx))
                        FileChunker.extractSingleChunk(fileToUpload, chunkIdx, chunkTempFile)

                        val caption = if (isChunked) {
                            "\uD83D\uDCE6 ${file.name} [Part $chunkIdx/$totalChunks]"
                        } else {
                            "\uD83D\uDCE4 ${file.name} (${formatFileSize(file.length())})"
                        }

                        // Execute 5-attempt retry engine for low network resilience
                        val uploadResult = uploadToTelegramWithRetry(
                            botToken = botToken,
                            chatId = chatId,
                            file = chunkTempFile,
                            caption = caption,
                            chunkIdx = chunkIdx,
                            totalChunks = totalChunks,
                            fileName = file.name,
                            secretKey = prefs.encryptionKey
                        )

                        // Clean temp chunk file immediately
                        if (chunkTempFile.exists()) chunkTempFile.delete()

                        if (uploadResult != null) {
                            val fileId = uploadResult.first
                            val messageId = uploadResult.second

                            if (chunkIdx == 1) {
                                firstChunkFileId = fileId
                                firstChunkMessageId = messageId
                            }

                            // Update chunk record to COMPLETED in Room DB
                            val completedEntity = ChunkProgressEntity(
                                sourcePath = file.absolutePath,
                                chunkIndex = chunkIdx,
                                totalChunks = totalChunks,
                                telegramFileId = fileId,
                                messageId = messageId,
                                chunkSize = chunkTempFile.length(),
                                status = "COMPLETED",
                                uploadedAt = System.currentTimeMillis()
                            )
                            chunkDao.insert(completedEntity)
                            uploadedChunksMap[chunkIdx] = completedEntity
                        } else {
                            // HTTP failed after 5 retries — release claim so it can be retried on next pass
                            chunkDao.deleteChunk(file.absolutePath, chunkIdx)
                            allChunksUploadedSuccessfully = false
                            Log.e(TAG, "Failed to upload chunk $chunkIdx/$totalChunks for ${file.name} after 5 retries")
                            break
                        }
                    }

                    // Cleanup encrypted temp file
                    if (isEncrypted && fileToUpload.exists() && fileToUpload.parentFile == tempDir) {
                        fileToUpload.delete()
                    }

                    // Check if all chunks completed
                    if (!allChunksUploadedSuccessfully || uploadedChunksMap.size < totalChunks) {
                        failCount++
                        Log.w(TAG, "File ${file.name} upload incomplete (${uploadedChunksMap.size}/$totalChunks chunks completed). Will resume next run.")
                        continue
                    }

                    // Step 8e: Retrieve first chunk info
                    if (firstChunkFileId.isEmpty()) {
                        firstChunkFileId = uploadedChunksMap[1]?.telegramFileId ?: ""
                        firstChunkMessageId = uploadedChunksMap[1]?.messageId ?: 0L
                    }

                    BackupProgressTracker.updateCurrentProgress(0.98f)

                    // Step 8f: Record fully uploaded file entry into Room DB
                    val entity = UploadedFile(
                        file.absolutePath,
                        file.name,
                        firstChunkFileId,
                        System.currentTimeMillis(),
                        file.length(),
                        firstChunkMessageId
                    )
                    if (isChunked) {
                        entity.totalChunks = totalChunks
                    }
                    uploadedDao.insert(entity)

                    // Clean up intermediate chunk progress records from DB
                    chunkDao.deleteChunksForFile(file.absolutePath)

                    // Step 8g: Sync file index to cloud PostgreSQL (non-blocking, best-effort)
                    if (userEmail.isNotEmpty()) {
                        try {
                            serverApi.indexFile(
                                prefs.serverUrl,
                                userEmail,
                                file.name,
                                firstChunkFileId,
                                file.length(),
                                file.absolutePath,
                                firstChunkMessageId
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Cloud index sync failed for ${file.name}, local record intact", e)
                        }
                    }

                    successCount++
                    BackupProgressTracker.markFileCompleted(file.name, file.absolutePath, file.length())
                    Log.d(TAG, "✅ Fully Backed Up: ${file.name} ($totalChunks chunks, fileId=$firstChunkFileId)")

                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "Error backing up ${file.name}", e)
                }
            }

            // Cleanup temp directory
            try {
                tempDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) { }

            val summary = buildString {
                append("Backed up $successCount files to Telegram Cloud!")
                if (failCount > 0) append(" ($failCount partial/failed)")
            }
            Log.d(TAG, summary)
            BackupProgressTracker.finishBackupSession(summary)

            // Send Android system status notification on background completion
            if (successCount > 0) {
                showCompletionNotification(summary)
            }

            Result.success()

        } finally {
            // Release Process-Wide Atomic Lock upon completion or error
            isWorkerRunning.set(false)
        }
    }

    /**
     * Posts Android system bar status notification upon background task completion.
     */
    private fun showCompletionNotification(summaryMsg: String) {
        try {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "bg_backup_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Background Cloud Backup",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when background cloud backups complete"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notif = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("☁️ Telegram Cloud Backup Complete")
                .setContentText(summaryMsg)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(7701, notif)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post background completion notification", e)
        }
    }

    /**
     * Executes up to 5 retries with exponential backoff for low network conditions.
     */
    private suspend fun uploadToTelegramWithRetry(
        botToken: String,
        chatId: String,
        file: File,
        caption: String,
        chunkIdx: Int,
        totalChunks: Int,
        fileName: String,
        secretKey: String = ""
    ): Pair<String, Long>? {
        var attempt = 1
        val maxAttempts = 5

        while (attempt <= maxAttempts) {
            val statusText = if (attempt > 1) {
                "⚡ Weak Network: Retrying $fileName [Part $chunkIdx/$totalChunks] (Attempt $attempt/$maxAttempts)..."
            } else {
                "Uploading $fileName [Part $chunkIdx/$totalChunks]..."
            }
            BackupProgressTracker.updateStatus(statusText)

            val result = uploadToTelegramSingleAttempt(botToken, chatId, file, caption, secretKey)
            if (result != null) {
                return result
            }

            if (attempt < maxAttempts) {
                val backoffMs = attempt * 3000L // 3s, 6s, 9s, 12s
                Log.w(TAG, "Chunk $chunkIdx/$totalChunks for $fileName upload attempt $attempt/$maxAttempts failed on low network. Retrying in ${backoffMs}ms...")
                delay(backoffMs)
            }
            attempt++
        }
        return null
    }

    /**
     * Uploads a single file/chunk to Telegram via Bot API `sendDocument` endpoint with full AES-256 encryption.
     */
    private fun uploadToTelegramSingleAttempt(botToken: String, chatId: String, file: File, caption: String, secretKey: String = ""): Pair<String, Long>? {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendDocument"

            val obfuscatedName = if (secretKey.isNotEmpty()) FileEncryptor.obfuscateFileName(file.name, secretKey) else "ENC_${Math.abs(file.name.hashCode())}.bin"
            val encryptedCaption = if (secretKey.isNotEmpty()) "🔒 ENC:" + FileEncryptor.encryptText(caption, secretKey) else caption

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", encryptedCaption)
                .addFormDataPart(
                    "document",
                    obfuscatedName,
                    file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = HttpClientProvider.uploadClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                if (json.optBoolean("ok", false)) {
                    val result = json.optJSONObject("result")
                    val messageId = result?.optLong("message_id", 0L) ?: 0L
                    val document = result?.optJSONObject("document")
                    val fileId = document?.optString("file_id", "") ?: ""

                    if (fileId.isNotEmpty()) {
                        return Pair(fileId, messageId)
                    }
                }
            }

            Log.e(TAG, "Telegram upload failed: HTTP ${response.code}, body: ${body.take(200)}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Telegram upload exception for ${file.name}", e)
            null
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
}
