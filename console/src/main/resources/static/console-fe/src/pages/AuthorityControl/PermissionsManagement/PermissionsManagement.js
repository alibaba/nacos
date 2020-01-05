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
import {
  Button,
  Field,
  Form,
  Grid,
  Input,
  Loading,
  Pagination,
  Table,
  ConfigProvider,
} from '@alifd/next';

import './PermissionsManagement.scss';

@ConfigProvider.config
class PermissionsManagement extends React.Component {
  static displayName = 'UserManagement';

  static propTypes = {
    locale: PropTypes.object,
  };

  render() {
    return (
      <>
        <h1>PermissionsManagement</h1>
      </>
    );
  }
}

export default PermissionsManagement;
