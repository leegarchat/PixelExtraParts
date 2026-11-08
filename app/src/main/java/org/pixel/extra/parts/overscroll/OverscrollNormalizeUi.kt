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
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.ExpressiveSegmentedRow
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Compose delta-normalization domain: master switch, detection-mode
// segmented row and the five tuning sliders. The pipeline sliders dim when
// normalization itself is switched off.
@Composable
fun OverscrollNormalizeGroup(
    values: OverscrollValues,
    masterEnabled: Boolean,
    resetLabel: String,
    onFloat: (key: String, value: Float) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detectOptions = listOf(
        stringResource(R.string.os_norm_behavior),
        stringResource(R.string.os_norm_hybrid),
        stringResource(R.string.os_norm_stack),
    )
    val pipelineEnabled = masterEnabled && values.normEnabled
    ExpandableGroupCard(
        title = stringResource(R.string.os_norm_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(
            R.string.os_norm_preview,
            detectOptions.getOrElse(values.normDetectMode) { detectOptions[1] },
            values.normFactor,
        ),
        keyTag = "overscroll_normalize",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.os_norm_enabled),
                summary = null,
                checked = values.normEnabled,
                onCheckedChange = onToggleEnabled,
                enabled = masterEnabled,
                variant = SettingSwitchRowVariant.Card,
            )
            Text(
                text = stringResource(R.string.os_norm_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSegmentedRow(
                    items = detectOptions,
                    selectedItem = detectOptions.getOrElse(
                        values.normDetectMode.coerceIn(0, detectOptions.lastIndex),
                    ) { detectOptions[1] },
                    onSelect = { onMode(detectOptions.indexOf(it)) },
                    labelProvider = { it },
                )
                if (!pipelineEnabled) {
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
                title = stringResource(R.string.os_norm_ref),
                value = values.normRefDelta,
                onValueChange = { onFloat(OverscrollKeys.NORM_REF_DELTA, it) },
                valueRange = 0.000001f..100f,
                step = 0.5f,
                defaultValue = OverscrollKeys.Defaults.NORM_REF_DELTA,
                resetLabel = resetLabel,
                enabled = pipelineEnabled,
                valueLabel = { String.format(Locale.US, "%.4f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_norm_mul),
                value = values.normDetectMul,
                onValueChange = { onFloat(OverscrollKeys.NORM_DETECT_MUL, it) },
                valueRange = 0f..10f,
                step = 0.1f,
                defaultValue = OverscrollKeys.Defaults.NORM_DETECT_MUL,
                resetLabel = resetLabel,
                enabled = pipelineEnabled,
                valueLabel = { String.format(Locale.US, "%.1f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_norm_factor),
                value = values.normFactor,
                onValueChange = { onFloat(OverscrollKeys.NORM_FACTOR, it) },
                valueRange = 0.01f..1f,
                step = 0.01f,
                defaultValue = OverscrollKeys.Defaults.NORM_FACTOR,
                resetLabel = resetLabel,
                enabled = pipelineEnabled,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_norm_window),
                value = values.normWindow,
                onValueChange = { onFloat(OverscrollKeys.NORM_WINDOW, it) },
                valueRange = 2f..64f,
                step = 1f,
                defaultValue = OverscrollKeys.Defaults.NORM_WINDOW,
                resetLabel = resetLabel,
                enabled = pipelineEnabled,
                valueLabel = { String.format(Locale.US, "%.0f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_norm_ramp),
                value = values.normRamp,
                onValueChange = { onFloat(OverscrollKeys.NORM_RAMP, it) },
                valueRange = 1f..20f,
                step = 1f,
                defaultValue = OverscrollKeys.Defaults.NORM_RAMP,
                resetLabel = resetLabel,
                enabled = pipelineEnabled,
                valueLabel = { String.format(Locale.US, "%.0f", it) },
            )
        }
    }
}
