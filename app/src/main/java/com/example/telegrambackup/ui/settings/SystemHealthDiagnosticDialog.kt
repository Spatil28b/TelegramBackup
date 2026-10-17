package com.example.telegrambackup.ui.settings

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.network.ServerApi
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DiagnosticItemResult(
    val title: String,
    val detail: String,
    val isSuccess: Boolean,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthDiagnosticDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }

    var isRunning by remember { mutableStateOf(true) }

    var botTokenResult by remember { mutableStateOf(DiagnosticItemResult("Telegram Bot Token", "Checking...", false, true)) }
    var chatIdResult by remember { mutableStateOf(DiagnosticItemResult("Telegram Chat Access", "Checking...", false, true)) }
    var backendLatencyResult by remember { mutableStateOf(DiagnosticItemResult("Vercel Backend Server", "Checking...", false, true)) }
    var postgresDbResult by remember { mutableStateOf(DiagnosticItemResult("Neon PostgreSQL Database", "Checking...", false, true)) }
    var batteryPermResult by remember { mutableStateOf(DiagnosticItemResult("Background Battery Optimization", "Checking...", false, true)) }

    fun runDiagnostics() {
        isRunning = true
        botTokenResult = DiagnosticItemResult("Telegram Bot Token", "Checking Telegram Bot API...", false, true)
        chatIdResult = DiagnosticItemResult("Telegram Chat Access", "Validating Chat ID...", false, true)
        backendLatencyResult = DiagnosticItemResult("Vercel Backend Server", "Measuring Latency...", false, true)
        postgresDbResult = DiagnosticItemResult("Neon PostgreSQL Database", "Testing DB Connection...", false, true)
        batteryPermResult = DiagnosticItemResult("Background Battery Optimization", "Checking permissions...", false, true)

        scope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
            val token = prefs.botToken.trim()
            val chat = prefs.chatId.trim()
            val serverUrl = prefs.serverUrl.trim()
            val email = prefs.userEmail.trim()

            // 1. Test Bot Token
            if (token.isEmpty()) {
                botTokenResult = DiagnosticItemResult("Telegram Bot Token", "❌ No Bot Token Configured in Settings", false, false)
            } else {
                try {
                    val getMeUrl = com.example.telegrambackup.utils.TelegramApiConfig.getMethodUrl(context, token, "getMe")
                    val req = Request.Builder().url(getMeUrl).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: ""
                    if (res.isSuccessful && body.contains("\"ok\":true")) {
                        val botName = JSONObject(body).optJSONObject("result")?.optString("first_name", "Bot") ?: "Bot"
                        botTokenResult = DiagnosticItemResult("Telegram Bot Token", "✅ Verified Bot: $botName", true, false)
                    } else {
                        botTokenResult = DiagnosticItemResult("Telegram Bot Token", "❌ Invalid Bot Token API Key", false, false)
                    }
                } catch (e: Exception) {
                    botTokenResult = DiagnosticItemResult("Telegram Bot Token", "❌ Connection Error: ${e.localizedMessage}", false, false)
                }
            }

            // 2. Test Chat ID
            if (chat.isEmpty() || token.isEmpty()) {
                chatIdResult = DiagnosticItemResult("Telegram Chat Access", "❌ No Chat ID Configured", false, false)
            } else {
                try {
                    val url = com.example.telegrambackup.utils.TelegramApiConfig.getMethodUrl(context, token, "getChat?chat_id=$chat")
                    val req = Request.Builder().url(url).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: ""
                    if (res.isSuccessful && body.contains("\"ok\":true")) {
                        val title = JSONObject(body).optJSONObject("result")?.optString("title", "Private Chat") ?: "Private Chat"
                        chatIdResult = DiagnosticItemResult("Telegram Chat Access", "✅ Chat Accessible: $title", true, false)
                    } else {
                        chatIdResult = DiagnosticItemResult("Telegram Chat Access", "❌ Chat Access Denied (Check Bot Admin)", false, false)
                    }
                } catch (e: Exception) {
                    chatIdResult = DiagnosticItemResult("Telegram Chat Access", "❌ Chat Error: ${e.localizedMessage}", false, false)
                }
            }

            // 3. Test Vercel Backend Latency
            val startTime = System.currentTimeMillis()
            try {
                val req = Request.Builder().url(serverUrl).build()
                val res = client.newCall(req).execute()
                val elapsed = System.currentTimeMillis() - startTime
                if (res.isSuccessful) {
                    backendLatencyResult = DiagnosticItemResult("Vercel Backend Server", "✅ Online ($elapsed ms latency)", true, false)
                } else {
                    backendLatencyResult = DiagnosticItemResult("Vercel Backend Server", "⚠️ HTTP ${res.code} ($elapsed ms)", false, false)
                }
            } catch (e: Exception) {
                backendLatencyResult = DiagnosticItemResult("Vercel Backend Server", "❌ Backend Offline: ${e.localizedMessage}", false, false)
            }

            // 4. Test Neon PostgreSQL DB
            if (email.isEmpty()) {
                postgresDbResult = DiagnosticItemResult("Neon PostgreSQL Database", "⚠️ No User Email Configured", false, false)
            } else {
                try {
                    val settings = ServerApi().getUserSettings(serverUrl, email)
                    postgresDbResult = DiagnosticItemResult("Neon PostgreSQL Database", "✅ PostgreSQL Connected & Synced", true, false)
                } catch (e: Exception) {
                    postgresDbResult = DiagnosticItemResult("Neon PostgreSQL Database", "❌ DB Error: ${e.localizedMessage}", false, false)
                }
            }

            // 5. Test Battery Optimization
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true

            batteryPermResult = if (isIgnoring) {
                DiagnosticItemResult("Background Execution", "✅ Unrestricted Battery Optimization", true, false)
            } else {
                DiagnosticItemResult("Background Execution", "⚠️ Optimized (May limit background sync)", false, false)
            }

            withContext(Dispatchers.Main) { isRunning = false }
        }
    }

    LaunchedEffect(Unit) {
        runDiagnostics()
    }

    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = TelegramEmerald, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "System Diagnostics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Real-time system health and network diagnostic tests:",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                listOf(botTokenResult, chatIdResult, backendLatencyResult, postgresDbResult, batteryPermResult).forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TelegramBlue, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = if (item.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (item.isSuccess) TelegramEmerald else Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    item.detail,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!isRunning) runDiagnostics() else onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Re-Run", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text("Close", color = MaterialTheme.colorScheme.outline, maxLines = 1, softWrap = false)
            }
        }
    )
}
