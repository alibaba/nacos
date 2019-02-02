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
  Checkbox,
  Collapse,
  ConfigProvider,
  Dialog,
  Dropdown,
  Field,
  Form,
  Icon,
  Input,
  Loading,
  Menu,
  Pagination,
  Select,
  Table,
} from '@alifd/next';
import BatchHandle from 'components/BatchHandle';
import RegionGroup from 'components/RegionGroup';
import ShowCodeing from 'components/ShowCodeing';
import DeleteDialog from 'components/DeleteDialog';
import DashboardCard from './DashboardCard';
import { getParams, setParams, request, aliwareIntl } from '@/globalLib';

import './index.scss';
import { LANGUAGE_KEY } from '../../../constants';

const { Panel } = Collapse;

@ConfigProvider.config
class ConfigurationManagement extends React.Component {
  static displayName = 'ConfigurationManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.deleteDialog = React.createRef();
    this.showcode = React.createRef();
    this.field = new Field(this);
    this.appName = getParams('appName') || getParams('edasAppId') || '';
    this.preAppName = this.appName;
    this.group = getParams('group') || '';
    this.preGroup = this.group;
    this.dataId = getParams('dataId') || '';
    this.preDataId = this.dataId;
    this.serverId = getParams('serverId') || 'center';
    this.edasAppId = getParams('edasAppId') || '';
    this.edasAppName = getParams('edasAppName') || '';
    this.inApp = this.edasAppId;
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
      config_tags: [],
      tagLst: [],
      selectValue: [],
      loading: false,
      groupList: [],
      groups: [],
      tenant: true,
      nownamespace_id: window.nownamespace || '',
      nownamespace_name: window.namespaceShowName || '',
      selectedRecord: [],
      selectedKeys: [],
      hasdash: false,
      isCn: true,
      contentList: [],
      isAdvancedQuery: false,
      isCheckAll: false,
    };
    const obj = {
      dataId: this.dataId || '',
      group: this.preGroup || '',
      appName: this.appName || '',
    };
    setParams(obj);
    this.batchHandle = null;
    this.toggleShowQuestionnaire = this.toggleShowQuestionnaire.bind(this);
  }

  componentDidMount() {
    const { locale = {} } = this.props;
    // this.getGroup();
    this.setIsCn();
    if (window._getLink && window._getLink('isCn') === 'true') {
      if (!this.checkQuestionnaire()) {
        if (window.location.host === 'acm.console.aliyun.com') {
          Dialog.alert({
            title: locale.questionnaire2,
            style: {
              width: '60%',
            },
            content: (
              <div>
                <div style={{ fontSize: '15px', lineHeight: '22px' }}>
                  {locale.ad}
                  <a href={'https://survey.aliyun.com/survey/k0BjJ2ARC'} target={'_blank'}>
                    {locale.questionnaire2}
                  </a>
                </div>
                <div style={{ fontSize: '15px' }}>
                  {locale.noLongerDisplay4}
                  <Checkbox onChange={this.toggleShowQuestionnaire} />
                </div>
              </div>
            ),
          });
        }
      }
    }
  }

  setIsCn() {
    this.setState({ isCn: localStorage.getItem(LANGUAGE_KEY) === 'zh-CN' });
  }

  /**
   * 获取概览页数据
   */
  getContentList() {
    request({
      url: 'com.alibaba.nacos.service.dashlist', // 以 com.alibaba. 开头最终会转换为真正的url地址
      data: {},
      $data: {}, // 替换请求url路径中{}占位符的内容
      success: res => {
        if (res.code === 200 && res.data) {
          if (res.data.length === 0) {
            this.setState({
              hasdash: false,
            });
          } else {
            this.setState({
              hasdash: true,
              contentList: res.data,
            });
          }
        }
      },
    });
  }

  toggleShowQuestionnaire(value) {
    if (value) {
      localStorage.setItem('acm_questionnaire', 1);
    } else {
      localStorage.removeItem('acm_questionnaire');
    }
  }

  checkQuestionnaire() {
    const acm_questionnaire = localStorage.getItem('acm_questionnaire');
    if (acm_questionnaire) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * 回车事件
   */
  keyDownSearch(e) {
    const theEvent = e || window.event;
    const code = theEvent.keyCode || theEvent.which || theEvent.charCode;
    if (code === 13) {
      this.getData();
      return false;
    }
    return true;
  }

  navTo(url, record) {
    this.serverId = getParams('serverId') || '';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `${url}?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
        record.group
      }&namespace=${this.tenant}`
    );
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

  UNSAFE_componentWillMount() {
    window.addEventListener('keydown', this.keyDownSearch.bind(this), false);
  }

  componentWillUnmount() {
    window.removeEventListener('keydown', this.keyDownSearch.bind(this));
  }

  onSearch() {}

  onChange() {}

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

  getData(pageNo = 1, clearSelect = true) {
    const self = this;
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.serverId = getParams('serverId') || '';
    let urlPrefix = '';
    if (this.dataId.indexOf('*') !== -1 || this.group.indexOf('*') !== -1) {
      urlPrefix = 'v1/cs/configs?search=blur';
    } else {
      urlPrefix = 'v1/cs/configs?search=accurate';
    }

    request({
      url: `${urlPrefix}&dataId=${this.dataId}&group=${this.group}&appName=${
        this.appName
      }&config_tags=${this.state.config_tags || ''}&pageNo=${pageNo}&pageSize=${
        this.state.pageSize
      }`,
      beforeSend() {
        self.openLoading();
      },
      success(data) {
        if (data != null) {
          self.setState({
            dataSource: data.pageItems,
            total: data.totalCount,
            currentPage: data.pageNumber,
          });
          if (clearSelect) {
            self.setState({
              selectedRecord: [],
              selectedKeys: [],
            });
          }
        }
        self.setState({
          tenant: self.tenant,
        });
      },
      error(data) {
        self.setState({
          dataSource: [],
          total: 0,
          currentPage: 0,
        });
      },
      complete() {
        self.closeLoading();
      },
    });
  }

  showMore() {}

  chooseNav(record, key) {
    const self = this;
    switch (key) {
      case 'nav1':
        self.navTo('/historyRollback', record);
        break;
      case 'nav2':
        self.navTo('/pushTrajectory', record);
        break;
      default:
      case 'nav3':
        self.navTo('/listeningToQuery', record);
        break;
    }
  }

  removeConfig(record) {
    const { locale = {} } = this.props;
    const self = this;
    Dialog.confirm({
      title: locale.removeConfiguration,
      content: (
        <div style={{ marginTop: '-20px' }}>
          <h3>{locale.sureDelete}</h3>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
            <span style={{ color: '#c7254e' }}>{record.dataId}</span>
          </p>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
            <span style={{ color: '#c7254e' }}>{record.group}</span>
          </p>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>{locale.environment}</span>
            <span style={{ color: '#c7254e' }}>{self.serverId || ''}</span>
          </p>
        </div>
      ),
      onOk: () => {
        const url = `v1/cs/configs?dataId=${record.dataId}&group=${record.group}`;
        request({
          url,
          type: 'delete',
          success(res) {
            const _payload = {};

            _payload.title = locale.configurationManagement;
            _payload.content = '';
            _payload.dataId = record.dataId;
            _payload.group = record.group;
            if (res === true) {
              _payload.isok = true;
            } else {
              _payload.isok = false;
              _payload.message = res.message;
            }
            self.deleteDialog.current.getInstance().openDialog(_payload);
            self.getData();
          },
        });
      },
    });
  }

  renderLastTime(value, index, record) {
    return <div>{aliwareIntl.intlNumberFormat(record.lastModifiedTime)}</div>;
  }

  showCode(record) {
    this.showcode.current.getInstance().openDialog(record);
  }

  renderCol(value, index, record) {
    const { locale = {} } = this.props;
    return (
      <div>
        <a onClick={this.goDetail.bind(this, record)} style={{ marginRight: 5 }}>
          {locale.details}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.showCode.bind(this, record)}>
          {locale.sampleCode}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.goEditor.bind(this, record)}>
          {locale.edit}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.removeConfig.bind(this, record)}>
          {locale.deleteAction}
        </a>
        <span style={{ marginRight: 5 }}>|</span>

        <Dropdown
          trigger={
            <span style={{ color: '#33cde5' }}>
              {locale.more}
              <Icon type={'arrow-down-filling'} size={'xxs'} />
            </span>
          }
          triggerType={'click'}
        >
          <Menu onItemClick={this.chooseNav.bind(this, record)}>
            <Menu.Item key={'nav1'}>{locale.version}</Menu.Item>
            <Menu.Item key={'nav3'}>{locale.listenerQuery}</Menu.Item>
          </Menu>
        </Dropdown>
      </div>
    );
  }

  changePage(value) {
    this.setState(
      {
        currentPage: value,
      },
      () => {
        this.getData(value, false);
      }
    );
  }

  handlePageSizeChange(pageSize) {
    this.setState(
      {
        pageSize,
      },
      () => {
        this.changePage(1);
      }
    );
  }

  onInputUpdate() {}

  chooseFieldChange(fieldValue) {
    this.setState({
      fieldValue,
    });
  }

  showSelect(value) {
    this.setState({
      selectValue: value,
    });
    if (value.indexOf('appName') !== -1) {
      this.setState({
        showAppName: true,
      });
    } else {
      this.setState({
        showAppName: false,
      });
    }
    if (value.indexOf('group') !== -1) {
      this.setState({
        showgroup: true,
      });
    } else {
      this.setState({
        showgroup: false,
      });
    }
    this.chooseFieldChange(value);
  }

  getAppName(value) {
    this.appName = value;
    this.setState({
      appName: value,
    });
  }

  setAppName(value) {
    this.appName = value;
    this.setState({
      appName: value,
    });
  }

  getDataId(value) {
    this.dataId = value;
    this.setState({
      dataId: value,
    });
  }

  setConfigTags(value) {
    this.setState({
      config_tags: value,
    });
  }

  /**
   * groupId赋值
   */
  setGroup(value) {
    this.group = value || '';
    this.setState({
      group: value || '',
    });
  }

  selectAll() {
    setParams('dataId', this.dataId);
    setParams('group', this.group);
    setParams('appName', this.appName);
    this.getData();
  }

  resetAll() {
    this.dataId = '';
    this.appName = '';
    this.group = '';
    this.setState({
      selectValue: [],
      dataId: '',
      appName: '',
      group: '',
      showAppName: false,
      showgroup: false,
    });
    this.selectAll();
  }

  chooseEnv(value) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `/newconfig?serverId=${this.serverId || ''}&namespace=${this.tenant}&edasAppName=${
        this.edasAppName
      }&edasAppId=${this.edasAppId}&searchDataId=${this.dataId}&searchGroup=${this.group}`
    );
  }

  setNowNameSpace(name, id) {
    this.setState({
      nownamespace_name: name,
      nownamespace_id: id,
    });
  }

  goDetail(record) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    // 点击详情到另一个页面, 返回时候要保留原来的搜索条件 比如: record.dataId为详情的, this.dataId为搜索条件的.
    this.props.history.push(
      `/configdetail?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
        record.group
      }&namespace=${this.tenant}&edasAppName=${this.edasAppName}&searchDataId=${
        this.dataId
      }&searchGroup=${this.group}`
    );
  }

  goEditor(record) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `/configeditor?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
        record.group
      }&namespace=${this.tenant}&edasAppName=${this.edasAppName}&edasAppId=${
        this.edasAppId
      }&searchDataId=${this.dataId}&searchGroup=${this.group}`
    );
  }

  goConfigSync(record) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `/configsync?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
        record.group
      }&namespace=${this.tenant}`
    );
  }

  onSelectChange(...args) {
    const record = [];
    args[1].forEach(item => {
      if (args[0].indexOf(item.id) >= 0 && this.state.selectedKeys.indexOf(item.id) < 0) {
        record.push(item);
      }
    });
    this.state.selectedRecord.forEach(item => {
      if (args[0].indexOf(item.id) >= 0) {
        record.push(item);
      }
    });
    this.setState({
      selectedRecord: record,
      selectedKeys: args[0],
      isCheckAll: record.length > 0 && record.length === this.state.dataSource.length,
    });
  }

  onPageSelectAll(selected, records) {}

  getBatchFailedContent(res) {
    const { locale = {} } = this.props;
    return (
      <div>
        <div style={{ fontSize: 18, color: '#373D41', overflow: 'auto' }}>{res.message}</div>
        {'data' in res && res.data != null && (
          <Collapse style={{ width: '500px' }}>
            {'failedItems' in res.data && res.data.failedItems.length > 0 ? (
              <Panel title={locale.failedEntry + res.data.failedItems.length}>
                <Table dataSource={res.data.failedItems} fixedHeader maxBodyHeight={400}>
                  <Table.Column title={'Data ID'} dataIndex={'dataId'} />
                  <Table.Column title={'Group'} dataIndex={'group'} />
                </Table>
              </Panel>
            ) : (
              <Panel style={{ display: 'none' }} />
            )}
            {'succeededItems' in res.data && res.data.succeededItems.length > 0 ? (
              <Panel title={locale.successfulEntry + res.data.succeededItems.length}>
                <Table dataSource={res.data.succeededItems} fixedHeader maxBodyHeight={400}>
                  <Table.Column title={'Data ID'} dataIndex={'dataId'} />
                  <Table.Column title={'Group'} dataIndex={'group'} />
                </Table>
              </Panel>
            ) : (
              <Panel style={{ display: 'none' }} />
            )}
            {'unprocessedItems' in res.data && res.data.unprocessedItems.length > 0 ? (
              <Panel title={locale.unprocessedEntry + res.data.unprocessedItems.length}>
                <Table dataSource={res.data.unprocessedItems} fixedHeader maxBodyHeight={400}>
                  <Table.Column title={'Data ID'} dataIndex={'dataId'} />
                  <Table.Column title={'Group'} dataIndex={'group'} />
                </Table>
              </Panel>
            ) : (
              <Panel style={{ display: 'none' }} />
            )}
          </Collapse>
        )}
      </div>
    );
  }

  onClickBatchHandle() {
    this.batchHandle &&
      this.batchHandle.openDialog({
        serverId: this.serverId,
        group: this.group,
        dataId: this.dataId,
        appName: this.appName,
        config_tags: this.state.config_tags || '',
        pageSize: this.state.pageSize,
      });
  }

  changeAdvancedQuery = () => {
    this.setState({
      isAdvancedQuery: !this.state.isAdvancedQuery,
    });
  };

  checkAllHandle(checked) {
    this.setState({
      isCheckAll: checked,
      selectedKeys: checked ? this.state.dataSource.map(item => item.id) : [],
      selectedRecord: checked ? this.state.dataSource : [],
    });
  }

  render() {
    const { locale = {} } = this.props;
    return (
      <div>
        <BatchHandle ref={ref => (this.batchHandle = ref)} />
        <Loading
          shape={'flower'}
          style={{ position: 'relative', width: '100%', overflow: 'auto' }}
          visible={this.state.loading}
          tip={'Loading...'}
          color={'#333'}
        >
          <div className={this.state.hasdash ? 'dash-page-container' : ''}>
            <div
              className={this.state.hasdash ? 'dash-left-container' : ''}
              style={{ position: 'relative', padding: 10 }}
            >
              <div style={{ display: this.inApp ? 'none' : 'block', marginTop: -15 }}>
                <RegionGroup
                  namespaceCallBack={this.cleanAndGetData.bind(this)}
                  setNowNameSpace={this.setNowNameSpace.bind(this)}
                />
              </div>
              <div
                style={{
                  display: this.inApp ? 'none' : 'block',
                  position: 'relative',
                  width: '100%',
                  overflow: 'hidden',
                  height: '40px',
                }}
              >
                <h3
                  style={{
                    height: 30,
                    width: '100%',
                    lineHeight: '30px',
                    padding: 0,
                    margin: 0,
                    paddingLeft: 10,
                    borderLeft: '3px solid #09c',
                    color: '#ccc',
                    fontSize: '12px',
                  }}
                >
                  <span style={{ fontSize: '14px', color: '#000', marginRight: 8 }}>
                    {locale.configurationManagement8}
                  </span>
                  <span style={{ fontSize: '14px', color: '#000', marginRight: 8 }}>|</span>
                  <span style={{ fontSize: '14px', color: '#000', marginRight: 8 }}>
                    {this.state.nownamespace_name}
                  </span>
                  <span style={{ fontSize: '14px', color: '#000', marginRight: 18 }}>
                    {this.state.nownamespace_id}
                  </span>
                  {locale.queryResults}
                  <strong style={{ fontWeight: 'bold' }}> {this.state.total} </strong>
                  {locale.articleMeetRequirements}
                </h3>
                <div
                  style={{ position: 'absolute', textAlign: 'right', zIndex: 2, right: 0, top: 0 }}
                />
              </div>
              <div
                style={{
                  position: 'relative',
                  marginTop: 10,
                  height: this.state.isAdvancedQuery ? 'auto' : 42,
                  overflow: 'hidden',
                }}
              >
                <Form inline>
                  <Form.Item label={'Data ID:'}>
                    <Input
                      htmlType={'text'}
                      placeholder={locale.fuzzyd}
                      style={{ width: 200 }}
                      value={this.state.dataId}
                      onChange={this.getDataId.bind(this)}
                    />
                  </Form.Item>

                  <Form.Item label={'Group:'}>
                    <Select.AutoComplete
                      style={{ width: 200 }}
                      size={'medium'}
                      placeholder={locale.fuzzyg}
                      dataSource={this.state.groups}
                      value={this.state.group}
                      onChange={this.setGroup.bind(this)}
                      hasClear
                    />
                  </Form.Item>
                  <Form.Item label={''}>
                    <Button
                      type={'primary'}
                      style={{ marginRight: 10 }}
                      onClick={this.selectAll.bind(this)}
                      data-spm-click={'gostr=/aliyun;locaid=dashsearch'}
                    >
                      {locale.query}
                    </Button>
                  </Form.Item>
                  <Form.Item
                    style={
                      this.inApp
                        ? { display: 'none' }
                        : { verticalAlign: 'middle', marginTop: 0, marginLeft: 10 }
                    }
                  >
                    <div
                      style={{ color: '#33cde5', fontSize: 12, cursor: 'pointer' }}
                      onClick={this.changeAdvancedQuery}
                    >
                      <span style={{ marginRight: 5, lineHeight: '28px' }}>
                        {locale.advancedQuery9}
                      </span>
                      <Icon
                        type={
                          this.state.isAdvancedQuery ? 'arrow-up-filling' : 'arrow-down-filling'
                        }
                        size={'xs'}
                      />
                    </div>
                  </Form.Item>
                  <br />
                  <Form.Item
                    style={this.inApp ? { display: 'none' } : {}}
                    label={locale.application0}
                  >
                    <Input
                      htmlType={'text'}
                      placeholder={locale.app1}
                      style={{ width: 200 }}
                      value={this.state.appName}
                      onChange={this.setAppName.bind(this)}
                    />
                  </Form.Item>
                  <Form.Item label={locale.tags}>
                    <Select
                      style={{ width: 200 }}
                      size={'medium'}
                      hasArrow
                      mode="tag"
                      filterLocal={false}
                      placeholder={locale.pleaseEnterTag}
                      dataSource={this.state.tagLst}
                      value={this.state.config_tags}
                      onChange={this.setConfigTags.bind(this)}
                      hasClear
                    />
                  </Form.Item>
                </Form>
                <div style={{ position: 'absolute', right: 10, top: 4 }}>
                  <Icon
                    type={'add'}
                    size={'medium'}
                    style={{
                      color: 'black',
                      marginRight: 0,
                      verticalAlign: 'middle',
                      cursor: 'pointer',
                      backgroundColor: '#eee',
                      border: '1px solid #ddd',
                      padding: '3px 6px',
                    }}
                    onClick={this.chooseEnv.bind(this)}
                  />
                </div>
              </div>
              <div>
                <Table
                  dataSource={this.state.dataSource}
                  locale={{ empty: locale.pubNoData }}
                  fixedHeader
                  maxBodyHeight={400}
                  ref={'dataTable'}
                >
                  <Table.Column title={'Data Id'} dataIndex={'dataId'} />
                  <Table.Column title={'Group'} dataIndex={'group'} />
                  {!this.inApp ? (
                    <Table.Column title={locale.application} dataIndex={'appName'} />
                  ) : (
                    <div />
                  )}
                  <Table.Column title={locale.operation} cell={this.renderCol.bind(this)} />
                </Table>
                {this.state.dataSource.length > 0 && (
                  <div style={{ marginTop: 10, overflow: 'hidden' }}>
                    <Pagination
                      style={{ float: 'right' }}
                      pageSizeList={[10, 20, 30]}
                      pageSizeSelector={'dropdown'}
                      onPageSizeChange={this.handlePageSizeChange.bind(this)}
                      current={this.state.currentPage}
                      total={this.state.total}
                      pageSize={this.state.pageSize}
                      onChange={this.changePage.bind(this)}
                    />
                  </div>
                )}
              </div>
              <ShowCodeing ref={this.showcode} />
              <DeleteDialog ref={this.deleteDialog} />
            </div>
            {this.state.hasdash && (
              <div
                className={'dash-right-container'}
                style={{ overflow: 'auto', height: window.innerHeight - 40 }}
              >
                {this.state.contentList.map((v, i) => (
                  <DashboardCard data={v} height={'auto'} key={`show${i}`} />
                ))}
              </div>
            )}
          </div>
        </Loading>
      </div>
    );
  }
}

export default ConfigurationManagement;
