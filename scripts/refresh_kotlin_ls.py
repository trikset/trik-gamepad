#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""refresh_kotlin_ls.py - check/replace the opencode kotlin-ls JetBrains build.

opencode's built-in `kotlin-ls` uses a JetBrains EAP `intellij-server` build
with a built-in ~6-week expiry ("This build of intellij-server has expired" -
hit 2026-09-11 when the 2026-07-26 build went stale mid-session). The build
date is machine-readable in `product-info.json` (`majorVersionReleaseDate`),
so a stale build is detectable without any network call.

Modes:

  (default / --check)  Print the installed build + release date, and exit
                       1 when it is missing or older than WARN_DAYS (the
                       safety margin before EAP expiry). Session-init hooks
                       run this; exit 0 means "trust the LSP".
  --refresh            Download the latest JetBrains.kotlin-server VSIX from
                       the VS Code Marketplace, extract `extension/server/`
                       and atomically replace the install dir. Idempotent -
                       safe to re-run.

Usage:
  uv run python scripts/refresh_kotlin_ls.py            # check (session init)
  uv run python scripts/refresh_kotlin_ls.py --refresh  # install fresh build
  uv run python scripts/refresh_kotlin_ls.py --dist ~/.cache/opencode/bin/kotlin-ls  # override install dir

Install dir defaults to `~/.cache/opencode/bin/kotlin-ls` (opencode's
`Global.Path.bin`); override with KOTLIN_LS_DIR or --dist.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import io
import json
import os
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

# EAP expiry is ~42 days from release; 35 leaves a week to notice and refresh.
WARN_DAYS = 35

MARKETPLACE_API = "_apis/public/gallery/extensionquery"
MARKETPLACE_HOST = "https://marketplace.visualstudio.com"
EXTENSION_ID = "JetBrains.kotlin-server"


def dist_dir(cli: str | None) -> Path:
    root = Path(cli) if cli else Path(os.environ.get("KOTLIN_LS_DIR") or Path.home() / ".cache" / "opencode" / "bin" / "kotlin-ls")
    return root


def installed_build(dist: Path) -> tuple[str | None, int | None, int | None]:
    """Return (version, age_days, warn_outcome) for the installed build."""
    info = dist / "product-info.json"
    if not info.is_file():
        return None, None, None
    try:
        data = json.loads(info.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None, None, None
    build = data.get("buildNumber", "?")
    rel = data.get("majorVersionReleaseDate")
    if not rel or len(str(rel)) != 8:
        return build, None, None
    try:
        release = _dt.datetime.strptime(str(rel), "%Y%m%d").date()
    except ValueError:
        return build, None, None
    age = (_dt.date.today() - release).days
    return build, age, age > WARN_DAYS


def latest_vsix_url() -> str:
    """Resolve the latest cross-platform VSIX package URL for the extension."""
    req = urllib.request.Request(
        f"{MARKETPLACE_HOST}/{MARKETPLACE_API}",
        data=json.dumps(
            {
                "filters": [
                    {"criteria": [{"filterType": 7, "value": EXTENSION_ID}], "pageNumber": 1, "pageSize": 1}
                ],
                "flags": 103,
            }
        ).encode(),
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json;api-version=3.0-preview.1",
            "User-Agent": "refresh_kotlin_ls",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as resp:  # noqa: S310 (https URL)
        data = json.loads(resp.read())
    for ext in data.get("results", [{}])[0].get("extensions", []):
        for ver in ext.get("versions", []):
            for f in ver.get("files", []):
                if f.get("assetType", "").startswith("Microsoft.VisualStudio.Services.VSIXPackage"):
                    return f["source"]
    raise RuntimeError("No VSIXPackage asset found for " + EXTENSION_ID)


def refresh(dist: Path) -> None:
    url = latest_vsix_url()
    print(f"Downloading {url}")
    buf = io.BytesIO()
    with urllib.request.urlopen(url, timeout=120) as resp:  # noqa: S310 (https URL)
        shutil.copyfileobj(resp, buf)
    buf.seek(0)

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)
        with zipfile.ZipFile(buf) as zf:
            zf.extractall(tmpdir)
        server = tmpdir / "extension" / "server"
        if not server.is_dir():
            raise RuntimeError("VSIX has no extension/server/ directory")

        staging = dist.parent / (dist.name + ".tmp")
        if staging.exists():
            shutil.rmtree(staging)
        shutil.copytree(server, staging)
        backup = dist.parent / (dist.name + ".old")
        if backup.exists():
            shutil.rmtree(backup)
        if dist.exists():
            dist.rename(backup)
        staging.rename(dist)
        shutil.rmtree(backup, ignore_errors=True)

    build, age, _ = installed_build(dist)
    print(f"Installed {build} (released {age} days ago) at {dist}")


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--refresh", action="store_true", help="download and install the latest build")
    ap.add_argument("--dist", default=None, help="install directory override")
    ap.add_argument("--warn-days", type=int, default=WARN_DAYS, help="age threshold in days (default 35)")
    args = ap.parse_args(argv)

    dist = dist_dir(args.dist)
    if args.refresh:
        refresh(dist)
        return 0

    # Check mode (default) - session-init guardrail.
    build, age, stale = installed_build(dist)
    if build is None:
        print(f"kotlin-ls not found at {dist} - install: uv run python scripts/refresh_kotlin_ls.py --refresh")
        return 1
    if age is None:
        print(f"kotlin-ls {build}: no release date in product-info.json - cannot verify, refresh to be safe")
        return 1
    if stale:
        print(
            f"kotlin-ls {build} is {age} days old (>{args.warn_days}) - EAP expiry risk. "
            "Refresh: uv run python scripts/refresh_kotlin_ls.py --refresh"
        )
        return 1
    print(f"kotlin-ls {build} released {age} days ago - OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
