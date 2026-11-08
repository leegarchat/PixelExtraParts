package org.pixel.extra.parts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared switch row (guideline 6.9): level-2 nested card with a 1.dp adaptive
// outline. Plain toggles the whole row with a display-only Switch, Card
// toggles the whole row with an interactive Switch, SwitchOnlyCard leaves the
// row inert and toggles through the Switch alone.
enum class SettingSwitchRowVariant { Plain, Card, SwitchOnlyCard }

@Composable
fun SettingSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: SettingSwitchRowVariant = SettingSwitchRowVariant.Plain,
    shapeRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    horizontalSpacing: Dp = 12.dp,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    titleWeight: FontWeight = FontWeight.SemiBold,
    summaryStyle: TextStyle = MaterialTheme.typography.bodySmall,
    dimContentWhenDisabled: Boolean = false,
    disabledTitleColor: Color? = null,
    rowHaptic: HapticFeedbackType = HapticFeedbackType.ContextClick,
    switchHaptic: HapticFeedbackType = HapticFeedbackType.ContextClick,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(shapeRadius)
    val container = MaterialTheme.colorScheme.surfaceContainer
    val rowClickable = variant != SettingSwitchRowVariant.SwitchOnlyCard
    val switchInteractive = variant != SettingSwitchRowVariant.Plain
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (!enabled && dimContentWhenDisabled) 0.45f else 1f)
            .clip(shape)
            .background(container)
            .border(width = 1.dp, color = container.edgeOutline(), shape = shape)
            .then(
                if (rowClickable) {
                    Modifier.clickable(
                        role = Role.Switch,
                        enabled = enabled,
                        onClick = {
                            haptics.performHapticFeedback(rowHaptic)
                            onCheckedChange(!checked)
                        },
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight,
                color = if (!enabled && disabledTitleColor != null) {
                    disabledTitleColor
                } else {
                    Color.Unspecified
                },
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = summaryStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (switchInteractive) {
                {
                    haptics.performHapticFeedback(switchHaptic)
                    onCheckedChange(it)
                }
            } else {
                null
            },
            enabled = enabled,
        )
    }
}
