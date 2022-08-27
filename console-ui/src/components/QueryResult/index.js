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
import PropTypes from 'prop-types';
import { Provider, connect } from 'react-redux';
import './index.scss';

@connect(state => ({ ...state.locale }))
class QueryResult extends React.Component {
  static displayName = 'QueryResult';

  static propTypes = {
    locale: PropTypes.object,
    total: PropTypes.number,
  };

  render() {
    const { locale = {}, total } = this.props;
    return (
      <div className="query_result_wrapper">
        {locale.ConfigurationManagement.queryResults}
        <strong style={{ fontWeight: 'bold' }}> {total} </strong>
        {locale.ConfigurationManagement.articleMeetRequirements}
      </div>
    );
  }
}

export default QueryResult;
