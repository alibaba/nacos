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
import {
  Button,
  Card,
  ConfigProvider,
  Dialog,
  Message,
  Tag,
  Grid,
  Icon,
  Loading,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import ShowCodeing from 'components/ShowCodeing';
import SkillOptimizeDialog from '../SkillManagement/SkillOptimizeDialog';
import { getParams, request } from '@/globalLib';

const { Row, Col } = Grid;

@ConfigProvider.config
class SkillDetail extends React.Component {
  static displayName = 'SkillDetail';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.showcode = React.createRef();
    this.state = {
      loading: true,
      skillData: null,
      optimizeDialogVisible: false,
    };
  }

  componentDidMount() {
    this.loadSkillData();
  }

  loadSkillData = () => {
    const skillName = getParams('name');
    const namespaceId = getParams('namespace') || '';

    this.setState({ loading: true });

    const params = new URLSearchParams();
    params.append('skillName', skillName);
    params.append('namespaceId', namespaceId);

    request({
      url: `v3/console/ai/skills?${params.toString()}`,
      success: data => {
        this.setState({ loading: false });
        if (data && (data.code === 0 || data.code === 200) && data.data) {
          this.setState({ skillData: data.data });
        } else {
          const { locale = {} } = this.props;
          Message.error(
            data?.message || locale.getSkillInfoFailed || 'Failed to get Skill information'
          );
        }
      },
      error: () => {
        this.setState({ loading: false });
        const { locale = {} } = this.props;
        Message.error(locale.getSkillInfoFailed || 'Failed to get Skill information');
      },
    });
  };

  handleEdit = () => {
    const namespaceId = getParams('namespace') || 'public';
    const skillName = getParams('name');
    this.props.history.push(`/newSkill?namespace=${namespaceId}&name=${skillName}&mode=edit`);
  };

  handleOptimize = () => {
    this.setState({ optimizeDialogVisible: true });
  };

  handleOptimizeSuccess = optimizedSkill => {
    const { locale = {} } = this.props;
    Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
    this.loadSkillData();
    this.handleOptimizeDialogClose();
  };

  handleOptimizeDialogClose = () => {
    this.setState({ optimizeDialogVisible: false });
  };

  handleDelete = () => {
    const { locale = {} } = this.props;
    const skillName = getParams('name');
    Dialog.confirm({
      title: locale.deleteConfirm || 'Delete Confirmation',
      content: (locale.deleteSkillConfirm || 'Are you sure you want to delete Skill "{0}"?').replace(
        '{0}',
        skillName
      ),
      onOk: () => {
        this.deleteSkill();
      },
    });
  };

  deleteSkill = () => {
    const { locale = {} } = this.props;
    const skillName = getParams('name');
    const namespaceId = getParams('namespace') || '';

    const params = new URLSearchParams();
    params.append('skillName', skillName);
    if (namespaceId) {
      params.append('namespaceId', namespaceId);
    }

    request({
      method: 'DELETE',
      url: `v3/console/ai/skills?${params.toString()}`,
      success: data => {
        if (data && data.code === 0) {
          Message.success(locale.deleteSuccess || 'Delete successful');
          setTimeout(() => {
            this.handleGoBack();
          }, 1000);
        } else {
          Message.error(data?.message || locale.deleteFailed || 'Delete failed');
        }
      },
      error: () => {
        Message.error(locale.deleteFailed || 'Delete failed');
      },
    });
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || '';
    this.props.history.push(`/skillManagement?namespace=${namespaceId}`);
  };

  handleViewJson = () => {
    const { skillData } = this.state;
    if (this.showcode && this.showcode.current) {
      this.showcode.current.show(JSON.stringify(skillData, null, 2), 'json');
    }
  };

  formatTime = timeStr => {
    if (!timeStr) return '--';
    try {
      const date = new Date(timeStr);
      if (isNaN(date.getTime())) return '--';
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch (e) {
      return '--';
    }
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, skillData } = this.state;

    if (loading) {
      return (
        <div style={{ padding: 20 }}>
          <Loading visible={loading} />
        </div>
      );
    }

    if (!skillData) {
      return (
        <div style={{ padding: 20 }}>
          <Message type="warning">{locale.skillNotFound || 'Skill not found'}</Message>
        </div>
      );
    }

    const resources = skillData.resource ? Object.values(skillData.resource) : [];

    return (
      <div className="skill-detail">
        <PageTitle
          title={locale.skillDetail || 'Skill Detail'}
          namespaceId={getParams('namespace') || 'public'}
        />

        <div style={{ marginBottom: 16, textAlign: 'right' }}>
          <Button type="primary" onClick={this.handleEdit} style={{ marginRight: 8 }}>
            <Icon type="edit" /> {locale.edit || 'Edit'}
          </Button>
          <Button onClick={this.handleOptimize} style={{ marginRight: 8 }}>
            <Icon type="ai" /> {locale.aiOptimize || 'AI Optimize'}
          </Button>
          <Button onClick={this.handleViewJson} style={{ marginRight: 8 }}>
            <Icon type="code" /> {locale.viewJson || 'View JSON'}
          </Button>
          <Button warning onClick={this.handleDelete}>
            <Icon type="delete" /> {locale.delete || 'Delete'}
          </Button>
        </div>

        <Card title={locale.basicInfo || 'Basic Information'} style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <div className="info-item">
                <label>{locale.skillName || 'Skill Name'}:</label>
                <span>{skillData.name || '--'}</span>
              </div>
            </Col>
            <Col span={12}>
              <div className="info-item">
                <label>{locale.skillId || 'Skill ID'}:</label>
                <span>{skillData.skillId || '--'}</span>
              </div>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <div className="info-item">
                <label>{locale.description || 'Description'}:</label>
                <span>{skillData.description || '--'}</span>
              </div>
            </Col>
          </Row>
        </Card>

        <Card title={locale.instruction || 'Instruction'} style={{ marginBottom: 16 }}>
          <div className="instruction-content">
            <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {skillData.instruction || '--'}
            </pre>
          </div>
        </Card>

        <Card title={locale.resources || 'Resources'}>
          {resources.length > 0 ? (
            <div className="resources-list">
              {resources.map((resource, index) => (
                <Card key={index} className="resource-item" style={{ marginBottom: 16 }}>
                  <Row gutter={16}>
                    <Col span={6}>
                      <div className="info-item">
                        <label>{locale.resourceName || 'Resource Name'}:</label>
                        <span>{resource.name || '--'}</span>
                      </div>
                    </Col>
                    <Col span={6}>
                      <div className="info-item">
                        <label>{locale.resourceType || 'Resource Type'}:</label>
                        <span>
                          {resource.type ? (
                            <Tag type="primary" size="small">
                              {resource.type}
                            </Tag>
                          ) : (
                            '--'
                          )}
                        </span>
                      </div>
                    </Col>
                    <Col span={12}>
                      <div className="info-item">
                        <label>{locale.resourceId || 'Resource ID'}:</label>
                        <span>{resource.resourceId || '--'}</span>
                      </div>
                    </Col>
                  </Row>
                  <Row gutter={16}>
                    <Col span={24}>
                      <div className="info-item">
                        <label>{locale.resourceContent || 'Resource Content'}:</label>
                        <div className="resource-content">
                          <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                            {resource.content || '--'}
                          </pre>
                        </div>
                      </div>
                    </Col>
                  </Row>
                  {resource.metadata && (
                    <Row gutter={16}>
                      <Col span={24}>
                        <div className="info-item">
                          <label>{locale.metadata || 'Metadata'}:</label>
                          <div className="resource-metadata">
                            <pre>{JSON.stringify(resource.metadata, null, 2)}</pre>
                          </div>
                        </div>
                      </Col>
                    </Row>
                  )}
                </Card>
              ))}
            </div>
          ) : (
            <div className="empty-resources">
              {locale.noResources || 'No resources'}
            </div>
          )}
        </Card>

        <ShowCodeing ref={this.showcode} />

        <SkillOptimizeDialog
          visible={this.state.optimizeDialogVisible}
          skill={this.state.skillData}
          onClose={this.handleOptimizeDialogClose}
          onSuccess={this.handleOptimizeSuccess}
          locale={this.props.locale}
          history={this.props.history}
        />
      </div>
    );
  }
}

export default SkillDetail;
