// End-to-end: resolve a real title through the Oracle proxy, feed the exact JSON WebPlayer.kt
// builds into pwAttach, and check that the video actually plays with the chrome over it.
// usage: node playtest.cjs [movieId] [tvId:season:episode]
const { spawn } = require('node:child_process');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const WEB = path.join(ROOT, 'spike', 'web');
const OUT = process.env.HOME;
const PORT = 9361;
const WEB_PORT = 8767;

function prop(name) {
  const m = fs.readFileSync(path.join(ROOT, 'local.properties'), 'utf8').match(new RegExp('^' + name + '=(.+)$', 'm'));
  return m ? m[1].trim() : '';
}
const PW_URL = prop('PW_URL');
const PW_KEY = prop('PW_KEY');

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm' };
const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]);
  const file = path.join(WEB, rel);
  if (!file.startsWith(WEB) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream', 'content-length': fs.statSync(file).size });
  fs.createReadStream(file).pipe(res);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const get = (p) => new Promise((res, rej) => http.get('http://127.0.0.1:' + PORT + p, (r) => {
  let b = ''; r.on('data', (c) => (b += c)); r.on('end', () => { try { res(JSON.parse(b)); } catch (e) { rej(e); } });
}).on('error', rej));

async function resolve(body) {
  const r = await fetch(PW_URL + '/resolve', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-pw-key': PW_KEY },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(90000),
  });
  if (!r.ok) throw new Error('resolve ' + r.status);
  return r.json();
}

