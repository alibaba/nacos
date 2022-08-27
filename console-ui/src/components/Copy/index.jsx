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

import React from 'react';
import { withRouter } from 'react-router-dom';
import { Icon, Message } from '@alifd/next';
import PropTypes from 'prop-types';

// 创建假元素
function createFakeElement(value) {
  const fakeElement = document.createElement('textarea');

  fakeElement.style.border = '0';
  fakeElement.style.padding = '0';
  fakeElement.style.margin = '0';

  fakeElement.style.position = 'absolute';
  fakeElement.style.left = '-999px';
  fakeElement.style.top = `${window.pageYOffset || document.documentElement.scrollTop}px`;
  fakeElement.setAttribute('readonly', '');
  fakeElement.value = value;
  return fakeElement;
}

function copyText(value) {
  const element = createFakeElement(value);
  document.body.appendChild(element);

  // 选中元素
  element.focus();
  element.select();
  element.setSelectionRange(0, element.value.length);

  document.execCommand('copy');
  document.body.removeChild(element);
  Message.success('Success copied!');
}

@withRouter
class Copy extends React.Component {
  static displayName = 'Copy';

  static propTypes = {
    style: PropTypes.object,
    value: PropTypes.string,
    textNode: PropTypes.string,
    className: PropTypes.string,
    showIcon: PropTypes.bool,
  };

  render() {
    const { style = {}, value, textNode, className, showIcon = true } = this.props;
    return (
      <div className={className} onClick={() => (showIcon ? '' : copyText(value))} style={style}>
        {textNode || value}
        {showIcon && (
          <Icon
            title="复制"
            className="copy-icon"
            size="small"
            type="copy"
            onClick={() => copyText(value)}
          />
        )}
      </div>
    );
  }
}

export default Copy;
