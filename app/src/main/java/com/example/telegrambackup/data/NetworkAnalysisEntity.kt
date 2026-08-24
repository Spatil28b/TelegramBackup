package com.example.telegrambackup.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "network_analysis_log")
data class NetworkAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val hourOfDay: Int = 0,     // 0 to 23
    val dayOfWeek: Int = 1,     // 1 to 7
    val pingMs: Long = 0L,
    val uploadSpeedKbps: Double = 0.0,
    val networkType: String = "UNKNOWN", // WIFI, CELLULAR_5G, CELLULAR_4G, METERED, UNKNOWN
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val status: String = "SUCCESS" // SUCCESS, FAILED
)

@Dao
interface NetworkAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(log: NetworkAnalysisEntity): Long

    @Query("SELECT * FROM network_analysis_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 336): List<NetworkAnalysisEntity>

    @Query("SELECT * FROM network_analysis_log WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getLogsSince(sinceTimestamp: Long): List<NetworkAnalysisEntity>

    @Query("SELECT AVG(uploadSpeedKbps) FROM network_analysis_log WHERE hourOfDay = :hour AND status = 'SUCCESS'")
    fun getAverageSpeedForHour(hour: Int): Double?

    @Query("SELECT AVG(pingMs) FROM network_analysis_log WHERE status = 'SUCCESS'")
    fun getAveragePing(): Double?

    @Query("SELECT COUNT(*) FROM network_analysis_log")
    fun getSampleCount(): Int

    @Query("DELETE FROM network_analysis_log WHERE timestamp < :cutoffTimestamp")
    fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM network_analysis_log")
    fun deleteAll()
}
