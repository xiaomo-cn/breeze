export interface ColumnData {
  id: number;
  name: string;
  statusMapping: string;
  wipLimit: number;
  sortOrder: number;
  color: string;
}

export interface BoardData {
  id: number;
  projectId: number;
  name: string;
  isDefault: boolean;
  columns: ColumnData[];
}
