#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""jacoco_report.py - summarize the JaCoCo coverage report (XML + HTML).

The coverage gate (`jacocoTestCoverageVerification`, app/build.gradle) fails
with a bare "branches covered ratio is 0.84, but expected minimum is 0.85".
This script turns that into an actionable list: global counters and the
per-class missed branches/lines that a coverage test must hit next. It reads
the generated report files; run `./gradlew jacocoTestReport` first.

Two sources, two subcommands:

- totals:  read `jacocoTestReport.xml` (the class-level counters), print the
           global COUNTER totals (INSTRUCTION/LINE/BRANCH/... + the ratio that
           the gate enforces) and, with --class-filters, the per-class missed
           branches/lines ordered by missed-branches desc.
- lines:   read the source HTML (html/com.trikset.gamepad2/Class.kt.html) and
           print the not-covered source line numbers for the named classes —
           the exact lines a test must execute.

Usage (from repo root, uv venv):
  uv run python scripts/jacoco_report.py totals \
      app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml \
      --class UdpTransport --class SenderService
  uv run python scripts/jacoco_report.py lines \
      app/build/reports/jacoco/jacocoTestReport/html/com.trikset.gamepad2 \
      UdpTransport SenderService
Exit code 0 on success; 1 when the XML/HTML is missing or unreadable.
"""

from __future__ import annotations

import argparse
import os
import re
import sys

BRANCH_GATE = 0.84  # mirror of app/build.gradle jacocoTestCoverageVerification


def totals_mode(args: argparse.Namespace) -> int:
    try:
        with open(args.xml, encoding="utf-8") as f:
            xml = f.read()
    except OSError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    # The global counters are the LAST occurrence of each type in the file
    # (the root-level summary after all classes).
    by_type: dict[str, tuple[int, int]] = {}
    for ctype, missed, covered in re.findall(
        r'<counter type="(\w+)" missed="(\d+)" covered="(\d+)"/>', xml
    ):
        by_type[ctype] = (int(missed), int(covered))

    print("global counters:")
    for ctype in ("INSTRUCTION", "LINE", "BRANCH", "COMPLEXITY", "METHOD", "CLASS"):
        if ctype not in by_type:
            continue
        missed, covered = by_type[ctype]
        total = missed + covered
        ratio = covered / total if total else 0.0
        print(f"  {ctype:12s} missed={missed:5d} covered={covered:5d} "
              f"total={total:5d} ratio={ratio:.3f}")
    missed_b, covered_b = by_type.get("BRANCH", (0, 0))
    total_b = missed_b + covered_b
    if total_b:
        status = "OK" if covered_b / total_b >= BRANCH_GATE else "FAILS GATE"
        print(f"  branch gate (>= {BRANCH_GATE}): {status}")

    if args.class_filters:
        print("\nper-class missed branches (desc):")
        rows = []
        for m in re.finditer(r'<class name="([^"]+)" sourcefilename="[^"]+">(.*?)</class>', xml, re.S):
            name, body = m.group(1), m.group(2)
            if not any(f in name for f in args.class_filters):
                continue
            bc = re.findall(r'<counter type="BRANCH" missed="(\d+)" covered="(\d+)"/>', body)
            lc = re.findall(r'<counter type="LINE" missed="(\d+)" covered="(\d+)"/>', body)
            bmissed = int(bc[-1][0]) if bc else 0
            lmissed = int(lc[-1][0]) if lc else 0
            rows.append((bmissed, lmissed, name))
        for bmissed, lmissed, name in sorted(rows, reverse=True):
            print(f"  missed={bmissed:4d} br / {lmissed:4d} lines  {name}")
    return 0


def lines_mode(args: argparse.Namespace) -> int:
    if not os.path.isdir(args.html_dir):
        print(f"error: {args.html_dir} is not a directory", file=sys.stderr)
        return 1
    for cls in args.classes:
        path = os.path.join(args.html_dir, f"{cls}.kt.html")
        try:
            with open(path, encoding="utf-8") as f:
                html = f.read()
        except OSError as exc:
            print(f"error: {exc}", file=sys.stderr)
            continue
        nc = [ln for cls_, ln in re.findall(r'<(?:span|div|td) class="[a-z ]*nc[a-z ]*" id="L(\d+)"', html)]
        print(f"{cls}: {len(nc)} uncovered line(s): {nc}")
        for ln in nc:
            m = re.search(r'<(?:span|div|td) class="[a-z ]*nc[a-z ]*" id="L' + ln + r'">(.*?)</(?:span|div|td)>', html, re.S)
            if m:
                content = re.sub(r"<[^>]+>", "", m.group(1))
                print(f"    L{ln}: {content}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize the JaCoCo coverage report.")
    sub = parser.add_subparsers(dest="mode", required=True)

    totals = sub.add_parser("totals", help="global counters + per-class missed branches")
    totals.add_argument("xml", help="path to jacocoTestReport.xml")
    totals.add_argument("--class", dest="class_filters", action="append", default=[],
                        help="substring filter on class name (repeatable)")

    lines = sub.add_parser("lines", help="uncovered source lines for named classes")
    lines.add_argument("html_dir", help="path to the html/<package> report directory")
    lines.add_argument("classes", nargs="+", help="class file names without the .kt.html suffix")

    args = parser.parse_args()
    if args.mode == "totals":
        return totals_mode(args)
    return lines_mode(args)


if __name__ == "__main__":
    sys.exit(main())
