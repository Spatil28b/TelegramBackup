package com.example.telegrambackup.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "uploaded_files")
public class UploadedFile {
    @PrimaryKey
    @NonNull
    private String path;
    private String fileName;
    private String telegramFileId;
    private long timestamp;
    private long size;
    private long messageId;
    private boolean isDeleted;
    private long deletedAt;
    private int totalChunks;
    private boolean isDriveUpload;

    public UploadedFile(@NonNull String path, String fileName, String telegramFileId, long timestamp, long size, long messageId) {
        this.path = path;
        this.fileName = fileName != null ? fileName : "";
        this.telegramFileId = telegramFileId != null ? telegramFileId : "";
        this.timestamp = timestamp;
        this.size = size;
        this.messageId = messageId;
        this.isDeleted = false;
        this.deletedAt = 0;
        this.totalChunks = 1;
        this.isDriveUpload = false;
    }

    @NonNull
    public String getPath() { return path; }
    public void setPath(@NonNull String path) { this.path = path; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getTelegramFileId() { return telegramFileId; }
    public void setTelegramFileId(String telegramFileId) { this.telegramFileId = telegramFileId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public boolean isDriveUpload() { return isDriveUpload; }
    public void setDriveUpload(boolean driveUpload) { isDriveUpload = driveUpload; }
}
