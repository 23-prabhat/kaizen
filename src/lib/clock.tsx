import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';
import { localDay } from './calendar';

const DayContext = createContext('');

export function DayProvider({ children }: PropsWithChildren) {
  const [day, setDay] = useState(() => localDay());

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    function refresh() {
      const now = new Date();
      setDay(localDay(now));
      const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
      clearTimeout(timer);
      // Check periodically for device clock or time-zone changes while open.
      timer = setTimeout(refresh, Math.min(midnight.getTime() - now.getTime() + 50, 60_000));
    }

    refresh();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') refresh();
    });
    return () => { clearTimeout(timer); subscription.remove(); };
  }, []);

  return <DayContext.Provider value={day}>{children}</DayContext.Provider>;
}

export function useToday() {
  return useContext(DayContext);
}
