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
  Button,
  Checkbox,
  ConfigProvider,
  Dialog,
  Field,
  Icon,
  Input,
  Loading,
  Message,
  Pagination,
  Table,
  Tag,
} from '@alifd/next';
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
    this.field = new Field(this);

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
      labelsMap: {},
      selectedLabel: null,
      selectedVersion: null,
      isLatestVersion: true,
      versionPanelVisible: false,
      versionPageNo: 1,
      versionPageSize: 10,
      versionTotal: 0,
      labelEditorVisible: false,
      labelEditorVersion: '',
      labelEditorSelected: [],
      labelEditorAll: [],
      labelEditorNewLabel: '',
      labelEditorSaving: false,
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
  loadPromptDetail = (version = null, label = null) => {
    const { promptKey, namespaceId } = this.state;
    const { locale = {} } = this.props;

    this.setState({ loading: true });
    request({
      url: 'v3/console/ai/prompt/metadata',
      method: 'get',
      data: { promptKey, namespaceId },
      success: metaResult => {
        if (!(metaResult && metaResult.code === 0 && metaResult.data)) {
          this.setState({ loading: false });
          Message.error(metaResult?.message || locale.getPromptFailed || '获取 Prompt 详情失败');
          return;
        }
        const metaData = metaResult.data;
        const detailParams = { promptKey, namespaceId };
        if (label) {
          detailParams.label = label;
        } else if (version) {
          detailParams.version = version;
        }
        request({
          url: 'v3/console/ai/prompt/detail',
          method: 'get',
          data: detailParams,
          success: detailResult => {
            if (detailResult && detailResult.code === 0 && detailResult.data) {
              const versionData = detailResult.data;
              const template = versionData.template || '';
              const isLatestVersion =
                !versionData.version || metaData.latestVersion === versionData.version;
              this.setState({
                loading: false,
                promptData: {
                  ...versionData,
                  description: metaData.description || '',
                  promptTags: (metaData.bizTags || []).join(','),
                },
                template: template,
                variables: this.extractVariables(template),
                descriptionValue: metaData.description || '',
                labelsMap: metaData.labels || {},
                selectedLabel: label || null,
                selectedVersion: versionData.version,
                isLatestVersion,
              });
            } else {
              this.setState({ loading: false });
              Message.error(
                detailResult?.message || locale.getPromptFailed || '获取 Prompt 详情失败'
              );
            }
          },
          error: () => {
            this.setState({ loading: false });
            Message.error(locale.getPromptFailed || '获取 Prompt 详情失败');
          },
        });
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getPromptFailed || '获取 Prompt 详情失败');
      },
    });
  };

  // Load history versions
  loadHistoryVersions = (
    pageNo = this.state.versionPageNo,
    pageSize = this.state.versionPageSize
  ) => {
    const { promptKey, namespaceId } = this.state;

    this.setState({ loadingHistory: true, versionPageNo: pageNo, versionPageSize: pageSize });

    request({
      url: 'v3/console/ai/prompt/versions',
      method: 'get',
      data: {
        promptKey,
        namespaceId,
        pageNo,
        pageSize,
      },
      success: result => {
        if (result && result.code === 0 && result.data) {
          const historyItems = result.data.pageItems || [];
          this.setState({
            loadingHistory: false,
            versions: historyItems,
            versionTotal: result.data.totalCount || 0,
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

  handleOpenVersionPanel = () => {
    this.setState(
      {
        versionPanelVisible: true,
      },
      () => this.loadHistoryVersions(1, this.state.versionPageSize)
    );
  };

  handleCloseVersionPanel = () => {
    this.setState({
      versionPanelVisible: false,
    });
  };

  getLabelsByVersion = version => {
    const { labelsMap = {} } = this.state;
    return Object.keys(labelsMap).filter(each => labelsMap[each] === version);
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

  openLabelEditor = version => {
    const { labelsMap = {} } = this.state;
    const allLabels = Object.keys(labelsMap);
    const selected = allLabels.filter(each => labelsMap[each] === version);
    this.setState({
      labelEditorVisible: true,
      labelEditorVersion: version || '',
      labelEditorSelected: selected,
      labelEditorAll: allLabels,
      labelEditorNewLabel: '',
    });
  };

  closeLabelEditor = () => {
    this.setState({
      labelEditorVisible: false,
      labelEditorVersion: '',
      labelEditorSelected: [],
      labelEditorAll: [],
      labelEditorNewLabel: '',
      labelEditorSaving: false,
    });
  };

  addNewLabelToEditor = () => {
    const { locale = {} } = this.props;
    const { labelEditorNewLabel, labelEditorAll, labelEditorSelected } = this.state;
    const newLabel = (labelEditorNewLabel || '').trim();
    if (!newLabel) {
      Message.error(locale.labelRequired || '请输入 Label');
      return;
    }
    if (!/^[A-Za-z0-9._-]+$/.test(newLabel)) {
      Message.error(locale.labelInvalid || 'Label 仅支持字母、数字、.-_');
      return;
    }
    if (labelEditorAll.includes(newLabel)) {
      Message.error(locale.labelExists || 'Label 已存在');
      return;
    }
    this.setState({
      labelEditorAll: [...labelEditorAll, newLabel],
      labelEditorSelected: [...labelEditorSelected, newLabel],
      labelEditorNewLabel: '',
    });
  };

  bindLabelRequest = (label, version) => {
    const { promptKey, namespaceId } = this.state;
    return new Promise((resolve, reject) => {
      request({
        method: 'PUT',
        url: 'v3/console/ai/prompt/label',
        data: {
          namespaceId,
          promptKey,
          label,
          version,
        },
        success: data => {
          if (data && data.code === 0) {
            resolve(true);
          } else {
            reject(new Error(data?.message || 'Label 绑定失败'));
          }
        },
        error: () => reject(new Error('Label 绑定失败')),
      });
    });
  };

  unbindLabelRequest = label => {
    const { promptKey, namespaceId } = this.state;
    const { locale = {} } = this.props;
    const params = new URLSearchParams();
    params.append('namespaceId', namespaceId);
    params.append('promptKey', promptKey);
    params.append('label', label);
    return new Promise((resolve, reject) => {
      request({
        method: 'DELETE',
        url: `v3/console/ai/prompt/label?${params.toString()}`,
        success: data => {
          if (data && data.code === 0) {
            resolve(true);
          } else {
            reject(new Error(data?.message || locale.unbindLabelFailed || 'Label 解绑失败'));
          }
        },
        error: () => reject(new Error(locale.unbindLabelFailed || 'Label 解绑失败')),
      });
    });
  };

  handleSaveLabelEditor = () => {
    const { locale = {} } = this.props;
    const { labelsMap = {}, labelEditorVersion, labelEditorSelected, selectedVersion } = this.state;
    if (!labelEditorVersion) {
      Message.error(locale.versionRequired || '缺少版本信息');
      return;
    }
    const allLabels = Object.keys(labelsMap);
    const currentBound = allLabels.filter(each => labelsMap[each] === labelEditorVersion);
    const selectedSet = new Set(labelEditorSelected);
    const needUnbind = currentBound.filter(each => !selectedSet.has(each));
    const needBind = labelEditorSelected.filter(each => labelsMap[each] !== labelEditorVersion);
    this.setState({ labelEditorSaving: true });
    Promise.all([
      ...needUnbind.map(each => this.unbindLabelRequest(each)),
      ...needBind.map(each => this.bindLabelRequest(each, labelEditorVersion)),
    ])
      .then(() => {
        Message.success(locale.bindLabelSuccess || 'Label 更新成功');
        this.closeLabelEditor();
        this.loadPromptDetail(selectedVersion, null);
        this.loadHistoryVersions(this.state.versionPageNo, this.state.versionPageSize);
      })
      .catch(err => {
        Message.error(err?.message || locale.bindLabelFailed || 'Label 更新失败');
      })
      .finally(() => {
        this.setState({ labelEditorSaving: false });
      });
  };

  handleViewVersion = version => {
    this.loadPromptDetail(version, null);
    this.handleCloseVersionPanel();
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

  render() {
    const { locale = {} } = this.props;
    const {
      loading,
      promptKey,
      promptData,
      template,
      variables,
      labelsMap,
      selectedVersion,
      isLatestVersion,
      versionPanelVisible,
      versionPageNo,
      versionPageSize,
      versionTotal,
      versions,
      loadingHistory,
      labelEditorVisible,
      labelEditorVersion,
      labelEditorSelected,
      labelEditorAll,
      labelEditorNewLabel,
      labelEditorSaving,
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

    const labels = Object.keys(labelsMap || {});

    return (
      <div className="prompt-detail">
        <div className="page-header">
          <div className="header-left">
            <h1 className="prompt-title">{promptKey}</h1>
            <span className="current-version-tag">
              {`${selectedVersion || '--'} ${
                isLatestVersion
                  ? `(${locale.latestVersion || '最新版本'})`
                  : `(${locale.historyVersion || '历史版本'})`
              }`}
            </span>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button onClick={this.handleOpenVersionPanel}>
              {locale.versionManagement || '版本管理'}
            </Button>
            <Button type="primary" onClick={this.handlePublishNewVersion}>
              {locale.publishNewVersion || '发布新版本'}
            </Button>
          </div>
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
          {promptData?.gmtModified && (
            <div className="meta-item">
              <span className="meta-label">{locale.publishTime || '发布时间'}:</span>
              <span className="meta-value">{this.formatTime(promptData.gmtModified)}</span>
            </div>
          )}
          {labels.length > 0 && (
            <div
              className="meta-item"
              style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}
            >
              <span className="meta-label">{locale.label || 'Label'}:</span>
              {labels.map(label => (
                <Tag
                  key={label}
                  onClick={() => this.openLabelEditor(selectedVersion)}
                  style={{ cursor: 'pointer' }}
                >
                  {label}
                </Tag>
              ))}
              <a onClick={() => this.openLabelEditor(selectedVersion)}>
                {locale.manageLabel || '管理Label'}
              </a>
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
        {versionPanelVisible && (
          <div className="version-panel-mask" onClick={this.handleCloseVersionPanel}>
            <div className="version-panel" onClick={e => e.stopPropagation()}>
              <div className="version-panel-header">
                <div className="version-panel-title">{locale.versionManagement || '版本管理'}</div>
                <Icon
                  type="close"
                  onClick={this.handleCloseVersionPanel}
                  style={{ cursor: 'pointer' }}
                />
              </div>
              <div className="version-panel-tab">{locale.versionList || '版本列表'}</div>
              <div className="version-panel-body">
                <Table dataSource={versions} loading={loadingHistory} primaryKey="version">
                  <Table.Column
                    title={locale.version || '版本号'}
                    dataIndex="version"
                    width={120}
                  />
                  <Table.Column
                    title={locale.publishTime || '发布日期'}
                    dataIndex="gmtModified"
                    width={160}
                    cell={value => this.formatTime(value)}
                  />
                  <Table.Column
                    title={locale.label || 'Labels'}
                    dataIndex="version"
                    cell={(value, index, record) => {
                      const tags = this.getLabelsByVersion(value);
                      return (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                          {tags.length === 0 ? (
                            <a
                              onClick={() => this.openLabelEditor(record.version)}
                              style={{ color: '#999' }}
                            >
                              --
                            </a>
                          ) : (
                            tags.map(each => (
                              <Tag
                                key={each}
                                onClick={() => this.openLabelEditor(record.version)}
                                style={{ cursor: 'pointer' }}
                              >
                                {each}
                              </Tag>
                            ))
                          )}
                        </div>
                      );
                    }}
                  />
                  <Table.Column title={locale.commitMsg || 'commitMsg'} dataIndex="commitMsg" />
                  <Table.Column
                    title={locale.operation || '操作'}
                    width={160}
                    cell={(value, index, record) => (
                      <div style={{ display: 'flex', gap: 8 }}>
                        <a onClick={() => this.handleViewVersion(record.version)}>
                          {locale.view || '查看'}
                        </a>
                        <a onClick={() => this.openLabelEditor(record.version)}>
                          {locale.manageLabel || '管理Label'}
                        </a>
                      </div>
                    )}
                  />
                </Table>
                <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                  <Pagination
                    current={versionPageNo}
                    pageSize={versionPageSize}
                    total={versionTotal}
                    onChange={page => this.loadHistoryVersions(page, versionPageSize)}
                    onPageSizeChange={size => this.loadHistoryVersions(1, size)}
                    pageSizeSelector="filter"
                    pageSizeList={[10, 20, 50]}
                  />
                </div>
              </div>
            </div>
          </div>
        )}
        <Dialog
          title={locale.manageLabel || '管理 Label'}
          visible={labelEditorVisible}
          onOk={this.handleSaveLabelEditor}
          onCancel={this.closeLabelEditor}
          onClose={this.closeLabelEditor}
          okProps={{ loading: labelEditorSaving }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minWidth: 420 }}>
            <div>{`${locale.version || '版本'}: ${labelEditorVersion || '--'}`}</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ fontWeight: 500 }}>{locale.customLabels || 'Custom labels'}</div>
              <Checkbox.Group
                value={labelEditorSelected}
                onChange={value => this.setState({ labelEditorSelected: value || [] })}
              >
                {(labelEditorAll || []).map(each => (
                  <div key={each} style={{ marginBottom: 8 }}>
                    <Checkbox value={each}>{each}</Checkbox>
                  </div>
                ))}
              </Checkbox.Group>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <Input
                value={labelEditorNewLabel}
                onChange={value => this.setState({ labelEditorNewLabel: value })}
                placeholder={locale.newLabel || 'New label'}
                onPressEnter={this.addNewLabelToEditor}
              />
              <Button onClick={this.addNewLabelToEditor}>
                <Icon type="add" />
              </Button>
            </div>
          </div>
        </Dialog>
      </div>
    );
  }
}

export default PromptDetail;
