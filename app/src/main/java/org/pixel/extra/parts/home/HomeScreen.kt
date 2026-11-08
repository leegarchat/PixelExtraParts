package org.pixel.extra.parts.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Home tab content: the donor demo list for the currently selected section.
// The root provides top/bottom clearance, so only a small bottom inset stays.
@Composable
fun HomeScreen(
    sectionLabel: String,
    @DrawableRes iconRes: Int,
) {
    DemoSectionScreen(
        sectionLabel = sectionLabel,
        iconRes = iconRes,
        contentPadding = PaddingValues(bottom = 12.dp),
    )
}
