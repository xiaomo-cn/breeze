import client from './client';
import type { GanttData } from '../types';

export async function getGanttData(projectId: number): Promise<GanttData> {
  const { data } = await client.get(`/projects/${projectId}/gantt`);
  return data;
}
