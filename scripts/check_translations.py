#!/usr/bin/env python3
# Copyright: Iakov Kirilenko
# SPDX-License-Identifier: Apache-2.0

"""check_translations.py - translation sync guard + one-off back-translation review.

TWO modes (AGENTS.md "keep translations in sync"):

  --sync            Deterministic, NO network. Verifies every values-*/strings.xml
                    has exactly the <string> keys of values/strings.xml and that
                    each key's format specifiers (%d, %1$s, ...) match. Wired into
                    scripts/gate.py (Android lint has no translation-parity check,
                    so this script is the guard).

  --back-translate  One-off semantic review aid (NOT a recurring gate; literals
                    change rarely). Back-translates each non-English string to
                    English via the MyMemory free API and reports strings whose
                    difflib similarity to the source is below --min-ratio for a
                    human to review. Results are cached in .tmp/ so re-runs are
                    cheap. Network-dependent; never part of the canonical gate.

Usage (from repo root):  uv run python scripts/check_translations.py --sync
                         uv run python scripts/check_translations.py --back-translate
"""

from __future__ import annotations

import argparse
import difflib
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res")
CACHE = os.path.join(ROOT, ".tmp", "backtranslate_cache.json")

FORMAT_RE = re.compile(r"%\d*\$?[a-zA-Z]")


def string_keys(path: str) -> dict[str, str]:
    """name -> value for every <string> element (string-arrays are skipped: their items are either
    enum values or @string references already covered by the referenced keys)."""
    if not os.path.isfile(path):
        return {}
    tree = ET.parse(path)
    out: dict[str, str] = {}
    for el in tree.getroot():
        name = el.get("name")
        if el.tag == "string" and name:
            out[name] = el.text or ""
    return out


def format_signature(value: str) -> list[str]:
    return FORMAT_RE.findall(value)


def sync_check() -> int:
    base_path = os.path.join(RES_DIR, "values", "strings.xml")
    base = string_keys(base_path)
    if not base:
        print("FAILED: values/strings.xml missing or unreadable")
        return 1
    locales = sorted(
        d
        for d in os.listdir(RES_DIR)
        if os.path.isdir(os.path.join(RES_DIR, d))
        and re.fullmatch(r"values-[a-z]{2}", d)
    )
    if not locales:
        print("FAILED: no values-* locale directories found")
        return 1
    failed = False
    for locale in locales:
        locale_path = os.path.join(RES_DIR, locale, "strings.xml")
        loc = string_keys(locale_path)
        missing = sorted(set(base) - set(loc))
        extra = sorted(set(loc) - set(base))
        if missing or extra:
            failed = True
            print(f"  [{locale}] key mismatch:")
            for key in missing:
                print(f"    MISSING {key}")
            for key in extra:
                print(f"    EXTRA   {key}")
        for key in base.keys() & loc.keys():
            if format_signature(base[key]) != format_signature(loc[key]):
                failed = True
                print(
                    f"  [{locale}] format specifiers differ for '{key}': "
                    f"en={format_signature(base[key])} vs {format_signature(loc[key])}"
                )
    if failed:
        print("FAILED: translations out of sync (see above)")
        return 1
    print(f"OK: {len(base)} keys in sync across {len(locales)} locales: {', '.join(locales)}")
    return 0


def load_cache() -> dict:
    if os.path.isfile(CACHE):
        try:
            with open(CACHE, encoding="utf-8") as f:
                return json.load(f)
        except (ValueError, OSError):
            pass
    return {}


def save_cache(cache: dict) -> None:
    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    with open(CACHE, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=1)


def translate(text: str, src: str) -> str | None:
    """Back-translate <src> -> en via MyMemory's free API; returns None on failure."""
    url = "https://api.mymemory.translated.net/get?{}".format(
        urllib.parse.urlencode(
            {"q": text, "langpair": f"{src}|en", "de": "me@trik-gamepad.invalid"}
        )
    )
    try:
        with urllib.request.urlopen(url, timeout=20) as resp:
            data = json.load(resp)
        return data["responseData"]["translatedText"] or None
    except (OSError, ValueError, KeyError):
        return None


def backtranslate_check(args) -> int:
    base_path = os.path.join(RES_DIR, "values", "strings.xml")
    base = string_keys(base_path)
    cache = load_cache()
    failures = 0
    rate_limited = 0
    total_queries = 0
    for locale in args.locales:
        locale_path = os.path.join(RES_DIR, f"values-{locale}", "strings.xml")
        loc = string_keys(locale_path)
        low = []
        for key in sorted(base.keys() & loc.keys()):
            source = base[key]
            if not source.strip():
                continue
            cache_key = f"{locale}:{key}"
            back = cache.get(cache_key)
            if back is None:
                back = translate(loc[key], locale)
                total_queries += 1
                if back is None:
                    rate_limited += 1
                    continue
                cache[cache_key] = back
                save_cache(cache)
                time.sleep(1.1)
            ratio = difflib.SequenceMatcher(None, source.lower(), back.lower()).ratio()
            if ratio < args.min_ratio:
                low.append((ratio, key, loc[key], back))
        print(f"== {locale} ({len(loc)} strings, {len(low)} below {args.min_ratio})")
        for ratio, key, value, back in sorted(low):
            print(f"  {ratio:.2f}  [{key}] '{value}' -> '{back}'")
        failures += len(low)
    print(
        f"back-translation done: {total_queries} fresh queries, {rate_limited} failed "
        f"(rate limit / offline), {failures} below {args.min_ratio}"
    )
    if args.strict and failures:
        print("FAILED: --strict and low-confidence translations present")
        return 1
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="trik-gamepad translation checks")
    parser.add_argument("--sync", action="store_true", help="deterministic key/specifier parity check")
    parser.add_argument("--back-translate", action="store_true", help="online back-translation review")
    parser.add_argument(
        "--locales", nargs="+", default=["ru", "fr", "de", "vi"], help="locales to back-translate"
    )
    parser.add_argument("--min-ratio", type=float, default=0.5, help="similarity threshold (0..1)")
    parser.add_argument("--strict", action="store_true", help="fail when rows fall below the threshold")
    args = parser.parse_args()

    # Windows consoles default to a locale codepage (e.g. cp1251) that cannot print the
    # Cyrillic/Accented characters back-translated here; force UTF-8 on the report stream.
    reconfigure = getattr(sys.stdout, "reconfigure", None)
    if reconfigure is not None:
        reconfigure(encoding="utf-8", errors="replace")

    if args.sync:
        return sync_check()
    if args.back_translate:
        return backtranslate_check(args)
    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
