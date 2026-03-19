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
  Dialog,
  Field,
  Form,
  Input,
  Message,
  Tag,
  Collapse,
  Grid,
  Card,
  Select,
  Checkbox,
  Loading,
  Icon,
  Balloon,
} from '@alifd/next';
import { request, getParams } from '@/globalLib';
import './SkillOptimizeDialog.scss';

const { Row, Col } = Grid;

class SkillOptimizeDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    skill: PropTypes.object,
    selectedFile: PropTypes.object, // 当前选中的文件
    fileTree: PropTypes.array, // 文件树结构
    onClose: PropTypes.func,
    onSuccess: PropTypes.func,
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.eventSource = null;
    this.state = {
      loading: false,
      streaming: false,
      streamContent: '',
      thinkingContent: '',
      streamType: null, // THINKING, TOOL_CALL, CONTENT, DONE
      optimizedSkill: null,
      changes: [],
      qualityScore: null,
      explanation: '',
      error: null,
      showComparison: false,
      mcpServers: [], // MCP服务器列表
      selectedMcpServer: null, // 选中的MCP服务器
      mcpTools: [], // MCP工具列表
      selectedMcpTools: [], // 选中的MCP工具
      loadingMcpServers: false, // 加载MCP服务器列表
      loadingMcpTools: false, // 加载MCP工具列表
      mcpToolSearchKeyword: '', // MCP工具搜索关键词
      inputSectionCollapsed: false, // 输入区域是否折叠
      thinkingCollapsed: false, // 思考内容是否折叠
      resultContentCollapsed: false, // 结果内容是否折叠
    };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.visible !== this.props.visible) {
      if (this.props.visible) {
        // Dialog opened, load MCP servers
        this.loadMcpServers();
      } else {
        // Dialog closed, cleanup
        this.cleanup();
      }
    }
  }

  componentWillUnmount() {
    this.cleanup();
  }

  cleanup = () => {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    this.setState({
      loading: false,
      streaming: false,
      streamContent: '',
      thinkingContent: '',
      streamType: null,
      optimizedSkill: null,
      explanation: '',
      error: null,
      thinkingCollapsed: false,
      inputSectionCollapsed: false,
      resultContentCollapsed: false,
      selectedMcpServer: null,
      mcpTools: [],
      selectedMcpTools: [],
      mcpToolSearchKeyword: '',
      conversationHistory: null, // 对话历史
      conversationHistoryJson: '', // 对话历史 JSON 字符串（用于编辑）
      showConversationHistory: false, // 是否显示对话历史输入
      selectedTargetFile: null, // 重置选中的目标文件
    });
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
      // eslint-disable-next-line no-console
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
      // eslint-disable-next-line no-console
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

  // 构建文件列表（扁平化 fileTree）
  buildFileList = fileTree => {
    if (!fileTree || !Array.isArray(fileTree)) {
      return [];
    }

    const fileList = [];

    const traverse = nodes => {
      if (!nodes) {
        return;
      }

      if (Array.isArray(nodes)) {
        nodes.forEach(node => traverse(node));
      } else {
        const node = nodes;
        if (node.type === 'file') {
          // 构建文件标识符
          let fileIdentifier = node.name;
          if (node.fileType === 'resource' && node.resourceKey) {
            fileIdentifier = node.resourceKey;
          }
          fileList.push({
            name: node.name,
            fileType: node.fileType,
            resourceKey: node.resourceKey,
            identifier: fileIdentifier, // 用于标识文件的唯一值
            displayName: node.name, // 显示名称
          });
        } else if (node.type === 'folder' && node.children) {
          // 递归处理文件夹中的文件
          traverse(node.children);
        }
      }
    };

    traverse(fileTree);
    return fileList;
  };

  handleTargetFileChange = value => {
    const { fileTree } = this.props;
    const fileList = this.buildFileList(fileTree);
    const selectedFile = fileList.find(f => f.identifier === value);
    this.setState({ selectedTargetFile: selectedFile || null });
  };

  handleOptimize = () => {
    const { skill, locale = {}, fileTree, selectedFile } = this.props;
    const optimizationGoal = this.field.getValue('optimizationGoal') || '';

    if (!skill) {
      Message.error('Skill data is required');
      return;
    }

    // 必须选择一个文件才能优化
    const fileList = fileTree ? this.buildFileList(fileTree) : [];
    let targetFile = this.state.selectedTargetFile;

    // 如果没有手动选择，使用默认选中的文件
    if (!targetFile && selectedFile) {
      let defaultIdentifier = '';
      if (selectedFile.fileType === 'resource' && selectedFile.resourceKey) {
        defaultIdentifier = selectedFile.resourceKey;
      } else if (selectedFile.name) {
        defaultIdentifier = selectedFile.name;
      }
      if (defaultIdentifier) {
        targetFile = fileList.find(f => f.identifier === defaultIdentifier);
      }
    }

    if (!targetFile) {
      Message.error(locale.selectTargetFileRequired || '请先选择一个文件进行优化');
      return;
    }

    this.setState({
      loading: true,
      streaming: true,
      streamContent: '',
      thinkingContent: '',
      streamType: null,
      optimizedSkill: null,
      explanation: '',
      error: null,
      thinkingCollapsed: false,
      inputSectionCollapsed: true, // 折叠输入区域
      resultContentCollapsed: false,
    });

    // Parse conversation history if provided
    let conversationHistory = null;
    if (this.state.conversationHistoryJson && this.state.conversationHistoryJson.trim()) {
      try {
        conversationHistory = JSON.parse(this.state.conversationHistoryJson);
      } catch (e) {
        Message.error(
          locale.conversationHistoryInvalid || 'Invalid conversation history JSON format'
        );
        this.setState({ loading: false, streaming: false });
        return;
      }
    } else if (this.state.conversationHistory) {
      conversationHistory = this.state.conversationHistory;
    }

    // Build request payload
    const payload = {
      skill,
      optimizationGoal,
      selectedMcpTools: this.state.selectedMcpTools.map(tool => ({
        name: tool.name,
        description: tool.description,
        inputSchema: tool.inputSchema,
      })),
      targetFileName: targetFile.identifier, // 必须指定文件
    };

    if (conversationHistory) {
      payload.conversationHistory = conversationHistory;
    }

    // Use EventSource for SSE
    const ctxPath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    const url = `${window.location.origin}${ctxPath}v3/console/copilot/skill/optimize`;
    const token = localStorage.getItem('token');

    // Create EventSource with POST support (using fetch + ReadableStream)
    this.startSSEStream(url, payload, token);
  };

  startSSEStream = (url, payload, token) => {
    let accessToken = '';
    try {
      const tokenObj = JSON.parse(token);
      accessToken = tokenObj.accessToken || '';
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('Failed to parse token:', e);
    }

    // Use fetch API for POST request with SSE
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
        let currentEventType = 'message'; // Default event type
        let pendingData = null; // Store data when event type comes after data

        const readStream = () => {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                // When stream ends, try to parse optimizedSkill from accumulated content
                // if not already set
                this.setState(prevState => {
                  if (!prevState.optimizedSkill && prevState.streamContent) {
                    let parsedSkill = this.parseOptimizedSkillFromContent(prevState.streamContent);
                    // Filter out SKILL.md from resources
                    if (parsedSkill) {
                      parsedSkill = this.filterSkillMdFromResources(parsedSkill);
                    }
                    if (parsedSkill) {
                      return {
                        streaming: false,
                        loading: false,
                        optimizedSkill: parsedSkill,
                        thinkingCollapsed: true,
                        resultContentCollapsed: true,
                        inputSectionCollapsed: true,
                      };
                    }
                  }
                  return {
                    streaming: false,
                    loading: false,
                  };
                });
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
                    const { locale = {} } = this.props;
                    const errorMessage =
                      pendingData.explanation ||
                      pendingData.message ||
                      locale.optimizeFailed ||
                      '优化失败';
                    Message.error(errorMessage);
                    this.setState({
                      streaming: false,
                      loading: false,
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
                        const { locale = {} } = this.props;
                        const errorMessage =
                          data.explanation || data.message || locale.optimizeFailed || '优化失败';
                        Message.error(errorMessage);
                        this.setState({
                          streaming: false,
                          loading: false,
                          error: errorMessage,
                        });
                      } else if (data.done && data.explanation && !data.optimizedSkill) {
                        // Handle error case: done=true but no optimizedSkill
                        const { locale = {} } = this.props;
                        const errorMessage =
                          data.explanation || data.message || locale.optimizeFailed || '优化失败';
                        Message.error(errorMessage);
                        this.setState({
                          streaming: false,
                          loading: false,
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
                streaming: false,
                loading: false,
                error: error.message || 'Stream read failed',
              });
            });
        };

        readStream();
      })
      .catch(error => {
        this.setState({
          streaming: false,
          loading: false,
          error: error.message || 'Request failed',
        });
      });
  };

  // Helper method to parse optimizedSkill from accumulated content
  /**
   * Filter out SKILL.md from resources
   * @param {Object} skill - Skill object to filter
   * @returns {Object} Filtered skill object
   */
  filterSkillMdFromResources = skill => {
    if (!skill || !skill.resource) {
      return skill;
    }

    const filteredResource = {};
    let hasFiltered = false;

    // Filter out SKILL.md resources
    Object.keys(skill.resource).forEach(key => {
      const resource = skill.resource[key];
      // Check if resource name or key is SKILL.md
      const resourceName = resource?.name || '';
      const resourceKey = key || '';

      // Skip if name or key contains SKILL.md (case-insensitive)
      if (
        resourceName.toUpperCase() === 'SKILL.MD' ||
        resourceKey.toUpperCase() === 'SKILL.MD' ||
        resourceName.toUpperCase().includes('SKILL.MD') ||
        resourceKey.toUpperCase().includes('SKILL.MD')
      ) {
        hasFiltered = true;
        // eslint-disable-next-line no-console
        console.warn('Filtered out SKILL.md resource:', { key, resourceName });
        return;
      }

      filteredResource[key] = resource;
    });

    if (hasFiltered) {
      return {
        ...skill,
        resource: filteredResource,
      };
    }

    return skill;
  };

  parseOptimizedSkillFromContent = content => {
    if (!content || !content.trim()) {
      return null;
    }

    try {
      let jsonContent = content.trim();

      // Try to parse directly first
      let parsed;
      try {
        parsed = JSON.parse(jsonContent);
      } catch (e) {
        // If direct parse fails, try to extract JSON object by finding first { and matching }
        const startIdx = jsonContent.indexOf('{');
        if (startIdx >= 0) {
          let braceCount = 0;
          let endIdx = -1;
          let inString = false;
          let escapeNext = false;

          for (let i = startIdx; i < jsonContent.length; i++) {
            const char = jsonContent[i];

            if (escapeNext) {
              escapeNext = false;
              continue;
            }

            if (char === '\\') {
              escapeNext = true;
              continue;
            }

            if (char === '"') {
              inString = !inString;
              continue;
            }

            if (!inString) {
              if (char === '{') {
                braceCount++;
              } else if (char === '}') {
                braceCount--;
                if (braceCount === 0) {
                  endIdx = i;
                  break;
                }
              }
            }
          }

          if (endIdx > startIdx) {
            jsonContent = jsonContent.substring(startIdx, endIdx + 1);
            parsed = JSON.parse(jsonContent);
          } else {
            throw e;
          }
        } else {
          throw e;
        }
      }

      // Extract optimizedSkill from parsed object
      let skillData = null;
      if (parsed.optimizedSkill) {
        skillData = parsed.optimizedSkill;
      } else if (parsed.skill) {
        skillData = parsed.skill;
      } else if (parsed.name && (parsed.description || parsed.instruction)) {
        skillData = parsed;
      }

      // Filter out SKILL.md from resources before returning
      if (skillData) {
        skillData = this.filterSkillMdFromResources(skillData);
      }

      return skillData;
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('Failed to parse optimizedSkill from content:', e);
    }

    return null;
  };

  handleSSEMessage = data => {
    const { type, chunk, done, optimizedSkill } = data;
    const typeStr = type?.code || type || 'CONTENT';

    if (typeStr === 'THINKING' || type === 'THINKING') {
      // Accumulate thinking content separately
      this.setState(prevState => ({
        thinkingContent: prevState.thinkingContent + (chunk || ''),
        streamType: 'THINKING',
        thinkingCollapsed: false, // 展开思考内容
      }));
    } else if (typeStr === 'TOOL_CALL' || type === 'TOOL_CALL') {
      // Accumulate tool call content
      this.setState(prevState => ({
        streamContent: prevState.streamContent + (chunk || ''),
        streamType: 'TOOL_CALL',
        thinkingCollapsed: true, // 折叠思考内容，开始显示结果
        resultContentCollapsed: false, // 展开结果内容
      }));
    } else if (typeStr === 'CONTENT' || type === 'CONTENT') {
      // Accumulate general content
      this.setState(prevState => ({
        streamContent: prevState.streamContent + (chunk || ''),
        streamType: 'CONTENT',
        thinkingCollapsed: true, // 折叠思考内容
        resultContentCollapsed: false, // 展开结果内容
      }));
    } else if (typeStr === 'DONE' || type === 'DONE' || done) {
      // Final result - collapse all streaming sections and show comparison
      // First, accumulate any chunk from DONE event to streamContent
      this.setState(prevState => {
        // Accumulate chunk if present in DONE event
        const accumulatedContent = prevState.streamContent + (chunk || '');

        // Support multiple field names: optimizedSkill, skill
        let skillData = optimizedSkill || data.skill || data.optimizedSkill || null;

        // If optimizedSkill is not in DONE event, try to parse it from accumulated streamContent
        if (!skillData && accumulatedContent) {
          skillData = this.parseOptimizedSkillFromContent(accumulatedContent);
          if (skillData) {
            // eslint-disable-next-line no-console
            console.log('Successfully parsed optimizedSkill from accumulated content');
          } else {
            // eslint-disable-next-line no-console
            console.warn(
              'Failed to parse optimizedSkill from accumulated content, length:',
              accumulatedContent.length
            );
          }
        }

        // Filter out SKILL.md from resources (even if skillData came from DONE event)
        if (skillData) {
          skillData = this.filterSkillMdFromResources(skillData);
        }

        // Debug: log the received data
        // eslint-disable-next-line no-console
        console.log('DONE event received:', {
          typeStr,
          done,
          skillData,
          data,
          streamContent: accumulatedContent,
        });

        if (!skillData) {
          // eslint-disable-next-line no-console
          console.warn(
            'No optimizedSkill found in DONE event or streamContent:',
            data,
            'Accumulated content:',
            accumulatedContent
          );
        }

        return {
          streaming: false,
          loading: false,
          streamContent: accumulatedContent,
          optimizedSkill: skillData,
          thinkingCollapsed: true, // 折叠思考内容
          resultContentCollapsed: true, // 折叠结果内容，显示对比表单
          inputSectionCollapsed: true, // 保持输入区域折叠状态
        };
      });
    } else if (typeStr === 'error' || type === 'error') {
      // Error case
      this.setState({
        streaming: false,
        loading: false,
        error: data.explanation || data.message || 'Optimization failed',
      });
    }
  };

  handleApply = () => {
    const { skill, onSuccess, locale = {}, history } = this.props;
    const { optimizedSkill } = this.state;

    if (!optimizedSkill) {
      Message.error(locale.noOptimizedSkill || 'No optimized skill data');
      return;
    }

    if (!skill) {
      Message.error('Skill data is required');
      return;
    }

    // Merge optimized fields with original skill (preserve namespaceId, etc.)
    // Use optimized resources instead of original resources
    // Important: Keep original skill name, don't use optimized name
    const mergedSkill = {
      ...skill,
      name: skill.name, // Always use original skill name
      description: optimizedSkill.description || skill.description,
      instruction: optimizedSkill.instruction || skill.instruction,
      // Use optimized resources
      resource: optimizedSkill.resource || optimizedSkill.resources || {},
    };

    // Priority: If onSuccess is provided, call it directly (for in-page optimization)
    // This allows updating the current page without navigation
    // This is especially important when already on the edit page
    if (onSuccess) {
      onSuccess(mergedSkill);
      this.handleClose();
      return;
    }

    // If history is provided but no onSuccess, navigate to edit page (for detail page)
    // This allows jumping to edit page and auto-filling the form
    if (history) {
      // Store optimized skill data to localStorage for editing page to pick up
      const namespaceId = skill.namespaceId || getParams('namespace') || 'public';

      // 获取被优化的文件名（targetFileName）
      const { fileTree, selectedFile } = this.props;
      let targetFileName = null;
      if (this.state.selectedTargetFile) {
        targetFileName = this.state.selectedTargetFile.identifier;
      } else if (selectedFile) {
        if (selectedFile.fileType === 'resource' && selectedFile.resourceKey) {
          targetFileName = selectedFile.resourceKey;
        } else if (selectedFile.name) {
          targetFileName = selectedFile.name;
        }
      }

      const optimizedSkillData = {
        ...mergedSkill,
        namespaceId, // Ensure namespaceId is included
        optimized: true, // Flag to indicate this is from optimization
        targetFileName, // 存储被优化的文件名，用于在编辑页面自动选中
      };
      localStorage.setItem('nacos_optimized_skill', JSON.stringify(optimizedSkillData));

      const skillName = mergedSkill.name || skill.name;
      const editUrl = `/newSkill?namespace=${namespaceId}&name=${encodeURIComponent(
        skillName
      )}&mode=edit&optimized=true`;
      history.push(editUrl);
      this.handleClose();
      return;
    }

    // Fallback: if no history and no onSuccess, use the old behavior (direct update)
    this.handleApplyDirect(mergedSkill, locale, onSuccess);
  };

  handleApplyDirect = (mergedSkill, locale, onSuccess) => {
    // Build skillCard object
    const skillCard = {
      name: mergedSkill.name,
      description: mergedSkill.description || '',
      instruction: mergedSkill.instruction || '',
    };

    // Use optimized resources (may be empty or have removed resources)
    if (mergedSkill.resource && Object.keys(mergedSkill.resource).length > 0) {
      skillCard.resource = mergedSkill.resource;
    } else {
      skillCard.resource = {};
    }

    // Prepare request data
    const namespaceId = mergedSkill.namespaceId || getParams('namespace') || 'public';
    const requestData = {
      namespaceId,
      skillName: mergedSkill.name,
      skillCard: JSON.stringify(skillCard),
    };

    this.setState({ loading: true });

    request({
      url: 'v3/console/ai/skills',
      method: 'PUT',
      data: requestData,
      success: data => {
        this.setState({ loading: false });
        if (data && data.code === 0) {
          Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
          if (onSuccess) {
            onSuccess(mergedSkill);
          }
          this.handleClose();
        } else {
          Message.error(data?.message || locale.optimizeFailed || 'Failed to apply optimization');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(locale.optimizeFailed || 'Failed to apply optimization');
      },
    });
  };

  handleClose = () => {
    this.cleanup();
    if (this.props.onClose) {
      this.props.onClose();
    }
  };

  getStreamTypeLabel = type => {
    const labels = {
      THINKING: '思考中',
      TOOL_CALL: '工具调用',
      CONTENT: '生成内容',
      DONE: '完成',
    };
    return labels[type] || type;
  };

  // 检查内容是否完全是 JSON（用于判断是否应该显示原始内容）
  // 更严谨的判断：内容必须完全是有效的 JSON，且包含 optimizedSkill 字段
  isPureJsonContent = content => {
    if (!content || !content.trim()) {
      return false;
    }
    try {
      const trimmed = content.trim();
      let jsonContent = null;

      // 1. 检查是否在 markdown 代码块中
      if (trimmed.includes('```json')) {
        const start = trimmed.indexOf('```json') + 7;
        const end = trimmed.indexOf('```', start);
        if (end > start) {
          jsonContent = trimmed.substring(start, end).trim();
        }
      } else if (trimmed.includes('```')) {
        const start = trimmed.indexOf('```') + 3;
        const end = trimmed.indexOf('```', start);
        if (end > start) {
          jsonContent = trimmed.substring(start, end).trim();
        }
      } else if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
        // 2. 检查是否直接是 JSON（没有代码块包装）
        // 需要确保整个内容都是 JSON，没有其他文本
        // 通过检查是否以 { 或 [ 开头，且以 } 或 ] 结尾
        if (
          (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
          (trimmed.startsWith('[') && trimmed.endsWith(']'))
        ) {
          jsonContent = trimmed;
        } else {
          // 如果只是部分 JSON，不算纯 JSON
          return false;
        }
      } else {
        // 3. 如果既不在代码块中，也不是直接的 JSON，则不是纯 JSON
        return false;
      }

      // 4. 尝试解析 JSON
      if (!jsonContent) {
        return false;
      }

      const parsed = JSON.parse(jsonContent);

      // 5. 检查是否包含 optimizedSkill 字段（这是我们关心的关键字段）
      // 如果包含 optimizedSkill，说明这是完整的优化结果，不需要显示原始内容
      if (parsed && typeof parsed === 'object' && 'optimizedSkill' in parsed) {
        return true;
      }

      // 6. 如果解析出的对象本身看起来像 Skill 对象（有 name, description, instruction 等字段）
      // 也可以认为是纯 JSON 内容
      if (
        parsed &&
        typeof parsed === 'object' &&
        ('name' in parsed || 'description' in parsed || 'instruction' in parsed)
      ) {
        return true;
      }

      return false;
    } catch (e) {
      // JSON 解析失败，不是有效的 JSON
      return false;
    }
  };

  getStreamTypeColor = type => {
    const colors = {
      THINKING: 'primary',
      TOOL_CALL: 'primary',
      CONTENT: 'primary',
      DONE: 'primary',
    };
    return colors[type] || 'normal';
  };

  // 获取文本的前N行预览
  getPreviewLines = (text, lines = 2) => {
    if (!text) return '';
    const textLines = text.split('\n');
    if (textLines.length <= lines) return text;
    return `${textLines.slice(0, lines).join('\n')}\n...`;
  };

  // 渲染可折叠内容区域
  renderCollapsibleSection = (
    title,
    content,
    isCollapsed,
    onToggle,
    icon = null,
    loading = false,
    thinkingContentForIcon = null // 新增参数：用于显示思考内容的图标
  ) => {
    if (!content && !loading) {
      return null;
    }

    const contentLines = content ? content.split('\n') : [];
    const previewContent = isCollapsed && content ? this.getPreviewLines(content, 2) : content;
    const hasMore = isCollapsed && contentLines.length > 2;
    const { locale = {} } = this.props;

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
                  e.stopPropagation(); // 阻止触发折叠/展开
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
                      {locale.thinking || 'Thinking'}
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

  renderInputSection = () => {
    const { locale = {}, fileTree, selectedFile } = this.props;
    const { inputSectionCollapsed, loading, streaming, selectedTargetFile } = this.state;

    // 构建文件列表（如果 fileTree 不存在，返回空数组）
    const fileList = fileTree ? this.buildFileList(fileTree) : [];
    const hasFiles = fileList.length > 0;

    // 调试信息：检查 fileTree 和 fileList
    if (this.props.visible && !hasFiles && fileTree) {
      // eslint-disable-next-line no-console
      console.warn('File tree exists but fileList is empty:', { fileTree, fileList });
    }

    // 计算当前下拉框选中的文件标识：
    // 1. 如果用户在弹窗中手动选择过文件，优先使用 state 中的 selectedTargetFile
    // 2. 否则使用详情页当前选中的文件（selectedFile）
    let defaultIdentifier = '';
    if (selectedFile) {
      if (selectedFile.fileType === 'resource' && selectedFile.resourceKey) {
        defaultIdentifier = selectedFile.resourceKey;
      } else if (selectedFile.name) {
        defaultIdentifier = selectedFile.name;
      }
    }
    const currentIdentifier =
      (selectedTargetFile && selectedTargetFile.identifier) || defaultIdentifier || '';

    const inputContent = (
      <div>
        {/* 文件选择（只要有文件就显示） */}
        {hasFiles && (
          <Form.Item label={locale.selectTargetFile || '选择优化文件'}>
            <Select
              placeholder={locale.selectTargetFilePlaceholder || '请选择需要优化的文件'}
              value={currentIdentifier}
              onChange={this.handleTargetFileChange}
              style={{ width: '100%' }}
              dataSource={fileList.map(file => ({
                label: file.displayName,
                value: file.identifier,
              }))}
              disabled={streaming || loading}
            />
            <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
              {locale.selectTargetFileHint || '请选择一个文件进行优化'}
            </div>
          </Form.Item>
        )}

        <Form.Item label={locale.optimizationGoal || 'Optimization Goal'}>
          <Input.TextArea
            value={this.field.getValue('optimizationGoal')}
            onChange={value => this.field.setValue('optimizationGoal', value)}
            placeholder={
              locale.optimizationGoalPlaceholder ||
              'Enter optimization goal (optional), e.g., improve instruction clarity, add error handling, add resource templates'
            }
            rows={6}
            maxLength={2000}
            disabled={streaming || loading}
          />
        </Form.Item>

        <Form.Item label={locale.selectMcpTools || 'Select MCP Tools (Optional)'}>
          <Loading visible={this.state.loadingMcpServers} style={{ width: '100%' }}>
            <Select
              placeholder={locale.selectMcpServer || 'Select MCP Server'}
              value={this.state.selectedMcpServer}
              onChange={this.handleMcpServerChange}
              style={{ width: '100%', marginBottom: 12 }}
              dataSource={this.state.mcpServers.map(server => ({
                label: server.name,
                value: server.id || server.name,
              }))}
              disabled={streaming || loading}
            />
          </Loading>

          {this.state.selectedMcpServer && (
            <Loading visible={this.state.loadingMcpTools} style={{ width: '100%' }}>
              <Input
                placeholder={locale.searchTools || 'Search tools by name...'}
                value={this.state.mcpToolSearchKeyword}
                onChange={this.handleMcpToolSearchChange}
                style={{ width: '100%', marginBottom: 12 }}
                hasClear
                disabled={streaming || loading}
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
                      checked={this.state.selectedMcpTools.some(t => t.name === tool.name)}
                      onChange={checked => this.handleMcpToolChange(checked, tool)}
                      style={{ display: 'block', marginBottom: 8 }}
                      disabled={streaming || loading}
                    >
                      <div>
                        <strong>{tool.name}</strong>
                        {tool.description && (
                          <div style={{ fontSize: '12px', color: '#666', marginTop: 4 }}>
                            {tool.description}
                          </div>
                        )}
                      </div>
                    </Checkbox>
                  ))
                ) : (
                  <div style={{ color: '#999', fontSize: '12px' }}>
                    {this.state.mcpToolSearchKeyword.trim()
                      ? locale.noToolsFound || 'No tools found matching your search'
                      : locale.noToolsAvailable || 'No tools available in this MCP server'}
                  </div>
                )}
              </div>
            </Loading>
          )}
        </Form.Item>

        <Form.Item label={locale.conversationHistory || 'Conversation History (Optional)'}>
          <div style={{ marginBottom: 8 }}>
            <Button
              text
              size="small"
              onClick={() => {
                this.setState({ showConversationHistory: !this.state.showConversationHistory });
              }}
              disabled={streaming || loading}
            >
              {this.state.showConversationHistory
                ? locale.hideConversationHistory || 'Hide Conversation History'
                : locale.showConversationHistory || 'Show Conversation History'}
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
                placeholder={
                  locale.conversationHistoryPlaceholder ||
                  `Enter conversation history in JSON format:
{
  "title": "Conversation Title",
  "context": "Context description",
  "messages": [
    {
      "type": "user",
      "content": "User input"
    },
    {
      "type": "tool_call",
      "toolName": "tool_name",
      "toolInput": {"param": "value"},
      "toolOutput": "result"
    },
    {
      "type": "model",
      "content": "Model response"
    }
  ]
}`
                }
                rows={12}
                style={{ fontFamily: 'monospace', fontSize: '12px' }}
                disabled={streaming || loading}
              />
              <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
                {locale.conversationHistoryHint ||
                  'The system will analyze the conversation history to determine if it is suitable for skill optimization. Include user inputs, tool calls, and model responses.'}
              </div>
            </div>
          )}
        </Form.Item>
      </div>
    );

    if (inputSectionCollapsed) {
      const optimizationGoal = this.field.getValue('optimizationGoal') || '';
      const selectedToolsCount = this.state.selectedMcpTools.length;
      let previewText = '';
      if (optimizationGoal) {
        previewText = optimizationGoal;
      } else if (selectedToolsCount > 0) {
        previewText = `${selectedToolsCount} MCP tools selected`;
      } else {
        previewText = 'No optimization goal';
      }

      return this.renderCollapsibleSection(
        locale.optimizationGoal || 'Optimization Goal',
        previewText,
        true,
        () => this.setState({ inputSectionCollapsed: false }),
        loading || streaming ? <Loading size="medium" style={{ marginRight: 4 }} /> : null,
        loading || streaming
      );
    }

    return inputContent;
  };

  renderStreamContent = () => {
    const {
      streamContent,
      thinkingContent,
      streamType,
      thinkingCollapsed,
      resultContentCollapsed,
      loading,
      streaming,
    } = this.state;
    const { locale = {} } = this.props;

    return (
      <div>
        {/* 思考内容区域 - 仅在 THINKING 阶段显示 */}
        {thinkingContent &&
          streamType === 'THINKING' &&
          this.renderCollapsibleSection(
            locale.thinking || 'Thinking',
            thinkingContent,
            thinkingCollapsed,
            () => this.setState({ thinkingCollapsed: !thinkingCollapsed }),
            <Tag type="primary" size="small">
              {locale.thinking || 'Thinking'}
            </Tag>,
            streaming && streamType === 'THINKING' && !thinkingContent
          )}

        {/* 结果内容区域 - 当开始接收模型回复时显示，包含思考内容图标 */}
        {streamContent &&
          streamType !== 'THINKING' &&
          this.renderCollapsibleSection(
            locale.generatingContent || '生成内容',
            streamContent,
            resultContentCollapsed,
            () => this.setState({ resultContentCollapsed: !resultContentCollapsed }),
            <Tag type="primary" size="small">
              {locale.generatingContent || '生成内容'}
            </Tag>,
            streaming && !streamContent,
            this.state.thinkingContent // 直接使用 state 中的 thinkingContent
          )}
      </div>
    );
  };

  renderOptimizedPreview = () => {
    const { skill, locale = {} } = this.props;
    const {
      optimizedSkill,
      thinkingContent,
      streamContent,
      thinkingCollapsed,
      resultContentCollapsed,
    } = this.state;

    // Debug: log render state
    // eslint-disable-next-line no-console
    console.log('renderOptimizedPreview called:', { optimizedSkill, skill });

    if (!optimizedSkill || !skill) {
      // eslint-disable-next-line no-console
      console.warn('renderOptimizedPreview: missing data', { optimizedSkill, skill });
      return (
        <div style={{ padding: 20, textAlign: 'center', color: '#999' }}>
          {locale.noOptimizedSkill || 'No optimized skill data available'}
        </div>
      );
    }

    const optimizedResources = optimizedSkill.resource ? Object.keys(optimizedSkill.resource) : [];
    const originalResources = skill?.resource ? Object.keys(skill.resource) : [];

    // 构建优化目标和工具信息的预览文本
    const optimizationGoal = this.field.getValue('optimizationGoal') || '';
    const selectedToolsCount = this.state.selectedMcpTools.length;
    const { inputSectionCollapsed } = this.state;

    // 构建详细内容（展开时显示）
    let detailedContent = '';
    if (optimizationGoal) {
      detailedContent = `Optimization Goal:\n${optimizationGoal}\n`;
    }
    if (selectedToolsCount > 0) {
      detailedContent += `\nSelected MCP Tools (${selectedToolsCount}):\n`;
      this.state.selectedMcpTools.forEach((tool, index) => {
        detailedContent += `${index + 1}. ${tool.name}`;
        if (tool.description) {
          detailedContent += ` - ${tool.description}`;
        }
        detailedContent += '\n';
      });
    }
    if (!optimizationGoal && selectedToolsCount === 0) {
      detailedContent = 'No optimization goal or MCP tools selected';
    }

    // 构建预览文本（折叠时显示）
    let previewText = '';
    if (optimizationGoal) {
      previewText = optimizationGoal;
    } else if (selectedToolsCount > 0) {
      previewText = `${selectedToolsCount} MCP tools selected`;
    } else {
      previewText = 'No optimization goal';
    }

    return (
      <div>
        {/* 显示优化目标和选择的工具信息 - 折叠状态，可展开查看 */}
        {this.renderCollapsibleSection(
          locale.optimizationGoal || 'Optimization Goal',
          inputSectionCollapsed ? previewText : detailedContent,
          inputSectionCollapsed,
          () => this.setState({ inputSectionCollapsed: !this.state.inputSectionCollapsed }),
          null,
          false
        )}

        {/* 思考内容 - 始终显示（如果存在） */}
        {thinkingContent &&
          this.renderCollapsibleSection(
            locale.thinking || '思考',
            thinkingContent,
            thinkingCollapsed,
            () => this.setState({ thinkingCollapsed: !thinkingCollapsed }),
            <Tag type="normal" size="small">
              {locale.thinking || '思考'}
            </Tag>,
            false
          )}

        {/* 结果内容 - 始终显示（如果存在） */}
        {streamContent &&
          this.renderCollapsibleSection(
            locale.generatingContent || '生成内容',
            streamContent,
            resultContentCollapsed,
            () => this.setState({ resultContentCollapsed: !resultContentCollapsed }),
            <Tag type="primary" size="small">
              {locale.generatingContent || '生成内容'}
            </Tag>,
            false,
            thinkingContent // 直接使用 state 中的 thinkingContent
          )}

        {/* 对比表单 - 仅在有变化时显示 */}
        {(() => {
          // 检查是否有任何变化
          const hasNameChange = optimizedSkill.name !== skill.name;
          const hasDescriptionChange = optimizedSkill.description !== skill.description;
          const hasInstructionChange = optimizedSkill.instruction !== skill.instruction;

          // 检查资源变化 - 包括名称和内容
          const optimizedResourcesSorted = [...optimizedResources].sort();
          const originalResourcesSorted = [...originalResources].sort();
          const resourceNameChanged =
            JSON.stringify(optimizedResourcesSorted) !== JSON.stringify(originalResourcesSorted);

          // 检查资源内容变化
          const originalResourceMap = skill?.resource || {};
          const optimizedResourceMap = optimizedSkill?.resource || {};
          let resourceContentChanged = false;
          const resourceChanges = [];

          // 检查所有资源（包括新增、删除、修改）
          const allResourceNames = new Set([...originalResources, ...optimizedResources]);
          allResourceNames.forEach(resourceName => {
            const originalResource = originalResourceMap[resourceName];
            const optimizedResource = optimizedResourceMap[resourceName];
            const originalContent = originalResource?.content || '';
            const optimizedContent = optimizedResource?.content || '';
            const originalType = originalResource?.type || '';
            const optimizedType = optimizedResource?.type || '';

            const isNew = !originalResource && optimizedResource;
            const isRemoved = originalResource && !optimizedResource;
            const isContentChanged = originalContent !== optimizedContent;
            const isTypeChanged = originalType !== optimizedType;

            if (isNew || isRemoved || isContentChanged || isTypeChanged) {
              resourceContentChanged = true;
              resourceChanges.push({
                name: resourceName,
                isNew,
                isRemoved,
                isContentChanged,
                isTypeChanged,
                originalContent,
                optimizedContent,
                originalType,
                optimizedType,
              });
            }
          });

          const resourceChanged = resourceNameChanged || resourceContentChanged;

          const hasAnyChange =
            hasNameChange || hasDescriptionChange || hasInstructionChange || resourceChanged;

          if (!hasAnyChange) {
            return null; // 没有变化，不显示对比
          }

          return (
            <div
              className="comparison-view"
              style={{
                marginTop: 24,
                marginBottom: 16,
                width: '100%',
                paddingTop: 16,
                borderTop: '2px solid #e6e6e6',
                display: 'block',
                visibility: 'visible',
              }}
            >
              <div
                style={{
                  fontSize: '16px',
                  fontWeight: 500,
                  marginBottom: 16,
                  color: '#333',
                  paddingBottom: 8,
                }}
              >
                {locale.optimizationItems || '优化项'}
              </div>
              <Row gutter={16}>
                <Col span={12}>
                  <Card
                    title={locale.originalContent || 'Original Content'}
                    className="comparison-card"
                    style={{ height: 'auto', minHeight: 'auto', maxHeight: 'none' }}
                    bodyStyle={{
                      height: 'auto',
                      minHeight: 'auto',
                      maxHeight: 'none',
                      overflow: 'visible',
                    }}
                  >
                    {/* 仅显示有变化的项 */}
                    {hasNameChange && (
                      <div className="comparison-item">
                        <label>{locale.skillName || 'Skill Name'}:</label>
                        <div>{skill.name || '--'}</div>
                      </div>
                    )}
                    {hasDescriptionChange && (
                      <div className="comparison-item">
                        <label>{locale.description || 'Description'}:</label>
                        <div>{skill.description || '--'}</div>
                      </div>
                    )}
                    {hasInstructionChange && (
                      <div className="comparison-item">
                        <label>{locale.instruction || 'Instruction'}:</label>
                        <pre className="comparison-pre">{skill.instruction || '--'}</pre>
                      </div>
                    )}
                    {resourceChanged && resourceChanges.length > 0 && (
                      <div className="comparison-item comparison-resources">
                        <label>{locale.resources || 'Resources'}:</label>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                          {resourceChanges
                            .filter(change => !change.isNew) // 左侧只显示原始存在的资源（包括被删除和修改的）
                            .map((change, index) => {
                              if (change.isRemoved) {
                                return (
                                  <div key={index} style={{ marginBottom: '8px' }}>
                                    <div style={{ marginBottom: '4px', fontWeight: 500 }}>
                                      {change.name}{' '}
                                      {change.originalType && `(${change.originalType})`}{' '}
                                      <Tag size="small" type="normal">
                                        (removed)
                                      </Tag>
                                    </div>
                                    {change.originalContent && (
                                      <pre
                                        className="comparison-pre"
                                        style={{ marginTop: '4px', fontSize: '12px' }}
                                      >
                                        {change.originalContent}
                                      </pre>
                                    )}
                                  </div>
                                );
                              }
                              return (
                                <div key={index} style={{ marginBottom: '8px' }}>
                                  <div style={{ marginBottom: '4px', fontWeight: 500 }}>
                                    {change.name}{' '}
                                    {change.originalType && `(${change.originalType})`}
                                    {(change.isContentChanged || change.isTypeChanged) && (
                                      <Tag size="small" type="normal">
                                        (changed)
                                      </Tag>
                                    )}
                                  </div>
                                  {change.originalContent && (
                                    <pre
                                      className="comparison-pre"
                                      style={{ marginTop: '4px', fontSize: '12px' }}
                                    >
                                      {change.originalContent}
                                    </pre>
                                  )}
                                </div>
                              );
                            })}
                        </div>
                      </div>
                    )}
                  </Card>
                </Col>
                <Col span={12}>
                  <Card
                    title={locale.optimizedContent || 'Optimized Content'}
                    className="comparison-card optimized"
                    style={{ height: 'auto', minHeight: 'auto', maxHeight: 'none' }}
                    bodyStyle={{
                      height: 'auto',
                      minHeight: 'auto',
                      maxHeight: 'none',
                      overflow: 'visible',
                    }}
                  >
                    {/* 仅显示有变化的项 */}
                    {hasNameChange && (
                      <div className="comparison-item">
                        <label>{locale.skillName || 'Skill Name'}:</label>
                        <div className="changed">{optimizedSkill.name || '--'}</div>
                      </div>
                    )}
                    {hasDescriptionChange && (
                      <div className="comparison-item">
                        <label>{locale.description || 'Description'}:</label>
                        <div className="changed">{optimizedSkill.description || '--'}</div>
                      </div>
                    )}
                    {hasInstructionChange && (
                      <div className="comparison-item">
                        <label>{locale.instruction || 'Instruction'}:</label>
                        <pre className="comparison-pre changed">
                          {optimizedSkill.instruction || '--'}
                        </pre>
                      </div>
                    )}
                    {resourceChanged && resourceChanges.length > 0 ? (
                      <div className="comparison-item comparison-resources">
                        <label>{locale.resources || 'Resources'}:</label>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                          {resourceChanges
                            .filter(change => !change.isRemoved) // 右侧只显示优化后的资源（新增和修改的）
                            .map((change, index) => {
                              const hasContentChange =
                                change.isContentChanged || change.isTypeChanged;
                              return (
                                <div key={index} style={{ marginBottom: '8px' }}>
                                  <div style={{ marginBottom: '4px', fontWeight: 500 }}>
                                    {change.name}{' '}
                                    {change.optimizedType && `(${change.optimizedType})`}
                                    {change.isNew && (
                                      <Tag size="small" type="normal">
                                        (new)
                                      </Tag>
                                    )}
                                    {hasContentChange && (
                                      <Tag size="small" type="normal">
                                        (changed)
                                      </Tag>
                                    )}
                                  </div>
                                  {change.optimizedContent && (
                                    <pre
                                      className={`comparison-pre ${
                                        hasContentChange ? 'changed' : ''
                                      }`}
                                      style={{ marginTop: '4px', fontSize: '12px' }}
                                    >
                                      {change.optimizedContent}
                                    </pre>
                                  )}
                                </div>
                              );
                            })}
                        </div>
                      </div>
                    ) : (
                      <div className="comparison-item">
                        <label>{locale.resources || 'Resources'}:</label>
                        <div>{locale.noResources || 'No resources'}</div>
                      </div>
                    )}
                  </Card>
                </Col>
              </Row>
            </div>
          );
        })()}

        <div style={{ color: '#999', fontSize: '12px', marginTop: 16, paddingBottom: 16 }}>
          {locale.applyOptimizedSkillHint ||
            'Click "Apply" to fill the form with the optimized Skill'}
        </div>
      </div>
    );
  };

  render() {
    const { visible, locale = {} } = this.props;
    const { loading, streaming, optimizedSkill, error, inputSectionCollapsed } = this.state;

    return (
      <Dialog
        visible={visible}
        title={locale.aiOptimize || 'AI 优化'}
        onClose={this.handleClose}
        onCancel={this.handleClose}
        onOk={optimizedSkill ? this.handleApply : this.handleOptimize}
        okProps={{
          loading: loading || streaming,
          children: optimizedSkill
            ? locale.apply || 'Apply'
            : locale.optimize || 'Start Optimization',
        }}
        cancelProps={{
          children: locale.cancel || 'Cancel',
        }}
        style={{ width: 1000 }}
        className="skill-optimize-dialog"
      >
        {!optimizedSkill ? (
          <div>
            {/* 输入区域 - 优化开始后折叠 */}
            {this.renderInputSection()}

            {/* 提示信息 - 仅在未开始优化时显示 */}
            {!inputSectionCollapsed && (
              <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
                {locale.optimizeHint ||
                  'Enter optimization goal and click Start Optimization, AI will optimize the Skill content based on the goal'}
              </div>
            )}

            {/* 错误信息 */}
            {error && (
              <Message type="error" style={{ marginTop: 16 }}>
                {error}
              </Message>
            )}

            {/* 流式内容 - 思考内容和结果内容 */}
            {(streaming || this.state.thinkingContent || this.state.streamContent) &&
              this.renderStreamContent()}
          </div>
        ) : (
          <div>{this.renderOptimizedPreview()}</div>
        )}
      </Dialog>
    );
  }
}

export default SkillOptimizeDialog;
