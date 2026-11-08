package org.pixel.extra.parts.display

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Saturation card: enable switch plus a 0-200% level slider applied
// immediately through SurfaceFlinger. The preview pager and QS tile entry
// from the legacy screen are dropped; only the tuning knobs are kept.
@Composable
fun SaturationCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(DisplayStore.isEnabled(context, DisplayKeys.SATURATION_ENABLED))
    }
    var percent by remember {
        mutableIntStateOf(
            DisplayStore.getInt(context, DisplayKeys.SATURATION_PERCENT, DisplayKeys.SATURATION_DEFAULT)
                .coerceIn(DisplayKeys.SATURATION_MIN, DisplayKeys.SATURATION_MAX),
        )
    }
    val applyFailedText = stringResource(R.string.saturation_apply_failed)

    GroupCard(
        title = stringResource(R.string.saturation_title),
        subtitle = stringResource(R.string.saturation_summary),
        modifier = modifier,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.saturation_enable_title),
            summary = stringResource(R.string.saturation_enable_summary),
            checked = enabled,
            onCheckedChange = { checked ->
                enabled = checked
                if (!DisplayStore.writeSaturation(context, checked, percent)) {
                    Toast.makeText(context, applyFailedText, Toast.LENGTH_SHORT).show()
                }
            },
            variant = SettingSwitchRowVariant.Plain,
            dimContentWhenDisabled = true,
            horizontalSpacing = 8.dp,
        )
        SettingSlider(
            title = stringResource(R.string.saturation_level_title),
            value = percent.toFloat(),
            onValueChange = { value ->
                percent = value.toInt().coerceIn(DisplayKeys.SATURATION_MIN, DisplayKeys.SATURATION_MAX)
                DisplayStore.writeSaturation(context, enabled, percent)
            },
            enabled = enabled,
            valueRange = DisplayKeys.SATURATION_MIN.toFloat()..DisplayKeys.SATURATION_MAX.toFloat(),
            step = 1f,
            defaultValue = DisplayKeys.SATURATION_DEFAULT.toFloat(),
            resetLabel = stringResource(R.string.motion_reset),
            valueLabel = { v -> stringResource(R.string.fmt_pct, v.toInt()) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.saturation_footer_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
