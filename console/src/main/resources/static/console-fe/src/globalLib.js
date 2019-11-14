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

import projectConfig from './config';
import moment from 'moment';
import $ from 'jquery';
import i18DocObj from './i18ndoc';

const global = window;

/**
 * 获取cookie值
 * @param {*String} keyName cookie名
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
 * 监听事件对象
 */
const nacosEvent = (function(_global) {
  const eventListObj = {};
  const ignoreEventListObj = {};
  return {
    /**
     * 只监听一次
     */
    once(eventName, callback) {
      this.listen.call(this, eventName, callback, true);
    },
    /**
     * 监听事件<eventName: String 监听事件名, callback: function 回调函数, once: boolean 是否监听一次>
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
     * 监听事件, 之前未消费的消息也会进行触发<eventName: String 监听事件名, callback: function 回调函数, once: boolean 是否监听一次>
     */
    listenAllTask(...args) {
      const self = this;
      const argsList = Array.prototype.slice.call(args);
      const eventName = argsList[0];

      if (!eventName) {
        return;
      }
      // 监听事件
      self.listen(...argsList);

      // 判断是否有未消费的消息
      if (ignoreEventListObj[eventName] && ignoreEventListObj[eventName].length > 0) {
        const eventObj = ignoreEventListObj[eventName].pop();
        self.trigger.apply(eventObj.self, eventObj.argsList);
      }
    },
    /**
     * 触发事件
     */
    trigger(...args) {
      const self = this;
      const argsList = Array.prototype.slice.call(args);
      const eventName = argsList.shift();
      // 如果还没有订阅消息, 将其放到未消费队列里
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
        // 删除只触发一次的事件
        if (!_obj.once) {
          newList.push(_obj);
        }
      });
      eventListObj[eventName] = newList;
    },
    /**
     * 删除监听事件
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
 * nacos的工具类
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
     * 打开loading效果
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
     * 尝试关闭loading, 只有当loadingCount小于0时才会关闭loading效果
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
     * 关闭loading效果
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
     * 获取资源地址, 如果资源需要静态化输出 请调用此方法
     */
    getURISource(url) {
      return url;
    },
  };
})(global);

const aliwareIntl = (function(_global) {
  /**
   * 国际化构造方法
   * @param {Object} options 配置信息
   */
  function AliwareI18n(options) {
    // let currentLocal = options.currentLocal || navigator.language || navigator.userLanguage;

    const nowData = options.locals;
    this.nowData = nowData;
    this.setMomentLocale(this.currentLanguageCode);
  }

  let aliwareLocal = aliwareGetCookieByKeyName('aliyun_lang') || 'zh';
  let aliwareLocalSite = aliwareGetCookieByKeyName('aliyun_country') || 'cn';
  aliwareLocal = aliwareLocal.toLowerCase();
  aliwareLocalSite = aliwareLocalSite.toLowerCase();
  // 当前语言
  AliwareI18n.prototype.currentLocal = aliwareLocal;
  // 当前地区
  AliwareI18n.prototype.currentSite = aliwareLocalSite;
  // 当前语言-地区
  AliwareI18n.prototype.currentLanguageCode =
    aliwareGetCookieByKeyName('docsite_language') || `${aliwareLocal}-${aliwareLocalSite}`;
  /**
   * 通过key获取对应国际化文案
   * @param {String} key 国际化key
   */
  AliwareI18n.prototype.get = function(key) {
    return this.nowData[key];
  };
  /**
   * 修改国际化文案数据
   * @param {String} local 语言信息
   */
  AliwareI18n.prototype.changeLanguage = function(local) {
    this.nowData = i18DocObj[local] || {};
  };
  /**
   * 数字国际化
   * @param {Number} num 数字
   */
  AliwareI18n.prototype.intlNumberFormat = function(num) {
    if (typeof Intl !== 'object' || typeof Intl.NumberFormat !== 'function') {
      return num;
    }
    try {
      return new Intl.NumberFormat(this.currentLanguageCode).format(num || 0);
    } catch (error) {
      return num;
    }
  };
  /**
   * 时间戳格式化
   * @param {Number} num 时间戳
   * @param {Object} initOption 配置信息
   */
  AliwareI18n.prototype.intlTimeFormat = function(num = Date.now(), initOption = {}) {
    try {
      const date = Object.prototype.toString.call(num) === '[object Date]' ? num : new Date(num);
      const options = Object.assign(
        {},
        {
          // weekday: "short",
          hour12: false,
          year: 'numeric',
          month: 'short',
          day: 'numeric',
          hour: 'numeric',
          minute: 'numeric',
          second: 'numeric',
        },
        initOption
      );
      return date.toLocaleDateString(this.currentLanguageCode, options);
    } catch (error) {
      return typeof moment === 'function' ? moment(num).format() : '--';
    }
  };
  /**
   * 获取当前时间格式
   * @param {String} language 语言信息: zh/en
   */
  AliwareI18n.prototype.getIntlTimeFormat = function(_language) {
    const language = _language || aliwareLocal;
    const langObj = {
      zh: 'YYYY年M月D日 HH:mm:ss',
      en: 'MMM D, YYYY, h:mm:ss A',
      default: 'YYYY-MM-DD HH:mm:ss',
    };
    return langObj[language] ? langObj[language] : langObj.default;
  };
  /**
   * 设置moment的locale
   * @param {String} languageCode 语言信息: zh-ch/en-us
   */
  AliwareI18n.prototype.setMomentLocale = function(languageCode) {
    if (Object.prototype.toString.call(moment) === '[object Function]') {
      moment.locale(languageCode || this.currentLanguageCode);
      return true;
    }
    return false;
  };

  return new AliwareI18n({
    currentLocal: `${aliwareLocal}`,
    locals:
      i18DocObj[AliwareI18n.prototype.currentLanguageCode] ||
      i18DocObj['en-us'] ||
      i18DocObj['zh-cn'] ||
      {},
  });
})(global);

