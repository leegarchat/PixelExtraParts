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

internal fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
    }.getOrNull()
}


internal fun Bitmap.toCenteredSquareBitmap(targetPx: Int): Bitmap {
    val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
    val scale = minOf(targetPx / width.toFloat(), targetPx / height.toFloat())
    val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    Canvas(output).drawBitmap(
        scaled,
        ((targetPx - scaledWidth) / 2f),
        ((targetPx - scaledHeight) / 2f),
        null
    )
    if (scaled !== this) {
        scaled.recycle()
    }
    return output
}


internal fun Bitmap.visibleAlphaBounds(): Rect? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((getPixel(x, y) ushr 24) > 8) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right >= left && bottom >= top) Rect(left, top, right + 1, bottom + 1) else null
}


internal fun getDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}


internal fun loadIconPackDrawable(
    context: Context,
    drawableName: String,
    dynamicIcon: DynamicIconInfo? = null
): Drawable? {
    val cleanName = drawableName.substringBeforeLast('.').trim()
    if (cleanName.isBlank()) return null
    val resources = context.resources
    val resId = resources.getIdentifier(cleanName, "drawable", context.packageName)
        .takeIf { it != 0 }
        ?: resources.getIdentifier(cleanName, "mipmap", context.packageName).takeIf { it != 0 }
        ?: return null
    val drawable = runCatching { resources.getDrawable(resId, context.theme) }.getOrNull()
        ?: return null
    return if (dynamicIcon?.type == IconPackManager.DYNAMIC_ICON_CLOCK && dynamicIcon.clockConfig != null) {
        IconPackManager.DynamicClockDrawable(drawable, dynamicIcon.clockConfig)
    } else {
        drawable
    }
}


internal fun loadInstalledApplicationIndex(packageManager: PackageManager): Map<String, InstalledAppRecord> {
    val launcherPackages = loadLauncherVisiblePackages(packageManager)
    return runCatching { packageManager.getInstalledApplications(PackageManager.GET_META_DATA) }
        .getOrDefault(emptyList())
        .mapNotNull { info ->
            val packageName = info.packageName ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: packageName
            InstalledAppRecord(
                label = label,
                packageName = packageName,
                icon = loadOriginalApplicationIcon(packageManager, info),
                isSystem = info.isSystemApplication(),
                isLauncherVisible = launcherPackages.contains(packageName)
            )
        }.associateBy { it.packageName }
}


private fun loadOriginalApplicationIcon(
    packageManager: PackageManager,
    info: ApplicationInfo
): Drawable? {
    val iconResId = when {
        info.icon != 0 -> info.icon
        info.logo != 0 -> info.logo
        else -> 0
    }
    if (iconResId != 0) {
        runCatching {
            packageManager.getResourcesForApplication(info).getDrawable(iconResId, null)
        }.getOrNull()?.let { return it }
    }
    return runCatching { info.loadUnbadgedIcon(packageManager) }.getOrNull()
}


private fun loadLauncherVisiblePackages(packageManager: PackageManager): Set<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching { packageManager.queryIntentActivities(intent, 0) }
        .getOrDefault(emptyList())
        .mapNotNullTo(linkedSetOf()) { it.activityInfo?.packageName ?: it.resolvePackageName }
}

