import projectConfig from './config';
import serviceConfig from './serviceMock';
import moment from 'moment';
import $ from 'jquery';

/**
 * 获取cookie值
 * @param {*String} keyName cookie名
 */
window.aliwareGetCookieByKeyName = function (keyName) {
    let result = '';
    let cookieList = document.cookie && document.cookie.split(';') || [];
    cookieList.forEach((str) => {
        let tmpList = str.split('=') || [];
        if (tmpList[0].trim().indexOf(keyName) !== -1) {
            result = tmpList[1];
        }
    })

    return result.trim();
};
/** 
 * 监听事件对象
 */
window.narutoEvent = (function (window) {
    let eventListObj = {};
    let ignoreEventListObj = {};
    return {
        /**
         * 只监听一次
         */
        once: function (eventName, callback) {
            this.listen.call(this, eventName, callback, true);
        },
        /**
         * 监听事件<eventName: String 监听事件名, callback: function 回调函数, once: boolean 是否监听一次>
         */
        listen: function (eventName, callback, once = false) {
            if (!eventName || !callback) {
                return;
            }
            !eventListObj[eventName] && (eventListObj[eventName] = []);
            eventListObj[eventName].push({
                callback: callback,
                once: once
            });
        },
        /**
         * 监听事件, 之前未消费的消息也会进行触发<eventName: String 监听事件名, callback: function 回调函数, once: boolean 是否监听一次>
         */
        listenAllTask: function () {
            const self = this;
            const argsList = Array.prototype.slice.call(arguments);
            const eventName = argsList[0];

            if (!eventName) {
                return;
            }
            //监听事件
            self.listen.apply(self, argsList);

            //判断是否有未消费的消息
            if (ignoreEventListObj[eventName] && ignoreEventListObj[eventName].length > 0) {
                const eventObj = ignoreEventListObj[eventName].pop();
                self.trigger.apply(eventObj.self, eventObj.argsList);
            }
        },
        /**
         * 触发事件
         */
        trigger: function () {
            const self = this;
            let argsList = Array.prototype.slice.call(arguments);
            const eventName = argsList.shift();
            //如果还没有订阅消息, 将其放到未消费队列里
            if (!eventListObj[eventName]) {
                !ignoreEventListObj[eventName] && (ignoreEventListObj[eventName] = []);
                ignoreEventListObj[eventName].push({
                    argsList: Array.prototype.slice.call(arguments),
                    self
                })
                return;
            }

            let newList = [];
            eventListObj[eventName].forEach(function (_obj, index) {
                if (Object.prototype.toString.call(_obj.callback) !== "[object Function]") {
                    return;
                }
                _obj.callback.apply(self, argsList);
                //删除只触发一次的事件
                if (!_obj.once) {
                    newList.push(_obj);
                }
            })
            eventListObj[eventName] = newList;
        },
        /**
         * 删除监听事件
         */
        remove: function (eventName, callback) {
            if (!eventName || !eventListObj[eventName]) {
                return;
            }
            if (!callback) {
                eventListObj[eventName] = null;
            } else {
                let newList = [];
                eventListObj[eventName].forEach(function (_obj, index) {
                    if (_obj.callback !== callback) {
                        newList.push(_obj);
                    }
                })
                eventListObj[eventName] = newList.length ? newList : null;
            }
        }
    }
})(window);
/**
 * Naruto的工具类
 */
window.narutoUtils = (function (window) {
    let loadingCount = 0;
    let loadingState = {
        visible: false,
        shape: "flower",
        tip: "loading...",
        // color: "#333",
        // style: { height: "100%", width: "100%" }
    }
    return {
        /**
         * 改变loading 的样式
         */
        changeLoadingAttr: function (obj) {
            if (Object.prototype.toString.call(obj) === "[object Object]") {
                loadingState = Object.assign({}, loadingCount, obj);
            }
        },
        /**
         * 打开loading效果
         */
        openLoading: function () {
            loadingCount++;
            window.narutoEvent.trigger("narutoLoadingEvent", Object.assign(loadingState, {
                visible: true,
                spinning: true
            }))
        },
        /**
         * 尝试关闭loading, 只有当loadingCount小于0时才会关闭loading效果 
         */
        closeLoading: function () {
            loadingCount--;
            if (loadingCount <= 0) {
                loadingCount = 0;
                window.narutoEvent.trigger("narutoLoadingEvent", Object.assign(loadingState, {
                    visible: false,
                    spinning: false
                }));
            }
        },
        /**
         * 关闭loading效果
         */
        closeAllLoading: function () {
            loadingCount = 0;
            window.narutoEvent.trigger("narutoLoadingEvent", Object.assign(loadingState, {
                visible: false,
                spinning: false
            }));
        },
        /**
         * 获取资源地址, 如果资源需要静态化输出 请调用此方法
         */
        getURISource: function (url) {
            return url;
        }
    }
})(window);

