package org.pixel.customparts.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.activities.AutoHbmActivity
import org.pixel.customparts.utils.AutoHbmController
import org.pixel.customparts.utils.RemoteStringsManager

class AutoHbmService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var aboveThresholdAt = 0L
    private var belowThresholdAt = 0L
    private var activatedAt = 0L
    private var cooldownUntil = 0L
    @Volatile private var listening = false
    private var evaluatorThread: HandlerThread? = null
    private var evaluatorHandler: Handler? = null
    private var evaluatorRunnable: Runnable? = null
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var rampThread: HandlerThread? = null
    private var rampHandler: Handler? = null
    @Volatile private var evaluatorRunning = false
    @Volatile private var isRamping = false
    private var registeredSamplingIntervalMs = 0
    private var safetyLockout: SafetyLockout? = null

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

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshNotification()
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
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(AutoHbmController.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (isInteractive()) {
            startListening()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_BRIGHTNESS_LOCK) {
            AutoHbmController.setBrightnessLock(
                this,
                !AutoHbmController.isBrightnessLockEnabled(this)
            )
            refreshNotification()
            return START_STICKY
        }
        if (intent?.action == ACTION_DISABLE) {
            AutoHbmController.setModeEnabled(
                this,
                AutoHbmController.getHbmMode(this),
                false
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!AutoHbmController.isEnabled(this) || !AutoHbmController.isSupported()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            postToEvaluator { deactivateHighBrightnessImmediate() }
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground()
        refreshNotification()

        if (isInteractive()) {
            startListening()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (AutoHbmController.isEnabled(this) && AutoHbmController.isSupported()) {
            // Keep monitoring independent from the PixelParts settings task lifecycle.
            startService(Intent(applicationContext, AutoHbmService::class.java))
        }
        super.onTaskRemoved(rootIntent)
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
        sensorHandler?.removeCallbacksAndMessages(null)
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null
        rampHandler?.removeCallbacksAndMessages(null)
        rampThread?.quitSafely()
        rampThread = null
        rampHandler = null
        runCatching { unregisterReceiver(screenStateReceiver) }
        runCatching { unregisterReceiver(stateReceiver) }
        AutoHbmController.publishState(this, lastLux)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val isPermanentMode = AutoHbmController.getHbmMode(this) == SettingsKeys.HBM_MODE_PERMANENT
        val notificationTitle = getString(
            if (isPermanentMode) {
                R.string.auto_hbm_notification_permanent_title
            } else {
                R.string.auto_hbm_notification_auto_title
            }
        )
        val notificationSummary = getString(
            if (isPermanentMode) {
                R.string.auto_hbm_notification_permanent_summary
            } else {
                R.string.auto_hbm_notification_auto_summary
            }
        )
        val openSettingsIntent = Intent(this, AutoHbmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openSettingsPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_SETTINGS,
            openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableIntent = Intent(this, AutoHbmService::class.java).apply {
            action = ACTION_DISABLE
        }
        val disablePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DISABLE,
            disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.auto_hbm_title),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.auto_hbm_summary)
                    setShowBadge(false)
                }
            )
        }

        val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(R.drawable.ic_auto_hbm_tile)
            .setContentTitle(notificationTitle)
            .setContentText(notificationSummary)
            .setContentIntent(openSettingsPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.auto_hbm_notification_disable),
                    disablePendingIntent
                ).build()
            )
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote Auto HBM service to foreground", e)
        }
    }

    private fun refreshNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val lockEnabled = AutoHbmController.isBrightnessLockEnabled(this)
        val openSettings = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_SETTINGS,
            Intent(this, AutoHbmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleLock = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE_BRIGHTNESS_LOCK,
            Intent(this, AutoHbmService::class.java).setAction(ACTION_TOGGLE_BRIGHTNESS_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DISABLE,
            Intent(this, AutoHbmService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val modeTitle = if (AutoHbmController.getHbmMode(this) == SettingsKeys.HBM_MODE_PERMANENT) {
            getString(R.string.auto_hbm_notification_permanent_title)
        } else {
            getString(R.string.auto_hbm_notification_auto_title)
        }
        val text = getString(
            R.string.auto_hbm_notification_live,
            lastLux.toInt(),
            AutoHbmController.getThreshold(this),
            if (lockEnabled) getString(R.string.auto_hbm_notification_lock_on)
            else getString(R.string.auto_hbm_notification_lock_off)
        )
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_auto_hbm_tile)
            .setContentTitle(modeTitle)
            .setContentText(text)
            .setContentIntent(openSettings)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.auto_hbm_notification_disable),
                    disableIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(
                        if (lockEnabled) {
                            R.string.auto_hbm_notification_lock_off
                        } else {
                            R.string.auto_hbm_notification_lock_on
                        }
                    ),
                    toggleLock
                ).build()
            )
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to refresh Auto HBM foreground notification", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LIGHT || !listening) return
        val lux = event.values.firstOrNull() ?: return
        if (!lux.isFinite() || lux < 0f) return
        lastLux = lux
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
                val enableDelayMs = AutoHbmController.getEnableTime(this@AutoHbmService) * 1000L
                aboveThresholdAt = SystemClock.elapsedRealtime() - enableDelayMs
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
        if (!AutoHbmController.isHbmActive(this)) {
            AutoHbmController.rememberSystemBrightness(this)
        }
        if (!AutoHbmController.isEnabled(this) || !AutoHbmController.isSupported()) {
            deactivateHighBrightnessImmediate()
            AutoHbmController.publishState(this, lastLux)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val lux = lastLux
        val threshold = AutoHbmController.getThreshold(this)
        val deactivateThreshold = (threshold * HYSTERESIS_FACTOR).toInt()
        val enableDelayMs = AutoHbmController.getEnableTime(this) * 1000L
        val disableDelayMs = AutoHbmController.getDisableTime(this) * 1000L
        val maxActiveMs = AutoHbmController.getMaxActiveTime(this) * 1000L
        val cooldownMs = AutoHbmController.getCooldownTime(this) * 1000L
        val temperatureCelsius = AutoHbmController.readSocTemperatureC()
        val tempLimit = AutoHbmController.getTemperatureLimit(this)
        val hbmCurrentlyActive = AutoHbmController.isHbmActive(this)
        val effectiveTempLimit = if (hbmCurrentlyActive) tempLimit.toFloat() else (tempLimit - THERMAL_RECOVERY_DELTA_C)
        val thermalBlocked = temperatureCelsius != null && temperatureCelsius >= effectiveTempLimit
        val cooldownActive = cooldownUntil > now

        if (hbmCurrentlyActive && activatedAt == 0L) {
            val persistedSince = AutoHbmController.getActiveSince(this)
            activatedAt = persistedSince.takeIf { it in 1L..now } ?: now
        }

        // The timeout is a safety limit for every HBM mode, not only the lux path.
        if (!isRamping && hbmCurrentlyActive && now - activatedAt >= maxActiveMs) {
            cancelRampAndDeactivateAsync()
            cooldownUntil = now + cooldownMs
            aboveThresholdAt = 0L
            belowThresholdAt = 0L
            enterSafetyLockout(SafetyLockout.TIMEOUT, now, cooldownMs)
            AutoHbmController.publishState(this, lux, temperatureCelsius)
            return
        }

        // Permanent HBM mode — keep max brightness, ignore auto settings
        if (AutoHbmController.isPermanentMode(this)) {
            if (!isInteractive()) {
                if (hbmCurrentlyActive) {
                    deactivateHighBrightnessImmediate()
                }
                AutoHbmController.publishState(this, lux, temperatureCelsius)
                return
            }

            if (isRamping && thermalBlocked) {
                deactivateHighBrightnessImmediate()
                cooldownUntil = now + cooldownMs
                aboveThresholdAt = 0L
                belowThresholdAt = 0L
                enterSafetyLockout(SafetyLockout.THERMAL, now, cooldownMs)
                AutoHbmController.publishState(this, lux, temperatureCelsius)
                return
            }

            if (thermalBlocked) {
                if (hbmCurrentlyActive) {
                    deactivateHighBrightnessImmediate()
                    cooldownUntil = now + cooldownMs
                    enterSafetyLockout(SafetyLockout.THERMAL, now, cooldownMs)
                }
                AutoHbmController.publishState(this, lux, temperatureCelsius)
                return
            }

            if (cooldownActive) {
                AutoHbmController.publishState(this, lux, temperatureCelsius)
                return
            }

            if (cooldownUntil != 0L) {
                cooldownUntil = 0L
            }

            val brightnessLockEnabled = AutoHbmController.isBrightnessLockEnabled(this)
            if (brightnessLockEnabled) {
                AutoHbmController.disableAutoBrightnessIfNeeded(this)
                if (AutoHbmController.forceMaxBrightness(this)) {
                    notifySafetyRestoredIfNeeded()
                }
                AutoHbmController.publishState(this, lux, temperatureCelsius)
                return
            }

            val maxBrightness = AutoHbmController.readMaxBrightness()
            if (maxBrightness == null) {
                deactivateHighBrightnessImmediate()
                AutoHbmController.publishState(this, lux, temperatureCelsius)
                return
            }

            if (!isRamping && !hbmCurrentlyActive) {
                startActivationRamp(
                    shouldContinue = { isInteractive() },
                    failureMessage = "Failed to activate permanent HBM"
                )
            }

            if (hbmCurrentlyActive) {
                AutoHbmController.maintainHighBrightness(this)
            }

            AutoHbmController.publishState(this, lux, temperatureCelsius)
            return
        }

        if (isRamping) {
            if (thermalBlocked) {
                deactivateHighBrightnessImmediate()
                cooldownUntil = now + cooldownMs
                aboveThresholdAt = 0L
                belowThresholdAt = 0L
                enterSafetyLockout(SafetyLockout.THERMAL, now, cooldownMs)
            }
            AutoHbmController.publishState(this, lux, temperatureCelsius)
            return
        }

        if (thermalBlocked || cooldownActive) {
            aboveThresholdAt = 0L
            belowThresholdAt = 0L
            if (thermalBlocked && hbmCurrentlyActive) {
                enterSafetyLockout(SafetyLockout.THERMAL, now, cooldownMs)
            }
            if (hbmCurrentlyActive) {
                cancelRampAndDeactivateAsync()
                if (thermalBlocked) {
                    cooldownUntil = now + cooldownMs
                }
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
                startActivationRamp(
                    shouldContinue = {
                        AutoHbmController.isEnabled(this) &&
                            isInteractive()
                    },
                    failureMessage = "Failed to activate high brightness asynchronously"
                )
            }

            if (AutoHbmController.isHbmActive(this)) {
                val maintained = if (AutoHbmController.isBrightnessLockEnabled(this)) {
                    AutoHbmController.forceMaxBrightness(this)
                } else {
                    AutoHbmController.maintainHighBrightness(this)
                }
                if (!maintained) {
                    Log.w(TAG, "Failed to maintain high brightness")
                }
                if (activatedAt == 0L) activatedAt = now
            }
        } else {
            aboveThresholdAt = 0L

            if (hbmCurrentlyActive) {
                if (belowThresholdAt == 0L) belowThresholdAt = now
                if (now - belowThresholdAt >= disableDelayMs) {
                    cancelRampAndDeactivateAsync()
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
    // Brightness control helpers — state transitions are serialized by the evaluator
    // =====================================================================

    private fun startActivationRamp(
        shouldContinue: () -> Boolean,
        failureMessage: String
    ): Boolean {
        val handler = rampHandler ?: return false
        val generation = ++rampGeneration
        isRamping = true

        runCatching {
            AutoHbmController.activateHighBrightnessAsync(
                context = this,
                handler = handler,
                smoothRamp = AutoHbmController.isSmoothRampEnabled(this),
                rampTimeMs = AutoHbmController.getRampTimeMs(this),
                shouldContinue = {
                    rampGeneration == generation && evaluatorRunning && shouldContinue()
                },
                onComplete = { success ->
                    postToEvaluator {
                        if (rampGeneration != generation) return@postToEvaluator
                        isRamping = false
                        if (success) {
                            activatedAt = SystemClock.elapsedRealtime()
                            notifySafetyRestoredIfNeeded()
                        } else {
                            Log.w(TAG, failureMessage)
                            deactivateHighBrightnessImmediate()
                        }
                        AutoHbmController.publishState(this, lastLux)
                    }
                }
            )
        }.onFailure { error ->
            postToEvaluator {
                if (rampGeneration != generation) return@postToEvaluator
                isRamping = false
                Log.w(TAG, failureMessage, error)
                deactivateHighBrightnessImmediate()
            }
        }
        return true
    }

    /**
     * Cancel any in-progress ramp and deactivate with smooth ramp asynchronously.
     */
    private fun cancelRampAndDeactivateAsync() {
        val handler = rampHandler ?: run {
            deactivateHighBrightnessImmediate()
            return
        }
        val gen = ++rampGeneration
        isRamping = true

        AutoHbmController.restoreOriginalBrightnessAsync(
            context = this,
            handler = handler,
            smoothRamp = AutoHbmController.isSmoothRampEnabled(this),
            rampTimeMs = AutoHbmController.getRampTimeMs(this),
            shouldContinue = { rampGeneration == gen && evaluatorRunning },
            onComplete = { success ->
                postToEvaluator {
                    if (rampGeneration == gen) {
                        isRamping = false
                        if (!success) {
                            AutoHbmController.restoreOriginalBrightnessImmediate(this)
                        }
                        AutoHbmController.restoreAutoBrightnessIfNeeded(this)
                        activatedAt = 0L
                        AutoHbmController.publishState(this, lastLux)
                    }
                }
            }
        )
    }

    /**
     * Immediate deactivation without smooth ramp. Used for screen-off and error paths.
     */
    private fun deactivateHighBrightnessImmediate() {
        rampGeneration++
        isRamping = false
        AutoHbmController.restoreOriginalBrightnessImmediate(this)
        AutoHbmController.restoreAutoBrightnessIfNeeded(this)
        activatedAt = 0L
    }

    // =====================================================================
    // Sensor and evaluator lifecycle
    // =====================================================================

    private fun startListening() {
        val manager = sensorManager ?: return
        val sensor = lightSensor ?: return
        ensureEvaluatorThread()
        ensureSensorThread()
        ensureRampThread()
        val callbackHandler = sensorHandler ?: return
        val intervalMs = AutoHbmController.getCheckIntervalMs(this)
        if (!listening || registeredSamplingIntervalMs != intervalMs) {
            if (listening) manager.unregisterListener(this)
            listening = manager.registerListener(this, sensor, intervalMs * 1000, 0, callbackHandler)
            registeredSamplingIntervalMs = if (listening) intervalMs else 0
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
        rampGeneration++
        rampHandler?.removeCallbacksAndMessages(null)
        registeredSamplingIntervalMs = 0
        belowThresholdAt = 0L
        cooldownUntil = 0L
        safetyLockout = null
        isRamping = false
    }

    private fun ensureSensorThread() {
        if (sensorThread == null) {
            sensorThread = HandlerThread("PixelParts-AutoHBM-Sensor").apply { start() }
            sensorHandler = Handler(sensorThread!!.looper)
        }
    }

    private fun ensureRampThread() {
        if (rampThread == null) {
            rampThread = HandlerThread("PixelParts-AutoHBM-Ramp").apply { start() }
            rampHandler = Handler(rampThread!!.looper)
        }
    }

    private fun startEvaluatorLoop() {
        ensureEvaluatorThread()
        if (evaluatorRunning) return

        evaluatorRunning = true
        if (evaluatorRunnable == null) {
            evaluatorRunnable = object : Runnable {
                override fun run() {
                    runCatching { evaluateState() }
                        .onFailure { Log.w(TAG, "Auto HBM evaluator failed", it) }

                    if (evaluatorRunning) {
                        // Re-register when the user changes the shared lux interval.
                        startListening()
                        evaluatorHandler?.postDelayed(this, AutoHbmController.getCheckIntervalMs(this@AutoHbmService).toLong())
                    }
                }
            }
        }
        evaluatorHandler?.removeCallbacks(evaluatorRunnable!!)
        evaluatorHandler?.post(evaluatorRunnable!!)
    }

    private fun ensureEvaluatorThread() {
        if (evaluatorThread == null) {
            evaluatorThread = HandlerThread("PixelParts-AutoHBM").apply { start() }
            evaluatorHandler = Handler(evaluatorThread!!.looper)
        }
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

    private fun enterSafetyLockout(reason: SafetyLockout, now: Long, cooldownMs: Long) {
        val wasCoolingDown = safetyLockout != null && cooldownUntil > now
        safetyLockout = reason
        cooldownUntil = maxOf(cooldownUntil, now + cooldownMs)
        if (!wasCoolingDown) {
            val message = when (reason) {
                SafetyLockout.TIMEOUT -> R.string.auto_hbm_timeout_toast
                SafetyLockout.THERMAL -> R.string.auto_hbm_thermal_toast
            }
            showToast(message)
        }
    }

    private fun notifySafetyRestoredIfNeeded() {
        if (safetyLockout != null) {
            safetyLockout = null
            showToast(R.string.auto_hbm_restored_toast)
        }
    }

    private fun showToast(messageRes: Int) {
        val message = RemoteStringsManager.getString(applicationContext, messageRes)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "AutoHbmService"
        private const val ACTION_DISABLE = "org.pixel.customparts.action.AUTO_HBM_DISABLE"
        private const val NOTIFICATION_CHANNEL_ID = "auto_hbm_listener"
        private const val NOTIFICATION_ID = 0x5048
        private const val REQUEST_CODE_OPEN_SETTINGS = 0x4842
        private const val REQUEST_CODE_DISABLE = 0x4843
        private const val ACTION_TOGGLE_BRIGHTNESS_LOCK =
            "org.pixel.customparts.action.AUTO_HBM_TOGGLE_BRIGHTNESS_LOCK"
        private const val REQUEST_CODE_TOGGLE_BRIGHTNESS_LOCK = 0x4844
        private const val THERMAL_RECOVERY_DELTA_C = 2.0f
        // Hysteresis: deactivate at 85% of activation threshold to prevent flicker
        private const val HYSTERESIS_FACTOR = 0.85f
    }

    private enum class SafetyLockout {
        TIMEOUT,
        THERMAL
    }
}
