'use strict';
// PhantomWatch web backend: resolves a title to playable formats and relays the
// bytes with the headers the browser is not allowed to send (Referer/UA).
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { Readable } = require('node:stream');
const bridge = require('./bridge');
const { rewrite } = require('./playlist');

const PORT = Number(process.env.PORT || 8787);
const KEY = process.env.PW_KEY || '';
const SECRET = process.env.PW_SECRET || '';
const TMDB = process.env.TMDB_KEY || '';
const TELEMETRY = process.env.TELEMETRY_URL || '';
const TELEMETRY_KEY = process.env.TELEMETRY_KEY || '';

// The app keeps this map on the device (Torrents.DeadHosts). A relay without it re-tries a CDN
// that just started 403ing on every single resolve.
const DEAD = new Map();
const BAN_MS = 30 * 60 * 1000;
const hostOf = (u) => { try { return new URL(u).host; } catch (e) { return ''; } };
const banned = (f) => { const t = DEAD.get(hostOf(f.url)); return t != null && t > Date.now(); };
const ban = (f) => { const h = hostOf(f.url); if (h) DEAD.set(h, Date.now() + BAN_MS); };
const forgive = (f) => { DEAD.delete(hostOf(f.url)); };

if (!SECRET) { console.error('PW_SECRET missing'); process.exit(1); }

const DIR = process.env.PW_ASSETS || path.join(__dirname, 'assets');
const cfg = JSON.parse(fs.readFileSync(path.join(DIR, 'providers.json'), 'utf8'));
const pw = bridge.init(fs.readFileSync(path.join(DIR, 'providers.js'), 'utf8'));
const LEGS = Object.keys(cfg.legs || {}).filter((l) => cfg.legs[l].enabled !== false);

const b64 = (s) => Buffer.from(s).toString('base64url');
const unb64 = (s) => Buffer.from(s, 'base64url').toString();
const sig = (u, h) => crypto.createHmac('sha256', SECRET).update(u + '\n' + h).digest('base64url').slice(0, 22);

function proxyUrl(base, url, headers) {
  const h = headers && Object.keys(headers).length ? b64(JSON.stringify(headers)) : '';
  return `${base}/p?u=${b64(url)}${h ? '&h=' + h : ''}&s=${sig(url, h)}`;
}

function baseOf(req) {
  const proto = req.headers['x-forwarded-proto'] || 'http';
  const host = req.headers['x-forwarded-host'] || req.headers.host;
  return `${proto}://${host}`;
}

async function tmdbMeta(kind, id) {
  if (!TMDB) return { title: '', imdb: '', isAnim: null };
  const ep = kind === 'movie' ? 'movie' : 'tv';
  const get = async (u) => {
    try {
      const r = await fetch(u, { signal: AbortSignal.timeout(8000) });
      return r.ok ? await r.json() : null;
    } catch (e) { return null; }
  };
  const [a, b] = await Promise.all([
    get(`https://api.themoviedb.org/3/${ep}/${id}?api_key=${TMDB}`),
    get(`https://api.themoviedb.org/3/${ep}/${id}/external_ids?api_key=${TMDB}`),
  ]);
  const title = ((a && (a.name || a.title)) || '').split(':')[0].trim();
  const isAnim = ((a && a.genres) || []).some((g) => g.id === 16);
  return { title, imdb: (b && b.imdb_id) || '', isAnim };
}

function headersFor(f) {
  const h = Object.assign({}, f.headers || {});
  if (Object.keys(h).length) return h;
  if (f.referer) {
    h.referer = f.referer;
    try { h.origin = new URL(f.referer).origin; } catch (e) {}
  }
  return h;
}

function subHeaders(sub, pick) {
  const h = Object.assign({}, (pick && pick.headers) || {});
  if (Object.keys(h).length) return h;
  if (pick && pick.referer) {
    h.referer = pick.referer;
    try { h.origin = new URL(pick.referer).origin; } catch (e) {}
  }
  return h;
}

async function runOne(leg, job, info) {
  if (pw.skip(leg, { kind: job.kind, season: job.season, title: info.title, imdb: info.imdb, isAnim: info.isAnim })) {
    return { leg, skipped: true, formats: [], subs: [], ms: 0 };
  }
  const args = {
    tmdbId: job.tmdbId,
    kind: job.kind,
    season: job.season,
    episode: job.episode,
    title: info.title,
    tmdbKey: TMDB,
    tmdbBase: (cfg.services && cfg.services.tmdb && cfg.services.tmdb.base) || 'https://api.themoviedb.org/3',
    cfg: cfg.legs[leg],
  };
  if (leg === 'vap') args.imdb = info.imdb;
  const t0 = Date.now();
  const { r, err } = await pw.runLeg(leg, args);
  const ms = Date.now() - t0;
  if (err) return { leg, ok: false, formats: [], subs: [], ms, err: 'js ' + err };
  if (r && r.skipped) return { leg, skipped: true, formats: [], subs: [], ms };
  const formats = (r && r.formats) || [];
  const subs = (r && r.subs) || [];
  return { leg, ok: formats.length > 0, formats, subs, ms };
}

