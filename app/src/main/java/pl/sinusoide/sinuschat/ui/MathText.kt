package pl.sinusoide.sinuschat.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import kotlin.math.max

object MathText {

    private val FRACTION = Regex(
        """(?<![\w./])(\d{1,3}(?:[.,]\d{1,3})?|[a-zA-Z])\s*/\s*(\d{1,3}(?:[.,]\d{1,3})?|[a-zA-Z])(?![\w/])(?!\.\d)"""
    )
    private val POWER = Regex("""(?<=[\w)])\^\{?(-?\d+|[a-zA-Z])\}?""")
    private val INDEX = Regex("""(?<=[A-Za-z])_\{?(\d+|[a-zA-Z])\}?""")
    private val TIMES = Regex("""(?<=\d)\s*\*\s*(?=\d)""")

    private const val SUP_FROM = "0123456789+-=()n"
    private const val SUP_TO   = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿ"
    private const val SUB_FROM = "0123456789+-=()"
    private const val SUB_TO   = "₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎"

    private fun map(s: String, from: String, to: String): String? {
        val out = StringBuilder(s.length)
        for (c in s) {
            val i = from.indexOf(c)
            if (i < 0) return null
            out.append(to[i])
        }
        return out.toString()
    }

    fun format(text: String): CharSequence {
        if (text.isEmpty()) return text
        var s = text
        s = TIMES.replace(s, " × ")
        s = POWER.replace(s) { m -> map(m.groupValues[1], SUP_FROM, SUP_TO) ?: m.value }
        s = INDEX.replace(s) { m -> map(m.groupValues[1], SUB_FROM, SUB_TO) ?: m.value }

        val hits = FRACTION.findAll(s).toList()
        if (hits.isEmpty()) return s

        val out = SpannableStringBuilder(s)
        for (m in hits.asReversed()) {
            val start = m.range.first
            out.replace(start, m.range.last + 1, "￼")
            out.setSpan(
                FractionSpan(m.groupValues[1], m.groupValues[2]),
                start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return out
    }
}

private class FractionSpan(private val num: String, private val den: String) : ReplacementSpan() {

    private val scale = 0.78f
    private var width = 0f

    private fun partWidth(paint: Paint): Float {
        val size = paint.textSize
        paint.textSize = size * scale
        val w = max(paint.measureText(num), paint.measureText(den))
        paint.textSize = size
        return w
    }

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?
    ): Int {
        width = partWidth(paint) + paint.textSize * 0.36f
        if (fm != null) {
            val h = paint.textSize * scale
            fm.ascent = (-h * 1.65f).toInt()
            fm.top = fm.ascent
            fm.descent = (h * 0.95f).toInt()
            fm.bottom = fm.descent
        }
        return width.toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val size = paint.textSize
        val barY = y - size * 0.33f
        paint.textSize = size * scale

        val cx = x + width / 2f
        canvas.drawText(num, cx - paint.measureText(num) / 2f, barY - size * 0.22f, paint)
        canvas.drawText(den, cx - paint.measureText(den) / 2f, barY + size * 0.74f, paint)

        val stroke = paint.strokeWidth
        paint.strokeWidth = max(1f, size * 0.055f)
        canvas.drawLine(x + size * 0.09f, barY, x + width - size * 0.09f, barY, paint)
        paint.strokeWidth = stroke
        paint.textSize = size
    }
}
