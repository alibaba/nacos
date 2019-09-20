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
} from '@alifd/next';
import { request } from '../../../globalLib';
import RegionGroup from '../../../components/RegionGroup';
import EditServiceDialog from '../ServiceDetail/EditServiceDialog';
import ShowServiceCodeing from 'components/ShowCodeing/ShowServiceCodeing';

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
    this.showcode = React.createRef();
    this.state = {
      loading: false,
      total: 0,
      pageSize: 10,
      currentPage: 1,
      dataSource: [],
      search: {
        serviceName: '',
        groupName: '',
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

  queryServiceList() {
    const { currentPage, pageSize, search, withInstances = false, hasIpCount } = this.state;
    const parameter = [
      `hasIpCount=${hasIpCount}`,
      `withInstances=${withInstances}`,
      `pageNo=${currentPage}`,
      `pageSize=${pageSize}`,
      `serviceNameParam=${search.serviceName}`,
      `groupNameParam=${search.groupName}`,
    ];
    request({
      url: `v1/ns/catalog/services?${parameter.join('&')}`,
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

  showcode = () => {
    setTimeout(() => this.queryServiceList());
  };

  /**
   *
   * Added method to open sample code window
   * @author yongchao9  #2019年05月18日 下午5:46:28
   *
   */
  showSampleCode(record) {
    this.showcode.current.getInstance().openDialog(record);
  }

  deleteService(service) {
    const { locale = {} } = this.props;
    const { prompt, promptDelete } = locale;
    Dialog.confirm({
      title: prompt,
      content: promptDelete,
      onOk: () => {
        request({
          method: 'DELETE',
          url: `v1/ns/service?serviceName=${service.name}&groupName=${service.groupName}`,
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
      groupName,
      groupNamePlaceholder,
      hiddenEmptyService,
      query,
      create,
      operation,
      detail,
      sampleCode,
      deleteAction,
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
            <span className="title-item">{serviceList}</span>
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
                <FormItem label={serviceName}>
                  <Input
                    placeholder={serviceNamePlaceholder}
                    style={{ width: 200 }}
                    value={search.serviceName}
                    onChange={serviceName => this.setState({ search: { ...search, serviceName } })}
                    onPressEnter={() =>
                      this.setState({ currentPage: 1 }, () => this.queryServiceList())
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
                      this.setState({ currentPage: 1 }, () => this.queryServiceList())
                    }
                  />
                </FormItem>
                <Form.Item label={`${hiddenEmptyService}:`}>
                  <Switch
                    checked={hasIpCount}
                    onChange={hasIpCount =>
                      this.setState({ hasIpCount, currentPage: 1 }, () => {
                        localStorage.setItem('hasIpCount', hasIpCount);
                        this.queryServiceList();
                      })
                    }
                  />
                </Form.Item>
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
                locale={{ empty: pubNoData }}
                getRowProps={row => this.rowColor(row)}
              >
                <Column title={locale.columnServiceName} dataIndex="name" />
                <Column title={locale.groupName} dataIndex="groupName" />
                <Column title={locale.columnClusterCount} dataIndex="clusterCount" />
                <Column title={locale.columnIpCount} dataIndex="ipCount" />
                <Column
                  title={locale.columnHealthyInstanceCount}
                  dataIndex="healthyInstanceCount"
                />
                <Column title={locale.columnTriggerFlag} dataIndex="triggerFlag" />
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
                            `/serviceDetail?name=${record.name}&groupName=${record.groupName}`
                          )
                        }
                        style={{ marginRight: 5 }}
                      >
                        {detail}
                      </a>
                      <span style={{ marginRight: 5 }}>|</span>
                      <a style={{ marginRight: 5 }} onClick={() => this.showSampleCode(record)}>
                        {sampleCode}
                      </a>
                      <span style={{ marginRight: 5 }}>|</span>
                      <a onClick={() => this.deleteService(record)} style={{ marginRight: 5 }}>
                        {deleteAction}
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
                  this.setState({ currentPage }, () => this.queryServiceList())
                }
              />
            </div>
          )}
        </Loading>
        <ShowServiceCodeing ref={this.showcode} />
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
