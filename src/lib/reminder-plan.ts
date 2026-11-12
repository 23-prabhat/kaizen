import { addDays, localDay } from './calendar';
import type { Entry } from './planner';

export type ReminderKind = 'todos' | 'goals';
export type ReminderPermission = 'granted' | 'requestable' | 'blocked';
export type Reminder = { day: string; at: number; title: string; body: string };
export type ReminderPlan = Partial<Record<ReminderKind, Reminder[]>>;

function localTime(day: string, hour: number) {
  return new Date(`${day}T${String(hour).padStart(2, '0')}:00:00`).getTime();
}

export function pendingTodoReminders(entries: Entry[], now: Date): Reminder[] {
  const day = localDay(now);
  const at = localTime(day, 19);
  const pending = entries.filter(entry => !entry.completedAt).length;
  if (!pending || at <= now.getTime()) return [];
  return [{ day, at, title: 'Unfinished to-dos', body: pending === 1
    ? 'You have 1 to-do left today.' : `You have ${pending} to-dos left today.` }];
}

export function goalDeadlineReminders(entries: Entry[], now: Date): Reminder[] {
  const today = localDay(now);
  const byDay = new Map<string, { today: number; tomorrow: number }>();
  function count(day: string, due: 'today' | 'tomorrow') {
    const totals = byDay.get(day) ?? { today: 0, tomorrow: 0 };
    totals[due]++;
    byDay.set(day, totals);
  }

  for (const { completedAt, schedule } of entries) {
    if (completedAt || !schedule || schedule.finish < today) continue;
    count(schedule.finish, 'today');
    if (schedule.start < schedule.finish) count(addDays(schedule.finish, -1), 'tomorrow');
  }

  return [...byDay.entries()].sort(([left], [right]) => left.localeCompare(right)).flatMap(([day, totals]) => {
    const at = localTime(day, 9);
    if (at <= now.getTime()) return [];
    const body = (['today', 'tomorrow'] as const).flatMap(due => {
      const count = totals[due];
      if (!count) return [];
      return [count === 1 ? `1 goal is due ${due}.` : `${count} goals are due ${due}.`];
    }).join(' ');
    return [{ day, at, title: 'Goal deadlines', body }];
  // Keep below iOS's pending notification limit and leave room for the to-do reminder.
  }).slice(0, 60);
}
