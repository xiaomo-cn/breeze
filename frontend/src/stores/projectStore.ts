import { create } from 'zustand';

/**
 * 项目全局状态 — 主要用于跨组件通信：
 * - 创建项目后通知 Sidebar 刷新项目列表
 * - 后续可扩展归档/删除等场景
 */
interface ProjectStore {
  /** 递增计数器，每次 +1 触发监听方刷新 */
  refreshKey: number;
  /** 创建/更新/删除项目后调用，通知所有订阅方重新加载 */
  triggerRefresh: () => void;
}

export const useProjectStore = create<ProjectStore>((set) => ({
  refreshKey: 0,
  triggerRefresh: () => set((s) => ({ refreshKey: s.refreshKey + 1 })),
}));
