import math, sys, json, subprocess, os

HERE = os.path.dirname(os.path.abspath(__file__))
SLANT = 0.16
T = 0.38

L = [(0, 12, 38)]
LOOP = [(34, 110, 70), (46, 186, 88), (34, 224, 180), (20, 188, 268)]

G = {
    'a': dict(lead=L + [(10, 60, 86)], hlead=[], body=[
        (36, 100, 0), (58, 90, 310, 160), (36, 100, 180), (10, 58, 266), (32, 0, 0), (60, 38, 72),
        (66, 98, 86, 272), (68, 22, 270), (80, 1, 0), (96, 12, 38)], adv=96),
    'b': dict(lead=L, body=LOOP + [
        (18, 90, 271), (20, 16, 275), (38, 0, 0), (64, 48, 88),
        (54, 92, 140), (38, 96, 205), (36, 82, 295), (52, 74, 10), (84, 88, 12)], adv=84, high=True),
    'c': dict(lead=L + [(10, 60, 86)], body=[
        (36, 100, 0), (58, 90, 310, 160), (36, 100, 180), (10, 58, 266), (34, 0, 0), (70, 12, 38)], adv=70),
    'e': dict(lead=L, hlead=[(18, 60, 330)], body=[
        (46, 66, 52), (30, 100, 180), (6, 58, 268), (30, 0, 0), (70, 12, 38)], adv=70),
    'h': dict(lead=L, body=LOOP + [
        (18, 100, 270), (17, 0, 270, 80), (24, 56, 70), (46, 98, 0), (66, 68, 280),
        (68, 20, 270), (82, 1, 0), (100, 12, 38)], adv=100),
    'i': dict(lead=L, body=[
        (30, 100, 72, 266), (32, 20, 270), (44, 1, 0), (62, 12, 38)], adv=62,
        marks=[('dot', 36, 142)]),
    'j': dict(lead=L, body=[
        (30, 100, 72, 266), (30, 0, 268), (26, -86, 264), (12, -122, 180), (0, -100, 92),
        (22, -30, 52), (60, 12, 38)], adv=60, marks=[('dot', 36, 142)]),
    'l': dict(lead=L, body=LOOP + [
        (18, 80, 271), (22, 14, 280), (36, 0, 0), (58, 12, 38)], adv=58),
    'm': dict(lead=[(0, 12, 50)], body=[
        (14, 88, 76), (30, 100, 0), (44, 70, 276), (44, 0, 270, 82),
        (50, 62, 70), (68, 100, 0), (84, 70, 278), (84, 0, 270, 82),
        (90, 62, 70), (108, 100, 0), (124, 70, 278), (126, 20, 270), (138, 1, 0),
        (156, 12, 38)], adv=156),
    'n': dict(lead=[(0, 12, 50)], body=[
        (14, 88, 76), (30, 100, 0), (44, 70, 276), (44, 0, 270, 82),
        (50, 62, 70), (68, 100, 0), (84, 70, 278), (86, 20, 270), (98, 1, 0),
        (116, 12, 38)], adv=116),
    'o': dict(lead=L + [(10, 60, 86)], hlead=[], body=[
        (36, 100, 0), (56, 94, 330, 170), (36, 100, 180), (8, 54, 268), (38, 0, 0), (68, 50, 90),
        (56, 94, 135), (40, 98, 200), (36, 84, 290), (52, 76, 10), (86, 90, 12)], adv=86, high=True,
        end=[(36, 100, 0), (56, 94, 330, 170), (36, 100, 180), (8, 54, 268), (38, 0, 0), (68, 50, 90),
             (54, 96, 145), (30, 100, 188)],
        first=[(56, 94, 170), (36, 100, 180), (8, 54, 268), (38, 0, 0), (68, 50, 90),
               (56, 94, 135), (40, 98, 200), (36, 84, 290), (52, 76, 10), (86, 90, 12)]),
    'r': dict(lead=[(0, 12, 45)], body=[
        (24, 98, 72, 320), (36, 86, 330, 40), (50, 96, 20, 272), (52, 20, 270), (64, 1, 0),
        (82, 12, 38)], adv=82),
    's': dict(lead=[(0, 12, 45)], body=[
        (30, 104, 68, 296), (50, 40, 272), (34, 1, 190), (20, 12, 115, 350),
        (60, 12, 18)], adv=60),
    't': dict(lead=[(0, 12, 42)], body=[
        (36, 160, 78, 268), (38, 20, 270), (50, 1, 0), (68, 12, 38)], adv=68,
        marks=[('line', 14, 106, 64, 110)]),
    'u': dict(lead=[(0, 12, 50)], body=[
        (18, 100, 76, 268), (18, 26, 268), (34, 1, 0), (54, 44, 76),
        (60, 100, 84, 268), (60, 20, 270), (72, 1, 0), (90, 12, 38)], adv=90),
    'р': dict(lead=[(0, 12, 50)], body=[
        (18, 98, 76, 266), (15, -116, 266, 86, 0.2), (20, 70, 86, 64), (40, 98, 0), (60, 64, 274),
        (42, 8, 198), (22, 24, 120, 345, 0.3), (74, 12, 30)], adv=74),
}
ACUTE = ('line', 44, 124, 64, 152)
ALIAS = {'á': 'a', 'п': 'n', 'и': 'u', 'в': 'b', 'і': 'i', 'т': 'm'}

