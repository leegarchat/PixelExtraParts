package org.pixel.customparts.ui.addons

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.net.URL
import java.util.Locale
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.activities.AddonPageActivity
import org.pixel.customparts.core.ApkModulesGate
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.utils.HookKeyCompat
import org.pixel.customparts.utils.PixelPartsTileRefresher
import org.pixel.customparts.utils.TileUtils
import org.pixel.customparts.utils.runShellCommandForResult

private const val TAG = "AddonManagerUI"
private const val ADDON_DIR = "/data/pixelparts/addons"
private const val SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons"
private const val ADDON_PREFIX = "pixel_addon_"
private const val INJECT_PREFIX = "pixel_extra_parts_inject_package_"
private const val SAFE_MODE_SETTING = "pixel_addon_safe_mode"
private const val IGNORED_PACKAGE = "android" // system_server — can't hook reliably
private const val DYNAMIC_ADDON_TILE_COUNT = 40

// =====================================================================
// Data model
// =====================================================================

internal fun parseOptionalColor(value: String): Color? {
    if (value.isBlank()) return null
    return try { Color(android.graphics.Color.parseColor(value.trim())) } catch (_: Throwable) { null }
}

@Composable
internal fun settingAccent(setting: AddonSettingDef, addon: AddonUiModel): Color {
    return parseOptionalColor(setting.accentColor)
        ?: parseOptionalColor(addon.accentColor)
        ?: MaterialTheme.colorScheme.primary
}

internal fun groupContainerColor(setting: AddonSettingDef, fallback: Color, fallbackAlpha: Float): Color {
    val base = parseOptionalColor(setting.color) ?: fallback.copy(alpha = fallbackAlpha)
    return if (setting.surfaceAlpha >= 0f) base.copy(alpha = setting.surfaceAlpha) else base
}

/**
 * Renders an icon for a setting if configured.
 * Returns true if an icon was rendered, false otherwise.
 */
@Composable
internal fun SettingIcon(setting: AddonSettingDef, addon: AddonUiModel, modifier: Modifier = Modifier): Boolean {
    val iconName = setting.icon.trim()
    if (iconName.isEmpty()) return false

    val iconSizeDp = setting.iconSize.dp
    val accent = settingAccent(setting, addon)
    val iconType = setting.iconType.trim().lowercase()

    val shapeModifier = when (setting.iconShape) {
        "circle" -> Modifier
            .size(iconSizeDp + 12.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
        "rounded" -> Modifier
            .size(iconSizeDp + 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
        else -> Modifier // "none" or unsupported
    }

    val hasShape = setting.iconShape != "none" && setting.iconShape.isNotEmpty()

    if (iconType == "file" || (iconType.isEmpty() && looksLikeBitmapPath(iconName))) {
        val iconBitmap = remember(addon.jarPath, iconName) {
            extractBitmapFromJar(java.io.File(addon.jarPath), iconName)?.asImageBitmap()
        }
        if (iconBitmap != null) {
            if (hasShape) {
                Box(modifier = modifier.then(shapeModifier), contentAlignment = Alignment.Center) {
                    Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(iconSizeDp))
                }
            } else {
                Image(bitmap = iconBitmap, contentDescription = null, modifier = modifier.size(iconSizeDp))
            }
            return true
        }
    }

    if (iconType != "file") {
        val imageVector = rememberMaterialIcon(iconName)
        if (imageVector != null) {
            if (hasShape) {
                Box(modifier = modifier.then(shapeModifier), contentAlignment = Alignment.Center) {
                    Icon(imageVector = imageVector, contentDescription = null, modifier = Modifier.size(iconSizeDp), tint = accent)
                }
            } else {
                Icon(imageVector = imageVector, contentDescription = null, modifier = modifier.size(iconSizeDp), tint = accent)
            }
            return true
        }
    }
    return false
}

/**
 * Resolves a Material Icons name to an ImageVector.
 * Supports Rounded, Filled, Outlined variants via prefix (e.g. "Rounded.Settings", "Settings").
 * Falls back to Icons.Rounded, then Icons.Filled.
 */
@Composable
internal fun rememberMaterialIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector? {
    return remember(name) {
        resolveMaterialIcon(name.trim())
    }
}

private data class MaterialIconVariant(
    val packageName: String,
    val receiver: Any
)

private fun resolveMaterialIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector? {
    if (name.isBlank()) return null

    val parts = name.split('.').filter { it.isNotBlank() }
    val autoMirrored = parts.firstOrNull()?.equals("AutoMirrored", ignoreCase = true) == true
    val style = when {
        autoMirrored && parts.size >= 2 -> parts[1].lowercase()
        parts.size >= 2 -> parts[0].lowercase()
        else -> ""
    }
    val iconName = when {
        autoMirrored && parts.size >= 3 -> parts.drop(2).joinToString("")
        parts.size >= 2 -> parts.drop(1).joinToString("")
        else -> parts.firstOrNull().orEmpty()
    }
    if (iconName.isBlank()) return null

    for (variant in materialIconVariants(style, autoMirrored)) {
        val result = resolveIconFromKtClass(variant.packageName, iconName, variant.receiver)
        if (result != null) return result
    }

    return fallbackMaterialIcon(iconName)
}

private fun fallbackMaterialIcon(iconName: String): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (iconName.trim().lowercase(Locale.ROOT)) {
        "apps", "circle", "dashboard", "gridview", "menu", "rocket", "viewcarousel", "viewmodule" -> Icons.Rounded.Apps
        "arrowdropdowncircle", "radiobuttonchecked", "linearscale", "speed", "tune" -> Icons.Rounded.Tune
        "batterychargingfull", "bluetooth", "brightnesshigh", "notifications", "powersettingsnew", "settings", "textfields", "toggleon" -> Icons.Rounded.Settings
        "checkbox", "check", "checkcircle", "looksone", "lookstwo" -> Icons.Rounded.CheckCircle
        "camera", "cameraalt", "flashon", "photocamera" -> Icons.Rounded.Tune
        "colorlens", "palette", "diamond" -> Icons.Rounded.Palette
        "delete", "deleteoutline" -> Icons.Rounded.Delete
        "eco", "favorite", "star" -> Icons.Rounded.Favorite
        "fileopen" -> Icons.Rounded.FileOpen
        "home" -> Icons.Rounded.Home
        "locationon", "wifi" -> Icons.Rounded.NetworkCell
        "search" -> Icons.Rounded.Search
        "security", "shield" -> Icons.Rounded.Security
        "volumeup" -> Icons.Rounded.Tune
        else -> Icons.Rounded.Extension
    }
}

