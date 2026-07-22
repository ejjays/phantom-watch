-- Per-comment likes. Mirrors public.votes/cast_vote: clients never write
-- counts, cast_comment_vote owns the toggle so a user cannot double-vote.

create table if not exists public.comment_likes (
  user_id uuid not null references auth.users (id) on delete cascade,
  comment_id bigint not null references public.comments (id) on delete cascade,
  value smallint not null check (value in (-1, 1)),
  created_at timestamptz not null default now(),
  primary key (user_id, comment_id)
);

create index if not exists comment_likes_comment_idx on public.comment_likes (comment_id);

alter table public.comment_likes enable row level security;

drop policy if exists comment_likes_own on public.comment_likes;
create policy comment_likes_own on public.comment_likes for select to authenticated
  using (auth.uid() = user_id);

create or replace function public.comment_vote_stats(p_comment bigint)
returns table (likes integer, dislikes integer, mine integer)
language sql stable security definer set search_path = public as $$
  select
    (select count(*) from comment_likes l where l.comment_id = p_comment and l.value = 1)::int,
    (select count(*) from comment_likes l where l.comment_id = p_comment and l.value = -1)::int,
    coalesce((select l.value from comment_likes l
      where l.user_id = auth.uid() and l.comment_id = p_comment), 0)::int;
$$;

create or replace function public.comment_stats(p_kind text, p_tmdb integer)
returns table (comment_id bigint, likes integer, dislikes integer, mine integer)
language sql stable security definer set search_path = public as $$
  select
    c.id,
    (select count(*) from comment_likes l where l.comment_id = c.id and l.value = 1)::int,
    (select count(*) from comment_likes l where l.comment_id = c.id and l.value = -1)::int,
    coalesce((select l.value from comment_likes l
      where l.user_id = auth.uid() and l.comment_id = c.id), 0)::int
  from comments c
  where c.kind = p_kind and c.tmdb_id = p_tmdb and not c.hidden;
$$;

create or replace function public.cast_comment_vote(p_comment bigint, p_value smallint)
returns table (likes integer, dislikes integer, mine integer)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  cur smallint;
begin
  if uid is null then raise exception 'auth required'; end if;
  if p_value not in (-1, 1) then raise exception 'bad vote'; end if;

  select l.value into cur from comment_likes l
    where l.user_id = uid and l.comment_id = p_comment;

  if cur = p_value then
    delete from comment_likes where user_id = uid and comment_id = p_comment;
  elsif cur is null then
    insert into comment_likes (user_id, comment_id, value) values (uid, p_comment, p_value);
  else
    update comment_likes set value = p_value
      where user_id = uid and comment_id = p_comment;
  end if;

  return query select * from public.comment_vote_stats(p_comment);
end $$;

revoke all on function public.comment_vote_stats(bigint) from public, anon;
revoke all on function public.comment_stats(text, integer) from public, anon;
revoke all on function public.cast_comment_vote(bigint, smallint) from public, anon;
grant execute on function public.comment_vote_stats(bigint) to authenticated;
grant execute on function public.comment_stats(text, integer) to authenticated;
grant execute on function public.cast_comment_vote(bigint, smallint) to authenticated;
