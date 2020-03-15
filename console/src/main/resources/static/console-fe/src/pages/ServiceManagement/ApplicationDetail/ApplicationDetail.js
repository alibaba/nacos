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
import { request } from '@/globalLib';
import { Input, Button, Card, ConfigProvider, Form, Loading, Message } from '@alifd/next';
import { getParameter } from 'utils/nacosutil';
import './ApplicationDetail.scss';
import AppInstanceTable from './AppInstanceTable';

const FormItem = Form.Item;
const pageFormLayout = {
  labelCol: { fixedSpan: 10 },
  wrapperCol: { span: 14 },
};

@ConfigProvider.config
class ApplicationDetail extends React.Component {
  static displayName = 'ApplicationDetail';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
    location: PropTypes.object,
    namespaceId: PropTypes.string,
  };

  constructor(props) {
    super(props);
    this.state = {
      ip: getParameter(props.location.search, 'ip'),
      instanceList: [],
      port: getParameter(props.location.search, 'port'),
      instanceCount: getParameter(props.location.search, 'instanceCount'),
      namespaceId: getParameter(props.location.search, 'namespaceId'),
      loading: false,
      currentPage: 1,
      clusters: [],
      instances: {},
      service: {},
      pageSize: 10,
      pageNum: {},
    };
  }

  componentDidMount() {
    if (!this.state.ip) {
      this.props.history.goBack();
    }
  }

  render() {
    const { locale = {} } = this.props;
    const { ip, instanceList, loading, port, instanceCount, namespaceId } = this.state;
    return (
      <div className="main-container service-detail">
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          className="loading"
          visible={loading}
          color={'#333'}
        >
          <h1
            style={{
              position: 'relative',
              width: '100%',
            }}
          >
            {locale.applicationDetail}
            <Button
              type="primary"
              className="header-btn"
              onClick={() => this.props.history.goBack()}
            >
              {locale.back}
            </Button>
          </h1>

          <Form {...pageFormLayout}>
            <FormItem label={`${locale.applicationIp}:`}>
              <Input value={ip} readOnly />
            </FormItem>
            <FormItem label={`${locale.applicationPort}:`}>
              <Input value={port} readOnly />
            </FormItem>
            <FormItem label={`${locale.instanceCount}:`}>
              <Input value={instanceCount} readOnly />
            </FormItem>
          </Form>
          <AppInstanceTable ip={ip} port={port} namespaceId={namespaceId} />
        </Loading>
      </div>
    );
  }
}

export default ApplicationDetail;
