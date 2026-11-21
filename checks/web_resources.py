#!/usr/bin/env python3
"""The wasm bundle ships a copy of the resource tree, and nothing puts it there.

`web/web/composeResources/` is a hand-run `cp` of what the compiler generates. Compose
addresses every string by byte offset inside `strings.commonMain.cvr`, so a copy that lags
`strings.xml` by even one key does not fail to build or throw at runtime: each accessor
decodes the neighbouring record and the text comes out blank or wrong. That is how every
settings label, tab title and the Top 10 heading went missing on the live site.

This compares the shipped tree against the sources it mirrors, without Gradle:
- every file under shared/src/commonMain/composeResources must have a byte-identical
  counterpart under web/web/composeResources/dev.phantomwatch.res, and nothing extra
- strings.commonMain.cvr must hold exactly the keys of values/strings.xml, same values

Run: python3 checks/web_resources.py
"""
import base64
import pathlib
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "shared/src/commonMain/composeResources"
SHIPPED = ROOT / "web/web/composeResources/dev.phantomwatch.res"
CVR = pathlib.Path("values/strings.commonMain.cvr")
SYNC = ("cp -a web/build/kotlin-multiplatform-resources/resources-from-dependencies/"
        "wasmJs/composeResources/. web/web/composeResources/")

problems = []


def fail(msg):
    problems.append(msg)


def xml_records(path):
    """(kind, name) -> text, as strings.xml declares it."""
    out = {}
    for el in ET.parse(path).getroot():
        if el.tag == "plurals":
            out[("plurals", el.get("name"))] = " ".join(
                "%s=%s" % (i.get("quantity").upper(), i.text or "") for i in el.findall("item"))
        elif el.tag == "string":
            out[("string", el.get("name"))] = el.text or ""
    return out


def cvr_records(path):
    """(kind, name) -> text, as the shipped cvr stores it."""
    out = {}
    for line in path.read_text().splitlines():
        if not line or line.startswith("version:"):
            continue
        kind, name, payload = line.split("|", 2)
        if kind == "plurals":
            items = [i.split(":", 1) for i in payload.split(",")]
            out[(kind, name)] = " ".join(
                "%s=%s" % (q, base64.b64decode(v).decode()) for q, v in items)
        else:
            out[(kind, name)] = base64.b64decode(payload).decode()
    return out


def check_strings():
    want = xml_records(SRC / "values/strings.xml")
    got = cvr_records(SHIPPED / CVR)
    for kind, name in sorted(set(want) - set(got)):
        fail("cvr is missing %s %s" % (kind, name))
    for kind, name in sorted(set(got) - set(want)):
        fail("cvr has %s %s, strings.xml does not" % (kind, name))
    for key in sorted(set(want) & set(got)):
        if want[key] != got[key]:
            fail("cvr value differs for %s %s: xml %r, cvr %r"
                 % (key[0], key[1], want[key], got[key]))


def check_tree():
    for path in sorted(SRC.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(SRC)
        if str(rel) == "values/strings.xml":  # becomes the cvr, compared above
            continue
        shipped = SHIPPED / rel
        if not shipped.exists():
            fail("not shipped: %s" % rel)
        elif shipped.read_bytes() != path.read_bytes():
            fail("differs from source: %s" % rel)
    for path in sorted(SHIPPED.rglob("*")):
        rel = path.relative_to(SHIPPED)
        if path.is_file() and str(rel) != str(CVR) and not (SRC / rel).exists():
            fail("shipped but not in source: %s" % rel)


check_strings()
check_tree()
if problems:
    for msg in problems[:20]:
        print("web_resources:", msg)
    if len(problems) > 20:
        print("web_resources: ... and %d more" % (len(problems) - 20))
    print("\nThe bundle needs the generated tree, not the last one:\n  " + SYNC)
    sys.exit(1)
print("web_resources: %s in sync" % SHIPPED.relative_to(ROOT))
