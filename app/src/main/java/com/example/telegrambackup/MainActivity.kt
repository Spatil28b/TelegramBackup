package com.example.telegrambackup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.telegrambackup.utils.AppNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.AuthRepository
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.theme.TelegramBackupTheme
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.isLiquidGlass
import com.example.telegrambackup.theme.liquidGlassCard
import com.example.telegrambackup.theme.liquidGlassPill
import com.example.telegrambackup.theme.liquidGlassSurfaceBrush
import com.example.telegrambackup.theme.liquidGlassBorderBrush
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.telegrambackup.ui.common.KomiBottomBar
import com.example.telegrambackup.ui.common.KomiNavItemData
import com.example.telegrambackup.ui.common.rememberSmartScrollNavBarConnection
import com.example.telegrambackup.ui.auth.AuthScreen
import com.example.telegrambackup.ui.drive.DriveExplorerScreen
import com.example.telegrambackup.ui.explorer.BackupExplorerScreen
import com.example.telegrambackup.ui.exceptions.BackupExceptionsScreen
import com.example.telegrambackup.ui.main.ActivityScreen
import com.example.telegrambackup.ui.main.DashboardHomeScreen
import com.example.telegrambackup.ui.main.ProfileScreen
import com.example.telegrambackup.ui.main.TrashBinScreen
import com.example.telegrambackup.ui.main.MonthlyArchiveScreen
import com.example.telegrambackup.ui.main.GuideScreen
import com.example.telegrambackup.ui.main.LocalFilesScreen
import com.example.telegrambackup.ui.main.LiveProgressScreen
import com.example.telegrambackup.ui.main.VaultHealthScreen
import com.example.telegrambackup.ui.settings.BackupSettingsScreen
import com.example.telegrambackup.ui.player.AudioPlayerManager
import com.example.telegrambackup.ui.player.ModernAudioPlayerSheet
import com.example.telegrambackup.ui.player.ModernMiniPlayerCapsule
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.BackupProgressTracker
import com.example.telegrambackup.utils.BiometricHelper
import com.example.telegrambackup.utils.FileViewerHelper
import com.example.telegrambackup.utils.SettingsDbManager
import com.example.telegrambackup.worker.BackupWorker
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
private fun triggerHapticFeedback(context: Context, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback?) {
    try {
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) { }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(30L)
        }
    } catch (_: Exception) { }
}

enum class MainTab {
    HOME,
    DRIVE,
    LOCAL,
    PROGRESS,
    FILES,
    MONTHLY_ARCHIVE,
    GUIDE,
    TRASH,
    EXCEPTIONS,
    ACTIVITY,
    SETTINGS,
    PROFILE,
    VAULT_HEALTH
}

class MainActivity : FragmentActivity() {
    private val authRepository by lazy { AuthRepository(this) }

