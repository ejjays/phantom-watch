-- reports flag a comment for review; only a moderator hides it
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
  set report_count = (select count(*)::integer from public.comment_reports r where r.comment_id = c.id)
  where c.id = p_comment;
end $$;

revoke all on function public.report_comment(bigint, text, text) from public, anon;
grant execute on function public.report_comment(bigint, text, text) to authenticated;