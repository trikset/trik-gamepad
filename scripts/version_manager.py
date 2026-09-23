#!/usr/bin/env python3
"""Version manager — single source of truth for the app version.

`version.properties` (repo root) holds VERSION_MAJOR / VERSION_MINOR. It feeds
`app/build.gradle` (versionCode/versionName) and this script keeps the
fastlane/F-Droid metadata (fastlane/metadata/com.trikset.gamepad2.yml) and
release tags in sync. Bump the version here, never by hand.

Subcommands:
  check             Verify all version consumers agree with version.properties.
  bump [minor]      Bump VERSION_MINOR (default: current + 1) and sync the
                    fastlane/F-Droid metadata. --dry-run previews without
                    writing.

Exit codes: 0 = ok, 1 = drift/mismatch (check) or failure, 2 = usage error.

Usage examples:
  uv run python scripts/version_manager.py --help
  uv run python scripts/version_manager.py check
  uv run python scripts/version_manager.py bump --dry-run
  uv run python scripts/version_manager.py bump 45
"""

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_PROPS = ROOT / "version.properties"
BUILD_GRADLE = ROOT / "app" / "build.gradle"
FASTLANE_YML = ROOT / "fastlane" / "metadata" / "com.trikset.gamepad2.yml"

MIN_SDK = 21  # mirrors app/build.gradle defaultConfig.minSdk
ABI_CODE = 0  # mirrors app/build.gradle `def abiCode = 0`


class Version:
    def __init__(self, major: int, minor: int):
        self.major = major
        self.minor = minor

    @property
    def name(self) -> str:
        return f"{self.major}.{self.minor}"

    @property
    def code(self) -> int:
        # Mirror of app/build.gradle versionCode formula:
        # minSdk * 10000 + abiCode * 1000 + major * 100 + minor
        return MIN_SDK * 10000 + ABI_CODE * 1000 + self.major * 100 + self.minor

    def __str__(self) -> str:
        return f"{self.name} (versionCode {self.code})"


def read_version() -> Version:
    props: dict[str, str] = {}
    for line in VERSION_PROPS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        props[k.strip()] = v.strip()
    try:
        return Version(int(props["VERSION_MAJOR"]), int(props["VERSION_MINOR"]))
    except (KeyError, ValueError) as e:
        sys.exit(f"version_manager: bad {VERSION_PROPS}: {e}")


def parse_fastlane() -> dict[str, str | None]:
    """Extract the version-relevant fields from the fastlane/F-Droid yml."""
    text = FASTLANE_YML.read_text(encoding="utf-8")
    fields = {
        "Builds[0].versionName": re.search(r"versionName:\s*'([^']+)'", text),
        "Builds[0].versionCode": re.search(r"versionCode:\s*(\d+)", text),
        "Builds[0].commit": re.search(r"commit:\s*(v\S+)", text),
        "CurrentVersion": re.search(r"CurrentVersion:\s*'([^']+)'", text),
        "CurrentVersionCode": re.search(r"CurrentVersionCode:\s*(\d+)", text),
    }
    return {k: (m.group(1) if m else None) for k, m in fields.items()}


def write_fastlane(version: Version) -> None:
    """Rewrite version-dependent lines of the yml in place (line-based, safe)."""
    text = FASTLANE_YML.read_text(encoding="utf-8")
    replacements = [
        (r"versionName:\s*'[^']+'", f"versionName: '{version.name}'"),
        (r"versionCode:\s*\d+", f"versionCode: {version.code}"),
        (r"commit:\s*v\S+", f"commit: v{version.name}"),
        (r"CurrentVersion:\s*'[^']+'", f"CurrentVersion: '{version.name}'"),
        (r"CurrentVersionCode:\s*\d+", f"CurrentVersionCode: {version.code}"),
    ]
    for pattern, replacement in replacements:
        new_text, n = re.subn(pattern, replacement, text, count=1)
        if n != 1:
            sys.exit(f"version_manager: cannot update {FASTLANE_YML} ({pattern})")
        text = new_text
    FASTLANE_YML.write_text(text, encoding="utf-8")


