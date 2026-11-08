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

// Curated subset of the legacy rotation catalog (full IDs preserved):
// 0 default, 4 zoom, 5 slide, 7 fade, -1 custom theme APK.
private const val STYLE_DEFAULT = 0
private const val STYLE_CUSTOM = -1
private const val STYLE_ZOOM = 4
private const val STYLE_SLIDE = 5
private const val STYLE_FADE = 7

// Hub group for the rotation animation: style segmented row plus the active
// custom theme package (read-only). Import/compile flows from the legacy
// activity are out of scope; only the style int and package name persist.
@Composable
internal fun RotationGroup(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var style by remember {
        mutableIntStateOf(readGlobalInt(context, SystemUiKeys.ROTATION_STYLE, STYLE_DEFAULT))
    }
    val customPackage by remember {
        mutableStateOf(readGlobalString(context, SystemUiKeys.ROTATION_CUSTOM_PACKAGE))
    }

    val rotationOptions = listOf(
        SegmentOption(STYLE_DEFAULT, R.string.sysui_rot_default),
        SegmentOption(STYLE_ZOOM, R.string.sysui_rot_zoom),
        SegmentOption(STYLE_SLIDE, R.string.sysui_rot_slide),
        SegmentOption(STYLE_FADE, R.string.sysui_rot_fade),
        SegmentOption(STYLE_CUSTOM, R.string.sysui_rot_custom),
    )

    ExpandableGroupCard(
        title = stringResource(R.string.sysui_rot_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(R.string.sysui_rot_status, stringResource(rotationStyleLabel(style))),
        keyTag = "systemui_rotation",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.sysui_rot_style_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ExpressiveSegmentedRow(
                items = rotationOptions,
                selectedItem = rotationOptions.firstOrNull { it.value == style }
                    ?: rotationOptions.first(),
                onSelect = {
                    style = it.value
                    writeGlobalInt(context, SystemUiKeys.ROTATION_STYLE, it.value)
                },
                labelProvider = { stringResource(it.labelRes) },
            )
        }
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

private fun rotationStyleLabel(style: Int): Int = when (style) {
    STYLE_ZOOM -> R.string.sysui_rot_zoom
    STYLE_SLIDE -> R.string.sysui_rot_slide
    STYLE_FADE -> R.string.sysui_rot_fade
    STYLE_CUSTOM -> R.string.sysui_rot_custom
    else -> R.string.sysui_rot_default
}
