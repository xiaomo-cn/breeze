import client from './client';
import type { Task } from '../types';

export interface SearchResult {
  query: string;
  type: string;
  tasks: Task[];
}

export async function searchTasks(
  projectId: number,
  params: { q: string; type?: 'fulltext' | 'semantic'; limit?: number },
): Promise<SearchResult> {
  const { data } = await client.get(`/projects/${projectId}/search`, { params });
  return data;
}
