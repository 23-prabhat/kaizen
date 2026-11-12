import { addDays } from './calendar';

export type TodoActivity = { day: string; done: number; remaining: number };
export const activityDays = 14;

export function summarizeActivity(start: string, end: string, records: { day: string; completedAt: string | null }[]) {
  const counts = new Map<string, TodoActivity>();
  for (let day = start; day <= end; day = addDays(day, 1)) {
    counts.set(day, { day, done: 0, remaining: 0 });
    if (day === end) break;
  }
  for (const record of records) {
    const count = counts.get(record.day);
    if (count) count[record.completedAt ? 'done' : 'remaining']++;
  }
  return [...counts.values()];
}
