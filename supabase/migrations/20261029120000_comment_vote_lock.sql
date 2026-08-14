-- cast_comment_vote reads then writes; without a lock two calls from one user can
-- both see no row and race the primary key.

create or replace function public.cast_comment_vote(p_comment bigint, p_value smallint)
returns table (likes integer, dislikes integer, mine integer)
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  cur smallint;
begin
  if uid is null then raise exception 'auth required'; end if;
  if p_value not in (-1, 1) then raise exception 'bad vote'; end if;

  perform pg_advisory_xact_lock(hashtextextended(uid::text || ':' || p_comment::text, 0));

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

revoke all on function public.cast_comment_vote(bigint, smallint) from public, anon;
grant execute on function public.cast_comment_vote(bigint, smallint) to authenticated;
