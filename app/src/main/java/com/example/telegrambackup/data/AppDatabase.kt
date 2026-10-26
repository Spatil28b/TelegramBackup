package com.example.telegrambackup.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UploadedFile::class,
        AppSettingEntity::class,
        ChunkProgressEntity::class,
        TelegramBotEntity::class,
        TelegramChannelEntity::class,
        NetworkAnalysisEntity::class,
        FileSearchIndex::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadedFileDao(): UploadedFileDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun chunkProgressDao(): ChunkProgressDao
    abstract fun telegramBotDao(): TelegramBotDao
    abstract fun telegramChannelDao(): TelegramChannelDao
    abstract fun networkAnalysisDao(): NetworkAnalysisDao
    abstract fun fileSearchDao(): FileSearchDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chunk_upload_progress` (
                        `sourcePath` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `totalChunks` INTEGER NOT NULL,
                        `telegramFileId` TEXT NOT NULL,
                        `messageId` INTEGER NOT NULL,
                        `chunkSize` INTEGER NOT NULL,
                        `uploadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourcePath`, `chunkIndex`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_bots` (
                        `botToken` TEXT NOT NULL,
                        `botUsername` TEXT NOT NULL DEFAULT '',
                        `displayName` TEXT NOT NULL DEFAULT '',
                        `isPrimary` INTEGER NOT NULL DEFAULT 0,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `rateLimitUntil` INTEGER NOT NULL DEFAULT 0,
                        `consecutiveErrors` INTEGER NOT NULL DEFAULT 0,
                        `totalBytesUploaded` INTEGER NOT NULL DEFAULT 0,
                        `successChunks` INTEGER NOT NULL DEFAULT 0,
                        `failedChunks` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`botToken`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_channels` (
                        `chatId` TEXT NOT NULL,
                        `title` TEXT NOT NULL DEFAULT '',
                        `categoryTarget` TEXT NOT NULL DEFAULT 'ALL',
                        `isPrimary` INTEGER NOT NULL DEFAULT 0,
                        `isChannelAdminVerified` INTEGER NOT NULL DEFAULT 0,
                        `canDeleteMessages` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`chatId`)
                    )
                """.trimIndent())

                try {
                    db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `channelId` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "Column channelId already exists or failed: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `botTokenUsed` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "Column botTokenUsed already exists or failed: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `botTokenUsed` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "Column botTokenUsed in chunk_progress already exists or failed: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `channelId` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "Column channelId in chunk_progress already exists or failed: ${e.message}")
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `network_analysis_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `pingMs` INTEGER NOT NULL,
                        `uploadSpeedKbps` REAL NOT NULL,
                        `networkType` TEXT NOT NULL,
                        `batteryLevel` INTEGER NOT NULL,
                        `isCharging` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_isDeleted_timestamp` ON `uploaded_files` (`isDeleted`, `timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_isDeleted_isDriveUpload` ON `uploaded_files` (`isDeleted`, `isDriveUpload`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_fileName` ON `uploaded_files` (`fileName`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_deletedAt` ON `uploaded_files` (`deletedAt`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_telegramFileId` ON `uploaded_files` (`telegramFileId`)")
                } catch (e: Exception) {
                    Log.w(TAG, "Index creation failed: ${e.message}")
                }
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `chunksJson` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "Column chunksJson already exists or failed: ${e.message}")
                }
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS `file_search_index` USING FTS4(
                            `filePath` TEXT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `extractedText` TEXT NOT NULL,
                            `tags` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `indexedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())
                } catch (e: Exception) {
                    Log.w(TAG, "FTS table migration failed: ${e.message}")
                }
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Migrate telegram_bots: switch primary key from botToken to botId
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `telegram_bots_new` (
                            `botId` TEXT NOT NULL,
                            `botUsername` TEXT NOT NULL DEFAULT '',
                            `displayName` TEXT NOT NULL DEFAULT '',
                            `isPrimary` INTEGER NOT NULL DEFAULT 0,
                            `isActive` INTEGER NOT NULL DEFAULT 1,
                            `rateLimitUntil` INTEGER NOT NULL DEFAULT 0,
                            `consecutiveErrors` INTEGER NOT NULL DEFAULT 0,
                            `totalBytesUploaded` INTEGER NOT NULL DEFAULT 0,
                            `successChunks` INTEGER NOT NULL DEFAULT 0,
                            `failedChunks` INTEGER NOT NULL DEFAULT 0,
                            `addedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`botId`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        INSERT OR REPLACE INTO `telegram_bots_new` (
                            `botId`, `botUsername`, `displayName`, `isPrimary`, `isActive`,
                            `rateLimitUntil`, `consecutiveErrors`, `totalBytesUploaded`,
                            `successChunks`, `failedChunks`, `addedAt`
                        )
                        SELECT
                            CASE WHEN instr(`botToken`, ':') > 0 THEN substr(`botToken`, 1, instr(`botToken`, ':') - 1) ELSE `botToken` END,
                            `botUsername`, `displayName`, `isPrimary`, `isActive`,
                            `rateLimitUntil`, `consecutiveErrors`, `totalBytesUploaded`,
                            `successChunks`, `failedChunks`, `addedAt`
                        FROM `telegram_bots`
                    """.trimIndent())

                    db.execSQL("DROP TABLE IF EXISTS `telegram_bots`")
                    db.execSQL("ALTER TABLE `telegram_bots_new` RENAME TO `telegram_bots`")
                } catch (e: Exception) {
                    Log.w(TAG, "MIGRATION_13_14 telegram_bots migration error: ${e.message}")
                }

                // 2. Add botIdUsed column to uploaded_files and backfill
                try {
                    db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `botIdUsed` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("""
                        UPDATE `uploaded_files`
                        SET `botIdUsed` = CASE WHEN instr(`botTokenUsed`, ':') > 0 THEN substr(`botTokenUsed`, 1, instr(`botTokenUsed`, ':') - 1) ELSE `botTokenUsed` END
                        WHERE `botIdUsed` = '' AND `botTokenUsed` != ''
                    """.trimIndent())
                } catch (e: Exception) {
                    Log.w(TAG, "MIGRATION_13_14 uploaded_files.botIdUsed migration error: ${e.message}")
                }

                // 3. Add botIdUsed column to chunk_upload_progress and backfill
                try {
                    db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `botIdUsed` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("""
                        UPDATE `chunk_upload_progress`
                        SET `botIdUsed` = CASE WHEN instr(`botTokenUsed`, ':') > 0 THEN substr(`botTokenUsed`, 1, instr(`botTokenUsed`, ':') - 1) ELSE `botTokenUsed` END
                        WHERE `botIdUsed` = '' AND `botTokenUsed` != ''
                    """.trimIndent())
                } catch (e: Exception) {
                    Log.w(TAG, "MIGRATION_13_14 chunk_upload_progress.botIdUsed migration error: ${e.message}")
                }
            }
        }

        private val MIGRATION_1_12 = object : Migration(1, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createAllTablesAndIndexes(db)
            }
        }

        private val MIGRATION_2_12 = object : Migration(2, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createAllTablesAndIndexes(db)
            }
        }

        private val MIGRATION_3_12 = object : Migration(3, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createAllTablesAndIndexes(db)
            }
        }

        private val MIGRATION_4_12 = object : Migration(4, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createAllTablesAndIndexes(db)
            }
        }

        private val MIGRATION_5_12 = object : Migration(5, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createAllTablesAndIndexes(db)
            }
        }

        private fun createAllTablesAndIndexes(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `uploaded_files` (
                        `path` TEXT NOT NULL PRIMARY KEY,
                        `fileName` TEXT NOT NULL DEFAULT '',
                        `telegramFileId` TEXT NOT NULL DEFAULT '',
                        `timestamp` INTEGER NOT NULL DEFAULT 0,
                        `size` INTEGER NOT NULL DEFAULT 0,
                        `messageId` INTEGER NOT NULL DEFAULT 0,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `deletedAt` INTEGER NOT NULL DEFAULT 0,
                        `totalChunks` INTEGER NOT NULL DEFAULT 1,
                        `isDriveUpload` INTEGER NOT NULL DEFAULT 0,
                        `channelId` TEXT NOT NULL DEFAULT '',
                        `botIdUsed` TEXT NOT NULL DEFAULT '',
                        `chunksJson` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())

                ensureUploadedFilesColumns(db)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `value` TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chunk_upload_progress` (
                        `sourcePath` TEXT NOT NULL,
                        `chunkIndex` INTEGER NOT NULL,
                        `totalChunks` INTEGER NOT NULL,
                        `telegramFileId` TEXT NOT NULL,
                        `messageId` INTEGER NOT NULL,
                        `chunkSize` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'COMPLETED',
                        `botIdUsed` TEXT NOT NULL DEFAULT '',
                        `channelId` TEXT NOT NULL DEFAULT '',
                        `uploadedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`sourcePath`, `chunkIndex`)
                    )
                """.trimIndent())

                ensureChunkProgressColumns(db)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_bots` (
                        `botId` TEXT NOT NULL PRIMARY KEY,
                        `botUsername` TEXT NOT NULL DEFAULT '',
                        `displayName` TEXT NOT NULL DEFAULT '',
                        `isPrimary` INTEGER NOT NULL DEFAULT 0,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `rateLimitUntil` INTEGER NOT NULL DEFAULT 0,
                        `consecutiveErrors` INTEGER NOT NULL DEFAULT 0,
                        `totalBytesUploaded` INTEGER NOT NULL DEFAULT 0,
                        `successChunks` INTEGER NOT NULL DEFAULT 0,
                        `failedChunks` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_channels` (
                        `chatId` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL DEFAULT '',
                        `categoryTarget` TEXT NOT NULL DEFAULT 'ALL',
                        `isPrimary` INTEGER NOT NULL DEFAULT 0,
                        `isChannelAdminVerified` INTEGER NOT NULL DEFAULT 0,
                        `canDeleteMessages` INTEGER NOT NULL DEFAULT 0,
                        `addedAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `network_analysis_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `pingMs` INTEGER NOT NULL,
                        `uploadSpeedKbps` REAL NOT NULL,
                        `networkType` TEXT NOT NULL,
                        `batteryLevel` INTEGER NOT NULL,
                        `isCharging` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())

                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_isDeleted_timestamp` ON `uploaded_files` (`isDeleted`, `timestamp`)") } catch (e: Exception) { Log.w(TAG, "Index failed: ${e.message}") }
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_isDeleted_isDriveUpload` ON `uploaded_files` (`isDeleted`, `isDriveUpload`)") } catch (e: Exception) { Log.w(TAG, "Index failed: ${e.message}") }
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_fileName` ON `uploaded_files` (`fileName`)") } catch (e: Exception) { Log.w(TAG, "Index failed: ${e.message}") }
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_deletedAt` ON `uploaded_files` (`deletedAt`)") } catch (e: Exception) { Log.w(TAG, "Index failed: ${e.message}") }
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_telegramFileId` ON `uploaded_files` (`telegramFileId`)") } catch (e: Exception) { Log.w(TAG, "Index failed: ${e.message}") }
                try {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS `file_search_index` USING FTS4(
                            `filePath` TEXT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `extractedText` TEXT NOT NULL,
                            `tags` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `indexedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())
                } catch (e: Exception) {
                    Log.w(TAG, "FTS table creation failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "createAllTablesAndIndexes failed: ${e.message}")
            }
        }

        private fun ensureUploadedFilesColumns(db: SupportSQLiteDatabase) {
            val existing = mutableSetOf<String>()
            try {
                db.query("PRAGMA table_info(`uploaded_files`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) existing.add(cursor.getString(nameIdx))
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed reading table info for uploaded_files: ${e.message}")
            }

            val columns = listOf(
                "fileName" to "TEXT NOT NULL DEFAULT ''",
                "telegramFileId" to "TEXT NOT NULL DEFAULT ''",
                "timestamp" to "INTEGER NOT NULL DEFAULT 0",
                "size" to "INTEGER NOT NULL DEFAULT 0",
                "messageId" to "INTEGER NOT NULL DEFAULT 0",
                "isDeleted" to "INTEGER NOT NULL DEFAULT 0",
                "deletedAt" to "INTEGER NOT NULL DEFAULT 0",
                "totalChunks" to "INTEGER NOT NULL DEFAULT 1",
                "isDriveUpload" to "INTEGER NOT NULL DEFAULT 0",
                "channelId" to "TEXT NOT NULL DEFAULT ''",
                "botTokenUsed" to "TEXT NOT NULL DEFAULT ''",
                "botIdUsed" to "TEXT NOT NULL DEFAULT ''",
                "chunksJson" to "TEXT NOT NULL DEFAULT ''"
            )
            for ((col, def) in columns) {
                if (!existing.contains(col)) {
                    try {
                        db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `$col` $def")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed adding column $col: ${e.message}")
                    }
                }
            }
        }

        private fun ensureChunkProgressColumns(db: SupportSQLiteDatabase) {
            val existing = mutableSetOf<String>()
            try {
                db.query("PRAGMA table_info(`chunk_upload_progress`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) existing.add(cursor.getString(nameIdx))
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed reading table info for chunk_upload_progress: ${e.message}")
            }

            val columns = listOf(
                "status" to "TEXT NOT NULL DEFAULT 'COMPLETED'",
                "botTokenUsed" to "TEXT NOT NULL DEFAULT ''",
                "botIdUsed" to "TEXT NOT NULL DEFAULT ''",
                "channelId" to "TEXT NOT NULL DEFAULT ''"
            )
            for ((col, def) in columns) {
                if (!existing.contains(col)) {
                    try {
                        db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `$col` $def")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed adding column $col: ${e.message}")
                    }
                }
            }
        }

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telegram_backup_database"
                )
                .addMigrations(
                    MIGRATION_1_12,
                    MIGRATION_2_12,
                    MIGRATION_3_12,
                    MIGRATION_4_12,
                    MIGRATION_5_12,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14
                )
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Self-healing schema validation on startup
                        createAllTablesAndIndexes(db)
                        try {
                            val now = System.currentTimeMillis()
                            db.execSQL("UPDATE uploaded_files SET timestamp = $now WHERE timestamp = 1784030400000 OR timestamp < 946684800000 OR timestamp IS NULL")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed healing dates in uploaded_files: ${e.message}")
                        }
                        try {
                            // Finding #4, #6: Ensure raw encryption keys and bot tokens are purged from Room settings
                            db.execSQL("DELETE FROM app_settings WHERE `key` IN ('encryption_key', 'bot_token', 'chat_id')")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed purging sensitive keys from app_settings: ${e.message}")
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