private fun materialIconVariants(style: String, autoMirrored: Boolean): List<MaterialIconVariant> {
    val rounded = MaterialIconVariant("androidx.compose.material.icons.rounded", Icons.Rounded)
    val filled = MaterialIconVariant("androidx.compose.material.icons.filled", Icons.Filled)
    val outlined = MaterialIconVariant("androidx.compose.material.icons.outlined", Icons.Outlined)
    val autoRounded = MaterialIconVariant("androidx.compose.material.icons.automirrored.rounded", Icons.AutoMirrored.Rounded)
    val autoFilled = MaterialIconVariant("androidx.compose.material.icons.automirrored.filled", Icons.AutoMirrored.Filled)
    val autoOutlined = MaterialIconVariant("androidx.compose.material.icons.automirrored.outlined", Icons.AutoMirrored.Outlined)

    return when {
        autoMirrored && style == "filled" -> listOf(autoFilled, autoRounded, autoOutlined)
        autoMirrored && style == "outlined" -> listOf(autoOutlined, autoRounded, autoFilled)
        autoMirrored -> listOf(autoRounded, autoFilled, autoOutlined)
        style == "filled" -> listOf(filled, rounded, outlined, autoFilled, autoRounded, autoOutlined)
        style == "outlined" -> listOf(outlined, rounded, filled, autoOutlined, autoRounded, autoFilled)
        style == "rounded" -> listOf(rounded, filled, outlined, autoRounded, autoFilled, autoOutlined)
        else -> listOf(rounded, filled, outlined, autoRounded, autoFilled, autoOutlined)
    }
}

private fun resolveIconFromKtClass(packageName: String, iconName: String, receiver: Any): androidx.compose.ui.graphics.vector.ImageVector? {
    // Compose icons compile as: package.IconNameKt.getIconName(Icons.Rounded)
    val className = "$packageName.${iconName}Kt"
    return try {
        val cls = Class.forName(className)
        val method = cls.methods.firstOrNull { m ->
            m.name == "get$iconName" && m.parameterCount == 1
        }
        method?.invoke(null, receiver) as? androidx.compose.ui.graphics.vector.ImageVector
    } catch (_: Throwable) {
        // Some icons have different class names (e.g. _Settings or numbers)
        try {
            val altClassName = "$packageName._${iconName}Kt"
            val cls = Class.forName(altClassName)
            val method = cls.methods.firstOrNull { m ->
                m.name == "get$iconName" && m.parameterCount == 1
            }
            method?.invoke(null, receiver) as? androidx.compose.ui.graphics.vector.ImageVector
        } catch (_: Throwable) {
            null
        }
    }
}

