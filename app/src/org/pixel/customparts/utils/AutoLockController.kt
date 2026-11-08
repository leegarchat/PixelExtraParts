package org.pixel.customparts.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManagerGlobal
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.services.AutoLockService

object AutoLockController {
    private const val TAG = "AutoLockController"

    const val DEFAULT_TIMEOUT_SECONDS = 300  // 5 minutes
    const val MIN_TIMEOUT_SECONDS = 10
    const val MAX_TIMEOUT_SECONDS = 3600     // 1 hour
    const val MODE_OFF = 0
    const val MODE_ALWAYS = 1
    const val MODE_BLOCKER_ONLY = 2

    const val ACTION_STATE_CHANGED = "org.pixel.customparts.action.AUTO_LOCK_STATE_CHANGED"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_TIMEOUT = "timeout"
    const val EXTRA_REMAINING = "remaining"

    fun isEnabled(context: Context): Boolean {
        return getMode(context) != MODE_OFF
    }

    fun getMode(context: Context): Int {
        val storedMode = SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_LOCK_MODE,
            if (SettingsCompat.isEnabled(context, SettingsKeys.AUTO_LOCK_ENABLED, false)) {
                MODE_ALWAYS
            } else {
                MODE_OFF
            }
        )
        return storedMode.coerceIn(MODE_OFF, MODE_BLOCKER_ONLY)
    }

    fun setMode(context: Context, mode: Int) {
        val normalizedMode = mode.coerceIn(MODE_OFF, MODE_BLOCKER_ONLY)
        SettingsCompat.putInt(context, SettingsKeys.AUTO_LOCK_MODE, normalizedMode)
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_LOCK_ENABLED,
            if (normalizedMode == MODE_OFF) 0 else 1
        )
        syncService(context)
        publishState(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        setMode(context, if (enabled) MODE_ALWAYS else MODE_OFF)
    }

    fun getTimeoutSeconds(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.AUTO_LOCK_TIMEOUT_SECONDS,
            DEFAULT_TIMEOUT_SECONDS
        ).coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    }

    fun setTimeoutSeconds(context: Context, seconds: Int) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_LOCK_TIMEOUT_SECONDS,
            seconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
        )
    }

    fun isPauseMediaEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.AUTO_LOCK_PAUSE_MEDIA, false)
    }

    fun setPauseMediaEnabled(context: Context, enabled: Boolean) {
        SettingsCompat.putInt(
            context,
            SettingsKeys.AUTO_LOCK_PAUSE_MEDIA,
            if (enabled) 1 else 0
        )
    }

    fun pauseActiveMedia(context: Context) {
        try {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            Log.i(TAG, "Media pause requested before screen lock")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to pause active media", e)
        }
    }

    fun syncService(context: Context) {
        try {
            val intent = Intent(context, AutoLockService::class.java)
            if (isEnabled(context)) {
                context.startService(intent)
            } else {
                context.stopService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync AutoLock service", e)
        }
    }

    fun publishState(context: Context, remainingSeconds: Int = -1) {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_ENABLED, isEnabled(context))
            putExtra(EXTRA_TIMEOUT, getTimeoutSeconds(context))
            putExtra(EXTRA_REMAINING, remainingSeconds)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Lock the screen — same approach as SystemUI SystemActions.lockScreen():
     * goToSleep() + lockNow()
     */
    fun lockScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.goToSleep(
                SystemClock.uptimeMillis(),
                PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                0
            )

            try {
                val wm = WindowManagerGlobal.getWindowManagerService()
                wm?.lockNow(null)
            } catch (e: Exception) {
                Log.w(TAG, "WindowManager.lockNow() failed, goToSleep should be enough", e)
            }

            Log.i(TAG, "Screen locked by auto-lock timer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock screen", e)
        }
    }

    /**
     * Format timeout in seconds to a human-readable localized string.
     * Examples (EN):
     *   30  -> "30 sec"
     *   90  -> "90 sec / 1 min 30 sec"
     *   3600 -> "3600 sec / 1 hr"
     */
    fun formatTimeout(context: Context, seconds: Int): String {
        if (seconds < 60) {
            return context.getString(R.string.auto_lock_time_seconds, seconds)
        }

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val totalSecStr = context.getString(R.string.auto_lock_time_seconds, seconds)

        val parts = mutableListOf<String>()
        if (hours > 0) {
            parts.add(context.getString(R.string.auto_lock_time_hours, hours))
        }
        if (minutes > 0) {
            parts.add(context.getString(R.string.auto_lock_time_minutes, minutes))
        }
        if (secs > 0) {
            parts.add(context.getString(R.string.auto_lock_time_seconds, secs))
        }

        val humanStr = parts.joinToString(" ")
        return context.getString(R.string.auto_lock_time_format, totalSecStr, humanStr)
    }
}
