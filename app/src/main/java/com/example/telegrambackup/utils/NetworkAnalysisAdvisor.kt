package com.example.telegrambackup.utils

import android.content.Context
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.NetworkAnalysisEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class NetworkHealthReport(
    val healthScore: Int,                 // 0 to 100
    val healthRating: String,             // EXCELLENT, GOOD, MODERATE, POOR
    val averageSpeedKbps: Double,
    val peakSpeedKbps: Double,
    val averagePingMs: Long,
    val wifiReliabilityPercent: Int,
    val totalTestsRun: Int,
    val bestBackupWindow: String,         // e.g. "02:00 AM - 06:00 AM"
    val bestStartHour: Int,
    val recommendedThreads: Int,          // 2, 4, 6, 8
    val recommendedSmartMode: Int,        // 0 = Continuous, 1 = Custom, 2 = Night Express
    val recommendedWifiOnly: Boolean,
    val recommendedBotFleetMode: String,  // TURBO, BALANCED, DEDICATED
    val hourlyAverages: Map<Int, Double>,
    val recommendationSummary: String
)

object NetworkAnalysisAdvisor {

    suspend fun generateReport(context: Context): NetworkHealthReport = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val logs = db.networkAnalysisDao().getRecentLogs(limit = 336) // 7 days (48/day)

        if (logs.isEmpty()) {
            return@withContext defaultReport()
        }

        val successLogs = logs.filter { it.status == "SUCCESS" }
        val totalCount = logs.size
        val successCount = successLogs.size

        val avgPing = if (successLogs.isNotEmpty()) successLogs.map { it.pingMs }.average().toLong() else 120L
        val avgSpeed = if (successLogs.isNotEmpty()) successLogs.map { it.uploadSpeedKbps }.average() else 2500.0
        val peakSpeed = if (successLogs.isNotEmpty()) successLogs.maxOfOrNull { it.uploadSpeedKbps } ?: 5000.0 else 5000.0

        val wifiTests = logs.filter { it.networkType == "WIFI" }
        val wifiReliability = if (logs.isNotEmpty()) ((wifiTests.size.toDouble() / logs.size.toDouble()) * 100).toInt() else 100

        // Compute hourly speed distribution (0..23)
        val hourlyMap = mutableMapOf<Int, Double>()
        for (h in 0..23) {
            val samplesForHour = successLogs.filter { it.hourOfDay == h }
            val avg = if (samplesForHour.isNotEmpty()) samplesForHour.map { it.uploadSpeedKbps }.average() else 0.0
            hourlyMap[h] = avg
        }

        // Find the 4-hour window with highest average throughput
        var bestWindowStart = 2
        var maxWindowThroughput = -1.0
        for (startH in 0..23) {
            var sum = 0.0
            for (offset in 0..3) {
                val hour = (startH + offset) % 24
                sum += hourlyMap[hour] ?: 0.0
            }
            if (sum > maxWindowThroughput) {
                maxWindowThroughput = sum
                bestWindowStart = startH
            }
        }
        val bestWindowEnd = (bestWindowStart + 4) % 24
        val bestWindowStr = String.format(Locale.US, "%02d:00 - %02d:00", bestWindowStart, bestWindowEnd)

        // Health Score (0 to 100)
        var score = 100
        if (avgPing > 300) score -= 25
        else if (avgPing > 150) score -= 10

        if (avgSpeed < 1000) score -= 30
        else if (avgSpeed < 3000) score -= 15

        val failRate = if (totalCount > 0) ((totalCount - successCount).toDouble() / totalCount.toDouble()) else 0.0
        score -= (failRate * 40).toInt()
        val finalScore = score.coerceIn(20, 100)

        val rating = when {
            finalScore >= 85 -> "EXCELLENT"
            finalScore >= 70 -> "GOOD"
            finalScore >= 50 -> "MODERATE"
            else -> "POOR"
        }

        // Recommendations based on measured metrics
        val recommendedThreads = when {
            avgSpeed > 8000 -> 6
            avgSpeed > 3000 -> 4
            else -> 2
        }

        val recommendedSmartMode = if (bestWindowStart in 0..5) 2 else 0 // Night Express if peak is early morning
        val recommendedWifiOnly = wifiReliability >= 70 || avgSpeed < 2000
        val recommendedBotFleetMode = when {
            avgSpeed > 5000 -> "TURBO"
            avgSpeed > 2000 -> "BALANCED"
            else -> "DEDICATED"
        }

        val speedMb = avgSpeed / 1024.0
        val peakMb = peakSpeed / 1024.0
        val summary = buildString {
            append("Your network delivers an average upload speed of ${String.format(Locale.US, "%.1f", speedMb)} MB/s (peak: ${String.format(Locale.US, "%.1f", peakMb)} MB/s) with ${avgPing}ms latency. ")
            if (recommendedSmartMode == 2) {
                append("We detected the lowest network congestion and highest speeds between $bestWindowStr. Setting Night Express mode will save battery and maximize backup speed.")
            } else {
                append("Your connection is fast and stable throughout the day. Multi-Bot Turbo mode with $recommendedThreads parallel threads is optimal for your setup.")
            }
        }

        return@withContext NetworkHealthReport(
            healthScore = finalScore,
            healthRating = rating,
            averageSpeedKbps = avgSpeed,
            peakSpeedKbps = peakSpeed,
            averagePingMs = avgPing,
            wifiReliabilityPercent = wifiReliability,
            totalTestsRun = totalCount,
            bestBackupWindow = bestWindowStr,
            bestStartHour = bestWindowStart,
            recommendedThreads = recommendedThreads,
            recommendedSmartMode = recommendedSmartMode,
            recommendedWifiOnly = recommendedWifiOnly,
            recommendedBotFleetMode = recommendedBotFleetMode,
            hourlyAverages = hourlyMap,
            recommendationSummary = summary
        )
    }

    fun applySmartRecommendations(context: Context, report: NetworkHealthReport) {
        val prefs = AppPrefs(context)
        prefs.uploadThreads = report.recommendedThreads
        prefs.smartBackupMode = report.recommendedSmartMode
        if (report.recommendedSmartMode == 2) {
            prefs.nightBackupHour = report.bestStartHour
        }
        prefs.wifiOnly = report.recommendedWifiOnly
        prefs.fleetMode = report.recommendedBotFleetMode
    }

    private fun defaultReport(): NetworkHealthReport {
        return NetworkHealthReport(
            healthScore = 90,
            healthRating = "EXCELLENT",
            averageSpeedKbps = 4500.0,
            peakSpeedKbps = 8500.0,
            averagePingMs = 85L,
            wifiReliabilityPercent = 95,
            totalTestsRun = 0,
            bestBackupWindow = "02:00 - 06:00",
            bestStartHour = 2,
            recommendedThreads = 4,
            recommendedSmartMode = 2,
            recommendedWifiOnly = true,
            recommendedBotFleetMode = "TURBO",
            hourlyAverages = emptyMap(),
            recommendationSummary = "Self-analysis has started. Over the next 7 days, the app will benchmark your connection every 30 minutes to fine-tune optimal backup hours and bot speeds."
        )
    }
}
