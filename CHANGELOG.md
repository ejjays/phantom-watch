# Changelog

## Unreleased

- player: a stream that hangs now hands itself back instead of spinning forever. After 15s
  without a first frame it drops the resume offset; after 30s of buffering it bans the host for
  two hours, marks the url dead and walks to the next format the last resolve still holds —
  preferring a real rung over an `auto` that can serve far less than it advertises — and only
  falls through to the sources list when nothing is left. A banned host is ranked last
  rather than filtered out, and a hang ban is never cleared by the one-byte re-admit probe, so a
  wrong ban costs rank and not the title. Also one inset per edge, bottom controls clear the
  display cutout, and the rail shell is picked by window width so a portrait tablet gets it too.
- player: a 403 is now a ten-minute cooldown, not a 24-hour host ban. Measured on a vidlink edge,
  the same url answered 403 to the player's user-agent and 429 to the same request with a
  referer — a throttled edge, not a dead host. A dead token and a throttle both clear in
  minutes, so a day-long ban only kept a working provider sunk behind a worse one.
- subs: a rung that ships no captions of its own now takes another leg's English file instead of
  waiting for OpenSubtitles. A subtitle is timed to the film, not the encode, so the foreign
  file is a far better guess than a hash-less OpenSubtitles search; dubs still never borrow,
  because a dub's own cut is what the text has to match.
- subs: **experimental, off** (`AiSubtitles.EXPERIMENTAL = false`). A dub whose cut ships no
  captions of its own was wired to transcribe the dub audio with Deepgram Nova-3, which is the
  only caption source that cannot drift against it. It stays off because the accurate pass
  cannot run on a muxed master: with no audio-only rendition and a single 1.6 Mbps variant
  (~300MB an episode) only the live socket was left, and its lines landed about a second late
  and read like video-call captions. The AI row is gone from the gear; a provider track always
  wins; debug builds only.
- subs: an English dub no longer shows the sub cut's captions. HiAnime's dub cut links to the
  sub cut's own vtt (same url, same bytes), so the text describes dialogue the dub voice actors
  are not saying and drifts against their timing; the leg keeps only captions the dub owns, and
  OpenSubtitles is skipped on any dub because its English files are cut against the original
  audio too.
- providers: a fixed rung now outranks an adaptive `auto` master. Measured on one title, the
  master settled at 640x268 while its own `vap-1080p` rung served 1920x804 on the same link, so
  the master's advertised height is not what it plays. `__pwRank` and `FormatOrder` keep an
  `auto` rung out of the 1080p+ bucket — its height is the ladder's ceiling, not the stream — and
  below every real rung; the shared `pick_order.txt` fixture pins a 2160 `auto` losing to a real
  800p with EN subs.
- i18n: every string the app renders now lives in `res/values/strings.xml` (161
  strings plus one plural), so a translation is a resource file rather than a
  Kotlin edit, and air dates follow the device locale. The brand name is the
  only string marked non-translatable; the disabled torrent screens are still
  hard-coded English.
- net: cleartext HTTP is now refused app-wide except for `127.0.0.1`, so the update helper
  keeps working but no https request can be silently downgraded. Every configured provider
  host is already https; an http media host would have to be added to
  `res/xml/network_security_config.xml`.
- build: detekt 1.23.8 runs as its own CI job against a curated config and a
  committed baseline (210 frozen findings, down from 1510 raw). It stays out of
  `check` so phone builds keep their speed; the baseline may shrink, never grow.

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
