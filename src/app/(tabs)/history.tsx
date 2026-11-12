import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { CalendarPicker } from '@/components/calendar-picker';
import { Checklist, DateStrip } from '@/components/checklist';
import { Copy, Heading, Page } from '@/components/ui';
import { addDays, dayLabel, firstCalendarDay, formatDay } from '@/lib/calendar';
import { useToday } from '@/lib/clock';
import { actionInk, useAppearance } from '@/lib/theme';
import { usePlannerQuery, useTodoActivity } from '@/lib/use-entries';

export default function History() {
  const today = useToday();
  const yesterday = addDays(today, -1);
  const { colors } = useAppearance();
  const { offline } = usePlannerQuery({ kind: 'todo', day: yesterday }, false);
  const activity = useTodoActivity(today, offline);
  const recent = (activity.data ?? []).filter(item => item.day < today && item.done + item.remaining > 0).reverse();
  const [selected, setSelected] = useState<string | null>(null);
  const [showCalendar, setShowCalendar] = useState(false);
  const day = selected && selected < yesterday ? selected : yesterday;

  function select(next: string) {
    setSelected(next < yesterday ? next : null);
    setShowCalendar(false);
  }

  return <Page>
    <Heading>History</Heading>
    <Checklist scope={{ kind: 'todo', day }} readOnly header={() => <>
      <DateStrip label={dayLabel(day)} onSelect={() => setShowCalendar(true)}
        previous={day > firstCalendarDay ? () => select(addDays(day, -1)) : undefined}
        next={() => select(addDays(day, 1))} nextDisabled={day === yesterday} />
      {recent.length > 0 && <View style={styles.recent}>
        <Copy secondary style={styles.label}>Recent days with to-dos</Copy>
        <View style={styles.dates}>
          {recent.map(item => <Pressable key={item.day} onPress={() => select(item.day)}
            accessibilityRole="button" accessibilityLabel={`Show ${dayLabel(item.day)}`}
            accessibilityState={{ selected: item.day === day }}
            style={({ pressed }) => [styles.date, { backgroundColor: item.day === day ? colors.action : colors.surface,
              borderColor: item.day === day ? actionInk : colors.ink, opacity: pressed ? 0.72 : 1 }]}>
            <Copy style={[styles.label, item.day === day && { color: actionInk }]}>
              {formatDay(item.day, { day: 'numeric', month: 'short' })}
            </Copy>
          </Pressable>)}
        </View>
      </View>}
    </>} />
    {showCalendar && <CalendarPicker selected={day} lastDay={yesterday} onClose={() => setShowCalendar(false)} onSelect={select} />}
  </Page>;
}

const styles = StyleSheet.create({
  recent: { gap: 5 },
  dates: { flexDirection: 'row', flexWrap: 'wrap', gap: 5 },
  date: { borderWidth: 1.5, borderRadius: 6, minHeight: 44, minWidth: 48, justifyContent: 'center',
    alignItems: 'center', paddingHorizontal: 8, paddingVertical: 4 },
  label: { fontWeight: '700', fontSize: 13, lineHeight: 18 },
});
