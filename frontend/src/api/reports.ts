import client from './client';
import type { DailyReport, WeeklyReport, SprintReport } from '../types';

/** AI 报告实体 */
export interface AiReportItem {
  id: number;
  projectId: number;
  type: string;
  title: string;
  content: string;
  generatedAt: string;
}

/** 生成 AI 报告（自动保存）。超时设为 2 分钟，AI 生成完整报告可能需要较长时间 */
export async function generateAiReport(projectId: number, type: string): Promise<AiReportItem> {
  const { data } = await client.post('/ai/reports/generate', null, {
    params: { projectId, type },
    timeout: 120000, // 2 分钟超时，AI 生成报告较慢
  });
  return {
    id: data.id,
    projectId,
    type,
    title: data.title,
    content: data.markdown,
    generatedAt: data.generatedAt,
  };
}

/** 查询项目下指定类型的历史 AI 报告 */
export async function listAiReports(projectId: number, type: string): Promise<AiReportItem[]> {
  const { data } = await client.get('/ai/reports', {
    params: { projectId, type },
  });
  return data;
}

export async function getDailyReport(projectId: number, date: string): Promise<DailyReport> {
  const { data } = await client.get(`/projects/${projectId}/reports/daily`, {
    params: { date },
  });
  return data;
}

export async function getWeeklyReport(
  projectId: number, start: string, end: string,
): Promise<WeeklyReport> {
  const { data } = await client.get(`/projects/${projectId}/reports/weekly`, {
    params: { start, end },
  });
  return data;
}

export async function getSprintReport(
  projectId: number, sprintId: number,
): Promise<SprintReport> {
  const { data } = await client.get(`/projects/${projectId}/reports/sprint/${sprintId}`);
  return data;
}

export function getExportUrl(
  projectId: number, type: string, format: string, params: Record<string, string>,
): string {
  const searchParams = new URLSearchParams({ type, format, ...params });
  return `/api/v1/projects/${projectId}/reports/export?${searchParams}`;
}
