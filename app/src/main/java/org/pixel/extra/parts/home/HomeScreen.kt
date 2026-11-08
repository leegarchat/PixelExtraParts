package org.pixel.extra.parts.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.pixel.extra.parts.R
import org.pixel.extra.parts.ui.SectionStub

// Home slot: title only, no demo rows. Real home content lands in a later stage.
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    SectionStub(
        titleRes = R.string.tab_home,
        bodyRes = R.string.stub_body,
        iconRes = R.drawable.ic_home,
        modifier = modifier,
    )
}
