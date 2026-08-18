package com.example.telegrambackup.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal LRU thumbnail cache for video frames & PDF previews
 */
private val thumbnailMemoryCache = object : LruCache<String, Bitmap>(50 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount
    }
}

/**
 * Smart Media & File Thumbnail View:
 * - Images (JPG, PNG, WEBP, GIF): Renders actual image preview with smooth subcompose loading.
 * - Videos (MP4, MKV, AVI, MOV): Renders actual video frame thumbnail + subtle play badge.
 * - PDFs: Renders native PDF first-page preview when file is available locally.
 * - Non-previewable formats / Cloud fallbacks: Seamlessly displays the sleek Windows 11 Fluent Blue File Icon.
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
    val nameLower = fileName.lowercase()
    val localFile = remember(filePath) { if (filePath.isNotEmpty()) File(filePath) else null }
    val localFileExists = localFile != null && localFile.exists() && localFile.length() > 0

    val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") ||
            nameLower.endsWith(".png") || nameLower.endsWith(".webp") ||
            nameLower.endsWith(".gif") || nameLower.endsWith(".bmp")

    val isVideo = nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") ||
            nameLower.endsWith(".mov") || nameLower.endsWith(".avi") ||
            nameLower.endsWith(".webm") || nameLower.endsWith(".3gp")

    val isPdf = nameLower.endsWith(".pdf")

    when {
        // 1. IMAGE PREVIEW
        isImage -> {
            val imageSource: Any? = when {
                localFileExists -> localFile
                filePath.isNotEmpty() && File(filePath).exists() -> File(filePath)
                telegramFileId.isNotEmpty() -> telegramFileId
                else -> null
            }

            if (imageSource != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = fileName,
                    contentScale = contentScale,
                    modifier = modifier
                        .fillMaxSize()
                        .clip(shape),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            OsFileIcon(fileName = fileName, size = iconSize)
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            OsFileIcon(fileName = fileName, size = iconSize)
                        }
                    }
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

        // 2. VIDEO THUMBNAIL PREVIEW
        isVideo && localFileExists -> {
            var videoBitmap by remember(filePath) { mutableStateOf<Bitmap?>(thumbnailMemoryCache.get(filePath)) }

            LaunchedEffect(filePath) {
                if (videoBitmap == null && localFile != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(localFile.absolutePath)
                            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: retriever.frameAtTime
                            retriever.release()
                            if (frame != null) {
                                thumbnailMemoryCache.put(filePath, frame)
                                withContext(Dispatchers.Main) {
                                    videoBitmap = frame
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
        isPdf && localFileExists -> {
            var pdfBitmap by remember(filePath) { mutableStateOf<Bitmap?>(thumbnailMemoryCache.get("pdf_$filePath")) }

            LaunchedEffect(filePath) {
                if (pdfBitmap == null && localFile != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(pfd)
                            if (renderer.pageCount > 0) {
                                val page = renderer.openPage(0)
                                val bmp = Bitmap.createBitmap(page.width.coerceAtMost(512), page.height.coerceAtMost(512), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                renderer.close()
                                pfd.close()
                                thumbnailMemoryCache.put("pdf_$filePath", bmp)
                                withContext(Dispatchers.Main) {
                                    pdfBitmap = bmp
                                }
                            } else {
                                renderer.close()
                                pfd.close()
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

        // 4. ALL OTHER FILES / CLOUD FALLBACK
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
