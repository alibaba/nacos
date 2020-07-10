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
  Icon,
  Collapse,
  Form,
  Grid,
  Input,
  Loading,
  Pagination,
  Table,
  ConfigProvider,
} from '@alifd/next';
import { request } from '../../../globalLib';
import RegionGroup from '../../../components/RegionGroup';

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
          <div style={{ marginTop: -15 }}>
            <RegionGroup
              setNowNameSpace={this.setNowNameSpace}
              namespaceCallBack={this.getQueryLater}
            />
          </div>
          <h3 className="page-title">
            <span className="title-item">{clusterNodeList}</span>
            <span className="title-item">|</span>
            <span className="title-item">{nowNamespaceName}</span>
            <span className="title-item">{nowNamespaceId}</span>
          </h3>
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
                getRowProps={row => this.rowColor(row)}
              >
                <Column title={locale.nodeIp} dataIndex="address" width="30%" />
                <Column
                  title={locale.nodeState}
                  dataIndex="state"
                  width="20%"
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
                          <Panel title="节点元数据">
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
