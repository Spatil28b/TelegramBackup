package com.example.telegrambackup.network

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.TelegramBotEntity
import com.example.telegrambackup.data.TelegramChannelEntity
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * =====================================================================================
 * BOT FLEET MANAGER & HIGH-SPEED LOAD BALANCER
 * =====================================================================================
 * Distributes concurrent file and chunk uploads across a pool of Telegram Bots and Channels.
 * Provides instant HTTP 429 Flood-Wait failover and category-specific channel routing.
 * =====================================================================================
 */
object BotFleetManager {
    private const val TAG = "BotFleetManager"

    private val roundRobinIndex = AtomicInteger(0)
    private val activeRequestsMap = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    private val initMutex = Mutex()
    private var isInitialized = false

    fun acquireBot(botToken: String) {
        val clean = botToken.trim()
        if (clean.isNotEmpty()) {
            activeRequestsMap.computeIfAbsent(clean) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    fun releaseBot(botToken: String) {
        val clean = botToken.trim()
        if (clean.isNotEmpty()) {
            activeRequestsMap[clean]?.decrementAndGet()
        }
    }

    fun getActiveRequests(botToken: String): Int {
        val clean = botToken.trim()
        return activeRequestsMap[clean]?.get()?.coerceAtLeast(0) ?: 0
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Initializes the bot fleet and channels table from AppPrefs if empty (Zero-Downtime Migration).
     */
    suspend fun ensureInitialized(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (isInitialized && !force) return@withLock
            try {
                val db = AppDatabase.getDatabase(context)
                val botDao = db.telegramBotDao()
                val channelDao = db.telegramChannelDao()
                val prefs = AppPrefs(context)

                val existingBots = botDao.getAllBots()
                if (existingBots.isEmpty() && prefs.botToken.isNotBlank()) {
                    val primaryBot = TelegramBotEntity(
                        botToken = prefs.botToken.trim(),
                        displayName = "Primary Bot",
                        isPrimary = true,
                        isActive = true,
                        addedAt = System.currentTimeMillis()
                    )
                    botDao.insert(primaryBot)
                    Log.d(TAG, "Migrated existing botToken to TelegramBotEntity")
                }

                val existingChannels = channelDao.getAllChannels()
                if (existingChannels.isEmpty() && prefs.chatId.isNotBlank()) {
                    val primaryChannel = TelegramChannelEntity(
                        chatId = prefs.chatId.trim(),
                        title = "Primary Backup Storage",
                        categoryTarget = "ALL",
                        isPrimary = true,
                        isChannelAdminVerified = prefs.credentialsVerified,
                        canDeleteMessages = true,
                        addedAt = System.currentTimeMillis()
                    )
                    channelDao.insert(primaryChannel)
                    Log.d(TAG, "Migrated existing chatId to TelegramChannelEntity")
                }
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing BotFleetManager", e)
            }
        }
    }

    /**
     * Returns the next active, healthy bot from the pool using least-connections load-balancing.
     * Automatically skips bots in a rate-limit cooldown (HTTP 429).
     */
    suspend fun getNextAvailableBot(context: Context): TelegramBotEntity? = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        try {
            val db = AppDatabase.getDatabase(context)
            val botDao = db.telegramBotDao()
            val activeBots = botDao.getActiveBots()

            if (activeBots.isEmpty()) {
                val prefs = AppPrefs(context)
                return@withContext if (prefs.botToken.isNotBlank()) {
                    TelegramBotEntity(botToken = prefs.botToken.trim(), isPrimary = true)
                } else null
            }

            val now = System.currentTimeMillis()
            val healthyBots = activeBots.filter { it.rateLimitUntil <= now }

            val candidatePool = if (healthyBots.isNotEmpty()) healthyBots else activeBots
            // Least-connections load balancer: prefer bot with fewest active requests, with round-robin tie breaker
            val bestBot = candidatePool.minByOrNull { getActiveRequests(it.botToken) }
                ?: candidatePool[(roundRobinIndex.getAndIncrement() and 0x7FFFFFFF) % candidatePool.size]
            return@withContext bestBot
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving available bot", e)
            val prefs = AppPrefs(context)
            return@withContext if (prefs.botToken.isNotBlank()) TelegramBotEntity(botToken = prefs.botToken.trim(), isPrimary = true) else null
        }
    }

    /**
     * Returns a rotating list of distinct active bots to upload chunks in parallel.
     * Sorted by least-active connections to ensure balanced work distribution.
     */
    suspend fun getBotPoolForParallelChunks(context: Context, requiredCount: Int): List<TelegramBotEntity> = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val db = AppDatabase.getDatabase(context)
        val activeBots = db.telegramBotDao().getActiveBots()
        if (activeBots.isEmpty()) {
            val single = getNextAvailableBot(context)
            return@withContext if (single != null) listOf(single) else emptyList()
        }

        val now = System.currentTimeMillis()
        val healthyBots = activeBots.filter { it.rateLimitUntil <= now }
        val pool = if (healthyBots.isNotEmpty()) healthyBots else activeBots
        val sortedPool = pool.sortedBy { getActiveRequests(it.botToken) }

        val result = mutableListOf<TelegramBotEntity>()
        val count = maxOf(requiredCount, sortedPool.size)
        for (i in 0 until count) {
            result.add(sortedPool[i % sortedPool.size])
        }
        return@withContext result
    }

