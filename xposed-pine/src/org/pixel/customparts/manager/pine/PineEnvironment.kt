package org.pixel.customparts.manager.pine

import android.content.Context
import android.provider.Settings
import android.util.Log
import org.pixel.customparts.core.IHookEnvironment






class PineEnvironment : IHookEnvironment {

    companion object {
        private const val TAG_PREFIX = "PineInject"
        private const val SUFFIX = "_pine"
    }

    



    private fun resolveKey(key: String): String {
        val baseKey = key.removeSuffix("_xposed").removeSuffix("_pine")
        return "$baseKey$SUFFIX"
    }

    override fun isEnabled(context: Context?, key: String, default: Boolean): Boolean {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getInt(context.contentResolver, finalKey, if (default) 1 else 0) != 0
        } catch (t: Throwable) {
            logError("Env", "Failed to read boolean setting $finalKey", t)
            default
        }
    }

    override fun getInt(context: Context?, key: String, default: Int): Int {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getInt(context.contentResolver, finalKey, default)
        } catch (t: Throwable) {
            logError("Env", "Failed to read int setting $finalKey", t)
            default
        }
    }

    override fun getFloat(context: Context?, key: String, default: Float): Float {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getFloat(context.contentResolver, finalKey, default)
        } catch (t: Throwable) {
            try {
                val intValue = Settings.Global.getInt(
                    context.contentResolver,
                    finalKey,
                    (default * 100).toInt()
                )
                intValue / 100f
            } catch (inner: Throwable) {
                logError("Env", "Failed to read float setting $finalKey", inner)
                default
            }
        }
    }

    override fun log(tag: String, message: String) {
        Log.d(TAG_PREFIX, "[$tag] $message")
    }

    override fun logError(tag: String, message: String, t: Throwable?) {
        Log.e(TAG_PREFIX, "[$tag] ERROR: $message", t)
    }
}