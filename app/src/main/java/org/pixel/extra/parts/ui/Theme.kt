package org.pixel.extra.parts.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

enum class ThemeMode {
    System, Light, Dark;

    companion object {
        fun fromOrdinal(ordinal: Int): ThemeMode = entries.getOrElse(ordinal) { System }
    }
}

@Composable
fun AppExpressiveTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

// Adaptive 1.dp micro-outline: darkens light containers, lightens dark ones.
fun Color.edgeOutline(alpha: Float = 0.45f): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    val shifted = if (luminance > 0.55f) {
        copy(red = red * 0.8f, green = green * 0.8f, blue = blue * 0.8f)
    } else {
        copy(
            red = (red + (1f - red) * 0.22f).coerceIn(0f, 1f),
            green = (green + (1f - green) * 0.22f).coerceIn(0f, 1f),
            blue = (blue + (1f - blue) * 0.22f).coerceIn(0f, 1f),
        )
    }
    return shifted.copy(alpha = alpha)
}
