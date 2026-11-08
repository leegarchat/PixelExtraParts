package org.pixel.extra.parts.overscroll

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.SettingSlider

// Surface deformation domains: vertical scale, zoom (XY) and horizontal
// scale. Each domain owns a mode segmented row (off / shrink / grow) plus
// intensity and clamp sliders; dependent sliders dim when the mode is off.
@Composable
fun OverscrollScaleGroup(
    values: OverscrollValues,
    masterEnabled: Boolean,
    resetLabel: String,
    onFloat: (key: String, value: Float) -> Unit,
    onMode: (key: String, mode: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modeOptions = listOf(
        stringResource(R.string.os_mode_off),
        stringResource(R.string.os_mode_shrink),
        stringResource(R.string.os_mode_grow),
    )
    val intensityLabel = stringResource(R.string.os_intensity)
    val intensityHLabel = stringResource(R.string.os_intensity_h)
    val limitLabel = stringResource(R.string.os_limit)
    val modeLabel = stringResource(R.string.os_mode)

    ExpandableGroupCard(
        title = stringResource(R.string.os_scale_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(
            R.string.os_scale_preview,
            modeName(modeOptions, values.scaleMode),
            values.scaleIntensity,
        ),
        keyTag = "overscroll_scale",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverscrollScaleDomain(
                domainTitle = stringResource(R.string.os_scale_vert),
                modeLabel = modeLabel,
                modeOptions = modeOptions,
                mode = values.scaleMode,
                intensity = values.scaleIntensity,
                intensityHoriz = values.scaleIntensityHoriz,
                limitMin = values.scaleLimitMin,
                intensityLabel = intensityLabel,
                intensityHLabel = intensityHLabel,
                limitLabel = limitLabel,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                onMode = { onMode(OverscrollKeys.SCALE_MODE, it) },
                onIntensity = { onFloat(OverscrollKeys.SCALE_INTENSITY, it) },
                onIntensityHoriz = { onFloat(OverscrollKeys.SCALE_INTENSITY_HORIZ, it) },
                onLimit = { onFloat(OverscrollKeys.SCALE_LIMIT_MIN, it) },
                defaultIntensity = OverscrollKeys.Defaults.SCALE_INTENSITY,
                defaultIntensityHoriz = OverscrollKeys.Defaults.SCALE_INTENSITY_HORIZ,
                defaultLimit = OverscrollKeys.Defaults.SCALE_LIMIT_MIN,
            )
            OverscrollScaleDomain(
                domainTitle = stringResource(R.string.os_scale_zoom),
                modeLabel = modeLabel,
                modeOptions = modeOptions,
                mode = values.zoomMode,
                intensity = values.zoomIntensity,
                intensityHoriz = values.zoomIntensityHoriz,
                limitMin = values.zoomLimitMin,
                intensityLabel = intensityLabel,
                intensityHLabel = intensityHLabel,
                limitLabel = limitLabel,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                onMode = { onMode(OverscrollKeys.ZOOM_MODE, it) },
                onIntensity = { onFloat(OverscrollKeys.ZOOM_INTENSITY, it) },
                onIntensityHoriz = { onFloat(OverscrollKeys.ZOOM_INTENSITY_HORIZ, it) },
                onLimit = { onFloat(OverscrollKeys.ZOOM_LIMIT_MIN, it) },
                defaultIntensity = OverscrollKeys.Defaults.ZOOM_INTENSITY,
                defaultIntensityHoriz = OverscrollKeys.Defaults.ZOOM_INTENSITY_HORIZ,
                defaultLimit = OverscrollKeys.Defaults.ZOOM_LIMIT_MIN,
            )
            OverscrollScaleDomain(
                domainTitle = stringResource(R.string.os_scale_horz),
                modeLabel = modeLabel,
                modeOptions = modeOptions,
                mode = values.hScaleMode,
                intensity = values.hScaleIntensity,
                intensityHoriz = values.hScaleIntensityHoriz,
                limitMin = values.hScaleLimitMin,
                intensityLabel = intensityLabel,
                intensityHLabel = intensityHLabel,
                limitLabel = limitLabel,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                onMode = { onMode(OverscrollKeys.H_SCALE_MODE, it) },
                onIntensity = { onFloat(OverscrollKeys.H_SCALE_INTENSITY, it) },
                onIntensityHoriz = { onFloat(OverscrollKeys.H_SCALE_INTENSITY_HORIZ, it) },
                onLimit = { onFloat(OverscrollKeys.H_SCALE_LIMIT_MIN, it) },
                defaultIntensity = OverscrollKeys.Defaults.H_SCALE_INTENSITY,
                defaultIntensityHoriz = OverscrollKeys.Defaults.H_SCALE_INTENSITY_HORIZ,
                defaultLimit = OverscrollKeys.Defaults.H_SCALE_LIMIT_MIN,
            )
        }
    }
}

private fun modeName(options: List<String>, mode: Int): String {
    return options.getOrElse(mode) { options.firstOrNull() ?: "" }
}

@Composable
private fun OverscrollScaleDomain(
    domainTitle: String,
    modeLabel: String,
    modeOptions: List<String>,
    mode: Int,
    intensity: Float,
    intensityHoriz: Float,
    limitMin: Float,
    intensityLabel: String,
    intensityHLabel: String,
    limitLabel: String,
    resetLabel: String,
    enabled: Boolean,
    onMode: (Int) -> Unit,
    onIntensity: (Float) -> Unit,
    onIntensityHoriz: (Float) -> Unit,
    onLimit: (Float) -> Unit,
    defaultIntensity: Float,
    defaultIntensityHoriz: Float,
    defaultLimit: Float,
) {
    val slidersActive = enabled && mode != 0
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = domainTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = modeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSegmentedRow(
                items = modeOptions,
                selectedItem = modeOptions.getOrElse(mode.coerceIn(0, modeOptions.lastIndex)) {
                    modeOptions.first()
                },
                onSelect = { onMode(modeOptions.indexOf(it)) },
                labelProvider = { it },
            )
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
        SettingSlider(
            title = intensityLabel,
            value = intensity,
            onValueChange = onIntensity,
            valueRange = 0f..10f,
            step = 0.05f,
            defaultValue = defaultIntensity,
            resetLabel = resetLabel,
            enabled = slidersActive,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = intensityHLabel,
            value = intensityHoriz,
            onValueChange = onIntensityHoriz,
            valueRange = 0f..10f,
            step = 0.05f,
            defaultValue = defaultIntensityHoriz,
            resetLabel = resetLabel,
            enabled = slidersActive,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = limitLabel,
            value = limitMin,
            onValueChange = onLimit,
            valueRange = 0.1f..10f,
            step = 0.05f,
            defaultValue = defaultLimit,
            resetLabel = resetLabel,
            enabled = slidersActive,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
    }
}
