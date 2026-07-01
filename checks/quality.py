#!/usr/bin/env python3
"""Repo invariants that must hold before a change lands.

Run: python3 checks/quality.py
Exit 1 on the first failing category.
"""
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

LINE_CAPS = {
    "app/src/main/java": 900,
    "app/src/test/java": 400,
    "app/src/main/assets": 700,
    "worker": 700,
    "checks": 400,
    "scripts": 300,
}

# still too big; they must not grow until they are split
LARGE_BASELINES = {
    "app/src/main/java/dev/phantomwatch/PlayerScreen.kt": 1640,
}

ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
}

failures = []


def fail(msg):
    failures.append(msg)


def tracked_files():
    try:
        out = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=True,
        ).stdout
    except Exception:
        out = ""
        for p in ROOT.rglob("*"):
            if p.is_file() and ".git/" not in str(p):
                out += str(p.relative_to(ROOT)) + "\n"
    return [l.strip() for l in out.splitlines() if l.strip()]


def check_line_caps(files):
    for f in files:
        p = ROOT / f
        if not p.is_file() or p.suffix not in (".kt", ".js", ".py", ".mjs", ".sh", ".kts"):
            continue
        for prefix, cap in LINE_CAPS.items():
            if f.startswith(prefix):
                n = len(p.read_text(errors="replace").splitlines())
                limit = LARGE_BASELINES.get(f, cap)
                if n > limit:
                    fail(f"{f}: {n} lines > limit {limit}")
                break


def check_no_todos(files):
    pat = re.compile(r"\b(TODO|FIXME|XXX)\b")
    for f in files:
        if not f.startswith("app/src/main"):
            continue
        p = ROOT / f
        if not p.is_file() or p.suffix not in (".kt", ".js"):
            continue
        for i, line in enumerate(p.read_text(errors="replace").splitlines(), 1):
            if pat.search(line):
                fail(f"{f}:{i}: leftover marker")


def check_no_dynamic_deps():
    p = ROOT / "app/build.gradle.kts"
    if not p.exists():
        return
    for i, line in enumerate(p.read_text().splitlines(), 1):
        if "SNAPSHOT" in line or re.search(r'"\d[\w.\-]*\+"', line):
            fail(f"app/build.gradle.kts:{i}: unpinned dependency")


def check_providers_config():
    p = ROOT / "app/src/main/assets/providers.json"
    cfg = json.loads(p.read_text())
    if not isinstance(cfg.get("version"), int):
        fail("providers.json: version is not an int")
    legs = cfg.get("legs")
    if not isinstance(legs, dict) or not legs:
        fail("providers.json: legs missing or empty")
        return
    enabled = 0
    for name, leg in legs.items():
        if not isinstance(leg, dict):
            fail(f"providers.json: leg {name} is not an object")
            continue
        on = leg.get("enabled", True)
        if not isinstance(on, bool):
            fail(f"providers.json: leg {name} enabled is not a bool")
        enabled += 1 if on else 0
    if not enabled:
        fail("providers.json: no enabled legs")
    js = (ROOT / "app/src/main/assets/providers.js").read_text()
    for symbol in ("window.__pwRun", "window.__pwSort"):
        if symbol not in js:
            fail(f"providers.js: missing {symbol}")


def check_permissions():
    p = ROOT / "app/src/main/AndroidManifest.xml"
    for perm in re.findall(r'android\.permission\.([A-Z_]+)', p.read_text()):
        full = f"android.permission.{perm}"
        if full not in ALLOWED_PERMISSIONS:
            fail(f"AndroidManifest.xml: unexpected permission {full}")


def check_no_committed_secrets(files):
    pat = re.compile(
        r"(?i)\b(api[_-]?key|secret|password|token)\b\s*[:=]\s*[\"'][A-Za-z0-9_\-]{20,}[\"']"
    )
    skip = ("providers.json", "sign_config.py", "setup-seedbox.sh", "quality.py")
    for f in files:
        if any(s in f for s in skip):
            continue
        p = ROOT / f
        if not p.is_file() or p.suffix not in (".kt", ".kts", ".js", ".py", ".json", ".yml"):
            continue
        for i, line in enumerate(p.read_text(errors="replace").splitlines(), 1):
            if pat.search(line):
                fail(f"{f}:{i}: possible committed secret")


def main():
    files = tracked_files()
    check_line_caps(files)
    check_no_todos(files)
    check_no_dynamic_deps()
    check_providers_config()
    check_permissions()
    check_no_committed_secrets(files)
    if failures:
        for f in failures:
            print(f"FAIL {f}")
        print(f"\n{len(failures)} problem(s)")
        return 1
    print(f"quality ok: {len(files)} tracked files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
