import assert from 'node:assert';
import worker from './telemetry.mjs';

const row = (h, legs) => ({ ts: Date.now() + h * 3600000, kind: 'tv', tmdb: '1', season: '1', episode: '1', formats: 2, legs: JSON.stringify(legs) });
// -1 keeps the digest out of the alert cases; the digest test pins its own hour
const env = { KEY: 'k', DB: null, TG_TOKEN: 't', TG_CHAT: 'c', DIGEST_UTC_HOUR: '-1' };
const sent = [];

globalThis.fetch = async (url, init) => {
  sent.push(JSON.parse(init.body).text);
  return { ok: true, status: 200, text: async () => '' };
};

function db(rows, first = null) {
  return {
    prepare: (sql) => ({
      bind: () => ({
        run: async () => ({ meta: { sql } }),
        all: async () => ({ results: rows }),
        first: async () => first,
      }),
    }),
  };
}

env.DB = db([]);
for (const path of ['/health?k=wrong', '/e']) {
  const r = await worker.fetch(new Request(`https://x${path}`, { method: 'POST', body: '{}' }), env);
  assert.equal(r.status, 403);
}

let inserted = null;
env.DB = { prepare: () => ({ bind: (...a) => ({ run: async () => (inserted = a) }) }) };
let r = await worker.fetch(new Request('https://x/e', { method: 'POST', body: JSON.stringify({ k: 'k', kind: 'tv', id: '5', s: '1', e: '2', f: 3, legs: { love: [0, 200] } }) }), env);
assert.equal(r.status, 204);
assert.equal(inserted[4], 'tv');
assert.equal(inserted[5], '5');
assert.equal(inserted[11], '{"love":[0,200]}');
assert.equal(inserted[12], 0);

env.DB = db([row(0, { love: [0, 200, 0, 0], vix: [2, 400, 1080, 1] }), { ts: Date.now(), kind: 'tv', tmdb: '9', season: '1', episode: '1', formats: 0, legs: '{}', ev: 'play', stalls: 2, stallMs: 4000 }]);
r = await worker.fetch(new Request('https://x/health?k=k&days=7'), env);
const health = await r.json();
assert.equal(health.resolves, 1);
assert.equal(health.plays, 1);
assert.equal(health.stalled, 1);
assert.equal(health.stallMs, 4000);
assert.deepEqual(health.legs, { love: { ok: 0, total: 1, h: 0, en: 0 }, vix: { ok: 1, total: 1, h: 1080, en: 1 } });

const base5 = [row(-10, { love: [2, 100], vix: [2, 100] }), row(-11, { love: [2, 100], vix: [2, 100] }), row(-12, { love: [2, 100], vix: [2, 100] }), row(-13, { love: [2, 100], vix: [2, 100] }), row(-14, { love: [2, 100], vix: [2, 100] })];
const broken = [row(0, { love: [0, 200], vix: [2, 400] }), row(0, { love: [0, 200], vix: [2, 400] }), row(0, { love: [0, 200], vix: [2, 400] }), row(0, { love: [0, 200], vix: [2, 400] }), row(0, { love: [0, 200], vix: [2, 400] })];

sent.length = 0;
env.DB = db([...base5, ...broken.map((x) => ({ ...x, ts: x.ts - 200 * 3600000 }))], '[]');
await worker.scheduled({ scheduledTime: Date.now() }, env);
assert.ok(sent.length === 0, 'no alert when the baseline is just as bad');

sent.length = 0;
env.DB = db([...base5, ...broken], '[]');
await worker.scheduled({ scheduledTime: Date.now() }, env);
assert.equal(sent.length, 1);
assert.match(sent[0], /providers down/);
assert.match(sent[0], /love 0\/5/);
assert.ok(!/vix/.test(sent[0]), 'vix stayed healthy');

sent.length = 0;
env.DB = db([...base5, ...broken], '["love"]');
await worker.scheduled({ scheduledTime: Date.now() }, env);
assert.equal(sent.length, 0, 'same set is not re-alerted');

sent.length = 0;
env.DB = db([...base5, ...broken], '["love","rock"]');
await worker.scheduled({ scheduledTime: Date.now() }, env);
assert.match(sent[0], /recovered: rock/);

sent.length = 0;
env.DB = db([...base5, ...broken, ...broken.map((x) => ({ ...x, formats: 0 }))], '[]');
await worker.scheduled({ scheduledTime: Date.now() }, env);
assert.match(sent[0], /no source 5\/10/);

sent.length = 0;
env.DB = db([...base5, row(0, { love: [2, 100] })], '[]');
await worker.scheduled({ scheduledTime: Date.now() }, env);
assert.equal(sent.length, 0, 'quiet when under the sample floor');

const digestEnv = { ...env, DIGEST_UTC_HOUR: '1' };
const digestHour = new Date();
digestHour.setUTCHours(1, 0, 0, 0);
sent.length = 0;
digestEnv.DB = db(base5);
await worker.scheduled({ scheduledTime: digestHour.getTime() }, digestEnv);
assert.equal(sent.length, 1);
assert.match(sent[0], /24h/);

const { parseRows } = await import('./chart.mjs');
assert.deepEqual(parseRows('<span class="rank">01</span><img src="x"><button>Animals</button>'), ['Animals']);
assert.deepEqual(parseRows('<span class="rank">02</span><img src="x"><button>Trinidad &amp; Tobago</button>'), ['Trinidad & Tobago']);
assert.deepEqual(parseRows('<span class="rank">02</span><button>A Dog&#x27;s Perfect Christmas</button>'), ["A Dog's Perfect Christmas"]);
assert.deepEqual(parseRows('<span class="rank">01</span><button>Pok&#xe9;mon</button>'), ['Pok\u00e9mon']);
assert.deepEqual(parseRows('<span class="rank">01</span><button>no image</button>'), ['no image']);
assert.deepEqual(parseRows('<span class="rank">01</span><td>no button</td>'), [], 'a rank without a title is not a row');

env.DB = db([]);
assert.equal((await worker.fetch(new Request('https://x/top10?k=wrong&c=PH&t=t'), env)).status, 403);
assert.equal((await worker.fetch(new Request('https://x/top10?k=k&c=P&t=t'), env)).status, 400);
assert.equal((await worker.fetch(new Request('https://x/top10?k=k&c=PH'), env)).status, 400);

console.log('ok');
