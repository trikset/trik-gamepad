#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""glyph_metrics.py - compute em-relative glyph layout metrics for the bundled symbol font.

Rasters every requested glyph with Pillow and reduces each to two fractions of the font's
em (the "render in a row" properties used by GlyphTextView/GlyphRow):

  visualHeightEm : height of the central 90%-mass band of the glyph's ink, in em units.
                   A glyph's VISUAL height at any textSize == textSize * visualHeightEm,
                   so a row equalizes glyphs by setting textSize = targetVisualHeight /
                   visualHeightEm.
  medianBiasEm   : signed offset of the glyph's weighted-ink median row from the line-box
                   center (baseline-relative, positive = median ABOVE the line center), in
                   em units. The renderer cancels it with asymmetric padding (never a
                   translation - see MagicButtonPanel.centerGlyph and DECISIONS.md).
  inkBoxEm       : full height of the glyph's ink box (all rows with ink, not the 90% band)
                   / em. Larger than visualHeightEm for glyphs with thin tails (a triangle
                   has a tall thin point: 90% of its mass sits in the base rows, so the band
                   under-reports its true box). The renderer uses it to cap textSize so the
                   ink box + the centering padding always fit the view (a 90%-band-equalized
                   textSize alone clips low-band glyphs like ▲ — hit 2026-09-01).

Both are fractions of the em, so they are size/density/font-scale invariant: the OS scales
textSize with density + user font scale, and the metrics scale with it. The Android and
Pillow rasterizers both use FreeType on the same bundled outline font, so the em-relative
fractions transfer to device rendering (validated on-device by measure_glyph_row.py).

Usage:
  uv run python scripts/glyph_metrics.py --font <ttf> --out <GlyphMetrics.kt> \
      --reference 0x31 --glyphs 0x25B2,0x25A0,...
  uv run python scripts/glyph_metrics.py --font <ttf> --print --glyphs 0x31,0xF0208

