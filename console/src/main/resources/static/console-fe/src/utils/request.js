import axios from 'axios';
import qs from 'qs';
import { Message } from '@alifd/next';
import { browserHistory } from 'react-router';
// import { SUCCESS_RESULT_CODE } from '../constants';

const API_GENERAL_ERROR_MESSAGE = 'Request error, please try again later!';

const request = () => {
  const instance = axios.create();

  instance.interceptors.request.use(
    config => {
      if (!config.params) {
        config.params = {};
      }
      if (!config.url.includes('auth/users/login')) {
        const { accessToken = '' } = JSON.parse(localStorage.token || '{}');
        config.params.accessToken = accessToken;
        config.headers = Object.assign({}, config.headers, { accessToken });
      }
      if (['post', 'put'].includes(config.method)) {
        config.data = qs.stringify(config.data);
        if (!config.headers) {
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
        const { data, status } = error.response;
        if (status === 403) {
          localStorage.removeItem('token');
          const [baseUrl] = location.href.split('#');
          location.href = `${baseUrl}#/login`;
          return Promise.reject(error);
        }
        Message.error(data && typeof data === 'string' ? data : `HTTP ERROR: ${status}`);
      } else {
        Message.error(API_GENERAL_ERROR_MESSAGE);
      }
    }
  );

  return instance;
};

export default request();
