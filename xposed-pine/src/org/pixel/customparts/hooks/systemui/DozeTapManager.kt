package org.pixel.customparts.hooks.systemui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import kotlin.math.abs

object DozeTapKeys {
    const val KEY_HOOK = "doze_double_tap_hook"
    const val KEY_TIMEOUT = "doze_double_tap_timeout"
    const val DEFAULT_TIMEOUT = 400
}

object DozeTapManager {
    private val handler = Handler(Looper.getMainLooper())

    private var doubleTapPending = false
    private var lastTapX = -1f
    private var lastTapY = -1f
    private var doubleTapSlop = -1

    private val timeoutRunnable = Runnable { resetState() }

    private fun resetState() {
        doubleTapPending = false
        lastTapX = -1f
        lastTapY = -1f
    }

    fun processTap(
        context: Context,
        x: Float,
        y: Float,
        isEnabled: Boolean,
        timeoutMs: Int,
        resetSensorAction: (() -> Unit)?
    ): Boolean {
        if (!isEnabled) return false

        if (doubleTapSlop < 0) {
            doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop
        }

        if (!doubleTapPending) {
            doubleTapPending = true
            lastTapX = x
            lastTapY = y

            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, timeoutMs.toLong())

            resetSensorAction?.invoke()
            return true
        }

        val dx = abs(x - lastTapX)
        val dy = abs(y - lastTapY)
        val invalidCoords = (x <= 0 && lastTapX <= 0)
        val isClose = invalidCoords || (dx < doubleTapSlop && dy < doubleTapSlop)

        return if (isClose) {
            resetState()
            handler.removeCallbacks(timeoutRunnable)
            false
        } else {
            lastTapX = x
            lastTapY = y

            handler.removeCallbacks(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, timeoutMs.toLong())

            resetSensorAction?.invoke()
            true
        }
    }
}
