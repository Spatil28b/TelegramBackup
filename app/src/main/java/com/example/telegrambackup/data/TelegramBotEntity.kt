package com.example.telegrambackup.data

import android.content.Context
import androidx.room.*
import com.example.telegrambackup.utils.KeystoreVault

@Entity(tableName = "telegram_bots")
data class TelegramBotEntity(
    @PrimaryKey
    var botId: String = "",
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
) {
    @Ignore
    var botToken: String = ""
        get() = field.ifBlank {
            if (botId.isNotBlank()) KeystoreVault.getCachedToken(botId) else ""
        }
        set(value) {
            val clean = value.trim()
            field = clean
            if (clean.isNotBlank()) {
                val derived = deriveBotId(clean)
                if (botId.isBlank()) botId = derived
                KeystoreVault.cacheToken(botId, clean)
            }
        }

    fun resolveToken(context: Context): String {
        if (botToken.isNotBlank()) return botToken
        if (botId.isNotBlank()) {
            val fromKeystore = KeystoreVault.retrieveEncryptedString(context, KEYSTORE_TOKEN_PREFIX + botId)
            if (!fromKeystore.isNullOrBlank()) {
                botToken = fromKeystore
                return fromKeystore
            }
        }
        return ""
    }

    fun persistToken(context: Context, token: String) {
        val clean = token.trim()
        if (clean.isNotBlank()) {
            botToken = clean
            if (botId.isBlank()) {
                botId = deriveBotId(clean)
            }
            KeystoreVault.storeEncryptedString(context, KEYSTORE_TOKEN_PREFIX + botId, clean)
        }
    }

    companion object {
        const val KEYSTORE_TOKEN_PREFIX = "bot_token_"

        fun deriveBotId(token: String): String {
            val trimmed = token.trim()
            val colonIdx = trimmed.indexOf(':')
            return if (colonIdx > 0) trimmed.substring(0, colonIdx) else trimmed
        }

        fun fromToken(
            token: String,
            displayName: String = "",
            isPrimary: Boolean = false,
            isActive: Boolean = true,
            botUsername: String = "",
            addedAt: Long = System.currentTimeMillis()
        ): TelegramBotEntity {
            val derivedId = deriveBotId(token)
            val entity = TelegramBotEntity(
                botId = derivedId,
                botUsername = botUsername,
                displayName = displayName.ifBlank { "Bot $derivedId" },
                isPrimary = isPrimary,
                isActive = isActive,
                addedAt = addedAt
            )
            entity.botToken = token
            return entity
        }

        fun create(
            context: Context,
            token: String,
            displayName: String = "",
            isPrimary: Boolean = false,
            botUsername: String = ""
        ): TelegramBotEntity {
            val derivedId = deriveBotId(token)
            val entity = TelegramBotEntity(
                botId = derivedId,
                botUsername = botUsername,
                displayName = displayName.ifBlank { "Bot $derivedId" },
                isPrimary = isPrimary
            )
            entity.persistToken(context, token)
            return entity
        }
    }
}

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

    @Query("SELECT * FROM telegram_bots WHERE botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1) LIMIT 1")
    fun getBotByIdOrToken(idOrToken: String): TelegramBotEntity?

    fun getBotByToken(token: String): TelegramBotEntity? = getBotByIdOrToken(token)

    fun getBotById(botId: String): TelegramBotEntity? = getBotByIdOrToken(botId)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bot: TelegramBotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(bots: List<TelegramBotEntity>)

    @Update
    fun update(bot: TelegramBotEntity)

    @Query("UPDATE telegram_bots SET isPrimary = CASE WHEN botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1) THEN 1 ELSE 0 END")
    fun setPrimaryBot(idOrToken: String)

    @Query("UPDATE telegram_bots SET isActive = :active WHERE botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1)")
    fun setBotActive(idOrToken: String, active: Boolean)

    @Query("UPDATE telegram_bots SET rateLimitUntil = :until, consecutiveErrors = consecutiveErrors + 1 WHERE botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1)")
    fun markRateLimited(idOrToken: String, until: Long)

    @Query("UPDATE telegram_bots SET consecutiveErrors = 0, totalBytesUploaded = totalBytesUploaded + :bytes, successChunks = successChunks + 1 WHERE botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1)")
    fun recordSuccess(idOrToken: String, bytes: Long)

    @Query("UPDATE telegram_bots SET consecutiveErrors = consecutiveErrors + 1, failedChunks = failedChunks + 1 WHERE botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1)")
    fun recordFailure(idOrToken: String)

    @Query("DELETE FROM telegram_bots WHERE botId = :idOrToken OR botId = substr(:idOrToken, 1, instr(:idOrToken, ':') - 1)")
    fun deleteByToken(idOrToken: String)

    fun deleteById(botId: String) = deleteByToken(botId)

    @Query("DELETE FROM telegram_bots")
    fun deleteAll()
}