/**
 * 获取url中的参数
 */
const getParams = (function(_global) {
  return function(name) {
    const reg = new RegExp(`(^|&)${name}=([^&]*)(&|$)`, 'i');
    let result = [];
    if (_global.location.hash !== '') {
      result = _global.location.hash.split('?'); // 优先判别hash
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
 * 设置参数
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
 * 设置参数
 */
const setParam = function(...args) {
  return setParams.apply(this, args);
};

/**
 * 删除参数
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
 * 封装的ajax请求
 */
const request = (function(_global) {
  const middlewareList = [];
  const middlewareBackList = [];
  const serviceMap = {};
  const serviceList = [];
  const methodList = [];
  /**
   * 获取真实url信息
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
      // 获取正确请求方式
      serviceObj.methodType = methodList[serviceObj.method];
      return serviceObj;
    };
  })();

  /**
   * 添加中间件函数
   * @param {*function} callback 回调函数
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
   * 处理中间件
   * @param {*Object} config ajax请求配置信息
   */
  function handleMiddleWare(...allArgs) {
    // 获取除config外传入的参数
    let [config, ...args] = allArgs;
    // 最后一个参数为middlewareList
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
   * 处理自定义url
   * @param {*Object} config ajax请求配置信息
   */
  function handleCustomService(...args) {
    let [config] = args;
    // 只处理com.alibaba.开头的url
    if (config && config.url && config.url.indexOf('com.alibaba.') === 0) {
      const registerName = config.url;
      const serviceObj = NacosRealUrlMapper(registerName);
      if (serviceObj && serviceObj.url && serviceObj.url.replace) {
        // 有mock数据 直接返回 生产环境失效
        if (projectConfig.is_preview && serviceObj.is_mock && config.success) {
          let code = null;
          try {
            code = JSON.parse(serviceObj.defaults);
          } catch (error) {}
          config.success(code);
          return;
        }
        // 替换url中的占位符
        config.url = serviceObj.url.replace(/{([^\}]+)}/g, ($1, $2) => config.$data[$2]);
        try {
          // 添加静态参数
          if (serviceObj.is_param && typeof config.data === 'object') {
            config.data = Object.assign({}, JSON.parse(serviceObj.params), config.data);
          }
        } catch (e) {}
        // 替换请求方式
        if (serviceObj.method && !config.type) {
          config.type = serviceObj.methodType;
        }
        // 将请求参数变为json格式
        if (serviceObj.isJsonData && typeof config.data === 'object') {
          config.data = JSON.stringify(config.data);
          config.processData = false;
          config.dataType = 'json';
          config.contentType = 'application/json';
        }
        try {
          // 设置临时代理 生产环境失效
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
        // 设置自动loading效果
        if (serviceObj.autoLoading) {
          nacosUtils.openLoading();
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

  function Request(...allArgs) {
    // 除了config外的传参
    let [config, ...args] = allArgs;
    // 处理前置中间件
    config = handleMiddleWare.apply(this, [config, ...args, middlewareList]);
    // 处理自定义url
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

    // 处理后置中间件
    config = handleMiddleWare.apply(this, [config, ...args, middlewareBackList]);

    return $.ajax(
      Object.assign({}, config, {
        type: config.type,
        url: config.url,
        data: config.data || '',
        dataType: config.dataType || 'json',
        beforeSend(xhr) {
          config.beforeSend && config.beforeSend(xhr);
        },
        headers: {
          Authorization: localStorage.getItem('token'),
        },
      })
    ).then(
      success => {},
      error => {
        // 处理403 forbidden
        if (error && (error.status === 403 || error.status === 401)) {
          // 跳转至login页
          // TODO: 用 react-router 重写，改造成本比较高，这里先hack
          const url = window.location.href;
          // TODO: 后端返回细致的错误码，如果原始密码不对 不应该直接跳到登陆页
          if (url.includes('password')) {
            return;
          }
          const base_url = url.split('#')[0];
          window.location = `${base_url}#/login`;
        }
      }
    );
  }

  // 暴露方法
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
  aliwareIntl,
  getParams,
  setParam,
  setParams,
  removeParams,
  request,
};
