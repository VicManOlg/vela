#!/usr/bin/env python3
"""
Builds `core/catalog/src/main/resources/catalog/emulators.json`, Vela's normalised catalogue of
Android emulators and how to launch them, from two public, MIT-licensed sources:

  * Daijishō platform definitions (TapiocaFox/Daijishou, `platforms/*.json`): one file per system
    with an `amStartArguments` recipe per emulator. This is the main source.
  * ES-DE Android find rules (`resources/systems/android/es_find_rules.xml`): package/activity pairs
    per emulator, used to cross-check the Daijishō recipes and recorded as `knownActivities`.

The catalogue is a versioned asset: the app never downloads it. Run this script to refresh it:

    python scripts/build_emulator_catalog.py            # download and regenerate
    python scripts/build_emulator_catalog.py --offline  # reuse the last download in scripts/.cache

Only the Python standard library is used. Unknown `am start` arguments are never dropped: every
emulator keeps the original recipe in `raw`, and anything the parser could not fully understand is
reported in the top-level `warnings` list (and printed).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shlex
import sys
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(__file__).resolve().parent / ".cache"
OUTPUT = ROOT / "core/catalog/src/main/resources/catalog/emulators.json"
SUMMARY = ROOT / ".claude/skills/emulator-launching/references/catalog-summary.md"

DAIJISHOU_API = "https://api.github.com/repos/TapiocaFox/Daijishou"
DAIJISHOU_TREE = "https://github.com/TapiocaFox/Daijishou/tree/main/platforms"
ESDE_FIND_RULES = "https://gitlab.com/es-de/emulationstation-de/-/raw/master/resources/systems/android/es_find_rules.xml"
USER_AGENT = "vela-catalog-builder (+https://github.com/VicManOlg/vela)"

# ---------------------------------------------------------------------------------------------
# am start argument parser
# ---------------------------------------------------------------------------------------------

ACTIVITY_FLAGS = {
    "--activity-clear-task": "FLAG_ACTIVITY_CLEAR_TASK",
    "--activity-clear-top": "FLAG_ACTIVITY_CLEAR_TOP",
    "--activity-new-task": "FLAG_ACTIVITY_NEW_TASK",
    "--activity-no-history": "FLAG_ACTIVITY_NO_HISTORY",
    "--activity-single-top": "FLAG_ACTIVITY_SINGLE_TOP",
    "--activity-multiple-task": "FLAG_ACTIVITY_MULTIPLE_TASK",
    "--activity-exclude-from-recents": "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS",
    "--activity-brought-to-front": "FLAG_ACTIVITY_BROUGHT_TO_FRONT",
    "--activity-reorder-to-front": "FLAG_ACTIVITY_REORDER_TO_FRONT",
    "--activity-no-animation": "FLAG_ACTIVITY_NO_ANIMATION",
    "--activity-no-user-action": "FLAG_ACTIVITY_NO_USER_ACTION",
    "--activity-clear-when-task-reset": "FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET",
    "--activity-reset-task-if-needed": "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED",
    "--activity-launched-from-history": "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY",
    "--activity-previous-is-top": "FLAG_ACTIVITY_PREVIOUS_IS_TOP",
    "--activity-task-on-home": "FLAG_ACTIVITY_TASK_ON_HOME",
    "--activity-forward-result": "FLAG_ACTIVITY_FORWARD_RESULT",
    "--activity-match-external": "FLAG_ACTIVITY_MATCH_EXTERNAL",
    "--grant-read-uri-permission": "FLAG_GRANT_READ_URI_PERMISSION",
    "--grant-write-uri-permission": "FLAG_GRANT_WRITE_URI_PERMISSION",
    "--grant-persistable-uri-permission": "FLAG_GRANT_PERSISTABLE_URI_PERMISSION",
    "--grant-prefix-uri-permission": "FLAG_GRANT_PREFIX_URI_PERMISSION",
    "--exclude-stopped-packages": "FLAG_EXCLUDE_STOPPED_PACKAGES",
    "--include-stopped-packages": "FLAG_INCLUDE_STOPPED_PACKAGES",
    "--debug-log-resolution": "FLAG_DEBUG_LOG_RESOLUTION",
}

# Numeric -f values, decoded for readability (android.content.Intent constants).
FLAG_BITS = {
    0x00000001: "FLAG_GRANT_READ_URI_PERMISSION",
    0x00000002: "FLAG_GRANT_WRITE_URI_PERMISSION",
    0x00000040: "FLAG_GRANT_PERSISTABLE_URI_PERMISSION",
    0x00000080: "FLAG_GRANT_PREFIX_URI_PERMISSION",
    0x00008000: "FLAG_ACTIVITY_MULTIPLE_TASK",
    0x00010000: "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS",
    0x00020000: "FLAG_ACTIVITY_NO_ANIMATION",
    0x00080000: "FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET",
    0x00200000: "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED",
    0x00800000: "FLAG_ACTIVITY_BROUGHT_TO_FRONT",
    0x02000000: "FLAG_ACTIVITY_SINGLE_TOP",
    0x04000000: "FLAG_ACTIVITY_CLEAR_TOP",
    0x08000000: "FLAG_ACTIVITY_CLEAR_TASK",
    0x10000000: "FLAG_ACTIVITY_NEW_TASK",
    0x20000000: "FLAG_ACTIVITY_NO_HISTORY",
    0x40000000: "FLAG_ACTIVITY_NO_USER_ACTION",
}

# option -> (extra type, number of value tokens)
EXTRA_OPTIONS = {
    "-e": ("string", 2), "--es": ("string", 2),
    "--esn": ("null", 1),
    "--ez": ("boolean", 2),
    "--ei": ("int", 2),
    "--el": ("long", 2),
    "--ef": ("float", 2),
    "--eu": ("uri", 2),
    "--ecn": ("component", 2),
    "--esa": ("string[]", 2),
    "--eia": ("int[]", 2),
    "--ela": ("long[]", 2),
    "--efa": ("float[]", 2),
    "--esal": ("string-list", 2),
    "--eial": ("int-list", 2),
    "--elal": ("long-list", 2),
    "--efal": ("float-list", 2),
}

PLACEHOLDER = re.compile(r"\{[a-zA-Z0-9_.]+\}")


@dataclass
class ParsedIntent:
    package: str | None = None
    activity: str | None = None
    action: str | None = None
    data: str | None = None
    mimeType: str | None = None
    categories: list[str] = field(default_factory=list)
    extras: list[dict] = field(default_factory=list)
    flags: list[str] = field(default_factory=list)
    rawFlags: int | None = None
    forceStop: bool = False
    waitForLaunch: bool = False
    userId: str | None = None
    placeholders: list[str] = field(default_factory=list)
    unparsed: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def tokenize(raw: str) -> tuple[list[str], list[str]]:
    """Shell-style split; falls back to whitespace splitting on unbalanced quotes."""
    text = raw.replace("\r", "\n").replace("\n", " ")
    try:
        return shlex.split(text, posix=True), []
    except ValueError as e:
        return text.split(), [f"quote error ({e}); split on whitespace"]


def parse_am_start(raw: str) -> ParsedIntent:
    """Parses the argument part of an `am start` command as Daijishō stores it."""
    out = ParsedIntent()
    tokens, warnings = tokenize(raw)
    out.warnings.extend(warnings)
    i = 0

    def take(n: int, opt: str) -> list[str] | None:
        nonlocal i
        if i + n >= len(tokens) + 0 and i + n > len(tokens) - 1 + 1:
            pass
        vals = tokens[i + 1 : i + 1 + n]
        if len(vals) < n:
            out.warnings.append(f"{opt} is missing {n - len(vals)} value(s)")
            out.unparsed.append(" ".join([opt] + vals))
            i = len(tokens)
            return None
        i += n
        return vals

    while i < len(tokens):
        tok = tokens[i]
        if tok in ("am", "start", "start-activity"):
            pass
        elif tok == "-n":
            vals = take(1, tok)
            if vals:
                comp = vals[0]
                if "/" in comp:
                    pkg, act = comp.split("/", 1)
                    out.package = pkg
                    out.activity = pkg + act if act.startswith(".") else act
                else:
                    out.warnings.append(f"-n without '/': {comp!r}")
                    out.package = comp
        elif tok == "-p":
            vals = take(1, tok)
            if vals:
                out.package = out.package or vals[0]
        elif tok == "-a":
            vals = take(1, tok)
            if vals:
                out.action = vals[0]
        elif tok == "-d":
            vals = take(1, tok)
            if vals:
                out.data = vals[0]
        elif tok == "-t":
            vals = take(1, tok)
            if vals:
                out.mimeType = vals[0]
        elif tok == "-c":
            vals = take(1, tok)
            if vals:
                out.categories.append(vals[0])
        elif tok == "-f":
            vals = take(1, tok)
            if vals:
                try:
                    n = int(vals[0], 0)
                    out.rawFlags = n
                    for bit, name in FLAG_BITS.items():
                        if n & bit and name not in out.flags:
                            out.flags.append(name)
                    leftover = n & ~sum(FLAG_BITS)
                    if leftover:
                        out.warnings.append(f"-f has bits without a known name: {hex(leftover)}")
                except ValueError:
                    out.warnings.append(f"-f value is not a number: {vals[0]!r}")
                    out.unparsed.append(f"-f {vals[0]}")
        elif tok in EXTRA_OPTIONS:
            kind, n = EXTRA_OPTIONS[tok]
            vals = take(n, tok)
            if vals:
                extra = {"key": vals[0], "type": kind}
                if n == 2:
                    extra["value"] = vals[1]
                out.extras.append(extra)
        elif tok in ACTIVITY_FLAGS:
            name = ACTIVITY_FLAGS[tok]
            if name not in out.flags:
                out.flags.append(name)
        elif tok == "-S":
            out.forceStop = True
        elif tok == "-W":
            out.waitForLaunch = True
        elif tok in ("-D", "-N", "-P", "--track-allocation", "-R", "--windowingMode", "--activityType", "--display", "--user", "--receiver-permission", "--attach-agent", "--sampling", "--streaming"):
            # Accepted by `am start` but meaningless for a frontend; keep the raw text so nothing is lost.
            n = 1 if tok in ("-P", "--user", "--receiver-permission", "--attach-agent", "--sampling", "-R", "--windowingMode", "--activityType", "--display") else 0
            vals = take(n, tok) if n else []
            out.unparsed.append(" ".join([tok] + (vals or [])))
            out.warnings.append(f"ignored am option {tok}")
            if tok == "--user" and vals:
                out.userId = vals[0]
        else:
            out.unparsed.append(tok)
            out.warnings.append(f"unknown token {tok!r}")
        i += 1

    text_for_placeholders = " ".join(tokens)
    out.placeholders = sorted(set(PLACEHOLDER.findall(text_for_placeholders)))
    if out.package is None:
        out.warnings.append("no package (-n or -p)")
    return out


def data_type(parsed: ParsedIntent) -> str:
    """How the emulator receives the game: 'uri', 'path', 'both' or 'none'."""
    text = json.dumps(asdict(parsed))
    uses_uri = "{file.uri}" in text or "uri}" in text
    uses_path = "{file.path}" in text or "path}" in text
    if uses_uri and uses_path:
        return "both"
    if uses_uri:
        return "uri"
    if uses_path:
        return "path"
    return "none"


# Daijishō writes the extension filter as `^(.*)\.(?:nes|zip)$`; some files forget the backslash
# before the dot (`^(.*).(?:java|jar)$`), so the escape is optional here.
EXT_GROUP = re.compile(r"\\?\.\(\?:([^()]+)\)\$?", re.IGNORECASE)
EXT_GROUP_PLAIN = re.compile(r"\\?\.\(([^()]+)\)\$?", re.IGNORECASE)
EXT_SINGLE = re.compile(r"\\?\.([a-z0-9]+)\$", re.IGNORECASE)


def extensions_from_regex(regex: str) -> tuple[list[str], str | None]:
    """Turns Daijishō's `^(.*)\\.(?:nes|zip)$` style filter into a sorted extension list."""
    if not regex:
        return [], "empty filename regex"
    for pattern in (EXT_GROUP, EXT_GROUP_PLAIN):
        m = pattern.search(regex)
        if m:
            parts = [p.strip().lower() for p in m.group(1).split("|") if p.strip()]
            clean = [p for p in parts if re.fullmatch(r"[a-z0-9]+", p)]
            warning = None if len(clean) == len(parts) else f"non-literal alternatives in regex {regex!r}"
            return sorted(set(clean)), warning
    m = EXT_SINGLE.search(regex)
    if m:
        return [m.group(1).lower()], None
    return [], f"could not read extensions from regex {regex!r}"