    /**
     * Allocates bots for Cloud Drive uploads based on the selected fleet strategy:
     * - BOOST Mode: If Drive is boosted or ALL, uses 100% of all bots in the fleet.
     * - DEDICATED Mode: Uses fixed dedicated slice of bots (driveMinBots).
     * - BALANCED Mode: Guarantees at least driveMinBots with dynamic sharing.
     * - MAX Mode: Uses all available bots.
     */
    suspend fun getDriveBotPool(context: Context, requiredCount: Int = 4): List<TelegramBotEntity> = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val prefs = AppPrefs(context)
        val db = AppDatabase.getDatabase(context)
        val activeBots = db.telegramBotDao().getActiveBots()
        if (activeBots.isEmpty()) {
            val single = getNextAvailableBot(context)
            return@withContext if (single != null) listOf(single) else emptyList()
        }

        val now = System.currentTimeMillis()
        val healthyBots = activeBots.filter { it.rateLimitUntil <= now }
        val pool = if (healthyBots.isNotEmpty()) healthyBots else activeBots

        val mode = prefs.fleetMode
        val boostTarget = prefs.boostTarget
        val minDrive = prefs.driveMinBots.coerceIn(1, pool.size)

        val driveBots: List<TelegramBotEntity> = when (mode) {
            "BOOST" -> {
                if (boostTarget == "DRIVE" || boostTarget == "ALL") {
                    pool // Turbo boost: ALL bots focus on Drive!
                } else {
                    pool.takeLast(minOf(pool.size, 1)) // 1 fallback bot if auto-backup is boosted
                }
            }
            "DEDICATED" -> {
                pool.takeLast(minDrive) // strictly dedicated slice
            }
            "BALANCED" -> {
                pool.takeLast(minOf(pool.size, maxOf(minDrive, requiredCount)))
            }
            else -> { // "MAX"
                pool
            }
        }

