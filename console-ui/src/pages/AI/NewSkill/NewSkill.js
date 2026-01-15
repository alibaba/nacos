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
  Field,
  Form,
  Input,
  Message,
  Grid,
  Icon,
  Dialog,
} from '@alifd/next';
import PageTitle from 'components/PageTitle';
import SkillOptimizeDialog from '../SkillManagement/SkillOptimizeDialog';
import { getParams, request } from '@/globalLib';
import './NewSkill.scss';

const { Row, Col } = Grid;

@ConfigProvider.config
class NewSkill extends React.Component {
  static displayName = 'NewSkill';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);

    const skillName = getParams('name');
    const mode = getParams('mode');
    const isEdit = mode === 'edit' && !!skillName;

    this.state = {
      loading: false,
      generating: false,
      streaming: false,
      streamContent: '',
      isEdit: isEdit,
      skillName,
      backgroundInfo: '',
      showAiGenerateDialog: false, // 控制是否显示AI生成弹窗
      generatedSkill: null, // 存储生成的Skill数据
      optimizeDialogVisible: false, // 控制是否显示AI优化弹窗
      currentSkillData: null, // 存储当前Skill数据用于优化
      // 创建模式下默认添加一个空的资源项
      resources: isEdit ? [] : [
        {
          resourceId: '',
          name: '',
          type: '',
          content: '',
          metadata: null,
        },
      ],
    };
    this.streamReader = null;
  }

  componentDidMount() {
    if (this.state.isEdit) {
      // Check if there's optimized skill data from optimization dialog
      const optimizedParam = getParams('optimized');
      if (optimizedParam === 'true') {
        this.loadOptimizedSkillData();
      } else {
        this.loadSkillData();
      }
    }
  }

  loadOptimizedSkillData = () => {
    try {
      const optimizedSkillStr = localStorage.getItem('nacos_optimized_skill');
      if (optimizedSkillStr) {
        const optimizedSkill = JSON.parse(optimizedSkillStr);
        
        // Fill form with optimized skill data
        this.field.setValues({
          name: optimizedSkill.name || '',
          description: optimizedSkill.description || '',
          instruction: optimizedSkill.instruction || '',
        });

        // Fill resources if any
        if (optimizedSkill.resource && Object.keys(optimizedSkill.resource).length > 0) {
          const resources = Object.values(optimizedSkill.resource).map(resource => ({
            resourceId: resource.resourceId || '',
            name: resource.name || '',
            type: resource.type || '',
            content: resource.content || '',
            metadata: resource.metadata || null,
          }));
          this.setState({ resources });
        } else {
          this.setState({ resources: [] });
        }

        // Clear the stored data
        localStorage.removeItem('nacos_optimized_skill');
        
        const { locale = {} } = this.props;
        Message.success(this.getLocaleValue('optimizedSkillLoaded', 'Optimized skill data loaded successfully'));
      } else {
        // Fallback to normal load
        this.loadSkillData();
      }
    } catch (e) {
      console.error('Failed to load optimized skill data', e);
      // Fallback to normal load
      this.loadSkillData();
    }
  }

  loadSkillData = () => {
    const { skillName } = this.state;
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
          const skillData = data.data;
          const resources = skillData.resource ? Object.values(skillData.resource) : [];

          this.field.setValues({
            name: skillData.name,
            description: skillData.description || '',
            instruction: skillData.instruction || '',
          });

          // 保存skill数据用于AI优化
          this.setState({ 
            resources,
            currentSkillData: skillData 
          });
        } else {
          Message.error(
            data?.message || this.getLocaleValue('getSkillInfoFailed', 'Failed to get Skill information')
          );
        }
      },
      error: () => {
        this.setState({ loading: false });
        Message.error(this.getLocaleValue('getSkillInfoFailed', 'Failed to get Skill information'));
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
      const { isEdit, resources } = this.state;

      // 构建 skillCard 对象
      const skillCard = {
        name: values.name,
        description: values.description || '',
        instruction: values.instruction || '',
      };

      // 构建 resource Map，过滤掉无效的资源（没有 name 或 name 为空的资源）
      if (resources && resources.length > 0) {
        const resourceMap = {};
        resources.forEach((resource, index) => {
          // 只包含有效的资源（有 name 且 name 不为空）
          if (resource.name && resource.name.trim() !== '') {
            const key = resource.name.trim();
            resourceMap[key] = {
              resourceId: resource.resourceId || '',
              name: resource.name.trim(),
              type: resource.type || '',
              content: resource.content || '',
              metadata: resource.metadata || null,
            };
          }
        });
        skillCard.resource = resourceMap;
      } else {
        skillCard.resource = {};
      }

      // 准备请求数据
      const requestData = {
        namespaceId: namespaceId,
        skillName: values.name,
        skillCard: JSON.stringify(skillCard),
      };

      const url = 'v3/console/ai/skills';

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
            Message.success(
              isEdit
                ? this.getLocaleValue('updateSuccess', 'Update successful')
                : this.getLocaleValue('createSuccess', 'Create successful')
            );

            setTimeout(() => {
              this.handleGoBack();
            }, 1000);
          } else {
            Message.error(
              data?.message ||
                (isEdit
                  ? this.getLocaleValue('updateFailed', 'Update failed')
                  : this.getLocaleValue('createFailed', 'Create failed'))
            );
          }
        },
        error: error => {
          console.error('Request failed:', error);
          this.setState({ loading: false });
          Message.error(
            isEdit ? this.getLocaleValue('updateFailed', 'Update failed') : this.getLocaleValue('createFailed', 'Create failed')
          );
        },
      });
    });
  };

  handleGoBack = () => {
    const namespaceId = getParams('namespace') || '';
    this.props.history.push(`/skillManagement?namespace=${namespaceId}`);
  };

  handleAddResource = () => {
    const { resources } = this.state;
    const newResource = {
      resourceId: '',
      name: '',
      type: '',
      content: '',
      metadata: null,
    };
    this.setState({ resources: [...resources, newResource] });
  };

  handleRemoveResource = index => {
    Dialog.confirm({
      title: this.getLocaleValue('deleteConfirm', 'Delete Confirmation'),
      content: this.getLocaleValue('deleteResourceConfirm', 'Are you sure you want to delete this resource?'),
      onOk: () => {
        const { resources } = this.state;
        const newResources = resources.filter((_, i) => i !== index);
        this.setState({ resources: newResources });
      },
    });
  };

  handleResourceChange = (index, field, value) => {
    const { resources } = this.state;
    const newResources = [...resources];
    newResources[index] = {
      ...newResources[index],
      [field]: value,
    };
    this.setState({ resources: newResources });
  };

  validateRequired = (rule, value, callback) => {
    if (!value || value.trim() === '') {
      callback(this.getLocaleValue('requiredField', 'This field is required'));
    } else {
      callback();
    }
  };

  handleGenerateSkill = () => {
    const { backgroundInfo } = this.state;

    if (!backgroundInfo || backgroundInfo.trim() === '') {
      Message.warning(this.getLocaleValue('backgroundInfoRequired', 'Please enter background information'));
      return;
    }

    this.setState({ 
      generating: true, 
      streaming: true,
      streamContent: '',
      generatedSkill: null 
    });

    // Build request payload
    const payload = {
      backgroundInfo: backgroundInfo.trim(),
    };

    // Use SSE stream
    const baseUrl = window.location.origin;
    const url = `${baseUrl}/v3/console/copilot/skill/generate`;
    const token = localStorage.getItem('token');

    this.startSSEStream(url, payload, token);
  };

  startSSEStream = (url, payload, token) => {
    // Use fetch API for POST request with SSE
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: token } : {}),
      },
      body: JSON.stringify(payload),
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        this.streamReader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const readStream = () => {
          this.streamReader
            .read()
            .then(({ done, value }) => {
              if (done) {
                this.setState({ streaming: false, generating: false });
                return;
              }

              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split('\n');
              buffer = lines.pop() || ''; // Keep incomplete line in buffer

              lines.forEach(line => {
                if (line.startsWith('data:')) {
                  const dataStr = line.substring(5).trim();
                  if (dataStr) {
                    try {
                      const data = JSON.parse(dataStr);
                      this.handleSSEMessage(data);
                    } catch (e) {
                      // Failed to parse SSE data
                    }
                  }
                } else if (line.startsWith('event:')) {
                  const eventType = line.substring(6).trim();
                  // Handle event type if needed
                }
              });

              readStream();
            })
            .catch(error => {
              this.setState({
                streaming: false,
                generating: false,
                error: error.message || 'Stream read failed',
              });
              Message.error(this.getLocaleValue('generateFailed', 'Failed to generate skill'));
            });
        };

        readStream();
      })
      .catch(error => {
        this.setState({
          streaming: false,
          generating: false,
        });
        Message.error(this.getLocaleValue('generateFailed', 'Failed to generate skill'));
      });
  };

  handleSSEMessage = (data) => {
    if (!data) {
      return;
    }

    // Handle different response types
    if (data.type === 'THINKING' || data.type === 'TOOL_CALL' || data.type === 'CONTENT') {
      // Accumulate stream content
      const currentContent = this.state.streamContent || '';
      this.setState({
        streamContent: currentContent + (data.chunk || ''),
      });
    } else if (data.type === 'DONE' || data.done) {
      // Final response received
      if (data.skill) {
        this.setState({
          generating: false,
          streaming: false,
          generatedSkill: {
            skill: data.skill,
            explanation: data.explanation || this.getLocaleValue('generateSuccess', 'Skill generated successfully')
          }
        });
      } else {
        Message.error(this.getLocaleValue('generateFailed', 'Failed to generate skill: no skill data returned'));
        this.setState({ generating: false, streaming: false });
      }
    } else if (data.explanation && data.explanation.includes('失败')) {
      // Error response
      Message.error(data.explanation);
      this.setState({ generating: false, streaming: false });
    }
  };

  handleApplyGeneratedSkill = () => {
    const { generatedSkill } = this.state;
    
    if (!generatedSkill || !generatedSkill.skill) {
      return;
    }

    const skill = generatedSkill.skill;

    // Fill form with generated skill
    this.field.setValues({
      name: skill.name || '',
      description: skill.description || '',
      instruction: skill.instruction || '',
    });

    // Fill resources if any
    if (skill.resource && Object.keys(skill.resource).length > 0) {
      const resources = Object.values(skill.resource).map(resource => ({
        resourceId: resource.resourceId || '',
        name: resource.name || '',
        type: resource.type || '',
        content: resource.content || '',
        metadata: resource.metadata || null,
      }));
      this.setState({ resources });
    }

    Message.success(generatedSkill.explanation || this.getLocaleValue('generateSuccess', 'Skill generated successfully'));
    
    // Close dialog and reset
    this.setState({ 
      showAiGenerateDialog: false, 
      backgroundInfo: '',
      generatedSkill: null 
    });
  };

  handleBackgroundInfoChange = value => {
    this.setState({ backgroundInfo: value });
  };

  handleShowAiGenerate = () => {
    this.setState({ showAiGenerateDialog: true, backgroundInfo: '', generatedSkill: null });
  };

  handleCloseAiGenerateDialog = () => {
    this.setState({ 
      showAiGenerateDialog: false, 
      backgroundInfo: '', 
      generatedSkill: null 
    });
  };

  handleShowOptimizeDialog = () => {
    this.setState({ optimizeDialogVisible: true });
  };

  handleOptimizeDialogClose = () => {
    this.setState({ optimizeDialogVisible: false });
  };

  handleOptimizeSuccess = (optimizedSkill) => {
    const { locale = {} } = this.props;
    // 优化成功后，将优化后的skill填充到表单中
    if (optimizedSkill) {
      // 处理资源数据，确保格式正确
      let resources = [];
      if (optimizedSkill.resource && Object.keys(optimizedSkill.resource).length > 0) {
        // 使用Object.entries来同时获取key和value，因为Map的key可能是资源名称
        resources = Object.entries(optimizedSkill.resource).map(([resourceKey, resource]) => {
          // 确保资源对象包含所有必要字段
          // 如果resource对象没有name，使用resourceKey作为name
          const resourceObj = {
            resourceId: resource.resourceId || '',
            name: resource.name || resourceKey || '',
            type: resource.type || '',
            content: resource.content || '',
            metadata: resource.metadata || null,
          };
          return resourceObj;
        });
      }
      
      this.field.setValues({
        name: optimizedSkill.name || this.field.getValue('name'),
        description: optimizedSkill.description || this.field.getValue('description'),
        instruction: optimizedSkill.instruction || this.field.getValue('instruction'),
      });

      this.setState({ 
        resources,
        currentSkillData: optimizedSkill 
      });
      
      // 如果添加了新资源，显示提示信息
      if (resources.length > 0) {
        const newResourcesCount = resources.filter(r => !r.resourceId || r.resourceId === '').length;
        if (newResourcesCount > 0) {
          Message.success(
            (locale.optimizeSuccess || 'Optimization applied successfully') + ` (${newResourcesCount} new resource(s) added)`
          );
        } else {
          Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
        }
      } else {
        Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
      }
    } else {
      Message.success(locale.optimizeSuccess || 'Optimization applied successfully');
    }
    
    this.handleOptimizeDialogClose();
  };

  // Helper function to get locale value, supporting both nested and flattened structures
  getLocaleValue = (key, fallback) => {
    const { locale = {} } = this.props;
    // Try nested structure first (locale.SkillManagement.key)
    if (locale.SkillManagement && locale.SkillManagement[key]) {
      return locale.SkillManagement[key];
    }
    // Try flattened structure (locale.key)
    if (locale[key]) {
      return locale[key];
    }
    return fallback;
  };

  render() {
    const { locale = {} } = this.props;
    const { loading, generating, isEdit, resources, showAiGenerateInput } = this.state;
    // Support both nested (locale.SkillManagement) and flattened (locale.skillName) structures
    const skillLocale = locale.SkillManagement || locale;

    const formItemLayout = {
      labelCol: { span: 3 },
      wrapperCol: { span: 20 },
    };

    return (
      <div className="new-skill-container">
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
          <PageTitle
            title={isEdit ? this.getLocaleValue('editSkill', 'Edit Skill') : this.getLocaleValue('createSkill', 'Create Skill')}
            namespaceId={getParams('namespace') || 'public'}
          />
          {!isEdit && (
            <Button
              type="primary"
              onClick={this.handleShowAiGenerate}
              style={{ marginLeft: 16 }}
            >
              <Icon type="magic-wand" /> {this.getLocaleValue('aiGenerate', 'AI Generate Skill')}
            </Button>
          )}
          {isEdit && (
            <Button
              type="primary"
              onClick={this.handleShowOptimizeDialog}
              style={{ marginLeft: 16 }}
            >
              <Icon type="refresh" /> {this.getLocaleValue('aiOptimize', 'AI Optimize')}
            </Button>
          )}
        </div>

        <div style={{ background: '#fff', padding: '20px', borderRadius: '4px', border: '1px solid #e6e6e6' }}>
          <Form field={this.field} {...formItemLayout} className="new-skill-form">
            <Form.Item
              label={this.getLocaleValue('skillName', 'Skill Name')}
              required
              validator={this.validateRequired}
            >
              <Input
                name="name"
                placeholder={this.getLocaleValue('skillNamePlaceholder', 'Please enter Skill name')}
                disabled={isEdit}
                maxLength={255}
              />
            </Form.Item>

            <Form.Item label={this.getLocaleValue('description', 'Description')}>
              <Input.TextArea
                name="description"
                placeholder={this.getLocaleValue('descriptionPlaceholder', 'Please enter Skill description')}
                rows={3}
                maxLength={1000}
              />
            </Form.Item>

            <Form.Item
              label={this.getLocaleValue('instruction', 'Instruction')}
              required
              validator={this.validateRequired}
            >
              <Input.TextArea
                name="instruction"
                placeholder={this.getLocaleValue('instructionPlaceholder', 'Please enter Skill instruction')}
                rows={8}
                maxLength={10000}
              />
            </Form.Item>

            <Form.Item label={this.getLocaleValue('resources', 'Resources')}>
              <div className="resources-section">
                <Button type="primary" onClick={this.handleAddResource} style={{ marginBottom: 16 }}>
                  <Icon type="add" /> {this.getLocaleValue('addResource', 'Add Resource')}
                </Button>

                {resources.map((resource, index) => (
                  <Card key={index} className="resource-card" style={{ marginBottom: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                      <h4>
                        {this.getLocaleValue('resource', 'Resource')} {index + 1}
                      </h4>
                      <Button
                        text
                        warning
                        onClick={() => this.handleRemoveResource(index)}
                      >
                        <Icon type="delete" /> {this.getLocaleValue('delete', 'Delete')}
                      </Button>
                    </div>

                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item label={this.getLocaleValue('resourceName', 'Resource Name')}>
                          <Input
                            value={resource.name}
                            onChange={value => this.handleResourceChange(index, 'name', value)}
                            placeholder={this.getLocaleValue('resourceNamePlaceholder', 'e.g., template.json')}
                            maxLength={255}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item label={this.getLocaleValue('resourceType', 'Resource Type')}>
                          <Input
                            value={resource.type}
                            onChange={value => this.handleResourceChange(index, 'type', value)}
                            placeholder={this.getLocaleValue('resourceTypePlaceholder', 'e.g., template')}
                            maxLength={100}
                          />
                        </Form.Item>
                      </Col>
                    </Row>

                    <Form.Item label={this.getLocaleValue('resourceContent', 'Resource Content')}>
                      <Input.TextArea
                        value={resource.content}
                        onChange={value => this.handleResourceChange(index, 'content', value)}
                        placeholder={this.getLocaleValue('resourceContentPlaceholder', 'Enter resource content')}
                        rows={6}
                      />
                    </Form.Item>
                  </Card>
                ))}

                {resources.length === 0 && (
                  <div className="empty-resources">
                    {this.getLocaleValue('noResources', 'No resources added yet. Click "Add Resource" to add one.')}
                  </div>
                )}
              </div>
            </Form.Item>

            <Form.Item wrapperCol={{ offset: 3, span: 20 }}>
              <Button
                type="primary"
                onClick={this.handleSubmit}
                loading={loading}
                style={{ marginRight: 10 }}
              >
                {isEdit ? this.getLocaleValue('update', 'Update') : this.getLocaleValue('create', 'Create')}
              </Button>
              <Button onClick={this.handleGoBack}>{this.getLocaleValue('cancel', 'Cancel')}</Button>
            </Form.Item>
          </Form>
        </div>

        {!isEdit && (
          <Dialog
            visible={this.state.showAiGenerateDialog}
            title={this.getLocaleValue('aiGenerate', 'AI Generate Skill')}
            onClose={this.handleCloseAiGenerateDialog}
            onCancel={this.handleCloseAiGenerateDialog}
            onOk={this.state.generatedSkill ? this.handleApplyGeneratedSkill : this.handleGenerateSkill}
            okProps={{
              loading: this.state.generating,
              children: this.state.generatedSkill 
                ? this.getLocaleValue('apply', 'Apply') 
                : this.getLocaleValue('generateSkill', 'Generate Skill'),
            }}
            cancelProps={{
              children: this.getLocaleValue('cancel', 'Cancel'),
            }}
            style={{ width: 800 }}
          >
            {!this.state.generatedSkill ? (
              <div>
                <Form.Item label={this.getLocaleValue('backgroundInfo', 'Background Information')}>
                  <Input.TextArea
                    value={this.state.backgroundInfo}
                    onChange={this.handleBackgroundInfoChange}
                    placeholder={
                      this.getLocaleValue('backgroundInfoPlaceholder',
                        'Please describe what you want the Skill to do, e.g., "I need a Skill to check Nacos configuration status and provide solutions when issues are found"')
                    }
                    rows={6}
                    maxLength={2000}
                  />
                </Form.Item>
                <div style={{ marginTop: 8, color: '#999', fontSize: '12px' }}>
                  {this.getLocaleValue('generateHint',
                    'Enter background information and click to generate a Skill based on best practices')}
                </div>
              </div>
            ) : (
              <div>
                <Message type="success" style={{ marginBottom: 16 }}>
                  {this.state.generatedSkill.explanation || this.getLocaleValue('generateSuccess', 'Skill generated successfully')}
                </Message>
                <div style={{ marginBottom: 16 }}>
                  <strong>{this.getLocaleValue('skillName', 'Skill Name')}:</strong> {this.state.generatedSkill.skill.name || '--'}
                </div>
                <div style={{ marginBottom: 16 }}>
                  <strong>{this.getLocaleValue('description', 'Description')}:</strong> {this.state.generatedSkill.skill.description || '--'}
                </div>
                {this.state.generatedSkill.skill.resource && Object.keys(this.state.generatedSkill.skill.resource).length > 0 && (
                  <div style={{ marginBottom: 16 }}>
                    <strong>{this.getLocaleValue('resources', 'Resources')}:</strong> {Object.keys(this.state.generatedSkill.skill.resource).length} {this.getLocaleValue('resource', 'resource(s)')}
                  </div>
                )}
                <div style={{ color: '#999', fontSize: '12px', marginTop: 16 }}>
                  {this.getLocaleValue('applyGeneratedSkillHint', 'Click "Apply" to fill the form with the generated Skill')}
                </div>
              </div>
            )}
          </Dialog>
        )}

        {isEdit && (
          <SkillOptimizeDialog
            visible={this.state.optimizeDialogVisible}
            skill={this.state.currentSkillData}
            onClose={this.handleOptimizeDialogClose}
            onSuccess={this.handleOptimizeSuccess}
            locale={this.props.locale}
            history={this.props.history}
          />
        )}
      </div>
    );
  }
}

export default NewSkill;
