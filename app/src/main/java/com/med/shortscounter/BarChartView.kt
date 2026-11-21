package com.med.shortscounter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Basit çubuk grafik. Bir çubuğa dokununca değeri üstünde gösterilir.
 * labels: her çubuğun etiketi (boş string = etiket yazma)
 */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: IntArray = IntArray(0)
    private var labels: Array<String> = emptyArray()
    private var tips: Array<String> = emptyArray()
    private var selected = -1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.ACCENT_DIM }
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.ACCENT }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C.LINE; strokeWidth = dp(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C.SUB; textSize = sp(10f); textAlign = Paint.Align.CENTER
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C.TEXT; textSize = sp(12f); textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** Hiçbir çubuk seçili değilken üstte yazan metin (null = varsayılan). */
    var defaultTip: String? = null

    fun setBarColors(normal: Int, highlightColor: Int) {
        barPaint.color = normal
        hiPaint.color = highlightColor
        invalidate()
    }
    private val rect = RectF()

    /** Varsayılan vurgulanan çubuk (ör. bugün / şu anki saat). */
    var highlight = -1

    fun setData(values: IntArray, labels: Array<String>, tips: Array<String>) {
        this.values = values
        this.labels = labels
        this.tips = tips
        if (selected >= values.size) selected = -1
        invalidate()
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(170f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n == 0) return

        val top = dp(22f)
        val bottom = height - dp(18f)
        val chartH = bottom - top
        val slot = width.toFloat() / n
        val gap = (slot * 0.2f).coerceAtMost(dp(4f))
        val maxV = max(1, values.maxOrNull() ?: 1)

        canvas.drawLine(0f, bottom, width.toFloat(), bottom, axisPaint)

        for (i in 0 until n) {
            val v = values[i]
            val left = i * slot + gap / 2
            val right = (i + 1) * slot - gap / 2
            val hgt = if (v == 0) 0f else max(dp(2f), chartH * v / maxV)
            rect.set(left, bottom - hgt, right, bottom)
            val paint = if (i == selected || (selected < 0 && i == highlight)) hiPaint else barPaint
            canvas.drawRoundRect(rect, dp(3f), dp(3f), paint)

            val lab = labels.getOrNull(i) ?: ""
            if (lab.isNotEmpty()) {
                canvas.drawText(lab, (left + right) / 2, height - dp(4f), labelPaint)
            }
        }

        // Seçili çubuğun (yoksa en büyük değerin) bilgisini üstte yaz
        val showIdx = if (selected >= 0) selected else -1
        val text = if (showIdx >= 0) {
            tips.getOrNull(showIdx) ?: values[showIdx].toString()
        } else {
            defaultTip ?: "en yüksek: $maxV  •  çubuğa dokun"
        }
        var x = if (showIdx >= 0) (showIdx + 0.5f) * slot else width / 2f
        val half = tipPaint.measureText(text) / 2
        x = if (half * 2 >= width) width / 2f else x.coerceIn(half, width - half)
        canvas.drawText(text, x, top - dp(8f), tipPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (values.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val idx = (event.x / (width.toFloat() / values.size)).toInt().coerceIn(0, values.size - 1)
            if (idx != selected) {
                selected = idx
                invalidate()
            }
            return true
        }
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            return true
        }
        return super.onTouchEvent(event)
    }
}
