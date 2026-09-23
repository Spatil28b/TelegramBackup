package com.example.telegrambackup.ui.main

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.ui.settings.ClearDataAccountDialog
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.CloudProfileSyncManager
import com.example.telegrambackup.utils.ProfileManager
import com.example.telegrambackup.utils.SettingsDbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileMetricItem(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val iconTint: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userName: String = "My Cloud Account",
    userEmail: String = "user@mybackup.cloud",
    onLogout: () -> Unit = {},
    onProfileUpdated: (newName: String, newAvatarUri: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }

    var currentUserName by remember(userName) { mutableStateOf(userName) }
    var currentAvatarUri by remember { mutableStateOf(ProfileManager.getAvatarUri(context)) }

    var cloudFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Persistent Avatar Picker
    val avatarPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedUri = ProfileManager.saveAvatarFromUri(context, uri)
            if (savedUri != null) {
                currentAvatarUri = savedUri
                prefs.profilePictureUri = savedUri.toString()
                prefs.profileUpdatedAt = System.currentTimeMillis()
                scope.launch {
                    SettingsDbManager.saveCategorySettings(
                        context,
                        "Profile Picture",
                        mapOf(
                            "profile_picture_uri" to savedUri.toString(),
                            "display_name" to currentUserName
                        ),
                        showToast = false
                    )
                    CloudProfileSyncManager.syncLocalConfigToCloud(context)
                }
                onProfileUpdated(currentUserName, savedUri.toString())
                Toast.makeText(context, "✓ Profile Photo Updated & Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val files = db.uploadedFileDao().getAllFiles()
                cloudFiles = files
                totalBytes = files.sumOf { it.size }
            } catch (e: Exception) {
                cloudFiles = emptyList()
                totalBytes = 0L
            }
        }
    }

    val formattedSizeStr = remember(totalBytes) {
        if (totalBytes <= 0) "0 B"
        else com.example.telegrambackup.ui.explorer.formatFileSize(totalBytes)
    }

    val metrics = remember(cloudFiles, formattedSizeStr) {
        listOf(
            ProfileMetricItem("Total Storage", formattedSizeStr, Icons.Default.Cloud, TelegramBlue),
            ProfileMetricItem("Cloud Files", "${cloudFiles.size} Items", Icons.Default.InsertDriveFile, Color(0xFF10B981)),
            ProfileMetricItem("Active Sync", "Automated", Icons.Default.Sync, Color(0xFF8B5CF6)),
            ProfileMetricItem("Security", "AES-256 GCM", Icons.Default.Shield, Color(0xFFF59E0B))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Profile Avatar Header Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.size(92.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue)
                            .clickable { avatarPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentAvatarUri != null) {
                            AsyncImage(
                                model = currentAvatarUri,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = currentUserName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Edit Photo Badge
                    Surface(
                        shape = CircleShape,
                        color = TelegramBlue,
                        modifier = Modifier
                            .size(30.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { avatarPickerLauncher.launch("image/*") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Change Photo",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = currentUserName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TelegramBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Cloud Account Verified",
                            style = MaterialTheme.typography.labelSmall.copy(color = TelegramBlue, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Card — Unlimited Cloud Storage
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Storage Filled", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Surface(shape = RoundedCornerShape(8.dp), color = TelegramBlue.copy(alpha = 0.15f)) {
                        Text(
                            "∞ Unlimited Cloud",
                            style = MaterialTheme.typography.labelSmall.copy(color = TelegramBlue, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formattedSizeStr,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${cloudFiles.size} Total Files Synced to Telegram Cloud",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 Metrics Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.take(2).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(item.iconTint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(item.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(item.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.drop(2).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(item.iconTint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(item.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(item.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        var showClearDataAccountDialog by remember { mutableStateOf(false) }
        var dialogInitialTab by remember { mutableIntStateOf(0) }

        if (showClearDataAccountDialog) {
            ClearDataAccountDialog(
                initialTab = dialogInitialTab,
                onDismiss = { showClearDataAccountDialog = false },
                onLogoutCompleted = onLogout
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { dialogInitialTab = 0; showClearDataAccountDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear Data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { dialogInitialTab = 1; showClearDataAccountDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete Account", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out Account", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Edit Profile Modal Dialog
    if (showEditProfileDialog) {
        var tempNameInput by remember { mutableStateOf(currentUserName) }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TelegramBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Profile Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentAvatarUri != null) {
                                AsyncImage(
                                    model = currentAvatarUri,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = tempNameInput.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { avatarPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Photo", fontSize = 12.sp)
                        }

                        if (currentAvatarUri != null) {
                            OutlinedButton(
                                onClick = {
                                    ProfileManager.removeAvatar(context)
                                    currentAvatarUri = null
                                    prefs.profilePictureUri = ""
                                    prefs.profileUpdatedAt = System.currentTimeMillis()
                                    scope.launch {
                                        SettingsDbManager.saveCategorySettings(
                                            context,
                                            "Profile Picture",
                                            mapOf("profile_picture_uri" to ""),
                                            showToast = false
                                        )
                                        CloudProfileSyncManager.syncLocalConfigToCloud(context)
                                    }
                                    onProfileUpdated(currentUserName, "")
                                    Toast.makeText(context, "Profile Photo Removed", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove", fontSize = 12.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = tempNameInput,
                        onValueChange = { tempNameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = tempNameInput.trim().ifEmpty { "My Cloud Account" }
                        currentUserName = cleanName
                        prefs.displayName = cleanName
                        prefs.profileUpdatedAt = System.currentTimeMillis()
                        scope.launch {
                            SettingsDbManager.saveCategorySettings(
                                context,
                                "Profile",
                                mapOf(
                                    "display_name" to cleanName,
                                    "profile_picture_uri" to (currentAvatarUri?.toString() ?: prefs.profilePictureUri)
                                ),
                                showToast = true
                            )
                            CloudProfileSyncManager.syncLocalConfigToCloud(context)
                        }
                        onProfileUpdated(cleanName, currentAvatarUri?.toString() ?: "")
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
