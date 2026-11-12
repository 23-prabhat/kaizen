import { useState } from 'react';
import { Linking, StyleSheet, Switch, View } from 'react-native';
import { Action, Copy, Message, Panel } from '@/components/ui';
import { useReminders } from '@/lib/reminders';
import { useAppearance } from '@/lib/theme';

const options = [
  { kind: 'todos', title: 'Pending to-dos', description: '7 PM when today’s list has unfinished items.' },
  { kind: 'goals', title: 'Goal deadlines', description: '9 AM the day before and on the finish date.' },
] as const;

export function ReminderPreferences() {
  const { colors } = useAppearance();
  const reminders = useReminders();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function change(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not update reminders. Try again.');
    } finally {
      setBusy(false);
    }
  }

  return <>
    <Panel style={styles.panel}>
      {options.map(({ kind, title, description }, index) => <View key={kind}
        style={[styles.row, { borderTopWidth: index ? 1 : 0, borderTopColor: colors.divider }]}>
        <View style={styles.text}>
          <Copy style={styles.label}>{title}</Copy>
          <Copy secondary style={styles.description}>{description}</Copy>
        </View>
        <Switch accessibilityLabel={title} value={reminders.preferences[kind]}
          disabled={busy || !reminders.ready} trackColor={{ false: colors.divider, true: colors.goal }}
          onValueChange={enabled => void change(() => reminders.change(kind, enabled))} />
      </View>)}
    </Panel>
    {reminders.permission === 'blocked' && <>
      <Copy secondary style={styles.description}>Allow notifications in your device settings to receive reminders.</Copy>
      <Action label="Open device settings" icon="external-link" disabled={busy}
        onPress={() => void change(Linking.openSettings)} />
    </>}
    {reminders.syncError && <Message>Could not schedule reminders. Reopen the app to try again.</Message>}
    {error ? <Message>{error}</Message> : null}
  </>;
}

const styles = StyleSheet.create({
  panel: { overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'center', minHeight: 60, padding: 10, gap: 10 },
  text: { flex: 1, gap: 3 },
  label: { fontWeight: '700' },
  description: { fontSize: 14, lineHeight: 19 },
});
