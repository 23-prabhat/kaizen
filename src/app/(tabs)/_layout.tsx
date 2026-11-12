import { Tabs } from 'expo-router';
import { PlatformPressable } from 'expo-router/react-navigation';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, type IconName } from '@/components/ui';
import { actionInk, useAppearance } from '@/lib/theme';

function TabIcon({ name, focused }: { name: IconName; focused: boolean }) {
  const { colors } = useAppearance();
  return <View style={[styles.indicator, focused && { backgroundColor: colors.tab, borderColor: actionInk }]}>
    <Icon name={name} size={20} color={focused ? actionInk : colors.secondary} />
  </View>;
}

export default function PlannerTabs() {
  const { colors } = useAppearance();
  const { bottom } = useSafeAreaInsets();
  return <Tabs screenOptions={{
    headerShown: false,
    sceneStyle: { backgroundColor: colors.background },
    tabBarActiveTintColor: colors.ink,
    tabBarInactiveTintColor: colors.secondary,
    tabBarLabelPosition: 'below-icon',
    tabBarLabelStyle: { fontWeight: '600', fontSize: 11, marginBottom: 2, marginTop: 3 },
    tabBarButton: ({ style, ...props }) => <PlatformPressable {...props} style={[style, { paddingHorizontal: 0 }]} />,
    tabBarItemStyle: { marginHorizontal: 1, minHeight: 48 },
    tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.ink, borderTopWidth: 2,
      paddingHorizontal: 6, paddingTop: 4, height: 65 + bottom },
  }}>
    <Tabs.Screen name="index" options={{ title: 'Dashboard', tabBarAccessibilityLabel: 'Dashboard', tabBarIcon: ({ focused }) => <TabIcon name="grid" focused={focused} /> }} />
    <Tabs.Screen name="todos" options={{ title: 'To-dos', tabBarAccessibilityLabel: 'To-dos', tabBarIcon: ({ focused }) => <TabIcon name="check-square" focused={focused} /> }} />
    <Tabs.Screen name="goals" options={{ title: 'Goals', tabBarAccessibilityLabel: 'Goals', tabBarIcon: ({ focused }) => <TabIcon name="flag" focused={focused} /> }} />
    <Tabs.Screen name="history" options={{ title: 'History', tabBarAccessibilityLabel: 'History', tabBarIcon: ({ focused }) => <TabIcon name="clock" focused={focused} /> }} />
    <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarAccessibilityLabel: 'Settings', tabBarIcon: ({ focused }) => <TabIcon name="settings" focused={focused} /> }} />
    <Tabs.Screen name="targets" options={{ href: null }} />
  </Tabs>;
}

const styles = StyleSheet.create({
  indicator: { width: 41, height: 28, borderRadius: 6, borderWidth: 2, borderColor: 'transparent', alignItems: 'center', justifyContent: 'center' },
});
