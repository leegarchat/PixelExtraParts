package org.pixel.extra.parts.systemui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Curated subset of the legacy built-in catalog (full IDs preserved):
// 0 disabled, 10 slide, 50 fade, 60 zoom, -1 custom theme APK.
private const val MODE_DISABLED = 0
private const val MODE_CUSTOM = -1
private const val MODE_SLIDE = 10
private const val MODE_FADE = 50
private const val MODE_ZOOM = 60

// Hub group for activity transitions: independent open/close mode rows,
// predictive-back toggle and the active custom theme package (read-only).
// The legacy compiler, file pickers and theme catalog are intentionally out
// of scope for this skeleton; only the mode ints and package names persist.
@Composable
internal fun TransitionGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var openMode by remember {
        mutableIntStateOf(readGlobalInt(context, SystemUiKeys.ACTIVITY_OPEN_TRANSITION, MODE_DISABLED))
    }
    var closeMode by remember {
        mutableIntStateOf(readGlobalInt(context, SystemUiKeys.ACTIVITY_CLOSE_TRANSITION, MODE_DISABLED))
    }
    var predictiveDisabled by remember {
        mutableStateOf(readGlobalInt(context, SystemUiKeys.DISABLE_PREDICTIVE_BACK, 0) != 0)
    }
    // Legacy fallback chain: per-side package first, then the shared one.
    val customPackage by remember {
        mutableStateOf(
            readGlobalString(context, SystemUiKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE)
                ?: readGlobalString(context, SystemUiKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE),
        )
    }

    fun applyOpen(mode: Int) {
        openMode = mode
        writeGlobalInt(context, SystemUiKeys.ACTIVITY_OPEN_TRANSITION, mode)
    }

    fun applyClose(mode: Int) {
        closeMode = mode
        writeGlobalInt(context, SystemUiKeys.ACTIVITY_CLOSE_TRANSITION, mode)
        // Legacy behavior: any close animation disables predictive back.
        if (mode != MODE_DISABLED && !predictiveDisabled) {
            predictiveDisabled = true
            writeGlobalInt(context, SystemUiKeys.DISABLE_PREDICTIVE_BACK, 1)
        }
    }

    val subtitle = stringResource(
        R.string.sysui_trans_status,
        stringResource(transitionModeLabel(openMode)),
        stringResource(transitionModeLabel(closeMode)),
    )

    val openOptions = transitionOptions()
    val closeOptions = transitionOptions()
    ExpandableGroupCard(
        title = stringResource(R.string.sysui_trans_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = subtitle,
        keyTag = "systemui_transition",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.sysui_trans_open_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ExpressiveSegmentedRow(
                items = openOptions,
                selectedItem = openOptions.firstOrNull { it.value == openMode }
                    ?: openOptions.first(),
                onSelect = { applyOpen(it.value) },
                labelProvider = { stringResource(it.labelRes) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.sysui_trans_close_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ExpressiveSegmentedRow(
                items = closeOptions,
                selectedItem = closeOptions.firstOrNull { it.value == closeMode }
                    ?: closeOptions.first(),
                onSelect = { applyClose(it.value) },
                labelProvider = { stringResource(it.labelRes) },
            )
        }
        SettingSwitchRow(
            title = stringResource(R.string.sysui_trans_predict_title),
            summary = stringResource(R.string.sysui_trans_predict_desc),
            checked = predictiveDisabled,
            onCheckedChange = {
                predictiveDisabled = it
                writeGlobalInt(context, SystemUiKeys.DISABLE_PREDICTIVE_BACK, if (it) 1 else 0)
            },
            variant = SettingSwitchRowVariant.Card,
        )
        Text(
            text = if (customPackage.isNullOrBlank()) {
                stringResource(R.string.sysui_pkg_none)
            } else {
                stringResource(R.string.sysui_pkg_current, customPackage.orEmpty())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun transitionOptions(): List<SegmentOption> = listOf(
    SegmentOption(MODE_DISABLED, R.string.sysui_trans_off),
    SegmentOption(MODE_SLIDE, R.string.sysui_trans_slide),
    SegmentOption(MODE_FADE, R.string.sysui_trans_fade),
    SegmentOption(MODE_ZOOM, R.string.sysui_trans_zoom),
    SegmentOption(MODE_CUSTOM, R.string.sysui_trans_custom),
)

private fun transitionModeLabel(mode: Int): Int = when (mode) {
    MODE_SLIDE -> R.string.sysui_trans_slide
    MODE_FADE -> R.string.sysui_trans_fade
    MODE_ZOOM -> R.string.sysui_trans_zoom
    MODE_CUSTOM -> R.string.sysui_trans_custom
    else -> R.string.sysui_trans_off
}
