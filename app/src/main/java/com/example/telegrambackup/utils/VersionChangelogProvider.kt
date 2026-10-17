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
            versionName = "2.7.6",
            versionCode = 154,
            releaseDate = "October 2026",
            highlights = "Spatial Spring Page Navigation & Liquid Glass App Opening Reveal Animation",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF38BDF8),
                    "Direction-Aware Spatial Spring Page Transitions",
                    "Elevated main tab navigation with tactile VisionOS/Liquid Glass physics: directional slide, scale-depth recede/ascend (0.95x - 1.0x), and smooth cross-fade across all screens with zero jerk.",
                    "UI/UX"
                ),
                ChangelogEntry(
                    Icons.Default.CloudQueue,
                    Color(0xFF10B981),
                    "Kinetic Brand Reveal App Opening Animation",
                    "Cold-start illuminated glass capsule reveal with glowing ambient aura, kinetic spring shield entrance, zero-knowledge security badge, and smooth dissolve into the main dashboard.",
                    "ANIMATION"
                ),
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFFF59E0B),
                    "Animated Storage & Micro-Interactions",
                    "Fluid spring-animated segmented storage bar on dashboard entry, tactile bouncy tab switching, and elevated M3 Expressive card polish.",
                    "DESIGN"
                )
            )
        ),
        VersionRelease(
            versionName = "2.7.5",
            versionCode = 153,
            releaseDate = "October 2026",
            highlights = "100% Multi-Chunk Cloud Verification, HEVC Stride Hardening & Resumable Video Compression",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.VerifiedUser,
                    Color(0xFF10B981),
                    "Strict 100% Multi-Chunk Cloud Verification",
                    "Eliminates false-positive verification by individually verifying every chunk in multi-part files across the bot fleet. Damaged or partially deleted cloud files are purged for automatic clean re-upload.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Movie,
                    Color(0xFF38BDF8),
                    "HEVC Stride Alignment & Green Artifact Fix",
                    "Direct byte-buffer video transcoding preserves native source geometry, eliminating planar YUV stride mismatches and green screen corruption while delivering 70-80% file size reduction.",
                    "VIDEO"
                ),
                ChangelogEntry(
                    Icons.Default.Sync,
                    Color(0xFFF59E0B),
                    "Resumable Video Compression Cache",
                    "Deterministic hash-based video cache allows interrupted video uploads to resume seamlessly across worker restarts without re-encoding, auto-purging upon successful backup.",
                    "STABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.7.4",
            versionCode = 152,
            releaseDate = "October 2026",
            highlights = "Restored Ultra-Fast In-Built Browser Downloads, Lookahead CDN Prefetching & Universal Content-Length",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF38BDF8),
                    "Native 1-Click Browser Downloads",
                    "Restored ultra-fast native in-built browser download as primary action, eliminating slow client JS fetch loops and dialogs for maximum wire-speed downloads.",
                    "SPEED"
                ),
                ChangelogEntry(
                    Icons.Default.Bolt,
                    Color(0xFF10B981),
                    "Pipelined Lookahead CDN Prefetching",
                    "Pipelined chunk streaming fetches subsequent chunks in background while currently streaming to browser, eliminating dead-air latency between chunks and saturating client bandwidth.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.DownloadDone,
                    Color(0xFFF59E0B),
                    "Universal Content-Length & Range Safety",
                    "Always sends exact Content-Length headers for encrypted and plaintext downloads, enabling browser progress bars and ETAs. Range requests are safely streamed chunk-by-chunk with zero memory exhaustion.",
                    "STABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.7.3",
            versionCode = 151,
            releaseDate = "October 2026",
            highlights = "Turbo Parallel Browser Downloader, O(1) Bounded Chunk Slicing, Universal PIN Auth & Android Partial Download Guard",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Bolt,
                    Color(0xFF38BDF8),
                    "Turbo In-Browser Parallel Downloader",
                    "Streams large multi-part cloud files directly to local storage using the File System Access API or multi-worker Blob streamer with concurrent 4MB chunk fetching, live MB/s speedometer, ETA countdown, and progress HUD.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF10B981),
                    "Targeted O(1) Bounded Slicing",
                    "Eliminates sequential Telegram CDN chunk floods by calculating exact bounding chunk indices [firstIdx, lastIdx] and concurrently retrieving only required byte ranges, eliminating Vercel 504 timeouts.",
                    "SPEED"
                ),
                ChangelogEntry(
                    Icons.Default.Key,
                    Color(0xFFF59E0B),
                    "Universal Cookie-Free PIN & Session Auth",
                    "Validates authenticated PIN sessions across cookies, custom headers, and direct ?auth=<token> query parameters, eliminating lockouts in video players, Safari, and external download managers.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.DownloadDone,
                    Color(0xFF8B5CF6),
                    "Android Corrupt Chunk Reuse Guard",
                    "Validates intermediate chunk file sizes against FileChunker.CHUNK_SIZE and purges partial downloads upon cancellation or failure, ensuring 100% byte integrity during multi-chunk assembly.",
                    "INTEGRITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.7.2",
            versionCode = 150,
            releaseDate = "October 2026",
            highlights = "Bot Fleet Auto-Quarantine & Failover, Active Wi-Fi Sentinel, FTS4 OCR Deep Search Highlighting & Web Portal 4MB Range Clamping",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFFEF4444),
                    "Bot Fleet Auto-Quarantine & Failover",
                    "Automatically detects revoked bot tokens (HTTP 401/403), isolates compromised bots in Room database, notifies the user, and transparently shifts in-flight chunks to healthy bots in the fleet.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Wifi,
                    Color(0xFF38BDF8),
                    "Active Wi-Fi Sentinel & Handover Guard",
                    "Monitors Wi-Fi connectivity via NetworkCallback during backups. When Wi-Fi is dropped under Wi-Fi Only mode, gracefully suspends chunk uploads without burning cellular data, auto-resuming upon reconnect.",
                    "NETWORK"
                ),
                ChangelogEntry(
                    Icons.Default.Search,
                    Color(0xFF34D399),
                    "FTS4 OCR Deep Search & Term Highlighting",
                    "Explorer search now leverages Room FTS4 full-text index on scanned image text and document contents, with visual query keyword highlighting in AI image result cards.",
                    "SEARCH"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFFFBBF24),
                    "Web Portal 4MB Byte-Range Clamping",
                    "Clamps open-ended HTTP 206 byte-range streaming requests on the web share portal to 4MB chunks, preventing serverless memory exhaustion and 504 timeouts on massive video files.",
                    "PERFORMANCE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.7.1",
            versionCode = 149,
            releaseDate = "October 2026",
            highlights = "True Hash Deduplication Engine, Telegram Cloud Ghost Purge, Video Gestures & Audio Lockscreen Controls, Background Trash Retention Daemon & Multi-Select Batch ZIP Exporter",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Fingerprint,
                    Color(0xFF38BDF8),
                    "True Content-Hash Deduplication",
                    "Upgraded duplicate scanner with fast first/last byte-level MD5 fingerprinting and normalized filename matching, catching identical files even when renamed or copied across folders.",
                    "STORAGE"
                ),
                ChangelogEntry(
                    Icons.Default.DeleteSweep,
                    Color(0xFFEF4444),
                    "Telegram Cloud Ghost Purge",
                    "Cascades duplicate cleanup directly to Telegram channels via multi-bot batch message deletion, permanently freeing cloud storage and preventing phantom orphan chunks.",
                    "CLEANER"
                ),
                ChangelogEntry(
                    Icons.Default.Gesture,
                    Color(0xFF34D399),
                    "Modern Video Player Gestures & Audio Lockscreen",
                    "Added vertical drag gestures for brightness and volume, double-tap +/-10s seek with HUD overlays, 0.5x-2.0x playback speed selector, and background audio lockscreen notification controls.",
                    "MEDIA"
                ),
                ChangelogEntry(
                    Icons.Default.AutoDelete,
                    Color(0xFFFBBF24),
                    "Background Trash Retention Daemon",
                    "Enforces 7, 14, and 30-day trash auto-retention policies automatically during background backup worker runs without requiring manual user visits to the trash screen.",
                    "AUTOMATION"
                ),
                ChangelogEntry(
                    Icons.Default.FolderZip,
                    Color(0xFFA855F7),
                    "Multi-Select Batch ZIP Archive Exporter",
                    "Floating Bulk Action Bar now features a dedicated 'ZIP' action to bundle arbitrary multi-selected files into a clean compressed archive on the fly.",
                    "EXPORT"
                )
            )
        ),
        VersionRelease(
            versionName = "2.7.0",
            versionCode = 148,
            releaseDate = "October 2026",
            highlights = "Rolling Cloud File Verification, Live ETA & Speed Notifications, Decoy Safe Vault Auto-Populator, Selective Cloud Folder Restore & Diff Sync",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.VerifiedUser,
                    Color(0xFF38BDF8),
                    "Rolling File Verifier & Chunk Integrity",
                    "Introduced circular rolling verification cursor ensuring 100% of cloud files are audited incrementally over time, with multi-chunk completeness checks and elimination of destructive single-bot wipes.",
                    "RELIABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF34D399),
                    "Live Transfer Metrics & Real-Time ETA",
                    "Background notifications now display dynamic byte-metered transfer speeds and live remaining time estimates (ETA) alongside active queue progress.",
                    "OBSERVABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFFFBBF24),
                    "Decoy Vault Auto-Populator",
                    "Eliminates empty-vault suspicion under duress by automatically generating benign, realistic decoy documents when the decoy PIN is configured.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.FolderZip,
                    Color(0xFFA855F7),
                    "Selective Cloud Folder Restore & Diff Sync",
                    "Directly reconstructs cloud folder trees into local storage with intelligent diff synchronization that skips identical files and restores corrupted ones without ZIP wrapping.",
                    "RESTORE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.9",
            versionCode = 147,
            releaseDate = "October 2026",
            highlights = "Zero-Disk Streaming Encryption, Room FTS4 On-Device ML Kit OCR, Hardware Keystore Safe Vault & Decoy PIN, On-Device HEVC Video Compression",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.EnhancedEncryption,
                    Color(0xFF38BDF8),
                    "Zero-Disk Streaming Encryption (P0)",
                    "Directly streams encrypted 4MB AES-GCM chunks on the fly from source files into multi-part Telegram cloud uploads without creating multi-gigabyte temporary .enc files on internal storage.",
                    "CRYPTOGRAPHY"
                ),
                ChangelogEntry(
                    Icons.Default.FindInPage,
                    Color(0xFF34D399),
                    "Room FTS4 & ML Kit On-Device OCR (P1)",
                    "Offline background OCR indexes text and auto-tags documents and photos locally into a Room FTS4 virtual table, providing sub-millisecond 0ms instant photo and document search.",
                    "INTELLIGENCE"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFFFBBF24),
                    "Hardware Safe Vault & Decoy PIN (P2)",
                    "Protected with Android Hardware Keystore (TEE/StrongBox) encryption and fail-closed biometrics. Introduces duress Decoy PIN mode for plausible deniability against forced unlocks.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.VideoFile,
                    Color(0xFFA855F7),
                    "Hardware HEVC Video Compression (P3)",
                    "Optional hardware-accelerated MediaCodec H.265/HEVC compression achieves 70-80% smaller video backups on-device before uploading to your private Telegram storage.",
                    "MEDIA"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.8",
            versionCode = 146,
            releaseDate = "October 2026",
            highlights = "Multi-Bot Verification Safeguards, MediaStore ContentObserver Live Sync, Leak-Proof Storage Guard & Cloud Trash Purge",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.VerifiedUser,
                    Color(0xFF38BDF8),
                    "Multi-Bot Fleet Cloud Verification",
                    "Eliminated false-ghost deletions by verifying cloud files against the exact bot token that uploaded them, with automatic candidate fallback across all fleet bots.",
                    "ACCURACY"
                ),
                ChangelogEntry(
                    Icons.Default.CameraAlt,
                    Color(0xFF34D399),
                    "MediaStore ContentObserver Live Watcher",
                    "Added Android MediaStore image and video observers with IS_PENDING write completion checks, ensuring 100% reliable instant camera backup across Android 11 to 15.",
                    "CAMERA"
                ),
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFFFBBF24),
                    "Leak-Proof Storage & Headroom Guard",
                    "Added usableSpace disk checks before large file encryption and wrapped uploads in atomic finally blocks, preventing cache leakage and out-of-storage crashes.",
                    "STABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.DeleteForever,
                    Color(0xFFF43F5E),
                    "Cloud Trash Retention Purge",
                    "Synchronized auto-clean retention expiry with Telegram channel message deletion, preventing ghost messages from building up in backup channels.",
                    "PRIVACY"
                ),
                ChangelogEntry(
                    Icons.Default.Bolt,
                    Color(0xFFA78BFA),
                    "Debounced AI Search Tag Cache",
                    "Replaced synchronous JSON disk writes with debounced background serialization, eliminating micro-stutters during batch OCR image searches.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.SettingsSuggest,
                    Color(0xFF10B981),
                    "Custom Bot API Health Diagnostics",
                    "Resolved diagnostic checks to use TelegramApiConfig method URLs, fully supporting custom and local Bot API server endpoints.",
                    "DIAGNOSTICS"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.7",
            versionCode = 145,
            releaseDate = "October 2026",
            highlights = "120 FPS GPU Fluid Engine, Native Hardware Gaussian Blur, Crystal-Clear Text Contrast & Updater Sync",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF38BDF8),
                    "120 FPS Liquid Glass Fluid Engine",
                    "Re-engineered ambient liquid canvas using RenderNode transforms and memoized gradient shaders, eliminating per-frame allocations for buttery smooth scrolling.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.BlurOn,
                    Color(0xFF818CF8),
                    "Hardware Gaussian Blur Caustics",
                    "RenderEffect blur pools smoothly melt caustics into organic glowing liquid plasma on Android 12+ devices.",
                    "AESTHETICS"
                ),
                ChangelogEntry(
                    Icons.Default.Visibility,
                    Color(0xFF34D399),
                    "Crystal-Clear Visibility & Contrast",
                    "Frosted glass surface opacity calibrated to 68-78% with pure white typography, ensuring WCAG AAA readability.",
                    "CONTRAST"
                ),
                ChangelogEntry(
                    Icons.Default.SystemUpdate,
                    Color(0xFF38BDF8),
                    "In-App Updater Live Sync",
                    "Instant in-app update prompt delivery and one-click download verification across cloud endpoints.",
                    "UPDATER"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.6",
            versionCode = 144,
            releaseDate = "October 2026",
            highlights = "GPU-Accelerated 120 FPS Fluid Engine, Hardware Gaussian Blur, Crystal-Clear Text Contrast & Organic Liquid Caustics",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF38BDF8),
                    "120 FPS GPU-Accelerated Fluid Engine",
                    "Re-engineered ambient liquid canvas using RenderNode transforms and memoized gradient shaders, eliminating per-frame allocations for buttery smooth scrolling.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.BlurOn,
                    Color(0xFF818CF8),
                    "Hardware Gaussian Blur (Android 12+)",
                    "Applied native RenderEffect Gaussian blur on ambient fluid pools, melting caustics into a seamless, organic glowing liquid plasma.",
                    "VISUAL"
                ),
                ChangelogEntry(
                    Icons.Default.Visibility,
                    Color(0xFF2DD4BF),
                    "Crystal-Clear Contrast & Readability",
                    "Tuned frosted glass surfaces to 68-78% opacity with pure white typography, guaranteeing razor-sharp text visibility without background orb wash-out.",
                    "ACCESSIBILITY"
                ),
                ChangelogEntry(
                    Icons.Default.Palette,
                    Color(0xFFA78BFA),
                    "Crisp Metadata & Silver Bevels",
                    "Restored full opacity to file size labels, search icons, and options chevrons with cool slate-silver outlines and directional specular bevel borders.",
                    "POLISH"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.5",
            versionCode = 143,
            releaseDate = "October 2026",
            highlights = "Liquid Glass Material Design System, Translucent Frosted Glass UI, High-Speed Native Stream Downloads & Hardware Cryptography",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Palette,
                    Color(0xFF00E5FF),
                    "Liquid Glass Design System",
                    "Full-fidelity Liquid Glass theme with deep oceanic obsidian crystal, translucent frosted surfaces, dynamic specular refraction highlights, and glassmorphic bottom navigation bars.",
                    "DESIGN"
                ),
                ChangelogEntry(
                    Icons.Default.CloudDownload,
                    Color(0xFF10B981),
                    "Fast Direct Stream Downloads Default",
                    "Web share links now stream files directly via native browser download manager, bypassing sequential chunk stitching for maximum download speed.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF3B82F6),
                    "Zero-Overhead Browser Performance",
                    "Eliminated client-side memory blob buffers, chunk-by-chunk polling, and heavy progress bars to prevent mobile browser memory leaks and crashes.",
                    "OPTIMIZATION"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF8B5CF6),
                    "Strict Bot Token Sanitization",
                    "API credentials endpoints strictly mask raw Telegram bot tokens in query responses, completely preventing accidental token exposure.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Share,
                    Color(0xFFF59E0B),
                    "Autonomous Server-Side Share Creation",
                    "Share link generation resolves bot tokens and chunk credentials internally from the database without requiring client devices to transmit secrets.",
                    "PRIVACY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.4",
            versionCode = 142,
            releaseDate = "October 2026",
            highlights = "Fast Direct Stream Downloads Default, Zero-Memory Browser Streaming, Strict Bot Token Sanitization & Server-Side Share Link Creation",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.CloudDownload,
                    Color(0xFF10B981),
                    "Fast Direct Stream Downloads Default",
                    "Web share links now stream files directly via native browser download manager, bypassing sequential chunk stitching for maximum download speed.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF3B82F6),
                    "Zero-Overhead Browser Performance",
                    "Eliminated client-side memory blob buffers, chunk-by-chunk polling, and heavy progress bars to prevent mobile browser memory leaks and crashes.",
                    "OPTIMIZATION"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF8B5CF6),
                    "Strict Bot Token Sanitization",
                    "API credentials endpoints strictly mask raw Telegram bot tokens in query responses, completely preventing accidental token exposure.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Share,
                    Color(0xFFF59E0B),
                    "Autonomous Server-Side Share Creation",
                    "Share link generation resolves bot tokens and chunk credentials internally from the database without requiring client devices to transmit secrets.",
                    "PRIVACY"
                ),
                ChangelogEntry(
                    Icons.Default.Palette,
                    Color(0xFF00E5FF),
                    "Liquid Glass Material Theme",
                    "Full-fidelity Liquid Glass theme with deep oceanic obsidian crystal, translucent frosted surfaces, and specular refraction edge highlights.",
                    "DESIGN"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.3",
            versionCode = 141,
            releaseDate = "September 2026",
            highlights = "Keystore Hardware Cryptography, AES-GCM Direct Stream Decryption, Resilient Serverless Endpoints & Opaque Sharing",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF10B981),
                    "Hardware-Backed Keystore Vault",
                    "Master security key, zero-knowledge passphrases, bot tokens, and auth secrets are secured in hardware-backed Android KeyStore.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFF3B82F6),
                    "Authenticated AES-256-GCM Slicing",
                    "Full GCM256 authenticated encryption with byte-exact CTR decryption for seamless, zero-corruption media streaming.",
                    "CRYPTO"
                ),
                ChangelogEntry(
                    Icons.Default.CloudSync,
                    Color(0xFF8B5CF6),
                    "Zero-Downtime Serverless Engine",
                    "Resilient fallbacks for environment variables, instant pre-signed CDN download redirection, and robust Neon DB connection pooling.",
                    "BACKEND"
                ),
                ChangelogEntry(
                    Icons.Default.Share,
                    Color(0xFFF59E0B),
                    "HttpOnly Share Authentication",
                    "Share link web portal protects file streaming with HttpOnly session cookies, removing raw tokens and credentials from query URLs.",
                    "PRIVACY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.1",
            versionCode = 139,
            releaseDate = "September 2026",
            highlights = "Full Security & Integrity Overhaul, Dynamic Keystore AES Keys, Credential Sanitization & Concurrency Guard",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF10B981),
                    "Zero-Exposure Keystore Encryption",
                    "Eliminated static key fallbacks with dynamic 256-bit AES master keys generated securely in Android Keystore.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Lock,
                    Color(0xFF3B82F6),
                    "Strict JWT Bearer Authentication",
                    "Eliminated local plaintext password retention in SharedPreferences; authentication relies entirely on secure signed JWT tokens.",
                    "AUTH"
                ),
                ChangelogEntry(
                    Icons.Default.Share,
                    Color(0xFF8B5CF6),
                    "Opaque Server Share Shielding",
                    "Deprecated client credential embedding in fallback share links, strictly serving opaque server-side encrypted tokens.",
                    "PRIVACY"
                ),
                ChangelogEntry(
                    Icons.Default.AltRoute,
                    Color(0xFFF59E0B),
                    "Distinct Bot Fleet Concurrency",
                    "Removed modulo bot duplication in parallel chunk transfer pools and added non-negative connection bounds to prevent Telegram flood-wait limits.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.CallSplit,
                    Color(0xFF06B6D4),
                    "Strict Byte Chunk Splitting",
                    "Fixed exact-size chunk boundary splitting and reinforced chunk assembly regex to guarantee zero payload truncation.",
                    "INTEGRITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.6.0",
            versionCode = 138,
            releaseDate = "September 2026",
            highlights = "Direct High-Speed Architecture, Fluid Compute, Bounded Channels, Bot Fleet Load Balancing & Resumable Manifests",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Speed,
                    Color(0xFF3B82F6),
                    "Direct High-Speed Architecture",
                    "Android app transfers file chunks directly to and from Telegram Bot API and CDN, eliminating serverless proxy latency and keeping raw file bytes off Vercel.",
                    "ARCHITECTURE"
                ),
                ChangelogEntry(
                    Icons.Default.Bolt,
                    Color(0xFF10B981),
                    "Vercel Fluid Compute & Decoupled DDL",
                    "Enabled Vercel Fluid Compute with 300s function duration and decoupled database DDL migrations into a standalone deployment engine, reducing cold-start latency to near-instant.",
                    "BACKEND"
                ),
                ChangelogEntry(
                    Icons.Default.SyncAlt,
                    Color(0xFF8B5CF6),
                    "Bounded Channel Concurrency & Backpressure",
                    "Replaced unbounded coroutine mapping with bounded 4–12 worker Channel pools for uploads and downloads, eliminating RAM spikes, coroutine exhaustion, and disk contention.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.SmartToy,
                    Color(0xFFEC4899),
                    "Dynamic Least-Connections Bot Load Balancer",
                    "Fleet manager dynamically load-balances chunk transfers to bots with the fewest active requests and isolates HTTP 429 rate-limited bots into automatic cooldown.",
                    "LOAD BALANCER"
                ),
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFFF59E0B),
                    "Idempotent file_chunks & Resume Manifests",
                    "Added dedicated file_chunks table with ON CONFLICT DO UPDATE upserts, fast manifest verification endpoints, and composite cursor-based delta pagination.",
                    "DATABASE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.5.3",
            versionCode = 137,
            releaseDate = "September 2026",
            highlights = "Mandatory JWT Security, Hardened Secrets & OTP, 32MB Chunk Streaming Cache & Database Resilience",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF8B5CF6),
                    "Mandatory JWT Authentication",
                    "All private endpoints (credentials, cloud file indexing, deletion, purge, account deletion, user settings) now enforce cryptographically signed Bearer JWT authentication, preventing unauthorized access.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.VpnKey,
                    Color(0xFF3B82F6),
                    "Hardened Master Secrets & Atomic OTP",
                    "Upgraded master secrets to 256-bit cryptographically verified keys with single-use atomic consumption and brute-force rate limits.",
                    "SECURITY"
                ),
                ChangelogEntry(
                    Icons.Default.Memory,
                    Color(0xFF10B981),
                    "32MB Byte-Metered Streaming Cache",
                    "Backend chunk buffer cache is now strictly byte-metered to 32MB with LRU eviction, eliminating serverless RAM exhaustion and OOM crashes during heavy multi-part downloads.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFFF59E0B),
                    "Cloud Index & Database Resilience",
                    "Protected cloud file sync with HTTP 503 retryable status on database lapses to prevent accidental empty-vault wipes, and added compound cursor delta sync.",
                    "RELIABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.5.2",
            versionCode = 136,
            releaseDate = "September 2026",
            highlights = "Zero-OOM Video Thumbnail Engine, Large Heap Allocation & Media OutOfMemoryError Absorption",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Bolt,
                    Color(0xFF10B981),
                    "Zero-OOM Hardware-Scaled Video Thumbnails",
                    "Resolved OutOfMemoryError crash on app open by utilizing API 27+ MediaMetadataRetriever.getScaledFrameAtTime hardware downsampling and bounded 2-way semaphore throttling.",
                    "HOTFIX"
                ),
                ChangelogEntry(
                    Icons.Default.Memory,
                    Color(0xFF3B82F6),
                    "Large Heap Memory Expansion",
                    "Enabled android:largeHeap='true' in AndroidManifest to grant up to 512MB RAM for intensive media viewing, multi-GB cloud backups, and high-resolution photo galleries.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFF8B5CF6),
                    "Global OutOfMemoryError Shield Absorption",
                    "Hardened AppCrashShield to intercept unhandled bitmap OOM events, trigger immediate Garbage Collection, and prevent process death.",
                    "STABILITY"
                )
            )
        ),
        VersionRelease(
            versionName = "2.5.1",
            versionCode = 135,
            releaseDate = "September 2026",
            highlights = "Instant Cold-Boot Launch Resolution, Launcher Alias Sync, Database Migration Hardening & Screen Privacy Deferral",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Bolt,
                    Color(0xFF10B981),
                    "Instant Cold-Boot Launch Resolution",
                    "Overhauled AppCrashShield with looper-level message pumping and crash-loop guards, eliminating the startup black screen caused by infinite restart loops and dead main loopers.",
                    "HOTFIX"
                ),
                ChangelogEntry(
                    Icons.Default.Apps,
                    Color(0xFF3B82F6),
                    "Launcher Alias Synchronization",
                    "Added automatic launcher activity-alias synchronization during boot and app updates, ensuring the launcher icon stays active regardless of which theme icon was chosen.",
                    "LAUNCHER"
                ),
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFFF59E0B),
                    "Fail-Safe Room DB Auto-Healing",
                    "Added runtime PRAGMA column checks for chunk progress and cloud metadata with destructive fallback safety, preventing startup crashes from legacy schema mismatches.",
                    "DATABASE"
                ),
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF8B5CF6),
                    "Clean Display Frame Compositing",
                    "Deferred window security flags until after Compose window initialization, preventing display surface drops and blank black frames during cold startup.",
                    "PERFORMANCE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.5.0",
            versionCode = 134,
            releaseDate = "September 2026",
            highlights = "Zero-Crash App Crash Shield, Jetpack Compose Media Viewer Hardening, Literal Path Renaming & Room DB Migration Resilience",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Shield,
                    Color(0xFF10B981),
                    "Zero-Crash App Crash Shield",
                    "Integrated global application-level crash shield that intercepts uncaught coroutine and thread errors, suppresses benign OEM window manager crashes, and safely recovers the user session.",
                    "STABILITY"
                ),
                ChangelogEntry(
                    Icons.Default.Visibility,
                    Color(0xFF3B82F6),
                    "Jetpack Compose Media Viewer & OOM Hardening",
                    "Resolved slot table memoization glitches in media dialogs, added 256KB safe text reader limits, and removed unsafe manual bitmap recycling to guarantee 0 Canvas engine crashes.",
                    "MEDIA VIEWER"
                ),
                ChangelogEntry(
                    Icons.Default.DriveFileRenameOutline,
                    Color(0xFF8B5CF6),
                    "Literal Substring Folder Renaming",
                    "Replaced regex parsing with literal prefix substitution, eliminating PatternSyntaxException and corrupted file paths when renaming folders with special characters.",
                    "FILE ACTIONS"
                ),
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFFF59E0B),
                    "Room DB Multi-Version Migration Resilience",
                    "Added explicit database migrations from older schemas (v2, v3, v4, v5 to v12) with safe fallback to guarantee zero startup crashes for updating users.",
                    "DATABASE"
                ),
                ChangelogEntry(
                    Icons.Default.FolderZip,
                    Color(0xFF06B6D4),
                    "Scoped Storage Safe ZIP Downloader",
                    "Hardened both Monthly and Folder ZIP archivers with guaranteed stream closure in finally blocks and app-private Scoped Storage fallbacks.",
                    "ARCHIVE ENGINE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.9",
            versionCode = 133,
            releaseDate = "September 2026",
            highlights = "Zero-Loss Settings & API Keys Persistence Across Uninstall, Adaptive Launcher Icon Fitting & Modern TSpace Shield Status Bar",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Security,
                    Color(0xFF10B981),
                    "Zero-Loss Config & API Key Persistence",
                    "Added persistent external storage vault (.tspace_vault_config.json) that automatically backs up and seamlessly restores all app settings, Google Gemini API keys, Telegram Bot Fleet, Storage Channels, and Master AES-256 encryption keys across app uninstalls and reboots.",
                    "NEW"
                ),
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF8B5CF6),
                    "Google Gemini API Keys Auto-Recovery",
                    "Configured complete multi-layer cloud profile and local vault synchronization for Gemini API keys, AI model selection, and document scanner preferences.",
                    "AI PERSISTENCE"
                ),
                ChangelogEntry(
                    Icons.Default.NotificationsActive,
                    TelegramBlue,
                    "TSpace Shield Status Bar & Notification Badge",
                    "Redesigned notification status bar icons with high-contrast monochrome TSpace Vault Shield vectors with upload arrows, checkmarks, download glyphs, and pause bars. Integrated full-fidelity colored app shield icon in notification drawer.",
                    "UI & STATUS BAR"
                ),
                ChangelogEntry(
                    Icons.Default.CropFree,
                    Color(0xFFF59E0B),
                    "Adaptive Launcher Icon Scale & Mask Fitting",
                    "Downscaled launcher inner shield by 0.72x across all screen densities (MDPI to XXXHDPI), preventing round/squircle launcher mask clipping and ensuring full visibility on all Android app drawers.",
                    "ICON FIX"
                ),
                ChangelogEntry(
                    Icons.Default.CloudSync,
                    TelegramEmerald,
                    "Android 12+ Full Auto-Backup & Device Transfer",
                    "Enabled comprehensive Android Auto-Backup rules for SharedPreferences and Room SQLite databases, ensuring automatic Google Drive backup and instant device transfer.",
                    "BACKUP ENGINE"
                )
            )
        ),
        VersionRelease(
            versionName = "2.4.8",
            versionCode = 132,
            releaseDate = "September 2026",
            highlights = "Local Storage Crash Guard, 0ms Instant Browse Flow, Google Photos-Grade Visual OCR & Multimodal Search",
            entries = listOf(
                ChangelogEntry(
                    Icons.Default.Storage,
                    Color(0xFF10B981),
                    "Local Storage Path Protection & Async Loading",
                    "Eliminated crash when entering Local Storage via safe path canonicalization and asynchronous directory discovery on Android 11+.",
                    "FIX"
                ),
                ChangelogEntry(
                    Icons.Default.Speed,
                    TelegramBlue,
                    "Instant Browse Navigation (0ms Lag)",
                    "Added in-memory volatile caching for Starred and Safe Vault collections, eliminating continuous disk I/O and XML deserialization stutter.",
                    "PERFORMANCE"
                ),
                ChangelogEntry(
                    Icons.Default.Badge,
                    Color(0xFF8B5CF6),
                    "Advanced ID & Document Recognition",
                    "High-precision pattern matcher for college/student IDs, office badges, Aadhaar (UIDAI), Voter IDs, and Driving Licenses with signature and field validation.",
                    "AI SEARCH"
                ),
                ChangelogEntry(
                    Icons.Default.MedicalServices,
                    Color(0xFFEC4899),
                    "Medical Prescriptions & Doctor Slips",
                    "Instant OCR detection for medical prescriptions (Rx/℞), clinic consultation notes, hospital OPD/IPD slips, and medicine dosage markers.",
                    "OCR ENGINE"
                ),
                ChangelogEntry(
                    Icons.Default.LocationCity,
                    Color(0xFFF59E0B),
                    "Colleges, Societies & Buildings AI Recognition",
                    "Deep recognition for universities, campuses, residential societies, apartment complexes, and towers via OCR signboards and Gemini 2.0 Vision.",
                    "VISION"
                ),
                ChangelogEntry(
                    Icons.Default.AutoAwesome,
                    Color(0xFF06B6D4),
                    "Google Photos-Grade 640px Vision Reasoning",
                    "Upgraded multimodal candidate resolution to 640px @ 80% JPEG with 48-candidate pool and persistent local AI tag caching.",
                    "AI SEARCH"
                )
            )
        ),
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
