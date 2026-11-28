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
    const [url, label, height, english, enSubs, enAudio] = l.split('|');
    return {
      url,
      label,
      height: Number(height),
      english: english === 'true',
      enSubs: enSubs === 'true',
      enAudio: enAudio === 'true',
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

test('shared pipeline ranks, dedups and picks the same as the phone', () => {
  const { formats, order } = fixture();
  const dup = Object.assign({}, formats[1], { url: 'duplicate' });
  const out = window.__pwPipeline({ formats: formats.concat([dup]), subs: [] });
  const got = out.formats.map((f) => f.url);
  assert.deepEqual(got.filter((u) => u !== 'duplicate'), order);
  assert.equal(got.indexOf('duplicate'), got.indexOf(order[0]) + 1);
  assert.equal(out.pick.url, order[0]);
});

test('shared pipeline sinks blocked hosts and binds subs per format', () => {
  const formats = [
    { url: 'https://good.example/a', label: 'leg-1080p', height: 1080, enSubs: true, isHls: false },
    { url: 'https://bad.example/b', label: 'leg-720p', height: 720, enSubs: true, isHls: false },
  ];
  const subs = [
    { label: 'English', url: 's1', src: 'leg' },
    { label: 'Other', url: 's2', src: 'other' },
  ];
  const out = window.__pwPipeline({ formats, subs, blocked: ['bad.example'] });
  assert.deepEqual(out.formats.map((f) => f.url), ['https://good.example/a', 'https://bad.example/b']);
  assert.equal(out.pick.url, 'https://good.example/a');
  assert.deepEqual(out.usable.map((s) => s.url), ['s1']);
  assert.deepEqual(window.__pwSubsFor({ label: 'rock-x' }, subs), []);
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
  assert.equal(window.__pwSkip('hianime', { kind: 'movie', isAnim: false }), false);
  assert.equal(window.__pwSkip('hianime', { kind: 'movie', isAnim: true }), false);
  assert.equal(window.__pwSkip('hianime', { kind: 'tv' }), false);
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

test('allanime needs a tmdb key to search by title', () => {
  assert.equal(window.__pwSkip('allanime', {}), true);
  assert.equal(window.__pwSkip('allanime', { tmdbKey: 'k' }), false);
});

test('allanime skips live action titles from TMDB', async () => {
  doneCalls.length = 0;
  await withHttp(
    (id, json) => {
      const req = JSON.parse(json);
      if (req.url.includes('/movie/1058424')) {
        window.__pwBridge.httpDone(
          id,
          JSON.stringify({
            code: 200,
            body: JSON.stringify({ title: 'Hope', genres: [{ id: 878, name: 'Science Fiction' }] }),
          })
        );
      }
    },
    async () => {
      window.__pwRun('aa1', 'allanime', JSON.stringify({
        kind: 'movie',
        tmdbId: '1058424',
        tmdbKey: 'k',
        tmdbBase: 'https://api.themoviedb.org/3',
      }));
      await new Promise((r) => setTimeout(r, 10));
    }
  );
  assert.equal(doneCalls.length, 1);
  assert.equal(JSON.parse(doneCalls[0].out).skipped, true);
});

test('allanime answers empty when the crypto chunk cannot be read', async () => {
  doneCalls.length = 0;
  const asked = [];
  await withHttp(
    (id, json) => {
      const req = JSON.parse(json);
      asked.push(req.url);
      if (req.url.includes('api.themoviedb.org')) {
        window.__pwBridge.httpDone(
          id,
          JSON.stringify({ code: 200, body: JSON.stringify({ name: 'Solo Leveling', genres: [{ id: 16 }] }) })
        );
      } else if (req.url.includes('api.mkissa.net/api')) {
        window.__pwBridge.httpDone(
          id,
          JSON.stringify({ code: 200, body: JSON.stringify({ data: { shows: { edges: [{ _id: 'x1', name: 'Solo Leveling', englishName: 'Solo Leveling', availableEpisodes: { sub: 1, dub: 1 } }] } } }) })
        );
      } else {
        // site answers without app entry, discovery gives up
        window.__pwBridge.httpDone(id, JSON.stringify({ code: 200, body: '<html></html>' }));
      }
    },
    async () => {
      window.__pwRun('aa2', 'allanime', JSON.stringify({
        kind: 'tv',
        tmdbId: '127532',
        season: '1',
        episode: '1',
        tmdbKey: 'k',
        tmdbBase: 'https://api.themoviedb.org/3',
      }));
      await new Promise((r) => setTimeout(r, 30));
    }
  );
  const res = JSON.parse(doneCalls[0].out);
  assert.deepEqual(res.formats, []);
  assert.equal(res.skipped, undefined);
  assert.ok(asked.some((u) => u.startsWith('https://mkissa.to')));
});

test('vidora decrypts the moscow token and binds opensubtitles', async () => {
  doneCalls.length = 0;
  const token = 'ms_1ac493955cd0657b1ca86df6c2b6df7bf3e90b966d2e580c727b16e067438a6bbb12f705a8eb3364ac60032f2c022cf617295844bb51f970650e2eacb857bce721008b4b0a03d73a621a53447a3b5d67c277d9a45a5cee9b74caf5ab6714c733593a58356b2eab237f2e832725763c3154dece74c2794dde8e5dc2f1d0bbc6466a56728b59';
  const master = 'https://stream.hls.lol/nesterov/master.m3u8?i=0&k=1a481364520692f13f31&s=6d6c21d43fd881d0e29f46bde769c4b3';
  const playlist = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=640x360\n360.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2,RESOLUTION=1280x720\n720.m3u8\n';
  await withHttp(
    (id, json) => {
      const u = JSON.parse(json).url;
      let body = '{}';
      if (u.includes('/moscow')) body = JSON.stringify({ sources: { Moscow: { url: token } } });
      else if (u === master) body = playlist;
      else if (u.includes('rest.opensubtitles.org')) {
        body = JSON.stringify([{ SubLanguageID: 'eng', SubDownloadLink: 'https://dl.opensubtitles.org/en/download/src-api/vrf-abc/file/123' }]);
      }
      window.__pwBridge.httpDone(id, JSON.stringify({ code: 200, body }));
    },
    async () => {
      window.__pwRun('vo1', 'vidora', JSON.stringify({
        kind: 'movie', tmdbId: '1690098', title: 'Test', imdb: 'tt42576213',
        cfg: {
          api: 'https://stream.hls.lol/moscow',
          key: '55060a042823f51a94c296894a58a0db15f3baef807155140983083fa799ef47',
        },
      }));
      await new Promise((r) => setTimeout(r, 20));
    }
  );
  const res = JSON.parse(doneCalls[0].out);
  assert.deepEqual(res.formats.map((f) => f.label), ['vidora-auto', 'vidora-720p', 'vidora-360p']);
  assert.deepEqual(res.formats.map((f) => f.height), [720, 720, 360]);
  assert.equal(res.formats[0].enSubs, true);
  assert.deepEqual(res.subs.map((s) => s.url), ['https://dl.opensubtitles.org/en/download/subencoding-utf8/src-api/vrf-abc/file/123']);
});
