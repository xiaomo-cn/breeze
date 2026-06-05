import client from './client';
import type { User, Position, PageDTO } from '../types';

export async function listUsers(params?: { page?: number; size?: number; search?: string }): Promise<PageDTO<User>> {
  const { data } = await client.get('/admin/users', { params });
  return data;
}

export async function createUser(req: { username: string; email: string; password: string; displayName?: string; positionId?: number }): Promise<User> {
  const { data } = await client.post('/admin/users', req);
  return data;
}

export async function updateUserRole(userId: number, role: string): Promise<User> {
  const { data } = await client.patch(`/admin/users/${userId}/role`, { role });
  return data;
}

export async function toggleUserStatus(userId: number, isActive: boolean): Promise<User> {
  const { data } = await client.patch(`/admin/users/${userId}/status`, { isActive });
  return data;
}

export async function updateUserProfile(userId: number, displayName: string, title?: string, positionId?: number | null): Promise<User> {
  const { data } = await client.patch(`/admin/users/${userId}/profile`, { displayName, title, positionId });
  return data;
}

export async function resetUserPassword(userId: number, newPassword: string): Promise<void> {
  await client.patch(`/admin/users/${userId}/password`, { newPassword });
}

// ==================== 职务管理 ====================

/** 获取所有职务列表（公开接口） */
export async function listPositions(): Promise<Position[]> {
  const { data } = await client.get('/positions');
  return data;
}

/** 管理员创建职务 */
export async function createPosition(req: { name: string; color?: string }): Promise<Position> {
  const { data } = await client.post('/admin/positions', req);
  return data;
}

/** 管理员更新职务 */
export async function updatePosition(id: number, req: { name?: string; color?: string }): Promise<Position> {
  const { data } = await client.patch(`/admin/positions/${id}`, req);
  return data;
}

/** 管理员删除职务 */
export async function deletePosition(id: number): Promise<void> {
  await client.delete(`/admin/positions/${id}`);
}
