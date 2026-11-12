import type { GoalSchedule } from './goal-schedule';
import type { TodoActivity } from './activity';

export const localPlannerOwner = 'local-demo';

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
