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
import { GET_STATE, LOGINPAGE_ENABLED, GET_NOTICE, SERVER_GUIDE } from '../constants';

const initialState = {
  version: null,
  standaloneMode: '',
  functionMode: '',
  loginPageEnabled: '',
  authEnabled: '',
  notice: '',
  consoleUiEnable: '',
  guideMsg: '',
};

/**
 * 用户登录
 * @param {*} param0
 */
const login = user => request.post('v1/auth/users/login', user);

/**
 * 单独在login处调用 获取提示信息
 */
const guide = () => request.get('v1/console/server/guide');

/**
 * 单独在login调用 判断是否可以登陆
 */
const state = () => request.get('v1/console/server/state');

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
          consoleUiEnable: res.console_ui_enabled,
          startupMode: res.startup_mode,
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
          consoleUiEnable: null,
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

const getGuide = () => dispatch =>
  request
    .get('v1/console/server/guide')
    .then(res => {
      dispatch({
        type: SERVER_GUIDE,
        data: {
          guideMsg: res.data,
        },
      });
    })
    .catch(() => {
      dispatch({
        type: SERVER_GUIDE,
        data: {
          guideMsg: '',
        },
      });
    });

export default (state = initialState, action) => {
  switch (action.type) {
    case GET_STATE:
      return { ...state, ...action.data };
    case GET_NOTICE:
      return { ...state, ...action.data };
    case SERVER_GUIDE:
      return { ...state, ...action.data };
    default:
      return state;
  }
};

export { getState, login, getNotice, getGuide, guide, state };
