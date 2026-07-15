import client from './client';
import type { ApiResult } from './types';

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
  /* login / admin — these return flat token data, NOT the standard {code,data} wrapper */
  login: (data: { username: string; password: string }): Promise<LoginResponse> =>
    client.post('v3/auth/user/login', data) as Promise<LoginResponse>,

  admin: (data: { username: string; password: string }): Promise<AdminResponse> =>
    client.post('v3/auth/user/admin', data) as Promise<AdminResponse>,

  /* ---- Users ---- */
  listUsers: (params: {
    pageNo: number;
    pageSize: number;
    username?: string;
    search?: string;
  }): ApiResult<UserListResponse> =>
    client.get('v3/auth/user/list', { params }) as ApiResult<UserListResponse>,

  createUser: (data: { username: string; password: string }): ApiResult<boolean> =>
    client.post('v3/auth/user', data) as ApiResult<boolean>,

  deleteUser: (username: string): ApiResult<boolean> =>
    client.delete('v3/auth/user', { params: { username } }) as ApiResult<boolean>,

  resetPassword: (data: { username: string; newPassword: string }): ApiResult<boolean> =>
    client.put('v3/auth/user', data) as ApiResult<boolean>,

  /* ---- Roles ---- */
  listRoles: (params: {
    pageNo: number;
    pageSize: number;
    role?: string;
    username?: string;
    search?: string;
  }): ApiResult<RoleListResponse> =>
    client.get('v3/auth/role/list', { params }) as ApiResult<RoleListResponse>,

  createRole: (data: { role: string; username: string }): ApiResult<boolean> =>
    client.post('v3/auth/role', data) as ApiResult<boolean>,

  deleteRole: (data: { role: string; username: string }): ApiResult<boolean> =>
    client.delete('v3/auth/role', { params: data }) as ApiResult<boolean>,

  /* ---- Permissions ---- */
  listPermissions: (params: {
    pageNo: number;
    pageSize: number;
    role?: string;
    search?: string;
  }): ApiResult<PermissionListResponse> =>
    client.get('v3/auth/permission/list', { params }) as ApiResult<PermissionListResponse>,

  createPermission: (data: { role: string; resource: string; action: string }): ApiResult<boolean> =>
    client.post('v3/auth/permission', data) as ApiResult<boolean>,

  deletePermission: (data: { role: string; resource: string; action: string }): ApiResult<boolean> =>
    client.delete('v3/auth/permission', { params: data }) as ApiResult<boolean>,
};
