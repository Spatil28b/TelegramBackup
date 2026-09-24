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
import com.example.telegrambackup.network.BotFleetManager
import com.example.telegrambackup.network.HttpClientProvider
import com.example.telegrambackup.network.ProgressCountingRequestBody
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.AppCacheManager
import com.example.telegrambackup.utils.AppNotificationHelper
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupFileType
import com.example.telegrambackup.utils.BackupProgressTracker
import com.example.telegrambackup.utils.FileChunker
import com.example.telegrambackup.utils.FileEncryptor
import com.example.telegrambackup.utils.FileFingerprintUtil
import com.example.telegrambackup.utils.TelegramApiConfig
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
        private val globalBotIndex = AtomicInteger(0)

        fun enqueue(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "ManualBackupWork",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
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

            // Step 3: PRE-BACKUP CLOUD CATALOG SYNCHRONIZATION
            // Hydrate local Room DB from cloud PostgreSQL catalog FIRST to ensure zero-duplicate deduplication
            BackupProgressTracker.startBackupSession(0, "Synchronizing cloud file catalog...")
            try {
                SettingsDbManager.restoreCloudFileIndexToRoom(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud file index sync encountered an issue (proceeding with local catalog)", e)
            }

            // Safety Guard: If local DB has 0 files but user has account configured, verify we did not experience an empty race condition
            val currentLocalFiles = try { uploadedDao.getAllFiles() } catch (_: Exception) { emptyList() }
            if (currentLocalFiles.isEmpty() && prefs.userEmail.isNotBlank()) {
                Log.d(TAG, "Local catalog is currently blank for logged-in user. Retrying sync to safeguard against duplication...")
                try {
                    val restoredCount = SettingsDbManager.restoreCloudFileIndexToRoom(applicationContext)
                    Log.d(TAG, "Synchronized $restoredCount files from cloud.")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not reach cloud catalog. Postponing backup cycle to prevent duplicate uploads.", e)
                    BackupProgressTracker.finishBackupSession("Sync Pending")
                    return@withContext Result.retry()
                }
            }

            // Step 4: PRE-BACKUP VERIFICATION — Purge ghost records before scanning
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

            // Step 5: Identify storage directories to scan
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

            // Step 6: Calculate file size boundaries (in bytes) based on preferences (0 = Unlimited)
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

                                    // Guard: Skip app-internal cache, preview temp, and downloaded restored files
                                    val absLower = absPath.lowercase()
                                    if (absLower.contains("cloud_preview_cache") ||
                                        absLower.contains("backup_temp") ||
                                        absLower.contains("/download/telegrambackup") ||
                                        absLower.contains("/downloads/telegrambackup")) {
                                        return@filter false
                                    }

                                    val name = file.name.lowercase()
                                    if (name.endsWith(".downloading") || name.endsWith(".tmp") ||
                                        name.endsWith(".part") || name.endsWith(".crdownload")) {
                                        return@filter false
                                    }

                                    val hideHidden = !prefs.showHiddenFiles
                                    val isNotHidden = if (hideHidden) !name.startsWith(".") else true
                                    file.isFile && isNotHidden
                                } catch (e: Exception) { false }
                            }
                            .forEach { file ->
                                try {
                                    val fileLength = file.length()
                                    if (fileLength > 0) {
                                        val ext = file.extension.lowercase()
                                        val type = BackupFileType.fromExtension(ext)

                                        if (prefs.isCategoryEnabled(type)) {
                                            // Individual file size condition for each specific file type
                                            val effectiveRange = prefs.getEffectiveByteRange(type)
                                            if (fileLength in effectiveRange) {
                                                candidateFilesMap[file.absolutePath] = file
                                            }
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

            // Step 6: Multi-Criteria Deduplication Engine (Path, Name+Size, and Content-Hash Fingerprint matching)
            val existingFiles = try { uploadedDao.getAllFiles() } catch (e: Exception) { emptyList() }
            val existingPaths = HashSet<String>()
            val existingNameSizePairs = HashSet<String>()
            val existingFilesBySize = HashMap<Long, MutableList<UploadedFile>>()

            for (f in existingFiles) {
                if (f.path.isNotEmpty()) {
                    existingPaths.add(f.path)
                    existingPaths.add(f.path.lowercase())
                }
                if (f.fileName.isNotEmpty()) {
                    existingNameSizePairs.add("${f.fileName.lowercase()}_${f.size}")
                }
                if (f.size > 0 && f.telegramFileId.isNotBlank()) {
                    existingFilesBySize.getOrPut(f.size) { mutableListOf() }.add(f)
                }
            }

            val unbackedFiles = mutableListOf<File>()
            var deduplicatedCount = 0

            for (candidate in candidateFiles) {
                val pathExact = candidate.absolutePath
                val pathLower = candidate.absolutePath.lowercase()
                val nameSize = "${candidate.name.lowercase()}_${candidate.length()}"

                val isPathOrNameMatch = existingPaths.contains(pathExact) ||
                        existingPaths.contains(pathLower) ||
                        existingNameSizePairs.contains(nameSize)

                if (isPathOrNameMatch) {
                    continue
                }

                // Guard: Incomplete partial download protection
                // If a complete backup with the same filename exists with substantially larger size,
                // this candidate file on disk is an incomplete download. Never re-upload it!
                val existingBigger = existingFiles.firstOrNull {
                    it.fileName.equals(candidate.name, ignoreCase = true) &&
                    it.size > 0 &&
                    it.size >= (candidate.length() * 1.5).toLong()
                }
                if (existingBigger != null) {
                    Log.w(TAG, "🛡️ Incomplete download guard: Skipping ${candidate.name} (${candidate.length()} B) because cloud already holds complete file (${existingBigger.size} B).")
                    continue
                }

                // Step 6b: Content-Hash Fingerprint Deduplication (Rename-proof & folder-proof)
                val candidateLength = candidate.length()
                val sizeMatches = existingFilesBySize[candidateLength]
                var isContentDuplicate = false

                if (!sizeMatches.isNullOrEmpty()) {
                    val candidateFingerprint = FileFingerprintUtil.computeFastFingerprint(candidate)
                    if (candidateFingerprint.isNotEmpty()) {
                        for (match in sizeMatches) {
                            val matchLocalFile = File(match.path)
                            if (matchLocalFile.exists() && matchLocalFile.length() == candidateLength) {
                                val matchFingerprint = FileFingerprintUtil.computeFastFingerprint(matchLocalFile)
                                if (matchFingerprint.isNotEmpty() && matchFingerprint == candidateFingerprint) {
                                    // Found 100% cryptographic content duplicate! Link to existing Telegram cloud file
                                    val deduplicatedRecord = match.copy(
                                        path = candidate.absolutePath,
                                        fileName = candidate.name,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    try {
                                        uploadedDao.insert(deduplicatedRecord)
                                        existingPaths.add(candidate.absolutePath)
                                        existingNameSizePairs.add(nameSize)
                                        deduplicatedCount++
                                        isContentDuplicate = true
                                        Log.i(TAG, "⚡ Instant Deduplication: ${candidate.name} matches ${match.fileName} (Fingerprint: $candidateFingerprint). Linked to cloud without re-upload!")
                                    } catch (_: Exception) {}
                                    break
                                }
                            }
                        }
                    }
                }

                if (!isContentDuplicate) {
                    unbackedFiles.add(candidate)
                }
            }

            if (deduplicatedCount > 0) {
                Log.i(TAG, "🎯 Deduplication Engine saved $deduplicatedCount file uploads via Content-Hash matching!")
            }

            if (unbackedFiles.isEmpty()) {
                BackupProgressTracker.finishBackupSession("All files up to date!")
                return@withContext Result.success()
            }

            // Step 6b: Check Power & Battery Protection Guards
            val batteryIntent = applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val batteryStatus = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

            if (prefs.chargingOnly && !isCharging) {
                Log.d(TAG, "Charging-only rule active and phone is on battery. Skipping auto-backup.")
                BackupProgressTracker.finishBackupSession("Paused (Waiting for charger)")
                return@withContext Result.success()
            }

            if (prefs.lowBatteryProtection && batteryPct < 20 && !isCharging) {
                Log.d(TAG, "Low battery guard active ($batteryPct% < 20%). Skipping auto-backup to preserve phone battery.")
                BackupProgressTracker.finishBackupSession("Paused (Low battery < 20%)")
                return@withContext Result.success()
            }

            // Auto prune cache before starting new batch
            AppCacheManager.autoPruneCache(applicationContext)

            // Step 7: Queue unbacked files and start backup session
            val queueList = unbackedFiles.map { f ->
                UploadItemProgress(fileName = f.name, filePath = f.absolutePath, fileSize = f.length())
            }
            BackupProgressTracker.startBackupSession(queueList.size, "Preparing ${queueList.size} files for Telegram Cloud Sync...")
            BackupProgressTracker.updateQueue(queueList)

            // Prepare temp directory for encrypted/chunked files
            val tempDir = File(applicationContext.cacheDir, "backup_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            val allBots = db.telegramBotDao().getActiveBots()
            val allBotsCount = allBots.size.coerceAtLeast(1)
            val concurrency = maxOf(prefs.uploadThreads, allBotsCount).coerceIn(1, 16)
            val parallelSemaphore = Semaphore(concurrency)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            AppNotificationHelper.showPersistentControlNotification(
                context = applicationContext,
                isRunning = true,
                statusText = "Syncing ${unbackedFiles.size} files to Telegram Cloud"
            )
            AppNotificationHelper.showBackupActiveNotification(
                context = applicationContext,
                fileName = unbackedFiles.firstOrNull()?.name ?: "Files",
                completedFiles = 0,
                totalFiles = unbackedFiles.size,
                progressPercent = 0,
                speedStr = "0 KB/s"
            )

            coroutineScope {
                val jobs = unbackedFiles.map { file ->
                    launch(Dispatchers.IO) {
                        if (prefs.stopAllBackups) return@launch
                        if (!checkDailyDataAllowed(prefs, file.length())) {
                            Log.d(TAG, "Daily data limit reached (${prefs.dailyDataLimitMB} MB). Skipping ${file.name}")
                            return@launch
                        }

                        parallelSemaphore.withPermit {
                            if (prefs.stopAllBackups) return@withPermit
                            if (!checkDailyDataAllowed(prefs, file.length())) return@withPermit

                            try {
                                BackupProgressTracker.setCurrentUploadingFile(file.name, file.absolutePath, 0.05f)

                                // Determine Category & Destination Channel from Fleet
                                val ext = file.extension.lowercase()
                                val fileCategory = when {
                                    ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "raw", "svg") -> "PHOTOS"
                                    ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv") -> "VIDEOS"
                                    ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv") -> "DOCUMENTS"
                                    else -> "ALL"
                                }
                                val targetChannel = BotFleetManager.resolveDestinationChannel(applicationContext, fileCategory)
                                val destinationChatId = targetChannel.chatId.ifBlank { chatId }

                                // Step 8a: Optionally encrypt file before splitting into chunks
                                val fileToUpload: File
                                val isEncrypted = prefs.encryptionEnabled
                                if (isEncrypted) {
                                    val safeId = System.currentTimeMillis() + (file.hashCode().toLong() and 0x7FFFFFFF)
                                    val encryptedFile = File(tempDir, "${safeId}_${file.name}.enc")
                                    FileEncryptor.encryptFile(file, encryptedFile, prefs.encryptionKey)
                                    fileToUpload = encryptedFile
                                } else {
                                    fileToUpload = file
                                }

                                // Step 8b: Calculate total 4 MB chunks required
                                val totalChunks = FileChunker.getChunkCount(fileToUpload)
                                val isChunked = totalChunks > 1

                                // Acquire size-tiered rotating bot pool for parallel chunk distribution
                                val botPool = BotFleetManager.getAutoBackupBotPool(applicationContext, file.length(), totalChunks)

                                // Step 8c: Check for completed uploaded chunks (Resumable Upload)
                                val uploadedChunksMap = java.util.concurrent.ConcurrentHashMap<Int, ChunkProgressEntity>()
                                val savedChunks = chunkDao.getUploadedChunksForFile(file.absolutePath)
                                val completedSavedChunks = savedChunks.filter { it.status == "COMPLETED" && it.telegramFileId.isNotBlank() }

                                if (completedSavedChunks.isNotEmpty()) {
                                    completedSavedChunks.forEach { uploadedChunksMap[it.chunkIndex] = it }
                                    val initialFraction = (uploadedChunksMap.size.toFloat() / totalChunks.toFloat()).coerceIn(0.05f, 0.99f)
                                    BackupProgressTracker.setCurrentUploadingFile(file.name, file.absolutePath, initialFraction)
                                    BackupProgressTracker.updateActiveUpload(file.absolutePath, file.name, file.length(), uploadedChunksMap.size, totalChunks, initialFraction)
                                    Log.d(TAG, "⚡ Resuming ${file.name}: ${uploadedChunksMap.size}/$totalChunks chunks (${(initialFraction * 100).toInt()}%) already uploaded")
                                } else {
                                    BackupProgressTracker.setCurrentUploadingFile(file.name, file.absolutePath, 0.05f)
                                    BackupProgressTracker.updateActiveUpload(file.absolutePath, file.name, file.length(), 0, totalChunks, 0.05f)
                                }

                                val chunkConcurrency = minOf(botPool.size, maxOf(prefs.uploadThreads, allBotsCount)).coerceIn(1, 16)
                                val chunkSemaphore = Semaphore(chunkConcurrency)
                                BackupProgressTracker.updateBotUtilization(working = minOf(chunkConcurrency, allBotsCount), total = allBotsCount)

                                // Step 8d: Process all 4 MB chunks SIMULTANEOUSLY in parallel across Bot Fleet
                                coroutineScope {
                                    val chunkJobs = (1..totalChunks).map { chunkIdx ->
                                        launch(Dispatchers.IO) {
                                            if (prefs.stopAllBackups) return@launch
                                            if (uploadedChunksMap.containsKey(chunkIdx)) return@launch

                                             chunkSemaphore.withPermit {
                                                 if (prefs.stopAllBackups) return@withPermit

                                                 val botIdx = if (botPool.isNotEmpty()) {
                                                     (globalBotIndex.getAndIncrement() and 0x7FFFFFFF) % botPool.size
                                                 } else 0
                                                 val assignedBot = botPool.getOrNull(botIdx) ?: BotFleetManager.getNextAvailableBot(applicationContext)
                                                 val botTokenToUse: String = if (!assignedBot?.botToken.isNullOrBlank()) assignedBot!!.botToken else botToken

                                                 val claimEntity = ChunkProgressEntity(
                                                     sourcePath = file.absolutePath,
                                                     chunkIndex = chunkIdx,
                                                     totalChunks = totalChunks,
                                                     telegramFileId = "",
                                                     messageId = 0L,
                                                     chunkSize = 0L,
                                                     status = "UPLOADING",
                                                     botTokenUsed = botTokenToUse,
                                                     channelId = destinationChatId,
                                                     uploadedAt = System.currentTimeMillis()
                                                 )
                                                 chunkDao.insert(claimEntity)

                                                 val safeChunkId = System.currentTimeMillis() + ((file.hashCode() + chunkIdx).toLong() and 0x7FFFFFFF)
                                                 val chunkTempFile = File(tempDir, "${safeChunkId}_part%03d".format(chunkIdx))
                                                 BackupProgressTracker.onBotStartedWork(botTokenToUse, allBotsCount)
                                                 try {
                                                    FileChunker.extractSingleChunk(fileToUpload, chunkIdx, chunkTempFile)

                                                    val caption = if (isChunked) {
                                                        "📦 ${file.name} [Part $chunkIdx/$totalChunks]"
                                                    } else {
                                                        "📤 ${file.name} (${formatFileSize(file.length())})"
                                                    }

                                                    val uploadResult = uploadToTelegramWithRetry(
                                                        botToken = botTokenToUse,
                                                        chatId = destinationChatId,
                                                        file = chunkTempFile,
                                                        caption = caption,
                                                        chunkIdx = chunkIdx,
                                                        totalChunks = totalChunks,
                                                        sourceFilePath = file.absolutePath,
                                                        sourceFileName = file.name,
                                                        sourceFileSize = file.length(),
                                                        completedFiles = successCount.get(),
                                                        totalFiles = unbackedFiles.size,
                                                        secretKey = prefs.encryptionKey
                                                    )

                                                    if (uploadResult != null) {
                                                        val fileId = uploadResult.first
                                                        val messageId = uploadResult.second

                                                        val completedEntity = ChunkProgressEntity(
                                                            sourcePath = file.absolutePath,
                                                            chunkIndex = chunkIdx,
                                                            totalChunks = totalChunks,
                                                            telegramFileId = fileId,
                                                            messageId = messageId,
                                                            chunkSize = chunkTempFile.length(),
                                                            status = "COMPLETED",
                                                            botTokenUsed = botTokenToUse,
                                                            channelId = destinationChatId,
                                                            uploadedAt = System.currentTimeMillis()
                                                        )
                                                        chunkDao.insert(completedEntity)
                                                        uploadedChunksMap[chunkIdx] = completedEntity
                                                        val currentProg = (uploadedChunksMap.size.toFloat() / totalChunks.toFloat()).coerceIn(0.05f, 1f)
                                                        BackupProgressTracker.updateActiveUpload(file.absolutePath, file.name, file.length(), uploadedChunksMap.size, totalChunks, currentProg)
                                                        BotFleetManager.recordSuccess(applicationContext, botTokenToUse, chunkTempFile.length())
                                                        BackupProgressTracker.reportBytesUploaded(chunkTempFile.length())
                                                    } else {
                                                        chunkDao.deleteChunk(file.absolutePath, chunkIdx)
                                                        BotFleetManager.recordFailure(applicationContext, botTokenToUse)
                                                        Log.e(TAG, "Failed to upload chunk $chunkIdx/$totalChunks for ${file.name}")
                                                    }
                                                } finally {
                                                    BackupProgressTracker.onBotFinishedWork(botTokenToUse)
                                                    if (chunkTempFile.exists()) chunkTempFile.delete()
                                                }
                                            }
                                        }
                                    }
                                    chunkJobs.joinAll()
                                }

                                BackupProgressTracker.removeActiveUpload(file.absolutePath)
                                if (isEncrypted && fileToUpload.exists() && fileToUpload.parentFile == tempDir) {
                                    fileToUpload.delete()
                                }

                                if (uploadedChunksMap.size < totalChunks) {
                                    failCount.incrementAndGet()
                                    Log.w(TAG, "File ${file.name} upload incomplete (${uploadedChunksMap.size}/$totalChunks chunks completed).")
                                    return@withPermit
                                }

                                val firstChunk = uploadedChunksMap[1]
                                val firstChunkFileId = firstChunk?.telegramFileId ?: ""
                                val firstChunkMessageId = firstChunk?.messageId ?: 0L
                                val primaryBotTokenUsed = firstChunk?.botTokenUsed ?: botToken

                                val chunkList = (1..totalChunks).mapNotNull { idx ->
                                    uploadedChunksMap[idx]?.let { Triple(idx, it.telegramFileId, it.botTokenUsed) }
                                }

                                val entity = UploadedFile(
                                    file.absolutePath,
                                    file.name,
                                    firstChunkFileId,
                                    System.currentTimeMillis(),
                                    file.length(),
                                    firstChunkMessageId
                                ).apply {
                                    this.channelId = destinationChatId
                                    this.botTokenUsed = primaryBotTokenUsed
                                    this.totalChunks = totalChunks
                                    if (chunkList.isNotEmpty()) {
                                        this.setChunks(chunkList)
                                    }
                                }
                                uploadedDao.insert(entity)
                                // Retain chunk records in chunk_upload_progress so multi-part files can be downloaded/streamed complete

                                if (userEmail.isNotEmpty()) {
                                    try {
                                        val fileModTime = if (file.exists() && file.lastModified() > 946684800000L) {
                                            file.lastModified()
                                        } else {
                                            System.currentTimeMillis()
                                        }
                                        serverApi.indexFile(
                                            serverUrl = prefs.serverUrl,
                                            email = userEmail,
                                            fileName = file.name,
                                            telegramFileId = firstChunkFileId,
                                            size = file.length(),
                                            path = file.absolutePath,
                                            timestamp = fileModTime,
                                            messageId = firstChunkMessageId,
                                            isDriveUpload = false,
                                            chunksJson = entity.chunksJson,
                                            channelId = destinationChatId,
                                            botTokenUsed = primaryBotTokenUsed,
                                            totalChunks = totalChunks
                                        )
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Cloud index sync failed for ${file.name}, local record intact", e)
                                    }
                                }

                                recordDailyDataUsage(prefs, file.length())
                                val curSuccess = successCount.incrementAndGet()
                                BackupProgressTracker.markFileCompleted(file.name, file.absolutePath, file.length())
                                Log.d(TAG, "✅ Fully Backed Up ($curSuccess): ${file.name} to channel $destinationChatId ($totalChunks chunks)")

                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                                Log.e(TAG, "Error backing up ${file.name}", e)
                            }
                        }
                    }
                }
                jobs.joinAll()
            }

            // Cleanup temp directory
            try {
                tempDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) { }

            val sCount = successCount.get()
            val fCount = failCount.get()
            val summary = buildString {
                append("Backed up $sCount files to Telegram Cloud!")
                if (fCount > 0) append(" ($fCount partial/failed)")
            }
            Log.d(TAG, summary)
            BackupProgressTracker.finishBackupSession(summary)
            AppNotificationHelper.cancelBackupNotification(applicationContext)
            AppNotificationHelper.showPersistentControlNotification(applicationContext, isRunning = false)

            if (prefs.stopAllBackups) {
                AppNotificationHelper.showBackupPausedNotification(applicationContext)
            } else {
                AppNotificationHelper.showBackupFinishedNotification(applicationContext, sCount, summary)
            }

            Result.success()

        } finally {
            // Release Process-Wide Atomic Lock upon completion or error
            isWorkerRunning.set(false)
            try {
                val workerPrefs = AppPrefs(applicationContext)
                AppCacheManager.autoPruneCache(applicationContext)
                val db = AppDatabase.getDatabase(applicationContext)
                if (workerPrefs.trashAutoCleanDays > 0) {
                    val cutoff = System.currentTimeMillis() - (workerPrefs.trashAutoCleanDays.toLong() * 86400000L)
                    val expired = db.uploadedFileDao().getExpiredTrashFiles(cutoff)
                    if (expired.isNotEmpty()) {
                        val serverApi = com.example.telegrambackup.network.ServerApi()
                        val authPrefs = applicationContext.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        val userEmail = authPrefs.getString("user_email", "") ?: workerPrefs.recoveryEmail
                        expired.forEach { expFile ->
                            try { db.chunkProgressDao().deleteChunksForFile(expFile.path) } catch (_: Exception) {}
                            if (userEmail.isNotEmpty()) {
                                try { serverApi.deleteFile(workerPrefs.serverUrl, userEmail, expFile.telegramFileId, expFile.path) } catch (_: Exception) {}
                            }
                        }
                        db.uploadedFileDao().deleteExpiredTrash(cutoff)
                    }
                }
                db.chunkProgressDao().pruneOrphanedChunks()
            } catch (_: Exception) {}
        }
    }

    private fun checkDailyDataAllowed(prefs: AppPrefs, bytesToUpload: Long): Boolean {
        val limitMB = prefs.dailyDataLimitMB
        if (limitMB <= 0L) return true
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        if (prefs.dailyDataDate != today) {
            prefs.dailyDataDate = today
            prefs.dailyDataTransferredBytes = 0L
        }
        val maxBytes = limitMB * 1024L * 1024L
        return (prefs.dailyDataTransferredBytes + bytesToUpload) <= maxBytes
    }

    private fun recordDailyDataUsage(prefs: AppPrefs, bytesUploaded: Long) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        if (prefs.dailyDataDate != today) {
            prefs.dailyDataDate = today
            prefs.dailyDataTransferredBytes = 0L
        }
        prefs.dailyDataTransferredBytes += bytesUploaded
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
                .setSmallIcon(R.drawable.ic_notif_cloud_done)
                .setContentTitle("☁️ TSpace Backup Complete")
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
        sourceFilePath: String,
        sourceFileName: String,
        sourceFileSize: Long,
        completedFiles: Int,
        totalFiles: Int,
        secretKey: String = ""
    ): Pair<String, Long>? {
        var attempt = 1
        val maxAttempts = 5

        while (attempt <= maxAttempts) {
            val statusText = if (attempt > 1) {
                "⚡ Weak Network: Retrying $sourceFileName [Part $chunkIdx/$totalChunks] (Attempt $attempt/$maxAttempts)..."
            } else {
                "Uploading $sourceFileName [Part $chunkIdx/$totalChunks]..."
            }
            BackupProgressTracker.updateStatus(statusText)

            val result = uploadToTelegramSingleAttempt(
                botToken = botToken,
                chatId = chatId,
                file = file,
                caption = caption,
                chunkIdx = chunkIdx,
                totalChunks = totalChunks,
                sourceFilePath = sourceFilePath,
                sourceFileName = sourceFileName,
                sourceFileSize = sourceFileSize,
                completedFiles = completedFiles,
                totalFiles = totalFiles,
                secretKey = secretKey
            )
            if (result != null) {
                return result
            }

            if (attempt < maxAttempts) {
                val backoffMs = attempt * 2000L // 2s, 4s, 6s, 8s
                Log.w(TAG, "Chunk $chunkIdx/$totalChunks for $sourceFileName upload attempt $attempt/$maxAttempts failed on low network. Retrying in ${backoffMs}ms...")
                delay(backoffMs)
            }
            attempt++
        }
        return null
    }

    /**
     * Uploads a single file/chunk to Telegram via Bot API `sendDocument` endpoint with full AES-256 encryption.
     * Uses ProgressCountingRequestBody for silky-smooth real-time speed calculation and progress updates.
     */
    private suspend fun uploadToTelegramSingleAttempt(
        botToken: String,
        chatId: String,
        file: File,
        caption: String,
        chunkIdx: Int,
        totalChunks: Int,
        sourceFilePath: String,
        sourceFileName: String,
        sourceFileSize: Long,
        completedFiles: Int,
        totalFiles: Int,
        secretKey: String = ""
    ): Pair<String, Long>? {
        return try {
            val url = TelegramApiConfig.getMethodUrl(applicationContext, botToken, "sendDocument")

            val obfuscatedName = if (secretKey.isNotEmpty()) FileEncryptor.obfuscateFileName(file.name, secretKey) else "ENC_${file.name.hashCode() and 0x7FFFFFFF}.bin"
            val encryptedCaption = if (secretKey.isNotEmpty()) "🔒 ENC:" + FileEncryptor.encryptText(caption, secretKey) else caption

            val rawBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            var lastNotifTime = 0L

            val countingBody = ProgressCountingRequestBody(rawBody) { writtenDelta, totalBytes, writtenSoFar ->
                BackupProgressTracker.reportBytesUploaded(writtenDelta)

                val chunkFraction = if (totalBytes > 0) (writtenSoFar.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                val overallProgress = (((chunkIdx - 1).toFloat() + chunkFraction) / totalChunks.toFloat()).coerceIn(0f, 1f)

                BackupProgressTracker.updateActiveUpload(
                    filePath = sourceFilePath,
                    fileName = sourceFileName,
                    fileSize = sourceFileSize,
                    currentChunk = chunkIdx,
                    totalChunks = totalChunks,
                    progress = overallProgress
                )

                val now = System.currentTimeMillis()
                if (now - lastNotifTime >= 500) {
                    lastNotifTime = now
                    val totalPct = if (totalFiles > 0) {
                        (((completedFiles.toFloat() + overallProgress) / totalFiles.toFloat()) * 100).toInt()
                    } else {
                        (overallProgress * 100).toInt()
                    }
                    AppNotificationHelper.showBackupActiveNotification(
                        context = applicationContext,
                        fileName = sourceFileName,
                        completedFiles = completedFiles,
                        totalFiles = totalFiles,
                        progressPercent = totalPct.coerceIn(0, 100),
                        speedStr = BackupProgressTracker.formattedUploadSpeed.value
                    )
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", encryptedCaption)
                .addFormDataPart(
                    "document",
                    obfuscatedName,
                    countingBody
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

            if (response.code == 429) {
                try {
                    val json = JSONObject(body)
                    val retryAfter = json.optJSONObject("parameters")?.optInt("retry_after", 30) ?: 30
                    BotFleetManager.markBotRateLimited(applicationContext, botToken, retryAfter)
                } catch (e: Exception) {
                    BotFleetManager.markBotRateLimited(applicationContext, botToken, 30)
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
