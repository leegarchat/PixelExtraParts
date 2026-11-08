package org.pixel.extra.parts.overscroll

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.edgeOutline
import org.pixel.extra.parts.ui.sectionContentPadding

// Scroll-physics section: full profile editor rebuilt from scratch on top of
// the legacy Settings.Global keys. Master switch, preset/user profiles and
// domain slider groups (spring, deformation, pivots, normalization, system).
@Composable
fun OverscrollScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val resetLabel = stringResource(R.string.os_reset_label)

    var values by remember { mutableStateOf(OverscrollValues()) }
    var masterEnabled by remember { mutableStateOf(true) }
    var presets by remember { mutableStateOf(emptyList<OverscrollStoredProfile>()) }
    var saved by remember { mutableStateOf(emptyList<OverscrollStoredProfile>()) }
    var activeName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        values = OverscrollStore.loadValues(context)
        masterEnabled = OverscrollStore.isMasterEnabled(context)
        presets = OverscrollStore.loadPresets(context)
        saved = OverscrollStore.getSavedProfiles(context)
        activeName = OverscrollStore.getActiveProfileName(context)
    }

    fun refreshProfiles() {
        scope.launch {
            saved = OverscrollStore.getSavedProfiles(context)
            activeName = OverscrollStore.getActiveProfileName(context)
        }
    }

    // Every manual edit drops the active-profile marker, matching legacy.
    fun onFloat(key: String, value: Float) {
        val next = values.withFloat(key, value)
        values = next
        scope.launch {
            OverscrollStore.putFloat(context, key, value)
            OverscrollStore.clearActiveProfile(context)
            activeName = null
        }
    }

    fun onMode(key: String, mode: Int) {
        val next = values.withMode(key, mode)
        values = next
        scope.launch {
            OverscrollStore.putInt(context, key, mode)
            OverscrollStore.clearActiveProfile(context)
            activeName = null
        }
    }

    fun onToggle(key: String, checked: Boolean, apply: (OverscrollValues, Boolean) -> OverscrollValues) {
        values = apply(values, checked)
        scope.launch {
            OverscrollStore.putInt(context, key, if (checked) 1 else 0)
            OverscrollStore.clearActiveProfile(context)
            activeName = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__overscroll_header__") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tab_overscroll),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.os_header_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "master") {
            OverscrollMasterCard(
                checked = masterEnabled,
                onCheckedChange = { checked ->
                    masterEnabled = checked
                    scope.launch {
                        OverscrollStore.putMasterEnabled(context, checked)
                        OverscrollStore.clearActiveProfile(context)
                        activeName = null
                    }
                },
            )
        }
        item(key = "profiles") {
            OverscrollProfilesSection(
                presets = presets,
                saved = saved,
                activeName = activeName,
                onApply = { profile ->
                    scope.launch {
                        values = OverscrollStore.applyProfile(context, profile)
                        masterEnabled = OverscrollStore.isMasterEnabled(context)
                        refreshProfiles()
                    }
                },
                onSave = { name ->
                    scope.launch {
                        OverscrollStore.saveProfile(context, name)
                        refreshProfiles()
                    }
                },
                onDelete = { profile ->
                    scope.launch {
                        OverscrollStore.deleteProfile(context, profile)
                        refreshProfiles()
                    }
                },
            )
        }
        item(key = "physics") {
            OverscrollPhysicsGroup(
                values = values,
                masterEnabled = masterEnabled,
                resetLabel = resetLabel,
                onFloat = ::onFloat,
            )
        }
        item(key = "scale") {
            OverscrollScaleGroup(
                values = values,
                masterEnabled = masterEnabled,
                resetLabel = resetLabel,
                onFloat = ::onFloat,
                onMode = ::onMode,
            )
        }
        item(key = "anchors") {
            OverscrollAnchorsGroup(
                values = values,
                masterEnabled = masterEnabled,
                resetLabel = resetLabel,
                onFloat = ::onFloat,
                onToggleInvert = { onToggle(OverscrollKeys.INVERT_ANCHOR, it) { v, c -> v.copy(invertAnchor = c) } },
            )
        }
        item(key = "normalize") {
            OverscrollNormalizeGroup(
                values = values,
                masterEnabled = masterEnabled,
                resetLabel = resetLabel,
                onFloat = ::onFloat,
                onToggleEnabled = { onToggle(OverscrollKeys.NORM_ENABLED, it) { v, c -> v.copy(normEnabled = c) } },
                onMode = { onMode(OverscrollKeys.NORM_DETECT_MODE, it) },
            )
        }
        item(key = "system") {
            OverscrollSystemGroup(
                values = values,
                masterEnabled = masterEnabled,
                resetLabel = resetLabel,
                onFloat = ::onFloat,
                onToggleLogging = { onToggle(OverscrollKeys.LOGGING, it) { v, c -> v.copy(logging = c) } },
            )
        }
        item(key = "reset") {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        values = OverscrollStore.resetAll(context)
                        masterEnabled = true
                        refreshProfiles()
                        Toast.makeText(
                            context,
                            context.getString(R.string.os_reset_done),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = masterEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.os_reset))
            }
        }
    }
}

