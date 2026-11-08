package org.pixel.extra.parts.activities

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.extra.parts.ui.legacy.InfoDialog
import org.pixel.extra.parts.ui.legacy.RadioSelectionGroup
import org.pixel.extra.parts.ui.legacy.SliderSettingFloat
import org.pixel.extra.parts.R
import org.pixel.extra.parts.dynamicDarkColorScheme
import org.pixel.extra.parts.dynamicLightColorScheme
import org.pixel.extra.parts.services.OverscrollTileService
import org.pixel.extra.parts.ui.legacy.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.extra.parts.ui.legacy.RebootBubble
import org.pixel.extra.parts.ui.legacy.RebootBubbleMenuAction
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState
import org.pixel.extra.parts.utils.TileUtils


@Composable
internal fun MasterSwitchCard(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isChecked) stringResource(R.string.os_status_active) else stringResource(R.string.os_status_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onInfoClick, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Outlined.Info, "Info", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)

        }
    }
}

@Composable
internal fun StaticTestCard(label: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.height(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                stringResource(R.string.os_card_example_text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
internal fun OverscrollFloatSlider(
    context: Context,
    title: String,
    key: String,
    range: ClosedFloatingPointRange<Float>,
    defVal: Float,
    infoText: String,
    video: String,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    enabled: Boolean = true,
    onChange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var value by remember(refreshKey) { mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver, key, defVal)) }

    SliderSettingFloat(
        title = title,
        value = value,
        range = range,
        unit = "",
        enabled = enabled,
        onValueChange = {
            value = it
            scope.launch(Dispatchers.IO) {
                Settings.Global.putFloat(context.contentResolver, key, it)
                launch(Dispatchers.Main) { onChange() }
            }
        },
        onDefault = {
            value = defVal
            scope.launch(Dispatchers.IO) {
                Settings.Global.putFloat(context.contentResolver, key, defVal)
                launch(Dispatchers.Main) { onChange() }
            }
        },
        infoText = infoText,
        videoResName = video,
        onInfoClick = onInfo
    )
}

@Composable
internal fun ScaleGroup(
    context: Context,
    title: String,
    prefix: String,
    desc: String,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    isMasterEnabled: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onChange: () -> Unit,
    defIntensity: Float = 0f,
    defIntensityHoriz: Float = 0f,
    defLimitMin: Float = 0.1f
) {
    ExpandableSettingsGroupCard(
        title = title,
        enabled = isMasterEnabled,
        expanded = expanded,
        onExpandChange = onExpandChange
    ) {
        val scope = rememberCoroutineScope()
        // LSPlant runtime suffix for all overscroll keys.

        val modeKey = "${prefix}_mode"
        var mode by remember(refreshKey) {
            mutableIntStateOf(Settings.Global.getInt(context.contentResolver, modeKey, 0))
        }
        val modes = listOf(
            stringResource(R.string.os_mode_off),
            stringResource(R.string.os_mode_shrink),
            stringResource(R.string.os_mode_grow)
            )

        val areSlidersActive = isMasterEnabled && (mode != 0)

        RadioSelectionGroup(
            title = stringResource(R.string.os_lbl_scale_mode),
            options = modes,
            selectedIndex = mode,
            enabled = isMasterEnabled,
            onSelect = {
                mode = it
                scope.launch(Dispatchers.IO) {
                    Settings.Global.putInt(context.contentResolver, modeKey, it)
                    launch(Dispatchers.Main) { onChange() }
                }
            },
            infoText = desc,
            videoResName = "${prefix}_mode",
            onInfoClick = onInfo
        )

        OverscrollFloatSlider(context, stringResource(R.string.os_lbl_scale_int), "${prefix}_intensity", 0f..10f, defIntensity,
            infoText = stringResource(R.string.os_desc_scale_intensity), video = "${prefix}_intensity", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)

        OverscrollFloatSlider(context, stringResource(R.string.os_lbl_scale_int_horiz), "${prefix}_intensity_horiz", 0f..10f, defIntensityHoriz,
            infoText = stringResource(R.string.os_desc_scale_int_horiz), video = "${prefix}_intensity_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)

        OverscrollFloatSlider(context, stringResource(R.string.os_lbl_scale_limit), "${prefix}_limit_min", 0.1f..10f, defLimitMin,
            infoText = stringResource(R.string.os_desc_scale_limit), video = "${prefix}_intensity_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)

        val anchorTitle = stringResource(R.string.os_desc_anchor)

        when (prefix) {
            "overscroll_scale" -> {
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_y), "overscroll_scale_anchor_y", 0f..1f, 0.5f,
                    infoText = anchorTitle, video = "overscroll_scale_anchor_y", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_x_horiz), "overscroll_scale_anchor_x_horiz", 0f..1f, 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_x_horiz), video = "overscroll_scale_anchor_x_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
            }
            "overscroll_h_scale" -> {
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_x), "overscroll_h_scale_anchor_x", 0f..1f, 0.5f,
                    infoText = anchorTitle, video = "overscroll_h_scale_anchor_x", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_y_horiz), "overscroll_h_scale_anchor_y_horiz", 0f..1f, 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_y_horiz), video = "overscroll_h_scale_anchor_y_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
            }
            "overscroll_zoom" -> {
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_x), "overscroll_zoom_anchor_x", 0f..1f, 0.5f,
                    infoText = anchorTitle, video = "overscroll_zoom_anchor_x", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_y), "overscroll_zoom_anchor_y", 0f..1f, 0.5f,
                    infoText = anchorTitle, video = "overscroll_zoom_anchor_y", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)

                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_x_horiz), "overscroll_zoom_anchor_x_horiz", 0f..1f, 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_x_horiz), video = "overscroll_zoom_anchor_x_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, stringResource(R.string.os_lbl_anchor_y_horiz), "overscroll_zoom_anchor_y_horiz", 0f..1f, 0.5f,
                    infoText = stringResource(R.string.os_desc_anchor_y_horiz), video = "overscroll_zoom_anchor_y_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
            }
        }
    }
}