package org.pixel.extra.parts.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// Owner of the float-bar tuning state, living inside the dock module.
// The dock reads its tuning from this holder (created via
// rememberFloatBarMotion) so dropping the dock into a screen needs no
// settings boilerplate; a host that also shows MotionTuningSheet hoists one
// holder call and shares it with both. Backed by MotionPrefs: loaded once,
// saved (plus JSON export mirror + logcat) on every change.
@Stable
class FloatBarMotion internal constructor(initial: MotionSettings) {
    var settings by mutableStateOf(initial)
        private set

    fun update(next: MotionSettings) {
        settings = next
    }

    fun reset() {
        settings = MotionSettings()
    }
}

@Composable
fun rememberFloatBarMotion(): FloatBarMotion {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val holder = remember(appContext) {
        FloatBarMotion(MotionPrefs.load(appContext))
    }
    val current = holder.settings
    LaunchedEffect(current) { MotionPrefs.save(appContext, current) }
    return holder
}
