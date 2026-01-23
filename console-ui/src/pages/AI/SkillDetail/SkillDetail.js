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
      showPreviewDialog: false,
      previewData: null,
      fileTree: null,
      selectedFile: null,
    };
  }

  componentDidMount() {
    this.loadSkillData();
  }

  handleExpandChange = (expandedKeys) => {
    this.setState({ expandedKeys });
  };

  loadSkillData = () => {
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
          this.setState({ skillData: data.data });
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
      content: (locale.deleteSkillConfirm || 'Are you sure you want to delete Skill "{0}"?').replace(
        '{0}',
        skillName
      ),
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
  getResourceIdentifier = (resource) => {
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

  // Build file tree structure
  buildFileTree = (previewData) => {
    if (!previewData || !previewData.name) {
      return null;
    }

    const tree = {
      name: previewData.name,
      type: 'folder',
      children: [
        {
          name: 'SKILL.md',
          type: 'file',
          fileType: 'skill-md',
        },
      ],
    };

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

    // Add type folders
    Object.entries(resourcesByType).forEach(([type, files]) => {
      tree.children.push({
        name: type,
        type: 'folder',
        children: files,
      });
    });

    // Add resources without type (directly in skill folder)
    tree.children.push(...resourcesWithoutType);

    return tree;
  };

  // Escape YAML value (handle special characters)
  escapeYamlValue = (value) => {
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
  buildSkillMarkdown = (previewData) => {
    if (!previewData) {
      return '';
    }

    let markdown = '---\n';
    markdown += `name: ${this.escapeYamlValue(previewData.name || '')}\n`;
    markdown += `description: ${this.escapeYamlValue(previewData.description || '')}\n`;
    markdown += '---\n\n';

    // Instructions section
    if (previewData.instruction && previewData.instruction.trim() !== '') {
      markdown += `## Instructions\n\n${previewData.instruction}\n\n`;
    }

    // Resources section
    if (previewData.resource && Object.keys(previewData.resource).length > 0) {
      markdown += `## Resources\n\n`;
      Object.entries(previewData.resource).forEach(([key, resource]) => {
        const resourceName = resource.name || key;
        markdown += `### ${resourceName}\n\n`;
        const resourceId = resource.type && resource.type.trim() !== ''
          ? `${resource.type}::${resourceName}`
          : resourceName;
        markdown += `- **Resource ID**: ${resourceId}\n`;
        if (resource.type && resource.type.trim() !== '') {
          markdown += `- **Type**: ${resource.type}\n`;
          markdown += `- **File**: \`${resource.type}/${resourceName}\`\n\n`;
        } else {
          markdown += `- **Type**: \n`;
          markdown += `- **File**: \`${resourceName}\`\n\n`;
        }
      });
    }

    return markdown;
  };

  handleShowPreview = () => {
    const previewData = this.buildPreviewData();
    const fileTree = this.buildFileTree(previewData);
    this.setState({
      showPreviewDialog: true,
      previewData,
      fileTree,
      selectedFile: fileTree ? { name: 'SKILL.md', type: 'file', fileType: 'skill-md' } : null,
    });
  };

  handleClosePreview = () => {
    this.setState({
      showPreviewDialog: false,
      previewData: null,
      fileTree: null,
      selectedFile: null,
    });
  };

  handleFileClick = (file, e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
      // Also stop immediate propagation to prevent any other handlers
      if (e.nativeEvent && e.nativeEvent.stopImmediatePropagation) {
        e.nativeEvent.stopImmediatePropagation();
      }
    }
    // Use setTimeout to ensure state update happens after event handling
    setTimeout(() => {
      this.setState({ selectedFile: file });
    }, 0);
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
      
      // Check if browser supports File System Access API
      if ('showDirectoryPicker' in window) {
        // Use File System Access API to let user choose folder
        try {
          const directoryHandle = await window.showDirectoryPicker();
          await this.saveToDirectory(directoryHandle, skillName, previewData);
          Message.success(locale.exportSuccess || 'Export successful');
        } catch (dirError) {
          // User cancelled the directory picker
          if (dirError.name !== 'AbortError') {
            // eslint-disable-next-line no-console
            console.error('Directory picker error:', dirError);
            Message.error(locale.exportFailed || `Export failed: ${dirError.message || dirError}`);
          }
          // If user cancelled, just return silently
          return;
        }
      } else {
        // Fallback to zip download for browsers that don't support File System Access API
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
        const url = URL.createObjectURL(zipBlob);

        // Create a temporary link element and trigger download
        const link = document.createElement('a');
        link.href = url;
        link.download = `${skillName}.zip`;
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

  saveToDirectory = async (directoryHandle, skillName, previewData) => {
    // Create skill folder in the selected directory
    const skillFolderHandle = await directoryHandle.getDirectoryHandle(skillName, { create: true });

    // Save SKILL.md file
    const markdown = this.buildSkillMarkdown(previewData);
    const skillMdFileHandle = await skillFolderHandle.getFileHandle('SKILL.md', { create: true });
    const skillMdWritable = await skillMdFileHandle.createWritable();
    await skillMdWritable.write(markdown);
    await skillMdWritable.close();

    // Save resource files
    if (previewData.resource && Object.keys(previewData.resource).length > 0) {
      for (const [key, resource] of Object.entries(previewData.resource)) {
        const resourceName = resource.name || key;
        const resourceContent = resource.content || '';
        
        let targetFolderHandle = skillFolderHandle;
        
        if (resource.type && resource.type.trim() !== '') {
          // Create type folder if needed
          targetFolderHandle = await skillFolderHandle.getDirectoryHandle(resource.type.trim(), { create: true });
        }
        
        // Save resource file
        const resourceFileHandle = await targetFolderHandle.getFileHandle(resourceName, { create: true });
        const resourceWritable = await resourceFileHandle.createWritable();
        await resourceWritable.write(resourceContent);
        await resourceWritable.close();
      }
    }
  };

  renderFileTree = (node, level = 0, parentKey = '') => {
    if (!node) {
      return null;
    }

    const nodeKey = parentKey ? `${parentKey}/${node.name}` : node.name;
    const isSelected = this.state.selectedFile &&
      this.state.selectedFile.name === node.name &&
      this.state.selectedFile.fileType === node.fileType &&
      this.state.selectedFile.resourceKey === node.resourceKey;

    if (node.type === 'folder') {
      return (
        <div key={nodeKey} className="file-tree-folder">
          <div
            className="file-tree-item file-tree-folder-item"
            style={{ paddingLeft: `${level * 20 + 8}px` }}
          >
            <Icon type="folder" style={{ marginRight: 8 }} />
            <span>{node.name}</span>
          </div>
          {node.children && node.children.map((child) => this.renderFileTree(child, level + 1, nodeKey))}
        </div>
      );
    } else {
      return (
        <div
          key={nodeKey}
          className={`file-tree-item file-tree-file-item ${isSelected ? 'selected' : ''}`}
          style={{ paddingLeft: `${level * 20 + 8}px`, cursor: 'pointer' }}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            this.handleFileClick(node, e);
          }}
          onMouseDown={(e) => {
            // Also prevent on mousedown to be extra safe
            e.preventDefault();
            e.stopPropagation();
          }}
        >
          <Icon type="file" style={{ marginRight: 8 }} />
          <span 
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
            }}
            style={{ pointerEvents: 'none' }}
          >
            {node.name}
          </span>
        </div>
      );
    }
  };

  renderFileContent = () => {
    const { selectedFile, previewData } = this.state;

    if (!selectedFile || !previewData) {
      return (
        <div className="file-content-empty">
          {this.props.locale?.selectFileToPreview || 'Select a file to preview'}
        </div>
      );
    }

    if (selectedFile.fileType === 'skill-md') {
      const markdown = this.buildSkillMarkdown(previewData);
      return (
        <div className="file-content">
          <div className="file-content-header">
            <Icon type="file" style={{ marginRight: 8 }} />
            <span>{selectedFile.name}</span>
          </div>
          <pre className="file-content-markdown">{markdown}</pre>
        </div>
      );
    } else if (selectedFile.fileType === 'resource') {
      const resource = selectedFile.resource;
      return (
        <div className="file-content">
          <div className="file-content-header">
            <Icon type="file" style={{ marginRight: 8 }} />
            <span>{selectedFile.name}</span>
          </div>
          <div className="file-content-resource">
            <div className="resource-info">
              <div className="resource-info-item">
                <strong>{this.props.locale?.resourceName || 'Resource Name'}:</strong> {resource.name || '--'}
              </div>
              {resource.type && (
                <div className="resource-info-item">
                  <strong>{this.props.locale?.resourceType || 'Resource Type'}:</strong> {resource.type}
                </div>
              )}
            </div>
            <div className="resource-content">
              <div className="resource-content-label">
                <strong>{this.props.locale?.resourceContent || 'Resource Content'}:</strong>
              </div>
              {resource.content ? (
                <div style={{ border: '1px solid #e6e6e6', borderRadius: '4px', marginTop: '8px' }}>
                  <MonacoEditor
                    language={getLanguageFromFileName(resource.name || '')}
                    width="100%"
                    height={300}
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
                <div style={{ padding: '12px', color: '#999', marginTop: '8px' }}>
                  {this.props.locale?.noContent || 'No content'}
                </div>
              )}
            </div>
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

    const resources = skillData.resource ? Object.values(skillData.resource) : [];

    return (
      <div className="skill-detail">
        <div
          className="page-title"
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 8, marginBottom: 8 }}
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
                <MagicWandIcon size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} /> {locale.aiOptimize || 'AI 优化'}
              </Button>
              <Button onClick={this.handleShowPreview}>
                <Icon type="eye" /> {locale.preview || 'Preview'}
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

        <div style={{ background: '#fff', padding: '20px', borderRadius: '4px', border: '1px solid #e6e6e6' }}>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={12}>
              <div className="info-item">
                <label>{locale.skillName || 'Skill Name'}:</label>
                <span>{skillData.name || '--'}</span>
              </div>
            </Col>
          </Row>
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col span={24}>
              <div className="info-item">
                <label>{locale.description || 'Description'}:</label>
                <span>{skillData.description || '--'}</span>
              </div>
            </Col>
          </Row>

          <div style={{ marginBottom: 24 }}>
            <div style={{ fontSize: '14px', fontWeight: 500, marginBottom: '12px', color: '#333' }}>
              {locale.instruction || 'Instruction'}
            </div>
            <div className="instruction-content">
              <MarkdownRenderer content={skillData.instruction || ''} />
            </div>
          </div>

          <div>
            <div style={{ fontSize: '14px', fontWeight: 500, marginBottom: '12px', color: '#333' }}>
              {locale.resources || 'Resources'}
            </div>
            {resources.length > 0 ? (
              <div className="resources-section">
                <Collapse expandedKeys={this.state.expandedKeys} onExpand={this.handleExpandChange}>
                  {resources.map((resource, index) => (
                    <Panel
                      key={String(index)}
                      title={
                        <div className="resource-panel-header">
                          <span>
                            {resource.type && resource.name
                              ? `${resource.type}/${resource.name}`
                              : `${locale.resource || 'Resource'} ${index + 1}`}
                          </span>
                        </div>
                      }
                    >
                      <Row gutter={16}>
                        <Col span={12}>
                          <div className="info-item">
                            <label>{locale.resourceName || 'Resource Name'}:</label>
                            <span>{resource.name || '--'}</span>
                          </div>
                        </Col>
                        <Col span={12}>
                          <div className="info-item">
                            <label>{locale.resourceType || 'Resource Type'}:</label>
                            <span>
                              {resource.type ? (
                                <Tag type="primary" size="small">
                                  {resource.type}
                                </Tag>
                              ) : (
                                '--'
                              )}
                            </span>
                          </div>
                        </Col>
                      </Row>
                      <Row gutter={16}>
                        <Col span={24}>
                          <div className="info-item">
                            <label>{locale.resourceContent || 'Resource Content'}:</label>
                            <div className="resource-content">
                              {resource.content ? (
                                <MonacoEditor
                                  language={getLanguageFromFileName(resource.name || '')}
                                  width="100%"
                                  height={300}
                                  value={resource.content}
                                  options={{
                                    readOnly: true,
                                    wordWrap: 'on',
                                    minimap: { enabled: false },
                                    lineNumbers: 'on',
                                    scrollBeyondLastLine: false,
                                  }}
                                />
                              ) : (
                                <div style={{ padding: '12px', color: '#999' }}>--</div>
                              )}
                            </div>
                          </div>
                        </Col>
                      </Row>
                      {resource.metadata && (
                        <Row gutter={16}>
                          <Col span={24}>
                            <div className="info-item">
                              <label>{locale.metadata || 'Metadata'}:</label>
                              <div className="resource-metadata">
                                <pre>{JSON.stringify(resource.metadata, null, 2)}</pre>
                              </div>
                            </div>
                          </Col>
                        </Row>
                      )}
                    </Panel>
                  ))}
                </Collapse>
              </div>
            ) : (
              <div className="empty-resources">
                {locale.noResources || 'No resources'}
              </div>
            )}
          </div>
        </div>

        <SkillOptimizeDialog
          visible={this.state.optimizeDialogVisible}
          skill={this.state.skillData}
          onClose={this.handleOptimizeDialogClose}
          locale={this.props.locale}
          history={this.props.history}
        />

        <Dialog
          visible={this.state.showPreviewDialog}
          title={locale.previewSkill || 'Preview Skill'}
          onClose={this.handleClosePreview}
          onCancel={this.handleClosePreview}
          onOk={this.handleClosePreview}
          okProps={{
            children: locale.close || 'Close',
          }}
          cancelProps={{
            children: locale.cancel || 'Cancel',
          }}
          style={{ width: 1200 }}
          className="skill-preview-dialog"
        >
          <div className="preview-container">
            <div className="preview-sidebar">
              <div className="preview-sidebar-header">
                {locale.fileStructure || 'File Structure'}
              </div>
              <div 
                className="preview-file-tree"
                onClick={(e) => {
                  // Prevent clicks on the file tree container from bubbling
                  e.stopPropagation();
                }}
              >
                {this.state.fileTree ? this.renderFileTree(this.state.fileTree) : (
                  <div className="file-tree-empty">
                    {locale.noPreviewData || 'No preview data available'}
                  </div>
                )}
              </div>
            </div>
            <div className="preview-content-area">
              {this.renderFileContent()}
            </div>
          </div>
        </Dialog>
      </div>
    );
  }
}

export default SkillDetail;
