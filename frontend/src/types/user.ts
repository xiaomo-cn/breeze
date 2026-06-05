export interface User {
  id: number;
  username: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  title?: string;
  positionId?: number | null;
  department?: string;
  timezone?: string;
  locale?: string;
  role?: string;
  mustChangePassword?: boolean;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}
