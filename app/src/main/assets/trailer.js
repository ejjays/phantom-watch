(function () {
  if (window.__trOn) return;
  window.__trOn = true;
  var post = function (m) { window.TrailerBridge.post(JSON.stringify(m)); };
  var log = function (s) { post({ log: s }); };

  var C = window.__trCfg || {};
  var Innertube = null, BG = null, armed = null, arming = null;
  var CLIENTS = C.clients && C.clients.length ? C.clients : ['VISIONOS', 'ANDROID_VR', 'IOS'];
  var TTL = 6 * 60 * 60 * 1000;

  function bgUrls(p) {
    if (C.bg) return [C.bg + p + '?bundle'];
    return [
      'https://esm.sh/bgutils-js@4.0.3/' + p + '?bundle',
      'https://cdn.jsdelivr.net/npm/bgutils-js@4.0.3/dist/exports/' + p + '.js/+esm'
    ];
  }

  function imp(urls, ms) {
    var i = 0, last = null;
    function next() {
      if (i >= urls.length) return Promise.reject(last || new Error('import failed'));
      var u = urls[i++];
      return Promise.race([
        import(u),
        new Promise(function (_, rej) { setTimeout(function () { rej(new Error('timeout')); }, ms); })
      ]).catch(function (e) { last = e; return next(); });
    }
    return next();
  }

  var waits = {};
  window.__trHttpDone = function (id, json) {
    var w = waits[id];
    if (!w) return;
    delete waits[id];
    try {
      var r = JSON.parse(json);
      if (r.error) { w.reject(new Error(r.error)); return; }
      w.resolve(new Response(r.body || '', { status: r.code || 200, headers: r.headers || {} }));
    } catch (e) { w.reject(e); }
  };

  // every request goes through okhttp: a data: document has no origin, so youtube's
  // innertube POST would fail cors before it ever reached the network
  function pfetch(input, init) {
    return new Promise(function (resolve, reject) {
      var req = new Request(input, init);
      var id = 'f' + Date.now() + Math.random().toString(36).slice(2);
      waits[id] = { resolve: resolve, reject: reject };
      var h = {};
      req.headers.forEach(function (v, k) { h[k] = v; });
      var hasBody = req.method !== 'GET' && req.method !== 'HEAD';
      (hasBody ? req.text() : Promise.resolve('')).then(function (body) {
        window.TrailerBridge.http(id, JSON.stringify({ url: req.url, method: req.method, headers: h, body: body }));
      }).catch(function (e) { delete waits[id]; reject(e); });
    });
  }

  function ensureBG() {
    if (BG) return Promise.resolve(BG);
    return Promise.all([
      imp(bgUrls('botguard'), 25000),
      imp(bgUrls('webpo'), 25000),
      imp(bgUrls('utils'), 25000)
    ]).then(function (m) {
      BG = Object.assign({}, m[0], m[1], m[2]);
      return BG;
    });
  }

  // botguard's endpoints are cors-open for the youtube.com origin this document claims,
  // so the browser's own fetch is preferred; the proxy is only a fallback
  function bgFetch(u, i) {
    return fetch(u, i).catch(function (e) {
      log('bg fetch native ' + ((e && e.message) || e));
      return pfetch(u, i);
    });
  }

  async function mint(visitorData) {
    var challenge = await BG.getChallenge({ requestKey: 'O43z0dpjhgX20SCx4KAo', fetchFunction: bgFetch });
    if (!challenge) throw new Error('challenge empty');
    var script = challenge.interpreterJavascript && challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
    if (script) new Function(script)();
    var botguard = await BG.BotGuardClient.create({ program: challenge.program, globalName: challenge.globalName, globalObject: window });
    var out = [];
    var response = await botguard.snapshot({ webPoSignalOutput: out });
    var r = await bgFetch(BG.buildURL('GenerateIT', false), {
      method: 'POST', headers: BG.getHeaders(), body: JSON.stringify(['O43z0dpjhgX20SCx4KAo', response])
    });
    if (!r.ok) throw new Error('GenerateIT ' + r.status);
    var j = await r.json();
    var minter = await BG.WebPoMinter.create({ integrityToken: j[0], estimatedTtlSecs: j[1] }, out);
    return { poToken: await minter.mintAsWebsafeString(visitorData), ttlMs: j[1] ? j[1] * 1000 : 0 };
  }

  async function arm() {
    var base = await Innertube.create({ retrieve_player: false, fetch: pfetch });
    var visitorData = base.session.context.client.visitorData;
    await ensureBG();
    var tok = await mint(visitorData);
    var yt = await Innertube.create({ po_token: tok.poToken, visitor_data: visitorData, generate_session_locally: true, fetch: pfetch });
    return { yt: yt, expiresAt: Date.now() + Math.max((tok.ttlMs || TTL) - 300000, 60000) };
  }

  function getArmed() {
    if (armed && Date.now() < armed.expiresAt) return Promise.resolve(armed);
    if (arming) return arming;
    arming = arm().then(function (b) { armed = b; return b; }).finally(function () { arming = null; });
    return arming;
  }

  async function urlOf(f, player) {
    if (typeof f.url === 'string' && f.url) return f.url;
    try {
      var d = await f.decipher(player);
      if (typeof d === 'string') return d;
      if (d && typeof d.url === 'string') return d.url;
    } catch (e) {}
    return null;
  }

  window.__trResolve = async function (reqId, videoId) {
    try {
      var b = await getArmed();
      var last = 'no client';
      for (var i = 0; i < CLIENTS.length; i++) {
        var client = CLIENTS[i];
        try {
          var info = await b.yt.getInfo(videoId, { client: client });
          var sd = info.streaming_data || {};
          if (sd.hls_manifest_url) {
            post({ reqId: reqId, ok: true, url: sd.hls_manifest_url, client: client });
            return;
          }
          var vids = (sd.adaptive_formats || []).filter(function (f) { return f.has_video && !f.has_audio; });
          vids.sort(function (x, y) { return (y.height || 0) - (x.height || 0); });
          var aud = (sd.adaptive_formats || []).filter(function (f) { return f.has_audio && !f.has_video; });
          aud.sort(function (x, y) { return (y.bitrate || 0) - (x.bitrate || 0); });
          if (vids.length && aud.length) {
            var v = await urlOf(vids[0], b.yt.session.player);
            var a = await urlOf(aud[0], b.yt.session.player);
            if (v && a) {
              post({ reqId: reqId, ok: true, pair: { video: v, audio: a }, client: client });
              return;
            }
          }
          last = client + ': no stream';
        } catch (e) {
          last = client + ': ' + ((e && e.message) || e);
        }
      }
      post({ reqId: reqId, ok: false, error: last });
    } catch (e) {
      post({ reqId: reqId, ok: false, error: String((e && e.message) || e) });
    }
  };

  log('boot youtubei');
  imp(C.ytjs && C.ytjs.length ? C.ytjs : [
    'https://cdn.jsdelivr.net/npm/youtubei.js@18/bundle/browser.js',
    'https://unpkg.com/youtubei.js@18/bundle/browser.js',
    'https://esm.sh/youtubei.js@18?bundle'
  ], 25000)
    .then(function (m) { Innertube = m.Innertube; log('youtubei ok'); return getArmed(); })
    .then(function () { log('armed'); post({ boot: true }); })
    .catch(function (e) { log('boot fail ' + ((e && e.message) || e)); post({ boot: true, error: String((e && e.message) || e) }); });
})();
