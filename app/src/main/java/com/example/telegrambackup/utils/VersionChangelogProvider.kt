package com.example.telegrambackup.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
            versionName = "2.4.7",
            versionCode = 131,
            releaseDate = "September 2026",
            highlights = "Local Storage Crash Fix, 0ms Browse Lag, ID Card Recognition, Medical Prescriptions & Building/Society/College AI Search",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFF10B981),
                    "Local Storage Crash Fix",
                    "Eliminated Jetpack Compose duplicate key crash in Category and Folder views with compound keys, storage path deduplication, and error recovery.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    TelegramBlue,
                    "Browse Section Freeze Elimination",
                    "Offloaded monthly timeline calculations to background thread with date caching and replaced 14 loops with a 2ms single-pass accumulator, eliminating 1s stutter.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Badge,
                    Color(0xFF8B5CF6),
                    "ID Card AI & OCR Recognition",
                    "Instant on-device OCR and Gemini Vision recognition for student/college IDs, office badges, Aadhaar cards (UIDAI), Voter IDs, and Driving Licenses.",
                    "AI SEARCH"
                ),
                ChangelogEntry(
                    Icons.Default.MedicalServices,
                    Color(0xFFEC4899),
                    "Medical Prescription Recognition",
                    "High-accuracy detection for doctor consultation slips, clinic notes, Rx pads, dosage instructions, medicine slips, and pharmacy bills.",
                    "OCR ENGINE"
                ),
                ChangelogEntry(
                    Icons.Default.LocationCity,
                    Color(0xFFF59E0B),
                    "College & Building/Society Search",
                    "Deep visual & text recognition for colleges, universities, residential societies, apartment complexes, towers, and named locations (e.g. 'XYZ College', 'ABC Society').",
                    "VISION"
                ),
                ChangelogEntry(
                    Icons.Default.Search,
                    Color(0xFF06B6D4),
                    "Smart Query Tokenizer & Filler Stripping",
                    "Automatically strips query filler words ('image', 'photo', 'pic', 'show me') while preserving crucial short tokens ('id', 'rx', 'dl', 'dr') to avoid false negatives.",
                    "SEARCH"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.6",
            versionCode = 130,
            releaseDate = "September 2026",
            highlights = "Updater 404 Resolution, Material You Monet Theming, Duplicate Cleaner, Floating Audio Mini-Player, Smooth Thumbnails & Night Backup",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.SystemUpdate,
                    TelegramBlue,
                    "In-App Updater 404 Resolution",
                    "Direct pre-signed CDN resolution and redirect-sanitized downloads eliminate HTTP 404 on updates.",
                    "UPDATER FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Palette,
                    Color(0xFF8B5CF6),
                    "Material You Dynamic Theming",
                    "Extracts Monet dynamic wallpaper color schemes on Android 12+ with dynamic dark/light surface tones.",
                    "MONET THEME"
                ),
                ChangelogEntry(
                    Icons.Default.CleaningServices,
                    Color(0xFF10B981),
                    "Visual Duplicate Cleaner",
                    "Side-by-side thumbnail comparison cards and smart 'Auto-Keep Best' algorithm favoring original camera captures.",
                    "CLEANER"
                ),
                ChangelogEntry(
                    Icons.Default.MusicNote,
                    Color(0xFFEC4899),
                    "Mini Audio Player & Speed Controls",
                    "Floating audio mini-player capsule above navigation with background playback and 0.5x–2.0x playback speeds.",
                    "AUDIO PLAYER"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFFF59E0B),
                    "60/120fps Smooth Thumbnail Scrolling",
                    "Optimized thumbnail downsampling while strictly preserving 100% original uncompressed quality for downloads, viewer, and sharing.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Alarm,
                    Color(0xFF3B82F6),
                    "Strict Wi-Fi Night Backup",
                    "Exact alarm wakeup at 2:00 AM immune to Android Doze mode (AlarmManager.setAlarmClock) with Wi-Fi network validation.",
                    "NIGHT BACKUP"
                ),
                ChangelogEntry(
                    Icons.Default.Notifications,
                    TelegramEmerald,
                    "Modernized M3 Notifications & App Icon",
                    "Upgraded notifications to clean Material 3 typography and redesigned app icon to an obsidian/emerald vault shield.",
                    "UI DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.Folder,
                    TelegramBlue,
                    "Internal Storage Navigation Polish",
                    "Fixed internal storage directory resolution, folder category expansion, and eliminated tab navigation freezes.",
                    "STORAGE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.5",
            versionCode = 129,
            releaseDate = "September 2026",
            highlights = "On-Device Google ML Kit OCR, Gemini 2.0/3.0 Auto-Healing, Buttery Smooth Tab Switching, Smart Nav Bar & Modern Theme Shades",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.DocumentScanner,
                    Color(0xFF10B981),
                    "On-Device Google ML Kit OCR",
                    "Integrated local high-speed Google ML Kit text recognition (10-20ms per photo) to find bonafide certificates, PAN cards, UPI payment receipts (₹10k), and bills with 100% precision.",
                    "NEW OCR"
                ),
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFFF59E0B),
                    "Gemini 2.0 / 3.0 Vision & Deprecation Fix",
                    "Resolved Gemini 2.5 Flash deprecation error with intelligent auto-healing failover to Gemini 2.0 Flash and Gemini 3.0 Flash for seamless multimodal analysis.",
                    "AI ENGINE"
                ),
                ChangelogEntry(
                    Icons.Default.Animation,
                    TelegramBlue,
                    "Buttery Smooth Tab Transitions & Smart Nav Bar",
                    "Eliminated tab switching lag with lightweight snappy crossfades; bottom nav bar never hides on non-scrollable screens and smoothly toggles on screen tap.",
                    "SMOOTH UX"
                ),
                ChangelogEntry(
                    Icons.Default.Palette,
                    Color(0xFF8B5CF6),
                    "Modern Theme Shades & Cohesive M3 Palettes",
                    "Upgraded all 6 themes with rich multi-tier surface containers, luminous borders, and dynamic theme-matched bottom navigation highlights.",
                    "THEME"
                ),
                ChangelogEntry(
                    Icons.Default.Rule,
                    Color(0xFFEF4444),
                    "Backup Exceptions Redesign",
                    "Overhauled Backup Exceptions screen with full Material 3 theme adaptability, clear breadcrumb navigation, and modern status badges.",
                    "UI OVERHAUL"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.4",
            versionCode = 128,
            releaseDate = "September 2026",
            highlights = "Single-Line Main Settings, Top-Right Badges across Icons & Themes, Ultra-Fast High-Accuracy AI Vision Search",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Palette,
                    Color(0xFFF59E0B),
                    "Refined Single-Line Badges & Top-Right Placement",
                    "Fixed App Icon 'Default' tag wrapping vertically; aligned clean, single-line tags ('Default', 'Active', 'OLED') at the top right of cards for all 6 themes and launcher icons.",
                    "UI FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Tune,
                    TelegramBlue,
                    "Single-Line Main Settings Hierarchy",
                    "Redesigned main settings items like Quick Controller and Cache & Storage to fit cleanly in a single line with rich descriptive subtitles preserved below.",
                    "CLEAN UI"
                ),
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF8B5CF6),
                    "High-Accuracy & 7x Faster AI Vision Search",
                    "Optimized Gemini Vision search with strict document and photo classification rules, downsampled 384px payloads, and concurrent batch processing for rapid, pinpoint results.",
                    "AI VISION"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.3",
            versionCode = 127,
            releaseDate = "September 2026",
            highlights = "AI Visual Photo & Document Search from Main Screen, Image-First Matching & Top 6 Essential Themes",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF8B5CF6),
                    "AI Photo & Document Search",
                    "Natural-language visual search for certificates, payment receipts, PAN cards, party photos, 10th marksheets & bills with image gallery results.",
                    "NEW AI"
                ),
                ChangelogEntry(
                    Icons.Default.Search,
                    TelegramBlue,
                    "Main Screen 1-Tap Search Icon",
                    "Dedicated glowing AI Search button directly on the main screen top search bar, Home dashboard card, and drawer for instant photo discovery.",
                    "SHORTCUT"
                ),
                ChangelogEntry(
                    Icons.Default.Palette,
                    TelegramEmerald,
                    "Top 6 Clean Visual Themes",
                    "Streamlined theme suite to the top 6 premium styles: System Default, Clean Light, Midnight Dark, Pure AMOLED Black, Emerald Forest & Sunset Amber.",
                    "THEMES"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.2",
            versionCode = 126,
            releaseDate = "September 2026",
            highlights = "Dynamic Google Gemini 2.0/2.5 Flash Auto-Discovery, Self-Healing Model Failover & Deprecated Model Migration",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF8B5CF6),
                    "Google ModelService Auto-Discovery",
                    "Dynamic detection of active models via Google's ModelService API, eliminating HTTP 404 connection errors from deprecated models like gemini-1.5-flash.",
                    "AI ENGINE"
                ),
                ChangelogEntry(
                    Icons.Default.Bolt,
                    TelegramBlue,
                    "Gemini 2.0 & 2.5 Flash Engine",
                    "Upgraded default vision intelligence engine to Google's latest Gemini 2.0 / 2.5 Flash architectures for sub-second responses and superior OCR precision.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Build,
                    TelegramEmerald,
                    "Self-Healing Model Fallover",
                    "Automatic runtime fallback and retry if any requested model is sunset or unsupported, ensuring 100% uninterrupted Ask Photo Q&A.",
                    "STABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.1",
            versionCode = 125,
            releaseDate = "September 2026",
            highlights = "Google Gemini Multimodal Vision & Ask Photos, High-Speed Gallery Thumbnails & Zero-Loss Chunk Upload Integrity",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF8B5CF6),
                    "Ask Photos & Document Intelligence",
                    "Ask natural language questions about your photos and documents like Google Photos. Instant presets for 10th/12th marksheets, 10k payment receipts, and ID documents.",
                    "AI VISION"
                ),
                ChangelogEntry(
                    Icons.Default.Key,
                    TelegramBlue,
                    "Single & Multiple API Key Failover",
                    "Add one or multiple Google Gemini API keys with automatic round-robin rotation and transparent failover on rate limits (HTTP 429).",
                    "GEMINI"
                ),
                ChangelogEntry(
                    Icons.Default.BatteryChargingFull,
                    TelegramEmerald,
                    "Zero Extreme Device Load",
                    "Images are downsampled efficiently using RGB_565 under 5MB RAM before cloud reasoning, preventing battery drain, heating, or thermal throttling.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Image,
                    Color(0xFFF59E0B),
                    "Ultra-Fast Gallery Thumbnails",
                    "2-tier caching with 30MB memory LRU and persistent 8-12KB WebP thumbnails on disk enables buttery smooth 120Hz scrolling without downloading full files.",
                    "GALLERY"
                ),
                ChangelogEntry(
                    Icons.Default.CheckCircle,
                    TelegramEmerald,
                    "Hardened Chunk Upload Integrity",
                    "Strict multi-chunk validation checks every chunk 1..totalChunks for non-empty file IDs and exact chunk counts before committing, guaranteeing zero missing chunks.",
                    "INTEGRITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.0",
            versionCode = 124,
            releaseDate = "September 2026",
            highlights = "System-Wide UI/UX Typography Overhaul, Zero Font-Padding Vertical Alignment & Modern Material 3 Type System",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.TextFields,
                    TelegramBlue,
                    "System-Wide Typography Engine",
                    "Redesigned the entire Compose typography scale with strict letter-spacing, optical weights, and proportional line-heights for crisp readability.",
                    "CORE UI"
                ),
                ChangelogEntry(
                    Icons.Default.CheckCircle,
                    TelegramEmerald,
                    "Zero Font-Padding Alignment",
                    "Eliminated legacy Android platform font-padding across all buttons, chips, tabs, search bars, and dialogs for pixel-perfect vertical centering.",
                    "POLISH"
                ),
                ChangelogEntry(
                    Icons.AutoMirrored.Filled.MenuBook,
                    Color(0xFF8B5CF6),
                    "Refined Navigation & Drawer Hierarchy",
                    "Upgraded Drawer category headers with clean tracking and bold section titles, plus enhanced TopAppBar typography with proper single-line truncation.",
                    "NAVIGATION"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFFF59E0B),
                    "Tabular Monospace Metrics",
                    "Introduced dedicated monospace numeric styles for file sizes, timestamps, bitrates, and progress counters to eliminate text jitter.",
                    "METRICS"
                ),
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF00796B),
                    "Activity & Card Text Polishing",
                    "Enhanced activity feed timestamps, status badges, metric counters, and secondary details with modern Material 3 contrast hierarchy.",
                    "CARDS"
                )
            )
        ),
        VersionRelease(
            versionName = "2.3.3",
            versionCode = 117,
            releaseDate = "September 2026",
            highlights = "Vault Health & File Integrity Hub, Incomplete Chunk Diagnosis & Historical Date Recovery",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.HealthAndSafety,
                    TelegramBlue,
                    "Vault Health & Recovery Hub",
                    "Brand-new dedicated section in Navigation Drawer and Clean Dashboard to inspect 100% of your cloud files with health score and diagnostic indicators.",
                    "NEW HUB"
                ),
                ChangelogEntry(
                    Icons.Default.Warning,
                    Color(0xFFF59E0B),
                    "Incomplete Chunk Diagnosis & Inspector",
                    "Instantly identifies files with missing or incomplete chunks with a visual chunk breakdown sheet showing all parts, file IDs, and upload status.",
                    "DIAGNOSTICS"
                ),
                ChangelogEntry(
                    Icons.Default.DateRange,
                    TelegramEmerald,
                    "Authentic Capture Date Recovery",
                    "Permanently fixed dates shifting to recent days by accurately parsing capture dates from Camera, WhatsApp, Screenshot filenames and local files.",
                    "DATE HEALING"
                ),
                ChangelogEntry(
                    Icons.Default.AutoFixHigh,
                    Color(0xFF8B5CF6),
                    "One-Tap Auto-Repair All",
                    "Restores authentic dates across your entire vault and syncs local chunk upload mappings to the cloud database with one tap.",
                    "REPAIR TOOL"
                ),
                ChangelogEntry(
                    Icons.Default.Upload,
                    Color(0xFF10B981),
                    "Resume Incomplete Uploads",
                    "Identifies files present on device storage that have missing chunks in the cloud, allowing 1-tap background upload resumption.",
                    "RESUME"
                )
            )
        ),
        VersionRelease(
            versionName = "2.3.2",
            versionCode = 116,
            releaseDate = "September 2026",
            highlights = "Permanent Fix for Incomplete Downloads, Share Links & Cloud Sync",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.CloudSync,
                    TelegramEmerald,
                    "Delta Sync Chunk Backfill",
                    "Delta sync now hydrates and pushes chunk metadata for existing cloud files, ensuring multi-part records in Neon DB are always complete.",
                    "CORE"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFF4CAF50),
                    "BackupWorker Partial Download Guard",
                    "Excludes app download/cache folders and strictly prevents partial or truncated files on disk from overwriting complete cloud backups.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    TelegramBlue,
                    "High-Speed Batch Database Indexing",
                    "Refactored backend sync to multi-row parameterized queries running in < 200ms, completely eliminating Vercel 504 gateway timeouts.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Download,
                    Color(0xFF2196F3),
                    "Universal Multi-Chunk Assembly",
                    "All preview and download operations query both local and cloud metadata to assemble multi-part files with full AES-256 decryption.",
                    "FIX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.3.1",
            versionCode = 115,
            releaseDate = "September 2026",
            highlights = "Comprehensive Multi-Chunk Link Sharing & On-Device Download Invariant Fix",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Share,
                    TelegramEmerald,
                    "Link Sharing Multi-Chunk Resolution",
                    "Server dynamically auto-heals chunk metadata from cloud database. Prohibits premature 1-chunk fallback stream cut-off at 4.19 MB.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Download,
                    TelegramBlue,
                    "Strict Chunk Completeness Guard",
                    "TelegramMediaResolver strictly validates that all sequential parts (1..N) exist before assembling. Eliminates false-success incomplete file saves.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFF4CAF50),
                    "Corrupted Download & Re-backup Prevention",
                    "Downloads verify post-decryption size against declared metadata (90% threshold) and immediately purge any truncated downloads, preventing BackupWorker index poisoning.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Edit,
                    Color(0xFF9C27B0),
                    "Preserve Metadata on Rename & Actions",
                    "Renaming files in Drive Explorer and File Options retains chunksJson, channelId, totalChunks, and botToken across cloud index and local Room database.",
                    "REFINEMENT"
                )
            )
        ),
        VersionRelease(
            versionName = "2.3.0",
            versionCode = 114,
            releaseDate = "September 2026",
            highlights = "Critical Download Fix — 4.19 MB Stall Resolved, Cloud Chunk Re-Sync, Retry with Backoff",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Download,
                    TelegramEmerald,
                    "Critical 4.19 MB Download Fix",
                    "Resolved persistent download failure where files stopped at exactly 4.19 MB. Root cause: 30-second HTTP timeout killed stream mid-chunk, missing chunk metadata caused hard abort, and no retry logic existed.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Cloud,
                    TelegramBlue,
                    "Cloud Chunk Re-Sync",
                    "Downloads now automatically fetch fresh chunk metadata from the cloud server when local database is empty (new device or reinstall). Chunks are persisted locally for future use.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Refresh,
                    Color(0xFFFF9800),
                    "Retry with Exponential Backoff",
                    "Each chunk download retries up to 3 times with 2s/4s/6s backoff instead of failing instantly on first network hiccup.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Timer,
                    Color(0xFF9C27B0),
                    "Extended Download Timeout",
                    "Switched file downloads from 30-second to 15-minute read timeout for reliable streaming on slow mobile connections.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFF4CAF50),
                    "Single-Stream Fallback",
                    "When chunk metadata is completely unavailable, attempts full-file single-stream download as last resort before reporting failure.",
                    "NEW"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.9",
            versionCode = 112,
            releaseDate = "September 2026",
            highlights = "Permanent Large File Sharing Fix, Random-Access Decryption & Chunk Completeness Guard",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Share,
                    TelegramEmerald,
                    "Permanent Large File Sharing Fix",
                    "Bit-exact random-access AES-256-CBC range decryption, Telegram CDN URL caching, and zero-RAM disk streaming solve downloads aborting or corrupting on large multi-part files.",
                    "SHARING"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    TelegramBlue,
                    "Chunk Completeness Safeguard",
                    "Android media resolver guarantees all sequential chunks are verified and present before initiating local assembly, eliminating truncated file corruption.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Cloud,
                    TelegramEmerald,
                    "Zero-Disk Stream Engine",
                    "Enhanced web sharing portal with live download speed (MB/s), ETA calculations, and automatic retry on network blips.",
                    "STREAMING"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.8",
            versionCode = 111,
            releaseDate = "September 2026",
            highlights = "TSpace Rebranding, Dynamic App Icon Switcher & Multi-Part Large File Sharing",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Cloud,
                    TelegramEmerald,
                    "Rebranded to TSpace",
                    "Official name upgraded to TSpace Cloud Storage & Vault across the entire system, onboarding dialogs, and notifications.",
                    "REBRAND"
                ),
                ChangelogEntry(
                    Icons.Default.Palette,
                    TelegramBlue,
                    "Dynamic App Launcher Icons",
                    "Choose between signature Emerald Forest Green (Default) and Cyber Electric Blue launcher icons in Settings > Appearance with instant live preview.",
                    "ICONS"
                ),
                ChangelogEntry(
                    Icons.Default.Share,
                    TelegramEmerald,
                    "Large File Sharing (>3.99 MB) Resolved",
                    "Deep multi-bot fleet failover and progressive client-side chunk assembly guarantee 50MB, 500MB+ files download and stream completely with live progress.",
                    "SHARING"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.7",
            versionCode = 110,
            releaseDate = "September 2026",
            highlights = "Large File Sharing Limit Fix, September Archive Recovery & Moved Files",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Share,
                    TelegramBlue,
                    "Large File Sharing (No 3.99 MB Limit)",
                    "Fixed chunk resolution pipeline so files of any size (50MB, 500MB, 2GB+) stream and download completely without being capped at 3.99 MB.",
                    "CORE"
                ),
                ChangelogEntry(
                    Icons.Default.DateRange,
                    TelegramEmerald,
                    "September Archive Healing",
                    "Fixed indexing argument mismatch and sanitized all corrupted or legacy July 2026 placeholder timestamps back to September 2026 and real file dates.",
                    "ARCHIVE"
                ),
                ChangelogEntry(
                    Icons.Default.FolderOpen,
                    TelegramBlue,
                    "Moved & Cloud File Opening",
                    "Smart local file resolver locates moved files across directories; cloud files assemble all multi-part chunks before opening for seamless playback.",
                    "RESOLVER"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.6",
            versionCode = 109,
            releaseDate = "September 2026",
            highlights = "Komi Store Floating Capsule Nav Bar Background Polish",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramBlue,
                    "Komi Store Floating Capsule Background",
                    "Restored the signature Komi Store floating pill capsule background with elevated surfaceContainer gradient, soft drop shadow, rounded pill borders, and active pill tab indicators.",
                    "UI"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.5",
            versionCode = 108,
            releaseDate = "September 2026",
            highlights = "1970 January Timestamp Self-Healing, 100% Internal Storage Replica in Monthly Archive, Komi Store Floating Transparent Navigation Bar, Cloud File Streaming in Archive",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.DateRange,
                    TelegramBlue,
                    "1970 January Timestamp Self-Healing",
                    "Fixed legacy corrupted/0-epoch timestamp bug shifting files into January 1970. All affected items are automatically mapped to July 2026 across Room database, backend, and UI.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Folder,
                    Color(0xFF8B5CF6),
                    "100% Internal Storage Replica in Archive",
                    "Monthly Archive strictly models your device's internal storage directory tree. No loose root file dumping; subfolders like DCIM, Download, and Pictures preserve exact filesystem paths.",
                    "ARCHIVE"
                ),
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramEmerald,
                    "Komi Store Floating Zero-Background Nav Bar",
                    "Eliminated solid background cutoffs, gutters, and shadows. The 6-item navigation bar floats completely transparently edge-to-edge above your content.",
                    "UI"
                ),
                ChangelogEntry(
                    Icons.Default.CloudSync,
                    Color(0xFFF59E0B),
                    "Direct Cloud File Opening in Archive",
                    "Files backed up to Telegram can now be opened, previewed, and streamed directly from inside the Monthly Archive folder views.",
                    "CLOUD"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.4",
            versionCode = 107,
            releaseDate = "September 2026",
            highlights = "Large File Streaming & Range Support, Google Files Selection UX, 100% Exact Device Monthly Archive Folder Structure, Modern Monochrome Notifications & Floating UI",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.CloudDownload,
                    TelegramBlue,
                    "High-Speed Large File Link Sharing",
                    "Added HTTP Range 206 seeking support for instant video/audio playback, parallel chunk pre-resolution, and dedicated single-chunk endpoints for unlimited file sizes.",
                    "STREAMING"
                ),
                ChangelogEntry(
                    Icons.Default.FolderZip,
                    Color(0xFF8B5CF6),
                    "100% Exact Device Monthly Folder Tree",
                    "Monthly Archive now mirrors the real internal storage directory tree down to every subfolder, with breadcrumb navigation, subfolder drilldown, and Category view toggle.",
                    "ARCHIVE"
                ),
                ChangelogEntry(
                    Icons.Default.TouchApp,
                    TelegramEmerald,
                    "Google Files Multi-Select UX",
                    "Removed permanent checkboxes across local files & archive. Long-press triggers haptic feedback, enters selection mode, and reveals checkboxes for multi-select.",
                    "SELECTION"
                ),
                ChangelogEntry(
                    Icons.Default.NotificationsActive,
                    Color(0xFFF59E0B),
                    "Modern White Notification Icons",
                    "Replaced legacy tinted drawables with pure white monochrome vector icons (ic_notif_*) for crisp, distortion-free status bar rendering on Android 12-15.",
                    "NOTIFICATIONS"
                ),
                ChangelogEntry(
                    Icons.Default.Animation,
                    Color(0xFF06B6D4),
                    "Silky Smooth Shared-Axis Transitions",
                    "Upgraded tab switching to Material 3 Shared Axis fade & scale animations for zero-stutter, butter-smooth navigation across screens.",
                    "ANIMATION"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF10B981),
                    "Sequential Permission Request Flow",
                    "Storage and Notification permissions are requested strictly step-by-step so system intents never collide or cancel each other out.",
                    "PERMISSIONS"
                ),
                ChangelogEntry(
                    Icons.Default.InsertDriveFile,
                    Color(0xFFEC4899),
                    "Format-Specific Fluent File Icons",
                    "PDFs, Videos, Audios, APKs, Archives, and Code now feature custom Fluent color-coded vector icons with borderless floating styling.",
                    "ICONS"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.3",
            versionCode = 106,
            releaseDate = "September 2026",
            highlights = "Komi Store Floating Capsule Nav, Seamless In-App Auto-Update Engine, Zero-Lag Direction Transitions & Symbolic UI",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Sync,
                    TelegramBlue,
                    "Seamless Auto-Update Engine",
                    "Integrated compile-time BuildConfig versioning and unified update comparisons. Guarantees immediate in-app update prompts and 1-tap installation.",
                    "UPDATER"
                ),
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramEmerald,
                    "Komi Store Floating Capsule Nav Bar",
                    "Compact liquid pill bar (62dp) with spring bouncy touch animations (1.08x scale), continuous rounded corners, and soft elevation.",
                    "DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF8B5CF6),
                    "Zero-Lag Direction-Aware Transitions",
                    "Main-thread I/O eliminated; buttery smooth left-to-right / right-to-left slide & fade screen transitions follow navigation flow.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.VisibilityOff,
                    Color(0xFF06B6D4),
                    "Smart Scroll Auto-Hide",
                    "Bottom navigation bar intelligently slides down out of view when scrolling down to maximize content space, and smoothly re-emerges on scroll up.",
                    "EXPERIENCE"
                ),
                ChangelogEntry(
                    Icons.Default.Notifications,
                    Color(0xFFF59E0B),
                    "Symbolic Minimal Notifications",
                    "Clean glyph icons and metrics: ☁️, ⚡, ⬆️, ⬇️, ✓, 💾 with instant single-tap actions ([Pause], [Drive], [Open], [Share]).",
                    "NOTIFICATIONS"
                ),
                ChangelogEntry(
                    Icons.Default.DeleteSweep,
                    Color(0xFFEF4444),
                    "Minimalist Trash Bin & Storage UX",
                    "Clean status pills, inline search, and minimal modern spacing without bulky red alert banners.",
                    "CLEAN UI"
                ),
                ChangelogEntry(
                    Icons.AutoMirrored.Filled.MenuBook,
                    Color(0xFF10B981),
                    "Separated Setup Guide & User Manual",
                    "Distinct tabs for 🚀 Quick Setup Guide (4 steps) and 📖 Features & User Manual with in-depth operational breakdowns.",
                    "GUIDES"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.2",
            versionCode = 105,
            releaseDate = "September 2026",
            highlights = "Komi Store Floating Nav Bar, Zero-Lag Direction-Aware Transitions, Smart Scroll Auto-Hide, Symbolic Notifications & Minimal Clean UI",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramBlue,
                    "Komi Store Floating Capsule Nav Bar",
                    "Inspired by Komi Store: compact liquid pill bar (62dp) with spring bouncy touch animations, continuous rounded corners, and soft elevation.",
                    "DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    TelegramEmerald,
                    "Zero-Lag Direction-Aware Tab Transitions",
                    "Eliminated main-thread disk I/O, regex recompilations, and 4-pass calculation loops. Added buttery horizontal slide transitions that follow navigation direction.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.VisibilityOff,
                    Color(0xFF8B5CF6),
                    "Smart Scroll Auto-Hide",
                    "Bottom navigation bar intelligently slides down out of view when scrolling down to maximize content space, and smoothly glides back up on scroll up.",
                    "EXPERIENCE"
                ),
                ChangelogEntry(
                    Icons.Default.Notifications,
                    Color(0xFFF59E0B),
                    "Symbolic Minimal Notifications",
                    "Removed verbose sentence text in notifications in favor of clean glyph icons and metrics: ☁️, ⚡, ⬆️, ⬇️, ✓, 💾, ⏱️ with direct single-tap actions.",
                    "NOTIFICATIONS"
                ),
                ChangelogEntry(
                    Icons.Default.DeleteSweep,
                    Color(0xFFEF4444),
                    "Minimalist Trash Bin & Local Storage",
                    "Redesigned Trash Bin and Local Storage with compact status pills, inline search, and minimal modern spacing that eliminates visual clutter.",
                    "CLEAN UI"
                ),
                ChangelogEntry(
                    Icons.AutoMirrored.Filled.MenuBook,
                    Color(0xFF06B6D4),
                    "Separated Setup Guide & User Manual",
                    "Split guides into 2 dedicated tabs: 🚀 Quick Setup Guide (4 steps) and 📖 Features & User Manual with in-depth operational breakdowns.",
                    "GUIDES"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.1",
            versionCode = 104,
            releaseDate = "September 2026",
            highlights = "Modern M3 Navigation by Default, Zero-Lag Tab Switching, Chronological Activity Log, M3 Dialog Polish & Modern Notifications",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramBlue,
                    "Modern M3 Bottom Nav by Default",
                    "Defaulted navigation bar to modern Google M3 4-tab layout (Clean, Browse, Drive, Settings) with fixed position stability and zero jumping.",
                    "NAVBAR"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    TelegramEmerald,
                    "Instant Zero-Lag Tab Switching",
                    "Eliminated 3-4 second lag and freezes when switching tabs by removing heavy synchronous cloud indexing on tab change.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.History,
                    Color(0xFF8B5CF6),
                    "Newest First Activity Logs",
                    "Activity feed now chronologically sorts uploads, deletions, and system events with real numeric timestamps so latest actions appear at the top.",
                    "ACTIVITY"
                ),
                ChangelogEntry(
                    Icons.Default.Layers,
                    Color(0xFFF59E0B),
                    "Google M3 Minimal Dialogs",
                    "Overhauled app popups with 28dp rounded corners, clutter-free content, past updates viewer, and eliminated awkward button text wrapping.",
                    "UI/UX"
                ),
                ChangelogEntry(
                    Icons.Default.Notifications,
                    TelegramBlue,
                    "Modern Status Notifications",
                    "Refreshed notification styling with Telegram Blue tint, BigTextStyle expansions, subtext status badges, and clean single-line action buttons.",
                    "NOTIFICATIONS"
                )
            )
        ),
        VersionRelease(
            versionName = "2.2.00",
            versionCode = 103,
            releaseDate = "September 2026",
            highlights = "Safe Folder Vault Isolation, Customizable Bottom Navigation Bar, Material 3 Minimal FAB & 85%+ Content Viewport",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Lock,
                    TelegramBlue,
                    "Safe Folder Vault & Starred Separation",
                    "Completely separated Safe Folder encrypted vault files from Starred favorites with biometric/PIN access, one-tap instant lock, and auto-lock on exit.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramEmerald,
                    "Customizable Material 3 Bottom Nav",
                    "Full settings subpage with live interactive preview to customize visible tabs, label visibility (Always, Selected, Never), density height (80dp, 64dp, 54dp), pill indicators, and haptic feedback.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Add,
                    TelegramBlue,
                    "Minimal Material 3 Circular FAB",
                    "Replaced screen-hogging multi-pill speed dials with a sleek circular + FAB that opens a clean Material 3 modal bottom sheet for creating folders and uploading content.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.AspectRatio,
                    Color(0xFF8B5CF6),
                    "85%+ Usable Content Viewport",
                    "Eliminated duplicate top app bars on drilldown, added collapsible 42dp storage stats pill saving ~140dp, and streamlined file headers into a compact 48dp single row.",
                    "IMPROVEMENT"
                ),
                ChangelogEntry(
                    Icons.Default.GridView,
                    Color(0xFFF59E0B),
                    "Clean Google Files Grid Previews",
                    "Removed floating 3-dots badges covering photo/video thumbnails, replacing them with long-press action menu and unobtrusive metadata footer actions.",
                    "UI"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.99",
            versionCode = 102,
            releaseDate = "September 2026",
            highlights = "14 Premium M3 Themes, Instant Zero-Lag Browse Loading & Crash Resilience Engine",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Palette,
                    TelegramBlue,
                    "14 Premium Material 3 Themes",
                    "Added 4 new luxury palettes (Nordic Glacier, Sakura Blossom, Matrix Terminal, Mocha Espresso) and enhanced existing Emerald, Cyberpunk, Sunset, Crimson, Arctic, and Lavender themes with rich M3 surface elevation tokens.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Folder,
                    TelegramEmerald,
                    "Instant Zero-Lag Browse Loading",
                    "Resolved browse unresponsive loading by un-nesting the main layout hierarchy and streaming Room DB flows with 0ms first-frame response.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFFF59E0B),
                    "Full App Crash Prevention",
                    "Guarded media viewers, dynamic file lists, and gestures with strict boundary checks to eliminate out-of-bounds and asynchronous mutation crashes.",
                    "STABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.98",
            versionCode = 101,
            releaseDate = "September 2026",
            highlights = "100% System-Wide Google Material 3 UI/UX: Full M3 Typography, Surface Container Elevation Tokens, Floating Pill Search Bar, Real Thumbnail Strips & Rounded Components",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Palette,
                    TelegramBlue,
                    "System-Wide Material 3 Theming",
                    "Complete adoption of Google Material 3 surface container elevation tokens (surfaceContainerLowest to Highest), refined M3 typography scale, and authentic tonal color schemes.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Search,
                    TelegramEmerald,
                    "Google Files Floating Search Bar",
                    "Authentic floating M3 SearchBar pill on Clean and Browse tabs with drawer access, real-time cloud sync status pill, and avatar integration.",
                    "DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.CleaningServices,
                    Color(0xFFF59E0B),
                    "Material 3 Clean Cards & Visual Previews",
                    "Storage gauge card with 24dp corners, thumbnail preview strips for Junk, Backed Up Media, Duplicates, and Large Files, with M3 CircleShape pill action buttons.",
                    "FEATURE"
                ),
                ChangelogEntry(
                    Icons.Default.Navigation,
                    TelegramBlue,
                    "Refined Navigation & Sheets",
                    "M3 bottom NavigationBar with secondaryContainer pill active indicators, 28dp rounded ModalBottomSheets, and unified drawer styling across all themes.",
                    "UX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.97",
            versionCode = 100,
            releaseDate = "September 2026",
            highlights = "100% Google Files UI/UX Transformation: Clean & Browse Hubs, Smart Category Cards, Recent Carousel & Storage Devices",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.CleaningServices,
                    TelegramBlue,
                    "Google Files Clean Screen",
                    "Redesigned Clean screen with real StatFs device storage breakdown, junk cleaner (1-tap cache prune), backed up media deletion, duplicate cleaner, and trash quick access.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Folder,
                    TelegramEmerald,
                    "Google Files Browse Hub",
                    "New Browse screen featuring 'Search in Files' pill bar, Recent horizontal carousel, 2-column pastel category cards, Starred & Safe folder Collections, and real-time Storage devices.",
                    "DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.Star,
                    Color(0xFFF59E0B),
                    "Starred Files Collection",
                    "Star your favorite cloud files via the 3-dots action menu and access them instantly from the Collections section.",
                    "FEATURE"
                ),
                ChangelogEntry(
                    Icons.Default.Lock,
                    TelegramBlue,
                    "Safe Folder Vault",
                    "Biometric and PIN-protected private vault in Collections for end-to-end encrypted personal files.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Navigation,
                    Color(0xFF8B5CF6),
                    "Clean & Browse Navigation",
                    "Standardized bottom navigation tabs and top app bar headers matching Google Files clean layout.",
                    "UX"
                )
            )
        ),
        VersionRelease(
            versionName = "2.1.96",
            versionCode = 99,
            releaseDate = "September 2026",
            highlights = "All-New SpotiFLAC-Inspired Settings Design with Bold Aesthetic, Grouped Cards & Quick Search",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Palette,
                    TelegramBlue,
                    "SpotiFLAC Bold Settings UI",
                    "Complete redesign of the Settings screen inspired by SpotiFLAC. Features high-contrast bold typography, clean uppercase headers, and unified card clusters.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.Layers,
                    Color(0xFF8B5CF6),
                    "Unified Settings Groups",
                    "Settings are cleanly grouped inside elevated, 20dp rounded surface cards with subtle hairline dividers, squircle icon boxes, and value status badges.",
                    "DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.Search,
                    Color(0xFF6366F1),
                    "Instant Pill Search",
                    "New pill/capsule search bar at the top of Settings allows instantaneous filtering across cloud sync, bot fleet, security, rules, and preferences.",
                    "SEARCH"
                ),
                ChangelogEntry(
                    Icons.Default.ArrowBack,
                    TelegramEmerald,
                    "Polished Subpage Navigation",
                    "Redesigned subpage headers with circular back buttons and descriptive subtitles across Profile, Bot Fleet, Rules, Network, and Security.",
                    "IMPROVEMENT"
                )
            )
        ),
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
