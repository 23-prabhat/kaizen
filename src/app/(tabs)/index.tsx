import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { DailyMotivationCard } from '@/components/daily-motivation-card';
import { DashboardAvatar, mascots } from '@/components/dashboard-avatar';
import { NotificationDialog } from '@/components/notification-dialog';
import { Action, Copy, Heading, Icon, IconButton, Loading, Message, Page, Panel, type IconName } from '@/components/ui';
import { activityDays, type TodoActivity } from '@/lib/activity';
import { useAppOpening } from '@/lib/app-opening';
import { useAvatar } from '@/lib/avatar';
import { dayLabel, formatDay } from '@/lib/calendar';
import { useToday } from '@/lib/clock';
import { goalPhase, goalRange } from '@/lib/goal-schedule';
import { goalsScope, type Entry } from '@/lib/planner';
import { actionInk, useAppearance } from '@/lib/theme';
import { useEntries, useTodoActivity } from '@/lib/use-entries';

function SectionTitle({ title, link, onPress }: { title: string; link: string; onPress: () => void }) {
  const { colors } = useAppearance();
  return <View style={styles.sectionTitle}>
    <Text accessibilityRole="header" style={[styles.sectionLabel, { color: colors.ink }]}>{title}</Text>
    <Pressable accessibilityRole="link" accessibilityLabel={link} onPress={onPress}
      style={({ pressed }) => [styles.link, { opacity: pressed ? 0.65 : 1 }]}>
      <Copy style={styles.linkLabel}>{link}</Copy>
      <Icon name="arrow-right" color={colors.ink} size={16} />
    </Pressable>
  </View>;
}

const greetings = [
  'Ready for today?',
  'One thing at a time',
  'Pick your first quest',
  'Let’s get moving',
  'A fresh page',
  'Time to level up',
  'Make today count',
  'Back in action',
];

function DeskCover({ today, onChangeAvatar }: { today: string; onChangeAvatar: () => void }) {
  const { colors } = useAppearance();
  const { photos, fixedId } = useAvatar();
  const opening = useAppOpening();
  const position = opening % (mascots.length + photos.length);
  const photo = fixedId ? photos.find(item => item.id === fixedId) : photos[position - mascots.length];
  return <View style={styles.raised}>
    <View pointerEvents="none" style={[styles.offset, { backgroundColor: colors.shadow }]} />
    <View style={[styles.cover, { backgroundColor: colors.cover }]}>
      <View style={styles.coverCopy}>
        <View style={[styles.dateTag, { backgroundColor: colors.action }]}>
          <Icon name="calendar" size={14} color={actionInk} />
          <Copy style={styles.dateLabel}>{dayLabel(today)}</Copy>
        </View>
        <Heading style={styles.coverHeading}>{greetings[opening % greetings.length]}</Heading>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Change dashboard avatar" onPress={onChangeAvatar}
        style={({ pressed }) => [styles.avatar, pressed && { opacity: 0.75 }]}>
        <DashboardAvatar uri={photo?.uri} mascotIndex={position % mascots.length} style={styles.avatarImage} />
        <View style={[styles.avatarEdit, { backgroundColor: colors.action }]}>
          <Icon name="edit-2" size={12} color={actionInk} />
        </View>
      </Pressable>
    </View>
  </View>;
}

function TodoSummary({ label, entries, tone, icon }: {
  label: string; entries: Entry[]; tone: 'remaining' | 'completed'; icon: IconName;
}) {
  const { colors } = useAppearance();
  return <Panel style={styles.summary}>
    <View style={[styles.summaryTop, { backgroundColor: colors[tone] }]}>
      <View style={styles.summaryLabelRow}>
        <Icon name={icon} color={actionInk} size={15} />
        <Copy style={styles.summaryLabel}>{label}</Copy>
      </View>
      <Copy style={styles.number}>{entries.length}</Copy>
    </View>
    <View style={styles.summaryBody}>
      {entries.length ? entries.slice(0, 2).map(entry => <Text key={entry.id} numberOfLines={2}
        style={[styles.preview, { color: colors.ink }]}>{entry.title}</Text>) : <Copy secondary style={styles.preview}>
        {tone === 'completed' ? 'No checkmarks yet.' : 'Nothing left on the list.'}
      </Copy>}
    </View>
  </Panel>;
}

