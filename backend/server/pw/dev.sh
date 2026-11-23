#!/usr/bin/env bash
# resolve and proxy relay on device, wasm bakes PW_URL=127.0.0.1:8787 so web needs this up
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p assets
for f in providers.js providers.json; do
  cp -u "../../../app/src/main/assets/$f" "assets/$f"
done
export PW_ASSETS="$PWD/assets"
export PW_KEY="${PW_KEY:-$(sed -n 's/^PW_KEY=//p' ../../../local.properties)}"
export PW_SECRET="${PW_SECRET:-$(head -c 24 /dev/urandom | base64 | tr -d '/+=')}"
export TMDB_KEY="${TMDB_KEY:-$(sed -n 's/^TMDB_API_KEY=//p' ../../../local.properties)}"
export TELEMETRY_URL="${TELEMETRY_URL:-$(sed -n 's/^TELEMETRY_URL=//p' ../../../local.properties)}"
export TELEMETRY_KEY="${TELEMETRY_KEY:-$(sed -n 's/^TELEMETRY_KEY=//p' ../../../local.properties)}"
exec node index.js
