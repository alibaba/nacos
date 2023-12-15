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
  Tag,
  Collapse,
  Form,
  Grid,
  Input,
  Loading,
  Pagination,
  Table,
  Dialog,
  ConfigProvider,
  Message,
} from '@alifd/next';
import { request } from '../../../globalLib';
import axios from 'axios';
import PageTitle from '../../../components/PageTitle';

import './ClusterNodeList.scss';

const FormItem = Form.Item;
const { Row, Col } = Grid;
const { Column } = Table;
const { Panel } = Collapse;

@ConfigProvider.config
class ClusterNodeList extends React.Component {
  static displayName = 'ClusterNodeList';

  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
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

  componentDidMount() {
    this.getQueryLater();
  }

  openLoading() {
    this.setState({ loading: true });
  }

  closeLoading() {
    this.setState({ loading: false });
  }

  queryClusterStateList() {
    const { currentPage, pageSize, keyword, withInstances = false } = this.state;
    const parameter = [
      `withInstances=${withInstances}`,
      `pageNo=${currentPage}`,
      `pageSize=${pageSize}`,
      `keyword=${keyword}`,
    ];
    request({
      url: `v1/core/cluster/nodes?${parameter.join('&')}`,
      beforeSend: () => this.openLoading(),
      success: ({ count = 0, data = [] } = {}) => {
        this.setState({
          dataSource: data,
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

  leave(nodes) {
    const { locale = {} } = this.props;
    const accessToken = JSON.parse(localStorage.token || '{}').accessToken;
    this.openLoading();
    axios
      .post(`v1/core/cluster/server/leave?accessToken=${accessToken}`, nodes)
      .then(response => {
        if (response.data.code === 200) {
          Message.success(locale.leaveSucc);
        } else {
          const errorMessage = response.data.message || locale.leaveFail;
          this.showErrorDialog(locale.leavePrompt, errorMessage);
        }

        this.queryClusterStateList();
        this.closeLoading();
      })
      .catch(error => {
        const errorMessage = error.response?.data?.message || locale.leaveFail;
        this.showErrorDialog(locale.leavePrompt, errorMessage);

        this.queryClusterStateList();
        this.closeLoading();
      });
  }

  showErrorDialog(title, content) {
    Dialog.alert({
      title,
      content,
    });
  }

  showLeaveDialog(value) {
    const { locale = {} } = this.props;
    Dialog.confirm({
      title: locale.confirm,
      content: locale.confirmTxt,
      onOk: () => this.leave([value]),
      onCancel: () => {},
    });
  }

  renderCol(value, index, record) {
    const { locale = {} } = this.props;
    return (
      <Button onClick={this.showLeaveDialog.bind(this, value)} type="primary" warning>
        {locale.leave}
      </Button>
    );
  }

  getQueryLater = () => {
    setTimeout(() => this.queryClusterStateList());
  };

  setNowNameSpace = (nowNamespaceName, nowNamespaceId) =>
    this.setState({
      nowNamespaceName,
      nowNamespaceId,
    });

  rowColor = row => ({ className: !row.voteFor ? '' : '' });

  render() {
    const { locale = {} } = this.props;
    const { pubNoData, clusterNodeList, nodeIp, nodeIpPlaceholder, query } = locale;
    const { keyword, nowNamespaceName, nowNamespaceId } = this.state;
    const { init, getValue } = this.field;
    this.init = init;
    this.getValue = getValue;

    return (
      <div className="main-container cluster-management">
        <Loading
          shape="flower"
          style={{ position: 'relative', width: '100%' }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <PageTitle title={clusterNodeList} />
          <Row className="demo-row" style={{ marginBottom: 10, padding: 0 }}>
            <Col span="24">
              <Form inline field={this.field}>
                <FormItem label={nodeIp}>
                  <Input
                    placeholder={nodeIpPlaceholder}
                    style={{ width: 200 }}
                    value={keyword}
                    onChange={keyword => this.setState({ keyword })}
                    onPressEnter={() =>
                      this.setState({ currentPage: 1 }, () => this.queryClusterStateList())
                    }
                  />
                </FormItem>
                <FormItem label="">
                  <Button
                    type="primary"
                    onClick={() =>
                      this.setState({ currentPage: 1 }, () => this.queryClusterStateList())
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
                rowProps={row => this.rowColor(row)}
              >
                <Column title={locale.nodeIp} dataIndex="address" width="20%" align="center" />
                <Column
                  title={locale.nodeState}
                  dataIndex="state"
                  width="10%"
                  align="center"
                  cell={function(value, index, record) {
                    if (value === 'UP') {
                      return (
                        <Tag key={`p_p_${value}`} type="primary" color="green">
                          {value}
                        </Tag>
                      );
                    }
                    if (value === 'DOWN') {
                      return (
                        <Tag key={`p_p_${value}`} type="primary" color="red">
                          {value}
                        </Tag>
                      );
                    }
                    if (value === 'SUSPICIOUS') {
                      return (
                        <Tag key={`p_p_${value}`} type="primary" color="orange">
                          {value}
                        </Tag>
                      );
                    }
                    return (
                      <Tag key={`p_p_${value}`} type="primary" color="turquoise">
                        {value}
                      </Tag>
                    );
                  }}
                />
                <Column
                  title={locale.extendInfo}
                  dataIndex="extendInfo"
                  width="50%"
                  cell={function(value, index, record) {
                    function showCollapse() {
                      const collapse = (
                        <Collapse>
                          <Panel title={locale.extendInfo}>
                            <ul>
                              <li>
                                <pre>{JSON.stringify(value, null, 4)}</pre>
                              </li>
                            </ul>
                          </Panel>
                        </Collapse>
                      );
                      return collapse;
                    }

                    return showCollapse();
                  }}
                />
                <Column
                  title={locale.operation}
                  dataIndex="address"
                  width="20%"
                  align="center"
                  cell={this.renderCol.bind(this)}
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
                  this.setState({ currentPage }, () => this.queryClusterStateList())
                }
              />
            </div>
          )}
        </Loading>
      </div>
    );
  }
}

export default ClusterNodeList;
