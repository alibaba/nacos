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
} from '@alifd/next';
import BatchHandle from 'components/BatchHandle';
import RegionGroup from 'components/RegionGroup';
import ShowCodeing from 'components/ShowCodeing';
import DeleteDialog from 'components/DeleteDialog';
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
class CredentialManagement extends React.Component {
  static displayName = 'CredentialManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.deleteDialog = React.createRef();
    this.showcode = React.createRef();
    this.field = new Field(this);
    setParams('namespace', null);
    setParams('namespaceShowName', null);
    this.credentialName = getParams('credentialName') || '';
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
      showgroup: false,
      selectValue: [],
      loading: false,
      tenant: true,
      nownamespace_id: window.nownamespace || '',
      nownamespace_name: window.namespaceShowName || '',
      nownamespace_desc: window.namespaceDesc || '',
      selectedRecord: [],
      // selectedKeys: [],
      hasdash: false,
      isCn: true,
      contentList: [],
      isCheckAll: false,
      rowSelection: {
        onChange: this.configDataTableOnChange.bind(this),
        selectedRowKeys: [],
      },
      selectedRowKeys: [],
      selectedRows: [],
      isPageEnter: false,
      defaultFuzzySearch: true,
    };
    const obj = {
      dataId: this.dataId || '',
      group: this.preGroup || '',
      appName: this.appName || '',
    };
    setParams(obj);
    this.batchHandle = null;
  }

  componentDidMount() {}

  setIsCn() {
    this.setState({ isCn: localStorage.getItem(LANGUAGE_KEY) === 'zh-CN' });
  }

  navTo(url, record) {
    this.serverId = getParams('serverId') || '';
    this.tenant = 'nacos-default-mcp'; // 为当前实例保存tenant参数
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

    this.setState({
      loading: true,
    });

    const prePageNo = getParams('pageNo');
    const prePageSize = getParams('pageSize');
    const credentialName = getParams('credentialName') || this.credentialName || '';
    this.pageNo = prePageNo ? prePageNo : pageNo;
    this.pageSize = prePageSize ? prePageSize : this.state.pageSize;
    const params = {
      dataId: credentialName,
      groupName: 'credentials',
      pageNo: prePageNo ? prePageNo : pageNo,
      pageSize: prePageSize ? prePageSize : this.state.pageSize,
      namespaceId: 'nacos-default-mcp',
    };
    const result = await this.props.getConfigs(params);
    const _configurations = result.data;
    _configurations.pageItems = result.data.pageItems.map(item => ({ id: item.name, ...item }));
    this.setState({
      configurations: _configurations,
      loading: false,
      selectedRecord: [],
      // selectedKeys: [],
      selectedRowKeys: [],
    });
  };

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
      title: locale.deleteAction,
      content: (
        <div style={{ marginTop: '-20px' }}>
          <h3>{locale.deleteCredential}</h3>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>name</span>
            <span style={{ color: '#c7254e' }}>{record.dataId}</span>
          </p>
        </div>
      ),
      onOk: () => {
        this.setState({ loading: true });
        const url = `v3/console/cs/config?dataId=${record.dataId}&groupName=credentials&tenant=nacos-default-mcp`;
        request({
          url: url,
          type: 'delete',
          data: {
            tenant: 'nacos-default-mcp',
          },
          success(res) {
            // 删除成功
            self.setState({ loading: false });
            Message.success(locale.deleteSuccessfully);
            setTimeout(() => {
              self.getData();
            }, 500);
          },
          error(res) {
            Message.error(res.responseText || res.statusText);
            self.setState({ loading: false });
          },
        });
      },
    });
  }

  showCode(record) {
    this.showcode.current.getInstance().openDialog(record);
  }

  renderCol(value, index, record) {
    const { locale = {} } = this.props;
    return (
      <div>
        <a style={{ marginRight: 5 }} onClick={() => this.editDetail(record)}>
          {locale.edit}
          {/* 编辑 */}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }} onClick={this.removeConfig.bind(this, record)}>
          {locale.delete}
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

  selectAll() {
    this.getData();
  }

  chooseEnv(value) {
    this.serverId = getParams('serverId') || 'center';
    this.tenant = 'nacos-default-mcp'; // 为当前实例保存tenant参数
    this.props.history.push(`/newCredential?namespace=${this.tenant}&mcptype=new`);
  }

  editDetail = record => {
    // 判断是否有编辑权限
    this.props.history.push(
      `/newCredential?namespaceId=nacos-default-mcp&credentialName=${record?.dataId}&mcptype=edit`
    );
  };
  // 打开查看详情页
  openDetail = record => {
    this.props.history.push(
      `/newCredential?namespaceId=nacos-default-mcp&credentialName=${record?.dataId}&mcptype=readonly`
    );
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
            <h3>{locale.deleteCredential}</h3>
            <Table dataSource={selectedRows}>
              <Table.Column title="Credential" dataIndex="dataId" />
            </Table>
          </div>
        ),
        onOk: () => {
          self.setState({ loading: true });
          const url =
            `v3/console/cs/config/batchDelete?&ids=${Array.from(selectedRowKeys).join(
              ','
            )}&namespaceId=` + self.state.nownamespace_id;
          request({
            url,
            type: 'delete',
            success(res) {
              Message.success(locale.delSuccessMsg);
              self.setState({ loading: false });
              self.getData();
            },
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
                title={locale.credentialManagement8}
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
                    {locale.addNewCredential}
                  </Button>
                </Form.Item>
                <Form.Item label="Credential Id">
                  <Input
                    htmlType="text"
                    style={{ width: 200 }}
                    onChange={credentialName => {
                      this.credentialName = credentialName;
                      this.setState({ credentialName });
                      setParams('credentialName', credentialName);
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
                title={'Credential Id'}
                cell={(value, index, record) => {
                  return record.dataId || '--';
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
            <DeleteDialog ref={this.deleteDialog} />
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

export default CredentialManagement;
