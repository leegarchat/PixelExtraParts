package org.pixel.extra.parts.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import org.pixel.extra.parts.ui.GroupCard
import org.pixel.extra.parts.ui.SettingSlider
import org.pixel.extra.parts.ui.SettingSwitchRow
import org.pixel.extra.parts.ui.SettingSwitchRowVariant

// Renderer for the declarative addon model: every node maps to exactly one
// shared kit component, so addons never ship their own widgets. Node state
// is read once from AddonStore and written back on every change.
@Composable
fun AddonNodesColumn(
    nodes: List<AddonNode>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        nodes.forEachIndexed { index, node ->
            key("$index:" + nodeKey(node)) {
                AddonNodeRow(node = node)
            }
        }
    }
}

// Stable composition key per node: kind plus persisted key or title.
private fun nodeKey(node: AddonNode): String = when (node) {
    is AddonNode.Toggle -> "toggle:" + node.key
    is AddonNode.Slider -> "slider:" + node.key
    is AddonNode.Segment -> "segment:" + node.key
    is AddonNode.Group -> "group:" + node.titleRes
}

@Composable
private fun AddonNodeRow(node: AddonNode) {
    when (node) {
        is AddonNode.Toggle -> AddonToggleRow(node = node)
        is AddonNode.Slider -> AddonSliderRow(node = node)
        is AddonNode.Segment -> AddonSegmentRow(node = node)
        is AddonNode.Group -> AddonGroupCard(node = node)
    }
}

@Composable
private fun AddonToggleRow(node: AddonNode.Toggle) {
    val context = LocalContext.current
    var checked by remember(node.key) {
        mutableStateOf(AddonStore.isEnabled(context, node.key, node.default))
    }
    SettingSwitchRow(
        title = stringResource(node.titleRes),
        summary = node.summaryRes?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = { value ->
            checked = value
            AddonStore.setEnabled(context, node.key, value)
        },
        variant = SettingSwitchRowVariant.Plain,
    )
}

@Composable
private fun AddonSliderRow(node: AddonNode.Slider) {
    val context = LocalContext.current
    var value by remember(node.key) {
        mutableIntStateOf(
            AddonStore.getInt(context, node.key, node.default).coerceIn(node.min, node.max),
        )
    }
    SettingSlider(
        title = stringResource(node.titleRes),
        value = value.toFloat(),
        onValueChange = { raw ->
            val next = raw.toInt().coerceIn(node.min, node.max)
            value = next
            AddonStore.putInt(context, node.key, next)
        },
        valueRange = node.min.toFloat()..node.max.toFloat(),
        step = node.step.toFloat(),
        defaultValue = node.default.toFloat(),
        resetLabel = stringResource(R.string.motion_reset),
        valueLabel = { raw ->
            when (node.unit) {
                AddonSliderUnit.PERCENT -> stringResource(R.string.fmt_pct, raw.toInt())
                AddonSliderUnit.MS -> stringResource(R.string.fmt_ms, raw.toInt())
                AddonSliderUnit.SEC -> stringResource(R.string.display_fmt_sec, raw.toInt())
                AddonSliderUnit.DP -> stringResource(R.string.fmt_dp, raw.toInt())
                AddonSliderUnit.RAW -> raw.toInt().toString()
            }
        },
    )
}

@Composable
private fun AddonSegmentRow(node: AddonNode.Segment) {
    val context = LocalContext.current
    var selected by remember(node.key) {
        mutableIntStateOf(AddonStore.getInt(context, node.key, node.default))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(node.titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ExpressiveSegmentedRow(
            items = node.options,
            selectedItem = node.options.firstOrNull { it.value == selected }
                ?: node.options.first(),
            onSelect = { choice ->
                selected = choice.value
                AddonStore.putInt(context, node.key, choice.value)
            },
            labelProvider = { stringResource(it.labelRes) },
        )
    }
}

@Composable
private fun AddonGroupCard(node: AddonNode.Group) {
    if (node.collapsible) {
        ExpandableGroupCard(
            title = stringResource(node.titleRes),
            chevronPainter = painterResource(R.drawable.ic_chevron_down),
            subtitle = node.subtitleRes?.let { stringResource(it) },
            keyTag = "addon_group_" + node.titleRes,
        ) {
            AddonNodesColumn(nodes = node.children)
        }
    } else {
        GroupCard(
            title = stringResource(node.titleRes),
            subtitle = node.subtitleRes?.let { stringResource(it) },
        ) {
            AddonNodesColumn(nodes = node.children)
        }
    }
}
