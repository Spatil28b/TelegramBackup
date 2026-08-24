package com.example.telegrambackup.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.ChunkProgressEntity
import com.example.telegrambackup.data.TelegramBotEntity
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.BotFleetManager
import com.example.telegrambackup.network.HttpClientProvider
import com.example.telegrambackup.network.ProgressCountingRequestBody
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * DriveUploadManager
 * High-throughput parallel upload engine specifically for Cloud Drive files & folder trees.
 * Executes uploads using a dedicated pool of 3-4 Telegram Bots concurrently without blocking UI or background auto-backups.
 */
object DriveUploadManager {
    private const val TAG = "DriveUploadManager"
    private const val MAX_PARALLEL_UPLOADS = 4

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val parallelSemaphore = Semaphore(MAX_PARALLEL_UPLOADS)
    private val activeUploadCount = AtomicInteger(0)

    private fun resolveFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (result.isNullOrBlank()) {
            result = uri.path?.substringAfterLast('/')
        }
        if (result.isNullOrBlank()) {
            result = "Drive_File_${System.currentTimeMillis()}"
        }
        // Deduce extension from MIME type if filename lacks an extension
        if (!result!!.contains('.')) {
            try {
                val mimeType = context.contentResolver.getType(uri)
                if (!mimeType.isNullOrBlank()) {
                    val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    if (!ext.isNullOrBlank()) {
                        result = "$result.$ext"
                    }
                }
            } catch (_: Exception) {}
        }
        return result!!.substringAfterLast('/').substringAfterLast(':').trim().ifEmpty { "Drive_File_${System.currentTimeMillis()}" }
    }

    fun enqueuePersonalFiles(context: Context, uris: List<Uri>, targetDirectoryPath: String, onUpdate: () -> Unit) {
        scope.launch {
            val appCtx = context.applicationContext
            val prefs = AppPrefs(appCtx)

            BotFleetManager.ensureInitialized(appCtx)
            val driveChannel = BotFleetManager.resolveDestinationChannel(appCtx, "DRIVE")
            val targetChatId = driveChannel.chatId.ifBlank { prefs.chatId }

            // Dedicated Drive Bot Pool (3-4 bots)
            val driveBotPool = BotFleetManager.getDriveBotPool(appCtx, 4)
            val primaryBotToken = driveBotPool.firstOrNull()?.botToken ?: prefs.botToken

            if (primaryBotToken.isEmpty() || targetChatId.isEmpty()) {
                Log.w(TAG, "Missing bot credentials for Drive upload.")
                return@launch
            }

            val db = AppDatabase.getDatabase(appCtx)
            val tempDir = File(appCtx.cacheDir, "drive_upload_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            val totalFiles = uris.size
            BackupProgressTracker.startBackupSession(totalFiles, "Cloud Drive: Uploading $totalFiles files in parallel...")

            val completedCount = AtomicInteger(0)

            val jobs = uris.map { uri ->
                launch {
                    parallelSemaphore.withPermit {
                        activeUploadCount.incrementAndGet()
                        try {
                            val fileName = resolveFileName(appCtx, uri)
                            val targetPath = "$targetDirectoryPath/$fileName"

                            val safeTempName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                            val tempFile = File(tempDir, "${System.currentTimeMillis()}_$safeTempName")
                            appCtx.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }

                            if (tempFile.exists() && tempFile.length() > 0) {
                                processSingleDriveFileUpload(
                                    appCtx,
                                    prefs,
                                    db,
                                    driveBotPool,
                                    primaryBotToken,
                                    targetChatId,
                                    tempFile,
                                    fileName,
                                    targetPath,
                                    completedCount.get(),
                                    totalFiles
                                )
                                completedCount.incrementAndGet()
                            }
                            if (tempFile.exists()) tempFile.delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in Drive personal file upload", e)
                        } finally {
                            activeUploadCount.decrementAndGet()
                            withContext(Dispatchers.Main) { onUpdate() }
                        }
                    }
                }
            }

            jobs.joinAll()
            withContext(Dispatchers.Main) { onUpdate() }
            BackupProgressTracker.finishBackupSession("☁️ Drive upload complete ($totalFiles files)")
        }
    }

    fun enqueueFolderTree(context: Context, treeUri: Uri, targetDirectoryPath: String, onUpdate: () -> Unit) {
        scope.launch {
            val appCtx = context.applicationContext
            try {
                appCtx.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}

            val docDir = DocumentFile.fromTreeUri(appCtx, treeUri) ?: return@launch
            if (!docDir.isDirectory) return@launch

            val prefs = AppPrefs(appCtx)
            BotFleetManager.ensureInitialized(appCtx)
            val driveChannel = BotFleetManager.resolveDestinationChannel(appCtx, "DRIVE")
            val targetChatId = driveChannel.chatId.ifBlank { prefs.chatId }

            // Dedicated Drive Bot Pool (3-4 bots)
            val driveBotPool = BotFleetManager.getDriveBotPool(appCtx, 4)
            val primaryBotToken = driveBotPool.firstOrNull()?.botToken ?: prefs.botToken

            if (primaryBotToken.isEmpty() || targetChatId.isEmpty()) return@launch

            val db = AppDatabase.getDatabase(appCtx)
            val folderName = docDir.name ?: "Uploaded_Folder"
            val baseFolderPath = "$targetDirectoryPath/$folderName"
            val now = System.currentTimeMillis()

            val rootMarker = UploadedFile(
                baseFolderPath,
                folderName,
                "DIR_MARKER",
                now,
                0L,
                0L
            ).apply { isDriveUpload = true }
            db.uploadedFileDao().insert(rootMarker)

            val userEmail = prefs.userEmail
            if (userEmail.isNotEmpty()) {
                try {
                    ServerApi().indexFile(prefs.serverUrl, userEmail, folderName, "DIR_MARKER", 0L, baseFolderPath, now, 0L, true)
                } catch (_: Exception) {}
            }

            val filesToUpload = mutableListOf<Pair<DocumentFile, String>>()

            suspend fun scanTree(dir: DocumentFile, currentPath: String) {
                dir.listFiles().forEach { child ->
                    if (child.isDirectory) {
                        val subName = child.name ?: "SubFolder"
                        val subPath = "$currentPath/$subName"
                        val subMarker = UploadedFile(subPath, subName, "DIR_MARKER", now, 0L, 0L).apply { isDriveUpload = true }
                        db.uploadedFileDao().insert(subMarker)
                        if (userEmail.isNotEmpty()) {
                            try { ServerApi().indexFile(prefs.serverUrl, userEmail, subName, "DIR_MARKER", 0L, subPath, now, 0L, true) } catch (_: Exception) {}
                        }
                        scanTree(child, subPath)
                    } else if (child.isFile) {
                        filesToUpload.add(child to currentPath)
                    }
                }
            }

            scanTree(docDir, baseFolderPath)
            withContext(Dispatchers.Main) { onUpdate() }

            if (filesToUpload.isEmpty()) return@launch

            val tempDir = File(appCtx.cacheDir, "drive_folder_temp")
            if (!tempDir.exists()) tempDir.mkdirs()

            val totalFiles = filesToUpload.size
            BackupProgressTracker.startBackupSession(totalFiles, "Folder '$folderName': Uploading $totalFiles files in parallel...")

            val completedCount = AtomicInteger(0)

            val jobs = filesToUpload.map { (docFile, parentPath) ->
                launch {
                    parallelSemaphore.withPermit {
                        activeUploadCount.incrementAndGet()
                        try {
                            val fName = docFile.name ?: "File_${System.currentTimeMillis()}"
                            val fullPath = "$parentPath/$fName"
                            val tempFile = File(tempDir, "${System.currentTimeMillis()}_$fName")

                            appCtx.contentResolver.openInputStream(docFile.uri)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }

                            if (tempFile.exists() && tempFile.length() > 0) {
                                processSingleDriveFileUpload(
                                    appCtx,
                                    prefs,
                                    db,
                                    driveBotPool,
                                    primaryBotToken,
                                    targetChatId,
                                    tempFile,
                                    fName,
                                    fullPath,
                                    completedCount.get(),
                                    totalFiles
                                )
                                completedCount.incrementAndGet()
                            }
                            if (tempFile.exists()) tempFile.delete()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error uploading folder file", e)
                        } finally {
                            activeUploadCount.decrementAndGet()
                            withContext(Dispatchers.Main) { onUpdate() }
                        }
                    }
                }
            }

            jobs.joinAll()
            withContext(Dispatchers.Main) { onUpdate() }
            BackupProgressTracker.finishBackupSession("☁️ Folder '$folderName' upload complete")
        }
    }

    private suspend fun processSingleDriveFileUpload(
        context: Context,
        prefs: AppPrefs,
        db: AppDatabase,
        driveBotPool: List<TelegramBotEntity>,
        defaultBotToken: String,
        chatId: String,
        file: File,
        fileName: String,
        targetPath: String,
        completedFiles: Int,
        totalFiles: Int
    ) {
        val isVault = targetPath.startsWith("/Private Vault") || targetPath.startsWith("Private Vault")
        val isEncrypted = prefs.encryptionEnabled || isVault
        val secretKey = prefs.encryptionKey
        val tempDir = File(context.cacheDir, "drive_proc_temp")
        if (!tempDir.exists()) tempDir.mkdirs()

        val fileToUpload: File
        if (isEncrypted) {
            val encFile = File(tempDir, "${fileName}.enc")
            FileEncryptor.encryptFile(file, encFile, secretKey)
            fileToUpload = encFile
        } else {
            fileToUpload = file
        }

        val totalChunks = FileChunker.getChunkCount(fileToUpload)
        val uploadedChunksMap = ConcurrentHashMap<Int, ChunkProgressEntity>()
        val savedChunks = db.chunkProgressDao().getUploadedChunksForFile(targetPath)
        val completedSavedChunks = savedChunks.filter { it.status == "COMPLETED" && it.telegramFileId.isNotBlank() }

        if (completedSavedChunks.isNotEmpty()) {
            completedSavedChunks.forEach { uploadedChunksMap[it.chunkIndex] = it }
            val initialFraction = (uploadedChunksMap.size.toFloat() / totalChunks.toFloat()).coerceIn(0.05f, 0.99f)
            BackupProgressTracker.setCurrentUploadingFile(fileName, targetPath, initialFraction)
            BackupProgressTracker.updateActiveUpload(targetPath, fileName, file.length(), uploadedChunksMap.size, totalChunks, initialFraction)
            Log.d(TAG, "⚡ Resuming Drive $fileName: ${uploadedChunksMap.size}/$totalChunks chunks already saved")
        } else {
            BackupProgressTracker.setCurrentUploadingFile(fileName, targetPath, 0.05f)
            BackupProgressTracker.updateActiveUpload(targetPath, fileName, file.length(), 0, totalChunks, 0.05f)
        }

        val chunkConcurrency = minOf(driveBotPool.size, 4).coerceAtLeast(1)
        val chunkSemaphore = Semaphore(chunkConcurrency)
        val allBotsCount = db.telegramBotDao().getActiveBots().size.coerceAtLeast(1)
        BackupProgressTracker.updateBotUtilization(working = minOf(chunkConcurrency, driveBotPool.size), total = allBotsCount)

        // Upload chunks in parallel across dedicated Drive Bot Pool (3-4 bots)
        coroutineScope {
            val chunkJobs = (1..totalChunks).map { chunkIdx ->
                launch(Dispatchers.IO) {
                    if (uploadedChunksMap.containsKey(chunkIdx)) return@launch

                    chunkSemaphore.withPermit {
                        val assignedBot = driveBotPool.getOrNull((chunkIdx - 1) % driveBotPool.size)
                        val botTokenToUse = if (!assignedBot?.botToken.isNullOrBlank()) assignedBot!!.botToken else defaultBotToken

                        val safeChunkId = System.currentTimeMillis() + ((targetPath.hashCode() + chunkIdx).toLong() and 0x7FFFFFFF)
                        val chunkFile = File(tempDir, "${safeChunkId}_drive_part%03d".format(chunkIdx))
                        try {
                            FileChunker.extractSingleChunk(fileToUpload, chunkIdx, chunkFile)

                            val caption = if (totalChunks > 1) "📦 Drive: $fileName [Part $chunkIdx/$totalChunks]" else "☁️ Drive: $fileName"

                            val res = uploadSingleChunkWithRetry(
                                context = context,
                                botToken = botTokenToUse,
                                chatId = chatId,
                                file = chunkFile,
                                caption = caption,
                                chunkIdx = chunkIdx,
                                totalChunks = totalChunks,
                                sourceFilePath = targetPath,
                                sourceFileName = fileName,
                                sourceFileSize = file.length(),
                                completedFiles = completedFiles,
                                totalFiles = totalFiles,
                                secretKey = secretKey
                            )

                            if (res != null) {
                                val chunkEntity = ChunkProgressEntity(
                                    sourcePath = targetPath,
                                    chunkIndex = chunkIdx,
                                    totalChunks = totalChunks,
                                    telegramFileId = res.first,
                                    messageId = res.second,
                                    chunkSize = chunkFile.length(),
                                    status = "COMPLETED",
                                    botTokenUsed = botTokenToUse,
                                    channelId = chatId,
                                    uploadedAt = System.currentTimeMillis()
                                )
                                db.chunkProgressDao().insert(chunkEntity)
                                uploadedChunksMap[chunkIdx] = chunkEntity
                                val currentProg = (uploadedChunksMap.size.toFloat() / totalChunks.toFloat()).coerceIn(0.05f, 1f)
                                BackupProgressTracker.updateActiveUpload(targetPath, fileName, file.length(), uploadedChunksMap.size, totalChunks, currentProg)
                                BotFleetManager.recordSuccess(context, botTokenToUse, chunkFile.length())
                                BackupProgressTracker.reportBytesUploaded(chunkFile.length())
                            } else {
                                db.chunkProgressDao().deleteChunk(targetPath, chunkIdx)
                                BotFleetManager.recordFailure(context, botTokenToUse)
                                Log.e(TAG, "Failed chunk $chunkIdx for Drive file $fileName")
                            }
                        } finally {
                            if (chunkFile.exists()) chunkFile.delete()
                        }
                    }
                }
            }
            chunkJobs.joinAll()
        }

        BackupProgressTracker.removeActiveUpload(targetPath)
        if (isEncrypted && fileToUpload.exists() && fileToUpload.parentFile == tempDir) {
            fileToUpload.delete()
        }

        if (uploadedChunksMap.size >= totalChunks) {
            val firstChunk = uploadedChunksMap[1]
            val firstChunkFileId = firstChunk?.telegramFileId ?: ""
            val firstChunkMsgId = firstChunk?.messageId ?: 0L
            val primaryBotUsed = firstChunk?.botTokenUsed ?: defaultBotToken

            try {
                if (file.length() < 25 * 1024 * 1024L) { // Only cache small media < 25 MB for instant preview
                    val previewCacheDir = File(context.cacheDir, "cloud_preview_cache")
                    if (!previewCacheDir.exists()) previewCacheDir.mkdirs()
                    val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
                    val cachedTarget = File(previewCacheDir, "${firstChunkFileId.hashCode()}_$safeName")
                    file.copyTo(cachedTarget, overwrite = true)
                }
                AppCacheManager.autoPruneCache(context)
            } catch (_: Exception) {}

            val entity = UploadedFile(
                targetPath,
                fileName,
                firstChunkFileId,
                System.currentTimeMillis(),
                file.length(),
                firstChunkMsgId
            ).apply {
                isDriveUpload = true
                this.channelId = chatId
                this.botTokenUsed = primaryBotUsed
                if (totalChunks > 1) this.totalChunks = totalChunks
            }
            db.uploadedFileDao().insert(entity)

            val userEmail = prefs.userEmail
            if (userEmail.isNotEmpty()) {
                try {
                    ServerApi().indexFile(
                        prefs.serverUrl,
                        userEmail,
                        fileName,
                        firstChunkFileId,
                        file.length(),
                        targetPath,
                        System.currentTimeMillis(),
                        firstChunkMsgId,
                        true
                    )
                } catch (_: Exception) {}
            }
            BackupProgressTracker.markFileCompleted(fileName, targetPath, file.length())
            Log.d(TAG, "⚡ Drive upload complete with multi-bot engine: $fileName")
        }
    }

    private suspend fun uploadSingleChunkWithRetry(
        context: Context,
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
        secretKey: String
    ): Pair<String, Long>? {
        var attempt = 1
        while (attempt <= 5) {
            val res = uploadSingleChunk(
                context = context,
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
            if (res != null) return res
            if (attempt < 5) delay(attempt * 2000L)
            attempt++
        }
        return null
    }

    private fun uploadSingleChunk(
        context: Context,
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
        secretKey: String
    ): Pair<String, Long>? {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendDocument"
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
                    fileName = "[DRIVE] $sourceFileName",
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
                        context = context,
                        fileName = "[DRIVE] $sourceFileName",
                        completedFiles = completedFiles,
                        totalFiles = totalFiles,
                        progressPercent = totalPct.coerceIn(0, 100),
                        speedStr = BackupProgressTracker.formattedUploadSpeed.value
                    )
                }
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", encryptedCaption)
                .addFormDataPart("document", obfuscatedName, countingBody)
                .build()

            val req = Request.Builder().url(url).post(body).build()
            val resp = HttpClientProvider.uploadClient.newCall(req).execute()
            val respStr = resp.body?.string() ?: ""
            if (resp.isSuccessful && respStr.isNotEmpty()) {
                val json = JSONObject(respStr)
                if (json.optBoolean("ok")) {
                    val result = json.getJSONObject("result")
                    val messageId = result.optLong("message_id", 0L)
                    val doc = result.optJSONObject("document")
                    val fileId = doc?.optString("file_id", "") ?: ""
                    if (fileId.isNotEmpty()) return Pair(fileId, messageId)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
