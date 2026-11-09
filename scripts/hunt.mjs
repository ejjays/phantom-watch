#!/usr/bin/env node
// Hunt media URLs on a page whose player only reveals them through hls.js.
// Usage: node scripts/hunt.mjs <url> [waitSeconds] [jsToRunAfterLoad]
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import net from 'node:net';

const BIN = `${process.env.HOME}/bin/obscura`;
const url = process.argv[2];
const waitMs = (Number(process.argv[3]) || 12) * 1000;
const evalJs = process.argv[4];
if (!url) {
  console.error('usage: node scripts/hunt.mjs <url> [waitSeconds] [jsToRunAfterLoad]');
  process.exit(2);
}
if (!existsSync(BIN)) {
  console.error(`missing ${BIN}`);
  process.exit(2);
}

// hls.js refuses to load a manifest without MediaSource; a stub is enough for it to fetch.
const STUB = `(() => {
  const L = new WeakMap();
  function target(o){ o.addEventListener=function(t,f){const m=L.get(this)||{};L.set(this,m);(m[t]=m[t]||[]).push(f)}; o.removeEventListener=function(t,f){const m=L.get(this)||{};if(m[t])m[t]=m[t].filter(x=>x!==f)}; o.dispatchEvent=function(e){const m=L.get(this)||{};(m[e.type]||[]).forEach(f=>{try{f.call(this,e)}catch(_){}});return true}; return o }
  class SB { constructor(mime){this.mime=mime;this.updating=false;this.mode='segments';this.timestampOffset=0;this.buffered={length:0,start(){return 0},end(){return 0}};target(this)} appendBuffer(){this.updating=true;setTimeout(()=>{this.updating=false;this.dispatchEvent({type:'updateend'})},0)} remove(){this.dispatchEvent({type:'updateend'})} abort(){} }
  class MS { constructor(){this.readyState='closed';this.duration=NaN;this.sourceBuffers=[];this.activeSourceBuffers=[];target(this);setTimeout(()=>{this.readyState='open';this.dispatchEvent({type:'sourceopen'})},0)} static isTypeSupported(){return true} addSourceBuffer(m){const s=new SB(m);this.sourceBuffers.push(s);return s} removeSourceBuffer(s){this.sourceBuffers=this.sourceBuffers.filter(x=>x!==s)} endOfStream(){this.readyState='ended';this.dispatchEvent({type:'sourceended'})} setLiveSeekableRange(){} clearLiveSeekableRange(){} }
  window.MediaSource=MS; window.SourceBuffer=SB;
  const orig=URL.createObjectURL.bind(URL);
  URL.createObjectURL=function(o){return (o instanceof MS)?'blob:fake-ms':orig(o)};
  URL.revokeObjectURL=function(){};
})();`;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function freePort() {
  return new Promise((resolve) => {
    const s = net.createServer();
    s.listen(0, '127.0.0.1', () => {
      const port = s.address().port;
      s.close(() => resolve(port));
    });
  });
}

async function waitReady(port) {
  for (let i = 0; i < 100; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${port}/json/version`);
      if (r.ok) return r.json();
    } catch {}
    await sleep(100);
  }
  throw new Error('obscura serve did not start');
}

const port = await freePort();
const child = spawn('glibc-runner', [BIN, 'serve', '--port', String(port)], { stdio: 'ignore' });
child.on('error', (e) => {
  console.error(e.message);
  process.exit(1);
});

let ws;
try {
  const ver = await waitReady(port);
  ws = new WebSocket(ver.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
  });

  let id = 0;
  const pend = new Map();
  const urls = [];
  const send = (method, params = {}, sessionId) =>
    new Promise((resolve) => {
      const i = ++id;
      pend.set(i, resolve);
      ws.send(JSON.stringify({ id: i, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id && pend.has(m.id)) {
      pend.get(m.id)(m);
      pend.delete(m.id);
    }
    if (m.method === 'Network.requestWillBeSent') urls.push(m.params.request.url);
  };

  const { result: { targetId } } = await send('Target.createTarget', { url: 'about:blank' });
  const { result: { sessionId } } = await send('Target.attachToTarget', { targetId, flatten: true });
  await send('Page.enable', {}, sessionId);
  await send('Network.enable', {}, sessionId);
  await send('Page.addScriptToEvaluateOnNewDocument', { source: STUB }, sessionId);
  await send('Page.navigate', { url }, sessionId);
  if (evalJs) {
    for (let i = 0; i < 60; i++) {
      const r = await send('Runtime.evaluate', { expression: 'document.readyState' }, sessionId);
      if (r.result?.result?.value === 'complete') break;
      await sleep(250);
    }
    const r = await send('Runtime.evaluate', { expression: evalJs }, sessionId);
    const err = r.result?.exceptionDetails || r.error;
    if (err) console.error(`eval failed: ${err.text || err.message}`);
  }
  await sleep(waitMs);

  const title = (await send('Runtime.evaluate', { expression: 'document.title' }, sessionId)).result?.result?.value || '';
  const unique = [...new Set(urls)];
  const m3u8 = unique.filter((u) => /\.m3u8(\?|$)/.test(u));
  const seg = unique.filter((u) => /\.(ts|m4s|mp4)(\?|$)/.test(u));

  console.log(`title: ${title}`);
  console.log(`requests: ${unique.length}`);
  if (/just a moment/i.test(title)) console.log('cloudflare: challenge page, nothing to hunt');
  console.log(`m3u8 (${m3u8.length}):`);
  for (const u of m3u8) console.log(`  ${u}`);
  if (seg.length) {
    console.log(`segments: ${seg.length} (first 3)`);
    for (const u of seg.slice(0, 3)) console.log(`  ${u}`);
  }
  process.exitCode = m3u8.length ? 0 : 1;
} catch (e) {
  console.error(e.message);
  process.exitCode = 1;
} finally {
  if (ws) ws.close();
  child.kill('SIGTERM');
}
