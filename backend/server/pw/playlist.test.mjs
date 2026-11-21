import assert from 'node:assert';
import test from 'node:test';
import playlist from './playlist.js';

const { rewrite } = playlist;
const BASE = 'https://cdn.example/hls/master.m3u8';
const link = (u) => `PROXY(${u})`;

test('rewrites URI attributes on KEY, MAP and MEDIA tags', () => {
  const src = [
    '#EXTM3U',
    '#EXT-X-TARGETDURATION:10',
    '#EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x00',
    '#EXT-X-MAP:URI="init.mp4"',
    '#EXT-X-MEDIA:TYPE=SUBTITLES,URI="sub/en.vtt",LANGUAGE="en"',
    'seg1.ts',
  ].join('\n');
  const out = rewrite(src, BASE, link);
  assert.match(out, /URI="PROXY\(https:\/\/cdn\.example\/hls\/key\.bin\)"/);
  assert.match(out, /URI="PROXY\(https:\/\/cdn\.example\/hls\/init\.mp4\)"/);
  assert.match(out, /URI="PROXY\(https:\/\/cdn\.example\/hls\/sub\/en\.vtt\)"/);
  assert.match(out, /^PROXY\(https:\/\/cdn\.example\/hls\/seg1\.ts\)$/m);
  assert.ok(out.includes('#EXT-X-TARGETDURATION:10'), 'non-URI tags stay untouched');
});

test('leaves a KEY tag with METHOD=NONE and no URI alone', () => {
  const out = rewrite('#EXTM3U\n#EXT-X-KEY:METHOD=NONE\nseg1.ts', BASE, link);
  assert.ok(out.startsWith('#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n'));
  assert.ok(out.includes('PROXY(https://cdn.example/hls/seg1.ts)'));
});

test('preserves CRLF line endings', () => {
  const src = '#EXTM3U\r\n#EXT-X-MAP:URI="init.mp4"\r\nseg1.ts\r\n';
  const out = rewrite(src, BASE, link);
  assert.ok(out.includes('\r\n'));
  assert.ok(!/(^|[^\r])\n/.test(out), 'every newline stays CRLF');
  assert.match(out, /URI="PROXY\(https:\/\/cdn\.example\/hls\/init\.mp4\)"\r\n/);
});

test('does not follow absolute URIs through the base', () => {
  const out = rewrite('#EXT-X-MAP:URI="https://other.example/init.mp4"', BASE, link);
  assert.equal(out, '#EXT-X-MAP:URI="PROXY(https://other.example/init.mp4)"');
});
