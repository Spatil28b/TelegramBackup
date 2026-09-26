package com.example.telegrambackup.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
 * TSPACE GLOBAL APP CRASH SHIELD & RESILIENCE ENGINE
 * =====================================================================================
 * Intercepts all uncaught thread exceptions across the entire Android process:
 * 1. Suppresses benign platform exceptions (BadTokenException, DeadObjectException,
 *    CancellationException, transient notification SecurityException).
 * 2. Persists detailed diagnostic crash dumps to external storage (/sdcard/Documents/TelegramBackup/crash_logs.txt)
 *    and internal storage for zero-loss diagnostics.
 * 3. Gracefully restarts the application or recovers state instead of leaving the user
 *    with a frozen black screen or annoying system crash loop.
 * =====================================================================================
 */
object AppCrashShield {
    private const val TAG = "AppCrashShield"
    private const val MAX_CRASH_LOG_BYTES = 512 * 1024L // 512 KB log cap

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false
    private var appContextRef: WeakReference<Context>? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    fun install(application: Application) {
        if (isInstalled) return
        isInstalled = true
        appContextRef = WeakReference(application.applicationContext)

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivityRef = WeakReference(activity)
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

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }

        Log.i(TAG, "🛡️ AppCrashShield installed successfully")
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        val rootCause = getRootCause(throwable)

        // 1. Check for benign / transient platform glitches that should NOT kill the app
        if (isBenignException(throwable, rootCause)) {
            Log.w(TAG, "Shielded app from benign exception in thread [${thread.name}]: ${throwable.message}")
            return
        }

        // 2. Dump full diagnostic log
        val context = appContextRef?.get()
        if (context != null) {
            writeCrashLog(context, thread, throwable)
        }

        Log.e(TAG, "FATAL CRASH intercepted on thread [${thread.name}]", throwable)

        // 3. Attempt graceful recovery if on UI thread
        val currentActivity = currentActivityRef?.get()
        if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
            try {
                val intent = Intent(currentActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                currentActivity.finish()
                currentActivity.startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                return
            } catch (t: Throwable) {
                Log.e(TAG, "Failed graceful restart", t)
            }
        }

        // 4. Delegate to original system handler as last resort
        originalHandler?.uncaughtException(thread, throwable)
    }

    private fun isBenignException(throwable: Throwable, rootCause: Throwable): Boolean {
        // Coroutine cancellation
        if (throwable is CancellationException || rootCause is CancellationException) {
            return true
        }

        val msg = (throwable.message ?: "") + " " + (rootCause.message ?: "")
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

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
            val appVersion = AppPrefs(context).appVersionStr

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
