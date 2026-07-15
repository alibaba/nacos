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
import { Button, Dialog, Icon, Loading, Table, Message, ConfigProvider } from '@alifd/next';
import { request } from '../../../globalLib';
import PageTitle from '../../../components/PageTitle';
import PluginDetail from './PluginDetail';
import './PluginList.scss';
const { Column } = Table;

const PLUGIN_TYPE_ICONS = {
  auth: 'lock',
  'datasource-dialect': 'form',
  'config-change': 'edit',
  encryption: 'key',
  trace: 'chart',
  environment: 'cloud',
  control: 'set',
  visibility: 'eye',
  'ai-pipeline': 'sorting',
  'ai-storage': 'attachment',
};

@ConfigProvider.config
class PluginList extends React.Component {
  static displayName = 'PluginManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
    location: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      dataSource: [],
      expandedTypes: {},
      detailVisible: false,
      currentPluginId: null,
    };
  }

  componentDidMount() {
    this.queryPluginList();
  }

  openLoading() {
    this.setState({ loading: true });
  }

  closeLoading() {
    this.setState({ loading: false });
  }

  queryPluginList() {
    request({
      url: 'v3/console/plugin/list',
      beforeSend: () => this.openLoading(),
      success: res => {
        if (res.code === 0) {
          this.setState({
            dataSource: res.data || [],
          });
        } else {
          Message.error(res.message);
        }
      },
      error: err => {
        Message.error(err.message || 'Failed to fetch plugin list');
        this.setState({ dataSource: [] });
      },
      complete: () => this.closeLoading(),
    });
  }

  updatePluginStatus(record) {
    const { locale = {} } = this.props;
    const newStatus = !record.enabled;
    const action = newStatus ? 'enable' : 'disable';

    Dialog.confirm({
      title: locale.confirm || 'Confirm',
      content: `${locale[action] || action} plugin ${record.pluginName}?`,
      onOk: () => {
        this.openLoading();
        request({
          url: `v3/console/plugin/status?pluginType=${encodeURIComponent(
            record.pluginType
          )}&pluginName=${encodeURIComponent(record.pluginName)}&enabled=${newStatus}`,
          type: 'PUT',
          dataType: 'json',
          success: res => {
            if (res.code === 0) {
              Message.success(locale.updateSuccess || 'Status updated successfully');
              this.queryPluginList();
            } else {
              Message.error(res.message);
            }
          },
          error: err => {
            Message.error(err.message || 'Failed to update status');
          },
          complete: () => this.closeLoading(),
        });
      },
    });
  }

  showDetail(record) {
    this.setState({
      detailVisible: true,
      currentPluginId: record.pluginId,
    });
  }

  closeDetail = () => {
    this.setState({ detailVisible: false, currentPluginId: null });
  };

  toggleType = type => {
    this.setState(prevState => ({
      expandedTypes: {
        ...prevState.expandedTypes,
        [type]: !prevState.expandedTypes[type],
      },
    }));
  };

  groupByType() {
    const { dataSource } = this.state;
    const groups = {};
    dataSource.forEach(item => {
      const type = item.pluginType;
      if (!groups[type]) {
        groups[type] = [];
      }
      groups[type].push(item);
    });
    return groups;
  }

  renderAction = (value, index, record) => {
    const { locale = {} } = this.props;
    const canSwitch = !record.critical && !record.exclusive;
    return (
      <div>
        <Button
          type="primary"
          text
          onClick={() => this.showDetail(record)}
          style={{ marginRight: 10 }}
        >
          {locale.detail || 'Detail'}
        </Button>
        {canSwitch && (
          <Button
            type={record.enabled ? 'normal' : 'primary'}
            text
            warning={record.enabled}
            onClick={() => this.updatePluginStatus(record)}
          >
            {record.enabled ? locale.disable || 'Disable' : locale.enable || 'Enable'}
          </Button>
        )}
      </div>
    );
  };

  renderTypeCards() {
    const { locale = {} } = this.props;
    const { expandedTypes, detailVisible, currentPluginId } = this.state;
    const groups = this.groupByType();
    const types = Object.keys(groups);

    if (types.length === 0) {
      return <div className="plugin-empty">{locale.pubNoData || 'No Data'}</div>;
    }

    return (
      <div className="plugin-type-list">
        {types.map(type => {
          const plugins = groups[type];
          const enabledCount = plugins.filter(p => p.enabled).length;
          const iconType = PLUGIN_TYPE_ICONS[type] || 'appstore';
          const expanded = !!expandedTypes[type];
          return (
            <div key={type} className="plugin-type-section">
              <div
                className={`plugin-type-card ${expanded ? 'expanded' : ''}`}
                onClick={() => this.toggleType(type)}
              >
                <div className="plugin-type-card-icon">
                  <Icon type={iconType} size="xl" />
                </div>
                <div className="plugin-type-card-body">
                  <div className="plugin-type-card-title">{locale[type] || type}</div>
                  <div className="plugin-type-card-meta">
                    <span className="meta-total">
                      {plugins.length} {locale.pluginCount || 'plugins'}
                    </span>
                    <span className="meta-separator">·</span>
                    <span className="meta-enabled">
                      {enabledCount} {locale.enabled || 'Enabled'}
                    </span>
                  </div>
                </div>
                <div className="plugin-type-card-arrow">
                  <Icon type={expanded ? 'arrow-down' : 'arrow-right'} size="xs" />
                </div>
              </div>
              {expanded && (
                <div className="plugin-type-table">
                  <Table dataSource={plugins} locale={{ empty: locale.pubNoData || 'No Data' }}>
                    <Column title={locale.pluginName || 'Plugin Name'} dataIndex="pluginName" />
                    <Column
                      title={locale.status || 'Status'}
                      dataIndex="enabled"
                      cell={val => (
                        <span className={`plugin-status-tag ${val ? 'enabled' : 'disabled'}`}>
                          {val ? locale.enabled || 'Enabled' : locale.disabled || 'Disabled'}
                        </span>
                      )}
                    />
                    <Column
                      title={locale.critical || 'Key Plugin'}
                      dataIndex="critical"
                      cell={val => (
                        <div className="status-dot-cell">
                          <span className={`status-dot ${val ? 'active' : 'inactive'}`} />
                          <span>{val ? locale.yes || 'Yes' : locale.no || 'No'}</span>
                        </div>
                      )}
                    />
                    <Column
                      title={locale.availableNodes || 'Available Nodes'}
                      dataIndex="availableNodeCount"
                      cell={(val, index, record) => (
                        <div className="status-dot-cell">
                          <span className={`status-dot ${val > 0 ? 'active' : 'inactive'}`} />
                          <span>{`${val} / ${record.totalNodeCount}`}</span>
                        </div>
                      )}
                    />
                    <Column title={locale.operation || 'Operation'} cell={this.renderAction} />
                  </Table>
                </div>
              )}
            </div>
          );
        })}

        <PluginDetail
          visible={detailVisible}
          pluginId={currentPluginId}
          onClose={this.closeDetail}
          onSuccess={() => this.queryPluginList()}
          locale={locale}
        />
      </div>
    );
  }

  render() {
    const { locale = {} } = this.props;

    return (
      <div className="main-container plugin-management">
        <Loading
          shape="flower"
          style={{ position: 'relative', width: '100%' }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <PageTitle title={locale.pluginManagement || 'Plugin Management'} />

          <div className="plugin-list-card">{this.renderTypeCards()}</div>
        </Loading>
      </div>
    );
  }
}

export default PluginList;
