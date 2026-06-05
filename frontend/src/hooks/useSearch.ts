import { useState, useCallback, useRef } from 'react';
import { searchTasks } from '../api/search';
import type { Task } from '../types';

export function useSearch(projectId: number) {
  const [results, setResults] = useState<Task[]>([]);
  const [loading, setLoading] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const search = useCallback(
    (q: string, type: 'fulltext' | 'semantic') => {
      if (!q.trim()) {
        setResults([]);
        return;
      }
      setLoading(true);
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(async () => {
        try {
          const res = await searchTasks(projectId, { q, type, limit: 20 });
          setResults(res.tasks);
        } catch {
          setResults([]);
        } finally {
          setLoading(false);
        }
      }, 300);
    },
    [projectId],
  );

  const clear = useCallback(() => {
    setResults([]);
    setLoading(false);
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  return { results, loading, search, clear };
}
