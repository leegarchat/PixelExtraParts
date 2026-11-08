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
import android.database.ContentObserver
import android.hardware.input.InputManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import androidx.core.content.ContextCompat
import org.pixel.customparts.R
import org.pixel.customparts.activities.AutoLockSettingsActivity
import org.pixel.customparts.utils.AutoLockController

class AutoLockService : Service() {

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastInteractionUptimeMs = 0L
    @Volatile private var screenOnBlockerActive = false
    @Volatile private var running = false

    // Global input monitor — detects ALL input events (touch, volume, keys, mouse, gamepad)
    private var inputMonitor: android.view.InputMonitor? = null
    private var inputEventReceiver: InputEventReceiver? = null

    // Main timer — fires when timeout expires, locks the screen
    private val lockRunnable = Runnable {
        if (!running) return@Runnable
        Log.w(TAG, "[AutoLock] *** TIMER EXPIRED *** locking screen now")
        if (AutoLockController.isPauseMediaEnabled(this@AutoLockService)) {
            AutoLockController.pauseActiveMedia(this@AutoLockService)
        }
        AutoLockController.lockScreen(this@AutoLockService)
        stopMonitoring()
        AutoLockController.publishState(this@AutoLockService, 0)
    }

    // Countdown updater — publishes remaining time to UI every second
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val timeoutMs = AutoLockController.getTimeoutSeconds(this@AutoLockService) * 1000L
            val elapsed = SystemClock.elapsedRealtime() - lastInteractionUptimeMs
            val remaining = ((timeoutMs - elapsed) / 1000).toInt().coerceAtLeast(0)
            AutoLockController.publishState(this@AutoLockService, remaining)
            updateNotification()
            handler?.postDelayed(this, 1000L)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
            }
        }
    }

    // Detect volume key presses via ContentObserver on volume settings
    private var lastVolumeValue = -1
    private var volumeObserver: ContentObserver? = null

    // Detect media button events and other hardware key broadcasts
    private val hardwareInputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_KEEP_SCREEN_ON_CHANGED) {
                screenOnBlockerActive = intent.getBooleanExtra(EXTRA_ACTIVE, false)
                Log.i(TAG, "[AutoLock] KEEP_SCREEN_ON changed: active=$screenOnBlockerActive, " +
                        "package=${intent.getStringExtra(EXTRA_PACKAGE)}")
                if (AutoLockController.getMode(this@AutoLockService) == AutoLockController.MODE_BLOCKER_ONLY) {
                    if (screenOnBlockerActive) startMonitoring() else stopMonitoring()
                }
                return
            }
            if (!running) return
            when (intent.action) {
                ACTION_HARDWARE_INPUT -> {
                    val keyCode = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?.keyCode ?: -1
                    Log.d(TAG, "[AutoLock] Hardware key detected: keyCode=$keyCode")
                    resetTimer()
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.d(TAG, "[AutoLock] Audio becoming noisy (headphones disconnected)")
                    resetTimer()
                }
                Intent.ACTION_MEDIA_BUTTON -> {
                    Log.d(TAG, "[AutoLock] Media button pressed")
                    resetTimer()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[AutoLock] Service onCreate")

        handlerThread = HandlerThread("AutoLockThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registerHardwareInputReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[AutoLock] onStartCommand action=${intent?.action}, enabled=${AutoLockController.isEnabled(this)}")

        if (intent?.action == ACTION_DISABLE) {
            AutoLockController.setEnabled(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TOGGLE_SMART) {
            val nextMode = if (AutoLockController.getMode(this) == AutoLockController.MODE_BLOCKER_ONLY) {
                AutoLockController.MODE_ALWAYS
            } else {
                AutoLockController.MODE_BLOCKER_ONLY
            }
            AutoLockController.setMode(this, nextMode)
            updateNotification()
            return START_STICKY
        }

        if (intent?.action == ACTION_TOGGLE_PAUSE) {
            AutoLockController.setPauseMediaEnabled(
                this,
                !AutoLockController.isPauseMediaEnabled(this)
            )
            updateNotification()
            return START_STICKY
        }

        if (!AutoLockController.isEnabled(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground()

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == true) {
            if (shouldMonitor()) {
                startMonitoring()
            } else {
                stopMonitoring()
            }
            updateNotification()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (AutoLockController.isEnabled(this)) {
            startService(Intent(applicationContext, AutoLockService::class.java))
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopMonitoring()
        disposeInputMonitor()
        // Wait a bit for dispose to complete on handler thread before quitting
        handler?.post {
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
        }
        runCatching { unregisterReceiver(screenStateReceiver) }
        runCatching { unregisterReceiver(hardwareInputReceiver) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =====================================================================
    // Input Monitor — global listener for ALL input events
    // =====================================================================

    private fun createInputMonitor() {
        if (inputMonitor != null) return
        try {
            val im = getSystemService(Context.INPUT_SERVICE) as? InputManager
            if (im == null) {
                Log.e(TAG, "[AutoLock] InputManager unavailable")
                return
            }

            // InputManager.monitorGestureInput() — creates a global input monitor
            // that receives ALL input events. Requires INJECT_EVENTS permission.
            val monitorMethod = InputManager::class.java.getMethod(
                "monitorGestureInput", String::class.java, Int::class.javaPrimitiveType
            )
            inputMonitor = monitorMethod.invoke(im, "AutoLockMonitor", 0) as? android.view.InputMonitor

            if (inputMonitor == null) {
                Log.e(TAG, "[AutoLock] monitorInput() returned null")
                return
            }

            val channel: InputChannel? = inputMonitor?.getInputChannel()
            if (channel == null) {
                Log.e(TAG, "[AutoLock] InputMonitor returned null InputChannel")
                inputMonitor?.dispose()
                inputMonitor = null
                return
            }

            inputEventReceiver = object : InputEventReceiver(channel, handler!!.looper) {
                override fun onInputEvent(event: InputEvent) {
                    // ANY input event = user interaction → reset timer
                    if (running) {
                        val eventName = event::class.simpleName ?: "Unknown"
                        Log.d(TAG, "[AutoLock] Input event: $eventName, source=${event.source}")
                        resetTimer()
                    }
                    finishInputEvent(event, true)
                }
            }

            Log.i(TAG, "[AutoLock] InputMonitor created — global input detection active")
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "[AutoLock] monitorInput() not found on this API level", e)
            inputMonitor = null
        } catch (e: SecurityException) {
            Log.e(TAG, "[AutoLock] SecurityException — INJECT_EVENTS permission denied?", e)
            inputMonitor = null
        } catch (e: Exception) {
            Log.e(TAG, "[AutoLock] Failed to create InputMonitor", e)
            inputMonitor = null
        }
    }

    private fun disposeInputMonitor() {
        try {
            // Must dispose on the same Looper thread where InputEventReceiver was created
            handler?.post {
                try {
                    inputEventReceiver?.dispose()
                    inputEventReceiver = null
                    inputMonitor?.dispose()
                    inputMonitor = null
                    Log.d(TAG, "[AutoLock] InputMonitor disposed")
                } catch (e: Exception) {
                    Log.w(TAG, "[AutoLock] Error disposing InputMonitor on handler", e)
                }
            } ?: run {
                // Fallback if handler is already null
                inputEventReceiver?.dispose()
                inputEventReceiver = null
                inputMonitor?.dispose()
                inputMonitor = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "[AutoLock] Error disposing InputMonitor", e)
        }
    }

    // =====================================================================
    // Foreground notification
    // =====================================================================

    private fun promoteToForeground() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.auto_lock_title),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.auto_lock_summary)
                    setShowBadge(false)
                }
            )
        }

        val openSettingsIntent = Intent(this, AutoLockSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openSettingsPendingIntent = PendingIntent.getActivity(
            this, REQUEST_CODE_OPEN_SETTINGS, openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disableIntent = Intent(this, AutoLockService::class.java).apply {
            action = ACTION_DISABLE
        }
        val disablePendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_DISABLE, disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeoutSeconds = AutoLockController.getTimeoutSeconds(this)
        val timeoutText = AutoLockController.formatTimeout(this, timeoutSeconds)

        val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(R.drawable.ic_auto_lock_tile)
            .setContentTitle(getString(R.string.auto_lock_notification_title))
            .setContentText(getString(R.string.auto_lock_notification_text, timeoutText))
            .setContentIntent(openSettingsPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.auto_lock_notification_disable),
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
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote AutoLock service to foreground", e)
        }
    }

    // =====================================================================
    // Screen state handling
    // =====================================================================

    private fun handleScreenOn() {
        Log.d(TAG, "[AutoLock] SCREEN_ON received")
        if (AutoLockController.isEnabled(this) && shouldMonitor()) {
            startMonitoring()
        }
    }

    private fun handleScreenOff() {
        Log.d(TAG, "[AutoLock] SCREEN_OFF received")
        stopMonitoring()
    }

    // =====================================================================
    // Monitoring lifecycle
    // =====================================================================

    private fun startMonitoring() {
        if (running) {
            Log.d(TAG, "[AutoLock] Already running — resetting timer")
            resetTimer()
            return
        }
        running = true
        lastInteractionUptimeMs = SystemClock.elapsedRealtime()
        val timeoutSec = AutoLockController.getTimeoutSeconds(this)

        // Create global input monitor (detects touch, gestures)
        createInputMonitor()

        // Register volume change observer (detects volume keys)
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            lastVolumeValue = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1

            volumeObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    if (!running) return
                    try {
                        val currentVolume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
                        if (currentVolume != lastVolumeValue && lastVolumeValue != -1) {
                            Log.d(TAG, "[AutoLock] Volume changed: $lastVolumeValue → $currentVolume")
                            resetTimer()
                        }
                        lastVolumeValue = currentVolume
                    } catch (e: Exception) {
                        Log.w(TAG, "[AutoLock] Volume observer error", e)
                    }
                }
            }

            contentResolver.registerContentObserver(
                Settings.System.getUriFor("volume_music"),
                false,
                volumeObserver!!
            )
            Log.d(TAG, "[AutoLock] Volume observer registered, current volume=$lastVolumeValue")
        } catch (e: Exception) {
            Log.w(TAG, "[AutoLock] Failed to register volume observer", e)
        }

        // Start the exact timeout timer
        scheduleLock()

        // Start countdown UI updates
        handler?.postDelayed(countdownRunnable, 1000L)

        Log.i(TAG, "[AutoLock] *** MONITORING STARTED *** timeout=${timeoutSec}s, uptime=$lastInteractionUptimeMs, inputMonitor=${inputMonitor != null}")
    }

    private fun stopMonitoring() {
        running = false
        handler?.removeCallbacks(lockRunnable)
        handler?.removeCallbacks(countdownRunnable)

        // Unregister volume observer
        try {
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
            volumeObserver = null
        } catch (_: Exception) {}

        Log.i(TAG, "[AutoLock] *** MONITORING STOPPED ***")
        updateNotification()
    }

    private fun resetTimer() {
        if (!running) return
        val oldUptime = lastInteractionUptimeMs
        lastInteractionUptimeMs = SystemClock.elapsedRealtime()
        // Reschedule the exact lock timeout
        handler?.removeCallbacks(lockRunnable)
        scheduleLock()
        Log.d(TAG, "[AutoLock] Timer RESET — old uptime=$oldUptime, new uptime=$lastInteractionUptimeMs")
    }

    private fun scheduleLock() {
        val timeoutMs = AutoLockController.getTimeoutSeconds(this) * 1000L
        handler?.postDelayed(lockRunnable, timeoutMs)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val timeoutMs = AutoLockController.getTimeoutSeconds(this) * 1000L
        val remaining = ((timeoutMs - (SystemClock.elapsedRealtime() - lastInteractionUptimeMs)) / 1000)
            .toInt().coerceAtLeast(0)
        val mode = AutoLockController.getMode(this)
        val waitingForBlocker = mode == AutoLockController.MODE_BLOCKER_ONLY &&
                !screenOnBlockerActive
        val smartState = getString(
            if (mode == AutoLockController.MODE_BLOCKER_ONLY) {
                R.string.auto_lock_state_on
            } else {
                R.string.auto_lock_state_off
            }
        )
        val mediaState = getString(
            if (AutoLockController.isPauseMediaEnabled(this)) {
                R.string.auto_lock_state_on
            } else {
                R.string.auto_lock_state_off
            }
        )
        val content = if (waitingForBlocker) {
            getString(R.string.auto_lock_notification_waiting, smartState, mediaState)
        } else {
            getString(R.string.auto_lock_notification_countdown, remaining, smartState, mediaState)
        }
        val openSettings = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_SETTINGS,
            Intent(this, AutoLockSettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val smartIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE_SMART,
            Intent(this, AutoLockService::class.java).setAction(ACTION_TOGGLE_SMART),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE_PAUSE,
            Intent(this, AutoLockService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disableIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DISABLE,
            Intent(this, AutoLockService::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_auto_lock_tile)
            .setContentTitle(getString(R.string.auto_lock_notification_title))
            .setContentText(content)
            .setContentIntent(openSettings)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.auto_lock_notification_disable),
                    disableIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(
                        if (mode == AutoLockController.MODE_BLOCKER_ONLY) {
                            R.string.auto_lock_notification_smart_on
                        } else {
                            R.string.auto_lock_notification_smart_off
                        }
                    ),
                    smartIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(
                        if (AutoLockController.isPauseMediaEnabled(this)) {
                            R.string.auto_lock_notification_pause_on
                        } else {
                            R.string.auto_lock_notification_pause_off
                        }
                    ),
                    pauseIntent
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
            Log.e(TAG, "Unable to refresh AutoLock foreground notification", e)
        }
    }

    private fun registerHardwareInputReceiver() {
        try {
            val hwFilter = IntentFilter().apply {
                addAction(ACTION_HARDWARE_INPUT)
                addAction(ACTION_KEEP_SCREEN_ON_CHANGED)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_MEDIA_BUTTON)
            }
            ContextCompat.registerReceiver(
                this,
                hardwareInputReceiver,
                hwFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "[AutoLock] Hardware input receiver registered")
        } catch (e: Exception) {
            Log.w(TAG, "[AutoLock] Failed to register hardware input receiver", e)
        }
    }

    private fun shouldMonitor(): Boolean {
        return AutoLockController.getMode(this) != AutoLockController.MODE_BLOCKER_ONLY ||
                screenOnBlockerActive
    }

    companion object {
        private const val TAG = "AutoLockService"
        private const val NOTIFICATION_CHANNEL_ID = "auto_lock_channel"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE_OPEN_SETTINGS = 2001
        private const val REQUEST_CODE_DISABLE = 2002
        private const val ACTION_DISABLE = "org.pixel.customparts.action.AUTO_LOCK_DISABLE"
        private const val REQUEST_CODE_TOGGLE_SMART = 2003
        private const val REQUEST_CODE_TOGGLE_PAUSE = 2004
        private const val ACTION_TOGGLE_SMART = "org.pixel.customparts.action.AUTO_LOCK_TOGGLE_SMART"
        private const val ACTION_TOGGLE_PAUSE = "org.pixel.customparts.action.AUTO_LOCK_TOGGLE_PAUSE"
        private const val ACTION_HARDWARE_INPUT =
            "org.pixel.customparts.action.HARDWARE_INPUT"
        private const val ACTION_KEEP_SCREEN_ON_CHANGED =
            "org.pixel.customparts.action.KEEP_SCREEN_ON_CHANGED"
        private const val EXTRA_ACTIVE = "active"
        private const val EXTRA_PACKAGE = "package"
    }
}
