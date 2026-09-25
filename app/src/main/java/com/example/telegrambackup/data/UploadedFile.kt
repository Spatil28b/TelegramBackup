package com.example.telegrambackup.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "uploaded_files",
    indices = [
        Index(value = ["isDeleted", "timestamp"]),
        Index(value = ["isDeleted", "isDriveUpload"]),
        Index(value = ["fileName"]),
        Index(value = ["deletedAt"]),
        Index(value = ["telegramFileId"])
    ]
)
data class UploadedFile(
    @PrimaryKey
    var path: String,
    var fileName: String = "",
    var telegramFileId: String = "",
    var timestamp: Long = 0L,
    var size: Long = 0L,
    var messageId: Long = 0L,
    var isDeleted: Boolean = false,
    var deletedAt: Long = 0L,
    var totalChunks: Int = 1,
    var isDriveUpload: Boolean = false,
    var channelId: String = "",
    var botTokenUsed: String = "",
    var chunksJson: String = ""
) {
    /**
     * Returns all Telegram message IDs associated with this file (main message ID + all chunk message IDs).
     */
    fun getAllMessageIds(): List<Long> {
        val ids = mutableSetOf<Long>()
        if (messageId > 0L) ids.add(messageId)
        if (chunksJson.isNotBlank() && chunksJson != "[]" && chunksJson != "null") {
            try {
                val arr = JSONArray(chunksJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val mId = obj.optLong("messageId", 0L).let { if (it > 0L) it else obj.optString("messageId", "0").toLongOrNull() ?: 0L }
                    if (mId > 0L) ids.add(mId)
                }
            } catch (_: Exception) {}
        }
        return ids.toList()
    }

    /**
     * Parses chunksJson into ordered list of (chunkIndex, telegramFileId, botTokenUsed).
     */
    fun parseChunks(): List<Triple<Int, String, String>> {
        return parseChunkInfos().map { Triple(it.chunkIndex, it.telegramFileId, it.botTokenUsed) }
    }

    /**
     * Parses chunksJson into structured FileChunkInfo including messageId.
     */
    fun parseChunkInfos(): List<FileChunkInfo> {
        if (chunksJson.isBlank() || chunksJson == "[]" || chunksJson == "null") return emptyList()
        return try {
            val arr = JSONArray(chunksJson)
            val list = mutableListOf<FileChunkInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idx = obj.optInt("chunkIndex", i + 1)
                val fileId = obj.optString("telegramFileId", "").ifBlank { obj.optString("fileId", "") }
                val token = obj.optString("botTokenUsed", "").ifBlank { obj.optString("botToken", "") }
                val mId = obj.optLong("messageId", 0L).let { if (it > 0L) it else obj.optString("messageId", "0").toLongOrNull() ?: 0L }
                if (fileId.isNotBlank()) {
                    list.add(FileChunkInfo(idx, fileId, token, mId))
                }
            }
            list.sortedBy { it.chunkIndex }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializes a list of chunks into chunksJson string (preserves Triple compatibility).
     */
    fun setChunks(chunks: List<Triple<Int, String, String>>) {
        setChunkInfos(chunks.map { FileChunkInfo(it.first, it.second, it.third, 0L) })
    }

    /**
     * Serializes a list of chunks with message IDs into chunksJson string.
     */
    fun setChunkInfos(chunks: List<FileChunkInfo>) {
        if (chunks.isEmpty()) {
            chunksJson = "[]"
            return
        }
        val arr = JSONArray()
        for (c in chunks.sortedBy { it.chunkIndex }) {
            val obj = JSONObject().apply {
                put("chunkIndex", c.chunkIndex)
                put("telegramFileId", c.telegramFileId)
                put("botTokenUsed", c.botTokenUsed)
                if (c.messageId > 0L) put("messageId", c.messageId)
            }
            arr.put(obj)
        }
        chunksJson = arr.toString()
        totalChunks = maxOf(totalChunks, chunks.size)
    }
}

/**
 * Data holder for uploaded file chunk info including Telegram message_id.
 */
data class FileChunkInfo(
    val chunkIndex: Int,
    val telegramFileId: String,
    val botTokenUsed: String = "",
    val messageId: Long = 0L
)


