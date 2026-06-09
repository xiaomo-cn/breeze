import client from './client';
import type { KnowledgeDocument, KnowledgeTag, KnowledgeConversation, KnowledgeMessage } from '../types/knowledge';

const BASE = '/knowledge';

// ====== 文档管理 ======

/** 上传文档 */
export async function uploadDocument(formData: FormData): Promise<KnowledgeDocument> {
  const { data } = await client.post(`${BASE}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

/** 创建文件夹 */
export async function createFolder(parentFolderId: number | null, name: string): Promise<KnowledgeDocument> {
  const { data } = await client.post(`${BASE}/folders`, null, { params: { parentFolderId, name } });
  return data;
}

/** 获取文件夹内容 */
export async function fetchFolderContents(parentFolderId?: number | null): Promise<KnowledgeDocument[]> {
  const { data } = await client.get(`${BASE}/documents`, { params: { parentFolderId } });
  return data;
}

/** 获取文件夹树 */
export async function fetchFolderTree(): Promise<KnowledgeDocument[]> {
  const { data } = await client.get(`${BASE}/documents/tree`);
  return data;
}

/** 搜索文档 */
export async function searchDocuments(parentFolderId: number | null, keyword: string): Promise<KnowledgeDocument[]> {
  const { data } = await client.get(`${BASE}/documents/search`, { params: { parentFolderId, keyword } });
  return data;
}

/** 获取文档详情 */
export async function fetchDocument(id: number): Promise<KnowledgeDocument> {
  const { data } = await client.get(`${BASE}/documents/${id}`);
  return data;
}

/** 获取文档标签 */
export async function fetchDocumentTags(id: number): Promise<KnowledgeTag[]> {
  const { data } = await client.get(`${BASE}/documents/${id}/tags`);
  return data;
}

/** 更新文档元数据 */
export async function updateDocumentMeta(id: number, params: { title?: string; description?: string; tags?: string[] }): Promise<void> {
  await client.put(`${BASE}/documents/${id}`, null, { params });
}

/** 删除文档 */
export async function deleteDocument(id: number): Promise<void> {
  await client.delete(`${BASE}/documents/${id}`);
}

/** 重试向量化 */
export async function retryEmbedding(id: number): Promise<void> {
  await client.post(`${BASE}/documents/${id}/retry-embedding`);
}

/** 移动文档到指定文件夹 */
export async function moveDocument(id: number, parentFolderId: number | null): Promise<void> {
  await client.put(`${BASE}/documents/${id}/move`, null, { params: { parentFolderId } });
}

// ====== 标签 ======

/** 标签列表 */
export async function fetchTags(keyword?: string): Promise<KnowledgeTag[]> {
  const { data } = await client.get(`${BASE}/tags`, { params: { keyword } });
  return data;
}

// ====== AI 问答 ======

/** SSE 流式问答 */
export function knowledgeChat(conversationId: number | null, message: string): Promise<Response> {
  const params = new URLSearchParams({ message });
  if (conversationId) params.set('conversationId', String(conversationId));
  return fetch(`/api/v1${BASE}/chat`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${localStorage.getItem('accessToken')}` },
    body: params,
  });
}

/** 获取对话列表 */
export async function fetchConversations(): Promise<KnowledgeConversation[]> {
  const { data } = await client.get(`${BASE}/conversations`);
  return data;
}

/** 获取对话消息 */
export async function fetchMessages(conversationId: number): Promise<KnowledgeMessage[]> {
  const { data } = await client.get(`${BASE}/conversations/${conversationId}/messages`);
  return data;
}

/** 删除对话 */
export async function deleteConversation(id: number): Promise<void> {
  await client.delete(`${BASE}/conversations/${id}`);
}

// ====== 文件访问 ======

/** 获取文件预览 URL */
export function getFileUrl(documentId: number): string {
  const token = localStorage.getItem('accessToken');
  return `/api/v1${BASE}/files/${documentId}?token=${token}`;
}
