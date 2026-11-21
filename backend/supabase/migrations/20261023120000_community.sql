-- Votes and comments for the player community actions.
-- Clients never write votes directly: cast_vote owns the toggle so a user cannot
-- double-vote or forge counts. Comments are insert-only for their author.

create table if not exists public.votes (
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null,
  tmdb_id integer not null,
  value smallint not null check (value in (-1, 1)),
  created_at timestamptz not null default now(),
  primary key (user_id, kind, tmdb_id)
);

create table if not exists public.comments (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null,
  tmdb_id integer not null,
  body text not null check (char_length(btrim(body)) between 1 and 1000),
  created_at timestamptz not null default now(),
  hidden boolean not null default false
);

create index if not exists comments_title_idx on public.comments (kind, tmdb_id, created_at desc);
create index if not exists votes_title_idx on public.votes (kind, tmdb_id);

alter table public.votes enable row level security;
alter table public.comments enable row level security;

drop policy if exists votes_own on public.votes;
create policy votes_own on public.votes for select to authenticated
  using (auth.uid() = user_id);

drop policy if exists comments_read on public.comments;
create policy comments_read on public.comments for select
  using (not hidden);

drop policy if exists comments_insert on public.comments;
create policy comments_insert on public.comments for insert to authenticated
  with check (auth.uid() = user_id);

create or replace function public.comment_throttle() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from comments
      where user_id = new.user_id and created_at > now() - interval '1 minute') >= 5 then
    raise exception 'slow down';
  end if;
  return new;
end $$;

drop trigger if exists comments_throttle on public.comments;
create trigger comments_throttle before insert on public.comments
  for each row execute function public.comment_throttle();

create or replace function public.title_stats(p_kind text, p_tmdb integer)
returns table (likes integer, dislikes integer, mine integer)
language sql stable security definer set search_path = public as $$
  select
    (select count(*) from votes v where v.kind = p_kind and v.tmdb_id = p_tmdb and v.value = 1)::int,
    (select count(*) from votes v where v.kind = p_kind and v.tmdb_id = p_tmdb and v.value = -1)::int,
    coalesce((select v.value from votes v
      where v.user_id = auth.uid() and v.kind = p_kind and v.tmdb_id = p_tmdb), 0)::int;
$$;

create or replace function public.cast_vote(p_kind text, p_tmdb integer, p_value smallint)
returns table (likes integer, dislikes integer, mine integer)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  cur smallint;
begin
  if uid is null then raise exception 'auth required'; end if;
  if p_value not in (-1, 1) then raise exception 'bad vote'; end if;

  select v.value into cur from votes v
    where v.user_id = uid and v.kind = p_kind and v.tmdb_id = p_tmdb;

  if cur = p_value then
    delete from votes where user_id = uid and kind = p_kind and tmdb_id = p_tmdb;
  elsif cur is null then
    insert into votes (user_id, kind, tmdb_id, value) values (uid, p_kind, p_tmdb, p_value);
  else
    update votes set value = p_value
      where user_id = uid and kind = p_kind and tmdb_id = p_tmdb;
  end if;

  return query select * from public.title_stats(p_kind, p_tmdb);
end $$;

revoke all on function public.title_stats(text, integer) from public, anon;
revoke all on function public.cast_vote(text, integer, smallint) from public, anon;
grant execute on function public.title_stats(text, integer) to authenticated;
grant execute on function public.cast_vote(text, integer, smallint) to authenticated;
