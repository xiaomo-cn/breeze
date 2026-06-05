import client from './client';
import type { Project, ProjectMember, PageDTO } from '../types';

export type { Project, ProjectMember };

export async function listProjects(
  params?: { page?: number; size?: number; status?: string },
): Promise<PageDTO<Project>> {
  const { data } = await client.get('/projects', { params });
  return data;
}

export async function createProject(project: Partial<Project>): Promise<Project> {
  const { data } = await client.post('/projects', project);
  return data;
}

export async function getProject(id: number): Promise<Project> {
  const { data } = await client.get(`/projects/${id}`);
  return data;
}

export async function updateProject(
  id: number,
  updates: Partial<Project>,
): Promise<Project> {
  const { data } = await client.put(`/projects/${id}`, updates);
  return data;
}

export async function listProjectMembers(projectId: number): Promise<ProjectMember[]> {
  const { data } = await client.get(`/projects/${projectId}/members`);
  return data;
}

export async function addProjectMember(
  projectId: number,
  userId: number,
  role: string,
): Promise<ProjectMember> {
  const { data } = await client.post(`/projects/${projectId}/members`, { userId, role });
  return data;
}

export async function removeProjectMember(
  projectId: number,
  userId: number,
): Promise<void> {
  await client.delete(`/projects/${projectId}/members/${userId}`);
}

export async function updateProjectMemberRole(
  projectId: number,
  userId: number,
  role: string,
): Promise<void> {
  await client.patch(`/projects/${projectId}/members/${userId}`, { role });
}

/** 获取当前用户在该项目中的角色 */
export async function getMyProjectRole(projectId: number): Promise<string> {
  const { data } = await client.get(`/projects/${projectId}/my-role`);
  return data.role;
}
