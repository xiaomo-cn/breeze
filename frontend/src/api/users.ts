import client from './client';
import type { User, PageDTO } from '../types';

export type { User };

export async function listUsers(
  params?: { page?: number; size?: number; search?: string },
): Promise<PageDTO<User>> {
  const { data } = await client.get('/users', { params });
  return data;
}

export async function getUser(id: number): Promise<User> {
  const { data } = await client.get(`/users/${id}`);
  return data;
}

export async function getUserSuggestions(q: string): Promise<User[]> {
  const { data } = await client.get('/users/suggestions', { params: { q } });
  return data;
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await client.patch('/users/me/password', { oldPassword, newPassword });
}
