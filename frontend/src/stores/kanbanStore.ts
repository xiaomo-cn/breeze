import { create } from 'zustand';
import type { Task, SubtaskStats } from '../types';
import type { ColumnData } from '../types/board';
import { listTasks, updateTaskStatus, getSubtaskStats } from '../api/tasks';
import { getBoard } from '../api/board';

interface Column {
  id: string;
  title: string;
  color: string;
  tasks: Task[];
}

export interface TaskFilters {
  q?: string;
  status?: string;
  priority?: string;
  type?: string;
  assigneeId?: number;
}

interface KanbanState {
  columns: Column[];
  columnConfigs: ColumnData[];
  loading: boolean;
  filters: TaskFilters;
  sprintId?: number;
  lastManualUpdate: number;

  loadBoard: (projectId: number, sprintId?: number) => Promise<void>;
  loadTasks: (projectId: number) => Promise<void>;
  refreshTasks: (projectId: number) => Promise<void>;
  moveTask: (taskId: number, newStatus: string, newIndex: number) => Promise<void>;
  addTask: (task: Task) => void;
  upsertTask: (task: Task) => void;
  removeTask: (taskId: number) => void;
  setFilters: (filters: TaskFilters) => void;
  refetchBoard: (projectId: number) => Promise<void>;
  diffAndMerge: (newTasks: Task[]) => void;
}

function buildColumns(columnConfigs: ColumnData[], tasks: Task[], sprintMode: boolean): Column[] {
  const visible = sprintMode
    ? columnConfigs.filter((c) => c.statusMapping !== 'backlog')
    : columnConfigs;

  const columns = visible.map((col) => ({
    id: col.statusMapping,
    title: col.name,
    color: col.color,
    tasks: tasks
      .filter((t) => t.status === col.statusMapping)
      .sort((a, b) => a.sortOrder - b.sortOrder),
  }));

  // 未分类列：孤儿 status 任务
  const knownStatuses = new Set(columnConfigs.map((c) => c.statusMapping));
  const orphans = tasks.filter((t) => !knownStatuses.has(t.status));
  if (orphans.length > 0) {
    columns.unshift({
      id: '__unclassified__',
      title: '未分类',
      color: '#94a3b8',
      tasks: orphans.sort((a, b) => a.sortOrder - b.sortOrder),
    });
  }

  return columns;
}

