// Drives the standalone player chrome page over CDP: screenshots, geometry, interaction.
// Serves spike/web itself so the media file answers Range requests (python's http.server
// does not, and a non-seekable <video> cannot exercise the scrubber).
// usage: node chrometest.cjs
const { spawn } = require('node:child_process');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const WEB = path.join(__dirname, '..', 'web');
const PORT = 9341;
const WEB_PORT = 8766;
const OUT = process.env.HOME;
const PAGE = 'http://127.0.0.1:' + WEB_PORT + '/__player.html';
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm', '.wav': 'audio/wav' };

function writeWav(file) {
  const n = 8000 * 30, b = Buffer.alloc(44 + n * 2);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n * 2, 4); b.write('WAVE', 8);
  b.write('fmt ', 12); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22);
  b.writeUInt32LE(8000, 24); b.writeUInt32LE(16000, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34);
  b.write('data', 36); b.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i++) b.writeInt16LE(Math.round(3000 * Math.sin(2 * Math.PI * 440 * i / 8000)), 44 + i * 2);
  fs.writeFileSync(file, b);
}

function setup() {
  writeWav(path.join(WEB, '__t.wav'));
  const poster = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720"><defs>' +
    '<linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1b3a4b"/>' +
    '<stop offset="0.5" stop-color="#7a3b52"/><stop offset="1" stop-color="#c98b3a"/></linearGradient></defs>' +
    '<rect width="1280" height="720" fill="url(#g)"/><text x="640" y="380" font-size="90" fill="#ffffff55" ' +
    'text-anchor="middle">frame</text></svg>');
  fs.writeFileSync(path.join(WEB, '__player.html'), `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000}</style>
<script src="./icons.js"></script><script src="./player.js"></script></head><body>
<video id="v" playsinline autoplay></video>
<script>
var v = document.getElementById('v');
v.style.position='fixed'; v.style.background='#000'; v.style.objectFit='contain';
v.style.left='0px'; v.style.top='0px'; v.style.width=innerWidth+'px'; v.style.height=innerHeight+'px';
v.poster = '${poster}';
window.pwTitle(v, 'The Bear · S1 E3');
window.pwAttach(v, JSON.stringify([{u:'./__t.wav',h:false}]));
</script></body></html>`);
}

const server = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]);
  const file = path.join(WEB, rel);
  if (!file.startsWith(WEB) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); return res.end();
  }
  const size = fs.statSync(file).size;
  const type = MIME[path.extname(file)] || 'application/octet-stream';
  const range = req.headers.range;
  if (range) {
    const m = /bytes=(\d*)-(\d*)/.exec(range);
    const start = m && m[1] ? parseInt(m[1], 10) : 0;
    const end = m && m[2] ? parseInt(m[2], 10) : size - 1;
    res.writeHead(206, {
      'content-type': type, 'accept-ranges': 'bytes',
      'content-range': `bytes ${start}-${end}/${size}`, 'content-length': end - start + 1,
    });
    return fs.createReadStream(file, { start, end }).pipe(res);
  }
  res.writeHead(200, { 'content-type': type, 'accept-ranges': 'bytes', 'content-length': size });
  fs.createReadStream(file).pipe(res);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const get = (p) => new Promise((res, rej) => http.get('http://127.0.0.1:' + PORT + p, (r) => {
  let b = ''; r.on('data', (c) => (b += c)); r.on('end', () => { try { res(JSON.parse(b)); } catch (e) { rej(e); } });
}).on('error', rej));

