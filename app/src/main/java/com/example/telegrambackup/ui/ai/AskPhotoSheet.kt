package com.example.telegrambackup.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.ui.common.FileThumbnailView
import com.example.telegrambackup.utils.AiVisionManager
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * =====================================================================================
 * "ASK PHOTOS" - AI MULTIMODAL DOCUMENT & PHOTO ASSISTANT MODAL
 * =====================================================================================
 * Allows asking questions about any photo or document (marksheets, receipts, IDs, bills).
 * Powered by Gemini Vision with multi-key failover and zero local device load.
 * =====================================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskPhotoSheet(
    targetFile: File,
    fileName: String,
    filePath: String = targetFile.absolutePath,
    telegramFileId: String = "",
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs = remember { AppPrefs(context) }

    var userQueryInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val hasKey = remember(prefs.geminiApiKeys) { prefs.hasValidGeminiKey() }

    fun executeQuery(prompt: String) {
        val query = prompt.trim()
        if (query.isBlank()) return
        if (!hasKey) {
            showApiKeyDialog = true
            return
        }

        isLoading = true
        errorMessage = null
        aiResponse = null

        scope.launch {
            val result = AiVisionManager.askImageQuestion(
                context = context,
                imageFile = targetFile,
                prompt = query
            )

            withContext(Dispatchers.Main) {
                isLoading = false
                result.onSuccess { text ->
                    aiResponse = text
                }.onFailure { err ->
                    errorMessage = err.localizedMessage ?: "Failed to process with AI Vision"
                }
            }
        }
    }

    if (showApiKeyDialog) {
        ApiKeySetupDialog(
            currentKey = prefs.geminiApiKeys,
            onDismiss = { showApiKeyDialog = false },
            onSave = { newKey ->
                prefs.geminiApiKeys = newKey
                showApiKeyDialog = false
                Toast.makeText(context, "API Key saved! Ready to ask questions.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Icon + Title + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ask Photo & Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Multimodal AI Vision Assistant",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // File Thumbnail & Info Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        FileThumbnailView(
                            fileName = fileName,
                            filePath = filePath,
                            telegramFileId = telegramFileId,
                            iconSize = 46.dp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Size: ${(targetFile.length() / 1024L)} KB • Ready for AI extraction",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Banner if API key not added
            if (!hasKey) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TelegramEmerald.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TelegramEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Gemini API Key Needed", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TelegramEmerald))
                            Text("Add your free Gemini API key to ask questions about your documents.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(
                            onClick = { showApiKeyDialog = true }
                        ) {
                            Text("Set Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Preset Prompts Scrollable Row
            Text(
                text = "Quick Document Actions",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiVisionManager.PRESET_PROMPTS.forEach { preset ->
                    AssistChip(
                        onClick = {
                            userQueryInput = preset.prompt
                            executeQuery(preset.prompt)
                        },
                        label = { Text(preset.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Query Input Row
            OutlinedTextField(
                value = userQueryInput,
                onValueChange = { userQueryInput = it },
                placeholder = { Text("Ask anything (e.g. 10th marks, 10k receipt amount, roll no)...") },
                trailingIcon = {
                    IconButton(
                        onClick = { executeQuery(userQueryInput) },
                        enabled = userQueryInput.isNotBlank() && !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (userQueryInput.isNotBlank() && !isLoading) TelegramBlue else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Result or Progress Section
            AnimatedVisibility(
                visible = isLoading || aiResponse != null || errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (isLoading) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = TelegramBlue
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Analyzing document with Gemini Vision...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analysis Failed", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (aiResponse != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "AI Answer",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("AI Answer", aiResponse)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = aiResponse ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp
                                        )
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

@Composable
fun ApiKeySetupDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TelegramBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini AI API Key", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter one or multiple Google Gemini API keys (separate multiple keys by commas or newlines for auto-failover):",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API Key(s)") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isTesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying API Key with Google...", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (testResult != null) {
                    val color = if (testSuccess == true) TelegramEmerald else MaterialTheme.colorScheme.error
                    Text(testResult ?: "", style = MaterialTheme.typography.labelSmall, color = color)
                }

                Text(
                    text = "Tip: You can get a 100% free Gemini API Key from Google AI Studio (aistudio.google.com).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyInput.trim()) },
                enabled = keyInput.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val firstKey = keyInput.split(",", "\n", ";").firstOrNull { it.isNotBlank() } ?: ""
                        if (firstKey.isNotBlank()) {
                            isTesting = true
                            testResult = null
                            testSuccess = null
                            scope.launch {
                                val res = AiVisionManager.testApiKey(firstKey)
                                isTesting = false
                                res.onSuccess {
                                    testResult = "✓ $it"
                                    testSuccess = true
                                }.onFailure {
                                    testResult = "✗ ${it.message}"
                                    testSuccess = false
                                }
                            }
                        }
                    },
                    enabled = keyInput.isNotBlank() && !isTesting
                ) {
                    Text("Test Key")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
