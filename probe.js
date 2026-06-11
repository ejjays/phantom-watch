#!/usr/bin/env node
// Live provider health check running the shipped providers.js. Metadata only.
// Usage: node probe.js  (exit 1 if any provider fails)
const fs = require('fs');
const path = require('path');
const { AsyncLocalStorage } = require('node:async_hooks');

const ROOT = __dirname;
const cfg = JSON.parse(fs.readFileSync(path.join(ROOT, 'app/src/main/assets/providers.json'), 'utf8'));
const UA = cfg.ua || 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36';
const BASE = path.join(ROOT, 'probe_last.json');
const MOVIES = ['1560520', '980431', '1108427', '969681'];
const TVS = [['127532', '1', '1'], ['62715', '1', '1']];
const LEGS = Object.keys(cfg.legs || {});
const KEY = tmdbKey();
const als = new AsyncLocalStorage();

function tmdbKey() {
  try {
    const m = fs.readFileSync(path.join(ROOT, 'local.properties'), 'utf8').match(/^TMDB_API_KEY=(.+)$/m);
    return m ? m[1].trim() : '';
  } catch (e) {
    return '';
  }
}

function request(req) {
  const headers = { 'user-agent': UA };
  if (req.referer) headers.referer = req.referer;
  const init = {
    headers,
    redirect: 'follow',
    signal: AbortSignal.timeout(Math.min(20, Math.max(1, req.timeout || 10)) * 1000),
  };
  if ((req.method || 'GET') === 'POST') {
    init.method = 'POST';
    init.body = req.body || '';
    headers['content-type'] = 'application/json';
  }
  return init;
}

async function get(url, referer, timeout, range) {
  try {
    const headers = { 'user-agent': UA };
    if (referer) headers.referer = referer;
    if (range) headers.range = range;
    const r = await fetch(url, { headers, redirect: 'follow', signal: AbortSignal.timeout((timeout || 10) * 1000) });
    const body = range ? '' : await r.text();
    if (range && r.body) await r.body.cancel().catch(() => {});
    return { code: r.status, body };
  } catch (e) {
    return { code: 0, body: '', error: e.name || 'ERR' };
  }
}

async function getJson(url, referer) {
  const r = await get(url, referer, 15);
  try {
    return r.code === 200 ? JSON.parse(r.body) : null;
  } catch (e) {
    return null;
  }
}

globalThis.window = globalThis;
const pending = new Map();
let seq = 0;

globalThis.pw = {
  http(id, json) {
    const req = JSON.parse(json);
    const trace = als.getStore();
    fetch(req.url, request(req))
      .then(async (r) => {
        const body = await r.text();
        if (trace) trace.push({ code: r.status, url: req.url });
        window.__pwBridge.httpDone(id, JSON.stringify({ code: r.status, body }));
      })
      .catch((e) => {
        if (trace) trace.push({ code: 0, url: req.url, error: e.name || 'ERR' });
        window.__pwBridge.httpDone(id, JSON.stringify({ error: e.name || 'ERR' }));
      });
  },
  log() {},
  done(id, out) {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    let v = null;
    try { v = JSON.parse(out); } catch (e) {}
    p.ok(v);
  },
  fail(id, msg) {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    p.bad(msg);
  },
};

new Function(fs.readFileSync(path.join(ROOT, 'app/src/main/assets/providers.js'), 'utf8'))();

function runLeg(leg, args) {
  const id = 'p' + ++seq;
  const trace = [];
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      pending.delete(id);
      resolve({ r: null, trace, err: 'timeout' });
    }, 20000);
    pending.set(id, {
      ok: (r) => { clearTimeout(timer); resolve({ r, trace }); },
      bad: (e) => { clearTimeout(timer); resolve({ r: null, trace, err: e }); },
    });
    als.run(trace, () => window.__pwRun(id, leg, JSON.stringify(args)));
  });
}

function heights(text) {
  const out = new Set();
  for (const m of (text || '').matchAll(/RESOLUTION=\d+x(\d+)/g)) out.add(parseInt(m[1], 10));
  return [...out];
}

