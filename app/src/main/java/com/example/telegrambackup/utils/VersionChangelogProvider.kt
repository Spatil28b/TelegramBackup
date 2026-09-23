package com.example.telegrambackup.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald

data class ChangelogEntry(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val description: String,
    val tag: String = "NEW"
)

data class VersionRelease(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val highlights: String,
    val entries: List<ChangelogEntry>
)

object VersionChangelogProvider {

    private val releases = listOf(
        VersionRelease(
            versionName = "2.1.95",
            versionCode = 98,
            releaseDate = "September 2026",
            highlights = "Ubiquitous 3-Dots File Actions, Share, Rename, Technical Properties & System Chooser",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.MoreVert,
                    TelegramBlue,
                    "Ubiquitous 3-Dots Menu",
                    "3-dots overflow action button added to all file cards across the entire app: Cloud Explorer (Grid & List), Drive Explorer, Home Recent Files, Trash Bin, Monthly Archive, and Local Storage.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.MenuOpen,
                    Color(0xFF8B5CF6),
                    "Unified Material 3 Action Sheet",
                    "New bottom sheet modal offering 1-tap Preview/Open, Cloud Share Link & PIN, System Share, Download to Device, Rename, Properties, and Move to Trash.",
                    "FEATURE"
                ),
                ChangelogEntry(
                    Icons.Default.Share,
                    Color(0xFF6366F1),
                    "Native Android App Sharing",
                    "Send files directly to WhatsApp, Telegram, Gmail, Bluetooth with automatic FileProvider URI granting and on-demand cloud retrieval.",
                    "SHARE"
                ),
                ChangelogEntry(
                    Icons.Default.Edit,
                    TelegramEmerald,
                    "In-App File Renaming",
                    "Rename files and folders instantly with synchronized updates across local Room DB, chunk progress tracker, and PostgreSQL cloud index.",
                    "MANAGEMENT"
                ),
                ChangelogEntry(
                    Icons.Default.Info,
                    Color(0xFF3B82F6),
                    "Technical Properties & Metadata",
                    "Inspect exact file size in bytes, complete storage path, Telegram File ID, Message ID, chunk breakdown, and AES-256-GCM encryption with 1-tap copy buttons.",
                    "INSPECTOR"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.94",
            versionCode = 97,
            releaseDate = "September 2026",
            highlights = "Trash Bin Real-Time Flow & Resilience, Guaranteed Deletion & Material 3 UX",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.DeleteSweep,
                    TelegramEmerald,
                    "Real-Time Reactive Flow",
                    "Trash Bin now collects a real-time Room DB Flow. Files trashed, restored, or emptied update instantly across all screens with zero manual refresh.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    TelegramBlue,
                    "Guaranteed Permanent Purge",
                    "Added forceLocalDelete engine ensuring local cache, database, and chunk progress are reliably cleared even if Telegram channel bot rights are limited.",
                    "STABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.Warning,
                    Color(0xFFEF4444),
                    "Material 3 Safety Dialogs",
                    "Confirmation dialogs before Empty Trash, batch deletion, and single permanent deletion prevent accidental data loss.",
                    "SAFETY"
                ),
                ChangelogEntry(
                    Icons.Default.Tune,
                    Color(0xFF8B5CF6),
                    "Quick Auto-Clean Selector",
                    "Adjust auto-clean retention (Never, 7d, 14d, 30d, 60d, 90d) directly from the Trash Bin with automated timestamp healing.",
                    "CONFIG"
                ),
                ChangelogEntry(
                    Icons.Default.Search,
                    Color(0xFF10B981),
                    "Search & 'Select All' Batching",
                    "Find deleted files instantly with live search filtering, and select all items with a single tap for batch restore or purge.",
                    "UX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.88",
            versionCode = 91,
            releaseDate = "September 2026",
            highlights = "Deep Scan Cleanup, Zero-Loss Protection & Modernized Haptics",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.CleaningServices,
                    TelegramEmerald,
                    "Code Clean & Optimization",
                    "Eliminated 1,693 lines of dead code and obsolete prototypes under a strict triple cross-check standard.",
                    "OPTIMIZED"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    TelegramBlue,
                    "Zero-Loss Process Protection",
                    "Background backup jobs, workers, and active sync tasks run with uninterrupted execution and resilient lifecycle boundaries.",
                    "STABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.Vibration,
                    Color(0xFF8B5CF6),
                    "Modernized Haptic Engine",
                    "Unified VibratorManager system effects with backward-compatible fallbacks, eliminating runtime deprecation.",
                    "HAPTICS"
                ),
                ChangelogEntry(
                    Icons.Default.FolderZip,
                    Color(0xFF10B981),
                    "Streamlined ZIP Archiving",
                    "Enhanced ZipBackupDownloader with full multi-bot parallel chunk assembly and instant in-place AES-256 decryption.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.NotificationsActive,
                    Color(0xFFF59E0B),
                    "Unified Notification Receiver",
                    "Removed duplicate receivers; all notification quick actions now cleanly unified in BackupControlReceiver.",
                    "FIX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.81",
            versionCode = 84,
            releaseDate = "August 2026",
            highlights = "Bot Fleet Import/Export & Reinstall Data Persistence",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.CloudDownload,
                    TelegramEmerald,
                    "Import Bot Tokens & Fleet",
                    "Import bots easily via JSON backup or pasted token list with automatic validation and Room DB storage.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.CloudUpload,
                    TelegramBlue,
                    "Export Bot Tokens & Info",
                    "1-Tap export of full bot fleet metadata with Copy to Clipboard, Share via Apps, and Save to Downloads.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Sync,
                    Color(0xFF8B5CF6),
                    "Real-Time Database Sync",
                    "All added, updated, or removed bots are immediately saved to Room DB and synchronized to cloud database.",
                    "DATABASE"
                ),
                ChangelogEntry(
                    Icons.Default.Restore,
                    Color(0xFF10B981),
                    "Uninstall/Reinstall Zero Loss Fix",
                    "Fixed profile, encryption keys, and bot fleet restoration so re-logging in perfectly restores full data.",
                    "FIX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.80",
            versionCode = 83,
            releaseDate = "August 2026",
            highlights = "UI Glitch & Multi-Select Action Fixes",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.TouchApp,
                    TelegramEmerald,
                    "Category Multi-Select Fix",
                    "Fixed selection toggle in Unbacked Files Category View so tapping items properly selects them.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Download,
                    TelegramBlue,
                    "Recent Files ZIP Downloader",
                    "Connected multi-selection action bar in Home Dashboard directly to the 1-Tap ZIP Downloader.",
                    "IMPROVEMENT"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF8B5CF6),
                    "Scroll & GC Memory Optimization",
                    "Eliminated per-item date formatting allocations in Monthly Archive for 60fps smooth scrolling.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Sync,
                    Color(0xFF10B981),
                    "Live Backup State Sync",
                    "Pause/Resume toggle in Live Progress screen remains 100% synchronized with notification actions.",
                    "STABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.79",
            versionCode = 82,
            releaseDate = "August 2026",
            highlights = "Dynamic What's New & Full Version History Explorer",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    TelegramEmerald,
                    "Dynamic 'What's New' Engine",
                    "What's New automatically updates on every release with real version-specific release notes and version history.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.History,
                    TelegramBlue,
                    "Full Version Changelog Explorer",
                    "Browse changelogs from previous releases directly within the app under Settings > About & Updates.",
                    "IMPROVEMENT"
                ),
                ChangelogEntry(
                    Icons.Default.CloudSync,
                    Color(0xFF8B5CF6),
                    "Live Dynamic Release Sync",
                    "Fetches latest GitHub and server release notes in real-time on update notifications.",
                    "FIX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.78",
            versionCode = 81,
            releaseDate = "August 2026",
            highlights = "Long-Term Stability & Zero-Degradation Scaling",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Speed,
                    TelegramEmerald,
                    "Room DB Composite Indexing",
                    "5 composite SQLite indexes for sub-millisecond queries on 50,000+ files with zero table scans.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF8B5CF6),
                    "Integer Overflow & Hash Protection",
                    "Bitwise positive masking prevents integer overflow bugs in long-term round-robin bot rotation.",
                    "STABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.CleaningServices,
                    Color(0xFF10B981),
                    "Automated Background Garbage Collection",
                    "Routine maintenance auto-purges expired trash and orphaned chunks in background.",
                    "OPTIMIZATION"
                ),
                ChangelogEntry(
                    Icons.Default.CameraAlt,
                    Color(0xFF06B6D4),
                    "Android 14/15 Service Compatibility",
                    "Fixed foreground service type flags to guarantee 100% background stability on Android 14 and 15.",
                    "FIX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.77",
            versionCode = 80,
            releaseDate = "August 2026",
            highlights = "Modern Minimal Notifications & 1-Tap Quick Controller",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.NotificationsActive,
                    TelegramBlue,
                    "Persistent 1-Tap Quick Controller",
                    "Always-visible sleek status bar tile with 1-tap Start/Stop Backup & direct Cloud Drive access.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Tune,
                    Color(0xFFF59E0B),
                    "4 Separated Notification Channels",
                    "Dedicated independent streams for Controller, Uploads, Downloads, and System Alerts.",
                    "IMPROVEMENT"
                ),
                ChangelogEntry(
                    Icons.Default.Download,
                    TelegramEmerald,
                    "Dedicated Downloads Stream",
                    "Real-time download speeds and automatic tap-to-open completed file notifications.",
                    "NEW"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.76",
            versionCode = 79,
            releaseDate = "August 2026",
            highlights = "Multi-Bot Fleet Frictionless Addition & Bypass",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.SmartToy,
                    TelegramEmerald,
                    "Smart Token Sanitizer",
                    "Automatically cleans pasted bot tokens: strips URL prefixes, bot tags, and trailing query parameters.",
                    "IMPROVEMENT"
                ),
                ChangelogEntry(
                    Icons.Default.FlashOn,
                    Color(0xFFF59E0B),
                    "Force Add / Bypass Option",
                    "Add bots directly into your fleet even during Telegram network latency or rate-limit cooldowns.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Sync,
                    TelegramBlue,
                    "Instant PostgreSQL Cloud Sync",
                    "All bot and channel fleet changes sync to PostgreSQL cloud manifest immediately in real-time.",
                    "SYNC"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.75",
            versionCode = 78,
            releaseDate = "August 2026",
            highlights = "Production Deep Scan & Zero-Loss Reinstall Restore",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Lock,
                    Color(0xFF8B5CF6),
                    "Master Key Reinstall Preservation",
                    "Restores cloud master encryption key without being blocked by clean install UUID defaults.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Refresh,
                    TelegramEmerald,
                    "Reactive Room Database Flows",
                    "Live Kotlin Flow observers keep home storage metrics and file explorer refreshed in real-time.",
                    "UI"
                )
            )
        )
    )

    fun getReleaseForVersion(versionName: String): VersionRelease {
        val clean = versionName.replace("^v".toRegex(), "").trim()
        return releases.firstOrNull { it.versionName == clean } ?: releases.first()
    }

    fun getAllReleases(): List<VersionRelease> = releases

    /**
     * Parses raw release notes text (from server or GitHub API) into structured ChangelogEntry list.
     */
    fun parseRawReleaseNotes(raw: String, defaultVersion: String): List<ChangelogEntry> {
        if (raw.isBlank()) return getReleaseForVersion(defaultVersion).entries

        val lines = raw.lines().filter { it.trim().startsWith("-") || it.trim().startsWith("•") || it.trim().startsWith("*") }
        if (lines.isEmpty()) {
            return listOf(
                ChangelogEntry(
                    icon = Icons.Default.AutoAwesome,
                    iconTint = TelegramEmerald,
                    title = "Update v$defaultVersion",
                    description = raw.trim(),
                    tag = "UPDATE"
                )
            )
        }

        return lines.map { line ->
            val clean = line.replace("^[-•*]\\s*".toRegex(), "").trim()
            val colonIdx = clean.indexOf(":")
            val title = if (colonIdx > 0) clean.substring(0, colonIdx).trim() else clean
            val desc = if (colonIdx > 0 && colonIdx + 1 < clean.length) clean.substring(colonIdx + 1).trim() else ""

            val lower = clean.lowercase()
            val (icon, tint) = when {
                lower.contains("bot") || lower.contains("fleet") -> Pair(Icons.Default.SmartToy, TelegramEmerald)
                lower.contains("notif") || lower.contains("controller") -> Pair(Icons.Default.NotificationsActive, TelegramBlue)
                lower.contains("download") || lower.contains("zip") -> Pair(Icons.Default.Download, Color(0xFFF59E0B))
                lower.contains("index") || lower.contains("sql") || lower.contains("speed") -> Pair(Icons.Default.Speed, TelegramEmerald)
                lower.contains("security") || lower.contains("key") || lower.contains("lock") -> Pair(Icons.Default.Lock, Color(0xFF8B5CF6))
                lower.contains("search") -> Pair(Icons.Default.Search, Color(0xFF06B6D4))
                lower.contains("trash") || lower.contains("clean") -> Pair(Icons.Default.CleaningServices, Color(0xFF10B981))
                lower.contains("camera") || lower.contains("photo") -> Pair(Icons.Default.CameraAlt, TelegramEmerald)
                else -> Pair(Icons.Default.AutoAwesome, TelegramBlue)
            }

            ChangelogEntry(
                icon = icon,
                iconTint = tint,
                title = title.replace("^[^\u0000-\u007F]+".toRegex(), "").trim().ifEmpty { "Feature Update" },
                description = desc.ifEmpty { clean },
                tag = if (lower.contains("fix")) "FIX" else if (lower.contains("speed") || lower.contains("perf")) "SPEED" else "NEW"
            )
        }
    }
}
