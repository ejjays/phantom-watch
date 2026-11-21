#!/usr/bin/env python3
"""Every source path a doc mentions must exist."""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# docs shorten package paths, resolve them against module that owns them
ALIASES = (
    ("app/", "app/src/main/java/dev/phantomwatch/"),
    ("shared/", "shared/src/commonMain/kotlin/dev/phantomwatch/"),
    ("settings/", "shared/src/commonMain/kotlin/dev/phantomwatch/ui/settings/"),
    ("player/", "app/src/main/java/dev/phantomwatch/player/"),
    ("web/", "web/src/wasmJsMain/kotlin/dev/phantomwatch/web/"),
    ("assets/", "app/src/main/assets/"),
    ("res/", "app/src/main/res/"),
)
EXTRA = (
    "app/src/main/",
    "app/src/main/java/dev/phantomwatch/",
    "shared/src/commonMain/kotlin/dev/phantomwatch/",
    "web/src/wasmJsMain/kotlin/dev/phantomwatch/web/",
)
BY_NAME = (
    "app/src",
    "shared/src",
    "web/src",
    "app/src/main/assets",
    "app/src/main/res",
    "scripts",
    "checks",
    "backend/worker",
    "docs",
)
SKIP = re.compile(r"^(https?:|\./gradlew|node |python3 |curl |adb |am |\. |org\.)")
GENERATED = re.compile(r"(^|/)build/")


def resolve(token):
    token = token.split("#", 1)[0].lstrip("./")
    if not token:
        return None
    for base in ("", *EXTRA):
        path = ROOT / base / token
        if "*" in token:
            if list(ROOT.glob(str(pathlib.Path(base) / token))):
                return path
        elif path.exists():
            return path
    for prefix, target in ALIASES:
        if token.startswith(prefix):
            rel = token[len(prefix):]
            if list((ROOT / target).glob(rel)):
                return ROOT / target / rel
    if "/" not in token and re.search(r"\.(kt|js|mjs|json|md|py|xml|sh|pem|txt)$", token):
        for base in BY_NAME:
            if next((ROOT / base).rglob(token), None) is not None:
                return next((ROOT / base).rglob(token))
    return None


def main():
    problems = []
    checked = 0
    for doc in sorted((ROOT / "docs").glob("*.md")):
        for token in re.findall(r"`([^`\s]+)`", doc.read_text()):
            if token.startswith(("http", "-", "/")) or SKIP.search(token) or GENERATED.search(token):
                continue
            if not (re.search(r"\.(kt|kts|py|js|mjs|json|md|yml|xml|sh|pem|txt)$", token) or "/" in token):
                continue
            if any(c in token for c in "<>$…"):
                continue
            checked += 1
            if resolve(token) is None:
                problems.append(f"{doc.relative_to(ROOT)}: {token}")
    if problems:
        for p in problems:
            print(f"FAIL {p}")
        print(f"\n{len(problems)} missing path(s)")
        return 1
    print(f"docs ok: {checked} paths")
    return 0


if __name__ == "__main__":
    sys.exit(main())
