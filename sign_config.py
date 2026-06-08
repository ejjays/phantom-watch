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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", type=int, required=True)
    ap.add_argument("--key", default=os.path.expanduser("~/.phantomwatch/ota-key.pem"))
    a = ap.parse_args()
    cfg = json.loads(CFG.read_text())
    cfg["version"] = a.version
    cfg["script"] = JS.read_text()
    payload = json.dumps(cfg, separators=(",", ":"), ensure_ascii=False).encode()
    key = serialization.load_pem_private_key(pathlib.Path(a.key).read_bytes(), None)
    sig = key.sign(payload, ec.ECDSA(hashes.SHA256()))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "payload": base64.b64encode(payload).decode(),
        "sig": base64.b64encode(sig).decode(),
    }, indent=1) + "\n")
    print(f"v{a.version} payload={len(payload)}B js={len(cfg['script'])}B -> {OUT}")


if __name__ == "__main__":
    main()
