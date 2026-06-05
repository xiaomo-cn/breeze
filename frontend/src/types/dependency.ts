export interface TaskDependency {
  id: number;
  taskId: number;
  dependsOnTaskId: number;
  dependsOnTaskKey: string;
  dependsOnTaskTitle: string;
  type: string;
  createdAt: string;
}