# ---------------------------------------------------------------------------------------------
# Sources
# ---------------------------------------------------------------------------------------------

def fetch(url: str, dest: Path, offline: bool) -> bytes:
    if offline and dest.exists():
        return dest.read_bytes()
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    data = urllib.request.urlopen(req, timeout=60).read()
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return data


def fetch_daijishou(offline: bool) -> tuple[list[dict], str]:
    """Returns the platform JSON documents and the commit SHA they came from."""
    head = json.loads(fetch(f"{DAIJISHOU_API}/commits/main", CACHE / "daijishou_head.json", offline))
    sha = head["sha"]
    listing = json.loads(fetch(f"{DAIJISHOU_API}/contents/platforms?ref={sha}", CACHE / "daijishou_platforms.json", offline))
    docs = []
    for entry in sorted(listing, key=lambda e: e["name"]):
        if not entry["name"].endswith(".json"):
            continue  # *.json.deprecated and anything else
        data = fetch(entry["download_url"], CACHE / "daijishou" / entry["name"], offline)
        try:
            doc = json.loads(data.decode("utf-8"))
        except json.JSONDecodeError as e:
            print(f"warning: {entry['name']}: invalid JSON ({e})", file=sys.stderr)
            continue
        doc["_file"] = entry["name"]
        docs.append(doc)
    return docs, sha


