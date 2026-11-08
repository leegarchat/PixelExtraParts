package org.pixel.extra.parts.display

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.Log

// Fail-safe bridge to Settings.Global for every Display knob, plus the
// best-effort SurfaceFlinger saturation transaction ported from the legacy
// controller. The persisted keys are the source of truth; firmware services
// re-apply them on boot, so the UI never needs to start or stop services.
object DisplayStore {
    private const val TAG = "DisplayStore"
    private const val SURFACE_FLINGER_SERVICE = "SurfaceFlinger"
    private const val SURFACE_COMPOSER_INTERFACE = "android.ui.ISurfaceComposer"
    private const val TRANSACTION_SET_SATURATION = 1022

    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, key, defaultValue)
        } catch (e: Exception) {
            Log.w(TAG, "Read failed for $key", e)
            defaultValue
        }
    }

    fun putInt(context: Context, key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, value)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Write failed for $key", e)
            false
        }
    }

    fun isEnabled(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getInt(context, key, if (defaultValue) 1 else 0) != 0
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean): Boolean {
        return putInt(context, key, if (enabled) 1 else 0)
    }

    fun writeSaturation(context: Context, enabled: Boolean, percent: Int): Boolean {
        val clamped = percent.coerceIn(DisplayKeys.SATURATION_MIN, DisplayKeys.SATURATION_MAX)
        if (!putInt(context, DisplayKeys.SATURATION_PERCENT, clamped)) return false
        if (!setEnabled(context, DisplayKeys.SATURATION_ENABLED, enabled)) return false
        val effective = if (enabled) clamped else DisplayKeys.SATURATION_DEFAULT
        return applySaturationPercent(effective)
    }

    // Immediate preview of the stored value. Uses reflection for
    // ServiceManager so the call compiles against the plain SDK.
    fun applySaturationPercent(percent: Int): Boolean {
        val clamped = percent.coerceIn(DisplayKeys.SATURATION_MIN, DisplayKeys.SATURATION_MAX)
        val scale = if (clamped == DisplayKeys.SATURATION_DEFAULT) 1.001f else clamped / 100f
        val binder: IBinder = try {
            val manager = Class.forName("android.os.ServiceManager")
            val getter = manager.getMethod("getService", String::class.java)
            getter.invoke(null, SURFACE_FLINGER_SERVICE) as? IBinder ?: return false
        } catch (e: Exception) {
            Log.w(TAG, "SurfaceFlinger lookup failed", e)
            return false
        }
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE)
            data.writeFloat(scale)
            binder.transact(TRANSACTION_SET_SATURATION, data, null, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Saturation transaction failed", e)
            false
        } finally {
            data.recycle()
        }
    }
}
