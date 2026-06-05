import { useState, useEffect, useCallback } from 'react';
import { getTask } from '../api/tasks';
import type { Task } from '../types';

export function useTask(taskId: number | null) {
  const [task, setTask] = useState<Task | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch = useCallback(async () => {
    if (taskId == null) {
      setTask(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getTask(taskId);
      setTask(result);
    } catch (err) {
      setError('Failed to load task');
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { task, loading, error, refetch: fetch };
}