function enMedia(text) {
  return ((text || '').match(/#EXT-X-MEDIA:[^\r\n]*/g) || []).some((m) => /TYPE=SUBTITLES/.test(m) && /LANGUAGE="?en/.test(m));
}

function note(trace) {
  const bad = trace.find((x) => x.error || x.code < 200 || x.code >= 300);
  if (!bad) return '';
  let host = '';
  try { host = new URL(bad.url).host; } catch (e) {}
  return bad.error ? ` ${bad.error} ${host}` : ` http=${bad.code} ${host}`;
}

async function check(r) {
  const formats = (r && r.formats) || [];
  const subs = (r && r.subs) || [];
  let en = formats.some((f) => f.enSubs) || subs.some((s) => /english/i.test(s.label || ''));
  const heightsFmt = formats.map((f) => f.height || 0);
  const maxFmt = heightsFmt.length ? Math.max(...heightsFmt) : 0;
  if (!formats.length) return { ok: false, formats: 0, max: null, enSubs: en, err: 'empty' };
  const top = formats.reduce((a, b) => ((b.height || 0) > (a.height || 0) ? b : a));
  if (top.isHls) {
    const res = await get(top.url, top.referer, 12);
    if (res.code !== 200) return { ok: false, formats: formats.length, max: maxFmt || null, enSubs: en, err: `manifest http=${res.code || res.error}` };
    const rungs = heights(res.body);
    const ok = rungs.length > 0 || /#EXTINF/.test(res.body);
    return {
      ok,
      formats: formats.length,
      max: rungs.length ? Math.max(...rungs) : maxFmt || null,
      enSubs: en || enMedia(res.body),
      err: ok ? '' : 'no rungs',
    };
  }
  const res = await get(top.url, top.referer, 10, 'bytes=0-0');
  const ok = res.code >= 200 && res.code < 300;
  return { ok, formats: formats.length, max: maxFmt || null, enSubs: en, err: ok ? '' : `cdn http=${res.code || res.error}` };
}

function skipFor(leg, job, info) {
  if ((cfg.legs[leg] || {}).enabled === false) return 'disabled';
  if (leg === 'vap' && !info.imdb) return 'no imdb';
  if (leg === 'hianime' && !KEY) return 'no TMDB key';
  if (leg === 'paradise' && (job.kind !== 'tv' || job.season !== '1' || !info.title)) return 'tv-s1 only';
  if (leg === 'hianime' && job.kind !== 'tv') return 'tv only';
  return '';
}

async function run(leg, job, info) {
  const t0 = Date.now();
  const skip = skipFor(leg, job, info);
  if (skip) return { provider: leg, ok: true, skip, formats: 0, max: null, enSubs: false, ms: 0, err: '' };
  const args = {
    tmdbId: job.tmdbId,
    kind: job.kind,
    season: job.season,
    episode: job.episode,
    title: info.title,
    tmdbKey: KEY,
    cfg: cfg.legs[leg],
  };
  if (leg === 'vap') args.imdb = info.imdb;
  const { r, trace, err } = await runLeg(leg, args);
  const ms = Date.now() - t0;
  if (err) return { provider: leg, ok: false, formats: 0, max: null, enSubs: false, ms, err: 'js ' + err + note(trace) };
  const c = await check(r);
  return { provider: leg, ok: c.ok, formats: c.formats, max: c.max, enSubs: c.enSubs, ms, err: c.err + (c.ok ? '' : note(trace)) };
}

async function meta(job) {
  if (!KEY) return { title: '', imdb: '' };
  const ep = job.kind === 'movie' ? 'movie' : 'tv';
  const [a, b] = await Promise.all([
    getJson(`https://api.themoviedb.org/3/${ep}/${job.tmdbId}?api_key=${KEY}`, 'https://www.themoviedb.org/'),
    getJson(`https://api.themoviedb.org/3/${ep}/${job.tmdbId}/external_ids?api_key=${KEY}`, 'https://www.themoviedb.org/'),
  ]);
  const title = ((a && (a.name || a.title)) || '').split(':')[0].trim();
  return { title, imdb: (b && b.imdb_id) || '' };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  if (!KEY) console.log('warn: no TMDB_API_KEY in local.properties, vap/paradise/hianime skipped');
  let prev = {};
  try { prev = JSON.parse(fs.readFileSync(BASE, 'utf8')); } catch (e) {}
  const jobs = MOVIES.map((t) => ({ kind: 'movie', tmdbId: t, season: '', episode: '' }))
    .concat(TVS.map(([t, s, e]) => ({ kind: 'tv', tmdbId: t, season: s, episode: e })));
  const rows = [];
  const cur = {};
  let fails = 0;
  for (const job of jobs) {
    const info = await meta(job);
    const got = await Promise.all(LEGS.map((leg) => run(leg, job, info)));
    for (let i = 0; i < got.length; i++) {
      let r = got[i];
      if (!r.ok && !r.skip) {
        await sleep(5000);
        r = await run(LEGS[i], job, info);
        if (r.ok) r.retry = true;
      }
      r.title = `${job.kind}/${job.tmdbId}` + (job.kind === 'tv' ? ` s${job.season}e${job.episode}` : '');
      rows.push(r);
      const k = `${r.provider}:${r.title}`;
      cur[k] = { ok: r.ok, formats: r.formats, max: r.max, enSubs: r.enSubs };
      const p = prev[k];
      if (r.skip) r.flag = 'skip';
      else if (!p) r.flag = '';
      else if (p.ok !== r.ok) r.flag = r.ok ? 'recovered' : 'REGRESSED';
      else if (p.enSubs && !r.enSubs) r.flag = 'en lost';
      else r.flag = '';
      if (!r.ok && !r.skip) fails++;
    }
  }
  console.log(`${'provider'.padEnd(9)} ${'title'.padEnd(16)} ${'ms'.padStart(6)} ${'fmts'.padStart(4)} ${'max'.padStart(5)} en ${'st'.padEnd(6)} note`);
  for (const r of rows) {
    const mx = r.max ? `${r.max}p` : '-';
    const st = r.skip ? 'skip' : r.ok ? 'ok' : 'FAIL';
    const noteText = [r.flag, r.skip, r.err, r.retry ? 'retry' : ''].filter(Boolean).join(' ');
    console.log(`${r.provider.padEnd(9)} ${r.title.padEnd(16)} ${String(r.ms).padStart(6)} ${String(r.formats).padStart(4)} ${mx.padStart(5)} ${r.enSubs ? 'Y' : '-'} ${st.padEnd(6)} ${noteText}`);
  }
  fs.writeFileSync(BASE, JSON.stringify(cur, null, 1));
  console.log(`\n${rows.length - fails}/${rows.length} ok` + (Object.keys(prev).length ? '' : ' — baseline updated'));
  process.exit(fails ? 1 : 0);
}

main();
