begin;

alter table public.targets rename to goals;
alter table public.goals rename column period_start to start_date;
alter table public.goals add column finish_date date;

alter table public.goals disable trigger targets_updated_at;
update public.goals
set finish_date = case period_type
  when 'week' then start_date + 6
  else (start_date + interval '1 month')::date - 1
end;
alter table public.goals enable trigger targets_updated_at;

alter table public.goals
  alter column finish_date set not null,
  drop column period_type,
  add constraint goals_date_order check (finish_date >= start_date);

create index goals_owner_finish on public.goals (user_id, finish_date, id);

grant insert (finish_date) on public.goals to authenticated;
grant update (start_date, finish_date) on public.goals to authenticated;
alter policy targets_owner on public.goals rename to goals_owner;
alter trigger targets_updated_at on public.goals rename to goals_updated_at;

commit;
