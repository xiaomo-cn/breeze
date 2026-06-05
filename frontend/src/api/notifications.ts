import client from './client';
import type { Notification, PageDTO } from '../types';

export async function listNotifications(
  page = 1,
  size = 20,
): Promise<PageDTO<Notification>> {
  const { data } = await client.get('/notifications', { params: { page, size } });
  return data;
}

export async function unreadCount(): Promise<number> {
  const { data } = await client.get('/notifications/unread-count');
  return data.count;
}

export async function markRead(id: number): Promise<void> {
  await client.patch(`/notifications/${id}/read`);
}

export async function markAllRead(): Promise<void> {
  await client.patch('/notifications/read-all');
}
