#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""build_symbol_font.py - regenerate the bundled HUD symbol font.

Downloads the pinned DejaVuSansMono Nerd Font release, subsets it to exactly the
glyphs the app uses (the fundamental HUD symbols + the ASCII letters/digits users
can realistically type as magic-button symbols), cmap-verifies every required
codepoint, and writes the result to app/src/main/res/font/ with its license texts
next to it in app/src/main/res/raw/.

WHY this exact subset (see MEMORY.md "Symbol font" + DESIGN.md "Magic button
symbols"):
  * The Nerd Font patches the IEC power symbols (U+23FB-23FE, U+2B58) into
    DejaVu Sans Mono - the only free/monospace source of the pill's power glyph.
  * Everything else renders via Android's per-glyph system-font fallback
    (Roboto for Latin/Cyrillic, Noto Color Emoji for emoji), so bundling whole
    Unicode blocks or the PUA icon sets would only bloat the APK.
  * The subset is MONOSPACE (the Mono variant), so MagicButtonPanel's 0.6x
    diameter sizing and centerGlyph ink-box math stay exact.

Usage:  uv run python scripts/build_symbol_font.py
Run whenever the pinned release or the glyph list below changes; the committed
.ttf + license files must be regenerated together.
"""

from __future__ import annotations

import io
import os
import sys
import tarfile
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from glyph_metrics import emit_kotlin

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = os.path.join(ROOT, ".tmp", "fonts")
FONT_SRC_REL = "DejaVuSansMNerdFontMono-Regular.ttf"
FONT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "font", "symbols_mono.ttf")
METRICS_OUT = os.path.join(
    ROOT, "app", "src", "main", "kotlin", "com", "trikset", "gamepad2", "glyphs", "GlyphMetrics.kt"
)
RAW_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")

# Pinned release (see https://github.com/ryanoasis/nerd-fonts/releases).
NERD_RELEASE = "v3.5.0"
ARCHIVE_URL = (
    "https://github.com/ryanoasis/nerd-fonts/releases/download/"
    f"{NERD_RELEASE}/DejaVuSansMono.tar.xz"
)

# Codepoints the app must render with the bundled font. Everything else falls
# back to system fonts. Letters/digits/symbols = the realistic magic-button
# palette; the rest are the fixed HUD glyphs.
REQUIRED = {
    # Fixed HUD glyphs.
    "▲": 0x25B2,  # magic default 1
    "■": 0x25A0,  # magic default 2
    "●": 0x25CF,  # magic default 3
    "✕": 0x2715,  # magic default 4
    "◆": 0x25C6,  # magic default 5
    "⏻": 0x23FB,  # pill: tap to connect (IEC power symbol)
    "↺": 0x21BA,  # pill: connecting
    "⚙": 0x2699,  # settings gear
    "⏼": 0x23FC,  # IEC power on-off (reserved)
    "⏽": 0x23FD,  # IEC power on (reserved)
    "⏾": 0x23FE,  # IEC power sleep (reserved)
    "⭘": 0x2B58,  # IEC power off (reserved)
    # Video-source preset chips (Settings > Video quick-fill): eye base + digit for the module
    # cameras, eye-off for "no video", usb for the USB camera.
    "md-eye": 0xF0208,  # eye (camera/video source base)
    "md-eye_off": 0xF0209,  # eye with a slash (no video)
    "md-usb": 0xF0553,  # USB (USB web-camera)
    # ASCII palette for user-typed magic symbols (digits, math, pipe, letters).
    "0": 0x30, "1": 0x31, "2": 0x32, "3": 0x33, "4": 0x34,
    "5": 0x35, "6": 0x36, "7": 0x37, "8": 0x38, "9": 0x39,
    "-": 0x2D, "+": 0x2B, "=": 0x3D, "|": 0x7C,
    "X": 0x58, "Y": 0x59, "O": 0x4F, "W": 0x57, "M": 0x4D,
    "A": 0x41, "B": 0x42, "C": 0x43, "U": 0x55, "L": 0x4C, "R": 0x52,
}
# Space is always kept (layout metrics). Explicit codepoint list (ints, not a
# range string) so pyftsubset never mis-parses a token as a string.
SUBSET_UNICODES = [0x20] + list(range(0x30, 0x3A)) + [0x2B, 0x2D, 0x3D, 0x7C] + sorted(
    cp for name, cp in REQUIRED.items() if cp != 0x20
)


def log(msg: str) -> None:
    print(f"==> {msg}")


def download() -> bytes:
    archive_path = os.path.join(TMP, f"DejaVuSansMono-{NERD_RELEASE}.tar.xz")
    if not os.path.exists(archive_path):
        os.makedirs(TMP, exist_ok=True)
        log(f"downloading {ARCHIVE_URL}")
        urllib.request.urlretrieve(ARCHIVE_URL, archive_path)
    else:
        log(f"archive cached: {archive_path}")
    with open(archive_path, "rb") as f:
        return f.read()


def extract_font(archive: bytes) -> bytes:
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:xz") as tar:
        member = tar.getmember(FONT_SRC_REL)
        log(f"extracted {FONT_SRC_REL}")
        return tar.extractfile(member).read()


def subset_font(ttf: bytes) -> bytes:
    font = TTFont(io.BytesIO(ttf))
    opts = subset.Options()
    opts.ignore_missing_unicodes = False
    opts.name_IDs = [1, 2, 3, 4, 5, 6]
    opts.notdef_outline = True
    opts.recalc_bounds = True
    ss = subset.Subsetter(options=opts)
    ss.populate(unicodes=SUBSET_UNICODES)
    ss.subset(font)
    out = io.BytesIO()
    font.save(out)
    log(f"subset written ({out.tell()} bytes)")
    return out.getvalue()


def verify(font_bytes: bytes) -> None:
    cmap = TTFont(io.BytesIO(font_bytes)).getBestCmap()
    missing = [name for name, cp in REQUIRED.items() if cp not in cmap]
    if missing:
        log(f"FAILED: missing codepoints: {missing}")
        sys.exit(1)
    log("cmap verification passed (all required codepoints present)")


def write_license_files() -> None:
    # Bitstream Vera / DejaVu license - the base font; bundling requires it.
    vera = os.path.join(TMP, "LICENSE.txt")
    with open(vera, "r", encoding="utf-8") as f:
        vera_text = f.read()
    os.makedirs(RAW_DIR, exist_ok=True)
    with open(os.path.join(RAW_DIR, "symbol_font_vera_license.txt"), "w", encoding="utf-8") as f:
        f.write("DejaVu Sans Mono (Bitstream Vera base) - bundled subset\n")
        f.write("Source: https://github.com/dejavu-fonts/dejavu-fonts\n")
        f.write("Nerd Font patch: https://github.com/ryanoasis/nerd-fonts\n\n")
        f.write(vera_text)
    # IEC power symbols come from jloughry/unicode (MIT), patched in by Nerd Fonts.
    iec = os.path.join(RAW_DIR, "symbol_font_iec_license.txt")
    with open(iec, "w", encoding="utf-8") as f:
        f.write("IEC Power Symbols (U+23FB-23FE, U+2B58) added by the Nerd Font patch\n")
        f.write("Source: https://github.com/jloughry/Unicode\n")
        f.write("License: MIT\n\n")
        f.write("Copyright (c) 2015 Jason Loughry\n")
        f.write("Permission is hereby granted, free of charge, to any person obtaining a copy\n")
        f.write("of this software and associated documentation files (the \"Software\"), to deal\n")
        f.write("in the Software without restriction, including without limitation the rights\n")
        f.write("to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n")
        f.write("copies of the Software, and to permit persons to whom the Software is\n")
        f.write("furnished to do so, subject to the following conditions:\n")
        f.write("The above copyright notice and this permission notice shall be included in all\n")
        f.write("copies or substantial portions of the Software.\n")
        f.write("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n")
        f.write("IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n")
        f.write("FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n")
        f.write("AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n")
        f.write("LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n")
        f.write("OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n")
        f.write("SOFTWARE.\n")
    log("license files written to res/raw/")


def main() -> None:
    archive = download()
    ttf = extract_font(archive)
    subset_ttf = subset_font(ttf)
    verify(subset_ttf)
    os.makedirs(os.path.dirname(FONT_OUT), exist_ok=True)
    with open(FONT_OUT, "wb") as f:
        f.write(subset_ttf)
    log(f"font written: {FONT_OUT}")
    # Every bundled glyph ships with its em-relative "render in a row" metrics (visualHeightEm +
    # medianBiasEm) so GlyphTextView/GlyphRow can equalize and align glyphs on any device. The
    # metrics are computed from the exact subset written above - a glyph without metrics fails
    # the build here (all REQUIRED codepoints must resolve).
    os.makedirs(os.path.dirname(METRICS_OUT), exist_ok=True)
    emit_kotlin(REQUIRED, subset_ttf, reference="1", out_path=METRICS_OUT)
    write_license_files()


if __name__ == "__main__":
    main()
