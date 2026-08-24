package com.example.telegrambackup.data

import androidx.room.*

@Entity(tableName = "telegram_channels")
data class TelegramChannelEntity(
    @PrimaryKey
    var chatId: String = "",
    var title: String = "",
    var categoryTarget: String = "ALL", // "ALL", "PHOTOS", "VIDEOS", "DOCUMENTS", "DRIVE", "OTHER"
    var isPrimary: Boolean = false,
    var isChannelAdminVerified: Boolean = false,
    var canDeleteMessages: Boolean = false,
    var addedAt: Long = System.currentTimeMillis()
)

@Dao
interface TelegramChannelDao {
    @Query("SELECT * FROM telegram_channels ORDER BY isPrimary DESC, addedAt ASC")
    fun getAllChannels(): List<TelegramChannelEntity>

    @Query("SELECT * FROM telegram_channels ORDER BY isPrimary DESC, addedAt ASC")
    fun getAllChannelsFlow(): kotlinx.coroutines.flow.Flow<List<TelegramChannelEntity>>

    @Query("SELECT * FROM telegram_channels WHERE isPrimary = 1 LIMIT 1")
    fun getPrimaryChannel(): TelegramChannelEntity?

    @Query("SELECT * FROM telegram_channels WHERE categoryTarget = :category LIMIT 1")
    fun getChannelForCategory(category: String): TelegramChannelEntity?

    @Query("SELECT * FROM telegram_channels WHERE chatId = :chatId LIMIT 1")
    fun getChannelById(chatId: String): TelegramChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(channel: TelegramChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(channels: List<TelegramChannelEntity>)

    @Update
    fun update(channel: TelegramChannelEntity)

    @Query("UPDATE telegram_channels SET isPrimary = CASE WHEN chatId = :chatId THEN 1 ELSE 0 END")
    fun setPrimaryChannel(chatId: String)

    @Query("UPDATE telegram_channels SET categoryTarget = :category WHERE chatId = :chatId")
    fun updateCategoryTarget(chatId: String, category: String)

    @Query("DELETE FROM telegram_channels WHERE chatId = :chatId")
    fun deleteById(chatId: String)

    @Query("DELETE FROM telegram_channels")
    fun deleteAll()
}
