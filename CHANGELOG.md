# Changelog

## Unreleased

- providers: `providers.js` is the only implementation again - the Kotlin
  scrapers were deleted, the pick order and the skip rules live in one place
  and are pinned by a fixture both sides test against.
- providers: legs that cannot serve a title report `skipped` instead of
  looking like an outage, so the Telegram alerts stop crying wolf on
  live-action titles.
- player: resume where the episode was left off, without reopening a finished
  episode at its end or clearing progress before playback starts.
- providers: hianime keeps movie support and follows the configured TMDB base;
  a dead bridge no longer counts as a provider outage.
- app: a crash report is written to disk and offered for sharing on the next
  launch, so a family member can send it over.
- build: `versionCode` follows the commit count, R8 runs on release builds,
  the JitPack snapshot dependency is gone (three icons are vendored as
  vector drawables).
- ci: unit tests, provider tests, worker tests, the repo quality check and the
  R8 release build run on every push.
