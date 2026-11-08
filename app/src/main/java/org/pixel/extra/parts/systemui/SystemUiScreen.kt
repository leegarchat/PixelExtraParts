package org.pixel.extra.parts.systemui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.sectionContentPadding

// SystemUI settings hub on the dock-driven "systemui" route: three
// expandable subsections (text loupe, activity transitions, rotation style).
// Edge-to-edge list scrolling under the top bar and the dock, like every
// other section; no navigation library is involved.
@Composable
fun SystemUiScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "systemui_header") {
            Text(
                text = stringResource(R.string.tab_systemui),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.sysui_section_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "systemui_magnifier") {
            MagnifierGroup()
        }
        item(key = "systemui_transition") {
            TransitionGroup()
        }
        item(key = "systemui_rotation") {
            RotationGroup()
        }
    }
}
