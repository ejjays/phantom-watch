import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { b64, unb64, sig, proxyUrl } from './index.js';

test('base64url round trips unicode', () => {
  const s = 'https://cdn.test/üñ?x=1&y=2';
  assert.equal(unb64(b64(s)), s);
});

test('signature matches oracle hmac', async () => {
  const secret = 'unit-secret';
  const url = 'https://cdn.test/a.mp4';
  const h = b64(JSON.stringify({ 'User-Agent': 'okhttp/4.12.0' }));
  const expected = createHmac('sha256', secret).update(url + '\n' + h).digest('base64url').slice(0, 22);
  assert.equal(await sig(secret, url, h), expected);
});

test('proxy url carries headers and signature', async () => {
  const out = await proxyUrl('https://relay.test', 'https://cdn.test/a.mp4', { referer: 'https://site.test/' }, 'k');
  const q = new URL(out).searchParams;
  assert.equal(unb64(q.get('u')), 'https://cdn.test/a.mp4');
  assert.deepEqual(JSON.parse(unb64(q.get('h'))), { referer: 'https://site.test/' });
  assert.equal(q.get('s'), await sig('k', 'https://cdn.test/a.mp4', q.get('h')));
});
