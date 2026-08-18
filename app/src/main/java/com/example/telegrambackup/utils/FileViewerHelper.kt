package com.example.telegrambackup.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileViewerHelper {

    /**
     * Checks if a file is an audio/music format (MP3, WAV, FLAC, AAC, etc.)
     */
    fun isAudioFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma", "amr", "mid")
    }

    /**
     * Opens a file using the system's default viewer for its MIME type.
     * Supports local files (via FileProvider) and URL-based files (direct Intent).
     */
    fun openFile(context: Context, filePath: String, fileName: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "File not found: $fileName", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = getMimeType(fileName)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open ${getFileTypeLabel(fileName)}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                context.startActivity(chooser)
            } catch (anfe: Exception) {
                // Fallback to generic */* MIME type
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Open with...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens a file from a URL (streaming) using system Intent.
     */
    fun openFileFromUrl(context: Context, url: String, fileName: String) {
        try {
            val mimeType = getMimeType(fileName)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open ${getFileTypeLabel(fileName)}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Cannot open link: ${e2.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Returns the MIME type for a file based on its extension.
     */
    fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            // Images
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "heic", "heif" -> "image/heif"

            // Videos
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"

            // Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "wma" -> "audio/x-ms-wma"

            // Documents
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"

            // Archives
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"

            // APK
            "apk" -> "application/vnd.android.package-archive"

            else -> "*/*"
        }
    }

    /**
     * Returns a human-readable label for the file type.
     */
    fun getFileTypeLabel(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif" -> "Image"
            "mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "flv" -> "Video"
            "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma" -> "Audio"
            "pdf" -> "PDF Document"
            "doc", "docx" -> "Word Document"
            "xls", "xlsx" -> "Spreadsheet"
            "ppt", "pptx" -> "Presentation"
            "txt" -> "Text File"
            "csv" -> "CSV File"
            "html", "htm" -> "Web Page"
            "zip", "rar", "7z", "tar", "gz" -> "Archive"
            "apk" -> "Android App"
            else -> "File"
        }
    }
}