async function alive(f) {
  const headers = { 'user-agent': bridge.UA, 'accept-encoding': 'identity' };
  for (const k of Object.keys(headersFor(f))) headers[k.toLowerCase()] = headersFor(f)[k];
  try {
    if (f.isHls) {
      const r = await fetch(f.url, { headers, redirect: 'follow', signal: AbortSignal.timeout(5000) });
      if (!r.ok) { await r.body?.cancel().catch(() => {}); return false; }
      const t = await r.text();
      return t.startsWith('#EXTM3U');
    }
    const r = await fetch(f.url, { headers: Object.assign({ range: 'bytes=0-0' }, headers), redirect: 'follow', signal: AbortSignal.timeout(5000) });
    await r.body?.cancel().catch(() => {});
    return r.status >= 200 && r.status < 400;
  } catch (e) { return false; }
}

async function handleResolve(req, res, base) {
  let body;
  try { body = JSON.parse(await readBody(req)); } catch (e) { return json(res, 400, { error: 'bad json' }); }
  const kind = body.kind === 'tv' ? 'tv' : 'movie';
  const id = String(body.id || '');
  if (!/^\d+$/.test(id)) return json(res, 400, { error: 'bad id' });
  const job = { kind, tmdbId: id, season: String(body.season || ''), episode: String(body.episode || '') };

  const t0 = Date.now();
  const info = await tmdbMeta(kind, id);
  const legs = await Promise.all(LEGS.map((l) => runOne(l, job, info)));

  const all = [];
  const subs = [];
  const stats = {};
  for (const r of legs) {
    if (r.skipped) { stats[r.leg] = { skip: true }; continue; }
    stats[r.leg] = { ok: r.ok, formats: r.formats.length, ms: r.ms };
    if (r.err) stats[r.leg].err = r.err;
    for (const f of r.formats) all.push(Object.assign({}, f, { src: r.leg }));
    for (const s of r.subs) subs.push(Object.assign({}, s, { src: r.leg }));
  }

  const ranked = pw.rank(all).filter((f) => f && f.url);

  // One probe per host, best-ranked first: clears a stale ban, or sets a fresh one. The app gets
  // this signal from playback failures; the relay has no such feedback, so the probe is all it has.
  const probed = new Set();
  const heads = [];
  for (const f of ranked) {
    const h = hostOf(f.url);
    if (!h || probed.has(h)) continue;
    if (probed.size >= 3) break;
    probed.add(h);
    heads.push(f);
  }
  await Promise.all(heads.map(async (f) => {
    if (await alive(f)) { forgive(f); return; }
    ban(f);
    stats[f.src] = Object.assign({}, stats[f.src], { dead: true });
  }));

  // Banned hosts sink to the back of the fallback chain, like the app's rankLast.
  const ordered = ranked.filter((f) => !banned(f)).concat(ranked.filter(banned));

  const pick = ordered[0] || null;
  const wire = (f) => ({
    url: proxyUrl(base, f.url, headersFor(f)),
    h: Number.isFinite(f.height) ? f.height : 0,
    label: f.label || '',
    hls: !!f.isHls,
    src: f.src,
    enSubs: !!f.enSubs,
  });
  const usable = pick ? subs.filter((s) => !s.src || String(pick.label || '').startsWith(s.src) || s.src === pick.src) : subs;

  // Same event probe.js posts, so /health and the alerts see web traffic too.
  if (TELEMETRY) {
    const tel = {};
    for (const r of legs) {
      if (r.skipped) continue;
      const maxH = r.formats.reduce((a, f) => Math.max(a, f.height || 0), 0);
      const en = r.subs.some((s) => /english/i.test(s.label || ''));
      tel[r.leg] = [r.formats.length, r.ms, maxH, en ? 1 : 0];
    }
    fetch(TELEMETRY, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        k: TELEMETRY_KEY, src: 'web', v: 'web', cfg: cfg.version || 0,
        kind: job.kind, id: job.tmdbId, s: job.season, e: job.episode,
        ms: Date.now() - t0, f: ordered.length, pick: pick ? pick.src : '', legs: tel,
        h: pick && Number.isFinite(pick.height) ? pick.height : 0,
        hls: pick && pick.isHls ? 1 : 0,
        ens: pick && pick.enSubs ? 1 : 0,
      }),
      signal: AbortSignal.timeout(5000),
    }).catch(() => {});
  }

  return json(res, 200, {
    cfg: cfg.version || 0,
    pick: pick ? wire(pick) : null,
    formats: ordered.slice(0, 12).map(wire),
    subs: usable.map((s) => ({ label: s.label || 'Subtitles', url: proxyUrl(base, s.url, subHeaders(s, pick)), src: s.src })),
    legs: stats,
    ms: Date.now() - t0,
  });
}