window.aliwareIntl = (function (window) {
    /**
     * 国际化构造方法
     * @param {Object} options 配置信息
     */
    function aliwareI18n(options) {
        // let currentLocal = options.currentLocal || navigator.language || navigator.userLanguage;

        let nowData = options.locals;
        this.nowData = nowData;
        this.setMomentLocale(this.currentLanguageCode);
    }
    var aliwareLocal = window.aliwareGetCookieByKeyName('aliyun_lang') || 'zh';
    var aliwareLocalSite = window.aliwareGetCookieByKeyName('aliyun_country') || 'cn';
    aliwareLocal = aliwareLocal.toLowerCase();
    aliwareLocalSite = aliwareLocalSite.toLowerCase();
    //当前语言
    aliwareI18n.prototype.currentLocal = aliwareLocal;
    //当前地区
    aliwareI18n.prototype.currentSite = aliwareLocalSite;
    //当前语言-地区
    aliwareI18n.prototype.currentLanguageCode = window.aliwareGetCookieByKeyName('docsite_language') || `${aliwareLocal}-${aliwareLocalSite}`;
    /**
     * 通过key获取对应国际化文案
     * @param {String} key 国际化key
     */
    aliwareI18n.prototype.get = function (key) {
        return this.nowData[key];
    }
    /**
     * 修改国际化文案数据
     * @param {String} local 语言信息
     */
    aliwareI18n.prototype.changeLanguage = function (local) {
        this.nowData = window[`i18n_${local}_doc`] || (window.i18ndoc && window.i18ndoc[local]) || {}
    }
    /**
     * 数字国际化
     * @param {Number} num 数字
     */
    aliwareI18n.prototype.intlNumberFormat = function (num) {
        if (typeof Intl !== 'object' || typeof Intl.NumberFormat !== 'function') {
            return num;
        }
        try {
            return new Intl.NumberFormat(this.currentLanguageCode).format(num || 0);
        } catch (error) {
            return num;
        }
    }
    /**
     * 时间戳格式化
     * @param {Number} num 时间戳
     * @param {Object} initOption 配置信息
     */
    aliwareI18n.prototype.intlTimeFormat = function (num = Date.now(), initOption = {}) {
        try {
            var date = Object.prototype.toString.call(num) === '[object Date]' ? num : new Date(num);
            var options = Object.assign({}, {
                // weekday: "short",
                hour12: false,
                year: "numeric",
                month: "short",
                day: "numeric",
                hour: "numeric",
                minute: "numeric",
                second: "numeric",
            }, initOption);
            return date.toLocaleDateString(this.currentLanguageCode, options);
        } catch (error) {
            return typeof moment === 'function' ? moment(num).format() : "--";
        }
    }
    /**
     * 获取当前时间格式
     * @param {String} language 语言信息: zh/en
     */
    aliwareI18n.prototype.getIntlTimeFormat = function (language) {
        language = language ? language : aliwareLocal;
        let langObj = {
            zh: "YYYY年M月D日 HH:mm:ss",
            en: "MMM D, YYYY, h:mm:ss A",
            default: "YYYY-MM-DD HH:mm:ss"
        }
        return langObj[language] ? langObj[language] : langObj["default"];
    }
    /**
     * 设置moment的locale
     * @param {String} languageCode 语言信息: zh-ch/en-us
     */
    aliwareI18n.prototype.setMomentLocale = function (languageCode) {
        if (Object.prototype.toString.call(moment) === "[object Function]") {
            moment.locale(languageCode || this.currentLanguageCode);
            return true;
        }
        return false;
    }

    return new aliwareI18n({
        currentLocal: `${aliwareLocal}`,
        locals: window[`i18n_${aliwareLocal}_doc`] || window[`i18n_en_doc`] || (window.i18ndoc && window.i18ndoc[aliwareI18n.prototype.currentLanguageCode]) || {}
    });
})(window);
/**
 * 获取url中的参数
 */
