import type { GoalSchedule } from './goal-schedule';
import { summarizeActivity, type TodoActivity } from './activity';
import { supabase } from './supabase';

export type Scope = { kind: 'todo'; day: string } | { kind: 'goal' };
export type Entry = { id: string; title: string; note: string | null; completedAt: string | null; schedule?: GoalSchedule };
export type EntryDraft = Pick<Entry, 'id' | 'title' | 'note' | 'schedule'>;
export const goalsScope = { kind: 'goal' } as const;

export interface Planner {
  list(scope: Scope, signal?: AbortSignal): Promise<Entry[]>;
  activity(start: string, end: string, signal?: AbortSignal): Promise<TodoActivity[]>;
  save(scope: Scope, draft: EntryDraft, isNew: boolean): Promise<void>;
  setCompleted(scope: Scope, id: string, completedAt: string | null): Promise<void>;
  remove(scope: Scope, id: string): Promise<void>;
}

export function scopeKey(scope: Scope) {
  return scope.kind === 'todo' ? `todo:${scope.day}` : 'goals';
}

function table(scope: Scope) {
  return scope.kind === 'todo' ? 'todos' : 'goals';
}

function columns(scope: Scope) {
  return scope.kind === 'todo' ? { task_date: scope.day } : {};
}

function backend() {
  if (!supabase) throw new Error('Supabase is not configured.');
  return supabase;
}

export function createSupabasePlanner(userId: string): Planner {
  return {
    async activity(start, end, signal) {
      const records: { day: string; completedAt: string | null }[] = [];
      const pageSize = 250;
      for (let offset = 0; ; offset += pageSize) {
        const request = backend().from('todos').select('task_date,completed_at').eq('user_id', userId)
          .gte('task_date', start).lte('task_date', end).order('task_date').order('id').range(offset, offset + pageSize - 1);
        const { data, error } = await (signal ? request.abortSignal(signal) : request);
        if (error) throw error;
        records.push(...data.map(row => ({ day: row.task_date, completedAt: row.completed_at })));
        if (data.length < pageSize) return summarizeActivity(start, end, records);
      }
    },
    async list(scope, signal) {
      const rows: Entry[] = [];
      const pageSize = 250;
      for (let offset = 0; ; offset += pageSize) {
        const select = scope.kind === 'todo'
          ? backend().from('todos').select('id,title,note,completed_at')
          : backend().from('goals').select('id,title,completed_at,start_date,finish_date');
        const request = select.eq('user_id', userId).match(columns(scope))
          .order(scope.kind === 'todo' ? 'created_at' : 'finish_date').order('id').range(offset, offset + pageSize - 1);
        const { data, error } = await (signal ? request.abortSignal(signal) : request);
        if (error) throw error;
        rows.push(...data.map(row => ({
          id: row.id,
          title: row.title,
          note: 'note' in row ? row.note : null,
          completedAt: row.completed_at,
          ...('start_date' in row && { schedule: { start: row.start_date, finish: row.finish_date } }),
        })));
        if (data.length < pageSize) return rows;
      }
    },
    async save(scope, draft, isNew) {
      const { schedule, title, note } = draft;
      if (scope.kind === 'goal' && !schedule) throw new Error('Goal dates are required.');
      const fields = scope.kind === 'todo' ? { title, note } : {
        title, start_date: schedule?.start, finish_date: schedule?.finish,
      };
      const records = backend().from(table(scope));
      if (isNew) {
        const { error } = await records.upsert({ id: draft.id, user_id: userId, ...columns(scope), ...fields }, {
          onConflict: 'id', ignoreDuplicates: true,
        });
        if (error) throw error;
        return;
      }
      const { data, error } = await records.update(fields).eq('id', draft.id).eq('user_id', userId)
        .match(columns(scope)).select('id').maybeSingle();
      if (error) throw error;
      if (!data) throw new Error('This item was deleted.');
    },
    async setCompleted(scope, id, completedAt) {
      const { data, error } = await backend().from(table(scope)).update({ completed_at: completedAt })
        .eq('id', id).eq('user_id', userId).match(columns(scope)).select('id').maybeSingle();
      if (error) throw error;
      if (!data) throw new Error('This item was deleted.');
    },
    async remove(scope, id) {
      const { error } = await backend().from(table(scope)).delete().eq('id', id)
        .eq('user_id', userId).match(columns(scope));
      if (error) throw error;
    },
  };
}
