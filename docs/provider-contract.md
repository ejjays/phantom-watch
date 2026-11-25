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
formats: [{ url, isHls, label, height, english, referer, headers?, enSubs, enAudio,
            dub?, subs?, intro?, outro? }]
subs:    [{ label, url, src }]
```

`VideoFormat` also declares a `recap` field. The current script never sets it,
and `Providers.formatsOf` does not map it.

`label` must start with the leg name (`vidlink-…`), `src` on a subtitle must
equal the leg name, otherwise the subtitle will not bind to the format.

A leg that cannot serve the title answers `{ formats: [], subs: [], skipped: true }`.
The app leaves skipped legs out of the telemetry; `scripts/probe.js` treats them as
skips, not failures. This rule exists because live-action titles used to
report paradise as down and set off outage alerts that were not real.

The `pinoy` leg calls a native `pw.voe(id, code)` extractor for voe.sx embeds.
`JsProviders.Bridge` implements it on Android. Node probe and CI do not, so
`pinoy` answers `skipped` there.

## Helpers the rest of the stack uses

| Function | Purpose |
|---|---|
| `__pwRank(formats)` | canonical pick order, returns a new sorted array |
| `__pwSkip(leg, ctx)` | true when the leg cannot serve this title |
| `__pwSort(id, formatsJson)` | bridge variant of `__pwRank`, answers `{ order: [url…] }` |

### Pick order

1. real height before pseudo height (negative height marks the moviebox "auto" entries)
2. sub cut before dub cut
3. known height (`> 0`) before unknown height
4. 1080p and up with English subtitles on a fixed rung
5. fixed rung before adaptive masters (`-auto` labels)
6. English subtitles
7. English subtitles with verified English audio
8. formats with `english === false` rank below the rest
9. height, descending

The app asks the bridge for this order; `scripts/probe.js` calls `__pwRank` directly.
The Kotlin fallback in `app/src/main/java/dev/phantomwatch/provider/FormatOrder.kt`
must match, and both are pinned by `app/src/test/resources/pick_order.txt`.

### Skip rules

| Leg | Skipped when |
|---|---|
| `vap` | no IMDb id |
| `paradise` | not a TV show, or not season 1, or no title |
| `allanime` | no TMDB key, or no animation genre in the TMDB genre list |
| `hianime` | no animation genre in the TMDB genre list |

`vap`, `paradise` and the key check for `allanime` live in `__pwSkip`. The
animation checks run inside the `hianime` and `allanime` legs and read genre id
16 from TMDB.

## Adding a provider

1. Add the leg function to `providers.js` and register it in `legs`.
2. Add a `legs.<name>` entry to `providers.json` (host, keys, `enabled`).
3. Label the formats `<name>-…` and set `src: <name>` on its subtitles.
4. `python3 checks/quality.py` and `node --test tests/*.test.mjs`.
5. `node scripts/probe.js`: the new leg shows up in the report.
6. Sign and deploy the bundle (`scripts/sign_config.py --version N`).

Anything that needs new headers, a POST body or a different content type fits
the bridge already. Anything native (DRM, a new protocol, a new permission)
still needs an APK.
