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

import projectConfig from './config';
import $ from 'jquery';
import { Message } from '@alifd/next';
import { LOGINPAGE_ENABLED } from './constants';

function goLogin() {
  const url = window.location.href;
  localStorage.removeItem('token');
  const base_url = url.split('#')[0];
  console.log('base_url', base_url);
  window.location = `${base_url}#/login`;
}

function goRegister() {
  const url = window.location.href;
  localStorage.removeItem('token');
  const base_url = url.split('#')[0];
  window.location = `${base_url}#/register`;
}

function generateRandomPassword(length) {
  const charset = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let password = '';
  for (let i = 0; i < length; i++) {
    const randomIndex = Math.floor(Math.random() * charset.length);
    password += charset[randomIndex];
  }
  return password;
}

const global = window;

/**
 * Get cookie value
 * @param {*String} keyName cookie name
 */
const aliwareGetCookieByKeyName = function(keyName) {
  let result = '';
  const cookieList = (document.cookie && document.cookie.split(';')) || [];
  cookieList.forEach(str => {
    const [name = '', value = ''] = str.split('=') || [];
    if (name.trim().indexOf(keyName) !== -1) {
      result = value;
    }
  });

  return result.trim();
};

/**
 * Event listener object
 */
const nacosEvent = (function(_global) {
  const eventListObj = {};
  const ignoreEventListObj = {};
  return {
    /**
     * Listen only once
     */
    once(eventName, callback) {
      this.listen.call(this, eventName, callback, true);
    },
    /**
     * Listen to event <eventName: String event name, callback: function callback function, once: boolean whether to listen only once>
     */
    listen(eventName, callback, once = false) {
      if (!eventName || !callback) {
        return;
      }
      !eventListObj[eventName] && (eventListObj[eventName] = []);
      eventListObj[eventName].push({
        callback,
        once,
      });
    },
    /**
     * Listen to event, previously unconsumed messages will also be triggered <eventName: String event name, callback: function callback function, once: boolean whether to listen only once>
     */
    listenAllTask(...args) {
      const self = this;
      const argsList = Array.prototype.slice.call(args);
      const eventName = argsList[0];

      if (!eventName) {
        return;
      }
      // Listen to event
      self.listen(...argsList);

      // Check if there are unconsumed messages
      if (ignoreEventListObj[eventName] && ignoreEventListObj[eventName].length > 0) {
        const eventObj = ignoreEventListObj[eventName].pop();
        self.trigger.apply(eventObj.self, eventObj.argsList);
      }
    },
    /**
     * Trigger event
     */
    trigger(...args) {
      const self = this;
      const argsList = Array.prototype.slice.call(args);
      const eventName = argsList.shift();
      // If no subscription yet, put it in the unconsumed queue
      if (!eventListObj[eventName]) {
        !ignoreEventListObj[eventName] && (ignoreEventListObj[eventName] = []);
        ignoreEventListObj[eventName].push({
          argsList: Array.prototype.slice.call(args),
          self,
        });
        return;
      }

      const newList = [];
      eventListObj[eventName].forEach((_obj, index) => {
        if (Object.prototype.toString.call(_obj.callback) !== '[object Function]') {
          return;
        }
        _obj.callback.apply(self, argsList);
        // Remove events that only trigger once
        if (!_obj.once) {
          newList.push(_obj);
        }
      });
      eventListObj[eventName] = newList;
    },
    /**
     * Remove event listener
     */
    remove(eventName, callback) {
      if (!eventName || !eventListObj[eventName]) {
        return;
      }
      if (!callback) {
        eventListObj[eventName] = null;
      } else {
        const newList = [];
        eventListObj[eventName].forEach((_obj, index) => {
          if (_obj.callback !== callback) {
            newList.push(_obj);
          }
        });
        eventListObj[eventName] = newList.length ? newList : null;
      }
    },
  };
})(global);

/**
 * Nacos utility class
 */
const nacosUtils = (function(_global) {
  let loadingCount = 0;
  let loadingState = {
    visible: false,
    shape: 'flower',
    tip: 'loading...',
    // color: "#333",
    // style: { height: "100%", width: "100%" }
  };
  return {
    /**
     * 改变loading 的样式
     */
    changeLoadingAttr(obj) {
      if (Object.prototype.toString.call(obj) === '[object Object]') {
        loadingState = Object.assign({}, loadingCount, obj);
      }
    },
    /**
     * Open loading effect
     */
    openLoading() {
      loadingCount++;
      nacosEvent.trigger(
        'nacosLoadingEvent',
        Object.assign(loadingState, {
          visible: true,
          spinning: true,
        })
      );
    },
    /**
     * Attempt to close loading, only close when loadingCount is less than 0
     */
    closeLoading() {
      loadingCount--;
      if (loadingCount <= 0) {
        loadingCount = 0;
        nacosEvent.trigger(
          'nacosLoadingEvent',
          Object.assign(loadingState, {
            visible: false,
            spinning: false,
          })
        );
      }
    },
    /**
     * Close loading effect
     */
    closeAllLoading() {
      loadingCount = 0;
      nacosEvent.trigger(
        'nacosLoadingEvent',
        Object.assign(loadingState, {
          visible: false,
          spinning: false,
        })
      );
    },
    /**
     * Get resource address, call this method if resource needs static output
     */
    getURISource(url) {
      return url;
    },
  };
})(global);

