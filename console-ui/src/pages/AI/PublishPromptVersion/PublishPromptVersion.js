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
import { Button, ConfigProvider, Field, Form, Input, Message, Icon, Tag } from '@alifd/next';
import PageTitle from 'components/PageTitle';
import MonacoEditor from '../../../components/MonacoEditor/MonacoEditor';
import { getParams, request } from '@/globalLib';
import './PublishPromptVersion.scss';

@ConfigProvider.config
class PublishPromptVersion extends React.Component {
  static displayName = 'PromptManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);

    // Get data from sessionStorage (set by PromptDetail page)
    const storedTemplate = sessionStorage.getItem('promptPublishTemplate') || '';
    const storedCurrentVersion = sessionStorage.getItem('promptPublishCurrentVersion') || '';
    const storedDescription = sessionStorage.getItem('promptPublishDescription') || '';

    this.state = {
      loading: false,
      promptKey: getParams('promptKey') || '',
      namespaceId: getParams('namespace') || '',
      currentVersion: storedCurrentVersion,
      description: storedDescription,
      template: storedTemplate,
      variables: this.extractVariables(storedTemplate),
    };
  }

  componentDidMount() {
    // Suggest next version based on current version
    const { currentVersion } = this.state;
    if (currentVersion) {
      const suggestedVersion = this.suggestNextVersion(currentVersion);
      this.field.setValue('version', suggestedVersion);
    }

    // Clean up sessionStorage
    sessionStorage.removeItem('promptPublishTemplate');
    sessionStorage.removeItem('promptPublishCurrentVersion');
    sessionStorage.removeItem('promptPublishDescription');
  }

  // Suggest next version (increment patch number)
  suggestNextVersion = currentVersion => {
    if (!currentVersion) return '1.0.0';
    const parts = currentVersion.split('.');
    if (parts.length !== 3) return '1.0.0';
    const [major, minor, patch] = parts.map(Number);
    return `${major}.${minor}.${patch + 1}`;
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

  // Validate version format (major.minor.patch)
  validateVersion = version => {
    const regex = /^\d+\.\d+\.\d+$/;
    return regex.test(version);
  };

  // Compare versions: return true if newVersion > currentVersion
  isVersionGreater = (newVersion, currentVersion) => {
    if (!currentVersion) return true;
    const partsNew = newVersion.split('.').map(Number);
    const partsCurrent = currentVersion.split('.').map(Number);
    for (let i = 0; i < 3; i++) {
      if (partsNew[i] > partsCurrent[i]) return true;
      if (partsNew[i] < partsCurrent[i]) return false;
    }
    return false; // Equal versions
  };

  // Handle template change
  handleTemplateChange = value => {
    const variables = this.extractVariables(value);
    this.setState({ template: value, variables });
  };

  // Handle form submit
  handleSubmit = () => {
    const { locale = {} } = this.props;
    const { template, promptKey, namespaceId, currentVersion, description } = this.state;

    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }

      // Validate version format
      if (!this.validateVersion(values.version)) {
        Message.error(locale.versionFormatError || '版本号格式错误，请使用 x.y.z 格式');
        return;
      }

      // Validate version is greater than current
      if (!this.isVersionGreater(values.version, currentVersion)) {
        Message.error(
          (locale.versionMustBeGreater || '新版本号必须大于当前版本 {0}').replace(
            '{0}',
            currentVersion
          )
        );
        return;
      }

      // Validate template
      if (!template || !template.trim()) {
        Message.error(locale.templateRequired || '请输入 Prompt 模板内容');
        return;
      }

      this.setState({ loading: true });

      request({
        method: 'POST',
        url: 'v3/console/ai/prompt',
        data: {
          namespaceId: namespaceId,
          promptKey: promptKey,
          version: values.version,
          template: template,
          commitMsg: values.commitMsg || '',
        },
        success: data => {
          this.setState({ loading: false });
          if (data && data.code === 0) {
            Message.success(locale.publishSuccess || '发布成功');
            setTimeout(() => {
              // Navigate to list page after publish success
              this.handleGoToList();
            }, 1000);
          } else {
            Message.error(data?.message || locale.publishFailed || '发布失败');
          }
        },
        error: () => {
          this.setState({ loading: false });
          Message.error(locale.publishFailed || '发布失败');
        },
      });
    });
  };

  // Go back to detail page
  handleGoBack = () => {
    const { promptKey, namespaceId } = this.state;
    this.props.history.push(
      `/promptDetail?namespace=${namespaceId || 'public'}&promptKey=${promptKey}`
    );
  };

  // Go to list page
  handleGoToList = () => {
    const { namespaceId } = this.state;
    this.props.history.push(`/promptManagement?namespace=${namespaceId || 'public'}`);
  };

  // Cancel and go back to detail page
  handleCancel = () => {
    this.handleGoBack();
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, promptKey, currentVersion, template, variables } = this.state;
    const { init } = this.field;

    return (
      <div className="publish-prompt-version">
        <PageTitle title={locale.publishNewVersion || '发布新版本'} className="page-header" />

        <div className="prompt-info">
          <div className="info-item">
            <span className="info-label">{locale.promptKey || 'Prompt 名称'}:</span>
            <span className="info-value">{promptKey}</span>
          </div>
          <div className="info-item">
            <span className="info-label">{locale.currentVersion || '当前版本'}:</span>
            <Tag type="primary" size="small">
              {currentVersion || '--'}
            </Tag>
          </div>
        </div>

        <div className="form-container">
          <div className="form-left">
            <div className="form-card">
              <Form
                field={this.field}
                labelAlign="left"
                labelCol={{ span: 4 }}
                wrapperCol={{ span: 20 }}
              >
                <Form.Item
                  label={locale.newVersion || '新版本号'}
                  required
                  help={(locale.versionMustBeGreaterHelp || '必须大于当前版本 {0}').replace(
                    '{0}',
                    currentVersion || '--'
                  )}
                >
                  <Input
                    {...init('version', {
                      rules: [
                        { required: true, message: locale.versionRequired || '请输入版本号' },
                      ],
                    })}
                    placeholder={locale.versionPlaceholder || '例如: 1.0.0'}
                    style={{ width: 200 }}
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
                >
                  <div className="editor-container">
                    <MonacoEditor
                      language="plaintext"
                      width="100%"
                      height={350}
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
                  {locale.publish || '发布'}
                </Button>
                <Button onClick={this.handleCancel}>{locale.cancel || '取消'}</Button>
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
                    <div key={index} className="variable-item">
                      <Icon type="success" size="small" className="variable-icon" />
                      {`{{${variable}}}`}
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
      </div>
    );
  }
}

export default PublishPromptVersion;
