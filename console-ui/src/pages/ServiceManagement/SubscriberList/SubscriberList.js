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
import {
  Button,
  Field,
  Form,
  Grid,
  Input,
  Loading,
  Pagination,
  Table,
  Message,
  ConfigProvider,
} from '@alifd/next';
import { connect } from 'react-redux';
import { getSubscribers, removeSubscribers } from '../../../reducers/subscribers';
import { getParams } from '../../../globalLib';
import RegionGroup from '../../../components/RegionGroup';

import './SubscriberList.scss';

const FormItem = Form.Item;
const { Row, Col } = Grid;
const { Column } = Table;

@connect(state => ({ subscriberData: state.subscribers }), { getSubscribers, removeSubscribers })
@ConfigProvider.config
class SubscriberList extends React.Component {
  static displayName = 'SubscriberList';

  static propTypes = {
    locale: PropTypes.object,
    getSubscribers: PropTypes.func,
    removeSubscribers: PropTypes.func,
    subscriberData: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      total: 0,
      pageSize: 10,
      pageNo: 1,
      search: {
        serviceName: getParams('name') || '',
        groupName: getParams('groupName') || '',
      },
      nowNamespaceId: getParams('namespace') || '',
    };
    this.field = new Field(this);
  }

  componentDidMount() {
    const { search } = this.state;
    if (search.serviceName) {
      this.querySubscriberList();
    }
  }

  openLoading() {
    this.setState({ loading: true });
  }

  closeLoading() {
    this.setState({ loading: false });
  }

  querySubscriberList() {
    const { searchServiceNamePrompt } = this.props.locale;
    const { search, pageSize, pageNo, nowNamespaceId = '' } = this.state;
    if (!search.serviceName) {
      Message.error(searchServiceNamePrompt);
      return;
    }
    this.props.getSubscribers({
      ...search,
      pageSize,
      pageNo,
      namespaceId: nowNamespaceId,
    });
  }

  switchNamespace = () => {
    this.props.removeSubscribers();
  };

  setNowNameSpace = (nowNamespaceName, nowNamespaceId) =>
    this.setState({
      nowNamespaceName,
      nowNamespaceId,
    });

  render() {
    const { locale = {}, subscriberData = {} } = this.props;
    const { count = 0, subscribers = [] } = subscriberData;
    const {
      pubNoData,
      subscriberList,
      serviceName,
      serviceNamePlaceholder,
      groupName,
      groupNamePlaceholder,
      query,
    } = locale;
    const { search, nowNamespaceName, nowNamespaceId } = this.state;
    const { init, getValue } = this.field;
    this.init = init;
    this.getValue = getValue;
    return (
      <div className="main-container subscriber-list">
        <Loading
          shape="flower"
          style={{
            position: 'relative',
            width: '100%',
          }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <div style={{ marginTop: -15 }}>
            <RegionGroup
              setNowNameSpace={this.setNowNameSpace}
              namespaceCallBack={this.switchNamespace}
            />
          </div>
          <h3 className="page-title">
            <span className="title-item">{subscriberList}</span>
            <span className="title-item">|</span>
            <span className="title-item">{nowNamespaceName}</span>
            <span className="title-item">{nowNamespaceId}</span>
          </h3>
          <Row
            className="demo-row"
            style={{
              marginBottom: 10,
              padding: 0,
            }}
          >
            <Col span="24">
              <Form inline field={this.field}>
                <FormItem label={serviceName} required>
                  <Input
                    placeholder={serviceNamePlaceholder}
                    style={{ width: 200 }}
                    value={search.serviceName}
                    onChange={serviceName => this.setState({ search: { ...search, serviceName } })}
                    onPressEnter={() =>
                      this.setState({ pageNo: 1 }, () => this.querySubscriberList())
                    }
                  />
                </FormItem>
                <FormItem label={groupName}>
                  <Input
                    placeholder={groupNamePlaceholder}
                    style={{ width: 200 }}
                    value={search.groupName}
                    onChange={groupName => this.setState({ search: { ...search, groupName } })}
                    onPressEnter={() =>
                      this.setState({ pageNo: 1 }, () => this.querySubscriberList())
                    }
                  />
                </FormItem>
                <FormItem label="">
                  <Button
                    type="primary"
                    onClick={() => this.setState({ pageNo: 1 }, () => this.querySubscriberList())}
                    style={{ marginRight: 10 }}
                  >
                    {query}
                  </Button>
                </FormItem>
              </Form>
            </Col>
          </Row>
          <Row style={{ padding: 0 }}>
            <Col span="24" style={{ padding: 0 }}>
              <Table dataSource={subscribers} locale={{ empty: pubNoData }}>
                <Column title={locale.address} dataIndex="addrStr" />
                <Column title={locale.clientVersion} dataIndex="agent" />
                <Column title={locale.appName} dataIndex="app" />
              </Table>
            </Col>
          </Row>
          {count > this.state.pageSize && (
            <div
              style={{
                marginTop: 10,
                textAlign: 'right',
              }}
            >
              <Pagination
                current={this.state.pageNo}
                total={count}
                pageSize={this.state.pageSize}
                onChange={pageNo => this.setState({ pageNo }, () => this.querySubscriberList())}
              />
            </div>
          )}
        </Loading>
      </div>
    );
  }
}

export default SubscriberList;
