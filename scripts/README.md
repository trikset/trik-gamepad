# scripts/ — trik-gamepad tooling

<!-- encoding: utf-8 -->

This folder bridges the gap between the repo's build/test tooling and what an
agent, CI, or a developer actually needs to do. Everything here is a small,
focused Python script with a doc header, UTF-8 source, and a clean exit code.
Run them with `uv run python scripts/<name>.py ...` (repo-local `.venv`) —
the same command works on Windows and POSIX, and CI invokes them the same way.

## Rules for scripts in this folder

- **Agent-centric + reusable.** A script exists because an agent would
  otherwise re-type the logic (or ad-hoc `.tmp/`-dangle it). If a similar task
  arises, **reuse and improve the script** — extract hardcoded values to
  parameters, add `--help`, improve error handling — never write a one-off
  clone. But **no goldplating**: implement what the task needs, nothing more.
- **Doc header + UTF-8.** Every script starts with a `#!/usr/bin/env python3`
  - a docstring that says what it does, when to use it, and how (usage
    examples). Sources are UTF-8 (no BOM; `strip_bom` was a recovery, never a
    routine step).
- **Gate + CI.** `scripts/gate.py` is the canonical local quality gate; the
  steps it runs are mirrored in `.github/workflows/ci.yml` (keep both in
  sync). Pre-commit hooks live in `.pre-commit-config.yaml` and call scripts
  in this folder. CI checks are the strictest run — if a script must behave
  identically locally and in CI, test it with the same command.
- **Always improve in the retrospective.** Every campaign retrospective
  reviews the scripts: did a `.tmp/` ad-hoc script deserve promotion? Did an
  existing script need a parameter/`--help`? Record the improvement (or the
  deliberate decision not to) before the PR.

## Script inventory

| Script | What it does | Run when |
|--------|--------------|----------|
| `gate.py` | The canonical quality gate (spotless → test → lint → detekt → spotbugs → jacoco → spotlessCheck → jscpd → lizard trend → translations → device identifiers) | before every push; CI mirrors it |
| `run_bounded.py` | Run any command under a hard wall-clock timeout with a **process-tree** kill (children hold the output pipe otherwise) | wrapping long-lived native commands (adb, gradle, emulator launchers) |
| `_gradle.py` | Shared cross-platform `gradlew.bat`/`./gradlew` resolution + bounded invocation | imported by gate.py, not run directly |
| `spotless_apply.py` | Run Gradle `spotlessApply` from the pre-commit hook (cross-platform wrapper) | pre-commit hook only |
| `check_translations.py` | Enforce key/format parity across all 5 locales (`--sync`, in gate/CI); `--back-translate` for one-off semantic review | in gate.py + CI; ad-hoc semantic check |
| `check_xml_comments.py` | Reject `--` inside XML comments (aapt2 hard-fails) | pre-commit hook |
| `check_device_identifiers.py` | Reject device serials / model codes / IMEIs in committed content | pre-commit hook + gate.py + CI |
| `export_readme_screenshot.py` | Export the README hero screenshot from the HudThemeTest render (`--check` verifies freshness) | before release / HUD changes |
| `build_symbol_font.py` | Regenerate the bundled HUD symbol font (DejaVuSansMono Nerd Font subset) | when the glyph set changes |
| `glyph_metrics.py` | Rasterize the bundled font (Pillow) and emit per-glyph `visualHeightEm`/`medianBiasEm` metrics (Kotlin `GlyphMetrics` or `--print`) | when the glyph set changes (wired into `build_symbol_font.py`) |
| `measure_glyph_row.py` | Measure a row of glyph buttons' VISUAL ink alignment (weighted median + 90% band per button) from a uiautomator dump + screenshot | on-device verification of `GlyphRow` / video-source preset chips |
| `png_census.py` | **Pixel-census / pixel-diff** of UI screenshots (pure Python PNG decode; no PIL) — verify "the look changed" or "video is live" by pixels, not eyeballing | any UI screenshot proof (AGENTS.md pixel-census guardrail) |
| `jacoco_report.py` | Summarize the JaCoCo report — global counters + per-class missed branches (`totals`) and exact uncovered lines (`lines`) | when the coverage gate fails or a feature adds app classes |
| `ci_failures.py` | Which job/step failed in a `gh run`? (avoids the PowerShell `--jq` quoting trap) | the "check CI" loop after every push |
| `ui_dump_parse.py` | Print a uiautomator dump as readable rows (id/class/desc/text/bounds) with a `--filter` | reading the view tree + deriving tap bounds-centre |
| `dummy_gamepad.py` | Interactive/batch protocol-tracking gamepad client (TCP/UDP): logs every outbound command + inbound line, `wait <ms>`, `--batch "c1;c2"` for scripting | probing DummyRobotServer / a robot's control port |
| `strip_bom.py` | Remove a UTF-8 BOM from files (PS rewrites drop BOMs — hit C24) | after a PowerShell `Set-Content`/`Out-File` rewrite touched sources |
| `refresh_kotlin_ls.py` | Check the opencode kotlin-ls JetBrains EAP build age (`majorVersionReleaseDate` in `product-info.json`) and exit 1 when it's nearing expiry; `--refresh` downloads + installs the latest from the VS Code Marketplace | session init (AGENTS.md kotlin-ls expiry guard); when the LSP silently fails |
| `pr_gate.py` | Pre-upstream-PR APK quality gate: 7 checks via apkanalyzer (density completeness, dex refs, permissions, size, large blobs) | before creating a pull request to upstream trikset/trik-gamepad; after `assembleReleaseDebug` |

## New-script workflow

1. Write it in `.tmp/` first (iterating fast, session-local).
1. When it proves reusable (used twice, or a guardrail needs it), promote it:
   add the doc header, parametrize hardcoded paths, add `--help`/error
   handling, run `uv run python scripts/<name>.py --help` to sanity-check.
1. Add it to the inventory table above and to AGENTS.md if it guards a rule.
1. Gate (if it touches `gate.py`/CI, re-run `gate.py`) and commit as a
   `chore:` commit.
