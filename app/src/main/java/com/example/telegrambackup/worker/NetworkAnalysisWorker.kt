package com.example.telegrambackup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.telegrambackup.utils.AppPrefs
import com.example.telegrambackup.utils.NetworkAnalysisManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "NetworkAnalysisWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(applicationContext)
        if (!prefs.isSelfAnalysisEnabled) {
            Log.d(TAG, "Self Analysis disabled in prefs. Skipping.")
            return@withContext Result.success()
        }

        try {
            Log.d(TAG, "Running scheduled 30-min network benchmark test...")
            val result = NetworkAnalysisManager.runDiagnosticTest(applicationContext)
            Log.d(TAG, "Benchmark completed: Ping=${result.pingMs}ms, Speed=${result.uploadSpeedKbps}Kbps, Type=${result.networkType}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "NetworkAnalysisWorker execution error: ${e.message}", e)
            Result.success()
        }
    }
}
