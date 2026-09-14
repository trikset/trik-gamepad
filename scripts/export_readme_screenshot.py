#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""Export the README hero screenshot from the HudThemeTest render.

The committed README hero must be light (docs render fast online), so this
reads the full-resolution render that `HudThemeTest` writes to
`app/build/outputs/screenshots/hud_connected.png` (S10e profile 2280x1080),
downscales it to a display-friendly width and re-encodes it as a JPEG.

Keep this image fresh after a HUD change:
  1. `./gradlew test` (the unit-test gate re-renders hud_connected.png), then
  2. `uv run python scripts/export_readme_screenshot.py` and commit the
     regenerated `docs/img/hud_connected.jpg`.

`--check` exits 1 when the committed JPEG is older than the source render, so
a release checklist can verify freshness without silently overwriting.

Exit code: 0 on success, 1 on a stale/`--check` failure.
"""

from __future__ import annotations

import argparse
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(
    ROOT, "app", "build", "outputs", "screenshots", "hud_connected.png"
)
DST = os.path.join(ROOT, "docs", "img", "hud_connected.jpg")

# GitHub renders README images around this width; nothing is gained by keeping
# the full 2280px in the repo.
WIDTH = 1280
JPEG_QUALITY = 82


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="exit 1 if the committed JPEG is stale (no rewrite)",
    )
    args = parser.parse_args()

    if not os.path.exists(SRC):
        print(f"source render missing: {SRC} (run ./gradlew test first)")
        return 1 if args.check else 0
    if args.check:
        if not os.path.exists(DST):
            print(f"committed JPEG missing: {DST} (run the export first)")
            return 1
        if os.path.getmtime(SRC) > os.path.getmtime(DST):
            print(
                "STALE: source render is newer than the committed JPEG "
                f"({os.path.basename(DST)}) - re-run the export"
            )
            return 1
        print(f"OK: {os.path.basename(DST)} is up to date")
        return 0

    src_bytes = os.path.getsize(SRC)
    with Image.open(SRC) as img:
        if img.width > WIDTH:
            height = round(img.height * WIDTH / img.width)
            img = img.resize((WIDTH, height), Image.Resampling.LANCZOS)
        img = img.convert("RGB")
        os.makedirs(os.path.dirname(DST), exist_ok=True)
        img.save(DST, "JPEG", quality=JPEG_QUALITY, optimize=True)
    dst_bytes = os.path.getsize(DST)
    print(f"exported {DST} ({img.width}x{img.height})")
    print(f"size: {src_bytes / 1024:.0f} KiB -> {dst_bytes / 1024:.0f} KiB "
          f"({dst_bytes * 100 / src_bytes:.0f}%)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
