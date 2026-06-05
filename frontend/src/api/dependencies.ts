import client from './client';
import type { Dependency } from '../types';

export async function listDependencies(taskId: number): Promise<Dependency[]> {
  const { data } = await client.get(`/tasks/${taskId}/dependencies`);
  return data;
}

export async function createDependency(
  taskId: number, body: { dependsOnTaskId: number; type: string },
): Promise<Dependency> {
  const { data } = await client.post(`/tasks/${taskId}/dependencies`, body);
  return data;
}

export async function deleteDependency(taskId: number, depId: number): Promise<void> {
  await client.delete(`/tasks/${taskId}/dependencies/${depId}`);
}
