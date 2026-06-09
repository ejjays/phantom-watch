# Phantom Watch — minimal native streaming player

Home = TMDB trending grid. Tap a movie to play, tap a show to pick season/episode.
Player = ExoPlayer; streams merged from six direct providers (VidLove, VidRock,
VixSrc, VidZee, AnimeParadise, VAPlayer, best height wins); subs preferred from VidLove,
then OpenSubtitles / AI.

## Run

1. Open this folder in Android Studio.
2. Create `local.properties` next to this README:
   `TMDB_API_KEY=<same key as mobile/.env EXPO_PUBLIC_TMDB_API_KEY>`
3. Run on a device (min SDK 26).

## Notes

- VidLove supplies stream + subs in one response, so those stay in sync by
  construction; the other providers are stream-only.
- Best video is capped at 1080p; moviebox mp4 is last resort.

## Providers (hosts, keys and extractors without an APK)

`app/src/main/assets/providers.js` holds the extractors and `providers.json`
the hosts, keys, source lists and per-leg `enabled` flags. Both ship inside the
APK as the trusted fallback and together form one signed bundle: ECDSA P-256
over the payload, strictly increasing `version`, cached in prefs. Installs pick
up a newer bundle on next launch, so parsing, crypto and new providers ship the
same way as host changes. Kotlin legs remain as a fallback.

Deploy: `python3 sign_config.py --version N` (N above the deployed version),
then upload `build/pages-config/providers.json` alone to the `CFG_URL` from
`local.properties`. Signing needs the private key; the app verifies with
`assets/ota_pub.pem`. Never upload the repo root.

`node probe.js` health-checks every leg by running that same `providers.js`.

## Subtitle cache (Supabase, once)

Sub hauls are cached per title so one opensubtitles fetch serves every
later viewer. Run this in the Supabase SQL editor once:

```sql
create table if not exists public.subtitles (
  key text primary key,
  lang text not null default 'en',
  label text not null default 'English',
  vtt text not null,
  source text not null default 'opensubtitles',
  created_at timestamptz not null default now()
);
alter table public.subtitles enable row level security;
drop policy if exists subtitles_read on public.subtitles;
create policy subtitles_read on public.subtitles
  for select using (true);
drop policy if exists subtitles_insert on public.subtitles;
create policy subtitles_insert on public.subtitles
  for insert with check (auth.uid() is not null);
```

Flow: bundled subs → VidLove → cache hit → opensubtitles fresh
(stored for next time). Writes use an anonymous session, no login needed.

## AI subs (Groq, optional)

For mistimed or missing subs, the AI button transcribes the video's own
audio straight to English timed cues (Groq whisper `/translations`,
same endpoint the main app uses). Put keys in `local.properties`:

```
GROQ_API_KEY=<key1>,<key2>
```

Keys rotate on rate limits. Timing comes from your video's audio, so it
cannot drift. Results cache to Supabase with source `ai`.
Qwen line-translation stays parked for non-English targets.
