"""Shared providers.json / providers.js rules.

Used by checks/quality.py, sign_config.py and the config-check workflow so the
schema is enforced in exactly one place.
"""
import json
import sys


def problems(cfg, script, require_script=True):
    out = []
    if not isinstance(cfg.get("version"), int):
        out.append("version is not an int")
    legs = cfg.get("legs")
    if not isinstance(legs, dict) or not legs:
        out.append("legs missing or empty")
        return out
    enabled = []
    for name, leg in legs.items():
        if not isinstance(leg, dict):
            out.append(f"{name}: leg is not an object")
            continue
        on = leg.get("enabled", True)
        if not isinstance(on, bool):
            out.append(f"{name}: enabled is not a bool")
        enabled.append(on)
    if not any(enabled):
        out.append("no enabled legs")
    services = cfg.get("services")
    if services is not None and not isinstance(services, dict):
        out.append("services is not an object")
    elif isinstance(services, dict):
        tmdb = services.get("tmdb")
        if tmdb is not None and not isinstance(tmdb, dict):
            out.append("services.tmdb is not an object")
        elif isinstance(tmdb, dict):
            for field in ("base", "image"):
                value = tmdb.get(field)
                if value is not None and (not isinstance(value, str) or not value.startswith("http")):
                    out.append(f"services.tmdb.{field} is not an http url")
            if "key" in tmdb and not isinstance(tmdb["key"], str):
                out.append("services.tmdb.key is not a string")
    if require_script and not script.strip():
        out.append("providers.js is empty")
    return out


if __name__ == "__main__":
    cfg = json.loads(open(sys.argv[1], encoding="utf-8").read())
    script = open(sys.argv[2], encoding="utf-8").read()
    errs = problems(cfg, script)
    for err in errs:
        print(f"config: {err}")
    sys.exit(1 if errs else 0)
