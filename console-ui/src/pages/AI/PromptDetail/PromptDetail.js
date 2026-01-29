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
import { Button, ConfigProvider, Dialog, Icon, Loading, Message, Select } from '@alifd/next';
import MonacoEditor from '../../../components/MonacoEditor/MonacoEditor';
import { getParams, request } from '@/globalLib';
import './PromptDetail.scss';

@ConfigProvider.config
class PromptDetail extends React.Component {
  static displayName = 'PromptManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);

    this.state = {
      loading: true,
      promptKey: getParams('promptKey') || '',
      namespaceId: getParams('namespace') || '',
      // Current prompt data
      promptData: null,
      // Template (editable in frontend only)
      template: '',
      // Parsed variables
      variables: [],
      // Version list (latest + history)
      versions: [],
      selectedVersion: null,
      isLatestVersion: true,
      // Description editing
      editingDescription: false,
      descriptionValue: '',
      savingDescription: false,
      // History versions loading
      loadingHistory: false,
    };
  }

  componentDidMount() {
    this.loadPromptDetail();
    this.loadHistoryVersions();
  }

  // Load prompt detail
  loadPromptDetail = (version = null) => {
    const { promptKey, namespaceId } = this.state;
    const { locale = {} } = this.props;

    this.setState({ loading: true });

    const params = {
      promptKey,
      namespaceId,
    };

    request({
      url: 'v3/console/ai/prompt',
      method: 'get',
      data: params,
      success: result => {
        if (result && result.code === 0 && result.data) {
          const data = result.data;
          const template = data.template || '';
          this.setState({
            loading: false,
            promptData: data,
            template: template,
            variables: this.extractVariables(template),
            descriptionValue: data.description || '',
            selectedVersion: data.version,
            isLatestVersion: true,
          });
        } else {
          this.setState({ loading: false });
          Message.error(result?.message || locale.getPromptFailed || '获取 Prompt 详情失败');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getPromptFailed || '获取 Prompt 详情失败');
      },
    });
  };

  // Load history versions
  loadHistoryVersions = () => {
    const { promptKey, namespaceId } = this.state;

    this.setState({ loadingHistory: true });

    request({
      url: 'v3/console/ai/prompt/history',
      method: 'get',
      data: {
        promptKey,
        namespaceId,
        pageNo: 1,
        pageSize: 20,
      },
      success: result => {
        if (result && result.code === 0 && result.data) {
          const historyItems = result.data.pageItems || [];
          this.setState({
            loadingHistory: false,
            versions: historyItems,
          });
        } else {
          this.setState({ loadingHistory: false });
        }
      },
      error: () => {
        this.setState({ loadingHistory: false });
      },
    });
  };

  // Load history version detail
  loadHistoryDetail = historyId => {
    const { promptKey, namespaceId, promptData } = this.state;
    const { locale = {} } = this.props;

    this.setState({ loading: true });

    request({
      url: 'v3/console/ai/prompt/history/detail',
      method: 'get',
      data: {
        promptKey,
        namespaceId,
        historyId,
      },
      success: result => {
        if (result && result.code === 0 && result.data) {
          const data = result.data;
          const template = data.template || '';
          this.setState({
            loading: false,
            template: template,
            variables: this.extractVariables(template),
            selectedVersion: data.version,
            isLatestVersion: false,
            // Update promptData with history version's commitMsg and time
            promptData: {
              ...promptData,
              commitMsg: data.commitMsg || '',
              updateTime: data.updateTime,
            },
          });
        } else {
          this.setState({ loading: false });
          Message.error(result?.message || locale.getHistoryFailed || '获取历史版本失败');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getHistoryFailed || '获取历史版本失败');
      },
    });
  };

  // Extract {{variable}} from template
  extractVariables = template => {
    if (!template) return [];
    const regex = /\{\{([^\s{}]+)\}\}/g;
    const variables = [];
    let match;
    while ((match = regex.exec(template)) !== null) {
      if (!variables.includes(match[1])) {
        variables.push(match[1]);
      }
    }
    return variables;
  };

  // Handle template change (frontend only)
  handleTemplateChange = value => {
    const variables = this.extractVariables(value);
    this.setState({ template: value, variables });
  };

  // Handle version change
  handleVersionChange = value => {
    if (value === 'latest') {
      this.loadPromptDetail();
    } else {
      // value is historyId
      this.loadHistoryDetail(value);
    }
  };

  // Start editing description
  handleEditDescription = () => {
    this.setState({
      editingDescription: true,
      descriptionValue: this.state.promptData?.description || '',
    });
  };

  // Cancel editing description
  handleCancelDescription = () => {
    this.setState({
      editingDescription: false,
      descriptionValue: this.state.promptData?.description || '',
    });
  };

  // Save description
  handleSaveDescription = () => {
    const { promptKey, namespaceId, descriptionValue } = this.state;
    const { locale = {} } = this.props;

    this.setState({ savingDescription: true });

    request({
      method: 'PUT',
      url: 'v3/console/ai/prompt/metadata',
      data: {
        namespaceId,
        promptKey,
        description: descriptionValue,
      },
      success: data => {
        this.setState({ savingDescription: false });
        if (data && data.code === 0) {
          Message.success(locale.updateDescSuccess || '描述修改成功');
          this.setState({
            editingDescription: false,
            promptData: {
              ...this.state.promptData,
              description: descriptionValue,
            },
          });
        } else {
          Message.error(data?.message || locale.updateDescFailed || '描述修改失败');
        }
      },
      error: () => {
        this.setState({ savingDescription: false });
        Message.error(locale.updateDescFailed || '描述修改失败');
      },
    });
  };

  // Navigate to publish new version
  handlePublishNewVersion = () => {
    const { promptKey, namespaceId, template, promptData } = this.state;
    // Store template and metadata in sessionStorage for the publish page to use
    sessionStorage.setItem('promptPublishTemplate', template);
    sessionStorage.setItem('promptPublishCurrentVersion', promptData?.version || '');
    sessionStorage.setItem('promptPublishDescription', promptData?.description || '');
    this.props.history.push(
      `/publishPromptVersion?namespace=${namespaceId}&promptKey=${promptKey}`
    );
  };

  // Delete prompt
  handleDeletePrompt = () => {
    const { locale = {} } = this.props;
    const { promptKey } = this.state;

    Dialog.confirm({
      title: locale.deleteConfirm || '删除确认',
      content: (locale.deletePromptConfirm || '确定要删除 Prompt "{0}" 吗？').replace(
        '{0}',
        promptKey
      ),
      onOk: () => {
        this.deletePrompt();
      },
    });
  };

  deletePrompt = () => {
    const { promptKey, namespaceId } = this.state;
    const { locale = {} } = this.props;

    const params = new URLSearchParams();
    params.append('promptKey', promptKey);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      method: 'DELETE',
      url: `v3/console/ai/prompt?${params.toString()}`,
      success: data => {
        if (data && data.code === 0) {
          Message.success(locale.deleteSuccess || '删除成功');
          setTimeout(() => {
            this.handleGoBack();
          }, 1000);
        } else {
          Message.error(data?.message || locale.deleteFailed || '删除失败');
        }
      },
      error: () => {
        Message.error(locale.deleteFailed || '删除失败');
      },
    });
  };

  // Go back to list
  handleGoBack = () => {
    const { namespaceId } = this.state;
    this.props.history.push(`/promptManagement?namespace=${namespaceId || 'public'}`);
  };

  // Format time
  formatTime = time => {
    if (!time) return '--';
    const date = new Date(time);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
  };

  // Build version options for select
  buildVersionOptions = () => {
    const { promptData, versions } = this.state;
    const { locale = {} } = this.props;
    const options = [];

    // Add latest version
    if (promptData?.version) {
      options.push({
        label: promptData.version,
        value: 'latest',
        version: promptData.version,
        commitMsg: promptData.commitMsg || '',
        publishTime: promptData.updateTime || promptData.publishTime,
        isLatest: true,
      });
    }

    // Add history versions
    versions.forEach(item => {
      if (item.version && item.version !== promptData?.version) {
        options.push({
          label: item.version,
          value: item.id,
          version: item.version,
          commitMsg: item.commitMsg || '',
          publishTime: item.publishTime || item.lastModifiedTime,
          isLatest: false,
        });
      }
    });

    return options;
  };

  // Render version option item
  renderVersionOption = item => {
    const { locale = {} } = this.props;
    return (
      <div className="version-option">
        <div className="version-option-header">
          <span className="version-number">{item.version}</span>
          {item.isLatest && (
            <span className="version-tag latest">{locale.latestVersion || '最新版本'}</span>
          )}
          {!item.isLatest && (
            <span className="version-tag history">{locale.historyVersion || '历史版本'}</span>
          )}
        </div>
        {item.commitMsg && <div className="version-commit">{item.commitMsg}</div>}
        <div className="version-time">{this.formatTime(item.publishTime)}</div>
      </div>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const {
      loading,
      promptKey,
      promptData,
      template,
      variables,
      selectedVersion,
      isLatestVersion,
      editingDescription,
      descriptionValue,
      savingDescription,
    } = this.state;

    if (loading && !promptData) {
      return (
        <div className="prompt-detail">
          <div className="loading-container">
            <Loading />
          </div>
        </div>
      );
    }

    const versionOptions = this.buildVersionOptions();

    return (
      <div className="prompt-detail">
        <div className="page-header">
          <div className="header-left">
            <h1 className="prompt-title">{promptKey}</h1>
            <Select
              className="version-select"
              popupClassName="version-select-popup"
              style={{ minWidth: 240 }}
              value={isLatestVersion ? 'latest' : selectedVersion}
              onChange={this.handleVersionChange}
              valueRender={() => (
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span>{selectedVersion}</span>
                  {isLatestVersion && (
                    <>
                      <Icon type="success" size="small" style={{ color: '#1DC11D' }} />
                      <span style={{ color: '#1DC11D' }}>{locale.latestVersion || '最新版本'}</span>
                    </>
                  )}
                  {!isLatestVersion && (
                    <span style={{ color: '#888' }}>{locale.historyVersion || '历史版本'}</span>
                  )}
                </span>
              )}
            >
              {versionOptions.map(item => {
                const isSelected =
                  (item.value === 'latest' && isLatestVersion) ||
                  (item.value !== 'latest' && !isLatestVersion && item.version === selectedVersion);
                return (
                  <Select.Option key={item.value} value={item.value}>
                    <div style={{ display: 'flex', alignItems: 'center', padding: '4px 0' }}>
                      <div style={{ flex: 1, lineHeight: '1.5' }}>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            marginBottom: '6px',
                          }}
                        >
                          <span style={{ fontWeight: 600, fontSize: '14px', color: '#333' }}>
                            {item.version}
                          </span>
                          <span
                            style={{
                              fontSize: '12px',
                              padding: '2px 8px',
                              borderRadius: '3px',
                              background: item.isLatest ? '#e6f7e6' : '#fff7e6',
                              color: item.isLatest ? '#52c41a' : '#faad14',
                              lineHeight: '1.4',
                            }}
                          >
                            {item.isLatest
                              ? locale.latestVersion || '最新版本'
                              : locale.historyVersion || '历史版本'}
                          </span>
                        </div>
                        {item.commitMsg && (
                          <div
                            style={{
                              fontSize: '13px',
                              color: '#555',
                              marginBottom: '4px',
                              lineHeight: '1.4',
                            }}
                          >
                            {item.commitMsg}
                          </div>
                        )}
                        <div style={{ fontSize: '12px', color: '#999', lineHeight: '1.4' }}>
                          {this.formatTime(item.publishTime)}
                        </div>
                      </div>
                      {isSelected && (
                        <Icon
                          type="select"
                          size="small"
                          style={{ color: '#1890ff', marginLeft: '12px' }}
                        />
                      )}
                    </div>
                  </Select.Option>
                );
              })}
            </Select>
          </div>
          <Button type="primary" onClick={this.handlePublishNewVersion}>
            {locale.publishNewVersion || '发布新版本'}
          </Button>
        </div>

        <div className="prompt-meta">
          {promptData?.description && (
            <div className="meta-item">
              <span className="meta-label">{locale.description || '描述'}:</span>
              <span className="meta-value">{promptData.description}</span>
            </div>
          )}
          {promptData?.commitMsg && (
            <div className="meta-item">
              <span className="meta-label">{locale.commitMsg || '提交信息'}:</span>
              <span className="meta-value">{promptData.commitMsg}</span>
            </div>
          )}
          {promptData?.updateTime && (
            <div className="meta-item">
              <span className="meta-label">{locale.publishTime || '发布时间'}:</span>
              <span className="meta-value">{this.formatTime(promptData.updateTime)}</span>
            </div>
          )}
        </div>

        <div className="header-divider"></div>

        <div className="detail-container">
          <div className="detail-left">
            <div className="section-label">{locale.promptTemplate || 'Prompt 模板'}</div>
            <div className="editor-container">
              <MonacoEditor
                language="plaintext"
                width="100%"
                height={400}
                value={template}
                onChange={this.handleTemplateChange}
                options={{
                  minimap: { enabled: false },
                  lineNumbers: 'on',
                  wordWrap: 'on',
                  scrollBeyondLastLine: false,
                }}
              />
            </div>
            <div className="action-buttons">
              <Button warning onClick={this.handleDeletePrompt}>
                {locale.deletePrompt || '删除 Prompt'}
              </Button>
            </div>
          </div>

          <div className="detail-right">
            <div className="variables-card">
              <div className="variables-title">
                {locale.templateVariables || '模板参数'}
                {variables.length > 0 && (
                  <span className="variables-count">{variables.length}</span>
                )}
              </div>

              {variables.length > 0 ? (
                <div className="variables-list">
                  {variables.map((variable, index) => (
                    <div key={index} className="variable-item">
                      <Icon type="success" size="small" className="variable-icon" />
                      {`{{${variable}}}`}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="no-variables">
                  <div className="no-variables-icon">
                    <Icon type="prompt" size="large" />
                  </div>
                  <div>{locale.noVariables || '暂无模板参数'}</div>
                </div>
              )}

              <div className="variables-hint">
                {locale.variablesHint || '使用 {{变量名}} 格式定义模板参数'}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

export default PromptDetail;
