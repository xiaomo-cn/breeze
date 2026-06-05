export const TASK_PRIORITIES = {
  URGENT: { value: 'urgent', label: '紧急', color: 'red' },
  HIGH: { value: 'high', label: '高', color: 'orange' },
  MEDIUM: { value: 'medium', label: '中', color: 'blue' },
  LOW: { value: 'low', label: '低', color: 'green' },
  LOWEST: { value: 'lowest', label: '最低', color: 'default' },
} as const;

export const TASK_TYPES = {
  STORY: { value: 'story', label: '故事' },
  BUG: { value: 'bug', label: '缺陷' },
  TASK: { value: 'task', label: '任务' },
  EPIC: { value: 'epic', label: '史诗' },
} as const;

export const PRIORITY_COLORS: Record<string, string> = {
  urgent: 'red',
  high: 'orange',
  medium: 'blue',
  low: 'green',
  lowest: 'default',
};

export const ROLES = {
  ADMIN: { value: 'admin', label: '管理员' },
  MANAGER: { value: 'manager', label: '经理' },
  MEMBER: { value: 'member', label: '成员' },
  VIEWER: { value: 'viewer', label: '观察者' },
} as const;

export const PROJECT_STATUSES = {
  ACTIVE: { value: 'active', label: '活跃' },
  ARCHIVED: { value: 'archived', label: '已归档' },
  COMPLETED: { value: 'completed', label: '已完成' },
} as const;

/** 默认职务（与后端种子数据一致） */
export const DEFAULT_POSITIONS = [
  { name: '前端开发',   color: '#1677ff' },
  { name: '后端开发',   color: '#52c41a' },
  { name: '全栈开发',   color: '#722ed1' },
  { name: '产品经理',   color: '#fa8c16' },
  { name: 'UI设计师',   color: '#eb2f96' },
  { name: '测试工程师', color: '#13c8cf' },
  { name: 'DevOps',     color: '#fa541c' },
  { name: '技术负责人', color: '#faad14' },
] as const;

/** ColorPicker 预设调色板（用于职务颜色选择） */
export const POSITION_COLOR_PRESETS = [
  { label: '推荐', colors: ['#1677ff', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c8cf', '#fa541c', '#faad14'] },
  { label: '更多', colors: ['#f5222d', '#fa541c', '#fa8c16', '#faad14', '#a0d911', '#52c41a', '#13c8cf', '#1677ff', '#2f54eb', '#722ed1', '#eb2f96'] },
];
