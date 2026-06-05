import { useEffect, useRef, useCallback } from 'react';

type EventHandler = (event: string, data: unknown) => void;

export function useRealtimeEvents() {
  const handlersRef = useRef<Map<string, Set<EventHandler>>>(new Map());
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const stoppedRef = useRef(false);
  const generationRef = useRef(0);

  const subscribe = useCallback((eventType: string, handler: EventHandler) => {
    if (!handlersRef.current.has(eventType)) {
      handlersRef.current.set(eventType, new Set());
    }
    handlersRef.current.get(eventType)!.add(handler);
    return () => {
      handlersRef.current.get(eventType)?.delete(handler);
    };
  }, []);

  useEffect(() => {
    stoppedRef.current = false;
    let aborter: AbortController | null = null;

    const connect = (generation: number) => {
      // Stale generation — ignore
      if (generation !== generationRef.current) return;

      // Clean up previous aborter
      if (aborter) {
        aborter.abort();
      }

      const controller = new AbortController();
      aborter = controller;

      fetch('/api/v1/events/stream', {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
        },
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            throw new Error('SSE connection failed');
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          let currentEvent = '';
          let currentData = '';

          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
              if (line.startsWith('event:')) {
                currentEvent = line.slice(6).trim();
              } else if (line.startsWith('data:')) {
                currentData = line.slice(5).trim();
              } else if (line === '' && currentEvent && currentData) {
                if (currentEvent !== 'heartbeat' && currentEvent !== 'connected') {
                  try {
                    const parsed = JSON.parse(currentData);
                    const handlers = handlersRef.current.get(currentEvent);
                    if (handlers) {
                      handlers.forEach((h) => h(currentEvent, parsed));
                    }
                  } catch {
                    // skip malformed events
                  }
                }
                currentEvent = '';
                currentData = '';
              }
            }
          }

          // Stream ended cleanly — reconnect
          scheduleRetry(generation);
        })
        .catch(() => {
          scheduleRetry(generation);
        });
    };

    const scheduleRetry = (generation: number) => {
      if (stoppedRef.current || generation !== generationRef.current) return;
      const delay = Math.min(5000, 30000); // 5s base, cap at 30s
      retryTimerRef.current = setTimeout(() => connect(generation), delay);
    };

    generationRef.current++;
    connect(generationRef.current);

    return () => {
      stoppedRef.current = true;
      generationRef.current++; // invalidate all pending retries
      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
      if (aborter) {
        aborter.abort();
        aborter = null;
      }
    };
  }, []);

  return { subscribe };
}
