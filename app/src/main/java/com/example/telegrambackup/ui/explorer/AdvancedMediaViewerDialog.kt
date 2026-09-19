package com.example.telegrambackup.ui.explorer

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.common.OsFileIcon
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.TelegramMediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    try {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(30L)
    } catch (_: Exception) { }
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
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
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
    val isImage = nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".gif") || nameLower.endsWith(".bmp")
    val isVideo = nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") || nameLower.endsWith(".mov") || nameLower.endsWith(".webm") || nameLower.endsWith(".3gp")
    val isPdf = nameLower.endsWith(".pdf")
    val isTextFile = nameLower.endsWith(".txt") || nameLower.endsWith(".log") || nameLower.endsWith(".json") || nameLower.endsWith(".csv") || nameLower.endsWith(".md") || nameLower.endsWith(".xml") || nameLower.endsWith(".kt") || nameLower.endsWith(".java") || nameLower.endsWith(".html") || nameLower.endsWith(".css") || nameLower.endsWith(".js") || nameLower.endsWith(".py") || nameLower.endsWith(".sh") || nameLower.endsWith(".yml") || nameLower.endsWith(".yaml")

    val localFile = remember(currentFile.path) { File(currentFile.path) }
    val localFileExists = localFile.exists() && localFile.length() > 0

    // Cloud streaming / caching state
    var resolvedDirectUrl by remember(currentFile.telegramFileId) { mutableStateOf<String?>(null) }
    var cachedDownloadedFile by remember(currentFile.telegramFileId) { mutableStateOf<File?>(null) }
    var isCloudResolving by remember(currentFile.telegramFileId) { mutableStateOf(false) }

    LaunchedEffect(currentFile) {
        if (!localFileExists && currentFile.telegramFileId.isNotBlank() && currentFile.telegramFileId != "DIR_MARKER") {
            isCloudResolving = true
            val directUrl = TelegramMediaResolver.resolveDirectUrl(context, currentFile.telegramFileId)
            resolvedDirectUrl = directUrl

            val cached = TelegramMediaResolver.fetchToTempCache(context, currentFile)
            if (cached != null) {
                cachedDownloadedFile = cached
            }
            isCloudResolving = false
        }
    }

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

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(currentFile.fileName, style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text("${currentIndex + 1} of ${filesList.size} • ${formatFileSize(currentFile.size)}", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f)))
                    }

                    Row {
                        IconButton(onClick = {
                            FileViewerHelper.openFile(context, currentFile.path, currentFile.fileName, currentFile.telegramFileId)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open In", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { triggerHapticFeedback(context, haptic); onInfoRequested(currentFile) }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { triggerHapticFeedback(context, haptic); onDeleteRequested(currentFile) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
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
                        // 1. IMAGE VIEWER WITH FULL PINCH ZOOM & PAN
                        isImage -> {
                            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                targetZoomScale = (targetZoomScale * zoomChange).coerceIn(1f, 5f)
                                if (targetZoomScale > 1.15f) {
                                    rawPanX += panChange.x
                                    rawPanY += panChange.y
                                }
                            }

                            val imageModel: Any? = when {
                                localFileExists -> localFile
                                cachedDownloadedFile != null -> cachedDownloadedFile
                                resolvedDirectUrl != null && prefs.encryptionKey.isEmpty() && currentFile.totalChunks <= 1 -> resolvedDirectUrl
                                else -> null
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
                                if (imageModel != null) {
                                    SubcomposeAsyncImage(
                                        model = imageModel,
                                        contentDescription = currentFile.fileName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = animatedZoom,
                                                scaleY = animatedZoom,
                                                translationX = animatedPanX,
                                                translationY = animatedPanY
                                            ),
                                        loading = {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = TelegramBlue)
                                            }
                                        },
                                        error = {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                OsFileIcon(fileName = currentFile.fileName, size = 64.dp)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("Tap 'Open' at top right to view", color = Color.White)
                                            }
                                        }
                                    )
                                } else {
                                    CircularProgressIndicator(color = TelegramBlue)
                                }
                            }
                        }

                        // 2. VIDEO VIEWER
                        isVideo -> {
                            val activeVideoSource = when {
                                localFileExists -> localFile.absolutePath
                                cachedDownloadedFile != null -> cachedDownloadedFile!!.absolutePath
                                resolvedDirectUrl != null -> resolvedDirectUrl
                                else -> null
                            }

                            if (activeVideoSource != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mc = MediaController(ctx)
                                            mc.setAnchorView(this)
                                            setMediaController(mc)
                                            if (activeVideoSource.startsWith("http")) {
                                                setVideoURI(Uri.parse(activeVideoSource))
                                            } else {
                                                setVideoPath(activeVideoSource)
                                            }
                                            start()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = TelegramBlue)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Preparing cloud video stream...", style = MaterialTheme.typography.titleSmall.copy(color = Color.White))
                                }
                            }
                        }

                        // 3. PDF VIEWER
                        isPdf -> {
                            val pdfTargetFile = when {
                                localFileExists -> localFile
                                cachedDownloadedFile != null -> cachedDownloadedFile
                                else -> null
                            }

                            if (pdfTargetFile != null && pdfTargetFile.exists()) {
                                PdfViewerComponent(file = pdfTargetFile)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = TelegramBlue)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Loading PDF from Telegram Cloud...", style = MaterialTheme.typography.titleSmall.copy(color = Color.White))
                                }
                            }
                        }

                        // 4. TEXT / NOTE / CODE DOCUMENT VIEWER
                        isTextFile -> {
                            val textTargetFile = when {
                                localFileExists -> localFile
                                cachedDownloadedFile != null -> cachedDownloadedFile
                                else -> null
                            }

                            if (textTargetFile != null && textTargetFile.exists()) {
                                TextDocumentViewerComponent(file = textTargetFile, fileName = currentFile.fileName)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = TelegramBlue)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Loading note content from cloud...", style = MaterialTheme.typography.titleSmall.copy(color = Color.White))
                                }
                            }
                        }

                        // 5. GENERAL DOCUMENTS / OTHER FILES
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                OsFileIcon(fileName = currentFile.fileName, size = 64.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(currentFile.fileName, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(formatFileSize(currentFile.size), style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)))
                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (localFileExists) {
                                                FileViewerHelper.openFile(context, localFile.absolutePath, currentFile.fileName)
                                            } else {
                                                Toast.makeText(context, "Opening ${currentFile.fileName}...", Toast.LENGTH_SHORT).show()
                                                val downloaded = cachedDownloadedFile ?: TelegramMediaResolver.fetchToTempCache(context, currentFile.telegramFileId, currentFile.fileName)
                                                if (downloaded != null && downloaded.exists()) {
                                                    FileViewerHelper.openFile(context, downloaded.absolutePath, currentFile.fileName)
                                                } else {
                                                    Toast.makeText(context, "Failed to download file from cloud", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in App Viewer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextDocumentViewerComponent(file: File, fileName: String) {
    val context = LocalContext.current
    var textContent by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file.absolutePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                textContent = file.readText()
            } catch (e: Exception) {
                textContent = "Error reading file: ${e.localizedMessage}"
            }
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TelegramBlue)
        }
    } else {
        val content = textContent ?: "Empty file"
        val lines = remember(content) { content.lines() }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Document top header with Copy action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$fileName • ${lines.size} lines",
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                    )

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("File content", content))
                            Toast.makeText(context, "Copied content to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                // Scrollable text view
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    itemsIndexed(lines) { idx, line ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(
                                text = "${idx + 1}".padStart(3, ' '),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFF1F5F9),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            )
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
        } catch (_: Exception) {
            totalPdfPages = 0
        }
        isRendering = false

        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (_: Exception) { }
            pageBitmapCache.values.forEach { try { it.recycle() } catch (_: Exception) {} }
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
                            } catch (_: Exception) {
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
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TelegramBlue)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Unable to preview PDF document", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
        }
    }
}
