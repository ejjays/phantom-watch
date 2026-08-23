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

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from config_rules import problems as config_problems

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
    "app/src/main/java/dev/phantomwatch/player/PlayerScreen.kt": 1338,
}

ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_INSTALL_PACKAGES",
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


def check_release_signing():
    p = ROOT / "app/build.gradle.kts"
    if p.exists() and "debug.keystore" in p.read_text():
        fail("app/build.gradle.kts: release signing must not use the shared debug keystore")


def check_providers_config():
    p = ROOT / "app/src/main/assets/providers.json"
    cfg = json.loads(p.read_text())
    for err in config_problems(cfg, "", require_script=False):
        fail(f"providers.json: {err}")
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


def check_media_notification():
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
    for perm in ("POST_NOTIFICATIONS", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK"):
        if f"android.permission.{perm}" not in manifest:
            fail(f"AndroidManifest.xml: media card needs {perm}")
    svc = re.search(r"<service[^>]*\.PlaybackService.*?</service>", manifest, re.S)
    if not svc:
        fail("AndroidManifest.xml: PlaybackService is gone, so no media card on any device")
    else:
        if 'foregroundServiceType="mediaPlayback"' not in svc.group(0):
            fail("AndroidManifest.xml: PlaybackService is not a mediaPlayback foreground service")
        if "androidx.media3.session.MediaSessionService" not in svc.group(0):
            fail("AndroidManifest.xml: PlaybackService has no MediaSessionService action")
    if "POST_NOTIFICATIONS" not in (ROOT / "app/src/main/java/dev/phantomwatch/MainActivity.kt").read_text():
        fail("MainActivity.kt: POST_NOTIFICATIONS is never requested, so Android 13+ shows no card")
    screen = (ROOT / "app/src/main/java/dev/phantomwatch/player/PlayerScreen.kt").read_text()
    if re.search(r"\bexo\.stop\(\)", screen):
        fail("PlayerScreen.kt: stopping the player on dispose removes the media card (use pause, or leave it)")


def check_manifest_components(files):
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
    sources = {
        f: (ROOT / f).read_text(errors="replace")
        for f in files
        if f.startswith("app/src/main/java") and f.endswith(".kt") and (ROOT / f).is_file()
    }
    for tag, name in re.findall(
        r"<(activity|service|receiver|provider)[^>]*android:name=\"\.([\w.]+)\"", manifest
    ):
        simple = name.rsplit(".", 1)[-1]
        want = "dev.phantomwatch" + ("." + name.rsplit(".", 1)[0] if "." in name else "")
        hits = [f for f, body in sources.items() if re.search(rf"\bclass\s+{simple}\b", body)]
        if not hits:
            fail(f"AndroidManifest.xml: {tag} .{name} points at a class that does not exist")
            continue
        packages = {re.search(r"^package\s+([\w.]+)", sources[f], re.M).group(1) for f in hits}
        if want not in packages:
            fail(
                f"AndroidManifest.xml: {tag} .{name} resolves to {want}.{simple}, "
                f"but {simple} is declared in {sorted(packages)[0]}"
            )


def check_package_dirs(files):
    for f in files:
        if not f.startswith(("app/src/main/java", "app/src/test/java")) or not f.endswith(".kt"):
            continue
        p = ROOT / f
        if not p.is_file():
            continue
        m = re.search(r"^package\s+([\w.]+)", p.read_text(errors="replace"), re.M)
        want = f.split("/java/", 1)[1].rpartition("/")[0].replace("/", ".")
        if not m:
            fail(f"{f}: no package declaration (expected {want})")
        elif m.group(1) != want:
            fail(f"{f}: package {m.group(1)} != directory {want}")


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
    check_release_signing()
    check_providers_config()
    check_permissions()
    check_media_notification()
    check_manifest_components(files)
    check_package_dirs(files)
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