function ActivityChart({ data }: { data: TodoActivity[] }) {
  const { colors } = useAppearance();
  const maximum = Math.max(2, ...data.map(item => item.done + item.remaining));
  const completed = data.reduce((sum, item) => sum + item.done, 0);
  const remaining = data.reduce((sum, item) => sum + item.remaining, 0);
  const logged = data.filter(item => item.done + item.remaining > 0).length;
  const plotHeight = 62;
  const scale = plotHeight / maximum;
  const dateLabel = (day: string) => formatDay(day, { day: 'numeric', month: 'short' });
  const metrics = [
    { label: 'Completed', value: completed, color: colors.completed },
    { label: 'Remaining', value: remaining, color: colors.remaining },
    { label: 'Days logged', value: logged, color: null },
  ];

  return <Panel style={styles.activity}>
    <View style={styles.chartMeta}>
      <Copy secondary style={styles.chartLabel}>To-dos by date</Copy>
      <Copy secondary style={styles.chartLabel}>Last {activityDays} days</Copy>
    </View>
    <View style={styles.metrics}>
      {metrics.map(({ label, value, color }, index) => <View key={label} style={[styles.metric,
        index !== 0 && { borderLeftWidth: 1, borderLeftColor: colors.divider }]}>
        <Copy style={styles.metricNumber}>{value}</Copy>
        <View style={styles.metricLabel}>
          {color && <View style={[styles.swatch, { backgroundColor: color }]} />}
          <Copy secondary style={styles.small}>{label}</Copy>
        </View>
      </View>)}
    </View>
    <View style={styles.chart}>
      <View style={styles.yAxis} aria-hidden>
        <Copy secondary style={styles.small}>{maximum}</Copy>
        <Copy secondary style={styles.small}>0</Copy>
      </View>
      <View style={styles.chartBody}>
        <View style={[styles.plot, { borderColor: colors.divider, height: plotHeight + 2 }]}>
          <View pointerEvents="none" style={[styles.midline, { backgroundColor: colors.divider }]} />
          {data.map(({ day, done, remaining: open }) => <View key={day} style={styles.column} accessible
            accessibilityLabel={`${dayLabel(day)}, ${done} completed, ${open} remaining`}>
            {done || open ? <View style={styles.stack}>
              {open > 0 && <View style={{ height: open * scale, backgroundColor: colors.remaining }} />}
              {done > 0 && <View style={{ height: done * scale, backgroundColor: colors.completed }} />}
            </View> : <View style={[styles.emptyMark, { backgroundColor: colors.divider }]} />}
          </View>)}
        </View>
        <View style={styles.xAxis} aria-hidden>
          {[0, 6, data.length - 1].map(index => <Copy key={index} secondary style={styles.small}>{dateLabel(data[index].day)}</Copy>)}
        </View>
      </View>
    </View>
  </Panel>;
}

function DataStatus({ source, label }: { source: ReturnType<typeof useEntries>; label: string }) {
  if (source.offline) return source.query.data ? null : <Copy secondary>{`Reconnect to load ${label}.`}</Copy>;
  if (source.query.isError) return <View style={styles.status}>
    <Message>{`Could not ${source.query.data ? 'refresh' : 'load'} ${label}.`}</Message>
    <Action label={`Retry ${label}`} onPress={() => void source.query.refetch()} />
  </View>;
  if (source.query.isPending) return <View style={styles.loading}><Loading label={`Loading ${label}`} /></View>;
  return null;
}

