package com.example.telegrambackup.ui.ai

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.common.FileThumbnailView
import com.example.telegrambackup.ui.explorer.AdvancedMediaViewerDialog
import com.example.telegrambackup.ui.explorer.formatFileSize
import com.example.telegrambackup.utils.AiPhotoSearchManager
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * =====================================================================================
 * AI PHOTO & DOCUMENT SEARCH MODAL
 * =====================================================================================
 * Natural-language visual photo & document search:
 * - Search by prompt: "bonafide certificate", "payment receipt", "pan card", "party photos",
 *   "10th marksheet", "10k transfer receipt", "electricity bill", etc.
 * - Displays matching images in an interactive visual gallery grid.
 * - Tapping any image opens full-screen zoomable media viewer or deep Q&A.
 * =====================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPhotoSearchDialog(
    initialQuery: String = "",
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs = remember { AppPrefs(context) }

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var searchResults by remember { mutableStateOf<List<AiPhotoSearchManager.AiSearchResultItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var hasSearchedAtLeastOnce by remember { mutableStateOf(false) }

    // Media viewer & Ask photo modal states
    var viewerInitialIndex by remember { mutableStateOf<Int?>(null) }
    var askPhotoTargetFile by remember { mutableStateOf<UploadedFile?>(null) }

    val hasKey = remember(prefs.geminiApiKeys) { prefs.hasValidGeminiKey() }
    val presets = remember { AiPhotoSearchManager.getPresets() }

    fun executeSearch(q: String) {
        val clean = q.trim()
        if (clean.isBlank()) return

        searchQuery = clean
        hasSearchedAtLeastOnce = true
        isSearching = true
        statusText = "Searching photos..."
        searchResults = emptyList()

        activeJob?.cancel()
        activeJob = AiPhotoSearchManager.search(
            context = context,
            query = clean,
            scope = scope,
            onInstantResults = { instantList ->
                searchResults = instantList
            },
            onProgress = { current, total, text ->
                statusText = text
            },
            onAiMatchFound = { matchItem ->
                // Add unique match item to results list
                val existing = searchResults.toMutableList()
                val key = matchItem.file.telegramFileId.ifBlank { matchItem.file.path }
                if (existing.none { (it.file.telegramFileId.ifBlank { it.file.path }) == key }) {
                    existing.add(matchItem)
                    searchResults = existing
                }
            },
            onComplete = { total ->
                isSearching = false
                statusText = if (total > 0) "Found $total matching photos" else "No matching photos found"
            }
        )
    }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            executeSearch(initialQuery)
        }
    }

    Dialog(
        onDismissRequest = {
            activeJob?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // =====================================================================
                // 1. TOP APP BAR
                // =====================================================================
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            activeJob?.cancel()
                            onDismiss()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(TelegramBlue, Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AI Photo & Doc Search",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Find certificates, receipts, cards & photos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "AI Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // =====================================================================
                // 2. SEARCH INPUT BOX
                // =====================================================================
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "e.g. bonafide certificate, payment receipt, pan card...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                hasSearchedAtLeastOnce = false
                                activeJob?.cancel()
                                isSearching = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }

                        Button(
                            onClick = { executeSearch(searchQuery) },
                            enabled = searchQuery.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Find", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                // =====================================================================
                // 3. QUICK PRESET CHIPS ROW
                // =====================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = searchQuery.equals(preset.query, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                searchQuery = preset.query
                                executeSearch(preset.query)
                            },
                            label = {
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelegramBlue.copy(alpha = 0.18f),
                                selectedLabelColor = TelegramBlue
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) TelegramBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        )
                    }
                }

                // =====================================================================
                // 4. API KEY ADVISORY BANNER (If no Gemini Key configured)
                // =====================================================================
                if (!hasKey) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Add a free Gemini API Key in Settings to enable deep AI reading of text inside receipts & certificates.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = onOpenSettings,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Add Key", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B)))
                            }
                        }
                    }
                }

                // =====================================================================
                // 5. STATUS / SCANNING INDICATOR
                // =====================================================================
                AnimatedVisibility(visible = isSearching || statusText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = TelegramBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSearching) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        if (searchResults.isNotEmpty()) {
                            Text(
                                "${searchResults.size} matches",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TelegramEmerald
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // =====================================================================
                // 6. MAIN CONTENT AREA: RESULTS GRID OR EMPTY GUIDANCE
                // =====================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    when {
                        // Results available: Show 2-column image gallery grid!
                        searchResults.isNotEmpty() -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(searchResults) { index, item ->
                                    AiImageResultCard(
                                        result = item,
                                        onClick = {
                                            viewerInitialIndex = index
                                        },
                                        onAskAi = {
                                            askPhotoTargetFile = item.file
                                        }
                                    )
                                }
                            }
                        }

                        // Searched but zero results found
                        hasSearchedAtLeastOnce && !isSearching -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.outline)
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "No matching photos found",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Could not find any photos matching \"$searchQuery\". Try searching another document type like \"pan card\", \"receipt\", or \"certificate\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        // Searching with zero results yet
                        isSearching && searchResults.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = TelegramBlue, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "AI Vision is analyzing your photos...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Scanning certificates, receipts, IDs and images",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Initial idle state: Guide & tips
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 28.dp, start = 8.dp, end = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(TelegramBlue.copy(alpha = 0.2f), Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    "Natural Language Photo Search",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Search for anything inside your photos. AI will inspect visual content and extract matching images!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Example search cards
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "POPULAR SEARCH QUERIES",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue, letterSpacing = 0.8.sp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))

                                        SearchSampleRow("🎓 bonafide certificate", "Finds college & school bonafide certificates") {
                                            searchQuery = "bonafide certificate"
                                            executeSearch("bonafide certificate")
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        SearchSampleRow("🧾 payment receipt", "Finds UPI, GPay, invoices & ₹10k transfer receipts") {
                                            searchQuery = "payment receipt"
                                            executeSearch("payment receipt")
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        SearchSampleRow("🪪 pan card", "Finds PAN, Aadhaar, driving licenses and Govt IDs") {
                                            searchQuery = "pan card"
                                            executeSearch("pan card")
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        SearchSampleRow("🎉 party photos", "Finds celebration, party, and birthday event photos") {
                                            searchQuery = "party photos"
                                            executeSearch("party photos")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // FULLSCREEN MEDIA VIEWER MODAL
    // =========================================================================
    viewerInitialIndex?.let { index ->
        val fileList = searchResults.map { it.file }
        AdvancedMediaViewerDialog(
            initialIndex = index,
            filesList = fileList,
            onDismiss = { viewerInitialIndex = null },
            onDeleteRequested = { _ -> },
            onInfoRequested = { target ->
                askPhotoTargetFile = target
            }
        )
    }

    // =========================================================================
    // ASK PHOTO SHEET MODAL (Deep Q&A on single document)
    // =========================================================================
    askPhotoTargetFile?.let { file ->
        val localTargetFile = File(file.path)
        AskPhotoSheet(
            targetFile = localTargetFile,
            fileName = file.fileName,
            filePath = file.path,
            telegramFileId = file.telegramFileId,
            onDismiss = { askPhotoTargetFile = null },
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
private fun SearchSampleRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
    }
}

/**
 * Visual card displaying an image result with thumbnail, match badge, and quick actions.
 */
@Composable
private fun AiImageResultCard(
    result: AiPhotoSearchManager.AiSearchResultItem,
    onClick: () -> Unit,
    onAskAi: () -> Unit
) {
    val file = result.file
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val dateStr = remember(file.timestamp) {
        if (file.timestamp > 0) dateFormat.format(Date(file.timestamp)) else "Backed up"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail container with match badge overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                FileThumbnailView(
                    fileName = file.fileName,
                    filePath = file.path,
                    telegramFileId = file.telegramFileId,
                    iconSize = 48.dp,
                    modifier = Modifier.fillMaxSize()
                )

                // Match reason badge over the photo
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = result.matchReason,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Info and metadata section
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$dateStr • ${formatFileSize(file.size)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Quick "Ask AI" button
                    IconButton(
                        onClick = onAskAi,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Ask AI about this photo",
                            tint = TelegramBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
