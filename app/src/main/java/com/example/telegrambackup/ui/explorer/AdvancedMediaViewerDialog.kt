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
import android.os.Environment
import android.util.Log
import androidx.compose.ui.text.style.TextAlign
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.FileEncryptor
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
    val isLocalReadable = localFile.exists() && localFile.length() > 0 && localFile.canRead()
    val localFileExists = isLocalReadable && (FileEncryptor.isLikelyPlainFile(localFile, currentFile.fileName) || FileEncryptor.isEncryptedFile(localFile, currentFile.fileName))

    // Cloud streaming / caching state
    var resolvedDirectUrl by remember(currentFile.telegramFileId) { mutableStateOf<String?>(null) }
    var cachedDownloadedFile by remember(currentFile.telegramFileId) { mutableStateOf<File?>(null) }
    var isCloudResolving by remember(currentFile.telegramFileId) { mutableStateOf(false) }

    LaunchedEffect(currentFile) {
        if ((!localFileExists || isPdf) && currentFile.telegramFileId.isNotBlank() && currentFile.telegramFileId != "DIR_MARKER") {
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
                            triggerHapticFeedback(context, haptic)
                            Toast.makeText(context, "Saving ${currentFile.fileName} to Downloads...", Toast.LENGTH_SHORT).show()
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val targetDir = File(dlDir, "TelegramBackup")
                                    if (!targetDir.exists()) targetDir.mkdirs()
                                    val targetFile = File(targetDir, currentFile.fileName)

                                    var ok = false
                                    if (cachedDownloadedFile != null && cachedDownloadedFile!!.exists() && cachedDownloadedFile!!.length() > 0) {
                                        cachedDownloadedFile!!.copyTo(targetFile, overwrite = true)
                                        FileEncryptor.ensureDecrypted(targetFile, context, currentFile.fileName)
                                        ok = targetFile.exists() && targetFile.length() > 0
                                    } else if (localFileExists) {
                                        localFile.copyTo(targetFile, overwrite = true)
                                        FileEncryptor.ensureDecrypted(targetFile, context, currentFile.fileName)
                                        ok = targetFile.exists() && targetFile.length() > 0
                                    } else {
                                        ok = TelegramMediaResolver.restoreFileToTarget(context, currentFile, targetFile)
                                    }

                                    if (ok && targetFile.exists()) {
                                        FileEncryptor.ensureDecrypted(targetFile, context, currentFile.fileName)
                                        try {
                                            android.media.MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                                        } catch (_: Exception) {}
                                    }

                                    withContext(Dispatchers.Main) {
                                        if (ok && targetFile.exists()) {
                                            Toast.makeText(context, "✅ Saved to Downloads/TelegramBackup/${currentFile.fileName}", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Failed to download ${currentFile.fileName}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download to Device", tint = TelegramEmerald, modifier = Modifier.size(22.dp))
                        }
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
                                cachedDownloadedFile != null && cachedDownloadedFile!!.exists() && cachedDownloadedFile!!.length() > 0 -> cachedDownloadedFile
                                localFileExists -> localFile
                                else -> null
                            }

                            if (pdfTargetFile != null && pdfTargetFile.exists()) {
                                PdfViewerComponent(file = pdfTargetFile, fileName = currentFile.fileName)
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
private fun PdfViewerComponent(file: File, fileName: String = file.name) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var totalPdfPages by remember { mutableIntStateOf(0) }
    var isRendering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var workingCopyFile by remember { mutableStateOf<File?>(null) }
    val lazyListState = rememberLazyListState()

    val pageBitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }
    val pdfLock = remember { Any() }

    LaunchedEffect(file.absolutePath, retryTrigger) {
        isRendering = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                if (file.exists() && file.length() > 0) {
                    val workingDir = File(context.cacheDir, "pdf_preview_work").apply { if (!exists()) mkdirs() }
                    val workingFile = File(workingDir, "preview_${System.currentTimeMillis()}_${file.name}")
                    file.copyTo(workingFile, overwrite = true)
                    FileEncryptor.ensureDecrypted(workingFile, context, fileName)
                    workingCopyFile = workingFile

                    val pfd = ParcelFileDescriptor.open(workingFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    fileDescriptor = pfd
                    pdfRenderer = renderer
                    totalPdfPages = renderer.pageCount
                } else {
                    errorMessage = "File not found or empty"
                }
            } catch (t: Throwable) {
                Log.e("PdfViewer", "PdfRenderer error", t)
                totalPdfPages = 0
                errorMessage = t.localizedMessage ?: "Invalid PDF document format"
            } finally {
                isRendering = false
            }
        }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (_: Throwable) {}
            try {
                workingCopyFile?.delete()
            } catch (_: Throwable) {}
            pageBitmapCache.values.forEach { try { it.recycle() } catch (_: Throwable) {} }
            pageBitmapCache.clear()
        }
    }

    if (isRendering) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TelegramBlue)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Preparing & Opening PDF...", style = MaterialTheme.typography.titleSmall.copy(color = Color.White))
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
                    var pageBitmap by remember(pageIdx) { mutableStateOf(pageBitmapCache[pageIdx]) }
                    var isPageLoading by remember(pageIdx) { mutableStateOf(pageBitmap == null) }

                    LaunchedEffect(pageIdx, pdfRenderer) {
                        if (pageBitmap == null && pdfRenderer != null) {
                            withContext(Dispatchers.IO) {
                                var renderedBitmap: Bitmap? = null
                                synchronized(pdfLock) {
                                    try {
                                        val renderer = pdfRenderer
                                        if (renderer != null && pageIdx < renderer.pageCount) {
                                            val page = renderer.openPage(pageIdx)
                                            val displayMetrics = context.resources.displayMetrics
                                            val targetWidth = minOf(displayMetrics.widthPixels, 1440).coerceAtLeast(720)
                                            val targetHeight = ((targetWidth.toFloat() / page.width.toFloat()) * page.height.toFloat()).toInt().coerceIn(400, 3200)
                                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                            bitmap.eraseColor(android.graphics.Color.WHITE)
                                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            page.close()

                                            if (pageBitmapCache.size > 10) {
                                                val oldest = pageBitmapCache.keys.firstOrNull()
                                                if (oldest != null) {
                                                    pageBitmapCache.remove(oldest)?.recycle()
                                                }
                                            }
                                            pageBitmapCache[pageIdx] = bitmap
                                            renderedBitmap = bitmap
                                        }
                                    } catch (t: Throwable) {
                                        Log.w("PdfViewer", "Error rendering page $pageIdx", t)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    if (renderedBitmap != null) {
                                        pageBitmap = renderedBitmap
                                    }
                                    isPageLoading = false
                                }
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
                                bitmap = pageBitmap!!.asImageBitmap(),
                                contentDescription = "PDF Page ${pageIdx + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TelegramBlue)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Unable to preview PDF document", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Size: ${formatFileSize(file.length())} • ${errorMessage ?: "Encrypted or corrupted format"}",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f)),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        FileViewerHelper.openFile(context, file.absolutePath, fileName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open in App")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val targetDir = File(dlDir, "TelegramBackup")
                                if (!targetDir.exists()) targetDir.mkdirs()
                                val targetFile = File(targetDir, fileName)
                                val sourceToUse = workingCopyFile?.takeIf { it.exists() && it.length() > 0 } ?: file
                                sourceToUse.copyTo(targetFile, overwrite = true)
                                FileEncryptor.ensureDecrypted(targetFile, context, fileName)
                                try {
                                    android.media.MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                                } catch (_: Exception) {}
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "✅ Saved to Downloads/TelegramBackup/$fileName", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save File")
                }
                IconButton(onClick = { retryTrigger++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White)
                }
            }
        }
    }
}
