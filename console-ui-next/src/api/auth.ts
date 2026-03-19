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

/* ---------- User ---------- */
export interface UserItem {
  username: string;
  password?: string;
}

export interface UserListResponse {
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
  pageItems: UserItem[];
}

/* ---------- Role ---------- */
export interface RoleItem {
  role: string;
  username: string;
}

export interface RoleListResponse {
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
  pageItems: RoleItem[];
}

/* ---------- Permission ---------- */
export interface PermissionItem {
  role: string;
  resource: string;
  action: string;
}

export interface PermissionListResponse {
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
  pageItems: PermissionItem[];
}

export const authApi = {
  /* login / admin */
  login: (data: { username: string; password: string }): AxiosPromise<LoginResponse> =>
    client.post('v3/auth/user/login', data),

  admin: (data: { username: string; password: string }): AxiosPromise<AdminResponse> =>
    client.post('v3/auth/user/admin', data),

  /* ---- Users ---- */
  listUsers: (params: {
    pageNo: number;
    pageSize: number;
    username?: string;
    search?: string;
  }): AxiosPromise<UserListResponse> =>
    client.get('v3/auth/user/list', { params }),

  createUser: (data: { username: string; password: string }): AxiosPromise<void> =>
    client.post('v3/auth/user', data),

  deleteUser: (username: string): AxiosPromise<void> =>
    client.delete('v3/auth/user', { params: { username } }),

  resetPassword: (data: { username: string; newPassword: string }): AxiosPromise<void> =>
    client.put('v3/auth/user', data),

  /* ---- Roles ---- */
  listRoles: (params: {
    pageNo: number;
    pageSize: number;
    role?: string;
    username?: string;
    search?: string;
  }): AxiosPromise<RoleListResponse> =>
    client.get('v3/auth/role/list', { params }),

  createRole: (data: { role: string; username: string }): AxiosPromise<void> =>
    client.post('v3/auth/role', data),

  deleteRole: (data: { role: string; username: string }): AxiosPromise<void> =>
    client.delete('v3/auth/role', { params: data }),

  /* ---- Permissions ---- */
  listPermissions: (params: {
    pageNo: number;
    pageSize: number;
    role?: string;
    search?: string;
  }): AxiosPromise<PermissionListResponse> =>
    client.get('v3/auth/permission/list', { params }),

  createPermission: (data: { role: string; resource: string; action: string }): AxiosPromise<void> =>
    client.post('v3/auth/permission', data),

  deletePermission: (data: { role: string; resource: string; action: string }): AxiosPromise<void> =>
    client.delete('v3/auth/permission', { params: data }),
};
