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
} from '@alifd/next';
import { request, getParams } from '@/globalLib';
import './SkillOptimizeDialog.scss';

const { Row, Col } = Grid;

class SkillOptimizeDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    skill: PropTypes.object,
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
      selectedMcpServer: null,
      mcpTools: [],
      selectedMcpTools: [],
      mcpToolSearchKeyword: '',
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

  handleMcpServerChange = async (value) => {
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

  handleMcpToolSearchChange = (value) => {
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

  loadMcpTools = async (mcpServerId) => {
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

  handleOptimize = () => {
    const { skill } = this.props;
    const optimizationGoal = this.field.getValue('optimizationGoal') || '';

    if (!skill) {
      Message.error('Skill data is required');
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
    });

    // Build request payload
    const payload = {
      skill,
      optimizationGoal,
      selectedMcpTools: this.state.selectedMcpTools.map(tool => ({
        name: tool.name,
        description: tool.description,
        inputSchema: tool.inputSchema,
      })),
    };

    // Use EventSource for SSE
    const baseUrl = window.location.origin;
    const url = `${baseUrl}/v3/console/copilot/skill/optimize`;
    const token = localStorage.getItem('token');

    // Create EventSource with POST support (using fetch + ReadableStream)
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

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const readStream = () => {
          reader
            .read()
            .then(({ done, value }) => {
              if (done) {
                this.setState({ streaming: false, loading: false });
                return;
              }

              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split('\n');
              buffer = lines.pop() || ''; // Keep incomplete line in buffer

              lines.forEach(line => {
                if (line.startsWith('data:')) {
                  const dataStr = line.substring(5).trim();
                  if (dataStr) {
                    try {
                      const data = JSON.parse(dataStr);
                      this.handleSSEMessage(data);
                    } catch (e) {
                      // Failed to parse SSE data
                    }
                  }
                } else if (line.startsWith('event:')) {
                  const eventType = line.substring(6).trim();
                  // Handle event type if needed
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

  handleSSEMessage = data => {
    const { type, chunk, done, optimizedSkill } = data;
    const typeStr = type?.code || type || 'CONTENT';

    if (typeStr === 'THINKING' || type === 'THINKING') {
      // Accumulate thinking content separately
      this.setState(prevState => ({
        thinkingContent: prevState.thinkingContent + (chunk || ''),
        streamType: 'THINKING',
      }));
    } else if (typeStr === 'TOOL_CALL' || type === 'TOOL_CALL') {
      // Accumulate tool call content
      this.setState(prevState => ({
        streamContent: prevState.streamContent + (chunk || ''),
        streamType: 'TOOL_CALL',
      }));
    } else if (typeStr === 'CONTENT' || type === 'CONTENT') {
      // Accumulate general content
      this.setState(prevState => ({
        streamContent: prevState.streamContent + (chunk || ''),
        streamType: 'CONTENT',
      }));
    } else if (typeStr === 'DONE' || type === 'DONE' || done) {
      // Final result - collapse thinking section
      // Support multiple field names: optimizedSkill, skill
      const skillData = optimizedSkill || data.skill || data.optimizedSkill || null;
      this.setState({
        streaming: false,
        loading: false,
        optimizedSkill: skillData,
        thinkingCollapsed: true, // Collapse thinking section when done
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

    // If onSuccess is provided, call it directly (for in-page optimization)
    if (onSuccess) {
      onSuccess(mergedSkill);
      this.handleClose();
      return;
    }

    // Store optimized skill data to localStorage for editing page to pick up
    const namespaceId = skill.namespaceId || getParams('namespace') || 'public';
    const optimizedSkillData = {
      ...mergedSkill,
      optimized: true, // Flag to indicate this is from optimization
    };
    localStorage.setItem('nacos_optimized_skill', JSON.stringify(optimizedSkillData));

    // Navigate to edit page (only if no onSuccess callback)
    if (history) {
      const skillName = mergedSkill.name || skill.name;
      history.push(`/newSkill?namespace=${namespaceId}&name=${skillName}&mode=edit&optimized=true`);
      this.handleClose();
    } else {
      // Fallback: if no history, use the old behavior (direct update)
      this.handleApplyDirect(mergedSkill, locale, onSuccess);
    }
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

  getStreamTypeColor = type => {
    const colors = {
      THINKING: 'blue',
      TOOL_CALL: 'orange',
      CONTENT: 'green',
      DONE: 'success',
    };
    return colors[type] || 'default';
  };

  renderStreamContent = () => {
    const { streamContent, thinkingContent, streamType, thinkingCollapsed } = this.state;
    const { locale = {} } = this.props;

    return (
      <div>
        {thinkingContent && (
          <div className="thinking-section" style={{ marginBottom: 16 }}>
            <Collapse
              defaultExpanded={!thinkingCollapsed}
              style={{ border: '1px solid #e6e6e6', borderRadius: '4px' }}
            >
              <Collapse.Panel
                title={
                  <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Tag type="blue" size="small" style={{ marginRight: 8 }}>
                      {locale.thinking || 'Thinking'}
                    </Tag>
                    <span>{locale.thinkingProcess || 'Optimization reasoning process'}</span>
                  </div>
                }
              >
                <div className="thinking-content">
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
                    {thinkingContent}
                  </pre>
                </div>
              </Collapse.Panel>
            </Collapse>
          </div>
        )}

        {streamContent && streamType !== 'THINKING' && (
          <div className="stream-content">
            <div className="stream-header">
              <Tag type={this.getStreamTypeColor(streamType)} size="small">
                {this.getStreamTypeLabel(streamType)}
              </Tag>
            </div>
            <div className="stream-text">
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
                {streamContent}
              </pre>
            </div>
          </div>
        )}
      </div>
    );
  };

  renderOptimizedPreview = () => {
    const { skill, locale = {} } = this.props;
    const { optimizedSkill, thinkingContent } = this.state;

    if (!optimizedSkill || !skill) {
      return null;
    }

    const optimizedResources = optimizedSkill.resource
      ? Object.keys(optimizedSkill.resource)
      : [];
    const originalResources = skill?.resource
      ? Object.keys(skill.resource)
      : [];

    return (
      <div>
        <Message type="success" style={{ marginBottom: 16 }}>
          {locale.optimizeSuccess || 'Skill optimized successfully'}
        </Message>

        {thinkingContent && (
          <div className="thinking-section" style={{ marginBottom: 16 }}>
            <Collapse defaultExpanded={false} style={{ border: '1px solid #e6e6e6', borderRadius: '4px' }}>
              <Collapse.Panel
                title={
                  <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Tag type="blue" size="small" style={{ marginRight: 8 }}>
                      {locale.thinking || 'Thinking'}
                    </Tag>
                    <span>{locale.thinkingProcess || 'Optimization reasoning process'}</span>
                  </div>
                }
              >
                <div className="thinking-content">
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
                    {thinkingContent}
                  </pre>
                </div>
              </Collapse.Panel>
            </Collapse>
          </div>
        )}

        <div className="comparison-view" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Card title={locale.originalContent || 'Original Content'} className="comparison-card">
                <div className="comparison-item">
                  <label>{locale.skillName || 'Skill Name'}:</label>
                  <div>{skill.name || '--'}</div>
                </div>
                <div className="comparison-item">
                  <label>{locale.description || 'Description'}:</label>
                  <div>{skill.description || '--'}</div>
                </div>
                <div className="comparison-item">
                  <label>{locale.instruction || 'Instruction'}:</label>
                  <pre className="comparison-pre">{skill.instruction || '--'}</pre>
                </div>
                {originalResources.length > 0 && (
                  <div className="comparison-item">
                    <label>{locale.resources || 'Resources'}:</label>
                    <div>
                      {originalResources.map((name, index) => (
                        <Tag key={index} style={{ marginRight: 8, marginBottom: 4 }}>
                          {name}
                        </Tag>
                      ))}
                    </div>
                  </div>
                )}
              </Card>
            </Col>
            <Col span={12}>
              <Card
                title={locale.optimizedContent || 'Optimized Content'}
                className="comparison-card optimized"
              >
                <div className="comparison-item">
                  <label>{locale.skillName || 'Skill Name'}:</label>
                  <div className={optimizedSkill.name !== skill.name ? 'changed' : ''}>
                    {optimizedSkill.name || '--'}
                  </div>
                </div>
                <div className="comparison-item">
                  <label>{locale.description || 'Description'}:</label>
                  <div className={optimizedSkill.description !== skill.description ? 'changed' : ''}>
                    {optimizedSkill.description || '--'}
                  </div>
                </div>
                <div className="comparison-item">
                  <label>{locale.instruction || 'Instruction'}:</label>
                  <pre className={`comparison-pre ${optimizedSkill.instruction !== skill.instruction ? 'changed' : ''}`}>
                    {optimizedSkill.instruction || '--'}
                  </pre>
                </div>
                {optimizedResources.length > 0 ? (
                  <div className="comparison-item">
                    <label>{locale.resources || 'Resources'}:</label>
                    <div>
                      {optimizedResources.map((name, index) => {
                        const isNew = !originalResources.includes(name);
                        return (
                          <Tag
                            key={index}
                            type={isNew ? 'success' : 'normal'}
                            style={{ marginRight: 8, marginBottom: 4 }}
                          >
                            {name}
                            {isNew && ' (new)'}
                          </Tag>
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

          {originalResources.some(name => !optimizedResources.includes(name)) && (
            <Card title={locale.removedResources || 'Removed Resources'} style={{ marginTop: 16 }}>
              <div>
                {originalResources
                  .filter(name => !optimizedResources.includes(name))
                  .map((name, index) => (
                    <Tag key={index} type="warning" style={{ marginRight: 8, marginBottom: 4 }}>
                      {name}
                    </Tag>
                  ))}
              </div>
            </Card>
          )}
        </div>

        <div style={{ color: '#999', fontSize: '12px', marginTop: 16 }}>
          {locale.applyOptimizedSkillHint || 'Click "Apply" to fill the form with the optimized Skill'}
        </div>
      </div>
    );
  };

  render() {
    const { visible, locale = {} } = this.props;
    const { loading, streaming, optimizedSkill, error } = this.state;

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
            ? (locale.apply || 'Apply')
            : (locale.optimize || 'Start Optimization'),
        }}
        cancelProps={{
          children: locale.cancel || 'Cancel',
        }}
        style={{ width: 1000 }}
        className="skill-optimize-dialog"
      >
        {!optimizedSkill ? (
          <div>
            <Form.Item label={locale.optimizationGoal || 'Optimization Goal'}>
              <Input.TextArea
                value={this.field.getValue('optimizationGoal')}
                onChange={value => this.field.setValue('optimizationGoal', value)}
                placeholder={locale.optimizationGoalPlaceholder || 'Enter optimization goal (optional), e.g., improve instruction clarity, add error handling, add resource templates'}
                rows={6}
                maxLength={2000}
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
                          ? (locale.noToolsFound || 'No tools found matching your search')
                          : (locale.noToolsAvailable || 'No tools available in this MCP server')}
                      </div>
                    )}
                  </div>
                </Loading>
              )}
            </Form.Item>

            <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
              {locale.optimizeHint || 'Enter optimization goal and click Start Optimization, AI will optimize the Skill content based on the goal'}
            </div>

            {error && (
              <Message type="error" style={{ marginTop: 16 }}>
                {error}
              </Message>
            )}

            {streaming && this.renderStreamContent()}
          </div>
        ) : (
          this.renderOptimizedPreview()
        )}
      </Dialog>
    );
  }
}

export default SkillOptimizeDialog;