export default function Dashboard() {
  const today = useToday();
  const router = useRouter();
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const { colors } = useAppearance();
  const todos = useEntries({ kind: 'todo', day: today });
  const goals = useEntries(goalsScope);
  const activity = useTodoActivity(today, todos.offline);
  const remaining = todos.query.data?.filter(entry => !entry.completedAt) ?? [];
  const completed = todos.query.data?.filter(entry => entry.completedAt) ?? [];
  const active = (goals.query.data ?? []).flatMap(entry =>
    entry.schedule && goalPhase(entry.schedule, today) === 'Active' ? [{ ...entry, schedule: entry.schedule }] : []);
  active.sort((left, right) => left.schedule.finish.localeCompare(right.schedule.finish));
  const done = active.filter(entry => entry.completedAt).length;

  function openNotificationTarget(path: '/todos' | '/goals') {
    setNotificationsOpen(false);
    router.navigate(path);
  }

  const missingSources = [todos, goals].filter(source => !source.query.data);
  let notificationStatus: string | undefined;
  if (missingSources.some(source => source.offline)) {
    notificationStatus = 'Reconnect to check all notifications.';
  } else if (missingSources.some(source => source.query.isError)) {
    notificationStatus = 'Some notifications couldn’t load.';
  } else if (missingSources.length) {
    notificationStatus = 'Checking notifications…';
  }

  return <Page>
    <View style={styles.pageHeader}>
      <View style={styles.brand}>
        <View style={styles.brandMark}>
          <Image source={require('../../../assets/kaizen-mark.png')} accessible={false} aria-hidden style={styles.brandImage} />
        </View>
        <Copy style={styles.wordmark}>Kaizen</Copy>
      </View>
      <IconButton icon="bell" label="Notifications"
        style={[styles.bell, { backgroundColor: colors.surface, borderColor: colors.ink }]}
        onPress={() => setNotificationsOpen(true)} />
    </View>
    <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
      <DeskCover today={today} onChangeAvatar={() => router.navigate({ pathname: '/settings', params: { section: 'avatar' } })} />
      <DailyMotivationCard today={today} />
      {(todos.offline || goals.offline) && <Message>You’re offline.</Message>}
      <View>
        <SectionTitle title="Today’s to-dos" link="View to-dos" onPress={() => router.navigate('/todos')} />
        <DataStatus source={todos} label="to-dos" />
        {todos.query.data && <View style={styles.summaries}>
          <TodoSummary label="Remaining" entries={remaining} tone="remaining" icon="circle" />
          <TodoSummary label="Completed" entries={completed} tone="completed" icon="check-circle" />
        </View>}
      </View>
      <View>
        <SectionTitle title="Activity" link="View history" onPress={() => router.navigate('/history')} />
        {activity.data ? <ActivityChart data={activity.data} /> : todos.offline ? <Copy secondary>Reconnect to load activity.</Copy>
          : activity.isError ? <View style={styles.status}>
            <Message>Could not load activity.</Message><Action label="Retry activity" onPress={() => void activity.refetch()} />
          </View> : <View style={styles.loading}><Loading label="Loading activity" /></View>}
      </View>
      <View>
        <SectionTitle title="Active goals" link="View goals" onPress={() => router.navigate('/goals')} />
        <DataStatus source={goals} label="goals" />
        {goals.query.data && <Panel style={styles.goalPanel}>
          {active.length ? <>
            <View style={[styles.goalSummary, { backgroundColor: colors.goal }]}>
              <Copy style={[styles.linkLabel, { color: colors.onGoal }]}>{`${done} of ${active.length} complete`}</Copy>
            </View>
            {active.slice(0, 3).map(entry => <View key={entry.id} style={[styles.goal, { borderTopColor: colors.divider }]}>
              <Icon name={entry.completedAt ? 'check-circle' : 'flag'} color={colors.ink} size={18} />
              <View style={styles.goalText}>
                <Copy style={styles.linkLabel}>{entry.title}</Copy>
                <Copy secondary style={styles.preview}>{goalRange(entry.schedule)}</Copy>
              </View>
            </View>)}
          </> : <View style={styles.goalSummary}><Copy secondary>No goals active today.</Copy></View>}
        </Panel>}
      </View>
    </ScrollView>
    {notificationsOpen && <NotificationDialog today={today} pendingTodos={remaining.length} goals={goals.query.data}
      status={notificationStatus} onClose={() => setNotificationsOpen(false)}
      onOpenTodos={() => openNotificationTarget('/todos')} onOpenGoals={() => openNotificationTarget('/goals')} />}
  </Page>;
}

