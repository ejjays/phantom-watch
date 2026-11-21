# Architecture

Three Gradle modules. `:app` is the Android shell and the engine; `:shared` is
the multiplatform UI and catalog; `:web` is the wasm shell.

| Module | Source root | Owns |
|---|---|---|
| `:app` | `app/src/main/java/dev/phantomwatch` | Android entry points, player engine, resolver, subtitles, downloads, auth, updates |
| `:shared` | `shared/src/commonMain/kotlin/dev/phantomwatch` | screens, navigation state, TMDB/catalog, settings, transport seams |
| `:web` | `web/src/wasmJsMain/kotlin/dev/phantomwatch/web` | wasm entry point, web player, web settings |

Dependencies point one way: `:app` and `:web` depend on `:shared`; `:shared`
depends on neither.

## Runtime flow

```
TMDB browse/detail ──► Resolve (5 min cache, in-flight dedupe)
                            │
                            ├─► providers.js in a WebView  (every enabled leg, parallel)
                            │     └─ formats + subs, already ranked
                            │
                            └─► pick order (__pwRank)  ──► ExoPlayer + MediaSessionService
                                                        └─► telemetry (one event per resolve)
```

## Packages

| Package | Files |
|---|---|
| app shell (root) | `MainActivity.kt`, `AppRoot.kt`, `PhantomApp.kt`, `MediaEnhance.kt`, `UpdateInstallActivity.kt`, `DownloadReceiver.kt` |
| `app/ui` | `TabletDetail.kt`, `DetailPane.kt`, `DetailAmbient.kt`, `CommentsSheet.kt`, `ClipsScreen.kt`, `TrailerChrome.kt`, `TrailerHero.kt`, `PhantomInstallDialog.kt`, `ActionIcons.kt`, `AmbientGlow.kt` |
| `app/player` | `PlayerScreen.kt` (state machine), `PlayerStage.kt`, `PlayerControls.kt`, `PlayerPanel.kt`, `PlayerWide.kt`, `PlayerPill.kt`, `PlayerMedia.kt`, `PlayerLoading.kt`, `PlayerVideo.kt`, `PlayerLayout.kt`, `PlayerSettings.kt`, `Playback.kt`, `Pip.kt`, `SubsController.kt`, `SubCache.kt` |
| `app/provider` | `Resolve.kt`, `Providers.kt`, `JsProviders.kt`, `FormatOrder.kt`, `DeadHosts.kt`, `VoeExtract.kt` |
| `app/subs` | `Subtitles.kt`, `OsSubs.kt`, `TranslateSubs.kt`, `MkvSubs.kt`, `PreRecorded.kt`, `DeepgramLive.kt` |
| `app/data` | `Resume.kt`, `Settings.kt`, `Downloads.kt`, `DubPref.kt` |
| `app/core` | `RemoteConfig.kt`, `Telemetry.kt`, `CrashReport.kt`, `AppLog.kt`, `UpdateChecker.kt`, `UpdateNotice.kt`, `UpdateTrust.kt`, `Push.kt`, `PushArt.kt`, `Connectivity.kt` |
| `app/social` | `Social.kt`, `GoogleAuth.kt` |
| `app/lan` | `LanServer.kt`, `LanShare.kt`, `LanPlaylist.kt` |
| `app/agent` | `AgentFunctions.kt`, `PlayRequest.kt` |
| `shared/ui` | `Browse.kt`, `BrowseState.kt`, `Nav.kt`, `Detail.kt`, `WideDetail.kt`, `History.kt`, `HomeLiquidTabs.kt`, `LiquidTabs.kt`, `Theme.kt`, `WindowLayout.kt`, `ScreenMetrics.kt`, `settings/*` |
| `shared/data` | `Tmdb.kt`, `Models.kt`, `Search.kt`, `Chart.kt`, `Aliases.kt`, `DetailCache.kt`, `SettingsKeys.kt`, `TmdbHost.kt` |
| `shared/core` | `Http.kt` (android), `Net.kt` (common + wasm), `Log.kt`, `SplashGate.kt` |
| `web/*` | `WebApp.kt`, `PlayerScreen.kt`, `PlayerPanels.kt` |

## Decisions

Deployable services live together under `backend/`: `backend/server/` (resolver
and proxy), `backend/worker/` (telemetry D1 Worker), `backend/relay-worker/`
(relay in front of the box) and `backend/supabase/` (migrations). Each is
deployed from its own directory; the grouping is only a tree change and does not
move any deploy root. Root `assets/` is branding source; the provider bundle
stays in `app/src/main/assets`.

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
too, so a live-action title no longer looks like an outage.

**State stays on the screen.** The activity handles `configChanges`, so a
rotation does not recreate it and Compose state survives. Screen-local state
lives in the composable; only what must outlive the screen (resume position,
crash report, cached config) goes to storage. Subtitle state and the OS/AI
pipelines live in `player/SubsController.kt`. `player/PlayerScreen.kt` is still
the biggest file in the repo; `checks/quality.py` caps it at its current size
until it is split, and that baseline may only go down.

**One route model.** `shared/ui/Nav.kt` holds `Route`/`NavStack` and is driven
by `BrowseState`. Both shells navigate with the same types: Android maps a
title or source pick to `Route.Player`/`Route.Sources`, and wasm keeps a
`Route.Player?` in `WebApp`. `Route.navLabel()` is the one back-stack label.

**Signing uses a private keystore.** Release builds read `PW_RELEASE_*` and
pin the release certificate in `UPDATE_CERT_SHA256`; the update helper only
installs an APK signed by the installed app and, on release, by that exact
certificate. CI smoke-builds with a throwaway key and never distributes its
APK. See [release.md](release.md).

## Testing seams

Pure logic is unit tested in the package of the code it covers (under
`app/src/test/java/dev/phantomwatch/`): the resolver cache, subtitle parsing
and ranking, the oshash vector, the MKV cue conversion, the config acceptance
rules, the pick order and the OpenSubtitles response parsing. Tests that cover `shared` code still live in
`app`; `shared/src/commonTest` covers `Route`/`NavStack` on the android host and wasm targets.
`org.json` is a stub in `android.jar`, so the unit tests depend on the real
`org.json` artifact (test-only, not shipped).

Provider logic is tested in Node (`tests/`) against the same fixture, because
the providers are JavaScript. The Worker has its own suite (`backend/worker/test.mjs`)
with a stubbed D1 and Telegram.

Network and UI are covered by hand on the device: there is no emulator in the
loop, so `adb` runs and the user checks playback.
