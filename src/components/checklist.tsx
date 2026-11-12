import { useRef, useState, type ReactNode } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useToday } from '@/lib/clock';
import { goalPhase, goalRange } from '@/lib/goal-schedule';
import { scopeKey, type Entry, type Scope } from '@/lib/planner';
import { useAppearance } from '@/lib/theme';
import { useEntries } from '@/lib/use-entries';
import { EntryEditor, type EditorState } from './entry-editor';
import { Action, Copy, Icon, IconButton, Loading, Message, Panel } from './ui';

export function DateStrip({ label, detail, onSelect, previous, next, nextDisabled }: {
  label: string; detail?: string; onSelect?: () => void;
  previous?: () => void; next?: () => void; nextDisabled?: boolean;
}) {
  const { colors } = useAppearance();
  return <Panel style={[styles.dateStrip, { backgroundColor: colors.date }]}>
    {previous && <IconButton icon="chevron-left" label="Previous day" onPress={previous} />}
    {onSelect ? <Pressable accessibilityRole="button" accessibilityLabel={`Choose date, ${label}`} onPress={onSelect}
      style={({ pressed }) => [styles.dateButton, { opacity: pressed ? 0.65 : 1 }]}>
      <Copy style={[styles.dateLabel, styles.center]}>{label}</Copy><Icon name="calendar" size={17} color={colors.ink} />
    </Pressable> : <Copy style={[styles.dateLabel, styles.dateText, Boolean(previous) && styles.center]}>{label}</Copy>}
    {detail && <Copy style={styles.detail}>{detail}</Copy>}
    {next && <IconButton icon="chevron-right" label="Next day" onPress={next} disabled={nextDisabled} />}
  </Panel>;
}

function EntryRow({ entry, subtitle, first, last, readOnly, busy, onToggle, onOpen }: {
  entry: Entry; first: boolean; last: boolean; readOnly: boolean; busy: boolean;
  subtitle?: string;
  onToggle: () => void; onOpen: () => void;
}) {
  const { colors } = useAppearance();
  const completed = Boolean(entry.completedAt);
  const checkbox = <View style={[styles.checkbox, { borderColor: colors.ink, backgroundColor: completed ? colors.ink : colors.surface }]}>
    {completed && <Icon name="check" size={15} color={colors.surface} />}
  </View>;

  return <View style={[styles.row, { backgroundColor: colors.surface, borderColor: colors.ink, borderBottomColor: last ? colors.ink : colors.divider },
    first && styles.first, last && styles.last]}>
    {readOnly ? <View style={styles.checkArea}>{checkbox}</View> : <Pressable accessibilityRole="checkbox" aria-checked={completed}
      accessibilityLabel={entry.title} accessibilityState={{ checked: completed, disabled: busy }} disabled={busy}
      onPress={onToggle} style={({ pressed }) => [styles.checkArea, { opacity: pressed ? 0.62 : 1 }]}>{checkbox}</Pressable>}
    <Pressable accessibilityRole="button" accessibilityLabel={readOnly ? `${entry.title}, ${completed ? 'completed' : 'unfinished'}` : `Edit ${entry.title}`}
      accessibilityHint={subtitle ?? entry.note?.split('\n')[0]} onPress={onOpen}
      style={({ pressed }) => [styles.rowBody, { backgroundColor: pressed ? colors.date : colors.surface }]}>
      <Text style={[styles.rowTitle, { color: completed ? colors.secondary : colors.ink }]}>{entry.title}</Text>
      {(subtitle || entry.note) && <Text numberOfLines={subtitle ? undefined : 1} style={[styles.note, { color: colors.secondary }]}>{subtitle ?? entry.note}</Text>}
    </Pressable>
  </View>;
}

