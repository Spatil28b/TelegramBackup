package com.example.telegrambackup.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.telegrambackup.data.UploadedFile

object FileViewerHelper {

    /**
     * Checks if a file is an audio/music format (MP3, WAV, FLAC, AAC, etc.)
     */
    fun isAudioFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma", "amr", "mid")
    }

    /**
     * Checks if a file is a video format (MP4, MKV, AVI, MOV, WEBM, etc.)
     */
    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "flv", "m4v", "ts")
    }

    /**
     * Checks if a file is an image format (JPG, PNG, GIF, WEBP, etc.)
     */
    fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic", "heif")
    }

    /**
     * Checks if a file is a PDF document
     */
    fun isPdfFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "pdf"
    }

    /**
     * Checks if a file is a text or document format
     */
    fun isDocumentFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml", "html", "htm", "rtf", "odt", "ods", "epub", "md", "log", "kt", "java", "py", "sh")
    }

    /**
     * Resolves the actual physical file on the device, even if it has moved across
     * directories, storage volumes, or was saved with a relative/cloud path.
     */
    fun findLocalFile(filePath: String, fileName: String): File? {
        if (filePath.isNotBlank()) {
            val direct = File(filePath)
            if (direct.exists() && direct.length() > 0) return direct

            // Try substituting Internal Storage / /storage/emulated/0
            val emulatedPath = filePath.replaceFirst(Regex("^(?:/)?(?:Internal Storage|internal storage)/"), "/storage/emulated/0/")
            val emulatedFile = File(emulatedPath)
            if (emulatedFile.exists() && emulatedFile.length() > 0) return emulatedFile

            val sdcardPath = filePath.replaceFirst(Regex("^(?:/)?(?:sdcard)/"), "/storage/emulated/0/")
            val sdcardFile = File(sdcardPath)
            if (sdcardFile.exists() && sdcardFile.length() > 0) return sdcardFile
        }

        if (fileName.isBlank()) return null

        // Search common Android user directories by exact filename
        val candidates = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/DCIM/Camera"),
            File("/storage/emulated/0/Pictures/Telegram"),
            File("/storage/emulated/0/Telegram/Telegram Documents")
        )

        for (dir in candidates) {
            if (dir != null && dir.exists() && dir.isDirectory) {
                val candidate = File(dir, fileName)
                if (candidate.exists() && candidate.length() > 0) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Opens a file using the system's default viewer for its MIME type.
     * Supports local files (via FileProvider), content URIs, cloud cache, and URL-based files.
     */
    fun openFile(context: Context, filePath: String, fileName: String, telegramFileId: String = "") {
        try {
            // 1. Content URIs (SAF)
            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                val mimeType = getMimeType(fileName)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Open ${getFileTypeLabel(fileName)}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                return
            }

            // 2. HTTP/HTTPS URLs
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                openFileFromUrl(context, filePath, fileName)
                return
            }

            // 3. Local Filesystem (Direct or Resolved via findLocalFile)
            val resolvedLocal = findLocalFile(filePath, fileName)
            if (resolvedLocal != null && resolvedLocal.exists() && resolvedLocal.length() > 0) {
                if (resolvedLocal.absolutePath != filePath && filePath.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                            val existing = db.uploadedFileDao().getFileByPath(filePath)
                            if (existing != null) {
                                db.uploadedFileDao().deleteByPath(filePath)
                                existing.path = resolvedLocal.absolutePath
                                db.uploadedFileDao().insert(existing)
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (!FileEncryptor.isLikelyPlainFile(resolvedLocal, fileName)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        FileEncryptor.ensureDecrypted(resolvedLocal, context, fileName)
                        withContext(Dispatchers.Main) {
                            launchFileProviderIntent(context, resolvedLocal, fileName)
                        }
                    }
                    return
                }
                launchFileProviderIntent(context, resolvedLocal, fileName)
                return
            }

            // 4. Search in Cloud Preview Cache
            val cacheDir = File(context.cacheDir, "cloud_preview_cache")
            if (cacheDir.exists()) {
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
                val cachedMatches = cacheDir.listFiles { f ->
                    f.name.endsWith(safeName) || (telegramFileId.isNotBlank() && f.name.startsWith("${telegramFileId.hashCode()}"))
                }
                val found = cachedMatches?.firstOrNull { it.exists() && it.length() > 0 }
                if (found != null) {
                    if (!FileEncryptor.isLikelyPlainFile(found, fileName)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            FileEncryptor.ensureDecrypted(found, context, fileName)
                            withContext(Dispatchers.Main) {
                                launchFileProviderIntent(context, found, fileName)
                            }
                        }
                        return
                    }
                    launchFileProviderIntent(context, found, fileName)
                    return
                }
            }

            // 5. On-Demand Cloud Fetch & Open if telegramFileId is provided (or if db record exists)
            val effectiveTgId = if (telegramFileId.isNotBlank()) telegramFileId else {
                runCatching {
                    val db = com.example.telegrambackup.data.AppDatabase.getDatabase(context)
                    val rec = db.uploadedFileDao().getFileByPath(filePath)
                        ?: db.uploadedFileDao().getFileByName(fileName)
                    rec?.telegramFileId ?: ""
                }.getOrDefault("")
            }

            if (effectiveTgId.isNotBlank() && effectiveTgId != "DIR_MARKER") {
                Toast.makeText(context, "Preparing $fileName for viewing...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    val downloaded = TelegramMediaResolver.fetchToTempCache(context, effectiveTgId, fileName)
                    if (downloaded != null && downloaded.exists()) {
                        FileEncryptor.ensureDecrypted(downloaded, context, fileName)
                    }
                    withContext(Dispatchers.Main) {
                        if (downloaded != null && downloaded.exists()) {
                            launchFileProviderIntent(context, downloaded, fileName)
                        } else {
                            Toast.makeText(context, "Could not load $fileName from cloud", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return
            }

            Toast.makeText(context, "File unavailable: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchFileProviderIntent(context: Context, file: File, fileName: String) {
        val sharedDir = File(context.cacheDir, "shared_provider_cache").apply { if (!exists()) mkdirs() }
        val sharedFile = File(sharedDir, fileName)
        val fileForUri = try {
            file.copyTo(sharedFile, overwrite = true)
            FileEncryptor.ensureDecrypted(sharedFile, context, fileName)
            if (sharedFile.exists() && sharedFile.length() > 0) sharedFile else file
        } catch (_: Exception) {
            FileEncryptor.ensureDecrypted(file, context, fileName)
            file
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileForUri
            )
        } catch (e: Exception) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val mimeType = getMimeType(fileName)
        val clip = android.content.ClipData.newRawUri(fileName, uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}

        val chooser = Intent.createChooser(intent, "Open ${getFileTypeLabel(fileName)}").apply {
            clipData = clip
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallbackIntent, "Open with...").apply {
                clipData = clip
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    /**
     * Shares a file to other apps (WhatsApp, Telegram, Gmail, etc.) using system ACTION_SEND intent.
     * Supports local files, cloud preview cache, and on-demand cloud retrieval.
     */
    fun shareFile(context: Context, file: UploadedFile) {
        val fileName = file.fileName
        val filePath = file.path
        val telegramFileId = file.telegramFileId

        val localFile = File(filePath)
        if (localFile.exists() && localFile.length() > 0) {
            launchShareIntent(context, localFile, fileName)
            return
        }

        // Check in Cloud Preview Cache
        val cacheDir = File(context.cacheDir, "cloud_preview_cache")
        if (cacheDir.exists()) {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
            val cachedMatches = cacheDir.listFiles { f ->
                f.name.endsWith(safeName) || (telegramFileId.isNotBlank() && f.name.startsWith("${telegramFileId.hashCode()}"))
            }
            val found = cachedMatches?.firstOrNull { it.exists() && it.length() > 0 }
            if (found != null) {
                launchShareIntent(context, found, fileName)
                return
            }
        }

        // On-demand fetch from cloud
        if (telegramFileId.isNotBlank() && telegramFileId != "DIR_MARKER") {
            Toast.makeText(context, "Preparing $fileName to share...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                val downloaded = TelegramMediaResolver.fetchToTempCache(context, telegramFileId, fileName)
                if (downloaded != null && downloaded.exists()) {
                    FileEncryptor.ensureDecrypted(downloaded, context, fileName)
                }
                withContext(Dispatchers.Main) {
                    if (downloaded != null && downloaded.exists()) {
                        launchShareIntent(context, downloaded, fileName)
                    } else {
                        Toast.makeText(context, "Could not load $fileName from cloud to share", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        Toast.makeText(context, "File unavailable to share: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun launchShareIntent(context: Context, file: File, fileName: String) {
        val sharedDir = File(context.cacheDir, "shared_provider_cache").apply { if (!exists()) mkdirs() }
        val sharedFile = File(sharedDir, fileName)
        val fileForUri = try {
            file.copyTo(sharedFile, overwrite = true)
            FileEncryptor.ensureDecrypted(sharedFile, context, fileName)
            if (sharedFile.exists() && sharedFile.length() > 0) sharedFile else file
        } catch (_: Exception) {
            FileEncryptor.ensureDecrypted(file, context, fileName)
            file
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileForUri
            )
        } catch (e: Exception) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val mimeType = getMimeType(fileName)
        val clip = android.content.ClipData.newRawUri(fileName, uri)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            val resInfoList = context.packageManager.queryIntentActivities(sendIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}

        val chooser = Intent.createChooser(sendIntent, "Send $fileName").apply {
            clipData = clip
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
            val chooser = Intent.createChooser(intent, "Open ${getFileTypeLabel(fileName)}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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
            "md" -> "text/markdown"
            "log" -> "text/plain"

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
