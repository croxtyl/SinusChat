import math, io, os
import hw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "java",
                   "pl", "sinusoide", "sinuschat", "ui", "HandwritingData.kt")

def seg_len(p0, c1, c2, p1, n=24):
    L = 0.0; prev = p0
    for i in range(1, n + 1):
        t = i / n; u = 1 - t
        x = u*u*u*p0[0] + 3*u*u*t*c1[0] + 3*u*t*t*c2[0] + t*t*t*p1[0]
        y = u*u*u*p0[1] + 3*u*u*t*c1[1] + 3*u*t*t*c2[1] + t*t*t*p1[1]
        L += math.hypot(x - prev[0], y - prev[1]); prev = (x, y)
    return L

def num(v):
    s = "%.1f" % v
    if s.endswith(".0"): s = s[:-2]
    if s == "-0": s = "0"
    return s + "f"

out = []
lens = {}
for w in hw.WORDS:
    knots, marks, width = hw.glyph_knots(w)
    segs = hw.beziers(knots)
    main = [hw.tf(*segs[0][0])]
    total = 0
    for p0, c1, c2, p1 in segs:
        main += [hw.tf(*c1), hw.tf(*c2), hw.tf(*p1)]
        total += seg_len(hw.tf(*p0), hw.tf(*c1), hw.tf(*c2), hw.tf(*p1))
    lens[w] = round(total)
    strokes = [main]
    for m in marks:
        if m[0] == 'dot':
            a = hw.tf(m[1], m[2]); b = hw.tf(m[1] + 1.5, m[2] + 1.5)
        else:
            a = hw.tf(m[1], m[2]); b = hw.tf(m[3], m[4])
        c1 = (a[0] + (b[0]-a[0])/3, a[1] + (b[1]-a[1])/3)
        c2 = (a[0] + 2*(b[0]-a[0])/3, a[1] + 2*(b[1]-a[1])/3)
        strokes.append([a, c1, c2, b])
    arrs = []
    for st in strokes:
        flat = ", ".join(num(c) for p in st for c in p)
        arrs.append("            floatArrayOf(%s)" % flat)
    out.append('        Word("%s", "%s", arrayOf(\n%s\n        ))' % (hw.LANGS[w], w, ",\n".join(arrs)))
print(lens)
kt = '''package pl.sinusoide.sinuschat.ui

internal object HandwritingData {

    class Word(val lang: String, val text: String, val strokes: Array<FloatArray>)

    const val ASCENT = 236f

    const val DESCENT = 134f

    const val PEN = 12.5f

    val WORDS: List<Word> = listOf(
%s
    )
}
''' % ",\n".join(out)
io.open(OUT, "w", encoding="utf-8", newline="\n").write(kt)
print("written")
