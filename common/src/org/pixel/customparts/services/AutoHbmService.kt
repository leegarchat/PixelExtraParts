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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.pixel.customparts.utils.AutoHbmController

class AutoHbmService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var aboveThresholdAt = 0L
    private var belowThresholdAt = 0L
    private var activatedAt = 0L
    private var cooldownUntil = 0L
    private var listening = false
    private var evaluatorThread: HandlerThread? = null
    private var evaluatorHandler: Handler? = null
    private var evaluatorRunnable: Runnable? = null
    private var evaluatorRunning = false
    @Volatile private var lastLux = 0f

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startListening()
                Intent.ACTION_SCREEN_OFF -> {
                    stopListening()
                    deactivateHighBrightness()
                    AutoHbmController.publishState(this@AutoHbmService, lastLux)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        lastLux = AutoHbmController.getLastLux(this)

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
            deactivateHighBrightness()
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
        evaluatorThread?.quitSafely()
        evaluatorThread = null
        evaluatorHandler = null
        evaluatorRunnable = null
        runCatching { unregisterReceiver(screenStateReceiver) }
        deactivateHighBrightness()
        AutoHbmController.publishState(this, lastLux)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || !AutoHbmController.isEnabled(this)) return
        lastLux = event.values.firstOrNull() ?: return
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun evaluateState() {
        if (!AutoHbmController.isEnabled(this) || !AutoHbmController.isSupported() || !isInteractive()) {
            deactivateHighBrightness()
            AutoHbmController.publishState(this, lastLux)
            return
        }

        val now = System.currentTimeMillis()
        val lux = lastLux
        val threshold = AutoHbmController.getThreshold(this)
        val enableDelayMs = AutoHbmController.getEnableTime(this) * 1000L
        val disableDelayMs = AutoHbmController.getDisableTime(this) * 1000L
        val maxActiveMs = AutoHbmController.getMaxActiveTime(this) * 1000L
        val cooldownMs = AutoHbmController.getCooldownTime(this) * 1000L
        val temperatureCelsius = AutoHbmController.readSocTemperatureC()
        val thermalBlocked = temperatureCelsius != null && temperatureCelsius >= AutoHbmController.getTemperatureLimit(this)
        val cooldownActive = cooldownUntil > now

        if (thermalBlocked || cooldownActive) {
            aboveThresholdAt = 0L
            belowThresholdAt = 0L
            if (AutoHbmController.isHbmActive(this)) {
                deactivateHighBrightness()
            } else {
                AutoHbmController.restoreAutoBrightnessIfNeeded(this)
            }
            AutoHbmController.publishState(this, lux, temperatureCelsius)
            return
        } else if (cooldownUntil != 0L) {
            cooldownUntil = 0L
        }

        if (lux >= threshold) {
            belowThresholdAt = 0L
            AutoHbmController.disableAutoBrightnessIfNeeded(this)

            if (aboveThresholdAt == 0L) {
                aboveThresholdAt = now
            }

            if (now - aboveThresholdAt >= enableDelayMs && !AutoHbmController.isHbmActive(this)) {
                if (AutoHbmController.activateHighBrightness(
                        context = this,
                        smoothRamp = AutoHbmController.isSmoothRampEnabled(this),
                        rampTimeMs = AutoHbmController.getRampTimeMs(this),
                        shouldContinue = {
                            evaluatorRunning &&
                                AutoHbmController.isEnabled(this) &&
                                AutoHbmController.isSmoothRampEnabled(this) &&
                                isInteractive() &&
                                lastLux >= AutoHbmController.getThreshold(this)
                        }
                    )
                ) {
                    activatedAt = System.currentTimeMillis()
                } else {
                    Log.w(TAG, "Failed to activate high brightness")
                    deactivateHighBrightness()
                }
            }

            if (AutoHbmController.isHbmActive(this)) {
                if (!AutoHbmController.maintainHighBrightness(this)) {
                    Log.w(TAG, "Failed to maintain high brightness")
                }
                if (activatedAt == 0L) activatedAt = now
                if (System.currentTimeMillis() - activatedAt >= maxActiveMs) {
                    deactivateHighBrightness()
                    cooldownUntil = System.currentTimeMillis() + cooldownMs
                    aboveThresholdAt = 0L
                    belowThresholdAt = 0L
                }
            }
        } else {
            aboveThresholdAt = 0L

            if (AutoHbmController.isHbmActive(this)) {
                if (belowThresholdAt == 0L) belowThresholdAt = now
                if (now - belowThresholdAt >= disableDelayMs) {
                    deactivateHighBrightness()
                    belowThresholdAt = 0L
                }
            } else {
                belowThresholdAt = 0L
                AutoHbmController.restoreAutoBrightnessIfNeeded(this)
            }
        }

        AutoHbmController.publishState(this, lux, temperatureCelsius)
    }

    private fun startListening() {
        val manager = sensorManager ?: return
        val sensor = lightSensor ?: return
        if (!listening) {
            listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (listening) {
            startEvaluatorLoop()
        }
    }

    private fun stopListening() {
        if (listening) {
            sensorManager?.unregisterListener(this)
            listening = false
        }
        stopEvaluatorLoop()
        aboveThresholdAt = 0L
        belowThresholdAt = 0L
        activatedAt = 0L
        cooldownUntil = 0L
    }

    private fun startEvaluatorLoop() {
        if (evaluatorThread == null) {
            evaluatorThread = HandlerThread("PixelParts-AutoHBM").apply { start() }
            evaluatorHandler = Handler(evaluatorThread!!.looper)
        }
        if (evaluatorRunning) return

        evaluatorRunning = true
        if (evaluatorRunnable == null) {
            evaluatorRunnable = object : Runnable {
                override fun run() {
                    runCatching { evaluateState() }
                        .onFailure { Log.w(TAG, "Auto HBM evaluator failed", it) }

                    if (evaluatorRunning) {
                        evaluatorHandler?.postDelayed(this, AutoHbmController.getCheckIntervalMs(this@AutoHbmService).toLong())
                    }
                }
            }
        }
        evaluatorHandler?.removeCallbacks(evaluatorRunnable!!)
        evaluatorHandler?.post(evaluatorRunnable!!)
    }

    private fun stopEvaluatorLoop() {
        evaluatorRunning = false
        evaluatorRunnable?.let { evaluatorHandler?.removeCallbacks(it) }
    }

    private fun deactivateHighBrightness() {
        if (!AutoHbmController.restoreOriginalBrightness(
                context = this,
                smoothRamp = AutoHbmController.isSmoothRampEnabled(this),
                rampTimeMs = AutoHbmController.getRampTimeMs(this)
            )
        ) {
            Log.w(TAG, "Failed to restore brightness")
        }
        AutoHbmController.restoreAutoBrightnessIfNeeded(this)
        activatedAt = 0L
    }

    private fun isInteractive(): Boolean {
        return (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
    }

    companion object {
        private const val TAG = "AutoHbmService"
    }
}