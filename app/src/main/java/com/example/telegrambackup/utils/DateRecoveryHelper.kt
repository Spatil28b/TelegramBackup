package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import com.example.telegrambackup.network.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.regex.Pattern

object DateRecoveryHelper {
    private const val TAG = "DateRecoveryHelper"

    private val CAMERA_PATTERN = Pattern.compile(
        "(?:VID|IMG|PXL|AUD|REC|FILE|Screenshot)_?(\\d{4})(\\d{2})(\\d{2})_?(\\d{2})?(\\d{2})?(\\d{2})?",
        Pattern.CASE_INSENSITIVE
    )

    private val WHATSAPP_PATTERN = Pattern.compile(
        "(?:VID|IMG|AUD|PTT)-(\\d{4})(\\d{2})(\\d{2})-WA",
        Pattern.CASE_INSENSITIVE
    )

    private val EPOCH_PATTERN = Pattern.compile("(\\d{13})")

    /**
     * Parses the authentic capture or creation date directly from the filename.
     * Works for Camera videos/photos, WhatsApp media, Screenshots, and Unix epoch named files.
     */
    fun parseTrueTimestampFromFileName(fileName: String): Long? {
        if (fileName.isBlank()) return null

        try {
            // Pattern 1: Camera / Screenshot / Generic standard media naming
            val m1 = CAMERA_PATTERN.matcher(fileName)
            if (m1.find()) {
                val year = m1.group(1)?.toIntOrNull() ?: return null
                val month = (m1.group(2)?.toIntOrNull() ?: return null) - 1
                val day = m1.group(3)?.toIntOrNull() ?: return null
                val hour = m1.group(4)?.toIntOrNull() ?: 12
                val min = m1.group(5)?.toIntOrNull() ?: 0
                val sec = m1.group(6)?.toIntOrNull() ?: 0

                if (year in 2015..2030 && month in 0..11 && day in 1..31 && hour in 0..23 && min in 0..59 && sec in 0..59) {
                    val cal = Calendar.getInstance()
                    cal.set(year, month, day, hour, min, sec)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            }

            // Pattern 2: WhatsApp media
            val m2 = WHATSAPP_PATTERN.matcher(fileName)
            if (m2.find()) {
                val year = m2.group(1)?.toIntOrNull() ?: return null
                val month = (m2.group(2)?.toIntOrNull() ?: return null) - 1
                val day = m2.group(3)?.toIntOrNull() ?: return null

                if (year in 2015..2030 && month in 0..11 && day in 1..31) {
                    val cal = Calendar.getInstance()
                    cal.set(year, month, day, 12, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            }

            // Pattern 3: Explicit Unix epoch in filename
            val m3 = EPOCH_PATTERN.matcher(fileName)
            if (m3.find()) {
                val ts = m3.group(1)?.toLongOrNull()
                if (ts != null && ts in 1500000000000L..2000000000000L) {
                    return ts
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing date from $fileName: ${e.message}")
        }

        return null
    }

    /**
     * Checks if a file's recorded timestamp differs significantly (> 24 hours) from its
     * true capture date (from filename or local file metadata).
     */
    fun isDateDesynced(file: UploadedFile): Boolean {
        val parsed = parseTrueTimestampFromFileName(file.fileName)
        if (parsed != null) {
            val diffMs = Math.abs(file.timestamp - parsed)
            return diffMs > (24L * 60 * 60 * 1000)
        }
        // Legacy placeholder July 2026 or corrupted zero-epoch
        return file.timestamp == 1784030400000L || file.timestamp < 946684800000L
    }

    /**
     * Obtains the most authentic timestamp possible for an uploaded file:
     * 1. Local file lastModified if it exists on device
     * 2. Parsed capture date from filename (Camera/WhatsApp/Screenshot)
     * 3. Existing file timestamp if reasonable (> 2020)
     * 4. Safe fallback
     */
    fun getAuthenticTimestamp(file: UploadedFile): Long {
        try {
            val local = FileViewerHelper.findLocalFile(file.path, file.fileName)
            if (local != null && local.exists() && local.lastModified() > 946684800000L) {
                return local.lastModified()
            }
        } catch (_: Exception) {}

        val parsed = parseTrueTimestampFromFileName(file.fileName)
        if (parsed != null) {
            return parsed
        }

        if (file.timestamp > 946684800000L && file.timestamp != 1784030400000L) {
            return file.timestamp
        }

        return System.currentTimeMillis()
    }

    /**
     * Safely heals all desynced or corrupted timestamps across Room DB and pushes
     * them to the cloud database. Never blindly sets timestamps to currentTimeMillis.
     */
    suspend fun recoverAuthenticDates(context: Context): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.uploadedFileDao()
            val allFiles = dao.getAllRawFiles()
            val toUpdate = mutableListOf<UploadedFile>()

            for (file in allFiles) {
                if (isDateDesynced(file)) {
                    val trueTs = getAuthenticTimestamp(file)
                    if (trueTs != file.timestamp) {
                        toUpdate.add(file.copy(timestamp = trueTs))
                        restoredCount++
                    }
                }
            }

            if (toUpdate.isNotEmpty()) {
                dao.insertAll(toUpdate)
                Log.i(TAG, "Restored authentic dates for $restoredCount files in local Room DB")

                // Push updated timestamps to cloud
                try {
                    val prefs = AppPrefs(context)
                    if (prefs.userEmail.isNotBlank()) {
                        ServerApi().indexFilesBatch(prefs.serverUrl, prefs.userEmail, toUpdate)
                        Log.i(TAG, "Successfully synced restored dates to cloud backend")
                    }
                } catch (cloudErr: Exception) {
                    Log.w(TAG, "Failed to push date fixes to cloud: ${cloudErr.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recoverAuthenticDates: ${e.message}", e)
        }
        restoredCount
    }
}
