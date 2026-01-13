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
          Message.error('技能列表JSON格式错误: ' + e.message);
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
          Message.error('安全配置JSON格式错误: ' + e.message);
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
          Message.error('安全模式配置JSON格式错误: ' + e.message);
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
          Message.error('额外接口JSON格式错误: ' + e.message);
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
                ? agentLocale.updateSuccess || '更新成功'
                : agentLocale.createSuccess || '创建成功'
            );

            setTimeout(() => {
              this.handleGoBack();
            }, 1000);
          } else {
            const agentLocale = locale.AgentManagement || locale;
            Message.error(
              data?.message ||
                (isEdit
                  ? agentLocale.updateFailed || '更新失败'
                  : agentLocale.createFailed || '创建失败')
            );
          }
        },
        error: error => {
          console.error('Request failed:', error);
          this.setState({ loading: false });
          const agentLocale = locale.AgentManagement || locale;
          Message.error(
            isEdit ? agentLocale.updateFailed || '更新失败' : agentLocale.createFailed || '创建失败'
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
        callback('JSON格式错误：不能包含等号(=)字符，请使用冒号(:)');
        return;
      }
      try {
        const parsed = JSON.parse(trimmedValue);
        if (typeof parsed !== 'object') {
          callback('JSON格式错误：必须是有效的JSON对象或数组');
          return;
        }
        callback();
      } catch (e) {
        callback('JSON格式错误：' + e.message);
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
            <h1>{isEdit ? '编辑Agent' : '新建Agent'}</h1>
          </Col>
          <Col span={8}>
            <div style={{ textAlign: 'right', marginTop: 10 }}>
              <Button
                type="primary"
                onClick={this.handleSubmit}
                loading={loading}
                style={{ marginRight: 10 }}
              >
                {isEdit ? '更新' : '创建'}
              </Button>
              <Button onClick={this.handleGoBack}>取消</Button>
            </div>
          </Col>
        </Row>

        <Form field={this.field} {...formItemLayout} className="new-agent-form">
          <Form.Item label="命名空间">
            <p>{getParams('namespace') || 'public'}</p>
          </Form.Item>

          {/* 主要信息放在最上面 */}
          <Form.Item
            label="Agent名称"
            required
            validator={this.validateRequired}
            help="Agent的唯一标识符，创建后不可修改"
          >
            <Input
              name="name"
              placeholder="请输入Agent名称，如：weather-agent"
              disabled={isEdit}
              maxLength={255}
            />
          </Form.Item>

          <Form.Item
            label="版本号"
            required
            validator={this.validateRequired}
            help="遵循语义化版本规范，如：1.0.0"
          >
            <Input name="version" placeholder="1.0.0" maxLength={50} />
          </Form.Item>

          <Form.Item
            label="服务地址"
            required
            validator={[this.validateRequired, this.validateUrl]}
            help="Agent服务的完整URL地址"
          >
            <Input name="url" placeholder="https://api.example.com/agent" maxLength={500} />
          </Form.Item>

          <Form.Item
            label="协议版本"
            required
            validator={this.validateRequired}
            help="Agent协议版本，默认使用最新版本"
          >
            <Input name="protocolVersion" placeholder="0.3.0" maxLength={50} />
          </Form.Item>

          <Form.Item
            label="传输协议"
            required
            validator={this.validateRequired}
            help="Agent通信使用的传输协议"
          >
            <Select
              name="preferredTransport"
              placeholder="请选择传输协议"
              dataSource={[
                { value: 'JSONRPC', label: 'JSONRPC' },
                { value: 'GRPC', label: 'GRPC' },
                { value: 'HTTP+JSON', label: 'HTTP_JSON' },
              ]}
            />
          </Form.Item>

          <Form.Item label="描述信息" help="简要描述Agent的功能和用途">
            <Input.TextArea
              name="description"
              placeholder="请输入Agent的功能描述..."
              rows={3}
              maxLength={1000}
            />
          </Form.Item>

          <Form.Item label="输入模式" help="Agent支持的默认输入模式，用逗号分隔">
            <Input name="defaultInputModes" placeholder="text,audio,image" maxLength={255} />
          </Form.Item>

          <Form.Item label="输出模式" help="Agent支持的默认输出模式，用逗号分隔">
            <Input name="defaultOutputModes" placeholder="text,audio,image" maxLength={255} />
          </Form.Item>

          {/* 将原来的capabilities JSON输入框替换为横向排列的三个独立开关 */}
          <Form.Item label="能力配置" help="Agent支持的核心能力配置">
            <div className="capabilities-container">
              <div className="capability-item">
                <div className="capability-label">流式传输</div>
                <div className="capability-switch">
                  <Switch
                    {...this.field.init('streaming', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    name="streaming"
                  />
                </div>
                <div className="capability-description">是否支持流式数据传输</div>
              </div>

              <div className="capability-item">
                <div className="capability-label">推送通知</div>
                <div className="capability-switch">
                  <Switch
                    {...this.field.init('pushNotifications', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    name="pushNotifications"
                  />
                </div>
                <div className="capability-description">是否支持推送通知功能</div>
              </div>

              <div className="capability-item">
                <div className="capability-label">状态历史</div>
                <div className="capability-switch">
                  <Switch
                    {...this.field.init('stateTransitionHistory', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    name="stateTransitionHistory"
                  />
                </div>
                <div className="capability-description">是否支持记录状态转换历史</div>
              </div>
            </div>
          </Form.Item>

          <Form.Item label="技能列表" validator={this.validateJson} help="Agent具备的技能清单">
            <Input.TextArea
              name="skills"
              placeholder='[{"name": "weather_query", "description": "查询天气信息"}]'
              rows={4}
            />
          </Form.Item>

          {/* 高级配置 */}
          <Divider style={{ margin: '30px 0' }}>
            <span>高级配置</span>
            <Button text size="small" onClick={this.toggleAdvanced} style={{ marginLeft: 10 }}>
              {showAdvanced ? '收起' : '展开'}
              <Icon type={showAdvanced ? 'arrow-up' : 'arrow-down'} style={{ marginLeft: 4 }} />
            </Button>
          </Divider>

          {showAdvanced && (
            <>
              <Form.Item
                label="图标URL"
                validator={this.validateUrl}
                help="Agent的图标地址，用于界面展示"
              >
                <Input name="iconUrl" placeholder="https://example.com/icon.png" maxLength={500} />
              </Form.Item>

              <Form.Item label="文档URL" validator={this.validateUrl} help="Agent的使用文档地址">
                <Input
                  name="documentationUrl"
                  placeholder="https://docs.example.com/agent"
                  maxLength={500}
                />
              </Form.Item>

              <Form.Item label="提供商名称" help="Agent提供商的名称">
                <Input name="organization" placeholder="请输入提供商名称" maxLength={255} />
              </Form.Item>

              <Form.Item label="提供商URL" validator={this.validateUrl} help="提供商的官方网站地址">
                <Input
                  name="providerUrl"
                  placeholder="https://provider.example.com"
                  maxLength={500}
                />
              </Form.Item>

              <Form.Item
                label="security"
                validator={this.validateJson}
                help="Agent的安全认证相关配置 (JSON格式)"
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
                help="Agent的安全模式配置 (JSON格式)"
              >
                <Input.TextArea
                  name="securitySchemes"
                  placeholder='{"type": "apiKey", "description": "API密钥认证"}'
                  rows={3}
                />
              </Form.Item>

              <Form.Item
                label="额外接口"
                validator={this.validateJson}
                help="Agent的额外接口配置 (JSON格式)"
              >
                <Input.TextArea
                  name="additionalInterfaces"
                  placeholder='[{"transport": "sse", "uri": "/sse"}]'
                  rows={3}
                />
              </Form.Item>

              <Form.Item label="扩展卡片支持" help="是否支持认证扩展卡片功能">
                <Switch name="supportsAuthenticatedExtendedCard" defaultChecked={false} />
              </Form.Item>
            </>
          )}

          {/* 版本设置 - 仅编辑模式显示 */}
          {isEdit && (
            <>
              <Form.Item label="设为最新版本" help="开启后，此版本将成为发布版本">
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
