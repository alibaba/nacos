/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
  Balloon,
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
import RegionGroup from 'components/RegionGroup';
import PageTitle from 'components/PageTitle';
import { getParams, request, setParams } from '@/globalLib';
import { GLOBAL_PAGE_SIZE_LIST } from '../../../constants';
import TotalRender from '../../../components/Page/TotalRender';
import './PromptManagement.scss';

@ConfigProvider.config
class PromptManagement extends React.Component {
  static displayName = 'PromptManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);

    this.state = {
      loading: false,
      dataSource: [],
      total: 0,
      pageSize: getParams('pageSize') ? parseInt(getParams('pageSize')) : 10,
      currentPage: getParams('pageNo') ? parseInt(getParams('pageNo')) : 1,
      searchKey: getParams('searchKey') || '',
      nownamespace_name: '',
      nownamespace_id: '',
      nownamespace_desc: '',
    };
  }

  componentDidMount() {
    let namespace = getParams('namespace') || '';
    const namespaceShowName = getParams('namespaceShowName') || '';
    const searchKey = getParams('searchKey') || '';

    if (!namespace) {
      namespace = 'public';
    }

    setParams({
      namespace,
      namespaceShowName,
      searchKey,
    });

    this.getData();
  }

  componentDidUpdate(prevProps) {
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
        searchKey: '',
      });
      const namespace = getParams('namespace') || '';
      const namespaceShowName = getParams('namespaceShowName') || '';
      setParams({
        namespace,
        namespaceShowName,
        searchKey: '',
      });
    }
    this.getData();
  };

  getData = (pageNo = this.state.currentPage) => {
    const { pageSize, searchKey } = this.state;
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';

    this.setState({ loading: true });

    const data = {
      pageNo: pageNo,
      pageSize: pageSize,
      promptKey: searchKey || '',
      search: 'blur',
      namespaceId: namespaceId,
    };

    request({
      url: 'v3/console/ai/prompt/list',
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
          Message.error(
            result?.message || locale.getPromptListFailed || 'Failed to get Prompt list'
          );
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getPromptListFailed || 'Failed to get Prompt list');
      },
    });
  };

  handleSearch = () => {
    const searchKey = this.field.getValue('searchKey') || '';
    this.setState({ searchKey, currentPage: 1 }, () => {
      setParams('searchKey', searchKey);
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

  handleCreatePrompt = () => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/newPrompt?namespace=${namespaceId}`);
  };

  handleViewDetail = record => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/promptDetail?namespace=${namespaceId}&promptKey=${record.promptKey}`);
  };

  handleDeletePrompt = record => {
    const { locale = {} } = this.props;
    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (
        locale.deletePromptConfirm || 'Are you sure you want to delete Prompt "{0}"?'
      ).replace('{0}', record.promptKey),
      onOk: () => {
        this.deletePrompt(record);
      },
    });
  };

  deletePrompt = record => {
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';
    const params = new URLSearchParams();
    params.append('promptKey', record.promptKey);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      method: 'DELETE',
      url: `v3/console/ai/prompt?${params.toString()}`,
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

  formatTime = timestamp => {
    if (!timestamp) return '--';
    try {
      const date = new Date(timestamp);
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
        <a onClick={() => this.handleDeletePrompt(record)} style={{ color: '#ff4d4f' }}>
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
        <div className="empty-text">{locale.noPromptData || 'No Prompt data'}</div>
        <Button type="primary" onClick={this.handleCreatePrompt}>
          {locale.createPrompt || 'Create Prompt'}
        </Button>
      </div>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, dataSource, total, pageSize, currentPage } = this.state;

    return (
      <div className="prompt-management">
        <div style={{ position: 'relative' }}>
          <PageTitle
            title={locale.promptRegistry || 'Prompt Registry'}
            desc={this.state.nownamespace_desc}
            namespaceId={this.state.nownamespace_id}
            namespaceName={this.state.nownamespace_name}
            nameSpace
          />
          <RegionGroup
            namespaceCallBack={this.cleanAndGetData.bind(this)}
            setNowNameSpace={this.setNowNameSpace.bind(this)}
          />

          <div
            style={{
              position: 'relative',
              marginTop: 10,
              height: 'auto',
              overflow: 'visible',
            }}
          >
            <Form inline field={this.field}>
              <Form.Item label={`${locale.promptKey || 'Prompt Key'}：`}>
                <Input
                  name="searchKey"
                  placeholder={locale.promptKeyPlaceholder || 'Please enter Prompt Key'}
                  style={{ width: 200 }}
                  onPressEnter={this.handleSearch}
                />
              </Form.Item>
              <Form.Item>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <Button type="primary" onClick={this.handleSearch}>
                    {locale.search || 'Search'}
                  </Button>
                  <Button type="primary" onClick={this.handleCreatePrompt}>
                    {locale.createPrompt || 'Create Prompt'}
                  </Button>
                </div>
              </Form.Item>
            </Form>
          </div>

          <Table
            className="configuration-table"
            dataSource={dataSource}
            loading={loading}
            emptyContent={this.renderEmptyState()}
            primaryKey="promptKey"
          >
            <Table.Column
              title={locale.promptKey || 'Prompt Key'}
              dataIndex="promptKey"
              width={200}
              cell={value => <strong className="prompt-key-cell">{value || '--'}</strong>}
            />
            <Table.Column
              title={locale.description || 'Description'}
              dataIndex="description"
              cell={value => {
                const description = value || '--';
                const isEmpty = !value || value === '--';
                const cellStyle = {
                  display: 'inline-block',
                  maxWidth: '100%',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  verticalAlign: 'middle',
                };

                const cellContent = <span style={cellStyle}>{description}</span>;

                if (isEmpty) {
                  return cellContent;
                }

                return (
                  <Balloon trigger={cellContent} triggerType="hover" closable={false}>
                    {description}
                  </Balloon>
                );
              }}
            />
            <Table.Column
              title={locale.version || 'Version'}
              dataIndex="latestVersion"
              width={120}
              cell={(value, index, record) => (
                <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                  {value ? (
                    <Tag size="small" className="version-tag">
                      {value}
                    </Tag>
                  ) : (
                    '--'
                  )}
                  {record.editingVersion && (
                    <Tag size="small" color="blue" style={{ borderRadius: 4 }}>
                      {locale.editingVersionLabel || 'Draft'}
                    </Tag>
                  )}
                </div>
              )}
            />
            <Table.Column
              title={locale.onlineCnt || 'Online'}
              dataIndex="onlineCnt"
              width={80}
              cell={value => (
                <span style={{ color: value > 0 ? '#52c41a' : '#999', fontWeight: 500 }}>
                  {value != null ? value : '--'}
                </span>
              )}
            />
            <Table.Column
              title={locale.labels || 'Labels'}
              dataIndex="labels"
              width={150}
              cell={value => {
                if (!value || typeof value !== 'object') return '--';
                const keys = Object.keys(value);
                if (keys.length === 0) return '--';
                const displayed = keys.slice(0, 2);
                const rest = keys.slice(2);
                return (
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}>
                    {displayed.map(key => (
                      <Tag key={key} size="small" style={{ borderRadius: 4 }}>
                        {key}
                      </Tag>
                    ))}
                    {rest.length > 0 && (
                      <Balloon
                        trigger={
                          <Tag size="small" style={{ borderRadius: 4, cursor: 'pointer' }}>
                            +{rest.length}
                          </Tag>
                        }
                        triggerType="hover"
                        closable={false}
                      >
                        {rest.map(key => (
                          <Tag key={key} size="small" style={{ margin: 2 }}>
                            {key}
                          </Tag>
                        ))}
                      </Balloon>
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
            <Pagination
              style={{ float: 'right', marginTop: 16 }}
              pageSizeList={GLOBAL_PAGE_SIZE_LIST}
              pageSizePosition="start"
              pageSizeSelector="dropdown"
              popupProps={{ align: 'bl tl' }}
              onPageSizeChange={this.handlePageSizeChange}
              current={currentPage}
              total={total}
              totalRender={totalCount => <TotalRender locale={locale} total={totalCount || 0} />}
              pageSize={pageSize}
              onChange={this.handlePageChange}
            />
          )}
        </div>
      </div>
    );
  }
}

export default PromptManagement;
