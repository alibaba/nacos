/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
  Card,
  ConfigProvider,
  Loading,
  Message,
  Tag,
  Table,
  Grid,
  Switch,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import { getParams, request } from '@/globalLib';
import '../NewAgent/NewAgent.scss';

@ConfigProvider.config
class AgentDetail extends React.Component {
  static displayName = 'AgentDetail';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      agentData: null,
      versionList: [],
    };
  }

  componentDidMount() {
    this.loadAgentDetail();
    this.loadVersionList();
  }

  // 添加 componentDidUpdate 来监听路由参数变化
  componentDidUpdate(prevProps) {
    // 检查URL参数是否发生变化
    const prevSearch = prevProps.location?.search || '';
    const currentSearch = this.props.location?.search || '';

    // 如果URL参数发生变化，重新加载数据
    if (prevSearch !== currentSearch) {
      this.loadAgentDetail();
      // 版本列表不需要重复加载，因为同一个agent的不同版本都在一个列表中
    }
  }

  loadAgentDetail = () => {
    const { locale = {} } = this.props;
    const agentName = getParams('name');
    const namespaceId = getParams('namespace') || 'public';
    const version = getParams('version'); // 获取URL中的版本参数

    if (!agentName) {
      Message.error(locale.AgentDetail?.agentNameRequired || 'Agent name cannot be empty');
      return;
    }

    this.setState({ loading: true });

    const params = new URLSearchParams();
    params.append('agentName', agentName);
    params.append('namespaceId', namespaceId);

    // 如果有版本号参数，则添加到请求参数中
    if (version) {
      params.append('version', version);
    }

    request({
      url: `v3/console/ai/a2a?${params.toString()}`,
      success: data => {
        console.log('Agent detail API response:', data);
        this.setState({ loading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          const agentData = data.data;
          this.setState(
            {
              agentData,
            },
            () => {
              console.log('State updated, agentData:', this.state.agentData);
            }
          );
        } else {
          console.log('Failed to load agent detail:', data);
          const { locale = {} } = this.props;
          Message.error(data?.message || locale.AgentDetail?.getAgentDetailFailed || 'Failed to get agent details');
        }
      },
      error: () => {
        this.setState({ loading: false });
        const { locale = {} } = this.props;
        Message.error(locale.AgentDetail?.getAgentDetailFailed || 'Failed to get agent details');
      },
    });
  };

  loadVersionList = () => {
    const agentName = getParams('name');
    const namespaceId = getParams('namespace') || 'public';

    if (!agentName) {
      return;
    }

    const params = new URLSearchParams();
    params.append('agentName', agentName);
    params.append('namespaceId', namespaceId);

    request({
      url: `v3/console/ai/a2a/version/list?${params.toString()}`,
      success: data => {
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          this.setState({
            versionList: data.data,
          });
        } else {
          console.log('Failed to load version list:', data);
          const { locale = {} } = this.props;
          Message.error(data?.message || locale.AgentDetail?.getVersionListFailed || 'Failed to get version list');
        }
      },
      error: () => {
        const { locale = {} } = this.props;
        Message.error(locale.AgentDetail?.getVersionListFailed || 'Failed to get version list');
      },
    });
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/agentManagement?namespace=${namespaceId}`);
  };

  handleEdit = () => {
    const agentName = getParams('name');
    const namespaceId = getParams('namespace') || 'public';
    this.props.history.push(`/newAgent?namespace=${namespaceId}&name=${agentName}&mode=edit`);
  };

  formatTime = timeStr => {
    if (!timeStr) return '--';
    try {
      const date = new Date(timeStr);
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch (e) {
      return timeStr;
    }
  };

  formatSkills = skills => {
    if (!skills) return '--';
    if (Array.isArray(skills)) {
      return skills.map(skill => skill.name || skill).join(', ') || '--';
    }
    if (typeof skills === 'object') {
      return JSON.stringify(skills, null, 2);
    }
    return skills.toString();
  };

  formatCapabilities = capabilities => {
    const { locale = {} } = this.props;
    if (!capabilities) return '--';
    if (typeof capabilities === 'object') {
      const caps = [];
      if (capabilities.sampling) caps.push(locale.AgentDetail?.sampling || 'Sampling');
      if (capabilities.tools && Array.isArray(capabilities.tools)) {
        caps.push(`${locale.AgentDetail?.tools || 'Tools'}: ${capabilities.tools.join(', ')}`);
      }
      return caps.length > 0 ? caps.join(', ') : JSON.stringify(capabilities, null, 2);
    }
    return capabilities.toString();
  };

  formatModes = modes => {
    if (!modes) return '--';
    if (Array.isArray(modes)) {
      return modes.join(', ') || '--';
    }
    return modes.toString();
  };

  renderSkillsContent = skills => {
    const { locale = {} } = this.props;
    if (!skills || (Array.isArray(skills) && skills.length === 0)) {
      return (
        <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>{locale.AgentDetail?.noSkillsConfig || 'No skills configured'}</div>
      );
    }

    if (Array.isArray(skills)) {
      return (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))',
            gap: '16px',
          }}
        >
          {skills.map((skill, index) => {
            const name = (skill && skill.name) || `${locale.AgentDetail?.skill || 'Skill'} ${index + 1}`;
            const id = (skill && skill.id) || '--';
            const desc = (skill && skill.description) || '--';
            const tags = skill && Array.isArray(skill.tags) ? skill.tags : [];
            const inputModes = skill && Array.isArray(skill.inputModes) ? skill.inputModes : [];
            const outputModes = skill && Array.isArray(skill.outputModes) ? skill.outputModes : [];
            const examples = skill && Array.isArray(skill.examples) ? skill.examples : [];
            const examplesInline = examples.slice(0, 2).join(', ');
            const moreSuffix = examples.length > 2 ? `, ...${locale.AgentDetail?.andMore || 'and'} ${examples.length} ${locale.AgentDetail?.items || 'items'}` : '';

            const Row = (label, content) => (
              <div style={{ display: 'flex', gap: 8, margin: '6px 0', alignItems: 'flex-start' }}>
                <div style={{ width: 72, color: '#666' }}>{label}：</div>
                <div style={{ flex: 1 }}>{content || '--'}</div>
              </div>
            );

            return (
              <div
                key={skill?.id || index}
                style={{
                  padding: '16px',
                  backgroundColor: '#fafafa',
                  borderRadius: '8px',
                  border: '1px solid #e8e8e8',
                }}
              >
                {Row(locale.AgentDetail?.name || 'Name', <strong style={{ fontSize: 16, color: '#333' }}>{name}</strong>)}
                {Row('ID', <span style={{}}>{id}</span>)}
                {Row(
                  locale.AgentDetail?.description || 'Description',
                  <span
                    title={desc}
                    style={{
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      lineHeight: 1.4,
                    }}
                  >
                    {desc}
                  </span>
                )}
                {Row(
                  locale.AgentDetail?.tags || 'Tags',
                  tags.length
                    ? tags.map((t, i) => (
                        <Tag key={i} size="small" style={{ marginRight: 4, marginBottom: 4 }}>
                          {t}
                        </Tag>
                      ))
                    : null
                )}
                {Row(
                  locale.AgentDetail?.inputModes || 'Input Modes',
                  inputModes.length
                    ? inputModes.map((m, i) => (
                        <Tag key={i} size="small" style={{ marginRight: 4 }}>
                          {m}
                        </Tag>
                      ))
                    : null
                )}
                {Row(
                  locale.AgentDetail?.outputModes || 'Output Modes',
                  outputModes.length
                    ? outputModes.map((m, i) => (
                        <Tag key={i} size="small" type="primary" style={{ marginRight: 4 }}>
                          {m}
                        </Tag>
                      ))
                    : null
                )}
                {Row(
                  locale.AgentDetail?.examples || 'Examples',
                  examples.length ? (
                    <span style={{ whiteSpace: 'pre-wrap' }}>
                      {examplesInline}
                      {moreSuffix}
                    </span>
                  ) : null
                )}
              </div>
            );
          })}
        </div>
      );
    }

    // 兜底：对象或字符串直接展示为 JSON
    const skillsValue =
      typeof skills === 'object' ? JSON.stringify(skills, null, 2) : String(skills);
    return (
      <pre
        style={{
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          margin: 0,
          padding: 12,
          backgroundColor: '#f5f5f5',
          borderRadius: 6,
          border: '1px solid #e8e8e8',
          fontSize: 13,
        }}
      >
        {skillsValue}
      </pre>
    );
  };

  renderCapabilitiesContent = capabilities => {
    const { locale = {} } = this.props;
    if (!capabilities) {
      return (
        <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>{locale.AgentDetail?.noCapabilitiesConfig || 'No capabilities configured'}</div>
      );
    }

    if (typeof capabilities === 'object') {
      // 解析三个核心能力的值，默认为false
      const streaming = !!capabilities.streaming;
      const pushNotifications = !!capabilities.pushNotifications;
      const stateTransitionHistory = !!capabilities.stateTransitionHistory;

      return (
        <div className="new-agent-container">
          <div className="capabilities-container">
            <div className="capability-item">
              <div className="capability-label">{locale.AgentDetail?.streaming || 'Streaming'}</div>
              <div className="capability-switch">
                <Switch
                  checked={streaming}
                  disabled={true}
                  checkedChildren={locale.AgentDetail?.enabled || 'Enabled'}
                  unCheckedChildren={locale.AgentDetail?.disabled || 'Disabled'}
                />
              </div>
              <div className="capability-description">{locale.AgentDetail?.streamingHelp || 'Whether streaming data transfer is supported'}</div>
            </div>

            <div className="capability-item">
              <div className="capability-label">{locale.AgentDetail?.pushNotifications || 'Push Notifications'}</div>
              <div className="capability-switch">
                <Switch
                  checked={pushNotifications}
                  disabled={true}
                  checkedChildren={locale.AgentDetail?.enabled || 'Enabled'}
                  unCheckedChildren={locale.AgentDetail?.disabled || 'Disabled'}
                />
              </div>
              <div className="capability-description">{locale.AgentDetail?.pushNotificationsHelp || 'Whether push notification functionality is supported'}</div>
            </div>

            <div className="capability-item">
              <div className="capability-label">{locale.AgentDetail?.stateTransitionHistory || 'State History'}</div>
              <div className="capability-switch">
                <Switch
                  checked={stateTransitionHistory}
                  disabled={true}
                  checkedChildren={locale.AgentDetail?.enabled || 'Enabled'}
                  unCheckedChildren={locale.AgentDetail?.disabled || 'Disabled'}
                />
              </div>
              <div className="capability-description">{locale.AgentDetail?.stateTransitionHistoryHelp || 'Whether recording state transition history is supported'}</div>
            </div>
          </div>
        </div>
      );
    }

    // 兜底：如果capabilities不是对象，则以JSON形式展示
    return (
      <pre
        style={{
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          margin: 0,
          padding: '12px',
          backgroundColor: '#f5f5f5',
          borderRadius: '6px',
          border: '1px solid #e8e8e8',
          fontSize: '13px',
        }}
      >
        {typeof capabilities === 'object'
          ? JSON.stringify(capabilities, null, 2)
          : String(capabilities)}
      </pre>
    );
  };

  renderDetailItem = (label, value, type = 'text') => {
    let displayValue = value === null || value === undefined ? '--' : value;

    if (type === 'url' && value) {
      const isValidUrl = value.startsWith('http://') || value.startsWith('https://');
      if (isValidUrl) {
        displayValue = (
          <a href={value} target="_blank" rel="noopener noreferrer">
            {value}
          </a>
        );
      } else {
        displayValue = <span>{value}</span>;
      }
    } else if (type === 'tag' && (value || value === 0 || value === false)) {
      displayValue = (
        <Tag type="primary" size="small">
          {value.toString()}
        </Tag>
      );
    } else if (type === 'time') {
      displayValue = this.formatTime(value);
    }

    return (
      <div style={{ marginBottom: 16, display: 'flex' }}>
        <div style={{ width: 120, fontWeight: 'bold', color: '#333' }}>{label}:</div>
        <div style={{ flex: 1 }}>{displayValue}</div>
      </div>
    );
  };

  renderVersionTable = () => {
    const { locale = {} } = this.props;
    const { versionList, agentData } = this.state;
    const currentVersion = agentData?.version;

    if (!versionList || versionList.length === 0) {
      return <div>{locale.AgentDetail?.noVersionInfo || 'No version information'}</div>;
    }

    return (
      <Table dataSource={versionList} size="small">
        <Table.Column
          title={locale.AgentDetail?.version || 'Version'}
          dataIndex="version"
          cell={(value, index, record) => {
            // 如果不是当前版本，则显示为可点击链接
            if (value !== currentVersion) {
              return (
                <a onClick={() => this.handleVersionClick(record)} style={{ cursor: 'pointer' }}>
                  {value || '--'}
                </a>
              );
            }
            return value || '--';
          }}
        />
        <Table.Column
          title={locale.AgentDetail?.isLatest || 'Is Latest'}
          dataIndex="latest"
          cell={value => (
            <Tag type={value ? 'primary' : 'normal'} size="small">
              {value ? (locale.AgentDetail?.yes || 'Yes') : (locale.AgentDetail?.no || 'No')}
            </Tag>
          )}
        />
        <Table.Column
          title={locale.AgentDetail?.createdAt || 'Created At'}
          dataIndex="createdAt"
          cell={value => this.formatTime(value)}
        />
        <Table.Column
          title={locale.AgentDetail?.updatedAt || 'Updated At'}
          dataIndex="updatedAt"
          cell={value => this.formatTime(value)}
        />
      </Table>
    );
  };

  // 处理版本号点击事件
  handleVersionClick = versionRecord => {
    const agentName = getParams('name');
    const namespaceId = getParams('namespace') || 'public';

    // 跳转到对应版本的详情页面
    this.props.history.push(
      `/agentDetail?namespace=${namespaceId}&name=${agentName}&version=${versionRecord.version}`
    );
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, agentData } = this.state;
    const { Row, Col } = Grid;

    if (loading) {
      return <Loading visible={true} style={{ width: '100%' }} />;
    }

    if (!agentData) {
      return (
        <div>
          <PageTitle title={locale.AgentDetail?.agentDetail || 'Agent Details'} />
          <Card style={{ marginTop: 16 }} contentHeight="auto">
            <div style={{ textAlign: 'center', padding: 40 }}>{locale.AgentDetail?.agentNotExist || 'Agent does not exist or has been deleted'}</div>
          </Card>
        </div>
      );
    }

    // 构造包含版本信息的标题
    const pageTitle = getParams('version')
      ? `${locale.AgentDetail?.agentDetail || 'Agent Details'} - ${agentData.name} (${locale.AgentDetail?.version || 'Version'}: ${getParams('version')})`
      : `${locale.AgentDetail?.agentDetail || 'Agent Details'} - ${agentData.name}`;

    return (
      <div>
        <PageTitle title={pageTitle} />

        <div style={{ marginBottom: 16 }}>
          <Button onClick={this.handleGoBack} style={{ marginRight: 8 }}>
            {locale.AgentDetail?.backToList || 'Back to List'}
          </Button>
          <Button type="primary" onClick={this.handleEdit}>
            {locale.AgentDetail?.edit || 'Edit'}
          </Button>
        </div>

        <Card title={locale.AgentDetail?.basicInfo || 'Basic Information'} style={{ marginBottom: 16 }} contentHeight="auto">
          <div style={{ display: 'flex', gap: '40px' }}>
            <div style={{ flex: 1 }}>
              {this.renderDetailItem(locale.AgentDetail?.agentName || 'Agent Name', agentData.name)}
              {this.renderDetailItem(locale.AgentDetail?.version || 'Version', agentData.version)}
              {this.renderDetailItem(locale.AgentDetail?.url || 'Service URL', agentData.url)}
              {this.renderDetailItem(locale.AgentDetail?.description || 'Description', agentData.description)}
              {this.renderDetailItem(locale.AgentDetail?.protocolVersion || 'Protocol Version', agentData.protocolVersion)}
              {this.renderDetailItem(locale.AgentDetail?.iconUrl || 'Icon URL', agentData.iconUrl, 'url')}
              {this.renderDetailItem(locale.AgentDetail?.documentationUrl || 'Documentation URL', agentData.documentationUrl, 'url')}
            </div>
            <div style={{ flex: 1 }}>
              {this.renderDetailItem(locale.AgentDetail?.inputModes || 'Input Modes', this.formatModes(agentData.defaultInputModes))}
              {this.renderDetailItem(locale.AgentDetail?.outputModes || 'Output Modes', this.formatModes(agentData.defaultOutputModes))}
              {this.renderDetailItem(locale.AgentDetail?.providerOrganization || 'Provider Name', agentData.provider?.organization)}
              {this.renderDetailItem(locale.AgentDetail?.providerUrl || 'Provider URL', agentData.provider?.url)}
              {this.renderDetailItem(locale.AgentDetail?.preferredTransport || 'Preferred Transport', agentData.preferredTransport)}
              {this.renderDetailItem(
                locale.AgentDetail?.supportsAuthenticatedExtendedCard || 'Extended Card Support',
                agentData.supportsAuthenticatedExtendedCard,
                'tag'
              )}
            </div>
          </div>
        </Card>

        <Card title={locale.AgentDetail?.skillsList || 'Skills List'} style={{ marginBottom: 16 }} contentHeight="auto">
          {this.renderSkillsContent(agentData.skills)}
        </Card>

        <Card title={locale.AgentDetail?.capabilities || 'Capabilities'} style={{ marginBottom: 16 }} contentHeight="auto">
          {this.renderCapabilitiesContent(agentData.capabilities)}
        </Card>

        <Card title={locale.AgentDetail?.securitySchemes || 'Security Schemes'} style={{ marginBottom: 16 }} contentHeight="auto">
          {agentData.securitySchemes ? (
            <pre
              style={{
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
                margin: 0,
                padding: '12px',
                backgroundColor: '#f5f5f5',
                borderRadius: '6px',
                border: '1px solid #e8e8e8',
                fontSize: '13px',
              }}
            >
              {JSON.stringify(agentData.securitySchemes, null, 2)}
            </pre>
          ) : (
            <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>
              {locale.AgentDetail?.noSecuritySchemesConfig || 'No security schemes configured'}
            </div>
          )}
        </Card>

        <Card title={locale.AgentDetail?.security || 'Security Configuration'} style={{ marginBottom: 16 }} contentHeight="auto">
          {agentData.security ? (
            <pre
              style={{
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
                margin: 0,
                padding: '12px',
                backgroundColor: '#f5f5f5',
                borderRadius: '6px',
                border: '1px solid #e8e8e8',
                fontSize: '13px',
              }}
            >
              {JSON.stringify(agentData.security, null, 2)}
            </pre>
          ) : (
            <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>{locale.AgentDetail?.noSecurityConfig || 'No security configuration'}</div>
          )}
        </Card>

        <Card title={locale.AgentDetail?.additionalInterfaces || 'Additional Interfaces'} style={{ marginBottom: 16 }} contentHeight="auto">
          {agentData.additionalInterfaces && agentData.additionalInterfaces.length > 0 ? (
            <div>
              {agentData.additionalInterfaces.map((interfaceItem, index) => (
                <div
                  key={index}
                  style={{
                    marginBottom: '12px',
                    padding: '12px',
                    backgroundColor: '#f5f5f5',
                    borderRadius: '6px',
                    border: '1px solid #e8e8e8',
                  }}
                >
                  <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>
                    {interfaceItem.name || `${locale.AgentDetail?.interface || 'Interface'} ${index + 1}`}
                  </div>
                  {interfaceItem.url && (
                    <div style={{ color: '#666', fontSize: '13px' }}>URL: {interfaceItem.url}</div>
                  )}
                  {interfaceItem.description && (
                    <div style={{ color: '#666', fontSize: '13px' }}>
                      {locale.AgentDetail?.description || 'Description'}: {interfaceItem.description}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>{locale.AgentDetail?.noAdditionalInterfaces || 'No additional interfaces'}</div>
          )}
        </Card>

        <Card title={locale.AgentDetail?.versionInfo || 'Version Information'} contentHeight="auto">
          {this.renderVersionTable()}
        </Card>
      </div>
    );
  }
}

export default AgentDetail;
