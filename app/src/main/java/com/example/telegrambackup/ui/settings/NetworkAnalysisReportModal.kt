package com.example.telegrambackup.ui.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.telegrambackup.theme.TelegramBlue
import com.example.telegrambackup.theme.TelegramEmerald
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.NetworkAnalysisAdvisor
import com.example.telegrambackup.utils.NetworkAnalysisManager
import com.example.telegrambackup.utils.NetworkHealthReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun NetworkAnalysisReportModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()

    var isAnalysisRunning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<NetworkHealthReport?>(null) }
    var selfAnalysisEnabled by remember { mutableStateOf(prefs.isSelfAnalysisEnabled) }

    fun refreshReport() {
        scope.launch {
            val rep = NetworkAnalysisAdvisor.generateReport(context)
            report = rep
        }
    }

    LaunchedEffect(Unit) {
        refreshReport()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Insights, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("7-Day Self-Analysis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Smart Network Benchmark & AI Advisor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val currentReport = report
                if (currentReport == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TelegramBlue)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. HEALTH SCORE CARD
                        val scoreGradient = when {
                            currentReport.healthScore >= 80 -> Brush.horizontalGradient(listOf(TelegramEmerald, Color(0xFF10B981)))
                            currentReport.healthScore >= 60 -> Brush.horizontalGradient(listOf(TelegramBlue, Color(0xFF0284C7)))
                            else -> Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
                        }

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "NETWORK HEALTH SCORE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${currentReport.healthScore}/100 • ${currentReport.healthRating}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = if (currentReport.healthScore >= 80) TelegramEmerald else TelegramBlue)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        if (currentReport.totalTestsRun > 0) "Based on ${currentReport.totalTestsRun} benchmark tests in last 7 days" else "Continuous background tests running every 30m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(scoreGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                        }

                        // 2. AI ADVISOR RECOMMENDATION CARD
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = TelegramBlue.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI Smart Recommendation", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TelegramBlue))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    currentReport.recommendationSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Window: ${currentReport.bestBackupWindow}", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("${currentReport.recommendedThreads} Threads (${currentReport.recommendedBotFleetMode})", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        NetworkAnalysisAdvisor.applySmartRecommendations(context, currentReport)
                                        Toast.makeText(context, "⚡ Applied Optimal Backup Settings!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Optimal Settings Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. 24-HOUR SPEED DISTRIBUTION CHART
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Hourly Upload Speed Profile", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Peak performance hours highlighted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val maxSpeed = currentReport.hourlyAverages.values.maxOrNull()?.coerceAtLeast(100.0) ?: 1000.0
                                    for (h in 0..23 step 2) {
                                        val avg = currentReport.hourlyAverages[h] ?: 0.0
                                        val barHeightFraction = (avg / maxSpeed).toFloat().coerceIn(0.12f, 1f)
                                        val isPeak = h >= currentReport.bestStartHour && h < (currentReport.bestStartHour + 4) % 24

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.6f)
                                                    .fillMaxHeight(barHeightFraction)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(if (isPeak) TelegramEmerald else TelegramBlue.copy(alpha = 0.6f))
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "${h}h",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = if (isPeak) TelegramEmerald else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. METRICS STATS GRID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricStatCard(
                                title = "Avg Speed",
                                value = String.format(Locale.US, "%.1f MB/s", currentReport.averageSpeedKbps / 1024.0),
                                icon = Icons.Default.CloudUpload,
                                tint = TelegramBlue,
                                modifier = Modifier.weight(1f)
                            )
                            MetricStatCard(
                                title = "Peak Speed",
                                value = String.format(Locale.US, "%.1f MB/s", currentReport.peakSpeedKbps / 1024.0),
                                icon = Icons.Default.Speed,
                                tint = TelegramEmerald,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricStatCard(
                                title = "Avg Latency",
                                value = "${currentReport.averagePingMs} ms",
                                icon = Icons.Default.NetworkPing,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f)
                            )
                            MetricStatCard(
                                title = "Wi-Fi Stability",
                                value = "${currentReport.wifiReliabilityPercent}%",
                                icon = Icons.Default.Wifi,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 5. CONTROLS & MANUAL BENCHMARK BUTTON
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("7-Day Background Benchmarking", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Runs tests every 30m to maintain optimal advice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Switch(
                                        checked = selfAnalysisEnabled,
                                        onCheckedChange = { isChecked ->
                                            selfAnalysisEnabled = isChecked
                                            prefs.isSelfAnalysisEnabled = isChecked
                                            NetworkAnalysisManager.syncSelfAnalysisWorker(context)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = {
                                        if (!isAnalysisRunning) {
                                            isAnalysisRunning = true
                                            scope.launch {
                                                Toast.makeText(context, "Running live benchmark probe...", Toast.LENGTH_SHORT).show()
                                                val log = NetworkAnalysisManager.runDiagnosticTest(context)
                                                refreshReport()
                                                isAnalysisRunning = false
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Test Done: ${String.format(Locale.US, "%.1f", log.uploadSpeedKbps / 1024.0)} MB/s (${log.pingMs}ms)", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isAnalysisRunning
                                ) {
                                    if (isAnalysisRunning) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TelegramBlue)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Probing Network Speed & Latency...")
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Run Benchmark Test Now")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}
