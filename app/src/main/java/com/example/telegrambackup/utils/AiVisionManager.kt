package com.example.telegrambackup.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.telegrambackup.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * =====================================================================================
 * AI & MULTIMODAL VISION MANAGER (GEMINI FLASH)
 * =====================================================================================
 * High-performance, low-device-load vision intelligence system:
 * - Multi-Key Rotation & Auto-Failover on HTTP 429 / Quota limits.
 * - Memory-safe downsampled image preparation (max 1024px, zero battery/CPU overheating).
 * - "Ask Photo" question answering for marksheets, payment receipts, bills, and IDs.
 * =====================================================================================
 */
object AiVisionManager {
    private const val TAG = "AiVisionManager"
    private val currentKeyIndex = AtomicInteger(0)

    val PRESET_PROMPTS = listOf(
        PresetPrompt("📄 Extract All Text", "Extract all visible text from this image or document verbatim. Maintain original layout and hierarchy."),
        PresetPrompt("🎓 10th / 12th Marksheet", "Analyze this educational marksheet or certificate. Extract: Board/University, Candidate Name, Roll Number, Exam Year, Subject-wise Marks/Grades, and Final Result/Percentage with clear bullet points."),
        PresetPrompt("🧾 10k / Payment Receipt", "Extract all financial transaction details from this payment receipt, invoice, or screenshot: Merchant/Recipient Name, Transaction ID / UTR, Date & Time, Payment Mode (UPI/Bank/Card), and Total Amount with currency symbol."),
        PresetPrompt("🪪 ID & Govt Document", "Extract personal identification information from this ID card (Aadhaar, PAN, Passport, Driving License, Student ID): Document Name, Card/ID Number, Full Name, Date of Birth, Father's/Spouse Name, and Address if present."),
        PresetPrompt("📋 Document Summary", "Provide a concise summary of this document, explaining its purpose, key participants, dates, and important terms or clauses.")
    )

    data class PresetPrompt(val title: String, val prompt: String)

    /**
     * Verifies whether an API key is functional by calling the Gemini text endpoint.
     */
    suspend fun testApiKey(apiKey: String, model: String = "gemini-1.5-flash"): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${apiKey.trim()}"
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", "Hello, please confirm in one sentence that this API connection is working.") })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(endpoint).post(requestBody).build()

            val response = HttpClientProvider.client.newCall(request).execute()
            val responseText = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseText)
                    errJson.optJSONObject("error")?.optString("message", "HTTP ${response.code}") ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception("Connection failed ($errorMsg)"))
            }

            val respJson = JSONObject(responseText)
            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val replyText = parts?.optJSONObject(0)?.optString("text", "Connection Successful!").orEmpty()

            Result.success(replyText.ifBlank { "Connection Successful!" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prepares memory-efficient downsampled JPEG base64 string from an image file.
     * Keeps max dimension <= 1024px to prevent phone memory spikes or thermal load.
     */
    private fun prepareOptimizedImageBase64(imageFile: File): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            var inSampleSize = 1
            val maxDimension = 1024
            if (width > maxDimension || height > maxDimension) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while ((halfWidth / inSampleSize) >= maxDimension || (halfHeight / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Consumes 50% less RAM than ARGB_8888
            }

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            bitmap.recycle() // Immediately free native memory buffer

            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize image for AI vision", e)
            null
        }
    }

    /**
     * Executes Multimodal Vision Question Answering using configured Gemini API keys.
     * Automatically fails over to the next API key if a key encounters HTTP 429 or quota limit.
     */
    suspend fun askImageQuestion(
        context: Context,
        imageFile: File,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val keyList = prefs.getGeminiApiKeyList()

        if (keyList.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API Key found. Please add your free Google Gemini API Key in Settings > AI & Vision Intelligence.")
            )
        }

        if (!imageFile.exists() || imageFile.length() <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Image file does not exist or is empty."))
        }

        val base64Image = prepareOptimizedImageBase64(imageFile)
            ?: return@withContext Result.failure(Exception("Could not process image for AI vision."))

        val modelName = prefs.activeAiModel.ifBlank { "gemini-1.5-flash" }
        var lastError: Exception? = null

        // Try available keys starting from current index
        val totalKeys = keyList.size
        for (attempt in 0 until totalKeys) {
            val keyIndex = (currentKeyIndex.getAndIncrement() and 0x7FFFFFFF) % totalKeys
            val apiKey = keyList[keyIndex]

            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

                val requestJson = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val parts = JSONArray().apply {
                                // Text prompt
                                put(JSONObject().apply {
                                    put("text", prompt.trim())
                                })
                                // Inline Image Data
                                put(JSONObject().apply {
                                    val inlineData = JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    }
                                    put("inline_data", inlineData)
                                })
                            }
                            put("parts", parts)
                        }
                        put(contentObj)
                    }
                    put("contents", contents)

                    val genConfig = JSONObject().apply {
                        put("temperature", 0.2)
                        put("maxOutputTokens", 2048)
                    }
                    put("generationConfig", genConfig)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                val response = HttpClientProvider.client.newCall(request).execute()
                val responseText = response.body?.string() ?: ""

                if (response.code == 429 || response.code == 403) {
                    Log.w(TAG, "Key #${keyIndex + 1} hit rate limit / quota (${response.code}). Failing over to next key...")
                    lastError = Exception("API Key #${keyIndex + 1} reached quota/rate limit (${response.code}).")
                    continue
                }

                if (!response.isSuccessful) {
                    val parsedMsg = try {
                        JSONObject(responseText).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    lastError = Exception("AI Error: $parsedMsg")
                    continue
                }

                val respJson = JSONObject(responseText)
                val candidates = respJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                val answer = parts?.optJSONObject(0)?.optString("text")

                if (!answer.isNullOrBlank()) {
                    return@withContext Result.success(answer.trim())
                } else {
                    lastError = Exception("No response generated by AI model.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request with key #${keyIndex + 1} encountered network error: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Failed to query AI Vision across all configured keys."))
    }
}
