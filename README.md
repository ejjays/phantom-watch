# Phantom Watch

Native Android streaming client. TMDB for discovery, eight providers for
streams, ExoPlayer for playback, and a signed OTA bundle so provider fixes
ship without an APK.

Home = TMDB trending grid. Tap a movie to play, tap a show to pick
season/episode. The player merges every provider, prefers English subtitles
and adaptive masters, and resumes where you left off.

## Layout

| Path | What lives there |
|---|---|
| `app/src/main/java/dev/phantomwatch/` | app: navigation, player, resolver, subs, telemetry |
| `app/src/main/java/dev/phantomwatch/ui/` | Compose screens (browse, detail, player chrome) |
| `app/src/main/assets/providers.js` | **the only** provider implementation, plus the pick order and skip rules |
| `app/src/main/assets/providers.json` | hosts, keys, per-leg `enabled` flags (signed and OTA-updatable) |
| `worker/` | Cloudflare Worker: telemetry ingest, provider alerts, daily digest |
| `probe.js` | live provider health check; runs the same `providers.js` |
| `sign_config.py` | signs a config bundle for the OTA endpoint |
| `checks/quality.py` | repo invariants enforced in CI |

Details: [docs/architecture.md](docs/architecture.md),
[docs/provider-contract.md](docs/provider-contract.md),
[docs/release.md](docs/release.md),
[docs/threat-model.md](docs/threat-model.md).

## Build and test

```sh
./gradlew :app:assembleDebug --offline      # debug APK
./gradlew :app:testDebugUnitTest --offline  # JVM unit tests
python3 checks/quality.py                   # repo invariants
node --test tests/*.test.mjs                # provider (JS) tests
node worker/test.mjs                        # telemetry worker tests
node probe.js                               # live provider check (~2 min)
```

The phone SDK is API 37 (hand-made platform, see AGENTS.md). CI builds against
the last published platform with `-PcompileSdk=36 -PtargetSdk=36` and
`-Pandroid.experimental.disableCompileSdkChecks=true`.

## Keys

Everything secret lives in `local.properties` (never committed):

```
TMDB_API_KEY=...
OS_API_KEY=...          # OpenSubtitles
SUPABASE_URL=...
SUPABASE_ANON_KEY=...
GROQ_API_KEY=key1,key2 # rotates on rate limits
SEEDBOX_URL=...        # optional seedbox
SEEDBOX_USER=...
SEEDBOX_PASS=...
CFG_URL=...            # optional OTA config endpoint
TELEMETRY_URL=...      # empty disables reporting
TELEMETRY_KEY=...
```

Missing keys only disable the feature that needs them; the app still plays.

## Providers

Eight legs run in parallel inside a WebView (`providers.js`): `love`, `rock`,
`vix`, `zee`, `paradise`, `vap`, `vidlink`, `hianime`. Each leg returns
formats and subtitles; the pick order and the "cannot serve this title" rules
live in the same file, so the app, the probe and the telemetry agree.

`providers.json` and `providers.js` ship inside the APK as the trusted
fallback and together form one signed bundle: ECDSA P-256 over the payload,
a `version` at least as new as the cached or bundled one, cached in prefs.
Older bundles are refused.

```sh
python3 sign_config.py --version N          # N above the deployed version
# deploy build/pages-config/providers.json alone to CFG_URL
```

The app verifies with `app/src/main/assets/ota_pub.pem`. The private key
lives in the `OTA_KEY` Actions secret; `gh workflow run sign-config.yml
-f version=N` produces a signed bundle without the original device.

## Subtitles

Bundled subs → VidLove → Supabase cache → OpenSubtitles → AI translation
(Groq/Cerebras Qwen). The cache table lives in Supabase (see the SQL in the
git history of this file); writes use an anonymous session. `MkvSubs` pulls
subtitle tracks straight out of MKV files, `AiSubs` translates the dialogue
when the release has no English track.

## Telemetry

One event per resolve (per-leg format counts and timings, TMDB ids only) and
one per playback session. The Worker alerts on Telegram when a leg collapses
and sends a daily digest.

```sh
curl -s "https://phantomwatch-telemetry.<account>.workers.dev/health?k=$TELEMETRY_KEY&days=7"
```

Setup, endpoints and alert rules: [worker/README.md](worker/README.md).
