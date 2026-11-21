package com.med.shortscounter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Uygulama renk paleti (koyu tema). */
object C {
    const val BG = 0xFF0D0D12.toInt()
    const val CARD = 0xFF18181F.toInt()
    const val CARD2 = 0xFF22222C.toInt()
    const val LINE = 0xFF2E2E3A.toInt()
    const val TEXT = 0xFFF3F3F7.toInt()
    const val SUB = 0xFF9898AC.toInt()
    const val ACCENT = 0xFF7C6CFF.toInt()
    const val ACCENT_DIM = 0xFF3A3466.toInt()
    const val IG = 0xFFE24E9B.toInt()
    const val YT = 0xFFFF5A4E.toInt()
    const val GREEN = 0xFF3DDC97.toInt()
    const val AMBER = 0xFFFFB547.toInt()
    const val RED = 0xFFFF6B6B.toInt()
    const val HERO_A = 0xFF3B2F8F.toInt()
    const val HERO_B = 0xFF8A3FA8.toInt()
}

/** Kod ile arayüz kurmak için küçük yardımcılar. */
class Ui(private val ctx: Context) {

    fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).roundToInt()

    fun round(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    fun ripple(bg: Drawable, radiusDp: Int): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, round(0xFFFFFFFF.toInt(), radiusDp))

    fun matchWrap(topMarginDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(topMarginDp) }

    fun weight1(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    fun vbox(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun hbox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(): LinearLayout = vbox().apply {
        background = round(C.CARD, 22)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = matchWrap(12)
    }

    fun text(sizeSp: Float, color: Int = C.TEXT, bold: Boolean = false, s: CharSequence = ""): TextView =
        TextView(ctx).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

    fun cardTitle(title: String, subtitle: String? = null): View {
        val box = vbox()
        box.addView(text(16f, C.TEXT, true, title))
        if (subtitle != null) {
            box.addView(text(12f, C.SUB, false, subtitle).apply { setPadding(0, dp(2), 0, 0) })
        }
        box.setPadding(0, 0, 0, dp(10))
        return box
    }

    /** Dokunulabilir hap şeklinde buton. */
    fun button(label: String, textColor: Int = C.TEXT, bg: Int = C.CARD2): TextView =
        text(14f, textColor, true, label).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = ripple(round(bg, 14), 14)
            isClickable = true
            isFocusable = true
        }

    fun spacer(hDp: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(hDp))
    }
}

/** Küçük istatistik kutusu: etiket / değer / alt bilgi */
class Tile(ui: Ui, label: String) {
    val root: LinearLayout = ui.vbox().apply {
        background = ui.round(C.CARD2, 16)
        setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
    }
    val value: TextView = ui.text(20f, C.TEXT, true, "—")
    val hint: TextView = ui.text(11f, C.SUB, false, "")

    init {
        root.addView(ui.text(12f, C.SUB, false, label))
        root.addView(value.apply { setPadding(0, ui.dp(4), 0, ui.dp(2)) })
        root.addView(hint)
    }

    fun set(v: String, h: String = "") {
        value.text = v
        hint.text = h
    }
}
