#!/usr/bin/env python3
"""One owner per string: app res keeps platform text, shared owns what it renders."""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main/res/values/strings.xml"
SHARED = ROOT / "shared/src/commonMain/composeResources/values/strings.xml"


def keys(path):
    text = path.read_text()
    return set(re.findall(r'<(?:string|plurals)\b[^>]*\bname="([^"]+)"', text))


def used(pattern, roots):
    found = set()
    for root in roots:
        for p in root.rglob("*.kt"):
            found |= set(re.findall(pattern, p.read_text(errors="replace")))
    return found


def main():
    app_keys = keys(APP)
    shared_keys = keys(SHARED)
    problems = []

    for key in sorted(app_keys & shared_keys):
        problems.append(f"{key}: defined in both app and shared resources")

    app_used = used(r"R\.string\.(\w+)", [ROOT / "app/src/main"])
    for key in sorted(app_used - app_keys - shared_keys):
        problems.append(f"{key}: used by app code but defined nowhere")

    app_plurals = used(r"R\.plurals\.(\w+)", [ROOT / "app/src/main"])
    for key in sorted(app_plurals - app_keys - shared_keys):
        problems.append(f"{key}: plural used by app code but defined nowhere")

    shared_used = used(r"Res\.string\.(\w+)", [ROOT / "shared/src", ROOT / "web/src"])
    for key in sorted(shared_used - shared_keys):
        problems.append(f"{key}: used by shared code but missing from shared resources")

    shared_plurals = used(r"Res\.plurals\.(\w+)", [ROOT / "shared/src", ROOT / "web/src"])
    for key in sorted(shared_plurals - shared_keys):
        problems.append(f"{key}: plural used by shared code but missing from shared resources")

    if problems:
        for p in problems:
            print(f"FAIL {p}")
        print(f"\n{len(problems)} problem(s)")
        return 1
    print(f"strings ok: {len(app_keys)} app, {len(shared_keys)} shared, no overlap")
    return 0


if __name__ == "__main__":
    sys.exit(main())
