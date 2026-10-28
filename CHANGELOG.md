# Changelog

## Unreleased

- player: a hung stream gives up instead of spinning. 15s with no first frame drops
  the resume offset, 30s of buffering bans the host for two hours.
- player: crop the black rows a padded 16:9 encode adds, so a 2.39 film fills the
  stage.
- player: a 403 is a ten-minute cooldown, not a day-long host ban. The same url
  answered 403 to our user-agent and 429 with a referer.
- player: one inset per edge, controls clear the display cutout, rail shell picked by
  window width.
- subs: a rung with no captions takes another leg's English file instead of waiting
  on OpenSubtitles. Dubs still never borrow.
- subs: an English dub no longer shows the sub cut's captions.
- subs: Deepgram Nova-3 for a dub with no captions, off by default
  (`AiSubtitles.EXPERIMENTAL`).
- providers: a fixed rung outranks an adaptive `auto` master. One title's master
  settled at 640x268 while its own 1080p rung served 1920x804.
- providers: `providers.js` is the only implementation now.
- providers: a leg that cannot serve a title reports `skipped`.
- providers: hianime keeps movie support and follows the configured TMDB base.
- home: a history icon next to search resumes what you were part way through.
- player: a 10s card at the end of an episode plays the next one.
- player: resume where the episode was left off.
- community: authors can delete their comments; three reports hide one pending
  review.
- update: the loopback updater needs a one-shot token and a matching signer.
- i18n: every rendered string lives in `res/values/strings.xml`.
- net: cleartext HTTP is refused except for `127.0.0.1`.
- build: detekt runs as its own CI job against a 210-finding baseline.
- build: `versionCode` follows the commit count, R8 runs on release builds.
- refactor: the flat `dev.phantomwatch` package is split by feature.
- app: a crash report is written to disk and offered on the next launch.
- ci: unit tests, provider tests, worker tests, quality and the R8 release build.
