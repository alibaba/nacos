import axios from 'axios';
import qs from 'qs';
import { Message } from '@alifd/next';
import { browserHistory } from 'react-router';
import { isPlainObject } from './nacosutil';
// import { SUCCESS_RESULT_CODE } from '../constants';

const API_GENERAL_ERROR_MESSAGE = 'Request error, please try again later!';

function goLogin() {
  const url = window.location.href;
  localStorage.removeItem('token');
  const base_url = url.split('#')[0];
  window.location.href = `${base_url}#/login`;
}

const request = () => {
  const instance = axios.create();

  instance.interceptors.request.use(
    config => {
      const { url, params, data, method, headers } = config;
      if (!params) {
        config.params = {};
      }
      if (!url.includes('auth/users/login')) {
        let token = {};
        try {
          token = JSON.parse(localStorage.token);
        } catch (e) {
          console.log(e);
          goLogin();
        }
        const { accessToken = '' } = token;
        config.params.accessToken = accessToken;
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
        Message.error(message);

        if (
          [401, 403].includes(status) &&
          ['unknown user!', 'token invalid', 'token expired!'].includes(message)
        ) {
          goLogin();
        }
        return Promise.reject(error.response);
      }
      Message.error(API_GENERAL_ERROR_MESSAGE);
      return Promise.reject(error);
    }
  );

  return instance;
};

export default request();
