create table public.todos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (title ~ '[^[:space:]]' and char_length(title) <= 160),
  note text check (char_length(note) <= 4000),
  task_date date not null,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.targets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (title ~ '[^[:space:]]' and char_length(title) <= 160),
  period_type text not null check (period_type in ('week', 'month')),
  period_start date not null,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint targets_period_start check (
    (period_type = 'week' and extract(isodow from period_start) = 1)
    or (period_type = 'month' and extract(day from period_start) = 1)
  )
);

create index todos_owner_date on public.todos (user_id, task_date, created_at, id);
create index targets_owner_period on public.targets (user_id, period_type, period_start, created_at, id);

create function public.set_planner_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger todos_updated_at before update on public.todos
for each row execute function public.set_planner_updated_at();

create trigger targets_updated_at before update on public.targets
for each row execute function public.set_planner_updated_at();

alter table public.todos enable row level security;
alter table public.targets enable row level security;

revoke all on public.todos, public.targets from anon, authenticated;
grant select, delete on public.todos, public.targets to authenticated;
grant insert (id, user_id, title, note, task_date) on public.todos to authenticated;
grant insert (id, user_id, title, period_type, period_start) on public.targets to authenticated;
grant update (title, note, completed_at) on public.todos to authenticated;
grant update (title, completed_at) on public.targets to authenticated;

create policy todos_owner on public.todos for all to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create policy targets_owner on public.targets for all to authenticated
using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