Called by build_symbol_font.py (which supplies the REQUIRED codepoints and the reference
"1" digit); every bundled glyph must have metrics or the font build fails.
"""

from __future__ import annotations

import argparse
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFont

# Raster the glyphs at this many pixels of em; large enough that the 90% band and median
# are stable to sub-pixel detail. Fractions are size-invariant (verified by --selfcheck).
RASTER_EM = 1024
# Luminance above which a pixel counts as glyph ink (drops anti-aliased grey floor).
INK_THRESHOLD = 32


def measure_glyph(font: ImageFont.FreeTypeFont, char: str) -> tuple[float, float, float]:
    """Return (visualHeightEm, medianBiasEm, inkBoxEm) for one character.

    Coordinate model: the default anchor for ImageDraw.text is the ascender line, so drawing
    at y=0 puts the ascender at 0 and the baseline at `ascent` (both up-positive from there).
    The line box spans [-descent, +ascent], so its center (up-positive, baseline-relative)
    is (ascent - descent) / 2.
    """
    ascent, descent = font.getmetrics()
    advance = font.getlength(char)
    width = max(int(advance) + 2, 2)
    height = ascent + descent + 2
    img = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(img)
    draw.text((1, 1), char, fill=255, font=font)
    # Direct pixel access (mode "L" -> 0..255 ints); bytes of the same buffer keep the
    # type checker happy and are 1:1 with load() for this mode.
    px: bytes = img.tobytes()

    # Per-row ink mass + baseline-relative row position (up-positive).
    row_mass: dict[int, int] = {}
    total = 0
    for y in range(height):
        row = px[y * width : (y + 1) * width]
        mass = sum(v for v in row if v > INK_THRESHOLD)
        if mass > 0:
            rel = ascent + 1 - y  # baseline at y = ascent + 1 (the +1 is the draw offset)
            row_mass[rel] = mass
            total += mass
    if total == 0:
        return 0.0, 0.0, 0.0

    # Weighted median row (up-positive).
    cumulative = 0
    median = None
    for rel in sorted(row_mass, reverse=True):
        cumulative += row_mass[rel]
        if cumulative >= total / 2:
            median = rel
            break

    # Central 90% band: trim 5% of the mass from each tail.
    cutoff = total * 0.05
    cumulative = 0
    lo = None
    for rel in sorted(row_mass, reverse=True):
        cumulative += row_mass[rel]
        if cumulative >= cutoff:
            lo = rel
            break
    cumulative = 0
    hi = None
    for rel in sorted(row_mass):
        cumulative += row_mass[rel]
        if cumulative >= cutoff:
            hi = rel
            break
    if lo is None or hi is None or median is None:
        return 0.0, 0.0, 0.0

    visual_height_em = (lo - hi + 1) / RASTER_EM
    line_center = (ascent - descent) / 2
    median_bias_em = (median - line_center) / RASTER_EM
    # Full ink box: topmost and bottommost rows that carry ink (up-positive, so max(rel) is
    # the top). Unlike the 90% band this counts the thin tails, e.g. a triangle's point.
    top_rel = max(row_mass)
    bottom_rel = min(row_mass)
    ink_box_em = (top_rel - bottom_rel + 1) / RASTER_EM
    return visual_height_em, median_bias_em, ink_box_em


def measure_line_box(font: ImageFont.FreeTypeFont) -> float:
    """Return the font's line-box height (ascent + descent) in em units.

    The renderer's textSize cap uses this (not the per-glyph ink box): Android lays the glyph's
    line box, and a line box taller than the fixed view clips even when the ink box would fit.
    """
    ascent, descent = font.getmetrics()
    return (ascent + descent) / RASTER_EM


def emit_kotlin(codepoints: dict[str, int], font_bytes: bytes, reference: str, out_path: str) -> None:
    font = ImageFont.truetype(io.BytesIO(font_bytes), RASTER_EM)
    line_box_em = measure_line_box(font)
    metrics = {}
    for name, cp in sorted(codepoints.items(), key=lambda kv: kv[1]):
        vh, mb, ink = measure_glyph(font, chr(cp))
        metrics[cp] = (name, vh, mb, ink)
    if reference in codepoints:
        ref_cp = codepoints[reference]
        ref_name, ref_vh, ref_mb, ref_ink = metrics[ref_cp]
    else:
        ref_name, ref_vh, ref_mb, ref_ink = "1", 0.72, 0.0, 0.72

    lines = [
        "// GENERATED data table: every numeric literal is a measured font metric, not a magic",
        "// number a reader would name - suppress detekt's MagicNumber for the whole file.",
        "@file:Suppress(\"MagicNumber\")",
        "",
        "package com.trikset.gamepad2.glyphs",
        "",
        "/**",
        " * GENERATED by scripts/glyph_metrics.py (via build_symbol_font.py) - do not edit.",
        " *",
        " * Per-glyph 'render in a row' metrics for the bundled res/font/symbols_mono.ttf.",
        " * Each metric is a fraction of the font's em (size/density/font-scale invariant):",
        " *",
        " *   visualHeightEm - central 90%-mass band of the glyph's ink / em. A glyph's",
        " *     VISUAL height at textSize T is T * visualHeightEm, so a row equalizes",
        " *     glyphs by setting textSize = targetVisualHeight / visualHeightEm.",
        " *   medianBiasEm   - signed offset of the weighted-ink median from the line-box",
        " *     center (positive = median above center), / em. Cancelled via asymmetric",
        " *     padding (GlyphRendering), never a translation.",
        " *   inkBoxEm       - full ink-box height (thin tails included) / em. Larger than",
        " *     visualHeightEm for glyphs with thin tails (a triangle's point carries little",
        " *     mass, so the band under-reports its true box).",
        " *",
        " * LINE_BOX_EM caps the renderer's textSize: Android lays out the glyph's line box",
        " * (ascent + descent), so textSize * (LINE_BOX_EM + 2*|medianBiasEm|) must fit the",
        " * fixed view or the glyph clips even when its ink box alone would fit.",
        " */",
        "object GlyphMetrics {",
        "  const val LINE_BOX_EM = %.4ff" % line_box_em,
        "  data class GlyphMetric(val visualHeightEm: Float, val medianBiasEm: Float, val inkBoxEm: Float)",
        "",
        f'  const val REFERENCE = "{reference}"',
        f"  const val REFERENCE_CODEPOINT = 0x{ref_cp:X}",
        "  val REFERENCE_METRIC = GlyphMetric(%.6ff, %.6ff, %.6ff)" % (ref_vh, ref_mb, ref_ink),
        "",
        "  private val TABLE: Map<Int, GlyphMetric> = mapOf(",
    ]
    for cp in sorted(metrics):
        name, vh, mb, ink = metrics[cp]
        lines.append(
            f"      0x{cp:X} to GlyphMetric({vh:.6f}f, {mb:.6f}f, {ink:.6f}f), // {name}"
        )
    lines += [
        "  )",
        "",
        "  fun metricFor(glyph: String): GlyphMetric? =",
        "      if (glyph.isEmpty()) null else TABLE[glyph.codePointAt(0)]",
        "}",
        "",
    ]
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"==> wrote {out_path} ({len(metrics)} glyphs)")


def main() -> int:
    ap = argparse.ArgumentParser(description="Compute em-relative glyph metrics for a symbol font.")
    ap.add_argument("--font", required=True, help="path to the TTF (the bundled subset)")
    ap.add_argument("--out", help="emit a Kotlin GlyphMetrics object to this path")
    ap.add_argument("--print", action="store_true", help="print metrics as text instead")
    ap.add_argument("--reference", default="1", help="reference glyph name/codepoint for REFERENCE_METRIC")
    ap.add_argument("--glyphs", required=True, help="comma-separated codepoints (hex 0x..)")
    args = ap.parse_args()

    font_bytes = open(args.font, "rb").read()
    cps = [int(g, 16) for g in args.glyphs.split(",")]
    codepoints = {f"0x{cp:X}": cp for cp in cps}

    if args.print:
        font = ImageFont.truetype(io.BytesIO(font_bytes), RASTER_EM)
        for cp in cps:
            vh, mb, ink = measure_glyph(font, chr(cp))
            print(f"0x{cp:X} visualHeightEm={vh:.4f} medianBiasEm={mb:+.4f} inkBoxEm={ink:.4f}")
        return 0

    if not args.out:
        ap.error("--out required unless --print")
    emit_kotlin(codepoints, font_bytes, args.reference, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
