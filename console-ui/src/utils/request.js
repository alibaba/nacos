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

import axios from 'axios';
import qs from 'qs';
import { toastError } from './message';
import { browserHistory } from 'react-router';
import { isPlainObject } from './nacosutil';
// import { SUCCESS_RESULT_CODE } from '../constants';
import { goRegister } from '../globalLib';

const API_GENERAL_ERROR_MESSAGE = 'Request error, please try again later!';

const SESSION_EXPIRED_MESSAGES = [
  'unknown user!',
  'user not found',
  'token invalid!',
  'token expired!',
  'expired token',
  'session expired!',
  'invalid signature',
  'unsupported signature algorithm',
  'invalid token',
  'token is required',
  'token is empty',
  'token has expired',
  'token signature verification failed',
  'no valid oidc token found',
  'token audience validation failed',
  'token issuer mismatch',
  'token is not yet valid',
  'token processing error',
];

function goLogin() {
  const url = window.location.href;
  localStorage.removeItem('token');
  const base_url = url.split('#')[0];
  window.location.href = `${base_url}#/login`;
}

function getContextPath() {
  return window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
}

const request = () => {
  const instance = axios.create({
    baseURL: getContextPath(),
  });

  instance.interceptors.request.use(
    config => {
      const { url, params, data, method, headers } = config;
      if (!params) {
        config.params = {};
      }
      if (!url.includes('auth/user/login') && localStorage.token) {
        let token = {};
        try {
          token = JSON.parse(localStorage.token);
        } catch (e) {
          console.log(e);
          goLogin();
        }
        const { accessToken = '', username = '' } = token;
        // support #3548 and fix #5835
        if (!url.includes('auth')) {
          config.params.username = username;
        }
        config.headers = Object.assign({}, headers, { accessToken });
      }
      if (data && isPlainObject(data) && ['post', 'put'].includes(method)) {
        config.data = qs.stringify(data);
        if (!headers) {
          config.headers = {};
        }
        config.headers['Content-Type'] = 'application/x-www-form-urlencoded';
      }
      return config;
    },
    error => Promise.reject(error)
  );

  instance.interceptors.response.use(
    response => {
      const { success, resultCode, resultMessage = API_GENERAL_ERROR_MESSAGE } = response.data;
      // if (!success && resultCode !== SUCCESS_RESULT_CODE) {
      //   Message.error(resultMessage);
      //   return Promise.reject(new Error(resultMessage));
      // }
      if (response.config && response.config.url === 'v3/console/server/state') {
        const { auth_admin_request = '' } = response.data;
        if (auth_admin_request && auth_admin_request === 'true') {
          goRegister();
        }
        if (
          auth_admin_request &&
          auth_admin_request === 'false' &&
          window.location.hash.includes('/register')
        ) {
          goLogin();
        }
      }
      // Handle session expired in success responses (HTTP 200 with business error code).
      if (
        response.data &&
        typeof response.data === 'object' &&
        response.data.code &&
        response.data.code !== 0
      ) {
        const msg = (response.data.message || '').toLowerCase();
        const dataField = (typeof response.data.data === 'string'
          ? response.data.data
          : ''
        ).toLowerCase();
        const combined = msg + ' ' + dataField;
        if (SESSION_EXPIRED_MESSAGES.some(m => combined.includes(m))) {
          goLogin();
          return Promise.reject(new Error('session expired'));
        }
      }
      return response.data;
    },
    error => {
      if (error.response) {
        const { data = {}, status } = error.response;
        let message = `HTTP ERROR: ${status}`;
        if (typeof data === 'string') {
          message = data;
        } else if (typeof data === 'object') {
          message = data.message;
        }
        toastError(message);

        const dataStr = typeof data === 'string' ? data : data.data || '';
        const isSessionExpired = SESSION_EXPIRED_MESSAGES.some(
          msg =>
            (message && message.toLowerCase().includes(msg)) ||
            (dataStr && dataStr.toLowerCase().includes(msg))
        );
        if ([401, 403].includes(status) && isSessionExpired) {
          goLogin();
        }
        return Promise.reject(error.response);
      }
      toastError(API_GENERAL_ERROR_MESSAGE);
      return Promise.reject(error);
    }
  );

  return instance;
};

export default request();