    fun updateScreenPrivacy(enabled: Boolean) {
        runOnUiThread {
            try {
                if (enabled) {
                    window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.example.telegrambackup.worker.OcrIndexingWorker.schedulePeriodicOcrIndexing(this)
        } catch (_: Exception) {}

        setContent {
            val context = LocalContext.current
            val prefs = remember { AppPrefs(context) }
            var currentThemeMode by remember { mutableIntStateOf(prefs.appTheme) }

            LaunchedEffect(prefs.screenPrivacyEnabled) {
                updateScreenPrivacy(prefs.screenPrivacyEnabled)
            }

            val systemDark = isSystemInDarkTheme()

            TelegramBackupTheme(themeMode = currentThemeMode, darkTheme = systemDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(
                        activity = this,
                        authRepository = authRepository,
                        onThemeChanged = { newTheme -> currentThemeMode = newTheme }
                    )
                }
            }
        }
    }
}

private fun getTabNavigationIndex(tab: MainTab): Int = when (tab) {
    MainTab.HOME -> 0
    MainTab.FILES -> 1
    MainTab.DRIVE -> 2
    MainTab.MONTHLY_ARCHIVE -> 3
    MainTab.LOCAL -> 4
    MainTab.PROGRESS -> 5
    MainTab.ACTIVITY -> 6
    MainTab.TRASH -> 7
    MainTab.EXCEPTIONS -> 8
    MainTab.GUIDE -> 9
    MainTab.SETTINGS -> 10
    MainTab.PROFILE -> 11
    MainTab.VAULT_HEALTH -> 12
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    activity: FragmentActivity,
    authRepository: AuthRepository,
    onThemeChanged: (Int) -> Unit = {}
) {
    val session by authRepository.session.collectAsState()
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    val (isNavBarVisibleState, scrollNavBarConnection) = rememberSmartScrollNavBarConnection()
    var explorerInitialTab by remember { mutableStateOf(0) }
    var targetCategoryTitle by remember { mutableStateOf<String?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var topSearchQuery by remember { mutableStateOf("") }
    var showAiSearchDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val haptic = LocalHapticFeedback.current

    val isBiometricActive = remember(prefs.isBiometricEnabled) {
        prefs.isBiometricEnabled && BiometricHelper.isBiometricAvailable(context)
    }
    var isAuthenticated by remember(isBiometricActive) { mutableStateOf(!isBiometricActive) }

    var backPressedTime by remember { mutableLongStateOf(0L) }
    val database = remember { AppDatabase.getDatabase(context) }
    val liveUploadedFiles by database.uploadedFileDao().getAllFilesFlow().collectAsState(initial = emptyList())
    var displayUserName by remember(prefs.displayName) { mutableStateOf(prefs.displayName) }
    var displayUserAvatarUri by remember { mutableStateOf(com.example.telegrambackup.utils.ProfileManager.getAvatarUri(context)?.toString() ?: prefs.profilePictureUri) }
    val userEmail = session.email.ifEmpty { "user@mybackup.cloud" }

    var appUpdateInfo by remember { mutableStateOf<com.example.telegrambackup.ui.update.AppVersionInfo?>(null) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }
    val currentInstalledVersionName = remember {
        com.example.telegrambackup.utils.AppVersionHelper.currentVersionName
    }

    LaunchedEffect(Unit) {
        if (prefs.lastSeenWhatsNewVersion.isNotEmpty() && prefs.lastSeenWhatsNewVersion != currentInstalledVersionName) {
            showWhatsNewDialog = true
        } else if (prefs.lastSeenWhatsNewVersion.isEmpty()) {
            prefs.lastSeenWhatsNewVersion = currentInstalledVersionName
        }

        scope.launch(Dispatchers.IO) {
            try {
                val serverUrl = prefs.serverUrl.ifEmpty { "https://telegram-backup-backend.vercel.app" }
                val versionInfo = com.example.telegrambackup.network.ServerApi().getLatestAppVersion(serverUrl)

                val isUpdateAvailable = com.example.telegrambackup.utils.AppVersionHelper.isUpdateAvailable(
                    serverVersionName = versionInfo.versionName,
                    serverVersionCode = versionInfo.versionCode,
                    context = context
                )

                if (versionInfo.success && isUpdateAvailable && versionInfo.downloadUrl.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        appUpdateInfo = versionInfo
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "App update check error on launch", e)
            }
        }
    }

    // Quick Upload File Picker Launcher
    // Fix #2: Previously inserted fake DB records with placeholder IDs.
    // Now only triggers BackupWorker which handles actual Telegram uploads.
    // The worker scans device storage and uploads any unbacked files.
    val quickUploadLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                // Trigger BackupWorker to scan & upload — it handles the real upload pipeline
                try {
                    val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                } catch (_: Exception) { }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "⚡ Backup started! ${uris.size} file(s) will be uploaded to Telegram Cloud.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Instant High-Speed Parallel Auto Re-Sync on App Open & Background Worker Kickstart
    LaunchedEffect(Unit) {
        AppNotificationHelper.initNotificationChannels(context)
        AppNotificationHelper.showPersistentControlNotification(context, isRunning = false)
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            // STEP 0: Auto-restore persistent external vault if app was reinstalled
            try {
                val restored = com.example.telegrambackup.utils.LocalConfigVaultManager.autoRestoreIfLocalEmpty(context)
                if (restored) {
                    authRepository.refreshSession()
                }
            } catch (_: Exception) {}

            // STEP 1: Restore cloud file index into Room DB FIRST before starting worker!
            try { SettingsDbManager.restoreCloudFileIndexToRoom(context) } catch (_: Exception) {}
            try { SettingsDbManager.fetchAndSyncSettingsOnAppOpen(context) } catch (_: Exception) {}
            try { BackupProgressTracker.hydrateRecentCompleted(context) } catch (_: Exception) {}
            try { com.example.telegrambackup.utils.AppCacheManager.autoPruneCache(context) } catch (_: Exception) {}
            try { com.example.telegrambackup.utils.NetworkAnalysisManager.syncSelfAnalysisWorker(context) } catch (_: Exception) {}
            try { com.example.telegrambackup.service.LiveCameraObserverService.start(context) } catch (_: Exception) {}
            try { authRepository.refreshSession() } catch (_: Exception) {}

            // STEP 2: Kickstart background backup scan AFTER Room DB is fully restored!
            if (prefs.autoBackupEnabled && !prefs.stopAllBackups) {
                try {
                    val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                } catch (_: Exception) { }
            }
        }
    }

    // Lifecycle-Aware Biometric Auto-Lock (Locks app after configurable background timeout)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var backgroundedTimestamp by remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner, isBiometricActive) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                backgroundedTimestamp = System.currentTimeMillis()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                if (isBiometricActive && backgroundedTimestamp > 0L) {
                    val elapsedSeconds = (System.currentTimeMillis() - backgroundedTimestamp) / 1000
                    val timeout = prefs.biometricAutoLockTimeoutSeconds
                    if (elapsedSeconds >= timeout) {
                        isAuthenticated = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isBiometricActive, isAuthenticated) {
        if (isBiometricActive && !isAuthenticated) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "My Backup Cloud",
                subtitle = "Authenticate to access your cloud backup",
                onSuccess = { isAuthenticated = true },
                onError = { errorMsg ->
                    Toast.makeText(context, "Authentication Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    BackHandler(enabled = isAuthenticated && session.isLoggedIn) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (isSearchActive) {
            isSearchActive = false
            topSearchQuery = ""
        } else if (targetCategoryTitle != null) {
            targetCategoryTitle = null
        } else if (selectedTab != MainTab.HOME) {
            targetCategoryTitle = null
            selectedTab = MainTab.HOME
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                activity.finish()
            } else {
                backPressedTime = currentTime
                triggerHapticFeedback(context, haptic)
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(activity.intent) {
        val navTab = activity.intent?.getStringExtra(com.example.telegrambackup.utils.AppNotificationHelper.EXTRA_NAVIGATE_TAB)
        if (!navTab.isNullOrBlank()) {
            when (navTab) {
                "PROGRESS" -> selectedTab = MainTab.PROGRESS
                "DRIVE" -> selectedTab = MainTab.DRIVE
                "SETTINGS" -> selectedTab = MainTab.SETTINGS
                "FILES" -> selectedTab = MainTab.FILES
            }
        }
    }

    // Sequential system permission launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !com.example.telegrambackup.ui.auth.isNotificationPermissionGranted(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val storageLegacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkAndRequestNotificationPermission()
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkAndRequestNotificationPermission()
    }

    fun requestPermissionsDirectly() {
        if (!com.example.telegrambackup.ui.auth.isStoragePermissionGranted(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (_: Exception) {
                    try {
                        manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {}
                }
            } else {
                storageLegacyLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            checkAndRequestNotificationPermission()
        }
    }

    LaunchedEffect(session.isLoggedIn) {
        if (session.isLoggedIn) {
            if (!com.example.telegrambackup.ui.auth.areAllRequiredPermissionsGranted(context)) {
                requestPermissionsDirectly()
            }
            displayUserName = prefs.displayName
            displayUserAvatarUri = com.example.telegrambackup.utils.ProfileManager.getAvatarUri(context)?.toString() ?: prefs.profilePictureUri
            scope.launch(Dispatchers.IO) {
                try {
                    com.example.telegrambackup.utils.DateRecoveryHelper.recoverAuthenticDates(context)
                } catch (_: Exception) {}
                try { SettingsDbManager.fetchAndSyncSettingsOnAppOpen(context) } catch (_: Exception) {}
                try { SettingsDbManager.restoreCloudFileIndexToRoom(context) } catch (_: Exception) {}
                try { com.example.telegrambackup.network.BotFleetManager.ensureInitialized(context, force = true) } catch (_: Exception) {}
                try { BackupProgressTracker.hydrateRecentCompleted(context) } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    displayUserName = prefs.displayName
                    displayUserAvatarUri = com.example.telegrambackup.utils.ProfileManager.getAvatarUri(context)?.toString() ?: prefs.profilePictureUri
                }
                if (prefs.autoBackupEnabled && !prefs.stopAllBackups) {
                    try {
                        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    if (!session.isLoggedIn) {
        AuthScreen(
            authRepository = authRepository,
            onAuthSuccess = {
                requestPermissionsDirectly()
            }
        )
    } else if (!isAuthenticated) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = TelegramBlue,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "App Locked for Security",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Fingerprint or PIN authentication is required to open My Backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        BiometricHelper.showBiometricPrompt(
                            activity = activity,
                            title = "My Backup Cloud",
                            subtitle = "Authenticate Fingerprint or Device Lock PIN to open",
                            onSuccess = { isAuthenticated = true },
                            onError = { errorMsg ->
                                Toast.makeText(context, "Auth Error: $errorMsg", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Unlock with Biometrics", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = if (MaterialTheme.isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .width(320.dp)
                        .then(
                            if (MaterialTheme.isLiquidGlass) {
                                Modifier.liquidGlassCard(
                                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                                    elevation = 16.dp,
                                    highlightAlpha = 0.45f
                                )
                            } else Modifier
                        )
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.PROFILE
                                scope.launch { drawerState.close() }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarFile = remember(displayUserAvatarUri) { com.example.telegrambackup.utils.ProfileManager.getAvatarFile(context) }
                            if (avatarFile.exists() && avatarFile.length() > 0) {
                                coil.compose.AsyncImage(
                                    model = avatarFile,
                                    contentDescription = "Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    displayUserName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayUserName,
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                userEmail,
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Section: CORE
                        Text("CORE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                        val drawerItemColors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                            label = { Text("Clean (Storage)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.HOME,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                targetCategoryTitle = null
                                selectedTab = MainTab.HOME
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text("Browse (Files)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.FILES,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                targetCategoryTitle = null
                                selectedTab = MainTab.FILES
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CloudQueue, contentDescription = null) },
                            label = { Text("Cloud Drive", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.DRIVE,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.DRIVE
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            label = { Text("Monthly Backup", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.MONTHLY_ARCHIVE,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.MONTHLY_ARCHIVE
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                            label = { Text("Local Storage", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.LOCAL,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.LOCAL
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 12.dp))
                        Spacer(modifier = Modifier.height(4.dp))

                        // Section: BACKUP & TOOLS
                        Text("TOOLS & SYNC", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.HourglassTop, contentDescription = null) },
                            label = { Text("Upload Progress", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.PROGRESS,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.PROGRESS
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                            label = { Text("Trash Bin", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.TRASH,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.TRASH
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Block, contentDescription = null) },
                            label = { Text("Backup Exceptions", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.EXCEPTIONS,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.EXCEPTIONS
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                            label = { Text("Activity Log", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.ACTIVITY,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.ACTIVITY
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null) },
                            label = { Text("Vault Health & Recovery", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.VAULT_HEALTH,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.VAULT_HEALTH
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF59E0B)) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("AI Photo Search", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = TelegramBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "NEW",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TelegramBlue,
                                                fontSize = 9.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            selected = false,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                scope.launch { drawerState.close() }
                                showAiSearchDialog = true
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 12.dp))
                        Spacer(modifier = Modifier.height(4.dp))

                        // Section: SYSTEM
                        Text("SYSTEM", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                            label = { Text("Setup Guide", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.GUIDE,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.GUIDE
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) },
                            selected = selectedTab == MainTab.SETTINGS,
                            onClick = {
                                triggerHapticFeedback(context, haptic)
                                selectedTab = MainTab.SETTINGS
                                scope.launch { drawerState.close() }
                            },
                            shape = CircleShape,
                            colors = drawerItemColors
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        label = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = {
                            triggerHapticFeedback(context, haptic)
                            scope.launch { drawerState.close() }
                            authRepository.logout()
                        },
                        shape = CircleShape,
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollNavBarConnection),
                containerColor = if (MaterialTheme.isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    val isCleanOrBrowseRoot = (selectedTab == MainTab.HOME || selectedTab == MainTab.FILES) && targetCategoryTitle == null

                    if (isCleanOrBrowseRoot) {
                        Surface(
                            shape = CircleShape,
                            color = if (MaterialTheme.isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = if (MaterialTheme.isLiquidGlass) 0.dp else 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .statusBarsPadding()
                                .height(52.dp)
                                .then(
                                    if (MaterialTheme.isLiquidGlass) {
                                        Modifier.liquidGlassPill(elevation = 10.dp)
                                    } else Modifier
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSearchActive) {
                                    IconButton(onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        isSearchActive = false
                                        topSearchQuery = ""
                                    }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = topSearchQuery,
                                        onValueChange = { topSearchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp
                                        ),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (topSearchQuery.isEmpty()) {
                                                    Text(
                                                        "Search in Files...",
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 15.sp
                                                        )
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    IconButton(onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        showAiSearchDialog = true
                                    }) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Photo & Document Search", tint = Color(0xFFF59E0B), modifier = Modifier.size(19.dp))
                                    }
                                    if (topSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { topSearchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                } else {
                                    IconButton(onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu", tint = MaterialTheme.colorScheme.onSurface)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable {
                                                triggerHapticFeedback(context, haptic)
                                                isSearchActive = true
                                                if (selectedTab != MainTab.FILES) {
                                                    selectedTab = MainTab.FILES
                                                }
                                            }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "Search in Files",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                fontSize = 15.sp
                                            )
                                        )
                                    }

                                    // ✨ AI Photo & Document Search Quick Button on Main Screen
                                    Surface(
                                        shape = CircleShape,
                                        color = TelegramBlue.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                triggerHapticFeedback(context, haptic)
                                                showAiSearchDialog = true
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = "AI Photo & Document Search",
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "AI",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.5.sp
                                                )
                                            )
                                        }
                                    }

                                    com.example.telegrambackup.ui.common.SyncStatusPill(
                                        modifier = Modifier.padding(end = 6.dp),
                                        onClick = {
                                            triggerHapticFeedback(context, haptic)
                                            selectedTab = MainTab.PROGRESS
                                        }
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(TelegramBlue.copy(alpha = 0.2f))
                                            .clickable {
                                                triggerHapticFeedback(context, haptic)
                                                selectedTab = MainTab.PROFILE
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val avatarFile = remember(displayUserAvatarUri) { com.example.telegrambackup.utils.ProfileManager.getAvatarFile(context) }
                                        if (avatarFile.exists() && avatarFile.length() > 0) {
                                            coil.compose.AsyncImage(
                                                model = avatarFile,
                                                contentDescription = "Profile",
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = displayUserName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if ((selectedTab == MainTab.FILES && targetCategoryTitle != null) || selectedTab == MainTab.DRIVE || selectedTab == MainTab.EXCEPTIONS) {
                        // Do NOT render duplicate TopAppBar: BackupExplorerScreen, DriveExplorerScreen & BackupExceptionsScreen render their own dedicated, sleek M3 headers
                    } else {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when {
                                        targetCategoryTitle != null -> targetCategoryTitle ?: "Files"
                                        selectedTab == MainTab.DRIVE -> "Cloud Drive"
                                        selectedTab == MainTab.LOCAL -> "Local Storage"
                                        selectedTab == MainTab.PROGRESS -> "Upload Progress"
                                        selectedTab == MainTab.MONTHLY_ARCHIVE -> "Monthly Archive"
                                        selectedTab == MainTab.GUIDE -> "Setup Guide"
                                        selectedTab == MainTab.TRASH -> "Trash Bin"
                                        selectedTab == MainTab.EXCEPTIONS -> "Backup Exceptions"
                                        selectedTab == MainTab.ACTIVITY -> "Activity Log"
                                        selectedTab == MainTab.VAULT_HEALTH -> "Vault Health & Recovery"
                                        selectedTab == MainTab.SETTINGS -> "Settings"
                                        selectedTab == MainTab.PROFILE -> "My Profile"
                                        else -> "Files"
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp
                                    ),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                if (targetCategoryTitle != null) {
                                    IconButton(onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        targetCategoryTitle = null
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu")
                                    }
                                }
                            },
                            actions = {
                                com.example.telegrambackup.ui.common.SyncStatusPill(
                                    modifier = Modifier.padding(end = 4.dp),
                                    onClick = {
                                        triggerHapticFeedback(context, haptic)
                                        selectedTab = MainTab.PROGRESS
                                    }
                                )
                                IconButton(onClick = {
                                    triggerHapticFeedback(context, haptic)
                                    if (prefs.botToken.isEmpty() || prefs.chatId.isEmpty()) {
                                        Toast.makeText(context, "Please configure Bot Token & Chat ID in Settings first!", Toast.LENGTH_LONG).show()
                                        selectedTab = MainTab.SETTINGS
                                        return@IconButton
                                    }
                                    try {
                                        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                            .build()
                                        WorkManager.getInstance(context).enqueueUniqueWork("ManualSyncWork", ExistingWorkPolicy.KEEP, workRequest)
                                        Toast.makeText(context, "Syncing files to Telegram Cloud...", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Sync Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync Cloud", tint = TelegramBlue)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = isNavBarVisibleState.value,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                        ) + fadeIn(animationSpec = tween(200)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        ) + fadeOut(animationSpec = tween(160))
                    ) {
                        val visibleTabsKeys = remember(prefs.bottomNavVisibleTabs) {
                            val parsed = prefs.bottomNavVisibleTabs.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            if (parsed.size >= 2) parsed else listOf("HOME", "FILES", "DRIVE", "MONTHLY_ARCHIVE", "SETTINGS")
                        }

                        val navHeight = when (prefs.bottomNavHeightMode) {
                            1 -> 58.dp
                            2 -> 52.dp
                            else -> 62.dp
                        }

                        val allTabsConfig = listOf(
                            KomiNavItemData(MainTab.HOME, "HOME", "Clean", Icons.Default.CleaningServices),
                            KomiNavItemData(MainTab.FILES, "FILES", "Browse", Icons.Default.Folder),
                            KomiNavItemData(MainTab.DRIVE, "DRIVE", "Drive", Icons.Default.CloudQueue),
                            KomiNavItemData(MainTab.MONTHLY_ARCHIVE, "MONTHLY_ARCHIVE", "Monthly", Icons.Default.CalendarMonth),
                            KomiNavItemData(MainTab.PROGRESS, "PROGRESS", "Progress", Icons.Default.HourglassTop),
                            KomiNavItemData(MainTab.SETTINGS, "SETTINGS", "Settings", Icons.Default.Settings)
                        )

                        val activeTabsToDisplay = allTabsConfig.filter { visibleTabsKeys.contains(it.key) }

                        if (AudioPlayerManager.hasActiveTrack) {
                            ModernMiniPlayerCapsule(
                                onExpand = { AudioPlayerManager.isExpandedSheetOpen = true }
                            )
                        }

                        KomiBottomBar(
                            items = activeTabsToDisplay,
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                targetCategoryTitle = null
                                selectedTab = tab
                                isNavBarVisibleState.value = true
                            },
                            barHeight = navHeight,
                            labelMode = prefs.bottomNavLabelMode,
                            hapticFeedbackEnabled = prefs.bottomNavHapticFeedback
                        )
                    }

                    if (AudioPlayerManager.isExpandedSheetOpen && AudioPlayerManager.hasActiveTrack) {
                        ModernAudioPlayerSheet(
                            filePathOrUrl = AudioPlayerManager.activeTrackPath ?: "",
                            fileName = AudioPlayerManager.activeTrackName ?: "",
                            onDismiss = { AudioPlayerManager.isExpandedSheetOpen = false }
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(120, easing = LinearOutSlowInEasing)).togetherWith(
                                fadeOut(animationSpec = tween(90, easing = FastOutLinearInEasing))
                            )
                        },
                        label = "MainTabAnimatedContent",
                        modifier = Modifier.fillMaxSize()
                    ) { currentTab ->
                        when (currentTab) {
                            MainTab.HOME -> DashboardHomeScreen(
                                userName = displayUserName,
                                recentFiles = liveUploadedFiles,
                                onQuickActionClick = { catIndex ->
                                    if (catIndex == -1) {
                                        selectedTab = MainTab.TRASH
                                    } else if (catIndex == -2) {
                                        selectedTab = MainTab.VAULT_HEALTH
                                    } else if (catIndex == -3) {
                                        showAiSearchDialog = true
                                    } else {
                                        explorerInitialTab = 2
                                        targetCategoryTitle = when (catIndex) {
                                            0 -> "Images"
                                            1 -> "Videos"
                                            2 -> "Documents & other"
                                            3 -> "Downloads"
                                            4 -> "Apps"
                                            5 -> "Audio"
                                            else -> "Images"
                                        }
                                        selectedTab = MainTab.FILES
                                    }
                                },
                                onViewAllClick = {
                                    explorerInitialTab = 0
                                    targetCategoryTitle = null
                                    selectedTab = MainTab.FILES
                                },
                                onFileClick = { file ->
                                    FileViewerHelper.openFile(context, file.path, file.fileName, file.telegramFileId)
                                }
                            )
                            MainTab.DRIVE -> DriveExplorerScreen(
                                externalSearchQuery = topSearchQuery,
                                onMenuClick = {
                                    triggerHapticFeedback(context, haptic)
                                    scope.launch { drawerState.open() }
                                }
                            )
                            MainTab.LOCAL -> LocalFilesScreen()
                            MainTab.PROGRESS -> LiveProgressScreen()
                            MainTab.FILES -> BackupExplorerScreen(
                                initialTab = explorerInitialTab,
                                initialCategoryTitle = targetCategoryTitle,
                                externalSearchQuery = topSearchQuery,
                                onCategorySelected = { newCat -> targetCategoryTitle = newCat },
                                onBackToCategories = { targetCategoryTitle = null }
                            )
                            MainTab.ACTIVITY -> ActivityScreen()
                            MainTab.MONTHLY_ARCHIVE -> MonthlyArchiveScreen()
                            MainTab.GUIDE -> GuideScreen()
                            MainTab.TRASH -> TrashBinScreen()
                            MainTab.EXCEPTIONS -> BackupExceptionsScreen(onNavigateBack = { selectedTab = MainTab.HOME })
                            MainTab.SETTINGS -> BackupSettingsScreen(
                                onThemeChanged = onThemeChanged,
                                onLogout = { authRepository.logout() },
                                onNameUpdated = { newName ->
                                    displayUserName = newName
                                    displayUserAvatarUri = com.example.telegrambackup.utils.ProfileManager.getAvatarUri(context)?.toString() ?: prefs.profilePictureUri
                                }
                            )
                            MainTab.PROFILE -> ProfileScreen(
                                userName = displayUserName,
                                userEmail = userEmail,
                                onLogout = { authRepository.logout() },
                                onProfileUpdated = { newName, newAvatar ->
                                    displayUserName = newName
                                    displayUserAvatarUri = newAvatar
                                }
                            )
                            MainTab.VAULT_HEALTH -> VaultHealthScreen(
                                onNavigateToProgress = { selectedTab = MainTab.PROGRESS },
                                onFileClick = { file ->
                                    FileViewerHelper.openFile(context, file.path, file.fileName, file.telegramFileId)
                                }
                            )
                        }
                    }

                    com.example.telegrambackup.ui.common.DynamicTopProcessBar(
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    if (appUpdateInfo != null) {
        com.example.telegrambackup.ui.update.AppUpdaterDialog(
            versionInfo = appUpdateInfo!!,
            onDismiss = {
                appUpdateInfo = null
            }
        )
    }

    if (showWhatsNewDialog) {
        com.example.telegrambackup.ui.update.WhatsNewDialog(
            versionName = currentInstalledVersionName,
            onDismiss = {
                prefs.lastSeenWhatsNewVersion = currentInstalledVersionName
                showWhatsNewDialog = false
            }
        )
    }

    if (showAiSearchDialog) {
        com.example.telegrambackup.ui.ai.AiPhotoSearchDialog(
            initialQuery = topSearchQuery,
            onDismiss = { showAiSearchDialog = false },
            onOpenSettings = {
                showAiSearchDialog = false
                selectedTab = MainTab.SETTINGS
            }
        )
    }
}
