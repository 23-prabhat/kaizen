import { useNetInfo } from '@react-native-community/netinfo';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { useAuth } from './auth';
import { activityDays } from './activity';
import { addDays } from './calendar';
import { scopeKey, type Entry, type EntryDraft, type Scope } from './planner';

export function usePlannerQuery(scope: Scope, enabled = true) {
  const { account, planner } = useAuth();
  const network = useNetInfo();
  const offline = account?.storage === 'supabase' && (network.isConnected === false || network.isInternetReachable === false);
  const key = scopeKey(scope);
  const owner = account?.id;
  const query = useQuery({
    queryKey: ['planner', owner, key],
    queryFn: ({ signal }) => planner!.list(scope, signal),
    enabled: enabled && Boolean(planner) && !offline,
    retry: false,
    staleTime: 15_000,
    networkMode: 'always',
  });
  return { query, offline };
}

export function useTodoActivity(today: string, offline: boolean) {
  const { account, planner } = useAuth();
  const client = useQueryClient();
  const owner = account?.id;
  const query = useQuery({
    queryKey: ['planner', owner, 'activity', today],
    queryFn: ({ signal }) => planner!.activity(addDays(today, 1 - activityDays), today, signal),
    enabled: Boolean(planner) && !offline,
    retry: false,
    staleTime: 15_000,
    networkMode: 'always',
  });
  useFocusEffect(useCallback(() => {
    if (owner && !offline) void client.invalidateQueries({ queryKey: ['planner', owner, 'activity', today] });
  }, [client, offline, owner, today]));
  return query;
}

export function useEntries(scope: Scope) {
  const { account, planner } = useAuth();
  const client = useQueryClient();
  const { query, offline } = usePlannerQuery(scope);
  const key = scopeKey(scope);
  const owner = account?.id;

  useFocusEffect(useCallback(() => {
    if (owner && !offline) void client.invalidateQueries({ queryKey: ['planner', owner, key] });
  }, [client, key, offline, owner]));

  async function write(destination: Scope, action: () => Promise<void>, update: (entries: Entry[]) => Entry[]) {
    if (offline) throw new Error('Reconnect to the internet and try again.');
    await action();
    const destinationKey = ['planner', owner, scopeKey(destination)];
    await client.cancelQueries({ queryKey: destinationKey, exact: true });
    client.setQueryData<Entry[]>(destinationKey, entries => entries && update(entries));
    void client.invalidateQueries({ queryKey: destinationKey, exact: true });
    if (destination.kind === 'todo') void client.invalidateQueries({ queryKey: ['planner', owner, 'activity'] });
  }

  return {
    query, offline,
    save: (destination: Scope, draft: EntryDraft, isNew: boolean) => write(
      destination, () => planner!.save(destination, draft, isNew), entries => {
        if (isNew && !entries.some(entry => entry.id === draft.id)) return [...entries, { ...draft, completedAt: null }];
        return isNew ? entries : entries.map(entry => entry.id === draft.id ? { ...entry, ...draft } : entry);
      },
    ),
    toggle: (entry: Entry) => {
      const completedAt = entry.completedAt ? null : new Date().toISOString();
      return write(scope, () => planner!.setCompleted(scope, entry.id, completedAt), entries =>
        entries.map(item => item.id === entry.id ? { ...item, completedAt } : item));
    },
    remove: (destination: Scope, id: string) => write(destination, () => planner!.remove(destination, id), entries =>
      entries.filter(entry => entry.id !== id)),
  };
}
