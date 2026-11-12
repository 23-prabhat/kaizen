import { useQueryClient } from '@tanstack/react-query';
import { getDocumentAsync } from 'expo-document-picker';
import { File } from 'expo-file-system';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useRef, useState, type ComponentRef } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { AvatarSettings } from '@/components/avatar-settings';
import { Action, Copy, Heading, Icon, Message, Page, Panel, type IconName } from '@/components/ui';
import { localPlannerOwner } from '@/lib/planner';
import { importWorkReports, maxReportBytes } from '@/lib/local-planner';
import { useAppearance, type Appearance } from '@/lib/theme';

const options: { value: Appearance; label: string; icon: IconName }[] = [
  { value: 'light', label: 'Light', icon: 'sun' },
  { value: 'dark', label: 'Dark', icon: 'moon' },
  { value: 'system', label: 'System', icon: 'smartphone' },
];

export default function Settings() {
  const { colors, appearance, setAppearance } = useAppearance();
  const queryClient = useQueryClient();
  const { section } = useLocalSearchParams<{ section?: string }>();
  const scroll = useRef<ComponentRef<typeof ScrollView>>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [importStatus, setImportStatus] = useState('');

  const showSection = useCallback(() => {
    if (section === 'avatar') {
      scroll.current?.scrollTo({ y: 0, animated: false });
    }
  }, [section]);
  useFocusEffect(showSection);

  async function change(action: () => Promise<void>, fallback: string, showError = false) {
    setBusy(true);
    setError('');
    try { await action(); }
    catch (failure) { setError(showError && failure instanceof Error ? failure.message : fallback); }
    finally { setBusy(false); }
  }

  async function importReport() {
    setImportStatus('');
    await change(async () => {
      const selection = await getDocumentAsync({
        type: Platform.OS === 'web' ? ['.json', 'application/json'] : '*/*',
        copyToCacheDirectory: true,
        base64: false,
      });
      const asset = selection.assets?.[0];
      if (!asset) return;
      const nativeFile = Platform.OS !== 'web' ? new File(asset.uri) : null;
      let added: number;
      try {
        if ((asset.size ?? nativeFile?.size ?? 0) > maxReportBytes) throw new Error('Choose a report file under 1 MB.');
        const text = await (asset.file ?? nativeFile)?.text();
        if (text === undefined) throw new Error('Could not open the selected report.');
        added = await importWorkReports(text);
      } finally {
        if (Platform.OS === 'web') URL.revokeObjectURL(asset.uri);
        else if (nativeFile) {
          try { nativeFile.delete(); } catch { /* The OS can clear the cached copy later. */ }
        }
      }
      setImportStatus(added ? `Imported ${added} to-do${added === 1 ? '' : 's'}.` : 'These to-dos are already in your planner.');
      void queryClient.invalidateQueries({ queryKey: ['planner', localPlannerOwner] });
    }, 'Could not import this report. Try again.', true);
  }

  return <Page>
    <Heading>Settings</Heading>
    <ScrollView ref={scroll} contentContainerStyle={styles.content}>
      <Copy style={styles.title}>Dashboard avatar</Copy>
      <AvatarSettings />
      <Copy style={[styles.title, styles.sectionTitle]}>Appearance</Copy>
      <Panel style={styles.panel}>
        {options.map(({ value, label, icon }, index) => <Pressable key={value} accessibilityRole="radio" aria-checked={appearance === value}
          accessibilityState={{ checked: appearance === value, disabled: busy }} disabled={busy}
          onPress={() => void change(() => setAppearance(value), 'Could not save your appearance setting. Try again.')}
          style={({ pressed }) => [styles.choice, { borderColor: colors.divider,
            borderTopWidth: index ? 1 : 0, backgroundColor: pressed ? colors.date : colors.surface }]}>
          <Icon name={icon} color={colors.ink} size={20} />
          <Copy style={styles.choiceLabel}>{label}</Copy>
          {appearance === value && <Icon name="check" color={colors.ink} size={20} />}
        </Pressable>)}
      </Panel>
      <Copy style={[styles.title, styles.sectionTitle]}>Planner data</Copy>
      <Panel style={styles.storage}>
        <Copy style={styles.choiceLabel}>Saved on this device</Copy>
        <Copy secondary style={styles.description}>Import a Kaizen report to add dated to-dos and notes to this planner.</Copy>
        <Action label="Import report" icon="download" disabled={busy} onPress={() => void importReport()} />
        {importStatus ? <View accessibilityLiveRegion="polite"><Copy>{importStatus}</Copy></View> : null}
      </Panel>
      {error ? <Message>{error}</Message> : null}
    </ScrollView>
  </Page>;
}

const styles = StyleSheet.create({
  content: { gap: 10, paddingBottom: 20 },
  title: { fontWeight: '800', fontSize: 18 },
  panel: { overflow: 'hidden' },
  choice: { flexDirection: 'row', alignItems: 'center', minHeight: 52, paddingHorizontal: 12, paddingVertical: 8, gap: 12 },
  choiceLabel: { flex: 1, fontWeight: '600' },
  sectionTitle: { marginTop: 10 },
  description: { fontSize: 14, lineHeight: 19 },
  storage: { gap: 8, padding: 12 },
});
