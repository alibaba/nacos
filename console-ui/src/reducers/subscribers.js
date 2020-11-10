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
import { GET_SUBSCRIBERS, REMOVE_SUBSCRIBERS } from '../constants';

const initialState = {
  subscribers: {},
};

const getSubscribers = params => dispatch =>
  request.get('v1/ns/service/subscribers', { params }).then(data => {
    dispatch({
      type: GET_SUBSCRIBERS,
      data,
    });
  });
const removeSubscribers = () => dispatch => dispatch({ type: REMOVE_SUBSCRIBERS });

export default (state = initialState, action) => {
  switch (action.type) {
    case GET_SUBSCRIBERS:
      return { ...state, ...action.data };
    case REMOVE_SUBSCRIBERS:
      return { ...state, subscribers: {} };
    default:
      return state;
  }
};

export { getSubscribers, removeSubscribers };
