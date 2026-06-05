import client from './client';

export interface AuthResponse {
  userId: number;
  username: string;
  role: string;
  mustChangePassword: boolean;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export async function login(
  username: string,
  password: string,
): Promise<AuthResponse> {
  const { data } = await client.post('/auth/login', { username, password });
  return data;
}

export async function logout(refreshToken: string): Promise<void> {
  await client.post('/auth/logout', { refreshToken });
}
