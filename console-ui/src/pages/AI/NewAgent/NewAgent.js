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
 *
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Card,
  ConfigProvider,
  Field,
  Form,
  Input,
  Message,
  Switch,
  Select,
  Icon,
  Grid,
  Divider,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import { getParams, request } from '@/globalLib';
import './NewAgent.scss';

const { Row, Col } = Grid;

@ConfigProvider.config
class NewAgent extends React.Component {
  static displayName = 'NewAgent';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);

    const agentName = getParams('name');
    const mode = getParams('mode');

    this.state = {
      loading: false,
      isEdit: mode === 'edit' && !!agentName,
      agentName,
      showAdvanced: false,
    };
  }

  componentDidMount() {
    if (this.state.isEdit) {
      this.loadAgentData();
    }
  }

  loadAgentData = () => {
    const { agentName } = this.state;
    const namespaceId = getParams('namespace') || '';

    this.setState({ loading: true });

    const params = new URLSearchParams();
    params.append('agentName', agentName);
    params.append('namespaceId', namespaceId);

    request({
      url: `v3/console/ai/a2a?${params.toString()}`,
      success: data => {
        this.setState({ loading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          const agentData = data.data;
          // 处理 capabilities 字段，确保正确解析三个能力开关的状态
          const capabilities = {
            streaming: false,
            pushNotifications: false,
            stateTransitionHistory: false,
          };

          if (agentData.capabilities && typeof agentData.capabilities === 'object') {
            capabilities.streaming = !!agentData.capabilities.streaming;
            capabilities.pushNotifications = !!agentData.capabilities.pushNotifications;
            capabilities.stateTransitionHistory = !!agentData.capabilities.stateTransitionHistory;
          }

          console.log(capabilities);

          this.field.setValues({
            name: agentData.name,
            description: agentData.description,
            version: agentData.version,
            protocolVersion: agentData.protocolVersion,
            url: agentData.url,
            preferredTransport: agentData.preferredTransport,
            iconUrl: agentData.iconUrl,
            documentationUrl: agentData.documentationUrl,
            organization: agentData.provider?.organization || '',
            providerUrl: agentData.provider?.url || '',
            // 设置能力配置开关的值
            streaming: capabilities.streaming,
            pushNotifications: capabilities.pushNotifications,
            stateTransitionHistory: capabilities.stateTransitionHistory,
            skills: agentData.skills ? JSON.stringify(agentData.skills, null, 2) : '',
            security: agentData.security ? JSON.stringify(agentData.security, null, 2) : '',
            securitySchemes: agentData.securitySchemes
              ? JSON.stringify(agentData.securitySchemes, null, 2)
              : '',
            defaultInputModes: agentData.defaultInputModes?.join(',') || '',
            defaultOutputModes: agentData.defaultOutputModes?.join(',') || '',
            additionalInterfaces: agentData.additionalInterfaces
              ? JSON.stringify(agentData.additionalInterfaces, null, 2)
              : '',
            supportsAuthenticatedExtendedCard: agentData.supportsAuthenticatedExtendedCard || false,
            setAsLatest: true,
          });
        } else {
          const { locale = {} } = this.props;
          Message.error(
            data?.message || locale.getAgentInfoFailed || 'Failed to get agent information'
          );
        }
      },
      error: () => {
        this.setState({ loading: false });
        const { locale = {} } = this.props;
        Message.error(locale.getAgentInfoFailed || 'Failed to get agent information');
      },
    });
  };

  handleSubmit = () => {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }

      this.setState({ loading: true });

      const namespaceId = getParams('namespace') || '';
      const { isEdit } = this.state;

      // 构建 agentCard 对象，包含所有需要放入 JSON 字符串的字段
      const agentCard = {
        name: values.name,
        description: values.description,
        version: values.version,
        protocolVersion: values.protocolVersion,
        url: values.url,
        preferredTransport: values.preferredTransport,
        iconUrl: values.iconUrl,
        documentationUrl: values.documentationUrl,
        // Add provider info if provided
        provider:
          values.organization || values.providerUrl
            ? {
                organization: values.organization || '',
                url: values.providerUrl || '',
              }
            : undefined,
      };

      // 构建 capabilities 对象，使用三个开关的值
      agentCard.capabilities = {
        streaming: !!values.streaming,
        pushNotifications: !!values.pushNotifications,
        stateTransitionHistory: !!values.stateTransitionHistory,
      };

      if (values.skills && values.skills.trim() && values.skills.trim() !== 'null') {
        try {
          const parsed = JSON.parse(values.skills.trim());
          if (parsed !== null && parsed !== undefined) {
            agentCard.skills = parsed;
          }
        } catch (e) {
          Message.error((locale.NewAgent?.skillsJsonError || 'Skills list JSON format error') + ': ' + e.message);
          this.setState({ loading: false });
          return;
        }
      }

      if (values.security && values.security.trim() && values.security.trim() !== 'null') {
        try {
          const parsed = JSON.parse(values.security.trim());
          if (parsed !== null && parsed !== undefined) {
            agentCard.security = parsed;
          }
        } catch (e) {
          Message.error((locale.NewAgent?.securityJsonError || 'Security configuration JSON format error') + ': ' + e.message);
          this.setState({ loading: false });
          return;
        }
      }

      if (
        values.securitySchemes &&
        values.securitySchemes.trim() &&
        values.securitySchemes.trim() !== 'null'
      ) {
        try {
          const parsed = JSON.parse(values.securitySchemes.trim());
          if (parsed !== null && parsed !== undefined) {
            agentCard.securitySchemes = parsed;
          }
        } catch (e) {
          Message.error((locale.NewAgent?.securitySchemesJsonError || 'Security schemes JSON format error') + ': ' + e.message);
          this.setState({ loading: false });
          return;
        }
      }

      if (
        values.additionalInterfaces &&
        values.additionalInterfaces.trim() &&
        values.additionalInterfaces.trim() !== 'null'
      ) {
        try {
          const parsed = JSON.parse(values.additionalInterfaces.trim());
          if (parsed !== null && parsed !== undefined) {
            agentCard.additionalInterfaces = parsed;
          }
        } catch (e) {
          Message.error((locale.NewAgent?.additionalInterfacesJsonError || 'Additional interfaces JSON format error') + ': ' + e.message);
          this.setState({ loading: false });
          return;
        }
      }

      if (values.defaultInputModes && values.defaultInputModes.trim()) {
        agentCard.defaultInputModes = values.defaultInputModes
          .split(',')
          .map(s => s.trim())
          .filter(s => s);
      }

      if (values.defaultOutputModes && values.defaultOutputModes.trim()) {
        agentCard.defaultOutputModes = values.defaultOutputModes
          .split(',')
          .map(s => s.trim())
          .filter(s => s);
      }

      if (values.supportsAuthenticatedExtendedCard !== undefined) {
        agentCard.supportsAuthenticatedExtendedCard = values.supportsAuthenticatedExtendedCard;
      }

      // 准备请求数据
      const requestData = {
        namespaceId: namespaceId,
        agentName: values.name,
        version: values.version,
        registrationType: isEdit ? '' : 'URL', // 默认使用 url 类型
        agentCard: JSON.stringify(agentCard),
      };

      // 更新模式下添加 setAsLatest 参数
      if (isEdit) {
        requestData.setAsLatest = values.setAsLatest;
      }

      const url = 'v3/console/ai/a2a';

      // 使用项目中已有的request方法发送请求，会自动处理认证信息
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
            const agentLocale = locale.AgentManagement || locale;
            Message.success(
              isEdit
                ? agentLocale.updateSuccess || 'Update successful'
                : agentLocale.createSuccess || 'Create successful'
            );

            setTimeout(() => {
              this.handleGoBack();
            }, 1000);
          } else {
            const agentLocale = locale.AgentManagement || locale;
            Message.error(
              data?.message ||
                (isEdit
                  ? agentLocale.updateFailed || 'Update failed'
                  : agentLocale.createFailed || 'Create failed')
            );
          }
        },
        error: error => {
          console.error('Request failed:', error);
          this.setState({ loading: false });
          const agentLocale = locale.AgentManagement || locale;
          Message.error(
            isEdit ? agentLocale.updateFailed || 'Update failed' : agentLocale.createFailed || 'Create failed'
          );
        },
      });
    });
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || '';
    this.props.history.push(`/agentManagement?namespace=${namespaceId}`);
  };

  toggleAdvanced = () => {
    this.setState({ showAdvanced: !this.state.showAdvanced });
  };

  validateRequired = (rule, value, callback) => {
    const { locale = {} } = this.props;
    if (!value || value.trim() === '') {
      callback(locale.requiredField || 'This field is required');
    } else {
      callback();
    }
  };

  validateUrl = (rule, value, callback) => {
    const { locale = {} } = this.props;
    if (value && value.trim()) {
      try {
        new URL(value);
        callback();
      } catch (e) {
        callback(locale.invalidUrl || 'Please enter a valid URL');
      }
    } else {
      callback();
    }
  };

  validateJson = (rule, value, callback) => {
    if (value && value.trim()) {
      const trimmedValue = value.trim();
      if (
        trimmedValue.includes('=') &&
        !trimmedValue.startsWith('{') &&
        !trimmedValue.startsWith('[')
      ) {
        callback(locale.NewAgent?.jsonErrorEqualSign || 'JSON format error: cannot contain equals sign (=), please use colon (:)');
        return;
      }
      try {
        const parsed = JSON.parse(trimmedValue);
        if (typeof parsed !== 'object') {
          callback(locale.NewAgent?.jsonErrorMustBeObject || 'JSON format error: must be a valid JSON object or array');
          return;
        }
        callback();
      } catch (e) {
        callback((locale.NewAgent?.jsonError || 'JSON format error') + ': ' + e.message);
      }
    } else {
      callback();
    }
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, isEdit, showAdvanced } = this.state;

    const formItemLayout = {
      labelCol: { span: 3 },
      wrapperCol: { span: 20 },
    };

    return (
      <div className="new-agent-container">
        <Row>
          <Col span={16}>
            <h1>{isEdit ? (locale.NewAgent?.editAgent || 'Edit Agent') : (locale.NewAgent?.newAgent || 'New Agent')}</h1>
          </Col>
          <Col span={8}>
            <div style={{ textAlign: 'right', marginTop: 10 }}>
              <Button
                type="primary"
                onClick={this.handleSubmit}
                loading={loading}
                style={{ marginRight: 10 }}
              >
                {isEdit ? (locale.NewAgent?.update || 'Update') : (locale.NewAgent?.create || 'Create')}
              </Button>
              <Button onClick={this.handleGoBack}>{locale.NewAgent?.cancel || 'Cancel'}</Button>
            </div>
          </Col>
        </Row>

        <Form field={this.field} {...formItemLayout} className="new-agent-form">
          <Form.Item label={locale.NewAgent?.namespace || 'Namespace'}>
            <p>{getParams('namespace') || 'public'}</p>
          </Form.Item>

          {/* 主要信息放在最上面 */}
          <Form.Item
            label={locale.NewAgent?.agentName || 'Agent Name'}
            required
            validator={this.validateRequired}
            help={locale.NewAgent?.agentNameHelp || 'Unique identifier for the agent, cannot be modified after creation'}
          >
            <Input
              name="name"
              placeholder={locale.NewAgent?.agentNamePlaceholder || 'Enter agent name, e.g., weather-agent'}
              disabled={isEdit}
              maxLength={255}
            />
          </Form.Item>

          <Form.Item
            label={locale.NewAgent?.version || 'Version'}
            required
            validator={this.validateRequired}
            help={locale.NewAgent?.versionHelp || 'Follow semantic versioning, e.g., 1.0.0'}
          >
            <Input name="version" placeholder="1.0.0" maxLength={50} />
          </Form.Item>

          <Form.Item
            label={locale.NewAgent?.url || 'Service URL'}
            required
            validator={[this.validateRequired, this.validateUrl]}
            help={locale.NewAgent?.urlHelp || 'Complete URL address of the agent service'}
          >
            <Input name="url" placeholder="https://api.example.com/agent" maxLength={500} />
          </Form.Item>

          <Form.Item
            label={locale.NewAgent?.protocolVersion || 'Protocol Version'}
            required
            validator={this.validateRequired}
            help={locale.NewAgent?.protocolVersionHelp || 'Agent protocol version, use latest by default'}
          >
            <Input name="protocolVersion" placeholder="0.3.0" maxLength={50} />
          </Form.Item>

          <Form.Item
            label={locale.NewAgent?.preferredTransport || 'Preferred Transport'}
            required
            validator={this.validateRequired}
            help={locale.NewAgent?.preferredTransportHelp || 'Transport protocol used for agent communication'}
          >
            <Select
              name="preferredTransport"
              placeholder={locale.NewAgent?.preferredTransportPlaceholder || 'Select transport protocol'}
              dataSource={[
                { value: 'JSONRPC', label: 'JSONRPC' },
                { value: 'GRPC', label: 'GRPC' },
                { value: 'HTTP+JSON', label: 'HTTP_JSON' },
              ]}
            />
          </Form.Item>

          <Form.Item label={locale.NewAgent?.description || 'Description'} help={locale.NewAgent?.descriptionHelp || 'Brief description of the agent\'s functionality and purpose'}>
            <Input.TextArea
              name="description"
              placeholder={locale.NewAgent?.descriptionPlaceholder || 'Enter agent function description...'}
              rows={3}
              maxLength={1000}
            />
          </Form.Item>

          <Form.Item label={locale.NewAgent?.defaultInputModes || 'Input Modes'} help={locale.NewAgent?.defaultInputModesHelp || 'Default input modes supported by the agent, comma-separated'}>
            <Input name="defaultInputModes" placeholder="text,audio,image" maxLength={255} />
          </Form.Item>

          <Form.Item label={locale.NewAgent?.defaultOutputModes || 'Output Modes'} help={locale.NewAgent?.defaultOutputModesHelp || 'Default output modes supported by the agent, comma-separated'}>
            <Input name="defaultOutputModes" placeholder="text,audio,image" maxLength={255} />
          </Form.Item>

          {/* 将原来的capabilities JSON输入框替换为横向排列的三个独立开关 */}
          <Form.Item label={locale.NewAgent?.capabilities || 'Capabilities'} help={locale.NewAgent?.capabilitiesHelp || 'Core capabilities supported by the agent'}>
            <div className="capabilities-container">
              <div className="capability-item">
                <div className="capability-label">{locale.NewAgent?.streaming || 'Streaming'}</div>
                <div className="capability-switch">
                  <Switch
                    {...this.field.init('streaming', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    name="streaming"
                  />
                </div>
                <div className="capability-description">{locale.NewAgent?.streamingHelp || 'Whether streaming data transfer is supported'}</div>
              </div>

              <div className="capability-item">
                <div className="capability-label">{locale.NewAgent?.pushNotifications || 'Push Notifications'}</div>
                <div className="capability-switch">
                  <Switch
                    {...this.field.init('pushNotifications', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    name="pushNotifications"
                  />
                </div>
                <div className="capability-description">{locale.NewAgent?.pushNotificationsHelp || 'Whether push notification functionality is supported'}</div>
              </div>

              <div className="capability-item">
                <div className="capability-label">{locale.NewAgent?.stateTransitionHistory || 'State History'}</div>
                <div className="capability-switch">
                  <Switch
                    {...this.field.init('stateTransitionHistory', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    name="stateTransitionHistory"
                  />
                </div>
                <div className="capability-description">{locale.NewAgent?.stateTransitionHistoryHelp || 'Whether recording state transition history is supported'}</div>
              </div>
            </div>
          </Form.Item>

          <Form.Item label={locale.NewAgent?.skills || 'Skills'} validator={this.validateJson} help={locale.NewAgent?.skillsHelp || 'List of skills possessed by the agent'}>
            <Input.TextArea
              name="skills"
              placeholder={locale.NewAgent?.skillsPlaceholder || '[{"name": "weather_query", "description": "Query weather information"}]'}
              rows={4}
            />
          </Form.Item>

          {/* 高级配置 */}
          <Divider style={{ margin: '30px 0' }}>
            <span>{locale.NewAgent?.advancedConfig || 'Advanced Configuration'}</span>
            <Button text size="small" onClick={this.toggleAdvanced} style={{ marginLeft: 10 }}>
              {showAdvanced ? (locale.NewAgent?.collapse || 'Collapse') : (locale.NewAgent?.expand || 'Expand')}
              <Icon type={showAdvanced ? 'arrow-up' : 'arrow-down'} style={{ marginLeft: 4 }} />
            </Button>
          </Divider>

          {showAdvanced && (
            <>
              <Form.Item
                label={locale.NewAgent?.iconUrl || 'Icon URL'}
                validator={this.validateUrl}
                help={locale.NewAgent?.iconUrlHelp || 'Icon URL of the agent for UI display'}
              >
                <Input name="iconUrl" placeholder="https://example.com/icon.png" maxLength={500} />
              </Form.Item>

              <Form.Item label={locale.NewAgent?.documentationUrl || 'Documentation URL'} validator={this.validateUrl} help={locale.NewAgent?.documentationUrlHelp || 'Documentation URL of the agent'}>
                <Input
                  name="documentationUrl"
                  placeholder="https://docs.example.com/agent"
                  maxLength={500}
                />
              </Form.Item>

              <Form.Item label={locale.NewAgent?.organization || 'Provider Name'} help={locale.NewAgent?.organizationHelp || 'Name of the agent provider'}>
                <Input name="organization" placeholder={locale.NewAgent?.organizationPlaceholder || 'Enter provider name'} maxLength={255} />
              </Form.Item>

              <Form.Item label={locale.NewAgent?.providerUrl || 'Provider URL'} validator={this.validateUrl} help={locale.NewAgent?.providerUrlHelp || 'Official website URL of the provider'}>
                <Input
                  name="providerUrl"
                  placeholder="https://provider.example.com"
                  maxLength={500}
                />
              </Form.Item>

              <Form.Item
                label="security"
                validator={this.validateJson}
                help={locale.NewAgent?.securityHelp || 'Security authentication configuration for the agent (JSON format)'}
              >
                <Input.TextArea
                  name="security"
                  placeholder='[{"apiKey": ["read", "write"]}]'
                  rows={3}
                />
              </Form.Item>

              <Form.Item
                label="securitySchemes"
                validator={this.validateJson}
                help={locale.NewAgent?.securitySchemesHelp || 'Security schemes configuration for the agent (JSON format)'}
              >
                <Input.TextArea
                  name="securitySchemes"
                  placeholder={locale.NewAgent?.securitySchemesPlaceholder || '{"type": "apiKey", "description": "API key authentication"}'}
                  rows={3}
                />
              </Form.Item>

              <Form.Item
                label={locale.NewAgent?.additionalInterfaces || 'Additional Interfaces'}
                validator={this.validateJson}
                help={locale.NewAgent?.additionalInterfacesHelp || 'Additional interface configuration for the agent (JSON format)'}
              >
                <Input.TextArea
                  name="additionalInterfaces"
                  placeholder='[{"transport": "sse", "uri": "/sse"}]'
                  rows={3}
                />
              </Form.Item>

              <Form.Item label={locale.NewAgent?.supportsAuthenticatedExtendedCard || 'Extended Card Support'} help={locale.NewAgent?.supportsAuthenticatedExtendedCardHelp || 'Whether authenticated extended card functionality is supported'}>
                <Switch name="supportsAuthenticatedExtendedCard" defaultChecked={false} />
              </Form.Item>
            </>
          )}

          {/* 版本设置 - 仅编辑模式显示 */}
          {isEdit && (
            <>
              <Form.Item label={locale.NewAgent?.setAsLatest || 'Set as Latest Version'} help={locale.NewAgent?.setAsLatestHelp || 'When enabled, this version will become the published version'}>
                <Switch name="setAsLatest" defaultChecked={false} />
              </Form.Item>
            </>
          )}
        </Form>
      </div>
    );
  }
}

export default NewAgent;
