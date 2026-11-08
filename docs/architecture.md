# Architecture

## Runtime flow

```
TMDB browse/detail ──► Resolve (5 min cache, in-flight dedupe)
                            │
                            ├─► providers.js in a WebView  (8 legs, parallel)
                            │     └─ formats + subs, already ranked
                            │
                            └─► pick order (__pwRank)  ──► ExoPlayer + MediaSessionService
                                                        └─► telemetry (one event per resolve)
```

## Layers

| Layer | Files | Rule |
|---|---|---|
| app shell | `MainActivity.kt`, `AppRoot.kt`, `PhantomApp.kt` | navigation state, PiP, crash dialog |
| screens | `ui/Browse.kt`, `ui/Detail.kt`, `ui/History.kt`, `player/PlayerStage.kt`, `player/PlayerPanel.kt` | rendering only |
| player | `player/PlayerScreen.kt`, `PlayerMedia.kt`, `PlayerLoading.kt`, `PlayerDownload.kt`, `PlayerLayout.kt`, `PlayerVideo.kt` | playback state machine |
| resolver | `provider/Resolve.kt`, `Providers.kt`, `JsProviders.kt`, `FormatOrder.kt` | formats, ordering, cache |
| providers | `assets/providers.js`, `assets/providers.json` | the only provider implementation |
| subs | `subs/Subtitles.kt`, `OsSubs.kt`, `SubsApi.kt`, `AiSubs.kt`, `MkvSubs.kt` | subtitle sources |
| data | `data/Models.kt`, `Tmdb.kt`, `Search.kt`, `Resume.kt`, `DetailCache.kt` | catalog and watch state |
| infra | `core/RemoteConfig.kt`, `core/Telemetry.kt`, `core/CrashReport.kt`, `core/Http.kt`, `provider/DeadHosts.kt` | config, telemetry, transport, host health |

Dependencies point down only. Screens do not talk to providers, the resolver
does not touch Compose, and `providers.js` knows nothing about the app.

## Decisions

**One provider implementation.** Every leg lives in `assets/providers.js` and
runs in a WebView behind the `pw` bridge. The Kotlin copies were deleted; they
were a second set of scrapers that had to be fixed twice.
The bridge is created once, on a background pool of four, with per-leg
timeouts (`12s`, `28s` for paradise) and a 35 s budget for the whole resolve.

**The pick order lives in JavaScript.** `__pwRank` is the canonical order; the
app asks the bridge for it (`__pwSort`) and `scripts/probe.js` calls it directly. The
Kotlin copy in `FormatOrder.kt` only runs if the sort round-trip fails. Both
sides are pinned by `app/src/test/resources/pick_order.txt`.

**Skip rules live in JavaScript too.** A leg that cannot serve the title
returns `{ formats: [], subs: [], skipped: true }` instead of an empty result.
The app leaves skipped legs out of the telemetry, and `scripts/probe.js` skips them
too, so a live-action title no longer looks like a paradise outage.

**State stays on the screen.** The activity handles `configChanges`, so a
rotation does not recreate it and Compose state survives. Screen-local state
lives in the composable; only what must outlive the screen (resume position,
crash report, cached config) goes to storage. Subtitle state and the
OS/AI pipelines live in `player/SubsController.kt`; `player/PlayerScreen.kt`
is still the biggest file in the repo because it is the player state machine;
`checks/quality.py` caps it at its current size until it is split.

**Signing uses a private keystore.** Release builds read `PW_RELEASE_*` and
pin the release certificate in `UPDATE_CERT_SHA256`; the update helper only
installs an APK signed by the installed app and, on release, by that exact
certificate. CI smoke-builds with a throwaway key and never distributes its
APK. See [release.md](release.md).

## Testing seams

Pure logic is unit tested in the package of the code it covers
(`app/src/test/java/dev/phantomwatch/{core,data,player,provider,subs,ui}`):
the resolver cache, subtitle parsing and ranking, the oshash vector, the MKV
cue conversion, the config acceptance rules, the pick order and the
OpenSubtitles response parsing.
`org.json` is a stub in `android.jar`, so the unit tests depend on the real
`org.json` artifact (test-only, not shipped).

Provider logic is tested in Node (`tests/`) against the same fixture, because
the providers are JavaScript. The Worker has its own suite (`worker/test.mjs`)
with a stubbed D1 and Telegram.

Network and UI are covered by hand on the device: there is no emulator in the
loop, so `adb` runs and the user checks playback.
