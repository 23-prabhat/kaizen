export type Period = 'week' | 'month';
export const firstCalendarDay = '0001-01-01';
export const lastCalendarDay = '9999-12-31';

export function localDay(now = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function calendarDate(day: string) {
  return new Date(`${day}T00:00:00.000Z`);
}

export function addDays(day: string, count: number): string {
  const date = calendarDate(day);
  date.setUTCDate(date.getUTCDate() + count);
  return date.toISOString().slice(0, 10);
}

export function startOfPeriod(day: string, period: Period): string {
  if (period === 'month') return `${day.slice(0, 7)}-01`;
  const daysSinceMonday = (calendarDate(day).getUTCDay() + 6) % 7;
  return addDays(day, -daysSinceMonday);
}

export function shiftPeriod(start: string, period: Period, direction: number): string {
  if (period === 'week') return addDays(start, direction * 7);
  const date = calendarDate(start);
  date.setUTCMonth(date.getUTCMonth() + direction, 1);
  return date.toISOString().slice(0, 10);
}

export function formatDay(day: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('en-GB', { ...options, timeZone: 'UTC' }).format(calendarDate(day));
}

export function dayLabel(day: string) {
  return `${formatDay(day, { weekday: 'short' })}, ${formatDay(day, { month: 'short', day: 'numeric' })}`;
}

export function calendarDays(start: string, finish: string): number {
  return (calendarDate(finish).getTime() - calendarDate(start).getTime()) / 86_400_000 + 1;
}

export function fullDayLabel(day: string) {
  return formatDay(day, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function monthDays(month: string) {
  const first = startOfPeriod(month, 'month');
  const last = calendarDate(first);
  last.setUTCMonth(last.getUTCMonth() + 1, 0);
  const offset = (calendarDate(first).getUTCDay() + 6) % 7;
  const days: (string | null)[] = Array.from({ length: offset }, () => null);
  for (let day = 1; day <= last.getUTCDate(); day++) days.push(addDays(first, day - 1));
  while (days.length % 7) days.push(null);
  return days;
}
