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
import { Button, Dialog, Field, Form, Input, Message, Loading, Icon } from '@alifd/next';
import './PromptOptimizeDialog.scss';

class PromptOptimizeDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    prompt: PropTypes.string,
    onClose: PropTypes.func,
    onApply: PropTypes.func,
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.state = {
      loading: false,
      streaming: false,
      streamContent: '',
      optimizedPrompt: null,
      error: null,
    };
    this.rightPanelRef = React.createRef();
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevProps.visible !== this.props.visible) {
      if (!this.props.visible) {
        this.cleanup();
      }
    }
    // Auto scroll to bottom when streaming
    if (prevState.streamContent !== this.state.streamContent && this.rightPanelRef.current) {
      this.rightPanelRef.current.scrollTop = this.rightPanelRef.current.scrollHeight;
    }
  }

  componentWillUnmount() {
    this.cleanup();
  }

  cleanup = () => {
    this.setState({
      loading: false,
      streaming: false,
      streamContent: '',
      optimizedPrompt: null,
      error: null,
    });
    this.field.reset();
  };

  handleOptimize = () => {
    const { prompt, locale = {} } = this.props;
    const optimizationGoal = this.field.getValue('optimizationGoal') || '';

    if (!prompt || !prompt.trim()) {
      Message.error(locale.promptRequired || 'Prompt 内容不能为空');
      return;
    }

    this.setState({
      loading: true,
      streaming: true,
      streamContent: '',
      optimizedPrompt: null,
      error: null,
    });

    const ctxPath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    const url = `${window.location.origin}${ctxPath}v3/console/copilot/prompt/optimize`;
    const token = localStorage.getItem('token');

    const payload = {
      prompt,
      optimizationGoal,
    };

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
                this.setState(prevState => ({
                  streaming: false,
                  loading: false,
                  optimizedPrompt: prevState.streamContent || null,
                }));
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
                      this.handleSSEMessage(data);
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
    const { type, chunk, done } = data;
    const typeStr = type?.code || type || 'CONTENT';

    if (typeStr === 'CONTENT' || type === 'CONTENT') {
      this.setState(prevState => ({
        streamContent: prevState.streamContent + (chunk || ''),
      }));
    } else if (typeStr === 'DONE' || type === 'DONE' || done) {
      this.setState(prevState => ({
        streaming: false,
        loading: false,
        optimizedPrompt: prevState.streamContent || null,
      }));
    } else if (typeStr === 'error' || type === 'error') {
      this.setState({
        streaming: false,
        loading: false,
        error: data.message || 'Optimization failed',
      });
    }
  };

  handleApply = () => {
    const { onApply, locale = {} } = this.props;
    const { optimizedPrompt } = this.state;

    if (!optimizedPrompt) {
      Message.error(locale.noOptimizedPrompt || '没有优化结果');
      return;
    }

    if (onApply) {
      onApply(optimizedPrompt);
    }
    this.handleClose();
  };

  handleClose = () => {
    this.cleanup();
    if (this.props.onClose) {
      this.props.onClose();
    }
  };

  // Simple line-based diff algorithm
  computeDiff = (original, optimized) => {
    if (!original || !optimized) return { left: [], right: [] };

    const originalLines = original.split('\n');
    const optimizedLines = optimized.split('\n');

    // LCS-based diff for better accuracy
    const lcs = this.computeLCS(originalLines, optimizedLines);

    const leftResult = [];
    const rightResult = [];

    let origIdx = 0;
    let optIdx = 0;
    let lcsIdx = 0;

    while (origIdx < originalLines.length || optIdx < optimizedLines.length) {
      if (lcsIdx < lcs.length) {
        // Add removed lines (in original but not in LCS)
        while (origIdx < originalLines.length && originalLines[origIdx] !== lcs[lcsIdx]) {
          leftResult.push({ text: originalLines[origIdx], type: 'removed' });
          origIdx++;
        }
        // Add added lines (in optimized but not in LCS)
        while (optIdx < optimizedLines.length && optimizedLines[optIdx] !== lcs[lcsIdx]) {
          rightResult.push({ text: optimizedLines[optIdx], type: 'added' });
          optIdx++;
        }
        // Add unchanged line
        if (origIdx < originalLines.length && optIdx < optimizedLines.length) {
          leftResult.push({ text: originalLines[origIdx], type: 'unchanged' });
          rightResult.push({ text: optimizedLines[optIdx], type: 'unchanged' });
          origIdx++;
          optIdx++;
          lcsIdx++;
        }
      } else {
        // Remaining lines
        while (origIdx < originalLines.length) {
          leftResult.push({ text: originalLines[origIdx], type: 'removed' });
          origIdx++;
        }
        while (optIdx < optimizedLines.length) {
          rightResult.push({ text: optimizedLines[optIdx], type: 'added' });
          optIdx++;
        }
      }
    }

    return { left: leftResult, right: rightResult };
  };

  // Compute Longest Common Subsequence
  computeLCS = (arr1, arr2) => {
    const m = arr1.length;
    const n = arr2.length;
    const dp = Array(m + 1)
      .fill(null)
      .map(() => Array(n + 1).fill(0));

    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        if (arr1[i - 1] === arr2[j - 1]) {
          dp[i][j] = dp[i - 1][j - 1] + 1;
        } else {
          dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
      }
    }

    // Backtrack to find LCS
    const lcs = [];
    let i = m;
    let j = n;
    while (i > 0 && j > 0) {
      if (arr1[i - 1] === arr2[j - 1]) {
        lcs.unshift(arr1[i - 1]);
        i--;
        j--;
      } else if (dp[i - 1][j] > dp[i][j - 1]) {
        i--;
      } else {
        j--;
      }
    }

    return lcs;
  };

  renderDiffLine = (line, index) => {
    const classMap = {
      removed: 'diff-line diff-removed',
      added: 'diff-line diff-added',
      unchanged: 'diff-line',
    };
    return (
      <div key={index} className={classMap[line.type] || 'diff-line'}>
        {line.text || '\u00A0'}
      </div>
    );
  };

  render() {
    const { visible, prompt, locale = {} } = this.props;
    const { loading, streaming, streamContent, optimizedPrompt, error } = this.state;
    const { init } = this.field;

    const hasResult = optimizedPrompt && !streaming;
    const showComparison = streaming || streamContent;

    // Compute diff when we have content
    const diff = showComparison ? this.computeDiff(prompt, streamContent) : { left: [], right: [] };

    return (
      <Dialog
        className="prompt-optimize-dialog"
        visible={visible}
        title={
          <span>
            <Icon type="magic" style={{ marginRight: 8 }} />
            {locale.aiOptimize || 'AI 优化 Prompt'}
          </span>
        }
        onClose={this.handleClose}
        footerActions={hasResult ? ['cancel', 'ok'] : ['cancel']}
        okProps={{ children: locale.applyOptimize || '应用优化结果' }}
        cancelProps={{ children: locale.close || '关闭' }}
        onOk={this.handleApply}
        onCancel={this.handleClose}
        style={{ width: 1000 }}
      >
        <div className="optimize-content">
          {/* Optimization Goal Input */}
          <div className="goal-section">
            <Form field={this.field} inline>
              <Form.Item label={locale.optimizationGoal || '优化目标'} style={{ flex: 1 }}>
                <Input
                  {...init('optimizationGoal')}
                  placeholder={
                    locale.optimizationGoalPlaceholder ||
                    '描述优化目标，如：让表达更清晰、增加示例等（可选）'
                  }
                  style={{ width: '100%' }}
                  disabled={streaming}
                />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  onClick={this.handleOptimize}
                  loading={loading}
                  disabled={streaming}
                >
                  <Icon type="magic" style={{ marginRight: 4 }} />
                  {streaming
                    ? locale.optimizing || '优化中...'
                    : locale.startOptimize || '开始优化'}
                </Button>
              </Form.Item>
            </Form>
          </div>

          {/* Error Display */}
          {error && (
            <div className="error-section">
              <div className="error-message">
                <Icon type="warning" style={{ marginRight: 8, color: '#ff4d4f' }} />
                {error}
              </div>
            </div>
          )}

          {/* Side-by-side Comparison */}
          <div className="comparison-container">
            {/* Left Panel - Original */}
            <div className="comparison-panel left-panel">
              <div className="panel-header">
                <span className="panel-title">{locale.originalPrompt || '优化前'}</span>
              </div>
              <div className="panel-content">
                {showComparison ? (
                  <div className="diff-content">
                    {diff.left.map((line, idx) => this.renderDiffLine(line, idx))}
                  </div>
                ) : (
                  <pre className="prompt-text">{prompt || ''}</pre>
                )}
              </div>
            </div>

            {/* Right Panel - Optimized */}
            <div className="comparison-panel right-panel">
              <div className="panel-header">
                <span className="panel-title">
                  {streaming && (
                    <>
                      <Loading size="small" style={{ marginRight: 8 }} />
                      {locale.optimizing || '优化中...'}
                    </>
                  )}
                  {!streaming && streamContent && (
                    <>
                      <Icon
                        type="success"
                        size="small"
                        style={{ marginRight: 8, color: '#52c41a' }}
                      />
                      {locale.optimizedPrompt || '优化后'}
                    </>
                  )}
                  {!streaming && !streamContent && (locale.optimizedPrompt || '优化后')}
                </span>
              </div>
              <div className="panel-content" ref={this.rightPanelRef}>
                {showComparison ? (
                  <div className="diff-content">
                    {diff.right.map((line, idx) => this.renderDiffLine(line, idx))}
                  </div>
                ) : (
                  <div className="empty-hint">
                    {locale.clickToOptimize || '点击"开始优化"按钮开始'}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Legend */}
          {showComparison && (
            <div className="diff-legend">
              <span className="legend-item">
                <span className="legend-color removed" />
                {locale.removed || '删除'}
              </span>
              <span className="legend-item">
                <span className="legend-color added" />
                {locale.added || '新增'}
              </span>
            </div>
          )}
        </div>
      </Dialog>
    );
  }
}

export default PromptOptimizeDialog;
