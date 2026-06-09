/** 知识库文档/文件夹 */
export interface KnowledgeDocument {
  id: number;
  parentFolderId: number | null;
  title: string;
  description?: string;
  fileName?: string;
  fileType: 'folder' | 'pdf' | 'docx' | 'xlsx' | 'pptx' | 'md' | 'txt' | 'png' | 'jpg' | 'html' | 'csv' | 'unknown';
  fileSize: number;
  fileHash?: string;
  storageKey?: string;
  chunkCount: number;
  childCount?: number; // 子项数量（仅文件夹有效）
  embeddingStatus: 'pending' | 'processing' | 'completed' | 'failed';
  createdBy: number;
  updatedBy?: number;
  createdAt: string;
  updatedAt: string;
  isDeleted: boolean;
}

/** 知识库标签 */
export interface KnowledgeTag {
  id: number;
  name: string;
  color: string;
  createdAt: string;
}

/** 知识库对话 */
export interface KnowledgeConversation {
  id: number;
  userId: number;
  title: string;
  model: string;
  createdAt: string;
  updatedAt: string;
}

/** 知识库消息 */
export interface KnowledgeMessage {
  id: number;
  conversationId: number;
  role: 'user' | 'assistant';
  content: string;
  referencedDocs?: ReferencedDoc[];
  tokenCount?: number;
  createdAt: string;
}

/** 引用文档 */
export interface ReferencedDoc {
  id: number;
  title: string;
  fileType: string;
  pageNumber?: string;
  score?: number;  // 相似度分数，如 85.5 表示相关度 85.5%
}
