import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { firstCalendarDay, formatDay, lastCalendarDay, monthDays, shiftPeriod, startOfPeriod } from '@/lib/calendar';
import { useAppearance } from '@/lib/theme';
import { Copy, Dialog, IconButton } from './ui';

const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

type CalendarProps = { selected: string; firstDay?: string; lastDay?: string; onSelect: (day: string) => void };

export function Calendar({ selected, firstDay = firstCalendarDay, lastDay = lastCalendarDay, onSelect }: CalendarProps) {
  const { colors } = useAppearance();
  const [month, setMonth] = useState(() => startOfPeriod(selected, 'month'));
  return <>
    <View style={styles.header}>
      <IconButton icon="chevron-left" label="Previous month" disabled={month <= startOfPeriod(firstDay, 'month')}
        onPress={() => setMonth(shiftPeriod(month, 'month', -1))} />
      <Copy style={styles.month}>{formatDay(month, { month: 'long', year: 'numeric' })}</Copy>
      <IconButton icon="chevron-right" label="Next month" disabled={month >= startOfPeriod(lastDay, 'month')}
        onPress={() => setMonth(shiftPeriod(month, 'month', 1))} />
    </View>
    <View style={styles.grid}>
      {weekdays.map(day => <View key={day} style={styles.cell}><Text style={{ color: colors.secondary, fontSize: 12 }}>{day}</Text></View>)}
      {monthDays(month).map((day, index) => {
        if (!day) return <View key={`blank-${index}`} style={styles.cell} />;
        const active = day === selected;
        const disabled = day < firstDay || day > lastDay;
        return <Pressable key={day} accessibilityRole="button" aria-selected={active} accessibilityState={{ selected: active, disabled }} disabled={disabled}
          accessibilityLabel={formatDay(day, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
          onPress={() => onSelect(day)} style={({ pressed }) => [styles.cell, active && { backgroundColor: colors.date, borderColor: colors.ink },
            { opacity: disabled ? 0.38 : pressed ? 0.62 : 1 }]}>
          <Copy>{Number(day.slice(-2))}</Copy>
        </Pressable>;
      })}
    </View>
  </>;
}

export function CalendarPicker({ onClose, ...props }: CalendarProps & { onClose: () => void }) {
  return <Dialog onClose={onClose}>
    <View style={styles.header}>
      <Copy style={styles.title}>Choose a day</Copy>
      <IconButton icon="x" label="Close calendar" onPress={onClose} />
    </View>
    <Calendar {...props} />
  </Dialog>;
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontWeight: '800', fontSize: 21, flex: 1 },
  month: { fontWeight: '700', flexShrink: 1, textAlign: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: '14.28%', minHeight: 48, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: 'transparent', borderRadius: 6 },
});
