import client from './client';
import type { ActivityLogEntry, PageDTO } from '../types';

export async function listActivity(
  projectId: number,
  page = 1,
  size = 20,
): Promise<PageDTO<ActivityLogEntry>> {
  const { data } = await client.get(`/projects/${projectId}/activity`, { params: { page, size } });
  return data;
}
