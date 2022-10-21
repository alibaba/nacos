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
import { Provider, connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import PropTypes from 'prop-types';
import Copy from '../Copy';

@withRouter
@connect(state => ({ ...state.locale }))
class PageTitle extends React.Component {
  static propTypes = {
    title: PropTypes.string,
    desc: PropTypes.string,
    nameSpace: PropTypes.bool,
    locale: PropTypes.object,
  };

  getNameSpace(locale, desc, nameSpace) {
    if (!nameSpace) {
      return desc;
    }
    return (
      <span style={{ display: 'flex', alignItems: 'center', marginLeft: 16 }}>
        {locale.NameSpace.namespaceID}
        <Copy
          style={{
            marginLeft: 16,
            height: 32,
            display: 'flex',
            alignItems: 'center',
            background: 'rgb(239, 243, 248)',
            padding: '0px 8px',
            minWidth: 220,
          }}
          value={desc}
        />
      </span>
    );
  }

  render() {
    const { title, desc, nameSpace, locale } = this.props;
    return (
      <div style={{ display: 'flex', alignItems: 'center', marginTop: 8, marginBottom: 8 }}>
        <span style={{ fontSize: 28, height: 40, fontWeight: 500 }}>{title}</span>
        <span style={{ marginLeft: 4 }}>
          {desc && desc !== 'undefined' ? this.getNameSpace(locale, desc, nameSpace) : ''}
        </span>
      </div>
    );
  }
}

export default PageTitle;
