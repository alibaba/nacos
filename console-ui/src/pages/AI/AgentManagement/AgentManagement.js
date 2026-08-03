/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
  Input,
  Message,
  Pagination,
  Select,
  Table,
  Tag,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import RegionGroup from 'components/RegionGroup';
import { getParams, setParams } from '@/globalLib';
import { GLOBAL_PAGE_SIZE_LIST } from '../../../constants';
import TotalRender from '../../../components/Page/TotalRender';
import { agentApi } from '../agent-api';
import './AgentManagement.scss';

@ConfigProvider.config
class AgentManagement extends React.Component {
  static displayName = 'AgentManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
    location: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.state = {
      loading: false,
      dataSource: [],
      total: 0,
      pageSize: getParams('pageSize') ? parseInt(getParams('pageSize'), 10) : 10,
      currentPage: getParams('pageNo') ? parseInt(getParams('pageNo'), 10) : 1,
      selectedRowKeys: [],
      selectedRows: [],
      searchName: getParams('searchName') || '',
      bizTag: '',
      owner: '',
      scope: '',
      nownamespace_name: '',
      nownamespace_id: '',
      nownamespace_desc: '',
    };
  }

  componentDidMount() {
    const namespace = getParams('namespace') || 'public';
    setParams({
      namespace,
      namespaceShowName: getParams('namespaceShowName') || '',
      searchName: this.state.searchName,
    });
    this.field.setValues({ searchName: this.state.searchName });
    this.getData();
  }

  componentDidUpdate(prevProps) {
    if (prevProps.location && prevProps.location.search !== this.props.location.search) {
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

  cleanAndGetData = needClean => {
    if (needClean) {
      this.field.reset();
      this.setState(
        {
          searchName: '',
          bizTag: '',
          owner: '',
          scope: '',
          currentPage: 1,
          selectedRowKeys: [],
          selectedRows: [],
        },
        this.getData
      );
      return;
    }
    this.getData();
  };

  getData = (pageNo = this.state.currentPage) => {
    const { locale = {} } = this.props;
    const { pageSize, searchName, bizTag, owner, scope } = this.state;
    this.setState({ loading: true });
    agentApi
      .list({
        namespaceId: getParams('namespace') || 'public',
        agentName: searchName || undefined,
        bizTag: bizTag || undefined,
        owner: owner || undefined,
        scope: scope || undefined,
        pageNo,
        pageSize,
      })
      .then(page => {
        this.setState({
          dataSource: page.pageItems || [],
          total: page.totalCount || 0,
          currentPage: pageNo,
          loading: false,
        });
      })
      .catch(error => {
        this.setState({ dataSource: [], total: 0, loading: false });
        Message.error(error.message || locale.getAgentListFailed || 'Failed to get Agent list');
      });
  };

  handleSearch = () => {
    const values = this.field.getValues();
    this.setState(
      {
        searchName: values.searchName || '',
        bizTag: values.bizTag || '',
        owner: values.owner || '',
        scope: values.scope || '',
        currentPage: 1,
      },
      () => {
        setParams('searchName', this.state.searchName);
        setParams('pageNo', '1');
        this.getData(1);
      }
    );
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

  navigateEditor = (mode, agentName) => {
    const params = new URLSearchParams({
      namespace: getParams('namespace') || 'public',
      mode,
    });
    if (agentName) {
      params.set('name', agentName);
    }
    this.props.history.push(`/newAgent?${params.toString()}`);
  };

  handleDeleteAgent = record => {
    const { locale = {} } = this.props;
    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (locale.deleteAgentConfirm || 'Delete Agent "{0}"?').replace(
        '{0}',
        record.agentName
      ),
      onOk: () =>
        agentApi
          .delete({
            namespaceId: getParams('namespace') || 'public',
            agentName: record.agentName,
          })
          .then(() => {
            Message.success(locale.deleteSuccess || 'Delete successful');
            this.getData();
          })
          .catch(error => Message.error(error.message || locale.deleteFailed || 'Delete failed')),
    });
  };

  handleBatchDelete = () => {
    const { locale = {} } = this.props;
    const { selectedRows } = this.state;
    if (selectedRows.length === 0) {
      Message.warning(locale.selectAgentToDelete || 'Select Agents first');
      return;
    }
    Dialog.confirm({
      title: locale.batchDeleteConfirm || 'Batch Delete',
      content: (locale.batchDeleteContent || 'Delete {0} Agents?').replace(
        '{0}',
        selectedRows.length
      ),
      onOk: () => {
        const namespaceId = getParams('namespace') || 'public';
        return Promise.all(
          selectedRows.map(row => {
            return agentApi
              .delete({ namespaceId, agentName: row.agentName })
              .then(() => true)
              .catch(() => false);
          })
        ).then(results => {
          if (results.every(Boolean)) {
            Message.success(locale.batchDeleteSuccess || 'Batch delete successful');
          } else {
            Message.error(locale.batchDeleteFailed || 'Some Agents could not be deleted');
          }
          this.setState({ selectedRowKeys: [], selectedRows: [] });
          this.getData();
        });
      },
    });
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
          namespaceCallBack={this.cleanAndGetData}
          setNowNameSpace={this.setNowNameSpace}
        />
        <div className="search-form">
          <Form inline field={this.field}>
            <Form.Item>
              <Input
                name="searchName"
                placeholder={locale.searchAgentName || 'Search Agent Name'}
                onPressEnter={this.handleSearch}
                style={{ width: 280 }}
              />
            </Form.Item>
            <Form.Item>
              <Input
                name="bizTag"
                placeholder="Business Tag"
                onPressEnter={this.handleSearch}
                style={{ width: 180 }}
              />
            </Form.Item>
            <Form.Item>
              <Input
                name="owner"
                placeholder="Owner"
                onPressEnter={this.handleSearch}
                style={{ width: 160 }}
              />
            </Form.Item>
            <Form.Item>
              <Select
                name="scope"
                dataSource={[
                  { value: '', label: 'ALL' },
                  { value: 'PUBLIC', label: 'PUBLIC' },
                  { value: 'PRIVATE', label: 'PRIVATE' },
                ]}
                style={{ width: 130 }}
              />
            </Form.Item>
            <Form.Item>
              <Button onClick={this.handleSearch} style={{ marginRight: 8 }}>
                {locale.search || 'Search'}
              </Button>
              <Button type="primary" onClick={() => this.navigateEditor('create')}>
                {locale.createAgent || 'Create Agent'}
              </Button>
            </Form.Item>
          </Form>
        </div>

        <Table
          dataSource={dataSource}
          loading={loading}
          primaryKey="agentName"
          rowSelection={{
            selectedRowKeys,
            onChange: (keys, rows) => this.setState({ selectedRowKeys: keys, selectedRows: rows }),
          }}
        >
          <Table.Column
            title={locale.agentName || 'Agent Name'}
            dataIndex="agentName"
            cell={(value, index, record) => (
              <span>
                <strong>{record.displayName || value}</strong>
                {record.displayName && <div style={{ color: '#999' }}>{value}</div>}
              </span>
            )}
          />
          <Table.Column title="Status" dataIndex="status" cell={value => <Tag>{value}</Tag>} />
          <Table.Column title="Scope" dataIndex="scope" cell={value => value || '--'} />
          <Table.Column
            title={locale.version || 'Latest Version'}
            cell={(value, index, record) =>
              (record.versionCatalog && record.versionCatalog.latestVersion) || '--'
            }
          />
          <Table.Column
            title={locale.operation || 'Operation'}
            width={250}
            cell={(value, index, record) => (
              <div>
                <a
                  onClick={() =>
                    this.props.history.push(
                      `/agentDetail?namespace=${getParams('namespace') || 'public'}&name=${
                        record.agentName
                      }`
                    )
                  }
                  style={{ marginRight: 8 }}
                >
                  {locale.details || 'Details'}
                </a>
                <a
                  onClick={() => this.navigateEditor('metadata', record.agentName)}
                  style={{ marginRight: 8 }}
                >
                  {locale.edit || 'Edit'}
                </a>
                <a
                  onClick={() => this.navigateEditor('draft-create', record.agentName)}
                  style={{ marginRight: 8 }}
                >
                  Draft
                </a>
                <a onClick={() => this.handleDeleteAgent(record)} style={{ color: '#ff4d4f' }}>
                  {locale.delete || 'Delete'}
                </a>
              </div>
            )}
          />
        </Table>

        {total > 0 && (
          <div className="batch-operations">
            <Button
              warning
              disabled={selectedRowKeys.length === 0}
              onClick={this.handleBatchDelete}
            >
              {locale.delete || 'Delete'} {selectedRowKeys.length || ''}
            </Button>
            <Pagination
              current={currentPage}
              total={total}
              pageSize={pageSize}
              pageSizeList={GLOBAL_PAGE_SIZE_LIST}
              pageSizePosition="start"
              pageSizeSelector="dropdown"
              totalRender={count => <TotalRender locale={locale} total={count || 0} />}
              onChange={this.handlePageChange}
              onPageSizeChange={this.handlePageSizeChange}
            />
          </div>
        )}
      </div>
    );
  }
}

export default AgentManagement;
