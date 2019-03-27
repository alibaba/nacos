/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import fusionEnUS from '@alifd/next/lib/locale/en-us';
import fusionZhCN from '@alifd/next/lib/locale/zh-cn';
import I18N from '../locales';
import { LANGUAGE_KEY, LANGUAGE_SWITCH } from '../constants';

const enUS = Object.assign({}, fusionEnUS, I18N.enUS);
const zhCN = Object.assign({}, fusionZhCN, I18N.zhCN);

const initialState = {
  language: 'en-us',
  locale: enUS,
};

const changeLanguage = lang => dispatch => {
  const language = lang === 'zh-CN' ? 'zh-CN' : 'en-US';
  localStorage.setItem(LANGUAGE_KEY, language);
  dispatch({ type: LANGUAGE_SWITCH, language, locale: language === 'zh-CN' ? zhCN : enUS });
};

export default (state = initialState, action) => {
  switch (action.type) {
    case LANGUAGE_SWITCH:
      return { ...state, ...action };
    default:
      return state;
  }
};

export { changeLanguage };
