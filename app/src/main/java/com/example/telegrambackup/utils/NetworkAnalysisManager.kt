package com.example.telegrambackup.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import androidx.work.*
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.NetworkAnalysisEntity
import com.example.telegrambackup.worker.NetworkAnalysisWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NetworkAnalysisManager {
    private const val TAG = "NetworkAnalysis"
    private const val WORK_NAME = "NetworkAnalysisPeriodicWork"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun runDiagnosticTest(context: Context): NetworkAnalysisEntity = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val prefs = AppPrefs(context)
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // 1. Detect Network Type
        val networkType = detectNetworkType(context)

        // 2. Detect Battery Status
        val (batteryLevel, isCharging) = getBatteryStatus(context)

        // 3. Resolve Telegram Bot Token
        val botToken = resolveBotToken(context, db, prefs)

        var pingMs = 0L
        var uploadSpeedKbps = 0.0
        var status = "SUCCESS"

        if (botToken.isNotBlank()) {
            try {
                // STEP A: Measure Ping / Latency to Telegram Bot API
                val pingStart = System.currentTimeMillis()
                val pingReq = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/getMe")
                    .get()
                    .build()
                val pingResp = httpClient.newCall(pingReq).execute()
                pingMs = (System.currentTimeMillis() - pingStart).coerceAtLeast(1L)
                pingResp.close()

                // STEP B: Measure Upload Throughput with small 128 KB payload
                val testData = ByteArray(128 * 1024) { (it % 255).toByte() }
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "document",
                        "speedtest_probe.tmp",
                        testData.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                    )
                    .build()

                val uploadStart = System.currentTimeMillis()
                // Use getMe / dummy probing or request with small payload
                val uploadReq = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/getMe")
                    .post(requestBody)
                    .build()
                
                try {
                    val uploadResp = httpClient.newCall(uploadReq).execute()
                    val uploadDurationMs = (System.currentTimeMillis() - uploadStart).coerceAtLeast(20L)
                    uploadResp.close()
                    // 128 KB in duration -> Kbps
                    val bytesUploaded = testData.size.toDouble()
                    val seconds = uploadDurationMs.toDouble() / 1000.0
                    uploadSpeedKbps = (bytesUploaded * 8.0 / 1000.0) / seconds
                } catch (_: Exception) {
                    // Fallback to ping-derived speed estimate if POST /getMe rejects multipart
                    uploadSpeedKbps = if (pingMs > 0) (1000.0 / pingMs.toDouble()) * 250.0 else 1024.0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Diagnostic test failed: ${e.message}")
                status = "FAILED"
                pingMs = 999L
                uploadSpeedKbps = 0.0
            }
        } else {
            status = "FAILED"
            pingMs = 0L
            uploadSpeedKbps = 0.0
        }

        val logEntity = NetworkAnalysisEntity(
            timestamp = System.currentTimeMillis(),
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek,
            pingMs = pingMs,
            uploadSpeedKbps = uploadSpeedKbps,
            networkType = networkType,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            status = status
        )

        try {
            db.networkAnalysisDao().insert(logEntity)
            // Auto prune logs older than 7 days (7 * 24 * 3600 * 1000L)
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)
            db.networkAnalysisDao().deleteOlderThan(sevenDaysAgo)
            prefs.lastAnalysisTimestamp = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting analysis log: ${e.message}")
        }

        return@withContext logEntity
    }

    fun syncSelfAnalysisWorker(context: Context) {
        val prefs = AppPrefs(context)
        if (prefs.isSelfAnalysisEnabled) {
            schedulePeriodicAnalysis(context)
        } else {
            cancelPeriodicAnalysis(context)
        }
    }

    fun schedulePeriodicAnalysis(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NetworkAnalysisWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled 30-min 7-day Network Self-Analysis background worker.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule network self analysis worker", e)
        }
    }

    fun cancelPeriodicAnalysis(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled Network Self-Analysis background worker.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel network self analysis worker", e)
        }
    }

    private fun detectNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "UNKNOWN"
            val net = cm.activeNetwork ?: return "NO_CONNECTION"
            val caps = cm.getNetworkCapabilities(net) ?: return "UNKNOWN"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "CELLULAR_5G"
                    else "CELLULAR_4G"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "UNKNOWN"
            }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    private fun getBatteryStatus(context: Context): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            Pair(pct, isCharging)
        } catch (_: Exception) {
            Pair(-1, false)
        }
    }

    private fun resolveBotToken(context: Context, db: AppDatabase, prefs: AppPrefs): String {
        val primaryBot = db.telegramBotDao().getPrimaryBot()
        if (primaryBot != null && primaryBot.botToken.isNotBlank()) return primaryBot.botToken
        val anyActive = db.telegramBotDao().getActiveBots().firstOrNull()
        if (anyActive != null && anyActive.botToken.isNotBlank()) return anyActive.botToken
        return prefs.botToken
    }
}
