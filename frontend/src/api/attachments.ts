import client from './client';
import type { Attachment } from '../types';

export async function listAttachments(taskId: number): Promise<Attachment[]> {
  const { data } = await client.get(`/tasks/${taskId}/attachments`);
  return data;
}

export async function uploadAttachment(taskId: number, file: File): Promise<Attachment> {
  const form = new FormData();
  form.append('file', file);
  const { data } = await client.post(`/tasks/${taskId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

export function getDownloadUrl(attachmentOrId: Attachment | number): string {
  if (typeof attachmentOrId === 'object') {
    return attachmentOrId.url ?? `/api/v1/attachments/${attachmentOrId.id}/download`;
  }
  return `/api/v1/attachments/${attachmentOrId}/download`;
}

export async function deleteAttachment(id: number): Promise<void> {
  await client.delete(`/attachments/${id}`);
}
