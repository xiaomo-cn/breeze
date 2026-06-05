import client from './client';
import type { Task, PageDTO, SubtaskStats } from '../types';

export type { Task };

export interface ListTasksParams {
  q?: string;
  status?: string;
  priority?: string;
  type?: string;
  assigneeId?: number;
  sprintId?: number;
  parentId?: number;
  topLevelOnly?: boolean;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

export async function listTasks(
  projectId: number,
  params?: ListTasksParams,
): Promise<PageDTO<Task>> {
  const { data } = await client.get(`/projects/${projectId}/tasks`, { params });
  return data;
}

export async function createTask(
  projectId: number,
  task: Partial<Task>,
): Promise<Task> {
  const { data } = await client.post(`/projects/${projectId}/tasks`, task);
  return data;
}

export async function getTask(id: number): Promise<Task> {
  const { data } = await client.get(`/tasks/${id}`);
  return data;
}

export async function getChildren(taskId: number): Promise<Task[]> {
  const { data } = await client.get(`/tasks/${taskId}/children`);
  return data;
}

export async function getSubtaskStats(
  projectId: number,
  ids: number[],
): Promise<Record<number, SubtaskStats>> {
  if (ids.length === 0) return {};
  const { data } = await client.get(`/projects/${projectId}/tasks/subtask-stats`, {
    params: { ids: ids.join(',') },
    paramsSerializer: (params) => {
      const search = new URLSearchParams();
      Object.entries(params).forEach(([k, v]) => search.append(k, String(v)));
      return search.toString();
    },
  });
  return data;
}

export async function updateTask(
  id: number,
  updates: Partial<Task>,
): Promise<Task> {
  const { data } = await client.put(`/tasks/${id}`, updates);
  return data;
}

export async function updateTaskStatus(
  taskId: number,
  status: string,
  sortOrder?: number,
  kanbanColumnId?: number,
): Promise<Task> {
  const { data } = await client.patch(`/tasks/${taskId}/status`, {
    status,
    sortOrder,
    kanbanColumnId,
  });
  return data;
}

export async function deleteTask(id: number): Promise<void> {
  await client.delete(`/tasks/${id}`);
}

export interface TaskSummary {
  id: number;
  key: string;
  title: string;
  status: string;
  priority: string;
  dueDate: string;
  projectId: number;
}

export interface MyOverdueTasks {
  overdue: TaskSummary[];
  dueToday: TaskSummary[];
  dueSoon: TaskSummary[];
}

export async function getMyOverdueTasks(): Promise<MyOverdueTasks> {
  const { data } = await client.get('/tasks/my-overdue');
  return data;
}

export async function searchTasks(
  projectId: number,
  params: { q: string; type?: string },
): Promise<{ items: Task[] }> {
  const { data } = await client.get(`/projects/${projectId}/search`, { params });
  return data;
}
