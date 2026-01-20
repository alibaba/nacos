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
import { Form, Input, NumberPicker, Switch, Select, Message, Field } from '@alifd/next';
import requestUtils from '../../utils/request';

const FormItem = Form.Item;

class CopilotConfig extends React.Component {
  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.state = {
      loading: false,
      config: null,
    };
  }

  componentDidMount() {
    this.loadConfig();
  }

  loadConfig = async () => {
    try {
      this.setState({ loading: true });
      const response = await requestUtils.get('v3/console/copilot/config');
      // Result format: {code: 0, message: "...", data: {...}}
      const config = (response && response.data) ? response.data : (response || {});
      this.setState({ config });
      // Set form values
      this.field.setValues({
        enabled: config.enabled !== undefined ? config.enabled : true,
        defaultNamespace: config.defaultNamespace || 'public',
        // LLM config
        llmProvider: config.llm?.provider || 'qwen',
        llmApiKey: config.llm?.apiKey || '',
        llmEndpoint: config.llm?.endpoint || '',
        llmModelName: config.llm?.model?.modelName || 'qwen-turbo',
        llmTemperature: config.llm?.model?.temperature || 0.7,
        llmMaxTokens: config.llm?.model?.maxTokens || 4096,
        // Stream config
        streamEnabled: config.stream?.enabled !== undefined ? config.stream.enabled : true,
        streamChunkSize: config.stream?.chunkSize || 1024,
        // Retry config
        retryMaxAttempts: config.retry?.maxAttempts || 3,
        retryBackoffMs: config.retry?.backoffMs || 1000,
        // Timeout config
        timeoutConnectMs: config.timeout?.connectMs || 5000,
        timeoutReadMs: config.timeout?.readMs || 60000,
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
      this.setState({ loading: true });
      const config = {
        enabled: values.enabled !== undefined ? values.enabled : true,
        defaultNamespace: values.defaultNamespace || 'public',
        llm: {
          provider: values.llmProvider || 'qwen',
          apiKey: values.llmApiKey || '',
          endpoint: values.llmEndpoint || '',
          model: {
            modelName: values.llmModelName || 'qwen-turbo',
            temperature: values.llmTemperature || 0.7,
            maxTokens: values.llmMaxTokens || 4096,
          },
        },
        stream: {
          enabled: values.streamEnabled !== undefined ? values.streamEnabled : true,
          chunkSize: values.streamChunkSize || 1024,
        },
        retry: {
          maxAttempts: values.retryMaxAttempts || 3,
          backoffMs: values.retryBackoffMs || 1000,
        },
        timeout: {
          connectMs: values.timeoutConnectMs || 5000,
          readMs: values.timeoutReadMs || 60000,
        },
      };

      // Send as JSON by stringifying the data and setting Content-Type
      const response = await requestUtils.put('v3/console/copilot/config', JSON.stringify(config), {
        headers: {
          'Content-Type': 'application/json',
        },
      });
      // Result format: {code: 0, message: "...", data: boolean}
      if (response && (response.code === 0 || response.code === 200)) {
        Message.success(locale.copilotConfigSaveSuccess || '保存成功');
        this.loadConfig();
      } else if (response && response.message) {
        Message.error(response.message || locale.copilotConfigSaveFailed || '保存配置失败');
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Failed to save Copilot config:', error);
      Message.error(locale.copilotConfigSaveFailed || '保存配置失败');
    } finally {
      this.setState({ loading: false });
    }
  };

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;

    return (
      <div className="copilot-config-container">
        <div className="setting-checkbox">
          <div className="setting-span">{locale.copilotConfigTitle || 'Nacos Copilot配置'}</div>
          <Form field={this.field} labelCol={{ span: 8 }} wrapperCol={{ span: 16 }}>
            {/* Basic Config */}
            <FormItem label={locale.copilotEnabled || '启用Copilot'}>
              <Switch
                {...init('enabled', {
                  valueName: 'checked',
                  initValue: true,
                })}
              />
            </FormItem>

            <FormItem label={locale.copilotDefaultNamespace || '默认命名空间'}>
              <Input
                {...init('defaultNamespace', {
                  initValue: 'public',
                })}
                placeholder="public"
              />
            </FormItem>

            {/* LLM Config */}
            <div style={{ marginTop: '20px', borderTop: '1px solid #eee', paddingTop: '20px' }}>
              <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '15px' }}>
                {locale.copilotLlmConfig || 'LLM配置'}
              </div>

              <FormItem label={locale.copilotLlmProvider || 'LLM提供者'}>
                <Select
                  {...init('llmProvider', {
                    initValue: 'qwen',
                  })}
                  dataSource={[
                    { value: 'qwen', label: 'Qwen' },
                    { value: 'claude', label: 'Claude' },
                    { value: 'openai', label: 'OpenAI' },
                    { value: 'custom', label: 'Custom' },
                  ]}
                />
              </FormItem>

              <FormItem label={locale.copilotLlmApiKey || 'API Key'}>
                <Input
                  {...init('llmApiKey', {
                    initValue: '',
                  })}
                  placeholder={locale.copilotLlmApiKeyPlaceholder || '请输入API Key'}
                  htmlType="password"
                />
              </FormItem>

              <FormItem label={locale.copilotLlmEndpoint || 'API Endpoint'}>
                <Input
                  {...init('llmEndpoint', {
                    initValue: '',
                  })}
                  placeholder={locale.copilotLlmEndpointPlaceholder || '请输入API Endpoint'}
                />
              </FormItem>

              <FormItem label={locale.copilotLlmModelName || '模型名称'}>
                <Input
                  {...init('llmModelName', {
                    initValue: 'qwen-turbo',
                  })}
                  placeholder="qwen-turbo"
                />
              </FormItem>

              <FormItem label={locale.copilotLlmTemperature || 'Temperature'}>
                <NumberPicker
                  {...init('llmTemperature', {
                    initValue: 0.7,
                  })}
                  min={0}
                  max={2}
                  step={0.1}
                />
              </FormItem>

              <FormItem label={locale.copilotLlmMaxTokens || 'Max Tokens'}>
                <NumberPicker
                  {...init('llmMaxTokens', {
                    initValue: 4096,
                  })}
                  min={1}
                  max={32768}
                />
              </FormItem>
            </div>

            {/* Stream Config */}
            <div style={{ marginTop: '20px', borderTop: '1px solid #eee', paddingTop: '20px' }}>
              <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '15px' }}>
                {locale.copilotStreamConfig || '流式配置'}
              </div>

              <FormItem label={locale.copilotStreamEnabled || '启用流式响应'}>
                <Switch
                  {...init('streamEnabled', {
                    valueName: 'checked',
                    initValue: true,
                  })}
                />
              </FormItem>

              <FormItem label={locale.copilotStreamChunkSize || 'Chunk Size'}>
                <NumberPicker
                  {...init('streamChunkSize', {
                    initValue: 1024,
                  })}
                  min={1}
                  max={8192}
                />
              </FormItem>
            </div>

            {/* Retry Config */}
            <div style={{ marginTop: '20px', borderTop: '1px solid #eee', paddingTop: '20px' }}>
              <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '15px' }}>
                {locale.copilotRetryConfig || '重试配置'}
              </div>

              <FormItem label={locale.copilotRetryMaxAttempts || '最大重试次数'}>
                <NumberPicker
                  {...init('retryMaxAttempts', {
                    initValue: 3,
                  })}
                  min={0}
                  max={10}
                />
              </FormItem>

              <FormItem label={locale.copilotRetryBackoffMs || '重试间隔(ms)'}>
                <NumberPicker
                  {...init('retryBackoffMs', {
                    initValue: 1000,
                  })}
                  min={0}
                  max={60000}
                />
              </FormItem>
            </div>

            {/* Timeout Config */}
            <div style={{ marginTop: '20px', borderTop: '1px solid #eee', paddingTop: '20px' }}>
              <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '15px' }}>
                {locale.copilotTimeoutConfig || '超时配置'}
              </div>

              <FormItem label={locale.copilotTimeoutConnectMs || '连接超时(ms)'}>
                <NumberPicker
                  {...init('timeoutConnectMs', {
                    initValue: 5000,
                  })}
                  min={1000}
                  max={60000}
                />
              </FormItem>

              <FormItem label={locale.copilotTimeoutReadMs || '读取超时(ms)'}>
                <NumberPicker
                  {...init('timeoutReadMs', {
                    initValue: 60000,
                  })}
                  min={1000}
                  max={300000}
                />
              </FormItem>
            </div>

            <div style={{ marginTop: '30px', textAlign: 'right' }}>
              <button
                type="button"
                onClick={this.saveConfig}
                disabled={this.state.loading}
                style={{
                  padding: '8px 24px',
                  backgroundColor: '#1890ff',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: this.state.loading ? 'not-allowed' : 'pointer',
                }}
              >
                {this.state.loading
                  ? locale.copilotConfigSaving || '保存中...'
                  : locale.copilotConfigSave || '保存配置'}
              </button>
            </div>
          </Form>
        </div>
      </div>
    );
  }
}

export default CopilotConfig;
