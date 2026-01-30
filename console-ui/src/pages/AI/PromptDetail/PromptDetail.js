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
import { Button, ConfigProvider, Dialog, Icon, Input, Loading, Message, Select } from '@alifd/next';
import MonacoEditor from '../../../components/MonacoEditor/MonacoEditor';
import PromptOptimizeDialog from '../PromptOptimizeDialog';
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
      // AI optimize dialog
      optimizeDialogVisible: false,
      // Debug functionality
      variableValues: {},
      userInput: '',
      debugging: false,
      debugThinking: '',
      debugContent: '',
      debugError: null,
    };
    this.debugResultRef = React.createRef();
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

  // Open AI optimize dialog
  handleOpenOptimizeDialog = () => {
    this.setState({ optimizeDialogVisible: true });
  };

  // Close AI optimize dialog
  handleCloseOptimizeDialog = () => {
    this.setState({ optimizeDialogVisible: false });
  };

  // Apply optimized prompt
  handleApplyOptimizedPrompt = optimizedPrompt => {
    const variables = this.extractVariables(optimizedPrompt);
    this.setState({
      template: optimizedPrompt,
      variables,
      optimizeDialogVisible: false,
    });
    Message.success(this.props.locale?.optimizeApplied || '优化结果已应用到编辑器');
  };

  // Handle variable value change
  handleVariableChange = (variable, value) => {
    this.setState(prevState => ({
      variableValues: {
        ...prevState.variableValues,
        [variable]: value,
      },
    }));
  };

  // Handle user input change
  handleUserInputChange = value => {
    this.setState({ userInput: value });
  };

  // Render prompt with variable values
  renderPromptWithVariables = () => {
    const { template, variableValues } = this.state;
    let renderedPrompt = template;
    Object.keys(variableValues).forEach(variable => {
      const regex = new RegExp(`\\{\\{${variable}\\}\\}`, 'g');
      renderedPrompt = renderedPrompt.replace(regex, variableValues[variable] || '');
    });
    return renderedPrompt;
  };

  // Start debugging
  handleStartDebug = () => {
    const { userInput } = this.state;
    const { locale = {} } = this.props;

    if (!userInput || !userInput.trim()) {
      Message.error(locale.userInputRequired || '请输入用户输入内容');
      return;
    }

    const renderedPrompt = this.renderPromptWithVariables();

    this.setState({
      debugging: true,
      debugThinking: '',
      debugContent: '',
      debugError: null,
    });

    const baseUrl = window.location.origin;
    const url = `${baseUrl}/v3/console/copilot/prompt/debug`;
    const token = localStorage.getItem('token');

    const payload = {
      prompt: renderedPrompt,
      userInput,
    };

    this.startDebugStream(url, payload, token);
  };

  // Start SSE stream for debugging
  startDebugStream = (url, payload, token) => {
    let accessToken = '';
    try {
      const tokenObj = JSON.parse(token);
      accessToken = tokenObj.accessToken || '';
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('Failed to parse token:', e);
    }

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: token } : {}),
        ...(accessToken ? { AccessToken: accessToken } : {}),
      },
      body: JSON.stringify(payload),
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const readStream = () => {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                this.setState({ debugging: false });
                return;
              }

              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split('\n');
              buffer = lines.pop() || '';

              lines.forEach(line => {
                if (line.startsWith('data:')) {
                  const dataStr = line.substring(5).trim();
                  if (dataStr) {
                    try {
                      const data = JSON.parse(dataStr);
                      this.handleDebugMessage(data);
                    } catch (e) {
                      // eslint-disable-next-line no-console
                      console.error('Failed to parse SSE data:', e, dataStr);
                    }
                  }
                }
              });

              readStream();
            })
            .catch(error => {
              this.setState({
                debugging: false,
                debugError: error.message || 'Stream read failed',
              });
            });
        };

        readStream();
      })
      .catch(error => {
        this.setState({
          debugging: false,
          debugError: error.message || 'Request failed',
        });
      });
  };

  // Handle debug SSE message
  handleDebugMessage = data => {
    const { type, chunk, done } = data;
    const typeStr = type?.code || type || 'CONTENT';

    if (typeStr === 'THINKING') {
      this.setState(prevState => ({
        debugThinking: prevState.debugThinking + (chunk || ''),
      }));
    } else if (typeStr === 'CONTENT') {
      this.setState(prevState => ({
        debugContent: prevState.debugContent + (chunk || ''),
      }));
    } else if (typeStr === 'DONE' || done) {
      this.setState({ debugging: false });
    } else if (typeStr === 'error') {
      this.setState({
        debugging: false,
        debugError: data.message || 'Debug failed',
      });
    }

    // Auto scroll
    if (this.debugResultRef.current) {
      this.debugResultRef.current.scrollTop = this.debugResultRef.current.scrollHeight;
    }
  };

  // Clear debug results
  handleClearDebug = () => {
    this.setState({
      debugThinking: '',
      debugContent: '',
      debugError: null,
    });
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
      optimizeDialogVisible,
      variableValues,
      userInput,
      debugging,
      debugThinking,
      debugContent,
      debugError,
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
            <div className="section-header">
              <div className="section-label">{locale.promptTemplate || 'Prompt 模板'}</div>
            </div>
            <div className="editor-container">
              <MonacoEditor
                language="plaintext"
                width="100%"
                height="100%"
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
              <Button
                type="primary"
                onClick={this.handleOpenOptimizeDialog}
                disabled={!template || !template.trim()}
              >
                <Icon type="magic" style={{ marginRight: 4 }} />
                {locale.aiOptimize || 'AI 优化'}
              </Button>
            </div>
          </div>

          <div className="detail-right">
            <div className="debug-card">
              <div className="debug-title">
                <Icon type="bug" style={{ marginRight: 8 }} />
                {locale.promptDebug || 'Prompt 调试'}
              </div>

              {/* Input Section - 包含模板参数和用户输入 */}
              <div className="debug-input-section">
                {/* Variable Inputs */}
                {variables.length > 0 && (
                  <div className="variable-inputs">
                    <div className="section-subtitle">
                      {locale.templateVariables || '模板参数'}
                      <span className="variables-count">{variables.length}</span>
                    </div>
                    <div className="variable-list">
                      {variables.map((variable, index) => (
                        <div key={index} className="variable-input-item">
                          <label className="variable-label">{`{{${variable}}}`}</label>
                          <Input
                            size="small"
                            placeholder={`${locale.enterValue || '输入'} ${variable}`}
                            value={variableValues[variable] || ''}
                            onChange={value => this.handleVariableChange(variable, value)}
                            disabled={debugging}
                          />
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Divider between variables and user input */}
                {variables.length > 0 && <div className="input-divider"></div>}

                {/* User Input */}
                <div className="user-input-wrapper">
                  <div className="section-subtitle">
                    {locale.userInputLabel || '用户输入'}
                    <span className="required">*</span>
                  </div>
                  <Input.TextArea
                    placeholder={locale.userInputPlaceholder || '输入要发送给模型的用户消息...'}
                    value={userInput}
                    onChange={this.handleUserInputChange}
                    disabled={debugging}
                    style={{ width: '100%', flex: 1, resize: 'none' }}
                  />
                </div>

                {/* Debug Button */}
                <div className="debug-actions">
                  <Button
                    type="primary"
                    onClick={this.handleStartDebug}
                    loading={debugging}
                    disabled={!userInput || !userInput.trim()}
                  >
                    <Icon type="play" style={{ marginRight: 4 }} />
                    {debugging ? locale.debugging || '调试中...' : locale.startDebug || '开始调试'}
                  </Button>
                  {(debugThinking || debugContent) && (
                    <Button onClick={this.handleClearDebug} disabled={debugging}>
                      {locale.clearResult || '清除结果'}
                    </Button>
                  )}
                </div>

                {/* Debug Error */}
                {debugError && (
                  <div className="debug-error">
                    <Icon type="warning" style={{ marginRight: 8, color: '#ff4d4f' }} />
                    {debugError}
                  </div>
                )}

                {/* Model Response Section */}
                <div className="response-divider"></div>
                <div className="section-subtitle">
                  {locale.modelResponse || '模型响应'}
                  {debugging && <Loading size="small" style={{ marginLeft: 8 }} />}
                </div>
                <div className="model-response-box" ref={this.debugResultRef}>
                  {!debugThinking && !debugContent && !debugging && (
                    <div className="response-placeholder">
                      {locale.responsePlaceholder || '点击"开始调试"后，模型响应将显示在这里...'}
                    </div>
                  )}
                  {/* Thinking Section */}
                  {debugThinking && (
                    <div className="result-section thinking-section">
                      <div className="result-label">
                        <Icon type="eye" style={{ marginRight: 4 }} />
                        {locale.thinkingProcess || '思考过程'}
                      </div>
                      <div className="result-content thinking-content">{debugThinking}</div>
                    </div>
                  )}
                  {/* Content Section */}
                  {debugContent && (
                    <div className="result-section content-section">
                      <div className="result-label">
                        <Icon type="success" style={{ marginRight: 4, color: '#52c41a' }} />
                        {locale.modelOutput || '模型输出'}
                      </div>
                      <div className="result-content output-content">{debugContent}</div>
                    </div>
                  )}
                </div>

                {/* Hint */}
                <div className="debug-hint">
                  {locale.debugHint ||
                    '调试时会将 Prompt 模板中的变量替换后作为系统提示词发送给模型'}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* AI Optimize Dialog */}
        <PromptOptimizeDialog
          visible={optimizeDialogVisible}
          prompt={template}
          onClose={this.handleCloseOptimizeDialog}
          onApply={this.handleApplyOptimizedPrompt}
          locale={locale}
        />
      </div>
    );
  }
}

export default PromptDetail;
