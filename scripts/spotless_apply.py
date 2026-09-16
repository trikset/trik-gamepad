#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""spotless_apply.py - run Gradle's spotlessApply from the pre-commit hook.

Cross-platform (Windows / Linux / macOS): picks `gradlew.bat` vs `./gradlew` by
OS. Used by .pre-commit-config.yaml (language: system) so the hook no longer
needs `cmd /c gradlew.bat` on Windows.
"""

from __future__ import annotations

import sys

from _gradle import call_gradle

if __name__ == "__main__":
    sys.exit(call_gradle("spotlessApply", "spotlessApply"))
