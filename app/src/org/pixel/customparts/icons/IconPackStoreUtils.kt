package org.pixel.customparts.icons

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

internal fun filterEntriesForMode(entries: List<IconPackEntry>, mode: IconApplyMode): List<IconPackEntry> {
    return when (mode) {
        IconApplyMode.ALL_PACK -> entries
        IconApplyMode.SYSTEM_ONLY -> entries.filter { it.installed && it.isSystem }
        IconApplyMode.USER_ONLY -> entries.filter { it.installed && !it.isSystem }
        IconApplyMode.INSTALLED_ONLY -> entries.filter { it.installed }
    }
}


internal fun readShapeAreaConfig(areaObject: JSONObject?): IconShapeAreaConfig? {
    if (areaObject == null) return null
    return IconShapeAreaConfig(
        stretchShape = areaObject.optBoolean("stretchShape", false),
        removeShape = areaObject.optBoolean("removeShape", false),
        scalePercent = areaObject.optDouble("scalePercent", IconPackManager.DEFAULT_SHAPE_SCALE_PERCENT.toDouble()).toFloat()
    ).normalized()
}


internal fun writeShapeAreaConfig(config: IconShapeAreaConfig?): JSONObject? {
    val normalized = config?.normalized() ?: return null
    return JSONObject().apply {
        put("stretchShape", normalized.stretchShape)
        put("removeShape", normalized.removeShape)
        put("scalePercent", normalized.scalePercent)
    }
}


internal fun readDynamicIconInfo(sourceObject: JSONObject): DynamicIconInfo? {
    val dynamicObject = sourceObject.optJSONObject("dynamicIcon")
    val type = (dynamicObject?.optString("type") ?: sourceObject.optString("dynamicType"))
        .takeIf { it.isNotBlank() }
        ?: return null
    val clockObject = dynamicObject?.optJSONObject("clockConfig") ?: sourceObject.optJSONObject("clockConfig")
    return DynamicIconInfo(
        type = type,
        clockConfig = clockObject?.let {
            DynamicClockConfig(
                hourLayerIndex = it.optInt("hourLayerIndex", -1),
                minuteLayerIndex = it.optInt("minuteLayerIndex", -1),
                secondLayerIndex = it.optInt("secondLayerIndex", -1),
                defaultHour = it.optInt("defaultHour", 0),
                defaultMinute = it.optInt("defaultMinute", 0),
                defaultSecond = it.optInt("defaultSecond", 0)
            )
        },
        calendarPrefix = dynamicObject?.optString("calendarPrefix")?.takeIf { it.isNotBlank() }
            ?: sourceObject.optString("calendarPrefix").takeIf { it.isNotBlank() },
        calendarFallbackDrawable = dynamicObject?.optString("calendarFallbackDrawable")?.takeIf { it.isNotBlank() }
            ?: sourceObject.optString("calendarFallbackDrawable").takeIf { it.isNotBlank() }
    )
}


internal fun writeDynamicIconInfo(dynamicIcon: DynamicIconInfo?): JSONObject? {
    dynamicIcon ?: return null
    return JSONObject().apply {
        put("type", dynamicIcon.type)
        dynamicIcon.calendarPrefix?.let { put("calendarPrefix", it) }
        dynamicIcon.calendarFallbackDrawable?.let { put("calendarFallbackDrawable", it) }
        dynamicIcon.clockConfig?.let { config ->
            put("clockConfig", JSONObject().apply {
                put("hourLayerIndex", config.hourLayerIndex)
                put("minuteLayerIndex", config.minuteLayerIndex)
                put("secondLayerIndex", config.secondLayerIndex)
                put("defaultHour", config.defaultHour)
                put("defaultMinute", config.defaultMinute)
                put("defaultSecond", config.defaultSecond)
            })
        }
    }
}


internal fun findDrawableResourceNames(sourceDir: String): List<String> {
    return runCatching {
        ZipFile(sourceDir).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { path ->
                    (path.startsWith("res/drawable") || path.startsWith("res/mipmap")) &&
                        (path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".xml") || path.endsWith(".jpg"))
                }
                .map { path ->
                    path.substringAfterLast('/')
                        .substringBeforeLast('.')
                        .removeSuffix(".9")
                }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        }
    }.getOrDefault(emptyList())
}


internal fun densityFolderForDpi(densityDpi: Int): String = when {
    densityDpi <= 160 -> "mdpi"
    densityDpi <= 240 -> "hdpi"
    densityDpi <= 320 -> "xhdpi"
    densityDpi <= 480 -> "xxhdpi"
    else -> "xxxhdpi"
}


internal fun createIconPackContext(context: Context, iconPackPackage: String): Context {
    return context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
}


internal fun fileNameForPackage(packageName: String): String {
    return packageName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"
}


internal fun normalizeFileName(value: String): String? {
    val fileName = value.trim()
    if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
        return null
    }
    return if (fileName.endsWith(".png")) fileName else "$fileName.png"
}


internal fun PackageInfo.versionCodeLong(): Long {
    @Suppress("DEPRECATION")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
}


internal fun ApplicationInfo.isSystemApplication(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
        (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

