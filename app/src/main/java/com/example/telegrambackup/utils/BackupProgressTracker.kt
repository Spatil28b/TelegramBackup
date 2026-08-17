package com.example.telegrambackup.utils

import android.content.Context
import com.example.telegrambackup.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class UploadItemProgress(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object BackupProgressTracker {
    private val _isBackupActive = MutableStateFlow(false)
    val isBackupActive: StateFlow<Boolean> = _isBackupActive.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle / Protection Active")
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
        _pendingFilesQueue.value = emptyList()
    }
}
