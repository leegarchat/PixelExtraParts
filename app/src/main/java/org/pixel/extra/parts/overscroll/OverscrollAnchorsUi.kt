package org.pixel.extra.parts.overscroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.ExpandableGroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Pivot-point domain: per-axis anchors for all three deformation kinds plus
// the smart-pivot mirror switch. Labels are shared across domains; the
// domain caption reuses the scale section titles.
@Composable
fun OverscrollAnchorsGroup(
    values: OverscrollValues,
    masterEnabled: Boolean,
    resetLabel: String,
    onFloat: (key: String, value: Float) -> Unit,
    onToggleInvert: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchorX = stringResource(R.string.os_anchor_x)
    val anchorY = stringResource(R.string.os_anchor_y)
    val anchorXH = stringResource(R.string.os_anchor_x_h)
    val anchorYH = stringResource(R.string.os_anchor_y_h)

    ExpandableGroupCard(
        title = stringResource(R.string.os_anchors_title),
        chevronPainter = painterResource(R.drawable.ic_chevron_down),
        subtitle = stringResource(
            R.string.os_anchors_preview,
            values.scaleAnchorX,
            values.scaleAnchorY,
        ),
        keyTag = "overscroll_anchors",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverscrollAnchorDomain(
                caption = stringResource(R.string.os_scale_vert),
                x = values.scaleAnchorX,
                y = values.scaleAnchorY,
                xHoriz = values.scaleAnchorXHoriz,
                yHoriz = values.scaleAnchorYHoriz,
                anchorX = anchorX,
                anchorY = anchorY,
                anchorXH = anchorXH,
                anchorYH = anchorYH,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                onX = { onFloat(OverscrollKeys.SCALE_ANCHOR_X, it) },
                onY = { onFloat(OverscrollKeys.SCALE_ANCHOR_Y, it) },
                onXHoriz = { onFloat(OverscrollKeys.SCALE_ANCHOR_X_HORIZ, it) },
                onYHoriz = { onFloat(OverscrollKeys.SCALE_ANCHOR_Y_HORIZ, it) },
            )
            OverscrollAnchorDomain(
                caption = stringResource(R.string.os_scale_zoom),
                x = values.zoomAnchorX,
                y = values.zoomAnchorY,
                xHoriz = values.zoomAnchorXHoriz,
                yHoriz = values.zoomAnchorYHoriz,
                anchorX = anchorX,
                anchorY = anchorY,
                anchorXH = anchorXH,
                anchorYH = anchorYH,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                onX = { onFloat(OverscrollKeys.ZOOM_ANCHOR_X, it) },
                onY = { onFloat(OverscrollKeys.ZOOM_ANCHOR_Y, it) },
                onXHoriz = { onFloat(OverscrollKeys.ZOOM_ANCHOR_X_HORIZ, it) },
                onYHoriz = { onFloat(OverscrollKeys.ZOOM_ANCHOR_Y_HORIZ, it) },
            )
            OverscrollAnchorDomain(
                caption = stringResource(R.string.os_scale_horz),
                x = values.hScaleAnchorX,
                y = values.hScaleAnchorY,
                xHoriz = values.hScaleAnchorXHoriz,
                yHoriz = values.hScaleAnchorYHoriz,
                anchorX = anchorX,
                anchorY = anchorY,
                anchorXH = anchorXH,
                anchorYH = anchorYH,
                resetLabel = resetLabel,
                enabled = masterEnabled,
                onX = { onFloat(OverscrollKeys.H_SCALE_ANCHOR_X, it) },
                onY = { onFloat(OverscrollKeys.H_SCALE_ANCHOR_Y, it) },
                onXHoriz = { onFloat(OverscrollKeys.H_SCALE_ANCHOR_X_HORIZ, it) },
                onYHoriz = { onFloat(OverscrollKeys.H_SCALE_ANCHOR_Y_HORIZ, it) },
            )
            SettingSwitchRow(
                title = stringResource(R.string.os_invert_anchor),
                summary = null,
                checked = values.invertAnchor,
                onCheckedChange = onToggleInvert,
                enabled = masterEnabled,
                variant = SettingSwitchRowVariant.Card,
            )
        }
    }
}

@Composable
private fun OverscrollAnchorDomain(
    caption: String,
    x: Float,
    y: Float,
    xHoriz: Float,
    yHoriz: Float,
    anchorX: String,
    anchorY: String,
    anchorXH: String,
    anchorYH: String,
    resetLabel: String,
    enabled: Boolean,
    onX: (Float) -> Unit,
    onY: (Float) -> Unit,
    onXHoriz: (Float) -> Unit,
    onYHoriz: (Float) -> Unit,
) {
    val anchor = OverscrollKeys.Defaults.ANCHOR
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        SettingSlider(
            title = anchorX,
            value = x,
            onValueChange = onX,
            valueRange = 0f..1f,
            step = 0.01f,
            defaultValue = anchor,
            resetLabel = resetLabel,
            enabled = enabled,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = anchorY,
            value = y,
            onValueChange = onY,
            valueRange = 0f..1f,
            step = 0.01f,
            defaultValue = anchor,
            resetLabel = resetLabel,
            enabled = enabled,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = anchorXH,
            value = xHoriz,
            onValueChange = onXHoriz,
            valueRange = 0f..1f,
            step = 0.01f,
            defaultValue = anchor,
            resetLabel = resetLabel,
            enabled = enabled,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
        SettingSlider(
            title = anchorYH,
            value = yHoriz,
            onValueChange = onYHoriz,
            valueRange = 0f..1f,
            step = 0.01f,
            defaultValue = anchor,
            resetLabel = resetLabel,
            enabled = enabled,
            valueLabel = { String.format(Locale.US, "%.2f", it) },
        )
    }
}
