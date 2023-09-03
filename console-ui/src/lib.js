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
import { getParams, request } from './globalLib';

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
  window.monaco.languages.register({ id: 'toml' });

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
  window.monaco.languages.setMonarchTokensProvider('toml', {
    // The main tokenizer for our languages
    tokenizer: {
      root: [
        { include: '@comment' },
        { include: '@datetime' },
        { include: '@boolean' },
        { include: '@number' },
        { include: '@string' },
        { include: '@table' },
        [/"""/, { token: 'string.block', bracket: '@open', next: '@string' }],
        [/'''/, { token: 'stringLiteral.block', bracket: '@open', next: '@string' }],
        [
          /\s*((?:(?:(?:[A-Za-z0-9_+-]+)|(?:"[^"]+")|(?:'[^']+'))\s*\.?\s*)+)\s*(=)/,
          ['variable.name', 'eq'],
        ],
      ],
      comment: [[/\s*((#).*)$/, 'comment']],
      datetime: [
        [/\d{2}:\d{2}:\d{2}(?:\.\d+)?/, 'localTime'],
        [/\d{4}\-\d{2}\-\d{2}/, 'localDate'],
        [/\d{4}\-\d{2}\-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?/, 'localDateTime'],
        [
          /(?<!\w)(\d{4}\-\d{2}\-\d{2}[T| ]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[\+\-]\d{2}:\d{2}))(?!\w)/,
          'offsetDateTime',
        ],
      ],
      boolean: [[/(?<!\w)(true|false)(?!\w)/, 'boolean']],
      number: [
        [/(?<!\w)((?:[\+\-]?(0|([1-9](([0-9]|_[0-9])+)?))))(?!\w)/, 'number'],
        [
          /(?<!\w)([\+\-]?(0|([1-9](([0-9]|_[0-9])+)?))(?:(?:\.([0-9]+))?[eE][\+\-]?[1-9]_?[0-9]*|(?:\.[0-9_]*)))(?!\w)/,
          'number.float',
        ],
        [/(?<!\w)((?:0x(([0-9a-fA-F](([0-9a-fA-F]|_[0-9a-fA-F])+)?))))(?!\w)/, 'number.hex'],
        [/(?<!\w)(0o[0-7](_?[0-7])*)(?!\w)/, 'number.octal'],
        [/(?<!\w)(0b[01](_?[01])*)(?!\w)/, 'number.binary'],
        [/(?<!\w)([\+\-]?inf)(?!\w)/, 'number.inf'],
        [/(?<!\w)([\+\-]?nan)(?!\w)/, 'number.nan'],
      ],
      string: [
        [/\\([btnfr"\\\n/ ]|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/, 'string.escape'],
        [/\\[^btnfr/"\\\n]/, 'string.escape.invalid'],
        [/".+?"/, 'string'],
        [/"""/, { token: 'string.block', bracket: '@close', next: '@pop' }],
        [/'.+?'/, 'stringLiteral'],
        [/'''/, { token: 'stringLiteral.block', bracket: '@close', next: '@pop' }],
      ],
      table: [
        [/^\s*(\[)([^\[\]]*)(\])/, 'table'],
        [/^\s*(\[\[)([^\[\]]*)(\]\])/, 'table.array'],
        [/(?<!\w)(\{)((.)+)(\})(?!\w)/, 'table.inline'],
      ],
    },
  });

  // Define a new theme that constains only rules that match this language
  window.monaco.editor.defineTheme('vs-dark-enhanced', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'key', foreground: '009968' },
      { token: 'value', foreground: '009968' },
      { token: 'table', foreground: 'eee8aa' },
      { token: 'table.array', foreground: 'eee8aa' },
      { token: 'table.inline', foreground: 'eee8aa' },
      { token: 'string.block', foreground: 'ce9178' },
      { token: 'stringLiteral', foreground: 'ce9178' },
      { token: 'stringLiteral.block', foreground: 'ce9178' },
      { token: 'boolean', foreground: '3dc9b0' },
      // { token: 'eq', foreground: '000000' },
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
