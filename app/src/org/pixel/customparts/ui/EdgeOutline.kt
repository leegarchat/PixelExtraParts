package org.pixel.customparts.ui

import androidx.compose.ui.graphics.Color

/**
 * Адаптивная микрообводка 1.dp для объёма (DESIGN_GUIDELINES §3, edgeOutline).
 *
 * На светлом контейнере (luminance > 0.55) затемняет на 20%,
 * на тёмном — осветляет на 22% по направлению к белому.
 *
 * Применение: `.border(1.dp, containerColor.edgeOutline(), shape)`.
 */
fun Color.edgeOutline(alpha: Float = 0.45f): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    val shifted = if (luminance > 0.55f) {
        copy(red = red * 0.8f, green = green * 0.8f, blue = blue * 0.8f)
    } else {
        copy(
            red = (red + (1f - red) * 0.22f).coerceIn(0f, 1f),
            green = (green + (1f - green) * 0.22f).coerceIn(0f, 1f),
            blue = (blue + (1f - blue) * 0.22f).coerceIn(0f, 1f)
        )
    }
    return shifted.copy(alpha = alpha)
}
