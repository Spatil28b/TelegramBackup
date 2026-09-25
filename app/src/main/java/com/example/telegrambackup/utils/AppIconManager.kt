package com.example.telegrambackup.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.telegrambackup.R

enum class AppIcon(
    val id: String,
    val title: String,
    val subtitle: String,
    val aliasClassName: String,
    val previewDrawableRes: Int
) {
    GREEN(
        id = "green",
        title = "Emerald Forest",
        subtitle = "Signature glowing emerald icon with dark crystal background",
        aliasClassName = "com.example.telegrambackup.MainActivityGreen",
        previewDrawableRes = R.drawable.app_icon_green
    ),
    BLUE(
        id = "blue",
        title = "Cyber Electric Blue",
        subtitle = "Striking neon blue icon with deep space contrast",
        aliasClassName = "com.example.telegrambackup.MainActivityBlue",
        previewDrawableRes = R.drawable.app_icon_blue
    )
}

object AppIconManager {
    private const val TAG = "AppIconManager"

    fun getCurrentIcon(context: Context): AppIcon {
        val saved = AppPrefs(context).appIcon
        return if (saved == "blue") AppIcon.BLUE else AppIcon.GREEN
    }

    fun setAppIcon(context: Context, targetIcon: AppIcon): Boolean {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName

            val greenComponent = ComponentName(pkg, AppIcon.GREEN.aliasClassName)
            val blueComponent = ComponentName(pkg, AppIcon.BLUE.aliasClassName)

            if (targetIcon == AppIcon.GREEN) {
                pm.setComponentEnabledSetting(
                    greenComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    blueComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                pm.setComponentEnabledSetting(
                    blueComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    greenComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Save preference
            AppPrefs(context).appIcon = targetIcon.id
            Log.d(TAG, "Successfully updated launcher app icon to: ${targetIcon.title}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch app icon to ${targetIcon.name}", e)
            false
        }
    }
}
