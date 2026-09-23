package com.example.telegrambackup.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.telegrambackup.BuildConfig

/**
 * Centralized, reliable provider for application version information.
 * Eliminates out-of-sync hardcoded strings and PackageManager lookup failures.
 */
object AppVersionHelper {

    /**
     * Compile-time constant version name (e.g. "2.2.2").
     * Never null, never throws, and never out of sync with build.gradle.kts.
     */
    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    /**
     * Compile-time constant version code (e.g. 105).
     * Never 0, never throws, and never out of sync with build.gradle.kts.
     */
    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    /**
     * Retrieves the installed package version name and code safely.
     * Uses Android 13+ (API 33+) PackageInfoFlags when available.
     * Guaranteed to fall back to BuildConfig values if PackageManager query fails.
     */
    fun getInstalledVersion(context: Context): Pair<String, Int> {
        return try {
            val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            val name = pkg.versionName ?: BuildConfig.VERSION_NAME
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode
            }

            Pair(name, if (code > 0) code else BuildConfig.VERSION_CODE)
        } catch (_: Exception) {
            Pair(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        }
    }

    /**
     * Determines whether an online server update is strictly newer than the currently installed app.
     * Protects against false-positive update prompts when the user already runs the latest version.
     */
    fun isUpdateAvailable(serverVersionName: String, serverVersionCode: Int, context: Context? = null): Boolean {
        val (installedName, installedCode) = if (context != null) {
            getInstalledVersion(context)
        } else {
            Pair(currentVersionName, currentVersionCode)
        }

        val cleanServer = serverVersionName.trim().removePrefix("v").trim()
        val cleanInstalled = installedName.trim().removePrefix("v").trim()

        // If the clean version names are identical, it is NOT an update
        if (cleanServer.equals(cleanInstalled, ignoreCase = true)) {
            return false
        }

        // Check if server version name is semantically newer (e.g. "2.2.3" > "2.2.2")
        val isSemVerNewer = SemVerUtils.isServerVersionNewer(cleanServer, cleanInstalled)

        // Check version code (e.g. 106 > 105)
        val isCodeNewer = serverVersionCode > installedCode

        return isSemVerNewer || isCodeNewer
    }
}
