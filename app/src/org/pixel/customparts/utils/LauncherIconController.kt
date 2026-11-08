package org.pixel.customparts.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherIconController {
    private const val LAUNCHER_ALIAS_CLASS = "org.pixel.customparts.LauncherIconActivity"

    fun isAvailable(context: Context): Boolean {
        return getAliasActivityInfo(context) != null
    }

    fun isEnabled(context: Context): Boolean {
        val packageManager = context.packageManager
        val component = launcherAliasComponent(context)
        return when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> getAliasActivityInfo(context)?.enabled == true
        }
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!isAvailable(context)) return false

        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            launcherAliasComponent(context),
            state,
            PackageManager.DONT_KILL_APP
        )
        return true
    }

    private fun launcherAliasComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, LAUNCHER_ALIAS_CLASS)
    }

    private fun getAliasActivityInfo(context: Context) = try {
        context.packageManager.getActivityInfo(
            launcherAliasComponent(context),
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
