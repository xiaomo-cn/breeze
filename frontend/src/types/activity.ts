export interface ActivityLogEntry {
  id: number;
  projectId: number;
  userId: number;
  username?: string;
  displayName?: string;
  avatarUrl?: string;
  actionType: string;
  entityType: string;
  entityId: number;
  details?: string;
  createdAt: string;
}
