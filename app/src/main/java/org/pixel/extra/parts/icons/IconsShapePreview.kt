package org.pixel.extra.parts.icons

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser

// Small adaptive-shape preview for preset and custom rows. The 100x100 path
// viewport is scaled into the view bounds; invalid data falls back to an oval.
@Composable
fun IconsShapePreview(
    pathData: String,
    active: Boolean,
    fillColor: Int,
    strokeColor: Int,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> IconsShapePreviewView(context) },
        update = { view ->
            view.setShape(pathData, fillColor, strokeColor, active)
        },
    )
}

private class IconsShapePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val bounds = RectF()
    private var pathData: String = ""
    private var active: Boolean = false

    fun setShape(pathData: String, fillColor: Int, strokeColor: Int, active: Boolean) {
        this.pathData = pathData
        this.active = active
        fillPaint.color = fillColor
        strokePaint.color = strokeColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat().coerceAtLeast(1f)
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        bounds.set(left, top, left + size, top + size)
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillPaint.color and 0x33FFFFFF
        }
        canvas.drawRoundRect(bounds, size * 0.18f, size * 0.18f, shade)
        val parsed = runCatching {
            PathParser.createPathFromPathData(pathData)
        }.getOrNull()
        if (parsed == null) {
            canvas.drawOval(bounds, fillPaint)
            canvas.drawOval(bounds, strokePaint)
            return
        }
        val matrix = Matrix().apply {
            setScale(size / 100f, size / 100f)
            postTranslate(left, top)
        }
        parsed.transform(matrix)
        canvas.drawPath(parsed, fillPaint)
        canvas.drawPath(parsed, strokePaint)
    }
}
