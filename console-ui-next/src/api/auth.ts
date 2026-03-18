import client from './client';
import type { AxiosPromise } from 'axios';

export interface LoginResponse {
  accessToken: string;
  username: string;
  globalAdmin: boolean;
}

export interface AdminResponse {
  accessToken: string;
  username: string;
  globalAdmin: boolean;
}

export const authApi = {
  login: (data: { username: string; password: string }): AxiosPromise<LoginResponse> =>
    client.post('v3/auth/user/login', data),
  
  admin: (data: { username: string; password: string }): AxiosPromise<AdminResponse> =>
    client.post('v3/auth/user/admin', data),
};
