import { BricolageGrotesque_700Bold } from '@expo-google-fonts/bricolage-grotesque/700Bold';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { Loading } from '@/components/ui';
import { localPlannerOwner } from '@/lib/planner';
import { AvatarProvider } from '@/lib/avatar';
import { DayProvider, useToday } from '@/lib/clock';
import { ReminderProvider } from '@/lib/reminders';
import { syncMotivationWidget } from '@/lib/sync-motivation-widget';
import { AppearanceProvider, useAppearance } from '@/lib/theme';

function Navigation() {
  const { colors, ready, mode } = useAppearance();
  const today = useToday();
  const [fontReady, fontError] = useFonts({ BricolageGrotesque_700Bold });

  useEffect(() => {
    void syncMotivationWidget().catch(error => console.warn('Could not refresh the Kaizen widget.', error));
  }, [today]);

  if (!ready || (!fontReady && !fontError)) return <Loading />;

  return <AvatarProvider owner={localPlannerOwner}>
    <StatusBar style={mode === 'dark' ? 'light' : 'dark'} />
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <Stack.Screen name="(tabs)" />
    </Stack>
  </AvatarProvider>;
}

export default function RootLayout() {
  const [client] = useState(() => new QueryClient());
  return <QueryClientProvider client={client}>
    <AppearanceProvider><DayProvider><ReminderProvider><Navigation /></ReminderProvider></DayProvider></AppearanceProvider>
  </QueryClientProvider>;
}