window.getParams = function (name) {
    let reg = new RegExp('(^|&)' + name + '=([^&]*)(&|$)', 'i');
    let result = [];
    if (window.location.hash !== '') {
        result = window.location.hash.split('?'); //优先判别hash
    } else {
        result = window.location.href.split('?');
    }

    if (result.length === 1) {
        result = window.parent.location.hash.split('?');
    }

    if (result.length > 1) {
        let r = result[1].match(reg);
        if (r != null) {
            return decodeURIComponent(r[2]);
        }
    }

    return null;
};
/**
 * 设置参数
 */
window.setParam = function (name, value) {
    return window.setParams.apply(this, arguments);
};
/**
 * 设置参数
 */
window.setParams = (function (window) {
    const _originHref = window.location.href.split("#")[0];
    return function (name, value) {
        if (!name) {
            return;
        }

        let obj = {};
        if (typeof name === 'string') {
            obj = {
                [name]: value
            }
        }

        if (Object.prototype.toString.call(name) === '[object Object]') {
            obj = name;
        }

        let hashArr = [];
        if (window.location.hash) {
            hashArr = window.location.hash.split('?');
        }

        let paramArr = hashArr[1] && hashArr[1].split('&') || [];

        let paramObj = {};
        paramArr.forEach((val) => {
            var tmpArr = val.split('=');
            paramObj[tmpArr[0]] = decodeURIComponent(tmpArr[1] || "");
        });
        paramObj = Object.assign({}, paramObj, obj);

        let resArr = Object.keys(paramObj).map(function (key) {
            return `${key}=${encodeURIComponent(paramObj[key] || "")}`;
        }) || [];

        hashArr[1] = resArr.join('&');
        let hashStr = hashArr.join('?');
        if (window.history.replaceState) {
            let url = _originHref + hashStr;
            window.history.replaceState(null, '', url);
        } else {
            window.location.hash = hashStr;
        }
    }
})(window);
/**
 * 删除参数
 */
window.removeParams = (function (window) {
    const _originHref = window.location.href.split("#")[0];
    return function (name) {
        let removeList = [];

        let nameType = Object.prototype.toString.call(name);
        if (nameType === "[object String]") {
            removeList.push(name);
        } else if (nameType === "[object Array]") {
            removeList = name;
        } else if (nameType === "[object Object]") {
            removeList = Object.keys(name);
        } else {
            return;
        }

        let hashArr = [];
        if (window.location.hash) {
            hashArr = window.location.hash.split('?');
        }

        let paramArr = hashArr[1] && hashArr[1].split('&') || [];

        // let paramObj = {};
        paramArr = paramArr.filter((val) => {
            var tmpArr = val.split('=');
            return removeList.indexOf(tmpArr[0]) === -1;
        });

        hashArr[1] = paramArr.join('&');
        let hashStr = hashArr.join('?');
        if (window.history.replaceState) {
            let url = _originHref + hashStr;
            window.history.replaceState(null, '', url);
        } else {
            window.location.hash = hashStr;
        }
    }
})(window);
/**
 * 封装的ajax请求
 */
