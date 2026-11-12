import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { localPlanner } from './local-planner';
import { activityDays } from './activity';
import { addDays } from './calendar';
import { localPlannerOwner, scopeKey, type Entry, type EntryDraft, type Scope } from './planner';

export function usePlannerQuery(scope: Scope, enabled = true) {
  const key = scopeKey(scope);
  const owner = localPlannerOwner;
  const query = useQuery({
    queryKey: ['planner', owner, key],
    queryFn: ({ signal }) => localPlanner.list(scope, signal),
    enabled,
    retry: false,
    staleTime: 15_000,
    networkMode: 'always',
  });
  return query;
}

export function useTodoActivity(today: string) {
  const client = useQueryClient();
  const owner = localPlannerOwner;
  const query = useQuery({
    queryKey: ['planner', owner, 'activity', today],
    queryFn: ({ signal }) => localPlanner.activity(addDays(today, 1 - activityDays), today, signal),
    retry: false,
    staleTime: 15_000,
    networkMode: 'always',
  });
  useFocusEffect(useCallback(() => {
    void client.invalidateQueries({ queryKey: ['planner', owner, 'activity', today] });
  }, [client, owner, today]));
  return query;
}

export function useEntries(scope: Scope) {
  const client = useQueryClient();
  const query = usePlannerQuery(scope);
  const key = scopeKey(scope);
  const owner = localPlannerOwner;

  useFocusEffect(useCallback(() => {
    void client.invalidateQueries({ queryKey: ['planner', owner, key] });
  }, [client, key, owner]));

  async function write(destination: Scope, action: () => Promise<void>, update: (entries: Entry[]) => Entry[]) {
    await action();
    const destinationKey = ['planner', owner, scopeKey(destination)];
    await client.cancelQueries({ queryKey: destinationKey, exact: true });
    client.setQueryData<Entry[]>(destinationKey, entries => entries && update(entries));
    void client.invalidateQueries({ queryKey: destinationKey, exact: true });
    if (destination.kind === 'todo') void client.invalidateQueries({ queryKey: ['planner', owner, 'activity'] });
  }

  return {
    query,
    save: (destination: Scope, draft: EntryDraft, isNew: boolean) => write(
      destination, () => localPlanner.save(destination, draft, isNew), entries => {
        if (isNew && !entries.some(entry => entry.id === draft.id)) return [...entries, { ...draft, completedAt: null }];
        return isNew ? entries : entries.map(entry => entry.id === draft.id ? { ...entry, ...draft } : entry);
      },
    ),
    toggle: (entry: Entry) => {
      const completedAt = entry.completedAt ? null : new Date().toISOString();
      return write(scope, () => localPlanner.setCompleted(scope, entry.id, completedAt), entries =>
        entries.map(item => item.id === entry.id ? { ...item, completedAt } : item));
    },
    remove: (destination: Scope, id: string) => write(destination, () => localPlanner.remove(destination, id), entries =>
      entries.filter(entry => entry.id !== id)),
  };
}
