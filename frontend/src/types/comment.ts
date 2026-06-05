export interface Comment {
  id: number;
  taskId: number;
  parentId?: number;
  userId: number;
  username?: string;
  displayName?: string;
  avatarUrl?: string;
  content: string;
  replies?: Comment[];
  createdAt?: string;
  updatedAt?: string;
}
