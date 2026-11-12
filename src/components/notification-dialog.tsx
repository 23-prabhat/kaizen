import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { ReminderPreferences } from '@/components/reminder-preferences';
import { Action, Copy, Dialog, Heading, Icon, IconButton, type IconName } from '@/components/ui';
import { addDays } from '@/lib/calendar';
import type { Entry } from '@/lib/planner';
import { useReminders } from '@/lib/reminders';
import { actionInk, useAppearance } from '@/lib/theme';

type NotificationDialogProps = {
  today: string;
  pendingTodos: number;
  goals: Entry[] | undefined;
  status?: string;
  onClose: () => void;
  onOpenTodos: () => void;
  onOpenGoals: () => void;
};

function Notice({ title, body, icon, color, onPress }: {
  title: string;
  body: string;
  icon: IconName;
  color: string;
  onPress: () => void;
}) {
  const { colors } = useAppearance();
  return <Pressable accessibilityRole="button" accessibilityLabel={`${title}. ${body}`} onPress={onPress}
    style={({ pressed }) => [styles.notice, { borderColor: colors.ink, backgroundColor: pressed ? colors.date : colors.surface }]}>
    <View style={[styles.icon, { backgroundColor: color }]}><Icon name={icon} size={18} color={actionInk} /></View>
    <View style={styles.noticeText}>
      <Copy style={styles.noticeTitle}>{title}</Copy>
      <Copy secondary style={styles.body}>{body}</Copy>
    </View>
    <Icon name="chevron-right" size={18} color={colors.ink} />
  </Pressable>;
}

export function NotificationDialog({ today, pendingTodos, goals, status, onClose, onOpenTodos, onOpenGoals }: NotificationDialogProps) {
  const { colors } = useAppearance();
  const { supported } = useReminders();
  const [showReminders, setShowReminders] = useState(false);
  const tomorrow = addDays(today, 1);
  const dueToday = goals?.filter(goal => !goal.completedAt && goal.schedule?.finish === today).length ?? 0;
  const dueTomorrow = goals?.filter(goal => !goal.completedAt && goal.schedule?.finish === tomorrow).length ?? 0;
  const goalMessages = [
    dueToday ? `${dueToday} ${dueToday === 1 ? 'goal is' : 'goals are'} due today.` : '',
    dueTomorrow ? `${dueTomorrow} ${dueTomorrow === 1 ? 'goal is' : 'goals are'} due tomorrow.` : '',
  ].filter(Boolean).join(' ');

  return <Dialog label="Notifications" onClose={onClose}>
    <View style={styles.content}>
      <View style={styles.header}>
        <Heading style={styles.heading}>Notifications</Heading>
        <IconButton icon="x" label="Close notifications" onPress={onClose} />
      </View>
      {pendingTodos > 0 && <Notice title="Unfinished to-dos" icon="check-square" color={colors.remaining} onPress={onOpenTodos}
        body={`You have ${pendingTodos} ${pendingTodos === 1 ? 'to-do' : 'to-dos'} left today.`} />}
      {goalMessages ? <Notice title="Goal deadlines" icon="flag" color={colors.goal} onPress={onOpenGoals} body={goalMessages} /> : null}
      {status ? <Copy secondary style={styles.body}>{status}</Copy> : null}
      {!status && !pendingTodos && !goalMessages ? <Copy secondary style={styles.empty}>You’re all caught up.</Copy> : null}
      {supported && <Action label={showReminders ? 'Hide reminder preferences' : 'Manage reminders'}
        onPress={() => setShowReminders(open => !open)} />}
      {supported && showReminders && <ReminderPreferences />}
    </View>
  </Dialog>;
}

const styles = StyleSheet.create({
  content: { gap: 8 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 4 },
  heading: { fontSize: 29, letterSpacing: -0.7, marginTop: 0, marginBottom: 0, flexShrink: 1 },
  notice: { flexDirection: 'row', alignItems: 'center', minHeight: 64, gap: 9, borderWidth: 2, borderRadius: 8, padding: 9 },
  icon: { width: 34, height: 34, alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderColor: actionInk, borderRadius: 6 },
  noticeText: { flex: 1, gap: 2 },
  noticeTitle: { fontWeight: '700' },
  body: { fontSize: 14, lineHeight: 19 },
  empty: { paddingBottom: 8 },
});
