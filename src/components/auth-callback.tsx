import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { supabase } from '@/lib/supabase';
import { Action, Copy, Field, Heading, Message, Page } from './ui';

async function verify(code: string, flowId?: string) {
  if (!supabase) throw new Error('This build needs a Supabase connection.');
  const { error } = await supabase.auth.exchangeCodeForSession(code, flowId ? { flowId } : undefined);
  if (error) throw error;
}

export function AuthCallback({ recovery = false }: { recovery?: boolean }) {
  const { code, sb_flow_id: flowId } = useLocalSearchParams<{ code?: string; sb_flow_id?: string }>();
  const router = useRouter();
  const request = useRef<{ code: string; flowId?: string; result: Promise<void> } | null>(null);
  const [status, setStatus] = useState<'checking' | 'valid' | 'invalid'>('checking');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (typeof code !== 'string' || !code) {
      setStatus('invalid');
      return;
    }
    setStatus('checking');
    const flow = typeof flowId === 'string' ? flowId : undefined;
    if (request.current?.code !== code || request.current.flowId !== flow) {
      request.current = { code, flowId: flow, result: verify(code, flow) };
    }
    let active = true;
    request.current.result.then(() => {
      if (active) setStatus('valid');
    }).catch(() => {
      if (active) setStatus('invalid');
    });
    return () => { active = false; };
  }, [code, flowId]);

  async function updatePassword() {
    if (!supabase || saving) return;
    if (!password || password !== confirmation) {
      setError('Enter the same new password in both fields.');
      return;
    }
    setError('');
    setSaving(true);
    try {
      const { error: updateError } = await supabase.auth.updateUser({ password });
      if (updateError) throw updateError;
      router.replace('/');
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not update your password. Try again.');
    } finally {
      setSaving(false);
    }
  }

  return <Page bottom>
    <Heading>{recovery ? 'New password' : 'Confirm email'}</Heading>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.form}>
      {status === 'checking' ? <Copy>Checking your link…</Copy> : status === 'invalid' ? <>
        <Message>This link couldn’t be verified. Request a new link and open it on the same device.</Message>
        <Action label="Continue" onPress={() => router.replace('/')} />
      </> : recovery ? <>
        <Field label="New password" secureTextEntry autoComplete="new-password" value={password} onChangeText={setPassword} editable={!saving} />
        <Field label="Confirm new password" secureTextEntry autoComplete="new-password" value={confirmation} onChangeText={setConfirmation} editable={!saving} />
        {error ? <Message>{error}</Message> : null}
        <Action label={saving ? 'Saving…' : 'Save password'} primary disabled={saving} onPress={() => void updatePassword()} />
      </> : <>
        <Copy>Your email is confirmed.</Copy>
        <Action label="Open planner" primary onPress={() => router.replace('/')} />
      </>}
    </ScrollView>
  </Page>;
}

const styles = StyleSheet.create({ form: { gap: 14, paddingBottom: 24 } });