def set_minor(minor: int) -> None:
    """Write VERSION_MINOR into version.properties, preserving the header."""
    lines = VERSION_PROPS.read_text(encoding="utf-8").splitlines(keepends=True)
    for i, line in enumerate(lines):
        if line.startswith("VERSION_MINOR="):
            lines[i] = f"VERSION_MINOR={minor}\n"
            break
    else:
        sys.exit(f"version_manager: VERSION_MINOR missing in {VERSION_PROPS}")
    VERSION_PROPS.write_text("".join(lines), encoding="utf-8")


def cmd_check(_args) -> int:
    version = read_version()
    print(f"version.properties: {version}")

    issues: list[str] = []
    expected = {
        "Builds[0].versionName": version.name,
        "Builds[0].versionCode": str(version.code),
        "Builds[0].commit": f"v{version.name}",
        "CurrentVersion": version.name,
        "CurrentVersionCode": str(version.code),
    }
    actual = parse_fastlane()
    for field, want in expected.items():
        got = actual[field]
        status = "OK" if got == want else f"MISMATCH (yml={got}, want={want})"
        print(f"  fastlane {field}: {status}")
        if got != want:
            issues.append(f"fastlane {field}: yml={got!r}, expected {want!r}")

    # Also verify the built APK metadata if a release build exists.
    meta = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "output-metadata.json"
    if meta.exists():
        import json

        element = json.loads(meta.read_text(encoding="utf-8"))["elements"][0]
        built_vc, built_vn = element["versionCode"], element["versionName"]
        built_name = built_vn.removesuffix(f"-API{MIN_SDK}")
        ok = built_vc == version.code and built_name == version.name
        print(f"  built APK: versionCode={built_vc} versionName={built_vn} "
              f"{'OK' if ok else 'MISMATCH'}")
        if not ok:
            issues.append(f"built APK versionCode/versionName != {version.name}")

    # Tag advisory: vX.Y exists? (During development it may legitimately not.)
    import subprocess

    tags = subprocess.run(
        ["git", "tag", "--list", f"v{version.name}"],
        cwd=ROOT, capture_output=True, text=True,
    ).stdout.strip()
    print(f"  git tag v{version.name}: "
          f"{'exists' if tags else 'NOT TAGGED yet (normal during development)'}")

    if issues:
        print("\nFAIL: version drift detected:")
        for issue in issues:
            print(f"  - {issue}")
        print("Run `uv run python scripts/version_manager.py bump --dry-run` "
              "to preview a sync, or fix manually.")
        return 1
    print("\nOK: all version consumers agree.")
    return 0


def cmd_bump(args) -> int:
    current = read_version()
    new_minor = args.minor if args.minor is not None else current.minor + 1
    if not 0 <= new_minor <= 99:
        sys.exit("version_manager: minor must be 0..99 (two digits per major)")
    new = Version(current.major, new_minor)
    print(f"current: {current}")
    print(f"new:     {new}")

    if not args.dry_run:
        set_minor(new_minor)
        write_fastlane(new)
        print(f"\nWrote {VERSION_PROPS.name} and {FASTLANE_YML.relative_to(ROOT)}")
    else:
        print("\n[--dry-run] no files changed.")

    print("\nNext steps:")
    print(f"  1. uv run python scripts/version_manager.py check")
    print(f"  2. ./gradlew test  (or full gate: uv run python scripts/gate.py)")
    print(f"  3. uv run python scripts/check_reproducibility.py  (F-Droid gate)")
    print(f"  4. git tag -s v{new.name} HEAD   # after the release commit")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="version_manager",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_check = sub.add_parser("check", help="verify all version consumers agree")
    p_check.set_defaults(func=cmd_check)

    p_bump = sub.add_parser("bump", help="bump VERSION_MINOR and sync metadata")
    p_bump.add_argument(
        "minor", type=int, nargs="?", default=None,
        help="new minor (default: current + 1), e.g. 45",
    )
    p_bump.add_argument(
        "--dry-run", action="store_true",
        help="show what would change without writing any file",
    )
    p_bump.set_defaults(func=cmd_bump)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())