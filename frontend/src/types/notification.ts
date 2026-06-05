export interface Notification {
  id: number;
  userId: number;
  type: 'TASK_ASSIGNED' | 'TASK_CREATED' | 'COMMENT_ADDED' | 'MENTIONED' | 'COLLABORATOR_ADDED';
  title: string;
  body?: string;
  referenceType?: string;
  referenceId?: number;
  isRead: boolean;
  createdAt?: string;
}
