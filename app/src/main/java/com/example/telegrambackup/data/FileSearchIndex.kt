package com.example.telegrambackup.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * =====================================================================================
 * FULL-TEXT SEARCH (FTS4) VIRTUAL TABLE ENTITY (P1)
 * =====================================================================================
 * Stores tokenized ML Kit OCR text and semantic tags for sub-millisecond offline search.
 * =====================================================================================
 */
@Entity(tableName = "file_search_index")
@Fts4
data class FileSearchIndex(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int = 0,
    val filePath: String,
    val fileName: String,
    val extractedText: String,
    val tags: String = "",
    val category: String = "",
    val indexedAt: Long = System.currentTimeMillis()
)

@Dao
interface FileSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(index: FileSearchIndex)

    @Query("SELECT rowid, * FROM file_search_index WHERE file_search_index MATCH :query LIMIT :limit")
    fun searchFts(query: String, limit: Int = 100): List<FileSearchIndex>

    @Query("SELECT rowid, * FROM file_search_index WHERE filePath = :path LIMIT 1")
    fun getIndexForFile(path: String): FileSearchIndex?

    @Query("DELETE FROM file_search_index WHERE filePath = :path")
    fun deleteForFile(path: String): Int

    @Query("SELECT COUNT(*) FROM file_search_index")
    fun getIndexedCount(): Int

    @Query("SELECT filePath FROM file_search_index")
    fun getAllIndexedFilePaths(): List<String>

    @Query("DELETE FROM file_search_index")
    fun clearAll(): Int
}
