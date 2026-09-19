package com.example.telegrambackup.utils

import android.content.Context
import com.example.telegrambackup.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

data class UploadItemProgress(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActiveUploadItem(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val currentChunk: Int = 1,
    val totalChunks: Int = 1,
    val progress: Float = 0f
)

object BackupProgressTracker {
    private val _isBackupActive = MutableStateFlow(false)
    val isBackupActive: StateFlow<Boolean> = _isBackupActive.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle / Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _currentFileName = MutableStateFlow("")
    val currentFileName: StateFlow<String> = _currentFileName.asStateFlow()

    private val _currentFilePath = MutableStateFlow("")
    val currentFilePath: StateFlow<String> = _currentFilePath.asStateFlow()

    private val _currentFileProgress = MutableStateFlow(0f)
    val currentFileProgress: StateFlow<Float> = _currentFileProgress.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _pendingFilesQueue = MutableStateFlow<List<UploadItemProgress>>(emptyList())
    val pendingFilesQueue: StateFlow<List<UploadItemProgress>> = _pendingFilesQueue.asStateFlow()

    private val _recentCompletedList = MutableStateFlow<List<UploadItemProgress>>(emptyList())
    val recentCompletedList: StateFlow<List<UploadItemProgress>> = _recentCompletedList.asStateFlow()

    // ⚡ Multi-File Parallel Active Uploads Tracking
    private val _activeUploadsMap = MutableStateFlow<Map<String, ActiveUploadItem>>(emptyMap())
    val activeUploadsList = MutableStateFlow<List<ActiveUploadItem>>(emptyList())

    // 🤖 Bot Fleet Utilization Tracker
    private val _workingBotsCount = MutableStateFlow(0)
    val workingBotsCount: StateFlow<Int> = _workingBotsCount.asStateFlow()

    private val _totalBotsCount = MutableStateFlow(1)
    val totalBotsCount: StateFlow<Int> = _totalBotsCount.asStateFlow()

    private val activeWorkingBotTokens = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun updateBotUtilization(working: Int, total: Int) {
        val count = maxOf(working, activeWorkingBotTokens.size).coerceAtLeast(0)
        _workingBotsCount.value = count
        _totalBotsCount.value = total.coerceAtLeast(1)
    }

    fun onBotStartedWork(botToken: String, totalBots: Int = 1) {
        if (botToken.isNotBlank()) activeWorkingBotTokens.add(botToken)
        _workingBotsCount.value = activeWorkingBotTokens.size
        _totalBotsCount.value = maxOf(_totalBotsCount.value, totalBots, 1)
    }

    fun onBotFinishedWork(botToken: String) {
        if (botToken.isNotBlank()) activeWorkingBotTokens.remove(botToken)
        _workingBotsCount.value = activeWorkingBotTokens.size
    }

    // ⚡ Real-Time Transfer Speeds
    private val _uploadSpeedBps = MutableStateFlow(0L)
    val uploadSpeedBps: StateFlow<Long> = _uploadSpeedBps.asStateFlow()

    private val _formattedUploadSpeed = MutableStateFlow("0 KB/s")
    val formattedUploadSpeed: StateFlow<String> = _formattedUploadSpeed.asStateFlow()

    private val _isDownloadActive = MutableStateFlow(false)
    val isDownloadActive: StateFlow<Boolean> = _isDownloadActive.asStateFlow()

    private val _currentDownloadFileName = MutableStateFlow("")
    val currentDownloadFileName: StateFlow<String> = _currentDownloadFileName.asStateFlow()

    private val _currentDownloadProgress = MutableStateFlow(0f)
    val currentDownloadProgress: StateFlow<Float> = _currentDownloadProgress.asStateFlow()

    private val _downloadSpeedBps = MutableStateFlow(0L)
    val downloadSpeedBps: StateFlow<Long> = _downloadSpeedBps.asStateFlow()

    private val _formattedDownloadSpeed = MutableStateFlow("0 KB/s")
    val formattedDownloadSpeed: StateFlow<String> = _formattedDownloadSpeed.asStateFlow()

    // Internal speed window trackers
    private var lastUploadTimeMs = 0L
    private var bytesUploadedInWindow = 0L

    private var lastDownloadTimeMs = 0L
    private var bytesDownloadedInWindow = 0L

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec <= 0 -> "0 KB/s"
            bytesPerSec < 1024 * 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
            bytesPerSec < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB/s", bytesPerSec / (1024.0 * 1024.0 * 1024.0))
        }
    }

    @Synchronized
    fun reportBytesUploaded(bytes: Long) {
        if (bytes <= 0) return
        val now = System.currentTimeMillis()
        bytesUploadedInWindow += bytes
        val elapsed = now - lastUploadTimeMs

        if (elapsed >= 350) {
            val speed = if (elapsed > 0) (bytesUploadedInWindow * 1000L) / elapsed else 0L
            _uploadSpeedBps.value = speed
            _formattedUploadSpeed.value = formatSpeed(speed)
            lastUploadTimeMs = now
            bytesUploadedInWindow = 0L
        }
    }

    @Synchronized
    fun reportBytesDownloaded(bytes: Long) {
        if (bytes <= 0) return
        val now = System.currentTimeMillis()
        bytesDownloadedInWindow += bytes
        val elapsed = now - lastDownloadTimeMs

        if (elapsed >= 350) {
            val speed = if (elapsed > 0) (bytesDownloadedInWindow * 1000L) / elapsed else 0L
            _downloadSpeedBps.value = speed
            _formattedDownloadSpeed.value = formatSpeed(speed)
            lastDownloadTimeMs = now
            bytesDownloadedInWindow = 0L
        }
    }

    fun updateActiveUpload(
        filePath: String,
        fileName: String,
        fileSize: Long,
        currentChunk: Int,
        totalChunks: Int,
        progress: Float
    ) {
        val currentMap = _activeUploadsMap.value.toMutableMap()
        val item = ActiveUploadItem(
            fileName = fileName,
            filePath = filePath,
            fileSize = fileSize,
            currentChunk = currentChunk,
            totalChunks = totalChunks,
            progress = progress.coerceIn(0f, 1f)
        )
        currentMap[filePath] = item
        _activeUploadsMap.value = currentMap
        activeUploadsList.value = currentMap.values.toList()

        _currentFileName.value = fileName
        _currentFilePath.value = filePath
        _currentFileProgress.value = progress
        _statusMessage.value = if (totalChunks > 1) {
            "Uploading $fileName [Part $currentChunk/$totalChunks] (${(progress * 100).toInt()}%)"
        } else {
            "Uploading $fileName (${(progress * 100).toInt()}%)"
        }
    }

    fun removeActiveUpload(filePath: String) {
        val currentMap = _activeUploadsMap.value.toMutableMap()
        currentMap.remove(filePath)
        _activeUploadsMap.value = currentMap
        activeUploadsList.value = currentMap.values.toList()

        val next = currentMap.values.firstOrNull()
        if (next != null) {
            _currentFileName.value = next.fileName
            _currentFilePath.value = next.filePath
            _currentFileProgress.value = next.progress
        } else {
            _currentFileName.value = ""
            _currentFilePath.value = ""
            _currentFileProgress.value = 0f
        }
    }

    fun startDownload(fileName: String) {
        _isDownloadActive.value = true
        _currentDownloadFileName.value = fileName
        _currentDownloadProgress.value = 0.05f
        lastDownloadTimeMs = System.currentTimeMillis()
        bytesDownloadedInWindow = 0L
    }

    fun updateDownloadProgress(progress: Float) {
        _currentDownloadProgress.value = progress.coerceIn(0f, 1f)
    }

    fun finishDownload() {
        _isDownloadActive.value = false
        _currentDownloadFileName.value = ""
        _currentDownloadProgress.value = 0f
        _downloadSpeedBps.value = 0L
        _formattedDownloadSpeed.value = "0 KB/s"
    }

    /**
     * Hydrates the recent completed list from local Room database on app open.
     * Prevents empty/reset UI displays on fresh app launch.
     */
    suspend fun hydrateRecentCompleted(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val files = db.uploadedFileDao().getAllFiles()
                if (files.isNotEmpty()) {
                    val recentItems = files
                        .sortedByDescending { it.timestamp }
                        .take(30)
                        .map { f ->
                            UploadItemProgress(
                                fileName = f.fileName.ifEmpty { f.path.substringAfterLast('/') },
                                filePath = f.path,
                                fileSize = f.size,
                                progress = 1f,
                                isCompleted = true,
                                timestamp = f.timestamp
                            )
                        }
                    _recentCompletedList.value = recentItems
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startBackupSession(total: Int, initialMessage: String = "Starting Backup...") {
        _isBackupActive.value = true
        if (_totalCount.value == 0 || !_isBackupActive.value) {
            _completedCount.value = 0
            _currentFileProgress.value = 0f
        }
        _totalCount.value = maxOf(_totalCount.value, total)
        _statusMessage.value = initialMessage
        lastUploadTimeMs = System.currentTimeMillis()
        bytesUploadedInWindow = 0L
        _uploadSpeedBps.value = 0L
        _formattedUploadSpeed.value = "0 KB/s"
    }

    fun updateStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun updateQueue(queue: List<UploadItemProgress>) {
        _pendingFilesQueue.value = queue
        _totalCount.value = _completedCount.value + queue.size
    }

    fun setCurrentUploadingFile(fileName: String, filePath: String, progress: Float = 0.1f) {
        _currentFileName.value = fileName
        _currentFilePath.value = filePath
        _currentFileProgress.value = progress
        _statusMessage.value = "Uploading $fileName..."
    }

    fun updateCurrentProgress(progress: Float) {
        _currentFileProgress.value = progress.coerceIn(0f, 1f)
    }

    fun markFileCompleted(fileName: String, filePath: String, fileSize: Long) {
        removeActiveUpload(filePath)
        _completedCount.value += 1
        _currentFileProgress.value = 1f
        val item = UploadItemProgress(fileName, filePath, fileSize, progress = 1f, isCompleted = true)
        _recentCompletedList.value = (listOf(item) + _recentCompletedList.value.filter { it.filePath != filePath }).take(30)
        
        // Remove completed item from pending queue
        _pendingFilesQueue.value = _pendingFilesQueue.value.filter { it.filePath != filePath }
    }

    fun finishBackupSession(summaryMsg: String = "Backup Completed!") {
        _isBackupActive.value = false
        _statusMessage.value = summaryMsg
        _currentFileName.value = ""
        _currentFilePath.value = ""
        _currentFileProgress.value = 0f
        _uploadSpeedBps.value = 0L
        _formattedUploadSpeed.value = "0 KB/s"
        activeWorkingBotTokens.clear()
        _workingBotsCount.value = 0
        _pendingFilesQueue.value = emptyList()
        _activeUploadsMap.value = emptyMap()
        activeUploadsList.value = emptyList()
    }
}
