export interface PageDTO<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ApiError {
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
  retryAfterSeconds?: number;
  timestamp: string;
}
