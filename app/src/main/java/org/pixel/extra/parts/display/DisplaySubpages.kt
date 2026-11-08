package org.pixel.extra.parts.display

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.extra.parts.R
import org.pixel.extra.parts.navigation.SectionRoutes
import org.pixel.extra.parts.ui.sectionContentPadding

// Top-bar titles for the display sub-routes: the root bar resolves labels
// from dock entries, and sub-routes are not dock entries, so this map fills
// the gap. Keys mirror SectionRoutes.DISPLAY_*.
internal val DisplaySubLabels: Map<String, Int> = mapOf(
    SectionRoutes.DISPLAY_SATURATION to R.string.saturation_title,
    SectionRoutes.DISPLAY_AUTO_HBM to R.string.auto_hbm_title,
    SectionRoutes.DISPLAY_DOUBLE_TAP to R.string.dtw_title,
    SectionRoutes.DISPLAY_AUTO_LOCK to R.string.auto_lock_title,
)

// Shared subpage scaffold: in-list header (title + summary, same strings as
// the hub rows) plus the existing section card untouched.
@Composable
private fun DisplaySubpage(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = sectionContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__display_sub_header__") {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "__display_sub_card__") {
            content()
        }
    }
}

// Saturation subpage: reuses SaturationCard without logic changes.
@Composable
fun DisplaySaturationScreen(modifier: Modifier = Modifier) {
    DisplaySubpage(
        titleRes = R.string.saturation_title,
        subtitleRes = R.string.saturation_summary,
        modifier = modifier,
    ) {
        SaturationCard()
    }
}

// AutoHBM subpage: reuses AutoHbmCard without logic changes.
@Composable
fun DisplayAutoHbmScreen(modifier: Modifier = Modifier) {
    DisplaySubpage(
        titleRes = R.string.auto_hbm_title,
        subtitleRes = R.string.auto_hbm_summary,
        modifier = modifier,
    ) {
        AutoHbmCard()
    }
}

// Double-tap subpage: reuses DoubleTapCard without logic changes.
@Composable
fun DisplayDoubleTapScreen(modifier: Modifier = Modifier) {
    DisplaySubpage(
        titleRes = R.string.dtw_title,
        subtitleRes = R.string.dtw_summary,
        modifier = modifier,
    ) {
        DoubleTapCard()
    }
}

// Auto-lock subpage: reuses AutoLockCard without logic changes.
@Composable
fun DisplayAutoLockScreen(modifier: Modifier = Modifier) {
    DisplaySubpage(
        titleRes = R.string.auto_lock_title,
        subtitleRes = R.string.auto_lock_summary,
        modifier = modifier,
    ) {
        AutoLockCard()
    }
}