def fetch_esde(offline: bool) -> list[dict] | None:
    """ES-DE emulator name -> package/activity entries, or None when GitLab is unreachable."""
    try:
        xml = fetch(ESDE_FIND_RULES, CACHE / "es_find_rules.xml", offline)
    except Exception as e:  # network or 404
        print(f"warning: ES-DE find rules unavailable: {e}", file=sys.stderr)
        return None
    root = ET.fromstring(xml)
    known = []
    for emu in root.iter("emulator"):
        name = emu.get("name")
        for rule in emu.findall("rule"):
            if rule.get("type") != "androidpackage":
                continue
            for entry in rule.findall("entry"):
                text = (entry.text or "").strip()
                if "/" not in text:
                    continue
                pkg, act = text.split("/", 1)
                known.append({"emulator": name, "package": pkg, "activity": pkg + act if act.startswith(".") else act})
    return known


# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------

def build(offline: bool) -> dict:
    docs, sha = fetch_daijishou(offline)
    known = fetch_esde(offline)
    known_pairs = {(k["package"], k["activity"]) for k in (known or [])}
    warnings: list[dict] = []
    platforms = []

    for doc in docs:
        if "platform" not in doc:
            continue  # index.json and other non-platform files
        p = doc.get("platform", {})
        pid = p.get("uniqueId") or Path(doc["_file"]).stem.lower()
        # Current Daijishō files keep a generic "not a hidden file" regex on the platform and the
        # real extension filter on each player; the platform list is the union of its players'.
        platform_exts, _ = extensions_from_regex(p.get("acceptedFilenameRegex", ""))
        emulators = []
        for player in doc.get("playerList", []):
            raw = player.get("amStartArguments", "") or ""
            parsed = parse_am_start(raw)
            eid = player.get("uniqueId") or f"{pid}.{len(emulators)}"
            for w in parsed.warnings:
                warnings.append({"platform": pid, "emulator": eid, "message": w})
            player_exts, ext_warning = extensions_from_regex(player.get("acceptedFilenameRegex", ""))
            if ext_warning:
                warnings.append({"platform": pid, "emulator": eid, "message": ext_warning})
            emulator = {
                "id": eid,
                "name": player.get("name", eid),
                "extensions": player_exts,
                "package": parsed.package,
                "activity": parsed.activity,
                "action": parsed.action,
                "dataType": data_type(parsed),
                "data": parsed.data,
                "mimeType": parsed.mimeType,
                "categories": parsed.categories,
                "extras": parsed.extras,
                "flags": parsed.flags,
                "forceStopBeforeLaunch": parsed.forceStop,
                "killProcess": bool(player.get("killPackageProcesses", False)),
                "requiresBios": None,  # neither source states it
                "placeholders": parsed.placeholders,
                "esdeVerified": (parsed.package, parsed.activity) in known_pairs if known else None,
                "source": "daijishou",
                "raw": raw,
                "notes": (player.get("description") or "").strip() or None,
                "warnings": parsed.warnings + ([f"unparsed: {' '.join(parsed.unparsed)}"] if parsed.unparsed else []),
            }
            emulators.append(emulator)
        exts = sorted(set(platform_exts) | {x for e in emulators for x in e["extensions"]})
        if not exts:
            warnings.append({"platform": pid, "emulator": None, "message": "no extensions could be read from the platform or its emulators"})
        platforms.append({
            "id": pid,
            "name": p.get("name", pid),
            "shortName": p.get("shortname"),
            "extensions": exts,
            "acceptedFilenameRegex": p.get("acceptedFilenameRegex"),
            "source": "daijishou",
            "emulators": emulators,
        })

    sources = [{
        "id": "daijishou",
        "name": "Daijishō platform definitions",
        "url": DAIJISHOU_TREE,
        "revision": sha,
        "license": "MIT",
        "copyright": "Copyright (c) 2022 TapiocaFox (Yves Chen)",
    }]
    if known is not None:
        sources.append({
            "id": "es-de",
            "name": "ES-DE Android find rules",
            "url": ESDE_FIND_RULES,
            "revision": "master",
            "license": "MIT",
            "copyright": "Copyright (c) 2024-2026 Northwestern Software AB; (c) 2020-2024 Leon Styhre; (c) 2014 Alec Lofquist",
        })

    return {
        "schemaVersion": 1,
        "generatedAt": dt.date.today().isoformat(),
        "sources": sources,
        "platforms": platforms,
        "knownActivities": known or [],
        "warnings": warnings,
    }


