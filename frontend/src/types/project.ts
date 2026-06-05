export interface Project {
  id: number;
  name: string;
  key: string;
  description?: string;
  iconUrl?: string;
  status: string;
  visibility?: string;
  ownerId: number;
  startDate?: string;
  endDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProjectMember {
  id: number;
  projectId: number;
  userId: number;
  role: string;
  joinedAt?: string;
}
