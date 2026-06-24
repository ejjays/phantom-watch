# telemetry worker

One Cloudflare Worker (D1 + hourly cron). The app posts one event per resolve
and `probe.js` one per probe title; the cron messages Telegram when a provider
leg collapses, and agents read the same store over HTTP.

## Setup (once)

```sh
cd worker
wrangler d1 create phantomwatch-telemetry     # paste database_id into wrangler.toml
wrangler d1 execute phantomwatch-telemetry --remote --file=schema.sql
wrangler secret put KEY                       # random string, becomes TELEMETRY_KEY
wrangler secret put TG_TOKEN                  # @BotFather token
wrangler secret put TG_CHAT                   # chat id (@userinfobot)
wrangler deploy
```

Then in `local.properties`:

```
TELEMETRY_URL=https://phantomwatch-telemetry.<account>.workers.dev/e
TELEMETRY_KEY=<the KEY secret>
```

An empty `TELEMETRY_URL` disables reporting.

## Endpoints

- `POST /e` `{k, src, v, cfg, kind, id, s, e, ms, f, pick, h, ens, ena, hls, legs:{name:[formats,ms,maxH,enSubs]}}` → 204, `k` must equal the `KEY` secret. `f` = formats the app would serve, `pick` = the format it picked first (label, height, EN subs/audio, HLS).
- `GET /health?k=...&days=7` → `{days, resolves, zero, legs:{name:{ok,total,h,en}}, noSource}` — `h` best height seen, `en` samples with EN subs
- `GET /events?k=...&days=7&limit=100` → raw rows, newest first
- `GET /test?k=...` → one Telegram message, to confirm the bot works

Events carry TMDB ids only: no titles, no stream URLs, no account.

## Alerts

Hourly: a leg whose success rate over the last 6h drops 40 points below its 8-day
baseline (both need 5 samples), or 50%+ of at least 8 plays found no source at all.
The down set is stored in `state`, so a steady outage alerts once and recovery
sends one message. Daily digest at `DIGEST_UTC_HOUR` (01:00 UTC = 09:00 PHT).

## Test

`node test.mjs` — stubs D1 and Telegram, no network.
