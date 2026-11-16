// network first, cache only as offline copy: bundle updates as one unit, a per file cache would
// serve old strings file next to new wasm and blank every label
// bump this whenever shell changes, a new value makes browser install worker again which fetches
// every file in SHELL again and drops old cache, otherwise a worker whose code did not change is
// never reinstalled
const V = 'pw-v2'

const SHELL = [
  './',
  './manifest.webmanifest',
  './phantom.svg',
  './icon-192.png',
  './icon-512.png',
  './icon-maskable-512.png',
  './apple-touch-icon.png',
  './icons.js',
  './player.js',
  './vendor/js-joda.esm.js',
  './kotlin/PhantomWatch-spike.mjs',
  './kotlin/PhantomWatch-spike.import-object.mjs',
  './kotlin/PhantomWatch-spike.js-builtins.mjs',
  './kotlin/skiko.mjs',
  './kotlin/skiko.wasm',
  './kotlin/PhantomWatch-spike.wasm',
]

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(V).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== V).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (e) => {
  const req = e.request
  if (req.method !== 'GET') return
  const url = new URL(req.url)
  // tmdb, posters, streams and chart stay out of the way
  if (url.origin !== location.origin) return
  if (url.pathname.endsWith('/geo') || url.pathname.endsWith('/top10')) return
  e.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone()
          caches.open(V).then((c) => c.put(req, copy))
        }
        return res
      })
      .catch(() => caches.match(req).then((hit) => hit || Response.error())),
  )
})
