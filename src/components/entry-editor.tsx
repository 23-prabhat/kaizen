import { getRandomValues } from 'expo-crypto';
import { useRef, useState } from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { calendarDays, dayLabel, fullDayLabel } from '@/lib/calendar';
import { useToday } from '@/lib/clock';
import { finishForDuration } from '@/lib/goal-schedule';
import type { Entry, EntryDraft, Scope } from '@/lib/planner';
import { useAppearance } from '@/lib/theme';
import { Calendar } from './calendar-picker';
import { Action, Copy, Field, Heading, IconButton, Message, Page, Panel } from './ui';

export type EditorState = { scope: Scope; entry?: Entry; readOnly?: boolean };
const timingOptions = [{ value: 'range', label: 'Date range' }, { value: 'days', label: 'Number of days' }] as const;
type Timing = typeof timingOptions[number]['value'];

function newEntryId() {
  // getRandomValues also works in the HTTP browser preview over Wi-Fi.
  const bytes = getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function EntryEditor({ editor, onClose, onSave, onDelete }: {
  editor: EditorState;
  onClose: () => void;
  onSave: (scope: Scope, draft: EntryDraft, isNew: boolean) => Promise<void>;
  onDelete: (scope: Scope, id: string) => Promise<void>;
}) {
  const { colors } = useAppearance();
  const today = useToday();
  const { scope, entry, readOnly } = editor;
  const [id] = useState(() => entry?.id ?? newEntryId());
  const [title, setTitle] = useState(entry?.title ?? '');
  const [note, setNote] = useState(entry?.note ?? '');
  const [start, setStart] = useState(entry?.schedule?.start ?? today);
  const [end, setEnd] = useState(entry?.schedule?.finish ?? today);
  const [timing, setTiming] = useState<Timing>('range');
  const [duration, setDuration] = useState('1');
  const [picking, setPicking] = useState<'start' | 'finish' | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const working = useRef(false);
  const noun = scope.kind === 'todo' ? 'to-do' : 'goal';
  const finish = timing === 'days' ? finishForDuration(start, duration) : end;
  const pickerTitle = picking === 'start' ? 'Start date' : 'Finish date';
  const close = () => {
    if (picking) setPicking(null);
    else if (!working.current) onClose();
  };

  function selectTiming(value: Timing) {
    if (value === timing) return;
    if (value === 'days') setDuration(String(calendarDays(start, end)));
    else setEnd(finish ?? end);
    setTiming(value);
    setError('');
  }

  function selectDay(day: string) {
    if (picking === 'start') {
      setStart(day);
      if (day > end) setEnd(day);
    } else {
      setEnd(day);
    }
    setError('');
    setPicking(null);
  }

  async function submit(deleting = false) {
    if (working.current) return;
    const cleanTitle = title.trim();
    if (!deleting && !cleanTitle) {
      setError('Enter a title.');
      return;
    }
    if (!deleting && scope.kind === 'goal' && !finish) {
      setError('Enter a positive whole number of days that fits on the calendar.');
      return;
    }
    working.current = true;
    setBusy(true);
    setError('');
    try {
      if (deleting) await onDelete(scope, id);
      else await onSave(scope, {
        id,
        title: cleanTitle,
        note: scope.kind === 'todo' ? note.trim() || null : null,
        ...(scope.kind === 'goal' && finish && { schedule: { start, finish } }),
      }, !entry);
      onClose();
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : '';
      setError(message === 'This item was deleted.' || message.startsWith('Reconnect')
        ? message : deleting ? 'Could not delete this item. Try again.' : 'Could not save your changes. Try again.');
    } finally {
      working.current = false;
      setBusy(false);
    }
  }

  return <Modal visible animationType="none" onRequestClose={close}>
    <Page bottom>
      <KeyboardAvoidingView style={styles.keyboard} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.top}>
          <View style={styles.heading}>
            <Heading>{picking ? pickerTitle : readOnly ? 'To-do' : confirm ? `Delete ${noun}?` : `${entry ? 'Edit' : 'Add'} ${noun}`}</Heading>
          </View>
          <IconButton icon="x" label={picking ? 'Close calendar' : 'Close'} onPress={close} disabled={busy} />
        </View>
        {picking ? <ScrollView contentContainerStyle={styles.form}>
          <Panel style={styles.calendar}>
            <Calendar selected={picking === 'start' ? start : end} firstDay={picking === 'finish' ? start : undefined}
              onSelect={selectDay} />
          </Panel>
        </ScrollView> : <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.form}>
          {scope.kind === 'todo' && <Panel style={[styles.date, { backgroundColor: colors.date }]}><Copy>{dayLabel(scope.day)}</Copy></Panel>}
          {readOnly ? <>
            <Copy style={styles.title}>{title}</Copy>
            <Copy secondary>{entry?.completedAt ? 'Completed' : 'Unfinished'}</Copy>
            {note ? <Copy>{note}</Copy> : null}
          </> : confirm ? <Copy>{`“${entry?.title ?? title}” will be deleted.`}</Copy> : <>
            <Field label="Title" value={title} onChangeText={setTitle} maxLength={160} editable={!busy} />
            {scope.kind === 'todo' && <Field label="Note (optional)" value={note} onChangeText={setNote} multiline maxLength={4000} editable={!busy} />}
            {scope.kind === 'goal' && <>
              <Panel style={styles.timing}>
                {timingOptions.map(option => <Pressable key={option.value} accessibilityRole="radio"
                  aria-checked={timing === option.value} accessibilityState={{ checked: timing === option.value, disabled: busy }}
                  onPress={() => selectTiming(option.value)} disabled={busy}
                  style={({ pressed }) => [styles.timingOption, { backgroundColor: timing === option.value ? colors.date : colors.surface, opacity: pressed ? 0.65 : 1 }]}>
                  <Copy style={styles.timingLabel}>{option.label}</Copy>
                </Pressable>)}
              </Panel>
              <View style={styles.dateField}>
                <Copy style={styles.fieldLabel}>Start date</Copy>
                <Action label={fullDayLabel(start)} accessibilityLabel={`Choose start date, ${fullDayLabel(start)}`} icon="calendar" disabled={busy} onPress={() => setPicking('start')} />
              </View>
              {timing === 'range' ? <View style={styles.dateField}>
                <Copy style={styles.fieldLabel}>Finish date</Copy>
                <Action label={fullDayLabel(end)} accessibilityLabel={`Choose finish date, ${fullDayLabel(end)}`} icon="calendar" disabled={busy} onPress={() => setPicking('finish')} />
              </View> : <Field label="Number of days" value={duration} onChangeText={setDuration} inputMode="numeric" maxLength={7} editable={!busy} />}
              {finish && <Panel style={[styles.date, { backgroundColor: colors.date }]}>
                <Copy>{timing === 'days' ? `Finishes ${fullDayLabel(finish)}` : `${calendarDays(start, finish)} ${start === finish ? 'day' : 'days'}`}</Copy>
              </Panel>}
            </>}
            {entry && <Action label={`Delete ${noun}`} danger icon="trash-2" disabled={busy}
              onPress={() => { setError(''); setConfirm(true); }} />}
          </>}
          {error ? <Message>{error}</Message> : null}
        </ScrollView>}
        {!readOnly && !picking && <View style={styles.footer}>
          {confirm ? <>
            <Action label="Cancel" disabled={busy} onPress={() => { setError(''); setConfirm(false); }} />
            <Action label={busy ? 'Deleting…' : `Delete ${noun}`} danger disabled={busy} onPress={() => void submit(true)} />
          </> : <Action label={busy ? 'Saving…' : `Save ${noun}`} primary disabled={busy} onPress={() => void submit()} />}
        </View>}
      </KeyboardAvoidingView>
    </Page>
  </Modal>;
}

const styles = StyleSheet.create({
  keyboard: { flex: 1 },
  top: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  heading: { flex: 1 },
  form: { gap: 18, paddingBottom: 20 },
  date: { paddingHorizontal: 14, paddingVertical: 12 },
  timing: { flexDirection: 'row', overflow: 'hidden' },
  timingOption: { flex: 1, minHeight: 48, padding: 7, alignItems: 'center', justifyContent: 'center' },
  timingLabel: { textAlign: 'center', fontWeight: '700' },
  dateField: { gap: 6 },
  fieldLabel: { fontWeight: '700' },
  calendar: { paddingHorizontal: 8, paddingBottom: 8 },
  title: { fontSize: 23, lineHeight: 29, fontWeight: '700' },
  footer: { gap: 10, paddingTop: 9, paddingBottom: 16 },
});
