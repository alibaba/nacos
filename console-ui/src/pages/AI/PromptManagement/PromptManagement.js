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
      // Edit description dialog state
      editDescVisible: false,
      editDescLoading: false,
      editDescRecord: null,
      editDescValue: '',
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
      // timestamp is a Long (milliseconds)
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

  // Open edit description dialog
  handleEditDescription = record => {
    this.setState({
      editDescVisible: true,
      editDescRecord: record,
      editDescValue: record.description || '',
    });
  };

  // Close edit description dialog
  handleEditDescClose = () => {
    this.setState({
      editDescVisible: false,
      editDescRecord: null,
      editDescValue: '',
      editDescLoading: false,
    });
  };

  // Handle description input change
  handleDescChange = value => {
    this.setState({ editDescValue: value });
  };

  // Submit description update
  handleEditDescSubmit = () => {
    const { locale = {} } = this.props;
    const { editDescRecord, editDescValue } = this.state;
    const namespaceId = getParams('namespace') || '';

    if (!editDescRecord) return;

    this.setState({ editDescLoading: true });

    request({
      method: 'PUT',
      url: 'v3/console/ai/prompt/metadata',
      data: {
        namespaceId: namespaceId,
        promptKey: editDescRecord.promptKey,
        description: editDescValue,
      },
      success: data => {
        this.setState({ editDescLoading: false });
        if (data && data.code === 0) {
          Message.success(locale.updateDescSuccess || 'Description updated successfully');
          this.handleEditDescClose();
          this.getData();
        } else {
          Message.error(data?.message || locale.updateDescFailed || 'Failed to update description');
        }
      },
      error: () => {
        this.setState({ editDescLoading: false });
        Message.error(locale.updateDescFailed || 'Failed to update description');
      },
    });
  };

  renderOperationColumn = (value, index, record) => {
    const { locale = {} } = this.props;
    return (
      <div>
        <a onClick={() => this.handleViewDetail(record)} style={{ marginRight: 8 }}>
          {locale.details || 'Details'}
        </a>
        <a onClick={() => this.handleEditDescription(record)} style={{ marginRight: 8 }}>
          {locale.editDescription || 'Edit Desc'}
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

  renderEditDescDialog = () => {
    const { locale = {} } = this.props;
    const { editDescVisible, editDescLoading, editDescRecord, editDescValue } = this.state;

    return (
      <Dialog
        title={locale.editDescription || 'Edit Description'}
        visible={editDescVisible}
        onOk={this.handleEditDescSubmit}
        onCancel={this.handleEditDescClose}
        onClose={this.handleEditDescClose}
        okProps={{ loading: editDescLoading }}
        style={{ width: 500 }}
      >
        <Form style={{ width: '100%' }}>
          <Form.Item label={`${locale.promptKey || 'Prompt Key'}：`}>
            <Input value={editDescRecord?.promptKey || ''} disabled style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={`${locale.description || 'Description'}：`}>
            <Input.TextArea
              value={editDescValue}
              onChange={this.handleDescChange}
              placeholder={locale.descriptionPlaceholder || 'Please enter description'}
              style={{ width: '100%' }}
              rows={4}
              maxLength={256}
              showLimitHint
            />
          </Form.Item>
        </Form>
      </Dialog>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, dataSource, total, pageSize, currentPage } = this.state;

    return (
      <>
        {this.renderEditDescDialog()}
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
                width={100}
                cell={value =>
                  value ? (
                    <Tag size="small" className="version-tag">
                      {value}
                    </Tag>
                  ) : (
                    '--'
                  )
                }
              />
              <Table.Column
                title={locale.operation || 'Operation'}
                cell={this.renderOperationColumn}
                width={200}
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
      </>
    );
  }
}

export default PromptManagement;
