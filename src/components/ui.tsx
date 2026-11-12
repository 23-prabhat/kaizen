import Feather from '@expo/vector-icons/Feather';
import { useIsFocused } from 'expo-router';
import type { ComponentProps, PropsWithChildren } from 'react';
import { ActivityIndicator, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type StyleProp, type TextStyle, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { actionInk, useAppearance } from '@/lib/theme';

export type IconName = ComponentProps<typeof Feather>['name'];

export function Icon({ name, color, size = 22 }: { name: IconName; color?: ComponentProps<typeof Feather>['color']; size?: number }) {
  return <Feather name={name} color={color} size={size} accessible={false} aria-hidden />;
}

export function Page({ children, bottom = false }: PropsWithChildren<{ bottom?: boolean }>) {
  const { colors } = useAppearance();
  const focused = useIsFocused();
  return <SafeAreaView edges={bottom ? ['top', 'left', 'right', 'bottom'] : ['top', 'left', 'right']}
    aria-hidden={!focused} accessibilityElementsHidden={!focused} importantForAccessibility={focused ? 'auto' : 'no-hide-descendants'}
    style={[styles.page, { backgroundColor: colors.background }]}>
    <View style={styles.content}>{children}</View>
  </SafeAreaView>;
}

export function Heading({ children, style }: PropsWithChildren<{ style?: StyleProp<TextStyle> }>) {
  const { colors } = useAppearance();
  return <Text accessibilityRole="header" style={[styles.heading, { color: colors.ink }, style]}>{children}</Text>;
}

export function Copy({ children, secondary = false, style }: PropsWithChildren<{ secondary?: boolean; style?: StyleProp<TextStyle> }>) {
  const { colors } = useAppearance();
  return <Text style={[styles.copy, { color: secondary ? colors.secondary : colors.ink }, style]}>{children}</Text>;
}

export function Action({ label, accessibilityLabel = label, onPress, icon, primary = false, disabled = false, danger = false, style }: {
  label: string; onPress: () => void; icon?: IconName; primary?: boolean; disabled?: boolean; danger?: boolean;
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useAppearance();
  const ink = primary ? actionInk : danger ? colors.danger : colors.ink;
  return <View style={[primary && styles.shadowWrap, style]}>
    {primary && <View pointerEvents="none" style={[styles.offset, { backgroundColor: colors.shadow }]} />}
    <Pressable accessibilityRole="button" accessibilityLabel={accessibilityLabel} accessibilityState={{ disabled }} disabled={disabled} onPress={onPress}
      style={({ pressed }) => [styles.action, {
        borderColor: primary ? actionInk : ink, backgroundColor: primary ? colors.action : colors.surface,
        opacity: disabled ? 0.54 : pressed ? 0.76 : 1,
      }]}>
      {icon && <Icon name={icon} color={ink} size={21} />}
      <Text style={[styles.actionText, { color: ink }]}>{label}</Text>
    </Pressable>
  </View>;
}

export function IconButton({ icon, label, onPress, disabled, style }: {
  icon: IconName; label: string; onPress: () => void; disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useAppearance();
  return <Pressable accessibilityRole="button" accessibilityLabel={label} disabled={disabled}
    accessibilityState={{ disabled }} onPress={onPress}
    style={({ pressed }) => [styles.iconButton, style, { opacity: disabled ? 0.35 : pressed ? 0.58 : 1 }]}>
    <Icon name={icon} color={colors.ink} size={20} />
  </Pressable>;
}

export function Panel({ children, style }: PropsWithChildren<{ style?: StyleProp<ViewStyle> }>) {
  const { colors } = useAppearance();
  return <View style={[styles.panel, { borderColor: colors.ink, backgroundColor: colors.surface }, style]}>{children}</View>;
}

export function Field({ label, multiline, ...props }: ComponentProps<typeof TextInput> & { label: string }) {
  const { colors } = useAppearance();
  return <View style={styles.field}>
    <Copy style={styles.label}>{label}</Copy>
    <TextInput {...props} accessibilityLabel={label} multiline={multiline} placeholderTextColor={colors.secondary}
      style={[styles.input, multiline && styles.multiline, { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.ink }, props.style]} />
  </View>;
}

export function Message({ children }: PropsWithChildren) {
  const { colors } = useAppearance();
  return <Text accessibilityRole="alert" accessibilityLiveRegion="polite" style={[styles.copy, { color: colors.danger }]}>{children}</Text>;
}

export function Loading({ label = 'Loading' }: { label?: string }) {
  const { colors } = useAppearance();
  return <View style={[styles.loading, { backgroundColor: colors.background }]}>
    <ActivityIndicator accessibilityLabel={label} color={colors.ink} />
  </View>;
}

export function Dialog({ children, label, onClose }: PropsWithChildren<{ label?: string; onClose: () => void }>) {
  return <Modal visible transparent animationType="none" onRequestClose={onClose}
    {...(Platform.OS === 'web' && label ? { 'aria-label': label } : {})}>
    <SafeAreaView style={styles.scrim}>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.dialogScroll}>
        <Panel style={styles.dialog}>
          <View accessibilityViewIsModal>{children}</View>
        </Panel>
      </ScrollView>
    </SafeAreaView>
  </Modal>;
}

const styles = StyleSheet.create({
  page: { flex: 1 },
  content: { flex: 1, width: '100%', maxWidth: 560, alignSelf: 'center', paddingHorizontal: 16 },
  heading: { fontFamily: 'BricolageGrotesque_700Bold', fontSize: 45, letterSpacing: -1.6, marginTop: 17, marginBottom: 15 },
  copy: { fontSize: 16, lineHeight: 22 },
  action: { minHeight: 48, paddingHorizontal: 12, paddingVertical: 9, borderWidth: 2, borderRadius: 7, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9 },
  actionText: { fontSize: 17, fontWeight: '700', textAlign: 'center', flexShrink: 1 },
  shadowWrap: { paddingRight: 3, paddingBottom: 3 },
  offset: { position: 'absolute', top: 3, right: 0, bottom: 0, left: 3, borderRadius: 7 },
  iconButton: { width: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  panel: { borderWidth: 2, borderRadius: 9 },
  field: { gap: 6 },
  label: { fontWeight: '700' },
  input: { minHeight: 48, borderWidth: 2, borderRadius: 7, paddingHorizontal: 12, paddingVertical: Platform.OS === 'ios' ? 12 : 9, fontSize: 16 },
  multiline: { minHeight: 132, textAlignVertical: 'top' },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  scrim: { flex: 1, backgroundColor: '#24182199' },
  dialogScroll: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 12, paddingVertical: 16 },
  dialog: { width: '100%', maxWidth: 420, padding: 12 },
});
