package com.example.telegrambackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UploadedFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(file: UploadedFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(files: List<UploadedFile>)

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllFiles(): List<UploadedFile>

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllFilesFlow(): kotlinx.coroutines.flow.Flow<List<UploadedFile>>

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 AND isDriveUpload = 1 ORDER BY timestamp DESC")
    fun getDriveFiles(): List<UploadedFile>

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 AND isDriveUpload = 1 ORDER BY timestamp DESC")
    fun getDriveFilesFlow(): kotlinx.coroutines.flow.Flow<List<UploadedFile>>

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashFiles(): List<UploadedFile>

    @Query("SELECT * FROM uploaded_files WHERE path = :path LIMIT 1")
    fun getFileByPath(path: String): UploadedFile?

    @Query("SELECT COUNT(*) FROM uploaded_files WHERE isDeleted = 0")
    fun getCount(): Int

    @Query("UPDATE uploaded_files SET isDeleted = 1, deletedAt = :now WHERE path = :path OR (telegramFileId != '' AND telegramFileId = :telegramFileId)")
    fun softDelete(path: String, telegramFileId: String, now: Long)

    @Query("UPDATE uploaded_files SET isDeleted = 0, deletedAt = 0 WHERE path = :path OR (telegramFileId != '' AND telegramFileId = :telegramFileId)")
    fun restoreFile(path: String, telegramFileId: String)

    @Query("DELETE FROM uploaded_files WHERE telegramFileId = :telegramFileId AND telegramFileId != ''")
    fun deleteByTelegramFileId(telegramFileId: String)

    @Query("DELETE FROM uploaded_files WHERE path = :path")
    fun deleteByPath(path: String)

    @Query("DELETE FROM uploaded_files WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :cutoffTimestamp")
    fun deleteExpiredTrash(cutoffTimestamp: Long)

    @Query("SELECT MAX(messageId) FROM uploaded_files")
    fun getMaxMessageId(): Long?

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 AND (fileName LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%') ORDER BY timestamp DESC LIMIT :limit")
    fun searchFiles(query: String, limit: Int = 200): List<UploadedFile>

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 AND (:query = '' OR fileName LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%') AND size >= :minSize AND (:maxSize <= 0 OR size <= :maxSize) AND timestamp >= :fromTime AND (:toTime <= 0 OR timestamp <= :toTime) ORDER BY timestamp DESC LIMIT :limit")
    fun searchFiltered(query: String, minSize: Long = 0L, maxSize: Long = 0L, fromTime: Long = 0L, toTime: Long = 0L, limit: Int = 300): List<UploadedFile>

    @Query("SELECT * FROM uploaded_files WHERE isDeleted = 0 AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    fun getFilesByTimeRange(startTime: Long, endTime: Long): List<UploadedFile>

    @Query("SELECT * FROM uploaded_files")
    fun getAllRawFiles(): List<UploadedFile>

    @Query("DELETE FROM uploaded_files")
    fun deleteAll()
}
