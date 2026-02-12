package org.pixel.customparts.activities

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.customparts.SettingsKeys

object DoubleTapManager {
    val KEY_DT2W_ENABLE: String
        get() = SettingsKeys.DOZE_DOUBLE_TAP_HOOK

    val KEY_DT2S_ENABLE: String
        get() = SettingsKeys.LAUNCHER_DT2S_ENABLED

    private const val KEY_DT2W_TIMEOUT = SettingsKeys.DOZE_DOUBLE_TAP_TIMEOUT
    private const val KEY_DT2S_TIMEOUT = SettingsKeys.LAUNCHER_DT2S_TIMEOUT
    private const val KEY_DT2S_SLOP = SettingsKeys.LAUNCHER_DT2S_SLOP

    fun isDt2wEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, KEY_DT2W_ENABLE, 0) == 1
    }

    suspend fun setDt2wEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_DT2W_ENABLE, if (enabled) 1 else 0)
    }

    fun getDt2wTimeout(context: Context): Int {
        return Settings.Global.getInt(context.contentResolver, KEY_DT2W_TIMEOUT, 400)
    }

    suspend fun setDt2wTimeout(context: Context, value: Int) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_DT2W_TIMEOUT, value)
    }

    fun isDt2sEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, KEY_DT2S_ENABLE, 0) == 1
    }

    suspend fun setDt2sEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_DT2S_ENABLE, if (enabled) 1 else 0)
    }

    fun getDt2sTimeout(context: Context): Int {
        return Settings.Global.getInt(context.contentResolver, KEY_DT2S_TIMEOUT, 400)
    }

    suspend fun setDt2sTimeout(context: Context, value: Int) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_DT2S_TIMEOUT, value)
    }

    fun getDt2sSlop(context: Context): Int {
        return Settings.Global.getInt(context.contentResolver, KEY_DT2S_SLOP, 0)
    }

    suspend fun setDt2sSlop(context: Context, value: Int) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_DT2S_SLOP, value)
    }
}
