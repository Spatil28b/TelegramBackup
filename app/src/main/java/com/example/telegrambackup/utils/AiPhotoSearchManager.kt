package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.*
import java.io.File

/**
 * =====================================================================================
 * AI PHOTO & DOCUMENT SEARCH MANAGER
 * =====================================================================================
 * Enables natural-language visual search across all backed-up and local photos:
 * - "bonafide certificate", "payment receipt", "pan card", "party photos",
 *   "10th marksheet", "10k transfer receipt", "electricity bill", "car / bike photos".
 *
 * Two-stage execution:
 * 1. Instant Semantic & Cached Ranker (0ms response time).
 * 2. Deep Multimodal Gemini Vision Classifier (streaming visual image matches).
 * =====================================================================================
 */
object AiPhotoSearchManager {
    private const val TAG = "AiPhotoSearchManager"

    data class AiSearchResultItem(
        val file: UploadedFile,
        val matchReason: String,
        val isAiVerified: Boolean,
        val badge: String = "✨ Match"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "heic", "gif", "pdf"
    )

    private val PRESET_QUERIES = listOf(
        Preset("🎓 Bonafide Certificate", "bonafide certificate"),
        Preset("🧾 Payment Receipt", "payment receipt"),
        Preset("🪪 PAN Card / ID", "pan card"),
        Preset("🎉 Party & Events", "party photos"),
        Preset("📄 10th / 12th Marksheet", "marksheet"),
        Preset("💵 ₹10k Transfer", "10k payment receipt"),
        Preset("⚡ Electricity Bill", "electricity bill"),
        Preset("🏥 Medical / Prescription", "medical prescription"),
        Preset("🚗 Car & Travel", "car vehicle travel"),
        Preset("🍕 Food & Cafe", "food dinner restaurant")
    )

    data class Preset(val label: String, val query: String)

    fun getPresets(): List<Preset> = PRESET_QUERIES

    private val KEYWORD_SYNONYMS = mapOf(
        "bonafide" to listOf("bonafide", "bonafied", "certificate", "study", "college", "school", "admission", "student_cert", "institution"),
        "certificate" to listOf("certificate", "cert", "bonafide", "degree", "diploma", "award", "completion"),
        "pan" to listOf("pan", "pancard", "pan_card", "nsdl", "uti", "incometax", "income_tax", "tax_id"),
        "receipt" to listOf("receipt", "payment", "bill", "invoice", "upi", "gpay", "phonepe", "paytm", "transaction", "paid", "utr", "challan"),
        "payment" to listOf("payment", "receipt", "invoice", "bill", "upi", "gpay", "phonepe", "paytm", "trans", "transfer", "10k", "10000"),
        "10k" to listOf("10k", "10000", "10,000", "payment", "receipt", "transfer"),
        "party" to listOf("party", "birthday", "bday", "celebrat", "event", "gathering", "friends", "night", "club", "fest", "dinner", "picnic"),
        "marksheet" to listOf("marksheet", "mark_sheet", "result", "grade", "score", "10th", "12th", "ssc", "hsc", "cbse", "icse", "board", "transcript"),
        "bill" to listOf("bill", "electricity", "water", "gas", "power", "utility", "bescom", "mseb", "tneb", "invoice", "receipt"),
        "aadhaar" to listOf("aadhaar", "aadhar", "uidai", "id_card", "identity", "govt_id"),
        "license" to listOf("license", "licence", "driving", "dl", "rc", "vehicle_id"),
        "salary" to listOf("salary", "payslip", "pay_slip", "statement", "ctc"),
        "medical" to listOf("medical", "hospital", "prescription", "doctor", "clinic", "lab", "test_report", "rx", "medicine"),
        "food" to listOf("food", "dinner", "lunch", "breakfast", "cafe", "restaurant", "meal", "coffee")
    )

