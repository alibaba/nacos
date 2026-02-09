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
  Card,
  Collapse,
  ConfigProvider,
  Dialog,
  Message,
  Tag,
  Grid,
  Icon,
  Loading,
  Input,
} from '@alifd/next';
import SkillOptimizeDialog from '../SkillManagement/SkillOptimizeDialog';
import MarkdownRenderer from '../../../components/MarkdownRenderer/MarkdownRenderer';
import MonacoEditor from '../../../components/MonacoEditor/MonacoEditor';
import MagicWandIcon from '../../../components/MagicWandIcon/MagicWandIcon';
import JSZip from 'jszip';
import { getLanguageFromFileName } from '../../../utils/languageDetector';
import { getParams, request } from '@/globalLib';

const { Row, Col } = Grid;
const { Panel } = Collapse;

@ConfigProvider.config
class SkillDetail extends React.Component {
  static displayName = 'SkillManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: true,
      skillData: null,
      optimizeDialogVisible: false,
      expandedKeys: [],
      fileTree: null,
      selectedFile: null,
      editingFileName: null, // 正在编辑的文件名（格式：{nodeKey, oldName, type}）
      editingFileNameValue: '', // 正在编辑的文件名的临时值
      resources: [], // 资源列表（用于编辑）
      draggingFile: null, // 正在拖拽的文件（格式：{resourceKey, name, type}）
      dragOverFolder: null, // 当前拖拽悬停的文件夹名称
    };
  }

  componentDidMount() {
    this.loadSkillData();
  }

  componentDidUpdate(prevProps, prevState) {
    // 当skillData加载完成后，初始化文件树和选中文件
    if (!prevState.skillData && this.state.skillData) {
      const previewData = this.buildPreviewData();
      const fileTree = this.buildFileTree(previewData);
      if (fileTree) {
        this.setState({
          fileTree,
          selectedFile: { name: 'SKILL.md', type: 'file', fileType: 'skill-md' },
        });
      }
    }
  }

  componentWillUnmount() {
    // Cleanup
  }

  handleExpandChange = expandedKeys => {
    this.setState({ expandedKeys });
  };

  loadSkillData = callback => {
    const skillName = getParams('name');
    const namespaceId = getParams('namespace') || '';

    this.setState({ loading: true });

    const params = new URLSearchParams();
    params.append('skillName', skillName);
    params.append('namespaceId', namespaceId);

    request({
      url: `v3/console/ai/skills?${params.toString()}`,
      success: data => {
        this.setState({ loading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          const skillData = data.data;
          const previewData = this.buildPreviewDataStatic(skillData);
          const fileTree = this.buildFileTree(previewData);
          // Find SKILL.md file in the file list
          const skillMdFile =
            fileTree && Array.isArray(fileTree)
              ? fileTree.find(file => file.name === 'SKILL.md' && file.fileType === 'skill-md')
              : null;
          // 加载资源列表用于编辑
          const resources = skillData.resource ? Object.values(skillData.resource) : [];
          this.setState(
            {
              skillData,
              fileTree,
              selectedFile: skillMdFile || (fileTree && fileTree.length > 0 ? fileTree[0] : null),
              resources,
            },
            () => {
              if (callback && typeof callback === 'function') {
                callback();
              }
            }
          );
        } else {
          const { locale = {} } = this.props;
          Message.error(
            data?.message || locale.getSkillInfoFailed || 'Failed to get Skill information'
          );
        }
      },
      error: () => {
        this.setState({ loading: false });
        const { locale = {} } = this.props;
        Message.error(locale.getSkillInfoFailed || 'Failed to get Skill information');
      },
    });
  };

  handleEdit = () => {
    const namespaceId = getParams('namespace') || 'public';
    const skillName = getParams('name');
    this.props.history.push(`/newSkill?namespace=${namespaceId}&name=${skillName}&mode=edit`);
  };

  handleOptimize = () => {
    this.setState({ optimizeDialogVisible: true });
  };

  handleOptimizeDialogClose = () => {
    this.setState({ optimizeDialogVisible: false });
  };

  handleDelete = () => {
    const { locale = {} } = this.props;
    const skillName = getParams('name');
    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (
        locale.deleteSkillConfirm || 'Are you sure you want to delete Skill "{0}"?'
      ).replace('{0}', skillName),
      onOk: () => {
        this.deleteSkill();
      },
    });
  };

  deleteSkill = () => {
    const { locale = {} } = this.props;
    const skillName = getParams('name');
    const namespaceId = getParams('namespace') || '';

    const params = new URLSearchParams();
    params.append('skillName', skillName);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      method: 'DELETE',
      url: `v3/console/ai/skills?${params.toString()}`,
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
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || '';
    this.props.history.push(`/skillManagement?namespace=${namespaceId}`);
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

  // Generate resource unique identifier
  // Format: "type::name" if type is not blank, otherwise "name"
  // The separator "::" is used because it's not in the allowed character set for type and name
  getResourceIdentifier = resource => {
    if (resource.type && resource.type.trim() !== '') {
      return `${resource.type}::${resource.name || ''}`;
    }
    return resource.name || '--';
  };

  // Build preview data from skill data
  buildPreviewData = () => {
    const { skillData } = this.state;
    if (!skillData) {
      return null;
    }

    return {
      name: skillData.name || '',
      description: skillData.description || '',
      instruction: skillData.instruction || '',
      resource: skillData.resource || {},
    };
  };

  // Build preview data from skill data (static version for use in render)
  buildPreviewDataStatic = skillData => {
    if (!skillData) {
      return null;
    }

    return {
      name: skillData.name || '',
      description: skillData.description || '',
      instruction: skillData.instruction || '',
      resource: skillData.resource || {},
    };
  };

  // Build file tree structure (only files, no project name folder)
  buildFileTree = previewData => {
    if (!previewData || !previewData.name) {
      return null;
    }

    // Build file list directly (no project name folder)
    const fileList = [
      {
        name: 'SKILL.md',
        type: 'file',
        fileType: 'skill-md',
      },
    ];

    // Group resources by type
    const resourcesByType = {};
    const resourcesWithoutType = [];

    if (previewData.resource && Object.keys(previewData.resource).length > 0) {
      Object.entries(previewData.resource).forEach(([key, resource]) => {
        if (resource.type && resource.type.trim() !== '') {
          const type = resource.type.trim();
          if (!resourcesByType[type]) {
            resourcesByType[type] = [];
          }
          resourcesByType[type].push({
            name: resource.name || key,
            type: 'file',
            fileType: 'resource',
            resourceKey: key,
            resource: resource,
          });
        } else {
          resourcesWithoutType.push({
            name: resource.name || key,
            type: 'file',
            fileType: 'resource',
            resourceKey: key,
            resource: resource,
          });
        }
      });
    }

    // Sort files inside each type folder by name (A-Z)
    Object.keys(resourcesByType).forEach(type => {
      resourcesByType[type].sort((a, b) =>
        (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' })
      );
    });

    // Build type folders and root files, merge and sort by name: SKILL.md first, then all else A-Z
    const typeFolders = Object.entries(resourcesByType)
      .sort((a, b) => a[0].localeCompare(b[0], undefined, { sensitivity: 'base' }))
      .map(([type, files]) => ({
        name: type,
        type: 'folder',
        children: files,
      }));
    const sortedRootFiles = resourcesWithoutType.sort((a, b) =>
      (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' })
    );
    const afterSkill = [...typeFolders, ...sortedRootFiles].sort((a, b) =>
      (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' })
    );
    fileList.push(...afterSkill);

    return fileList;
  };

  // Escape YAML value (handle special characters)
  escapeYamlValue = value => {
    if (!value) {
      return '';
    }
    // If value contains special characters, wrap in quotes
    if (value.includes(':') || value.includes('"') || value.includes("'") || value.includes('\n')) {
      return `"${value.replace(/"/g, '\\"')}"`;
    }
    return value;
  };

  // Build SKILL.md content
  buildSkillMarkdown = previewData => {
    if (!previewData) {
      return '';
    }

    let markdown = '---\n';
    markdown += `name: ${this.escapeYamlValue(previewData.name || '')}\n`;
    markdown += `description: ${this.escapeYamlValue(previewData.description || '')}\n`;
    markdown += '---\n\n';

    // Instructions section - directly show instruction content without "## Instructions" header
    if (previewData.instruction && previewData.instruction.trim() !== '') {
      markdown += `${previewData.instruction}\n`;
    }

    return markdown;
  };

  handleFileClick = (file, e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    // Clear selectedFile first to force MonacoEditor to unmount, then set new file
    // This prevents errors when switching between files with different languages
    this.setState({ selectedFile: null }, () => {
      // Use setTimeout to ensure the previous editor is fully unmounted
      setTimeout(() => {
        this.setState({ selectedFile: file });
      }, 0);
    });
  };

  // 开始编辑文件名
  handleStartEditFileName = (node, e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    // SKILL.md 不能被编辑
    if (node.fileType === 'skill-md' || node.name === 'SKILL.md') {
      return;
    }
    // 只有资源文件可以编辑
    if (node.fileType === 'resource') {
      this.setState({
        editingFileName: {
          nodeKey: node.resourceKey || node.name,
          oldName: node.name,
          type: node.resource?.type || '',
        },
        editingFileNameValue: node.name, // 初始化编辑值
      });
    }
  };

  // 更新正在编辑的文件名临时值
  handleEditingFileNameChange = value => {
    // 过滤文件名：只允许英文大小写、数字、点号、下划线、横杠
    const filteredValue = value.replace(/[^a-zA-Z0-9._-]/g, '');
    this.setState({ editingFileNameValue: filteredValue });
  };

  // 保存文件名修改（详情页需要调用更新 API）
  handleSaveFileName = async newName => {
    const { editingFileName, resources, skillData } = this.state;
    if (!editingFileName) {
      this.setState({ editingFileName: null, editingFileNameValue: '' });
      return;
    }

    // 使用传入的 newName 或当前编辑值
    const nameToSave = newName || this.state.editingFileNameValue || editingFileName.oldName;
    if (!nameToSave || nameToSave.trim() === '') {
      this.setState({ editingFileName: null, editingFileNameValue: '' });
      return;
    }

    // 过滤文件名：只允许英文大小写、数字、点号、下划线、横杠
    const filteredName = nameToSave.replace(/[^a-zA-Z0-9._-]/g, '');

    if (filteredName === editingFileName.oldName) {
      // 没有变化，取消编辑
      this.setState({ editingFileName: null, editingFileNameValue: '' });
      return;
    }

    // 检查是否重名
    const isDuplicate = resources.some(
      r =>
        r.name === filteredName &&
        r.type === editingFileName.type &&
        r.name !== editingFileName.oldName
    );

    if (isDuplicate) {
      const { locale = {} } = this.props;
      Message.warning(locale.fileNameDuplicate || 'File name already exists');
      return;
    }

    // 更新资源名称
    const resourceIndex = resources.findIndex(
      r => r.name === editingFileName.oldName && r.type === editingFileName.type
    );

    if (resourceIndex !== -1) {
      const newResources = [...resources];
      newResources[resourceIndex] = {
        ...newResources[resourceIndex],
        name: filteredName,
      };

      // 更新 skillData 中的 resource
      const newResourceMap = {};
      newResources.forEach(r => {
        if (r.name && r.name.trim() !== '') {
          const key = r.name.trim();
          newResourceMap[key] = {
            name: r.name.trim(),
            type: r.type || '',
            content: r.content || '',
            metadata: r.metadata || null,
          };
        }
      });

      const updatedSkillData = {
        ...skillData,
        resource: newResourceMap,
      };

      // 调用更新 API
      const namespaceId = getParams('namespace') || '';
      const skillName = skillData.name;
      const params = new URLSearchParams();
      params.append('skillName', skillName);
      if (namespaceId) {
        params.append('namespaceId', namespaceId);
      }

      request({
        method: 'PUT',
        url: `v3/console/ai/skills?${params.toString()}`,
        data: updatedSkillData,
        success: data => {
          if (data && (data.code === 0 || data.code === 200)) {
            const { locale = {} } = this.props;
            Message.success(locale.updateSuccess || 'Update successful');
            // 重新加载数据（会自动更新文件树和选中文件）
            this.loadSkillData();
          } else {
            const { locale = {} } = this.props;
            Message.error(data?.message || locale.updateFailed || 'Update failed');
            this.setState({ editingFileName: null, editingFileNameValue: '' });
          }
        },
        error: () => {
          const { locale = {} } = this.props;
          Message.error(locale.updateFailed || 'Update failed');
          this.setState({ editingFileName: null, editingFileNameValue: '' });
        },
      });
    } else {
      this.setState({ editingFileName: null, editingFileNameValue: '' });
    }
  };

  // 取消编辑文件名
  handleCancelEditFileName = () => {
    this.setState({ editingFileName: null, editingFileNameValue: '' });
  };

  // 拖拽开始
  handleDragStart = (node, e) => {
    if (node.fileType === 'resource' && node.name !== 'SKILL.md') {
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData(
        'text/plain',
        JSON.stringify({
          resourceKey: node.resourceKey || node.name,
          name: node.name,
          type: node.resource?.type || '',
        })
      );
      this.setState({
        draggingFile: {
          resourceKey: node.resourceKey || node.name,
          name: node.name,
          type: node.resource?.type || '',
        },
      });
    } else {
      e.preventDefault();
    }
  };

  // 拖拽结束
  handleDragEnd = () => {
    this.setState({
      draggingFile: null,
      dragOverFolder: null,
    });
  };

  // 拖拽悬停在文件夹上
  handleDragOver = (folderName, e) => {
    e.preventDefault();
    e.stopPropagation();
    e.dataTransfer.dropEffect = 'move';
    if (this.state.dragOverFolder !== folderName) {
      this.setState({ dragOverFolder: folderName });
    }
  };

  // 拖拽离开文件夹
  handleDragLeave = e => {
    e.preventDefault();
    e.stopPropagation();
    // 只有当真正离开文件夹区域时才清除状态
    const relatedTarget = e.relatedTarget;
    if (!relatedTarget || !e.currentTarget.contains(relatedTarget)) {
      this.setState({ dragOverFolder: null });
    }
  };

  // 文件拖放到文件夹
  handleDrop = async (folderName, e) => {
    e.preventDefault();
    e.stopPropagation();

    const { draggingFile, resources, skillData } = this.state;
    if (!draggingFile) {
      this.setState({ dragOverFolder: null });
      return;
    }

    // 找到要移动的资源
    const resourceIndex = resources.findIndex(
      r => r.name === draggingFile.name && r.type === draggingFile.type
    );

    if (resourceIndex !== -1) {
      const newResources = [...resources];
      // 更新资源的 type 为文件夹名称
      newResources[resourceIndex] = {
        ...newResources[resourceIndex],
        type: folderName,
      };

      // 更新 skillData 中的 resource
      const newResourceMap = {};
      newResources.forEach(r => {
        if (r.name && r.name.trim() !== '') {
          const key = r.name.trim();
          newResourceMap[key] = {
            name: r.name.trim(),
            type: r.type || '',
            content: r.content || '',
            metadata: r.metadata || null,
          };
        }
      });

      const updatedSkillData = {
        ...skillData,
        resource: newResourceMap,
      };

      // 调用更新 API
      const namespaceId = getParams('namespace') || '';
      const skillName = skillData.name;
      const params = new URLSearchParams();
      params.append('skillName', skillName);
      if (namespaceId) {
        params.append('namespaceId', namespaceId);
      }

      request({
        method: 'PUT',
        url: `v3/console/ai/skills?${params.toString()}`,
        data: updatedSkillData,
        success: data => {
          if (data && (data.code === 0 || data.code === 200)) {
            const { locale = {} } = this.props;
            Message.success(locale.updateSuccess || 'Update successful');
            // 重新加载数据
            this.loadSkillData();
          } else {
            const { locale = {} } = this.props;
            Message.error(data?.message || locale.updateFailed || 'Update failed');
          }
        },
        error: () => {
          const { locale = {} } = this.props;
          Message.error(locale.updateFailed || 'Update failed');
        },
      });

      this.setState({ draggingFile: null, dragOverFolder: null });
    } else {
      this.setState({ draggingFile: null, dragOverFolder: null });
    }
  };

  handleExport = async () => {
    const { locale = {} } = this.props;
    const { skillData } = this.state;

    if (!skillData) {
      Message.warning(locale.noSkillData || 'No skill data to export');
      return;
    }

    const previewData = this.buildPreviewData();
    if (!previewData) {
      Message.warning(locale.noSkillData || 'No skill data to export');
      return;
    }

    try {
      const skillName = skillData.name || 'skill';
      const zipFileName = `${skillName}.zip`;

      // Always create zip package
      const zip = new JSZip();
      const folder = zip.folder(skillName);

      // Add SKILL.md file
      const markdown = this.buildSkillMarkdown(previewData);
      folder.file('SKILL.md', markdown);

      // Add resource files
      if (previewData.resource && Object.keys(previewData.resource).length > 0) {
        Object.entries(previewData.resource).forEach(([key, resource]) => {
          const resourceName = resource.name || key;
          const resourceContent = resource.content || '';

          if (resource.type && resource.type.trim() !== '') {
            // Add to type folder
            const typeFolder = folder.folder(resource.type.trim());
            typeFolder.file(resourceName, resourceContent);
          } else {
            // Add directly to skill folder
            folder.file(resourceName, resourceContent);
          }
        });
      }

      // Generate zip file
      const zipBlob = await zip.generateAsync({ type: 'blob' });

      // Check if browser supports File System Access API
      if ('showSaveFilePicker' in window) {
        // Use File System Access API to let user choose save location
        try {
          const fileHandle = await window.showSaveFilePicker({
            suggestedName: zipFileName,
            types: [
              {
                description: 'ZIP files',
                accept: {
                  'application/zip': ['.zip'],
                },
              },
            ],
          });

          const writable = await fileHandle.createWritable();
          await writable.write(zipBlob);
          await writable.close();

          Message.success(locale.exportSuccess || 'Export successful');
        } catch (saveError) {
          // User cancelled the file picker
          if (saveError.name !== 'AbortError') {
            // eslint-disable-next-line no-console
            console.error('Save file error:', saveError);
            Message.error(
              locale.exportFailed || `Export failed: ${saveError.message || saveError}`
            );
          }
          // If user cancelled, just return silently
          return;
        }
      } else {
        // Fallback to traditional download for browsers that don't support File System Access API
        const url = URL.createObjectURL(zipBlob);

        // Create a temporary link element and trigger download
        const link = document.createElement('a');
        link.href = url;
        link.download = zipFileName;
        document.body.appendChild(link);
        link.click();

        // Clean up
        document.body.removeChild(link);
        URL.revokeObjectURL(url);

        Message.success(locale.exportSuccess || 'Export successful');
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Export failed:', error);
      Message.error(locale.exportFailed || `Export failed: ${error.message || error}`);
    }
  };

  renderFileTree = (fileList, level = 0, parentKey = '') => {
    if (!fileList) {
      return null;
    }

    // If fileList is an array, render each item
    if (Array.isArray(fileList)) {
      return fileList.map(node => this.renderFileTree(node, level, parentKey));
    }

    // If it's a single node
    const node = fileList;
    const nodeKey = node.resourceKey
      ? `${parentKey}/${node.resourceKey}`
      : parentKey
      ? `${parentKey}/${node.name}`
      : node.name;
    const isSelected =
      this.state.selectedFile &&
      this.state.selectedFile.name === node.name &&
      this.state.selectedFile.fileType === node.fileType &&
      this.state.selectedFile.resourceKey === node.resourceKey;

    if (node.type === 'folder') {
      const { dragOverFolder, draggingFile } = this.state;
      const isDragOver = dragOverFolder === node.name && draggingFile;
      const folderStyle = {
        paddingLeft: level === 0 ? '8px' : `${level * 20 + 8}px`,
        paddingTop: '8px',
        paddingBottom: '6px',
        paddingRight: '12px',
        marginTop: level === 0 ? '8px' : '12px',
        marginBottom: '4px',
        display: 'flex',
        alignItems: 'center',
        fontWeight: 600,
        color: '#666',
        fontSize: '13px',
        backgroundColor: isDragOver ? '#e6f7ff' : 'transparent',
        borderRadius: 4,
        transition: 'background-color 0.2s',
      };

      return (
        <div key={nodeKey} className="file-tree-folder">
          <div
            className="file-tree-item file-tree-folder-item"
            style={folderStyle}
            onDragOver={e => this.handleDragOver(node.name, e)}
            onDragLeave={this.handleDragLeave}
            onDrop={e => this.handleDrop(node.name, e)}
          >
            <Icon type="folder" style={{ marginRight: 8, color: '#666' }} />
            <span style={{ fontWeight: 600 }}>{node.name}</span>
          </div>
          {node.children &&
            node.children.map(child => this.renderFileTree(child, level + 1, nodeKey))}
        </div>
      );
    } else {
      const itemStyle = {
        paddingLeft: level === 0 ? '8px' : `${level * 20 + 8}px`,
        paddingTop: '10px',
        paddingBottom: '10px',
        paddingRight: '12px',
        cursor: 'pointer',
        backgroundColor: isSelected ? '#e6f7ff' : 'transparent',
        color: isSelected ? '#1890ff' : '#333',
        fontWeight: isSelected ? 500 : 'normal',
        borderRadius: 4,
        margin: '2px 4px',
        display: 'flex',
        alignItems: 'center',
      };

      const { editingFileName, draggingFile } = this.state;
      const isEditing =
        editingFileName &&
        editingFileName.nodeKey === (node.resourceKey || node.name) &&
        editingFileName.oldName === node.name;
      const canEdit = node.fileType === 'resource' && node.name !== 'SKILL.md';
      const isDragging =
        draggingFile &&
        draggingFile.resourceKey === (node.resourceKey || node.name) &&
        draggingFile.name === node.name;

      return (
        <div
          key={nodeKey}
          className={`file-tree-item file-tree-file-item ${isSelected ? 'selected' : ''}`}
          style={{
            ...itemStyle,
            opacity: isDragging ? 0.5 : 1,
            cursor: canEdit ? 'move' : 'pointer',
          }}
          draggable={canEdit && !isEditing}
          onDragStart={canEdit && !isEditing ? e => this.handleDragStart(node, e) : undefined}
          onDragEnd={canEdit ? this.handleDragEnd : undefined}
          onClick={e => {
            e.preventDefault();
            e.stopPropagation();
            if (!isEditing) {
              this.handleFileClick(node, e);
            }
          }}
          onDoubleClick={e => {
            e.preventDefault();
            e.stopPropagation();
            if (canEdit) {
              this.handleStartEditFileName(node, e);
            }
          }}
        >
          <Icon type="file" style={{ marginRight: 8 }} />
          {isEditing ? (
            <Input
              size="small"
              value={this.state.editingFileNameValue}
              autoFocus
              style={{ flex: 1, marginRight: 4 }}
              onChange={value => {
                this.handleEditingFileNameChange(value);
              }}
              onBlur={e => {
                this.handleSaveFileName(e.target.value);
              }}
              onPressEnter={e => {
                this.handleSaveFileName(e.target.value);
              }}
              onKeyDown={e => {
                if (e.key === 'Escape') {
                  this.handleCancelEditFileName();
                }
              }}
              onClick={e => {
                e.stopPropagation();
              }}
            />
          ) : (
            <span
              style={{ pointerEvents: 'none', flex: 1 }}
              title={
                canEdit ? this.props.locale?.doubleClickToRename || 'Double click to rename' : ''
              }
            >
              {node.name}
            </span>
          )}
        </div>
      );
    }
  };

  renderFileContent = () => {
    const { selectedFile, skillData } = this.state;

    if (!selectedFile || !skillData) {
      return (
        <div
          className="file-content-empty"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
            color: '#999',
            fontSize: '14px',
          }}
        >
          {this.props.locale?.selectFileToPreview || 'Select a file to preview'}
        </div>
      );
    }

    if (selectedFile.fileType === 'skill-md') {
      const previewData = this.buildPreviewDataStatic(skillData);
      const markdown = this.buildSkillMarkdown(previewData);
      return (
        <div
          className="file-content"
          style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}
        >
          <div
            className="file-content-header"
            style={{
              padding: '12px 16px',
              borderBottom: '1px solid #e6e6e6',
              display: 'flex',
              alignItems: 'center',
              fontWeight: 500,
              background: '#fafafa',
            }}
          >
            <Icon type="file" style={{ marginRight: 8 }} />
            <span>{selectedFile.name}</span>
          </div>
          <div style={{ flex: 1, overflow: 'hidden', padding: '16px' }}>
            <div style={{ border: '1px solid #d9d9d9', borderRadius: '4px', height: '100%' }}>
              <MonacoEditor
                language="markdown"
                width="100%"
                height="100%"
                value={markdown}
                options={{
                  readOnly: true,
                  wordWrap: 'on',
                  minimap: { enabled: false },
                  lineNumbers: 'on',
                  scrollBeyondLastLine: false,
                }}
              />
            </div>
          </div>
        </div>
      );
    } else if (selectedFile.fileType === 'resource') {
      const resource = selectedFile.resource;
      return (
        <div
          className="file-content"
          style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}
        >
          <div
            className="file-content-header"
            style={{
              padding: '12px 16px',
              borderBottom: '1px solid #e6e6e6',
              display: 'flex',
              alignItems: 'center',
              fontWeight: 500,
              background: '#fafafa',
            }}
          >
            <Icon type="file" style={{ marginRight: 8 }} />
            <span>{selectedFile.name}</span>
          </div>
          <div
            className="file-content-resource"
            style={{
              flex: 1,
              padding: '16px',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            {resource.content ? (
              <div
                style={{
                  flex: 1,
                  border: '1px solid #e6e6e6',
                  borderRadius: '4px',
                  overflow: 'hidden',
                }}
              >
                <MonacoEditor
                  key={`${selectedFile.resourceKey || selectedFile.name}-${getLanguageFromFileName(
                    resource.name || ''
                  )}`}
                  language={getLanguageFromFileName(resource.name || '')}
                  width="100%"
                  height="100%"
                  value={resource.content}
                  options={{
                    readOnly: true,
                    wordWrap: 'on',
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    scrollBeyondLastLine: false,
                  }}
                />
              </div>
            ) : (
              <div
                style={{
                  padding: '12px',
                  color: '#999',
                  flex: 1,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                {this.props.locale?.noContent || 'No content'}
              </div>
            )}
          </div>
        </div>
      );
    }

    return null;
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, skillData } = this.state;

    if (loading) {
      return (
        <div style={{ padding: 20 }}>
          <Loading visible={loading} />
        </div>
      );
    }

    if (!skillData) {
      return (
        <div style={{ padding: 20 }}>
          <Message type="warning">{locale.skillNotFound || 'Skill not found'}</Message>
        </div>
      );
    }

    const previewData = this.buildPreviewDataStatic(skillData);
    const fileTree = this.state.fileTree || this.buildFileTree(previewData);

    return (
      <div className="skill-detail">
        <div
          className="page-title"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: 8,
            marginBottom: 8,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 28, height: 40, fontWeight: 500 }}>
              {locale.skillDetail || 'Skill Detail'}
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Button type="primary" onClick={this.handleEdit}>
                <Icon type="edit" /> {locale.edit || 'Edit'}
              </Button>
              <Button onClick={this.handleOptimize}>
                <MagicWandIcon size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} />{' '}
                {locale.aiOptimize || 'AI 优化'}
              </Button>
              <Button onClick={this.handleExport}>
                <Icon type="download" /> {locale.export || 'Export'}
              </Button>
            </div>
          </div>
          <div>
            <Button warning onClick={this.handleDelete}>
              <Icon type="delete" /> {locale.delete || 'Delete'}
            </Button>
          </div>
        </div>

        <div
          className="skill-detail-container"
          style={{
            background: '#fff',
            borderRadius: '4px',
            border: '1px solid #e6e6e6',
            height: 'calc(100vh - 200px)',
            display: 'flex',
            overflow: 'hidden',
          }}
        >
          <div
            className="skill-detail-sidebar"
            style={{
              width: '300px',
              borderRight: '1px solid #e6e6e6',
              display: 'flex',
              flexDirection: 'column',
              background: '#fafafa',
            }}
          >
            <div
              className="skill-detail-sidebar-header"
              style={{
                padding: '12px 16px',
                borderBottom: '1px solid #e6e6e6',
                fontWeight: 500,
                background: '#fff',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <span>{skillData.name || locale.projectFiles || '项目文件'}</span>
            </div>
            <div
              className="skill-detail-file-tree"
              style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}
              onDragOver={e => {
                e.preventDefault();
                e.stopPropagation();
                if (this.state.draggingFile) {
                  e.dataTransfer.dropEffect = 'move';
                  if (this.state.dragOverFolder !== '') {
                    this.setState({ dragOverFolder: '' });
                  }
                }
              }}
              onDrop={async e => {
                e.preventDefault();
                e.stopPropagation();
                const { draggingFile, resources, skillData } = this.state;
                if (!draggingFile) return;

                const resourceIndex = resources.findIndex(
                  r => r.name === draggingFile.name && r.type === draggingFile.type
                );

                if (resourceIndex !== -1) {
                  const newResources = [...resources];
                  newResources[resourceIndex] = {
                    ...newResources[resourceIndex],
                    type: '', // 拖到根目录，清空 type
                  };

                  const newResourceMap = {};
                  newResources.forEach(r => {
                    if (r.name && r.name.trim() !== '') {
                      const key = r.name.trim();
                      newResourceMap[key] = {
                        name: r.name.trim(),
                        type: r.type || '',
                        content: r.content || '',
                        metadata: r.metadata || null,
                      };
                    }
                  });

                  const updatedSkillData = {
                    ...skillData,
                    resource: newResourceMap,
                  };

                  const namespaceId = getParams('namespace') || '';
                  const skillName = skillData.name;
                  const params = new URLSearchParams();
                  params.append('skillName', skillName);
                  if (namespaceId) {
                    params.append('namespaceId', namespaceId);
                  }

                  request({
                    method: 'PUT',
                    url: `v3/console/ai/skills?${params.toString()}`,
                    data: updatedSkillData,
                    success: data => {
                      if (data && (data.code === 0 || data.code === 200)) {
                        const { locale = {} } = this.props;
                        Message.success(locale.updateSuccess || 'Update successful');
                        this.loadSkillData();
                      } else {
                        const { locale = {} } = this.props;
                        Message.error(data?.message || locale.updateFailed || 'Update failed');
                      }
                    },
                    error: () => {
                      const { locale = {} } = this.props;
                      Message.error(locale.updateFailed || 'Update failed');
                    },
                  });

                  this.setState({ draggingFile: null, dragOverFolder: null });
                }
              }}
            >
              {fileTree ? (
                this.renderFileTree(fileTree)
              ) : (
                <div
                  className="file-tree-empty"
                  style={{
                    padding: '40px 20px',
                    textAlign: 'center',
                    color: '#999',
                    fontSize: '13px',
                  }}
                >
                  {locale.noPreviewData || 'No preview data available'}
                </div>
              )}
            </div>
          </div>
          <div
            className="skill-detail-content-area"
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
              background: '#fff',
            }}
          >
            {this.renderFileContent()}
          </div>
        </div>

        <SkillOptimizeDialog
          visible={this.state.optimizeDialogVisible}
          skill={this.state.skillData}
          selectedFile={this.state.selectedFile}
          fileTree={this.state.fileTree}
          onClose={this.handleOptimizeDialogClose}
          locale={this.props.locale}
          history={this.props.history}
        />
      </div>
    );
  }
}

export default SkillDetail;
