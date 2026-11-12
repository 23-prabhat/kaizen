import { useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import { Copy, Dialog, Heading, Icon, IconButton } from '@/components/ui';
import { dailyMotivation } from '@/lib/daily-motivation';
import { actionInk, useAppearance } from '@/lib/theme';

export function DailyMotivationCard({ today }: { today: string }) {
  const { colors } = useAppearance();
  const { line, credit } = dailyMotivation(today);
  const [showWidget, setShowWidget] = useState(false);
  const closeWidget = () => setShowWidget(false);

  return <View style={styles.floating}>
    <View pointerEvents="none" style={[styles.shadow, { backgroundColor: colors.shadow }]} />
    <View style={[styles.card, { backgroundColor: colors.action }]}>
      <View style={styles.toolbar}>
        <View style={styles.label}>
          <Icon name="zap" size={16} color={actionInk} />
          <Copy style={styles.labelText}>Daily motivation</Copy>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Add widget" onPress={() => setShowWidget(true)}
          style={({ pressed }) => [styles.widgetButton, pressed && { opacity: 0.7 }]}>
          <Icon name="plus-square" size={15} color={actionInk} />
          <Copy style={styles.widgetLabel}>Add widget</Copy>
        </Pressable>
      </View>
      <Copy style={styles.line}>{credit ? `“${line}”` : line}</Copy>
      {credit && <Copy style={styles.credit}>{credit}</Copy>}
    </View>
    {showWidget && <Dialog label="Add home screen widget" onClose={closeWidget}>
      <View style={styles.dialog}>
        <View style={styles.dialogHeader}>
          <Heading style={styles.dialogTitle}>Add home screen widget</Heading>
          <IconButton icon="x" label="Close widget instructions" onPress={closeWidget} />
        </View>
        {Platform.OS === 'web' && <Copy secondary>Use the Kaizen Android or iOS app to add the widget.</Copy>}
        {Platform.OS !== 'ios' && <View style={styles.instructions}>
          <Copy style={styles.platform}>Android</Copy>
          <Copy>Touch and hold an empty spot on the home screen. Open Widgets, find Kaizen, and drag Daily motivation onto your screen.</Copy>
        </View>}
        {Platform.OS !== 'android' && <View style={styles.instructions}>
          <Copy style={styles.platform}>iPhone</Copy>
          <Copy>Touch and hold the home screen, then tap Edit → Add Widget. Search for Kaizen, choose Daily motivation, and tap Add Widget.</Copy>
        </View>}
      </View>
    </Dialog>}
  </View>;
}

const styles = StyleSheet.create({
  floating: { zIndex: 1, marginTop: -26, marginHorizontal: 10, paddingRight: 5, paddingBottom: 6 },
  shadow: { position: 'absolute', top: 6, left: 5, right: 0, bottom: 0, borderRadius: 10 },
  card: { paddingHorizontal: 12, paddingTop: 5, paddingBottom: 13, borderRadius: 10, borderWidth: 2, borderColor: actionInk },
  toolbar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', columnGap: 4 },
  label: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  labelText: { color: actionInk, fontWeight: '700', fontSize: 13, lineHeight: 18 },
  widgetButton: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 44, paddingHorizontal: 4 },
  widgetLabel: { color: actionInk, fontWeight: '700', fontSize: 12, lineHeight: 16 },
  line: { color: actionInk, fontFamily: 'BricolageGrotesque_700Bold', fontSize: 25, lineHeight: 29, letterSpacing: -0.6 },
  credit: { color: actionInk, marginTop: 5, fontSize: 13, lineHeight: 18 },
  dialog: { gap: 11 },
  dialogHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 4 },
  dialogTitle: { flexShrink: 1, fontSize: 26, marginVertical: 0, letterSpacing: -0.5 },
  instructions: { gap: 4, paddingBottom: 4 },
  platform: { fontWeight: '700' },
});
