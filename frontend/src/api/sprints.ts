import client from './client';
import type { Sprint, BurndownPoint } from '../types';

export async function listSprints(projectId: number): Promise<Sprint[]> {
  const { data } = await client.get(`/projects/${projectId}/sprints`);
  return data;
}

export async function createSprint(
  projectId: number,
  body: { name: string; goal?: string; startDate?: string; endDate?: string },
): Promise<Sprint> {
  const { data } = await client.post(`/projects/${projectId}/sprints`, body);
  return data;
}

export async function updateSprint(
  projectId: number,
  sprintId: number,
  body: { name?: string; goal?: string; startDate?: string; endDate?: string },
): Promise<Sprint> {
  const { data } = await client.put(`/projects/${projectId}/sprints/${sprintId}`, body);
  return data;
}

export async function startSprint(projectId: number, sprintId: number): Promise<Sprint> {
  const { data } = await client.post(`/projects/${projectId}/sprints/${sprintId}/start`);
  return data;
}

export async function closeSprint(projectId: number, sprintId: number): Promise<Sprint> {
  const { data } = await client.post(`/projects/${projectId}/sprints/${sprintId}/close`);
  return data;
}

export async function getBurndown(projectId: number, sprintId: number): Promise<BurndownPoint[]> {
  const { data } = await client.get(`/projects/${projectId}/sprints/${sprintId}/burndown`);
  return data;
}

export async function deleteSprint(projectId: number, sprintId: number): Promise<void> {
  await client.delete(`/projects/${projectId}/sprints/${sprintId}`);
}

export async function addTaskToSprint(projectId: number, sprintId: number, taskId: number): Promise<void> {
  await client.post(`/projects/${projectId}/sprints/${sprintId}/tasks`, { taskId });
}

export async function removeTaskFromSprint(projectId: number, sprintId: number, taskId: number): Promise<void> {
  await client.delete(`/projects/${projectId}/sprints/${sprintId}/tasks/${taskId}`);
}

export async function getSprint(projectId: number, sprintId: number): Promise<Sprint> {
  const { data } = await client.get(`/projects/${projectId}/sprints/${sprintId}`);
  return data;
}
