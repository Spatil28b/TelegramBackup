package com.example.telegrambackup.data

import androidx.room.*

@Entity(tableName = "telegram_bots")
data class TelegramBotEntity(
    @PrimaryKey
    var botToken: String = "",
    var botUsername: String = "",
    var displayName: String = "",
    var isPrimary: Boolean = false,
    var isActive: Boolean = true,
    var rateLimitUntil: Long = 0L,
    var consecutiveErrors: Int = 0,
    var totalBytesUploaded: Long = 0L,
    var successChunks: Int = 0,
    var failedChunks: Int = 0,
    var addedAt: Long = System.currentTimeMillis()
)

@Dao
interface TelegramBotDao {
    @Query("SELECT * FROM telegram_bots ORDER BY isPrimary DESC, addedAt ASC")
    fun getAllBots(): List<TelegramBotEntity>

    @Query("SELECT * FROM telegram_bots ORDER BY isPrimary DESC, addedAt ASC")
    fun getAllBotsFlow(): kotlinx.coroutines.flow.Flow<List<TelegramBotEntity>>

    @Query("SELECT * FROM telegram_bots WHERE isActive = 1 ORDER BY isPrimary DESC, addedAt ASC")
    fun getActiveBots(): List<TelegramBotEntity>

    @Query("SELECT * FROM telegram_bots WHERE isPrimary = 1 LIMIT 1")
    fun getPrimaryBot(): TelegramBotEntity?

    @Query("SELECT * FROM telegram_bots WHERE botToken = :token LIMIT 1")
    fun getBotByToken(token: String): TelegramBotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bot: TelegramBotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(bots: List<TelegramBotEntity>)

    @Update
    fun update(bot: TelegramBotEntity)

    @Query("UPDATE telegram_bots SET isPrimary = CASE WHEN botToken = :token THEN 1 ELSE 0 END")
    fun setPrimaryBot(token: String)

    @Query("UPDATE telegram_bots SET isActive = :active WHERE botToken = :token")
    fun setBotActive(token: String, active: Boolean)

    @Query("UPDATE telegram_bots SET rateLimitUntil = :until, consecutiveErrors = consecutiveErrors + 1 WHERE botToken = :token")
    fun markRateLimited(token: String, until: Long)

    @Query("UPDATE telegram_bots SET consecutiveErrors = 0, totalBytesUploaded = totalBytesUploaded + :bytes, successChunks = successChunks + 1 WHERE botToken = :token")
    fun recordSuccess(token: String, bytes: Long)

    @Query("UPDATE telegram_bots SET consecutiveErrors = consecutiveErrors + 1, failedChunks = failedChunks + 1 WHERE botToken = :token")
    fun recordFailure(token: String)

    @Query("DELETE FROM telegram_bots WHERE botToken = :token")
    fun deleteByToken(token: String)

    @Query("DELETE FROM telegram_bots")
    fun deleteAll()
}
