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
} from '@alifd/next';
import { request } from '../../../globalLib';
import RegionGroup from '../../../components/RegionGroup';
import EditServiceDialog from '../ServiceDetail/EditServiceDialog';

import './ServiceList.scss';

const FormItem = Form.Item;
const { Row, Col } = Grid;
const { Column } = Table;

@ConfigProvider.config
class ServiceList extends React.Component {
  static displayName = 'ServiceList';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.editServiceDialog = React.createRef();
    this.state = {
      loading: false,
      total: 0,
      pageSize: 10,
      currentPage: 1,
      keyword: '',
      dataSource: [],
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

  queryServiceList() {
    const { currentPage, pageSize, keyword } = this.state;
    const parameter = [`startPg=${currentPage}`, `pgSize=${pageSize}`, `keyword=${keyword}`];
    request({
      url: `v1/ns/catalog/serviceList?${parameter.join('&')}`,
      beforeSend: () => this.openLoading(),
      success: ({ count = 0, serviceList = [] } = {}) => {
        this.setState({
          dataSource: serviceList,
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
    setTimeout(() => this.queryServiceList());
  };

  deleteService(serviceName) {
    const { locale = {} } = this.props;
    const { prompt, promptDelete } = locale;
    Dialog.confirm({
      title: prompt,
      content: promptDelete,
      onOk: () => {
        request({
          method: 'DELETE',
          url: `v1/ns/service?serviceName=${serviceName}`,
          dataType: 'text',
          beforeSend: () => this.openLoading(),
          success: res => {
            if (res !== 'ok') {
              Message.error(res);
              return;
            }
            this.queryServiceList();
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
      pubNoData,
      serviceList,
      serviceName,
      serviceNamePlaceholder,
      query,
      create,
      operation,
      detail,
      deleteAction,
    } = locale;
    const { keyword, nowNamespaceName, nowNamespaceId } = this.state;
    const { init, getValue } = this.field;
    this.init = init;
    this.getValue = getValue;

    return (
      <div className="main-container service-management">
        <Loading
          shape="flower"
          style={{ position: 'relative' }}
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
            <span className="title-item">{serviceList}</span>
            <span className="title-item">|</span>
            <span className="title-item">{nowNamespaceName}</span>
            <span className="title-item">{nowNamespaceId}</span>
          </h3>
          <Row className="demo-row" style={{ marginBottom: 10, padding: 0 }}>
            <Col span="24">
              <Form inline field={this.field}>
                <FormItem label={serviceName}>
                  <Input
                    placeholder={serviceNamePlaceholder}
                    style={{ width: 200 }}
                    value={keyword}
                    onChange={keyword => this.setState({ keyword })}
                  />
                </FormItem>
                <FormItem label="">
                  <Button
                    type="primary"
                    onClick={() => this.setState({ currentPage: 1 }, () => this.queryServiceList())}
                    style={{ marginRight: 10 }}
                  >
                    {query}
                  </Button>
                </FormItem>
                <FormItem label="" style={{ float: 'right' }}>
                  <Button type="secondary" onClick={() => this.openEditServiceDialog()}>
                    {create}
                  </Button>
                </FormItem>
              </Form>
            </Col>
          </Row>
          <Row style={{ padding: 0 }}>
            <Col span="24" style={{ padding: 0 }}>
              <Table
                dataSource={this.state.dataSource}
                fixedHeader
                maxBodyHeight={530}
                locale={{ empty: pubNoData }}
                getRowProps={row => this.rowColor(row)}
              >
                <Column title={locale.columnServiceName} dataIndex="name" />
                <Column title={locale.columnClusterCount} dataIndex="clusterCount" />
                <Column title={locale.columnIpCount} dataIndex="ipCount" />
                <Column
                  title={locale.columnHealthyInstanceCount}
                  dataIndex="healthyInstanceCount"
                />
                <Column
                  title={operation}
                  align="center"
                  cell={(value, index, record) => (
                    <div>
                      <Button
                        type="normal"
                        onClick={() =>
                          this.props.history.push(`/serviceDetail?name=${record.name}`)
                        }
                      >
                        {detail}
                      </Button>
                      <Button
                        style={{ marginLeft: 12 }}
                        type="normal"
                        onClick={() => this.deleteService(record.name)}
                      >
                        {deleteAction}
                      </Button>
                    </div>
                  )}
                />
              </Table>
            </Col>
          </Row>
          {this.state.total > this.state.pageSize && (
            <div style={{ marginTop: 10, textAlign: 'right' }}>
              <Pagination
                current={this.state.currentPage}
                total={this.state.total}
                pageSize={this.state.pageSize}
                onChange={currentPage =>
                  this.setState({ currentPage }, () => this.queryServiceList())
                }
              />
            </div>
          )}
        </Loading>
        <EditServiceDialog
          ref={this.editServiceDialog}
          openLoading={() => this.openLoading()}
          closeLoading={() => this.closeLoading()}
          queryServiceList={() => this.setState({ currentPage: 1 }, () => this.queryServiceList())}
        />
      </div>
    );
  }
}

export default ServiceList;
