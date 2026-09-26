package com.example.telegrambackup.ui.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.telegrambackup.network.HttpClientProvider
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class AppVersionInfo(
    val success: Boolean,
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdaterDialog(
    versionInfo: AppVersionInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var isInstallerLaunched by remember { mutableStateOf(false) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var showWhatsNewModal by remember { mutableStateOf(false) }

    val currentInstalledVersion = remember {
        com.example.telegrambackup.utils.AppVersionHelper.currentVersionName
    }

    if (showWhatsNewModal) {
        WhatsNewDialog(
            versionName = currentInstalledVersion,
            onDismiss = { showWhatsNewModal = false }
        )
    }

    fun openApkFileManually(apkFile: File) {
        try {
            apkFile.setReadable(true, false)
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("TelegramBackupUpdate", apkUri)
            }
            val chooser = Intent.createChooser(viewIntent, "Install update with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage}. File at: ${apkFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    fun findSystemPackageInstallerIntent(apkUri: Uri): Intent {
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            clipData = ClipData.newRawUri("TelegramBackupUpdate", apkUri)
        }

        val pm = context.packageManager
        val resolvedList = try {
            pm.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }

        val knownInstallerPackages = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.vivo.packageinstaller",
            "com.oppo.packageinstaller",
            "com.transsion.packageinstaller",
            "com.huawei.appmarket",
            "com.huawei.packageinstaller"
        )

        // 1. First priority: match known official OEM/Google package installers
        var targetResolveInfo = resolvedList.firstOrNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName.lowercase()
            knownInstallerPackages.any { pkg == it || pkg.contains("packageinstaller") }
        }

        // 2. Second priority: any system-level package that can install packages
        if (targetResolveInfo == null) {
            targetResolveInfo = resolvedList.firstOrNull { resolveInfo ->
                val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val pkg = resolveInfo.activityInfo.packageName.lowercase()
                isSystem && pkg != context.packageName && !pkg.contains("zip") && !pkg.contains("rar") && !pkg.contains("file") && !pkg.contains("archiver")
            }
        }

        if (targetResolveInfo != null) {
            val targetPkg = targetResolveInfo.activityInfo.packageName
            val targetClass = targetResolveInfo.activityInfo.name
            Log.d("AppUpdater", "Targeting system package installer: $targetPkg/$targetClass")
            context.grantUriPermission(targetPkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return Intent(baseIntent).apply {
                setClassName(targetPkg, targetClass)
            }
        }

        // 3. Fallback: grant permissions to all candidates and return base intent
        for (resolveInfo in resolvedList) {
            try {
                context.grantUriPermission(resolveInfo.activityInfo.packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }
        return baseIntent
    }

    fun triggerInstallation(apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Downloaded update APK is missing or empty.", Toast.LENGTH_LONG).show()
                return
            }

            // Verify package archive integrity
            val pm = context.packageManager
            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                }
            } catch (_: Exception) { null }

            if (pkgInfo == null) {
                downloadStatusText = "⚠️ Downloaded APK is incomplete or corrupted. Please try updating again."
                Toast.makeText(context, "Downloaded file is corrupted or not a valid APK.", Toast.LENGTH_LONG).show()
                return
            }

            apkFile.setReadable(true, false)
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = findSystemPackageInstallerIntent(apkUri)

            try {
                context.startActivity(installIntent)
            } catch (targetedException: Exception) {
                Log.w("AppUpdater", "Targeted installer failed, falling back to generic VIEW intent", targetedException)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("TelegramBackupUpdate", apkUri)
                }
                context.startActivity(fallbackIntent)
            }

            isInstallerLaunched = true
            downloadStatusText = "🚀 Package Installer launched!\nTap 'Update' on the Android system prompt to finish."
            Toast.makeText(context, "Tap 'Update' on the Android installer prompt", Toast.LENGTH_LONG).show()
            Log.d("AppUpdater", "Launched FileProvider install Intent successfully with URI: $apkUri")
        } catch (e: Exception) {
            Log.e("AppUpdater", "FileProvider install failed: ${e.message}", e)
            Toast.makeText(context, "Launching default installer failed. Trying chooser...", Toast.LENGTH_SHORT).show()
            openApkFileManually(apkFile)
        }
    }

    val unknownAppSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls()) {
                downloadedApkFile?.let { apk ->
                    triggerInstallation(apk)
                }
            } else {
                Toast.makeText(context, "Permission to install unknown apps was not granted.", Toast.LENGTH_LONG).show()
                downloadStatusText = "⚠️ Unknown app installation permission required to proceed."
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "Downloaded update APK is missing or empty.", Toast.LENGTH_LONG).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow 'Install Unknown Apps' for My Backup to install update", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    unknownAppSourcesLauncher.launch(settingsIntent)
                    return
                }
            }

            triggerInstallation(apkFile)
        } catch (e: Exception) {
            Log.e("AppUpdater", "Failed to launch package installer", e)
            Toast.makeText(context, "Error opening package installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun copyToDownloads(apkFile: File) {
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!publicDir.exists()) publicDir.mkdirs()
            val destFile = File(publicDir, "TelegramBackup_v${versionInfo.versionName}.apk")
            apkFile.copyTo(destFile, overwrite = true)
            Toast.makeText(context, "Saved to Downloads: ${destFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not save to Downloads: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun restartAppProcess() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
            }
            Runtime.getRuntime().exit(0)
        } catch (_: Exception) {
            System.exit(0)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(TelegramEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = TelegramEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Update Available",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "v$currentInstalledVersion",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            "➔",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TelegramEmerald
                            )
                        )
                        Text(
                            "v${versionInfo.versionName}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TelegramEmerald,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // What's New Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "✨ What's New:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TelegramBlue,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            versionInfo.releaseNotes,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                // Option to view past updates
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showWhatsNewModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = TelegramEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "View Past Updates & Changelog",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isDownloading || isInstallerLaunched || downloadedApkFile != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            downloadStatusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isInstallerLaunched) TelegramEmerald else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 2
                        )
                        if (isDownloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = TelegramEmerald,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (downloadedApkFile != null) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { installApk(downloadedApkFile!!) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Install Now", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = { openApkFileManually(downloadedApkFile!!) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open File", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { copyToDownloads(downloadedApkFile!!) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("To Downloads", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = { restartAppProcess() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restart", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        isDownloading = true
                        downloadStatusText = "Connecting to update server..."

                        scope.launch(Dispatchers.IO) {
                            val client = okhttp3.OkHttpClient.Builder()
                                .connectionPool(okhttp3.ConnectionPool(5, 10, java.util.concurrent.TimeUnit.MINUTES))
                                .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                                .writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .retryOnConnectionFailure(true)
                                .build()

                            val candidateUrls = mutableListOf<String>()
                            // 1. Primary: Server Proxy pre-signed CDN resolver (immune to auth mismatches & GitHub release private 404s)
                            candidateUrls.add("https://telegram-backup-backend.vercel.app/api/app/download-latest")
                            if (versionInfo.downloadUrl.isNotBlank() && !candidateUrls.contains(versionInfo.downloadUrl)) {
                                candidateUrls.add(versionInfo.downloadUrl)
                            }

                            // Query GitHub Releases API for direct asset download URLs
                            try {
                                val ghApiReq = Request.Builder()
                                    .url("https://api.github.com/repos/sid-codes28/telegram_backup_backend/releases/latest")
                                    .header("User-Agent", "TelegramBackup-Updater/2.2")
                                    .header("Accept", "application/vnd.github.v3+json")
                                    .build()
                                val ghApiRes = client.newCall(ghApiReq).execute()
                                if (ghApiRes.isSuccessful) {
                                    val ghBody = ghApiRes.body?.string() ?: ""
                                    val ghJson = JSONObject(ghBody)
                                    val assets = ghJson.optJSONArray("assets")
                                    if (assets != null) {
                                        for (i in 0 until assets.length()) {
                                            val asset = assets.optJSONObject(i)
                                            val aName = asset?.optString("name", "") ?: ""
                                            val aUrl = asset?.optString("url", "") ?: ""
                                            val bUrl = asset?.optString("browser_download_url", "") ?: ""
                                            if (aName.endsWith(".apk", ignoreCase = true)) {
                                                // aUrl is authenticated API endpoint: api.github.com/repos/.../releases/assets/...
                                                if (aUrl.isNotBlank() && !candidateUrls.contains(aUrl)) {
                                                    candidateUrls.add(aUrl)
                                                }
                                                // bUrl is fallback only for public repos
                                                if (bUrl.isNotBlank() && !candidateUrls.contains(bUrl)) {
                                                    candidateUrls.add(bUrl)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}

                            candidateUrls.add("https://raw.githubusercontent.com/sid-codes28/telegram_backup_backend/main/Releases/TelegramBackup-latest.apk")
                            candidateUrls.add("https://github.com/sid-codes28/telegram_backup_backend/releases/download/v${versionInfo.versionName}/TelegramBackup-latest.apk")
                            candidateUrls.add("https://github.com/sid-codes28/telegram_backup_backend/releases/download/v${versionInfo.versionName}/TelegramBackup-v${versionInfo.versionName}.apk")

                            val distinctUrls = candidateUrls.distinct()
                            var downloadSuccess = false
                            var lastErrorMsg = ""

                            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                            val updateFile = File(updateDir, "TelegramBackup_v${versionInfo.versionName}.apk")

                            for ((index, rawTargetUrl) in distinctUrls.withIndex()) {
                                try {
                                    withContext(Dispatchers.Main) {
                                        downloadStatusText = if (index == 0) "Connecting to update server..." else "Trying fallback source (${index + 1}/${distinctUrls.size})..."
                                    }

                                    if (updateFile.exists()) {
                                        updateFile.delete()
                                    }

                                    var effectiveUrl = rawTargetUrl
                                    val effectiveHeaders = mutableMapOf<String, String>()

                                    // For GitHub Asset API endpoints, resolve the 302 redirect manually to prevent header pollution on S3/Azure Blob
                                    if (rawTargetUrl.contains("api.github.com/repos") && rawTargetUrl.contains("/assets/")) {
                                        try {
                                            val nonRedirectClient = client.newBuilder().followRedirects(false).build()
                                            val probeReq = Request.Builder()
                                                .url(rawTargetUrl)
                                                .header("User-Agent", "TelegramBackup-Updater/2.2")
                                                .header("Accept", "application/octet-stream")
                                                .build()
                                            val probeRes = nonRedirectClient.newCall(probeReq).execute()
                                            val redirectLoc = probeRes.header("Location")
                                            probeRes.close()
                                            if (!redirectLoc.isNullOrBlank()) {
                                                effectiveUrl = redirectLoc
                                            } else {
                                                effectiveHeaders["Accept"] = "application/octet-stream"
                                            }
                                        } catch (_: Exception) {
                                            effectiveHeaders["Accept"] = "application/octet-stream"
                                        }
                                    }

                                    val reqBuilder = Request.Builder()
                                        .url(effectiveUrl)
                                        .header("User-Agent", "TelegramBackup-Updater/2.2")

                                    effectiveHeaders.forEach { (k, v) ->
                                        reqBuilder.header(k, v)
                                    }

                                    val req = reqBuilder.build()
                                    val res = client.newCall(req).execute()
                                    val body = res.body

                                    if (!res.isSuccessful || body == null) {
                                        lastErrorMsg = "HTTP ${res.code}"
                                        continue
                                    }

                                    val totalBytes = body.contentLength()
                                    var lastProgressUpdateMs = 0L

                                    body.byteStream().use { input ->
                                        FileOutputStream(updateFile).use { output ->
                                            val buffer = ByteArray(32768)
                                            var bytesRead: Int
                                            var downloadedBytes = 0L

                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                output.write(buffer, 0, bytesRead)
                                                downloadedBytes += bytesRead

                                                val now = System.currentTimeMillis()
                                                if (now - lastProgressUpdateMs >= 200 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                                                    lastProgressUpdateMs = now
                                                    if (totalBytes > 0) {
                                                        val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                                        val mbDownloaded = String.format(Locale.US, "%.1f", downloadedBytes / (1024f * 1024f))
                                                        val mbTotal = String.format(Locale.US, "%.1f", totalBytes / (1024f * 1024f))
                                                        withContext(Dispatchers.Main) {
                                                            downloadProgress = progress
                                                            downloadStatusText = "Downloading update... $mbDownloaded MB / $mbTotal MB (${(progress * 100).toInt()}%)"
                                                        }
                                                    } else {
                                                        val mbDownloaded = String.format(Locale.US, "%.1f", downloadedBytes / (1024f * 1024f))
                                                        withContext(Dispatchers.Main) {
                                                            downloadStatusText = "Downloading update... $mbDownloaded MB"
                                                        }
                                                    }
                                                }
                                            }
                                            output.flush()
                                        }
                                    }

                                    if (updateFile.exists() && updateFile.length() > 1024 * 1024) {
                                        downloadSuccess = true
                                        break
                                    }
                                } catch (e: Exception) {
                                    lastErrorMsg = e.localizedMessage ?: "Connection error"
                                }
                            }

                            if (downloadSuccess && updateFile.exists()) {
                                withContext(Dispatchers.Main) {
                                    isDownloading = false
                                    downloadedApkFile = updateFile
                                    downloadStatusText = "Verifying package & opening installer..."
                                    installApk(updateFile)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isDownloading = false
                                    Toast.makeText(context, "Update download failed: $lastErrorMsg", Toast.LENGTH_LONG).show()
                                    downloadStatusText = "❌ Download error: $lastErrorMsg"
                                }
                            }
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramEmerald),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Downloading...", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Now", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isDownloading) onDismiss() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    if (isInstallerLaunched || downloadedApkFile != null) "Close" else "Later",
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    )
}
