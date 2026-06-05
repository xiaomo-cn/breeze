export interface GanttTask {
  id: number;
  key: string;
  title: string;
  startDate: string | null;
  endDate: string | null;
  assigneeName: string | null;
  status: string;
  dependencies: number[];
}

export interface GanttData {
  tasks: GanttTask[];
}
