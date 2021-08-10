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
import { ConfigProvider, Field, Form, Input, Loading, Pagination, Table } from '@alifd/next';
import RegionGroup from 'components/RegionGroup';
import { getParams, setParams, request } from '@/globalLib';

import './index.scss';

@ConfigProvider.config
class HistoryRollback extends React.Component {
  static displayName = 'HistoryRollback';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);

    this.field = new Field(this);
    this.appName = getParams('appName') || '';
    this.preAppName = this.appName;
    this.group = getParams('historyGroup') || '';
    this.preGroup = this.group;

    this.dataId = getParams('historyDataId') || '';
    this.preDataId = this.dataId;
    this.serverId = getParams('historyServerId') || '';
    this.state = {
      value: '',
      visible: false,
      total: 0,
      pageSize: 10,
      currentPage: 1,
      dataSource: [],
      fieldValue: [],
      showAppName: false,
      showgroup: false,
      dataId: this.dataId,
      group: this.group,
      appName: this.appName,
      selectValue: [],
      loading: false,
    };
  }

  componentDidMount() {
    this.field.setValue('group', this.group);
    this.field.setValue('dataId', this.dataId);
    // this.getData()
  }

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

  cleanAndGetData(needclean = false) {
    if (needclean) {
      this.dataId = '';
      this.group = '';
      this.setState({
        group: '',
        dataId: '',
      });
      setParams({
        group: '',
        dataId: '',
      });
    }

    this.getData();
  }

  getData(pageNo = 1) {
    const self = this;
    this.serverId = getParams('serverId') || '';
    if (!this.dataId) return false;
    request({
      beforeSend() {
        self.openLoading();
      },
      url: `v1/cs/history?search=accurate&dataId=${this.dataId}&group=${this.group}&&pageNo=${pageNo}&pageSize=${this.state.pageSize}`,
      success(data) {
        if (data != null) {
          self.setState({
            dataSource: data.pageItems || [],
            total: data.totalCount,
            currentPage: data.pageNumber,
          });
        }
      },
      complete() {
        self.closeLoading();
      },
    });
  }

  renderCol(value, index, record) {
    const { locale = {} } = this.props;
    return (
      <div>
        <a onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>
          {locale.details}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.goRollBack.bind(this, record)}>
          {locale.rollback}
        </a>
      </div>
    );
  }

  changePage(value) {
    this.setState({
      currentPage: value,
    });
    this.getData(value);
  }

  chooseFieldChange(fieldValue) {
    this.setState({
      fieldValue,
    });
  }

  selectAll() {
    this.dataId = this.field.getValue('dataId');
    this.group = this.field.getValue('group');
    if (!this.dataId || !this.group) {
      return false;
    }
    if (this.dataId !== this.preDataId) {
      setParams('dataId', this.dataId);
      this.preDataId = this.dataId;
    }
    if (this.group !== this.preGroup) {
      setParams('group', this.preGroup);
      this.preGroup = this.group;
    }
    this.getData();
  }

  resetAll() {
    this.dataId = '';
    this.group = '';
    this.setState({
      selectValue: [],
      dataId: '',
      appName: '',
      group: '',
      showAppName: false,
      showgroup: false,
    });
    setParams({
      group: '',
      dataId: '',
    });
  }

  chooseEnv(value) {}

  goDetail(record) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `/historyDetail?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
        record.group
      }&nid=${record.id}&namespace=${this.tenant}`
    );
  }

  goRollBack(record) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `/configRollback?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
        record.group
      }&nid=${record.id}&namespace=${this.tenant}&nid=${record.id}`
    );
  }

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    this.init = init;
    return (
      <div style={{ padding: 10 }}>
        <Loading
          shape="flower"
          style={{ position: 'relative', width: '100%' }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <RegionGroup
            left={locale.toConfigure}
            namespaceCallBack={this.cleanAndGetData.bind(this)}
          />
          <div>
            <Form inline field={this.field}>
              <Form.Item label="Data ID:" required>
                <Input
                  placeholder={locale.dataId}
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
              </Form.Item>
              <Form.Item label="Group:" required>
                <Input
                  placeholder={locale.group}
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
              </Form.Item>

              <Form.Item label="">
                <Form.Submit
                  validate
                  type="primary"
                  onClick={this.selectAll.bind(this)}
                  style={{ marginRight: 10 }}
                >
                  {locale.query}
                </Form.Submit>
              </Form.Item>
            </Form>
          </div>
          <div style={{ position: 'relative', width: '100%', overflow: 'hidden', height: '40px' }}>
            <h3
              style={{
                height: 30,
                width: '100%',
                lineHeight: '30px',
                padding: 0,
                margin: 0,
                paddingLeft: 10,
                borderLeft: '3px solid #09c',
                fontSize: 16,
              }}
            >
              {locale.queryResult}
              <strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>
              {locale.articleMeet}
            </h3>
          </div>
          <div>
            <Table dataSource={this.state.dataSource} locale={{ empty: locale.pubNoData }}>
              <Table.Column title="Data ID" dataIndex="dataId" />
              <Table.Column title="Group" dataIndex="group" />
              <Table.Column title={locale.operator} dataIndex="srcUser" />
              <Table.Column
                title={locale.lastUpdateTime}
                dataIndex="lastModifiedTime"
                cell={val => {
                  if (!val) {
                    return '';
                  }
                  try {
                    const date = new Date(val);
                    return date.toLocaleString(locale.momentLocale);
                  } catch (e) {
                    return '';
                  }
                }}
              />
              <Table.Column title={locale.operation} cell={this.renderCol.bind(this)} />
            </Table>
          </div>
          <div style={{ marginTop: 10, textAlign: 'right' }}>
            <Pagination
              current={this.state.currentPage}
              total={this.state.total}
              pageSize={this.state.pageSize}
              onChange={this.changePage.bind(this)}
            />
            ,
          </div>
        </Loading>
      </div>
    );
  }
}

export default HistoryRollback;
