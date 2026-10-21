package pl.sinusoide.sinuschat.ui

import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import pl.sinusoide.sinuschat.util.ThemeManager
import kotlin.math.min
import kotlin.math.roundToInt

object FlowLens {

    class Lens(val view: View, val radiusPx: Float? = null, val magnify: Float = 0f, val blurDp: Float = 6f)

    interface Handle {
        fun refresh()
        fun add(lens: Lens)
        fun remove(view: View)
    }

    fun attach(
        content: View,
        lenses: List<Lens>,
        topEdge: () -> Float = { 0f },
        bottomEdge: () -> Float = { 1_000_000f },
    ): Handle? {
        if (Build.VERSION.SDK_INT < 33 || !ThemeManager.isFlow(content.context)) return null
        return LensEffect(content, lenses, topEdge, bottomEdge).also { it.install() }
    }

    fun supported(ctx: android.content.Context) = Build.VERSION.SDK_INT >= 33 && ThemeManager.isFlow(ctx)

    fun underBars(column: android.widget.LinearLayout, scroll: View, lenses: List<Lens>): Handle? {
        if (!supported(column.context)) return null
        val host = column.parent as? FrameLayout ?: return null
        val index = column.indexOfChild(scroll)
        if (index < 0) return null
        val slot = View(column.context)
        val lp = scroll.layoutParams
        column.removeView(scroll)
        column.addView(slot, index, lp)
        host.addView(scroll, host.indexOfChild(column), FrameLayout.LayoutParams(-1, -1))
        (scroll as? ViewGroup)?.clipToPadding = false
        val baseTop = scroll.paddingTop
        val baseBottom = scroll.paddingBottom
        var insetTop = -1
        var insetBottom = -1
        column.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (slot.height == 0 && slot.top == 0) return true
                val top = column.top + slot.top
                val bottom = (host.height - (column.top + slot.bottom)).coerceAtLeast(0)
                if (top == insetTop && bottom == insetBottom) return true
                insetTop = top; insetBottom = bottom
                scroll.setPadding(scroll.paddingLeft, baseTop + top, scroll.paddingRight, baseBottom + bottom)
                return false
            }
        })
        return attach(scroll, lenses,
            topEdge = { (column.top + slot.top).toFloat() },
            bottomEdge = { (column.top + slot.bottom).toFloat() })
    }
}

