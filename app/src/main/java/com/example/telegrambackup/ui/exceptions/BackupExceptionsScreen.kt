package com.example.telegrambackup.ui.exceptions

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val TelegramBlue = Color(0xFF2481CC)
private val SurfaceDark = Color(0xFF1E293B)
private val CardBackground = Color(0xFF0F172A)
private val ExceptionRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupExceptionsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    var activeDir by remember { mutableStateOf(Environment.getExternalStorageDirectory() ?: File("/sdcard")) }
    val folderHistory = remember { mutableStateListOf<File>() }
    var currentExceptions by remember { mutableStateOf(prefs.backupExceptions.toSet()) }

    BackHandler(enabled = folderHistory.isNotEmpty()) {
        if (folderHistory.isNotEmpty()) {
            activeDir = folderHistory.removeAt(folderHistory.size - 1)
        }
    }

    val dirContents = remember(activeDir, currentExceptions) {
        val files = try { activeDir.listFiles()?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        files.filter { !it.name.startsWith(".") && it.name.lowercase() != "android" }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun syncExceptionsToCloud(newSet: Set<String>) {
        prefs.backupExceptions = newSet
        currentExceptions = newSet
        val userEmail = prefs.userEmail
        if (userEmail.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val settingsObj = JSONObject().apply {
                        put("backup_exceptions", JSONArray(newSet.toList()))
                    }
                    ServerApi().saveUserSettings(prefs.serverUrl, userEmail, settingsObj.toString())
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(16.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (folderHistory.isNotEmpty()) {
                    activeDir = folderHistory.removeAt(folderHistory.size - 1)
                } else {
                    onNavigateBack()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🚫 Backup Exceptions",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Text(
                    "Excluded from Auto-Backup",
                    style = MaterialTheme.typography.bodySmall.copy(color = ExceptionRed)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ExceptionRed.copy(alpha = 0.2f))
                    .border(1.dp, ExceptionRed.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${currentExceptions.size} Excluded",
                    style = MaterialTheme.typography.labelSmall.copy(color = ExceptionRed, fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Info Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Toggle any folder or file below to exclude it from automatic cloud backups. Excluded items will never be backed up automatically.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Excluded Items Chips List (if any)
        if (currentExceptions.isNotEmpty()) {
            Text(
                "Currently Excluded Paths:",
                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.7f)),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                items(currentExceptions.toList()) { excPath ->
                    val name = File(excPath).name.ifEmpty { excPath }
                    AssistChip(
                        onClick = {
                            val newSet = currentExceptions.toMutableSet().apply { remove(excPath) }
                            syncExceptionsToCloud(newSet)
                            Toast.makeText(context, "Removed '$name' from exceptions", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("🚫 $name", color = Color.White, fontSize = 12.sp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", tint = ExceptionRed, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = ExceptionRed.copy(alpha = 0.25f))
                    )
                }
            }
        }

        // Internal Storage Replica Navigation Breadcrumb
        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val displayPath = activeDir.absolutePath.replace(Environment.getExternalStorageDirectory()?.absolutePath ?: "", "Internal Storage")
                Text(
                    displayPath,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Replica Folder/File Tree List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(dirContents) { item ->
                val itemPath = item.absolutePath
                val isExcluded = currentExceptions.any { exc -> itemPath == exc || itemPath.startsWith("$exc/") }
                val isDirectlyExcluded = currentExceptions.contains(itemPath)

                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isExcluded) ExceptionRed.copy(alpha = 0.12f) else CardBackground),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) {
                                    folderHistory.add(activeDir)
                                    activeDir = item
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (item.isDirectory) TelegramBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.isDirectory) TelegramBlue else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metaText = if (item.isDirectory) {
                                val childCount = try { item.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
                                "$childCount items"
                            } else {
                                formatFileSize(item.length())
                            }
                            Text(
                                metaText,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Exclude Switch
                        Switch(
                            checked = isExcluded,
                            onCheckedChange = { checked ->
                                val newSet = currentExceptions.toMutableSet()
                                if (checked) {
                                    newSet.add(itemPath)
                                    Toast.makeText(context, "Added '${item.name}' to Backup Exceptions", Toast.LENGTH_SHORT).show()
                                } else {
                                    newSet.remove(itemPath)
                                    // Also remove parent exclusions if unchecking a child
                                    newSet.removeAll { exc -> itemPath.startsWith(exc) }
                                    Toast.makeText(context, "Removed '${item.name}' from Exceptions", Toast.LENGTH_SHORT).show()
                                }
                                syncExceptionsToCloud(newSet)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ExceptionRed,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
