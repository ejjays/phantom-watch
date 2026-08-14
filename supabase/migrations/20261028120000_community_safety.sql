-- Harden community writes at the database boundary; client-side checks are advisory only.

alter table public.comments
  add column if not exists moderation_status text not null default 'visible'
    check (moderation_status in ('visible', 'pending', 'hidden')),
  add column if not exists report_count integer not null default 0 check (report_count >= 0);

update public.comments
set moderation_status = case when hidden then 'hidden' else 'visible' end;

alter table public.comments
  add constraint comments_valid_title check (
    kind in ('movie', 'tv') and tmdb_id > 0
  ),
  add constraint comments_no_empty_author check (user_id is not null);

alter table public.comments
  alter column user_id set default auth.uid();

alter table public.comments enable row level security;

drop policy if exists comments_read on public.comments;
create policy comments_read on public.comments for select to authenticated
  using (moderation_status = 'visible' and not hidden);

-- Reply targets are validated by enforce_comment_write(); an unqualified parent_id
-- inside a parent subquery would resolve to the parent row's own column.
drop policy if exists comments_insert on public.comments;
create policy comments_insert on public.comments for insert to authenticated
  with check (
    auth.uid() = user_id and
    kind in ('movie', 'tv') and tmdb_id > 0 and
    char_length(btrim(body)) between 1 and 500
  );

drop policy if exists comments_delete_own on public.comments;
create policy comments_delete_own on public.comments for delete to authenticated
  using (auth.uid() = user_id);

create table if not exists public.comment_reports (
  reporter_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  comment_id bigint not null references public.comments (id) on delete cascade,
  reason text not null check (reason in ('spam', 'harassment', 'spoiler', 'other')),
  details text not null default '' check (char_length(details) <= 300),
  created_at timestamptz not null default now(),
  primary key (reporter_id, comment_id)
);

alter table public.comment_reports enable row level security;
drop policy if exists comment_reports_insert on public.comment_reports;
create policy comment_reports_insert on public.comment_reports for insert to authenticated
  with check (auth.uid() = reporter_id);
drop policy if exists comment_reports_read_own on public.comment_reports;
create policy comment_reports_read_own on public.comment_reports for select to authenticated
  using (auth.uid() = reporter_id);

create or replace function public.enforce_comment_write()
returns trigger
language plpgsql security definer set search_path = public, auth as $$
declare
  uid uuid := auth.uid();
  recent_count integer;
  last_post timestamptz;
begin
  if uid is null or new.user_id is distinct from uid then
    raise exception 'auth required';
  end if;
  if new.kind not in ('movie', 'tv') or new.tmdb_id <= 0 then
    raise exception 'invalid title';
  end if;
  if char_length(btrim(new.body)) not between 1 and 500 then
    raise exception 'comment must be 1 to 500 characters';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(uid::text, 0));
  select count(*)::integer, max(created_at)
    into recent_count, last_post
    from public.comments
    where user_id = uid and created_at > now() - interval '1 hour';
  if recent_count >= 10 then raise exception 'comment rate limit'; end if;
  if last_post is not null and last_post > now() - interval '10 seconds' then
    raise exception 'please wait before posting again';
  end if;

  if new.parent_id is not null and not exists (
    select 1 from public.comments parent
    where parent.id = new.parent_id
      and parent.kind = new.kind
      and parent.tmdb_id = new.tmdb_id
      and parent.parent_id is null
      and parent.moderation_status = 'visible'
      and not parent.hidden
  ) then
    raise exception 'invalid reply target';
  end if;

  new.hidden := false;
  new.report_count := 0;
  new.moderation_status := 'visible';
  return new;
end $$;

revoke all on function public.enforce_comment_write() from public, anon, authenticated;
drop trigger if exists comments_throttle on public.comments;
drop trigger if exists comments_enforce_write on public.comments;
create trigger comments_enforce_write before insert on public.comments
  for each row execute function public.enforce_comment_write();

create or replace function public.report_comment(
  p_comment bigint,
  p_reason text,
  p_details text default ''
) returns void
language plpgsql security definer set search_path = public, auth as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then raise exception 'auth required'; end if;
  if p_reason not in ('spam', 'harassment', 'spoiler', 'other') then
    raise exception 'invalid report reason';
  end if;
  if char_length(coalesce(p_details, '')) > 300 then
    raise exception 'report details too long';
  end if;
  if not exists (select 1 from public.comments where id = p_comment and user_id <> uid) then
    raise exception 'comment unavailable';
  end if;

  insert into public.comment_reports(reporter_id, comment_id, reason, details)
  values (uid, p_comment, p_reason, coalesce(p_details, ''))
  on conflict (reporter_id, comment_id) do nothing;

  update public.comments c
  set report_count = (select count(*)::integer from public.comment_reports r where r.comment_id = c.id),
      moderation_status = case
        when (select count(*) from public.comment_reports r where r.comment_id = c.id) >= 3
          then 'pending'
        else c.moderation_status
      end,
      hidden = case
        when (select count(*) from public.comment_reports r where r.comment_id = c.id) >= 3
          then true
        else c.hidden
      end
  where c.id = p_comment;
end $$;

revoke all on function public.report_comment(bigint, text, text) from public, anon;
grant execute on function public.report_comment(bigint, text, text) to authenticated;

create table if not exists public.community_moderators (
  user_id uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.community_moderators enable row level security;
revoke all on public.community_moderators from public, anon, authenticated;

create or replace function public.moderate_comment(p_comment bigint, p_status text)
returns void
language plpgsql security definer set search_path = public, auth as $$
begin
  if auth.uid() is null or not exists (
    select 1 from public.community_moderators where user_id = auth.uid()
  ) then
    raise exception 'moderator required';
  end if;
  if p_status not in ('visible', 'hidden') then raise exception 'invalid status'; end if;
  update public.comments
  set moderation_status = p_status, hidden = (p_status = 'hidden')
  where id = p_comment;
end $$;

revoke all on function public.moderate_comment(bigint, text) from public, anon;
grant execute on function public.moderate_comment(bigint, text) to authenticated;
