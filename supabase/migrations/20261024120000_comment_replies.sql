-- replies: one level of threading under a root comment
alter table public.comments
  add column if not exists parent_id bigint references public.comments (id) on delete cascade;

create index if not exists comments_parent_idx on public.comments (parent_id, created_at);
