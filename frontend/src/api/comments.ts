import client from './client';
import type { Comment, PageDTO } from '../types';

export async function listComments(
  taskId: number,
  page = 1,
  size = 20,
): Promise<PageDTO<Comment>> {
  const { data } = await client.get(`/tasks/${taskId}/comments`, { params: { page, size } });
  return data;
}

export async function createComment(
  taskId: number,
  content: string,
  parentId?: number,
): Promise<Comment> {
  const { data } = await client.post(`/tasks/${taskId}/comments`, { content, parentId });
  return data;
}

export async function updateComment(id: number, content: string): Promise<Comment> {
  const { data } = await client.put(`/comments/${id}`, { content });
  return data;
}

export async function deleteComment(id: number): Promise<void> {
  await client.delete(`/comments/${id}`);
}
