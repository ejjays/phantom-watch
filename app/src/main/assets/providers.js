// contract: __pwRun(id, leg, argsJson); window.pw = { http, done, fail, log }
// resolve(ctx) -> { formats: [{url,isHls,label,height,english,referer,headers?,enSubs}], subs: [{label,url,src}] }
// a leg that cannot serve the title returns { formats: [], subs: [], skipped: true }
(function () {
  var pending = new Map();
  var seq = 0;

  function skipped() {
    return { formats: [], subs: [], skipped: true };
  }

  // pick order: real height first, then 1080p+ with en subs, then a
  // fixed rung over adaptive master (abr settles low, 268p while
  // its own 1080p rung served 804), en subs, english audio, then height
  // auto rung height is the ladder ceiling not what it serves so it
  // never claims the 1080p+ bucket, 0 height means the leg never reported one
  // so it cant outrank a known height (0 height rung once won and sat dead)
  function rankOf(f) {
    var h = typeof f.height === 'number' ? f.height : 0;
    var auto = /-auto$/.test(f.label || '');
    return [
      h < 0 ? 0 : 1,
      f.dub ? 0 : 1,
      h > 0 ? 1 : 0,
      (f.enSubs && h >= 1080 && !auto) ? 1 : 0,
      auto ? 0 : 1,
      f.enSubs ? 1 : 0,
      f.english !== false ? 1 : 0,
      h
    ];
  }

  function pwRank(formats) {
    return (formats || []).slice().sort(function (a, b) {
      var x = rankOf(a);
      var y = rankOf(b);
      for (var i = 0; i < x.length; i++) {
        if (x[i] !== y[i]) return y[i] - x[i];
      }
      return 0;
    });
  }

  // legs that cant serve this title at all, app and probe.js share this
  function pwSkip(name, ctx) {
    ctx = ctx || {};
    if (name === 'vap') return !ctx.imdb;
    // allanime needs a tmdb title to search by
    if (name === 'allanime') return !ctx.tmdbKey;
    if (name === 'paradise') return ctx.kind !== 'tv' || String(ctx.season) !== '1' || !ctx.title;
    return false;
  }

  function http(req) {
    return new Promise(function (resolve, reject) {
      var id = 'h' + (++seq);
      pending.set(id, { resolve: resolve, reject: reject });
      pw.http(id, JSON.stringify(req || {}));
    });
  }

  function bridgeDone(id, json) {
    var p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    try { p.resolve(JSON.parse(json)); } catch (e) { p.reject(e); }
  }

  function bridgeFail(id, msg) {
    var p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    p.reject(new Error(msg || 'bridge'));
  }

  // native extractor, no pw.voe in node/probe so caller skips
  function voe(code) {
    return new Promise(function (resolve) {
      if (!pw || typeof pw.voe !== 'function') { resolve(null); return; }
      var id = 'v' + (++seq);
      pending.set(id, { resolve: resolve, reject: function () { resolve(null); } });
      try { pw.voe(id, String(code)); } catch (e) { pending.delete(id); resolve(null); }
    });
  }

  function absUrl(rel, base) {
    try { return new URL(rel, base).toString(); } catch (e) { return rel; }
  }

  function masterVariants(text, base) {
    var out = [];
    var height = -1;
    var lines = text.split(/\r\n|\r|\n/);
    for (var i = 0; i < lines.length; i++) {
      var t = lines[i].trim();
      if (t.indexOf('#EXT-X-STREAM-INF') === 0) {
        var m = /RESOLUTION=\d+x(\d+)/.exec(t);
        height = m ? parseInt(m[1], 10) : 0;
      } else if (t && t.charAt(0) !== '#' && height >= 0) {
        out.push(fmt(absUrl(t, base), true, height > 0 ? height + 'p' : 'auto', height));
        height = -1;
      }
    }
    return out.sort(function (a, b) { return b.height - a.height; });
  }

  function fmt(url, isHls, label, height) {
    return { url: url, isHls: isHls, label: label, height: height, english: true, referer: '', enSubs: false, enAudio: false };
  }

  function normTitle(t) {
    return String(t || '').toLowerCase().replace(/[^a-z0-9]+/g, '');
  }

  async function get(url, referer, timeout, headers) {
    var r = await http({ url: url, referer: referer || '', timeout: timeout || 10, headers: headers });
    return r && r.code === 200 ? r.body : null;
  }

  function capVer(u) {
    var m = /(?:%2F|\/)v(\d+)(?:%2F|\/)/i.exec(u);
    return m ? parseInt(m[1], 10) : 0;
  }

  // disabled, captions desynced
  async function love(ctx) {
    var cfg = ctx.cfg || {};
    var api = cfg.api || 'https://api.vidlove.cc';
    var player = cfg.player || 'https://player.vidlove.cc';
    var sources = cfg.sources && cfg.sources.length ? cfg.sources : ['', 'vidapi', 'moviebox', 'dcloud', 'tcloud', 'moviebox2'];
    var movie = ctx.kind === 'movie';
    var base = movie
      ? api + '/movie?id=' + ctx.tmdbId + '&mode=json'
      : api + '/tv?id=' + ctx.tmdbId + '&season=' + ctx.season + '&episode=' + ctx.episode + '&mode=json';
    var embed = movie
      ? player + '/embed/movie/' + ctx.tmdbId
      : player + '/embed/tv/' + ctx.tmdbId + '/' + ctx.season + '/' + ctx.episode;
    var urls = sources.map(function (s) { return s ? base + '&sources=' + s : base; });
    var got = await Promise.all(urls.map(function (u) {
      return get(u, embed, 20, cfg.headers).catch(function () { return null; });
    }));
    var bodies = got.filter(Boolean);
    var formats = [];
    var subs = [];
    var seen = new Set();
    for (var bi = 0; bi < bodies.length; bi++) {
      var root;
      try { root = JSON.parse(bodies[bi]); } catch (e) { continue; }
      var src = root && root.source;
      if (!src) continue;
      var srcUrl = src.url || '';
      if (!srcUrl) continue;
      var tag = src.label || 'love';
      var box = /moviebox/i.test(tag);
      var qualities = src.qualities || [];
      for (var qi = 0; qi < qualities.length; qi++) {
        var u = qualities[qi] && qualities[qi].url || '';
        if (u.indexOf('http') !== 0) continue;
        var m = /(\d{3,4})p/.exec(qualities[qi].quality || '');
        var h = m ? parseInt(m[1], 10) : 0;
        formats.push(fmt(u, u.indexOf('.m3u8') >= 0, 'love-' + tag + '-' + (h > 0 ? h + 'p' : 'src'), box ? -h : h));
      }
      var manifest = src.manifest || '';
      if (manifest.indexOf('#EXTM3U') >= 0 && manifest.indexOf('#EXT-X-STREAM-INF') >= 0) {
        var variants = masterVariants(manifest, srcUrl);
        for (var vi = 0; vi < variants.length; vi++) {
          formats.push(fmt(variants[vi].url, true, 'love-' + tag + '-' + variants[vi].label, box ? -variants[vi].height : variants[vi].height));
        }
      } else if (manifest.indexOf('#EXTM3U') >= 0) {
        formats.push(fmt(srcUrl, true, 'love-' + tag + '-src', 0));
      }
      var subtitles = root.subtitles || [];
      for (var si = 0; si < subtitles.length; si++) {
        var f = subtitles[si] && subtitles[si].file || '';
        var l = (subtitles[si] && subtitles[si].label || '').trim() || 'Unknown';
        if (f.indexOf('http') === 0 && !seen.has(f)) {
          seen.add(f);
          subs.push({ label: l, url: f, src: 'love' });
        }
      }
    }
    if (!subs.length) {
      var capUrl = movie
        ? api + '/subtitles/movie/' + ctx.tmdbId
        : api + '/subtitles/tv/' + ctx.tmdbId + '/' + ctx.season + '/' + ctx.episode;
      var capBody = await get(capUrl, embed, 20, cfg.headers).catch(function () { return null; });
      if (capBody) {
        try {
          (JSON.parse(capBody) || []).forEach(function (c) {
            var u = c && c.file || '';
            if (u.indexOf('http') !== 0 || !/english/i.test(c.label || '') || seen.has(u)) return;
            seen.add(u);
            subs.push({ label: c.label, url: u, src: 'love' });
          });
        } catch (e) {}
      }
    }
    // cache keeps every revision, only newest is retimed for current encode
    subs.sort(function (a, b) { return capVer(b.url) - capVer(a.url); });
    var enSubs = subs.some(function (s) { return /english/i.test(s.label); });
    formats.sort(function (a, b) { return b.height - a.height; });
    formats.forEach(function (f) { f.referer = embed; f.enSubs = enSubs; });
    return { formats: formats, subs: subs };
  }

  function hexBytes(hex) {
    var c = hex.trim();
    var out = new Uint8Array(c.length >> 1);
    for (var i = 0; i < out.length; i++) out[i] = parseInt(c.substring(i * 2, i * 2 + 2), 16);
    return out;
  }

  function b64url(s) {
    s = s.trim().replace(/-/g, '+').replace(/_/g, '/');
    while (s.length % 4) s += '=';
    return Uint8Array.from(atob(s), function (ch) { return ch.charCodeAt(0); });
  }

  async function rockDecrypt(enc, keyHex) {
    var raw = b64url(enc);
    var key = await crypto.subtle.importKey('raw', hexBytes(keyHex).slice(0, 32), { name: 'AES-GCM' }, false, ['decrypt']);
    var pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: raw.slice(0, 12), tagLength: 128 }, key, raw.slice(12));
    return new TextDecoder().decode(pt);
  }

  async function getRange(url, referer, timeout) {
    var r = await http({ url: url, referer: referer, timeout: timeout || 8 });
    return r && r.code >= 200 && r.code < 300 ? r.body : null;
  }

  function rockFmt(c, url, isHls, label, height) {
    var f = fmt(url, isHls, label, height);
    f.english = c.english;
    return f;
  }

  async function probeRock(c, referer) {
    if (c.type === 'mp4' && c.url.indexOf('.m3u8') < 0) {
      return [rockFmt(c, c.url, false, 'rock-' + c.name, 1080)];
    }
    var text = await getRange(c.url, referer);
    if (text === null || text.indexOf('#EXTM3U') < 0) {
      if (c.url.indexOf('.mp4') >= 0) return [rockFmt(c, c.url, false, 'rock-' + c.name, 1080)];
      return [];
    }
    if (text.indexOf('#EXT-X-STREAM-INF') < 0) {
      return [rockFmt(c, c.url, true, 'rock-' + c.name, 0)];
    }
    return masterVariants(text, c.url).map(function (v) {
      return rockFmt(c, v.url, true, 'rock-' + c.name + '-' + v.label, v.height);
    });
  }

  async function rock(ctx) {
    var cfg = ctx.cfg || {};
    var api = (cfg.api || 'https://vidrock.net/api').replace(/\/+$/, '');
    var referer = cfg.referer || 'https://vidrock.net/';
    var key = cfg.key || '7f3e9c2a8b5d1f4e6a9c3b7d2e5f8a1c4b6d9e2f5a8c1b4d7e9f2a5c8b1d4e7f';
    var names = cfg.candidates && cfg.candidates.length ? cfg.candidates : ['flix', 'hind', 'telugu', 'tamil', 'hindi'];
    var movie = ctx.kind === 'movie';
    var path = movie ? 'movie/' + ctx.tmdbId : 'tv/' + ctx.tmdbId + '/' + ctx.season + '/' + ctx.episode;
    var body = await get(api + '/' + path, referer);
    if (body === null) return { formats: [], subs: [] };
    var obj;
    try { obj = JSON.parse(body); } catch (e) { return { formats: [], subs: [] }; }
    var keys = [];
    names.forEach(function (n) { keys.push(n, n + '_dub', n + '_sub'); });
    var uniq = [];
    Object.keys(obj).concat(keys).forEach(function (n) { if (uniq.indexOf(n) < 0) uniq.push(n); });
    var cands = [];
    uniq.slice(0, 6).forEach(function (name) {
      var e = obj[name];
      if (!e || typeof e !== 'object') return;
      var enc = e.url || '';
      if (!enc) return;
      cands.push({ name: name, enc: enc, type: e.type || '', lang: e.language || '' });
    });
    var out = await Promise.all(cands.map(async function (c) {
      var url;
      try { url = await rockDecrypt(c.enc, key); } catch (e) { return []; }
      if (url.indexOf('http') !== 0) return [];
      var english = !c.lang || c.lang.toLowerCase().indexOf('english') === 0;
      return probeRock({ name: c.name, url: url, type: c.type, english: english }, referer);
    }));
    var formats = [];
    out.forEach(function (list) { formats = formats.concat(list); });
    formats.sort(function (a, b) { return b.height - a.height; });
    formats.forEach(function (f) { f.referer = referer; });
    return { formats: formats, subs: [] };
  }

  function empty() {
    return { formats: [], subs: [] };
  }

  async function raw(url, referer, timeout) {
    var r = await http({ url: url, referer: referer || '', timeout: timeout || 8 });
    return r && !r.error ? r.body : null;
  }

  async function postJson(url, referer, body, timeout) {
    var r = await http({ url: url, referer: referer, method: 'POST', body: body, timeout: timeout || 10 });
    if (!r || r.error || r.code !== 200) return null;
    try { return JSON.parse(r.body); } catch (e) { return null; }
  }

  function enRenditions(text) {
    var re = /#EXT-X-MEDIA:[^\r\n]*/g;
    var m, subs = false, audio = false;
    while ((m = re.exec(text)) !== null) {
      if (!/LANGUAGE="?en/.test(m[0])) continue;
      if (m[0].indexOf('TYPE=SUBTITLES') >= 0) subs = true;
      if (m[0].indexOf('TYPE=AUDIO') >= 0) audio = true;
    }
    return { subs: subs, audio: audio };
  }

  function introOf(io) {
    return io && io.end > io.start ? { start: io.start, end: io.end } : null;
  }

  function urlencode(s) {
    return encodeURIComponent(s)
      .replace(/[!'()~]/g, function (c) { return '%' + c.charCodeAt(0).toString(16).toUpperCase(); })
      .replace(/%20/g, '+');
  }

  function maxHeight(list) {
    return list.length ? Math.max.apply(null, list.map(function (f) { return f.height; })) : 0;
  }

  // cinesrc light -> lightspeed.st, cdn gates on Origin not Referer (404 Removed)
  // and only master carries audio group, rungs are video only so a rung
  // picked from master plays silent
  async function light(ctx) {
    var cfg = ctx.cfg || {};
    var referer = cfg.referer || 'https://cinesrc.st/';
    var origin = referer.replace(/\/+$/, '');
    var query = '?apiKey=' + (cfg.key || '392948') + '&provider=' + (cfg.provider || 'light') + '&id=' + ctx.tmdbId;
    query += ctx.kind === 'movie' ? '&type=movie' : '&type=tv&season=' + ctx.season + '&episode=' + ctx.episode;
    var api = (cfg.api || 'https://cinesrc.st/api/stream').replace(/\/+$/, '');
    var r = await http({ url: api + query, timeout: 12 });
    if (!r || r.error) return empty();
    if (r.code === 404) return /no_streams/.test(r.body || '') ? skipped() : empty();
    if (r.code !== 200) return empty();
    var root;
    try { root = JSON.parse(r.body); } catch (e) { return empty(); }
    var master = ((root.url || [])[0] || {}).url || '';
    if (master.indexOf('http') !== 0) return empty();
    var m = await http({ url: master, referer: referer, headers: { Origin: origin }, timeout: 10 });
    var manifest = m && m.code === 200 ? m.body : '';
    var audio = manifest.split(/\r?\n/).some(function (l) {
      return l.indexOf('TYPE=AUDIO') > 0 && /LANGUAGE="?en/i.test(l) && !/commentary/i.test(l);
    });
    var subs = (root.captions || []).filter(function (c) { return c && c.url && c.language === 'en'; })
      .map(function (c) { return { label: c.display || 'English', url: c.url, src: 'light' }; });
    var f = fmt(master, true, 'light-auto', maxHeight(masterVariants(manifest, master)));
    f.headers = { Referer: referer, Origin: origin };
    f.enSubs = subs.length > 0; f.enAudio = audio; f.english = audio;
    return { formats: [f], subs: subs };
  }

  async function zee(ctx) {
    var cfg = ctx.cfg || {};
    var api = cfg.api || 'https://core.vidzee.wtf';
    var origin = cfg.origin || 'https://player.vidzee.wtf';
    var path = ctx.kind === 'movie'
      ? '/streams/movie/' + ctx.tmdbId
      : '/streams/tv/' + ctx.tmdbId + '/' + ctx.season + '/' + ctx.episode;
    var body = await get(api + path + '?s=' + (cfg.source || 'dcloud') + '&e=0', origin + '/');
    if (body === null) return empty();
    var url = '';
    try { url = JSON.parse(body).url || ''; } catch (e) {}
    if (url.indexOf('http') !== 0) return empty();
    var f = fmt(url, true, 'zee-dcloud-auto', 0);
    f.referer = origin + '/';
    return { formats: [f], subs: [] };
  }

  async function vix(ctx) {
    var cfg = ctx.cfg || {};
    var site = cfg.site || 'https://vixsrc.to';
    var referer = site + '/';
    var api = ctx.kind === 'movie'
      ? site + '/api/movie/' + ctx.tmdbId
      : site + '/api/tv/' + ctx.tmdbId + '/' + ctx.season + '/' + ctx.episode;
    var body = await getRange(api, referer);
    if (body === null) return empty();
    var src = '';
    try { src = JSON.parse(body).src || ''; } catch (e) {}
    if (src.indexOf('/embed/') !== 0) return empty();
    var page = await raw(site + src, referer, 8);
    if (page === null) return empty();
    var token = /['"]token['"]:\s?['"]([^'"]*)['"]/.exec(page);
    var expires = /['"]expires['"]:\s?['"]([^'"]*)['"]/.exec(page);
    var pageUrl = /url:\s?['"]([^'"]*)['"]/.exec(page);
    if (!token || !expires || !pageUrl) return empty();
    if (!token[1] || !expires[1] || !pageUrl[1]) return empty();
    var base;
    try { base = new URL(pageUrl[1]); } catch (e) { return empty(); }
    var q = base.search ? base.search.slice(1) + '&' : '';
    var master = base.protocol + '//' + base.host + base.pathname + '.m3u8?' + q +
      'token=' + token[1] + '&expires=' + expires[1] + '&h=1';
    var manifest = await raw(master, referer, 8);
    if (manifest === null) return empty();
    var rungs = masterVariants(manifest, master).map(function (v) {
      var f = fmt(v.url, true, 'vix-' + v.label, v.height);
      return f;
    });
    if (!rungs.length) return empty();
    var e = enRenditions(manifest);
    var auto = fmt(master, true, 'vix-auto', maxHeight(rungs));
    auto.referer = referer;
    auto.enSubs = e.subs;
    auto.enAudio = e.audio;
    // bare rung is video only here, vixsrc keeps sound in separate rendition
    var splitAudio = /#EXT-X-MEDIA:[^\r\n]*TYPE=AUDIO/.test(manifest);
    return { formats: splitAudio ? [auto] : [auto].concat(rungs), subs: [] };
  }

  async function vap(ctx) {
    if (pwSkip('vap', ctx)) return skipped();
    if (!ctx.imdb) return empty();
    var cfg = ctx.cfg || {};
    var api = cfg.api || 'https://streamdata.vaplayer.ru/api.php';
    var origin = cfg.origin || 'https://nextgencloudfabric.com';
    var movie = ctx.kind === 'movie';
    var query = movie
      ? 'imdb=' + ctx.imdb + '&type=movie'
      : 'imdb=' + ctx.imdb + '&type=tv&season=' + ctx.season + '&episode=' + ctx.episode;
    var referer = movie
      ? origin + '/embed/movie/' + ctx.imdb
      : origin + '/embed/tv/' + ctx.imdb + '/' + ctx.season + '/' + ctx.episode;
    var body = await get(api + '?' + query, referer, 10);
    if (body === null) return empty();
    var master = '';
    try {
      var urls = JSON.parse(body).data.stream_urls;
      master = urls[0] || '';
    } catch (e) {}
    if (!master || master.indexOf('http') !== 0) return empty();
    var manifest = await raw(master, origin + '/', 8);
    if (manifest === null || manifest.indexOf('#EXTM3U') < 0) return empty();
    var e = enRenditions(manifest);
    var rungs = masterVariants(manifest, master).map(function (v) {
      var f = fmt(v.url, true, 'vap-' + v.label, v.height);
      f.referer = origin + '/';
      return f;
    });
    var auto = fmt(master, true, 'vap-auto', maxHeight(rungs));
    auto.referer = origin + '/';
    auto.enSubs = e.subs;
    auto.enAudio = e.audio;
    return { formats: [auto].concat(rungs), subs: [] };
  }

  async function vidlink(ctx) {
    var cfg = ctx.cfg || {};
    var api = cfg.api || 'https://vidlink.pro/api/b';
    var encUrl = cfg.enc || 'https://enc-dec.app/api/enc-vidlink?text=';
    var referer = cfg.referer || 'https://vidlink.pro/';
    var encBody = await get(encUrl + ctx.tmdbId, referer, 8);
    var enc = '';
    if (encBody !== null) {
      try { enc = JSON.parse(encBody).result || ''; } catch (e) {}
    }
    if (!enc) return empty();
    var path = ctx.kind === 'movie'
      ? 'movie/' + enc
      : 'tv/' + enc + '/' + ctx.season + '/' + ctx.episode;
    var body = await get(api + '/' + path, referer, 10);
    if (body === null) return empty();
    var stream;
    try { stream = JSON.parse(body).stream; } catch (e) {}
    if (!stream) return empty();
    var subs = [];
    (stream.captions || []).forEach(function (o) {
      var u = o.url || '';
      if (u.indexOf('http') === 0) subs.push({ label: o.language || 'Unknown', url: u, src: 'vidlink' });
    });
    var enSubs = subs.some(function (s) { return s.label.toLowerCase() === 'english'; });
    var formats = [];
    var q = stream.qualities || {};
    Object.keys(q).forEach(function (key) {
      var u = (q[key] && q[key].url) || '';
      if (u.indexOf('http') !== 0) return;
      var h = parseInt(key, 10);
      if (isNaN(h)) h = 0;
      var f = fmt(u, false, 'vidlink-' + (h > 0 ? h + 'p' : 'src'), h);
      f.headers = { 'User-Agent': 'okhttp/4.12.0' };
      f.enSubs = enSubs;
      formats.push(f);
    });
    formats.sort(function (a, b) { return b.height - a.height; });
    return { formats: formats, subs: subs };
  }

  function zeroPad(bytes, n) {
    var out = new Uint8Array(n);
    out.set(bytes.slice(0, n));
    return out;
  }

  async function megaDecrypt(enc, keyStr, ivStr) {
    var rawBytes = b64url(enc);
    var key = await crypto.subtle.importKey(
      'raw', zeroPad(new TextEncoder().encode(keyStr), 32), { name: 'AES-CBC' }, false, ['decrypt']);
    var pt = await crypto.subtle.decrypt(
      { name: 'AES-CBC', iv: new TextEncoder().encode(ivStr) }, key, rawBytes);
    return new TextDecoder().decode(pt);
  }

  async function hianime(ctx) {
    if (pwSkip('hianime', ctx)) return skipped();
    var cfg = ctx.cfg || {};
    var origin = cfg.origin || 'https://megaplay.buzz';
    var key = cfg.key || 'i?LMTAx0Q6,:}50U';
    var iv = cfg.iv || 'hlsMediaAttached';
    var ep = ctx.kind === 'movie' ? 'movie' : 'tv';
    var base = ctx.tmdbBase || 'https://api.themoviedb.org/3';
    var meta = await get(base + '/' + ep + '/' + ctx.tmdbId + '?api_key=' + ctx.tmdbKey, 'https://www.themoviedb.org/');
    if (meta === null) return empty();
    var title = '', fullTitle = '', isAnim = false;
    try {
      var o = JSON.parse(meta);
      fullTitle = o.name || o.title || '';
      title = fullTitle.split(':')[0].trim();
      var genres = o.genres || [];
      for (var gi = 0; gi < genres.length; gi++) if (genres[gi].id === 16) { isAnim = true; break; }
    } catch (e) {}
    if (!isAnim) return skipped();
    if (!title) return empty();
    var gql = JSON.stringify({
      query: 'query($s:String){Page(page:1,perPage:5){media(search:$s,type:ANIME,sort:[POPULARITY_DESC]){idMal episodes title{romaji english native} synonyms relations{edges{relationType node{idMal episodes}}}}}}',
      variables: { s: title },
    });
    var resp = await postJson('https://graphql.anilist.co', 'https://anilist.co/', gql, 8);
    var list = (resp && resp.data && resp.data.Page && resp.data.Page.media) || [];
    var want = normTitle(title), wantFull = normTitle(fullTitle), M = null;
    for (var mi = 0; mi < list.length; mi++) {
      var it = list[mi];
      if (!it || !it.idMal) continue;
      var cands = [it.title && it.title.romaji, it.title && it.title.english, it.title && it.title.native]
        .concat(it.synonyms || []).filter(Boolean).map(normTitle);
      if (cands.indexOf(want) >= 0 || cands.indexOf(wantFull) >= 0) { M = it; break; }
    }
    if (!M) return empty();
    var mal = M.idMal || 0;
    if (mal <= 0) return empty();
    var n = parseInt(ctx.episode, 10);
    var epNum = ctx.kind === 'movie' ? 1 : (isNaN(n) || n <= 0 ? 1 : n);
    // tmdb flattens some anime into one long season, anilist keeps cours apart
    if (M.episodes && epNum > M.episodes) {
      var edges = (M.relations && M.relations.edges) || [];
      for (var q = 0; q < edges.length; q++) {
        var node = edges[q].node || {};
        if (edges[q].relationType !== 'SEQUEL' || !node.idMal || !node.episodes) continue;
        if (epNum - M.episodes <= node.episodes) {
          mal = node.idMal;
          epNum = epNum - M.episodes;
        }
        break;
      }
    }
    async function cut(kind) {
      var page = origin + '/stream/mal/' + mal + '/' + epNum + '/' + kind;
      var r = await http({ url: page, referer: page, timeout: 10 });
      if (r && (r.code === 403 || r.code === 429 || r.code >= 500)) {
        await new Promise(function (res) { setTimeout(res, 400); });
        r = await http({ url: page, referer: page, timeout: 10 });
      }
      var html = r && r.code === 200 ? r.body : null;
      if (html === null) return null;
      var idm = /data-id=["'](\d+)["']/.exec(html);
      if (!idm) return null;
      var srcBody = await get(origin + '/stream/getSources?id=' + idm[1] + '&id=' + idm[1], page, 10);
      if (srcBody === null) return null;
      var srcObj;
      try { srcObj = JSON.parse(srcBody); } catch (e) { return null; }
      var enc = srcObj.enc || '';
      if (!enc) return null;
      var master = '';
      try {
        var m = /([a-z0-9.-]+\.[a-z]+\/anime\/[a-f0-9]+\/[a-f0-9]+\/master\.m3u8)/
          .exec(await megaDecrypt(enc, key, iv));
        if (m) master = 'https://' + m[1];
      } catch (e) {}
      if (!master) return null;
      var manifest = await raw(master, origin + '/', 10);
      if (manifest === null || manifest.indexOf('#EXTM3U') < 0) return null;
      return { srcObj: srcObj, master: master, manifest: manifest };
    }

    // dub is a separate cut with its own source id, one cut missing must not kill leg
    var both = await Promise.all([cut('sub'), cut('dub')]);
    // some episodes 403 the sub source but serve the dub, use that cut and label it
    var dubOnly = !both[0] && both[1];
    if (dubOnly) { both[0] = both[1]; both[1] = null; }
    var sub = both[0];
    if (!sub) return empty();
    var srcObj = sub.srcObj;
    var master = sub.master;
    var manifest = sub.manifest;
    var subs = [];
    (srcObj.tracks || []).forEach(function (tr) {
      var u = tr.file || '';
      var l = tr.label || '';
      if (u.indexOf('http') === 0 && l.toLowerCase() === 'english') {
        subs.push({ label: 'English', url: u, src: 'hianime' });
      }
    });
    var e = enRenditions(manifest);
    var enSubs = e.subs || subs.length > 0;
    var rungs = masterVariants(manifest, master).map(function (v) {
      var f = fmt(v.url, true, 'hianime-' + v.label, v.height);
      f.referer = origin + '/';
      f.enSubs = enSubs;
      return f;
    });
    var auto = fmt(master, true, 'hianime-auto', maxHeight(rungs));
    auto.referer = origin + '/';
    auto.enSubs = enSubs;
    auto.enAudio = e.audio;
    var intro = introOf(srcObj.intro);
    var formats = [auto].concat(rungs);
    if (intro) formats.forEach(function (f) { f.intro = intro; });
    var dub = both[1];
    if (dub) {
      var dRungs = masterVariants(dub.manifest, dub.master).map(function (v) {
        var f = fmt(v.url, true, 'hianime-dub-' + v.label, v.height);
        f.referer = origin + '/';
        f.enSubs = enSubs;
        f.dub = 'English';
        return f;
      });
      var dAuto = fmt(dub.master, true, 'hianime-dub-auto', maxHeight(dRungs));
      dAuto.referer = origin + '/';
      dAuto.enSubs = enSubs;
      dAuto.dub = 'English';
      var dIntro = introOf(dub.srcObj.intro);
      var dSubs = [];
      (dub.srcObj.tracks || []).forEach(function (tr) {
        var u = tr.file || '';
        var l = tr.label || '';
        if (u.indexOf('http') === 0 && l.toLowerCase() === 'english') {
          dSubs.push({ label: 'English', url: u, src: 'hianime' });
        }
      });
      var dFormats = [dAuto].concat(dRungs);
      if (dIntro) dFormats.forEach(function (f) { f.intro = dIntro; });
      // dub cut points at the sub cut's own vtt which reads as jp captions over
      // dub audio, keep only captions the dub owns
      var ownDubSubs = dSubs.filter(function (s) {
        return !subs.some(function (o) { return o.url === s.url; });
      });
      dFormats.forEach(function (f) { f.subs = ownDubSubs; f.enSubs = ownDubSubs.length > 0; });
      formats = formats.concat(dFormats);
    }
    if (dubOnly) formats.forEach(function (f) { f.dub = 'English'; f.subs = subs; });
    return { formats: formats, subs: subs };
  }

  async function paradise(ctx) {
    if (pwSkip('paradise', ctx)) return skipped();
    if (ctx.kind !== 'tv' || String(ctx.season) !== '1' || !ctx.title) return empty();
    var cfg = ctx.cfg || {};
    var api = cfg.api || 'https://api.animeparadise.moe';
    var stream = cfg.stream || 'https://stream.animeparadise.moe';
    var origin = cfg.origin || 'https://animeparadise.moe';
    var ref = origin + '/';

    async function getJson(path) {
      var r = await http({ url: api + path, referer: ref, timeout: 15 });
      if (!r || r.error || r.code !== 200) return null;
      try { return JSON.parse(r.body); } catch (e) { return null; }
    }

    var want = normTitle(ctx.title);
    var showId = '';
    var search = await getJson('/search?q=' + urlencode(ctx.title) + '&limit=20');
    var list = (search && search.data) || [];
    for (var i = 0; i < list.length; i++) {
      var o = list[i] || {};
      var li = o.link || '';
      if (normTitle(o.title) !== want && normTitle(li) !== want) continue;
      var id = o._id || '';
      if (!id || !li) continue;
      var info = await getJson('/anime/' + li);
      var type = info && info.data && info.data.type;
      if (type !== 'TV') continue;
      showId = id;
      break;
    }
    if (!showId) return empty();
    var originId = showId;
    function pickUid(list, n) {
      for (var j = 0; j < list.length; j++) {
        var e = list[j] || {};
        var num = e.number == null ? '' : String(e.number);
        if (num === String(n) && e.uid) return e.uid;
      }
      return '';
    }
    var eps = await getJson('/anime/' + showId + '/episode');
    var arr = (eps && eps.data) || [];
    var uid = pickUid(arr, ctx.episode);
    // same flattening as hianime, provider lists next cour as its own show
    if (!uid && arr.length && Number(ctx.episode) > arr.length) {
      var s2 = await getJson('/search?q=' + urlencode(ctx.title + ' Season 2') + '&limit=20');
      var l2 = (s2 && s2.data) || [];
      for (var k = 0; k < l2.length && !uid; k++) {
        var o2 = l2[k] || {};
        if (!o2._id || normTitle(o2.link || '').indexOf(want) !== 0) continue;
        var i2 = await getJson('/anime/' + o2.link);
        if (!i2 || !i2.data || i2.data.type !== 'TV') continue;
        var x2 = await getJson('/anime/' + o2._id + '/episode');
        uid = pickUid((x2 && x2.data) || [], Number(ctx.episode) - arr.length);
        if (uid) originId = o2._id;
      }
    }
    if (!uid) return empty();
    var epData = await getJson('/ep/' + uid + '?origin=' + originId);
    var ep = epData && epData.data && epData.data.episode;
    var streamLink = (ep && ep.streamLink) || '';
    if (!streamLink) return empty();
    var subs = [];
    ((ep && ep.subData) || []).forEach(function (s) {
      var u = s.src || '';
      var l = s.label || '';
      if (s.type === 'vtt' && u.indexOf('http') === 0 && /english/i.test(l)) {
        subs.push({ label: l, url: u, src: 'paradise' });
      }
    });
    var masterUrl = stream + '/m3u8?url=' + urlencode(streamLink);
    var manifest = await raw(masterUrl, ref, 20);
    if (manifest === null || manifest.indexOf('#EXTM3U') < 0) return empty();
    var e2 = enRenditions(manifest);
    var enSubs = e2.subs || subs.length > 0;
    var rungs = masterVariants(manifest, masterUrl).map(function (v) {
      var f = fmt(v.url, true, 'paradise-' + v.label, v.height);
      f.referer = ref;
      f.enSubs = enSubs;
      return f;
    });
    var auto = fmt(masterUrl, true, 'paradise-auto', maxHeight(rungs));
    auto.referer = ref;
    auto.enSubs = enSubs;
    auto.enAudio = e2.audio;
    var intro = introOf((ep.skipData || {}).intro);
    var outro = introOf((ep.skipData || {}).outro);
    var formats = [auto].concat(rungs);
    if (intro) formats.forEach(function (f) { f.intro = intro; });
    if (outro) formats.forEach(function (f) { f.outro = outro; });
    return { formats: formats, subs: subs };
  }

  function unpackJs(packed) {
    return eval('(' + packed.replace(/^eval\(/, '').replace(/\)\s*$/, '') + ')');
  }

  function daySlug(iso) {
    var d = new Date(iso + 'T00:00:00Z');
    if (isNaN(d.getTime())) return '';
    var names = ['january', 'february', 'march', 'april', 'may', 'june', 'july',
      'august', 'september', 'october', 'november', 'december'];
    return names[d.getUTCMonth()] + '-' + d.getUTCDate() + '-' + d.getUTCFullYear();
  }

  async function tmdbTitles(ctx) {
    var base = ctx.tmdbBase || 'https://api.themoviedb.org/3';
    var kind = ctx.kind === 'movie' ? 'movie' : 'tv';
    var body = await get(base + '/' + kind + '/' + ctx.tmdbId + '?api_key=' + ctx.tmdbKey, 'https://www.themoviedb.org/', 10);
    var out = [];
    if (body !== null) {
      try {
        var o = JSON.parse(body);
        out = [o.title, o.name, o.original_title, o.original_name];
      } catch (e) {}
    }
    return out.filter(function (t) { return !!t; });
  }

  async function moviePage(site, query) {
    var want = normTitle(query);
    var rest = await get(site + '/wp-json/wp/v2/movies?per_page=10&_fields=link,title&search=' + urlencode(query), site + '/', 10);
    if (rest !== null) {
      try {
        var arr = JSON.parse(rest);
        for (var i = 0; i < arr.length; i++) {
          var o = arr[i] || {};
          if (normTitle(o.link).indexOf(want) !== -1 || normTitle(o.title && o.title.rendered).indexOf(want) !== -1) return o.link;
        }
      } catch (e) {}
    }
    var html = await get(site + '/?s=' + urlencode(query), site + '/', 12);
    if (html === null) return rest === null ? null : '';
    var re = /href="([^"]*\/movies\/[^"\/]+)\/?"/g;
    var m;
    while ((m = re.exec(html)) !== null) {
      if (normTitle(m[1]).indexOf(want) !== -1) return m[1].replace(/\/?$/, '/');
    }
    return '';
  }

  async function pinoyMovie(ctx, cfg, title) {
    var site = cfg.movie || 'https://pinoymoviepedia.ru';
    var page = await moviePage(site, title);
    if (page === null) return empty();
    if (!page) {
      var more = await tmdbTitles(ctx);
      for (var i = 0; i < more.length && !page; i++) {
        if (normTitle(more[i]) === normTitle(title)) continue;
        page = await moviePage(site, more[i]);
        if (page === null) return empty();
      }
    }
    if (!page) return skipped();
    var body = await get(page, site + '/', 12);
    if (body === null) return empty();
    var em = /https:\/\/abstream\.to\/embed\/([a-z0-9]+)/.exec(body);
    if (!em) {
      var vm = /https:\/\/voe\.sx\/e\/([a-z0-9]+)/.exec(body);
      // no native extractor (probe/ci) means title is out of reach here not a failure
      if (!vm || cfg.voe === false || typeof pw.voe !== 'function') return skipped();
      var v = await voe(vm[1]);
      if (!v || !v.url) return empty();
      var ref = v.referer || '';
      if (v.hls) {
        var mf = await raw(v.url, ref, 10);
        if (mf !== null && mf.indexOf('#EXTM3U') >= 0) {
          var e = enRenditions(mf);
          var rungs = masterVariants(mf, v.url).map(function (r) {
            var f = fmt(r.url, true, 'pinoy-' + r.label, r.height);
            f.referer = ref;
            f.enSubs = e.subs;
            return f;
          });
          if (rungs.length) {
            var auto = fmt(v.url, true, 'pinoy-auto', maxHeight(rungs));
            auto.referer = ref;
            auto.enSubs = e.subs;
            auto.enAudio = e.audio;
            return { formats: [auto].concat(rungs), subs: [] };
          }
        }
      }
      var vf = fmt(v.url, !!v.hls, 'pinoy-voe', -1);
      vf.referer = ref;
      return { formats: [vf], subs: [] };
    }
    var embed = await get('https://abstream.to/embed/' + em[1], page, 12);
    if (embed === null) return empty();
    var mm = /https:\/\/[^"'\s]*master\.m3u8[^"'\s]*/.exec(embed);
    if (!mm) return empty();
    var master = mm[0];
    var manifest = await raw(master, '', 10);
    if (manifest === null || manifest.indexOf('#EXTM3U') < 0) return empty();
    var e = enRenditions(manifest);
    var rungs = masterVariants(manifest, master).map(function (v) {
      var f = fmt(v.url, true, 'pinoy-' + v.label, v.height);
      f.enSubs = e.subs;
      return f;
    });
    var auto = fmt(master, true, 'pinoy-auto', maxHeight(rungs));
    auto.enSubs = e.subs;
    auto.enAudio = e.audio;
    return { formats: [auto].concat(rungs), subs: [] };
  }

  async function tvHit(tv, query, day) {
    var list = await get(tv + '/search.php?s=' + urlencode(query + ' ' + day.replace(/-/g, ' ')), tv + '/', 12);
    if (list === null) return null;
    var want = normTitle(query);
    var re = /href="(\/watch\/[^"]+)"/g;
    var m;
    while ((m = re.exec(list)) !== null) {
      if (m[1].indexOf('-' + day) > 0 && normTitle(m[1]).indexOf(want) !== -1) return m[1];
    }
    return '';
  }

  async function pinoyTv(ctx, cfg, title) {
    if (!ctx.tmdbKey) return skipped();
    var tv = cfg.tv || 'https://www.teleserye.su';
    var base = ctx.tmdbBase || 'https://api.themoviedb.org/3';
    var info = await get(base + '/tv/' + ctx.tmdbId + '/season/' + ctx.season + '/episode/' + ctx.episode + '?api_key=' + ctx.tmdbKey, 'https://www.themoviedb.org/', 10);
    if (info === null) return empty();
    var air = '';
    try { air = JSON.parse(info).air_date || ''; } catch (e) {}
    var day = daySlug(air);
    if (!day) return skipped();
    var hit = await tvHit(tv, title, day);
    if (hit === null) return empty();
    if (!hit) {
      var more = await tmdbTitles(ctx);
      for (var i = 0; i < more.length && !hit; i++) {
        if (normTitle(more[i]) === normTitle(title)) continue;
        hit = await tvHit(tv, more[i], day);
        if (hit === null) return empty();
      }
    }
    if (!hit) return skipped();
    var page = await get(tv + hit, tv + '/', 12);
    if (page === null) return empty();
    var pm = /(https:\/\/player\.[a-z0-9.]+\/[^"']*watch\.php\?v=[^"']+)/.exec(page);
    if (!pm) return empty();
    var player = await get(pm[1], tv + '/', 12);
    if (player === null) return empty();
    var at = player.indexOf('eval(function(p,a,c,k,e,d)');
    if (at < 0) return empty();
    var end = player.indexOf('</script>', at);
    var src = '';
    try { src = unpackJs(player.slice(at, end < 0 ? undefined : end).trim()); } catch (e) { return empty(); }
    var fm = /"file":"([^"]+)"/.exec(src);
    if (!fm) return empty();
    var bs = String.fromCharCode(92);
    var file = fm[1].split(bs + 'u0026').join('&').split(bs + '/').join('/');
    var url = absUrl(file, pm[1]);
    return { formats: [fmt(url, /\.m3u8|m3u8\?/.test(url), 'pinoy-auto', -1)], subs: [] };
  }

  async function pinoy(ctx) {
    var cfg = ctx.cfg || {};
    var title = ctx.title || '';
    if (!title) return skipped();
    if (ctx.kind === 'movie') return pinoyMovie(ctx, cfg, title);
    return pinoyTv(ctx, cfg, title);
  }

  var STREAM_L = [1116352408, 1899447441, 3049323471, 3921009573, 961987163, 1508970993,
    2453635748, 2870763221, 3624381080, 310598401, 607225278, 1426881987, 1925078388,
    2162078206, 2614888103, 3248222580];
  var STREAM_U = [109, 118, 109, 49];
  function streamMix(e) {
    e = e >>> 0;
    e ^= e >>> 16;
    e = Math.imul(e, 2246822507) >>> 0;
    e ^= e >>> 13;
    e = Math.imul(e, 3266489909) >>> 0;
    return (e ^ (e >>> 16)) >>> 0;
  }
  function streamRot(e, t) {
    t &= 31;
    if (t === 0) return e >>> 0;
    return ((e << t) | (e >>> (32 - t))) >>> 0;
  }
  function streamFnv(s) {
    var h = 2166136261;
    for (var i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619) >>> 0;
    return streamMix(h);
  }
  function streamState(seed, mediaId) {
    var t = streamMix(streamFnv(seed) ^ streamMix((mediaId >>> 0) ^ 2654435769)) >>> 0;
    var a = Array(61);
    for (var i = 0; i < 8; i++) {
      var t2 = t % 61;
      t = streamRot((t + 2654435769) >>> 0, 7 + (7 & i));
      a[t2] = (t ^ streamMix(t)) >>> 0;
      t = streamMix((t + t2) >>> 0);
    }
    return { S: a, acc: streamMix(2779096485 ^ t) >>> 0 };
  }
  function streamStep(st, n) {
    var d = st.acc % 61;
    var i = st.S[d] >>> 0;
    var s = (i ^ Math.imul(2654435769, n + 1)) >>> 0;
    // d is always < S.length (61) so chunk r is always -1 and l is n|s
    var l = (st.acc | s) >>> 0;
    var lv = (streamRot((l + st.acc) >>> 0, 31 & d) ^ streamRot(st.acc, 31 & Math.imul(d, 7))) >>> 0;
    var nv = streamMix((lv + 2654435769) >>> 0);
    st.S[d] = nv >>> 0;
    st.acc = nv >>> 0;
    return nv >>> 0;
  }
  function b64e(str) {
    var t = str.replace(/-/g, '+').replace(/_/g, '/');
    while (t.length % 4) t += '=';
    return Uint8Array.from(atob(t), function (ch) { return ch.charCodeAt(0); });
  }
  function streamDecrypt(body, seed, mediaId) {
    var raw = b64e(body);
    var st = streamState(seed, mediaId);
    var ks = new Uint8Array(raw.length);
    var n = 0;
    for (var off = 0; off < raw.length;) {
      var t = streamStep(st, n++);
      for (var k = 0; k < 4 && off < raw.length; k++, off++) ks[off] = (t >>> (k * 8)) & 255;
    }
    for (var i = 0; i < raw.length; i++) raw[i] ^= ks[i];
    for (var j = 0; j < STREAM_U.length; j++) if (raw[j] !== STREAM_U[j]) return null;
    return new TextDecoder().decode(raw.subarray(STREAM_U.length));
  }

  async function videasy(ctx) {
    var cfg = ctx.cfg || {};
    var api = (cfg.api || 'https://api.wecollege.net').replace(/\/+$/, '');
    var player = (cfg.player || 'https://www.vidy.st').replace(/\/+$/, '');
    var cities = cfg.cities && cfg.cities.length ? cfg.cities : ['miami', 'boise'];
    var title = ctx.title || '';
    if (!title) return skipped();
    var isTv = ctx.kind === 'tv';
    var ref = player + '/';
    var hdrs = {
      'Origin': ref,
      'Accept': '*/*',
      'Accept-Language': 'en-US,en;q=0.9',
      'sec-fetch-mode': 'cors',
      'sec-fetch-site': 'cross-site',
      'sec-fetch-dest': 'empty',
      'sec-ch-ua': '"Chromium";v="126", "Not.A/Brand";v="24"',
      'sec-ch-ua-mobile': '?1',
      'sec-ch-ua-platform': '"Android"',
    };
    var seedR = await http({ url: api + '/seed?mediaId=' + ctx.tmdbId, referer: ref, timeout: 8, headers: hdrs });
    var seed = '';
    var seedErr = '';
    if (seedR && !seedR.error && seedR.code === 200) {
      try { seed = JSON.parse(seedR.body).seed || ''; } catch (e) { seedErr = 'json'; }
    } else {
      seedErr = String((seedR && seedR.code) || (seedR && seedR.error) || 'none');
    }
    pw.log('videasy seed ' + (seed ? 'ok' : 'fail ' + seedErr));
    if (!seed) return empty();
    var qs = '?title=' + encodeURIComponent(title) +
      '&mediaType=' + (isTv ? 'tv' : 'movie') +
      '&year=' + (ctx.year || '') +
      '&episodeId=' + (isTv ? (ctx.episode || 1) : 1) +
      '&seasonId=' + (isTv ? (ctx.season || 1) : 1) +
      '&tmdbId=' + ctx.tmdbId +
      '&imdbId=' + encodeURIComponent(ctx.imdb || '') +
      '&enc=2&seed=' + seed;
    for (var i = 0; i < cities.length; i++) {
      var r = await http({ url: api + '/' + cities[i] + '/sources' + qs, referer: ref, timeout: 12, headers: hdrs });
      pw.log('videasy ' + cities[i] + ' ' + String((r && r.code) || (r && r.error) || 'none'));
      if (!r || r.error || r.code !== 200) continue;
      var pt = streamDecrypt(String(r.body || ''), seed, Number(ctx.tmdbId));
      if (!pt) continue;
      var root;
      try { root = JSON.parse(pt); } catch (e) { continue; }
      var subs = [];
      (root.subtitles || []).forEach(function (s) {
        var u = s && s.url || '';
        if (u.indexOf('http') !== 0) return;
        var l = String(s.lang || '').toLowerCase();
        if (l === 'eng') subs.push({ label: 'English', url: u, src: 'videasy' });
        else if (l === 'eng (forced)') subs.push({ label: 'English (forced)', url: u, src: 'videasy' });
      });
      var enSubs = subs.length > 0;
      var formats = [];
      (root.sources || []).forEach(function (s) {
        var u = typeof s === 'string' ? s : (s && s.url) || '';
        if (u.indexOf('http') !== 0) return;
        var q = String((s && s.quality) || '');
        var m = /(\d{3,4})p/i.exec(q);
        var h = /4k|2160/i.test(q) ? 2160 : (m ? parseInt(m[1], 10) : 0);
        var f = fmt(u, /\.m3u8|hls/i.test(u), 'videasy-' + (h ? h + 'p' : 'auto'), h);
        f.referer = ref;
        f.enSubs = enSubs;
        formats.push(f);
      });
      if (formats.length) {
        formats.sort(function (a, b) { return b.height - a.height; });
        return { formats: formats, subs: subs };
      }
    }
    return empty();
  }

  function captionUrl(proxy) {
    var m = /[?&]url=([^&]+)/.exec(proxy || '');
    if (!m) return proxy || '';
    try { return decodeURIComponent(m[1]); } catch (e) { return proxy; }
  }

  // netmirror clone, tmdb id in, signed mp4 cdn + srt captions out, cdn
  // 429s hard on bursts so one request and one shot per rung
  async function net79(ctx) {
    var cfg = ctx.cfg || {};
    var api = (cfg.api || 'https://net79.cc').replace(/\/+$/, '');
    var query = '?type=' + ctx.kind + (ctx.kind === 'tv'
      ? '&season=' + ctx.season + '&episode=' + ctx.episode : '');
    var body = await get(api + '/api/embed-tmdb/' + ctx.tmdbId + query, api + '/', 20);
    if (body === null) return empty();
    var root;
    try { root = JSON.parse(body); } catch (e) { return empty(); }
    if (root.noSource) return skipped();
    var subs = [];
    (root.captions || []).forEach(function (c) {
      var u = c && c.lang === 'en' ? captionUrl(c.url) : '';
      if (u.indexOf('http') === 0) subs.push({ label: c.name || 'English', url: u, src: 'net79' });
    });
    var formats = [];
    (root.streams || []).forEach(function (s) {
      var u = (s && s.url) || '';
      if (u.indexOf('http') !== 0) return;
      var h = parseInt(s.resolution, 10) || 0;
      var f = fmt(u, false, 'net79-' + (h > 0 ? h + 'p' : 'src'), h);
      f.enSubs = subs.length > 0;
      formats.push(f);
    });
    if (root.fallbackHls) {
      var fa = fmt(api + root.fallbackHls, true, 'net79-auto', 0);
      fa.enSubs = subs.length > 0;
      formats.push(fa);
    }
    formats.sort(function (a, b) { return b.height - a.height; });
    return { formats: formats, subs: subs };
  }

  // the site signs each call with a key built from a mask in its own chunk plus
  // a boot token, both only exist at run time so they are read out of the
  // shipped bundle once per site build
  var aaHost = 'https://mkissa.to', aaApi = 'https://api.mkissa.net', aaLane = 'k7';
  var aaEpochMs = 604800000, aaGraceMs = 86400000, aaReqMs = 300000;
  var aaCookie = '';
  var aaCache = { cfg: null, key: null, epoch: 0, build: '', at: 0, cfgAt: 0 };
  // the resolver only answers this exact operation, its hash is part of the
  // signed token so a trimmed query fails server side
  var aaQuery = "\nquery(\n$showId: String!\n$translationType: VaildTranslationTypeEnumType!\n$episodeString: String!\n) {\nepisode(\nshowId: $showId\ntranslationType: $translationType\nepisodeString: $episodeString\n) {\nepisodeString\nuploadDate\nsourceUrls\nthumbnail\nnotes\nshow{\n\n\n_id\nname\nenglishName\nnativeName\nslugTime\n\nthumbnail\n\ntbObj {\n  u\n  sm\n  md\n  ts\n}\n\nlastEpisodeInfo\nlastEpisodeDate\ntype\nseason\nscore\nairedStart\navailableEpisodes\nepisodeDuration\nepisodeCount\nlastUpdateEnd\ncharacterCount\n\ndescription\nbroadcastInterval\nbanner\ncharacters\navailableEpisodesDetail\nnameOnlyString\ncharacters\nisAdult\nrelatedShows\nrelatedMangas\naltNames\ndisqusIds\n}\npageStatus{\n_id\nnotes\npageId\nshowId\n\nviews\nlikesCount\ncommentCount\ndislikesCount\nboostsCount\nreviewCount\nuserScoreCount\nuserScoreTotalValue\nuserScoreAverValue\nviewers{\nfirstViewers{\nviewCount\nlastWatchedDate\nuser{\n\n  _id\n  username\n  displayName\n  createdAt\n  picture\n  reputation\n  roleLevel\n\n  \n  brief\n  followerCount\n  followingCount\n  pDec\n  equippedBadgeKey\n  equippedBadge {\n    key\n    name\n    rank\n    iconPath\n    date\n  }\n  ugcContributorStats {\n    mediaEditReviewSubmitCount\n    mediaEditApprovedCount\n    mediaEditRejectedCount\n    mediaEditAppliedCount\n    mediaEditContributionPoints\n    mediaEditModContributionPoints\n  }\n\n  hideMe\n\n}\n}\nrecViewers{\nviewCount\nlastWatchedDate\nuser{\n\n  _id\n  username\n  displayName\n  createdAt\n  picture\n  reputation\n  roleLevel\n\n  \n  brief\n  followerCount\n  followingCount\n  pDec\n  equippedBadgeKey\n  equippedBadge {\n    key\n    name\n    rank\n    iconPath\n    date\n  }\n  ugcContributorStats {\n    mediaEditReviewSubmitCount\n    mediaEditApprovedCount\n    mediaEditRejectedCount\n    mediaEditAppliedCount\n    mediaEditContributionPoints\n    mediaEditModContributionPoints\n  }\n\n  hideMe\n\n}\n}\n}\n\n}\nepisodeInfo{\nnotes\nthumbnails\n\ntbObj {\n  u\n  sm\n  md\n  ts\n}\n\nvidInforssub\nuploadDates\nvidInforsdub\nvidInforsraw\ndescription\n}\nversionFix\n}\n}\n";

  function aaB64(s) { var t = atob(s), u = new Uint8Array(t.length); for (var i = 0; i < t.length; i++) u[i] = t.charCodeAt(i); return u; }
  function aaB64s(u) { for (var s = "", i = 0; i < u.length; i++) s += String.fromCharCode(u[i]); return btoa(s); }
  function aaCat(list) {
    var n = 0, i, at = 0;
    for (i = 0; i < list.length; i++) n += list[i].length;
    var o = new Uint8Array(n);
    for (i = 0; i < list.length; i++) { o.set(list[i], at); at += list[i].length; }
    return o;
  }
  function aaHex(u) { for (var s = '', i = 0; i < u.length; i++) s += (u[i] < 16 ? '0' : '') + u[i].toString(16); return s; }
  async function aaSha(u) { return new Uint8Array(await crypto.subtle.digest('SHA-256', u)); }
  async function aaHmac(k, m) {
    var key = await crypto.subtle.importKey('raw', k, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    return new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(m)));
  }
  async function aaGcm(k, iv, d, how) {
    var key = await crypto.subtle.importKey('raw', k, { name: 'AES-GCM' }, false, [how]);
    return new Uint8Array(await crypto.subtle[how]({ name: 'AES-GCM', iv: iv, tagLength: 128 }, key, d));
  }
  function aaTake(r) {
    var h = r && r.headers && (r.headers['set-cookie'] || r.headers['Set-Cookie']);
    if (!h) return;
    var pairs = aaCookie ? aaCookie.split('; ') : [];
    (typeof h === 'string' ? [h] : h).forEach(function (v) {
      var one = String(v).split(';')[0].trim(), k = one.split('=')[0];
      pairs = pairs.filter(function (p) { return p.split('=')[0] !== k; });
      pairs.push(one);
    });
    aaCookie = pairs.join('; ');
  }

  // the config is a literal in a minified bundle, sliced out by walking the
  // statement rather than matching a fixed shape
  function aaEnd(text, start) {
    var p = 0, b = 0, c = 0, q = '';
    for (var i = start; i < text.length; i++) {
      var ch = text[i];
      if (q) { if (ch === '\\') i++; else if (ch === q) q = ''; continue; }
      if (ch === '"' || ch === "'" || ch === '`') { q = ch; continue; }
      if (ch === '(') p++; else if (ch === ')') p--;
      else if (ch === '[') b++; else if (ch === ']') b--;
      else if (ch === '{') c++; else if (ch === '}') c--;
      else if (ch === ';' && !p && !b && !c) return i;
    }
    return -1;
  }
  function aaCutAt(text) {
    var out = [], start = 0, p = 0, b = 0, c = 0, q = '';
    for (var i = 0; i < text.length; i++) {
      var ch = text[i];
      if (q) { if (ch === '\\') i++; else if (ch === q) q = ''; continue; }
      if (ch === '"' || ch === "'" || ch === '`') { q = ch; continue; }
      if (ch === '(') p++; else if (ch === ')') p--;
      else if (ch === '[') b++; else if (ch === ']') b--;
      else if (ch === '{') c++; else if (ch === '}') c--;
      else if (ch === ',' && !p && !b && !c) { out.push(text.slice(start, i)); start = i + 1; }
    }
    out.push(text.slice(start));
    return out;
  }
  function aaBlock(text, start) {
    var depth = 0, seen = false;
    for (var i = start; i < text.length; i++) {
      if (text[i] === '{') { depth++; seen = true; }
      else if (text[i] === '}' && seen && --depth === 0) return i + 1;
    }
    return -1;
  }
  function aaFn(text, name) {
    var m = new RegExp('function\\s+' + name + '\\s*\\(').exec(text);
    if (!m) return null;
    var end = aaBlock(text, text.indexOf('{', m.index));
    return end === -1 ? null : text.slice(m.index, end);
  }
  // mask is a table of decoded strings next to the config literal, eval the
  // table and its init then read the values back out
  function aaFragmentCfg(chunk) {
    var re = /\bsaltMul\s*:/g, m;
    while ((m = re.exec(chunk)) !== null) {
      var at = chunk.lastIndexOf('const ', m.index);
      if (at < 0) continue;
      var end = aaEnd(chunk, at);
      if (end === -1 || m.index > end) continue;
      var decls = aaCutAt(chunk.slice(at + 6, end)).map(function (v) {
        var e = /^\s*([A-Za-z_$][\w$]*)\s*=\s*([\s\S]+)$/.exec(v);
        return e ? { name: e[1], expression: e[2] } : null;
      }).filter(Boolean);
      var ci = -1, i;
      for (i = 0; i < decls.length; i++) if (/\b(?:saltMul|fragMul)\s*:/.test(decls[i].expression)) { ci = i; break; }
      if (ci < 0) continue;
      var pi = -1;
      for (i = ci - 1; i >= 0; i--) if (decls[i].expression.trim().charAt(0) === '[') { pi = i; break; }
      // mask array and build id share one declaration, decls[0] is the id and
      // the mask sits after it
      var build = decls[0], parts = decls[pi];
      var params = decls[ci];
      if (!build || !parts || !params) continue;
      var expr = build.expression + ';' + parts.expression + ';' + params.expression;
      var names = {}, callRe = /\b([A-Za-z_$][\w$]*)\s*\(/g, cm;
      while ((cm = callRe.exec(expr)) !== null) names[cm[1]] = 1;
      var helpers = [], table = null;
      for (var pass = 0; pass < 8 && !table; pass++) {
        var changed = false;
        for (var name in names) {
          if (!Object.prototype.hasOwnProperty.call(names, name)) continue;
          var dup = false;
          for (i = 0; i < helpers.length; i++) if (new RegExp('function\\s+' + name + '\\s*\\(').test(helpers[i])) dup = true;
          if (dup) continue;
          var src = aaFn(chunk, name);
          if (!src) continue;
          helpers.push(src);
          var refRe = /\b([A-Za-z_$][\w$]*)\s*\(/g, rm;
          while ((rm = refRe.exec(src)) !== null) if (!names[rm[1]]) { names[rm[1]] = 1; changed = true; }
        }
        for (i = 0; i < helpers.length; i++) {
          if (/^function\s+[A-Za-z_$][\w$]*\s*\(\)\s*\{const\s+e=\[/.test(helpers[i])) { table = helpers[i]; break; }
        }
        if (!changed && !table) break;
      }
      if (!table) continue;
      var tableName = /^function\s+([A-Za-z_$][\w$]*)\s*\(/.exec(table)[1];
      var initEnd = chunk.lastIndexOf(')(' + tableName + ',', at);
      if (initEnd === -1) continue;
      var initStart = -1, depth = 0;
      for (i = initEnd; i >= 0; i--) {
        if (chunk[i] === ')') depth++;
        else if (chunk[i] === '(' && --depth === 0) { initStart = i; break; }
      }
      if (initStart < 0) continue;
      var init = chunk.slice(initStart, chunk.indexOf(';', initEnd) + 1);
      var keep = helpers.filter(function (x) { return x.indexOf('function ' + tableName) !== 0; });
      var code = table + '\n' + init + '\n' + keep.join('\n') +
        '\nreturn { buildId: (' + build.expression + '), maskParts: (' + parts.expression + '), params: (' + params.expression + ') };';
      var out;
      try { out = new Function(code)(); } catch (e) { continue; }
      if (!out || !out.buildId || !out.maskParts || out.maskParts.length < 4) continue;
      var p = out.params || {};
      if (!isFinite(Number(p.saltMul)) || !isFinite(Number(p.saltAdd))) continue;
      if (!isFinite(Number(p.fragMul)) || !isFinite(Number(p.fragAdd))) continue;
      if (!p.parts || !p.parts.length || typeof p.bootPrefix !== 'string') continue;
      return {
        scheme: 'fragments', buildId: String(out.buildId),
        maskParts: out.maskParts.slice(0, 4).map(String),
        saltMul: Number(p.saltMul), saltAdd: Number(p.saltAdd),
        fragMul: Number(p.fragMul), fragAdd: Number(p.fragAdd),
        bootPrefix: p.bootPrefix, join: typeof p.join === 'string' ? p.join : '|',
        parts: p.parts.slice(), omitEmptyLane: !!p.omitEmptyLane,
      };
    }
    return null;
  }
  async function aaDiscover(force) {
    if (!force && aaCache.cfg && Date.now() - aaCache.cfgAt < 6 * 3600 * 1000) return aaCache.cfg;
    var html = await get(aaHost + '/', aaHost + '/', 15);
    if (!html) return null;
    var m = /(?:import\(|src=)["']([^"']+\/_app\/immutable\/entry\/app\.[^"']+\.js)["']/.exec(html);
    if (!m) return null;
    var appUrl = absUrl(m[1], aaHost), app = await get(appUrl, aaHost + '/', 15);
    if (!app) return null;
    var urls = [], seen = {}, re = /["']([^"']*\/?(?:chunks|nodes)\/[^"']+\.js)["']/g, cm;
    while ((cm = re.exec(app)) !== null) {
      var u = absUrl(cm[1], appUrl);
      if (seen[u]) continue;
      seen[u] = 1;
      urls.push(u);
    }
    for (var i = 0; i < urls.length && i < 12; i += 3) {
      var batch = urls.slice(i, i + 3);
      var bodies = await Promise.all(batch.map(function (x) { return get(x, aaHost + '/', 15).catch(function () { return null; }); }));
      for (var j = 0; j < bodies.length; j++) {
        var text = bodies[j];
        if (!text || !/client-crypto|x-aa-boot|aaReq|partB/.test(text)) continue;
        var cfg = null;
        try { cfg = aaFragmentCfg(text); } catch (e) {}
        if (!cfg) continue;
        aaCache.cfg = cfg;
        aaCache.cfgAt = Date.now();
        return cfg;
      }
    }
    return null;
  }
  function aaMask(cfg) {
    var out = new Uint8Array(32), name = String(cfg.buildId), i, j, p, part, off;
    var seed = new Uint8Array(32);
    for (i = 0; i < 32; i++) {
      seed[i] = (name.charCodeAt(i % name.length) || 0) ^ (cfg.scheme === 'fragments'
        ? ((i * cfg.saltMul + cfg.saltAdd) & 255) : ((i * 17 + 31) & 255));
    }
    for (p = 0; p < cfg.maskParts.length; p++) {
      part = aaB64(cfg.maskParts[p]);
      off = p * 8;
      for (j = 0; j < 8; j++) {
        out[off + j] = cfg.scheme === 'fragments'
          ? part[j] ^ seed[off + j] ^ ((p * cfg.fragMul + j * cfg.fragAdd) & 255)
          : (part[j] ^ seed[off + j]) ^ ((p * 41 + j * 7) & 255);
      }
    }
    return out;
  }
  async function aaBoot(cfg, epoch) {
    var key = await aaHmac(aaMask(cfg), (cfg.bootPrefix || 'aa-boot:') + cfg.buildId);
    if (cfg.scheme !== 'fragments') return aaHex(await aaHmac(key, cfg.buildId + ':mkissa:mkissa.to:' + epoch + ':' + aaLane));
    var f = { buildId: cfg.buildId, group: 'mkissa', host: 'mkissa.to', epoch: String(epoch), lane: aaLane };
    var parts = cfg.omitEmptyLane && !f.lane ? cfg.parts.filter(function (p) { return p !== 'lane'; }) : cfg.parts;
    return aaHex(await aaHmac(key, parts.map(function (p) { return f[p] == null ? '' : f[p]; }).join(cfg.join)));
  }
  async function aaLaneKey(force) {
    var cfg = await aaDiscover(force);
    if (!cfg) throw new Error('config');
    if (!force && aaCache.key && aaCache.build === cfg.buildId && Date.now() - aaCache.at < 3 * 3600 * 1000) {
      return { cfg: cfg, key: aaCache.key, epoch: aaCache.epoch };
    }
    var now = Date.now(), epoch = Math.floor(now / aaEpochMs);
    if (now - epoch * aaEpochMs < aaGraceMs && epoch > 0) epoch -= 1;
    var url = aaApi + '/client-crypto/v1/bootstrap?buildId=' + encodeURIComponent(cfg.buildId) + '&k=' + aaLane;
    var boot = null;
    for (var i = 0; i < 2 && !boot; i++) {
      var r = await http({
        url: url, referer: aaHost + '/', timeout: 12,
        headers: { origin: aaHost, 'x-build-id': cfg.buildId, 'x-aa-boot': await aaBoot(cfg, epoch + i), cookie: aaCookie },
      });
      aaTake(r);
      if (!r || r.code !== 200) continue;
      try { var j = JSON.parse(r.body); if (j && j.partB) boot = j; } catch (e) {}
    }
    if (!boot) throw new Error('bootstrap');
    var mask = aaMask(cfg), part = aaB64(boot.partB), key = new Uint8Array(32);
    for (var k = 0; k < 32; k++) key[k] = part[k] ^ mask[k % mask.length];
    aaCache.key = key;
    aaCache.epoch = boot.epoch;
    aaCache.build = cfg.buildId;
    aaCache.at = Date.now();
    return { cfg: cfg, key: key, epoch: boot.epoch };
  }
  async function aaToken(lane, qh) {
    var ts = Math.floor(Date.now() / aaReqMs) * aaReqMs;
    var iv = (await aaSha(new TextEncoder().encode(lane.epoch + ':' + lane.cfg.buildId + ':' + qh + ':' + ts + ':' + aaLane))).subarray(0, 12);
    var plain = new TextEncoder().encode(JSON.stringify({ v: 1, ts: ts, epoch: lane.epoch, buildId: lane.cfg.buildId, qh: qh, k: aaLane }));
    return aaB64s(aaCat([new Uint8Array([1]), iv, await aaGcm(lane.key, iv, plain, 'encrypt')]));
  }
  // shows is not gated, only the resolver is
  async function aaFind(title) {
    var q = 'query($search:SearchInput,$limit:Int,$page:Int,$translationType:VaildTranslationTypeEnumType,$countryOrigin:VaildCountryOriginEnumType){shows(search:$search,limit:$limit,page:$page,translationType:$translationType,countryOrigin:$countryOrigin){edges{_id name englishName slugTime availableEpisodes}}}';
    var body = JSON.stringify({
      query: q,
      variables: { search: { allowAdult: false, allowUnknown: false, query: title }, limit: 20, page: 1, translationType: 'sub', countryOrigin: 'ALL' },
    });
    var r = await http({ url: aaApi + '/api', referer: aaHost + '/', method: 'POST', body: body, contentType: 'application/json', timeout: 15, headers: { origin: aaHost, cookie: aaCookie } });
    aaTake(r);
    var root = null;
    try { root = JSON.parse(r && r.body); } catch (e) { return null; }
    var list = (root && root.data && root.data.shows && root.data.shows.edges) || [];
    var want = normTitle(title), best = null, near = null;
    for (var i = 0; i < list.length; i++) {
      var it = list[i] || {}, names = [it.name, it.englishName].filter(Boolean).map(normTitle);
      if (names.indexOf(want) >= 0) { best = it; break; }
      if (!near) for (var j = 0; j < names.length; j++) if (names[j].indexOf(want) === 0) { near = it; break; }
    }
    var hit = best || near;
    if (!hit || !hit._id) return null;
    var eps = hit.availableEpisodes || {};
    return { id: hit._id, sub: eps.sub || 0, dub: eps.dub || 0, slug: hit.slugTime || '' };
  }
  // the api rate limits by burst and answers with the seconds to wait
  async function aaWait(r) {
    var m = r && r.body && /try again in (\d+)/.exec(r.body);
    if (!m) return r;
    await new Promise(function (res) { setTimeout(res, Math.min(4, parseInt(m[1], 10) || 2) * 1000); });
    return null;
  }
  async function aaCut(lane, showId, ep, audio) {
    var qh = aaHex(await aaSha(new TextEncoder().encode(aaQuery)));
    var body = JSON.stringify({
      query: aaQuery,
      variables: { showId: showId, translationType: audio, episodeString: String(ep) },
      extensions: { aaReq: await aaToken(lane, qh), k: aaLane },
    });
    var post = function () {
      return http({
        url: aaApi + '/api', referer: aaHost + '/', method: 'POST', body: body,
        contentType: 'application/json', timeout: 15,
        headers: { origin: aaHost, 'x-build-id': lane.cfg.buildId, cookie: aaCookie },
      });
    };
    var r = await aaWait(await post()) || await post();
    aaTake(r);
    // a burned address gets NEED_CAPTCHA back for a long stretch: a fresh bootstrap,
    // a get instead of a post and a nine minute wait all still hit it, so the leg
    // answers empty and the app falls through rather than spending its budget
    if (!r || r.error || r.code !== 200) return null;
    var root;
    try { root = JSON.parse(r.body); } catch (e) { return null; }
    var data = root.data || {};
    if (data.tobeparsed) {
      var raw = aaB64(data.tobeparsed);
      if (raw[0] !== 1) return null;
      try {
        var plain = await aaGcm(lane.key, raw.subarray(1, 13), raw.subarray(13), 'decrypt');
        data = JSON.parse(new TextDecoder().decode(plain));
      } catch (e) { return null; }
    }
    return data.episode || null;
  }
  // only the player cut is a file the app can hand to exoplayer, the rest are
  // embeds with their own extractors
  function aaFile(ep) {
    var list = (ep && ep.sourceUrls) || [];
    for (var i = 0; i < list.length; i++) if (list[i].type === 'player' && /^https?:\/\//.test(list[i].sourceUrl || '')) return list[i].sourceUrl;
    for (var j = 0; j < list.length; j++) if (/^Yt-mp4$/.test(list[j].sourceName || '') && /^https?:\/\//.test(list[j].sourceUrl || '')) return list[j].sourceUrl;
    return '';
  }
  async function allanime(ctx) {
    if (pwSkip('allanime', ctx)) return skipped();
    var cfg = ctx.cfg || {};
    // mirrors move, both hosts come from remote config rather than constants
    aaHost = (cfg.site || 'https://mkissa.to').replace(/\/+$/, '');
    aaApi = (cfg.api || 'https://api.mkissa.net').replace(/\/+$/, '');
    var base = ctx.tmdbBase || 'https://api.themoviedb.org/3';
    var kind = ctx.kind === 'movie' ? 'movie' : 'tv';
    var meta = await get(base + '/' + kind + '/' + ctx.tmdbId + '?api_key=' + ctx.tmdbKey, 'https://www.themoviedb.org/', 10);
    if (meta === null) return empty();
    var title = '', anim = false;
    try {
      var o = JSON.parse(meta);
      title = (o.name || o.title || '').split(':')[0].trim();
      var genres = o.genres || [];
      for (var gi = 0; gi < genres.length; gi++) if (genres[gi].id === 16) { anim = true; break; }
    } catch (e) {}
    if (!anim) return skipped();
    if (!title) return empty();
    var show = await aaFind(title);
    if (!show) return empty();
    var ep = ctx.kind === 'movie' ? 1 : (parseInt(ctx.episode, 10) || 1);
    var lane;
    try { lane = await aaLaneKey(false); } catch (e) { return empty(); }
    var cuts = await Promise.all([
      aaCut(lane, show.id, ep, 'sub').catch(function () { return null; }),
      show.dub > 0 ? aaCut(lane, show.id, ep, 'dub').catch(function () { return null; }) : null,
    ]);
    var formats = [], sub = aaFile(cuts[0]), dub = aaFile(cuts[1]);
    if (sub) {
      var f = fmt(sub, false, 'allanime-1080p', 1080);
      f.referer = aaHost + '/';
      // the sub cut ships burned in, its mp4 text track carries no samples
      f.enSubs = true;
      formats.push(f);
    }
    if (dub) {
      var d = fmt(dub, false, 'allanime-dub-1080p', 1080);
      d.referer = aaHost + '/';
      d.dub = 'English';
      formats.push(d);
    }
    return formats.length ? { formats: formats, subs: [] } : empty();
  }
  var legs = {
    love: love, rock: rock, vix: vix, zee: zee, light: light,
    paradise: paradise, vap: vap, vidlink: vidlink, hianime: hianime, pinoy: pinoy,
    net79: net79, videasy: videasy, allanime: allanime,
  };

  try {
    pw.log('subtle=' + !!(window.crypto && window.crypto.subtle) + ' secure=' + window.isSecureContext);
  } catch (e) {}
  window.__pwBridge = { httpDone: bridgeDone, httpFail: bridgeFail, voeDone: bridgeDone };
  window.__pwRank = pwRank;
  window.__pwSkip = pwSkip;
  window.__pwSort = function (id, formatsJson) {
    var list;
    try { list = JSON.parse(formatsJson); } catch (e) { pw.fail(id, 'sort args'); return; }
    var order = pwRank(list).map(function (f) { return f.url; });
    pw.done(id, JSON.stringify({ order: order }));
  };
  window.__pwRun = function (id, name, argsJson) {
    var args;
    try { args = JSON.parse(argsJson); } catch (e) { pw.fail(id, 'args ' + e); return; }
    args.http = http;
    args.log = function (m) { try { pw.log(String(m)); } catch (e) {} };
    var fn = legs[name];
    if (!fn) { pw.fail(id, 'unknown leg ' + name); return; }
    Promise.resolve().then(function () { return fn(args); })
      .then(function (r) { pw.done(id, JSON.stringify(r)); })
      .catch(function (e) { pw.fail(id, String(e && e.message || e)); });
  };
})();
