import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import { useColorScheme } from 'react-native';

const palettes = {
  light: {
    background: '#F5F1FF', surface: '#FFFEFC', ink: '#231A2A', secondary: '#584C62',
    date: '#E7DCFF', action: '#FFD947', divider: '#D6C9E0', danger: '#A12B31', shadow: '#231A2A',
    remaining: '#FF987A', completed: '#BCA0FA', goal: '#D9F398', onGoal: '#231A2A',
    tab: '#FFD947', cover: '#D6BCFF',
  },
  dark: {
    background: '#221D22', surface: '#302830', ink: '#FFF6EE', secondary: '#DCCCDD',
    date: '#473449', action: '#FFD947', divider: '#68576A', danger: '#FFAA9D', shadow: '#0D0A0D',
    remaining: '#FF987A', completed: '#C8ABFF', goal: '#D9F398', onGoal: '#231A2A',
    tab: '#FFD947', cover: '#C7A7F3',
  },
};

export const actionInk = '#231A2A';
export type Appearance = 'light' | 'dark' | 'system';
const storageKey = 'daytabs.appearance';

type Theme = {
  colors: typeof palettes.light;
  appearance: Appearance;
  mode: 'light' | 'dark';
  ready: boolean;
  setAppearance: (appearance: Appearance) => Promise<void>;
};

const ThemeContext = createContext<Theme | null>(null);

export function AppearanceProvider({ children }: PropsWithChildren) {
  const [appearance, setPreference] = useState<Appearance>('light');
  const [ready, setReady] = useState(false);
  const system = useColorScheme();
  const mode = appearance === 'system' ? system ?? 'light' : appearance;

  useEffect(() => {
    let active = true;
    AsyncStorage.getItem(storageKey).then(value => {
      if (active && (value === 'light' || value === 'dark' || value === 'system')) setPreference(value);
    }).catch(() => undefined).finally(() => {
      if (active) setReady(true);
    });
    return () => { active = false; };
  }, []);

  async function setAppearance(value: Appearance) {
    await AsyncStorage.setItem(storageKey, value);
    setPreference(value);
  }

  return <ThemeContext.Provider value={{ colors: palettes[mode], appearance, mode, ready, setAppearance }}>
    {children}
  </ThemeContext.Provider>;
}

export function useAppearance() {
  const theme = useContext(ThemeContext);
  if (!theme) throw new Error('Appearance provider is required.');
  return theme;
}
