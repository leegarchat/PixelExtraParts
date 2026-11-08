package org.pixel.customparts

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun dynamicDarkColorScheme(context: Context): ColorScheme {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        androidx.compose.material3.dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
}

@Composable
fun dynamicLightColorScheme(context: Context): ColorScheme {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        androidx.compose.material3.dynamicLightColorScheme(context)
    } else {
        lightColorScheme()
    }
}