// Master switch card (level-1 surface, secondary tint kept from legacy).
@Composable
private fun OverscrollMasterCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)
    val container = MaterialTheme.colorScheme.secondaryContainer
    Surface(
        color = container,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, container.edgeOutline(), shape)
            .clickable(
                role = Role.Switch,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onCheckedChange(!checked)
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.os_master_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(
                        if (checked) R.string.os_status_on else R.string.os_status_off,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onCheckedChange(it)
                },
            )
        }
    }
}

// Immutable per-key updaters so the screen owns a single snapshot.
private fun OverscrollValues.withFloat(key: String, value: Float): OverscrollValues {
    return when (key) {
        OverscrollKeys.PULL -> copy(pull = value)
        OverscrollKeys.STIFFNESS -> copy(stiffness = value)
        OverscrollKeys.DAMPING -> copy(damping = value)
        OverscrollKeys.FLING -> copy(fling = value)
        OverscrollKeys.RESISTANCE_EXPONENT -> copy(resistanceExponent = value)
        OverscrollKeys.ANIM_SPEED -> copy(animSpeed = value)
        OverscrollKeys.SCALE_INTENSITY -> copy(scaleIntensity = value)
        OverscrollKeys.SCALE_INTENSITY_HORIZ -> copy(scaleIntensityHoriz = value)
        OverscrollKeys.SCALE_LIMIT_MIN -> copy(scaleLimitMin = value)
        OverscrollKeys.SCALE_ANCHOR_X -> copy(scaleAnchorX = value)
        OverscrollKeys.SCALE_ANCHOR_Y -> copy(scaleAnchorY = value)
        OverscrollKeys.SCALE_ANCHOR_X_HORIZ -> copy(scaleAnchorXHoriz = value)
        OverscrollKeys.SCALE_ANCHOR_Y_HORIZ -> copy(scaleAnchorYHoriz = value)
        OverscrollKeys.ZOOM_INTENSITY -> copy(zoomIntensity = value)
        OverscrollKeys.ZOOM_INTENSITY_HORIZ -> copy(zoomIntensityHoriz = value)
        OverscrollKeys.ZOOM_LIMIT_MIN -> copy(zoomLimitMin = value)
        OverscrollKeys.ZOOM_ANCHOR_X -> copy(zoomAnchorX = value)
        OverscrollKeys.ZOOM_ANCHOR_Y -> copy(zoomAnchorY = value)
        OverscrollKeys.ZOOM_ANCHOR_X_HORIZ -> copy(zoomAnchorXHoriz = value)
        OverscrollKeys.ZOOM_ANCHOR_Y_HORIZ -> copy(zoomAnchorYHoriz = value)
        OverscrollKeys.H_SCALE_INTENSITY -> copy(hScaleIntensity = value)
        OverscrollKeys.H_SCALE_INTENSITY_HORIZ -> copy(hScaleIntensityHoriz = value)
        OverscrollKeys.H_SCALE_LIMIT_MIN -> copy(hScaleLimitMin = value)
        OverscrollKeys.H_SCALE_ANCHOR_X -> copy(hScaleAnchorX = value)
        OverscrollKeys.H_SCALE_ANCHOR_Y -> copy(hScaleAnchorY = value)
        OverscrollKeys.H_SCALE_ANCHOR_X_HORIZ -> copy(hScaleAnchorXHoriz = value)
        OverscrollKeys.H_SCALE_ANCHOR_Y_HORIZ -> copy(hScaleAnchorYHoriz = value)
        OverscrollKeys.INPUT_SMOOTH -> copy(inputSmooth = value)
        OverscrollKeys.PHYSICS_MIN_VEL -> copy(physicsMinVel = value)
        OverscrollKeys.PHYSICS_MIN_VAL -> copy(physicsMinVal = value)
        OverscrollKeys.LERP_MAIN_IDLE -> copy(lerpMainIdle = value)
        OverscrollKeys.LERP_MAIN_RUN -> copy(lerpMainRun = value)
        OverscrollKeys.COMPOSE_SCALE -> copy(composeScale = value)
        OverscrollKeys.NORM_REF_DELTA -> copy(normRefDelta = value)
        OverscrollKeys.NORM_DETECT_MUL -> copy(normDetectMul = value)
        OverscrollKeys.NORM_FACTOR -> copy(normFactor = value)
        OverscrollKeys.NORM_WINDOW -> copy(normWindow = value)
        OverscrollKeys.NORM_RAMP -> copy(normRamp = value)
        else -> this
    }
}

private fun OverscrollValues.withMode(key: String, mode: Int): OverscrollValues {
    return when (key) {
        OverscrollKeys.SCALE_MODE -> copy(scaleMode = mode)
        OverscrollKeys.ZOOM_MODE -> copy(zoomMode = mode)
        OverscrollKeys.H_SCALE_MODE -> copy(hScaleMode = mode)
        OverscrollKeys.NORM_DETECT_MODE -> copy(normDetectMode = mode)
        else -> this
    }
}