window.request = (function (window) {
    var middlewareList = [];
    var middlewareBackList = [];
    var serviceMap = {};
    var serviceList = serviceConfig.serviceList || [];
    var methodList = serviceConfig.method || [];
    /**
     * 获取真实url信息
     */
    var NarutoRealUrlMapper = (function () {
        serviceList.forEach(obj => {
            serviceMap[obj.registerName] = obj;
        })
        return function (registerName) {
            var serviceObj = serviceMap[registerName];
            if (!serviceObj) {
                return null;
            }
            //获取正确请求方式
            serviceObj.methodType = methodList[serviceObj.method];
            return serviceObj;
        }
    })()
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
    function handleMiddleWare(config) {
        //获取除config外传入的参数
        let args = [].slice.call(arguments, 1);
        //最后一个参数为middlewareList
        let middlewareList = args.pop() || [];
        if (middlewareList && middlewareList.length > 0) {
            config = middlewareList.reduce((config, callback) => {
                if (typeof callback === 'function') {
                    return callback.apply(this, [config, ...args]) || config;
                }
                return config;
            }, config)
        }
        return config;
    }
    /**
     * 处理自定义url
     * @param {*Object} config ajax请求配置信息
     */
    function handleCustomService(config) {
        //只处理com.alibaba.开头的url
        if (config && config.url && config.url.indexOf('com.alibaba.') === 0) {
            var registerName = config.url;
            var serviceObj = NarutoRealUrlMapper(registerName);
            if (serviceObj && serviceObj.url && serviceObj.url.replace) {
                //有mock数据 直接返回 生产环境失效
                if (projectConfig.is_preview && serviceObj.is_mock && config.success) {
                    var code = null;
                    try {
                        code = JSON.parse(serviceObj.defaults);
                    } catch (error) {
                    }
                    config.success(code);
                    return;
                }
                //替换url中的占位符
                config.url = serviceObj.url.replace(/{([^\}]+)}/g, function ($1, $2) {
                    return config.$data[$2];
                })
                try {
                    //添加静态参数
                    if (serviceObj.is_param && typeof config.data === 'object') {
                        config.data = Object.assign({}, JSON.parse(serviceObj.params), config.data);
                    }
                } catch (e) {
                    console.log(e)
                }
                //替换请求方式
                if (serviceObj.method && !config.type) {
                    config.type = serviceObj.methodType;
                }
                //将请求参数变为json格式
                if (serviceObj.isJsonData && typeof config.data === 'object') {
                    config.data = JSON.stringify(config.data)
                    config.processData = false;
                    config.dataType = 'json';
                    config.contentType = 'application/json';
                }
                try {
                    //设置临时代理 生产环境失效
                    if (projectConfig.is_preview && serviceObj.is_proxy) {
                        var beforeSend = config.beforeSend;
                        config.beforeSend = function (xhr) {
                            serviceObj.cookie && xhr.setRequestHeader('tmpCookie', serviceObj.cookie);
                            serviceObj.header && xhr.setRequestHeader('tmpHeader', serviceObj.header);
                            serviceObj.proxy && xhr.setRequestHeader('tmpProxy', serviceObj.proxy)
                            beforeSend && beforeSend(xhr);
                        }
                    }
                } catch (e) {
                    console.log(e)
                }
                //设置自动loading效果
                if (serviceObj.autoLoading) {
                    window.narutoUtils.openLoading();
                    const prevComplete = config.complete;
                    config.complete = function () {
                        window.narutoUtils.closeLoading();
                        typeof prevComplete === "function" && prevComplete.apply($, Array.prototype.slice.call(arguments));
                    }
                }
                //serviceObj = null;
            }
        }
        return config;
    }
    /**
     * 处理edas的Url
     * @param {*Object} config ajax请求配置信息
     */
    function handleEdasUrl(config, noprefix) {
        var _url = config.url;
        var edasprefix = window.edasprefix || ''
        try {
            if (window.parent.location && window.parent.location.host && window.parent.location.host.indexOf('edas') !== -1) { //如果是包含在edas里面需要增加前缀
                if (!noprefix) {//如果没有显示指明不加前缀
                    if (_url.indexOf('/authgw/') === -1) { //如果没有添加edas网关前缀则添加
                        _url = '/authgw/' + edasprefix + _url;
                    }
                }
            }
            config.url = _url;
        } catch (error) {
        }
        return config;
    }
    function Request(config) {
        //除了config外的传参
        var args = [].slice.call(arguments, 1);
        //处理前置中间件
        config = handleMiddleWare.apply(this, [config, ...args, middlewareList]);
        //处理自定义url
        config = handleCustomService.apply(this, [config, ...args]);
        if (!config)
            return;
        //xsrf
        if (config.type && config.type.toLowerCase() === 'post' && config.data && Object.prototype.toString.call(config.data) === '[object Object]' && !config.data.sec_token) {
            var sec_token = window.aliwareGetCookieByKeyName('XSRF-TOKEN')
            sec_token && (config.data.sec_token = sec_token);
        }
        //处理edas的url
        config = handleEdasUrl.apply(this, [config, ...args]);

        //处理后置中间件
        config = handleMiddleWare.apply(this, [config, ...args, middlewareBackList]);

        return $.ajax(Object.assign({}, config, {
            type: config.type,
            url: config.url,
            data: config.data || '',
            dataType: config.dataType || 'json',
            beforeSend: function (xhr) {
                xhr.setRequestHeader('poweredBy', 'naruto');
                xhr.setRequestHeader('projectName', 'newDiamond');
                config.beforeSend && config.beforeSend(xhr);
            }
        }))
    }
    //暴露方法
    Request.handleCustomService = handleCustomService;
    Request.handleMiddleWare = handleMiddleWare;
    Request.NarutoRealUrlMapper = NarutoRealUrlMapper;
    Request.serviceList = serviceList;
    Request.serviceMap = serviceMap;
    Request.middleWare = middleWare;

    return Request;
})(window);