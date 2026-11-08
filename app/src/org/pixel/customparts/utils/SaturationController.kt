package org.pixel.customparts.utils

import android.content.Context
import android.os.Parcel
import android.os.ServiceManager
import android.util.Log
import org.pixel.customparts.SettingsKeys

object SaturationController {
    const val MIN_PERCENT = 0
    const val DEFAULT_PERCENT = 100
    const val MAX_PERCENT = 200

    private const val TAG = "SaturationController"
    private const val SURFACE_FLINGER_SERVICE = "SurfaceFlinger"
    private const val SURFACE_COMPOSER_INTERFACE = "android.ui.ISurfaceComposer"
    private const val TRANSACTION_SET_SATURATION = 1022

    fun isEnabled(context: Context): Boolean {
        return SettingsCompat.isEnabled(context, SettingsKeys.SATURATION_ENABLED, false)
    }

    fun getPercent(context: Context): Int {
        return SettingsCompat.getInt(
            context,
            SettingsKeys.SATURATION_PERCENT,
            DEFAULT_PERCENT
        ).coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        SettingsCompat.putInt(context, SettingsKeys.SATURATION_ENABLED, if (enabled) 1 else 0)
        return applyEffectiveSaturation(context)
    }

    fun setPercent(context: Context, percent: Int): Boolean {
        val clampedPercent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        SettingsCompat.putInt(context, SettingsKeys.SATURATION_PERCENT, clampedPercent)
        return if (isEnabled(context)) applyPercent(clampedPercent) else true
    }

    fun applyEffectiveSaturation(context: Context): Boolean {
        val effectivePercent = if (isEnabled(context)) getPercent(context) else DEFAULT_PERCENT
        return applyPercent(effectivePercent)
    }

    fun applyPercent(percent: Int): Boolean {
        val surfaceFlinger = ServiceManager.getService(SURFACE_FLINGER_SERVICE) ?: return false
        val data = Parcel.obtain()

        return try {
            data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE)
            data.writeFloat(toSurfaceFlingerScale(percent.coerceIn(MIN_PERCENT, MAX_PERCENT)))
            surfaceFlinger.transact(TRANSACTION_SET_SATURATION, data, null, 0)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to apply display saturation", e)
            false
        } finally {
            data.recycle()
        }
    }

    fun toSurfaceFlingerScale(percent: Int): Float {
        return if (percent == DEFAULT_PERCENT) 1.001f else percent / 100f
    }

}