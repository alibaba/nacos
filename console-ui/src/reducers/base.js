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

import request from '../utils/request';
import { GET_STATE, LOGINPAGE_ENABLED, GET_NOTICE } from '../constants';

const initialState = {
  version: null,
  standaloneMode: '',
  functionMode: '',
  loginPageEnabled: '',
  authEnabled: '',
  notice: '',
};

/**
 * 用户登录
 * @param {*} param0
 */
const login = user => request.post('v1/auth/users/login', user);

const getState = () => dispatch =>
  request
    .get('v1/console/server/state')
    .then(res => {
      localStorage.setItem(LOGINPAGE_ENABLED, res.login_page_enabled);
      dispatch({
        type: GET_STATE,
        data: {
          version: res.version,
          standaloneMode: res.standalone_mode,
          functionMode: res.function_mode,
          loginPageEnabled: res.login_page_enabled,
          authEnabled: res.auth_enabled,
        },
      });
    })
    .catch(() => {
      localStorage.setItem(LOGINPAGE_ENABLED, null);
      dispatch({
        type: GET_STATE,
        data: {
          version: null,
          functionMode: null,
          loginPageEnabled: null,
          authEnabled: null,
        },
      });
    });

const getNotice = () => dispatch =>
  request
    .get('v1/console/server/announcement')
    .then(res => {
      dispatch({
        type: GET_NOTICE,
        data: {
          notice: res.data,
        },
      });
    })
    .catch(() => {
      dispatch({
        type: GET_NOTICE,
        data: {
          notice: '',
        },
      });
    });

export default (state = initialState, action) => {
  switch (action.type) {
    case GET_STATE:
      return { ...state, ...action.data };
    case GET_NOTICE:
      return { ...state, ...action.data };
    default:
      return state;
  }
};

export { getState, login, getNotice };
