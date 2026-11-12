import AsyncStorage from '@react-native-async-storage/async-storage';
import { useQueryClient } from '@tanstack/react-query';
import { createContext, useContext, useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';
import { useAuth } from './auth';
import { localDay } from './calendar';
import { useToday } from './clock';
import { goalsScope, scopeKey } from './planner';
import { getReminderPermission, requestReminderPermission, supportsReminders, syncDeviceReminders } from './reminder-device';
import { goalDeadlineReminders, pendingTodoReminders, type ReminderKind, type ReminderPermission, type ReminderPlan } from './reminder-plan';
import { usePlannerQuery } from './use-entries';

type Preferences = Record<ReminderKind, boolean>;
const off: Preferences = { todos: false, goals: false };

type Reminders = {
  supported: boolean;
  ready: boolean;
  preferences: Preferences;
  permission: ReminderPermission | null;
  syncError: boolean;
  change: (kind: ReminderKind, enabled: boolean) => Promise<void>;
};

const ReminderContext = createContext<Reminders | null>(null);

export function ReminderProvider({ children }: PropsWithChildren) {
  const { account, loading } = useAuth();
  const owner = account?.id;
  const today = useToday();
  const client = useQueryClient();
  const [stored, setStored] = useState<{ owner: string; preferences: Preferences }>();
  const [permission, setPermission] = useState<ReminderPermission | null>(null);
  const [syncError, setSyncError] = useState(false);
  const [resume, setResume] = useState(0);
  const changing = useRef(false);
  const ready = Boolean(owner && stored?.owner === owner);
  const preferences = ready ? stored!.preferences : off;
  const enabled = supportsReminders && ready && permission === 'granted';
  const todoOn = enabled && preferences.todos;
  const goalOn = enabled && preferences.goals;
  const { query: todos } = usePlannerQuery({ kind: 'todo', day: today }, todoOn);
  const { query: goals } = usePlannerQuery(goalsScope, goalOn);
  const todoData = todoOn ? todos.data : undefined;
  const goalData = goalOn ? goals.data : undefined;

  useEffect(() => {
    if (!supportsReminders || !owner) return;
    let active = true;
    AsyncStorage.getItem(`daytabs.reminders:${owner}`).then(saved => {
      const values: Partial<Preferences> | null = saved ? JSON.parse(saved) : null;
      if (active) setStored({ owner, preferences: { todos: values?.todos === true, goals: values?.goals === true } });
    }).catch(() => {
      if (active) setStored({ owner, preferences: off });
    });
    return () => { active = false; };
  }, [owner]);

  useEffect(() => {
    if (!supportsReminders) return;
    let active = true;
    async function readPermission() {
      if (changing.current) return;
      try {
        const current = await getReminderPermission();
        if (active && !changing.current) setPermission(current);
      } catch {
        if (active) setSyncError(true);
      }
    }
    void readPermission();
    const subscription = AppState.addEventListener('change', state => {
      if (state !== 'active') return;
      void readPermission();
      setResume(value => value + 1);
    });
    return () => { active = false; subscription.remove(); };
  }, []);

  useEffect(() => {
    if (!resume || !owner) return;
    if (todoOn) void client.invalidateQueries({ queryKey: ['planner', owner, scopeKey({ kind: 'todo', day: today })] });
    if (goalOn) void client.invalidateQueries({ queryKey: ['planner', owner, scopeKey(goalsScope)] });
  }, [client, goalOn, owner, resume, today, todoOn]);

  useEffect(() => {
    if (!supportsReminders || loading) return;
    const plan: ReminderPlan = {};
    const now = new Date();
    if (ready) {
      if (!preferences.todos || (permission && !todoOn)) plan.todos = [];
      else if (todoData && today === localDay(now)) plan.todos = pendingTodoReminders(todoData, now);
      if (!preferences.goals || (permission && !goalOn)) plan.goals = [];
      else if (goalData) plan.goals = goalDeadlineReminders(goalData, now);
    }
    let active = true;
    syncDeviceReminders(owner, plan).then(() => {
      if (active) setSyncError(false);
    }).catch(() => {
      if (active) setSyncError(true);
    });
    return () => { active = false; };
  }, [goalData, goalOn, loading, owner, permission, preferences, ready, resume, today, todoData, todoOn]);

  async function change(kind: ReminderKind, enabled: boolean) {
    if (!supportsReminders || !owner || !ready || changing.current) return;
    changing.current = true;
    try {
      if (enabled) {
        let current = await getReminderPermission();
        if (current === 'requestable') current = await requestReminderPermission();
        setPermission(current);
        if (current !== 'granted') throw new Error('Allow notifications in your device settings to turn on reminders.');
      }
      const next = { ...preferences, [kind]: enabled };
      await AsyncStorage.setItem(`daytabs.reminders:${owner}`, JSON.stringify(next));
      setStored({ owner, preferences: next });
    } finally {
      changing.current = false;
    }
  }

  return <ReminderContext.Provider value={{ supported: supportsReminders, ready, preferences, permission, syncError, change }}>
    {children}
  </ReminderContext.Provider>;
}

export function useReminders() {
  const reminders = useContext(ReminderContext);
  if (!reminders) throw new Error('Reminder provider is required.');
  return reminders;
}
