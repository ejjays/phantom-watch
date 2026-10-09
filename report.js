#!/usr/bin/env node
// Provider health table from telemetry: one row per leg, verdict from a fixed rule.
// Read-only. Usage: node report.js [--days=7] [--limit=2000]
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const H = 3600000;

function localProp(name) {
  try {
    const m = fs.readFileSync(path.join(ROOT, 'local.properties'), 'utf8').match(new RegExp(`^${name}=(.+)$`, 'm'));
    return m ? m[1].trim() : '';
  } catch (e) {
    return '';
  }
}

function arg(name, def) {
  const hit = process.argv.slice(2).find((a) => a.startsWith(`--${name}=`));
  return hit ? Number(hit.split('=')[1]) || def : def;
}

function fmtHours(h) {
  if (h === Infinity) return 'never';
  return h >= 48 ? `${Math.round(h / 24)}d` : `${h.toFixed(1)}h`;
}

function pct(ok, n) {
  return n ? `${Math.round((ok / n) * 100)}%` : '-';
}

function cell(ok, n) {
  return `${pct(ok, n)} ${ok}/${n}`;
}

// Disabling needs 0 successes over 24h with >=8 samples AND no success for 48h (or never).
// A low window average alone is not evidence: an outage two days ago drags it down.
function verdict(a, now) {
  const since = a.last ? (now - a.last) / H : Infinity;
  if (a.ok === 0 && a.n >= 20) return `dead — 0/${a.n} in window, confirm host`;
  if (a.n24 >= 8 && a.ok24 === 0) {
    return since >= 48
      ? `dead — 0/${a.n24} in 24h, last ok ${fmtHours(since)}, confirm host`
      : `down ${fmtHours(since)} — recent, keep on`;
  }
  if (a.n24 === 0) return 'cannot tell — no samples in 24h';
  if (a.ok24 === 0) return `cannot tell — n=${a.n24} in 24h`;
  if (a.ok6 === 0 && a.n6 >= 2) return `watch — 0/${a.n6} in 6h`;
  if (a.ok / a.n + 0.25 <= a.ok24 / a.n24) return 'ok — window dragged down by an outage';
  if (a.ok24 / a.n24 + 0.25 <= a.ok / a.n) return 'ok — recent dip, watch';
  return 'ok';
}

const RANK = { dead: 0, down: 1, 'cannot tell': 2, watch: 3, ok: 4 };

async function main() {
  const days = arg('days', 7);
  const limit = arg('limit', 2000);
  const base = localProp('TELEMETRY_URL');
  const key = localProp('TELEMETRY_KEY');
  if (!base || !key) {
    console.error('TELEMETRY_URL / TELEMETRY_KEY missing in local.properties');
    process.exit(1);
  }
  const url = `${new URL(base).origin}/events?k=${encodeURIComponent(key)}&days=${days}&limit=${limit}`;
  const r = await fetch(url, { signal: AbortSignal.timeout(20000) });
  if (!r.ok) {
    console.error(`events ${r.status} ${r.statusText}`);
    process.exit(1);
  }
  const body = await r.json();
  const rows = Array.isArray(body) ? body : body.events || body.rows || [];
  if (!rows.length) {
    console.log(`no rows in the last ${days}d`);
    return;
  }

  const now = Date.now();
  const agg = {};
  for (const row of rows) {
    if (!row.legs || row.kind === 'play') continue;
    let legs;
    try {
      legs = typeof row.legs === 'string' ? JSON.parse(row.legs) : row.legs;
    } catch (e) {
      continue;
    }
    for (const leg of Object.keys(legs)) {
      const v = legs[leg] || [];
      const a = agg[leg] || (agg[leg] = { n: 0, ok: 0, n24: 0, ok24: 0, n6: 0, ok6: 0, last: 0, maxH: 0 });
      const ok = (v[0] || 0) > 0;
      const age = now - row.ts;
      a.n++;
      if (ok) a.ok++;
      if (age < 24 * H) {
        a.n24++;
        if (ok) a.ok24++;
      }
      if (age < 6 * H) {
        a.n6++;
        if (ok) a.ok6++;
      }
      if (ok) {
        if (row.ts > a.last) a.last = row.ts;
        a.maxH = Math.max(a.maxH, v[2] || 0);
      }
    }
  }

  const cfg = JSON.parse(fs.readFileSync(path.join(ROOT, 'app/src/main/assets/providers.json'), 'utf8'));
  const span = (now - Math.min(...rows.map((x) => x.ts))) / H;
  const out = Object.keys(agg).map((leg) => {
    const a = agg[leg];
    const v = verdict(a, now);
    return {
      leg,
      a,
      v,
      rank: RANK[v.split(' ')[0]] ?? 9,
      on: cfg.legs && cfg.legs[leg] ? (cfg.legs[leg].enabled === false ? 'off' : 'on') : 'gone',
    };
  });
  out.sort((x, y) => x.rank - y.rank || y.a.n - x.a.n);

  console.log(`# telemetry ${new Date(now).toISOString().replace(/\.\d+Z$/, 'Z')}  window=${span.toFixed(1)}h  rows=${rows.length}`);
  console.log(`# /events?days=${days}&limit=${limit} (key redacted) — verdicts are a prompt to confirm, never an auto-disable`);
  console.log('# bundle = app/src/main/assets/providers.json, which can be newer than the deployed config');
  console.log(`${'leg'.padEnd(10)}${'bundle'.padEnd(8)}${'6h'.padEnd(13)}${'24h'.padEnd(13)}${'window'.padEnd(14)}${'lastOk'.padEnd(8)}${'maxH'.padEnd(6)}verdict`);
  for (const row of out) {
    const { a } = row;
    console.log(
      row.leg.padEnd(10) +
        row.on.padEnd(8) +
        cell(a.ok6, a.n6).padEnd(13) +
        cell(a.ok24, a.n24).padEnd(13) +
        cell(a.ok, a.n).padEnd(14) +
        fmtHours(a.last ? (now - a.last) / H : Infinity).padEnd(8) +
        String(a.maxH).padEnd(6) +
        row.v
    );
  }
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
