import sys, struct, os

src, dst = sys.argv[1], sys.argv[2]
ranges = []
for line in open(src, encoding="ascii", errors="ignore"):
    if not line or line[0] == "#":
        continue
    parts = line.strip().split(",")
    if len(parts) != 3:
        continue
    a, b, cc = int(parts[0]), int(parts[1]), parts[2].strip().upper()
    if len(cc) != 2 or not cc.isalpha():
        continue
    ranges.append((a, b, cc))
ranges.sort()

starts, codes = [], []
pos = 0
for a, b, cc in ranges:
    if a < pos:
        a = pos
    if a > b:
        continue
    if a > pos:
        starts.append(pos); codes.append("")
    if codes and codes[-1] == cc and starts:
        pass
    else:
        starts.append(a); codes.append(cc)
    pos = b + 1
if pos <= 0xFFFFFFFF:
    starts.append(pos); codes.append("")

table = sorted(set(c for c in codes if c))
index = {c: i for i, c in enumerate(table)}
with open(dst, "wb") as f:
    f.write(b"SCG1")
    f.write(struct.pack(">HI", len(table), len(starts)))
    for c in table:
        f.write(c.encode("ascii"))
    f.write(struct.pack(">%dI" % len(starts), *starts))
    f.write(bytes(255 if not c else index[c] for c in codes))
print("ranges", len(ranges), "entries", len(starts), "countries", len(table), "bytes", os.path.getsize(dst))
