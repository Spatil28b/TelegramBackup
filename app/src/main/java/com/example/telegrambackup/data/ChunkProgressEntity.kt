package com.example.telegrambackup.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "chunk_upload_progress",
    primaryKeys = ["sourcePath", "chunkIndex"]
)
data class ChunkProgressEntity(
    val sourcePath: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val telegramFileId: String,
    val messageId: Long,
    val chunkSize: Long,
    val status: String = "COMPLETED",
    val botIdUsed: String = "",
    val channelId: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
) {
    @get:Ignore
    val botTokenUsed: String
        get() = botIdUsed

    @Ignore
    constructor(
        sourcePath: String,
        chunkIndex: Int,
        totalChunks: Int,
        telegramFileId: String,
        messageId: Long,
        chunkSize: Long,
        status: String = "COMPLETED",
        botTokenUsed: String = "",
        channelId: String = "",
        uploadedAt: Long = System.currentTimeMillis(),
        ignoredDummy: Boolean = true
    ) : this(
        sourcePath = sourcePath,
        chunkIndex = chunkIndex,
        totalChunks = totalChunks,
        telegramFileId = telegramFileId,
        messageId = messageId,
        chunkSize = chunkSize,
        status = status,
        botIdUsed = TelegramBotEntity.deriveBotId(botTokenUsed),
        channelId = channelId,
        uploadedAt = uploadedAt
    )
}

@Dao
interface ChunkProgressDao {
    @Query("SELECT * FROM chunk_upload_progress WHERE sourcePath = :sourcePath ORDER BY chunkIndex ASC")
    fun getUploadedChunksForFile(sourcePath: String): List<ChunkProgressEntity>

    @Query("SELECT * FROM chunk_upload_progress WHERE sourcePath = :sourcePath AND chunkIndex = :chunkIndex LIMIT 1")
    fun getChunk(sourcePath: String, chunkIndex: Int): ChunkProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(chunk: ChunkProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(chunks: List<ChunkProgressEntity>)

    @Query("DELETE FROM chunk_upload_progress WHERE sourcePath = :sourcePath AND chunkIndex = :chunkIndex")
    fun deleteChunk(sourcePath: String, chunkIndex: Int)

    @Query("DELETE FROM chunk_upload_progress WHERE sourcePath = :sourcePath")
    fun deleteChunksForFile(sourcePath: String)

    @Query("UPDATE chunk_upload_progress SET sourcePath = :newPath WHERE sourcePath = :oldPath")
    fun updateSourcePath(oldPath: String, newPath: String)

    @Query("SELECT MAX(messageId) FROM chunk_upload_progress")
    fun getMaxMessageId(): Long?

    @Query("SELECT * FROM chunk_upload_progress")
    fun getAllChunks(): List<ChunkProgressEntity>

    @Query("DELETE FROM chunk_upload_progress WHERE sourcePath NOT IN (SELECT path FROM uploaded_files)")
    fun pruneOrphanedChunks()

    @Query("DELETE FROM chunk_upload_progress")
    fun deleteAll()
}