const styles = StyleSheet.create({
  pageHeader: { minHeight: 66, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 8 },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1 },
  brandMark: { width: 32, height: 32, borderWidth: 2, borderColor: actionInk, borderRadius: 6,
    overflow: 'hidden', transform: [{ rotate: '-7deg' }] },
  brandImage: { width: '100%', height: '100%' },
  wordmark: { fontFamily: 'BricolageGrotesque_700Bold', fontSize: 25, lineHeight: 30, letterSpacing: -0.9 },
  bell: { borderWidth: 2, borderRadius: 9 },
  content: { gap: 10, paddingBottom: 24 },
  raised: { paddingRight: 4, paddingBottom: 4 },
  offset: { position: 'absolute', top: 4, left: 4, right: 0, bottom: 0, borderRadius: 12 },
  cover: { minHeight: 138, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 6,
    padding: 12, paddingBottom: 24, borderWidth: 2, borderColor: actionInk, borderRadius: 12 },
  coverCopy: { flex: 1, alignItems: 'flex-start', gap: 8 },
  dateTag: { minHeight: 26, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 5, alignSelf: 'flex-start',
    borderWidth: 1.5, borderColor: actionInk, borderRadius: 5, paddingHorizontal: 6, paddingVertical: 1 },
  dateLabel: { fontSize: 12, fontWeight: '800', color: actionInk, lineHeight: 17 },
  coverHeading: { marginTop: 0, marginBottom: 0, fontSize: 34, lineHeight: 36, letterSpacing: -1.3, color: actionInk },
  avatar: { width: '39%', maxWidth: 122, height: 122, flexShrink: 0 },
  avatarImage: { width: '100%', height: '100%' },
  avatarEdit: { position: 'absolute', right: 0, bottom: 0, width: 27, height: 27, borderRadius: 6,
    borderWidth: 1.5, borderColor: actionInk, justifyContent: 'center', alignItems: 'center' },
  sectionTitle: { minHeight: 44, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', columnGap: 8 },
  sectionLabel: { flexShrink: 1, fontFamily: 'BricolageGrotesque_700Bold', fontSize: 22, letterSpacing: -0.5 },
  link: { minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: 5 },
  linkLabel: { fontSize: 14, fontWeight: '700' },
  summaries: { flexDirection: 'row', alignItems: 'stretch', gap: 8 },
  summary: { flex: 1, minWidth: 0, overflow: 'hidden' },
  summaryTop: { paddingHorizontal: 9, paddingTop: 7, paddingBottom: 5, gap: 1, borderBottomWidth: 2, borderBottomColor: actionInk },
  summaryLabelRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 5 },
  summaryLabel: { flexShrink: 1, fontWeight: '800', fontSize: 13, lineHeight: 18, color: actionInk },
  number: { fontFamily: 'BricolageGrotesque_700Bold', fontSize: 38, lineHeight: 42, letterSpacing: -1.4,
    fontVariant: ['tabular-nums'], color: actionInk },
  summaryBody: { padding: 9, gap: 5 },
  preview: { fontSize: 13, lineHeight: 17 },
  activity: { padding: 11, gap: 8 },
  chartMeta: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', justifyContent: 'space-between', gap: 5 },
  chartLabel: { fontSize: 12, fontWeight: '700', lineHeight: 16 },
  metrics: { flexDirection: 'row' },
  metric: { flex: 1, minWidth: 0, paddingHorizontal: 4, alignItems: 'center' },
  metricNumber: { fontFamily: 'BricolageGrotesque_700Bold', fontSize: 28, lineHeight: 32, fontVariant: ['tabular-nums'] },
  metricLabel: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', justifyContent: 'center', gap: 4 },
  swatch: { width: 9, height: 9, borderWidth: 1, borderColor: actionInk, borderRadius: 2 },
  chart: { flexDirection: 'row', gap: 6, marginTop: 5 },
  yAxis: { minWidth: 12, justifyContent: 'space-between', alignItems: 'flex-end', paddingBottom: 17 },
  chartBody: { flex: 1 },
  plot: { flexDirection: 'row', alignItems: 'flex-end', borderTopWidth: 1, borderBottomWidth: 1 },
  midline: { position: 'absolute', left: 0, right: 0, top: '50%', height: StyleSheet.hairlineWidth },
  column: { flex: 1, minWidth: 0, alignItems: 'center' },
  stack: { width: '72%', borderTopLeftRadius: 3, borderTopRightRadius: 3, overflow: 'hidden' },
  emptyMark: { width: '46%', height: 2 },
  xAxis: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 3 },
  small: { fontSize: 11, lineHeight: 14, fontVariant: ['tabular-nums'] },
  goalPanel: { overflow: 'hidden' },
  goalSummary: { paddingHorizontal: 12, paddingVertical: 9 },
  goal: { flexDirection: 'row', alignItems: 'flex-start', gap: 11, padding: 12, borderTopWidth: 1 },
  goalText: { flex: 1, gap: 3 },
  status: { gap: 8, marginBottom: 8 },
  loading: { minHeight: 48 },
});
