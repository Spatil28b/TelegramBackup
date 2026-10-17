package com.example.telegrambackup.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent on-device disk cache for AI-detected image tags and document classifications.
 * Saves Gemini Vision analysis results so repeating a search for "pan card", "receipt",
 * or "bonafide certificate" returns instantly with zero network or battery overhead.
 */
object AiSearchTagCache {
    private const val TAG = "AiSearchTagCache"
    private const val FILE_NAME = "ai_search_tags_v1.json"

    data class TagEntry(
        val key: String,
        val tags: List<String>,
        val summary: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val inMemoryMap = ConcurrentHashMap<String, TagEntry>()
    private var isLoaded = false

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists() && file.length() > 0) {
                val jsonStr = file.readText(Charsets.UTF_8)
                val root = JSONObject(jsonStr)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = root.optJSONObject(k) ?: continue
                    val summary = obj.optString("summary", "")
                    val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
                    val tagsList = mutableListOf<String>()
                    for (i in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.optString(i))
                    }
                    val ts = obj.optLong("ts", System.currentTimeMillis())
                    inMemoryMap[k] = TagEntry(k, tagsList, summary, ts)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI search tag cache", e)
        } finally {
            isLoaded = true
        }
    }

    @Synchronized
    private fun persist(context: Context) {
        try {
            val root = JSONObject()
            inMemoryMap.forEach { (k, entry) ->
                val obj = JSONObject()
                obj.put("summary", entry.summary)
                obj.put("ts", entry.timestamp)
                val arr = JSONArray()
                entry.tags.forEach { arr.put(it) }
                obj.put("tags", arr)
                root.put(k, obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting AI search tag cache", e)
        }
    }

    private val persistScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var persistJob: kotlinx.coroutines.Job? = null

    fun getTagEntry(context: Context, key: String): TagEntry? {
        ensureLoaded(context)
        return inMemoryMap[key]
    }

    fun putTagEntry(context: Context, key: String, summary: String, tags: List<String>) {
        ensureLoaded(context)
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) return
        val current = inMemoryMap[cleanKey]
        val mergedTags = (current?.tags.orEmpty() + tags).distinct()
        inMemoryMap[cleanKey] = TagEntry(cleanKey, mergedTags, summary.ifBlank { current?.summary.orEmpty() })
        
        // Debounce disk serialization by 500ms so rapid OCR scans don't stutter flash I/O
        persistJob?.cancel()
        persistJob = persistScope.launch {
            kotlinx.coroutines.delay(500)
            persist(context)
        }
    }

    fun flush(context: Context) {
        persistJob?.cancel()
        persist(context)
    }

    fun matchesQuery(context: Context, key: String, query: String): Pair<Boolean, String?> {
        ensureLoaded(context)
        val entry = inMemoryMap[key] ?: return Pair(false, null)
        val q = query.lowercase().trim()
        if (q.isBlank()) return Pair(false, null)

        for (tag in entry.tags) {
            if (tag.lowercase().contains(q) || q.contains(tag.lowercase())) {
                return Pair(true, entry.summary.ifBlank { tag })
            }
        }
        if (entry.summary.lowercase().contains(q)) {
            return Pair(true, entry.summary)
        }
        return Pair(false, null)
    }

    fun clear(context: Context) {
        inMemoryMap.clear()
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }
}
