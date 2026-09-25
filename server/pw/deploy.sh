#!/usr/bin/env bash
# Push the resolver+proxy (and optionally the wasm build) to the Oracle box.
#   ./server/pw/deploy.sh        server only
#   ./server/pw/deploy.sh web    server + /opt/pw/web
set -euo pipefail

HOST="${PW_HOST:-ubuntu@158.178.228.161}"
KEY="${PW_SSH:-$HOME/.ssh/oci_bastion}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SSH=(ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$HOST")

tar czf - -C "$ROOT/server/pw" --exclude assets . | "${SSH[@]}" 'tar xzf - -C /opt/pw'
tar czf - -C "$ROOT/app/src/main/assets" providers.js providers.json | "${SSH[@]}" 'tar xzf - -C /opt/pw/assets'

if [ "${1:-}" = "web" ]; then
  tar czf - -C "$ROOT/spike/web" . | "${SSH[@]}" 'tar xzf - -C /opt/pw/web'
fi

"${SSH[@]}" 'sudo systemctl restart pw-proxy && sleep 1 && curl -s http://127.0.0.1:8787/health'
echo
