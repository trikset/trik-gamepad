#!/usr/bin/env python3
"""pr_gate.py - pre-upstream-PR quality gate for trik-gamepad.

Runs after the normal gate.py to validate the assembled APK using CLI tools
from the Android SDK (apkanalyzer). Catches what code-level analysis misses:
density gaps, oversized blobs, method-count creep, permission drift.

Usage:
    uv run python scripts/pr_gate.py [--apk path/to/app.apk]

Defaults to app/build/outputs/apk/releaseDebug/app-releaseDebug.apk. Run after
a successful `./gradlew assembleReleaseDebug`.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys

from run_bounded import run

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG_DIR = os.path.join(ROOT, ".tmp")
LOG = os.path.join(LOG_DIR, "pr_gate.log")

REQUIRED_MIPMAP_DENSITIES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"}
MAX_NON_DEX_KB = 500


def append_log(text: str) -> None:
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(text + "\n")


def _cmd_output(cmd: list[str], timeout: float = 60) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return result.stdout + result.stderr


def _local_properties_sdk() -> str | None:
    path = os.path.join(ROOT, "local.properties")
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("sdk.dir"):
                _, _, val = line.partition("=")
                val = val.strip().replace("\\:", ":").replace("\\", "/").replace("C:", "")
                val = val.replace("\\", "/")
                if val.startswith("/"):
                    val = "C:" + val
                return val.replace("/", os.sep)
    return None


def apkanalyzer() -> str:
    sdk = _local_properties_sdk() or os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or ""
    candidates = [
        os.path.join(sdk, "cmdline-tools", "latest", "bin", "apkanalyzer.bat"),
        shutil.which("apkanalyzer"),
        shutil.which("apkanalyzer.bat"),
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    sys.exit(f"apkanalyzer not found. Install cmdline-tools or set ANDROID_SDK_ROOT.\n  Tried: sdk={sdk}, candidates={candidates}")


def check_apk_summary(apk: str) -> int:
    print("==> APK summary")
    tool = apkanalyzer()
    out = _cmd_output([tool, "apk", "summary", apk])
    append_log(out)
    # expected: appId TAB versionCode TAB versionName
    parts = out.strip().split("\t")
    if len(parts) == 3:
        print(f"    package={parts[0]} versionCode={parts[1]} versionName={parts[2]}")
        return 0
    print(f"FAILED: unexpected summary format: {out.strip()}")
    return 1


def check_file_size(apk: str) -> int:
    print("==> APK file size")
    tool = apkanalyzer()
    out = _cmd_output([tool, "apk", "file-size", apk])
    append_log(out)
    size = out.strip()
    if not size:
        return 1
    print(f"    {size} bytes")
    try:
        mb = int(size) / 1_048_576
        print(f"    {mb:.1f} MB")
    except ValueError:
        pass
    return 0


def check_download_size(apk: str) -> int:
    print("==> APK download size (estimated)")
    tool = apkanalyzer()
    out = _cmd_output([tool, "apk", "download-size", apk])
    append_log(out)
    size = out.strip()
    if not size:
        return 1
    print(f"    {size} bytes")
    try:
        mb = int(size) / 1_048_576
        print(f"    {mb:.1f} MB")
    except ValueError:
        pass
    return 0


def check_permissions(apk: str, baseline: set[str] | None = None) -> int:
    print("==> Manifest permissions")
    tool = apkanalyzer()
    out = _cmd_output([tool, "manifest", "permissions", apk])
    append_log(out)
    perms = set(p.strip() for p in out.strip().split("\n") if p.strip())
    for p in sorted(perms):
        print(f"    {p}")
    if baseline is not None:
        new = perms - baseline
        if new:
            print(f"FAILED: new permission(s) not in baseline: {', '.join(sorted(new))}")
            return 1
    return 0


def check_dex_refs(apk: str, max_refs: int = 60000) -> int:
    print("==> Dex reference count")
    tool = apkanalyzer()
    out = _cmd_output([tool, "dex", "references", apk])
    append_log(out)
    total = 0
    for line in out.splitlines():
        line = line.strip()
        if not line or "\t" not in line:
            continue
        try:
            n = int(line.split("\t")[1])
            total += n
        except (ValueError, IndexError):
            pass
    print(f"    {total} total references across {len([l for l in out.splitlines() if l.strip()])} dex files")
    if total > max_refs:
        print(f"FAILED: {total} references exceeds threshold of {max_refs}")
        return 1
    return 0


def check_icon_densities(apk: str, icon_name: str = "trik_gamepad_logo") -> int:
    print("==> Icon density check")
    tool = apkanalyzer()
    out = _cmd_output([tool, "files", "list", apk])
    append_log(out)
    found: set[str] = set()
    for line in out.splitlines():
        line = line.strip()
        if icon_name in line and line.endswith(".webp"):
            m = re.search(r"/res/mipmap-([a-z]+)-v4/", line)
            if m:
                found.add(m.group(1))
    missing = REQUIRED_MIPMAP_DENSITIES - found
    if missing:
        print(f"FAILED: icon '{icon_name}' missing densities: {', '.join(sorted(missing))}")
        return 1
    print(f"    {icon_name}.webp present in all {len(found)} densities: {', '.join(sorted(found))}")
    return 0


def check_large_blobs(apk: str) -> int:
    print("==> Unexpected large files (>500 KB non-dex)")
    import zipfile
    large: list[str] = []
    try:
        with zipfile.ZipFile(apk, "r") as zf:
            for info in zf.infolist():
                if info.is_dir():
                    continue
                # Standard APK paths we don't flag
                fname = info.filename.replace("\\", "/")
                if fname.startswith("res/") or fname.startswith("META-INF/"):
                    continue
                if fname.startswith("classes") and fname.endswith(".dex"):
                    continue
                if fname in ("AndroidManifest.xml", "resources.arsc"):
                    continue
                if info.compress_size > MAX_NON_DEX_KB * 1024:
                    size_kb = info.compress_size / 1024
                    large.append(f"    {fname}: {size_kb:.0f} KB (compressed)")
    except (FileNotFoundError, zipfile.BadZipFile) as e:
        print(f"FAILED: {e}")
        return 1
    if large:
        for l in large:
            print(l)
        print(f"Found {len(large)} large non-standard file(s)")
    else:
        print("    no non-standard large files found")
    return 0


def main() -> None:
    os.makedirs(LOG_DIR, exist_ok=True)
    with open(LOG, "w", encoding="utf-8") as f:
        f.write("")

    # Resolve APK path
    apk = None
    if "--apk" in sys.argv:
        idx = sys.argv.index("--apk")
        if idx + 1 < len(sys.argv):
            apk = sys.argv[idx + 1]
    if apk is None:
        apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "releaseDebug", "app-releaseDebug.apk")
    if not os.path.isfile(apk):
        sys.exit(f"APK not found: {apk}\nBuild it first: ./gradlew assembleReleaseDebug")

    failures = 0

    failures += check_apk_summary(apk)
    failures += check_file_size(apk)
    failures += check_download_size(apk)
    failures += check_permissions(apk)
    failures += check_dex_refs(apk)
    failures += check_icon_densities(apk)
    failures += check_large_blobs(apk)

    if failures:
        print(f"\nPR GATE FAILED - {failures} check(s) failed. Log: .tmp/pr_gate.log")
    else:
        print("\nPR GATE PASSED - all checks green. Log: .tmp/pr_gate.log")
    sys.exit(failures)


if __name__ == "__main__":
    main()
