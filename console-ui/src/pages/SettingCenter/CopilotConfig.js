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
import { Form, Input, Message, Field, Select, Balloon, Icon } from '@alifd/next';
import requestUtils from '../../utils/request';

const FormItem = Form.Item;

const findBaseUrl = (provider, protocol, region) => {
  const protocolMetadata = provider?.protocols?.find(item => item.name === protocol);
  return protocolMetadata?.endpoints?.find(item => item.region === region)?.baseUrl || '';
};

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
      providers: [],
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
      const [response, providersResponse] = await Promise.all([
        requestUtils.get('v3/console/copilot/config'),
        requestUtils.get('v3/console/copilot/config/providers'),
      ]);
      // Result format: {code: 0, message: "...", data: {...}}
      const config = (response && response.data) ? response.data : (response || {});
      let providers = [];
      if (providersResponse && providersResponse.data) {
        providers = providersResponse.data;
      } else if (Array.isArray(providersResponse)) {
        providers = providersResponse;
      }
      const providerName = config.provider || 'DashScope';
      const providerMetadata = providers.find(item => item.name === providerName);
      const protocol = config.protocol || providerMetadata?.defaultProtocol || '';
      const region = config.region || providerMetadata?.defaultRegion || '';
      this.setState({ config, providers });
      // Process studioUrl: remove trailing slash if exists
      let studioUrl = config.studioUrl || '';
      if (studioUrl && studioUrl.endsWith('/')) {
        studioUrl = studioUrl.slice(0, -1);
      }
      // Set all console-managed configuration values.
      this.field.setValues({
        apiKey: config.apiKey || '',
        provider: providerName,
        protocol,
        region,
        model: config.model || providerMetadata?.defaultModel || 'qwen-turbo',
        baseUrl: config.baseUrl || findBaseUrl(providerMetadata, protocol, region),
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
      // Send all console-managed configuration values.
      const config = {
        apiKey: values.apiKey || '',
        provider: values.provider || 'DashScope',
        protocol: values.protocol || '',
        region: values.region || '',
        model: values.model || 'qwen-turbo',
        baseUrl: values.baseUrl || '',
        studioUrl,
        studioProject: values.studioProject || 'NacosCopilot',
      };

      // Send as JSON by stringifying the data and setting Content-Type
      const response = await requestUtils.post('v3/console/copilot/config', JSON.stringify(config), {
        headers: {
          'Content-Type': 'application/json',
        },
      });
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

  handleProviderChange = value => {
    const provider = this.state.providers.find(item => item.name === value);
    const protocol = provider?.defaultProtocol || '';
    const region = provider?.defaultRegion || '';
    this.field.setValues({
      provider: value,
      protocol,
      region,
      model: provider?.defaultModel || 'qwen-turbo',
      baseUrl: findBaseUrl(provider, protocol, region),
    });
  };

  handleProtocolChange = value => {
    const providerName = this.field.getValue('provider') || 'DashScope';
    const provider = this.state.providers.find(item => item.name === providerName);
    const region = this.field.getValue('region') || provider?.defaultRegion || '';
    this.field.setValues({
      protocol: value,
      baseUrl: findBaseUrl(provider, value, region),
    });
  };

  handleRegionChange = value => {
    const providerName = this.field.getValue('provider') || 'DashScope';
    const provider = this.state.providers.find(item => item.name === providerName);
    const protocol = this.field.getValue('protocol') || provider?.defaultProtocol || '';
    this.field.setValues({
      region: value,
      baseUrl: findBaseUrl(provider, protocol, value),
    });
  };

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const providerName = this.field.getValue('provider') || 'DashScope';
    const provider = this.state.providers.find(item => item.name === providerName);
    const protocolName = this.field.getValue('protocol') || provider?.defaultProtocol || '';
    const protocol = provider?.protocols?.find(item => item.name === protocolName);
    const region = this.field.getValue('region') || provider?.defaultRegion || '';
    const modelOptions = (provider?.models || []).map(item => ({
      value: item.modelId,
      label: item.modelId,
    }));

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
            placeholder={locale.copilotLlmApiKeyPlaceholder || '请输入API Key（建议通过环境变量设置）'}
            htmlType="password"
          />
        </FormItem>

        <FormItem label="Provider">
          <Select
            {...init('provider', {
              initValue: 'DashScope',
            })}
            dataSource={this.state.providers.map(item => ({ value: item.name, label: item.name }))}
            onChange={this.handleProviderChange}
            placeholder="Select provider"
            style={{ width: '100%' }}
          />
        </FormItem>

        {provider?.protocols?.length > 0 && (
          <FormItem label="Protocol">
            <Select
              {...init('protocol', {
                initValue: provider.defaultProtocol || '',
              })}
              dataSource={provider.protocols.map(item => ({ value: item.name, label: item.name }))}
              onChange={this.handleProtocolChange}
              placeholder="Select protocol"
              style={{ width: '100%' }}
            />
          </FormItem>
        )}

        {protocol && (
          <FormItem label="Region">
            <Select
              {...init('region', {
                initValue: provider.defaultRegion || '',
              })}
              dataSource={protocol.endpoints.map(item => ({
                value: item.region,
                label: item.region,
              }))}
              onChange={this.handleRegionChange}
              placeholder="Select region"
              style={{ width: '100%' }}
            />
          </FormItem>
        )}

        <FormItem label={locale.copilotLlmModelName || 'Model'}>
          <Select
            {...init('model', {
              initValue: 'qwen-turbo',
            })}
            dataSource={modelOptions}
            placeholder={locale.copilotLlmModelNamePlaceholder || '请选择模型'}
            style={{ width: '100%' }}
          />
        </FormItem>

        {protocol && (
          <FormItem label="Base URL">
            <Input
              {...init('baseUrl', {
                initValue: '',
              })}
              placeholder={findBaseUrl(provider, protocolName, region)}
            />
          </FormItem>
        )}

        <FormItem label={locale.copilotStudioUrl || 'Studio URL'}>
          <Input
            {...init('studioUrl', {
              initValue: '',
            })}
            placeholder={locale.copilotStudioUrlPlaceholder || '请输入 AgentScope Studio 地址，例如: http://localhost:3000'}
          />
        </FormItem>

        <FormItem label={locale.copilotStudioProject || 'Studio Project'}>
          <Input
            {...init('studioProject', {
              initValue: 'NacosCopilot',
            })}
            placeholder={locale.copilotStudioProjectPlaceholder || '请输入 Studio 项目名称，例如: NacosCopilot'}
          />
        </FormItem>
        </Form>
      </div>
    );
  }
}

export default CopilotConfig;
