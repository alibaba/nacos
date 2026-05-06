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
  ConfigProvider,
  Dialog,
  Field,
  Form,
  Icon,
  Input,
  Message,
  Pagination,
  Table,
  Tag,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import DeleteDialog from 'components/DeleteDialog';
import RegionGroup from 'components/RegionGroup';
import { getParams, request, setParams } from '@/globalLib';
import { GLOBAL_PAGE_SIZE_LIST } from '../../../constants';
import TotalRender from '../../../components/Page/TotalRender';
import './AgentManagement.scss';

@ConfigProvider.config
class AgentManagement extends React.Component {
  static displayName = 'AgentManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.deleteDialog = React.createRef();
    this.field = new Field(this);

    this.state = {
      loading: false,
      dataSource: [],
      total: 0,
      pageSize: getParams('pageSize') ? parseInt(getParams('pageSize')) : 10,
      currentPage: getParams('pageNo') ? parseInt(getParams('pageNo')) : 1,
      selectedRowKeys: [],
      selectedRows: [],
      searchName: getParams('searchName') || '',
      nownamespace_name: '',
      nownamespace_id: '',
      nownamespace_desc: '',
    };
  }

  componentDidMount() {
    let namespace = getParams('namespace') || '';
    const namespaceShowName = getParams('namespaceShowName') || '';
    const searchName = getParams('searchName') || '';

    // 如果没有指定命名空间，默认设置为public
    if (!namespace) {
      namespace = 'public';
    }

    setParams({
      namespace,
      namespaceShowName,
      searchName,
    });

    this.getData();
  }

  componentDidUpdate(prevProps) {
    // 当路由参数变化时，重新加载数据
    if (prevProps.location?.search !== this.props.location?.search) {
      this.getData();
    }
  }

  setNowNameSpace = (name, id, desc) => {
    this.setState({
      nownamespace_name: name,
      nownamespace_id: id,
      nownamespace_desc: desc,
    });
  };

  cleanAndGetData = (needclean = false) => {
    if (needclean) {
      this.setState({
        searchName: '',
        selectedRowKeys: [],
        selectedRows: [],
      });
      const namespace = getParams('namespace') || '';
      const namespaceShowName = getParams('namespaceShowName') || '';
      setParams({
        namespace,
        namespaceShowName,
        searchName: '',
      });
    }
    this.getData();
  };

  getData = (pageNo = this.state.currentPage) => {
    const { pageSize, searchName } = this.state;
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';

    this.setState({ loading: true });

    const data = {
      pageNo: pageNo,
      pageSize: pageSize,
      agentName: searchName || '',
      search: 'blur',
      namespaceId: namespaceId,
    };

    request({
      url: 'v3/console/ai/a2a/list',
      method: 'get',
      data,
      success: result => {
        if (result && result.code === 0) {
          this.setState({
            dataSource: result.data?.pageItems || [],
            total: result.data?.totalCount || 0,
            loading: false,
            currentPage: pageNo,
          });
        } else {
          this.setState({ loading: false });
          Message.error(result?.message || locale.getAgentListFailed || 'Failed to get Agent list');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getAgentListFailed || 'Failed to get Agent list');
      },
    });
  };

  handleSearch = () => {
    const searchName = this.field.getValue('searchName') || '';
    this.setState({ searchName, currentPage: 1 }, () => {
      setParams('searchName', searchName);
      setParams('pageNo', '1');
      this.getData(1);
    });
  };

  handlePageChange = currentPage => {
    this.setState({ currentPage }, () => {
      setParams('pageNo', String(currentPage));
      this.getData(currentPage);
    });
  };

  handlePageSizeChange = pageSize => {
    this.setState({ pageSize, currentPage: 1 }, () => {
      setParams('pageSize', String(pageSize));
      setParams('pageNo', '1');
      this.getData(1);
    });
  };

  handleRowSelectionChange = (selectedRowKeys, selectedRows) => {
    this.setState({ selectedRowKeys, selectedRows });
  };

  handleCreateAgent = () => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/newAgent?namespace=${namespaceId}`);
  };

  handleViewDetail = record => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/agentDetail?namespace=${namespaceId}&name=${record.name}`);
  };

  handleEditAgent = record => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/newAgent?namespace=${namespaceId}&name=${record.name}&mode=edit`);
  };

  handleDeleteAgent = record => {
    const { locale = {} } = this.props;
    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (
        locale.deleteAgentConfirm || 'Are you sure you want to delete Agent "{0}"?'
      ).replace('{0}', record.name),
      onOk: () => {
        this.deleteAgent(record);
      },
    });
  };

  deleteAgent = record => {
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';
    const params = new URLSearchParams();
    params.append('agentName', record.name);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      method: 'DELETE',
      url: `v3/console/ai/a2a?${params.toString()}`,
      success: data => {
        if (data && data.code === 0) {
          Message.success(locale.deleteSuccess || 'Delete successful');
          this.getData();
        } else {
          Message.error(data?.message || locale.deleteFailed || 'Delete failed');
        }
      },
      error: () => {
        Message.error(locale.deleteFailed || 'Delete failed');
      },
    });
  };

  handleBatchDelete = () => {
    const { selectedRows } = this.state;
    const { locale = {} } = this.props;

    if (selectedRows.length === 0) {
      Dialog.alert({
        title: locale.tip || 'Tip',
        content: locale.selectAgentToDelete || 'Please select Agents to delete first',
      });
      return;
    }

    Dialog.confirm({
      title: locale.batchDeleteConfirm || 'Batch Delete Confirmation',
      content: (
        <div>
          <p>
            {(
              locale.batchDeleteContent ||
              'Are you sure you want to delete the following {0} Agents?'
            ).replace('{0}', selectedRows.length)}
          </p>
          <ul>
            {selectedRows.map(row => (
              <li key={row.name}>{row.name}</li>
            ))}
          </ul>
        </div>
      ),
      onOk: () => {
        this.batchDeleteAgents();
      },
    });
  };

  batchDeleteAgents = () => {
    const { selectedRows } = this.state;
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';

    const deletePromises = selectedRows.map(row => {
      const params = new URLSearchParams();
      params.append('agentName', row.name);
      if (namespaceId) {
        params.append('namespaceId', namespaceId);
      }

      return new Promise((resolve, reject) => {
        request({
          method: 'DELETE',
          url: `v3/console/ai/a2a?${params.toString()}`,
          success: resolve,
          error: reject,
        });
      });
    });

    Promise.all(deletePromises)
      .then(() => {
        Message.success(locale.batchDeleteSuccess || 'Batch delete successful');
        this.setState({ selectedRowKeys: [], selectedRows: [] });
        this.getData();
      })
      .catch(() => {
        Message.error(locale.batchDeleteFailed || 'Batch delete failed');
      });
  };

  formatTime = timeStr => {
    if (!timeStr) return '--';
    try {
      const date = new Date(timeStr);
      if (isNaN(date.getTime())) return '--';
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch (e) {
      return '--';
    }
  };

  renderOperationColumn = (value, index, record) => {
    const { locale = {} } = this.props;
    return (
      <div>
        <a onClick={() => this.handleViewDetail(record)} style={{ marginRight: 8 }}>
          {locale.details || 'Details'}
        </a>
        <a onClick={() => this.handleEditAgent(record)} style={{ marginRight: 8 }}>
          {locale.edit || 'Edit'}
        </a>
        <a onClick={() => this.handleDeleteAgent(record)} style={{ color: '#ff4d4f' }}>
          {locale.delete || 'Delete'}
        </a>
      </div>
    );
  };

  renderEmptyState = () => {
    const { locale = {} } = this.props;
    return (
      <div className="empty-state">
        <div className="empty-icon">
          <Icon type="inbox" />
        </div>
        <div className="empty-text">{locale.noAgentData || 'No Agent data'}</div>
        <Button type="primary" onClick={this.handleCreateAgent}>
          {locale.createAgent || 'Create Agent'}
        </Button>
      </div>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, dataSource, total, pageSize, currentPage, selectedRowKeys } = this.state;

    return (
      <div className="agent-management">
        <PageTitle
          title={locale.agentManagement || 'Agent Management'}
          desc={this.state.nownamespace_desc}
          namespaceId={this.state.nownamespace_id}
          namespaceName={this.state.nownamespace_name}
          nameSpace
        />
        <RegionGroup
          namespaceCallBack={this.cleanAndGetData.bind(this)}
          setNowNameSpace={this.setNowNameSpace.bind(this)}
        />

        <div className="search-form">
          <Form inline field={this.field}>
            <Form.Item label={`${locale.agentName || 'Agent Name'}：`}>
              <Input
                name="searchName"
                placeholder={locale.agentNamePlaceholder || 'Please enter Agent name'}
                style={{ width: 200 }}
                onPressEnter={this.handleSearch}
              />
            </Form.Item>
            <Form.Item>
              <Button type="primary" onClick={this.handleSearch} style={{ marginRight: 16 }}>
                {locale.search || 'Search'}
              </Button>
              <Button type="primary" onClick={this.handleCreateAgent}>
                {locale.createAgent || 'Create Agent'}
              </Button>
            </Form.Item>
          </Form>
        </div>

        <Table
          className="agent-table"
          dataSource={dataSource}
          loading={loading}
          emptyContent={this.renderEmptyState()}
          primaryKey="name"
          rowSelection={{
            onChange: this.handleRowSelectionChange,
            selectedRowKeys,
          }}
        >
          <Table.Column
            title={locale.agentName || 'Agent Name'}
            dataIndex="name"
            cell={value => <strong>{value || '--'}</strong>}
          />
          <Table.Column
            title={locale.version || 'Active Version'}
            dataIndex="latestPublishedVersion"
            cell={value => (
              <Tag type="primary" size="small">
                {value || '--'}
              </Tag>
            )}
          />
          <Table.Column
            title="能力列表"
            dataIndex="capabilities"
            cell={(value, index, record) => {
              const capabilities = [];
              if (record.capabilities) {
                if (record.capabilities.streaming) {
                  capabilities.push('流式传输');
                }
                if (record.capabilities.stateTransitionHistory) {
                  capabilities.push('状态历史');
                }
                if (record.capabilities.pushNotifications) {
                  capabilities.push('推送通知');
                }
              }
              return capabilities.length > 0 ? (
                <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                  {capabilities.map((cap, index) => (
                    <Tag key={index} type="primary" size="small">
                      {cap}
                    </Tag>
                  ))}
                </div>
              ) : (
                '--'
              );
            }}
          />
          <Table.Column
            title="技能列表"
            dataIndex="skills"
            cell={(value, index, record) => {
              if (!record.skills || !Array.isArray(record.skills) || record.skills.length === 0) {
                return '--';
              }

              const skillNames = record.skills
                .map(skill => (typeof skill === 'object' && skill !== null ? skill.name : skill))
                .filter(name => name);

              if (skillNames.length === 0) {
                return '--';
              }

              const displaySkills = skillNames.slice(0, 3);
              const remainingCount = skillNames.length > 3 ? skillNames.length - 3 : 0;

              return (
                <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                  {displaySkills.map((skill, index) => (
                    <Tag key={index} type="normal" size="small">
                      {skill}
                    </Tag>
                  ))}
                  {remainingCount > 0 && (
                    <Tag type="normal" size="small">
                      +{remainingCount}
                    </Tag>
                  )}
                </div>
              );
            }}
          />
          <Table.Column
            title={locale.operation || 'Operation'}
            cell={this.renderOperationColumn}
            width={150}
          />
        </Table>

        {total > 0 && (
          <div className="batch-operations">
            <div>
              <Button
                warning
                disabled={selectedRowKeys.length === 0}
                onClick={this.handleBatchDelete}
              >
                {locale.delete || 'Delete'}{' '}
                {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
              </Button>
            </div>
            <Pagination
              current={currentPage}
              total={total}
              pageSize={pageSize}
              pageSizeList={GLOBAL_PAGE_SIZE_LIST}
              pageSizePosition="start"
              pageSizeSelector="dropdown"
              totalRender={totalCount => <TotalRender locale={locale} total={totalCount || 0} />}
              onChange={this.handlePageChange}
              onPageSizeChange={this.handlePageSizeChange}
            />
          </div>
        )}

        <DeleteDialog ref={this.deleteDialog} />
      </div>
    );
  }
}

export default AgentManagement;