export function Checklist({ scope, readOnly = false, header }: {
  scope: Scope; readOnly?: boolean; header?: (done: number, total: number) => ReactNode;
}) {
  const { query, offline, save, remove, toggle } = useEntries(scope);
  const today = useToday();
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [toggling, setToggling] = useState(false);
  const [error, setError] = useState<{ scope: Scope; text: string } | null>(null);
  const working = useRef(false);
  const records = query.data ?? [];
  const completed = records.filter(entry => entry.completedAt);
  const byCompletion = [...records.filter(entry => !entry.completedAt), ...completed];
  const ordered = scope.kind === 'goal' ? ['Active', 'Upcoming', 'Past'].flatMap(phase =>
    byCompletion.filter(entry => entry.schedule && goalPhase(entry.schedule, today) === phase)) : byCompletion;
  const noun = scope.kind === 'todo' ? 'to-do' : 'goal';
  const visibleError = error && scopeKey(error.scope) === scopeKey(scope) ? error.text : '';

  async function changeCompletion(entry: Entry) {
    if (working.current) return;
    working.current = true;
    setToggling(true);
    setError(null);
    try {
      await toggle(entry);
    } catch {
      setError({ scope, text: offline ? 'Reconnect to update this item.' : 'Could not update this item. Tap its checkbox to try again.' });
    } finally {
      working.current = false;
      setToggling(false);
    }
  }

  const empty = offline || query.isError ? null : query.isPending
    ? <View style={styles.empty}><Loading label={`Loading ${noun}s`} /></View>
    : <Panel style={styles.empty}><Copy secondary>{scope.kind === 'todo'
      ? readOnly ? 'No to-dos for this day' : 'No to-dos for today' : 'No goals yet'}</Copy></Panel>;

  return <>
    <FlatList
      contentContainerStyle={styles.list}
      data={ordered}
      keyExtractor={entry => entry.id}
      ListHeaderComponent={header || offline || query.isError || visibleError ? <View style={styles.listHeader}>
        {header?.(completed.length, records.length)}
        {offline ? <Message>{query.data ? 'You’re offline. Reconnect to make changes.' : 'You’re offline. Reconnect to load this list.'}</Message> : null}
        {!offline && query.isError ? <View style={styles.feedback}>
          <Message>{query.data ? 'Could not refresh this list.' : `Could not load ${noun}s.`}</Message>
          <Action label="Retry" onPress={() => void query.refetch()} />
        </View> : null}
        {visibleError ? <Message>{visibleError}</Message> : null}
      </View> : undefined}
      ListEmptyComponent={empty ?? undefined}
      renderItem={({ item, index }) => <EntryRow entry={item} first={index === 0} last={index === ordered.length - 1}
        subtitle={item.schedule ? `${goalPhase(item.schedule, today)} · ${goalRange(item.schedule)}` : undefined}
        readOnly={readOnly} busy={toggling} onToggle={() => void changeCompletion(item)}
        onOpen={() => setEditor({ scope, entry: item, readOnly })} />}
      onRefresh={offline ? undefined : () => void query.refetch()}
      refreshing={query.isRefetching}
      keyboardShouldPersistTaps="handled"
    />
    {!readOnly && <Action label={`Add ${noun}`} icon="plus" primary style={styles.footer}
      onPress={() => setEditor({ scope })} />}
    {editor && <EntryEditor editor={editor} onClose={() => setEditor(null)} onSave={save} onDelete={remove} />}
  </>;
}

const styles = StyleSheet.create({
  dateStrip: { minHeight: 48, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' },
  dateLabel: { fontWeight: '800', fontVariant: ['tabular-nums'], fontSize: 17 },
  dateText: { flexGrow: 1, flexShrink: 1, paddingHorizontal: 12, paddingVertical: 11 },
  dateButton: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 7 },
  detail: { fontWeight: '700', paddingHorizontal: 12, paddingVertical: 8, fontVariant: ['tabular-nums'] },
  center: { flexShrink: 1, textAlign: 'center' },
  list: { paddingBottom: 12 },
  listHeader: { gap: 11, marginBottom: 11 },
  feedback: { gap: 7 },
  row: { flexDirection: 'row', alignItems: 'stretch', borderLeftWidth: 1.5, borderRightWidth: 1.5, borderBottomWidth: 1, overflow: 'hidden' },
  first: { borderTopWidth: 1.5, borderTopLeftRadius: 7, borderTopRightRadius: 7 },
  last: { borderBottomWidth: 1.5, borderBottomLeftRadius: 7, borderBottomRightRadius: 7 },
  checkArea: { width: 51, minHeight: 51, alignItems: 'center', justifyContent: 'center' },
  checkbox: { width: 22, height: 22, borderWidth: 1.6, borderRadius: 4, alignItems: 'center', justifyContent: 'center' },
  rowBody: { flex: 1, justifyContent: 'center', paddingVertical: 12, paddingRight: 12, gap: 3 },
  rowTitle: { fontWeight: '700', fontSize: 16 },
  note: { fontSize: 14 },
  empty: { minHeight: 58, paddingHorizontal: 14, paddingVertical: 12, justifyContent: 'center' },
  footer: { marginTop: 8, marginBottom: 17 },
});
