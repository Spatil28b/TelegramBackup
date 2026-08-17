package com.example.telegrambackup.ui.explorer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Vibrator
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    try {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(30L)
    } catch (e: Exception) { }
}

@Composable
fun AdvancedMediaViewerDialog(
    initialIndex: Int,
    filesList: List<UploadedFile>,
    onDismiss: () -> Unit,
    onDeleteRequested: (UploadedFile) -> Unit,
    onInfoRequested: (UploadedFile) -> Unit
) {
    if (initialIndex < 0 || initialIndex >= filesList.size) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var currentIndex by remember(initialIndex) { mutableIntStateOf(initialIndex) }

    var targetZoomScale by remember { mutableFloatStateOf(1f) }
    var rawPanX by remember { mutableFloatStateOf(0f) }
    var rawPanY by remember { mutableFloatStateOf(0f) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val animatedZoom by animateFloatAsState(
        targetValue = targetZoomScale,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "ZoomAnim"
    )

    val animatedPanX by animateFloatAsState(
        targetValue = if (targetZoomScale <= 1.15f) 0f else rawPanX,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "PanXAnim"
    )

    val animatedPanY by animateFloatAsState(
        targetValue = if (targetZoomScale <= 1.15f) 0f else rawPanY,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "PanYAnim"
    )

    LaunchedEffect(currentIndex) {
        targetZoomScale = 1f
        rawPanX = 0f
        rawPanY = 0f
        dragAccumulator = 0f
    }

    val currentFile = filesList[currentIndex]
    val nameLower = currentFile.fileName.lowercase()
    val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") || nameLower.endsWith(".heic")
    val isVideo = nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") || nameLower.endsWith(".mov")
    val isPdf = nameLower.endsWith(".pdf")

    val fileSource = if (File(currentFile.path).exists()) File(currentFile.path) else currentFile.telegramFileId

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // TOP ACTION BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(currentFile.fileName, style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text("${currentIndex + 1} of ${filesList.size} • ${formatFileSize(currentFile.size)}", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f)))
                    }

                    Row {
                        IconButton(onClick = { triggerHapticFeedback(context, haptic); onInfoRequested(currentFile) }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = { triggerHapticFeedback(context, haptic); onDeleteRequested(currentFile) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // MAIN CONTENT CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isImage -> {
                            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                targetZoomScale = (targetZoomScale * zoomChange).coerceIn(1f, 5f)
                                if (targetZoomScale > 1.15f) {
                                    rawPanX += panChange.x
                                    rawPanY += panChange.y
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .transformable(state = transformState)
                                    .pointerInput(targetZoomScale) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                targetZoomScale = if (targetZoomScale > 1.2f) 1f else 2.5f
                                                rawPanX = 0f
                                                rawPanY = 0f
                                            }
                                        )
                                    }
                                    .pointerInput(targetZoomScale) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                if (targetZoomScale <= 1.15f) {
                                                    dragAccumulator += dragAmount
                                                    if (dragAccumulator > 120f) {
                                                        if (currentIndex > 0) {
                                                            triggerHapticFeedback(context, haptic)
                                                            currentIndex--
                                                            dragAccumulator = 0f
                                                        }
                                                    } else if (dragAccumulator < -120f) {
                                                        if (currentIndex < filesList.size - 1) {
                                                            triggerHapticFeedback(context, haptic)
                                                            currentIndex++
                                                            dragAccumulator = 0f
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = { dragAccumulator = 0f },
                                            onDragCancel = { dragAccumulator = 0f }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = fileSource,
                                    contentDescription = currentFile.fileName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = animatedZoom,
                                            scaleY = animatedZoom,
                                            translationX = animatedPanX,
                                            translationY = animatedPanY
                                        )
                                )
                            }
                        }

                        isVideo -> {
                            val videoFile = File(currentFile.path)
                            if (videoFile.exists()) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mc = MediaController(ctx)
                                            mc.setAnchorView(this)
                                            setMediaController(mc)
                                            setVideoPath(videoFile.absolutePath)
                                            start()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Video stored on Telegram Cloud", style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                                }
                            }
                        }

                        isPdf -> {
                            PdfViewerComponent(file = File(currentFile.path))
                        }

                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(currentFile.fileName, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfViewerComponent(file: File) {
    val context = LocalContext.current
    var totalPdfPages by remember { mutableIntStateOf(0) }
    var isRendering by remember { mutableStateOf(true) }

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val lazyListState = rememberLazyListState()

    val pageBitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }
    val pdfLock = remember { Any() }

    DisposableEffect(file.absolutePath) {
        isRendering = true
        try {
            if (file.exists()) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                totalPdfPages = renderer.pageCount
            }
        } catch (e: Exception) {
            totalPdfPages = 0
        }
        isRendering = false

        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) { }
            pageBitmapCache.values.forEach { try { it.recycle() } catch (e: Exception) {} }
            pageBitmapCache.clear()
        }
    }

    if (isRendering) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TelegramBlue)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Opening PDF Document...", style = MaterialTheme.typography.titleSmall.copy(color = Color.White))
        }
    } else if (totalPdfPages > 0 && pdfRenderer != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            ) {
                items(totalPdfPages, key = { it }) { pageIdx ->
                    val pageBitmap = pageBitmapCache[pageIdx] ?: remember(pageIdx) {
                        synchronized(pdfLock) {
                            try {
                                if (pdfRenderer != null) {
                                    val page = pdfRenderer!!.openPage(pageIdx)
                                    val scaleFactor = if (page.width > 2000) 1 else 2
                                    val bitmap = Bitmap.createBitmap(page.width * scaleFactor, page.height * scaleFactor, Bitmap.Config.ARGB_8888)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()
                                    if (pageBitmapCache.size > 15) {
                                        val firstKey = pageBitmapCache.keys.firstOrNull()
                                        if (firstKey != null) {
                                            pageBitmapCache.remove(firstKey)?.recycle()
                                        }
                                    }
                                    pageBitmapCache[pageIdx] = bitmap
                                    bitmap
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    ) {
                        if (pageBitmap != null) {
                            Image(
                                bitmap = pageBitmap.asImageBitmap(),
                                contentDescription = "PDF Page ${pageIdx + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TelegramBlue)
                            }
                        }
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Page ${lazyListState.firstVisibleItemIndex + 1} of $totalPdfPages",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(file.name, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(4.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = TelegramEmerald.copy(alpha = 0.2f)) {
                Text("Native PDF Reader Active", style = MaterialTheme.typography.labelSmall.copy(color = TelegramEmerald), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
