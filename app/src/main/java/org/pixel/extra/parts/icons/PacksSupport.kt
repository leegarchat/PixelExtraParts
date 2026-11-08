package org.pixel.extra.parts.icons

import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import java.util.Calendar
import org.xmlpull.v1.XmlPullParser

// Pack support types shared by the backend and the UI: density records,
// the live clock drawable wrapper, and the appfilter parser holder.

internal class DynamicPackClockDrawable(
    private val drawable: Drawable,
    private val config: DynamicClockConfig,
) : Drawable() {
    private var alpha: Int = 255
    private var colorFilter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val layerDrawable = drawable as? LayerDrawable
        if (layerDrawable == null) {
            drawable.bounds = bounds
            drawable.alpha = alpha
            drawable.colorFilter = colorFilter
            drawable.draw(canvas)
            return
        }
        layerDrawable.bounds = bounds
        val calendar = Calendar.getInstance()
        for (index in 0 until layerDrawable.numberOfLayers) {
            val child = layerDrawable.getDrawable(index) ?: continue
            val rotation = clockLayerRotation(index, calendar)
            child.alpha = alpha
            child.colorFilter = colorFilter
            val childBounds = child.bounds.takeUnless { it.isEmpty }
            if (childBounds == null) child.bounds = bounds
            val save = canvas.save()
            if (rotation != 0f) {
                canvas.rotate(rotation, bounds.exactCenterX(), bounds.exactCenterY())
            }
            child.draw(canvas)
            canvas.restoreToCount(save)
        }
    }

    private fun clockLayerRotation(index: Int, calendar: Calendar): Float {
        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        return when (index) {
            config.hourLayerIndex -> ((hour + minute / 60f) * 30f) -
                ((config.defaultHour % 12 + config.defaultMinute / 60f) * 30f)
            config.minuteLayerIndex -> ((minute + second / 60f) * 6f) -
                ((config.defaultMinute + config.defaultSecond / 60f) * 6f)
            config.secondLayerIndex -> (second * 6f) - (config.defaultSecond * 6f)
            else -> 0f
        }
    }

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun getAlpha(): Int = alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = drawable.intrinsicWidth

    override fun getIntrinsicHeight(): Int = drawable.intrinsicHeight
}

internal class PackParserHolder(
    val parser: XmlPullParser,
    private val closeable: AutoCloseable,
) : AutoCloseable {
    override fun close() {
        closeable.close()
    }
}

internal inline fun PackParserHolder.useParser(block: (XmlPullParser) -> Unit) {
    use { holder -> block(holder.parser) }
}
