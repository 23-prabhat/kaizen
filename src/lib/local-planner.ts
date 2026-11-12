import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import { summarizeActivity } from './activity';
import { shiftPeriod, addDays, type Period } from './calendar';
import { scopeKey, type Entry, type Planner } from './planner';

// Preserve all records saved in the earlier device-only preview.
const storageKey = 'daytabs.demo.v1';
type DeviceRecords = Record<string, Entry[]>;
type WorkReport = { id: string; day: string; title: string; note: string | null; done: boolean };
export const maxReportBytes = 1_000_000;

const stockTodos = new Map([
  ['dentist', 'Book a dentist appointment'], ['estimate', 'Send the revised estimate'],
  ['plants', 'Water the plants'], ['reading', 'Read 20 pages'],
]);

function parseWorkReports(input: string): WorkReport[] {
  if (input.length > maxReportBytes) throw new Error('Choose a report file under 1 MB.');
  let data: unknown;
  try { data = JSON.parse(input); }
  catch { throw new Error('Choose a valid Kaizen report file.'); }
  if (!Array.isArray(data) || !data.length) throw new Error('No to-dos found in this file.');
  return data.map((row, i) => {
    if (!row || typeof row !== 'object') throw new Error(`To-do ${i + 1} is invalid.`);
    const { id, day, title, note, done } = row;
    const date = new Date(`${day}T00:00:00.000Z`);
    if (typeof id !== 'string' || !/^[\w-]{1,120}$/.test(id)
      || typeof day !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(day) || day < '0001-01-01'
      || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== day
      || typeof title !== 'string' || !title.trim() || title.length > 160
      || (note !== null && (typeof note !== 'string' || note.length > 4000)) || typeof done !== 'boolean') {
      throw new Error(`To-do ${i + 1} is invalid.`);
    }
    return { id, day, title: title.trim(), note, done };
  });
}

function mergeReports(records: DeviceRecords, reports: WorkReport[]) {
  let added = 0;
  for (const report of reports) {
    const key = scopeKey({ kind: 'todo', day: report.day });
    const id = `work:${report.id}`;
    const entries = records[key] ??= [];
    if (entries.some(entry => entry.id === id)) continue;
    entries.push({
      id, title: report.title, note: report.note,
      completedAt: report.done ? `${report.day}T12:00:00.000Z` : null,
    });
    added++;
  }
  return added;
}

function loadPreviewReports(records: DeviceRecords) {
  const input = process.env.EXPO_PUBLIC_DAYTABS_LOCAL_REPORT;
  if (!__DEV__ || !input || records['import:november-work']) return false;
  const reports = parseWorkReports(input);
  for (const key of Object.keys(records)) {
    if (key.startsWith('todo:')) records[key] = records[key].filter(entry => stockTodos.get(entry.id) !== entry.title);
  }
  mergeReports(records, reports);
  records['import:november-work'] = [];
  return true;
}

function migrateGoals(records: DeviceRecords) {
  const goals = records.goals ?? [];
  let changed = false;
  for (const key of Object.keys(records)) {
    const match = /^target:(week|month):(\d{4}-\d{2}-\d{2})$/.exec(key);
    if (!match) continue;
    const period = match[1] as Period;
    const start = match[2];
    const finish = addDays(shiftPeriod(start, period, 1), -1);
    for (const entry of records[key]) goals.push({ ...entry, schedule: { start, finish } });
    delete records[key];
    changed = true;
  }
  if (changed) records.goals = goals;
  return changed;
}

let operations = Promise.resolve();

function withRecords<T>(action: (records: DeviceRecords) => Promise<T>): Promise<T> {
  async function run() {
    const saved = await AsyncStorage.getItem(storageKey);
    const records: DeviceRecords = saved ? JSON.parse(saved) : {};
    const goalsMigrated = migrateGoals(records);
    const reportsAdded = loadPreviewReports(records);
    if (goalsMigrated || reportsAdded) await AsyncStorage.setItem(storageKey, JSON.stringify(records));
    return action(records);
  }
  const next = operations.then(() => Platform.OS === 'web' && typeof navigator !== 'undefined' && navigator.locks
    ? navigator.locks.request('daytabs:device-storage', run) : run());
  operations = next.then(() => undefined, () => undefined);
  return next;
}

function change(key: string, update: (entries: Entry[]) => Entry[]) {
  return withRecords(async records => {
    records[key] = update(records[key] ?? []);
    await AsyncStorage.setItem(storageKey, JSON.stringify(records));
  });
}

export function importWorkReports(input: string) {
  const reports = parseWorkReports(input);
  return withRecords(async records => {
    const added = mergeReports(records, reports);
    if (added) await AsyncStorage.setItem(storageKey, JSON.stringify(records));
    return added;
  });
}

export const localPlanner: Planner = {
  list: scope => withRecords(async records => records[scopeKey(scope)] ?? []),
  activity: (start, end) => withRecords(async records => summarizeActivity(start, end,
    Object.entries(records).flatMap(([key, entries]) => {
      if (!key.startsWith('todo:')) return [];
      return entries.map(({ completedAt }) => ({ day: key.slice(5), completedAt }));
    }))),
  save: (scope, draft, isNew) => change(scopeKey(scope), entries => {
    const existing = entries.find(entry => entry.id === draft.id);
    if (isNew) return existing ? entries : [...entries, { ...draft, completedAt: null }];
    if (!existing) throw new Error('This item was deleted.');
    return entries.map(entry => entry.id === draft.id ? { ...entry, ...draft } : entry);
  }),
  setCompleted: (scope, id, completedAt) => change(scopeKey(scope), entries =>
    entries.map(entry => entry.id === id ? { ...entry, completedAt } : entry)),
  remove: (scope, id) => change(scopeKey(scope), entries => entries.filter(entry => entry.id !== id)),
};
