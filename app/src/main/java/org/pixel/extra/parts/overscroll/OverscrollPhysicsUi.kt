package org.pixel.extra.parts.overscroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Spring core + global speed (pull, stiffness, damping, fling, resistance
// curve, animation speed) and the low-level system pipeline group.
@Composable
fun OverscrollPhysicsGroup(
    values: OverscrollValues,
    masterEnabled: Boolean,
    resetLabel: String,
    onFloat: (key: String, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpandableGroupCard(
        title = stringResource(R.string.os_physics_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(
            R.string.os_physics_preview,
            values.pull,
            values.stiffness,
            values.damping,
            values.fling,
        ),
        keyTag = "overscroll_physics",
        modifier = modifier,
    ) {
        SettingSlider(
            title = stringResource(R.string.os_pull),
            value = values.pull,
            onValueChange = { onFloat(OverscrollKeys.PULL, it) },
            valueRange = 0.1f..3.0f,
            step = 0.05f,
            defaultValue = OverscrollKeys.Defaults.PULL,
            resetLabel = resetLabel,
            enabled = masterEnabled,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = stringResource(R.string.os_stiffness),
            value = values.stiffness,
            onValueChange = { onFloat(OverscrollKeys.STIFFNESS, it) },
            valueRange = 10f..1000f,
            step = 1f,
            defaultValue = OverscrollKeys.Defaults.STIFFNESS,
            resetLabel = resetLabel,
            enabled = masterEnabled,
            valueLabel = { String.format(Locale.US, "%.1f", it) },
        )
        SettingSlider(
            title = stringResource(R.string.os_damping),
            value = values.damping,
            onValueChange = { onFloat(OverscrollKeys.DAMPING, it) },
            valueRange = 0.1f..2.0f,
            step = 0.01f,
            defaultValue = OverscrollKeys.Defaults.DAMPING,
            resetLabel = resetLabel,
            enabled = masterEnabled,
            valueLabel = { String.format(Locale.US, "%.3f", it) },
        )
        SettingSlider(
            title = stringResource(R.string.os_fling),
            value = values.fling,
            onValueChange = { onFloat(OverscrollKeys.FLING, it) },
            valueRange = 0.1f..3.0f,
            step = 0.05f,
            defaultValue = OverscrollKeys.Defaults.FLING,
            resetLabel = resetLabel,
            enabled = masterEnabled,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = stringResource(R.string.os_res_exp),
            value = values.resistanceExponent,
            onValueChange = { onFloat(OverscrollKeys.RESISTANCE_EXPONENT, it) },
            valueRange = 1f..8f,
            step = 0.1f,
            defaultValue = OverscrollKeys.Defaults.RESISTANCE_EXPONENT,
            resetLabel = resetLabel,
            enabled = masterEnabled,
            valueLabel = { String.format(Locale.US, "%.1f", it) },
        )
        SettingSlider(
            title = stringResource(R.string.os_anim_speed),
            value = values.animSpeed,
            onValueChange = { onFloat(OverscrollKeys.ANIM_SPEED, it) },
            valueRange = 1f..300f,
            step = 1f,
            defaultValue = OverscrollKeys.Defaults.ANIM_SPEED,
            resetLabel = resetLabel,
            enabled = masterEnabled,
            valueLabel = { String.format(Locale.US, "%.0f", it) },
        )
    }
}

@Composable
fun OverscrollSystemGroup(
    values: OverscrollValues,
    masterEnabled: Boolean,
    resetLabel: String,
    onFloat: (key: String, value: Float) -> Unit,
    onToggleLogging: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpandableGroupCard(
        title = stringResource(R.string.os_system_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(
            R.string.os_system_preview,
            values.inputSmooth,
            values.composeScale,
        ),
        keyTag = "overscroll_system",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingSlider(
                title = stringResource(R.string.os_input_smooth),
                value = values.inputSmooth,
                onValueChange = { onFloat(OverscrollKeys.INPUT_SMOOTH, it) },
                valueRange = 0f..0.95f,
                step = 0.01f,
                defaultValue = OverscrollKeys.Defaults.INPUT_SMOOTH,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_min_vel),
                value = values.physicsMinVel,
                onValueChange = { onFloat(OverscrollKeys.PHYSICS_MIN_VEL, it) },
                valueRange = 0f..400f,
                step = 1f,
                defaultValue = OverscrollKeys.Defaults.PHYSICS_MIN_VEL,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                valueLabel = { String.format(Locale.US, "%.0f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_min_val),
                value = values.physicsMinVal,
                onValueChange = { onFloat(OverscrollKeys.PHYSICS_MIN_VAL, it) },
                valueRange = 0f..20f,
                step = 0.1f,
                defaultValue = OverscrollKeys.Defaults.PHYSICS_MIN_VAL,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                valueLabel = { String.format(Locale.US, "%.1f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_lerp_idle),
                value = values.lerpMainIdle,
                onValueChange = { onFloat(OverscrollKeys.LERP_MAIN_IDLE, it) },
                valueRange = 0f..1f,
                step = 0.01f,
                defaultValue = OverscrollKeys.Defaults.LERP_MAIN_IDLE,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_lerp_run),
                value = values.lerpMainRun,
                onValueChange = { onFloat(OverscrollKeys.LERP_MAIN_RUN, it) },
                valueRange = 0f..1f,
                step = 0.01f,
                defaultValue = OverscrollKeys.Defaults.LERP_MAIN_RUN,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
            )
            SettingSlider(
                title = stringResource(R.string.os_compose_scale),
                value = values.composeScale,
                onValueChange = { onFloat(OverscrollKeys.COMPOSE_SCALE, it) },
                valueRange = 0.01f..10.0f,
                step = 0.05f,
                defaultValue = OverscrollKeys.Defaults.COMPOSE_SCALE,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                valueLabel = { String.format(Locale.US, "%.2f", it) },
            )
            SettingSwitchRow(
                title = stringResource(R.string.os_logging),
                summary = null,
                checked = values.logging,
                onCheckedChange = onToggleLogging,
                enabled = masterEnabled,
                variant = SettingSwitchRowVariant.Card,
            )
        }
    }
}
