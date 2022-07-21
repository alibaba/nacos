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
import { ConfigProvider, Field, Form, Loading, Pagination, Select, Table } from '@alifd/next';
import RegionGroup from 'components/RegionGroup';
import { getParams, setParams, request } from '@/globalLib';

import './index.scss';
import DiffEditorDialog from '../../../components/DiffEditorDialog';

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
    this.diffEditorDialog = React.createRef();
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
    this.getConfigList();
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
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.goCompare.bind(this, record)}>
          {locale.compare}
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

  goCompare(record) {
    let tenant = getParams('namespace') || '';
    let serverId = getParams('serverId') || 'center';
    this.getConfig(-1, tenant, serverId, this.dataId, this.group).then(lasted => {
      this.getHistoryConfig(record.id, this.dataId, this.group).then(selected => {
        this.diffEditorDialog.current.getInstance().openDialog(selected.content, lasted.content);
      });
    });
  }

  /**
   * 获取最新版本配置
   * @param id
   * @param tenant
   * @param serverId
   * @param dataId
   * @param group
   * @returns {Promise<unknown>}
   */
  getConfig(id, tenant, serverId, dataId, group) {
    return new Promise((resolve, reject) => {
      const { locale = {} } = this.props;
      const self = this;
      this.tenant = tenant;
      this.serverId = tenant;
      const url = `v1/cs/configs?show=all&dataId=${dataId}&group=${group}`;
      request({
        url,
        beforeSend() {
          self.openLoading();
        },
        success(result) {
          if (result != null) {
            resolve(result);
          }
        },
        complete() {
          self.closeLoading();
        },
      });
    });
  }

  /**
   * 获取历史版本配置数据
   * @param nid
   * @param dataId
   * @param group
   * @returns {Promise<unknown>}
   */
  getHistoryConfig(nid, dataId, group) {
    return new Promise((resolve, reject) => {
      const { locale = {} } = this.props;
      const self = this;
      request({
        url: `v1/cs/history?dataId=${dataId}&group=${group}&nid=${nid}`,
        success(result) {
          if (result != null) {
            resolve(result);
          }
        },
      });
    });
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

  getConfigList() {
    const { locale = {} } = this.props;
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    const self = this;
    request({
      url: `v1/cs/history/configs?tenant=${this.tenant}`,
      success(result) {
        if (result != null) {
          const dataIdList = [];
          const groupList = [];
          for (let i = 0; i < result.length; i++) {
            dataIdList.push({
              value: result[i].dataId,
              label: result[i].dataId,
            });
            groupList.push({
              value: result[i].group,
              label: result[i].group,
            });
          }
          self.setState({
            dataIds: dataIdList,
            groups: groupList,
          });
        }
      },
    });
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
                <Select
                  style={{ width: 200 }}
                  size="medium"
                  hasArrow
                  mode="single"
                  placeholder={locale.dataId}
                  dataSource={this.state.dataIds}
                  hasClear
                  showSearch
                  onSearch={val => {
                    const { dataIds } = this.state;
                    if (!dataIds.includes(val)) {
                      this.setState({ dataIds: dataIds.concat(val) });
                    }
                  }}
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
                <Select
                  style={{ width: 200 }}
                  size="medium"
                  hasArrow
                  mode="single"
                  placeholder={locale.group}
                  dataSource={this.state.groups}
                  hasClear
                  showSearch
                  onSearch={val => {
                    const { groups } = this.state;
                    if (!groups.includes(val)) {
                      this.setState({ groups: groups.concat(val) });
                    }
                  }}
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
          <DiffEditorDialog
            ref={this.diffEditorDialog}
            title={locale.historyCompareTitle}
            currentArea={locale.historyCompareSelectedVersion}
            originalArea={locale.historyCompareLastVersion}
          />
        </Loading>
      </div>
    );
  }
}

export default HistoryRollback;