/**
 * Get parameters from url
 */
const getParams = (function(_global) {
  return function(name) {
    const reg = new RegExp(`(^|&)${name}=([^&]*)(&|$)`, 'i');
    let result = [];
    if (_global.location.hash !== '') {
      result = _global.location.hash.split('?'); // Prioritize hash
    } else {
      result = _global.location.href.split('?');
    }

    if (result.length === 1) {
      result = _global.parent.location.hash.split('?');
    }

    if (result.length > 1) {
      const r = result[1].match(reg);
      if (r != null) {
        return decodeURIComponent(r[2]);
      }
    }

    return null;
  };
})(global);

/**
 * Set parameters
 */
const setParams = (function(global) {
  let _global = global;
  const _originHref = _global.location.href.split('#')[0];
  return function(name, value) {
    if (!name) {
      return;
    }

    let obj = {};
    if (typeof name === 'string') {
      obj = {
        [name]: value,
      };
    }

    if (Object.prototype.toString.call(name) === '[object Object]') {
      obj = name;
    }

    let hashArr = [];
    if (_global.location.hash) {
      hashArr = _global.location.hash.split('?');
    }

    const paramArr = (hashArr[1] && hashArr[1].split('&')) || [];

    let paramObj = {};
    paramArr.forEach(val => {
      const tmpArr = val.split('=');
      paramObj[tmpArr[0]] = decodeURIComponent(tmpArr[1] || '');
    });
    paramObj = Object.assign({}, paramObj, obj);

    const resArr =
      Object.keys(paramObj).map(key => `${key}=${encodeURIComponent(paramObj[key] || '')}`) || [];

    hashArr[1] = resArr.join('&');
    const hashStr = hashArr.join('?');
    if (_global.history.replaceState) {
      const url = _originHref + hashStr;
      _global.history.replaceState(null, '', url);
    } else {
      _global.location.hash = hashStr;
    }
  };
})(global);

/**
 * Set parameter
 */
const setParam = function(...args) {
  return setParams.apply(this, args);
};

/**
 * Remove parameters
 */
const removeParams = (function(global) {
  let _global = global;
  const _originHref = _global.location.href.split('#')[0];
  return function(name) {
    let removeList = [];

    const nameType = Object.prototype.toString.call(name);
    if (nameType === '[object String]') {
      removeList.push(name);
    } else if (nameType === '[object Array]') {
      removeList = name;
    } else if (nameType === '[object Object]') {
      removeList = Object.keys(name);
    } else {
      return;
    }

    let hashArr = [];
    if (_global.location.hash) {
      hashArr = _global.location.hash.split('?');
    }

    let paramArr = (hashArr[1] && hashArr[1].split('&')) || [];

    // let paramObj = {};
    paramArr = paramArr.filter(val => {
      const tmpArr = val.split('=');
      return removeList.indexOf(tmpArr[0]) === -1;
    });

    hashArr[1] = paramArr.join('&');
    const hashStr = hashArr.join('?');
    if (_global.history.replaceState) {
      const url = _originHref + hashStr;
      _global.history.replaceState(null, '', url);
    } else {
      _global.location.hash = hashStr;
    }
  };
})(global);

/**
 * Wrapped ajax request
 */
