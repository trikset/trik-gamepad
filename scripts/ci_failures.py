#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

r"""ci_failures.py - which job/step failed in a GitHub Actions run?

CI triage loop (AGENTS.md "After push"): `gh run list` shows the run id, this
script answers "which job and step went red?" without hand-parsing `gh run
view --log-failed` (and without the PowerShell `--jq "..."` quoting trap that
breaks inline jq expressions — AGENTS.md "Windows/PowerShell quirks").

Uses `gh` (must be authenticated) and reports each failed job + its failed
steps. Read-only; never triggers a re-run.

Usage (from repo root, uv venv):
  uv run python scripts/ci_failures.py <run-id> [--repo iakov/trik-gamepad]
Exit code 0 on success (even when the run failed — the report is the point);
1 when `gh` is unavailable or returns nothing.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys


def default_repo() -> str:
    """The origin remote (the fork branch lives there); gh defaults to upstream."""
    result = subprocess.run(
        ["git", "remote", "get-url", "origin"], capture_output=True, text=True
    )
    url = result.stdout.strip()
    if url.endswith(".git"):
        url = url[:-4]
    for prefix in ("https://github.com/", "git@github.com:"):
        if url.startswith(prefix):
            return url[len(prefix):]
    return "iakov/trik-gamepad"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="List the failed job(s)/step(s) of a GitHub Actions run."
    )
    parser.add_argument("run_id", help="the gh run id (from `gh run list`)")
    parser.add_argument("--repo", default=default_repo(),
                        help="owner/repo (default: origin remote)")
    args = parser.parse_args()

    if shutil.which("gh") is None:
        print("error: gh not found on PATH", file=sys.stderr)
        return 1
    result = subprocess.run(
        ["gh", "run", "view", args.run_id, "--repo", args.repo, "--json", "jobs"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"error: gh run view failed: {result.stderr.strip()}", file=sys.stderr)
        return 1
    try:
        jobs = json.loads(result.stdout).get("jobs", [])
    except json.JSONDecodeError as exc:
        print(f"error: could not parse gh output: {exc}", file=sys.stderr)
        return 1
    for job in jobs:
        failed = [s["name"] for s in job["steps"] if s["conclusion"] == "failure"]
        if failed or job.get("conclusion") == "failure":
            print(f"job={job['name']} conclusion={job.get('conclusion')} "
                  f"failed_steps={failed}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
