import { BricolageGrotesque_700Bold } from '@expo-google-fonts/bricolage-grotesque/700Bold';
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Action, Copy, Heading, Loading } from '@/components/ui';
import { AuthProvider, useAuth } from '@/lib/auth';
import { AvatarProvider } from '@/lib/avatar';
import { DayProvider, useToday } from '@/lib/clock';
import { ReminderProvider } from '@/lib/reminders';
import { syncMotivationWidget } from '@/lib/sync-motivation-widget';
import { AppearanceProvider, useAppearance } from '@/lib/theme';

function Navigation() {
  const { colors, ready, mode } = useAppearance();
  const { account, loading, reconnect } = useAuth();
  const client = useQueryClient();
  const previousAccount = useRef<string | undefined>(undefined);
  const id = account?.id;
  const today = useToday();
  const [fontReady, fontError] = useFonts({ BricolageGrotesque_700Bold });

  useEffect(() => {
    if (previousAccount.current && previousAccount.current !== id) {
      client.removeQueries({ queryKey: ['planner', previousAccount.current] });
    }
    previousAccount.current = id;
  }, [client, id]);

  useEffect(() => {
    void syncMotivationWidget().catch(error => console.warn('Could not refresh the Kaizen widget.', error));
  }, [today]);

  if (!ready || loading || (!fontReady && !fontError)) return <Loading />;
  if (!account) return <View style={[styles.retry, { backgroundColor: colors.background }]}>
    <Heading>Kaizen</Heading>
    <Copy secondary>Could not connect to your planner. Check your connection and try again.</Copy>
    <Action label="Try again" onPress={reconnect} primary />
  </View>;

  return <AvatarProvider key={account.id} owner={account.id}>
    <StatusBar style={mode === 'dark' ? 'light' : 'dark'} />
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <Stack.Screen name="(tabs)" />
      <Stack.Screen name="sign-in" />
      <Stack.Screen name="auth/confirm" />
      <Stack.Screen name="auth/recovery" />
    </Stack>
  </AvatarProvider>;
}

const styles = StyleSheet.create({
  retry: { flex: 1, justifyContent: 'center', padding: 24, gap: 16 },
});

export default function RootLayout() {
  const [client] = useState(() => new QueryClient());
  return <QueryClientProvider client={client}>
    <AppearanceProvider><AuthProvider><DayProvider><ReminderProvider><Navigation /></ReminderProvider></DayProvider></AuthProvider></AppearanceProvider>
  </QueryClientProvider>;
}