(async () => {
  const movieId = process.argv[2] || '603';
  const tv = (process.argv[3] || '62715:1:1').split(':');

  let movie = { formats: [] };
  if (movieId !== '-') {
    console.log('resolve movie', movieId, '...');
    movie = await resolve({ kind: 'movie', id: movieId });
    console.log('  formats', movie.formats.length, 'pick', movie.pick && movie.pick.label, 'ms', movie.ms);
  }

  console.log('resolve tv', tv.join(':'), '...');
  let show = { formats: [] };
  try { show = await resolve({ kind: 'tv', id: tv[0], season: tv[1], episode: tv[2] }); } catch (e) { console.log('  tv resolve failed', e.message); }
  console.log('  formats', show.formats.length, 'pick', show.pick && show.pick.label, 'hls', !!(show.pick && show.pick.hls));

  await new Promise((r) => server.listen(WEB_PORT, '127.0.0.1', r));
  const chrome = spawn('chromium-browser', ['--headless=new', '--no-sandbox', '--enable-unsafe-swiftshader',
    '--disable-dev-shm-usage', '--autoplay-policy=no-user-gesture-required', '--window-size=1280,720',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + OUT + '/.pwplay', 'about:blank'], { stdio: 'ignore' });

  let list = null;
  for (let i = 0; i < 60 && !list; i++) { await sleep(250); try { list = await get('/json/list'); } catch (e) {} }
  const ws = new WebSocket(list[0].webSocketDebuggerUrl);
  let id = 0; const pend = new Map();
  const call = (m, p) => new Promise((res) => { const n = ++id; pend.set(n, res); ws.send(JSON.stringify({ id: n, method: m, params: p })); });
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pend.has(m.id)) { pend.get(m.id)(m.result); pend.delete(m.id); }
    if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      console.log('  ERR', m.params.args.map((a) => a.value || a.description || '').join(' ').slice(0, 200));
    }
  };
  await new Promise((r) => (ws.onopen = r));
  await call('Page.enable'); await call('Runtime.enable'); await call('Network.enable');
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.method === 'Network.responseReceived' && /\/p\?/.test(m.params.response.url)) {
      console.log('  NET', m.params.response.status, m.params.response.mimeType, m.params.response.url.slice(0, 90));
    }
    if (m.method === 'Network.loadingFailed' && m.params.type === 'XHR') {
      console.log('  NETFAIL', m.params.errorText);
    }
  });
  await call('Emulation.setDeviceMetricsOverride', { width: 1280, height: 720, deviceScaleFactor: 1, mobile: false });

  const js = async (e) => (await call('Runtime.evaluate', { expression: e, returnByValue: true })).result.value;

  async function play(label, formats, shot) {
    const list = formats.slice(0, 12).map((f) => ({ u: f.url, h: !!f.hls }));
    const html = `<!doctype html><meta charset="utf-8">
<style>html,body{margin:0;height:100%;background:#000}</style>
<script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.17/dist/hls.min.js"></script>
<script src="./icons.js"></script>
<script src="./player.js"></script>
<video id="v" playsinline autoplay></video>
<script>
var v = document.getElementById('v');
v.style.position='fixed'; v.style.background='#000'; v.style.objectFit='contain';
v.style.left='0px'; v.style.top='0px'; v.style.width=innerWidth+'px'; v.style.height=innerHeight+'px';
window.pwTitle(v, ${JSON.stringify(label)});
window.pwAttach(v, ${JSON.stringify(JSON.stringify(list))});
</script>`;
    fs.writeFileSync(path.join(WEB, '__play.html'), html);
    await call('Page.navigate', { url: 'http://127.0.0.1:' + WEB_PORT + '/__play.html?t=' + Date.now() });
    for (let i = 0; i < 10; i++) {
      await sleep(2000);
      const l = await js('(function(){var v=document.querySelector("video");return v.readyState+"|"+v.currentTime.toFixed(2)+"|"+(v.buffered.length?v.buffered.end(0).toFixed(0):0)})()');
      console.log('    t+' + ((i + 1) * 2) + 's rs|t|buf ' + l);
      if (parseFloat(l.split('|')[1]) > 2) break;
    }
    const a = await js('document.querySelector("video").currentTime');
    await sleep(3000);
    const state = await js(`(function(){var v=document.querySelector("video");
      return JSON.stringify({rs:v.readyState, t:+v.currentTime.toFixed(2), dur:+v.duration.toFixed(1),
        buf:v.buffered.length?+v.buffered.end(0).toFixed(1):0, err:v.error?v.error.code:0,
        paused:v.paused, muted:v.muted, hls:!!v.__pwHls, vw:v.videoWidth, vh:v.videoHeight,
        stage:!!document.querySelector('.pw-stage'), clock:document.querySelector('.pw-clock').textContent,
        src:(v.currentSrc||'').slice(0,70)})})()`);
    const s = JSON.parse(state);

    console.log('  ' + label + ':', state, '| advanced', (s.t - a).toFixed(2), 's in 3s');

    // real seek through the proxy: 10 min in, then confirm it lands and keeps playing
    if (s.dur > 700) {
      await js('document.querySelector("video").currentTime = 600');
      await sleep(2500);
      const after = await js('(function(){var v=document.querySelector("video");return JSON.stringify({t:+v.currentTime.toFixed(1), rs:v.readyState, err:v.error?v.error.code:0})})()');
      console.log('  seek to 600s ->', after);
      await sleep(2000);
      console.log('  still moving ->', await js('document.querySelector("video").currentTime.toFixed(1)'));
    }

    if (shot) {
      // controls auto-hide after 3 s; one centre tap brings them back
      await js('document.querySelector(".pw-zone[data-z=c]").click()');
      await sleep(400);
      const r = await call('Page.captureScreenshot', { format: 'png' });
      fs.writeFileSync(path.join(OUT, shot), Buffer.from(r.data, 'base64'));
      console.log('  shot', shot);
    }
    fs.unlinkSync(path.join(WEB, '__play.html'));
    return s;
  }

  if (movieId !== '-') {
    console.log('MP4 path:');
    await play('movie ' + movieId, movie.formats, 'p-mp4.png');
  }
  if (show.formats.length) {
    console.log('HLS path:');
    await play('tv ' + tv.join('x'), show.formats, 'p-hls.png');
  }

  chrome.kill();
  server.close();
  process.exit(0);
})();
