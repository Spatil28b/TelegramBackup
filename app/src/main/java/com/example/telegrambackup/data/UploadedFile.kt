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
     * Parses chunksJson into ordered list of (chunkIndex, telegramFileId, botTokenUsed).
     */
    fun parseChunks(): List<Triple<Int, String, String>> {
        if (chunksJson.isBlank() || chunksJson == "[]" || chunksJson == "null") return emptyList()
        return try {
            val arr = JSONArray(chunksJson)
            val list = mutableListOf<Triple<Int, String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idx = obj.optInt("chunkIndex", i + 1)
                val fileId = obj.optString("telegramFileId", "").ifBlank { obj.optString("fileId", "") }
                val token = obj.optString("botTokenUsed", "").ifBlank { obj.optString("botToken", "") }
                if (fileId.isNotBlank()) {
                    list.add(Triple(idx, fileId, token))
                }
            }
            list.sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializes a list of chunks into chunksJson string.
     */
    fun setChunks(chunks: List<Triple<Int, String, String>>) {
        if (chunks.isEmpty()) {
            chunksJson = "[]"
            return
        }
        val arr = JSONArray()
        for (c in chunks.sortedBy { it.first }) {
            val obj = JSONObject().apply {
                put("chunkIndex", c.first)
                put("telegramFileId", c.second)
                put("botTokenUsed", c.third)
            }
            arr.put(obj)
        }
        chunksJson = arr.toString()
        totalChunks = maxOf(totalChunks, chunks.size)
    }
}

