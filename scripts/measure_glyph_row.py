#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""Measure the VISUAL ink alignment of a row of glyph buttons from a uiautomator
dump + screenshot pair.

For each `android.widget.Button` node in the dump, samples the screenshot pixels
inside the node bounds (via png_census) and reports:
  * the weighted-ink median row and the 90%-mass band per button,
  * the median spread across buttons and the union of the 90% bands.

"Row looks aligned" <=> medians within ~1px of each other and every 90% band
overlapping the union. This is the check used to validate the smart-glyph row
(GlyphRow / GlyphRendering) and the video-source preset chips on-device.

Usage:
  uv run python scripts/measure_glyph_row.py --dump <uiautomator.xml> --shot <screenshot.png>
  uv run python scripts/measure_glyph_row.py --dump out.xml --shot shot.png --filter "btn"

Capture the pair with:
  adb shell uiautomator dump /sdcard/window_dump.xml && adb pull ...
  adb shell screencap -p /sdcard/shot.png && adb pull ...
(button bounds come from the dump; the screenshot must match the dump's frame)
"""

import argparse
import re
import sys

sys.path.insert(0, "scripts")
import png_census


def decode_png(path):
    return png_census.decode_png(path)


def parse_buttons(dump_path, content_desc_filter):
    raw = open(dump_path, "rb").read()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("utf-16")
    buttons = []
    for m in re.finditer(r'<node[^>]*class="android.widget.Button"[^>]*/>', text):
        node = m.group(0)
        cd = re.search(r'content-desc="([^"]*)"', node)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if cd and b:
            desc = cd.group(1)
            if content_desc_filter and content_desc_filter not in desc:
                continue
            buttons.append((desc, tuple(int(x) for x in b.groups())))
    return buttons


def row_hist(w, h, bpp, buf, x0, y0, x1, y1, min_lum):
    rows = {}
    for y in range(y0, y1):
        total = 0
        for x in range(x0, x1):
            c = png_census.sample(w, h, bpp, buf, x, y)
            if c:
                lum = (c[0] + c[1] + c[2]) // 3
                if lum > min_lum:
                    total += lum
        rows[y] = total
    return rows


def median_row(rh, total):
    cum = 0.0
    for y in sorted(rh):
        cum += rh[y]
        if cum >= total * 0.5:
            return y
    return None


def band_90(rh, total):
    items = [(y, rh[y]) for y in sorted(rh) if rh[y] > 0]
    if not items:
        return (None, None)
    cum = 0.0
    lo = items[0][0]
    for y, v in items:
        cum += v
        if cum >= total * 0.05:
            lo = y
            break
    cum = 0.0
    hi = items[-1][0]
    for y, v in reversed(items):
        cum += v
        if cum >= total * 0.05:
            hi = y
            break
    return (lo, hi)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--dump", required=True, help="uiautomator dump XML (utf-8 or utf-16)")
    p.add_argument("--shot", required=True, help="matching screenshot PNG")
    p.add_argument("--filter", default="", help="only nodes whose content-desc contains this")
    p.add_argument(
        "--min-lum",
        type=int,
        default=50,
        help="min average pixel luminance counted as ink. Default 50 works for both bright "
        "chips and the dimmed HUD (its glyph ink sits ~64, the circle border ~51, the fill "
        "~17); raise to ~58 to exclude the border ring entirely.",
    )
    args = p.parse_args()

    buttons = parse_buttons(args.dump, args.filter)
    if not buttons:
        print("no matching Button nodes found in the dump")
        return 1

    w, h, bpp, buf = decode_png(args.shot)

    results = {}
    for desc, (x0, y0, x1, y1) in buttons:
        rh = row_hist(w, h, bpp, buf, x0, y0, x1, y1, args.min_lum)
        total = sum(rh.values())
        if total == 0:
            print(f"{desc!r}: no ink above threshold in bounds")
            continue
        median = median_row(rh, total)
        lo, hi = band_90(rh, total)
        results[desc] = dict(total=total, median=median, lo=lo, hi=hi, band_h=hi - lo + 1)
        print(f"{desc!r}: total={total} median={median} 90%band={lo}..{hi} (h={hi-lo+1})")

    if not results:
        print("no buttons with ink found")
        return 1

    medians = [r["median"] for r in results.values()]
    band_lo = min(r["lo"] for r in results.values())
    band_hi = max(r["hi"] for r in results.values())
    print("\ncomparison (target: medians within 1px, 90% bands overlap):")
    print(f"median spread: {max(medians)-min(medians)}px (medians={medians})")
    print(f"union of 90% bands: {band_lo}..{band_hi} (h={band_hi-band_lo+1})")
    for desc, r in results.items():
        overlap = max(0, min(r["hi"], band_hi) - max(r["lo"], band_lo) + 1)
        print(f"  {desc!r}: band overlaps union by {overlap}px")
    return 0


if __name__ == "__main__":
    sys.exit(main())
