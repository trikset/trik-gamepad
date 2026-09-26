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
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_PROPS = ROOT / "version.properties"
BUILD_GRADLE = ROOT / "app" / "build.gradle"
FASTLANE_YML = ROOT / "fastlane" / "metadata" / "com.trikset.gamepad2.yml"
FDROID_YML = ROOT / "fdroiddata" / "com.trikset.gamepad2.yml"
CHANGELOG_DIR = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"

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
        version = Version(int(props["VERSION_MAJOR"]), int(props["VERSION_MINOR"]))
    except (KeyError, ValueError) as e:
        sys.exit(f"version_manager: bad {VERSION_PROPS}: {e}")
    # Verify stored VERSION_CODE matches the computed one (F-Droid reads it
    # via UpdateCheckData). Warn — don't hard-fail — for the transient state
    # where VERSION_CODE hasn't been added yet.
    if "VERSION_CODE" in props:
        stored = int(props["VERSION_CODE"])
        if stored != version.code:
            print(
                f"WARNING: {VERSION_PROPS} VERSION_CODE={stored} != computed "
                f"versionCode={version.code} — run `bump` to sync."
            )
    else:
        print(f"WARNING: VERSION_CODE missing in {VERSION_PROPS}")
    return version


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


def _apply_version(yml_path: Path, version: Version) -> None:
    """Rewrite version-dependent lines of a YAML file in place."""
    text = yml_path.read_text(encoding="utf-8")
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
            sys.exit(f"version_manager: cannot update {yml_path} ({pattern})")
        text = new_text
    yml_path.write_text(text, encoding="utf-8")


def write_fastlane(version: Version) -> None:
    _apply_version(FASTLANE_YML, version)
    if FDROID_YML.exists():
        _apply_version(FDROID_YML, version)


def write_version_props(version: Version) -> None:
    """Rewrite VERSION_MINOR and VERSION_CODE in version.properties."""
    text = VERSION_PROPS.read_text(encoding="utf-8")
    text, n1 = re.subn(r"^VERSION_MINOR=\d+", f"VERSION_MINOR={version.minor}", text, count=1, flags=re.MULTILINE)
    if n1 != 1:
        sys.exit(f"version_manager: cannot find VERSION_MINOR in {VERSION_PROPS}")
    has_code = re.search(r"^VERSION_CODE=", text, re.MULTILINE)
    if has_code:
        text, n2 = re.subn(r"^VERSION_CODE=\d+", f"VERSION_CODE={version.code}", text, count=1, flags=re.MULTILINE)
        if n2 != 1:
            sys.exit(f"version_manager: cannot update VERSION_CODE in {VERSION_PROPS}")
    else:
        text += f"VERSION_CODE={version.code}\n"
    VERSION_PROPS.write_text(text, encoding="utf-8")


def check_fdroiddata(version: Version) -> list[str]:
    """Validate fdroiddata/build metadata. Returns list of issues (empty = ok)."""
    issues: list[str] = []
    if not FDROID_YML.exists():
        issues.append(f"fdroiddata YAML not found at {FDROID_YML}")
        return issues

    text = FDROID_YML.read_text(encoding="utf-8")

    # 1. Valid YAML
    try:
        import yaml as _y  # noqa: F401
        import yaml
        yaml.safe_load(text)
    except Exception as e:
        issues.append(f"fdroiddata YAML parse error: {e}")

    # 2. AllowedAPKSigningKeys present
    if "AllowedAPKSigningKeys:" not in text:
        issues.append("fdroiddata: missing AllowedAPKSigningKeys")

    # 3. Binaries present
    if "Binaries:" not in text:
        issues.append("fdroiddata: missing Binaries")

    # 4. commit: uses full SHA (not tag/branch) — warn for ref copy
    m = re.search(r"commit:\s*(\S+)", text)
    if m:
        sha = m.group(1)
        if re.match(r"^v?\d+\.\d+$", sha) or "/" in sha:
            print("  WARNING: fdroiddata commit uses tag/branch '{0}' — "
                  "replace with full SHA before submitting to fdroiddata".format(sha))

    # 5. UpdateCheckData regex: no ^ anchor (fdroidserver uses re.MULTILINE=False)
    m = re.search(r"UpdateCheckData:\s*\S+\|(\^?)([^|]+)", text)
    if m and m.group(1) == "^":
        issues.append("fdroiddata: UpdateCheckData regex starts with ^ — fdroidserver "
                      "compiles without re.MULTILINE, so ^ matches only the file start")

    # 6. No Description / Summary in fdroiddata (they go in upstream fastlane)
    for field in ("Description:", "Summary:"):
        if re.search(rf"^{field}", text, re.MULTILINE):
            issues.append(f"fdroiddata: {field} should be removed — "
                          "lives in upstream repo's fastlane metadata, not in fdroiddata")

    # 7. Changelog for current versionCode exists
    changelog = CHANGELOG_DIR / f"{version.code}.txt"
    if not changelog.exists():
        issues.append(f"missing changelog: {changelog.relative_to(ROOT)} "
                      "(create it before release)")

    return issues


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
        print(f"  built APK: versionCode={built_vc} versionName={built_vn} {'OK' if ok else 'MISMATCH'}")
        if not ok:
            issues.append(f"built APK versionCode/versionName != {version.name}")

    # Tag advisory: vX.Y exists? (During development it may legitimately not.)
    tags = subprocess.run(
        ["git", "tag", "--list", f"v{version.name}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    ).stdout.strip()
    print(f"  git tag v{version.name}: {'exists' if tags else 'NOT TAGGED yet (normal during development)'}")

    # F-Droid metadata validation
    print()
    fd_issues = check_fdroiddata(version)
    if fd_issues:
        print("F-Droid metadata issues:")
        for i in fd_issues:
            print(f"  - {i}")
            issues.append(i)
    else:
        print("fdroiddata metadata: OK")

    if issues:
        print("\nFAIL: version drift detected:")
        for issue in issues:
            print(f"  - {issue}")
        print("Run `uv run python scripts/version_manager.py bump --dry-run` to preview a sync, or fix manually.")
        return 1
    print("\nOK: all consumers agree, fdroiddata metadata valid.")
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
        write_version_props(new)
        write_fastlane(new)
        print(f"\nWrote {VERSION_PROPS.name} and {FASTLANE_YML.relative_to(ROOT)}")
    else:
        print("\n[--dry-run] no files changed.")

    print("\nNext steps:")
    print("  1. uv run python scripts/version_manager.py check")
    print("  2. ./gradlew test  (or full gate: uv run python scripts/gate.py)")
    print("  3. uv run python scripts/check_reproducibility.py  (F-Droid gate)")
    print(f"  4. git tag -s v{new.name} upstream/master")
    print(f"  5. git push upstream v{new.name}")
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
        "minor",
        type=int,
        nargs="?",
        default=None,
        help="new minor (default: current + 1), e.g. 45",
    )
    p_bump.add_argument(
        "--dry-run",
        action="store_true",
        help="show what would change without writing any file",
    )
    p_bump.set_defaults(func=cmd_bump)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
