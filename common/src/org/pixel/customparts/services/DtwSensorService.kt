package org.pixel.customparts.services

import android.app.Service
import android.app.PowerManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import android.provider.Settings
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.utils.SettingsCompat

class DtwSensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var mainHandler: Handler
    private var singleTapSensor: Sensor? = null
    private var proximitySensor: Sensor? = null

    // DTW state
    private var tapCount = 0
    private var lastTapTime = 0L
    private var lastTapX = -1f
    private var lastTapY = -1f
    private var dtwTimeoutRunnable: Runnable? = null
    private var proximityCheckListener: SensorEventListener? = null
    private var proximityCheckWakeLock: WakeLock? = null

    // System settings observer to keep SystemUI doze disabled while DTW is active
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            if (!isDtwEnabled()) return

            val resolver = applicationContext.contentResolver
            val dozeTap = Settings.Secure.getInt(resolver, Settings.Secure.DOZE_TAP_GESTURE, 0)
            val pulseTap = Settings.Secure.getInt(resolver, "doze_pulse_on_single_tap", 0)

            // Write 0 only if value is currently 1 (prevents infinite loop)
            if (dozeTap != 0) {
                Settings.Secure.putInt(resolver, Settings.Secure.DOZE_TAP_GESTURE, 0)
            }
            if (pulseTap != 0) {
                Settings.Secure.putInt(resolver, "doze_pulse_on_single_tap", 0)
            }
        }
    }

    // Screen state receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_SCREEN_OFF && isDtwEnabled()) {
                registerSingleTapListener()
            } else if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
                unregisterSingleTapListener()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isDtwEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Disable SystemUI doze to prevent conflict
        disableSystemDoze()

        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        // Register settings observer
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DOZE_TAP_GESTURE),
            false,
            settingsObserver
        )
        settingsObserver.onChange(false)

        // If screen is already off, register immediately
        if (!powerManager.isInteractive) {
            registerSingleTapListener()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSingleTapListener()
        proximityCheckCleanup()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) { }
        contentResolver.unregisterContentObserver(settingsObserver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun disableSystemDoze() {
        if (!isDtwEnabled()) return
        Settings.Secure.putInt(contentResolver, Settings.Secure.DOZE_TAP_GESTURE, 0)
        Settings.Secure.putInt(contentResolver, "doze_pulse_on_single_tap", 0)
    }

    private fun isDtwEnabled(): Boolean {
        return SettingsCompat.isEnabled(applicationContext, SettingsKeys.DTW_ENABLED, false)
    }

    private fun registerSingleTapListener() {
        if (singleTapSensor != null) return

        // Find the Pixel single-touch sensor by its custom string type
        singleTapSensor = sensorManager.getSensorList(Sensor.TYPE_ALL).find {
            it.stringType == SINGLE_TAP_SENSOR_TYPE
        }

        // Fallback: search by name
        if (singleTapSensor == null) {
            sensorManager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
                if (sensor.name?.contains(SINGLE_TAP_SENSOR_NAME_KEYWORD, ignoreCase = true) == true) {
                    singleTapSensor = sensor
                    return@forEach
                }
            }
        }

        singleTapSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    private fun unregisterSingleTapListener() {
        singleTapSensor?.let { sensor ->
            sensorManager.unregisterListener(this, sensor)
        }
        singleTapSensor = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = event.sensor
        when (sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                // Proximity check result
                proximityCheckListener?.let { listener ->
                    val distance = event.values.firstOrNull() ?: 0f
                    val isNear = distance < 1.0f || distance < sensor.maximumRange.coerceAtLeast(1f)
                    proximityCheckCleanup()
                    if (!isNear) {
                        triggerWakeUp()
                    }
                }
            }

            else -> {
                // Handle single tap sensor events
                if (sensor.stringType == SINGLE_TAP_SENSOR_TYPE ||
                    sensor.name?.contains(SINGLE_TAP_SENSOR_NAME_KEYWORD, ignoreCase = true) == true) {
                    processSingleTap(event)
                }
            }
        }
    }

    private fun processSingleTap(event: SensorEvent) {
        val x = event.values.getOrNull(0) ?: 0f
        val y = event.values.getOrNull(1) ?: 0f
        val now = SystemClock.uptimeMillis()

        val requiredTaps = SettingsCompat.getInt(
            applicationContext, SettingsKeys.DTW_TAP_COUNT, DEFAULT_TAP_COUNT
        ).coerceIn(1, 3)

        val timeoutMs = SettingsCompat.getInt(
            applicationContext, SettingsKeys.DTW_TAP_TIMEOUT_MS, DEFAULT_TIMEOUT_MS
        ).coerceIn(100, 2000)

        val checkProximity = SettingsCompat.isEnabled(
            applicationContext, SettingsKeys.DTW_PROXIMITY_CHECK, false
        )

        val checkCoords = SettingsCompat.isEnabled(
            applicationContext, SettingsKeys.DTW_COORDINATE_CHECK, false
        )

        val maxDistanceDp = SettingsCompat.getInt(
            applicationContext, SettingsKeys.DTW_MAX_DISTANCE_DP, DEFAULT_MAX_DISTANCE_DP
        ).coerceIn(10, 1000)

        val maxDistancePx = maxDistanceDp * resources.displayMetrics.density

        // Check if tap is within timeout window
        if (tapCount > 0 && (now - lastTapTime) > timeoutMs) {
            resetTapState()
        }

        // Coordinate check
        if (checkCoords && tapCount > 0) {
            val dx = (x - lastTapX).toDouble()
            val dy = (y - lastTapY).toDouble()
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared > (maxDistancePx * maxDistancePx)) {
                resetTapState()
            }
        }

        tapCount++
        lastTapTime = now
        lastTapX = x
        lastTapY = y

        if (tapCount >= requiredTaps) {
            // Cancel timeout
            dtwTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            dtwTimeoutRunnable = null

            // Perform action
            if (checkProximity) {
                resetTapState()
                performProximityCheckAndWake()
            } else {
                resetTapState()
                triggerWakeUp()
            }
        } else {
            // Schedule timeout to reset
            dtwTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            dtwTimeoutRunnable = Runnable { resetTapState() }
            mainHandler.postDelayed(dtwTimeoutRunnable!!, timeoutMs.toLong())
        }
    }

    private fun resetTapState() {
        tapCount = 0
        lastTapTime = 0L
        lastTapX = -1f
        lastTapY = -1f
        mainHandler.removeCallbacksAndMessages(null)
        dtwTimeoutRunnable = null
    }

    private fun performProximityCheckAndWake() {
        val sensor = proximitySensor ?: run {
            resetTapState()
            triggerWakeUp()
            return
        }

        proximityCheckWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PixelParts:DtwProximityCheck"
        ).apply { acquire(150) }

        proximityCheckListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                mainHandler.removeCallbacksAndMessages(null)
                val distance = event.values.firstOrNull() ?: 0f
                val isNear = distance < 1.0f || distance < sensor.maximumRange.coerceAtLeast(1f)
                proximityCheckCleanup()
                if (!isNear) {
                    triggerWakeUp()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(proximityCheckListener, sensor, SensorManager.SENSOR_DELAY_FASTEST)

        // Fallback timeout (60ms) — if proximity sensor doesn't report in time
        mainHandler.postDelayed({
            proximityCheckCleanup()
            triggerWakeUp()
        }, 60)
    }

    private fun proximityCheckCleanup() {
        proximityCheckListener?.let { sensorManager.unregisterListener(it) }
        proximityCheckListener = null
        proximityCheckWakeLock?.let { if (it.isHeld) it.release() }
        proximityCheckWakeLock = null
    }

    private fun triggerWakeUp() {
        val now = SystemClock.uptimeMillis()
        val wakeReasonGesture = 4
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                powerManager.wakeUp(now, wakeReasonGesture, "PixelExtraParts:dtw")
            } else {
                @Suppress("DEPRECATION")
                powerManager.wakeUp(now)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val SINGLE_TAP_SENSOR_TYPE = "com.google.sensor.single_touch"
        private const val SINGLE_TAP_SENSOR_NAME_KEYWORD = "single_touch"
        private const val DEFAULT_TIMEOUT_MS = 300
        private const val DEFAULT_MAX_DISTANCE_DP = 50
        private const val DEFAULT_TAP_COUNT = 2

        fun isDt2wSupported(context: Context): Boolean {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getSensorList(Sensor.TYPE_ALL).find {
                it.stringType == SINGLE_TAP_SENSOR_TYPE
            }
            return sensor != null
        }
    }
}
