# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""Shared cross-platform Gradle wrapper resolution for trik-gamepad scripts.

Picks `gradlew.bat` on Windows and `./gradlew` elsewhere so the Python tooling
(gate.py, spotless_apply.py) runs unchanged on Windows / Linux / macOS.
"""

from __future__ import annotations

import os

from run_bounded import run

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Generous default so legit slow cold builds never false-trip; the point is
# bounding a hang, not speed (see AGENTS.md "Operational rules").
GRADLE_TIMEOUT_S = 900


def gradle_cmd() -> str:
    return os.path.join(ROOT, "gradlew.bat") if os.name == "nt" else os.path.join(ROOT, "gradlew")


def call_gradle(label: str, *args: str, log: str | None = None, timeout: float = GRADLE_TIMEOUT_S) -> int:
    """Run a Gradle task with `--no-daemon` under a hard timeout; append output to `log`.

    Runs through run_bounded so a hang (e.g. a Windows daemon holding the output
    pipe) is killed at the process TREE, never leaving the caller blocked.
    Returns the process exit code (124 on timeout; caller decides the verdict).
    """
    print(f"==> {label}")
    cmd = [gradle_cmd(), *args, "--no-daemon"]
    return run(timeout, label, cmd, log)
