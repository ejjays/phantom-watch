#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# threshold is baseline, lower it as clones are removed
JSCPD="${JSCPD:-$HOME/tools/jscpd4/node_modules/jscpd/bin/jscpd}"
if [ ! -f "$JSCPD" ] && command -v jscpd >/dev/null 2>&1; then
  JSCPD="$(command -v jscpd)"
fi
if [ ! -f "$JSCPD" ]; then
  echo "npm install -g jscpd@4.3.0, or set JSCPD to its bin/jscpd path" >&2
  exit 2
fi

node "$JSCPD" \
  --min-lines 12 --min-tokens 70 \
  --ignore '**/build/**' --ignore '**/node_modules/**' --ignore '**/.gradle/**' \
  --threshold 4.76 \
  --reporters console --noTips .