def write_summary(catalog: dict) -> None:
    lines = [
        "# Emulator catalogue summary",
        "",
        f"Generated {catalog['generatedAt']} by `scripts/build_emulator_catalog.py` from:",
    ]
    for s in catalog["sources"]:
        lines.append(f"- {s['name']} ({s['license']}, revision `{s['revision'][:12]}`): {s['url']}")
    n_emu = sum(len(p["emulators"]) for p in catalog["platforms"])
    lines += [
        "",
        f"{len(catalog['platforms'])} platforms, {n_emu} emulator recipes, {len(catalog['knownActivities'])} ES-DE package/activity pairs, {len(catalog['warnings'])} warnings.",
        "",
        "Regenerate with `python scripts/build_emulator_catalog.py` (add `--offline` to reuse `scripts/.cache`).",
        "",
        "## Platforms",
        "",
        "| id | name | extensions | emulators |",
        "|---|---|---|---|",
    ]
    for p in catalog["platforms"]:
        lines.append(f"| `{p['id']}` | {p['name']} | {', '.join(p['extensions'][:12])}{'…' if len(p['extensions']) > 12 else ''} | {len(p['emulators'])} |")
    lines += ["", "## Packages seen", ""]
    pkgs: dict[str, set[str]] = {}
    for p in catalog["platforms"]:
        for e in p["emulators"]:
            if e["package"]:
                pkgs.setdefault(e["package"], set()).add(p["id"])
    for pkg in sorted(pkgs):
        lines.append(f"- `{pkg}`: {', '.join(sorted(pkgs[pkg]))}")
    if catalog["warnings"]:
        lines += ["", "## Warnings", ""]
        for w in catalog["warnings"]:
            lines.append(f"- `{w['platform']}` / `{w['emulator']}`: {w['message']}")
    SUMMARY.parent.mkdir(parents=True, exist_ok=True)
    SUMMARY.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--offline", action="store_true", help="reuse scripts/.cache instead of downloading")
    ap.add_argument("--output", type=Path, default=OUTPUT)
    args = ap.parse_args()

    catalog = build(args.offline)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    write_summary(catalog)

    n_emu = sum(len(p["emulators"]) for p in catalog["platforms"])
    print(f"{len(catalog['platforms'])} platforms, {n_emu} emulators, {len(catalog['knownActivities'])} ES-DE pairs, {len(catalog['warnings'])} warnings")
    print(f"written {args.output.relative_to(ROOT)} and {SUMMARY.relative_to(ROOT)}")
    for w in catalog["warnings"]:
        print(f"  warning: {w['platform']} / {w['emulator']}: {w['message']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
