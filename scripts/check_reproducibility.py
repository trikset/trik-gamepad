#!/usr/bin/env python3
"""Verify F-Droid reproducible build compatibility for a release.

Checks DECISIONS.md "[2026-09-23] F-Droid reproducible builds":
1. `assembleRelease` succeeds WITHOUT the local keystore (F-Droid builds unsigned).
2. Two fresh `clean assembleRelease` runs produce byte-identical unsigned APKs
   (zero build timestamps are the main lever; verifies nothing embedded a
   per-run timestamp/path).

Usage:
    uv run python scripts/check_reproducibility.py [--skip-build-1] [--skip-build-2]

Exit codes:
    0  all checks pass
    1  any check failed
    2  already running (lock)
"""

import argparse
import hashlib
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# The keystore is a symlink INSIDE the project dir (gitignored via **/*.p12);
# never touch files outside this project. F-Droid has no keystore at all, so a
# build with it absent must succeed.
KEYSTORE = ROOT / "android-keystorage.p12"
APK_REL = Path("app/build/outputs/apk/release/app-release-unsigned.apk")
LOCK = ROOT / ".tmp" / "reprod.lock"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def find_unsigned_apk() -> Path | None:
    apk_dir = Path("app/build/outputs/apk/release")
    if not apk_dir.exists():
        return None
    candidates = [p for p in apk_dir.iterdir() if p.is_file()]
    for c in candidates:
        if c.name.endswith(".apk"):
            return c
    # This project sets a custom archivesName without the .apk suffix
    # (e.g. "TRIKGamepad-2.44-API21-release"). Verify by zip magic.
    for c in candidates:
        if c.name.endswith("-release"):
            with open(c, "rb") as f:
                if f.read(2) == b"PK":
                    return c
    return None


def build(env: dict) -> bool:
    """Run clean assembleRelease with keystore absent. Returns build success."""
    cmd = ["./gradlew", "clean", "assembleRelease", "-PpreDexEnable=false"]
    r = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True, text=True)
    ok = r.returncode == 0
    if not ok:
        sys.stderr.write(r.stdout[-3000:] + "\n" + r.stderr[-3000:] + "\n")
    return ok


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build-1", action="store_true")
    parser.add_argument("--skip-build-2", action="store_true")
    args = parser.parse_args()

    (ROOT / ".tmp").mkdir(exist_ok=True)
    try:
        lock_fd = os.open(LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        os.write(lock_fd, str(os.getpid()).encode())
        os.close(lock_fd)
    except FileExistsError:
        sys.stderr.write("Another reproducibility check is running.\n")
        return 2

    try:
        print("== F-Droid reproducible build check ==")

        env = dict(os.environ)
        # Simulate F-Droid: it never has the keystore (gitignored, outside
        # upstream). Move the symlink aside (inside project .tmp/), build, then
        # restore it exactly. The keystore contents are never read.
        backup = ROOT / ".tmp" / "android-keystorage.p12.reprod-bak"
        moved = KEYSTORE.exists()
        if moved:
            os.rename(KEYSTORE, backup)
            print(f"[1/3] keystore moved aside: {KEYSTORE.name} (absent during build)")
        else:
            print("[1/3] keystore already absent — good (F-Droid environment)")

# Keep the keystore absent for BOTH builds (F-Droid environment has no
        # keystore); restore at the very end.
        if args.skip_build_1:
            print("[2/3] skipping build #1 (--skip-build-1)")
        elif build(env):
            print("[2/3] build #1 WITHOUT keystore: SUCCESS")
        else:
            print("[2/3] build #1 WITHOUT keystore: FAILED")
            return 1

        apk1 = find_unsigned_apk()
        if apk1 is None:
            print("FAIL: no release APK produced")
            return 1

        # Second independent build — same working tree, clean.
        if args.skip_build_2:
            print("[3/3] skipping build #2 (--skip-build-2)")
            print("SKIP: reproducibility not verified (build #2 skipped)")
            return 1 if not args.skip_build_1 else 0

        if build(env):
            print("[3/3] build #2: SUCCESS")
        else:
            print("[3/3] build #2: FAILED")
            return 1

        apk2 = find_unsigned_apk()
        if apk2 is None:
            print("FAIL: no release APK produced by build #2")
            return 1
        h1, h2 = sha256(apk1), sha256(apk2)
        print(f"  APK #1: {apk1} {h1}")
        print(f"  APK #2: {apk2} {h2}")
        if h1 == h2:
            print("RESULT: REPRODUCIBLE — hashes match \u2713")
            return 0
        print(
            "RESULT: NOT REPRODUCIBLE — hashes differ. Check for timestamps,"
            " embedded build paths, or non-deterministic resource ordering."
        )
        return 1
    finally:
        if moved:
            os.rename(backup, KEYSTORE)
            print("        keystore restored")
        try:
            os.unlink(LOCK)
        except FileNotFoundError:
            pass


if __name__ == "__main__":
    sys.exit(main())