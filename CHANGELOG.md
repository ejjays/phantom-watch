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
- home: the splash stays up until the cached feed is drawn, so no half-built home
  shows first. It gives up after 2.5s on a cold first run.
- player: a 10s card at the end of an episode plays the next one.
- player: resume where the episode was left off.
- community: authors can delete their comments; three reports flag one for review,
  only a moderator hides it.
- update: a GitHub release installs in app, with the signer and version pinned.
- update: the installer activity is no longer exported.
- i18n: every rendered string lives in `res/values/strings.xml`.
- net: cleartext HTTP is refused.
- home: trending paints before the rails, and the rails load in parallel.
- home: the last feed paints while the network refreshes it.
- subs: an embedded English track skips the OpenSubtitles fetch.
- subs: the unused Supabase subtitle cache and remote key fetch are gone.
- startup: the trailer WebView warms when the clip feed opens, not at launch.
- logs: crash and update logs land in Downloads, minus stream urls.
- build: detekt runs as its own CI job against a 210-finding baseline.
- build: `versionCode` follows the commit count, R8 runs on release builds.
- refactor: the flat `dev.phantomwatch` package is split by feature.
- app: a crash report is written to disk and offered on the next launch.
- ci: unit tests, provider tests, worker tests, quality and the R8 release build.
