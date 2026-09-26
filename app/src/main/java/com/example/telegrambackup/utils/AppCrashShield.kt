package com.example.telegrambackup.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.telegrambackup.MainActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * =====================================================================================
 * TSPACE GLOBAL APP CRASH SHIELD & RESILIENCE ENGINE (V2 - BLACK SCREEN IMMUNE)
 * =====================================================================================
 * 1. Looper-Level Main Thread Shield: Catches benign OEM and layout exceptions inside
 *    the message loop so the Looper never dies and the UI never freezes on a black screen.
 * 2. Background Thread Isolation: Uncaught exceptions in background coroutines, WorkManager,
 *    or OkHttp are logged and absorbed without restarting the activity or killing the UI.
 * 3. Infinite Restart Loop Protection: Detects crash loops (< 10s between crashes). If a
 *    crash loop occurs, suppresses restarts so Android never gets trapped in a black screen.
 * 4. Diagnostics: Persists crash dumps to internal filesDir and Documents for inspection.
 * =====================================================================================
 */
object AppCrashShield {
    private const val TAG = "AppCrashShield"
    private const val MAX_CRASH_LOG_BYTES = 512 * 1024L // 512 KB log cap
    private const val LOOP_THRESHOLD_MS = 10000L

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false
    private var appContextRef: WeakReference<Context>? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    private var lastRestartTimestamp = 0L
    private var consecutiveRestartCount = 0
    private var activityCreationTimeMs = 0L