WORDS = ["hej", "hello", "ahoj", "привіт", "hallo"]
LANGS = {"hej": "pl", "hello": "en", "ahoj": "cs", "привіт": "uk", "hallo": "de"}


def knot(k):
    x, y, ai = k[0], k[1], k[2]
    ao = k[3] if len(k) > 3 else ai
    t = k[4] if len(k) > 4 else T
    return x, y, ai, ao, t


def glyph_knots(word):
    knots, marks = [], []
    cx, prev_high = 0.0, False
    for i, ch in enumerate(word):
        base = ALIAS.get(ch, ch)
        g = G[base]
        if i == 0 and 'first' in g:
            seq = []
        elif i == 0:
            seq = list(g['lead'])
        elif prev_high:
            seq = list(g.get('hlead', g['lead'][1:]))
        else:
            seq = list(g['lead'][1:])
        if i == 0 and 'first' in g:
            seq += g['first']
        else:
            seq += g.get('end', g['body']) if i == len(word) - 1 else g['body']
        for k in seq:
            x, y, ai, ao, t = knot(k)
            knots.append((x + cx, y, ai, ao, t))
        ms = list(g.get('marks', []))
        if ch == 'á':
            ms.append(ACUTE)
        for m in ms:
            if m[0] == 'dot':
                marks.append(('dot', m[1] + cx, m[2]))
            else:
                marks.append(('line', m[1] + cx, m[2], m[3] + cx, m[4]))
        cx += g['adv']
        prev_high = g.get('high', False)
    return knots, marks, cx


def tf(x, y):
    return (x + SLANT * y, -y)


def beziers(knots):
    segs = []
    for (x0, y0, _, a0, t0), (x1, y1, a1, _, _) in zip(knots, knots[1:]):
        d = math.hypot(x1 - x0, y1 - y0)
        h = t0 * d
        c1 = (x0 + h * math.cos(math.radians(a0)), y0 + h * math.sin(math.radians(a0)))
        c2 = (x1 - h * math.cos(math.radians(a1)), y1 - h * math.sin(math.radians(a1)))
        segs.append(((x0, y0), c1, c2, (x1, y1)))
    return segs


def fmt(p):
    x, y = tf(*p)
    return "%.1f,%.1f" % (x, y)


def path_data(knots):
    segs = beziers(knots)
    s = "M" + fmt(segs[0][0])
    for _, c1, c2, p in segs:
        s += " C" + fmt(c1) + " " + fmt(c2) + " " + fmt(p)
    return s


def mark_data(m):
    if m[0] == 'dot':
        return "M%s L%s" % (fmt((m[1], m[2])), fmt((m[1] + 1.5, m[2] + 1.5)))
    return "M%s L%s" % (fmt((m[1], m[2])), fmt((m[3], m[4])))


def word_strokes(word):
    knots, marks, width = glyph_knots(word)
    return [path_data(knots)] + [mark_data(m) for m in marks], width


def sheet(out_png, words, debug=False, scale=0.62, cols=2, rowh=245, top=150):
    lines = []
    colw = 1400 / cols
    for i, w in enumerate(words):
        col, row = i % cols, i // cols
        ox = 40 + col * colw + 30
        oy = top + row * rowh
        strokes, _ = word_strokes(w)
        lines.append("%d|%d|1|14|text|%s" % (ox - 30, oy - 140, w))
        for s in strokes:
            lines.append("%.1f|%.1f|%.3f|%.1f|#F2F4F8|%s" % (ox, oy, scale, 11 * scale, s))
        if debug:
            knots, _, _ = glyph_knots(w)
            for k in knots:
                x, y = tf(k[0], k[1])
                lines.append("%.1f|%.1f|%.3f|%.1f|#FF5A5A|M%.1f,%.1f L%.1f,%.1f" % (ox, oy, scale, 5, x, y, x + .4, y + .4))
    spec = os.path.join(HERE, "spec.txt")
    open(spec, "w", encoding="utf-8").write("\n".join(lines))
    rows = (len(words) + cols - 1) // cols
    subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", os.path.join(HERE, "render.ps1"),
                    spec, out_png, "1400", str(top + 40 + rows * rowh)], check=True)


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "words"
    if what == "words":
        sheet(os.path.join(HERE, "words.png"), WORDS, debug="-d" in sys.argv)
    elif what == "letters":
        sheet(os.path.join(HERE, "letters.png"), sys.argv[2].split(",") if len(sys.argv) > 2 else list("abcehijlmnorstu"),
              debug=True, scale=1.5, cols=4, rowh=560, top=380)
    elif what == "zoom":
        sheet(os.path.join(HERE, "zoom.png"), sys.argv[2].split(","), debug="-d" in sys.argv,
              scale=1.0, cols=2, rowh=380, top=250)
    else:
        sheet(os.path.join(HERE, "one.png"), what.split(","), debug="-d" in sys.argv, scale=1.0, cols=2)
