#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""run_bounded.py - run any command under a hard wall-clock timeout with a process-tree kill.

Cross-platform bounded runner for trik-gamepad tooling (gradle, adb, npx, ...).
The critical behavior is the TREE kill on timeout: killing only the direct
process leaves children (cmd wrappers, gradle daemons, adb clients) alive and
holding the inherited output pipe, so the CALLER blocks forever even though the
tool "timed out" (hit 2026-08-13: an `adb install` during an emulator offline
blip hung the caller for ~15h). See AGENTS.md "Operational rules".

The child is ALWAYS started with stdout=PIPE (never the caller's own stdout), so
no descendant can hold the caller's output-pipe handle open. A reader thread
pumps decoded output to the sink (the --log file or stdout); the main thread
only waits on proc.wait(timeout) and kills the tree if the budget expires.
This combination guarantees the caller always returns: the pipe EOF is decided
by the reader thread, and proc.wait() is bounded by the timeout.

Usage:
  uv run python scripts/run_bounded.py --timeout 600 --label assembleDebug --log .tmp/x.log -- gradlew.bat --no-daemon assembleDebug
  uv run python scripts/run_bounded.py --timeout 120 -- adb -s emulator-5556 install -r app.apk

Exit code: the wrapped command's exit code, or 124 on timeout. On timeout the
log (or stdout) gets a "TIMEOUT after Ns" marker line.
"""

from __future__ import annotations

import argparse
import codecs
import os
import signal
import subprocess
import sys
import threading


def kill_tree(proc: subprocess.Popen) -> None:
    """Kill the whole process tree so no child can keep a borrowed handle open."""
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(proc.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=30,
        )
    else:
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass


def _pump(stream, sink, close_sink) -> None:
    """Copy child stdout to the sink until EOF; always closes the sink."""
    decoder = None
    try:
        while True:
            chunk = stream.read(65536)
            if not chunk:
                break
            if decoder is None:
                decoder = codecs.getincrementaldecoder("utf-8")("replace")
                decoder.decode(b"")  # prime the decoder state
            try:
                text = decoder.decode(chunk)
            except Exception:
                text = chunk.decode("utf-8", "replace")
            if text:
                try:
                    sink.write(text)
                    sink.flush()
                except Exception:
                    pass
        if decoder is not None:
            try:
                tail = decoder.decode(b"", final=True)
                if tail:
                    sink.write(tail)
                    sink.flush()
            except Exception:
                pass
    finally:
        try:
            close_sink()
        except Exception:
            pass


def run(timeout: float, label: str, cmd: list[str], log: str | None) -> int:
    kwargs: dict = {}
    if os.name != "nt":
        # POSIX: put the child in its own process group so killpg can reap the tree.
        kwargs["start_new_session"] = True

    if log is not None:
        f = open(log, "a", encoding="utf-8")
        sink: object = f
        close_sink = f.close
    else:
        sink = sys.stdout
        close_sink = lambda: None  # noqa: E731

    # Always PIPE: if the child tree inherits the caller's own stdout/stderr,
    # the pipe EOF (and therefore the bash tool's return) waits on every
    # grandchild (adb server daemon, detached launchers, ...) — the 2026-08-13
    # ~15h hang. PIPE keeps the caller's handles out of the child tree.
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        **kwargs,
    )
    reader = threading.Thread(target=_pump, args=(proc.stdout, sink, close_sink), daemon=True)
    reader.start()

    try:
        proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        marker = f"TIMEOUT after {timeout:g}s ({label})\n"
        try:
            sys.stderr.write(marker)
            sys.stderr.flush()
        except Exception:
            pass
        if log is not None:
            try:
                with open(log, "a", encoding="utf-8") as f:
                    f.write(marker)
            except Exception:
                pass
        kill_tree(proc)
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            pass
        return 124

    reader.join(timeout=5)
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=float, required=True, help="hard wall-clock timeout in seconds")
    parser.add_argument("--label", default="", help="human label used in the TIMEOUT marker")
    parser.add_argument("--log", default=None, help="append output to this file instead of stdout")
    args, rest = parser.parse_known_args()
    if not rest:
        parser.error("no command given (separate with -- before the command)")
    if rest[0] == "--":
        rest = rest[1:]
    return run(args.timeout, args.label, rest, args.log)


if __name__ == "__main__":
    sys.exit(main())
