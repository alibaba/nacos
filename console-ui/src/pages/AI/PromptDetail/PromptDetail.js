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
  Icon,
  Input,
  Loading,
  Message,
  Select,
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
    this.state = {
      loading: true,
      versionLoading: false,
      // Governance data
      governanceData: null,
      // Current selected version
      selectedVersion: null,
      selectedVersionStatus: null,
      versionContent: null,
      // Template editing
      template: '',
      variables: [],
      serverVariables: [],
      isEditingDraft: false,
      editCommitMsg: '',
      // Version panel
      versionPanelVisible: false,
      // Label editor
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
      // BizTags editing
      editingBizTags: false,
      bizTagsValue: '',
      savingBizTags: false,
      // Pipeline
      pipelineInfo: null,
      // Operation states
      submitting: false,
      publishing: false,
      onlining: false,
      creatingDraft: false,
      publishUpdateLatest: true,
      // Debug
      variableValues: {},
      userInput: '',
      debugging: false,
      debugThinking: '',
      debugContent: '',
      debugError: null,
      // AI optimize
      optimizeDialogVisible: false,
    };
    this.debugResultRef = React.createRef();
  }

  componentDidMount() {
    this.loadGovernanceData();
  }

  // ===== Data Loading =====

  loadGovernanceData = callback => {
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';
    const { locale = {} } = this.props;

    this.setState({ loading: true });

    const params = new URLSearchParams();
    params.append('promptKey', promptKey);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      url: `v3/console/ai/prompt/governance?${params.toString()}`,
      success: data => {
        this.setState({ loading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          const gov = data.data;
          const versions = gov.versionDetails || [];

          // Auto-select version: draft first, then latest online, then first
          let autoVersion = this.state.selectedVersion;
          let autoStatus = this.state.selectedVersionStatus;
          if (!autoVersion && versions.length > 0) {
            const draft = versions.find(v => v.status === 'draft');
            const online = versions.find(v => v.status === 'online');
            const selected = draft || online || versions[0];
            autoVersion = selected.version;
            autoStatus = selected.status;
          }

          this.setState(
            {
              governanceData: gov,
              selectedVersion: autoVersion,
              selectedVersionStatus: autoStatus,
            },
            () => {
              if (autoVersion) {
                this.loadVersionContent(autoVersion);
              }
              if (callback && typeof callback === 'function') {
                callback();
              }
            }
          );
        } else {
          Message.error(data?.message || locale.getPromptFailed || 'Failed to get Prompt detail');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.getPromptFailed || 'Failed to get Prompt detail');
      },
    });
  };

  loadVersionContent = version => {
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';
    const { locale = {} } = this.props;

    this.setState({ versionLoading: true });

    const params = new URLSearchParams();
    params.append('promptKey', promptKey);
    params.append('version', version);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      url: `v3/console/ai/prompt/version?${params.toString()}`,
      success: data => {
        this.setState({ versionLoading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          const versionData = data.data;
          const template = versionData.template || '';
          const svrVars = versionData.variables || [];
          const initialVarValues = {};
          svrVars.forEach(v => {
            if (v.defaultValue) {
              initialVarValues[v.name] = v.defaultValue;
            }
          });

          // Find version status and pipeline info from governance data
          const versions = this.state.governanceData?.versionDetails || [];
          const versionSummary = versions.find(v => v.version === version);
          let pipelineInfo = null;
          if (versionSummary?.publishPipelineInfo) {
            try {
              pipelineInfo = JSON.parse(versionSummary.publishPipelineInfo);
            } catch (e) {
              // ignore parse error
            }
          }

          this.setState({
            versionContent: versionData,
            template,
            variables: this.extractVariables(template),
            serverVariables: svrVars,
            variableValues: initialVarValues,
            selectedVersion: version,
            selectedVersionStatus: versionSummary?.status || versionData.status || null,
            pipelineInfo,
            isEditingDraft: false,
            editCommitMsg: versionData.commitMsg || '',
          });
        } else {
          Message.error(data?.message || locale.getPromptFailed || 'Failed to get version content');
        }
      },
      error: () => {
        this.setState({ versionLoading: false });
        Message.error(locale.getPromptFailed || 'Failed to get version content');
      },
    });
  };

  // ===== Utility Methods =====

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
        second: '2-digit',
      });
    } catch (e) {
      return '--';
    }
  };

  getVersionStatusColor = status => {
    switch (status) {
      case 'draft':
        return '#1890ff';
      case 'reviewing':
        return '#fa8c16';
      case 'online':
        return '#52c41a';
      case 'offline':
        return '#999';
      default:
        return '#999';
    }
  };

  getVersionStatusText = status => {
    const { locale = {} } = this.props;
    switch (status) {
      case 'draft':
        return locale.statusDraft || 'Draft';
      case 'reviewing':
        return locale.statusReviewing || 'Reviewing';
      case 'online':
        return locale.statusOnline || 'Online';
      case 'offline':
        return locale.statusOffline || 'Offline';
      default:
        return status || '--';
    }
  };

  getLabelsByVersion = version => {
    const labels = this.state.governanceData?.labels || {};
    return Object.keys(labels).filter(label => labels[label] === version);
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || '';
    this.props.history.push(`/promptManagement?namespace=${namespaceId}`);
  };

  // ===== Version Panel =====

  handleOpenVersionPanel = () => {
    this.setState({ versionPanelVisible: true });
  };

  handleCloseVersionPanel = () => {
    this.setState({ versionPanelVisible: false });
  };

  handleViewVersion = version => {
    this.loadVersionContent(version);
    this.handleCloseVersionPanel();
  };

  handleVersionSelectChange = version => {
    this.loadVersionContent(version);
  };

  // ===== Description Editing =====

  handleEditDescription = () => {
    this.setState({
      editingDescription: true,
      descriptionValue: this.state.governanceData?.description || '',
    });
  };

  handleCancelDescription = () => {
    this.setState({ editingDescription: false });
  };

  handleSaveDescription = () => {
    const { locale = {} } = this.props;
    const { descriptionValue } = this.state;
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    this.setState({ savingDescription: true });

    request({
      method: 'PUT',
      url: 'v3/console/ai/prompt/description',
      data: { promptKey, description: descriptionValue, namespaceId },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ savingDescription: false });
        if (data && data.code === 0) {
          Message.success(locale.descriptionUpdateSuccess || 'Description updated');
          this.setState({ editingDescription: false });
          this.loadGovernanceData();
        } else {
          Message.error(data?.message || locale.updateDescFailed || 'Failed to update description');
        }
      },
      error: () => {
        this.setState({ savingDescription: false });
        Message.error(locale.updateDescFailed || 'Failed to update description');
      },
    });
  };

  // ===== BizTags Editor =====

  handleEditBizTags = () => {
    const bizTags = this.state.governanceData?.bizTagsStr;
    let parsed = [];
    if (bizTags) {
      try {
        const arr = JSON.parse(bizTags);
        parsed = Array.isArray(arr) ? arr : [];
      } catch (e) {
        parsed = bizTags
          .split(',')
          .map(s => s.trim())
          .filter(Boolean);
      }
    }
    this.setState({
      editingBizTags: true,
      bizTagsValue: parsed.join(','),
    });
  };

  handleCancelBizTags = () => {
    this.setState({ editingBizTags: false });
  };

  handleSaveBizTags = () => {
    const { locale = {} } = this.props;
    const { bizTagsValue } = this.state;
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';
    const tags = bizTagsValue
      .split(',')
      .map(s => s.trim())
      .filter(Boolean);

    this.setState({ savingBizTags: true });

    request({
      method: 'PUT',
      url: 'v3/console/ai/prompt/biz-tags',
      data: { promptKey, bizTags: tags.join(','), namespaceId },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ savingBizTags: false });
        if (data && data.code === 0) {
          Message.success(locale.bizTagsUpdateSuccess || 'Biz tags updated');
          this.setState({ editingBizTags: false });
          this.loadGovernanceData();
        } else {
          Message.error(data?.message || locale.updateBizTagsFailed || 'Failed to update biz tags');
        }
      },
      error: () => {
        this.setState({ savingBizTags: false });
        Message.error(locale.updateBizTagsFailed || 'Failed to update biz tags');
      },
    });
  };

  // ===== Labels Editor =====

  openLabelEditor = version => {
    const labels = this.state.governanceData?.labels || {};
    const allLabels = Object.keys(labels);
    const selectedLabels = allLabels.filter(label => labels[label] === version);
    this.setState({
      labelEditorVisible: true,
      labelEditorVersion: version,
      labelEditorAll: [...allLabels],
      labelEditorSelected: [...selectedLabels],
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
    });
  };

  addNewLabelToEditor = () => {
    const { locale = {} } = this.props;
    const { labelEditorNewLabel, labelEditorAll, labelEditorSelected } = this.state;
    const newLabel = (labelEditorNewLabel || '').trim();
    if (!newLabel) {
      Message.error(locale.labelRequired || 'Please enter label name');
      return;
    }
    if (!/^[A-Za-z0-9._-]+$/.test(newLabel)) {
      Message.error(locale.labelInvalid || 'Label only supports letters, numbers, .-_');
      return;
    }
    if (labelEditorAll.includes(newLabel)) {
      Message.error(locale.labelExists || 'Label already exists');
      return;
    }
    this.setState({
      labelEditorAll: [...labelEditorAll, newLabel],
      labelEditorSelected: [...labelEditorSelected, newLabel],
      labelEditorNewLabel: '',
    });
  };

  handleSaveLabelEditor = () => {
    const { locale = {} } = this.props;
    const { labelEditorVersion, labelEditorSelected } = this.state;
    const labels = this.state.governanceData?.labels || {};
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    if (!labelEditorVersion) {
      Message.error(locale.labelRequired || 'Version required');
      return;
    }

    // Build new labels map: keep labels for other versions, update for this version
    const newLabelsMap = {};
    Object.keys(labels).forEach(label => {
      if (labels[label] !== labelEditorVersion) {
        newLabelsMap[label] = labels[label];
      }
    });
    labelEditorSelected.forEach(label => {
      newLabelsMap[label] = labelEditorVersion;
    });

    this.setState({ labelEditorSaving: true });

    request({
      method: 'PUT',
      url: 'v3/console/ai/prompt/labels',
      data: {
        promptKey,
        labels: JSON.stringify(newLabelsMap),
        namespaceId,
      },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ labelEditorSaving: false });
        if (data && data.code === 0) {
          Message.success(locale.labelsUpdateSuccess || 'Labels updated');
          this.closeLabelEditor();
          this.loadGovernanceData();
        } else {
          Message.error(data?.message || locale.bindLabelFailed || 'Failed to update labels');
        }
      },
      error: () => {
        this.setState({ labelEditorSaving: false });
        Message.error(locale.bindLabelFailed || 'Failed to update labels');
      },
    });
  };

  // ===== Draft Editing Mode =====

  handleStartEditDraft = () => {
    this.setState({
      isEditingDraft: true,
      editCommitMsg: this.state.versionContent?.commitMsg || '',
    });
  };

  handleCancelEditDraft = () => {
    // Revert template to saved version content
    const template = this.state.versionContent?.template || '';
    this.setState({
      isEditingDraft: false,
      template,
      variables: this.extractVariables(template),
      editCommitMsg: this.state.versionContent?.commitMsg || '',
    });
  };

  handleSaveDraft = () => {
    const { locale = {} } = this.props;
    const { template, serverVariables, editCommitMsg } = this.state;
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    request({
      method: 'PUT',
      url: 'v3/console/ai/prompt/draft',
      data: {
        promptKey,
        template,
        variables: JSON.stringify(serverVariables || []),
        commitMsg: editCommitMsg,
        namespaceId,
      },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        if (data && data.code === 0) {
          Message.success(locale.updateDraftSuccess || 'Draft saved');
          this.setState(
            { isEditingDraft: false, selectedVersion: null, selectedVersionStatus: null },
            () => {
              this.loadGovernanceData();
            }
          );
        } else {
          Message.error(data?.message || locale.updateDraftFailed || 'Failed to save draft');
        }
      },
      error: () => {
        Message.error(locale.updateDraftFailed || 'Failed to save draft');
      },
    });
  };

  handleTemplateChange = value => {
    const variables = this.extractVariables(value);
    this.setState({ template: value, variables });
  };

  // ===== Lifecycle Operations =====

  handleDeleteDraft = () => {
    const { locale = {} } = this.props;
    Dialog.confirm({
      title: locale.deleteDraft || 'Delete Draft',
      content: locale.deleteDraftConfirm || 'Are you sure you want to delete this draft?',
      onOk: () => {
        const promptKey = getParams('promptKey') || '';
        const namespaceId = getParams('namespace') || '';

        const params = new URLSearchParams();
        params.append('promptKey', promptKey);
        if (namespaceId) {
          params.append('namespaceId', namespaceId);
        }

        request({
          method: 'DELETE',
          url: `v3/console/ai/prompt/draft?${params.toString()}`,
          success: data => {
            if (data && data.code === 0) {
              Message.success(locale.deleteDraftSuccess || 'Draft deleted');
              this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
                this.loadGovernanceData();
              });
            } else {
              Message.error(data?.message || locale.deleteDraftFailed || 'Failed to delete draft');
            }
          },
          error: () => {
            Message.error(locale.deleteDraftFailed || 'Failed to delete draft');
          },
        });
      },
    });
  };

  handleSubmitForReview = () => {
    const { locale = {} } = this.props;
    const { selectedVersion, selectedVersionStatus } = this.state;
    if (selectedVersionStatus !== 'draft') return;

    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    this.setState({ submitting: true });

    request({
      method: 'POST',
      url: 'v3/console/ai/prompt/submit',
      data: { promptKey, version: selectedVersion, namespaceId },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ submitting: false });
        if (data && data.code === 0) {
          Message.success(locale.submitSuccess || 'Submitted for review');
          this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
            this.loadGovernanceData();
          });
        } else {
          Message.error(data?.message || locale.submitFailed || 'Failed to submit');
        }
      },
      error: () => {
        this.setState({ submitting: false });
        Message.error(locale.submitFailed || 'Failed to submit');
      },
    });
  };

  handlePublish = () => {
    const { locale = {} } = this.props;
    const { selectedVersion, pipelineInfo } = this.state;

    if (pipelineInfo && pipelineInfo.status !== 'APPROVED') {
      Message.warning(locale.publishNotApproved || 'Cannot publish: pipeline not approved');
      return;
    }

    this.setState({ publishUpdateLatest: true });

    Dialog.confirm({
      title: locale.publish || 'Publish',
      content: (
        <div>
          <p>
            {(locale.publishConfirm || 'Are you sure you want to publish version {0}?').replace(
              '{0}',
              selectedVersion
            )}
          </p>
          <Checkbox
            defaultChecked
            onChange={checked => this.setState({ publishUpdateLatest: checked })}
          >
            {locale.updateLatestLabel || 'Update latest label'}
          </Checkbox>
        </div>
      ),
      onOk: () => {
        const promptKey = getParams('promptKey') || '';
        const namespaceId = getParams('namespace') || '';

        this.setState({ publishing: true });

        request({
          method: 'POST',
          url: 'v3/console/ai/prompt/publish',
          data: {
            promptKey,
            version: selectedVersion,
            updateLatestLabel: this.state.publishUpdateLatest,
            namespaceId,
          },
          contentType: 'application/x-www-form-urlencoded',
          success: data => {
            this.setState({ publishing: false });
            if (data && data.code === 0) {
              Message.success(locale.publishSuccess || 'Published successfully');
              this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
                this.loadGovernanceData();
              });
            } else {
              Message.error(data?.message || locale.publishFailed || 'Failed to publish');
            }
          },
          error: () => {
            this.setState({ publishing: false });
            Message.error(locale.publishFailed || 'Failed to publish');
          },
        });
      },
    });
  };

  handleForcePublish = () => {
    const { locale = {} } = this.props;
    const { selectedVersion } = this.state;

    this.setState({ publishUpdateLatest: true });

    Dialog.confirm({
      title: locale.forcePublish || 'Force Publish',
      content: (
        <div>
          <p>
            {(
              locale.forcePublishConfirm || 'Are you sure you want to force publish version {0}?'
            ).replace('{0}', selectedVersion)}
          </p>
          <Checkbox
            defaultChecked
            onChange={checked => this.setState({ publishUpdateLatest: checked })}
          >
            {locale.updateLatestLabel || 'Update latest label'}
          </Checkbox>
        </div>
      ),
      onOk: () => {
        const promptKey = getParams('promptKey') || '';
        const namespaceId = getParams('namespace') || '';

        this.setState({ publishing: true });

        request({
          method: 'POST',
          url: 'v3/console/ai/prompt/force-publish',
          data: {
            promptKey,
            version: selectedVersion,
            updateLatestLabel: this.state.publishUpdateLatest,
            namespaceId,
          },
          contentType: 'application/x-www-form-urlencoded',
          success: data => {
            this.setState({ publishing: false });
            if (data && data.code === 0) {
              Message.success(locale.forcePublishSuccess || 'Force published successfully');
              this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
                this.loadGovernanceData();
              });
            } else {
              Message.error(data?.message || locale.publishFailed || 'Failed to publish');
            }
          },
          error: () => {
            this.setState({ publishing: false });
            Message.error(locale.publishFailed || 'Failed to publish');
          },
        });
      },
    });
  };

  handleOnlineVersion = version => {
    const { locale = {} } = this.props;
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    this.setState({ onlining: true });

    request({
      method: 'POST',
      url: 'v3/console/ai/prompt/online',
      data: { promptKey, version, namespaceId },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ onlining: false });
        if (data && data.code === 0) {
          Message.success(locale.onlineSuccess || 'Online successfully');
          this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
            this.loadGovernanceData();
          });
        } else {
          Message.error(data?.message || locale.onlineFailed || 'Failed to go online');
        }
      },
      error: () => {
        this.setState({ onlining: false });
        Message.error(locale.onlineFailed || 'Failed to go online');
      },
    });
  };

  handleOfflineVersion = version => {
    const { locale = {} } = this.props;
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    this.setState({ onlining: true });

    request({
      method: 'POST',
      url: 'v3/console/ai/prompt/offline',
      data: { promptKey, version, namespaceId },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ onlining: false });
        if (data && data.code === 0) {
          Message.success(locale.offlineSuccess || 'Offline successfully');
          this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
            this.loadGovernanceData();
          });
        } else {
          Message.error(data?.message || locale.offlineFailed || 'Failed to go offline');
        }
      },
      error: () => {
        this.setState({ onlining: false });
        Message.error(locale.offlineFailed || 'Failed to go offline');
      },
    });
  };

  handleCreateDraftFromVersion = basedOnVersion => {
    const { locale = {} } = this.props;
    const promptKey = getParams('promptKey') || '';
    const namespaceId = getParams('namespace') || '';

    this.setState({ creatingDraft: true });

    request({
      method: 'POST',
      url: 'v3/console/ai/prompt/draft',
      data: {
        promptKey,
        basedOnVersion: basedOnVersion || '',
        template: this.state.template || '',
        variables: JSON.stringify(this.state.serverVariables || []),
        namespaceId,
      },
      contentType: 'application/x-www-form-urlencoded',
      success: data => {
        this.setState({ creatingDraft: false });
        if (data && data.code === 0) {
          Message.success(locale.createDraftSuccess || 'Draft created');
          this.setState({ selectedVersion: null, selectedVersionStatus: null }, () => {
            this.loadGovernanceData();
          });
        } else {
          Message.error(data?.message || locale.createDraftFailed || 'Failed to create draft');
        }
      },
      error: () => {
        this.setState({ creatingDraft: false });
        Message.error(locale.createDraftFailed || 'Failed to create draft');
      },
    });
  };

  handleDeletePrompt = () => {
    const { locale = {} } = this.props;
    const promptKey = getParams('promptKey') || '';

    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (
        locale.deletePromptConfirm || 'Are you sure you want to delete Prompt "{0}"?'
      ).replace('{0}', promptKey),
      onOk: () => {
        const namespaceId = getParams('namespace') || '';
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
              Message.success(locale.deleteSuccess || 'Delete successful');
              setTimeout(() => {
                this.handleGoBack();
              }, 1000);
            } else {
              Message.error(data?.message || locale.deleteFailed || 'Delete failed');
            }
          },
          error: () => {
            Message.error(locale.deleteFailed || 'Delete failed');
          },
        });
      },
    });
  };

  // ===== AI Optimize =====

  handleOpenOptimizeDialog = () => {
    this.setState({ optimizeDialogVisible: true });
  };

  handleCloseOptimizeDialog = () => {
    this.setState({ optimizeDialogVisible: false });
  };

  handleApplyOptimizedPrompt = optimizedPrompt => {
    const variables = this.extractVariables(optimizedPrompt);
    this.setState({
      template: optimizedPrompt,
      variables,
      optimizeDialogVisible: false,
    });
    const { locale = {} } = this.props;
    Message.success(locale.optimizeApplied || 'Optimization applied');
  };

  // ===== Debug =====

  handleVariableChange = (variable, value) => {
    this.setState(prevState => ({
      variableValues: {
        ...prevState.variableValues,
        [variable]: value,
      },
    }));
  };

  handleUserInputChange = value => {
    this.setState({ userInput: value });
  };

  renderPromptWithVariables = () => {
    const { template, variableValues, serverVariables } = this.state;
    const merged = {};
    (serverVariables || []).forEach(v => {
      if (v.defaultValue) {
        merged[v.name] = v.defaultValue;
      }
    });
    Object.keys(variableValues).forEach(key => {
      if (variableValues[key]) {
        merged[key] = variableValues[key];
      }
    });
    let renderedPrompt = template;
    Object.keys(merged).forEach(variable => {
      const regex = new RegExp(`\\{\\{${variable}\\}\\}`, 'g');
      renderedPrompt = renderedPrompt.replace(regex, merged[variable] || '');
    });
    return renderedPrompt;
  };

  handleStartDebug = () => {
    const { userInput } = this.state;
    const { locale = {} } = this.props;

    if (!userInput || !userInput.trim()) {
      Message.error(locale.userInputRequired || 'Please enter user input');
      return;
    }

    const renderedPrompt = this.renderPromptWithVariables();

    this.setState({
      debugging: true,
      debugThinking: '',
      debugContent: '',
      debugError: null,
    });

    const ctxPath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    const url = `${window.location.origin}${ctxPath}v3/console/copilot/prompt/debug`;
    const token = localStorage.getItem('token');

    this.startDebugStream(url, { prompt: renderedPrompt, userInput }, token);
  };

  startDebugStream = (url, payload, token) => {
    let accessToken = '';
    try {
      const tokenObj = JSON.parse(token);
      accessToken = tokenObj.accessToken || '';
    } catch (e) {
      // ignore
    }

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
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
                      // ignore parse error
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

    if (this.debugResultRef.current) {
      this.debugResultRef.current.scrollTop = this.debugResultRef.current.scrollHeight;
    }
  };

  handleClearDebug = () => {
    this.setState({
      debugThinking: '',
      debugContent: '',
      debugError: null,
    });
  };

  // ===== Render =====

  render() {
    const { locale = {} } = this.props;
    const {
      loading,
      versionLoading,
      governanceData,
      selectedVersion,
      selectedVersionStatus,
      template,
      variables,
      isEditingDraft,
      editCommitMsg,
      versionPanelVisible,
      pipelineInfo,
      labelEditorVisible,
      labelEditorVersion,
      labelEditorSelected,
      labelEditorAll,
      labelEditorNewLabel,
      labelEditorSaving,
      editingDescription,
      descriptionValue,
      savingDescription,
      editingBizTags,
      bizTagsValue,
      savingBizTags,
      optimizeDialogVisible,
      variableValues,
      userInput,
      debugging,
      debugThinking,
      debugContent,
      debugError,
      submitting,
      publishing,
      onlining,
      creatingDraft,
    } = this.state;

    const promptKey = getParams('promptKey') || '';

    if (loading && !governanceData) {
      return (
        <div className="prompt-detail">
          <div className="loading-container">
            <Loading />
          </div>
        </div>
      );
    }

    const versions = governanceData?.versionDetails || [];
    const hasVersions = versions.length > 0;
    const versionLabels = selectedVersion ? this.getLabelsByVersion(selectedVersion) : [];
    const onlineCnt = governanceData?.onlineCnt || 0;
    const editingVersionStr = governanceData?.editingVersion || null;
    const reviewingVersionStr = governanceData?.reviewingVersion || null;
    const description = governanceData?.description || '';
    const bizTags = (() => {
      const raw = governanceData?.bizTagsStr;
      if (!raw) return [];
      try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
      } catch (e) {
        return raw
          .split(',')
          .map(s => s.trim())
          .filter(Boolean);
      }
    })();
    const isDraft = selectedVersionStatus === 'draft';
    const isReviewing = selectedVersionStatus === 'reviewing';

    return (
      <div className="prompt-detail">
        {/* Back Navigation */}
        <div style={{ marginBottom: 8 }}>
          <a onClick={this.handleGoBack} style={{ fontSize: 13, color: '#666', cursor: 'pointer' }}>
            <Icon type="arrow-left" style={{ marginRight: 4 }} />
            {locale.backToList || 'Back to List'}
          </a>
        </div>

        {/* Page Header */}
        <div className="page-header">
          <div className="header-left">
            <h1 className="prompt-title">{promptKey}</h1>
            {/* Version Selector */}
            {hasVersions && (
              <Select
                className="version-selector"
                value={selectedVersion}
                onChange={this.handleVersionSelectChange}
                style={{ minWidth: 200 }}
              >
                {versions.map(v => (
                  <Select.Option key={v.version} value={v.version}>
                    {v.version}
                    <span
                      style={{
                        marginLeft: 8,
                        fontSize: 12,
                        color: this.getVersionStatusColor(v.status),
                      }}
                    >
                      ({this.getVersionStatusText(v.status)})
                    </span>
                  </Select.Option>
                ))}
              </Select>
            )}
            {selectedVersion && (
              <Tag
                size="small"
                color={this.getVersionStatusColor(selectedVersionStatus)}
                style={{ borderRadius: 4 }}
              >
                {selectedVersion} - {this.getVersionStatusText(selectedVersionStatus)}
              </Tag>
            )}
            {onlineCnt > 0 && (
              <span style={{ color: '#52c41a', fontSize: 13 }}>
                {locale.onlineCnt || 'Online'}: {onlineCnt}
              </span>
            )}
          </div>
          <div className="header-actions">
            {hasVersions && (
              <Button onClick={this.handleOpenVersionPanel}>
                {locale.versionManagement || 'Version Management'}
              </Button>
            )}
            {/* Context-sensitive lifecycle action buttons */}
            {isDraft && !isEditingDraft && (
              <>
                <Button type="primary" onClick={this.handleStartEditDraft}>
                  <Icon type="edit" /> {locale.editDraft || 'Edit Draft'}
                </Button>
                <Button onClick={this.handleSubmitForReview} loading={submitting}>
                  {locale.submitForReview || 'Submit for Review'}
                </Button>
                <Button warning onClick={this.handleDeleteDraft}>
                  {locale.deleteDraft || 'Delete Draft'}
                </Button>
              </>
            )}
            {isDraft && isEditingDraft && (
              <>
                <Button type="primary" onClick={this.handleSaveDraft}>
                  {locale.saveDraft || 'Save Draft'}
                </Button>
                <Button onClick={this.handleCancelEditDraft}>
                  {locale.cancelEdit || 'Cancel'}
                </Button>
                <Button onClick={this.handleOpenOptimizeDialog} disabled={!template}>
                  {locale.aiOptimize || 'AI Optimize'}
                </Button>
              </>
            )}
            {isReviewing && (
              <>
                <Button
                  type="primary"
                  onClick={this.handlePublish}
                  loading={publishing}
                  disabled={pipelineInfo && pipelineInfo.status !== 'APPROVED'}
                >
                  {locale.publish || 'Publish'}
                </Button>
                {pipelineInfo && pipelineInfo.status === 'REJECTED' && (
                  <Button onClick={this.handleForcePublish} loading={publishing}>
                    {locale.forcePublish || 'Force Publish'}
                  </Button>
                )}
              </>
            )}
            {selectedVersionStatus === 'online' && (
              <>
                <Button
                  onClick={() => this.handleOfflineVersion(selectedVersion)}
                  loading={onlining}
                >
                  {locale.offlineAction || 'Offline'}
                </Button>
                {!editingVersionStr && !reviewingVersionStr && (
                  <Button
                    onClick={() => this.handleCreateDraftFromVersion(selectedVersion)}
                    loading={creatingDraft}
                  >
                    {locale.createDraftFromVersion || 'Create Draft From This'}
                  </Button>
                )}
              </>
            )}
            {selectedVersionStatus === 'offline' && (
              <>
                <Button
                  type="primary"
                  onClick={() => this.handleOnlineVersion(selectedVersion)}
                  loading={onlining}
                >
                  {locale.onlineAction || 'Online'}
                </Button>
                {!editingVersionStr && !reviewingVersionStr && (
                  <Button
                    onClick={() => this.handleCreateDraftFromVersion(selectedVersion)}
                    loading={creatingDraft}
                  >
                    {locale.createDraftFromVersion || 'Create Draft From This'}
                  </Button>
                )}
              </>
            )}
            <Button warning onClick={this.handleDeletePrompt}>
              <Icon type="delete" /> {locale.delete || 'Delete'}
            </Button>
          </div>
        </div>

        {/* Description & Tags */}
        <div className="meta-section">
          <div className="description-row">
            <span className="meta-label">{locale.description || 'Description'}:</span>
            {editingDescription ? (
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flex: 1 }}>
                <Input
                  value={descriptionValue}
                  onChange={val => this.setState({ descriptionValue: val })}
                  style={{ flex: 1 }}
                  placeholder={locale.descriptionPlaceholder || 'Enter description'}
                />
                <Button
                  size="small"
                  type="primary"
                  onClick={this.handleSaveDescription}
                  loading={savingDescription}
                >
                  {locale.save || 'Save'}
                </Button>
                <Button size="small" onClick={this.handleCancelDescription}>
                  {locale.cancel || 'Cancel'}
                </Button>
              </div>
            ) : (
              <span className="description-text">
                {description || '--'}
                <Icon type="edit" className="edit-icon" onClick={this.handleEditDescription} />
              </span>
            )}
          </div>
          <div className="tags-row">
            <span className="meta-label">{locale.bizTags || 'Biz Tags'}:</span>
            {editingBizTags ? (
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flex: 1 }}>
                <Input
                  value={bizTagsValue}
                  onChange={val => this.setState({ bizTagsValue: val })}
                  style={{ flex: 1 }}
                  placeholder={locale.bizTagsPlaceholder || 'Enter tags separated by commas'}
                />
                <Button
                  size="small"
                  type="primary"
                  onClick={this.handleSaveBizTags}
                  loading={savingBizTags}
                >
                  {locale.save || 'Save'}
                </Button>
                <Button size="small" onClick={this.handleCancelBizTags}>
                  {locale.cancel || 'Cancel'}
                </Button>
              </div>
            ) : (
              <span className="description-text">
                {bizTags.length > 0
                  ? bizTags.map(tag => (
                      <Tag key={tag} size="small" style={{ borderRadius: 4 }}>
                        {tag}
                      </Tag>
                    ))
                  : '--'}
                <Icon type="edit" className="edit-icon" onClick={this.handleEditBizTags} />
              </span>
            )}
          </div>
          {versionLabels.length > 0 && (
            <div className="tags-row">
              <span className="meta-label">{locale.versionLabels || 'Labels'}:</span>
              {versionLabels.map(label => (
                <Tag key={label} size="small" color="blue" style={{ borderRadius: 4 }}>
                  {label}
                </Tag>
              ))}
              {selectedVersionStatus !== 'draft' && selectedVersionStatus !== 'reviewing' && (
                <a
                  onClick={() => this.openLabelEditor(selectedVersion)}
                  style={{ marginLeft: 8, fontSize: 12 }}
                >
                  {locale.manageLabels || 'Manage Labels'}
                </a>
              )}
            </div>
          )}
          {versionLabels.length === 0 && selectedVersion && (
            <div className="tags-row">
              <span className="meta-label">{locale.versionLabels || 'Labels'}:</span>
              <span style={{ color: '#999', fontSize: 12 }}>{locale.noLabels || 'No labels'}</span>
              {selectedVersionStatus !== 'draft' && selectedVersionStatus !== 'reviewing' && (
                <a
                  onClick={() => this.openLabelEditor(selectedVersion)}
                  style={{ marginLeft: 8, fontSize: 12 }}
                >
                  {locale.manageLabels || 'Manage Labels'}
                </a>
              )}
            </div>
          )}
        </div>

        {/* Pipeline Status */}
        {isReviewing && pipelineInfo && (
          <div className="pipeline-section">
            <span className="meta-label">{locale.pipelineStatus || 'Pipeline'}:</span>
            <Tag
              size="small"
              color={
                pipelineInfo.status === 'APPROVED'
                  ? '#52c41a'
                  : pipelineInfo.status === 'REJECTED'
                  ? '#ff4d4f'
                  : '#fa8c16'
              }
              style={{ borderRadius: 4 }}
            >
              {pipelineInfo.status === 'APPROVED'
                ? locale.pipelineApproved || 'Approved'
                : pipelineInfo.status === 'REJECTED'
                ? locale.pipelineRejected || 'Rejected'
                : locale.pipelinePending || 'In Progress'}
            </Tag>
            {pipelineInfo.status === 'REJECTED' && pipelineInfo.pipeline && (
              <div className="pipeline-details">
                {pipelineInfo.pipeline.map((node, idx) => (
                  <div key={idx} className="pipeline-node">
                    <Tag
                      size="small"
                      color={node.passed ? '#52c41a' : '#ff4d4f'}
                      style={{ borderRadius: 4 }}
                    >
                      {node.nodeId || `Node ${idx + 1}`}
                    </Tag>
                    {node.message && <pre className="pipeline-message">{node.message}</pre>}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Content Area */}
        <Loading visible={versionLoading} style={{ width: '100%' }}>
          <div className="detail-container">
            {/* Left: Template Editor */}
            <div className="detail-left">
              <div className="section-header">
                <div className="section-label">{locale.promptTemplate || 'Prompt 模板'}</div>
                {isEditingDraft && (
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <span style={{ color: '#666', fontSize: 13 }}>
                      {locale.commitMsg || 'Commit Message'}:
                    </span>
                    <Input
                      size="small"
                      value={editCommitMsg}
                      onChange={val => this.setState({ editCommitMsg: val })}
                      placeholder={locale.commitMsgPlaceholder || 'Commit message'}
                      style={{ width: 200 }}
                    />
                  </div>
                )}
              </div>
              <div className="editor-container">
                <MonacoEditor
                  language="plaintext"
                  width="100%"
                  height="100%"
                  value={template}
                  onChange={isEditingDraft ? this.handleTemplateChange : undefined}
                  options={{
                    readOnly: !isEditingDraft,
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    wordWrap: 'on',
                    scrollBeyondLastLine: false,
                  }}
                />
              </div>

              {/* Variable Definition Editor (only in draft editing mode) */}
              {isEditingDraft && variables.length > 0 && (
                <div className="variable-def-editor">
                  <div className="section-header">
                    <div className="section-label">
                      {locale.templateVariables || '模板参数'}
                      <span style={{ color: '#1890ff', fontSize: 12, marginLeft: 8 }}>
                        ({variables.length})
                      </span>
                    </div>
                  </div>
                  <div className="variable-def-list">
                    {variables.map((variable, index) => {
                      const svrVar = (this.state.serverVariables || []).find(
                        v => v.name === variable
                      );
                      return (
                        <div key={index} className="variable-def-item">
                          <div className="variable-def-name">{`{{${variable}}}`}</div>
                          <div className="variable-def-fields">
                            <Input
                              size="small"
                              addonBefore={locale.defaultValue || '默认值'}
                              value={svrVar?.defaultValue || ''}
                              onChange={val => {
                                const updated = [...(this.state.serverVariables || [])];
                                const idx = updated.findIndex(v => v.name === variable);
                                if (idx >= 0) {
                                  updated[idx] = { ...updated[idx], defaultValue: val };
                                } else {
                                  updated.push({
                                    name: variable,
                                    defaultValue: val,
                                    description: '',
                                  });
                                }
                                this.setState({ serverVariables: updated });
                              }}
                              placeholder={locale.defaultValuePlaceholder || '输入默认值'}
                              style={{ flex: 1 }}
                            />
                            <Input
                              size="small"
                              addonBefore={locale.variableDesc || '描述'}
                              value={svrVar?.description || ''}
                              onChange={val => {
                                const updated = [...(this.state.serverVariables || [])];
                                const idx = updated.findIndex(v => v.name === variable);
                                if (idx >= 0) {
                                  updated[idx] = { ...updated[idx], description: val };
                                } else {
                                  updated.push({
                                    name: variable,
                                    defaultValue: '',
                                    description: val,
                                  });
                                }
                                this.setState({ serverVariables: updated });
                              }}
                              placeholder={locale.variableDescPlaceholder || '输入变量描述'}
                              style={{ flex: 1 }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>

            {/* Right: Debug Panel */}
            <div className="detail-right">
              <div className="debug-card">
                <div className="debug-title">
                  <Icon type="bug" style={{ marginRight: 8 }} />
                  {locale.debugPanel || 'Prompt 调试'}
                </div>

                <div className="debug-input-section">
                  {/* Variable Inputs */}
                  {variables.length > 0 && (
                    <div className="variable-inputs">
                      <div className="section-subtitle">
                        {locale.templateVariables || '模板参数'}
                        <span className="variables-count">{variables.length}</span>
                      </div>
                      <div className="variable-list">
                        {variables.map((variable, index) => {
                          const svrVar = (this.state.serverVariables || []).find(
                            v => v.name === variable
                          );
                          const defaultVal = svrVar?.defaultValue;
                          const desc = svrVar?.description;
                          const placeholder = defaultVal
                            ? `默认值: ${defaultVal}`
                            : `输入 ${variable}`;
                          return (
                            <div key={index} className="variable-input-item">
                              <label className="variable-label">
                                {`{{${variable}}}`}
                                {desc && (
                                  <span
                                    style={{
                                      fontWeight: 'normal',
                                      color: '#999',
                                      marginLeft: 4,
                                      fontSize: 12,
                                    }}
                                  >
                                    {desc}
                                  </span>
                                )}
                              </label>
                              <Input
                                size="small"
                                placeholder={placeholder}
                                value={variableValues[variable] || ''}
                                onChange={value => this.handleVariableChange(variable, value)}
                                disabled={debugging}
                              />
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {variables.length > 0 && <div className="input-divider"></div>}

                  {/* User Input */}
                  <div className="user-input-wrapper">
                    <div className="section-subtitle">
                      {locale.userInput || '用户输入'}
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
                      {debugging
                        ? locale.streaming || '调试中...'
                        : locale.startDebug || '开始调试'}
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
                    {locale.modelOutput || '模型响应'}
                    {debugging && <Loading size="small" style={{ marginLeft: 8 }} />}
                  </div>
                  <div className="model-response-box" ref={this.debugResultRef}>
                    {!debugThinking && !debugContent && !debugging && (
                      <div className="response-placeholder">
                        {locale.responsePlaceholder || '点击"开始调试"后，模型响应将显示在这里...'}
                      </div>
                    )}
                    {debugThinking && (
                      <div className="result-section thinking-section">
                        <div className="result-label">
                          <Icon type="eye" style={{ marginRight: 4 }} />
                          {locale.thinking || '思考过程'}
                        </div>
                        <div className="result-content thinking-content">{debugThinking}</div>
                      </div>
                    )}
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

                  <div className="debug-hint">
                    {locale.debugHint ||
                      '调试时会将 Prompt 模板中的变量替换后作为系统提示词发送给模型'}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Loading>

        {/* Version Panel Drawer */}
        <Dialog
          visible={versionPanelVisible}
          title={locale.versionManagement || 'Version Management'}
          onClose={this.handleCloseVersionPanel}
          footer={false}
          style={{ width: 700 }}
        >
          <Table dataSource={versions} primaryKey="version">
            <Table.Column
              title={locale.version || 'Version'}
              dataIndex="version"
              cell={value => <a onClick={() => this.handleViewVersion(value)}>{value}</a>}
            />
            <Table.Column
              title={locale.status || 'Status'}
              dataIndex="status"
              cell={value => (
                <Tag
                  size="small"
                  color={this.getVersionStatusColor(value)}
                  style={{ borderRadius: 4 }}
                >
                  {this.getVersionStatusText(value)}
                </Tag>
              )}
            />
            <Table.Column title={locale.author || 'Author'} dataIndex="srcUser" />
            <Table.Column
              title={locale.updateTime || 'Update Time'}
              dataIndex="gmtModified"
              cell={value => this.formatTime(value)}
            />
            <Table.Column
              title={locale.versionLabels || 'Labels'}
              cell={(value, index, record) => {
                const labels = this.getLabelsByVersion(record.version);
                return labels.length > 0
                  ? labels.map(l => (
                      <Tag
                        key={l}
                        size="small"
                        color="blue"
                        style={{ borderRadius: 4, marginRight: 4 }}
                      >
                        {l}
                      </Tag>
                    ))
                  : '--';
              }}
            />
            <Table.Column
              title={locale.operation || 'Operation'}
              cell={(value, index, record) => (
                <div style={{ display: 'flex', gap: 8 }}>
                  <a onClick={() => this.handleViewVersion(record.version)}>
                    {locale.view || 'View'}
                  </a>
                  {record.status !== 'draft' && record.status !== 'reviewing' && (
                    <a onClick={() => this.openLabelEditor(record.version)}>
                      {locale.manageLabels || 'Labels'}
                    </a>
                  )}
                  {record.status === 'online' && (
                    <a onClick={() => this.handleOfflineVersion(record.version)}>
                      {locale.offlineAction || 'Offline'}
                    </a>
                  )}
                  {record.status === 'offline' && (
                    <a onClick={() => this.handleOnlineVersion(record.version)}>
                      {locale.onlineAction || 'Online'}
                    </a>
                  )}
                </div>
              )}
            />
          </Table>
        </Dialog>

        {/* Label Editor Dialog */}
        <Dialog
          visible={labelEditorVisible}
          title={`${locale.manageLabels || 'Manage Labels'} - ${labelEditorVersion}`}
          onClose={this.closeLabelEditor}
          onOk={this.handleSaveLabelEditor}
          okProps={{ loading: labelEditorSaving }}
        >
          <div className="label-editor">
            {labelEditorAll.map(label => (
              <div key={label} className="label-item">
                <Checkbox
                  checked={labelEditorSelected.includes(label)}
                  onChange={checked => {
                    if (checked) {
                      this.setState({ labelEditorSelected: [...labelEditorSelected, label] });
                    } else {
                      this.setState({
                        labelEditorSelected: labelEditorSelected.filter(l => l !== label),
                      });
                    }
                  }}
                >
                  {label}
                </Checkbox>
              </div>
            ))}
            <div className="new-label-row">
              <Input
                size="small"
                value={labelEditorNewLabel}
                onChange={val => this.setState({ labelEditorNewLabel: val })}
                placeholder={locale.newLabel || 'New Label'}
                onPressEnter={this.addNewLabelToEditor}
                style={{ width: 150 }}
              />
              <Button size="small" onClick={this.addNewLabelToEditor}>
                {locale.create || 'Add'}
              </Button>
            </div>
          </div>
        </Dialog>

        {/* AI Optimize Dialog */}
        {optimizeDialogVisible && (
          <PromptOptimizeDialog
            visible={optimizeDialogVisible}
            template={template}
            onClose={this.handleCloseOptimizeDialog}
            onApply={this.handleApplyOptimizedPrompt}
          />
        )}
      </div>
    );
  }
}

export default PromptDetail;