@RequiresApi(33)
private class LensEffect(
    private val content: View,
    initial: List<FlowLens.Lens>,
    private val topEdge: () -> Float,
    private val bottomEdge: () -> Float,
) : FlowLens.Handle {

    private val lenses = initial.toMutableList()

    private val density = content.resources.displayMetrics.density
    private val shader = RuntimeShader(SHADER)
    private val cur = FloatArray(MAX * 8 + 2)
    private val last = FloatArray(MAX * 8 + 2) { Float.NaN }
    private val base = IntArray(2)
    private val loc = IntArray(2)
    private val rect = RectF()
    private var effectOn = false

    private val preDraw = ViewTreeObserver.OnPreDrawListener { update(); true }

    private fun effectiveAlpha(v: View): Float {
        var a = v.alpha
        var p = v.parent as? View
        while (p != null && a > 0.01f) { a *= p.alpha; p = p.parent as? View }
        return a
    }

    private fun q(v: Float) = (v * 2f).roundToInt() / 2f

    private fun update() {
        if (!content.isAttachedToWindow || content.width == 0) return
        java.util.Arrays.fill(cur, 0f)
        content.getLocationOnScreen(base)
        var n = 0
        for (lens in lenses) {
            if (n >= MAX) break
            val v = lens.view
            if (!v.isShown || v.width == 0 || v.height == 0) continue
            val alpha = effectiveAlpha(v).coerceIn(0f, 1f)
            if (alpha < 0.02f) continue
            val parent = v.parent as? View ?: continue
            parent.getLocationOnScreen(loc)
            rect.set(0f, 0f, v.width.toFloat(), v.height.toFloat())
            v.matrix.mapRect(rect)
            rect.offset((v.left - parent.scrollX + loc[0] - base[0]).toFloat(),
                (v.top - parent.scrollY + loc[1] - base[1]).toFloat())
            if (rect.bottom < 0f || rect.top > content.height) continue
            val half = min(rect.width(), rect.height()) / 2f
            val i = n * 8
            cur[i] = q(rect.left); cur[i + 1] = q(rect.top); cur[i + 2] = q(rect.right); cur[i + 3] = q(rect.bottom)
            cur[i + 4] = q(min(lens.radiusPx?.times(v.scaleY) ?: half, half))
            cur[i + 5] = (alpha * 50f).roundToInt() / 50f
            cur[i + 6] = lens.magnify
            cur[i + 7] = lens.blurDp * density
            n++
        }
        cur[MAX * 8] = q(topEdge())
        cur[MAX * 8 + 1] = q(bottomEdge())
        if (cur.contentEquals(last)) return
        cur.copyInto(last)

        val idle = n == 0 && cur[MAX * 8] <= 0f && cur[MAX * 8 + 1] >= content.height
        if (idle) {
            if (effectOn) { content.setRenderEffect(null); effectOn = false }
            return
        }
        for (k in 0 until MAX) {
            val i = k * 8
            shader.setFloatUniform("r$k", cur[i], cur[i + 1], cur[i + 2], cur[i + 3])
            shader.setFloatUniform("p$k", cur[i + 4], cur[i + 5], cur[i + 6], cur[i + 7])
        }
        shader.setFloatUniform("edges", cur[MAX * 8], cur[MAX * 8 + 1], density, 30f * density)
        content.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
        effectOn = true
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.viewTreeObserver.addOnPreDrawListener(preDraw)
            last.fill(Float.NaN)
            v.invalidate()
        }
        override fun onViewDetachedFromWindow(v: View) {
            v.viewTreeObserver.removeOnPreDrawListener(preDraw)
        }
    }

    override fun refresh() {
        last.fill(Float.NaN)
        content.invalidate()
    }

    override fun add(lens: FlowLens.Lens) {
        lenses.removeAll { it.view === lens.view }
        lenses.add(0, lens)
        refresh()
    }

    override fun remove(view: View) {
        if (lenses.removeAll { it.view === view }) refresh()
    }

    fun install() {
        content.addOnAttachStateChangeListener(attachListener)
        if (content.isAttachedToWindow) attachListener.onViewAttachedToWindow(content)
    }

    private companion object {
        const val MAX = 6

        private const val SHADER = """
            uniform shader content;
            uniform float4 r0; uniform float4 p0;
            uniform float4 r1; uniform float4 p1;
            uniform float4 r2; uniform float4 p2;
            uniform float4 r3; uniform float4 p3;
            uniform float4 r4; uniform float4 p4;
            uniform float4 r5; uniform float4 p5;
            uniform float4 edges;

            float sdLens(float2 p, float4 r, float rad) {
                float2 c = (r.xy + r.zw) * 0.5;
                float2 b = (r.zw - r.xy) * 0.5;
                float2 q = abs(p - c) - b + float2(rad, rad);
                return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - rad;
            }

            // Matowe szkło: próbki na dysku (spirala), obróconym losowo dla każdego piksela.
            // Stały wzór dawał widmowe kopie tekstu — losowy obrót zamienia je w drobne ziarno.
            half4 frost(float2 p, float rad) {
                if (rad < 0.75) {
                    return content.eval(p);
                }
                float ang = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
                half4 s = half4(0.0, 0.0, 0.0, 0.0);
                for (int i = 0; i < 16; i++) {
                    float fi = float(i);
                    float r = rad * sqrt((fi + 0.5) / 16.0);
                    float a = ang + fi * 2.3999632;
                    s += content.eval(p + float2(cos(a), sin(a)) * r);
                }
                return s * 0.0625;
            }

            half4 main(float2 p) {
                float best = 1.0;
                float4 R = float4(0.0, 0.0, 0.0, 0.0);
                float4 P = float4(0.0, 0.0, 0.0, 0.0);
                float sd = 0.0;
                if (p0.y > 0.0) { sd = sdLens(p, r0, p0.x); if (sd < best) { best = sd; R = r0; P = p0; } }
                if (p1.y > 0.0) { sd = sdLens(p, r1, p1.x); if (sd < best) { best = sd; R = r1; P = p1; } }
                if (p2.y > 0.0) { sd = sdLens(p, r2, p2.x); if (sd < best) { best = sd; R = r2; P = p2; } }
                if (p3.y > 0.0) { sd = sdLens(p, r3, p3.x); if (sd < best) { best = sd; R = r3; P = p3; } }
                if (p4.y > 0.0) { sd = sdLens(p, r4, p4.x); if (sd < best) { best = sd; R = r4; P = p4; } }
                if (p5.y > 0.0) { sd = sdLens(p, r5, p5.x); if (sd < best) { best = sd; R = r5; P = p5; } }

                if (best >= 0.0) {
                    // Poza szkłem: miękkie wygaszanie pod górnymi i dolnymi paskami.
                    float tTop = clamp((edges.x - p.y) / edges.w, 0.0, 1.0);
                    float tBot = clamp((p.y - edges.y) / edges.w, 0.0, 1.0);
                    float edge = max(tTop, tBot);
                    edge = edge * edge * (3.0 - 2.0 * edge);
                    if (edge <= 0.0) {
                        return content.eval(p);
                    }
                    half4 f = frost(p, 5.0 * edges.z * edge);
                    return f * half(1.0 - 0.6 * edge);
                }

                // W szkle: rozciąganie przy brzegu, matowe wnętrze, tęczowe obrzeże.
                float2 c = (R.xy + R.zw) * 0.5;
                float2 hb = (R.zw - R.xy) * 0.5;
                // Pas rozciągania przy brzegu — na dużych kartach nie szerszy niż 20 dp,
                // inaczej drobny tekst pod szkłem ciągnął się w prążki.
                float band = max(min(min(hb.x, hb.y) * 0.9, 20.0 * edges.z), 1.0);
                float t = clamp(-best / band, 0.0, 1.0);
                float2 g = float2(sdLens(p + float2(1.0, 0.0), R, P.x) - sdLens(p - float2(1.0, 0.0), R, P.x),
                                  sdLens(p + float2(0.0, 1.0), R, P.x) - sdLens(p - float2(0.0, 1.0), R, P.x));
                float gl = length(g);
                float2 n = gl > 0.0001 ? g / gl : float2(0.0, 0.0);
                float k = 1.0 - t;
                // Przy brzegu obraz spod szkła ciągnie się aż do krawędzi (jak w soczewce paska
                // nawigacji). Współczynnik < 0.5 — odwzorowanie zostaje monotoniczne.
                float2 q = p - n * (0.47 * band * k * k * P.y);
                // Powiększenie wzdłuż krótszego boku: długi pasek nie przesuwa treści na boki.
                float2 mag = float2(1.0 + P.z * P.y * min(1.0, hb.y / hb.x),
                                    1.0 + P.z * P.y * min(1.0, hb.x / hb.y));
                q = c + (q - c) / mag;
                float blurR = P.w * P.y;
                float fr = k * k * k * 1.8 * edges.z * P.y;
                half4 col;
                if (fr > 0.35) {
                    half4 cg = frost(q, blurR);
                    half4 cr = frost(q + n * fr, blurR);
                    half4 cb = frost(q - n * fr, blurR);
                    col = half4(cr.r, cg.g, cb.b, max(cg.a, max(cr.a, cb.a)));
                } else {
                    col = frost(q, blurR);
                }
                // Lekko przyciemnione wnętrze — biały tekst na szkle zostaje czytelny.
                col.rgb *= half(1.0 - 0.2 * P.y);
                return col;
            }
        """
    }
}
