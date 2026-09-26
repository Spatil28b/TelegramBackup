package com.example.telegrambackup.data

import android.content.Context
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
        NetworkAnalysisEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadedFileDao(): UploadedFileDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun chunkProgressDao(): ChunkProgressDao
    abstract fun telegramBotDao(): TelegramBotDao
    abstract fun telegramChannelDao(): TelegramChannelDao
    abstract fun networkAnalysisDao(): NetworkAnalysisDao

    companion object {
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
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `botTokenUsed` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `botTokenUsed` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `channelId` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
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
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `chunksJson` TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_1_12 = object : Migration(1, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all latest schema elements exist for legacy users jumping directly from v1
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
                        `botTokenUsed` TEXT NOT NULL DEFAULT '',
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
                        `uploadedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'COMPLETED',
                        `botTokenUsed` TEXT NOT NULL DEFAULT '',
                        `channelId` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`sourcePath`, `chunkIndex`)
                    )
                """.trimIndent())

                ensureChunkProgressColumns(db)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_bots` (
                        `botToken` TEXT NOT NULL PRIMARY KEY,
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

                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_isDeleted_timestamp` ON `uploaded_files` (`isDeleted`, `timestamp`)") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_isDeleted_isDriveUpload` ON `uploaded_files` (`isDeleted`, `isDriveUpload`)") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_fileName` ON `uploaded_files` (`fileName`)") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_deletedAt` ON `uploaded_files` (`deletedAt`)") } catch (_: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_uploaded_files_telegramFileId` ON `uploaded_files` (`telegramFileId`)") } catch (_: Exception) {}
            } catch (_: Exception) {}
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
            } catch (_: Throwable) {}

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
                "chunksJson" to "TEXT NOT NULL DEFAULT ''"
            )
            for ((col, def) in columns) {
                if (!existing.contains(col)) {
                    try {
                        db.execSQL("ALTER TABLE `uploaded_files` ADD COLUMN `$col` $def")
                    } catch (_: Throwable) {}
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
            } catch (_: Throwable) {}

            val columns = listOf(
                "status" to "TEXT NOT NULL DEFAULT 'COMPLETED'",
                "botTokenUsed" to "TEXT NOT NULL DEFAULT ''",
                "channelId" to "TEXT NOT NULL DEFAULT ''"
            )
            for ((col, def) in columns) {
                if (!existing.contains(col)) {
                    try {
                        db.execSQL("ALTER TABLE `chunk_upload_progress` ADD COLUMN `$col` $def")
                    } catch (_: Throwable) {}
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
                    MIGRATION_11_12
                )
                // Guarantee zero crashes on startup for any corrupt or unknown previous database schema
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Self-healing schema validation on startup
                        createAllTablesAndIndexes(db)
                        try {
                            val now = System.currentTimeMillis()
                            db.execSQL("UPDATE uploaded_files SET timestamp = $now WHERE timestamp = 1784030400000 OR timestamp < 946684800000 OR timestamp IS NULL")
                        } catch (_: Exception) {}
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
