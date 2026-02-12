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
  Field,
  Form,
  Input,
  Message,
  Grid,
  Icon,
  Dialog,
  Select,
  Checkbox,
  Loading,
  Balloon,
  Tag,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import SkillOptimizeDialog from '../SkillManagement/SkillOptimizeDialog';
import MonacoEditor from '../../../components/MonacoEditor/MonacoEditor';
import MarkdownRenderer from '../../../components/MarkdownRenderer/MarkdownRenderer';
import MagicWandIcon from '../../../components/MagicWandIcon/MagicWandIcon';
import { getLanguageFromFileName } from '../../../utils/languageDetector';
import { getParams, request } from '@/globalLib';
import './NewSkill.scss';

const { Row, Col } = Grid;
const { Panel } = Collapse;

@ConfigProvider.config
class NewSkill extends React.Component {
  static displayName = 'SkillManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);

    const skillName = getParams('name');
    const mode = getParams('mode');
    const isEdit = mode === 'edit' && !!skillName;

    this.state = {
      loading: false,
      generating: false,
      streaming: false,
      streamContent: '',
      thinkingContent: '', // 思考内容
      thinkingCollapsed: false, // 思考内容是否折叠
      showInputCollapsed: false, // 用户输入是否折叠
      inputSectionCollapsed: false, // 输入区域是否折叠（生成开始后折叠）
      resultContentCollapsed: false, // 结果内容是否折叠
      inputTooltipVisible: false, // 用户输入tooltip显示状态
      inputTooltipPosition: { x: 0, y: 0 }, // 用户输入tooltip位置
      thinkingTooltipVisible: false, // 思考内容tooltip显示状态
      thinkingTooltipPosition: { x: 0, y: 0 }, // 思考内容tooltip位置
      parsingResult: false, // 是否正在解析结果
      isEdit: isEdit,
      skillName,
      backgroundInfo: '',
      conversationHistory: null, // 对话历史
      conversationHistoryJson: '', // 对话历史 JSON 字符串（用于编辑）
      showConversationHistory: false, // 是否显示对话历史输入
      showAiGenerateDialog: false, // 控制是否显示AI生成弹窗
      generatedSkill: null, // 存储生成的Skill数据
      mcpServers: [], // MCP服务器列表
      selectedMcpServer: null, // 选中的MCP服务器
      mcpTools: [], // MCP工具列表
      selectedMcpTools: [], // 选中的MCP工具
      loadingMcpServers: false, // 加载MCP服务器列表
      loadingMcpTools: false, // 加载MCP工具列表
      mcpToolSearchKeyword: '', // MCP工具搜索关键词
      optimizeDialogVisible: false, // 控制是否显示AI优化弹窗
      currentSkillData: null, // 存储当前Skill数据用于优化
      editModeFileTree: null, // 编辑模式下的文件树
      editModeSelectedFile: null, // 编辑模式下选中的文件
      editingFileName: null, // 正在编辑的文件名（格式：{nodeKey, oldName, type}）
      editingFileNameValue: '', // 正在编辑的文件名的临时值
      draggingFile: null, // 正在拖拽的文件（格式：{resourceKey, name, type}）
      dragOverFolder: null, // 当前拖拽悬停的文件夹名称
      prevFormValues: {}, // 上一次的表单值，用于检测变化
      originalFormData: null, // 原始表单数据（用于比较是否有修改）
      originalResources: [], // 原始资源数据（用于比较是否有修改）
      hasChanges: false, // 是否有数据变化
      // 创建模式下默认添加一个空的资源项
      resources: isEdit
        ? []
        : [
            {
              name: '',
              type: '',
              content: '',
              metadata: null,
            },
          ],
      // 资源面板展开状态，创建模式下默认展开第一个
      expandedKeys: isEdit ? [] : ['0'],
      // 正在编辑的资源索引（用于标题编辑）
      editingResourceIndex: null,
      // 鼠标位置和tooltip显示状态
      tooltipVisible: false,
      tooltipPosition: { x: 0, y: 0 },
      tooltipResourceIndex: null,
    };
    this.streamReader = null;
  }

  componentDidMount() {
    if (this.state.isEdit) {
      // Check if there's optimized skill data from optimization dialog
      const optimizedParam = getParams('optimized');
      if (optimizedParam === 'true') {
        this.loadOptimizedSkillData();
      } else {
        this.loadSkillData();
      }
    }
    // Add click listener to handle clicking outside edit area
    // Use capture phase to handle before other click handlers
    document.addEventListener('click', this.handleDocumentClick, true);
  }

  componentWillUnmount() {
    // Remove click listener
    document.removeEventListener('click', this.handleDocumentClick, true);
  }

  componentDidUpdate(prevProps, prevState) {
    // 在编辑模式下，当表单值或资源变化时更新文件树和变化状态
    if (this.state.isEdit) {
      const values = this.field.getValues();
      const prevValues = prevState.prevFormValues || {};

      // 检查表单值是否变化
      const formChanged =
        values.name !== prevValues.name ||
        values.description !== prevValues.description ||
        values.instruction !== prevValues.instruction;

      // 检查资源是否变化
      const resourcesChanged =
        JSON.stringify(this.state.resources) !== JSON.stringify(prevState.resources);

      if (formChanged || resourcesChanged) {
        this.updateEditModeFileTree();
        // 保存当前表单值用于下次比较
        this.setState({ prevFormValues: values });
      }

      // 检查是否有数据变化并更新状态
      const hasChanged = this.hasDataChanged();
      if (hasChanged !== this.state.hasChanges) {
        this.setState({ hasChanges: hasChanged });
      }
    }
  }

  handleDocumentClick = e => {
    // Skip if clicking on resource title text (which should enter edit mode)
    if (e.target.closest('.resource-title-text')) {
      return;
    }

    // If editing resource title, check if click is outside the editor
    if (this.state.editingResourceIndex !== null) {
      // Find the editor element for the current editing resource
      const editorElements = document.querySelectorAll('.resource-title-editor');
      const currentEditor = editorElements[this.state.editingResourceIndex];

      if (currentEditor && !currentEditor.contains(e.target)) {
        // Click is outside the editor, exit edit mode
        this.setState({ editingResourceIndex: null });
      }
    }
  };

  loadOptimizedSkillData = () => {
    try {
      const optimizedSkillStr = localStorage.getItem('nacos_optimized_skill');
      if (optimizedSkillStr) {
        const optimizedSkill = JSON.parse(optimizedSkillStr);

        // 先加载原始数据作为基准，用于比较变化
        const { skillName } = this.state;
        const namespaceId = optimizedSkill.namespaceId || getParams('namespace') || '';

        this.setState({ loading: true });

        const params = new URLSearchParams();
        params.append('skillName', skillName);
        params.append('namespaceId', namespaceId);

        request({
          url: `v3/console/ai/skills?${params.toString()}`,
          success: data => {
            this.setState({ loading: false });
            if (data && (data.code === 0 || data.code === 200) && data.data) {
              const originalSkillData = data.data;
              const originalResources = originalSkillData.resource
                ? Object.values(originalSkillData.resource)
                : [];

              // 保存原始数据用于比较
              const originalFormData = {
                name: originalSkillData.name,
                description: originalSkillData.description || '',
                instruction: originalSkillData.instruction || '',
              };

              // Fill form with optimized skill data
              // Important: Keep original skill name, don't use optimized name
              const originalName = this.state.skillName || '';
              this.field.setValues({
                name: originalName, // Always use original skill name
                description: optimizedSkill.description || '',
                instruction: optimizedSkill.instruction || '',
              });

              // Fill resources if any
              let resources = [];
              let resourceMap = {};
              if (optimizedSkill.resource && Object.keys(optimizedSkill.resource).length > 0) {
                resources = Object.values(optimizedSkill.resource).map(resource => ({
                  name: resource.name || '',
                  type: resource.type || '',
                  content: resource.content || '',
                  metadata: resource.metadata || null,
                }));
                // Build resource map for currentSkillData
                Object.entries(optimizedSkill.resource).forEach(([key, resource]) => {
                  resourceMap[key] = resource;
                });
              }

              // Build currentSkillData for AI optimization feature
              const currentSkillData = {
                name: originalName,
                namespaceId: namespaceId,
                description: optimizedSkill.description || '',
                instruction: optimizedSkill.instruction || '',
                resource: resourceMap,
              };

              // 获取被优化的文件名
              const targetFileName = optimizedSkill.targetFileName;

              // 比较优化后的数据与原始数据，判断是否有变化
              const formChanged =
                (optimizedSkill.description || '') !== (originalSkillData.description || '') ||
                (optimizedSkill.instruction || '') !== (originalSkillData.instruction || '');
              const resourcesChanged =
                JSON.stringify(resources) !== JSON.stringify(originalResources);
              const hasChanges = formChanged || resourcesChanged;

              this.setState(
                {
                  resources,
                  expandedKeys: resources.map((_, index) => String(index)),
                  currentSkillData, // Set currentSkillData so AI optimization can work
                  originalFormData, // 保存原始表单数据用于比较
                  originalResources: JSON.parse(JSON.stringify(originalResources)), // 深拷贝原始资源
                  hasChanges, // 设置变化状态
                },
                () => {
                  // 在 resources 更新后构建文件树
                  const previewData = this.buildPreviewData();
                  const fileTree = this.buildFileTree(previewData);

                  // 根据 targetFileName 查找并选中对应的文件
                  let selectedFile = null;
                  if (targetFileName && fileTree && Array.isArray(fileTree)) {
                    // 递归查找文件
                    const findFileInTree = tree => {
                      if (Array.isArray(tree)) {
                        for (const node of tree) {
                          if (node.type === 'file') {
                            // 检查是否匹配
                            let fileIdentifier = node.name;
                            if (node.fileType === 'resource' && node.resourceKey) {
                              fileIdentifier = node.resourceKey;
                            }
                            if (fileIdentifier === targetFileName) {
                              return node;
                            }
                          }
                          if (node.type === 'folder' && node.children) {
                            const found = findFileInTree(node.children);
                            if (found) {
                              return found;
                            }
                          }
                        }
                      } else if (tree.type === 'file') {
                        let fileIdentifier = tree.name;
                        if (tree.fileType === 'resource' && tree.resourceKey) {
                          fileIdentifier = tree.resourceKey;
                        }
                        if (fileIdentifier === targetFileName) {
                          return tree;
                        }
                      } else if (tree.type === 'folder' && tree.children) {
                        return findFileInTree(tree.children);
                      }
                      return null;
                    };

                    selectedFile = findFileInTree(fileTree);
                  }

                  // 如果没有找到目标文件，默认选中 SKILL.md 或第一个文件
                  if (!selectedFile && fileTree && Array.isArray(fileTree)) {
                    selectedFile =
                      fileTree.find(
                        file => file.name === 'SKILL.md' && file.fileType === 'skill-md'
                      ) || (fileTree.length > 0 ? fileTree[0] : null);
                  }

                  this.setState({
                    editModeFileTree: fileTree,
                    editModeSelectedFile: selectedFile,
                  });

                  // Clear the stored data
                  localStorage.removeItem('nacos_optimized_skill');

                  const { locale = {} } = this.props;
                  Message.success(
                    this.getLocaleValue(
                      'optimizedSkillLoaded',
                      'Optimized skill data loaded successfully'
                    )
                  );
                }
              );
            } else {
              // 如果加载原始数据失败，fallback 到正常加载
              this.loadSkillData();
            }
          },
          error: () => {
            this.setState({ loading: false });
            // 如果加载原始数据失败，fallback 到正常加载
            this.loadSkillData();
          },
        });
      } else {
        // Fallback to normal load
        this.loadSkillData();
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('Failed to load optimized skill data', e);
      // Fallback to normal load
      this.loadSkillData();
    }
  };

  loadSkillData = callback => {
    const { skillName } = this.state;
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
          const resources = skillData.resource ? Object.values(skillData.resource) : [];

          this.field.setValues({
            name: skillData.name,
            description: skillData.description || '',
            instruction: skillData.instruction || '',
          });

          // 保存原始数据用于比较
          const originalFormData = {
            name: skillData.name,
            description: skillData.description || '',
            instruction: skillData.instruction || '',
          };
          const originalResources = JSON.parse(JSON.stringify(resources)); // 深拷贝

          // 先更新 resources，然后再构建文件树，确保使用最新的数据
          this.setState(
            {
              resources,
              expandedKeys: resources.map((_, index) => String(index)),
              currentSkillData: skillData,
              prevFormValues: originalFormData,
              originalFormData,
              originalResources,
              hasChanges: false, // 初始状态没有变化
            },
            () => {
              // 在 resources 更新后构建文件树
              const previewData = this.buildPreviewData();
              const fileTree = this.buildFileTree(previewData);
              // Find SKILL.md file in the file list
              const skillMdFile =
                fileTree && Array.isArray(fileTree)
                  ? fileTree.find(file => file.name === 'SKILL.md' && file.fileType === 'skill-md')
                  : null;

              // 更新文件树和选中的文件
              this.setState(
                {
                  editModeFileTree: fileTree,
                  editModeSelectedFile:
                    skillMdFile || (fileTree && fileTree.length > 0 ? fileTree[0] : null),
                },
                () => {
                  // 执行回调，允许调用方在数据加载完成后执行额外操作
                  if (callback && typeof callback === 'function') {
                    callback();
                  }
                }
              );
            }
          );
        } else {
          Message.error(
            data?.message ||
              this.getLocaleValue('getSkillInfoFailed', 'Failed to get Skill information')
          );
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(this.getLocaleValue('getSkillInfoFailed', 'Failed to get Skill information'));
      },
    });
  };

  handleSubmit = () => {
    const { locale = {} } = this.props;

    // 如果有正在编辑的文件名，先保存它
    const { editingFileName, editingFileNameValue, resources } = this.state;
    if (editingFileName && editingFileNameValue) {
      const nameToSave = editingFileNameValue.trim();
      if (nameToSave && nameToSave !== editingFileName.oldName) {
        // 同步保存文件名
        const filteredName = nameToSave.replace(/[^a-zA-Z0-9._-]/g, '');

        if (filteredName && filteredName !== editingFileName.oldName) {
          // 检查是否重名
          const isDuplicate = resources.some(
            r =>
              r.name === filteredName &&
              r.type === editingFileName.type &&
              r.name !== editingFileName.oldName
          );

          if (isDuplicate) {
            Message.warning(locale.fileNameDuplicate || 'File name already exists');
            return;
          }

          const resourceIndex = resources.findIndex(
            r => r.name === editingFileName.oldName && r.type === editingFileName.type
          );

          if (resourceIndex !== -1) {
            const newResources = [...resources];
            newResources[resourceIndex] = {
              ...newResources[resourceIndex],
              name: filteredName,
            };
            // 更新 resources，然后继续提交
            this.setState(
              {
                resources: newResources,
                editingFileName: null,
                editingFileNameValue: '',
              },
              () => {
                // 继续提交
                this.doSubmit(newResources);
              }
            );
            return;
          }
        }
      }
      // 如果文件名没有变化或无效，取消编辑状态后继续提交
      this.setState({ editingFileName: null, editingFileNameValue: '' }, () => {
        this.doSubmit();
      });
      return;
    }

    this.doSubmit();
  };

  doSubmit = (resourcesToUse = null) => {
    const { locale = {} } = this.props;

    // 在 validate 之前获取 resources，避免闭包问题
    const resourcesSnapshot = resourcesToUse || [...this.state.resources];
    console.log('[doSubmit] resources snapshot:', JSON.stringify(resourcesSnapshot));

    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }

      this.setState({ loading: true });

      // 使用快照的 resources
      const resources = resourcesSnapshot;

      const namespaceId = getParams('namespace') || '';
      const { isEdit } = this.state;

      // 构建 skillCard 对象
      const skillCard = {
        name: values.name,
        description: values.description || '',
        instruction: values.instruction || '',
      };

      // 构建 resource Map，过滤掉无效的资源（没有 name 或 name 为空的资源）
      if (resources && resources.length > 0) {
        const resourceMap = {};
        const duplicateNames = new Set();
        resources.forEach((resource, index) => {
          // 只包含有效的资源（有 name 且 name 不为空）
          if (resource.name && resource.name.trim() !== '') {
            const key = resource.name.trim();
            // 检查是否有重复的文件名
            if (resourceMap[key]) {
              duplicateNames.add(key);
              console.warn(
                `Duplicate resource name detected: ${key}. Previous content will be overwritten.`
              );
            }
            resourceMap[key] = {
              name: resource.name.trim(),
              type: resource.type || '',
              content: resource.content || '',
              metadata: resource.metadata || null,
            };
          } else {
            console.warn(
              `Skipping resource at index ${index} because name is empty or invalid:`,
              resource
            );
          }
        });
        if (duplicateNames.size > 0) {
          console.warn(
            `Found ${duplicateNames.size} duplicate resource name(s):`,
            Array.from(duplicateNames)
          );
        }
        skillCard.resource = resourceMap;
        console.log('[doSubmit] Built resourceMap:', JSON.stringify(resourceMap));
      } else {
        skillCard.resource = {};
      }

      console.log('[doSubmit] Final skillCard:', JSON.stringify(skillCard));

      // 准备请求数据
      const requestData = {
        namespaceId: namespaceId,
        skillName: values.name,
        skillCard: JSON.stringify(skillCard),
      };

      const url = 'v3/console/ai/skills';

      request({
        url: url,
        method: isEdit ? 'PUT' : 'POST',
        data: requestData,
        contentType: 'application/x-www-form-urlencoded',
        success: data => {
          this.setState({ loading: false });
          if (
            data &&
            (data.code === 0 ||
              data.code === 200 ||
              data.data === 'ok' ||
              data.message === 'success')
          ) {
            Message.success(
              isEdit
                ? this.getLocaleValue('updateSuccess', 'Update successful')
                : this.getLocaleValue('createSuccess', 'Create successful')
            );

            if (isEdit) {
              // 更新成功后，重新加载数据并重置变化状态
              // 保存当前选中的文件信息，以便在重新加载后恢复选中状态
              const { editModeSelectedFile } = this.state;
              const selectedFileName = editModeSelectedFile?.name;
              const selectedFileType = editModeSelectedFile?.fileType;
              const selectedResourceKey = editModeSelectedFile?.resourceKey;

              this.loadSkillData(() => {
                // 在数据加载完成后，尝试恢复之前选中的文件
                if (selectedFileName && selectedFileType === 'resource') {
                  const { editModeFileTree } = this.state;
                  if (editModeFileTree && Array.isArray(editModeFileTree)) {
                    // 递归查找文件
                    const findFileInTree = tree => {
                      if (Array.isArray(tree)) {
                        for (const node of tree) {
                          if (
                            node.fileType === 'resource' &&
                            (node.resourceKey === selectedResourceKey ||
                              node.name === selectedFileName)
                          ) {
                            return node;
                          }
                          if (node.children) {
                            const found = findFileInTree(node.children);
                            if (found) return found;
                          }
                        }
                      }
                      return null;
                    };

                    const restoredFile = findFileInTree(editModeFileTree);
                    if (restoredFile) {
                      this.setState({ editModeSelectedFile: restoredFile });
                    }
                  }
                }
              });
            } else {
              setTimeout(() => {
                this.handleGoBack();
              }, 1000);
            }
          } else {
            Message.error(
              data?.message ||
                (isEdit
                  ? this.getLocaleValue('updateFailed', 'Update failed')
                  : this.getLocaleValue('createFailed', 'Create failed'))
            );
          }
        },
        error: error => {
          console.error('Request failed:', error);
          this.setState({ loading: false });
          Message.error(
            isEdit
              ? this.getLocaleValue('updateFailed', 'Update failed')
              : this.getLocaleValue('createFailed', 'Create failed')
          );
        },
      });
    });
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || '';
    this.props.history.push(`/skillManagement?namespace=${namespaceId}`);
  };

  handleAddResource = () => {
    const { resources } = this.state;
    const newResource = {
      name: '',
      type: '',
      content: '',
      metadata: null,
    };
    const newIndex = resources.length;
    this.setState(
      {
        resources: [...resources, newResource],
        expandedKeys: [String(newIndex)], // 只展开新添加的资源
      },
      () => {
        // 更新文件树和变化状态
        if (this.state.isEdit) {
          this.updateEditModeFileTree();
          // 检查是否有变化
          const hasChanged = this.hasDataChanged();
          if (hasChanged !== this.state.hasChanges) {
            this.setState({ hasChanges: hasChanged });
          }
        }
      }
    );
  };

  handleExpandChange = expandedKeys => {
    this.setState({ expandedKeys });
  };

  handleRemoveResource = index => {
    Dialog.confirm({
      title: this.getLocaleValue('deleteConfirm', 'Delete Confirmation'),
      content: this.getLocaleValue(
        'deleteResourceConfirm',
        'Are you sure you want to delete this resource?'
      ),
      onOk: () => {
        const { resources, expandedKeys } = this.state;
        const newResources = resources.filter((_, i) => i !== index);
        // 更新 expandedKeys，移除被删除的索引并重新计算后续索引
        const newExpandedKeys = expandedKeys
          .filter(key => key !== String(index))
          .map(key => {
            const keyNum = parseInt(key, 10);
            return keyNum > index ? String(keyNum - 1) : key;
          });
        this.setState({ resources: newResources, expandedKeys: newExpandedKeys }, () => {
          // 更新文件树和变化状态
          if (this.state.isEdit) {
            this.updateEditModeFileTree();
            // 检查是否有变化
            const hasChanged = this.hasDataChanged();
            if (hasChanged !== this.state.hasChanges) {
              this.setState({ hasChanges: hasChanged });
            }
          }
        });
      },
    });
  };

  handleResourceChange = (index, field, value) => {
    let filteredValue = value;

    // 资源名称：支持英文大小写、数字、点号、下划线、横杠，不能有空格
    if (field === 'name') {
      filteredValue = value.replace(/[^a-zA-Z0-9._-]/g, '');
    }
    // 资源类型：只支持英文大小写、横杠、点号
    if (field === 'type') {
      filteredValue = value.replace(/[^a-zA-Z.-]/g, '');
    }

    const { resources } = this.state;
    const newResources = [...resources];
    newResources[index] = {
      ...newResources[index],
      [field]: filteredValue,
    };

    this.setState({ resources: newResources }, () => {
      // 更新文件树和变化状态
      if (this.state.isEdit) {
        this.updateEditModeFileTree();
        // 检查是否有变化
        const hasChanged = this.hasDataChanged();
        if (hasChanged !== this.state.hasChanges) {
          this.setState({ hasChanges: hasChanged });
        }
      }
    });
  };

  handleResourceTitleClick = (index, e) => {
    e.stopPropagation();
    this.setState({ editingResourceIndex: index });
  };

  handleResourceTitleMouseEnter = (index, e) => {
    this.setState({
      tooltipVisible: true,
      tooltipPosition: { x: e.clientX, y: e.clientY },
      tooltipResourceIndex: index,
    });
  };

  handleResourceTitleMouseMove = e => {
    if (this.state.tooltipVisible) {
      this.setState({
        tooltipPosition: { x: e.clientX, y: e.clientY },
      });
    }
  };

  handleResourceTitleMouseLeave = () => {
    this.setState({
      tooltipVisible: false,
      tooltipResourceIndex: null,
    });
  };

  handleResourceTitleCancel = () => {
    this.setState({ editingResourceIndex: null });
  };

  handleSkillNameChange = value => {
    // Skill名称：只允许英文、下划线、横杠，输入非法字符时实时提醒
    const filteredValue = value.replace(/[^a-zA-Z_-]/g, '');
    if (filteredValue.length !== value.length) {
      Message.warning(
        this.getLocaleValue(
          'skillNameInvalidChars',
          'Name can only contain English letters, underscore and hyphen'
        )
      );
    }
    this.field.setValue('name', filteredValue);
    // 实时校验（双下划线、必填等），在表单项下展示错误
    this.field.validate('name');
    // 检查是否有变化
    if (this.state.isEdit) {
      setTimeout(() => {
        const hasChanged = this.hasDataChanged();
        if (hasChanged !== this.state.hasChanges) {
          this.setState({ hasChanges: hasChanged });
        }
      }, 0);
    }
  };

  validateSkillName = (rule, value, callback) => {
    if (!value || value.trim() === '') {
      callback(this.getLocaleValue('requiredField', 'This field is required'));
      return;
    }
    if (!/^[a-zA-Z_-]+$/.test(value)) {
      callback(
        this.getLocaleValue(
          'skillNameInvalidChars',
          'Name can only contain English letters, underscore and hyphen'
        )
      );
      return;
    }
    if (value.includes('__')) {
      callback(
        this.getLocaleValue(
          'skillNameNoDoubleUnderscore',
          'Name cannot contain double underscores (__)'
        )
      );
      return;
    }
    callback();
  };

  validateRequired = (rule, value, callback) => {
    if (!value || value.trim() === '') {
      callback(this.getLocaleValue('requiredField', 'This field is required'));
    } else {
      callback();
    }
  };

  handleGenerateSkill = () => {
    const { backgroundInfo } = this.state;

    if (!backgroundInfo || backgroundInfo.trim() === '') {
      Message.warning(
        this.getLocaleValue('backgroundInfoRequired', 'Please enter background information')
      );
      return;
    }

    this.setState({
      generating: true,
      streaming: true,
      streamContent: '',
      thinkingContent: '',
      generatedSkill: null,
      showInputCollapsed: true, // 折叠用户输入
      inputSectionCollapsed: true, // 折叠输入区域
      thinkingCollapsed: false, // 展开思考内容
      resultContentCollapsed: false, // 展开结果内容
      parsingResult: false,
    });

    // Parse conversation history if provided
    let conversationHistory = null;
    if (this.state.conversationHistoryJson && this.state.conversationHistoryJson.trim()) {
      try {
        conversationHistory = JSON.parse(this.state.conversationHistoryJson);
      } catch (e) {
        Message.error(
          this.getLocaleValue(
            'conversationHistoryInvalid',
            'Invalid conversation history JSON format'
          )
        );
        this.setState({ generating: false, streaming: false });
        return;
      }
    } else if (this.state.conversationHistory) {
      conversationHistory = this.state.conversationHistory;
    }

    // Build request payload
    const payload = {
      backgroundInfo: backgroundInfo.trim(),
      selectedMcpTools: this.state.selectedMcpTools.map(tool => ({
        name: tool.name,
        description: tool.description,
        inputSchema: tool.inputSchema,
      })),
    };

    if (conversationHistory) {
      payload.conversationHistory = conversationHistory;
    }

    // Use SSE stream
    const baseUrl = window.location.origin;
    const url = `${baseUrl}/v3/console/copilot/skill/generate`;
    const token = localStorage.getItem('token');

    this.startSSEStream(url, payload, token);
  };

  startSSEStream = (url, payload, token) => {
    // Use fetch API for POST request with SSE
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: token } : {}),
      },
      body: JSON.stringify(payload),
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        this.streamReader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let currentEventType = 'message'; // Default event type
        let pendingData = null; // Store data when event type comes after data

        const readStream = () => {
          this.streamReader
            .read()
            .then(({ done, value }) => {
              if (done) {
                this.setState({ streaming: false, generating: false });
                return;
              }

              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split('\n');
              buffer = lines.pop() || ''; // Keep incomplete line in buffer

              lines.forEach(line => {
                if (line.startsWith('event:')) {
                  const eventType = line.substring(6).trim();
                  currentEventType = eventType;
                  // If we have pending data and this is an error event, handle it now
                  if (eventType === 'error' && pendingData) {
                    const errorMessage =
                      pendingData.explanation ||
                      pendingData.message ||
                      this.getLocaleValue('generateFailed', '生成失败');
                    Message.error(errorMessage);
                    this.setState({
                      streaming: false,
                      generating: false,
                      error: errorMessage,
                    });
                    pendingData = null;
                  }
                } else if (line.startsWith('data:')) {
                  const dataStr = line.substring(5).trim();
                  if (dataStr) {
                    try {
                      const data = JSON.parse(dataStr);
                      // Handle error event - check if current event type is error
                      if (currentEventType === 'error') {
                        const errorMessage =
                          data.explanation ||
                          data.message ||
                          this.getLocaleValue('generateFailed', '生成失败');
                        Message.error(errorMessage);
                        this.setState({
                          streaming: false,
                          generating: false,
                          error: errorMessage,
                        });
                      } else if (data.done && data.explanation && !data.skill) {
                        // Handle case where error comes in data with done=true but no skill
                        // This might be an error response
                        const errorMessage =
                          data.explanation ||
                          data.message ||
                          this.getLocaleValue('generateFailed', '生成失败');
                        Message.error(errorMessage);
                        this.setState({
                          streaming: false,
                          generating: false,
                          error: errorMessage,
                        });
                        pendingData = null; // Clear pending data since we've handled the error
                      } else {
                        // Store data temporarily in case event:error comes after
                        pendingData = data;
                        this.handleSSEMessage(data);
                      }
                    } catch (e) {
                      // Failed to parse SSE data
                      console.error('Failed to parse SSE data:', e, dataStr);
                    }
                  }
                }
              });

              readStream();
            })
            .catch(error => {
              this.setState({
                streaming: false,
                generating: false,
                error: error.message || 'Stream read failed',
              });
              Message.error(this.getLocaleValue('generateFailed', 'Failed to generate skill'));
            });
        };

        readStream();
      })
      .catch(error => {
        this.setState({
          streaming: false,
          generating: false,
        });
        Message.error(this.getLocaleValue('generateFailed', 'Failed to generate skill'));
      });
  };

  handleSSEMessage = data => {
    if (!data) {
      return;
    }

    const typeStr = data.type?.code || data.type || 'CONTENT';

    // Handle different response types
    if (typeStr === 'THINKING' || data.type === 'THINKING') {
      // Accumulate thinking content separately
      this.setState(prevState => ({
        thinkingContent: (prevState.thinkingContent || '') + (data.chunk || ''),
        streamType: 'THINKING',
      }));
    } else if (
      typeStr === 'TOOL_CALL' ||
      data.type === 'TOOL_CALL' ||
      typeStr === 'CONTENT' ||
      data.type === 'CONTENT'
    ) {
      // Accumulate stream content
      const currentContent = this.state.streamContent || '';
      this.setState({
        streamContent: currentContent + (data.chunk || ''),
        streamType: typeStr === 'TOOL_CALL' ? 'TOOL_CALL' : 'CONTENT',
        thinkingCollapsed: true, // 折叠思考内容，开始显示结果
        resultContentCollapsed: false, // 展开结果内容
      });
    } else if (typeStr === 'DONE' || data.done) {
      // Final response received - start parsing
      this.setState({
        streaming: false,
        thinkingCollapsed: true, // 折叠思考内容
        resultContentCollapsed: true, // 折叠结果内容，显示生成的Skill
        parsingResult: true, // 开始解析结果
      });

      // Parse result after a short delay
      setTimeout(() => {
        // DONE event doesn't contain full content, must parse from accumulated streamContent
        let skillData = null;

        // Always try to parse from accumulated streamContent first
        if (this.state.streamContent) {
          try {
            // Try to extract JSON from streamContent
            const content = this.state.streamContent;
            let jsonContent = content;

            // Try to extract JSON from markdown code blocks
            if (content.includes('```json')) {
              const start = content.indexOf('```json') + 7;
              const end = content.indexOf('```', start);
              if (end > start) {
                jsonContent = content.substring(start, end).trim();
              }
            } else if (content.includes('```')) {
              const start = content.indexOf('```') + 3;
              const end = content.indexOf('```', start);
              if (end > start) {
                jsonContent = content.substring(start, end).trim();
              }
            }

            // Try to find JSON object by matching braces
            if (!jsonContent.startsWith('{') && !jsonContent.startsWith('[')) {
              const jsonMatch = jsonContent.match(/\{[\s\S]*\}/);
              if (jsonMatch) {
                jsonContent = jsonMatch[0];
              }
            }

            // Parse JSON
            const parsed = JSON.parse(jsonContent);
            // Support multiple formats: {skill: {...}} or direct skill object
            skillData = parsed.skill || parsed;

            // eslint-disable-next-line no-console
            console.log('Parsed skill from streamContent:', skillData);
          } catch (e) {
            // eslint-disable-next-line no-console
            console.error(
              'Failed to parse skill from streamContent:',
              e,
              'Content:',
              this.state.streamContent
            );
          }
        }

        // Fallback: check if skill is directly in DONE event (unlikely but possible)
        if (!skillData && data.skill) {
          skillData = data.skill;
          // eslint-disable-next-line no-console
          console.log('Using skill from DONE event:', skillData);
        }

        if (skillData) {
          this.setState({
            generating: false,
            parsingResult: false,
            generatedSkill: {
              skill: skillData,
              explanation: this.getLocaleValue('generateSuccess', 'Skill generated successfully'),
            },
          });
        } else {
          // eslint-disable-next-line no-console
          console.error('No skill found in DONE event or streamContent:', {
            data,
            streamContent: this.state.streamContent,
          });
          Message.error(
            this.getLocaleValue(
              'generateFailed',
              'Failed to generate skill: no skill data returned'
            )
          );
          this.setState({ generating: false, parsingResult: false });
        }
      }, 500);
    } else if (data.explanation && data.explanation.includes('失败')) {
      // Error response
      Message.error(data.explanation);
      this.setState({ generating: false, streaming: false, parsingResult: false });
    }
  };

  handleApplyGeneratedSkill = () => {
    const { generatedSkill } = this.state;

    if (!generatedSkill || !generatedSkill.skill) {
      return;
    }

    const skill = generatedSkill.skill;

    // Fill form with generated skill
    this.field.setValues({
      name: skill.name || '',
      description: skill.description || '',
      instruction: skill.instruction || '',
    });

    // Fill resources if any
    if (skill.resource && Object.keys(skill.resource).length > 0) {
      const resources = Object.values(skill.resource).map(resource => ({
        name: resource.name || '',
        type: resource.type || '',
        content: resource.content || '',
        metadata: resource.metadata || null,
      }));
      this.setState({
        resources,
        expandedKeys: resources.map((_, index) => String(index)),
      });
    }

    Message.success(
      generatedSkill.explanation ||
        this.getLocaleValue('generateSuccess', 'Skill generated successfully')
    );

    // Close dialog and reset
    this.setState({
      showAiGenerateDialog: false,
      backgroundInfo: '',
      generatedSkill: null,
    });
  };

  handleBackgroundInfoChange = value => {
    this.setState({ backgroundInfo: value });
  };

  handleShowAiGenerate = () => {
    this.setState({
      showAiGenerateDialog: true,
      backgroundInfo: '',
      generatedSkill: null,
      selectedMcpServer: null,
      mcpTools: [],
      selectedMcpTools: [],
      mcpToolSearchKeyword: '',
      thinkingContent: '',
      thinkingCollapsed: false,
      showInputCollapsed: false,
      inputTooltipVisible: false,
      thinkingTooltipVisible: false,
      parsingResult: false,
      streaming: false,
      streamContent: '',
    });
    this.loadMcpServers();
  };

  handleCloseAiGenerateDialog = () => {
    this.setState({
      showAiGenerateDialog: false,
      backgroundInfo: '',
      generatedSkill: null,
      selectedMcpServer: null,
      mcpTools: [],
      selectedMcpTools: [],
      mcpToolSearchKeyword: '',
      thinkingContent: '',
      thinkingCollapsed: false,
      showInputCollapsed: false,
      inputTooltipVisible: false,
      thinkingTooltipVisible: false,
      parsingResult: false,
    });
  };

  handleInputMouseEnter = e => {
    this.setState({
      inputTooltipVisible: true,
      inputTooltipPosition: { x: e.clientX, y: e.clientY },
    });
  };

  handleInputMouseMove = e => {
    if (this.state.inputTooltipVisible) {
      this.setState({
        inputTooltipPosition: { x: e.clientX, y: e.clientY },
      });
    }
  };

  handleInputMouseLeave = () => {
    this.setState({
      inputTooltipVisible: false,
    });
  };

  handleThinkingMouseEnter = e => {
    this.setState({
      thinkingTooltipVisible: true,
      thinkingTooltipPosition: { x: e.clientX, y: e.clientY },
    });
  };

  handleThinkingMouseMove = e => {
    if (this.state.thinkingTooltipVisible) {
      this.setState({
        thinkingTooltipPosition: { x: e.clientX, y: e.clientY },
      });
    }
  };

  handleThinkingMouseLeave = () => {
    this.setState({
      thinkingTooltipVisible: false,
    });
  };

  renderUserInput = () => {
    const { backgroundInfo, selectedMcpTools, showInputCollapsed } = this.state;

    if (!showInputCollapsed) {
      return null;
    }

    const selectedToolsText =
      selectedMcpTools.length > 0
        ? selectedMcpTools.map(t => t.name).join(', ')
        : this.getLocaleValue('noToolsSelected', 'No tools selected');

    const fullContent = `${this.getLocaleValue(
      'backgroundInfo',
      'Background Information'
    )}: ${backgroundInfo}\n\n${this.getLocaleValue(
      'selectedTools',
      'Selected Tools'
    )}: ${selectedToolsText}`;

    return (
      <div
        style={{
          marginBottom: 16,
          padding: '12px 16px',
          background: '#fafafa',
          borderRadius: 4,
          border: '1px solid #e6e6e6',
          cursor: 'pointer',
        }}
        onMouseEnter={this.handleInputMouseEnter}
        onMouseMove={this.handleInputMouseMove}
        onMouseLeave={this.handleInputMouseLeave}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Icon type="info" size="small" style={{ color: '#1890ff' }} />
            <span style={{ fontSize: '13px', color: '#666' }}>
              {this.getLocaleValue('userInput', 'User Input')}
            </span>
          </div>
          <Icon type="arrow-up" size="small" style={{ color: '#999' }} />
        </div>
        <div
          style={{
            marginTop: 8,
            fontSize: '12px',
            color: '#999',
            maxHeight: 40,
            overflow: 'hidden',
          }}
        >
          {backgroundInfo.substring(0, 100)}
          {backgroundInfo.length > 100 ? '...' : ''}
        </div>
      </div>
    );
  };

  renderThinkingContent = () => {
    const { thinkingContent, thinkingCollapsed, streaming } = this.state;

    if (!streaming && !thinkingContent) {
      return null;
    }

    if (thinkingCollapsed && thinkingContent) {
      return (
        <div
          style={{
            marginBottom: 16,
            padding: '12px 16px',
            background: '#fafafa',
            borderRadius: 4,
            border: '1px solid #e6e6e6',
            cursor: 'pointer',
          }}
          onMouseEnter={this.handleThinkingMouseEnter}
          onMouseMove={this.handleThinkingMouseMove}
          onMouseLeave={this.handleThinkingMouseLeave}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Tag type="normal" size="small">
                {this.getLocaleValue('thinking', 'Thinking')}
              </Tag>
              <span style={{ fontSize: '13px', color: '#666' }}>
                {this.getLocaleValue('thinkingProcess', 'Thinking Process')}
              </span>
            </div>
            <Icon type="arrow-up" size="small" style={{ color: '#999' }} />
          </div>
          <div
            style={{
              marginTop: 8,
              fontSize: '12px',
              color: '#999',
              maxHeight: 40,
              overflow: 'hidden',
            }}
          >
            {thinkingContent.substring(0, 100)}
            {thinkingContent.length > 100 ? '...' : ''}
          </div>
        </div>
      );
    }

    if (!thinkingCollapsed && thinkingContent) {
      return (
        <div style={{ marginBottom: 16 }}>
          <Collapse
            defaultExpandedKeys={['0']}
            style={{ border: '1px solid #e6e6e6', borderRadius: '4px' }}
          >
            <Panel
              key="0"
              title={
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <Tag type="normal" size="small" style={{ marginRight: 8 }}>
                    {this.getLocaleValue('thinking', 'Thinking')}
                  </Tag>
                  <span>{this.getLocaleValue('thinkingProcess', 'Thinking Process')}</span>
                </div>
              }
            >
              <div
                style={{
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  margin: 0,
                  fontSize: '13px',
                }}
              >
                {thinkingContent}
              </div>
            </Panel>
          </Collapse>
        </div>
      );
    }

    return null;
  };

  // 获取文本的前N行预览
  getPreviewLines = (text, lines = 2) => {
    if (!text) return '';
    const textLines = text.split('\n');
    if (textLines.length <= lines) return text;
    return `${textLines.slice(0, lines).join('\n')}\n...`;
  };

  // 渲染可折叠内容区域（参考 SkillOptimizeDialog）
  renderCollapsibleSection = (
    title,
    content,
    isCollapsed,
    onToggle,
    icon = null,
    loading = false,
    thinkingContentForIcon = null
  ) => {
    if (!content && !loading) {
      return null;
    }

    const contentLines = content ? content.split('\n') : [];
    const previewContent = isCollapsed && content ? this.getPreviewLines(content, 2) : content;
    const hasMore = isCollapsed && contentLines.length > 2;

    return (
      <div
        style={{
          border: '1px solid #e6e6e6',
          borderRadius: '4px',
          marginBottom: 16,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 16px',
            background: '#fafafa',
            cursor: 'pointer',
            borderBottom: content || loading ? '1px solid #e6e6e6' : 'none',
          }}
          onClick={onToggle}
        >
          <div style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
            {icon && (
              <div style={{ marginRight: 8, display: 'flex', alignItems: 'center' }}>{icon}</div>
            )}
            <span style={{ fontWeight: 500 }}>{title}</span>
            {thinkingContentForIcon && thinkingContentForIcon.trim() && (
              <span
                style={{ marginLeft: 8, display: 'inline-flex', alignItems: 'center' }}
                onClick={e => {
                  e.stopPropagation();
                }}
              >
                <Balloon
                  trigger={
                    <Icon
                      type="info"
                      size="small"
                      style={{
                        color: '#1890ff',
                        cursor: 'pointer',
                        fontSize: '14px',
                      }}
                    />
                  }
                  triggerType="hover"
                  align="t"
                  style={{ maxWidth: '500px' }}
                >
                  <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
                    <div style={{ fontWeight: 500, marginBottom: 8 }}>
                      {this.getLocaleValue('thinking', 'Thinking')}
                    </div>
                    <pre
                      style={{
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        margin: 0,
                        fontSize: '12px',
                        lineHeight: '1.6',
                      }}
                    >
                      {thinkingContentForIcon}
                    </pre>
                  </div>
                </Balloon>
              </span>
            )}
            {loading && <Loading size="medium" style={{ marginLeft: 8 }} />}
          </div>
          <Icon
            type={isCollapsed ? 'arrow-down' : 'arrow-up'}
            style={{ fontSize: 12, color: '#666' }}
          />
        </div>
        {(content || loading) && (
          <div
            style={{
              padding: '12px 16px',
              maxHeight: isCollapsed ? '60px' : 'none',
              overflow: isCollapsed ? 'hidden' : 'auto',
              background: '#fff',
              position: 'relative',
            }}
          >
            {loading && !content ? (
              <div style={{ display: 'flex', alignItems: 'center', color: '#999' }}>
                <Loading size="medium" style={{ marginRight: 8 }} />
                <span>{title}</span>
              </div>
            ) : (
              <pre
                style={{
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  margin: 0,
                  fontSize: '13px',
                  lineHeight: '1.6',
                  color: '#333',
                }}
              >
                {previewContent}
              </pre>
            )}
            {isCollapsed && hasMore && (
              <div
                style={{
                  position: 'absolute',
                  bottom: 0,
                  left: 0,
                  right: 0,
                  height: '20px',
                  background:
                    'linear-gradient(to bottom, rgba(255,255,255,0) 0%, rgba(255,255,255,1) 100%)',
                  pointerEvents: 'none',
                }}
              />
            )}
          </div>
        )}
      </div>
    );
  };

  // 渲染流式内容（参考 SkillOptimizeDialog）
  renderStreamContent = () => {
    const {
      streamContent,
      thinkingContent,
      streamType,
      thinkingCollapsed,
      resultContentCollapsed,
      streaming,
    } = this.state;

    return (
      <div>
        {/* 思考内容区域 - 仅在 THINKING 阶段显示 */}
        {thinkingContent &&
          streamType === 'THINKING' &&
          this.renderCollapsibleSection(
            this.getLocaleValue('thinking', '思考') || '思考',
            thinkingContent,
            thinkingCollapsed,
            () => this.setState({ thinkingCollapsed: !thinkingCollapsed }),
            <Tag type="normal" size="small">
              {this.getLocaleValue('thinking', '思考') || '思考'}
            </Tag>,
            streaming && streamType === 'THINKING' && !thinkingContent
          )}

        {/* 结果内容区域 - 当开始接收模型回复时显示，包含思考内容图标 */}
        {streamContent &&
          streamType !== 'THINKING' &&
          this.renderCollapsibleSection(
            this.getLocaleValue('generatingContent', '生成内容') || '生成内容',
            streamContent,
            resultContentCollapsed,
            () => this.setState({ resultContentCollapsed: !resultContentCollapsed }),
            <Tag type="primary" size="small">
              {this.getLocaleValue('generatingContent', '生成内容') || '生成内容'}
            </Tag>,
            streaming && !streamContent,
            this.state.thinkingContent
          )}
      </div>
    );
  };

  renderGeneratedSkill = () => {
    const {
      generatedSkill,
      backgroundInfo,
      selectedMcpTools,
      thinkingContent,
      streamContent,
      inputSectionCollapsed,
      thinkingCollapsed,
      resultContentCollapsed,
    } = this.state;
    if (!generatedSkill || !generatedSkill.skill) {
      return null;
    }

    try {
      const skill = generatedSkill.skill;
      const resources = skill.resource ? Object.values(skill.resource) : [];

      // 构建用户输入内容
      const selectedToolsText =
        selectedMcpTools.length > 0
          ? selectedMcpTools.map(t => t.name).join(', ')
          : this.getLocaleValue('noToolsSelected', 'No tools selected');
      const userInputContent = `${this.getLocaleValue(
        'backgroundInfo',
        'Background Information'
      )}: ${backgroundInfo}\n\n${this.getLocaleValue(
        'selectedTools',
        'Selected Tools'
      )}: ${selectedToolsText}`;
      const userInputPreview = backgroundInfo
        ? backgroundInfo.substring(0, 100) + (backgroundInfo.length > 100 ? '...' : '')
        : '';

      return (
        <div>
          {/* 显示用户输入 - 折叠状态，可展开查看 */}
          {this.renderCollapsibleSection(
            this.getLocaleValue('userInput', 'User Input') || '用户输入',
            inputSectionCollapsed ? userInputPreview : userInputContent,
            inputSectionCollapsed,
            () => this.setState({ inputSectionCollapsed: !this.state.inputSectionCollapsed }),
            null,
            false
          )}

          {/* 思考内容 - 始终显示（如果存在） */}
          {thinkingContent &&
            this.renderCollapsibleSection(
              this.getLocaleValue('thinking', '思考') || '思考',
              thinkingContent,
              thinkingCollapsed,
              () => this.setState({ thinkingCollapsed: !thinkingCollapsed }),
              <Tag type="normal" size="small">
                {this.getLocaleValue('thinking', '思考') || '思考'}
              </Tag>,
              false
            )}

          {/* 结果内容 - 始终显示（如果存在） */}
          {streamContent &&
            this.renderCollapsibleSection(
              this.getLocaleValue('generatingContent', '生成内容') || '生成内容',
              streamContent,
              resultContentCollapsed,
              () => this.setState({ resultContentCollapsed: !resultContentCollapsed }),
              <Tag type="primary" size="small">
                {this.getLocaleValue('generatingContent', '生成内容') || '生成内容'}
              </Tag>,
              false,
              thinkingContent
            )}

          {/* 生成的Skill预览 */}
          <div style={{ marginTop: 24, paddingTop: 16, borderTop: '2px solid #e6e6e6' }}>
            <div
              style={{
                background: '#fff',
                padding: '20px',
                borderRadius: '4px',
                border: '1px solid #e6e6e6',
              }}
            >
              <Row gutter={16} style={{ marginBottom: 16 }}>
                <Col span={12}>
                  <div style={{ marginBottom: 16 }}>
                    <label
                      style={{
                        display: 'block',
                        marginBottom: 8,
                        fontSize: '14px',
                        fontWeight: 500,
                        color: '#333',
                      }}
                    >
                      {this.getLocaleValue('skillName', 'Skill Name')}:
                    </label>
                    <span style={{ fontSize: '14px', color: '#666' }}>{skill.name || '--'}</span>
                  </div>
                </Col>
              </Row>
              <Row gutter={16} style={{ marginBottom: 24 }}>
                <Col span={24}>
                  <div style={{ marginBottom: 16 }}>
                    <label
                      style={{
                        display: 'block',
                        marginBottom: 8,
                        fontSize: '14px',
                        fontWeight: 500,
                        color: '#333',
                      }}
                    >
                      {this.getLocaleValue('description', 'Description')}:
                    </label>
                    <span style={{ fontSize: '14px', color: '#666' }}>
                      {skill.description || '--'}
                    </span>
                  </div>
                </Col>
              </Row>

              <div style={{ marginBottom: 24 }}>
                <div
                  style={{ fontSize: '14px', fontWeight: 500, marginBottom: '12px', color: '#333' }}
                >
                  {this.getLocaleValue('instruction', 'Instruction')}
                </div>
                <div
                  style={{
                    minHeight: 200,
                    padding: '12px',
                    background: '#fafafa',
                    borderRadius: 4,
                    border: '1px solid #e6e6e6',
                  }}
                >
                  <MarkdownRenderer content={skill.instruction || ''} />
                </div>
              </div>

              <div>
                <div
                  style={{ fontSize: '14px', fontWeight: 500, marginBottom: '12px', color: '#333' }}
                >
                  {this.getLocaleValue('resources', 'Resources')}
                </div>
                {resources.length > 0 ? (
                  <div>
                    <Collapse>
                      {resources.map((resource, index) => (
                        <Panel
                          key={String(index)}
                          title={
                            <div>
                              <span>
                                {resource.type && resource.name
                                  ? `${resource.type}/${resource.name}`
                                  : `${this.getLocaleValue('resource', 'Resource')} ${index + 1}`}
                              </span>
                            </div>
                          }
                        >
                          <Row gutter={16}>
                            <Col span={12}>
                              <div style={{ marginBottom: 16 }}>
                                <label
                                  style={{
                                    display: 'block',
                                    marginBottom: 8,
                                    fontSize: '13px',
                                    color: '#666',
                                  }}
                                >
                                  {this.getLocaleValue('resourceName', 'Resource Name')}:
                                </label>
                                <span style={{ fontSize: '13px', color: '#333' }}>
                                  {resource.name || '--'}
                                </span>
                              </div>
                            </Col>
                            <Col span={12}>
                              <div style={{ marginBottom: 16 }}>
                                <label
                                  style={{
                                    display: 'block',
                                    marginBottom: 8,
                                    fontSize: '13px',
                                    color: '#666',
                                  }}
                                >
                                  {this.getLocaleValue('resourceType', 'Resource Type')}:
                                </label>
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
                              <div>
                                <label
                                  style={{
                                    display: 'block',
                                    marginBottom: 8,
                                    fontSize: '13px',
                                    color: '#666',
                                  }}
                                >
                                  {this.getLocaleValue('resourceContent', 'Resource Content')}:
                                </label>
                                <div
                                  style={{
                                    border: '1px solid #e6e6e6',
                                    borderRadius: '4px',
                                    marginTop: '8px',
                                  }}
                                >
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
                                    <div
                                      style={{ padding: '12px', color: '#999', marginTop: '8px' }}
                                    >
                                      {this.getLocaleValue('noContent', 'No content')}
                                    </div>
                                  )}
                                </div>
                              </div>
                            </Col>
                          </Row>
                        </Panel>
                      ))}
                    </Collapse>
                  </div>
                ) : (
                  <div
                    style={{
                      padding: '20px',
                      textAlign: 'center',
                      color: '#999',
                      fontSize: '13px',
                    }}
                  >
                    {this.getLocaleValue('noResources', 'No resources')}
                  </div>
                )}
              </div>
            </div>
          </div>

          <div style={{ color: '#999', fontSize: '12px', marginTop: 16 }}>
            {this.getLocaleValue(
              'applyGeneratedSkillHint',
              'Click "Apply" to fill the form with the generated Skill'
            )}
          </div>
        </div>
      );
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Error rendering generated skill:', error, generatedSkill);
      return (
        <div
          style={{
            padding: '20px',
            color: '#f5222d',
            background: '#fff2f0',
            border: '1px solid #ffccc7',
            borderRadius: '4px',
          }}
        >
          <div style={{ marginBottom: 8, fontWeight: 500 }}>渲染生成的 Skill 时出错</div>
          <div style={{ fontSize: '12px' }}>{error.message || 'Unknown error'}</div>
        </div>
      );
    }
  };

  loadMcpServers = async () => {
    this.setState({ loadingMcpServers: true });
    try {
      const namespaceId = getParams('namespace') || 'public';
      const result = await request({
        url: 'v3/console/ai/mcp/list',
        method: 'get',
        data: {
          namespaceId,
          pageNo: 1,
          pageSize: 100,
        },
      });
      if (result.code === 0 && result.data) {
        // Map items to ensure id field exists (use name as id if id is not available)
        const servers = (result.data.pageItems || []).map(item => ({
          ...item,
          id: item.id || item.name,
        }));
        this.setState({
          mcpServers: servers,
          loadingMcpServers: false,
        });
      } else {
        this.setState({ loadingMcpServers: false });
      }
    } catch (error) {
      console.error('Failed to load MCP servers:', error);
      this.setState({ loadingMcpServers: false });
    }
  };

  handleMcpServerChange = async value => {
    this.setState({
      selectedMcpServer: value,
      mcpTools: [],
      selectedMcpTools: [],
      mcpToolSearchKeyword: '', // 重置搜索关键词
    });

    if (value) {
      await this.loadMcpTools(value);
    }
  };

  handleMcpToolSearchChange = value => {
    this.setState({
      mcpToolSearchKeyword: value,
    });
  };

  getFilteredMcpTools = () => {
    const { mcpTools, mcpToolSearchKeyword } = this.state;
    if (!mcpToolSearchKeyword.trim()) {
      return mcpTools;
    }
    const keyword = mcpToolSearchKeyword.toLowerCase();
    return mcpTools.filter(tool => tool.name && tool.name.toLowerCase().includes(keyword));
  };

  loadMcpTools = async mcpServerId => {
    this.setState({ loadingMcpTools: true });
    try {
      const namespaceId = getParams('namespace') || 'public';
      const result = await request({
        url: 'v3/console/ai/mcp',
        method: 'get',
        data: {
          namespaceId,
          mcpId: mcpServerId,
        },
      });
      if (result.code === 0 && result.data && result.data.toolSpec) {
        const tools = result.data.toolSpec.tools || [];
        this.setState({
          mcpTools: tools,
          loadingMcpTools: false,
        });
      } else {
        this.setState({ loadingMcpTools: false });
      }
    } catch (error) {
      console.error('Failed to load MCP tools:', error);
      this.setState({ loadingMcpTools: false });
    }
  };

  handleMcpToolChange = (checked, tool) => {
    const { selectedMcpTools } = this.state;
    if (checked) {
      this.setState({
        selectedMcpTools: [...selectedMcpTools, tool],
      });
    } else {
      this.setState({
        selectedMcpTools: selectedMcpTools.filter(t => t.name !== tool.name),
      });
    }
  };

  handleShowOptimizeDialog = () => {
    this.setState({ optimizeDialogVisible: true });
  };

  handleOptimizeDialogClose = () => {
    this.setState({ optimizeDialogVisible: false });
  };

  handleOptimizeSuccess = optimizedSkill => {
    const { locale = {} } = this.props;
    console.log('handleOptimizeSuccess called with:', optimizedSkill);

    // 优化成功后，将优化后的skill填充到表单中
    if (optimizedSkill) {
      // 处理资源数据，确保格式正确
      let resources = [];
      const resourceData = optimizedSkill.resource || optimizedSkill.resources || {};
      console.log('Resource data:', resourceData);

      if (resourceData && Object.keys(resourceData).length > 0) {
        // 使用Object.entries来同时获取key和value，因为Map的key可能是资源名称
        resources = Object.entries(resourceData).map(([resourceKey, resource]) => {
          // 确保资源对象包含所有必要字段
          // 如果resource对象没有name，使用resourceKey作为name
          const resourceObj = {
            name: resource.name || resourceKey || '',
            type: resource.type || '',
            content: resource.content || '',
            metadata: resource.metadata || null,
          };
          return resourceObj;
        });
      }

      console.log('Processed resources:', resources);

      // Important: Keep original skill name, don't use optimized name
      const originalName = this.field.getValue('name') || this.state.skillName;

      // 获取当前的原始数据用于比较（不要更新原始数据）
      const { originalFormData, originalResources } = this.state;

      // Update form fields with optimized values
      // Use optimized values directly, even if they're empty (to allow clearing fields)
      const optimizedDescription =
        optimizedSkill.description !== undefined
          ? optimizedSkill.description
          : this.field.getValue('description');
      const optimizedInstruction =
        optimizedSkill.instruction !== undefined
          ? optimizedSkill.instruction
          : this.field.getValue('instruction');

      this.field.setValues({
        name: originalName, // Always use original skill name
        description: optimizedDescription,
        instruction: optimizedInstruction,
      });

      // 比较优化后的数据与原始数据，判断是否有变化
      // 如果 originalFormData 或 originalResources 不存在，说明是新建模式，不需要比较
      let hasChanges = false;
      if (originalFormData && originalResources) {
        const formChanged =
          optimizedDescription !== (originalFormData.description || '') ||
          optimizedInstruction !== (originalFormData.instruction || '');
        const resourcesChanged = JSON.stringify(resources) !== JSON.stringify(originalResources);
        hasChanges = formChanged || resourcesChanged;
      } else {
        // 新建模式：如果有任何内容，就认为有变化
        hasChanges =
          optimizedDescription !== '' || optimizedInstruction !== '' || resources.length > 0;
      }

      // Force form to re-render by updating state
      // 重要：不要更新 originalFormData 和 originalResources，保持它们作为比较基准
      this.setState(
        {
          resources,
          expandedKeys: resources.map((_, index) => String(index)),
          currentSkillData: optimizedSkill,
          // 保持 originalFormData 和 originalResources 不变，用于后续比较
          hasChanges, // 根据比较结果设置变化状态
        },
        () => {
          // After state update, ensure form fields are refreshed
          // Force a re-render by calling setValue again if needed
          if (optimizedSkill.description !== undefined) {
            this.field.setValue('description', optimizedSkill.description);
          }
          if (optimizedSkill.instruction !== undefined) {
            this.field.setValue('instruction', optimizedSkill.instruction);
          }
        }
      );

      // 如果添加了新资源，显示提示信息
      if (resources.length > 0) {
        // All resources are considered new when applied from optimization
        const newResourcesCount = resources.length;
        if (newResourcesCount > 0) {
          Message.success(
            (locale.optimizeSuccess || 'Optimization applied successfully') +
              ` (${newResourcesCount} new resource(s) added)`
          );
        } else {
          Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
        }
      } else {
        Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
      }
    } else {
      Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
    }

    this.handleOptimizeDialogClose();
  };

  // Helper function to get locale value with fallback
  getLocaleValue = (key, fallback) => {
    const { locale = {} } = this.props;
    return locale[key] || fallback;
  };

  // Generate resource unique identifier
  // Format: "type::name" if type is not blank, otherwise "name"
  // The separator "::" is used because it's not in the allowed character set for type and name
  getResourceIdentifier = resource => {
    if (resource.type && resource.type.trim() !== '') {
      return `${resource.type}::${resource.name || ''}`;
    }
    return resource.name || '';
  };

  // Build preview data from current form values
  buildPreviewData = () => {
    const values = this.field.getValues();
    const { resources } = this.state;

    // 构建 skillCard 对象（与 handleSubmit 中的逻辑一致）
    const skillCard = {
      name: values.name || '',
      description: values.description || '',
      instruction: values.instruction || '',
    };

    // 构建 resource Map，过滤掉无效的资源（没有 name 或 name 为空的资源）
    if (resources && resources.length > 0) {
      const resourceMap = {};
      resources.forEach((resource, index) => {
        // 只包含有效的资源（有 name 且 name 不为空）
        if (resource.name && resource.name.trim() !== '') {
          const key = resource.name.trim();
          resourceMap[key] = {
            name: resource.name.trim(),
            type: resource.type || '',
            content: resource.content || '',
            metadata: resource.metadata || null,
          };
        }
      });
      skillCard.resource = resourceMap;
    } else {
      skillCard.resource = {};
    }

    return skillCard;
  };

  // Update file tree when resources change
  updateEditModeFileTree = callback => {
    const previewData = this.buildPreviewData();
    const fileTree = this.buildFileTree(previewData);
    this.setState({ editModeFileTree: fileTree }, () => {
      if (callback && typeof callback === 'function') {
        callback(fileTree);
      }
    });
  };

  // Check if data has been modified
  hasDataChanged = () => {
    const { originalFormData, originalResources } = this.state;
    if (!originalFormData || !originalResources) {
      return false;
    }

    const currentValues = this.field.getValues();

    // Check form data changes
    const formChanged =
      currentValues.name !== originalFormData.name ||
      currentValues.description !== originalFormData.description ||
      currentValues.instruction !== originalFormData.instruction;

    // Check resources changes
    const currentResources = this.state.resources || [];
    const resourcesChanged = JSON.stringify(currentResources) !== JSON.stringify(originalResources);

    return formChanged || resourcesChanged;
  };

  // Build file tree structure; type may contain "/" for multi-level folders (e.g. folder1/folder2)
  buildFileTree = previewData => {
    if (!previewData || !previewData.name) {
      return null;
    }

    const fileList = [
      {
        name: 'SKILL.md',
        type: 'file',
        fileType: 'skill-md',
      },
    ];

    const rootChildren = [];
    const resourcesWithoutType = [];

    const getOrCreateFolder = (children, folderName, level = 0) => {
      let folder = children.find(n => n.type === 'folder' && n.name === folderName);
      if (!folder) {
        // Default expand folders up to level 2 (0-indexed: 0, 1, 2 = 3 levels)
        folder = { name: folderName, type: 'folder', children: [], expanded: level < 3 };
        children.push(folder);
      }
      return { children: folder.children, level: level + 1 };
    };

    const sortNodeChildren = nodes => {
      nodes.sort((a, b) =>
        (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' })
      );
      nodes.forEach(n => {
        if (n.type === 'folder' && n.children && n.children.length) {
          sortNodeChildren(n.children);
        }
      });
    };

    if (previewData.resource && Object.keys(previewData.resource).length > 0) {
      Object.entries(previewData.resource).forEach(([key, resource]) => {
        const fileNode = {
          name: resource.name || key,
          type: 'file',
          fileType: 'resource',
          resourceKey: key,
          resource: resource,
        };
        if (!resource.type || resource.type.trim() === '') {
          resourcesWithoutType.push(fileNode);
        } else {
          const pathSegments = resource.type
            .trim()
            .split('/')
            .filter(Boolean);
          let target = { children: rootChildren, level: 0 };
          for (const seg of pathSegments) {
            target = getOrCreateFolder(target.children, seg, target.level);
          }
          target.children.push(fileNode);
        }
      });
    }

    sortNodeChildren(rootChildren);
    const sortedRootFiles = resourcesWithoutType.sort((a, b) =>
      (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' })
    );
    const afterSkill = [...rootChildren, ...sortedRootFiles].sort((a, b) =>
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

  handleEditModeFileClick = (file, e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    // Clear editModeSelectedFile first to force MonacoEditor to unmount, then set new file
    this.setState({ editModeSelectedFile: null }, () => {
      setTimeout(() => {
        this.setState({ editModeSelectedFile: file });
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

  // 保存文件名修改
  handleSaveFileName = newName => {
    const { editingFileName, resources } = this.state;
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
      this.setState(
        {
          resources: newResources,
          editingFileName: null,
          editingFileNameValue: '',
        },
        () => {
          // 更新文件树和变化状态
          if (this.state.isEdit) {
            // 使用 callback 确保文件树更新完成后再查找新文件
            this.updateEditModeFileTree(fileTree => {
              // 更新选中的文件，使其指向新的文件名
              const { editModeSelectedFile } = this.state;
              if (
                editModeSelectedFile &&
                editModeSelectedFile.fileType === 'resource' &&
                editModeSelectedFile.resourceKey === editingFileName.oldName
              ) {
                if (fileTree && Array.isArray(fileTree)) {
                  // 递归查找文件
                  const findFileInTree = tree => {
                    if (Array.isArray(tree)) {
                      for (const node of tree) {
                        if (
                          node.fileType === 'resource' &&
                          node.resourceKey === filteredName &&
                          node.resource?.type === editingFileName.type
                        ) {
                          return node;
                        }
                        if (node.children) {
                          const found = findFileInTree(node.children);
                          if (found) return found;
                        }
                      }
                    }
                    return null;
                  };

                  const newFile = findFileInTree(fileTree);
                  if (newFile) {
                    this.setState({ editModeSelectedFile: newFile });
                  } else {
                    // 如果找不到，清空选中状态
                    this.setState({ editModeSelectedFile: null });
                  }
                }
              }

              const hasChanged = this.hasDataChanged();
              if (hasChanged !== this.state.hasChanges) {
                this.setState({ hasChanges: hasChanged });
              }
            });
          }
        }
      );
    } else {
      this.setState({ editingFileName: null, editingFileNameValue: '' });
    }
  };

  // 取消编辑文件名
  handleCancelEditFileName = () => {
    this.setState({ editingFileName: null, editingFileNameValue: '' });
  };

  // 在文件树中新增文件
  handleAddFileInTree = () => {
    const newResource = {
      name: `new_file_${Date.now()}`,
      type: '',
      content: '',
      metadata: null,
    };
    console.log('[handleAddFileInTree] Adding new resource:', newResource);
    console.log(
      '[handleAddFileInTree] Current resources before add:',
      JSON.stringify(this.state.resources)
    );

    const newResources = [...this.state.resources, newResource];
    console.log('[handleAddFileInTree] New resources after add:', JSON.stringify(newResources));

    this.setState(
      {
        resources: newResources,
      },
      () => {
        console.log(
          '[handleAddFileInTree] State updated, resources:',
          JSON.stringify(this.state.resources)
        );
        // 更新文件树
        if (this.state.isEdit) {
          // 使用 callback 确保文件树更新完成后再查找新文件
          this.updateEditModeFileTree(fileTree => {
            // 选中新文件并进入编辑模式
            if (fileTree && Array.isArray(fileTree)) {
              const newFile = fileTree.find(
                file => file.fileType === 'resource' && file.resourceKey === newResource.name
              );
              if (newFile) {
                this.setState({ editModeSelectedFile: newFile });
                // 自动开始编辑文件名
                setTimeout(() => {
                  this.setState({
                    editingFileName: {
                      nodeKey: newResource.name,
                      oldName: newResource.name,
                      type: '',
                    },
                    editingFileNameValue: newResource.name, // 初始化编辑值
                  });
                }, 100);
              }
            }
            const hasChanged = this.hasDataChanged();
            if (hasChanged !== this.state.hasChanges) {
              this.setState({ hasChanges: hasChanged });
            }
          });
        }
      }
    );
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
  handleDrop = (folderName, e) => {
    e.preventDefault();
    e.stopPropagation();

    const { draggingFile, resources } = this.state;
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

      this.setState(
        {
          resources: newResources,
          draggingFile: null,
          dragOverFolder: null,
        },
        () => {
          // 更新文件树和变化状态
          if (this.state.isEdit) {
            this.updateEditModeFileTree();
            const hasChanged = this.hasDataChanged();
            if (hasChanged !== this.state.hasChanges) {
              this.setState({ hasChanges: hasChanged });
            }
          }
        }
      );
    } else {
      this.setState({ draggingFile: null, dragOverFolder: null });
    }
  };

  toggleFolderExpanded = (fileTree, targetKey) => {
    if (!fileTree || !Array.isArray(fileTree)) {
      return fileTree;
    }

    const toggleNode = (nodes, currentKey = '') => {
      return nodes.map(node => {
        const nodeKey = node.resourceKey
          ? `${currentKey}/${node.resourceKey}`
          : currentKey
          ? `${currentKey}/${node.name}`
          : node.name;

        if (nodeKey === targetKey && node.type === 'folder') {
          return { ...node, expanded: !node.expanded };
        }

        if (node.type === 'folder' && node.children) {
          return {
            ...node,
            children: toggleNode(node.children, nodeKey),
          };
        }

        return node;
      });
    };

    return toggleNode(fileTree);
  };

  renderFileTree = (fileList, level = 0, parentKey = '', isEditMode = false) => {
    if (!fileList) {
      return null;
    }

    // If fileList is an array, render each item
    if (Array.isArray(fileList)) {
      return fileList.map(node => this.renderFileTree(node, level, parentKey, isEditMode));
    }

    // If it's a single node
    const node = fileList;
    const nodeKey = node.resourceKey
      ? `${parentKey}/${node.resourceKey}`
      : parentKey
      ? `${parentKey}/${node.name}`
      : node.name;

    const selectedFile = this.state.editModeSelectedFile;
    const isSelected =
      selectedFile &&
      selectedFile.name === node.name &&
      selectedFile.fileType === node.fileType &&
      selectedFile.resourceKey === node.resourceKey;

    if (node.type === 'folder') {
      const { dragOverFolder, draggingFile } = this.state;
      const isDragOver = dragOverFolder === node.name && draggingFile;
      const isExpanded = node.expanded !== false; // Default to true if not set
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
        cursor: 'pointer',
      };

      const toggleExpand = e => {
        e.stopPropagation();
        const updatedTree = this.toggleFolderExpanded(
          isEditMode ? this.state.editModeFileTree : this.state.fileTree,
          nodeKey
        );
        if (isEditMode) {
          this.setState({ editModeFileTree: updatedTree });
        } else {
          this.setState({ fileTree: updatedTree });
        }
      };

      return (
        <div key={nodeKey} className="file-tree-folder">
          <div
            className="file-tree-item file-tree-folder-item"
            style={folderStyle}
            onClick={toggleExpand}
            onDragOver={isEditMode ? e => this.handleDragOver(node.name, e) : undefined}
            onDragLeave={isEditMode ? this.handleDragLeave : undefined}
            onDrop={isEditMode ? e => this.handleDrop(node.name, e) : undefined}
          >
            <Icon
              type={isExpanded ? 'arrow-down' : 'arrow-right'}
              style={{ marginRight: 4, fontSize: 12, color: '#999' }}
            />
            <Icon type="folder" style={{ marginRight: 8, color: '#666' }} />
            <span style={{ fontWeight: 600 }}>{node.name}</span>
          </div>
          {isExpanded &&
            node.children &&
            node.children.map(child => this.renderFileTree(child, level + 1, nodeKey, isEditMode))}
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
      const canEdit = isEditMode && node.fileType === 'resource' && node.name !== 'SKILL.md';
      const isDragging =
        draggingFile &&
        draggingFile.resourceKey === (node.resourceKey || node.name) &&
        draggingFile.name === node.name;

      const row = (
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
            if (isEditMode && !isEditing) {
              this.handleEditModeFileClick(node, e);
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
              style={{
                pointerEvents: 'none',
                flex: 1,
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {node.name}
            </span>
          )}
        </div>
      );
      if (!isEditing) {
        return (
          <Balloon
            trigger={row}
            triggerType="hover"
            delay={100}
            align="tl"
            closable={false}
            popupStyle={{
              padding: '4px 8px',
              fontSize: 12,
            }}
          >
            <span style={{ whiteSpace: 'nowrap' }}>{node.name}</span>
          </Balloon>
        );
      }
      return row;
    }
  };

  renderEditModeFileContent = () => {
    const { editModeSelectedFile } = this.state;
    const values = this.field.getValues();
    const { resources } = this.state;

    if (!editModeSelectedFile) {
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
          {this.getLocaleValue('selectFileToEdit', 'Select a file to edit')}
        </div>
      );
    }

    if (editModeSelectedFile.fileType === 'skill-md') {
      const markdown = this.buildSkillMarkdown(this.buildPreviewData());
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
            <span>{editModeSelectedFile.name}</span>
          </div>
          <div style={{ flex: 1, overflow: 'hidden', padding: '16px' }}>
            <div style={{ border: '1px solid #d9d9d9', borderRadius: '4px', height: '100%' }}>
              <MonacoEditor
                language="markdown"
                width="100%"
                height="100%"
                value={values.instruction || ''}
                onChange={value => {
                  this.field.setValue('instruction', value);
                  // 更新文件树和变化状态
                  setTimeout(() => {
                    if (this.state.isEdit) {
                      this.updateEditModeFileTree();
                      // 检查是否有变化
                      const hasChanged = this.hasDataChanged();
                      if (hasChanged !== this.state.hasChanges) {
                        this.setState({ hasChanges: hasChanged });
                      }
                    }
                  }, 0);
                }}
                options={{
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
    } else if (editModeSelectedFile.fileType === 'resource') {
      const resource = editModeSelectedFile.resource;
      // 找到对应的资源索引
      const resourceIndex = resources.findIndex(r => {
        if (editModeSelectedFile.resourceKey) {
          return r.name === editModeSelectedFile.resourceKey;
        }
        return r.name === resource.name && r.type === resource.type;
      });

      if (resourceIndex === -1) {
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
            {this.getLocaleValue('resourceNotFound', 'Resource not found')}
          </div>
        );
      }

      const currentResource = resources[resourceIndex];
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
            <span>{editModeSelectedFile.name}</span>
          </div>
          <div style={{ flex: 1, overflow: 'hidden', padding: '16px' }}>
            <div style={{ border: '1px solid #d9d9d9', borderRadius: '4px', height: '100%' }}>
              <MonacoEditor
                key={`${editModeSelectedFile.resourceKey ||
                  editModeSelectedFile.name}-${getLanguageFromFileName(
                  currentResource.name || ''
                )}`}
                language={getLanguageFromFileName(currentResource.name || '')}
                width="100%"
                height="100%"
                value={currentResource.content || ''}
                onChange={value => {
                  this.handleResourceChange(resourceIndex, 'content', value);
                }}
                options={{
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
    }

    return null;
  };

  render() {
    const { locale = {} } = this.props;
    const {
      loading,
      generating,
      isEdit,
      resources,
      expandedKeys,
      showAiGenerateInput,
    } = this.state;

    const formItemLayout = {
      labelCol: { span: 3 },
      wrapperCol: { span: 20 },
    };

    // 检查是否有数据变化（使用state中的值，避免每次render都计算）
    const hasChanged = isEdit ? this.state.hasChanges : false;

    return (
      <div className="new-skill-container">
        <div
          className="page-title"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 8,
            marginTop: 8,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 28, height: 40, fontWeight: 500 }}>
              {isEdit
                ? this.getLocaleValue('editSkill', 'Edit Skill')
                : this.getLocaleValue('createSkill', 'Create Skill')}
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {!isEdit && (
                <Button type="primary" onClick={this.handleShowAiGenerate}>
                  <MagicWandIcon size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} />{' '}
                  {this.getLocaleValue('aiGenerate', 'AI生成')}
                </Button>
              )}
              {isEdit && (
                <Button onClick={this.handleShowOptimizeDialog}>
                  <MagicWandIcon size={16} style={{ marginRight: 4, verticalAlign: 'middle' }} />{' '}
                  {this.getLocaleValue('aiOptimize', 'AI 优化')}
                </Button>
              )}
              {isEdit && (
                <>
                  <Button
                    type="primary"
                    onClick={this.handleSubmit}
                    loading={loading}
                    disabled={!hasChanged}
                  >
                    {this.getLocaleValue('update', 'Update')}
                  </Button>
                  <Button warning onClick={this.handleGoBack} disabled={!hasChanged}>
                    {this.getLocaleValue('cancel', 'Cancel')}
                  </Button>
                </>
              )}
            </div>
          </div>
          {!isEdit && (
            <div>
              <Button onClick={this.handleGoBack}>{this.getLocaleValue('cancel', 'Cancel')}</Button>
            </div>
          )}
        </div>

        {isEdit ? (
          // 编辑模式：使用文件目录形式展示（与查看详情页面一致）
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
                  justifyContent: 'space-between',
                }}
              >
                <span>
                  {this.field.getValue('name') || this.getLocaleValue('projectFiles', '项目文件')}
                </span>
                <Button
                  size="small"
                  type="primary"
                  text
                  onClick={this.handleAddFileInTree}
                  style={{ padding: '0 4px' }}
                  title={this.getLocaleValue('addFile', 'Add File')}
                >
                  <Icon type="add" size="small" />
                </Button>
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
                onDrop={e => {
                  e.preventDefault();
                  e.stopPropagation();
                  const { draggingFile, resources } = this.state;
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
                    this.setState(
                      {
                        resources: newResources,
                        draggingFile: null,
                        dragOverFolder: null,
                      },
                      () => {
                        if (this.state.isEdit) {
                          this.updateEditModeFileTree();
                          const hasChanged = this.hasDataChanged();
                          if (hasChanged !== this.state.hasChanges) {
                            this.setState({ hasChanges: hasChanged });
                          }
                        }
                      }
                    );
                  }
                }}
              >
                {this.state.editModeFileTree ? (
                  this.renderFileTree(this.state.editModeFileTree, 0, '', true)
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
                    {this.getLocaleValue('noPreviewData', 'No preview data available')}
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
              {this.renderEditModeFileContent()}
            </div>
          </div>
        ) : (
          // 创建模式：使用传统表单形式
          <div
            style={{
              background: '#fff',
              padding: '20px',
              borderRadius: '4px',
              border: '1px solid #e6e6e6',
            }}
          >
            <Form field={this.field} {...formItemLayout} className="new-skill-form">
              <Form.Item
                label={this.getLocaleValue('skillName', 'Skill Name')}
                required
                validator={this.validateSkillName}
              >
                <Input
                  name="name"
                  placeholder={this.getLocaleValue(
                    'skillNamePlaceholder',
                    'Please enter Skill name (letters, underscore, hyphen only)'
                  )}
                  disabled={isEdit}
                  maxLength={255}
                  onChange={this.handleSkillNameChange}
                />
              </Form.Item>

              <Form.Item label={this.getLocaleValue('description', 'Description')}>
                <Input.TextArea
                  name="description"
                  placeholder={this.getLocaleValue(
                    'descriptionPlaceholder',
                    'Please enter Skill description'
                  )}
                  rows={3}
                  maxLength={1000}
                />
              </Form.Item>

              <Form.Item
                label={this.getLocaleValue('instruction', 'Instruction')}
                required
                validator={this.validateRequired}
              >
                <div
                  style={{ border: '1px solid #d9d9d9', borderRadius: '4px', minHeight: '400px' }}
                >
                  <MonacoEditor
                    language="markdown"
                    width="100%"
                    height={400}
                    value={this.field.getValue('instruction') || ''}
                    onChange={value => {
                      this.field.setValue('instruction', value);
                    }}
                    options={{
                      wordWrap: 'on',
                      minimap: { enabled: false },
                      lineNumbers: 'on',
                    }}
                  />
                </div>
              </Form.Item>

              <Form.Item label={this.getLocaleValue('resources', 'Resources')}>
                <div className="resources-section">
                  <Button
                    type="primary"
                    onClick={this.handleAddResource}
                    style={{ marginBottom: 16 }}
                  >
                    <Icon type="add" /> {this.getLocaleValue('addResource', 'Add Resource')}
                  </Button>

                  {resources.length > 0 && (
                    <Collapse expandedKeys={expandedKeys} onExpand={this.handleExpandChange}>
                      {resources.map((resource, index) => {
                        const isEditing = this.state.editingResourceIndex === index;
                        const displayText =
                          resource.type && resource.name
                            ? `${resource.type}/${resource.name}`
                            : `${this.getLocaleValue('resource', 'Resource')} ${index + 1}`;

                        return (
                          <Collapse.Panel
                            key={String(index)}
                            title={
                              <div className="resource-panel-header">
                                {isEditing ? (
                                  <div
                                    className="resource-title-editor"
                                    onClick={e => e.stopPropagation()}
                                    style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: '8px',
                                      flex: 1,
                                    }}
                                  >
                                    <Input
                                      size="small"
                                      value={resource.type || ''}
                                      onChange={value => {
                                        const filteredValue = value.replace(/[^a-zA-Z.-]/g, '');
                                        const newResources = [...this.state.resources];
                                        newResources[index] = {
                                          ...newResources[index],
                                          type: filteredValue,
                                        };
                                        this.setState({ resources: newResources }, () => {
                                          if (this.state.isEdit) {
                                            this.updateEditModeFileTree();
                                          }
                                        });
                                      }}
                                      placeholder={this.getLocaleValue(
                                        'resourceTypePlaceholder',
                                        'Type'
                                      )}
                                      style={{ width: '120px' }}
                                      onPressEnter={() => {
                                        this.setState({ editingResourceIndex: null });
                                      }}
                                    />
                                    <span>/</span>
                                    <Input
                                      size="small"
                                      value={resource.name || ''}
                                      onChange={value => {
                                        const filteredValue = value.replace(/[^a-zA-Z0-9._-]/g, '');
                                        const newResources = [...this.state.resources];
                                        newResources[index] = {
                                          ...newResources[index],
                                          name: filteredValue,
                                        };
                                        this.setState({ resources: newResources }, () => {
                                          if (this.state.isEdit) {
                                            this.updateEditModeFileTree();
                                          }
                                        });
                                      }}
                                      placeholder={this.getLocaleValue(
                                        'resourceNamePlaceholder',
                                        'Name'
                                      )}
                                      style={{ flex: 1 }}
                                      onPressEnter={() => {
                                        this.setState({ editingResourceIndex: null });
                                      }}
                                    />
                                    <Button
                                      text
                                      size="small"
                                      onClick={e => {
                                        e.stopPropagation();
                                        this.handleResourceTitleCancel();
                                      }}
                                    >
                                      <Icon type="close" />
                                    </Button>
                                  </div>
                                ) : (
                                  <>
                                    <span
                                      className="resource-title-text"
                                      onClick={e => this.handleResourceTitleClick(index, e)}
                                      onMouseEnter={e =>
                                        this.handleResourceTitleMouseEnter(index, e)
                                      }
                                      onMouseMove={this.handleResourceTitleMouseMove}
                                      onMouseLeave={this.handleResourceTitleMouseLeave}
                                      style={{ cursor: 'pointer', flex: 1 }}
                                    >
                                      {displayText}
                                    </span>
                                    <Button
                                      text
                                      warning
                                      onClick={e => {
                                        e.stopPropagation();
                                        this.handleRemoveResource(index);
                                      }}
                                    >
                                      <Icon type="delete" />{' '}
                                      {this.getLocaleValue('delete', 'Delete')}
                                    </Button>
                                  </>
                                )}
                              </div>
                            }
                          >
                            <Form.Item>
                              <div style={{ border: '1px solid #e6e6e6', borderRadius: '4px' }}>
                                <MonacoEditor
                                  language={getLanguageFromFileName(resource.name || '')}
                                  width="100%"
                                  height={300}
                                  value={resource.content || ''}
                                  onChange={value =>
                                    this.handleResourceChange(index, 'content', value)
                                  }
                                  options={{
                                    readOnly: false,
                                    wordWrap: 'on',
                                    minimap: { enabled: false },
                                    lineNumbers: 'on',
                                    scrollBeyondLastLine: false,
                                    theme: 'vs-dark-enhanced',
                                  }}
                                />
                              </div>
                            </Form.Item>
                          </Collapse.Panel>
                        );
                      })}
                    </Collapse>
                  )}

                  {resources.length === 0 && (
                    <div className="empty-resources">
                      {this.getLocaleValue(
                        'noResources',
                        'No resources added yet. Click "Add Resource" to add one.'
                      )}
                    </div>
                  )}
                </div>
              </Form.Item>

              <Form.Item wrapperCol={{ offset: 3, span: 20 }}>
                <Button
                  type="primary"
                  onClick={this.handleSubmit}
                  loading={loading}
                  style={{ marginRight: 10 }}
                >
                  {this.getLocaleValue('create', 'Create')}
                </Button>
              </Form.Item>
            </Form>
          </div>
        )}

        {!isEdit && (
          <Dialog
            visible={this.state.showAiGenerateDialog}
            title={this.getLocaleValue('aiGenerate', 'AI生成')}
            onClose={this.handleCloseAiGenerateDialog}
            onCancel={this.handleCloseAiGenerateDialog}
            onOk={
              this.state.generatedSkill ? this.handleApplyGeneratedSkill : this.handleGenerateSkill
            }
            okProps={{
              loading: this.state.generating,
              children: this.state.generatedSkill
                ? this.getLocaleValue('apply', 'Apply')
                : this.getLocaleValue('generateSkill', 'Generate Skill'),
            }}
            cancelProps={{
              children: this.getLocaleValue('cancel', 'Cancel'),
            }}
            style={{ width: 1000 }}
          >
            {!this.state.generatedSkill ? (
              <div>
                {/* 用户输入区域 - 生成前显示完整，生成后折叠 */}
                {!this.state.showInputCollapsed ? (
                  <div>
                    <Form.Item
                      label={this.getLocaleValue('backgroundInfo', 'Background Information')}
                    >
                      <Input.TextArea
                        value={this.state.backgroundInfo}
                        onChange={this.handleBackgroundInfoChange}
                        placeholder={this.getLocaleValue(
                          'backgroundInfoPlaceholder',
                          'Please describe what you want the Skill to do, e.g., "I need a Skill to check Nacos configuration status and provide solutions when issues are found"'
                        )}
                        rows={6}
                        maxLength={2000}
                      />
                    </Form.Item>

                    <Form.Item
                      label={this.getLocaleValue('selectMcpTools', 'Select MCP Tools (Optional)')}
                    >
                      <Loading visible={this.state.loadingMcpServers} style={{ width: '100%' }}>
                        <Select
                          placeholder={this.getLocaleValue('selectMcpServer', 'Select MCP Server')}
                          value={this.state.selectedMcpServer}
                          onChange={this.handleMcpServerChange}
                          style={{ width: '100%', marginBottom: 12 }}
                          dataSource={this.state.mcpServers.map(server => ({
                            label: server.name,
                            value: server.id || server.name,
                          }))}
                        />
                      </Loading>

                      {this.state.selectedMcpServer && (
                        <Loading visible={this.state.loadingMcpTools} style={{ width: '100%' }}>
                          <Input
                            placeholder={this.getLocaleValue(
                              'searchTools',
                              'Search tools by name...'
                            )}
                            value={this.state.mcpToolSearchKeyword}
                            onChange={this.handleMcpToolSearchChange}
                            style={{ width: '100%', marginBottom: 12 }}
                            hasClear
                          />
                          <div
                            style={{
                              border: '1px solid #e6e6e6',
                              borderRadius: 4,
                              padding: 12,
                              maxHeight: 200,
                              overflowY: 'auto',
                              background: '#fafafa',
                            }}
                          >
                            {this.getFilteredMcpTools().length > 0 ? (
                              this.getFilteredMcpTools().map((tool, index) => (
                                <Checkbox
                                  key={index}
                                  checked={this.state.selectedMcpTools.some(
                                    t => t.name === tool.name
                                  )}
                                  onChange={checked => this.handleMcpToolChange(checked, tool)}
                                  style={{ display: 'block', marginBottom: 8 }}
                                >
                                  <div>
                                    <strong>{tool.name}</strong>
                                    {tool.description && (
                                      <div
                                        style={{ fontSize: '12px', color: '#666', marginTop: 4 }}
                                      >
                                        {tool.description}
                                      </div>
                                    )}
                                  </div>
                                </Checkbox>
                              ))
                            ) : (
                              <div style={{ color: '#999', fontSize: '12px' }}>
                                {this.state.mcpToolSearchKeyword.trim()
                                  ? this.getLocaleValue(
                                      'noToolsFound',
                                      'No tools found matching your search'
                                    )
                                  : this.getLocaleValue(
                                      'noToolsAvailable',
                                      'No tools available in this MCP server'
                                    )}
                              </div>
                            )}
                          </div>
                        </Loading>
                      )}
                    </Form.Item>

                    <Form.Item
                      label={this.getLocaleValue(
                        'conversationHistory',
                        'Conversation History (Optional)'
                      )}
                    >
                      <div style={{ marginBottom: 8 }}>
                        <Button
                          text
                          size="small"
                          onClick={() =>
                            this.setState({
                              showConversationHistory: !this.state.showConversationHistory,
                            })
                          }
                        >
                          {this.state.showConversationHistory
                            ? this.getLocaleValue(
                                'hideConversationHistory',
                                'Hide Conversation History'
                              )
                            : this.getLocaleValue(
                                'showConversationHistory',
                                'Show Conversation History'
                              )}
                          <Icon
                            type={this.state.showConversationHistory ? 'arrow-up' : 'arrow-down'}
                            style={{ marginLeft: 4 }}
                          />
                        </Button>
                      </div>
                      {this.state.showConversationHistory && (
                        <div>
                          <Input.TextArea
                            value={this.state.conversationHistoryJson}
                            onChange={value => this.setState({ conversationHistoryJson: value })}
                            placeholder={this.getLocaleValue(
                              'conversationHistoryPlaceholder',
                              'Enter conversation history in JSON format:\n{\n  "title": "Conversation Title",\n  "context": "Context description",\n  "messages": [\n    {\n      "type": "user",\n      "content": "User input"\n    },\n    {\n      "type": "tool_call",\n      "toolName": "tool_name",\n      "toolInput": {"param": "value"},\n      "toolOutput": "result"\n    },\n    {\n      "type": "model",\n      "content": "Model response"\n    }\n  ]\n}'
                            )}
                            rows={12}
                            style={{ fontFamily: 'monospace', fontSize: '12px' }}
                          />
                          <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
                            {this.getLocaleValue(
                              'conversationHistoryHint',
                              'The system will analyze the conversation history to determine if it is suitable for skill generation. Include user inputs, tool calls, and model responses.'
                            )}
                          </div>
                        </div>
                      )}
                    </Form.Item>

                    <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
                      {this.getLocaleValue(
                        'generateHint',
                        'Enter background information and click to generate a Skill based on best practices'
                      )}
                    </div>
                  </div>
                ) : (
                  this.renderUserInput()
                )}

                {/* 结果解析中提示 */}
                {this.state.parsingResult && (
                  <div style={{ marginTop: 16, textAlign: 'center', padding: '20px' }}>
                    <Loading
                      visible={true}
                      tip={this.getLocaleValue('parsingResult', 'Parsing result...')}
                    />
                  </div>
                )}

                {/* 流式内容 - 思考内容和结果内容 */}
                {(this.state.streaming || this.state.thinkingContent || this.state.streamContent) &&
                  this.renderStreamContent()}
              </div>
            ) : (
              this.renderGeneratedSkill()
            )}

            {/* Tooltip for user input */}
            {this.state.inputTooltipVisible && (
              <div
                style={{
                  position: 'fixed',
                  left: `${this.state.inputTooltipPosition.x}px`,
                  top: `${this.state.inputTooltipPosition.y - 10}px`,
                  transform: 'translate(-50%, -100%)',
                  background: '#333',
                  color: '#fff',
                  padding: '12px 16px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  maxWidth: '500px',
                  maxHeight: '400px',
                  overflowY: 'auto',
                  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
                  zIndex: 9999,
                  pointerEvents: 'none',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {(() => {
                  const { backgroundInfo, selectedMcpTools } = this.state;
                  const selectedToolsText =
                    selectedMcpTools.length > 0
                      ? selectedMcpTools.map(t => t.name).join(', ')
                      : this.getLocaleValue('noToolsSelected', 'No tools selected');
                  return `${this.getLocaleValue(
                    'backgroundInfo',
                    'Background Information'
                  )}: ${backgroundInfo}\n\n${this.getLocaleValue(
                    'selectedTools',
                    'Selected Tools'
                  )}: ${selectedToolsText}`;
                })()}
              </div>
            )}

            {/* Tooltip for thinking content */}
            {this.state.thinkingTooltipVisible && this.state.thinkingContent && (
              <div
                style={{
                  position: 'fixed',
                  left: `${this.state.thinkingTooltipPosition.x}px`,
                  top: `${this.state.thinkingTooltipPosition.y - 10}px`,
                  transform: 'translate(-50%, -100%)',
                  background: '#333',
                  color: '#fff',
                  padding: '12px 16px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  maxWidth: '500px',
                  maxHeight: '400px',
                  overflowY: 'auto',
                  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
                  zIndex: 9999,
                  pointerEvents: 'none',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {this.state.thinkingContent}
              </div>
            )}
          </Dialog>
        )}

        {isEdit && (
          <SkillOptimizeDialog
            visible={this.state.optimizeDialogVisible}
            skill={this.state.currentSkillData}
            fileTree={this.state.editModeFileTree}
            selectedFile={this.state.editModeSelectedFile}
            onClose={this.handleOptimizeDialogClose}
            onSuccess={this.handleOptimizeSuccess}
            locale={this.props.locale}
            history={this.props.history}
          />
        )}

        {/* Custom Tooltip for resource title - follows mouse position */}
        {this.state.tooltipVisible && this.state.tooltipResourceIndex !== null && (
          <div
            style={{
              position: 'fixed',
              left: `${this.state.tooltipPosition.x}px`,
              top: `${this.state.tooltipPosition.y - 10}px`,
              transform: 'translate(-50%, -100%)',
              background: '#333',
              color: '#fff',
              padding: '8px 12px',
              borderRadius: '4px',
              fontSize: '12px',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
              zIndex: 9999,
              pointerEvents: 'none',
              whiteSpace: 'nowrap',
            }}
          >
            {this.getLocaleValue('clickToEdit', '点击进行编辑')}
          </div>
        )}
      </div>
    );
  }
}

export default NewSkill;
