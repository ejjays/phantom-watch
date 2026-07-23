// contract: __pwRun(id, leg, argsJson); window.pw = { http, done, fail, log }
// resolve(ctx) -> { formats: [{url,isHls,label,height,english,referer,headers?,enSubs}], subs: [{label,url,src}] }
// a leg that cannot serve the title returns { formats: [], subs: [], skipped: true }
(function () {
  var pending = new Map();
  var seq = 0;

  function skipped() {
    return { formats: [], subs: [], skipped: true };
  }

  // canonical pick order: real height first, then 1080p+ with EN subs, adaptive
  // masters, EN subs, english audio, then height
  function rankOf(f) {
    var h = typeof f.height === 'number' ? f.height : 0;
    return [
      h < 0 ? 0 : 1,
      (f.enSubs && h >= 1080) ? 1 : 0,
      /-auto$/.test(f.label || '') ? 1 : 0,
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

  // legs that cannot serve this title at all; the app and probe.js share this
  function pwSkip(name, ctx) {
    ctx = ctx || {};
    if (name === 'vap') return !ctx.imdb;
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

  async function get(url, referer, timeout) {
    var r = await http({ url: url, referer: referer || '', timeout: timeout || 10 });
    return r && r.code === 200 ? r.body : null;
  }

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
      return get(u, embed).catch(function () { return null; });
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

  function urlencode(s) {
    return encodeURIComponent(s)
      .replace(/[!'()~]/g, function (c) { return '%' + c.charCodeAt(0).toString(16).toUpperCase(); })
      .replace(/%20/g, '+');
  }

  function maxHeight(list) {
    return list.length ? Math.max.apply(null, list.map(function (f) { return f.height; })) : 0;
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
    return { formats: [auto].concat(rungs), subs: [] };
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
    var cfg = ctx.cfg || {};
    var origin = cfg.origin || 'https://megaplay.buzz';
    var key = cfg.key || 'i?LMTAx0Q6,:}50U';
    var iv = cfg.iv || 'hlsMediaAttached';
    var ep = ctx.kind === 'movie' ? 'movie' : 'tv';
    var base = ctx.tmdbBase || 'https://api.themoviedb.org/3';
    var meta = await get(base + '/' + ep + '/' + ctx.tmdbId +
      '?api_key=' + ctx.tmdbKey, 'https://www.themoviedb.org/');
    if (meta === null) return empty();
    var title = '';
    try {
      var o = JSON.parse(meta);
      var t = o.name || '';
      if (!t) t = o.title || '';
      title = t.split(':')[0].trim();
    } catch (e) {}
    if (!title) return empty();
    var gql = JSON.stringify({
      query: 'query($s:String){Media(search:$s,type:ANIME){idMal}}',
      variables: { s: title },
    });
    var resp = await postJson('https://graphql.anilist.co', 'https://anilist.co/', gql, 8);
    var mal = (resp && resp.data && resp.data.Media && resp.data.Media.idMal) || 0;
    if (mal <= 0) return empty();
    var n = parseInt(ctx.episode, 10);
    var epNum = ctx.kind === 'movie' ? 1 : (isNaN(n) || n <= 0 ? 1 : n);
    var page = origin + '/stream/mal/' + mal + '/' + epNum + '/sub';
    var html = await raw(page, page, 10);
    if (html === null) return empty();
    var idm = /data-id=["'](\d+)["']/.exec(html);
    if (!idm) return empty();
    var srcBody = await get(origin + '/stream/getSources?id=' + idm[1] + '&id=' + idm[1], page, 10);
    if (srcBody === null) return empty();
    var srcObj;
    try { srcObj = JSON.parse(srcBody); } catch (e) { srcObj = null; }
    var enc = (srcObj && srcObj.enc) || '';
    if (!enc) return empty();
    var master = '';
    try {
      var m = /([a-z0-9.-]+\.[a-z]+\/anime\/[a-f0-9]+\/[a-f0-9]+\/master\.m3u8)/
        .exec(await megaDecrypt(enc, key, iv));
      if (m) master = 'https://' + m[1];
    } catch (e) {}
    if (!master) return empty();
    var manifest = await raw(master, origin + '/', 10);
    if (manifest === null || manifest.indexOf('#EXTM3U') < 0) return empty();
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
    return { formats: [auto].concat(rungs), subs: subs };
  }

  function normTitle(t) {
    return String(t || '').toLowerCase().replace(/[^a-z0-9]+/g, '');
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
    var uid = '';
    var eps = await getJson('/anime/' + showId + '/episode');
    var arr = (eps && eps.data) || [];
    for (var j = 0; j < arr.length; j++) {
      var e = arr[j] || {};
      var num = e.number == null ? '' : String(e.number);
      if (num === String(ctx.episode) && e.uid) {
        uid = e.uid;
        break;
      }
    }
    if (!uid) return empty();
    var epData = await getJson('/ep/' + uid + '?origin=' + showId);
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
    return { formats: [auto].concat(rungs), subs: subs };
  }

  var legs = {
    love: love, rock: rock, vix: vix, zee: zee,
    paradise: paradise, vap: vap, vidlink: vidlink, hianime: hianime,
  };

  try {
    pw.log('subtle=' + !!(window.crypto && window.crypto.subtle) + ' secure=' + window.isSecureContext);
  } catch (e) {}
  window.__pwBridge = { httpDone: bridgeDone, httpFail: bridgeFail };
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
