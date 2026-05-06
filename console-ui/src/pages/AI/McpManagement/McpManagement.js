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
import PropTypes, { string } from 'prop-types';
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
  Tag,
  Input,
  Message,
  Pagination,
  Select,
  Table,
  Card,
  Grid,
  Balloon,
  Loading,
  Progress,
} from '@alifd/next';
import RegionGroup from 'components/RegionGroup';
import BatchHandle from 'components/BatchHandle';
import ShowCodeing from 'components/ShowCodeing';
import DeleteDialog from 'components/DeleteDialog';
import DashboardCard from './DashboardCard';
import ImportMcpDialog from './ImportMcpDialog';
import { getParams, request, setParams } from '@/globalLib';
import { goLogin } from '../../../globalLib';
import { connect } from 'react-redux';
import { getConfigs, getConfigsV2 } from '../../../reducers/configuration';
import PageTitle from '../../../components/PageTitle';

import { GLOBAL_PAGE_SIZE_LIST, LANGUAGE_KEY, LOGINPAGE_ENABLED } from '../../../constants';
import TotalRender from '../../../components/Page/TotalRender';

@connect(
  state => ({
    configurations: state.configuration.configurations,
  }),
  { getConfigs, getConfigsV2 }
)
@ConfigProvider.config
class McpManagement extends React.Component {
  static displayName = 'McpServerManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.deleteDialog = React.createRef();
    this.showcode = React.createRef();
    this.field = new Field(this);
    this.appName = getParams('appName') || '';
    this.preAppName = this.appName;
    this.group = getParams('group') || '';
    this.preGroup = this.group;
    this.dataId = getParams('dataId') || '';
    this.mcpName = getParams('mcpName') || '';
    this.preDataId = this.dataId;
    this.serverId = getParams('serverId') || 'center';
    this.edasAppId = getParams('edasAppId') || '';
    this.edasAppName = getParams('edasAppName') || '';
    this.inApp = this.edasAppId;
    this.isAdvance = getParams('isAdvanceQuery') || false;
    this.DEFAULT_REGISTRY_URL = 'https://registry.modelcontextprotocol.io/v0/servers';

