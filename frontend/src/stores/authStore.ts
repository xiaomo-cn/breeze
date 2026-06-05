import { create } from 'zustand';
import { logout as logoutApi } from '../api/auth';
import type { AuthResponse } from '../api/auth';

interface AuthState {
  userId: number | null;
  username: string | null;
  role: string | null;
  mustChangePassword: boolean;
  isLoggedIn: boolean;
  isInitialized: boolean;
  setAuth: (resp: AuthResponse) => void;
  logout: () => Promise<void>;
  initialize: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  userId: null,
  username: null,
  role: null,
  mustChangePassword: false,
  isLoggedIn: false,
  isInitialized: false,

  initialize: () => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      set({
        userId: Number(localStorage.getItem('userId')),
        username: localStorage.getItem('username'),
        role: localStorage.getItem('role'),
        mustChangePassword: localStorage.getItem('mustChangePassword') === 'true',
        isLoggedIn: true,
        isInitialized: true,
      });
    } else {
      set({ isInitialized: true });
    }
  },

  setAuth: (resp) => {
    localStorage.setItem('userId', String(resp.userId));
    localStorage.setItem('username', resp.username);
    localStorage.setItem('role', resp.role || 'user');
    localStorage.setItem('mustChangePassword', String(resp.mustChangePassword || false));
    localStorage.setItem('accessToken', resp.accessToken);
    localStorage.setItem('refreshToken', resp.refreshToken);
    set({
      userId: resp.userId,
      username: resp.username,
      role: resp.role || 'user',
      mustChangePassword: resp.mustChangePassword || false,
      isLoggedIn: true,
    });
  },

  logout: async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      try {
        await logoutApi(refreshToken);
      } catch {
        // ignore — still clear local state
      }
    }
    localStorage.clear();
    set({ userId: null, username: null, role: null, mustChangePassword: false, isLoggedIn: false });
  },
}));
