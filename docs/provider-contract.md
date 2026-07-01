# Provider contract

`app/src/main/assets/providers.js` is the only provider implementation. It runs
inside a WebView (`https://phantom.local/`, no network loads from the page
itself) and talks to the app through the `pw` bridge.

## Bridge

```js
window.pw = {
  http(id, reqJson),  // app performs the request
  done(id, resultJson),
  fail(id, message),
  log(message),
};
```

`http` request fields: `url` (required), `referer`, `headers` (object),
`method` (default `GET`), `body`, `contentType`, `timeout` (1..20 s).
The app answers with `{ code, headers, body }` or `{ error }`.

## Running a leg

```js
window.__pwRun(id, legName, argsJson)
```

`args` carries `tmdbId`, `kind` (`movie`/`tv`), `season`, `episode`, `title`,
`imdb` (vap only), `tmdbKey`, `tmdbBase`, `cfg` (the leg entry from
`providers.json` without `enabled`), plus `http` and `log` helpers.

The leg answers with `{ formats, subs }`:

```js
formats: [{ url, isHls, label, height, english, referer, headers?, enSubs, enAudio }]
subs:    [{ label, url, src }]
```

`label` must start with the leg name (`vidlink-…`), `src` on a subtitle must
equal the leg name, otherwise the subtitle will not bind to the format.

A leg that cannot serve the title answers `{ formats: [], subs: [], skipped: true }`.
The app leaves skipped legs out of the telemetry; `probe.js` treats them as
skips, not failures.

## Helpers the rest of the stack uses

| Function | Purpose |
|---|---|
| `__pwRank(formats)` | canonical pick order, returns a new sorted array |
| `__pwSkip(leg, ctx)` | true when the leg cannot serve this title |
| `__pwSort(id, formatsJson)` | bridge variant of `__pwRank`, answers `{ order: [url…] }` |

### Pick order

1. real height before pseudo height (negative height marks the moviebox "auto" entries)
2. 1080p and up with English subtitles
3. adaptive masters (`-auto` labels)
4. English subtitles
5. English audio
6. height, descending

The app asks the bridge for this order; `probe.js` calls `__pwRank` directly.
The Kotlin fallback in `FormatOrder.kt` must match - both are pinned by
`app/src/test/resources/pick_order.txt`.

### Skip rules

| Leg | Skipped when |
|---|---|
| `vap` | no IMDb id |
| `paradise` | not a TV show, or not season 1, or no title |

## Adding a provider

1. Add the leg function to `providers.js` and register it in `legs`.
2. Add a `legs.<name>` entry to `providers.json` (host, keys, `enabled`).
3. Label the formats `<name>-…` and set `src: <name>` on its subtitles.
4. `python3 checks/quality.py` and `node --test tests/*.test.mjs`.
5. `node probe.js` - the new leg shows up in the report.
6. Sign and deploy the bundle (`sign_config.py --version N`).

Anything that needs new headers, a POST body or a different content type fits
the bridge already. Anything native (DRM, a new protocol, a new permission)
still needs an APK.
