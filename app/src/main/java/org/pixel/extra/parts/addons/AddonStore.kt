package org.pixel.extra.parts.addons

import android.content.Context
import android.provider.Settings
import android.util.Log

// Fail-safe Settings.Global bridge for the addon engine. Reads fall back to
// the declared default, writes are best-effort (priv-app grants arrive only
// in firmware builds). All addon values are ints; booleans map to 0/1.
object AddonStore {
    private const val TAG = "AddonStore"

    fun getInt(context: Context, key: String, defaultValue: Int): Int =
        try {
            Settings.Global.getInt(context.contentResolver, key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Read failed for $key", e)
            defaultValue
        }

    fun putInt(context: Context, key: String, value: Int) {
        try {
            Settings.Global.putInt(context.contentResolver, key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Write failed for $key", e)
        }
    }

    fun isEnabled(context: Context, key: String, defaultValue: Boolean = false): Boolean =
        getInt(context, key, if (defaultValue) 1 else 0) != 0

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        putInt(context, key, if (enabled) 1 else 0)
    }
}
