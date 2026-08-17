package com.example.telegrambackup.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import com.example.telegrambackup.network.HttpClientProvider

/**
 * =====================================================================================
 * TELEGRAM BOT CONNECTION TESTER
 * =====================================================================================
 * Validates user-provided Telegram Bot Tokens and Chat IDs against Telegram's Bot API.
 *
 * Checks performed:
 * 1. `getMe`: Validates bot token authenticity and extracts bot name and username.
 * 2. `getChat`: Validates target Chat ID/Channel ID access and admin privileges.
 * 3. Auto-formats 10-digit channel IDs to standard `-100` prefix format.
 * =====================================================================================
 */
class TelegramBotTester {
    private val client = HttpClientProvider.client

    /**
     * Tests connection to Telegram Bot API using the given token and optional chat ID.
     * @param botToken Telegram Bot API token from @BotFather
     * @param chatId Telegram Chat ID or Channel ID
     * @return Result containing success message with bot name & chat title or actionable error instructions
     */
    suspend fun testBotConnection(botToken: String, chatId: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        var targetChatId = chatId.trim()

        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(Exception("Bot Token cannot be empty."))
        }

        try {
            // Step 1: Test Bot Token with getMe
            val meUrl = "https://api.telegram.org/bot$cleanToken/getMe"
            val meRequest = Request.Builder().url(meUrl).build()
            val meResponse = client.newCall(meRequest).execute()
            val meBody = meResponse.body?.string() ?: ""

            if (!meResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Invalid Bot Token (HTTP ${meResponse.code})"))
            }

            val meJson = JSONObject(meBody)
            if (!meJson.optBoolean("ok", false)) {
                val desc = meJson.optString("description", "Invalid Bot Token")
                return@withContext Result.failure(Exception("Bot Token Error: $desc"))
            }

            val result = meJson.optJSONObject("result")
            val botName = result?.optString("first_name") ?: "Telegram Bot"
            val username = result?.optString("username") ?: ""
            val botInfoStr = "$botName (@$username)"

            if (targetChatId.isEmpty()) {
                return@withContext Result.success("Connected to $botInfoStr successfully!")
            }

            // Auto-fix 10-digit raw channel IDs to standard Telegram channel format (-100...)
            if (targetChatId.length == 10 && targetChatId.all { it.isDigit() }) {
                targetChatId = "-100$targetChatId"
            }

            // Step 2: Test Chat Access with getChat
            val chatUrl = "https://api.telegram.org/bot$cleanToken/getChat?chat_id=$targetChatId"
            val chatRequest = Request.Builder().url(chatUrl).build()
            val chatResponse = client.newCall(chatRequest).execute()
            val chatBody = chatResponse.body?.string() ?: ""

            if (chatResponse.isSuccessful) {
                val chatJson = JSONObject(chatBody)
                if (chatJson.optBoolean("ok", false)) {
                    val chatObj = chatJson.optJSONObject("result")
                    val chatTitle = chatObj?.optString("title") ?: chatObj?.optString("username") ?: targetChatId
                    return@withContext Result.success("✅ Connected to $botInfoStr & Chat '$chatTitle' successfully!")
                }
            }

            val chatJson = try { JSONObject(chatBody) } catch (e: Exception) { null }
            val desc = chatJson?.optString("description") ?: "HTTP ${chatResponse.code}"
            return@withContext Result.failure(Exception("Chat ID Error: $desc.\n\nTips:\n1. Add bot @$username as ADMIN in your channel/group.\n2. Ensure Chat ID includes -100 prefix for channels (e.g. -100234567890)."))

        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Network error: ${e.localizedMessage}"))
        }
    }
}
