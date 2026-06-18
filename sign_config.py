#!/usr/bin/env python3
"""Wrap providers.json + providers.js into the signed OTA envelope.
Usage: python3 sign_config.py --version N
Writes build/pages-config/providers.json (deploy that dir, never the repo)."""
import argparse, base64, json, os, pathlib
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

ROOT = pathlib.Path(__file__).resolve().parent
CFG = ROOT / "app/src/main/assets/providers.json"
JS = ROOT / "app/src/main/assets/providers.js"
OUT = ROOT / "build/pages-config/providers.json"
PUB = ROOT / "app/src/main/assets/ota_pub.pem"


def tmdb_key():
    lp = ROOT / "local.properties"
    if not lp.exists():
        return ""
    for line in lp.read_text().splitlines():
        if line.startswith("TMDB_API_KEY="):
            return line.split("=", 1)[1].strip()
    return ""


def validate(cfg, script):
    legs = cfg.get("legs")
    if not isinstance(legs, dict) or not legs:
        raise SystemExit("config: no legs")
    enabled = []
    for name, leg in legs.items():
        if not isinstance(leg, dict):
            raise SystemExit(f"config: leg {name} is not an object")
        on = leg.get("enabled", True)
        if not isinstance(on, bool):
            raise SystemExit(f"config: leg {name} enabled is not bool")
        enabled.append(on)
    if not any(enabled):
        raise SystemExit("config: no enabled legs")
    if not script.strip():
        raise SystemExit("config: empty script")
    services = cfg.get("services")
    if services is not None and not isinstance(services, dict):
        raise SystemExit("config: services is not an object")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", type=int, required=True)
    ap.add_argument("--key", default=os.path.expanduser("~/.phantomwatch/ota-key.pem"))
    ap.add_argument("--only", help="comma-separated legs to keep enabled in this payload")
    a = ap.parse_args()
    cfg = json.loads(CFG.read_text())
    cfg["version"] = a.version
    cfg["script"] = JS.read_text()
    if a.only:
        keep = {x.strip() for x in a.only.split(",") if x.strip()}
        unknown = keep - set(cfg.get("legs", {}))
        if unknown:
            raise SystemExit("config: unknown --only legs " + ", ".join(sorted(unknown)))
        for name, leg in cfg.get("legs", {}).items():
            leg["enabled"] = name in keep
    validate(cfg, cfg["script"])
    tmdb = tmdb_key()
    if tmdb:
        cfg.setdefault("services", {}).setdefault("tmdb", {})["key"] = tmdb
    payload = json.dumps(cfg, separators=(",", ":"), ensure_ascii=False).encode()
    key = serialization.load_pem_private_key(pathlib.Path(a.key).read_bytes(), None)
    sig = key.sign(payload, ec.ECDSA(hashes.SHA256()))
    pub = serialization.load_pem_public_key(PUB.read_bytes())
    pub.verify(sig, payload, ec.ECDSA(hashes.SHA256()))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "payload": base64.b64encode(payload).decode(),
        "sig": base64.b64encode(sig).decode(),
    }, indent=1) + "\n")
    print(f"v{a.version} payload={len(payload)}B js={len(cfg['script'])}B tmdb_key={'cfg' if tmdb else 'apk'} sig=ok -> {OUT}")


if __name__ == "__main__":
    main()
