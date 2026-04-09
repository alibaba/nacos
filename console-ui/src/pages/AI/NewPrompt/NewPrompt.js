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
import { Button, ConfigProvider, Field, Form, Input, Message, Icon } from '@alifd/next';
import PageTitle from 'components/PageTitle';
import MonacoEditor from '../../../components/MonacoEditor/MonacoEditor';
import PromptOptimizeDialog from '../PromptOptimizeDialog';
import { getParams, request } from '@/globalLib';
import { COPILOT_ENABLED } from '@/constants';
import './NewPrompt.scss';

@ConfigProvider.config
class NewPrompt extends React.Component {
  static displayName = 'PromptManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);

    this.state = {
      loading: false,
      template: '',
      variables: [],
      variableDefaults: {},
      variableDescriptions: {},
      optimizeDialogVisible: false,
    };
  }

  componentDidMount() {
    // Check for fork mode
    const mode = getParams('mode') || 'create';
    const basedOnVersion = getParams('basedOnVersion') || '';
    const promptKeyParam = getParams('promptKey') || '';

    if (mode === 'fork' && basedOnVersion && promptKeyParam) {
      // Fork mode: load base version data
      this.field.setValue('promptKey', promptKeyParam);
      this.field.setValues({ promptKey: promptKeyParam });
      this.loadBaseVersion(promptKeyParam, basedOnVersion);
    }
  }

  loadBaseVersion = (promptKey, version) => {
    const namespaceId = getParams('namespace') || '';
    const params = new URLSearchParams();
    params.append('promptKey', promptKey);
    params.append('version', version);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      url: `v3/console/ai/prompt/version?${params.toString()}`,
      success: data => {
        if (data && data.code === 0 && data.data) {
          const versionData = data.data;
          const template = versionData.template || '';
          const variables = this.extractVariables(template);
          const defaults = {};
          const descs = {};
          (versionData.variables || []).forEach(v => {
            if (v.defaultValue) defaults[v.name] = v.defaultValue;
            if (v.description) descs[v.name] = v.description;
          });
          this.setState({
            template,
            variables,
            variableDefaults: defaults,
            variableDescriptions: descs,
          });
        }
      },
    });
  };

  // Extract {{variable}} from template (supports Chinese and other Unicode characters)
  extractVariables = template => {
    if (!template) return [];
    // Use [^\s{}]+ to match any non-whitespace, non-brace characters (supports Chinese)
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

  // Validate version format (major.minor.patch)
  validateVersion = version => {
    const regex = /^\d+\.\d+\.\d+$/;
    return regex.test(version);
  };

  // Handle template change
  handleTemplateChange = value => {
    const variables = this.extractVariables(value);
    this.setState({ template: value, variables });
  };

  // Handle form submit
  handleSubmit = () => {
    const { locale = {} } = this.props;
    const { template } = this.state;

    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }

      // Validate template
      if (!template || !template.trim()) {
        Message.error(locale.templateRequired || '请输入 Prompt 模板内容');
        return;
      }

      this.setState({ loading: true });

      const namespaceId = getParams('namespace') || '';
      const basedOnVersion = getParams('basedOnVersion') || '';

      const { variables: vars, variableDefaults, variableDescriptions } = this.state;
      const variablesDef = vars.map(name => ({
        name,
        defaultValue: variableDefaults[name] || null,
        description: variableDescriptions[name] || null,
      }));

      const requestData = {
        namespaceId: namespaceId,
        promptKey: values.promptKey,
        description: values.description || '',
        template: template,
        commitMsg: values.commitMsg || '',
        variables: JSON.stringify(variablesDef),
      };

      // Optional target version
      if (values.targetVersion && values.targetVersion.trim()) {
        requestData.targetVersion = values.targetVersion.trim();
      }

      // Fork mode: pass basedOnVersion
      if (basedOnVersion) {
        requestData.basedOnVersion = basedOnVersion;
      }

      request({
        method: 'POST',
        url: 'v3/console/ai/prompt/draft',
        data: requestData,
        contentType: 'application/x-www-form-urlencoded',
        success: data => {
          this.setState({ loading: false });
          if (data && data.code === 0) {
            Message.success(locale.createDraftSuccess || locale.createSuccess || '创建成功');
            setTimeout(() => {
              // Navigate to detail page
              const promptKeyVal = values.promptKey;
              this.props.history.push(
                `/promptDetail?namespace=${namespaceId}&promptKey=${promptKeyVal}`
              );
            }, 500);
          } else {
            Message.error(
              data?.message || locale.createDraftFailed || locale.createFailed || '创建失败'
            );
          }
        },
        error: () => {
          this.setState({ loading: false });
          Message.error(locale.createDraftFailed || locale.createFailed || '创建失败');
        },
      });
    });
  };

  // Go back to list
  handleGoBack = () => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/promptManagement?namespace=${namespaceId}`);
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
    Message.success(this.props.locale?.optimizeApplied || '优化结果已应用');
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, template, variables, optimizeDialogVisible } = this.state;
    const { init } = this.field;

    return (
      <div className="new-prompt">
        <PageTitle title={locale.newPrompt || '新建 Prompt'} className="page-header" />

        <div className="form-container">
          <div className="form-left">
            <div className="form-card">
              <Form
                field={this.field}
                labelAlign="left"
                labelCol={{ span: 4 }}
                wrapperCol={{ span: 20 }}
              >
                <Form.Item label={locale.promptKey || 'Prompt 名称'} required>
                  <Input
                    {...init('promptKey', {
                      rules: [
                        {
                          required: true,
                          message: locale.promptKeyRequired || '请输入 Prompt 名称',
                        },
                        {
                          pattern: /^[a-zA-Z0-9_.-]+$/,
                          message: locale.promptKeyPattern || '只允许字母、数字、下划线和横杠',
                        },
                      ],
                    })}
                    placeholder={locale.promptKeyPlaceholder || '请输入 Prompt 名称'}
                    style={{ width: '100%' }}
                    disabled={!!getParams('basedOnVersion')}
                  />
                </Form.Item>

                <Form.Item
                  label={locale.targetVersion || '目标版本号'}
                  help={locale.targetVersionPlaceholder || '可选，例如: 1.0.1'}
                >
                  <Input
                    {...init('targetVersion')}
                    placeholder={locale.targetVersionPlaceholder || '可选，例如: 1.0.1'}
                    style={{ width: 200 }}
                  />
                </Form.Item>

                <Form.Item label={locale.description || '描述'}>
                  <Input.TextArea
                    {...init('description')}
                    placeholder={locale.descriptionPlaceholder || '请输入描述'}
                    rows={2}
                    maxLength={256}
                    showLimitHint
                    style={{ width: '100%' }}
                  />
                </Form.Item>

                <Form.Item label={locale.commitMsg || '提交说明'}>
                  <Input
                    {...init('commitMsg')}
                    placeholder={locale.commitMsgPlaceholder || '请输入提交说明（可选）'}
                    style={{ width: '100%' }}
                  />
                </Form.Item>

                <Form.Item
                  label={locale.templateContent || '模板内容'}
                  required
                  className="form-item-full"
                  help={
                    locale.templateHelp || '示例: 请回答以下问题: {{question}}，上下文: {{context}}'
                  }
                >
                  <div className="editor-container">
                    <div className="editor-toolbar">
                      {localStorage.getItem(COPILOT_ENABLED) === 'true' && (
                        <Button
                          type="secondary"
                          size="small"
                          onClick={this.handleOpenOptimizeDialog}
                          disabled={!template || !template.trim()}
                        >
                          <Icon type="magic" style={{ marginRight: 4 }} />
                          {locale.aiOptimize || 'AI 优化'}
                        </Button>
                      )}
                    </div>
                    <MonacoEditor
                      language="plaintext"
                      width="100%"
                      height={300}
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
                </Form.Item>
              </Form>

              <div className="form-buttons">
                <Button type="primary" onClick={this.handleSubmit} loading={loading}>
                  {locale.create || '创建'}
                </Button>
                <Button onClick={this.handleGoBack}>{locale.cancel || '取消'}</Button>
              </div>
            </div>
          </div>

          <div className="form-right">
            <div className="variables-card">
              <div className="variables-title">
                {locale.templateVariables || '模板参数'}
                {variables.length > 0 && (
                  <span className="variables-count">{variables.length}</span>
                )}
              </div>

              {variables.length > 0 ? (
                <div className="variables-list">
                  {variables.map((variable, index) => (
                    <div
                      key={index}
                      className="variable-item"
                      style={{ flexDirection: 'column', alignItems: 'stretch' }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 4 }}>
                        <Icon type="success" size="small" className="variable-icon" />
                        <span style={{ fontWeight: 500 }}>{`{{${variable}}}`}</span>
                      </div>
                      <Input
                        size="small"
                        placeholder={locale.defaultValuePlaceholder || '默认值（可选）'}
                        value={this.state.variableDefaults[variable] || ''}
                        onChange={value =>
                          this.setState(prev => ({
                            variableDefaults: { ...prev.variableDefaults, [variable]: value },
                          }))
                        }
                        style={{ marginBottom: 4 }}
                      />
                      <Input
                        size="small"
                        placeholder={locale.variableDescPlaceholder || '变量说明（可选）'}
                        value={this.state.variableDescriptions[variable] || ''}
                        onChange={value =>
                          this.setState(prev => ({
                            variableDescriptions: {
                              ...prev.variableDescriptions,
                              [variable]: value,
                            },
                          }))
                        }
                      />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="no-variables">
                  <div className="no-variables-icon">
                    <Icon type="prompt" size="large" />
                  </div>
                  <div>{locale.noVariables || '暂无模板参数'}</div>
                </div>
              )}

              <div className="variables-hint">
                {locale.variablesHint || '使用 {{变量名}} 格式定义模板参数，例如 {{question}}'}
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

export default NewPrompt;
