import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

globalThis.window = globalThis;
const doneCalls = [];
globalThis.pw = {
  http() {},
  log() {},
  done(id, out) {
    doneCalls.push({ id, out });
  },
  fail(id, msg) {
    throw new Error(`pw.fail ${id}: ${msg}`);
  },
};

new Function(fs.readFileSync(path.join(ROOT, 'app/src/main/assets/providers.js'), 'utf8'))();

function fixture() {
  const text = fs.readFileSync(path.join(ROOT, 'app/src/test/resources/pick_order.txt'), 'utf8');
  const lines = text.split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
  const split = lines.indexOf('order');
  const formats = lines.slice(0, split).map((l) => {
    const [url, label, height, english, enSubs] = l.split('|');
    return {
      url,
      label,
      height: Number(height),
      english: english === 'true',
      enSubs: enSubs === 'true',
      isHls: true,
      referer: '',
    };
  });
  return { formats, order: lines.slice(split + 1) };
}

test('pick order matches the shared fixture', () => {
  const { formats, order } = fixture();
  assert.deepEqual(window.__pwRank(formats).map((f) => f.url), order);
});

test('pick order does not mutate the input', () => {
  const { formats } = fixture();
  const before = formats.map((f) => f.url);
  window.__pwRank(formats);
  assert.deepEqual(formats.map((f) => f.url), before);
});

test('bridge sort returns the same order', () => {
  const { formats, order } = fixture();
  doneCalls.length = 0;
  window.__pwSort('x1', JSON.stringify(formats));
  assert.equal(doneCalls.length, 1);
  assert.deepEqual(JSON.parse(doneCalls[0].out).order, order);
});

test('legs that cannot serve the title are skipped', () => {
  assert.equal(window.__pwSkip('vap', { kind: 'movie', imdb: '' }), true);
  assert.equal(window.__pwSkip('vap', { kind: 'movie', imdb: 'tt1' }), false);
  assert.equal(window.__pwSkip('paradise', { kind: 'movie', season: '1', title: 'x' }), true);
  assert.equal(window.__pwSkip('paradise', { kind: 'tv', season: '2', title: 'x' }), true);
  assert.equal(window.__pwSkip('paradise', { kind: 'tv', season: '1', title: 'x' }), false);
  assert.equal(window.__pwSkip('hianime', { kind: 'movie' }), false);
  assert.equal(window.__pwSkip('hianime', { kind: 'movie', isAnim: false }), true);
  assert.equal(window.__pwSkip('hianime', { kind: 'movie', isAnim: true }), false);
  assert.equal(window.__pwSkip('hianime', { kind: 'tv' }), false);
  assert.equal(window.__pwSkip('hianime', { kind: 'tv', isAnim: false }), true);
  assert.equal(window.__pwSkip('love', { kind: 'movie' }), false);
});

test('legs report skipped instead of empty', async () => {
  doneCalls.length = 0;
  window.__pwRun('x2', 'paradise', JSON.stringify({ kind: 'movie', season: '1', title: 'x' }));
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(doneCalls.length, 1);
  assert.equal(JSON.parse(doneCalls[0].out).skipped, true);
});

test('hianime skips non-animation titles from TMDB', async () => {
  doneCalls.length = 0;
  const prevHttp = globalThis.pw.http;
  globalThis.pw.http = (id, json) => {
    const req = JSON.parse(json);
    if (req.url.includes('/movie/1058424')) {
      window.__pwBridge.httpDone(
        id,
        JSON.stringify({
          code: 200,
          body: JSON.stringify({
            title: 'Hope',
            genres: [{ id: 878, name: 'Science Fiction' }],
          }),
        })
      );
    }
  };
  try {
    window.__pwRun('x3', 'hianime', JSON.stringify({
      kind: 'movie',
      tmdbId: '1058424',
      tmdbKey: 'k',
      tmdbBase: 'https://api.themoviedb.org/3',
    }));
    await new Promise((r) => setTimeout(r, 10));
    assert.equal(doneCalls.length, 1);
    const res = JSON.parse(doneCalls[0].out);
    assert.equal(res.skipped, true);
    assert.deepEqual(res.formats, []);
  } finally {
    globalThis.pw.http = prevHttp;
  }
});

test('hianime rejects title mismatches from AniList', async () => {
  doneCalls.length = 0;
  const prevHttp = globalThis.pw.http;
  globalThis.pw.http = (id, json) => {
    const req = JSON.parse(json);
    if (req.url.includes('/movie/109445')) {
      window.__pwBridge.httpDone(
        id,
        JSON.stringify({
          code: 200,
          body: JSON.stringify({
            title: 'Frozen',
            genres: [{ id: 16, name: 'Animation' }],
          }),
        })
      );
    } else if (req.url.includes('graphql.anilist.co')) {
      window.__pwBridge.httpDone(
        id,
        JSON.stringify({
          code: 200,
          body: JSON.stringify({
            data: {
              Page: {
                media: [{
                  idMal: 61492,
                  title: { romaji: 'Entotsu-machi no Poupelle', english: 'Chimney Town' },
                  synonyms: [],
                }],
              },
            },
          }),
        })
      );
    }
  };
  try {
    window.__pwRun('x4', 'hianime', JSON.stringify({
      kind: 'movie',
      tmdbId: '109445',
      tmdbKey: 'k',
      tmdbBase: 'https://api.themoviedb.org/3',
    }));
    await new Promise((r) => setTimeout(r, 10));
    assert.equal(doneCalls.length, 1);
    const res = JSON.parse(doneCalls[0].out);
    assert.equal(res.skipped, undefined);
    assert.deepEqual(res.formats, []);
  } finally {
    globalThis.pw.http = prevHttp;
  }
});

