import { addDays, calendarDays, formatDay, fullDayLabel, lastCalendarDay } from './calendar';

export type GoalSchedule = { start: string; finish: string };

export function finishForDuration(start: string, duration: string): string | null {
  const days = Number(duration);
  if (!/^\d+$/.test(duration) || days < 1 || days > calendarDays(start, lastCalendarDay)) return null;
  return addDays(start, days - 1);
}

export function goalRange({ start, finish }: GoalSchedule) {
  if (start === finish) return fullDayLabel(start);
  const first = start.slice(0, 4) === finish.slice(0, 4)
    ? formatDay(start, { day: 'numeric', month: 'short' }) : fullDayLabel(start);
  return `${first} – ${fullDayLabel(finish)}`;
}

export function goalPhase({ start, finish }: GoalSchedule, day: string) {
  if (day < start) return 'Upcoming';
  if (day > finish) return 'Past';
  return 'Active';
}
