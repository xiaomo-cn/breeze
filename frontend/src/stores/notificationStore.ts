import { create } from 'zustand';
import type { Notification } from '../types';
import { unreadCount as fetchUnreadCount, listNotifications } from '../api/notifications';

interface NotificationState {
  unreadCount: number;
  recentList: Notification[];
  loading: boolean;
  fetchUnreadCount: () => Promise<void>;
  fetchRecentList: () => Promise<void>;
  incrementUnread: () => void;
  markRead: (id: number) => void;
  markAllRead: () => void;
  prependNotification: (notification: Notification) => void;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  unreadCount: 0,
  recentList: [],
  loading: false,

  fetchUnreadCount: async () => {
    try {
      const count = await fetchUnreadCount();
      set({ unreadCount: count });
    } catch {
      // ignore — bell just shows 0
    }
  },

  fetchRecentList: async () => {
    set({ loading: true });
    try {
      const page = await listNotifications(1, 10);
      set({ recentList: page.items, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  incrementUnread: () => {
    set((s) => ({ unreadCount: s.unreadCount + 1 }));
  },

  markRead: (id: number) => {
    set((s) => ({
      recentList: s.recentList.map((n) =>
        n.id === id ? { ...n, isRead: true } : n,
      ),
    }));
  },

  markAllRead: () => {
    set((s) => ({
      unreadCount: 0,
      recentList: s.recentList.map((n) => ({ ...n, isRead: true })),
    }));
  },

  prependNotification: (notification: Notification) => {
    set((s) => ({
      recentList: [notification, ...s.recentList].slice(0, 10),
    }));
  },
}));
