package org.pixel.extra.parts.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Auto-lock card: enable switch, segmented always/smart mode, pause-media
// switch and an inactivity timeout slider. A receiver keeps the knobs in
// sync with the firmware service (tiles or the service itself may change
// the state); every local change is published back on the same contract.
@Composable
fun AutoLockCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by remember {
        mutableStateOf(
            DisplayStore.getInt(context, DisplayKeys.AUTO_LOCK_MODE, DisplayKeys.AUTO_LOCK_MODE_OFF)
                .coerceIn(DisplayKeys.AUTO_LOCK_MODE_OFF, DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY),
        )
    }
    var pauseMedia by remember {
        mutableStateOf(DisplayStore.isEnabled(context, DisplayKeys.AUTO_LOCK_PAUSE_MEDIA))
    }
    var timeoutSeconds by remember {
        mutableIntStateOf(
            DisplayStore.getInt(
                context,
                DisplayKeys.AUTO_LOCK_TIMEOUT_SECONDS,
                DisplayKeys.AUTO_LOCK_TIMEOUT_DEFAULT_S,
            ).coerceIn(DisplayKeys.AUTO_LOCK_TIMEOUT_MIN_S, DisplayKeys.AUTO_LOCK_TIMEOUT_MAX_S),
        )
    }
    val enabled = mode != DisplayKeys.AUTO_LOCK_MODE_OFF
    val blockerOnly = mode == DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY

    // External changes (tiles, service) arrive on the firmware broadcast.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mode = DisplayStore.getInt(
                    context,
                    DisplayKeys.AUTO_LOCK_MODE,
                    DisplayKeys.AUTO_LOCK_MODE_OFF,
                ).coerceIn(DisplayKeys.AUTO_LOCK_MODE_OFF, DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY)
                pauseMedia = DisplayStore.isEnabled(context, DisplayKeys.AUTO_LOCK_PAUSE_MEDIA)
                timeoutSeconds = DisplayStore.getInt(
                    context,
                    DisplayKeys.AUTO_LOCK_TIMEOUT_SECONDS,
                    DisplayKeys.AUTO_LOCK_TIMEOUT_DEFAULT_S,
                ).coerceIn(DisplayKeys.AUTO_LOCK_TIMEOUT_MIN_S, DisplayKeys.AUTO_LOCK_TIMEOUT_MAX_S)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DisplayStore.AUTO_LOCK_ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    GroupCard(
        title = stringResource(R.string.auto_lock_title),
        subtitle = stringResource(R.string.auto_lock_summary),
        modifier = modifier,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.auto_lock_enable_title),
            summary = stringResource(R.string.auto_lock_enable_summary),
            checked = enabled,
            onCheckedChange = { checked ->
                mode = if (checked) {
                    if (blockerOnly) DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY
                    else DisplayKeys.AUTO_LOCK_MODE_ALWAYS
                } else {
                    DisplayKeys.AUTO_LOCK_MODE_OFF
                }
                writeAutoLockMode(context, mode)
            },
            variant = SettingSwitchRowVariant.Plain,
            dimContentWhenDisabled = true,
            horizontalSpacing = 8.dp,
        )
        Text(
            text = stringResource(R.string.auto_lock_timer_section),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
                ExpressiveSegmentedRow(
                    items = listOf(DisplayKeys.AUTO_LOCK_MODE_ALWAYS, DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY),
                    selectedItem = if (blockerOnly) {
                        DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY
                    } else {
                        DisplayKeys.AUTO_LOCK_MODE_ALWAYS
                    },
                    onSelect = { selected ->
                        mode = selected
                        writeAutoLockMode(context, selected)
                    },
                    labelProvider = { item ->
                        if (item == DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY) {
                            stringResource(R.string.auto_lock_blocker_only_title)
                        } else {
                            stringResource(R.string.display_mode_always)
                        }
                    },
                )
            }
            if (!enabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
        }
        Text(
            text = stringResource(R.string.auto_lock_blocker_only_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SettingSwitchRow(
            title = stringResource(R.string.auto_lock_pause_media_title),
            summary = stringResource(R.string.auto_lock_pause_media_summary),
            checked = pauseMedia,
            enabled = enabled,
            onCheckedChange = { checked ->
                pauseMedia = checked
                DisplayStore.setEnabled(context, DisplayKeys.AUTO_LOCK_PAUSE_MEDIA, checked)
            },
            variant = SettingSwitchRowVariant.Plain,
            dimContentWhenDisabled = true,
            horizontalSpacing = 8.dp,
        )
        SettingSlider(
            title = stringResource(R.string.auto_lock_timeout_title),
            value = timeoutSeconds.toFloat(),
            onValueChange = { value ->
                timeoutSeconds = value.toInt()
                    .coerceIn(DisplayKeys.AUTO_LOCK_TIMEOUT_MIN_S, DisplayKeys.AUTO_LOCK_TIMEOUT_MAX_S)
                DisplayStore.putInt(context, DisplayKeys.AUTO_LOCK_TIMEOUT_SECONDS, timeoutSeconds)
                DisplayStore.publishAutoLockState(context, enabled, timeoutSeconds)
            },
            enabled = enabled,
            valueRange = DisplayKeys.AUTO_LOCK_TIMEOUT_MIN_S.toFloat()..
                DisplayKeys.AUTO_LOCK_TIMEOUT_MAX_S.toFloat(),
            step = 10f,
            defaultValue = DisplayKeys.AUTO_LOCK_TIMEOUT_DEFAULT_S.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
            valueLabel = { v -> formatAutoLockTimeout(context, v.toInt()) },
        )
        Text(
            text = stringResource(R.string.auto_lock_timeout_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private fun writeAutoLockMode(context: Context, mode: Int) {
    val normalized = mode.coerceIn(DisplayKeys.AUTO_LOCK_MODE_OFF, DisplayKeys.AUTO_LOCK_MODE_BLOCKER_ONLY)
    DisplayStore.putInt(context, DisplayKeys.AUTO_LOCK_MODE, normalized)
    DisplayStore.setEnabled(
        context,
        DisplayKeys.AUTO_LOCK_ENABLED,
        normalized != DisplayKeys.AUTO_LOCK_MODE_OFF,
    )
    DisplayStore.publishAutoLockState(
        context,
        normalized != DisplayKeys.AUTO_LOCK_MODE_OFF,
        DisplayStore.getInt(
            context,
            DisplayKeys.AUTO_LOCK_TIMEOUT_SECONDS,
            DisplayKeys.AUTO_LOCK_TIMEOUT_DEFAULT_S,
        ),
    )
}

// Localized timeout label ported from the legacy controller:
// below a minute plain seconds, otherwise "total / human" form.
private fun formatAutoLockTimeout(context: Context, seconds: Int): String {
    if (seconds < 60) {
        return context.getString(R.string.auto_lock_time_seconds, seconds)
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    val total = context.getString(R.string.auto_lock_time_seconds, seconds)
    val parts = mutableListOf<String>()
    if (hours > 0) {
        parts.add(context.getString(R.string.auto_lock_time_hours, hours))
    }
    if (minutes > 0) {
        parts.add(context.getString(R.string.auto_lock_time_minutes, minutes))
    }
    if (secs > 0) {
        parts.add(context.getString(R.string.auto_lock_time_seconds, secs))
    }
    return context.getString(R.string.auto_lock_time_format, total, parts.joinToString(" "))
}
