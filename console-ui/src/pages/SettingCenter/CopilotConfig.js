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
import { Form, Input, Message, Field, Select, Balloon, Icon, Button } from '@alifd/next';
import requestUtils from '../../utils/request';

const FormItem = Form.Item;

class CopilotConfig extends React.Component {
  static propTypes = {
    locale: PropTypes.object,
    onSaveReady: PropTypes.func, // 回调函数，用于将保存方法暴露给父组件
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.state = {
      loading: false,
      config: null,
      testing: false,
      testResult: null,
    };
  }

  componentDidMount() {
    this.loadConfig();
    // 将保存方法暴露给父组件
    if (this.props.onSaveReady) {
      this.props.onSaveReady(this.saveConfig);
    }
  }

  loadConfig = async () => {
    try {
      this.setState({ loading: true });
      const response = await requestUtils.get('v3/console/copilot/config');
      // Result format: {code: 0, message: "...", data: {...}}
      const config = response && response.data ? response.data : response || {};
      this.setState({ config });
      // Process studioUrl: remove trailing slash if exists
      let studioUrl = config.studioUrl || '';
      if (studioUrl && studioUrl.endsWith('/')) {
        studioUrl = studioUrl.slice(0, -1);
      }
      // Set form values - only apiKey, model, studioUrl and studioProject
      this.field.setValues({
        apiKey: config.apiKey || '',
        model: config.model || 'qwen-turbo',
        studioUrl,
        studioProject: config.studioProject || 'NacosCopilot',
      });
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Failed to load Copilot config:', error);
      Message.error(this.props.locale?.copilotConfigLoadFailed || '加载配置失败');
    } finally {
      this.setState({ loading: false });
    }
  };