function upstreamHeaders(extra, range) {
  const h = { 'user-agent': bridge.UA, 'accept-encoding': 'identity' };
  for (const k of Object.keys(extra)) h[k.toLowerCase()] = extra[k];
  if (range) h.range = range;
  return h;
}

async function handleProxy(req, res, q) {
  const u = q.get('u') || '';
  const hRaw = q.get('h') || '';
  const url = unb64(u);
  if (!url || sig(url, hRaw) !== q.get('s')) return text(res, 403, 'bad signature');
  let extra = {};
  if (hRaw) { try { extra = JSON.parse(unb64(hRaw)); } catch (e) { return text(res, 400, 'bad headers'); } }

  let up;
  try {
    up = await fetch(url, {
      headers: upstreamHeaders(extra, req.headers.range),
      redirect: 'follow',
      method: req.method === 'HEAD' ? 'HEAD' : 'GET',
      signal: AbortSignal.timeout(60000),
    });
  } catch (e) {
    return text(res, 502, 'upstream ' + (e.name || 'ERR'));
  }

  const ct = up.headers.get('content-type') || '';
  const isList = /mpegurl/i.test(ct) || /\.m3u8(\?|$)/i.test(up.url || url);

  if (isList) {
    const body = await up.text();
    const out = body.startsWith('#EXTM3U') ? rewrite(body, up.url || url, (abs) => proxyUrl(baseOf(req), abs, extra)) : body;
    res.writeHead(up.status === 200 ? 200 : up.status, {
      'content-type': 'application/vnd.apple.mpegurl',
      'cache-control': 'no-cache',
      'access-control-allow-origin': '*',
    });
    return res.end(req.method === 'HEAD' ? undefined : out);
  }

  const h = {
    'content-type': ct || 'application/octet-stream',
    'access-control-allow-origin': '*',
    'access-control-expose-headers': 'content-length, content-range, accept-ranges',
    'cache-control': 'public, max-age=300',
  };
  for (const k of ['content-length', 'content-range', 'accept-ranges', 'etag', 'last-modified']) {
    const v = up.headers.get(k);
    if (v) h[k] = v;
  }
  res.writeHead(up.status, h);
  if (!up.body || req.method === 'HEAD') return res.end();
  Readable.fromWeb(up.body).on('error', () => res.destroy()).pipe(res);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let n = 0;
    req.on('data', (c) => { n += c.length; if (n > 65536) { reject(new Error('too big')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks).toString()));
    req.on('error', reject);
  });
}

function json(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(body),
    'access-control-allow-origin': '*',
    'cache-control': 'no-store',
  });
  res.end(body);
}

function text(res, code, msg) {
  res.writeHead(code, { 'content-type': 'text/plain', 'access-control-allow-origin': '*' });
  res.end(msg);
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET, HEAD, POST, OPTIONS',
      'access-control-allow-headers': 'content-type, x-pw-key, range',
      'access-control-max-age': '86400',
    });
    return res.end();
  }
  try {
    if (u.pathname === '/health') return json(res, 200, { ok: true, up: Math.round(process.uptime()), legs: LEGS });
    if (u.pathname === '/resolve') {
      if (req.method !== 'POST') return text(res, 405, 'POST');
      if (KEY && req.headers['x-pw-key'] !== KEY) return text(res, 401, 'key');
      return await handleResolve(req, res, baseOf(req));
    }
    if (u.pathname === '/p') return await handleProxy(req, res, u.searchParams);
    return text(res, 404, 'not found');
  } catch (e) {
    console.error(new Date().toISOString(), u.pathname, e && e.stack || e);
    if (!res.headersSent) text(res, 500, 'error');
    else res.destroy();
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`pw listening on 127.0.0.1:${PORT} legs=${LEGS.join(',')} tmdb=${TMDB ? 'yes' : 'no'}`);
});
