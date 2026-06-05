export interface Attachment {
  id: number;
  taskId: number;
  userId: number;
  userName?: string;
  fileName: string;
  fileSize: number;
  contentType?: string;
  url?: string;
  storageProvider?: string;
  createdAt: string;
}
