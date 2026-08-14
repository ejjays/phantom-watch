# Changelog

## Unreleased

- community: authors can delete their comments (replies go with them) and anyone
  can report spam, harassment, spoilers or other; three reports hide a comment
  pending moderator review. The database enforces the same length, title, reply
  and rate rules as the composer, and the client filters hidden comments.
- update: the loopback updater requires a one-shot token, refuses redirects,
  oversized or incomplete APKs, and only installs a newer APK with the same
  package and signer pinned to the release certificate. Release builds now need
  the private keystore via `PW_RELEASE_*`; CI smoke-builds with a throwaway key.
- refactor: the flat `dev.phantomwatch` package is split into `core`, `data`,
  `provider`, `player`, `subs`, `social`, `torrent` and `ui`; files, tests and
  drawables are named after what they contain, and the config schema has one
  shared validator (`checks/config_rules.py`) for CI and signing.
- home: a history icon next to search opens what you were part way through
  and resumes it at the saved position; a finished episode drops off the list.
- player: when an episode ends, a 10 s card offers the next one and plays it
  automatically, rolling into the next season when the season list is cached.
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
