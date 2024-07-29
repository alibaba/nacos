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
import { Message } from '@alifd/next';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { FaCopy } from 'react-icons/fa';

@connect(state => ({ ...state.locale }))
@withRouter
class Copy extends React.Component {
  static displayName = 'Copy';

  static propTypes = {
    style: PropTypes.object,
    value: PropTypes.string,
    textNode: PropTypes.string,
    className: PropTypes.string,
    showIcon: PropTypes.bool,
    title: PropTypes.string,
    locale: PropTypes.object,
  };

copyText(locale, value) {
  // fix Navigator clipboard api needs a secure context (https)
  // refer to https://stackoverflow.com/questions/51805395/navigator-clipboard-is-undefined
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(value);
  } else {
    // Use the 'out of viewport hidden text area' trick
    const textArea = document.createElement('textarea');
    textArea.value = value;

    // Move textarea out of the viewport so it's not visible
    textArea.style.position = 'absolute';
    textArea.style.left = '-999999px';

    document.body.prepend(textArea);
    textArea.select();

    try {
      document.execCommand('copy');
    } catch (error) {
    } finally {
      textArea.remove();
    }
  }
  Message.success(locale.Components.copySuccessfully);
}

  render() {
    const { style = {}, value, textNode, className, showIcon = true, title, locale } = this.props;
    return (
      <div className={className} onClick={() => (showIcon ? '' : this.copyText(locale, value))} style={style}>
        {textNode || value}
        {showIcon && (
          <FaCopy
            title={title || '复制'}
            className="copy-icon"
            type="copy"
            onClick={() => this.copyText(locale, value)}
          />
        )}
      </div>
    );
  }
}

export default Copy;
