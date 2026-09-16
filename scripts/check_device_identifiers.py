#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""check_device_identifiers.py - reject device identifiers in committed content.

A physical device's serial, model code or IMEI can identify the exact person or
device; the guardrail (AGENTS.md "Repo hygiene", DECISIONS.md "[2026-08-15]" and
"[2026-08-20]") forbids them in repo content, CI logs and PR bodies. This hook
automates the pre-commit sweep (the 2026-08-20 decision's "future candidate"):
it greps every staged file and fails the commit with a report.

Detected shapes (additive - add a shape only when a real identifier uses it):

- IMEI:     15 consecutive digits (\b\d{15}\b).
- Model:    Samsung-style model codes `SM-<letter><digits>` (e.g.
             `SM-XXXXXX`-shaped but with a real letter+digit sequence, not the
             all-X docs placeholder, which is intentionally NOT matched).
- Serial:   Android adb serials in the observed prefix shape (`RFCX`-prefixed,
             11 characters total). Extend the regex when a different real
             serial shape leaks.

Usage (pre-commit):  entry: python scripts/check_device_identifiers.py
                     pass_filenames: true   (scans the staged files)
"""

from __future__ import annotations

import os
import re
import sys

# Matches the whole shape, so a bare placeholder stays safe and partial strings
# (e.g. `RFCX-410-VZ2Z`) are not flagged (a real identifier would be pasted as-is).
IMEI_RE = re.compile(r"\b\d{15}\b")
MODEL_RE = re.compile(r"\bSM-[A-Z][0-9]{3,}\b")
SERIAL_RE = re.compile(r"\bRFCX[0-9A-Z]{7}\b")

PATTERNS = [
    ("IMEI", IMEI_RE),
    ("model code", MODEL_RE),
    ("serial", SERIAL_RE),
]

# --selftest fixtures: each (text, expected_findings) pair drives the same matchers the gate uses,
# so a regex drift (too strict / too lax) fails the selftest before it can silently leak a real
# identifier. Kept as comments-only strings (never matched as content).
SELFTEST_CASES = [
    # Must be found (real-identifier shapes, kept SYNTHETIC — the fixtures must never contain an
    # actual device's serial/model/IMEI, or the guardrail itself would leak one).
    ("serial RFCX1234567 imei 987654321098765 model SM-A9999", 3),
    ("path/to/SM-A9999-report.md", 1),
    ("line with 123456789012345 on it", 1),
    # Must NOT be found (placeholders / partials / other text).
    ("serial RFCX-123-4567 (partial, not a real paste)", 0),
    ("the SM-XXXXXX docs placeholder is intentionally safe", 0),
    ("no identifiers here: 12345 and SM-X and 9999", 0),
]


def _run_selftest() -> int:
    failures = 0
    for text, expected in SELFTEST_CASES:
        found = sum(len(regex.findall(text)) for _, regex in PATTERNS)
        if found != expected:
            print(f"selftest FAIL: expected {expected} finding(s), got {found} in {text!r}")
            failures += 1
    if failures:
        print("check_device_identifiers selftest failed; fix the regexes or the fixtures.")
        return 1
    print(f"selftest OK: {len(SELFTEST_CASES)} fixtures matched ({sum(c for _, c in SELFTEST_CASES)} findings).")
    return 0


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "--selftest":
        return _run_selftest()
    bad = False
    for path in sys.argv[1:]:
        # The selftest fixtures above are the only legitimate identifier-shaped strings in the
        # repo; scanning the script itself would be a false positive (it is never committed content).
        if os.path.abspath(path) == os.path.abspath(__file__):
            continue
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except (OSError, UnicodeDecodeError):
            # A non-UTF-8 (binary) file is not a source of identifier text.
            continue
        for label, regex in PATTERNS:
            for match in regex.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                print(f"{path}:{line_no}: {label} '{match.group(0)}'")
                bad = True
    if bad:
        print("Device identifiers are forbidden in repo content (AGENTS.md 'Repo hygiene').")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
