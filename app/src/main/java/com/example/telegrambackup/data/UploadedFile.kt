package com.example.telegrambackup.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    var botTokenUsed: String = ""
)