    this.state = {
      configurations: {
        pageItems: [],
        pageNumber: 1,
        pagesAvailable: 1,
        totalCount: 2,
      },
      value: '',
      visible: false,
      total: 0,
      pageSize: getParams('pageSize') ? getParams('pageSize') : 10,
      currentPage: 1,
      dataSource: [],
      fieldValue: [],
      showAppName: false,
      showgroup: false,
      dataId: this.dataId,
      group: this.group,
      appName: this.appName,
      config_detail: getParams('configDetail') || '',
      config_tags: getParams('configTags') ? getParams('configTags').split(',') : [],
      types: getParams('types') ? getParams('types').split(',') : [],
      tagLst: getParams('tagList') ? getParams('tagList').split(',') : [],
      selectValue: [],
      loading: false,
      groupList: [],
      groups: [],
      tenant: true,
      nownamespace_id: window.nownamespace || '',
      nownamespace_name: window.namespaceShowName || '',
      nownamespace_desc: window.namespaceDesc || '',
      selectedRecord: [],
      // selectedKeys: [],
      hasdash: false,
      isCn: true,
      contentList: [],
      isAdvancedQuery: this.isAdvance,
      isCheckAll: false,
      rowSelection: {
        onChange: this.configDataTableOnChange.bind(this),
        selectedRowKeys: [],
      },
      selectedRowKeys: [],
      selectedRows: [],
      isPageEnter: false,
      defaultFuzzySearch: true,
      // ensure mcpName is controlled and initialized from query params
      mcpName: this.mcpName || '',
      // Import dialog state (visibility only; logic moved to child component)
      importVisible: false,
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
            style: { width: '60%' },
            content: (
              <div>
                <div style={{ fontSize: '15px', lineHeight: '22px' }}>
                  {locale.ad}
                  {/* eslint-disable */}
                  <a href="https://survey.aliyun.com/survey/k0BjJ2ARC" target="_blank">
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

  navTo(url, record) {
    this.serverId = getParams('serverId') || '';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    switch (url) {
      case '/historyRollback':
        url = `${url}?historyServerId=${this.serverId || ''}&historyDataId=${
          record.dataId
        }&historyGroup=${record.group}&namespace=${this.tenant}`;
        break;
      case '/listeningToQuery':
        url = `${url}?listeningServerId=${this.serverId || ''}&listeningDataId=${
          record.dataId
        }&listeningGroup=${record.group}&namespace=${this.tenant}`;
        break;
      case '/pushTrajectory':
        url = `${url}?serverId=${this.serverId || ''}&dataId=${record.dataId}&group=${
          record.group
        }&namespace=${this.tenant}`;
        break;
      default:
    }
    this.props.history.push(url);
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
      this.mcpName = '';
      this.setState({
        group: '',
        dataId: '',
        mcpName: '',
      });
      setParams({
        group: '',
        dataId: '',
        mcpName: '',
      });
    }
    this.getData();
    const { rowSelection } = this.state;
    rowSelection.selectedRowKeys = [];
    this.setState({ rowSelection });
  }

  changeParamsBySearchType(params) {
    if (this.state.defaultFuzzySearch) {
      if (params.dataId && params.dataId !== '') {
        params.dataId = '*' + params.dataId + '*';
      }
      if (params.group && params.group !== '') {
        params.group = '*' + params.group + '*';
      }
    }
    if (this.state.defaultFuzzySearch) {
      params.search = 'blur';
    } else {
      params.search = 'accurate';
    }
  }

  getData = async (pageNo = 1) => {
    if (this.state.loading) {
      return;
    }
    const prePageNo = getParams('pageNo');
    const prePageSize = getParams('pageSize');
    // always use state to avoid stale values from previous searches
    const mcpName = this.state.mcpName || '';
    this.pageNo = prePageNo ? prePageNo : pageNo;
    this.pageSize = prePageSize ? prePageSize : this.state.pageSize;
    const data = {
      pageNo: prePageNo ? prePageNo : pageNo,
      pageSize: prePageSize ? prePageSize : this.state.pageSize,
      mcpName: mcpName,
      search: 'blur',
    };
    this.setState({ loading: true });
    const result = await request({
      url: 'v3/console/ai/mcp/list',
      method: 'get',
      data,
      error: () => {
        this.setState({ loading: false });
      },
    });

    if (result.code == 0) {
      const _configurations = result.data;
      _configurations.pageItems = result.data.pageItems.map(item => ({ id: item.name, ...item }));
      this.setState({
        configurations: _configurations,
        loading: false,
        selectedRecord: [],
        // selectedKeys: [],
        selectedRowKeys: [],
      });
    }
  };

  // ---------- Import MCP Server ----------
  openImportDialog = () => this.setState({ importVisible: true });
  closeImportDialog = () => this.setState({ importVisible: false });

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

  updateState = async record => {
    this.setState({ loading: true });
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'get',
      data: {
        mcpId: record.id,
        mcpName: record.name,
      },
      error: () => {
        this.setState({ loading: false }, this.getData);
      },
    });

    const data = result.data;
    console.log(data);
    let getEndpointSpecification = '';
    if (data && data.protocol && data.protocol !== 'stdio') {
      // 使用已有服务
      if (
        data.remoteServerConfig &&
        data.remoteServerConfig.serviceRef &&
        data.remoteServerConfig.serviceRef.serviceName
      ) {
        getEndpointSpecification = JSON.stringify({
          type: 'REF',
          data: {
            groupName: data.remoteServerConfig.serviceRef.groupName,
            namespaceId: data.remoteServerConfig.serviceRef.namespaceId,
            serviceName: data.remoteServerConfig.serviceRef.serviceName,
          },
        });
      }
      // 外部直连
      else if (data && data.backendEndpoints && data.backendEndpoints.length > 0) {
        // 解析address
        const protocol = data.backendEndpoints[0].protocol;
        const address = data.backendEndpoints[0].address;
        const port = data.backendEndpoints[0].port;
        const exportPath = data.backendEndpoints[0].path;
        getEndpointSpecification = JSON.stringify({
          type: 'DIRECT',
          data: {
            protocol: protocol,
            address: address,
            port: port,
            exportPath: exportPath,
          },
        });
      }
    }

