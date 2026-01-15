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
  Form,
  Field,
  Input,
  Message,
  Loading,
  Icon,
  Tag,
  Card,
  Grid,
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
    };
  }

  componentDidUpdate(prevProps) {
    if (prevProps.visible !== this.props.visible && !this.props.visible) {
      // Dialog closed, cleanup
      this.cleanup();
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
      streamType: null,
      optimizedSkill: null,
      changes: [],
      qualityScore: null,
      explanation: '',
      error: null,
      showComparison: false,
    });
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
      streamType: null,
      optimizedSkill: null,
      changes: [],
      qualityScore: null,
      explanation: '',
      error: null,
      showComparison: false,
    });

    // Build request payload
    const payload = {
      skill,
      optimizationGoal,
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
    const { type, chunk, done, optimizedSkill, changes, qualityScore, explanation } = data;

    if (type === 'THINKING' || type === 'TOOL_CALL' || type === 'CONTENT') {
      // Accumulate stream content
      this.setState(prevState => ({
        streamContent: prevState.streamContent + (chunk || ''),
        streamType: type,
      }));
    } else if (type === 'DONE' || done) {
      // Final result
      this.setState({
        streaming: false,
        loading: false,
        optimizedSkill: optimizedSkill || null,
        changes: changes || [],
        qualityScore: qualityScore || null,
        explanation: explanation || '',
        showComparison: true,
      });
    } else if (type === 'error' || explanation) {
      // Error case
      this.setState({
        streaming: false,
        loading: false,
        error: explanation || 'Optimization failed',
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

    // Merge optimized fields with original skill (preserve namespaceId, skillId, etc.)
    // Use optimized resources instead of original resources
    const mergedSkill = {
      ...skill,
      name: optimizedSkill.name || skill.name,
      description: optimizedSkill.description || skill.description,
      instruction: optimizedSkill.instruction || skill.instruction,
      resource: optimizedSkill.resource || {}, // Use optimized resources
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
    const { streamContent, streamType } = this.state;
    if (!streamContent && !streamType) {
      return null;
    }

    return (
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
    );
  };

  renderComparison = () => {
    const { skill, locale = {} } = this.props;
    const { optimizedSkill, changes, qualityScore, explanation } = this.state;

    if (!optimizedSkill) {
      return null;
    }

    const originalResources = skill.resource ? Object.keys(skill.resource) : [];
    const optimizedResources = optimizedSkill.resource ? Object.keys(optimizedSkill.resource) : [];
    const removedResources = originalResources.filter(name => !optimizedResources.includes(name));

    return (
      <div className="comparison-view">
        <Row gutter={16}>
          <Col span={12}>
            <Card title={locale.originalContent || '原始内容'} className="comparison-card">
              <div className="comparison-item">
                <label>{locale.skillName || '名称'}:</label>
                <div>{skill.name || '--'}</div>
              </div>
              <div className="comparison-item">
                <label>{locale.description || '描述'}:</label>
                <div>{skill.description || '--'}</div>
              </div>
              <div className="comparison-item">
                <label>{locale.instruction || '指令'}:</label>
                <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                  {skill.instruction || '--'}
                </pre>
              </div>
              {originalResources.length > 0 && (
                <div className="comparison-item">
                  <label>{locale.resources || '资源'}:</label>
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
            <Card title={locale.optimizedContent || '优化后内容'} className="comparison-card optimized">
              <div className="comparison-item">
                <label>{locale.skillName || '名称'}:</label>
                <div>{optimizedSkill.name || '--'}</div>
              </div>
              <div className="comparison-item">
                <label>{locale.description || '描述'}:</label>
                <div>{optimizedSkill.description || '--'}</div>
              </div>
              <div className="comparison-item">
                <label>{locale.instruction || '指令'}:</label>
                <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                  {optimizedSkill.instruction || '--'}
                </pre>
              </div>
              {optimizedResources.length > 0 ? (
                <div className="comparison-item">
                  <label>{locale.resources || '资源'}:</label>
                  <div>
                    {optimizedResources.map((name, index) => (
                      <Tag key={index} type="success" style={{ marginRight: 8, marginBottom: 4 }}>
                        {name}
                      </Tag>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="comparison-item">
                  <label>{locale.resources || '资源'}:</label>
                  <div>{locale.noResources || '暂无资源'}</div>
                </div>
              )}
            </Card>
          </Col>
        </Row>

        {removedResources.length > 0 && (
          <Card title={locale.removedResources || '已移除的资源'} style={{ marginTop: 16 }}>
            <div>
              {removedResources.map((name, index) => (
                <Tag key={index} type="warning" style={{ marginRight: 8, marginBottom: 4 }}>
                  {name}
                </Tag>
              ))}
            </div>
          </Card>
        )}

        {changes.length > 0 && (
          <Card title="变更详情" style={{ marginTop: 16 }}>
            {changes.map((change, index) => (
              <div key={index} className="change-item">
                <div className="change-header">
                  <Tag type="primary" size="small">
                    {change.field}
                  </Tag>
                  <Tag type={change.type === 'improvement' ? 'success' : 'normal'} size="small">
                    {change.type}
                  </Tag>
                </div>
                <div className="change-description">{change.description}</div>
                <div className="change-reason">原因: {change.reason}</div>
              </div>
            ))}
          </Card>
        )}

        {qualityScore !== null && (
          <Card title="质量评分" style={{ marginTop: 16 }}>
            <div className="quality-score">
              <span className="score-value">{qualityScore}</span>
              <span className="score-label">/ 1.0</span>
            </div>
          </Card>
        )}

        {explanation && (
          <Card title="优化说明" style={{ marginTop: 16 }}>
            <div className="explanation">{explanation}</div>
          </Card>
        )}
      </div>
    );
  };

  render() {
    const { visible, locale = {} } = this.props;
    const { loading, streaming, showComparison, error } = this.state;

    return (
      <Dialog
        visible={visible}
        title={locale.aiOptimize || 'AI 优化'}
        onClose={this.handleClose}
        onCancel={this.handleClose}
        onOk={showComparison ? this.handleApply : this.handleOptimize}
        okProps={{
          loading: loading || streaming,
          children: showComparison ? (locale.apply || '应用') : (locale.optimize || '开始优化'),
        }}
        cancelProps={{
          children: locale.cancel || '取消',
        }}
        style={{ width: 1000 }}
        className="skill-optimize-dialog"
      >
        <Loading visible={loading && !streaming} tip={locale.optimizing || '优化中...'}>
          <Form field={this.field}>
            <Form.Item label={locale.optimizationGoal || '优化目标'} required={false}>
              <Input.TextArea
                name="optimizationGoal"
                placeholder={locale.optimizationGoalPlaceholder || '请输入优化目标（可选）'}
                rows={3}
              />
            </Form.Item>
          </Form>

          {error && (
            <Message type="error" style={{ marginTop: 16 }}>
              {error}
            </Message>
          )}

          {streaming && this.renderStreamContent()}

          {showComparison && this.renderComparison()}
        </Loading>
      </Dialog>
    );
  }
}

export default SkillOptimizeDialog;
