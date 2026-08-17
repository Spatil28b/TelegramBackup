package com.example.telegrambackup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UploadedFile::class, AppSettingEntity::class, ChunkProgressEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uploadedFileDao(): UploadedFileDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun chunkProgressDao(): ChunkProgressDao

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

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telegram_backup_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
