export interface RiskItem {
  id: number;
  key: string;
  title: string;
  status: string;
  priority: string;
  riskLevel: string;
  riskReason: string;
  assigneeId: number;
  dueDate: string;
}

export interface ProjectRisks {
  high: RiskItem[];
  medium: RiskItem[];
  low: RiskItem[];
}

export async function getProjectRisks(projectId: number): Promise<ProjectRisks> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch(`/api/v1/ai/projects/${projectId}/risks`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed');
  return res.json();
}

export async function assessTask(taskId: number): Promise<{ riskLevel: string; riskReason: string }> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch(`/api/v1/ai/risks/${taskId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed');
  return res.json();
}

export async function assessProject(projectId: number): Promise<void> {
  const token = localStorage.getItem('accessToken');
  const res = await fetch(`/api/v1/ai/risks/project/${projectId}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('Failed');
}
