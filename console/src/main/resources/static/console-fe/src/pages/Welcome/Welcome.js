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

import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';

@connect(state => ({ ...state.base }))
class Welcome extends React.Component {
  static propTypes = {
    functionMode: PropTypes.string,
  };

  render() {
    const { functionMode } = this.props;
    return (
      <div>
        {functionMode !== '' && (
          <Redirect
            to={`/${functionMode === 'naming' ? 'serviceManagement' : 'configurationManagement'}`}
          />
        )}
      </div>
    );
  }
}

export default Welcome;