export const useKanbanStore = create<KanbanState>((set, get) => ({
  columns: [],
  columnConfigs: [],
  loading: false,
  filters: {},
  sprintId: undefined,
  lastManualUpdate: 0,

  loadBoard: async (projectId, sprintId) => {
    set({ loading: true, sprintId });
    try {
      const [board, page] = await Promise.all([
        getBoard(projectId),
        listTasks(projectId, {
          ...get().filters,
          sprintId,
          topLevelOnly: true,  // 只加载顶层任务，子任务在详情中查看
          size: 200,
        }),
      ]);
      const tasks = page.items;
      // 批量获取子任务统计
      await fetchAndAttachStats(projectId, tasks);
      set({
        columnConfigs: board.columns,
        columns: buildColumns(board.columns, tasks, !!sprintId),
        loading: false,
      });
    } catch {
      set({ loading: false });
    }
  },

  loadTasks: async (projectId) => {
    set({ loading: true });
    try {
      const { filters, sprintId, columnConfigs } = get();
      const page = await listTasks(projectId, {
        ...filters,
        sprintId,
        topLevelOnly: true,
        size: 200,
      });
      const tasks = page.items;
      await fetchAndAttachStats(projectId, tasks);
      set({
        columns: buildColumns(columnConfigs, tasks, !!sprintId),
        loading: false,
      });
    } catch {
      set({ loading: false });
    }
  },

  refreshTasks: async (projectId) => {
    try {
      const { filters, sprintId } = get();
      const page = await listTasks(projectId, {
        ...filters,
        sprintId,
        topLevelOnly: true,
        size: 200,
      });
      await fetchAndAttachStats(projectId, page.items);
      get().diffAndMerge(page.items);
    } catch {
      // silent
    }
  },

  moveTask: async (taskId, newStatus, newIndex) => {
    const prev = get().columns;
    const newColumns = prev.map((col) => ({
      ...col,
      tasks: col.tasks.filter((t) => t.id !== taskId),
    }));

    let movedTask: Task | undefined;
    for (const col of prev) {
      const found = col.tasks.find((t) => t.id === taskId);
      if (found) {
        movedTask = { ...found, status: newStatus, sortOrder: newIndex };
        break;
      }
    }
    if (!movedTask) return;

    const targetCol = newColumns.find((c) => c.id === newStatus);
    if (targetCol) {
      targetCol.tasks = [
        ...targetCol.tasks.slice(0, newIndex),
        movedTask,
        ...targetCol.tasks.slice(newIndex),
      ];
    }
    set({ columns: newColumns, lastManualUpdate: Date.now() });

    try {
      await updateTaskStatus(taskId, newStatus, newIndex);
    } catch {
      set({ columns: prev });
    }
  },

  addTask: (task) => {
    set((state) => {
      // 检查任务是否已存在（防止 SSE 与本地添加冲突）
      const exists = state.columns.some((col) =>
        col.tasks.some((t) => t.id === task.id)
      );
      if (exists) {
        // 已存在则走更新逻辑
        const columns = state.columns.map((col) => ({
          ...col,
          tasks: col.tasks.map((t) => {
            if (t.id === task.id) {
              return { ...task, subtaskStats: t.subtaskStats || task.subtaskStats };
            }
            return t;
          }),
        }));
        return { columns };
      }
      const columns = state.columns.map((col) => {
        if (col.id === task.status) {
          return { ...col, tasks: [...col.tasks, task] };
        }
        return col;
      });
      return { columns, lastManualUpdate: Date.now() };
    });
  },

  upsertTask: (task) => {
    set((state) => {
      // 从所有列中移除旧任务，同时保留已有的 subtaskStats
      let existingStats: SubtaskStats | undefined;
      const stripped = state.columns.map((col) => ({
        ...col,
        tasks: col.tasks.filter((t) => {
          if (t.id === task.id) {
            existingStats = t.subtaskStats;
            return false;
          }
          return true;
        }),
      }));

      // 合并任务数据
      const merged = { ...task };
      if (!merged.subtaskStats && existingStats) {
        merged.subtaskStats = existingStats;
      }
      if (!merged.status) return { columns: stripped };

      // 插入到对应状态列
      const columns = stripped.map((col) => {
        if (col.id === task.status) {
          const tasks = [...col.tasks, merged];
          // 按 sortOrder 排序
          tasks.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
          return { ...col, tasks };
        }
        return col;
      });

      return { columns };
    });
  },

  removeTask: (taskId) => {
    set((state) => ({
      columns: state.columns.map((col) => ({
        ...col,
        tasks: col.tasks.filter((t) => t.id !== taskId),
      })),
    }));
  },

  setFilters: (newFilters) => {
    set((state) => {
      const merged = { ...state.filters, ...newFilters };
      Object.keys(merged).forEach((k) => {
        if (merged[k as keyof TaskFilters] === undefined) delete merged[k as keyof TaskFilters];
      });
      return { filters: merged };
    });
  },

  refetchBoard: async (projectId) => {
    const board = await getBoard(projectId);
    set({ columnConfigs: board.columns });
    // 重建 columns 结构（保留已有任务数据）
    const { columns: oldColumns } = get();
    const tasks = oldColumns.flatMap((c) => c.tasks);
    set({
      columns: buildColumns(board.columns, tasks, !!get().sprintId),
    });
  },

  diffAndMerge: (newTasks) => {
    const { columns } = get();
    const { columnConfigs, sprintId } = get();
    const newColumns = buildColumns(columnConfigs, newTasks, !!sprintId);
    let changed = false;
    const maxLen = Math.max(columns.length, newColumns.length);
    for (let i = 0; i < maxLen; i++) {
      const oldIds = (columns[i]?.tasks ?? []).map((t) => t.id).join(',');
      const newIds = (newColumns[i]?.tasks ?? []).map((t) => t.id).join(',');
      if (oldIds !== newIds) {
        changed = true;
        break;
      }
    }
    if (changed) {
      set({ columns: newColumns });
    }
  },
}));

/** 批量获取子任务统计并附加到任务对象上 */
async function fetchAndAttachStats(projectId: number, tasks: Task[]) {
  const ids = tasks.map((t) => t.id);
  if (ids.length === 0) return;
  try {
    const stats = await getSubtaskStats(projectId, ids);
    for (const task of tasks) {
      if (stats[task.id]) {
        task.subtaskStats = stats[task.id];
      }
    }
  } catch {
    // 静默失败，不影响看板正常显示
  }
}