    /**
     * Executes a full natural-language photo search across the user's database.
     */
    fun search(
        context: Context,
        query: String,
        scope: CoroutineScope,
        onInstantResults: (List<AiSearchResultItem>) -> Unit,
        onProgress: (scanned: Int, total: Int, statusText: String) -> Unit,
        onAiMatchFound: (AiSearchResultItem) -> Unit,
        onComplete: (totalMatches: Int) -> Unit
    ): Job {
        return scope.launch(Dispatchers.IO) {
            val cleanQuery = query.trim().lowercase()
            if (cleanQuery.isBlank()) {
                withContext(Dispatchers.Main) {
                    onInstantResults(emptyList())
                    onComplete(0)
                }
                return@launch
            }

            // Step 1: Load image candidate files from Room DB
            val db = AppDatabase.getDatabase(context)
            val allFiles = try {
                db.uploadedFileDao().getAllFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching files from database", e)
                emptyList()
            }

            val imageCandidates = allFiles.filter { file ->
                val ext = file.fileName.substringAfterLast('.', "").lowercase()
                ext in IMAGE_EXTENSIONS
            }

            if (imageCandidates.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onInstantResults(emptyList())
                    onComplete(0)
                }
                return@launch
            }

            // Step 2: Instant Keyword & Cached AI Tag Matching (0ms)
            val instantMatches = mutableListOf<AiSearchResultItem>()
            val matchedKeys = mutableSetOf<String>()

            // Extract expanded search tokens
            val queryWords = cleanQuery.split(Regex("[\\s,_\\-]+")).filter { it.isNotBlank() }
            val expandedKeywords = mutableSetOf<String>()
            expandedKeywords.addAll(queryWords)
            for (w in queryWords) {
                KEYWORD_SYNONYMS[w]?.let { expandedKeywords.addAll(it) }
                for ((keyWord, synList) in KEYWORD_SYNONYMS) {
                    if (w.contains(keyWord) || keyWord.contains(w)) {
                        expandedKeywords.addAll(synList)
                    }
                }
            }

            for (file in imageCandidates) {
                val key = file.telegramFileId.ifBlank { file.path }

                // Check persistent AI tag cache
                val (isCachedMatch, cachedSummary) = AiSearchTagCache.matchesQuery(context, key, cleanQuery)
                if (isCachedMatch) {
                    val item = AiSearchResultItem(
                        file = file,
                        matchReason = cachedSummary ?: "Cached AI match",
                        isAiVerified = true,
                        badge = "✨ AI Verified"
                    )
                    instantMatches.add(item)
                    matchedKeys.add(key)
                    continue
                }

                // Check filename and path against expanded keywords
                val fileLower = file.fileName.lowercase()
                val pathLower = file.path.lowercase()
                var keywordMatched: String? = null

                for (kw in expandedKeywords) {
                    if (fileLower.contains(kw) || pathLower.contains(kw)) {
                        keywordMatched = kw
                        break
                    }
                }

                if (keywordMatched != null) {
                    val label = when {
                        cleanQuery.contains("bonafide") -> "Bonafide Certificate"
                        cleanQuery.contains("receipt") || cleanQuery.contains("payment") -> "Payment Receipt"
                        cleanQuery.contains("pan") -> "PAN Card"
                        cleanQuery.contains("party") -> "Party Photo"
                        cleanQuery.contains("marksheet") -> "Marksheet"
                        cleanQuery.contains("bill") -> "Utility Bill"
                        else -> "Matches \"$cleanQuery\""
                    }
                    val item = AiSearchResultItem(
                        file = file,
                        matchReason = label,
                        isAiVerified = false,
                        badge = "✨ Keyword Match"
                    )
                    instantMatches.add(item)
                    matchedKeys.add(key)
                }
            }

            withContext(Dispatchers.Main) {
                onInstantResults(instantMatches)
                onProgress(instantMatches.size, imageCandidates.size, "Found ${instantMatches.size} instant matches")
            }

            // Step 3: Deep Gemini Vision Multimodal Inspection for uncached photos
            val prefs = AppPrefs(context)
            val hasAiKey = prefs.hasValidGeminiKey()

            if (!hasAiKey) {
                withContext(Dispatchers.Main) {
                    onProgress(imageCandidates.size, imageCandidates.size, "Done (Instant mode). Add Gemini API Key for deep image scan.")
                    onComplete(instantMatches.size)
                }
                return@launch
            }

            // Filter remaining candidates that don't have descriptive names (e.g. IMG_..., PXL_..., WhatsApp...)
            val uncachedPhotos = imageCandidates.filter { file ->
                val key = file.telegramFileId.ifBlank { file.path }
                !matchedKeys.contains(key)
            }.take(32) // Scan up to 32 recent photos per run to prevent quota exhaustion

            if (uncachedPhotos.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onComplete(instantMatches.size)
                }
                return@launch
            }

            var scannedCount = 0
            val batchSize = 4
            val chunks = uncachedPhotos.chunked(batchSize)

            for (chunk in chunks) {
                if (!isActive) break

                val candidatesToEval = mutableListOf<AiVisionManager.BatchImageCandidate>()
                for (file in chunk) {
                    // Resolve image file (from local path or cached thumbnail)
                    val localFile = File(file.path)
                    val targetImageFile: File? = if (localFile.exists() && localFile.length() > 0) {
                        localFile
                    } else {
                        // Check if thumbnail exists
                        val thumbKey = ThumbnailManager.getThumbnailKey(file.path, file.telegramFileId, file.fileName)
                        ThumbnailManager.getDiskThumbnail(context, thumbKey)
                    }

                    if (targetImageFile != null && targetImageFile.exists()) {
                        val key = file.telegramFileId.ifBlank { file.path }
                        candidatesToEval.add(
                            AiVisionManager.BatchImageCandidate(
                                id = key,
                                imageFile = targetImageFile,
                                fileName = file.fileName
                            )
                        )
                    }
                }

                if (candidatesToEval.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onProgress(scannedCount, uncachedPhotos.size, "Scanning photos with AI Vision... (${scannedCount}/${uncachedPhotos.size})")
                    }

                    val evalResult = AiVisionManager.evaluateBatchForSearch(
                        context = context,
                        candidates = candidatesToEval,
                        searchQuery = cleanQuery
                    )

                    evalResult.onSuccess { matches ->
                        for (match in matches) {
                            if (match.matched && !matchedKeys.contains(match.candidateId)) {
                                matchedKeys.add(match.candidateId)
                                val matchedFile = chunk.find { (it.telegramFileId.ifBlank { it.path }) == match.candidateId }
                                if (matchedFile != null) {
                                    val resultItem = AiSearchResultItem(
                                        file = matchedFile,
                                        matchReason = match.reason,
                                        isAiVerified = true,
                                        badge = "✨ AI Vision Match"
                                    )
                                    withContext(Dispatchers.Main) {
                                        onAiMatchFound(resultItem)
                                    }
                                }
                            }
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "Batch evaluation failed: ${err.message}")
                    }
                }

                scannedCount += chunk.size
                // Short cooperative delay between batches to respect rate limits
                delay(300)
            }

            withContext(Dispatchers.Main) {
                onProgress(uncachedPhotos.size, uncachedPhotos.size, "Search completed")
                onComplete(matchedKeys.size)
            }
        }
    }
}
