export interface Task {
  id: number;
  projectId: number;
  parentId?: number;
  key: string;
  title: string;
  description?: string;
  type: string;
  status: string;
  priority: string;
  assigneeId?: number;
  /** 协作人用户 ID 列表 */
  collaboratorIds?: number[];
  reporterId: number;
  sprintId?: number;
  storyPoints?: number;
  estimatedHours?: number;
  loggedHours?: number;
  dueDate?: string;
  startedAt?: string;
  resolvedAt?: string;
  sortOrder: number;
  kanbanColumnId?: number;
  riskLevel?: string;
  riskReason?: string;
  isDeleted?: boolean;
  createdAt?: string;
  updatedAt?: string;
  /** 子任务统计（看板聚合时填充） */
  subtaskStats?: SubtaskStats;
}

export interface SubtaskStats {
  total: number;
  done: number;
}
