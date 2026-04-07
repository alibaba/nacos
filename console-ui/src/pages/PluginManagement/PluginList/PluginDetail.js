/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
import { Dialog, Form, Input, Select, Switch, Table, Icon, Message, Tag } from '@alifd/next';
import Field from '@alifd/next/lib/field';
import { request } from '../../../globalLib';

const FormItem = Form.Item;

class PluginDetail extends React.Component {
  static propTypes = {
    locale: PropTypes.object,
    visible: PropTypes.bool,
    pluginId: PropTypes.string,
    onClose: PropTypes.func,
    onSuccess: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      detail: null,
      config: {},
    };
    this.field = new Field(this);
  }

  componentDidUpdate(prevProps) {
    if (this.props.visible && !prevProps.visible && this.props.pluginId) {
      this.getDetail();
    }
  }

  getDetail() {
    this.setState({ loading: true });
    const { pluginId } = this.props;
    const [pluginType, pluginName] = pluginId.split(':');

    request({
      url: 'v3/console/plugin',
      data: {
        pluginType,
        pluginName,
      },
      success: res => {
        if (res.code === 0) {
          const detail = res.data;
          this.setState({ detail, config: detail.config || {} });
          // 设置表单初始值
          if (detail.configDefinitions) {
            const initValues = {};
            detail.configDefinitions.forEach(def => {
              // 优先使用当前配置值，如果没有则使用默认值
              const configValue = detail.config && detail.config[def.key];
              initValues[def.key] = configValue !== undefined ? configValue : def.defaultValue;
            });
            this.field.setValues(initValues);
          }
        } else {
          Message.error(res.message);
        }
      },
      error: err => {
        Message.error(err.message || 'Failed to fetch plugin detail');
      },
      complete: () => {
        this.setState({ loading: false });
      },
    });
  }

  handleSubmit = () => {
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }

      const { detail } = this.state;
      const { locale } = this.props;

      this.setState({ loading: true });
      request({
        url: 'v3/console/plugin/config',
        method: 'PUT',
        data: JSON.stringify({
          pluginType: detail.pluginType,
          pluginName: detail.pluginName,
          config: values,
        }),
        headers: { 'Content-Type': 'application/json' },
        success: res => {
          if (res.code === 0) {
            Message.success(locale.updateSuccess || 'Update Success');
            this.props.onSuccess && this.props.onSuccess();
            this.props.onClose && this.props.onClose();
          } else {
            Message.error(res.message);
          }
        },
        error: err => {
          Message.error(err.message || 'Failed to update config');
        },
        complete: () => {
          this.setState({ loading: false });
        },
      });
    });
  };

  renderConfigForm() {
    const { detail } = this.state;
    const { locale } = this.props;

    if (
      !detail ||
      !detail.configurable ||
      !detail.configDefinitions ||
      detail.configDefinitions.length === 0
    ) {
      return (
        <div
          className="no-config-placeholder"
          style={{ padding: '40px 0', textAlign: 'center', color: '#999' }}
        >
          <Icon type="set" size="xl" style={{ marginBottom: 12, color: '#e0e0e0' }} />
          <p>{locale.noConfigAvailable || 'No configuration available'}</p>
        </div>
      );
    }

    return (
      <Form field={this.field} className="config-form">
        {detail.configDefinitions.map(def => {
          const label = (
            <span>
              {def.name}
              {def.description && (
                <span
                  title={def.description}
                  style={{ marginLeft: 6, color: '#999', cursor: 'help' }}
                >
                  <Icon type="help" size="xs" />
                </span>
              )}
            </span>
          );

          let component;
          if (def.type === 'BOOLEAN') {
            component = <Switch />;
          } else if (def.type === 'ENUM') {
            component = (
              <Select>
                {def.enumValues.map(val => (
                  <Select.Option key={val} value={val}>
                    {val}
                  </Select.Option>
                ))}
              </Select>
            );
          } else {
            component = <Input />;
          }

          return (
            <FormItem key={def.key} label={label} required={def.required} help={def.description}>
              {React.cloneElement(component, { name: def.key })}
            </FormItem>
          );
        })}
      </Form>
    );
  }

  renderNodeAvailability() {
    const { detail } = this.state;
    const { locale } = this.props;

    if (!detail || !detail.nodeAvailability) {
      return null;
    }

    const dataSource = Object.entries(detail.nodeAvailability).map(([address, available]) => ({
      address,
      available,
    }));

    return (
      <div className="node-availability-table">
        <Table dataSource={dataSource} size="small">
          <Table.Column title={locale.nodeAddress || 'Node Address'} dataIndex="address" />
          <Table.Column
            title={locale.status || 'Status'}
            dataIndex="available"
            cell={val => (
              <Tag type="primary" color={val ? 'green' : 'red'}>
                {val ? locale.available || 'Available' : locale.unavailable || 'Unavailable'}
              </Tag>
            )}
          />
        </Table>
      </div>
    );
  }

  render() {
    const { visible, onClose, locale } = this.props;
    const { detail, loading } = this.state;

    return (
      <Dialog
        title={locale.pluginDetail || 'Plugin Detail'}
        visible={visible}
        onOk={this.handleSubmit}
        onCancel={onClose}
        onClose={onClose}
        okProps={{ loading }}
        className="plugin-detail-dialog"
        style={{ width: 720 }}
      >
        {loading && !detail && (
          <div style={{ textAlign: 'center', padding: '20px' }}>Loading...</div>
        )}
        {detail && (
          <div className="plugin-detail-content">
            <div className="detail-card">
              <h4>{locale.basicInfo || 'Basic Info'}</h4>
              <div className="basic-info-grid">
                <div className="info-item plugin-name-item">
                  <span className="label">{locale.pluginName || 'Name'}:</span>
                  <span className="value">{detail.pluginName}</span>
                </div>
                <div className="info-item">
                  <span className="label">{locale.status || 'Status'}:</span>
                  <span className="value">
                    <Tag type="normal" color={detail.enabled ? 'green' : 'red'}>
                      {detail.enabled ? locale.enabled || 'Enabled' : locale.disabled || 'Disabled'}
                    </Tag>
                  </span>
                </div>
                <div className="info-item">
                  <span className="label">{locale.pluginType || 'Type'}:</span>
                  <span className="value">{locale[detail.pluginType] || detail.pluginType}</span>
                </div>
                <div className="info-item">
                  <span className="label">{locale.critical || 'Critical'}:</span>
                  <span className="value">
                    {detail.critical ? locale.yes || 'Yes' : locale.no || 'No'}
                  </span>
                </div>
              </div>
            </div>

            <div className="detail-card">
              <h4>{locale.configItems || 'Configuration'}</h4>
              {this.renderConfigForm()}
            </div>

            <div className="detail-card">
              <h4>{locale.nodeAvailability || 'Node Availability'}</h4>
              {this.renderNodeAvailability()}
            </div>
          </div>
        )}
      </Dialog>
    );
  }
}

export default PluginDetail;
