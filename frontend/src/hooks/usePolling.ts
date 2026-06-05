import { useEffect, useRef } from 'react';

export function usePolling<T>(
  fetcher: () => Promise<T>,
  options: {
    interval: number;
    enabled: boolean;
    onData: (data: T) => void;
    skipUntil?: () => boolean;
  },
) {
  const { interval, enabled, onData, skipUntil } = options;
  const savedOnData = useRef(onData);
  savedOnData.current = onData;
  const savedSkipUntil = useRef(skipUntil);
  savedSkipUntil.current = skipUntil;

  useEffect(() => {
    if (!enabled) return;

    let timer: ReturnType<typeof setTimeout>;
    let stopped = false;

    const tick = async () => {
      if (stopped) return;
      if (savedSkipUntil.current?.()) {
        timer = setTimeout(tick, interval);
        return;
      }
      try {
        const data = await fetcher();
        if (!stopped) savedOnData.current(data);
      } catch {
        // silent
      }
      if (!stopped) timer = setTimeout(tick, interval);
    };

    // Pause when tab is hidden
    const handleVisibility = () => {
      if (document.hidden) {
        clearTimeout(timer);
      } else {
        tick();
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    tick();

    return () => {
      stopped = true;
      clearTimeout(timer);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [fetcher, interval, enabled]);
}
