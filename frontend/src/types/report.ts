export interface TaskSummary {
  id: number;
  key: string;
  title: string;
  status: string;
  priority: string;
  assigneeName: string | null;
}

export interface DailyReport {
  date: string;
  completedTasks: TaskSummary[];
  inProgressTasks: TaskSummary[];
  blockedTasks: TaskSummary[];
  createdCount: number;
  completedCount: number;
}

export interface DailyPoint {
  date: string;
  created: number;
  completed: number;
}

export interface MemberContribution {
  userName: string;
  completed: number;
  created: number;
}

export interface WeeklyReport {
  startDate: string;
  endDate: string;
  dailyPoints: DailyPoint[];
  taskDistribution: Record<string, number>;
  contributions: MemberContribution[];
  newTasks: number;
  completedTasks: number;
  remainingTasks: number;
}

export interface BurndownPoint {
  date: string;
  idealRemaining: number;
  actualRemaining: number;
}

export interface SprintReport {
  sprintId: number;
  sprintName: string;
  sprintGoal: string;
  sprintStatus: string;
  totalTasks: number;
  completedTasks: number;
  totalStoryPoints: number;
  completedStoryPoints: number;
  completionRate: number;
  contributions: MemberContribution[];
  burndown: BurndownPoint[];
}
