package com.med.shortscounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * Yatay, yuvarlak köşeli parçalı çubuk.
 * values toplamı total'e oranlanır (total <= 0 ise values toplamı kullanılır).
 */
class SegmentBar(context: Context, private val heightDp: Int = 10) : View(context) {

    private var values = FloatArray(0)
    private var colors = IntArray(0)
    private var total = 0f
    var trackColor = C.CARD2

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    fun set(values: FloatArray, colors: IntArray, total: Float = 0f) {
        this.values = values
        this.colors = colors
        this.total = total
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), resources.displayMetrics
        ).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        rect.set(0f, 0f, w, h)
        paint.color = trackColor
        canvas.drawRoundRect(rect, r, r, paint)

        val sum = if (total > 0f) total else values.sum()
        if (sum <= 0f) return

        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        var x = 0f
        for (i in values.indices) {
            val segW = w * values[i] / sum
            if (segW <= 0f) continue
            paint.color = colors.getOrElse(i) { C.ACCENT }
            canvas.drawRect(x, 0f, x + segW, h, paint)
            x += segW
        }
        canvas.restore()
    }
}