(async () => {
  setup();
  await new Promise((r) => server.listen(WEB_PORT, '127.0.0.1', r));

  const chrome = spawn('chromium-browser', [
    '--headless=new', '--no-sandbox', '--enable-unsafe-swiftshader', '--disable-dev-shm-usage',
    '--autoplay-policy=no-user-gesture-required', '--window-size=1280,720',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + OUT + '/.pwchrome_t', 'about:blank',
  ], { stdio: 'ignore' });

  let list = null;
  for (let i = 0; i < 60 && !list; i++) { await sleep(250); try { list = await get('/json/list'); } catch (e) {} }
  const ws = new WebSocket(list[0].webSocketDebuggerUrl);
  let id = 0; const pend = new Map();
  const call = (method, params) => new Promise((res) => { const n = ++id; pend.set(n, res); ws.send(JSON.stringify({ id: n, method, params })); });
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pend.has(m.id)) { pend.get(m.id)(m.result); pend.delete(m.id); }
    if (m.method === 'Runtime.exceptionThrown') console.log('EXC', JSON.stringify(m.params.exceptionDetails).slice(0, 400));
  };
  await new Promise((r) => (ws.onopen = r));
  await call('Page.enable'); await call('Runtime.enable');
  await call('Emulation.setDeviceMetricsOverride', { width: 1280, height: 720, deviceScaleFactor: 1, mobile: false });
  await call('Page.navigate', { url: PAGE });
  await sleep(2500);

  const js = async (e) => {
    const r = await call('Runtime.evaluate', { expression: e, returnByValue: true, awaitPromise: true });
    return r && r.result ? r.result.value : undefined;
  };
  const shot = async (name) => {
    const r = await call('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUT, name), Buffer.from(r.data, 'base64'));
  };

  console.log('stage:', await js('!!document.querySelector(".pw-stage")'));
  console.log('controls on:', await js('document.querySelector(".pw-stage").dataset.on'));
  console.log('clock:', await js('document.querySelector(".pw-clock").textContent'));
  console.log('media:', await js(`(function(){var v=document.querySelector("video");
    return 'rs='+v.readyState+' seekable='+(v.seekable.length?v.seekable.end(0):'none')+
      ' dur='+v.duration+' paused='+v.paused})()`));
  console.log('geom:', await js(`(function(){
    var q=function(s){var e=document.querySelector(s); if(!e) return s+':missing';
      var r=e.getBoundingClientRect(), c=getComputedStyle(e);
      return s+' '+Math.round(r.left)+','+Math.round(r.top)+' '+Math.round(r.width)+'x'+Math.round(r.height)+
        ' font='+c.fontSize+'/'+c.fontWeight+' vis='+c.visibility+' op='+c.opacity;};
    return [q('.pw-top'),q('.pw-back'),q('.pw-title'),q('.pw-cc'),q('.pw-gear'),q('.pw-bottom'),
      q('.pw-seek'),q('.pw-clock'),q('.pw-mute'),q('.pw-fs'),q('.pw-playbtn'),q('.pw-scrim')].join('\\n');
  })()`));
  await shot('t-controls.png');

  await js('document.querySelector(".pw-zone[data-z=c]").click()');
  await sleep(60);
  await js('document.querySelector(".pw-zone[data-z=c]").click()');
  await sleep(300);
  console.log('after centre double tap, paused:', await js('document.querySelector("video").paused'));
  console.log('play glyph:', await js('getComputedStyle(document.querySelector(".pw-playbtn svg")).width'));
  await shot('t-paused.png');

  await js('document.querySelector(".pw-zone[data-z=r]").click()');
  await sleep(80);
  await js('document.querySelector(".pw-zone[data-z=r]").click()');
  await sleep(200);
  console.log('after +10, t=', await js('document.querySelector("video").currentTime'));
  console.log('hud:', await js('(function(){var h=document.querySelector(".pw-hud");return h.dataset.on+" side="+h.dataset.side+" n="+h.querySelector(".pw-hud-n").textContent})()'));
  await shot('t-hud.png');

  const b = JSON.parse(await js('(function(){var r=document.querySelector(".pw-seek").getBoundingClientRect();return JSON.stringify({x:r.left,y:r.top,w:r.width,h:r.height})})()'));
  const y = b.y + b.h / 2;
  await call('Input.dispatchMouseEvent', { type: 'mousePressed', x: b.x + b.w * 0.2, y, button: 'left', clickCount: 1 });
  await call('Input.dispatchMouseEvent', { type: 'mouseMoved', x: b.x + b.w * 0.5, y, button: 'left', buttons: 1 });
  await sleep(150);
  console.log('scrub label:', await js('document.querySelector(".pw-scrubtime").textContent + " on=" + document.querySelector(".pw-scrubtime").dataset.on'));
  await shot('t-scrub.png');
  await call('Input.dispatchMouseEvent', { type: 'mouseReleased', x: b.x + b.w * 0.5, y, button: 'left', clickCount: 1 });
  await sleep(400);
  console.log('after seek t=', await js('document.querySelector("video").currentTime'));

  await js('document.querySelector(".pw-stage").dataset.fs="1"');
  await sleep(200);
  console.log('lock box:', await js('(function(){var r=document.querySelector(".pw-lock").getBoundingClientRect();return Math.round(r.left)+","+Math.round(r.top)+" "+Math.round(r.width)+"x"+Math.round(r.height)})()'));
  await shot('t-fs.png');
  await js('document.querySelector(".pw-stage").dataset.fs="0"');

  await sleep(3200);
  console.log('after 3.2s controls on:', await js('document.querySelector(".pw-stage").dataset.on'));
  await shot('t-hidden.png');

  chrome.kill();
  server.close();
  fs.unlinkSync(path.join(WEB, '__player.html'));
  fs.unlinkSync(path.join(WEB, '__t.wav'));
  process.exit(0);
})();
