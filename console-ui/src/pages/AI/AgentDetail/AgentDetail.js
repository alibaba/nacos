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
import { Button, Card, ConfigProvider, Loading, Message, Tag, Table, Grid } from '@alifd/next';
import PageTitle from 'components/PageTitle';
import { getParams, request } from '@/globalLib';

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
  }

  loadAgentDetail = () => {
    const agentName = getParams('name');
    const namespaceId = getParams('namespace') || 'public';

    if (!agentName) {
      Message.error('Agent名称不能为空');
      return;
    }

    this.setState({ loading: true });

    const params = new URLSearchParams();
    params.append('name', agentName);
    params.append('namespaceId', namespaceId);

    request({
      url: `/v3/console/ai/a2a?${params.toString()}`,
      success: data => {
        console.log('Agent detail API response:', data);
        this.setState({ loading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          const agentData = data.data;
          console.log('Agent detail loaded:', agentData);
          this.setState(
            {
              agentData,
              versionList: agentData.versionDetails || [],
            },
            () => {
              console.log('State updated, agentData:', this.state.agentData);
            }
          );
        } else {
          console.log('Failed to load agent detail:', data);
          Message.error(data?.message || '获取Agent详情失败');
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error('获取Agent详情失败');
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
    if (!capabilities) return '--';
    if (typeof capabilities === 'object') {
      const caps = [];
      if (capabilities.sampling) caps.push('采样');
      if (capabilities.tools && Array.isArray(capabilities.tools)) {
        caps.push(`工具: ${capabilities.tools.join(', ')}`);
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
    if (!skills) {
      return (
        <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>暂无技能配置</div>
      );
    }

    if (Array.isArray(skills)) {
      return (
        <div>
          {skills.map((skill, index) => (
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
                {skill.name || `技能 ${index + 1}`}
              </div>
              {skill.description && (
                <div style={{ color: '#666', fontSize: '13px' }}>{skill.description}</div>
              )}
            </div>
          ))}
        </div>
      );
    }

    // 如果是对象或字符串，显示JSON格式
    const skillsValue =
      typeof skills === 'object' ? JSON.stringify(skills, null, 2) : skills.toString();
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
        {skillsValue}
      </pre>
    );
  };

  renderCapabilitiesContent = capabilities => {
    if (!capabilities) {
      return (
        <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>暂无能力配置</div>
      );
    }

    if (typeof capabilities === 'object') {
      return (
        <div>
          {Object.entries(capabilities).map(([key, value]) => (
            <div
              key={key}
              style={{
                marginBottom: '12px',
                padding: '12px',
                backgroundColor: '#f5f5f5',
                borderRadius: '6px',
                border: '1px solid #e8e8e8',
              }}
            >
              <div style={{ fontWeight: 'bold', marginBottom: '4px', textTransform: 'capitalize' }}>
                {key}
              </div>
              <div style={{ color: '#666', fontSize: '13px' }}>
                {Array.isArray(value) ? value.join(', ') : value.toString()}
              </div>
            </div>
          ))}
        </div>
      );
    }

    // 如果是字符串，显示原始内容
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
        {capabilities.toString()}
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
    const { versionList } = this.state;

    if (!versionList || versionList.length === 0) {
      return <div>暂无版本信息</div>;
    }

    return (
      <Table dataSource={versionList} size="small">
        <Table.Column title="版本号" dataIndex="version" cell={value => value || '--'} />
        <Table.Column
          title="是否最新"
          dataIndex="latest"
          cell={value => (
            <Tag type={value ? 'primary' : 'normal'} size="small">
              {value ? '是' : '否'}
            </Tag>
          )}
        />
        <Table.Column
          title="创建时间"
          dataIndex="createdAt"
          cell={value => this.formatTime(value)}
        />
        <Table.Column
          title="更新时间"
          dataIndex="updatedAt"
          cell={value => this.formatTime(value)}
        />
      </Table>
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
          <PageTitle title="Agent详情" />
          <Card style={{ marginTop: 16 }}>
            <div style={{ textAlign: 'center', padding: 40 }}>Agent不存在或已被删除</div>
          </Card>
        </div>
      );
    }

    return (
      <div>
        <PageTitle title={`Agent详情 - ${agentData.name}`} />

        <div style={{ marginBottom: 16 }}>
          <Button onClick={this.handleGoBack} style={{ marginRight: 8 }}>
            返回列表
          </Button>
          <Button type="primary" onClick={this.handleEdit}>
            编辑
          </Button>
        </div>

        <Card title="基本信息" style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', gap: '40px' }}>
            <div style={{ flex: 1 }}>
              {this.renderDetailItem('Agent名称', agentData.name)}
              {this.renderDetailItem('版本号', agentData.version)}
              {this.renderDetailItem('服务地址', agentData.url)}
              {this.renderDetailItem('描述信息', agentData.description)}
              {this.renderDetailItem('协议版本', agentData.protocolVersion)}
            </div>
            <div style={{ flex: 1 }}>
              {this.renderDetailItem('输入模式', this.formatModes(agentData.defaultInputModes))}
              {this.renderDetailItem('输出模式', this.formatModes(agentData.defaultOutputModes))}
              {this.renderDetailItem('提供商名称', agentData.provider?.name)}
              {this.renderDetailItem('提供商URL', agentData.provider?.url)}
              {this.renderDetailItem('传输协议', agentData.preferredTransport)}
            </div>
          </div>
        </Card>

        <Card title="技能列表" style={{ marginBottom: 16 }}>
          {this.renderSkillsContent(agentData.skills)}
        </Card>

        <Card title="能力配置" style={{ marginBottom: 16 }}>
          {this.renderCapabilitiesContent(agentData.capabilities)}
        </Card>

        <Card title="版本信息">{this.renderVersionTable()}</Card>
      </div>
    );
  }
}

export default AgentDetail;
