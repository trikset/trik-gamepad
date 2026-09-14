#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""check_xml_comments.py - reject "--" inside XML comments.

aapt2 hard-fails on any "--" inside an XML comment ("The string -- is not
permitted within comments"), and nothing in the normal lint/detekt gate catches
it at commit time (hit twice in one session, Campaign 15). This pre-commit hook
grep-checks every staged .xml file so the build-breaker fails at commit, not at
aapt2.

Usage (pre-commit):  files: \.xml$
"""

from __future__ import annotations

import re
import sys

COMMENT_RE = re.compile(r"<!--.*?-->", re.S)


def main() -> int:
    bad = False
    for path in sys.argv[1:]:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except (OSError, UnicodeDecodeError):
            continue
        for match in COMMENT_RE.finditer(text):
            # Strip the <!-- and --> delimiters; the closing "-->" is legal.
            body = match.group(0)[4:-3]
            if "--" in body:
                print(f"{path}: XML comment contains '--' (aapt2 rejects this)")
                bad = True
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
