package com.example.telegrambackup.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.FileSearchIndex
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * =====================================================================================
 * ON-DEVICE ML KIT OCR BACKGROUND INDEXER (P1)
 * =====================================================================================
 * Runs locally on-device without network reliance. Extracts text from backed-up
 * photos and documents, populating Room's FTS4 virtual table for instant 0ms search.
 * =====================================================================================
 */
class OcrIndexingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "OcrIndexingWorker"
        private const val PERIODIC_WORK_NAME = "PeriodicOcrIndexingWork"
        private const val ONE_TIME_WORK_NAME = "OneTimeOcrIndexingWork"

        private val SUPPORTED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "bmp", "heic"
        )

        /**
         * Enqueues a one-time immediate background indexing run.
         */
        fun enqueueOneTimeIndexing(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrIndexingWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Schedules periodic background indexing (every 12 hours) when battery is not low.
         */
        fun schedulePeriodicOcrIndexing(context: Context) {
            val request = PeriodicWorkRequestBuilder<OcrIndexingWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Indexes a single file synchronously / on-demand if it exists locally.
         */
        suspend fun indexSingleFile(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
            if (!file.exists() || !file.canRead()) return@withContext false
            val ext = file.extension.lowercase()
            if (!SUPPORTED_EXTENSIONS.contains(ext)) return@withContext false

            val db = AppDatabase.getDatabase(context)
            val existing = db.fileSearchDao().getIndexForFile(file.absolutePath)
            if (existing != null) return@withContext true

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val inputImage = InputImage.fromFilePath(context, Uri.fromFile(file))
                val visionText = recognizer.process(inputImage).awaitResult()
                val extracted = visionText.text.trim()
                if (extracted.isNotBlank()) {
                    val tags = deriveTags(file.name, extracted)
                    val category = deriveCategory(extracted)
                    val index = FileSearchIndex(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        extractedText = extracted,
                        tags = tags,
                        category = category
                    )
                    db.fileSearchDao().insertOrUpdate(index)
                    Log.d(TAG, "Indexed file '${file.name}' with ${extracted.length} chars of OCR text.")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "OCR extraction failed for ${file.name}: ${e.message}")
            } finally {
                recognizer.close()
            }
            return@withContext false
        }

        private fun deriveTags(fileName: String, text: String): String {
            val combined = (fileName + " " + text).lowercase()
            val tags = mutableSetOf<String>()

            if (combined.contains("pan") || combined.contains("incometax") || combined.contains("permanent account")) tags.add("pan_card")
            if (combined.contains("aadhaar") || combined.contains("aadhar") || combined.contains("uidai")) tags.add("aadhaar")
            if (combined.contains("license") || combined.contains("driving") || combined.contains("transport")) tags.add("driving_license")
            if (combined.contains("receipt") || combined.contains("invoice") || combined.contains("paid") || combined.contains("upi") || combined.contains("transaction")) tags.add("receipt")
            if (combined.contains("bonafide") || combined.contains("student") || combined.contains("admission") || combined.contains("college") || combined.contains("university")) tags.add("student_doc")
            if (combined.contains("marksheet") || combined.contains("score") || combined.contains("board") || combined.contains("result")) tags.add("marksheet")
            if (combined.contains("prescription") || combined.contains("doctor") || combined.contains("hospital") || combined.contains("clinic") || combined.contains("pharma")) tags.add("medical")
            if (combined.contains("bill") || combined.contains("electricity") || combined.contains("bescom") || combined.contains("mseb")) tags.add("bill")
            if (combined.contains("salary") || combined.contains("payslip") || combined.contains("earnings")) tags.add("salary")

            return tags.joinToString(", ")
        }

        private fun deriveCategory(text: String): String {
            val lower = text.lowercase()
            return when {
                lower.contains("prescription") || lower.contains("doctor") || lower.contains("hospital") -> "HEALTH"
                lower.contains("pan") || lower.contains("aadhaar") || lower.contains("license") || lower.contains("identity") -> "ID_CARD"
                lower.contains("receipt") || lower.contains("bill") || lower.contains("invoice") || lower.contains("paid") -> "FINANCE"
                lower.contains("bonafide") || lower.contains("marksheet") || lower.contains("college") || lower.contains("university") -> "EDUCATION"
                else -> "GENERAL"
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val allFiles = db.uploadedFileDao().getAllFiles()
        if (allFiles.isEmpty()) return@withContext Result.success()

        val alreadyIndexed = db.fileSearchDao().getAllIndexedFilePaths().toSet()
        val toIndex = allFiles.filter { file ->
            val ext = file.fileName.substringAfterLast('.', "").lowercase()
            SUPPORTED_EXTENSIONS.contains(ext) &&
                    !alreadyIndexed.contains(file.path) &&
                    File(file.path).exists()
        }.take(30) // Process in friendly batches of 30 files per cycle

        if (toIndex.isEmpty()) return@withContext Result.success()

        Log.d(TAG, "Starting on-device OCR indexing batch of ${toIndex.size} files...")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            for (uploadedFile in toIndex) {
                val localFile = File(uploadedFile.path)
                if (!localFile.exists()) continue

                try {
                    val inputImage = InputImage.fromFilePath(applicationContext, Uri.fromFile(localFile))
                    val visionText = recognizer.process(inputImage).awaitResult()
                    val extracted = visionText.text.trim()

                    val tags = deriveTags(uploadedFile.fileName, extracted)
                    val category = deriveCategory(extracted)

                    val index = FileSearchIndex(
                        filePath = uploadedFile.path,
                        fileName = uploadedFile.fileName,
                        extractedText = extracted.ifBlank { uploadedFile.fileName },
                        tags = tags,
                        category = category
                    )
                    db.fileSearchDao().insertOrUpdate(index)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed indexing ${uploadedFile.fileName}: ${e.message}")
                }
            }
        } finally {
            recognizer.close()
        }

        Result.success()
    }
}

/**
 * Extension helper to suspend until Google Play Tasks complete without play-services-coroutines artifact.
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