  saveConfig = async () => {
    const { locale = {} } = this.props;
    const values = this.field.getValues();

    try {
      // Process studioUrl: remove trailing slash if exists
      let studioUrl = values.studioUrl || '';
      if (studioUrl && studioUrl.endsWith('/')) {
        studioUrl = studioUrl.slice(0, -1);
      }
      // Only send apiKey, model, studioUrl and studioProject
      const config = {
        apiKey: values.apiKey || '',
        model: values.model || 'qwen-turbo',
        studioUrl,
        studioProject: values.studioProject || 'NacosCopilot',
      };

      // Send as JSON by stringifying the data and setting Content-Type
      const response = await requestUtils.post(
        'v3/console/copilot/config',
        JSON.stringify(config),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        }
      );
      // Result format: {code: 0, message: "...", data: boolean}
      if (response && (response.code === 0 || response.code === 200)) {
        Message.success(locale.copilotConfigSaveSuccess || '保存成功');
        this.loadConfig();
        return true;
      } else if (response && response.message) {
        Message.error(response.message || locale.copilotConfigSaveFailed || '保存配置失败');
        return false;
      }
      return false;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Failed to save Copilot config:', error);
      Message.error(locale.copilotConfigSaveFailed || '保存配置失败');
      return false;
    }
  };

  verifyConnection = async () => {
    const { locale = {} } = this.props;
    const values = this.field.getValues();

    try {
      this.setState({ testing: true });
      let studioUrl = values.studioUrl || '';
      if (studioUrl && studioUrl.endsWith('/')) {
        studioUrl = studioUrl.slice(0, -1);
      }
      const config = {
        apiKey: values.apiKey || '',
        model: values.model || 'qwen-turbo',
        studioUrl,
        studioProject: values.studioProject || 'NacosCopilot',
      };

      const response = await requestUtils.post(
        'v3/console/copilot/config/verify',
        JSON.stringify(config),
        {
          headers: {
            'Content-Type': 'application/json',
          },
        }
      );
      const result = response && response.data ? response.data : response || {};
      this.setState({ testResult: result });
      if (result.success) {
        Message.success(result.message || locale.copilotConnectionTestSuccess || '连接检测通过');
      } else {
        Message.error(result.message || locale.copilotConnectionTestFailed || '连接检测失败');
      }
      return result.success;
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Failed to verify Copilot connection:', error);
      this.setState({
        testResult: {
          success: false,
          configValid: false,
          llmReachable: false,
          apiKeySource: 'NONE',
          studioConfigured: false,
          message: locale.copilotConnectionTestFailed || '连接检测失败',
        },
      });
      Message.error(locale.copilotConnectionTestFailed || '连接检测失败');
      return false;
    } finally {
      this.setState({ testing: false });
    }
  };

  renderStatusText = (passed, passText, failText) => (
    <span style={{ color: passed ? '#1f9d55' : '#d93026', fontWeight: 500 }}>
      {passed ? passText : failText}
    </span>
  );

  renderApiKeySource = (source, locale) => {
    if (source === 'ENV') {
      return locale.copilotConnectionApiKeySourceEnv || '环境变量';
    }
    if (source === 'CONFIG') {
      return locale.copilotConnectionApiKeySourceConfig || '页面配置';
    }
    return locale.copilotConnectionApiKeySourceNone || '未配置';
  };

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const { testing, testResult } = this.state;

    // 千问系列常见文本模型列表
    const qwenModels = [
      { value: 'qwen-turbo', label: 'qwen-turbo (快速版)' },
      { value: 'qwen-plus', label: 'qwen-plus (增强版)' },
      { value: 'qwen-max', label: 'qwen-max (最强版)' },
      { value: 'qwen-7b-chat', label: 'qwen-7b-chat' },
      { value: 'qwen-14b-chat', label: 'qwen-14b-chat' },
      { value: 'qwen-72b-chat', label: 'qwen-72b-chat' },
      { value: 'qwen3-turbo', label: 'qwen3-turbo (千问3快速版)' },
      { value: 'qwen3-plus', label: 'qwen3-plus (千问3增强版)' },
      { value: 'qwen3-max', label: 'qwen3-max (千问3最强版)' },
      { value: 'qwen3-7b-instruct', label: 'qwen3-7b-instruct' },
      { value: 'qwen3-14b-instruct', label: 'qwen3-14b-instruct' },
      { value: 'qwen3-32b-instruct', label: 'qwen3-32b-instruct' },
      { value: 'qwen3-72b-instruct', label: 'qwen3-72b-instruct' },
    ];

    return (
      <div style={{ width: '100%', maxWidth: '800px' }}>
        <Form field={this.field} labelCol={{ span: 6 }} wrapperCol={{ span: 18 }}>
          <FormItem
            label={
              <span>
                {locale.copilotLlmApiKey || 'API Key'}
                <Balloon
                  trigger={
                    <Icon
                      type="help"
                      size="small"
                      style={{
                        color: '#1DC11D',
                        marginLeft: '4px',
                        verticalAlign: 'middle',
                        cursor: 'help',
                      }}
                    />
                  }
                  triggerType="hover"
                  align="t"
                >
                  {locale.copilotLlmApiKeyHint || '建议通过环境变量 COPILOT_API_KEY 设置'}
                </Balloon>
              </span>
            }
          >
            <Input
              {...init('apiKey', {
                initValue: '',
              })}
              placeholder={
                locale.copilotLlmApiKeyPlaceholder || '请输入API Key（建议通过环境变量设置）'
              }
              htmlType="password"
            />
          </FormItem>

          <FormItem label={locale.copilotLlmModelName || 'Model'}>
            <Select
              {...init('model', {
                initValue: 'qwen-turbo',
              })}
              dataSource={qwenModels}
              placeholder={locale.copilotLlmModelNamePlaceholder || '请选择模型'}
              style={{ width: '100%' }}
            />
          </FormItem>

          <FormItem label={locale.copilotStudioUrl || 'Studio URL'}>
            <Input
              {...init('studioUrl', {
                initValue: '',
              })}
              placeholder={
                locale.copilotStudioUrlPlaceholder ||
                '请输入 AgentScope Studio 地址，例如: http://localhost:3000'
              }
            />
          </FormItem>

          <FormItem label={locale.copilotStudioProject || 'Studio Project'}>
            <Input
              {...init('studioProject', {
                initValue: 'NacosCopilot',
              })}
              placeholder={
                locale.copilotStudioProjectPlaceholder ||
                '请输入 Studio 项目名称，例如: NacosCopilot'
              }
            />
          </FormItem>
          <FormItem label={locale.copilotConnectionTest || '检测连接'}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Button type="primary" text loading={testing} onClick={this.verifyConnection}>
                  {locale.copilotConnectionTest || '检测连接'}
                </Button>
                {testResult && (
                  <span
                    style={{ color: testResult.success ? '#1f9d55' : '#d93026', fontWeight: 500 }}
                  >
                    {testResult.success
                      ? locale.copilotConnectionTestSuccess || '连接检测通过'
                      : locale.copilotConnectionTestFailed || '连接检测失败'}
                  </span>
                )}
              </div>
              {testResult && (
                <div
                  style={{
                    padding: '12px 16px',
                    borderRadius: '6px',
                    background: '#fafafa',
                    border: `1px solid ${testResult.success ? '#b7ebc6' : '#ffd8d6'}`,
                  }}
                >
                  <div style={{ marginBottom: '8px', color: '#333' }}>
                    {testResult.message ||
                      locale.copilotConnectionTestHint ||
                      '检测结果将展示当前配置是否可用。'}
                  </div>
                  <div
                    style={{ display: 'flex', flexWrap: 'wrap', gap: '16px 24px', color: '#666' }}
                  >
                    <span>
                      {locale.copilotConnectionConfigStatus || '配置检查'}：
                      {this.renderStatusText(
                        !!testResult.configValid,
                        locale.copilotConnectionConfigValid || '通过',
                        locale.copilotConnectionConfigInvalid || '未通过'
                      )}
                    </span>
                    <span>
                      {locale.copilotConnectionLlmStatus || 'LLM连通性'}：
                      {this.renderStatusText(
                        !!testResult.llmReachable,
                        locale.copilotConnectionLlmReachable || '可访问',
                        locale.copilotConnectionLlmUnreachable || '不可访问'
                      )}
                    </span>
                    <span>
                      {locale.copilotConnectionApiKeySource || 'API Key来源'}：
                      <span style={{ color: '#333', fontWeight: 500 }}>
                        {this.renderApiKeySource(testResult.apiKeySource, locale)}
                      </span>
                    </span>
                    <span>
                      {locale.copilotConnectionStudioStatus || 'Studio配置'}：
                      {this.renderStatusText(
                        !!testResult.studioConfigured,
                        locale.copilotConnectionStudioConfigured || '已配置',
                        locale.copilotConnectionStudioNotConfigured || '未配置'
                      )}
                    </span>
                  </div>
                </div>
              )}
            </div>
          </FormItem>
        </Form>
      </div>
    );
  }
}

export default CopilotConfig;