    fun install(application: Application) {
        if (isInstalled) return
        isInstalled = true
        appContextRef = WeakReference(application.applicationContext)

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivityRef = WeakReference(activity)
                activityCreationTimeMs = System.currentTimeMillis()
            }
            override fun onActivityStarted(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() === activity) {
                    currentActivityRef = null
                }
            }
        })

        // 1. Install Main Looper Protection (keeps UI alive through benign crashes)
        try {
            Handler(Looper.getMainLooper()).post {
                while (true) {
                    try {
                        Looper.loop()
                    } catch (t: Throwable) {
                        handleMainLooperException(t)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed installing looper protector: ${e.message}")
        }

        // 2. Global Uncaught Exception Handler
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }

        Log.i(TAG, "🛡️ AppCrashShield v2 installed with anti-restart loop protection")
    }

    private fun handleMainLooperException(throwable: Throwable) {
        val rootCause = getRootCause(throwable)

        if (isBenignException(throwable, rootCause)) {
            Log.w(TAG, "Main looper caught benign exception: ${throwable.message}")
            return
        }

        val context = appContextRef?.get()
        if (context != null) {
            writeCrashLog(context, Looper.getMainLooper().thread, throwable)
        }

        Log.e(TAG, "Main looper FATAL exception", throwable)

        // Attempt graceful activity recovery with loop protection
        attemptSafeActivityRecovery()
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        val rootCause = getRootCause(throwable)

        // 1. Check for benign / transient platform glitches
        if (isBenignException(throwable, rootCause)) {
            Log.w(TAG, "Shielded app from benign exception in thread [${thread.name}]: ${throwable.message}")
            return
        }

        // 2. Dump diagnostic log
        val context = appContextRef?.get()
        if (context != null) {
            writeCrashLog(context, thread, throwable)
        }

        Log.e(TAG, "Uncaught exception on thread [${thread.name}]", throwable)

        // 3. BACKGROUND THREADS: Do NOT kill or restart MainActivity!
        val isMainThread = thread === Looper.getMainLooper().thread
        if (!isMainThread) {
            Log.w(TAG, "Absorbed background thread crash [${thread.name}] to protect UI state.")
            return
        }

        // 4. MAIN THREAD FATAL: Attempt safe recovery with anti-loop guard
        attemptSafeActivityRecovery()

        // 5. Delegate to original handler if recovery was suppressed
        originalHandler?.uncaughtException(thread, throwable)
    }

    private fun attemptSafeActivityRecovery() {
        val now = System.currentTimeMillis()
        if (now - lastRestartTimestamp < LOOP_THRESHOLD_MS) {
            consecutiveRestartCount++
        } else {
            consecutiveRestartCount = 1
        }
        lastRestartTimestamp = now

        // Prevent infinite restart loops (which cause black screens)
        if (consecutiveRestartCount > 1) {
            Log.e(TAG, "Crash loop detected ($consecutiveRestartCount crashes). Suppressing restart to prevent black screen.")
            return
        }

        // If the crash happened within 2.5 seconds of startup, restarting will just crash again
        if (now - activityCreationTimeMs < 2500L) {
            Log.e(TAG, "Startup crash detected (< 2.5s). Suppressing restart to avoid black screen.")
            return
        }

        val currentActivity = currentActivityRef?.get()
        if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
            try {
                val intent = Intent(currentActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                currentActivity.finish()
                currentActivity.startActivity(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed graceful restart", t)
            }
        }
    }

    private fun isBenignException(throwable: Throwable, rootCause: Throwable): Boolean {
        // Coroutine cancellation
        if (throwable is CancellationException || rootCause is CancellationException) {
            return true
        }

        val msg = (throwable.message ?: "") + " " + (rootCause.message ?: "")

        // WindowManager BadTokenException (activity finished while popup/dialog was pending)
        if (throwable is android.view.WindowManager.BadTokenException || rootCause is android.view.WindowManager.BadTokenException) {
            return true
        }

        // DeadObjectException / DeadSystemException
        if (throwable is android.os.DeadObjectException || rootCause is android.os.DeadObjectException) {
            return true
        }

        // Android 14 ForegroundServiceStartNotAllowedException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (throwable.javaClass.name.contains("ForegroundServiceStartNotAllowedException") ||
                rootCause.javaClass.name.contains("ForegroundServiceStartNotAllowedException")
            ) {
                return true
            }
        }

        // Notification permission SecurityException
        if (throwable is SecurityException || rootCause is SecurityException) {
            if (msg.contains("POST_NOTIFICATIONS", ignoreCase = true) ||
                msg.contains("NotificationManager", ignoreCase = true) ||
                msg.contains("startForeground", ignoreCase = true)
            ) {
                return true
            }
        }

        // Canvas trying to use recycled bitmap
        if (msg.contains("recycled bitmap", ignoreCase = true)) {
            return true
        }

        // OutOfMemoryError in media/bitmap/canvas rendering - invoke GC and prevent fatal process termination
        if (throwable is OutOfMemoryError || rootCause is OutOfMemoryError) {
            Log.w(TAG, "Absorbed OutOfMemoryError in media/bitmap pipeline. Triggering garbage collection.")
            try {
                System.gc()
            } catch (_: Throwable) {}
            return true
        }

        return false
    }

    private fun getRootCause(throwable: Throwable): Throwable {
        var cause: Throwable = throwable
        while (cause.cause != null && cause.cause !== cause) {
            cause = cause.cause!!
        }
        return cause
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val appVersion = try { AppPrefs(context).appVersionStr } catch (_: Throwable) { "unknown" }

            val report = buildString {
                appendLine("================================================================================")
                appendLine("TSPACE CRASH REPORT - $timestamp")
                appendLine("App Version: $appVersion")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Failing Thread: ${thread.name} (ID: ${thread.id}, Priority: ${thread.priority})")
                appendLine("Exception Type: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("---------------------------------- STACK TRACE ----------------------------------")
                appendLine(stackTrace)
                appendLine("================================================================================")
                appendLine()
            }

            // 1. Write to internal storage
            try {
                val internalFile = File(context.filesDir, "crash_log.txt")
                if (internalFile.exists() && internalFile.length() > MAX_CRASH_LOG_BYTES) {
                    internalFile.delete()
                }
                internalFile.appendText(report)
            } catch (_: Throwable) {}

            // 2. Write to permanent Documents folder if storage accessible
            try {
                val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "TelegramBackup")
                if (!docsDir.exists()) docsDir.mkdirs()
                val externalFile = File(docsDir, "crash_logs.txt")
                if (externalFile.exists() && externalFile.length() > MAX_CRASH_LOG_BYTES) {
                    externalFile.delete()
                }
                externalFile.appendText(report)
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }
}
