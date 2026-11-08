package org.pixel.extra.parts.systemui

import android.content.Context
import android.provider.Settings
import androidx.annotation.StringRes

// Fail-safe Settings.Global access: reads fall back to the default,
// writes are best-effort (priv-app grants arrive only in firmware builds).
internal fun readGlobalInt(context: Context, key: String, defaultValue: Int): Int =
    try {
        Settings.Global.getInt(context.contentResolver, key, defaultValue)
    } catch (_: Exception) {
        defaultValue
    }

internal fun writeGlobalInt(context: Context, key: String, value: Int) {
    try {
        Settings.Global.putInt(context.contentResolver, key, value)
    } catch (_: Exception) {
    }
}

internal fun readGlobalFloat(context: Context, key: String, defaultValue: Float): Float =
    try {
        Settings.Global.getFloat(context.contentResolver, key, defaultValue)
    } catch (_: Exception) {
        defaultValue
    }

internal fun writeGlobalFloat(context: Context, key: String, value: Float) {
    try {
        Settings.Global.putFloat(context.contentResolver, key, value)
    } catch (_: Exception) {
    }
}

internal fun readGlobalString(context: Context, key: String): String? =
    try {
        Settings.Global.getString(context.contentResolver, key)
    } catch (_: Exception) {
        null
    }

// One int-valued option of a segmented row; labels come from resources.
internal data class SegmentOption(
    val value: Int,
    @StringRes val labelRes: Int,
)
