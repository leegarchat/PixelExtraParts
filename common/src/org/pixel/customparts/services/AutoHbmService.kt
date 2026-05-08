package org.pixel.customparts.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.pixel.customparts.utils.AutoHbmController

class AutoHbmService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var crossedThresholdAt = 0L
    private var activatedAt = 0L
    private var listening = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startListening()
                Intent.ACTION_SCREEN_OFF -> {
                    stopListening()
                    AutoHbmController.restoreOriginalBrightness(this@AutoHbmService)
                    AutoHbmController.publishState(this@AutoHbmService, AutoHbmController.getLastLux(this@AutoHbmService))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        val screenStateFilter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            screenStateFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (isInteractive()) {
            startListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AutoHbmController.isEnabled(this) || !AutoHbmController.isSupported()) {
            AutoHbmController.restoreOriginalBrightness(this)
            stopSelf()
            return START_NOT_STICKY
        }

        if (isInteractive()) {
            startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        runCatching { unregisterReceiver(screenStateReceiver) }
        AutoHbmController.restoreOriginalBrightness(this)
        AutoHbmController.publishState(this, AutoHbmController.getLastLux(this))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || !AutoHbmController.isEnabled(this)) return

        val lux = event.values.firstOrNull() ?: return
        val now = System.currentTimeMillis()
        val threshold = AutoHbmController.getThreshold(this)
        val enableDelayMs = AutoHbmController.getEnableTime(this) * 1000L
        val disableDelayMs = AutoHbmController.getDisableTime(this) * 1000L

        if (lux >= threshold) {
            if (crossedThresholdAt == 0L) {
                crossedThresholdAt = now
            }

            if (now - crossedThresholdAt >= enableDelayMs && !AutoHbmController.isHbmActive(this)) {
                if (AutoHbmController.activateHighBrightness(this)) {
                    activatedAt = now
                } else {
                    Log.w(TAG, "Failed to activate high brightness")
                }
            }
        } else {
            crossedThresholdAt = 0L

            if (AutoHbmController.isHbmActive(this)) {
                if (activatedAt == 0L) activatedAt = now
                if (now - activatedAt >= disableDelayMs) {
                    if (AutoHbmController.restoreOriginalBrightness(this)) {
                        activatedAt = 0L
                    } else {
                        Log.w(TAG, "Failed to restore brightness")
                    }
                }
            }
        }

        AutoHbmController.publishState(this, lux)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startListening() {
        if (listening) return
        val manager = sensorManager ?: return
        val sensor = lightSensor ?: return
        listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopListening() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
        crossedThresholdAt = 0L
        activatedAt = 0L
    }

    private fun isInteractive(): Boolean {
        return (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
    }

    companion object {
        private const val TAG = "AutoHbmService"
    }
}