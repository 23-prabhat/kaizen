import AsyncStorage from '@react-native-async-storage/async-storage';
import { useEffect, useState } from 'react';
import { AppState, Platform } from 'react-native';
import { useAuth } from './auth';

export function useAppOpening() {
  const owner = useAuth().account?.id;
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (!owner) return;
    const key = `daytabs.opening:${owner}`;
    let active = true;
    let previous = -1;
    let wasBackgrounded = false;
    let updates = Promise.resolve();

    async function advance() {
      const saved = await AsyncStorage.getItem(key).catch(() => null);
      if (!active) return;
      const last = Number(saved ?? -1);
      if (Number.isSafeInteger(last) && last < Number.MAX_SAFE_INTEGER) previous = Math.max(previous, last);
      previous += 1;
      await AsyncStorage.setItem(key, String(previous)).catch(() => undefined);
      if (active) setIndex(previous);
    }

    function onOpen() {
      updates = updates.then(() => {
        if (Platform.OS === 'web' && typeof navigator !== 'undefined' && navigator.locks) {
          return navigator.locks.request(key, advance);
        }
        return advance();
      }).catch(() => undefined);
    }

    onOpen();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'background') wasBackgrounded = true;
      if (state === 'active' && wasBackgrounded) {
        wasBackgrounded = false;
        onOpen();
      }
    });
    return () => {
      active = false;
      subscription.remove();
    };
  }, [owner]);

  return index;
}
