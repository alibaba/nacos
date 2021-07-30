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
import { getParams, request, aliwareIntl } from './globalLib';

let hasAlert = false;

window.edasprefix = 'acm'; // 固定的edas网关需要的项目名

export const isParentEdas = () =>
  window.parent && window.parent.location.host.indexOf('edas') !== -1;

window.globalConfig = {
  isParentEdas,
};

request.middleWare((_config = {}) => {
  let config = _config;
  let { url = '' } = config;

  const namespace = localStorage.getItem('namespace') ? localStorage.getItem('namespace') : '';
  // 如果url中已经有 namespaceId, 不在data中添加namespaceId
  config.data =
    url.indexOf('namespaceId=') === -1
      ? Object.assign({}, config.data, { namespaceId: namespace })
      : config.data;

  let tenant = window.nownamespace || getParams('namespace') || '';
  tenant = tenant === 'global' ? '' : tenant;
  const splitArr = url.split('?');
  if (splitArr.length > 1) {
    const params = splitArr[1];
    if (params.indexOf('dataId') !== -1) {
      url += `&tenant=${tenant}`;
      config.url = url;
    }
  }

  const preSucess = config.success;
  const preErorr = config.error;

  config.success = function(res) {
    if (res.code === 'ConsoleNeedLogin' && window.location.host.indexOf('acm') !== -1) {
      window.location.reload();
    }
    if (res.code === 403 && !hasAlert) {
      hasAlert = true;
      window.Dialog.alert({
        style: { width: 400 },
        content: res.message,
        onOk: () => {
          hasAlert = false;
        },
        onCancel: () => {
          hasAlert = false;
        },
        onClose: () => {
          hasAlert = false;
        },
      });
    } else {
      typeof preSucess === 'function' && preSucess(res);
    }
  };

  config.error = function(res) {
    if (res.status === 403 && !hasAlert) {
      hasAlert = true;

      window.Dialog.alert({
        style: { width: 400 },
        content: aliwareIntl.get('com.alibaba.nacos.pubshow'), // '子账号没有权限，请联系主账号负责人RAM上授权',
        onOk: () => {
          hasAlert = false;
        },
        onCancel: () => {
          hasAlert = false;
        },
        onClose: () => {
          hasAlert = false;
        },
      });
    } else {
      typeof preErorr === 'function' && preErorr(res);
    }
  };

  return config;
});

/**
 * 配置 monaco
 */
window.require.config({
  paths: { vs: process.env.NODE_ENV === 'production' ? 'console-ui/public/js/vs' : 'js/vs' },
});
window.require.config({
  'vs/nls': {
    availableLanguages: {
      '*': 'zh-cn',
    },
  },
});

window.require(['vs/editor/editor.main'], () => {
  // Register a new language
  window.monaco.languages.register({ id: 'properties' });

  // Register a tokens provider for the language
  window.monaco.languages.setMonarchTokensProvider('properties', {
    tokenizer: {
      root: [
        [/^\#.*/, 'comment'],
        [/.*\=/, 'key'],
        [/^=.*/, 'value'],
      ],
    },
  });

  // Define a new theme that constains only rules that match this language
  window.monaco.editor.defineTheme('properties', {
    base: 'vs',
    inherit: false,
    rules: [
      { token: 'key', foreground: '009968' },
      { token: 'value', foreground: '009968' },
      { token: 'comment', foreground: '666666' },
    ],
  });

  // Register a completion item provider for the new language
  window.monaco.languages.registerCompletionItemProvider('properties', {
    provideCompletionItems: () => [
      {
        label: 'simpleText',
        kind: window.monaco.languages.CompletionItemKind.Text,
      },
      {
        label: 'testing',
        kind: window.monaco.languages.CompletionItemKind.Keyword,
        insertText: {
          value: 'testing(${1:condition})',
        },
      },
      {
        label: 'ifelse',
        kind: window.monaco.languages.CompletionItemKind.Snippet,
        insertText: {
          value: ['if (${1:condition}) {', '\t$0', '} else {', '\t', '}'].join('\n'),
        },
        documentation: 'If-Else Statement',
      },
    ],
  });
});

window.importEditor = callback => {
  window.require(['vs/editor/editor.main'], () => {
    callback && callback();
  });
};

// 同步获取命名空间地址

window._getLink = (function() {
  const _linkObj = {};
  // request({
  //     url: "com.alibaba.nacos.service.getLinks",
  //     async: false,
  //     data: {},
  //     success: res => {
  //         if (res.code === 200) {
  //             _linkObj = res.data;
  //         }
  //     }
  // });
  return function(linkName) {
    return _linkObj[linkName] || '';
  };
})(window);

window.addEventListener('resize', () => {
  try {
    if (this.timmer) {
      clearTimeout(this.timmer);
    }
    this.timmer = setTimeout(() => {
      let height = document.body.clientHeight;
      height = height > 800 ? height : 800;
      window.parent.adjustHeight && window.parent.adjustHeight(height);
    }, 500);
  } catch (e) {}
});
// 判断是否是国际站国际用户
window.isIntel = function() {
  const { host } = window.location;
  return host.indexOf('alibabacloud.com') !== -1;
};

export default {};
