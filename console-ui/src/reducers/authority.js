/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Message } from '@alifd/next';
import request from '../utils/request';
import { UPDATE_USER, SIGN_IN, USER_LIST, ROLE_LIST, PERMISSIONS_LIST } from '../constants';

const initialState = {
  users: {
    totalCount: 0,
    pageNumber: 1,
    pagesAvailable: 1,
    pageItems: [],
  },
  roles: {
    totalCount: 0,
    pageNumber: 1,
    pagesAvailable: 1,
    pageItems: [],
  },
  permissions: {
    totalCount: 0,
    pageNumber: 1,
    pagesAvailable: 1,
    pageItems: [],
  },
};

const successMsg = res => {
  if (res.code === 0) {
    Message.success(res.message);
  }
  return res;
};

/**
 * User list
 * @param {*} params
 */
const getUsers = params => dispatch =>
  request
    .get('v3/auth/user/list', { params })
    .then(data => dispatch({ type: USER_LIST, data: data.data }));

/**
 * Create user
 * @param {*} param0
 */
const createUser = ([username, password]) =>
  request.post('v3/auth/user', { username, password }).then(res => successMsg(res));

/**
 * Fuzzy search by username
 * @param {*} param0
 */
const searchUsers = username =>
  request.get('v3/auth/user/search', { params: { username } }).then(res => successMsg(res.data));

/**
 * Delete user
 * @param {*} username
 */
const deleteUser = username =>
  request.delete('v3/auth/user', { params: { username } }).then(res => successMsg(res));

/**
 * Reset password
 * @param {*} param0
 */
const passwordReset = ([username, newPassword]) =>
  request.put('v3/auth/user', { username, newPassword });

/**
 * Role list
 * @param {*} params
 */

const getRoles = params => dispatch =>
  request
    .get('v3/auth/role/list', { params })
    .then(data => dispatch({ type: ROLE_LIST, data: data.data }));

/**
 * Fuzzy search by role
 * @param {*} param0
 */
const searchRoles = role =>
  request.get('v3/auth/role/search', { params: { role } }).then(res => successMsg(res.data));

/**
 * Create role
 * @param {*} param0
 */
const createRole = ([role, username]) =>
  request.post('v3/auth/role', { role, username }).then(res => successMsg(res));

/**
 * Delete role
 * @param {*} param0
 */
const deleteRole = role =>
  request.delete('v3/auth/role', { params: role }).then(res => successMsg(res));

/**
 * Permission list
 * @param {*} params
 */
const getPermissions = params => dispatch =>
  request
    .get('v3/auth/permission/list', { params })
    .then(data => dispatch({ type: PERMISSIONS_LIST, data: data.data }));

/**
 * Pre-validation before adding permission
 * @param {*} param0
 */
const checkPermission = ([role, resource, action]) => {
  const params = { role, resource, action };
  return request.get('v3/auth/permission', { params }).then(res => res.data);
};

/**
 * Add permission to role
 * @param {*} param0
 */
const createPermission = ([role, resource, action]) =>
  request.post('v3/auth/permission', { role, resource, action }).then(res => successMsg(res));

/**
 * Delete permission
 * @param {*} param0
 */
const deletePermission = permission =>
  request.delete('v3/auth/permission', { params: permission }).then(res => successMsg(res));

export default (state = initialState, action) => {
  switch (action.type) {
    case USER_LIST:
      return { ...state, users: { ...action.data } };
    case ROLE_LIST:
      return { ...state, roles: { ...action.data } };
    case PERMISSIONS_LIST:
      return { ...state, permissions: { ...action.data } };
    default:
      return state;
  }
};

export {
  searchUsers,
  getUsers,
  createUser,
  deleteUser,
  passwordReset,
  searchRoles,
  getRoles,
  createRole,
  deleteRole,
  getPermissions,
  checkPermission,
  createPermission,
  deletePermission,
};