        val targetCount = maxOf(requiredCount, driveBots.size)
        val result = mutableListOf<TelegramBotEntity>()
        for (i in 0 until targetCount) {
            result.add(driveBots[i % driveBots.size])
        }
        return@withContext result
    }

    /**
     * Allocates bots for Auto-Backup based on the selected fleet strategy:
     * - BOOST Mode: If Auto-Backup is boosted or ALL, uses 100% of all bots in the fleet.
     * - DEDICATED Mode: Uses dedicated slice of bots (autoBackupMinBots).
     * - BALANCED Mode: Guarantees at least autoBackupMinBots with size-tiered scaling.
     * - MAX Mode: Uses all available bots.
     */
    suspend fun getAutoBackupBotPool(context: Context, fileSize: Long, requiredChunks: Int): List<TelegramBotEntity> = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        val prefs = AppPrefs(context)
        val db = AppDatabase.getDatabase(context)
        val activeBots = db.telegramBotDao().getActiveBots()
        if (activeBots.isEmpty()) {
            val single = getNextAvailableBot(context)
            return@withContext if (single != null) listOf(single) else emptyList()
        }

        val now = System.currentTimeMillis()
        val healthyBots = activeBots.filter { it.rateLimitUntil <= now }
        val pool = if (healthyBots.isNotEmpty()) healthyBots else activeBots

        val mode = prefs.fleetMode
        val boostTarget = prefs.boostTarget
        val minAuto = prefs.autoBackupMinBots.coerceIn(1, pool.size)

        val backupBots: List<TelegramBotEntity> = when (mode) {
            "BOOST" -> {
                if (boostTarget == "AUTO_BACKUP" || boostTarget == "ALL") {
                    pool // Turbo boost: ALL bots focus on Auto-Backup!
                } else {
                    pool.take(minOf(pool.size, 1)) // 1 fallback bot if drive is boosted
                }
            }
            "DEDICATED" -> {
                pool.take(minAuto) // strictly dedicated slice
            }
            "BALANCED" -> {
                pool // Fully leverage all healthy bots in fleet for auto-backup
            }
            else -> { // "MAX"
                pool
            }
        }

        val targetCount = maxOf(requiredChunks, backupBots.size)
        val result = mutableListOf<TelegramBotEntity>()
        for (i in 0 until targetCount) {
            result.add(backupBots[i % backupBots.size])
        }
        return@withContext result
    }

    /**
     * Places a bot in a temporary flood-wait penalty box.
     */
    suspend fun markBotRateLimited(context: Context, botToken: String, retryAfterSeconds: Int) = withContext(Dispatchers.IO) {
        try {
            val penaltyMs = (retryAfterSeconds.coerceIn(5, 300) * 1000L)
            val until = System.currentTimeMillis() + penaltyMs
            AppDatabase.getDatabase(context).telegramBotDao().markRateLimited(botToken, until)
            Log.w(TAG, "Bot placed in cooldown for ${retryAfterSeconds}s until $until")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking bot rate limited", e)
        }
    }

    suspend fun recordSuccess(context: Context, botToken: String, bytes: Long) = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getDatabase(context).telegramBotDao().recordSuccess(botToken, bytes)
        } catch (_: Exception) {}
    }

    suspend fun recordFailure(context: Context, botToken: String) = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getDatabase(context).telegramBotDao().recordFailure(botToken)
        } catch (_: Exception) {}
    }

    /**
     * Resolves the target channel for a given file category (PHOTOS, VIDEOS, DOCUMENTS, DRIVE, ALL).
     */
    suspend fun resolveDestinationChannel(context: Context, categoryTarget: String): TelegramChannelEntity = withContext(Dispatchers.IO) {
        ensureInitialized(context)
        try {
            val db = AppDatabase.getDatabase(context)
            val channelDao = db.telegramChannelDao()

            if (categoryTarget != "ALL") {
                val catChannel = channelDao.getChannelForCategory(categoryTarget)
                if (catChannel != null && catChannel.chatId.isNotBlank()) {
                    return@withContext catChannel
                }
            }

            val primary = channelDao.getPrimaryChannel()
            if (primary != null && primary.chatId.isNotBlank()) {
                return@withContext primary
            }

            val anyChannel = channelDao.getAllChannels().firstOrNull()
            if (anyChannel != null && anyChannel.chatId.isNotBlank()) {
                return@withContext anyChannel
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving destination channel", e)
        }

        val prefs = AppPrefs(context)
        return@withContext TelegramChannelEntity(
            chatId = prefs.chatId.trim(),
            title = "Default Cloud Storage",
            categoryTarget = "ALL",
            isPrimary = true
        )
    }

    /**
     * Tests a Telegram Bot Token via getMe.
     * Returns Pair(success, botUsername or error message)
     */
    suspend fun testBotToken(botToken: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val clean = botToken.trim()
            if (clean.isBlank()) return@withContext Pair(false, "Token cannot be empty")
            val url = "https://api.telegram.org/bot$clean/getMe"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                if (json.optBoolean("ok")) {
                    val res = json.getJSONObject("result")
                    val username = res.optString("username", "")
                    val firstName = res.optString("first_name", "Bot")
                    return@withContext Pair(true, "@$username ($firstName)")
                }
            }
            return@withContext Pair(false, "Invalid Bot Token")
        } catch (e: Exception) {
            return@withContext Pair(false, e.localizedMessage ?: "Connection error")
        }
    }
}
