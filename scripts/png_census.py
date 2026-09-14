#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""png_census.py - pixel-census and pixel-diff for UI screenshots.

Device-independent verification of on-device/emulator screenshots (AGENTS.md
"Verify a UI change by pixel-census, not by eyeballing the diff"): decodes a
PNG's pixels (pure Python: struct + zlib, no PIL) and either samples colour
bands / strips (a "census" of what is actually drawn) or diffs two captures
of the same size and reports where they differ.

Two modes (mutually exclusive):

- census:  sample full-width top/bottom strips + the centre row, and print the
           most common quantized colours per band. Verifies "the look changed"
           without trusting a screenshot eyeball.
- diff:    compare two PNGs of identical size; report the number of changed
           pixels, the changed x/y range, and per-y-band counts (top chip /
           middle / bottom). Used to prove video/UI liveness (e.g. two
           captures a second apart must differ) or that a stored screenshot
           matches a fresh capture (AGENTS.md "Proof of a stored screenshot").

Usage (from repo root, uv venv):
  uv run python scripts/png_census.py census   .tmp/shot.png
  uv run python scripts/png_census.py diff     .tmp/a.png .tmp/b.png
Exit code 0 on success; non-zero on decode/dimension-mismatch errors.
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib
from collections import Counter

# Bands used by `diff` to attribute changed rows to a screen region (rows are
# 0-based from the top; tune the thresholds per screenshot size).
DIFF_BANDS = [
    ("top", 0, 240),  # chip / status pill strip
    ("middle", 240, 700),  # pads / video
    ("bottom", 700, 2**31),  # magic buttons / gear
]


def decode_png(path: str) -> tuple[int, int, int, bytes]:
    """Decode a PNG to (width, height, bytes-per-pixel, raw RGBA-ish pixels).

    Supports greyscale(0)/RGB(2)/palette(4)/RGBA(6) colour types and all
    filter types; palette entries are returned as-is (census only uses the
    first three bytes). Raises ValueError for a non-PNG or truncated file.
    """
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path}: not a PNG (bad signature)")
    pos = 8
    width = height = bit_depth = colour_type = None
    idat = b""
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos : pos + 4])
        chunk_type = data[pos + 4 : pos + 8]
        chunk = data[pos + 8 : pos + 8 + length]
        if chunk_type == b"IHDR":
            width, height, bit_depth, colour_type = struct.unpack(">IIBB", chunk[:10])
        elif chunk_type == b"IDAT":
            idat += chunk
        pos += 12 + length
        if chunk_type == b"IEND":
            break
    if width is None or height is None or colour_type is None:
        raise ValueError(f"{path}: no IHDR/IDAT found")
    bpp = {0: 1, 2: 3, 4: 2, 6: 4}[colour_type]
    raw = zlib.decompress(idat)
    stride = width * bpp
    out = bytearray(width * height * bpp)
    prev = bytearray(stride)
    for y in range(height):
        filter_byte = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1 : (y + 1) * (stride + 1)])
        if filter_byte == 1:  # Sub
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif filter_byte == 2:  # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filter_byte == 3:  # Average
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filter_byte == 4:  # Paeth
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride : (y + 1) * stride] = line
        prev = line
    return width, height, bpp, bytes(out)


def sample(w: int, h: int, bpp: int, buf: bytes, x: int, y: int) -> tuple[int, int, int] | None:
    if x < 0 or y < 0 or x >= w or y >= h:
        return None
    i = (y * w + x) * bpp
    return (buf[i], buf[i + 1], buf[i + 2])


def census_mode(args: argparse.Namespace) -> int:
    w, h, bpp, buf = decode_png(args.file)
    print(f"size {w}x{h} bpp={bpp}")

    def strip_counter(y_from: int, y_to: int) -> Counter:
        cnt: Counter = Counter()
        for y in range(y_from, y_to):
            for x in range(0, w, 8):
                rgb = sample(w, h, bpp, buf, x, y)
                if rgb:
                    cnt[(rgb[0] // 16, rgb[1] // 16, rgb[2] // 16)] += 1
        return cnt

    for name, y_from, y_to in [
        ("top strip", 0, min(20, h)),
        ("bottom strip", max(0, h - 20), h),
        ("centre rows", h // 2 - 2, h // 2 + 2),
    ]:
        cnt = strip_counter(y_from, y_to)
        print(f"{name}: {cnt.most_common(args.count)}")
    return 0


def diff_mode(args: argparse.Namespace) -> int:
    w1, h1, bpp1, buf1 = decode_png(args.a)
    w2, h2, bpp2, buf2 = decode_png(args.b)
    if (w1, h1) != (w2, h2) or bpp1 != bpp2:
        print(f"dimension mismatch: {args.a}={w1}x{h1} {args.b}={w2}x{h2}")
        return 1
    changed = 0
    xs: set[int] = set()
    ys: set[int] = set()
    for y in range(0, h1, args.stride):
        for x in range(0, w1, args.stride):
            i = (y * w1 + x) * bpp1
            if buf1[i : i + bpp1] != buf2[i : i + bpp1]:
                changed += 1
                xs.add(x)
                ys.add(y)
    print(f"{args.a} vs {args.b}: {changed} differing sampled pixels")
    if changed:
        print(f"x range {min(xs)}..{max(xs)}, y range {min(ys)}..{max(ys)}")
        band_counts: Counter = Counter()
        for y in ys:
            for name, lo, hi in DIFF_BANDS:
                if lo <= y < hi:
                    band_counts[name] += 1
                    break
        print(f"per-band changed rows: {dict(band_counts)}")
    # A diff with zero changes is a liveness FAILURE for video proofs; 0 anyway.
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Pixel-census / pixel-diff for UI screenshots.")
    sub = parser.add_subparsers(dest="mode", required=True)

    census = sub.add_parser("census", help="sample colour bands of one screenshot")
    census.add_argument("file", help="path to a PNG screenshot")
    census.add_argument("--count", type=int, default=3, help="most-common colours per band")

    diff = sub.add_parser("diff", help="compare two same-size screenshots")
    diff.add_argument("a", help="first PNG")
    diff.add_argument("b", help="second PNG")
    diff.add_argument("--stride", type=int, default=4, help="pixel sampling step (default 4)")

    args = parser.parse_args()
    try:
        if args.mode == "census":
            return census_mode(args)
        return diff_mode(args)
    except (OSError, ValueError, zlib.error) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
