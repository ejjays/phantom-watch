# MovieProto — minimal native VidLove player

Home = TMDB trending grid. Tap a movie to play, tap a show to pick season/episode.
Player = ExoPlayer, VidLove stream only, VidLove subs only (English preferred).

## Run

1. Open this folder in Android Studio.
2. Create `local.properties` next to this README:
   `TMDB_API_KEY=<same key as mobile/.env EXPO_PUBLIC_TMDB_API_KEY>`
3. Run on a device (min SDK 26).

## Notes

- No relay through other providers: stream + subs both come from the same
  VidLove response, so they stay in sync by construction.
- Best video is capped at 1080p; moviebox mp4 is last resort.

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
