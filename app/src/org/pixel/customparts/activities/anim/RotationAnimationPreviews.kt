package org.pixel.customparts.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun RotationAnimPreview(
    exitAnimRes: Int,
    enterAnimRes: Int,
    width: Dp = 110.dp,
    height: Dp = 190.dp,
    speedMultiplier: Float = 1f
) {
    val spec = remember(exitAnimRes, enterAnimRes) {
        EffectLoopSpec.forRotationRes(exitAnimRes, enterAnimRes)
    }
    StudioLoopPreview(
        spec = spec,
        width = width,
        height = height,
        speedMultiplier = speedMultiplier
    )
}

@Composable
internal fun RotationCustomAnimPreview(
    packageName: String,
    width: Dp = 110.dp,
    height: Dp = 190.dp,
    speedMultiplier: Float = 1f
) {
    val spec = remember(packageName) {
        EffectLoopSpec.forRotationTheme(packageName)
    }
    StudioLoopPreview(
        spec = spec,
        width = width,
        height = height,
        speedMultiplier = speedMultiplier
    )
}