private fun getIconField(cls: Class<*>, instance: Any, name: String): androidx.compose.ui.graphics.vector.ImageVector? {
    // Legacy fallback - try direct property access on companion
    return try {
        val method = cls.getDeclaredMethod("get${name}")
        method.isAccessible = true
        method.invoke(instance) as? androidx.compose.ui.graphics.vector.ImageVector
    } catch (_: Throwable) {
        null
    }
}

/**
 * Loads select options from an external JSON file in the addon data directory.
 * Expected JSON format: array of objects with "name"/"label" and "value" fields.
 * e.g. [{"name": "Option A", "value": "a"}, {"name": "Option B", "value": "b"}]
 */
private fun loadOptionsFromFile(addonId: String, path: String): List<SelectOption> {
    if (path.isBlank()) return emptyList()
    val file = java.io.File("$ADDON_DIR/$addonId/$path")
    if (!file.exists()) {
        // Also try in /data/pixelparts/addons/<path> directly
        val altFile = java.io.File("$ADDON_DIR/$path")
        if (!altFile.exists()) return emptyList()
        return parseOptionsJson(altFile)
    }
    return parseOptionsJson(file)
}

private fun parseOptionsJson(file: java.io.File): List<SelectOption> {
    return try {
        val text = file.readText(Charsets.UTF_8)
        val arr = org.json.JSONArray(text)
        val result = mutableListOf<SelectOption>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj != null) {
                val value = obj.optString("value", obj.optString("id", ""))
                val label = obj.optString("name", obj.optString("label", obj.optString("title", value)))
                if (value.isNotEmpty()) result.add(SelectOption(value, label))
            } else {
                val s = arr.optString(i)
                if (s.isNotEmpty()) result.add(SelectOption(s, s))
            }
        }
        result
    } catch (_: Throwable) { emptyList() }
}

/**
 * Resolves options for a select setting: from inline options or from external file.
 */
@Composable
internal fun resolveSelectOptions(setting: AddonSettingDef, addon: AddonUiModel): List<SelectOption> {
    return if (setting.optionsSource == "file" && setting.optionsPath.isNotEmpty()) {
        val fileOptions = remember(addon.id, setting.optionsPath) {
            loadOptionsFromFile(addon.id, setting.optionsPath)
        }
        if (fileOptions.isNotEmpty()) fileOptions else setting.options
    } else {
        setting.options
    }
}

internal fun parseColorValue(value: String, format: ColorOutputFormat, allowAlpha: Boolean): Int {
    if (value.isBlank()) return if (allowAlpha) 0xFFFFFFFF.toInt() else 0xFF2196F3.toInt()
    return try {
        val trimmed = value.trim()
        if (format == ColorOutputFormat.INT) {
            trimmed.toIntOrNull() ?: trimmed.toLongOrNull()?.toInt() ?: 0
        } else if (trimmed.startsWith("#")) {
            android.graphics.Color.parseColor(trimmed)
        } else {
            val parts = value.split(',').mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 255) }
            val r = parts.getOrElse(0) { 33 }
            val g = parts.getOrElse(1) { 150 }
            val b = parts.getOrElse(2) { 243 }
            val a = if (format == ColorOutputFormat.RGBA_CSV || allowAlpha) parts.getOrElse(3) { 255 } else 255
            android.graphics.Color.argb(a, r, g, b)
        }
    } catch (_: Throwable) {
        if (allowAlpha) 0xFFFFFFFF.toInt() else 0xFF2196F3.toInt()
    }
}

internal fun formatColorValue(color: Int, format: ColorOutputFormat): String {
    val a = android.graphics.Color.alpha(color)
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    return when (format) {
        ColorOutputFormat.HEX_ARGB -> String.format("#%02X%02X%02X%02X", a, r, g, b)
        ColorOutputFormat.RGB_CSV -> "$r,$g,$b"
        ColorOutputFormat.RGBA_CSV -> "$r,$g,$b,$a"
        ColorOutputFormat.INT -> color.toString()
        ColorOutputFormat.HEX_RGB -> String.format("#%02X%02X%02X", r, g, b)
    }
}