    let toolSpec = '';
    if (data.toolSpec) {
      toolSpec = JSON.stringify(data.toolSpec);
    }

    data.enabled = !data.enabled;
    request({
      url: `v3/console/ai/mcp`,
      type: 'put',
      data: {
        serverSpecification: JSON.stringify(data),
        toolSpecification: toolSpec,
        endpointSpecification: getEndpointSpecification,
      },
      success: res => {
        Message.success('Success');
        this.setState({ loading: false }, this.getData);
      },
      error: res => {
        Message.error(res.responseText || res.statusText);
        this.setState({ loading: false });
      },
    });
  };

  removeConfig = record => {
    const { locale = {} } = this.props;
    const self = this;
    Dialog.confirm({
      title: locale.deleteAction,
      content: (
        <div style={{ marginTop: '-20px' }}>
          <h3>{locale.deleteMcpServer}</h3>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>name</span>
            <span style={{ color: '#c7254e' }}>{record.name}</span>
          </p>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>{locale.description}</span>
            <span style={{ color: '#c7254e' }}>{record.description}</span>
          </p>
        </div>
      ),
      onOk: () => {
        this.setState({ loading: true });
        request({
          url: `v3/console/ai/mcp?mcpId=${record.id}`,
          type: 'delete',
          success: res => {
            // 删除成功
            Message.success(locale.deleteSuccessfully);
            this.setState({ loading: false }, this.getData);
          },
          error: res => {
            Message.error(res.responseText || res.statusText);
            this.setState({ loading: false });
          },
        });
      },
    });
  };

  showCode(record) {
    this.showcode.current.getInstance().openDialog(record);
  }

  renderCol(value, index, record) {
    const { locale = {} } = this.props;
    return (
      <div>
        <a onClick={() => this.openDetail(record)} style={{ marginRight: 5 }}>
          {locale.details}
        </a>
        {/* <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.showCode.bind(this, record)}>
          {locale.sampleConfig}
        </a> */}
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={() => this.editDetail(record)}>
          {locale.edit}
          {/* 编辑 */}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={() => this.removeConfig(record)}>
          {locale.delete}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={() => this.updateState(record)}>
          {record.enabled ? locale.offline : locale.online}
        </a>
      </div>
    );
  }

  changePage(value, e) {
    setParams('pageNo', String(value));
    this.setState(
      {
        isPageEnter: e && e.keyCode && e.keyCode === 13,
        currentPage: value,
      },
      () => this.getData(value, false)
    );
  }

  // onChangeSort(dataIndex, order) {
  //   const { configurations = {} } = this.props;
  //   configurations.pageItems.sort(function(a, b) {
  //     if (order === 'asc') {
  //       return (a[dataIndex] + '').localeCompare(b[dataIndex] + '');
  //     }
  //     return (b[dataIndex] + '').localeCompare(a[dataIndex] + '');
  //   });
  //   this.forceUpdate();
  // }

  handlePageSizeChange(pageSize) {
    setParams('pageSize', String(pageSize));
    this.setState({ pageSize }, () => this.changePage(1));
  }

  setConfigDetail(value) {
    this.setState({
      config_detail: value,
    });
    setParams('configDetail', value);
  }

  setAppName(value) {
    this.appName = value;
    this.setState({
      appName: value,
    });
    setParams('appName', value);
  }

  setConfigTags(value) {
    this.setState({
      config_tags: value || [],
      tagLst: value,
    });
    if (!value) {
      setParams('tagList', '');
      setParams('configTags', '');
    } else {
      setParams('tagList', value.join(','));
      setParams('configTags', value.join(','));
    }
  }

  selectAll() {
    this.getData();
  }

  chooseEnv(value) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || 'public'; // 为当前实例保存tenant参数
    this.props.history.push(`/newMcpServer?namespace=${this.tenant}`);
  }

  editDetail = record => {
    // 判断是否有编辑权限
    const ns = getParams('namespace') || 'public';
    this.props.history.push(`/newMcpServer?namespace=${ns}&id=${record?.id}&mcptype=edit`);
  };
  // 打开查看详情页
  openDetail = record => {
    const ns = getParams('namespace') || 'public';
    this.props.history.push(`/mcpServerDetail?namespace=${ns}&id=${record?.id}`);
  };

  setNowNameSpace(name, id, desc) {
    this.setState({
      nownamespace_name: name,
      nownamespace_id: id,
      nownamespace_desc: desc,
    });
  }

  setConfigTypes(value) {
    this.setState({
      types: value || [],
    });
    if (!value) {
      setParams('types', '');
    } else {
      setParams('types', value.join(','));
    }
  }

  multipleSelectionDeletion = () => {
    const { locale = {} } = this.props;
    const { selectedRowKeys = [], selectedRows = [] } = this.state;
    const self = this;
    if (selectedRowKeys.length === 0 || selectedRows.length === 0) {
      Dialog.alert({
        title: locale.delSelectedAlertTitle,
        content: locale.delSelectedAlertContent,
      });
    } else {
      Dialog.confirm({
        title: locale.removeConfiguration,
        content: (
          <div style={{ marginTop: '-20px' }}>
            <h3>{locale.deleteMcpServer}</h3>
            <Table dataSource={selectedRows}>
              <Table.Column title="MCP Server" dataIndex="name" />
              <Table.Column title={locale.description} dataIndex="description" />
              <Table.Column title={locale.mcpServerType} dataIndex="frontProtocol" />
            </Table>
          </div>
        ),
        onOk: () => {
          self.setState({ loading: true });
          Promise.all(
            selectedRows.map(item =>
              request({
                url: `v3/console/ai/mcp`,
                type: 'delete',
                data: {
                  mcpId: item.id,
                },
              })
            )
          ).then(res => {
            self.setState({ loading: false }, self.getData);
            // 判断是否全部删除成功
            if (res.every(item => item.code === 0)) {
              Message.success(locale.batchDeleteSuccessfully);
            } else {
              // 提示删除失败的配置项
              const failedItems = res.filter(item => item.code !== 0);
              failedItems.forEach(item => {
                Message.error(`${item.name} ${locale.batchDeleteFailed}`);
              });
            }
          });
        },
      });
    }
  };

  configDataTableOnChange = (ids, records) => {
    this.setState({
      selectedRowKeys: ids,
      selectedRows: records,
    });
  };

  render() {
    const { configurations = {} } = this.state;
    const { locale = {} } = this.props;
    return (
      <>
        <BatchHandle ref={ref => (this.batchHandle = ref)} />
        <div className={this.state.hasdash ? 'dash-page-container' : ''}>
          <div
            className={this.state.hasdash ? 'dash-left-container' : ''}
            style={{ position: 'relative' }}
          >
            <div style={{ display: this.inApp ? 'none' : 'block' }}>
              <PageTitle
                title={locale.mcpManagement8}
                desc={this.state.nownamespace_desc}
                namespaceId={this.state.nownamespace_id}
                namespaceName={this.state.nownamespace_name}
                nameSpace
              />
              <RegionGroup
                namespaceCallBack={this.cleanAndGetData.bind(this)}
                setNowNameSpace={this.setNowNameSpace.bind(this)}
              />
            </div>

            <div
              style={{
                position: 'relative',
                marginTop: 10,
                height: 'auto',
                overflow: 'visible',
              }}
            >
              <Form inline>
                <Form.Item>
                  <Button type="primary" onClick={this.chooseEnv.bind(this)}>
                    {locale.addNewMcpServer}
                  </Button>
                </Form.Item>
                <Form.Item>
                  <Button onClick={this.openImportDialog}>
                    {locale.importMcpServer || '导入 MCP Server'}
                  </Button>
                </Form.Item>
                <Form.Item label="Server Name">
                  <Input
                    htmlType="text"
                    style={{ width: 200 }}
                    value={this.state.mcpName}
                    onChange={mcpName => {
                      this.mcpName = mcpName;
                      this.setState({ mcpName });
                      setParams('mcpName', mcpName);
                    }}
                    onPressEnter={() => this.selectAll()}
                  />
                </Form.Item>

                <Form.Item label={''}>
                  <Button
                    type={'primary'}
                    style={{ marginRight: 10 }}
                    onClick={this.selectAll.bind(this)}
                    data-spm-click={'gostr=/aliyun;locaid=dashsearch'}
                  >
                    {locale.search}
                  </Button>
                </Form.Item>
                <Form.Item
                  style={
                    this.inApp
                      ? { display: 'none' }
                      : {
                          verticalAlign: 'middle',
                          marginTop: 0,
                          marginLeft: 0,
                        }
                  }
                ></Form.Item>
              </Form>
            </div>

            <Table
              className="configuration-table"
              dataSource={configurations.pageItems}
              locale={{ empty: locale.pubNoData }}
              ref="dataTable"
              loading={this.state.loading}
              rowSelection={{
                onChange: this.configDataTableOnChange,
                selectedRowKeys: this.state.selectedRowKeys,
              }}
              // onSort={this.onChangeSort.bind(this)}
            >
              <Table.Column
                // sortable={true}
                title={'MCP Server'}
                cell={(value, index, record) => {
                  return record.name || '--';
                }}
              />
              {/* 支持能力 */}
              <Table.Column
                title={locale.capability}
                cell={(value, index, record) => {
                  const { capabilities = [] } = record;
                  return capabilities?.length
                    ? capabilities.map((item, index) => {
                        return (
                          <Tag
                            type="primary"
                            key={item}
                            style={{ marginLeft: index ? '10px' : '0' }}
                          >
                            {item}
                          </Tag>
                        );
                      })
                    : '--';
                }}
              />
              <Table.Column
                title={locale.mcpServerType}
                cell={(value, index, record) => {
                  if ((record.protocol === 'http') | (record.protocol === 'https')) {
                    return (
                      <>
                        {record.frontProtocol}
                        <Tag type="primary" size={'small'} color="green" style={{ marginLeft: 10 }}>
                          {locale.convertService}
                        </Tag>
                      </>
                    );
                  }
                  return record.frontProtocol || '--';
                }}
              />
              <Table.Column
                title={locale.mcpServerVersion}
                cell={(value, index, record) => {
                  return record?.versionDetail?.version || '--';
                }}
              />
              <Table.Column title={locale.operation} cell={this.renderCol.bind(this)} />
            </Table>
            {configurations.totalCount > 0 && (
              <>
                <div style={{ float: 'left' }}>
                  <Button
                    warning
                    disabled={!this.state.selectedRowKeys.length}
                    type="primary"
                    style={{ marginRight: 10 }}
                    onClick={this.multipleSelectionDeletion}
                    data-spm-click={`gostr=/aliyun;locaid=configsDelete`}
                  >
                    {locale.delete}
                    {/* 删除 */}
                  </Button>
                </div>
                <Pagination
                  style={{ float: 'right' }}
                  pageSizeList={GLOBAL_PAGE_SIZE_LIST}
                  pageSizePosition="start"
                  pageSizeSelector="dropdown"
                  popupProps={{ align: 'bl tl' }}
                  onPageSizeChange={val => this.handlePageSizeChange(val)}
                  current={configurations.pageNumber}
                  total={configurations.totalCount}
                  totalRender={total => <TotalRender locale={locale} total={total} />}
                  pageSize={this.state.pageSize}
                  onChange={this.changePage.bind(this)}
                />
              </>
            )}
            <ShowCodeing ref={this.showcode} />
            <DeleteDialog ref={this.deleteDialog} />
            <ImportMcpDialog
              visible={this.state.importVisible}
              onClose={this.closeImportDialog}
              onImported={this.getData}
              locale={this.props.locale}
            />
          </div>
          {this.state.hasdash && (
            <div className="dash-right-container">
              {this.state.contentList.map((v, i) => (
                <DashboardCard data={v} height={'auto'} key={`show${i}`} />
              ))}
            </div>
          )}
        </div>
      </>
    );
  }
}

export default McpManagement;
