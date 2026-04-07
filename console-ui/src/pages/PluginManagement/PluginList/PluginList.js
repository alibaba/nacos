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
import { Button, Dialog, Loading, Select, Table, Message, ConfigProvider } from '@alifd/next';
import { request } from '../../../globalLib';
import PageTitle from '../../../components/PageTitle';
import PluginDetail from './PluginDetail';
import './PluginList.scss';
const { Column } = Table;

@ConfigProvider.config
class PluginList extends React.Component {
  static displayName = 'PluginManagement';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      dataSource: [],
      pluginType: '',
      status: '', // Filter status: '', 'enabled', 'disabled'
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
    const { pluginType } = this.state;
    const parameter = pluginType ? `pluginType=${pluginType}` : '';

    request({
      url: `v3/console/plugin/list?${parameter}`,
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

  renderAction = (value, index, record) => {
    const { locale = {} } = this.props;
    // Hide switch button for critical plugins (datasource) or exclusive plugins (auth, datasource)
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

  render() {
    const { locale = {} } = this.props;
    const { pluginType, status, detailVisible, currentPluginId, dataSource } = this.state;

    const pluginTypes = [
      { value: 'auth', label: locale.auth || 'Authentication' },
      { value: 'datasource-dialect', label: locale['datasource-dialect'] || 'Datasource Dialect' },
      { value: 'config-change', label: locale['config-change'] || 'Config Change' },
      { value: 'encryption', label: locale.encryption || 'Encryption' },
      { value: 'trace', label: locale.trace || 'Trace' },
      { value: 'environment', label: locale.environment || 'Environment' },
      { value: 'control', label: locale.control || 'Control' },
    ];

    const statusOptions = [
      { value: 'enabled', label: locale.enabled || 'Enabled' },
      { value: 'disabled', label: locale.disabled || 'Disabled' },
    ];

    // Client-side filtering for Status
    let filteredDataSource = dataSource;
    if (status) {
      const isEnabled = status === 'enabled';
      filteredDataSource = dataSource.filter(item => item.enabled === isEnabled);
    }

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

          <div className="plugin-list-card">
            <div className="plugin-list-toolbar">
              <div className="filter-section">
                <span style={{ marginRight: 10 }}>{locale.pluginType || 'Plugin Type'}:</span>
                <Select
                  hasClear
                  style={{ width: 200, marginRight: 20 }}
                  value={pluginType}
                  onChange={val => this.setState({ pluginType: val }, () => this.queryPluginList())}
                  dataSource={pluginTypes}
                  placeholder={locale.pluginTypeAll || 'All Types'}
                />

                <span style={{ marginRight: 10 }}>{locale.status || 'Status'}:</span>
                <Select
                  hasClear
                  style={{ width: 150 }}
                  value={status}
                  onChange={val => this.setState({ status: val })}
                  dataSource={statusOptions}
                  placeholder={locale.statusAll || 'All Statuses'}
                />
                <Button
                  type="primary"
                  style={{ marginLeft: 20 }}
                  onClick={() => this.queryPluginList()}
                >
                  {locale.refresh || 'Refresh'}
                </Button>
              </div>
            </div>

            <Table
              dataSource={filteredDataSource}
              locale={{ empty: locale.pubNoData || 'No Data' }}
            >
              <Column title={locale.pluginName || 'Plugin Name'} dataIndex="pluginName" />
              <Column
                title={locale.pluginType || 'Plugin Type'}
                dataIndex="pluginType"
                cell={val => locale[val] || val}
              />
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

          <PluginDetail
            visible={detailVisible}
            pluginId={currentPluginId}
            onClose={this.closeDetail}
            onSuccess={() => this.queryPluginList()}
            locale={locale}
          />
        </Loading>
      </div>
    );
  }
}

export default PluginList;
