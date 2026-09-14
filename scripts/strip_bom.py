#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""strip_bom.py - remove a UTF-8 BOM from files.

A PowerShell rewrite (`Set-Content`/`Out-File`) drops a UTF-8 BOM onto files it
touches (hit 2026-08-18, C24: a `Set-Content` rewrite BOM'd Kotlin sources;
recovered by stripping it). This restores BOM-free UTF-8. Reports each file's
before/after state; files without a BOM are left untouched.

Usage (from repo root, uv venv):
  uv run python scripts/strip_bom.py app/src/main/res/values/strings.xml ...
Exit code 0 on success (even when nothing had a BOM); 1 if any file was
unreadable.
"""

from __future__ import annotations

import argparse
import sys

BOM = b"\xef\xbb\xbf"


def main() -> int:
    parser = argparse.ArgumentParser(description="Strip a UTF-8 BOM from files.")
    parser.add_argument("files", nargs="+", help="files to check/strip")
    args = parser.parse_args()

    rc = 0
    for path in args.files:
        try:
            with open(path, "rb") as f:
                data = f.read()
        except OSError as exc:
            print(f"error: {path}: {exc}", file=sys.stderr)
            rc = 1
            continue
        if data.startswith(BOM):
            with open(path, "wb") as f:
                f.write(data[len(BOM):])
            print(f"stripped BOM: {path}")
        else:
            print(f"no BOM: {path}")
    return rc


if __name__ == "__main__":
    sys.exit(main())
