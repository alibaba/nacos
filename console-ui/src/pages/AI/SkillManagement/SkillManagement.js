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
  Upload,
} from '@alifd/next';
import RegionGroup from 'components/RegionGroup';
import PageTitle from 'components/PageTitle';
import { getParams, goLogin, request, setParams } from '@/globalLib';
import { GLOBAL_PAGE_SIZE_LIST, LOGINPAGE_ENABLED } from '../../../constants';
import TotalRender from '../../../components/Page/TotalRender';
import './SkillManagement.scss';

@ConfigProvider.config
class SkillManagement extends React.Component {
  static displayName = 'SkillManagement';

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
      selectedRowKeys: [],
      selectedRows: [],
      searchName: getParams('searchName') || '',
      nownamespace_name: '',
      nownamespace_id: '',
      nownamespace_desc: '',
      orderBy: '',
    };
  }

  componentDidMount() {
    let namespace = getParams('namespace') || '';
    const namespaceShowName = getParams('namespaceShowName') || '';
    const searchName = getParams('searchName') || '';

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
    const { pageSize, searchName, orderBy } = this.state;
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';

    this.setState({ loading: true });

    const data = {
      pageNo: pageNo,
      pageSize: pageSize,
      skillName: searchName || '',
      search: 'blur',
      namespaceId: namespaceId,
    };
    if (orderBy) {
      data.orderBy = orderBy;
    }

    request({
      url: 'v3/console/ai/skills/list',
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
          Message.error(result?.message || locale.getSkillListFailed || 'Failed to get Skill list');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getSkillListFailed || 'Failed to get Skill list');
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

  handleCreateSkill = () => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/newSkill?namespace=${namespaceId}`);
  };

  handleViewDetail = record => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/skillDetail?namespace=${namespaceId}&name=${record.name}`);
  };

  handleEditSkill = record => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/newSkill?namespace=${namespaceId}&name=${record.name}&mode=edit`);
  };

  handleDeleteSkill = record => {
    const { locale = {} } = this.props;
    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (
        locale.deleteSkillConfirm || 'Are you sure you want to delete Skill "{0}"?'
      ).replace('{0}', record.name),
      onOk: () => {
        this.deleteSkill(record);
      },
    });
  };

  deleteSkill = record => {
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';
    const params = new URLSearchParams();
    params.append('skillName', record.name);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      method: 'DELETE',
      url: `v3/console/ai/skills?${params.toString()}`,
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
        content: locale.selectSkillToDelete || 'Please select Skills to delete first',
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
              'Are you sure you want to delete the following {0} Skills?'
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
        this.batchDeleteSkills();
      },
    });
  };

  batchDeleteSkills = () => {
    const { selectedRows } = this.state;
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';

    const deletePromises = selectedRows.map(row => {
      const params = new URLSearchParams();
      params.append('skillName', row.name);
      if (namespaceId) {
        params.append('namespaceId', namespaceId);
      }

      return new Promise((resolve, reject) => {
        request({
          method: 'DELETE',
          url: `v3/console/ai/skills?${params.toString()}`,
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

  handleOnlineToggle = record => {
    const { locale = {} } = this.props;
    const namespaceId = getParams('namespace') || '';
    const isOnline = record.enable !== false;
    const url = isOnline ? 'v3/console/ai/skills/offline' : 'v3/console/ai/skills/online';

    request({
      url,
      method: 'POST',
      data: {
        skillName: record.name,
        scope: 'skill',
        namespaceId,
      },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        if (data && data.code === 0) {
          Message.success(
            isOnline
              ? locale.offlineSuccess || 'Offline successfully'
              : locale.onlineSuccess || 'Online successfully'
          );
          this.getData();
        } else {
          Message.error(
            data?.message ||
              (isOnline
                ? locale.offlineFailed || 'Failed to go offline'
                : locale.onlineFailed || 'Failed to go online')
          );
        }
      },
      error: () => {
        Message.error(
          isOnline
            ? locale.offlineFailed || 'Failed to go offline'
            : locale.onlineFailed || 'Failed to go online'
        );
      },
    });
  };

  getTokenInfo = () => {
    const _LOGINPAGE_ENABLED = localStorage.getItem(LOGINPAGE_ENABLED);
    let token = {};
    if (_LOGINPAGE_ENABLED !== 'false') {
      try {
        token = JSON.parse(localStorage.token);
      } catch (e) {
        console.log(e);
        goLogin();
        return {};
      }
    }
    return token;
  };

  getUploadAction = () => {
    const { accessToken = '', username = '' } = this.getTokenInfo();
    const basePath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    return `${basePath}v3/console/ai/skills/upload?namespaceId=${getParams('namespace') ||
      ''}&accessToken=${accessToken}&username=${username}`;
  };

  getUploadHeaders = () => {
    const { accessToken = '' } = this.getTokenInfo();
    return { accessToken };
  };

  beforeUpload = file => {
    const { locale = {} } = this.props;
    const isZip = file.name.toLowerCase().endsWith('.zip');
    if (!isZip) {
      Message.error(locale.uploadSkillFormatError || 'Please upload a zip file');
      return false;
    }
    return true;
  };

  uploadFormatter = res => {
    // Format response for Upload component (similar to ImportDialog)
    if (res && (res.code === 0 || res.code === 200)) {
      return {
        code: '0',
        retData: res,
      };
    } else {
      return {
        code: '1',
        error: {
          message: res?.message || 'Upload failed',
        },
        retData: res,
      };
    }
  };

  handleUploadSuccess = (file, fileList) => {
    const { locale = {} } = this.props;
    // Upload component onSuccess: (file, fileList)
    // The formatted response is stored in file.response (after formatter processing)
    const formatted = file?.response || file;
    const response = formatted?.retData || formatted?.response || formatted;

    // Check formatted code (formatter returns '0' for success, '1' for error)
    if (formatted?.code === '0') {
      Message.success(locale.uploadSkillSuccess || 'Skill uploaded successfully');
      this.getData();
    } else {
      // Also check original response code as fallback
      if (response && (response.code === 0 || response.code === 200)) {
        Message.success(locale.uploadSkillSuccess || 'Skill uploaded successfully');
        this.getData();
      } else {
        const errorMsg =
          formatted?.error?.message ||
          response?.message ||
          locale.uploadSkillFailed ||
          'Upload failed';
        Message.error(errorMsg);
      }
    }
  };

  handleUploadError = error => {
    const { locale = {} } = this.props;
    // Upload component error format
    // Check if it's actually a success response that was treated as error
    const errorResponse = error?.response || error;
    if (errorResponse && (errorResponse.code === 0 || errorResponse.code === 200)) {
      Message.success(locale.uploadSkillSuccess || 'Skill uploaded successfully');
      this.getData();
      return;
    }

    const errorData = error?.response?.data || error?.response || error;
    const errorMessage =
      errorData?.message || error?.message || locale.uploadSkillFailed || 'Upload failed';
    Message.error(errorMessage);
  };

  renderOperationColumn = (value, index, record) => {
    const { locale = {} } = this.props;
    const isEnabled = record.enable !== false;
    return (
      <div>
        <a
          onClick={() => this.handleOnlineToggle(record)}
          style={{ marginRight: 8, color: isEnabled ? '#fa8c16' : '#52c41a' }}
        >
          {isEnabled ? locale.offline || 'Offline' : locale.online || 'Online'}
        </a>
        <a onClick={() => this.handleViewDetail(record)} style={{ marginRight: 8 }}>
          {locale.details || 'Details'}
        </a>
        <a onClick={() => this.handleDeleteSkill(record)} style={{ color: '#ff4d4f' }}>
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
        <div className="empty-text">{locale.noSkillData || 'No Skill data'}</div>
        <Button type="primary" onClick={this.handleCreateSkill}>
          {locale.createSkill || 'Create Skill'}
        </Button>
      </div>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, dataSource, total, pageSize, currentPage, selectedRowKeys } = this.state;

    return (
      <>
        <div>
          <div style={{ position: 'relative' }}>
            <PageTitle
              title={locale.skillManagement || 'Skill Management'}
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
                <Form.Item label={`${locale.skillName || 'Skill Name'}：`}>
                  <Input
                    name="searchName"
                    placeholder={locale.skillNamePlaceholder || 'Please enter Skill name'}
                    style={{ width: 200 }}
                    onPressEnter={this.handleSearch}
                  />
                </Form.Item>
                <Form.Item>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <Button type="primary" onClick={this.handleSearch}>
                      {locale.search || 'Search'}
                    </Button>
                    <Button type="primary" onClick={this.handleCreateSkill}>
                      {locale.createSkill || 'Create Skill'}
                    </Button>
                    <Upload
                      accept=".zip"
                      action={this.getUploadAction()}
                      headers={this.getUploadHeaders()}
                      beforeUpload={this.beforeUpload}
                      formatter={this.uploadFormatter}
                      onSuccess={this.handleUploadSuccess}
                      onError={this.handleUploadError}
                      showUploadList={false}
                    >
                      <Button type="normal">{locale.uploadSkill || 'Upload Skill'}</Button>
                    </Upload>
                  </div>
                </Form.Item>
              </Form>
            </div>

            <Table
              className="configuration-table"
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
                title={locale.skillName || 'Skill Name'}
                dataIndex="name"
                width={160}
                cell={value => <strong>{value || '--'}</strong>}
              />
              <Table.Column
                title={locale.description || 'Description'}
                dataIndex="description"
                width={280}
                cell={value => {
                  const description = value || '--';
                  const isEmpty = !value || value === '--';
                  const cellStyle = {
                    display: 'inline-block',
                    width: '260px',
                    maxWidth: '260px',
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
                title={locale.enable || 'Status'}
                dataIndex="enable"
                width={90}
                cell={value => {
                  const isEnabled = value !== false;
                  return (
                    <Tag
                      size="small"
                      color={isEnabled ? '#87d068' : '#999'}
                      style={{ borderRadius: 4 }}
                    >
                      {isEnabled ? locale.enable || 'Enabled' : locale.disabled || 'Disabled'}
                    </Tag>
                  );
                }}
              />
              <Table.Column
                title={locale.editingVersion || 'Draft'}
                dataIndex="editingVersion"
                width={100}
                cell={value => value || '--'}
              />
              <Table.Column
                title={locale.onlineCnt || 'Online'}
                dataIndex="onlineCnt"
                width={80}
                cell={value => (value > 0 ? value : '--')}
              />
              <Table.Column
                title={locale.downloadCount || 'Downloads'}
                dataIndex="downloadCount"
                width={100}
                cell={value => (value > 0 ? value.toLocaleString() : '--')}
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
                    <div
                      style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}
                    >
                      {displayed.map(k => (
                        <Tag key={k} size="small" style={{ borderRadius: 4 }}>
                          {k}
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
                          {rest.map(k => (
                            <Tag key={k} size="small" style={{ margin: 2 }}>
                              {k}
                            </Tag>
                          ))}
                        </Balloon>
                      )}
                    </div>
                  );
                }}
              />
              <Table.Column
                title={locale.updateTime || 'Update Time'}
                dataIndex="updateTime"
                width={150}
                cell={value => this.formatTime(value)}
              />
              <Table.Column
                title={locale.operation || 'Operation'}
                cell={this.renderOperationColumn}
                width={200}
              />
            </Table>

            {total > 0 && (
              <>
                <div style={{ float: 'left' }}>
                  <Button
                    warning
                    disabled={selectedRowKeys.length === 0}
                    type="primary"
                    style={{ marginRight: 10 }}
                    onClick={this.handleBatchDelete}
                  >
                    {locale.delete || 'Delete'}
                    {selectedRowKeys.length > 0 && ` (${selectedRowKeys.length})`}
                  </Button>
                </div>
                <Pagination
                  style={{ float: 'right' }}
                  pageSizeList={GLOBAL_PAGE_SIZE_LIST}
                  pageSizePosition="start"
                  pageSizeSelector="dropdown"
                  popupProps={{ align: 'bl tl' }}
                  onPageSizeChange={this.handlePageSizeChange}
                  current={currentPage}
                  total={total}
                  totalRender={totalCount => (
                    <TotalRender locale={locale} total={totalCount || 0} />
                  )}
                  pageSize={pageSize}
                  onChange={this.handlePageChange}
                />
              </>
            )}
          </div>
        </div>
      </>
    );
  }
}

export default SkillManagement;
