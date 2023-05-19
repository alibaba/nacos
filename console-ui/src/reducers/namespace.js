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
import { GET_NAMESPACES } from '../constants';

const initialState = {
  namespaces: [],
};

const getNamespaces = params => dispatch =>
  request.get('v1/console/namespaces', { params }).then(response => {
    const { code, data } = response;
    dispatch({
      type: GET_NAMESPACES,
      data: code === 200 ? data : [],
    });
  });

export default (state = initialState, action) => {
  switch (action.type) {
    case GET_NAMESPACES:
      return { ...state, namespaces: action.data };
    default:
      return state;
  }
};

export { getNamespaces };
