'use strict';
// Runs the shipped providers.js headless: same contract probe.js uses.
const { AsyncLocalStorage } = require('node:async_hooks');

const UA = process.env.PW_UA ||
  'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';

const BUDGET = { paradise: 32000, hianime: 30000 };

function init(src) {
  const pending = new Map();
  let seq = 0;

  globalThis.window = globalThis;
  globalThis.pw = {
    http(id, json) {
      const req = JSON.parse(json);
      const headers = { 'user-agent': UA };
      if (req.referer) headers.referer = req.referer;
      if (req.headers) for (const k of Object.keys(req.headers)) headers[k.toLowerCase()] = req.headers[k];
      const method = String(req.method || 'GET').toUpperCase();
      const init = {
        headers,
        method,
        redirect: 'follow',
        signal: AbortSignal.timeout(Math.min(20, Math.max(1, req.timeout || 10)) * 1000),
      };
      if (method !== 'GET' && method !== 'HEAD' && req.body != null) {
        init.body = req.body;
        if (!headers['content-type']) headers['content-type'] = req.contentType || 'application/json';
      }
      fetch(req.url, init)
        .then(async (r) => {
          const body = await r.text();
          const h = {};
          r.headers.forEach((v, k) => { (h[k] ||= []).push(v); });
          globalThis.__pwBridge.httpDone(id, JSON.stringify({ code: r.status, headers: h, body }));
        })
        .catch((e) => globalThis.__pwBridge.httpDone(id, JSON.stringify({ error: e.name || 'ERR' })));
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

  new Function(src)();

  function runLeg(leg, args) {
    const id = 'p' + (++seq);
    const budget = BUDGET[leg] || 20000;
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        pending.delete(id);
        resolve({ r: null, err: 'timeout' });
      }, budget);
      pending.set(id, {
        ok: (r) => { clearTimeout(timer); resolve({ r }); },
        bad: (e) => { clearTimeout(timer); resolve({ r: null, err: e }); },
      });
      globalThis.__pwRun(id, leg, JSON.stringify(args));
    });
  }

  return {
    runLeg,
    rank: (formats) => globalThis.__pwRank(formats) || [],
    skip: (leg, ctx) => globalThis.__pwSkip(leg, ctx),
  };
}

module.exports = { init, UA };
