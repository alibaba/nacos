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
  Dialog,
  Message,
  ConfigProvider,
  Switch,
  NumberPicker,
} from '@alifd/next';
import { request } from '../../../globalLib';
import RegionGroup from '../../../components/RegionGroup';
import EditServiceDialog from '../ServiceDetail/EditServiceDialog';

import './ApplicationList.scss';

const FormItem = Form.Item;
const { Row, Col } = Grid;
const { Column } = Table;

@ConfigProvider.config
class ApplicationList extends React.Component {
  static displayName = 'ApplicationList';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.editServiceDialog = React.createRef();
    this.showcode = React.createRef();
    this.state = {
      loading: false,
      total: 0,
      pageSize: 10,
      currentPage: 1,
      namesapceId: '',
      dataSource: [],
      search: {
        applicationIp: '',
        applicationPort: '',
      },
      hasIpCount: !(localStorage.getItem('hasIpCount') === 'false'),
    };
    this.field = new Field(this);
  }

  openLoading() {
    this.setState({ loading: true });
  }

  closeLoading() {
    this.setState({ loading: false });
  }

  openEditServiceDialog() {
    try {
      this.editServiceDialog.current.getInstance().show(this.state.service);
    } catch (error) {}
  }

  queryApplicationList() {
    const { currentPage, pageSize, search, nowNamespaceId } = this.state;
    const parameter = [
      `namespaceId=${nowNamespaceId}`,
      `pageNo=${currentPage}`,
      `pageSize=${pageSize}`,
      `applicationIp=${search.applicationIp}`,
      `applicationPort=${search.applicationPort !== 'undefined' ? search.applicationPort : ''}`,
    ];
    request({
      url: `v1/ns/catalog/applications?${parameter.join('&')}`,
      beforeSend: () => this.openLoading(),
      success: ({ count = 0, applicationList = [] } = {}) => {
        this.setState({
          dataSource: applicationList,
          total: count,
        });
      },
      error: () =>
        this.setState({
          dataSource: [],
          total: 0,
          currentPage: 0,
        }),
      complete: () => this.closeLoading(),
    });
  }

  getQueryLater = () => {
    setTimeout(() => this.queryApplicationList());
  };

  disableAllInstance(application) {
    const { locale = {} } = this.props;
    const { nowNamespaceId } = this.state;
    const { prompt, promptOffline } = locale;
    Dialog.confirm({
      title: prompt,
      content: promptOffline,
      onOk: () => {
        request({
          method: 'PUT',
          url: `v1/ns/catalog/applications/offline?ip=${application.ip}&port=${application.port}&namespaceId=${nowNamespaceId}`,
          dataType: 'text',
          beforeSend: () => this.openLoading(),
          success: ({ success, msg, count } = {}) => {
            if (success) {
              Message.error(msg);
              return;
            }
            this.queryApplicationList();
          },
          error: res => Message.error(res.responseText || res.statusText),
          complete: () => this.closeLoading(),
        });
      },
    });
  }

  setNowNameSpace = (nowNamespaceName, nowNamespaceId) =>
    this.setState({
      nowNamespaceName,
      nowNamespaceId,
    });

  rowColor = row => ({ className: !row.healthyInstanceCount ? 'row-bg-red' : '' });

  render() {
    const { locale = {} } = this.props;
    const {
      applicationList,
      applicationIp,
      applicationIpPlaceholder,
      applicationPort,
      applicationPortPlaceholder,
      query,
      pubNoData,
      operation,
      columnServiceCount,
      disableAllInstance,
      showRelativeList,
    } = locale;
    const { search, nowNamespaceName, nowNamespaceId, hasIpCount } = this.state;
    const { init, getValue } = this.field;
    this.init = init;
    this.getValue = getValue;

    return (
      <div className="main-container service-management">
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
              namespaceCallBack={this.getQueryLater}
            />
          </div>
          <h3 className="page-title">
            <span className="title-item">{applicationList}</span>
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
                <FormItem label={applicationIp}>
                  <Input
                    placeholder={applicationIpPlaceholder}
                    style={{ width: 200 }}
                    value={search.applicationIp}
                    onChange={applicationIp =>
                      this.setState({ search: { ...search, applicationIp } })
                    }
                    onPressEnter={() =>
                      this.setState({ currentPage: 1 }, () => this.queryApplicationList())
                    }
                  />
                </FormItem>
                <FormItem label={applicationPort}>
                  <NumberPicker
                    placeholder={applicationPortPlaceholder}
                    style={{ width: 200 }}
                    value={search.applicationPort}
                    max={65535}
                    min={0}
                    onChange={applicationPort => {
                      if (applicationPort === undefined) {
                        this.setState({ search: { ...search, applicationPort: '' } });
                      } else {
                        this.setState({ search: { ...search, applicationPort } });
                      }
                    }}
                    onPressEnter={() =>
                      this.setState({ currentPage: 1 }, () => this.queryApplicationList())
                    }
                  />
                </FormItem>
                <FormItem label="">
                  <Button
                    type="primary"
                    onClick={() =>
                      this.setState({ currentPage: 1 }, () => this.queryApplicationList())
                    }
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
              <Table
                dataSource={this.state.dataSource}
                locale={{ empty: pubNoData }}
                getRowProps={row => this.rowColor(row)}
              >
                <Column title={locale.applicationIp} dataIndex="ip" />
                <Column title={locale.applicationPort} dataIndex="port" />
                <Column title={locale.instanceCount} dataIndex="instanceCount" />
                <Column
                  title={operation}
                  align="center"
                  cell={(value, index, record) => (
                    // @author yongchao9  #2019年05月18日 下午5:46:28
                    /* Add a link to view "sample code"
                     replace the original button with a label,
                     which is consistent with the operation style in configuration management.
                     */
                    <div>
                      <a
                        onClick={() =>
                          this.props.history.push(
                            `/applicationDetail?ip=${record.ip}&port=${record.port}&namesapceId=${nowNamespaceId}&instanceCount=${record.instanceCount}`
                          )
                        }
                        style={{ marginRight: 5 }}
                      >
                        {showRelativeList}
                      </a>
                      <span style={{ marginRight: 5 }}>|</span>
                      <a onClick={() => this.disableAllInstance(record)} style={{ marginRight: 5 }}>
                        {disableAllInstance}
                      </a>
                    </div>
                  )}
                />
              </Table>
            </Col>
          </Row>
          {this.state.total > this.state.pageSize && (
            <div
              style={{
                marginTop: 10,
                textAlign: 'right',
              }}
            >
              <Pagination
                current={this.state.currentPage}
                total={this.state.total}
                pageSize={this.state.pageSize}
                onChange={currentPage =>
                  this.setState({ currentPage }, () => this.queryApplicationList())
                }
              />
            </div>
          )}
        </Loading>
        <EditServiceDialog
          ref={this.editServiceDialog}
          openLoading={() => this.openLoading()}
          closeLoading={() => this.closeLoading()}
          queryServiceList={() =>
            this.setState({ currentPage: 1 }, () => this.queryApplicationList())
          }
        />
      </div>
    );
  }
}

export default ApplicationList;
