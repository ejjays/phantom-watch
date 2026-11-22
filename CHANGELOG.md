# Changelog

## Unreleased

- player: a hung stream gives up instead of spinning. 15s with no first frame drops
  the resume offset, and a host that stops answering is banned for five minutes.
- player: a stalled segment retries its socket every 10s and streams through okhttp,
  so a dead keep alive no longer hangs the loader.
- player: after a hang the same cut's auto master is tried before another rung or
  leg, and a dub is kept when its own rung dies.
- player: crop the black rows a padded 16:9 encode adds, so a 2.39 film fills the
  stage.
- player: 403 and 429 no longer ban the host; only expired DNS gets a ten-minute
  cooldown. One refused segment had hidden a healthy cdn.
- player: one inset per edge, controls clear the display cutout, rail shell picked by
  window width.
- player: floating settings card on phones, new fullscreen glyphs and an episodes
  sidebar.
- player: a 10s card at the end of an episode plays the next one, and resume picks
  up where the episode was left off.
- player: the clip trailer is fitted instead of cropped, and orientation is no longer
  locked; the activity handles rotation.
- player: swipe brightness survives an episode change and clears when leaving the
  player. Pill controls and the player title are larger.
- subs: a rung with no captions gets none. Subs never cross legs, and a dub keeps its
  own cut's file.
- subs: an embedded English track skips the OpenSubtitles fetch, and an undetermined
  subtitle track is no longer picked automatically.
- subs: Deepgram Nova-3 for a dub with no captions, off by default
  (`LiveSubs.EXPERIMENTAL`).
- subs: the unused Supabase subtitle cache and remote key fetch are gone.
- providers: a fixed rung outranks an adaptive `auto` master, and verified English
  audio breaks the tie among sub rungs.
- providers: `providers.js` is the only implementation now, and a leg that cannot
  serve a title reports `skipped`.
- providers: hianime keeps movie support, follows the configured TMDB base and
  retries a page refused with 403 or 429.
- providers: the allanime leg stands down for ten minutes when the site asks for
  captcha instead of burning the address.
- home: history is a bottom tab, not an icon next to search.
- home: the splash stays up until the cached feed is drawn, so no half-built home
  shows first. It gives up after 2.5s on a cold first run.
- home: trending paints before the rails, and the rails load in parallel. The last
  feed paints while the network refreshes it.
- home: the top 10 is shows from the country TV chart, now named "Top 10 Shows in
  %1$s".
- detail: air dates read relative to now, falling back to the raw ISO on bad input.
- library: animated phantom on the empty state.
- settings: Google-style sign-in pill and a guest avatar.
- community: authors can delete their own comments and only moderators hide them.
  The three-report auto flag is gone.
- update: a GitHub release installs in app, with the signer and version pinned. The
  release installer is not exported; debug accepts loopback for local installs.
- i18n: shared owns what it renders and app res keeps platform-only text. The shared
  file is generated into app res at build time, and a check keeps the two disjoint.
- net: release refuses cleartext HTTP; debug permits loopback for the local updater.
- startup: the trailer WebView warms when the clip feed opens, not at launch.
- logs: crash and update logs land in Downloads, with stream urls stripped from
  crash text.
- web: installable from the browser, with the phone player rebuilt in DOM and CSS
  over a `<video>` stage. Chart and country come from the resolver.
- build: `versionCode` follows the commit count, R8 runs on release builds, and pins
  live in a version catalog.
- build: the player splits into `PlayerControls` and `PlayerPanels`, and one shared
  `Route` model drives both shells.
- build: detekt is a CI step against a 122-finding baseline. The flat
  `dev.phantomwatch` package is split by feature.
- build: a crash report is written to disk and offered on the next launch.
- ci: unit tests, provider tests, worker tests, quality, string and doc checks, and
  the R8 release build. Shared logic tests also run on the JVM host.
