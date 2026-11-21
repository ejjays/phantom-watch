import { rewrite } from './playlist.js';

const enc = new TextEncoder();
const dec = new TextDecoder();
const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';

const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, HEAD, POST, OPTIONS',
  'access-control-allow-headers': 'content-type, x-pw-key, range',
  'access-control-max-age': '86400',
};

function b64(s) {
  let bin = '';
  for (const b of enc.encode(s)) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function unb64(s) {
  let t = String(s).replace(/-/g, '+').replace(/_/g, '/');
  while (t.length % 4) t += '=';
  return dec.decode(Uint8Array.from(atob(t), (c) => c.charCodeAt(0)));
}

async function sig(secret, u, h) {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const out = await crypto.subtle.sign('HMAC', key, enc.encode(u + '\n' + h));
  let bin = '';
  for (const b of new Uint8Array(out)) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '').slice(0, 22);
}

async function proxyUrl(base, url, headers, secret) {
  const h = headers && Object.keys(headers).length ? b64(JSON.stringify(headers)) : '';
  return `${base}/p?u=${b64(url)}${h ? '&h=' + h : ''}&s=${await sig(secret, url, h)}`;
}

function upstreamHeaders(extra, range) {
  const h = { 'user-agent': UA, 'accept-encoding': 'identity' };
  for (const k of Object.keys(extra)) h[k.toLowerCase()] = extra[k];
  if (range) h.range = range;
  return h;
}

async function json(data, status = 200) {
  const body = JSON.stringify(data);
  return new Response(body, {
    status,
    headers: { ...CORS, 'content-type': 'application/json', 'content-length': String(body.length), 'cache-control': 'no-store' },
  });
}

function text(msg, status) {
  return new Response(msg, { status, headers: { ...CORS, 'content-type': 'text/plain' } });
}

async function forward(req, url, env, extraHeaders = {}) {
  const headers = { ...extraHeaders };
  for (const k of ['content-type', 'x-pw-key', 'range', 'x-forwarded-for']) {
    const v = req.headers.get(k);
    if (v) headers[k] = v;
  }
  let r;
  try {
    r = await fetch(url, { method: req.method, headers, body: req.method === 'GET' || req.method === 'HEAD' ? undefined : await req.text(), redirect: 'follow' });
  } catch (e) {
    return text('upstream ' + (e && e.name || 'ERR'), 502);
  }
  return new Response(r.body, {
    status: r.status,
    headers: { ...CORS, 'content-type': r.headers.get('content-type') || 'application/json', 'cache-control': 'no-store' },
  });
}

async function viaOracle(req, url, env) {
  const range = req.headers.get('range');
  return fetch(env.ORACLE_URL + '/p' + url.search, {
    method: req.method === 'HEAD' ? 'HEAD' : 'GET',
    headers: range ? { range } : {},
    redirect: 'follow',
  });
}

async function handleProxy(req, url, env) {
  const q = url.searchParams;
  const u = q.get('u') || '';
  const hRaw = q.get('h') || '';
  let upstream, extra;
  try {
    upstream = unb64(u);
    extra = hRaw ? JSON.parse(unb64(hRaw)) : {};
  } catch (e) {
    return text('bad url', 400);
  }
  if (!upstream || await sig(env.PW_SECRET, upstream, hRaw) !== q.get('s')) return text('bad signature', 403);

  let up;
  try {
    up = await fetch(upstream, {
      headers: upstreamHeaders(extra, req.headers.get('range')),
      redirect: 'follow',
      method: req.method === 'HEAD' ? 'HEAD' : 'GET',
    });
  } catch (e) {
    return viaOracle(req, url, env);
  }
  // some cdns refuse cloudflare egress but allow the oracle box
  // the signature is host agnostic
  if (up.status === 403 || up.status === 427) {
    await up.body?.cancel().catch(() => {});
    return viaOracle(req, url, env);
  }

  const ct = up.headers.get('content-type') || '';
  const base = url.origin;
  if (/mpegurl/i.test(ct) || /\.m3u8(\?|$)/i.test(up.url || upstream)) {
    const body = await up.text();
    let out = body;
    if (body.startsWith('#EXTM3U')) {
      const refs = [];
      out = rewrite(body, up.url || upstream, (abs) => `@@PW${refs.push(abs) - 1}@@`);
      for (let i = 0; i < refs.length; i++) {
        out = out.replace(`@@PW${i}@@`, await proxyUrl(base, refs[i], extra, env.PW_SECRET));
      }
    }
    return new Response(req.method === 'HEAD' ? null : out, {
      status: up.status === 200 ? 200 : up.status,
      headers: { ...CORS, 'content-type': 'application/vnd.apple.mpegurl', 'cache-control': 'no-cache' },
    });
  }

  const h = {
    ...CORS,
    'content-type': ct || 'application/octet-stream',
    'access-control-expose-headers': 'content-length, content-range, accept-ranges',
    'cache-control': 'public, max-age=300',
  };
  for (const k of ['content-length', 'content-range', 'accept-ranges', 'etag', 'last-modified']) {
    const v = up.headers.get(k);
    if (v) h[k] = v;
  }
  const noBody = req.method === 'HEAD' || up.status === 204 || up.status === 304;
  return new Response(noBody ? null : up.body, { status: up.status, headers: h });
}

export { b64, unb64, sig, proxyUrl };

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
    try {
      if (url.pathname === '/health') return json({ ok: true, relay: true });
      if (url.pathname === '/resolve') {
        if (req.method !== 'POST') return text('POST', 405);
        return forward(req, env.ORACLE_URL + '/resolve', env, {
          'x-forwarded-proto': 'https',
          'x-pw-public-host': url.host,
          'x-pw-key': env.PW_KEY,
        });
      }
      if (url.pathname === '/p') return await handleProxy(req, url, env);
      if (url.pathname === '/top10' || url.pathname === '/geo') return forward(req, env.ORACLE_URL + url.pathname + url.search, env);
      return text('not found', 404);
    } catch (e) {
      return text('error', 500);
    }
  },
};
