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
import RegionGroup from '../../../components/RegionGroup';
import { getParams, request } from '../../../globalLib';
import {
  ConfigProvider,
  Field,
  Form,
  Grid,
  Input,
  Loading,
  Pagination,
  Select,
  Table,
} from '@alifd/next';

import './index.scss';

const FormItem = Form.Item;
const { Row, Col } = Grid;

@ConfigProvider.config
class ListeningToQuery extends React.Component {
  static displayName = 'ListeningToQuery';

  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      value: '',
      visible: false,
      loading: false,
      total: 0,
      pageSize: 10,
      currentPage: 1,
      dataSource: [],
    };
    this.field = new Field(this);
    this.group = getParams('listeningGroup') || '';
    this.dataId = getParams('listeningDataId') || '';
    this.serverId = getParams('listeningServerId') || '';
    this.tenant = getParams('namespace') || '';
  }

  componentDidMount() {
    this.field.setValue('type', 0);
    this.field.setValue('group', this.group);
    this.field.setValue('dataId', this.dataId);
  }

  onSearch() {}

  onChange() {}

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  queryTrackQuery = () => {
    const self = this;
    let queryUrl = '';
    const type = this.getValue('type');
    if (type === 1) {
      const ip = this.getValue('ip');
      queryUrl = `v1/cs/listener?ip=${ip}`;
      const tenant = window.nownamespace || getParams('namespace') || '';
      if (tenant) {
        queryUrl += `&tenant=${tenant}`;
      }
    } else {
      const dataId = this.getValue('dataId');
      const group = this.getValue('group');
      if (!dataId || !group) return false;
      queryUrl = `v1/cs/configs/listener?dataId=${dataId}&group=${group}`;
    }
    request({
      url: queryUrl,
      beforeSend() {
        self.openLoading();
      },
      success(data) {
        if (data.collectStatus === 200) {
          const dataSoureTmp = [];
          const status = data.lisentersGroupkeyStatus;
          for (const key in status) {
            if (type === 1) {
              const obj = {};
              let [dataId, group] = key.split('+');
              obj.dataId = dataId;
              obj.group = group;
              obj.md5 = status[key];
              dataSoureTmp.push(obj);
            } else {
              const obj = {};
              obj.ip = key;
              obj.md5 = status[key];
              dataSoureTmp.push(obj);
            }
          }
          self.setState({
            dataSource: dataSoureTmp || [],
            total: dataSoureTmp.length || 0,
          });
        }
      },
      complete() {
        self.closeLoading();
      },
    });
  };

  showMore() {}

  changePage = value => {
    this.setState({
      currentPage: value,
    });
  };

  resetSearch() {
    this.field.reset();
    this.forceUpdate();
  }

  renderStatus(values, index, record) {
    const { locale = {} } = this.props;
    return (
      <div>
        {record.pushStatus === true ? (
          <span style={{ color: 'green' }}>{locale.success}</span>
        ) : (
          <span style={{ color: 'red' }}>{locale.failure}</span>
        )}
      </div>
    );
  }

  getQueryLater = () => {
    setTimeout(() => {
      // 子组件可能优先于父组件所以延迟执行
      this.queryTrackQuery();
    });
  };

  render() {
    const { locale = {} } = this.props;
    const { init, getValue } = this.field;
    this.init = init;
    this.getValue = getValue;

    const selectDataSource = [
      {
        label: locale.configuration,
        value: 0,
      },
      {
        label: 'IP',
        value: 1,
      },
    ];
    return (
      <>
        <Loading
          shape="flower"
          style={{ position: 'relative' }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <RegionGroup left={locale.listenerQuery} namespaceCallBack={this.getQueryLater} />
          <Row className="demo-row" style={{ marginBottom: 10, padding: 0 }}>
            <Col span="24">
              <Form inline field={this.field}>
                <FormItem label={`${locale.queryDimension}:`}>
                  <Select
                    dataSource={selectDataSource}
                    style={{ width: 200 }}
                    {...this.init('type')}
                    onChange={value => {
                      this.field.setValue('type', value);
                      this.queryTrackQuery();
                    }}
                  />
                </FormItem>
                <FormItem
                  label="Data ID:"
                  style={{
                    display: this.getValue('type') === 0 ? '' : 'none',
                  }}
                  required
                >
                  <Input
                    placeholder={locale.pleaseEnterTheDataId}
                    style={{ width: 200 }}
                    {...this.init('dataId', {
                      rules: [
                        {
                          required: true,
                          message: locale.dataIdCanNotBeEmpty,
                        },
                      ],
                    })}
                  />
                </FormItem>
                <FormItem
                  label="Group:"
                  style={{
                    display: this.getValue('type') === 0 ? '' : 'none',
                  }}
                  required
                >
                  <Input
                    placeholder={locale.pleaseInputGroup}
                    style={{ width: 200 }}
                    {...this.init('group', {
                      rules: [
                        {
                          required: true,
                          message: locale.groupCanNotBeEmpty,
                        },
                      ],
                    })}
                  />
                </FormItem>
                <FormItem
                  label="IP:"
                  style={{
                    display: this.getValue('type') === 0 ? 'none' : '',
                  }}
                >
                  <Input
                    placeholder={locale.pleaseInputIp}
                    style={{ width: 200, boxSize: 'border-box' }}
                    {...this.init('ip')}
                  />
                </FormItem>
                <FormItem label="">
                  <Form.Submit
                    validate
                    type="primary"
                    onClick={this.queryTrackQuery}
                    style={{ marginRight: 10 }}
                  >
                    {locale.query}
                  </Form.Submit>
                </FormItem>
              </Form>
            </Col>
          </Row>
          <div style={{ position: 'relative' }}>
            <h3
              style={{
                height: 28,
                lineHeight: '28px',
                paddingLeft: 10,
                borderLeft: '3px solid #09c',
                margin: 0,
                marginBottom: 10,
                fontSize: 16,
              }}
            >
              {locale.queryResultsQuery}
              <strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>
              {locale.articleMeetRequirementsConfiguration}
            </h3>
          </div>
          <Row style={{ padding: 0 }}>
            <Col span="24" style={{ padding: 0 }}>
              {this.getValue('type') === 1 ? (
                <Table
                  dataSource={this.state.dataSource}
                  fixedHeader
                  maxBodyHeight={500}
                  locale={{ empty: locale.pubNoData }}
                >
                  <Table.Column title="Data ID" dataIndex="dataId" />
                  <Table.Column title="Group" dataIndex="group" />
                  <Table.Column title="MD5" dataIndex="md5" />
                </Table>
              ) : (
                <Table
                  dataSource={this.state.dataSource}
                  fixedHeader
                  maxBodyHeight={400}
                  locale={{ empty: locale.pubNoData }}
                >
                  <Table.Column title="IP" dataIndex="ip" />
                  <Table.Column title="MD5" dataIndex="md5" />
                </Table>
              )}
            </Col>
          </Row>
          <div style={{ marginTop: 10, textAlign: 'right' }}>
            <Pagination
              current={this.state.currentPage}
              total={this.state.total}
              pageSize={this.state.pageSize}
              onChange={this.changePage}
            />
            ,
          </div>
        </Loading>
      </>
    );
  }
}

export default ListeningToQuery;
