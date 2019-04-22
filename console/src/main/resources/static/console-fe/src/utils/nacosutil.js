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

/* eslint-disable import/prefer-default-export */
export const throttle = (fn, delay) => {
  let timer = null;
  return function(...args) {
    const context = this;
    clearTimeout(timer);
    timer = setTimeout(() => {
      fn.apply(context, args);
    }, delay);
  };
};

export const getScrollTop = () => {
  let scrollTop = 0;
  if (document.documentElement && document.documentElement.scrollTop) {
    ({ scrollTop } = document.documentElement);
  } else if (document.body) {
    ({ scrollTop } = document.body);
  }
  return scrollTop;
};

export const getLink = link => {
  if (`${link}`.length > 1 && /^\/[^/]/.test(`${link}`)) {
    return `${window.rootPath}${link}`;
  }
  return link;
};

export const getParameter = (search, name) => {
  const [, query = ''] = search.split('?');
  const [hit = ''] = query.split('&').filter(item => name === item.split('=')[0]);
  const [, value = ''] = hit.split('=');
  return value;
};

/**
 * 将回车符和空格替换
 * @param {*} separator 替换符
 */
export const replaceEnter = (separator = ',') => text => {
  if (typeof text !== 'string') {
    return text;
  }

  return text
    .replace(/\r\n/g, separator)
    .replace(/[\r\n]/g, separator)
    .replace(/[\t\s]/g, '');
};

/**
 * 处理metaData对象生成可显示对象
 */
export const processMetaData = (separator = ',') => (metadata = {}) => {
  if (Object.prototype.toString.call(metadata) !== '[object Object]') {
    return '';
  }

  return Object.keys(metadata)
    .map(key => `${key}=${metadata[key]}`)
    .join(separator);
};
