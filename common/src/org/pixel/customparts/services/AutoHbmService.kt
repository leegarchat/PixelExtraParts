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
    // Incremented on each state transition to cancel in-progress ramps
    @Volatile private var rampGeneration = 0

    // Track whether HBM was active before screen off for deferred restore
    private var wasHbmActiveBeforeScreenOff = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
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
            postToEvaluator { deactivateHighBrightness() }
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
        // Synchronous final cleanup on evaluator thread
        evaluatorHandler?.let { handler ->
            val latch = java.util.concurrent.CountDownLatch(1)
            handler.post {
                deactivateHighBrightnessImmediate()
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
        }
        evaluatorThread?.quitSafely()
        evaluatorThread = null
        evaluatorHandler = null
        evaluatorRunnable = null
        runCatching { unregisterReceiver(screenStateReceiver) }
        AutoHbmController.publishState(this, lastLux)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || !AutoHbmController.isEnabled(this)) return
        lastLux = event.values.firstOrNull() ?: return
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // =====================================================================
    // Screen state handling — all brightness work posted to evaluator thread
    // =====================================================================

    private fun handleScreenOn() {
        val wasActive = wasHbmActiveBeforeScreenOff
        wasHbmActiveBeforeScreenOff = false

        // Start sensor + evaluator first so fresh lux arrives ASAP
        startListening()

        if (wasActive) {
            // HBM was active before screen off. The first evaluator tick (within checkIntervalMs)
            // will read fresh lux and decide whether to re-activate or not.
            // Pre-disable auto-brightness so the system doesn't fight us during that window.
            postToEvaluator {
                AutoHbmController.disableAutoBrightnessIfNeeded(this@AutoHbmService)
                // Set aboveThresholdAt to now so enableDelay is already satisfied
                // (user already had HBM active, no need to wait again)
                aboveThresholdAt = System.currentTimeMillis()
            }
        }
    }

    private fun handleScreenOff() {
        wasHbmActiveBeforeScreenOff = AutoHbmController.isHbmActive(this)
        stopListening()
        // Cancel any in-progress ramp and restore immediately (screen is off, no visual)
        rampGeneration++
        postToEvaluator {
            deactivateHighBrightnessImmediate()
            AutoHbmController.publishState(this@AutoHbmService, lastLux)
        }
    }

    // =====================================================================
    // Evaluator loop — single-threaded, all brightness mutations happen here
    // =====================================================================

    private fun evaluateState() {
        if (!AutoHbmController.isEnabled(this) || !AutoHbmController.isSupported() || !isInteractive()) {
            deactivateHighBrightnessImmediate()
            AutoHbmController.publishState(this, lastLux)
            return
        }

        val now = System.currentTimeMillis()
        val lux = lastLux
        val threshold = AutoHbmController.getThreshold(this)
        val deactivateThreshold = (threshold * HYSTERESIS_FACTOR).toInt()
        val enableDelayMs = AutoHbmController.getEnableTime(this) * 1000L
        val disableDelayMs = AutoHbmController.getDisableTime(this) * 1000L
        val maxActiveMs = AutoHbmController.getMaxActiveTime(this) * 1000L
        val cooldownMs = AutoHbmController.getCooldownTime(this) * 1000L
        val temperatureCelsius = AutoHbmController.readSocTemperatureC()
        val thermalBlocked = temperatureCelsius != null && temperatureCelsius >= AutoHbmController.getTemperatureLimit(this)
        val cooldownActive = cooldownUntil > now
        val hbmCurrentlyActive = AutoHbmController.isHbmActive(this)

        if (thermalBlocked || cooldownActive) {
            aboveThresholdAt = 0L
            belowThresholdAt = 0L
            if (hbmCurrentlyActive) {
                cancelRampAndDeactivate()
            } else {
                AutoHbmController.restoreAutoBrightnessIfNeeded(this)
            }
            AutoHbmController.publishState(this, lux, temperatureCelsius)
            return
        } else if (cooldownUntil != 0L) {
            cooldownUntil = 0L
        }

        val effectiveThreshold = if (hbmCurrentlyActive) deactivateThreshold else threshold

        if (lux >= effectiveThreshold) {
            belowThresholdAt = 0L
            AutoHbmController.disableAutoBrightnessIfNeeded(this)

            if (aboveThresholdAt == 0L) {
                aboveThresholdAt = now
            }

            if (!hbmCurrentlyActive && now - aboveThresholdAt >= enableDelayMs) {
                val gen = ++rampGeneration
                if (AutoHbmController.activateHighBrightness(
                        context = this,
                        smoothRamp = AutoHbmController.isSmoothRampEnabled(this),
                        rampTimeMs = AutoHbmController.getRampTimeMs(this),
                        shouldContinue = {
                            rampGeneration == gen &&
                                evaluatorRunning &&
                                AutoHbmController.isEnabled(this) &&
                                isInteractive() &&
                                lastLux >= deactivateThreshold
                        }
                    )
                ) {
                    activatedAt = System.currentTimeMillis()
                } else if (rampGeneration == gen) {
                    Log.w(TAG, "Failed to activate high brightness")
                    deactivateHighBrightnessImmediate()
                }
            }

            if (AutoHbmController.isHbmActive(this)) {
                if (!AutoHbmController.maintainHighBrightness(this)) {
                    Log.w(TAG, "Failed to maintain high brightness")
                }
                if (activatedAt == 0L) activatedAt = now
                if (System.currentTimeMillis() - activatedAt >= maxActiveMs) {
                    cancelRampAndDeactivate()
                    cooldownUntil = System.currentTimeMillis() + cooldownMs
                    aboveThresholdAt = 0L
                    belowThresholdAt = 0L
                }
            }
        } else {
            aboveThresholdAt = 0L

            if (hbmCurrentlyActive) {
                if (belowThresholdAt == 0L) belowThresholdAt = now
                if (now - belowThresholdAt >= disableDelayMs) {
                    cancelRampAndDeactivate()
                    belowThresholdAt = 0L
                }
            } else {
                belowThresholdAt = 0L
                AutoHbmController.restoreAutoBrightnessIfNeeded(this)
            }
        }

        AutoHbmController.publishState(this, lux, temperatureCelsius)
    }

    // =====================================================================
    // Brightness control helpers — always called from evaluator thread
    // =====================================================================

    /**
     * Cancel any in-progress ramp and deactivate with smooth ramp.
     */
    private fun cancelRampAndDeactivate() {
        val gen = ++rampGeneration
        if (!AutoHbmController.restoreOriginalBrightness(
                context = this,
                smoothRamp = AutoHbmController.isSmoothRampEnabled(this),
                rampTimeMs = AutoHbmController.getRampTimeMs(this),
                shouldContinue = { rampGeneration == gen && evaluatorRunning }
            )
        ) {
            // If ramp was cancelled, force immediate restore
            AutoHbmController.restoreOriginalBrightness(context = this, smoothRamp = false)
        }
        AutoHbmController.restoreAutoBrightnessIfNeeded(this)
        activatedAt = 0L
    }

    /**
     * Immediate deactivation without smooth ramp. Used for screen-off and error paths.
     */
    private fun deactivateHighBrightnessImmediate() {
        rampGeneration++
        AutoHbmController.restoreOriginalBrightness(context = this, smoothRamp = false)
        AutoHbmController.restoreAutoBrightnessIfNeeded(this)
        activatedAt = 0L
    }

    /**
     * Legacy wrapper — kept for compatibility with paths that may still call it.
     */
    private fun deactivateHighBrightness() {
        cancelRampAndDeactivate()
    }

    // =====================================================================
    // Sensor and evaluator lifecycle
    // =====================================================================

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
        belowThresholdAt = 0L
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

    private fun postToEvaluator(block: () -> Unit) {
        val handler = evaluatorHandler
        if (handler != null) {
            handler.post(block)
        } else {
            // Fallback: no evaluator thread yet, run inline
            block()
        }
    }

    private fun isInteractive(): Boolean {
        return (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
    }

    companion object {
        private const val TAG = "AutoHbmService"
        // Hysteresis: deactivate at 85% of activation threshold to prevent flicker
        private const val HYSTERESIS_FACTOR = 0.85f
    }
}
