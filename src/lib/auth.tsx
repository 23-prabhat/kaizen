import type { Session } from '@supabase/supabase-js';
import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { AppState, Platform } from 'react-native';
import { localPlanner } from './local-planner';
import { createSupabasePlanner, type Planner } from './planner';
import { supabase } from './supabase';

type Account = { id: string; storage: 'device' | 'supabase' };
type Auth = {
  loading: boolean;
  account: Account | null;
  planner: Planner | null;
  reconnect: () => void;
};

const AuthContext = createContext<Auth | null>(null);
// Keep the existing key so saved reminder preferences continue to work.
const deviceAccount: Account = { id: 'local-demo', storage: 'device' };

let pendingSession: Promise<Session> | undefined;

function openSession(auth: NonNullable<typeof supabase>['auth']) {
  async function restoreOrStart() {
    const saved = await auth.getSession();
    if (saved.error) throw saved.error;
    if (saved.data.session) return saved.data.session;

    const guest = await auth.signInAnonymously();
    if (guest.error) throw guest.error;
    if (!guest.data.session) throw new Error('Could not start a guest session.');
    return guest.data.session;
  }

  // Two web tabs can open before the first session has reached browser storage.
  pendingSession ??= (Platform.OS === 'web' && typeof navigator !== 'undefined' && navigator.locks
    ? navigator.locks.request('daytabs:open-session', restoreOrStart) : restoreOrStart())
    .finally(() => { pendingSession = undefined; });
  return pendingSession;
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [loading, setLoading] = useState(Boolean(supabase));
  const [session, setSession] = useState<Session | null>(null);
  const [attempt, setAttempt] = useState(0);
  const onDevice = !supabase;
  const id = session?.user.id;
  const account: Account | null = onDevice ? deviceAccount : id ? { id, storage: 'supabase' } : null;
  const planner = useMemo(() => onDevice ? localPlanner : id ? createSupabasePlanner(id) : null, [onDevice, id]);

  useEffect(() => {
    if (!supabase) return;
    const auth = supabase.auth;
    const { data: { subscription } } = auth.onAuthStateChange((event, currentSession) => {
      if (currentSession || event === 'SIGNED_OUT') setSession(currentSession);
    });
    if (Platform.OS === 'web') return () => subscription.unsubscribe();

    function refreshWhenActive(state: string | null) {
      if (state === 'active') void auth.startAutoRefresh();
      else void auth.stopAutoRefresh();
    }

    refreshWhenActive(AppState.currentState ?? null);
    const lifecycle = AppState.addEventListener('change', refreshWhenActive);
    return () => {
      subscription.unsubscribe();
      lifecycle.remove();
      void auth.stopAutoRefresh();
    };
  }, []);

  useEffect(() => {
    if (!supabase) return;
    let active = true;
    openSession(supabase.auth).then(currentSession => {
      if (active) setSession(currentSession);
    }).catch(() => {
      // Show a retry when the connection is unavailable.
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [attempt]);

  function reconnect() {
    setLoading(true);
    setAttempt(value => value + 1);
  }

  return <AuthContext.Provider value={{
    loading, account, planner, reconnect,
  }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('Auth provider is required.');
  return auth;
}

