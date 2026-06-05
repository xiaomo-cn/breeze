import client from './client';
import type { BoardData, ColumnData } from '../types/board';

export type { BoardData, ColumnData };

export async function getBoard(projectId: number): Promise<BoardData> {
  const { data } = await client.get(`/projects/${projectId}/board`);
  return data;
}

export async function createColumn(
  projectId: number,
  body: { name: string; statusMapping: string; wipLimit?: number; sortOrder?: number; color?: string },
): Promise<ColumnData> {
  const { data } = await client.post(`/projects/${projectId}/board/columns`, body);
  return data;
}

export async function updateColumn(
  projectId: number,
  columnId: number,
  body: { name?: string; statusMapping?: string; wipLimit?: number; sortOrder?: number; color?: string },
): Promise<ColumnData> {
  const { data } = await client.patch(`/projects/${projectId}/board/columns/${columnId}`, body);
  return data;
}

export async function deleteColumn(
  projectId: number,
  columnId: number,
  migrateToColumnId: number,
): Promise<void> {
  await client.delete(`/projects/${projectId}/board/columns/${columnId}`, {
    params: { migrateToColumnId },
  });
}

export async function updateColumnsOrder(
  projectId: number,
  sorts: { id: number; sortOrder: number }[],
): Promise<void> {
  await client.put(`/projects/${projectId}/board/columns`, sorts);
}

export async function getValidStatuses(projectId: number): Promise<string[]> {
  const { data } = await client.get(`/projects/${projectId}/board/statuses`);
  return data;
}
