package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import com.example.telegrambackup.data.UploadedFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
        Preset("🪪 ID Card (Student / Govt)", "id card"),
        Preset("🏥 Medical Prescription", "medical prescription"),
        Preset("🏛️ College / Campus", "college campus"),
        Preset("🏢 Building / Society", "building society"),
        Preset("🎓 Bonafide Certificate", "bonafide certificate"),
        Preset("🧾 Payment Receipt", "payment receipt"),
        Preset("🪪 PAN Card", "pan card"),
        Preset("🎉 Party & Events", "party photos"),
        Preset("📄 10th / 12th Marksheet", "marksheet"),
        Preset("💵 ₹10k Transfer", "10k payment receipt"),
        Preset("⚡ Electricity Bill", "electricity bill"),
        Preset("🚗 Car & Travel", "car vehicle travel"),
        Preset("🍕 Food & Cafe", "food dinner restaurant")
    )

    data class Preset(val label: String, val query: String)

    fun getPresets(): List<Preset> = PRESET_QUERIES

    private val FILLER_WORDS = setOf(
        "image", "images", "photo", "photos", "pic", "pics", "picture", "pictures",
        "of", "the", "a", "an", "in", "at", "on", "for", "with", "having",
        "show", "me", "find", "search", "get", "look", "please", "my"
    )

    private val PRESERVED_SHORT_TOKENS = setOf(
        "id", "rx", "dl", "rc", "tc", "dr", "no", "om", "ai", "co", "ph", "up", "uk", "hr"
    )

    private val KEYWORD_SYNONYMS = mapOf(
        "id" to listOf("id", "identity", "id_card", "student", "employee", "aadhaar", "aadhar", "voter", "license", "licence", "dl", "badge"),
        "card" to listOf("card", "id", "identity", "pan", "aadhaar", "voter", "license", "licence", "smartcard"),
        "identity" to listOf("identity", "id", "id_card", "student", "employee", "aadhaar", "identification"),
        "student" to listOf("student", "id", "college", "school", "roll", "campus", "bonafide", "marksheet"),
        "prescription" to listOf("prescription", "rx", "medical", "doctor", "clinic", "hospital", "medicine", "pharma", "dosage", "dr"),
        "medical" to listOf("medical", "hospital", "prescription", "doctor", "clinic", "lab", "test_report", "rx", "medicine", "pharma"),
        "doctor" to listOf("doctor", "dr", "prescription", "clinic", "hospital", "medical", "rx", "health"),
        "college" to listOf("college", "university", "institute", "campus", "department", "polytechnic", "school", "engineering", "academy"),
        "university" to listOf("university", "college", "campus", "institute", "dept", "faculty"),
        "campus" to listOf("campus", "college", "university", "institute", "grounds"),
        "building" to listOf("building", "tower", "complex", "society", "apartments", "apartment", "residency", "heights", "plaza", "enclave", "architecture"),
        "society" to listOf("society", "building", "residency", "apartment", "apartments", "tower", "enclave", "heights", "chs", "complex"),
        "residency" to listOf("residency", "society", "apartment", "apartments", "building", "enclave", "tower"),
        "apartment" to listOf("apartment", "apartments", "flat", "residency", "society", "tower", "building", "complex"),
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

            // Extract core semantic tokens, stripping filler intent words like "image", "photo", "pic"
            val rawTokens = cleanQuery.split(Regex("[\\s,_\\-]+")).filter { it.isNotBlank() }
            val coreTokens = rawTokens.filter { token ->
                token !in FILLER_WORDS && (token.length > 2 || token in PRESERVED_SHORT_TOKENS || token.any { it.isDigit() })
            }
            val effectiveQuery = if (coreTokens.isNotEmpty()) coreTokens.joinToString(" ") else cleanQuery

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

            // Extract expanded search tokens using core non-filler keywords
            val queryWords = if (coreTokens.isNotEmpty()) coreTokens else rawTokens
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
                        cleanQuery.contains("id") || cleanQuery.contains("identity") -> "ID Card / Identity"
                        cleanQuery.contains("prescription") || cleanQuery.contains("medical") -> "Medical Prescription"
                        cleanQuery.contains("college") || cleanQuery.contains("university") -> "College / Campus"
                        cleanQuery.contains("building") || cleanQuery.contains("society") || cleanQuery.contains("residency") -> "Building / Society"
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

            val isDocumentQuery = cleanQuery.contains("bonafide") ||
                    cleanQuery.contains("cert") ||
                    cleanQuery.contains("receipt") ||
                    cleanQuery.contains("payment") ||
                    cleanQuery.contains("pan") ||
                    cleanQuery.contains("marksheet") ||
                    cleanQuery.contains("bill") ||
                    cleanQuery.contains("invoice") ||
                    cleanQuery.contains("aadhaar") ||
                    cleanQuery.contains("doc") ||
                    cleanQuery.contains("id") ||
                    cleanQuery.contains("card") ||
                    cleanQuery.contains("identity") ||
                    cleanQuery.contains("student") ||
                    cleanQuery.contains("prescription") ||
                    cleanQuery.contains("medical") ||
                    cleanQuery.contains("doctor") ||
                    cleanQuery.contains("license") ||
                    cleanQuery.contains("licence") ||
                    cleanQuery.contains("rx")

            val isPlaceOrBuildingQuery = cleanQuery.contains("college") ||
                    cleanQuery.contains("university") ||
                    cleanQuery.contains("campus") ||
                    cleanQuery.contains("school") ||
                    cleanQuery.contains("institute") ||
                    cleanQuery.contains("building") ||
                    cleanQuery.contains("society") ||
                    cleanQuery.contains("apartment") ||
                    cleanQuery.contains("apartments") ||
                    cleanQuery.contains("residency") ||
                    cleanQuery.contains("tower") ||
                    cleanQuery.contains("heights") ||
                    cleanQuery.contains("plaza") ||
                    cleanQuery.contains("complex") ||
                    cleanQuery.contains("enclave")

            val isEventQuery = cleanQuery.contains("party") ||
                    cleanQuery.contains("birthday") ||
                    cleanQuery.contains("bday") ||
                    cleanQuery.contains("celebrat") ||
                    cleanQuery.contains("event") ||
                    cleanQuery.contains("trip") ||
                    cleanQuery.contains("travel") ||
                    cleanQuery.contains("food")

            data class EvaluatableCandidate(
                val file: UploadedFile,
                val imageFile: File,
                val score: Int
            )

            val scannablePool = mutableListOf<EvaluatableCandidate>()
            for (file in imageCandidates) {
                val key = file.telegramFileId.ifBlank { file.path }
                if (matchedKeys.contains(key)) continue

                val localFile = File(file.path)
                val targetImageFile: File? = if (localFile.exists() && localFile.length() > 5000) {
                    localFile
                } else {
                    val thumbKey = ThumbnailManager.getThumbnailKey(file.path, file.telegramFileId, file.fileName)
                    val thumb = ThumbnailManager.getDiskThumbnail(context, thumbKey)
                    if (thumb != null && thumb.exists() && thumb.length() > 2000) thumb else null
                }

                if (targetImageFile != null) {
                    val pathLower = file.path.lowercase()
                    val nameLower = file.fileName.lowercase()
                    var score = 0

                    // Check if file name or path contains any distinct query words or synonyms
                    for (kw in queryWords) {
                        if (nameLower.contains(kw)) {
                            score += 200
                            break
                        } else if (pathLower.contains(kw)) {
                            score += 100
                            break
                        }
                    }

                    if (isDocumentQuery) {
                        if (pathLower.contains("document") || nameLower.contains("doc") ||
                            pathLower.contains("scan") || pathLower.contains("camscanner") ||
                            pathLower.contains("screenshot") || pathLower.contains("download") ||
                            pathLower.contains("receipt") || pathLower.contains("bill") ||
                            nameLower.endsWith(".pdf")
                        ) {
                            score += 100
                        } else if (pathLower.contains("dcim") || pathLower.contains("camera")) {
                            // ID cards and prescriptions are often captured via camera
                            score += 60
                        }
                    } else if (isPlaceOrBuildingQuery) {
                        if (pathLower.contains("dcim") || pathLower.contains("camera") ||
                            pathLower.contains("pictures") || pathLower.contains("photo")
                        ) {
                            score += 120
                        }
                    } else if (isEventQuery) {
                        if (pathLower.contains("dcim") || pathLower.contains("camera") ||
                            pathLower.contains("pictures") || pathLower.contains("party") ||
                            pathLower.contains("bday") || nameLower.contains("img")
                        ) {
                            score += 100
                        }
                    }

                    scannablePool.add(EvaluatableCandidate(file, targetImageFile, score))
                }
            }

            // Step 2.5: Fast On-Device Google ML Kit OCR Scan for documents, certificates, bills, ID cards & signboards
            if (isDocumentQuery || isPlaceOrBuildingQuery || scannablePool.isNotEmpty()) {
                val ocrCandidates = scannablePool
                    .sortedWith(compareByDescending<EvaluatableCandidate> { it.score }.thenByDescending { it.file.timestamp })
                    .take(60)

                var ocrChecked = 0
                for (cand in ocrCandidates) {
                    if (!isActive) break
                    val key = cand.file.telegramFileId.ifBlank { cand.file.path }
                    if (matchedKeys.contains(key)) continue

                    try {
                        val text = OnDeviceOcrHelper.extractText(cand.imageFile)
                        if (text.isNotBlank()) {
                            // First try with effective stripped query, then full query
                            var (isMatch, reason) = OnDeviceOcrHelper.evaluateOcrMatch(effectiveQuery, text)
                            if (!isMatch && effectiveQuery != cleanQuery) {
                                val fallback = OnDeviceOcrHelper.evaluateOcrMatch(cleanQuery, text)
                                isMatch = fallback.first
                                reason = fallback.second
                            }

                            if (isMatch) {
                                matchedKeys.add(key)
                                AiSearchTagCache.putTagEntry(context, key, reason, listOf(cleanQuery, effectiveQuery, reason))
                                val resultItem = AiSearchResultItem(
                                    file = cand.file,
                                    matchReason = reason,
                                    isAiVerified = true,
                                    badge = "⚡ On-Device OCR Match"
                                )
                                withContext(Dispatchers.Main) {
                                    onAiMatchFound(resultItem)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR scan error on ${cand.file.fileName}", e)
                    }

                    ocrChecked++
                    if (ocrChecked % 3 == 0) {
                        withContext(Dispatchers.Main) {
                            onProgress(ocrChecked, ocrCandidates.size, "Scanning on-device with Google OCR... ($ocrChecked/${ocrCandidates.size})")
                        }
                    }
                }
            }

            // Step 3: Deep Gemini Vision Multimodal Inspection for remaining uncached photos
            val prefs = AppPrefs(context)
            val hasAiKey = prefs.hasValidGeminiKey()

            if (!hasAiKey) {
                withContext(Dispatchers.Main) {
                    onProgress(imageCandidates.size, imageCandidates.size, "Scan completed with On-Device OCR.")
                    onComplete(matchedKeys.size)
                }
                return@launch
            }

            // Filter out items already matched by OCR
            val remainingCandidates = scannablePool
                .filter { cand ->
                    val key = cand.file.telegramFileId.ifBlank { cand.file.path }
                    !matchedKeys.contains(key)
                }
                .sortedWith(compareByDescending<EvaluatableCandidate> { it.score }.thenByDescending { it.file.timestamp })
                .take(24) // Evaluate remaining photos with Gemini Vision

            if (remainingCandidates.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onComplete(matchedKeys.size)
                }
                return@launch
            }

            val prioritizedCandidates = remainingCandidates

            val scannedCounter = AtomicInteger(0)
            val batchSize = 4
            val chunks = prioritizedCandidates.chunked(batchSize)
            val semaphore = Semaphore(2) // Run 2 batches in parallel for high speed

            coroutineScope {
                chunks.map { chunk ->
                    async {
                        if (!isActive) return@async
                        semaphore.withPermit {
                            if (!isActive) return@withPermit

                            val candidatesToEval = chunk.map { c ->
                                val key = c.file.telegramFileId.ifBlank { c.file.path }
                                AiVisionManager.BatchImageCandidate(
                                    id = key,
                                    imageFile = c.imageFile,
                                    fileName = c.file.fileName
                                )
                            }

                            val currentScanned = scannedCounter.get()
                            withContext(Dispatchers.Main) {
                                onProgress(currentScanned, prioritizedCandidates.size, "Scanning photos with AI Vision... ($currentScanned/${prioritizedCandidates.size})")
                            }

                            val evalResult = AiVisionManager.evaluateBatchForSearch(
                                context = context,
                                candidates = candidatesToEval,
                                searchQuery = effectiveQuery.ifBlank { cleanQuery }
                            )

                            evalResult.onSuccess { matches ->
                                for (match in matches) {
                                    if (match.matched && !matchedKeys.contains(match.candidateId)) {
                                        matchedKeys.add(match.candidateId)
                                        val matchedCand = chunk.find { (it.file.telegramFileId.ifBlank { it.file.path }) == match.candidateId }
                                        if (matchedCand != null) {
                                            val resultItem = AiSearchResultItem(
                                                file = matchedCand.file,
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

                            val updated = scannedCounter.addAndGet(chunk.size)
                            withContext(Dispatchers.Main) {
                                onProgress(updated, prioritizedCandidates.size, "Scanning photos with AI Vision... ($updated/${prioritizedCandidates.size})")
                            }
                        }
                    }
                }.awaitAll()
            }

            withContext(Dispatchers.Main) {
                onProgress(prioritizedCandidates.size, prioritizedCandidates.size, "Search completed")
                onComplete(matchedKeys.size)
            }
        }
    }
}