function withHttp(handler, run) {
  const prev = globalThis.pw.http;
  globalThis.pw.http = handler;
  return Promise.resolve()
    .then(run)
    .finally(() => {
      globalThis.pw.http = prev;
    });
}

test('net79 skips titles the mirror does not carry', async () => {
  doneCalls.length = 0;
  await withHttp(
    (id) => {
      window.__pwBridge.httpDone(id, JSON.stringify({ code: 200, body: '{"ok":true,"noSource":true}' }));
    },
    async () => {
      window.__pwRun('n1', 'net79', JSON.stringify({ kind: 'movie', tmdbId: '414906' }));
      await new Promise((r) => setTimeout(r, 10));
    }
  );
  assert.equal(doneCalls.length, 1);
  assert.equal(JSON.parse(doneCalls[0].out).skipped, true);
});

test('net79 maps streams to formats and keeps only English subs', async () => {
  doneCalls.length = 0;
  const srt = 'https://cacdn.example/subtitle/en.srt';
  let asked = '';
  await withHttp(
    (id, json) => {
      asked = JSON.parse(json).url;
      window.__pwBridge.httpDone(
        id,
        JSON.stringify({
          code: 200,
          body: JSON.stringify({
            ok: true,
            fallbackHls: '/api/loffe/tt1',
            streams: [
              { url: 'https://cdn.example/720.mp4', resolution: 720 },
              { url: 'https://cdn.example/2160.mp4', resolution: 2160 },
              { url: 'https://cdn.example/480.mp4', resolution: 480 },
            ],
            captions: [
              { lang: 'en', name: 'English', url: 'https://net79.cc/api/proxy/video?url=' + encodeURIComponent(srt) },
              { lang: 'ar', name: 'Arabic', url: 'https://net79.cc/api/proxy/video?url=x' },
            ],
          }),
        })
      );
    },
    async () => {
      window.__pwRun('n2', 'net79', JSON.stringify({ kind: 'tv', tmdbId: '66732', season: '2', episode: '3' }));
      await new Promise((r) => setTimeout(r, 10));
    }
  );
  assert.match(asked, /type=tv&season=2&episode=3/);
  const res = JSON.parse(doneCalls[0].out);
  assert.deepEqual(res.formats.map((f) => f.height), [2160, 720, 480, 0]);
  assert.deepEqual(res.subs.map((s) => s.url), [srt]);
  assert.equal(res.formats[0].enSubs, true);
  assert.equal(res.formats[0].isHls, false);
  assert.equal(res.formats[3].isHls, true);
});

const STREAM_SEED = 'testseed.forTestingOnly-1234567890';
const STREAM_CIPHER =
  'RM_PAcNcuA1ny_kEioYTlW-qBgRrJHj3bRu9oiw4PQmmyB5BAEFgKNE7vQ5YAwHAuB8WGd63Jz_guN6a-pnn23ihSvoXxgFyGF9-uSlqSTushLpuqMBmki0Bn83eH-KrMILIyL6ybO85SfTJn0rucvdztMvP2yKGiFcwnCTMVRIJ24d_P8Zl1uJKXGGFopsVhL1uwsNtjngcv_afmESj-_Vcki5-DTqSRcDcBjQy-MATpIWt2C7cOcm6udGnum1z0sZN7jMdN5vfm2LpMHQ3h7chBgqgXvApiw4maozO0bp3ZPTDCqEHHDGE9hWoPEeZa_KLXA';

test('videasy decrypts its sources payload via /seed', async () => {
  doneCalls.length = 0;
  const asked = [];
  await withHttp(
    (id, json) => {
      const url = JSON.parse(json).url;
      asked.push(url);
      if (/^callback/.test(url)) return;
      if (url.includes('/seed?mediaId=1596275')) {
        window.__pwBridge.httpDone(id, JSON.stringify({ code: 200, body: JSON.stringify({ seed: STREAM_SEED, ttlMs: 30000 }) }));
      } else if (url.includes('/miami/sources')) {
        window.__pwBridge.httpDone(id, JSON.stringify({ code: 200, body: STREAM_CIPHER }));
      }
    },
    async () => {
      window.__pwRun(
        'v1',
        'videasy',
        JSON.stringify({ kind: 'movie', tmdbId: '1596275', title: 'Test', imdb: 'tt37180686', cfg: { api: 'https://api.wecollege.net', player: 'https://www.vidy.st', cities: ['miami'] } })
      );
      await new Promise((r) => setTimeout(r, 10));
    }
  );
  const res = JSON.parse(doneCalls[0].out);
  assert.deepEqual(res.formats.map((f) => f.height), [2160, 1080]);
  assert.equal(res.formats[0].isHls, true);
  assert.equal(res.formats[0].label, 'videasy-2160p');
  assert.equal(res.formats[0].enSubs, true);
  assert.deepEqual(res.subs.map((s) => s.label), ['English']);
  assert.match(asked.find((u) => u.includes('/sources')), /enc=2/);
});

test('videasy returns empty when /seed is rate limited', async () => {
  doneCalls.length = 0;
  await withHttp(
    (id, json) => {
      window.__pwBridge.httpDone(id, JSON.stringify({ code: 429, body: '{"error":"rate_limited"}' }));
    },
    async () => {
      window.__pwRun('v2', 'videasy', JSON.stringify({ kind: 'movie', tmdbId: '1596275', title: 'Test', cfg: { cities: ['miami'] } }));
      await new Promise((r) => setTimeout(r, 10));
    }
  );
  const res = JSON.parse(doneCalls[0].out);
  assert.deepEqual(res.formats, []);
});
