const HOUR = 3600000;
const JSON_H = { 'content-type': 'application/json' };
// anime-only legs are empty by design on live-action titles, so they need more evidence
const MIN_SAMPLES = { paradise: 10, hianime: 10 };
const DEF_SAMPLES = 5;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);

    if (req.method === 'POST' && url.pathname === '/e') {
      const b = await req.json().catch(() => null);
      if (!b || b.k !== env.KEY) return new Response(null, { status: 403 });
      await env.DB.prepare(
        'INSERT INTO event(ts,src,v,cfg,kind,tmdb,season,episode,ms,formats,pick,legs,h,ens,ena,hls,ev,startMs,stalls,stallMs,pos,err) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
      )
        .bind(
          Date.now(),
          str(b.src || 'app', 8),
          str(b.v, 16),
          b.cfg | 0,
          str(b.kind, 8),
          str(b.id, 16),
          str(b.s, 8),
          str(b.e, 8),
          b.ms | 0,
          b.f | 0,
          str(b.pick, 64),
          JSON.stringify(b.legs && typeof b.legs === 'object' ? b.legs : {}).slice(0, 2000),
          b.h | 0,
          b.ens ? 1 : 0,
          b.ena ? 1 : 0,
          b.hls ? 1 : 0,
          str(b.ev || 'resolve', 8),
          b.startMs | 0,
          b.stalls | 0,
          b.stallMs | 0,
          b.pos | 0,
          str(b.err, 120),
        )
        .run();
      return new Response(null, { status: 204 });
    }

    if (url.searchParams.get('k') !== env.KEY) return new Response(null, { status: 403 });

    if (url.pathname === '/test') {
      const r = await tg(env, 'phantomwatch · test alert');
      return new Response(r ? `sent ${r.status}` : 'no telegram secrets');
    }

    if (url.pathname === '/health' || url.pathname === '/events') {
      const days = Math.min(31, Math.max(1, Number(url.searchParams.get('days')) || 7));
      const limit = url.pathname === '/events' ? Math.min(500, Number(url.searchParams.get('limit')) || 100) : 5000;
      const { results } = await env.DB.prepare(
        `SELECT ts,src,v,cfg,kind,tmdb,season,episode,ms,formats,pick,legs,h,ens,ena,hls,ev,startMs,stalls,stallMs,pos,err FROM event WHERE ts>? ORDER BY ts DESC LIMIT ${limit}`,
      )
        .bind(Date.now() - days * 24 * HOUR)
        .all();
      if (url.pathname === '/events') return Response.json(results, { headers: JSON_H });
      return Response.json({ days, ...aggregate(results) }, { headers: JSON_H });
    }

    return new Response('phantomwatch telemetry', { status: 404 });
  },

  async scheduled(controller, env) {
    const now = controller.scheduledTime;
    const { results: rows } = await env.DB.prepare(
      'SELECT ts,kind,tmdb,season,episode,formats,legs,h,ens,ev,stalls,stallMs FROM event WHERE ts>? ORDER BY ts DESC LIMIT 5000',
    )
      .bind(now - 8 * 24 * HOUR)
      .all();

    if (new Date(now).getUTCHours() === Number(env.DIGEST_UTC_HOUR || 1)) {
      const day = rows.filter((r) => r.ts > now - 24 * HOUR);
      if (day.length) await tg(env, digest('24h', aggregate(day)));
    }

    const recent = rows.filter((r) => r.ts > now - 6 * HOUR);
    const base = rows.filter((r) => r.ts <= now - 6 * HOUR);
    if (recent.length < 5) return;

    const a = aggregate(recent);
    const b = aggregate(base);
    const down = [];
    for (const [leg, s] of Object.entries(a.legs)) {
      const was = b.legs[leg];
      const min = MIN_SAMPLES[leg] || DEF_SAMPLES;
      if (s.total < min || !was || was.total < min) continue;
      if (s.ok / s.total <= was.ok / was.total - 0.4) down.push(`${leg} ${s.ok}/${s.total} (7d ${was.ok}/${was.total})`);
    }
    if (a.resolves >= 8 && a.zero / a.resolves >= 0.5) down.push(`no source ${a.zero}/${a.resolves}`);

    const prev = JSON.parse((await env.DB.prepare('SELECT v FROM state WHERE k=?').bind('alerted').first('v')) || '[]');
    const next = down.map((d) => d.split(' ')[0]);
    if (next.join() === prev.join()) return;

    const recovered = prev.filter((l) => !next.includes(l));
    const text = [
      `phantomwatch · last 6h${down.length ? ' · providers down' : ''}`,
      `resolves ${a.resolves} · no source ${a.zero}`,
      ...down,
      ...(recovered.length ? [`recovered: ${recovered.join(', ')}`] : []),
    ].join('\n');
    await tg(env, text);
    await env.DB.prepare('INSERT INTO state(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v').bind('alerted', JSON.stringify(next)).run();
  },
};

function str(v, max) {
  return String(v ?? '').slice(0, max);
}

function aggregate(rows) {
  const legs = {};
  let zero = 0;
  let hd = 0;
  let en = 0;
  let plays = 0;
  let stalled = 0;
  let stallMs = 0;
  const noSource = [];
  for (const r of rows) {
    if (r.ev === 'play') {
      plays++;
      if (r.stalls > 0) stalled++;
      stallMs += r.stallMs || 0;
    } else {
      if (!r.formats) {
        zero++;
        if (noSource.length < 5) noSource.push(`${r.kind}/${r.tmdb} s${r.season}e${r.episode}`);
      }
      if (r.h >= 1080) hd++;
      if (r.ens) en++;
    }
    let parsed = {};
    try {
      parsed = JSON.parse(r.legs || '{}');
    } catch (e) {}
    for (const [name, val] of Object.entries(parsed)) {
      const s = legs[name] || (legs[name] = { ok: 0, total: 0, h: 0, en: 0 });
      s.total++;
      if (!Array.isArray(val)) {
        if (val > 0) s.ok++;
        continue;
      }
      if (val[0] > 0) s.ok++;
      if (val[2] > s.h) s.h = val[2];
      if (val[3]) s.en++;
    }
  }
  const ordered = Object.fromEntries(Object.entries(legs).sort(([x], [y]) => x.localeCompare(y)));
  return { resolves: rows.length - plays, zero, hd, en, plays, stalled, stallMs, legs: ordered, noSource };
}

function digest(label, a) {
  const legs = Object.entries(a.legs).map(([n, s]) => `${n} ${s.ok}/${s.total}` + (s.h > 0 ? ` ${s.h}` : '') + (s.en ? ` en${s.en}` : ''));
  return [
    `phantomwatch · ${label}`,
    `resolves ${a.resolves} · no source ${a.zero} · 1080+ ${a.hd} · en ${a.en}` + (a.plays ? ` · plays ${a.plays} · stalled ${a.stalled}` : ''),
    legs.join(' · ') || 'no legs reported',
    ...(a.noSource.length ? [`empty: ${a.noSource.join(', ')}`] : []),
  ].join('\n');
}

async function tg(env, text) {
  if (!env.TG_TOKEN || !env.TG_CHAT) return null;
  const r = await fetch(`https://api.telegram.org/bot${env.TG_TOKEN}/sendMessage`, {
    method: 'POST',
    headers: JSON_H,
    body: JSON.stringify({ chat_id: env.TG_CHAT, text, disable_notification: false }),
  });
  if (!r.ok) console.log('tg', r.status, (await r.text()).slice(0, 200));
  return r;
}
