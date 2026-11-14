// netflix publishes a per-country top 10 as server-rendered html; the app cannot
// carry ten tmdb lookups per home load, so they are resolved here and cached
const TMDB = 'https://api.themoviedb.org/3';
const UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36';
const TTL = 6 * 3600;
// bumped when the row shape changes: the edge cache outlives a deploy
const V = 4;
const JSON_H = { 'content-type': 'application/json' };

export async function handleChart(url) {
  const code = (url.searchParams.get('c') || '').toUpperCase();
  const kind = url.searchParams.get('kind') === 'tv' ? 'tv' : 'movie';
  const tmdb = url.searchParams.get('t') || '';
  if (!/^[A-Z]{2}$/.test(code)) return json({ error: 'country' }, 400);
  if (!tmdb) return json({ error: 'key' }, 400);

  const key = `https://chart.pw/${V}/${code}/${kind}`;
  const hit = await cache.match(key);
  if (hit) return new Response(hit.body, { headers: JSON_H });

  const slug = await slugOf(code);
  const html = await text(`https://www.netflix.com/tudum/top10/${slug}${kind === 'tv' ? '/tv' : ''}`);
  const rows = parseRows(html).slice(0, 10);
  const filled = await Promise.all(rows.map((r, i) => resolve(r, kind, tmdb).then((hit) => [i, hit])));
  // netflix rows are numbered by position, so dropping one renumbers the rest
  const results = filled.filter(([, hit]) => hit).map(([i, hit]) => ({ ...hit, rank: i + 1 }));
  const body = JSON.stringify({ c: code, kind, slug, at: Date.now(), results });
  // a country netflix does not chart must not pin an empty list for six hours
  if (results.length) {
    await cache.put(key, new Response(body, { headers: { ...JSON_H, 'cache-control': `max-age=${TTL}` } }));
  }
  return new Response(body, { headers: JSON_H });
}

export function parseRows(html) {
  const out = [];
  for (const m of html.matchAll(/class="rank">(\d+)<\/span>[\s\S]{0,600}?<button>([^<]+)<\/button>/g)) {
    out.push(decode(m[2]));
  }
  return out;
}

async function slugOf(code) {
  const key = 'https://chart.pw/countries';
  const hit = await cache.match(key);
  let map = hit ? await hit.json().catch(() => null) : null;
  if (!map) {
    const html = await text('https://www.netflix.com/tudum/top10');
    map = {};
    for (const m of html.matchAll(/\{"displayName":"([^"]+)","urlSegment":"([^"]+)"\}/g)) map[norm(m[1])] = m[2];
    await cache.put(key, new Response(JSON.stringify(map), { headers: { ...JSON_H, 'cache-control': 'max-age=86400' } }));
  }
  const name = new Intl.DisplayNames(['en'], { type: 'region' }).of(code) || '';
  return map[norm(name)] || norm(name).replace(/ /g, '-');
}

async function resolve(title, kind, tmdb) {
  // netflix hangs season and episode labels off show titles; the bare name is the better query
  const bare = kind === 'tv'
    ? title.replace(/\s*:\s*(Season\s+\d+|S\d+.*|Limited Series|Part\s+\d+)\s*$/i, '').trim()
    : title;
  const tries = kind === 'tv' ? [bare, title.replace(/:.*$/, ''), title] : [title];
  for (const q of new Set(tries)) {
    const hit = await search(q, kind, tmdb);
    if (hit) return hit;
  }
  return null;
}

async function search(q, kind, tmdb) {
  const r = await fetch(`${TMDB}/search/${kind}?api_key=${tmdb}&query=${encodeURIComponent(q)}`).catch(() => null);
  if (!r || !r.ok) return null;
  const d = await r.json().catch(() => null);
  const hit = d && Array.isArray(d.results) ? d.results[0] : null;
  if (!hit) return null;
  return {
    id: hit.id,
    media_type: kind,
    title: hit.title || hit.name,
    poster_path: hit.poster_path,
    backdrop_path: hit.backdrop_path,
    vote_average: hit.vote_average,
    release_date: hit.release_date || hit.first_air_date,
    overview: hit.overview,
  };
}

function norm(s) {
  return s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9 ]/g, '')
    .trim();
}

function decode(s) {
  return s
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#(?:x27|39);/g, "'")
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCharCode(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(+d))
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');
}

async function text(url) {
  const r = await fetch(url, { headers: { 'User-Agent': UA } }).catch(() => null);
  return r && r.ok ? r.text() : '';
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_H });
}

const cache = typeof caches === 'undefined' ? { match: async () => null, put: async () => {} } : caches.default;