const request = (function(_global) {
  const middlewareList = [];
  const middlewareBackList = [];
  const serviceMap = {};
  const serviceList = [];
  const methodList = [];
  /**
   * Get real url information
   */
  const NacosRealUrlMapper = (function() {
    serviceList.forEach(obj => {
      serviceMap[obj.registerName] = obj;
    });
    return function(registerName) {
      const serviceObj = serviceMap[registerName];
      if (!serviceObj) {
        return null;
      }
      // Get correct request method
      serviceObj.methodType = methodList[serviceObj.method];
      return serviceObj;
    };
  })();

  /**
   * Add middleware function
   * @param {*function} callback callback function
   */
  function middleWare(callback, isBack = true) {
    if (isBack) {
      middlewareBackList.push(callback);
    } else {
      middlewareList.push(callback);
    }
    return this;
  }

  /**
   * Handle middleware
   * @param {*Object} config ajax request configuration
   */
  function handleMiddleWare(...allArgs) {
    // Get parameters passed except config
    let [config, ...args] = allArgs;
    // Last parameter is middlewareList
    const middlewareList = args.pop() || [];
    if (middlewareList && middlewareList.length > 0) {
      config = middlewareList.reduce((config, callback) => {
        if (typeof callback === 'function') {
          return callback.apply(this, [config, ...args]) || config;
        }
        return config;
      }, config);
    }
    return config;
  }

  /**
   * Handle custom url
   * @param {*Object} config ajax request configuration
   */
  function handleCustomService(...args) {
    let [config] = args;
    // Only process urls starting with com.alibaba.
    if (config && config.url && config.url.indexOf('com.alibaba.') === 0) {
      const registerName = config.url;
      const serviceObj = NacosRealUrlMapper(registerName);
      if (serviceObj && serviceObj.url && serviceObj.url.replace) {
        // Has mock data, return directly, invalid in production
        if (projectConfig.is_preview && serviceObj.is_mock && config.success) {
          let code = null;
          try {
            code = JSON.parse(serviceObj.defaults);
          } catch (error) {}
          config.success(code);
          return;
        }
        // Replace placeholders in url
        config.url = serviceObj.url.replace(/{([^\}]+)}/g, ($1, $2) => config.$data[$2]);
        try {
          // Add static parameters
          if (serviceObj.is_param && typeof config.data === 'object') {
            config.data = Object.assign({}, JSON.parse(serviceObj.params), config.data);
          }
        } catch (e) {}
        // Replace request method
        if (serviceObj.method && !config.type) {
          config.type = serviceObj.methodType;
        }
        // Convert request parameters to JSON format
        if (serviceObj.isJsonData && typeof config.data === 'object') {
          config.data = JSON.stringify(config.data);
          config.processData = false;
          config.dataType = 'json';
          config.contentType = 'application/json';
        }
        try {
          // Set temporary proxy, invalid in production
          if (projectConfig.is_preview && serviceObj.is_proxy) {
            const { beforeSend } = config;
            config.beforeSend = function(xhr) {
              serviceObj.cookie && xhr.setRequestHeader('tmpCookie', serviceObj.cookie);
              serviceObj.header && xhr.setRequestHeader('tmpHeader', serviceObj.header);
              serviceObj.proxy && xhr.setRequestHeader('tmpProxy', serviceObj.proxy);
              beforeSend && beforeSend(xhr);
            };
          }
        } catch (e) {}
        // Set auto loading effect
        if (serviceObj.autoLoading) {
          // nacosUtils.openLoading();
          const prevComplete = config.complete;
          config.complete = function() {
            nacosUtils.closeLoading();
            typeof prevComplete === 'function' &&
              prevComplete.apply($, Array.prototype.slice.call(args));
          };
        }
        // serviceObj = null;
      }
    }
    return config;
  }

  async function Request(...allArgs) {
    // Parameters other than config
    let [config, ...args] = allArgs;
    // Handle pre-middleware
    config = handleMiddleWare.apply(this, [config, ...args, middlewareList]);
    // Handle custom url
    config = handleCustomService.apply(this, [config, ...args]);
    if (!config) return;
    // xsrf
    if (
      config.type &&
      config.type.toLowerCase() === 'post' &&
      config.data &&
      Object.prototype.toString.call(config.data) === '[object Object]' &&
      !config.data.sec_token
    ) {
      const sec_token = aliwareGetCookieByKeyName('XSRF-TOKEN');
      sec_token && (config.data.sec_token = sec_token);
    }

    // Handle post-middleware
    config = handleMiddleWare.apply(this, [config, ...args, middlewareBackList]);

    const [url, paramsStr] = config.url.split('?');
    const params = paramsStr ? paramsStr.split('&') : [];

    const _LOGINPAGE_ENABLED = localStorage.getItem(LOGINPAGE_ENABLED);

    let accessTokenInHeader = '';
    if (_LOGINPAGE_ENABLED !== 'false') {
      let token = {};
      try {
        token = JSON.parse(localStorage.token);
      } catch (e) {
        console.log('Token Error', localStorage.token, e);
        goLogin();
      }
      const { accessToken = '' } = token;
      accessTokenInHeader = accessToken;
    }

    return $.ajax(
      Object.assign({}, config, {
        type: config.type,
        url: [url, params.join('&')].join('?'),
        data: config.data || '',
        dataType: config.dataType || 'json',
        beforeSend(xhr) {
          config.beforeSend && config.beforeSend(xhr);
        },
        headers: {
          Authorization: localStorage.getItem('token') || undefined,
          AccessToken: accessTokenInHeader,
        },
      })
    ).then(
      success => {
        return success;
      },
      error => {
        // Handle 403 forbidden
        const { status, responseJSON = {} } = error || {};
        if (responseJSON.message) {
          const _errorcontent = responseJSON?.data ? ` : ${responseJSON.data}` : '';
          Message.error(responseJSON.message + _errorcontent);
        }
        const shouldRedirectToLogin =
          [401, 403].includes(status) &&
          typeof responseJSON.message === 'string' &&
          /(token\s*(invalid|expired)|unknown\s*user)/i.test(responseJSON.message);
        if (shouldRedirectToLogin) {
          goLogin();
        }
        return error;
      }
    );
  }

  // Expose methods
  Request.handleCustomService = handleCustomService;
  Request.handleMiddleWare = handleMiddleWare;
  Request.NacosRealUrlMapper = NacosRealUrlMapper;
  Request.serviceList = serviceList;
  Request.serviceMap = serviceMap;
  Request.middleWare = middleWare;

  return Request;
})(global);

export {
  nacosEvent,
  nacosUtils,
  aliwareGetCookieByKeyName,
  removeParams,
  getParams,
  setParam,
  setParams,
  goLogin,
  goRegister,
  generateRandomPassword,
  request,
};
