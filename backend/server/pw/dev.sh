#!/usr/bin/env bash
# Run the resolve+proxy relay on this device. The wasm build bakes PW_URL=127.0.0.1:8787,
# so the web app has nothing to play until this is up.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p assets
for f in providers.js providers.json; do
  cp -u "../../app/src/main/assets/$f" "assets/$f"
done
export PW_ASSETS="$PWD/assets"
export PW_KEY="${PW_KEY:-devkey}"
export PW_SECRET="${PW_SECRET:-$(head -c 24 /dev/urandom | base64 | tr -d '/+=')}"
export TMDB_KEY="${TMDB_KEY:-$(sed -n 's/^TMDB_API_KEY=//p' ../../local.properties)}"
export TELEMETRY_URL="${TELEMETRY_URL:-$(sed -n 's/^TELEMETRY_URL=//p' ../../local.properties)}"
export TELEMETRY_KEY="${TELEMETRY_KEY:-$(sed -n 's/^TELEMETRY_KEY=//p' ../../local.properties)}"
exec node index.js
