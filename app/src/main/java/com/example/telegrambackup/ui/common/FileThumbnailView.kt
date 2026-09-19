package com.example.telegrambackup.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.telegrambackup.utils.FileEncryptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.telegrambackup.utils.TelegramMediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal LRU thumbnail cache for video frames & PDF previews
 */
private val thumbnailMemoryCache = object : LruCache<String, Bitmap>(60 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount
    }
}

/**
 * Resolves any local file path across internal storage, absolute paths, standard media folders, or preview cache.
 */
fun resolveLocalDiskFile(context: Context, filePath: String, telegramFileId: String, fileName: String): File? {
    if (filePath.isNotBlank()) {
        // 1. Direct path check
        val f1 = File(filePath)
        if (f1.exists() && f1.length() > 0) return f1

        val clean = filePath.replace("\\", "/").trim()
        val simpleName = if (fileName.isNotBlank()) fileName.trim() else clean.substringAfterLast('/')

        // 2. Storage prefixes
        if (clean.startsWith("Internal Storage/")) {
            val rel = clean.removePrefix("Internal Storage/")
            val f2 = File(Environment.getExternalStorageDirectory(), rel)
            if (f2.exists() && f2.length() > 0) return f2
        } else if (clean.startsWith("/storage/emulated/0/")) {
            val f3 = File(clean)
            if (f3.exists() && f3.length() > 0) return f3
        } else {
            val f4 = File(Environment.getExternalStorageDirectory(), clean)
            if (f4.exists() && f4.length() > 0) return f4
        }

        // 3. Fallback scan in primary Android media directories
        try {
            val extDir = Environment.getExternalStorageDirectory()
            val candidates = listOf(
                File(extDir, "DCIM/Camera/$simpleName"),
                File(extDir, "DCIM/$simpleName"),
                File(extDir, "Pictures/$simpleName"),
                File(extDir, "Pictures/Screenshots/$simpleName"),
                File(extDir, "Download/$simpleName"),
                File(extDir, "Documents/$simpleName"),
                File(extDir, "Movies/$simpleName"),
                File(extDir, "WhatsApp/Media/WhatsApp Images/$simpleName"),
                File(extDir, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/$simpleName")
            )
            for (c in candidates) {
                if (c.exists() && c.length() > 0) return c
            }
        } catch (_: Exception) {}
    }

    // 4. Check cached preview from previous downloads
    if (telegramFileId.isNotBlank() && telegramFileId != "DIR_MARKER" && !telegramFileId.startsWith("NOTE_MARKER")) {
        val cacheDir = File(context.cacheDir, "cloud_preview_cache")
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file" }
        val cachedFile = File(cacheDir, "${telegramFileId.hashCode()}_$safeName")
        if (cachedFile.exists() && cachedFile.length() > 0) return cachedFile
    }

    return null
}

/**
 * Smart Multi-Format File Thumbnail & Preview View:
 * - Images (JPG, JPEG, PNG, WEBP, GIF, BMP, HEIC, JFIF): Renders real image preview from local storage or Telegram Cloud.
 * - Videos (MP4, MKV, AVI, MOV): Renders actual video frame thumbnail + subtle play badge.
 * - PDFs: Renders native PDF first-page preview from local storage or cloud cache.
 * - Fallbacks & Non-previewable formats: Modern Windows 11 Fluent Blue File Icon.
 */
@Composable
fun FileThumbnailView(
    modifier: Modifier = Modifier,
    fileName: String,
    filePath: String = "",
    telegramFileId: String = "",
    iconSize: Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val nameLower = fileName.trim().lowercase()
    val pathLower = filePath.trim().lowercase()

    val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") ||
            nameLower.endsWith(".jfif") || nameLower.endsWith(".jpe") ||
            nameLower.endsWith(".png") || nameLower.endsWith(".webp") ||
            nameLower.endsWith(".gif") || nameLower.endsWith(".bmp") ||
            nameLower.endsWith(".heic") || nameLower.endsWith(".heif") ||
            pathLower.endsWith(".jpg") || pathLower.endsWith(".jpeg") ||
            pathLower.endsWith(".png") || pathLower.endsWith(".webp")

    val isVideo = nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") ||
            nameLower.endsWith(".mov") || nameLower.endsWith(".avi") ||
            nameLower.endsWith(".webm") || nameLower.endsWith(".3gp") ||
            pathLower.endsWith(".mp4") || pathLower.endsWith(".mkv")

    val isPdf = nameLower.endsWith(".pdf") || pathLower.endsWith(".pdf")

    val localFile = remember(filePath, telegramFileId, fileName) {
        resolveLocalDiskFile(context, filePath, telegramFileId, fileName)
    }
    val localFileExists = localFile != null && localFile.exists() && localFile.length() > 0

    when {
        // 1. IMAGE PREVIEW (Local Storage or Cloud Bitmap)
        isImage -> {
            var cloudBitmap by remember(telegramFileId) { mutableStateOf<Bitmap?>(null) }
            var coilFailed by remember(filePath, telegramFileId) { mutableStateOf(false) }

            LaunchedEffect(telegramFileId, localFileExists, coilFailed) {
                if ((!localFileExists || coilFailed) && telegramFileId.isNotBlank() && telegramFileId != "DIR_MARKER" && !telegramFileId.startsWith("NOTE_MARKER")) {
                    val bmp = TelegramMediaResolver.resolveImageBitmap(context, telegramFileId, fileName)
                    if (bmp != null) {
                        cloudBitmap = bmp
                    }
                }
            }

            val imageModel: Any? = when {
                localFileExists -> localFile
                filePath.startsWith("content://") -> Uri.parse(filePath)
                filePath.startsWith("file://") -> Uri.parse(filePath)
                filePath.startsWith("http://") || filePath.startsWith("https://") -> filePath
                else -> null
            }

            if (imageModel != null && !coilFailed) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = fileName,
                    contentScale = contentScale,
                    modifier = modifier
                        .fillMaxSize()
                        .clip(shape),
                    loading = {
                        ShimmerBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = shape
                        )
                    },
                    error = {
                        LaunchedEffect(Unit) {
                            coilFailed = true
                        }
                        if (cloudBitmap != null) {
                            Image(
                                bitmap = cloudBitmap!!.asImageBitmap(),
                                contentDescription = fileName,
                                contentScale = contentScale,
                                modifier = Modifier.fillMaxSize().clip(shape)
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                OsFileIcon(fileName = fileName, size = iconSize)
                            }
                        }
                    }
                )
            } else if (cloudBitmap != null) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = cloudBitmap!!.asImageBitmap(),
                        contentDescription = fileName,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    OsFileIcon(fileName = fileName, size = iconSize)
                }
            }
        }

        // 2. VIDEO THUMBNAIL PREVIEW
        isVideo -> {
            var videoBitmap by remember(filePath, telegramFileId) { mutableStateOf<Bitmap?>(thumbnailMemoryCache.get(if (localFileExists) filePath else "vid_$telegramFileId")) }

            LaunchedEffect(filePath, telegramFileId, localFileExists) {
                if (videoBitmap == null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val activeFile = if (localFileExists) localFile else TelegramMediaResolver.fetchToTempCache(context, telegramFileId, fileName)
                            if (activeFile != null && activeFile.exists()) {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(activeFile.absolutePath)
                                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                    ?: retriever.frameAtTime
                                retriever.release()
                                if (frame != null) {
                                    thumbnailMemoryCache.put(if (localFileExists) filePath else "vid_$telegramFileId", frame)
                                    withContext(Dispatchers.Main) {
                                        videoBitmap = frame
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (videoBitmap != null) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = videoBitmap!!.asImageBitmap(),
                        contentDescription = fileName,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Play Badge Overlay
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (localFileExists || telegramFileId.isNotBlank()) {
                ShimmerBox(
                    modifier = modifier.fillMaxSize(),
                    shape = shape
                )
            } else {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    OsFileIcon(fileName = fileName, size = iconSize)
                }
            }
        }

        // 3. PDF FIRST-PAGE PREVIEW
        isPdf -> {
            var pdfBitmap by remember(filePath, telegramFileId) { mutableStateOf<Bitmap?>(thumbnailMemoryCache.get(if (localFileExists) "pdf_$filePath" else "pdf_$telegramFileId")) }

            LaunchedEffect(filePath, telegramFileId, localFileExists) {
                if (pdfBitmap == null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val activeFile = if (localFileExists) localFile else TelegramMediaResolver.fetchToTempCache(context, telegramFileId, fileName)
                            if (activeFile != null && activeFile.exists() && activeFile.length() > 0) {
                                val workingDir = File(context.cacheDir, "pdf_thumb_work").apply { if (!exists()) mkdirs() }
                                val workingFile = File(workingDir, "thumb_${activeFile.name.hashCode() and 0x7FFFFFFF}.pdf")
                                try {
                                    activeFile.copyTo(workingFile, overwrite = true)
                                    FileEncryptor.ensureDecrypted(workingFile, context, fileName)
                                    val pfd = ParcelFileDescriptor.open(workingFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                    val renderer = PdfRenderer(pfd)
                                    if (renderer.pageCount > 0) {
                                        val page = renderer.openPage(0)
                                        val bmp = Bitmap.createBitmap(page.width.coerceAtMost(512), page.height.coerceAtMost(512), Bitmap.Config.ARGB_8888)
                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()
                                        renderer.close()
                                        pfd.close()
                                        thumbnailMemoryCache.put(if (localFileExists) "pdf_$filePath" else "pdf_$telegramFileId", bmp)
                                        withContext(Dispatchers.Main) {
                                            pdfBitmap = bmp
                                        }
                                    } else {
                                        renderer.close()
                                        pfd.close()
                                    }
                                } finally {
                                    if (workingFile.exists()) workingFile.delete()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (pdfBitmap != null) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = pdfBitmap!!.asImageBitmap(),
                        contentDescription = fileName,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    OsFileIcon(fileName = fileName, size = iconSize)
                }
            }
        }

        // 4. ALL OTHER FILES / FALLBACK
        else -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OsFileIcon(fileName = fileName, size = iconSize)
            }
        }
    }
}
