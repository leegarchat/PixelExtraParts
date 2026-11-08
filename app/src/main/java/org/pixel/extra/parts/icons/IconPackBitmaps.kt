package org.pixel.extra.parts.icons

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
import org.pixel.extra.parts.R
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.roundToInt

// Shared bitmap helpers for icon packs (extracted from IconPackManager to keep files small).
internal object IconPackBitmaps {

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

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

    internal fun Drawable.toNormalizedIconBitmap(targetPx: Int): Bitmap {
        val raw = toBitmap(
            width = targetPx.coerceAtLeast(1),
            height = targetPx.coerceAtLeast(1),
            config = Bitmap.Config.ARGB_8888
        )
        val normalized = raw.normalizeVisibleIconArea(targetPx.coerceAtLeast(1))
        if (normalized !== raw) raw.recycle()
        return normalized
    }

    internal fun Bitmap.normalizeVisibleIconArea(targetPx: Int): Bitmap {
        val bounds = visibleAlphaBounds() ?: return this
        val visibleWidth = bounds.width().coerceAtLeast(1)
        val visibleHeight = bounds.height().coerceAtLeast(1)
        val minExpected = targetPx * 0.70f
        if (visibleWidth >= minExpected || visibleHeight >= minExpected) {
            return this
        }

        val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val targetVisiblePx = (targetPx * 0.88f).roundToInt().coerceIn(1, targetPx)
        val scale = minOf(
            targetVisiblePx / visibleWidth.toFloat(),
            targetVisiblePx / visibleHeight.toFloat()
        )
        val scaledWidth = (visibleWidth * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (visibleHeight * scale).roundToInt().coerceAtLeast(1)
        val dst = RectF(
            ((targetPx - scaledWidth) / 2f),
            ((targetPx - scaledHeight) / 2f),
            ((targetPx + scaledWidth) / 2f),
            ((targetPx + scaledHeight) / 2f)
        )
        Canvas(output).drawBitmap(this, bounds, dst, iconPaint)
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

    internal fun sendReloadBroadcast(context: Context) {
        context.sendBroadcast(
            Intent(IconPackManager.RELOAD_ACTION).addFlags(0x01000000 /* FLAG_RECEIVER_INCLUDE_BACKGROUND */)
        )
    }

    internal fun createIconPackContext(context: Context, iconPackPackage: String): Context {
        return context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
    }

    internal fun PackageInfo.versionCodeLong(): Long {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
    }

}