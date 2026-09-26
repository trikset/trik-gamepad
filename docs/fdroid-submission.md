# F-Droid submission reference

Capture of all lessons from the v2.45 submission to `fdroid/fdroiddata` MR !49992.

## Rules (from maintainer review)

| # | Rule | Why |
|---|------|-----|
| 1 | **`fdroid rewritemeta`** before every submission | Canonicalizes field ordering; CI will fail without it |
| 2 | **No `Description:` / `Summary:` in fdroiddata YAML** | They live in upstream repo's fastlane metadata; F-Droid auto-pulls from checkout at tag |
| 3 | **No `WebSite:` when same as `SourceCode:`** | Redundant; reviewer will ask to remove it |
| 4 | **`commit:` must be full SHA hash**, not tag or branch name | Tag/branch refs can move; SHA is immutable |
| 5 | **`UpdateCheckData` regex — no `^` anchor** | `fdroidserver` compiles without `re.MULTILINE`; `^` would match only file start |
| 6 | **`AllowedAPKSigningKeys:` lowercase hex, no colons** | Format: `keytool -list -v` output → `tr '[:upper:]' '[:lower:]'` → `tr -d ':'` |
| 7 | **`Binaries:` URL with `%v` / `%c` placeholders** | Required for reproducible-build verification |
| 8 | **Only latest version in `Builds:`** | No disabled/old versions; replace on update |
| 9 | **Release tags + `AutoUpdateMode: Version`** | Bot auto-detects new tags and opens update MRs |
| 10 | **`Categories:` from fdroiddata's `config/categories.yml`** | Only use existing categories from the fdroiddata repo |

## Pre-release gates (automated in `version_manager.py check`)

Run `uv run python scripts/version_manager.py check` before every release.
It now validates:

1. **Version drift** — version.properties, fastlane YML, fdroiddata YML, and built APK all agree
2. **`AllowedAPKSigningKeys` present** in fdroiddata YAML
3. **`Binaries` present** in fdroiddata YAML
4. **`commit:` is a full SHA** (40 hex chars), not a tag or branch name
5. **`UpdateCheckData` regex** has no `^` anchor (would break without MULTILINE)
6. **No `Description:` / `Summary:`** in fdroiddata YAML (must live in upstream fastlane only)
7. **Changelog exists** at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`

## Release flow (hardened)

```
 1. version_manager.py bump <minor>   # bumps version.properties + syncs YMLs
 2. version_manager.py check           # validates EVERYTHING (covers all 7 gates)
 3. git add -A && git commit -S        # commit the bump + any fixes
 4. ./gradlew test                     # or uv run gate.py (full gate)
 5. check_reproducibility.py           # F-Droid reproducibility gate
 6. git tag -s v<maj>.<min> upstream/master   # signed tag
 7. git push upstream v<maj>.<min>     # CI builds + publishes
```

## CI hardening suggestions (for GitHub Actions)

Add a **pre-tag validation job** to `.github/workflows/ci.yml` that runs on tag push:

```yaml
  validate-fdroiddata:
    name: Validate F-Droid metadata
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: uv run python scripts/version_manager.py check
```

This catches all 7 gates before the release is published. If it fails, the tag can be deleted and the issue fixed before re-tagging.

## Common CI failures

| Symptom | Root cause | Fix |
|---------|------------|-----|
| `rewritemeta` fails | Field ordering wrong | Run `fdroid rewritemeta <appid>` |
| `checkupdate` → "Couldn't find any version information" | No `UpdateCheckData` or `^` anchor in regex | Add `UpdateCheckData`; remove `^` |
| `checkupdate` → "file not found" | Tag doesn't contain the referenced file | Use a tag/commit that has it |
| `commit:` uses tag instead of SHA | Lazy copy-paste | `git rev-parse <tag>` → paste full hash |
| `Binaries` / `AllowedAPKSigningKeys` missing | Forgot to add | Add both for reproducible build |
| `WebSite` same as `SourceCode` | Redundant | Remove `WebSite:` |
| `Description:` present in fdroiddata | Duplicate | Remove; goes in upstream fastlane |
| Missing changelog | Version bump didn't create it | Create `changelogs/<versionCode>.txt` before tag |
