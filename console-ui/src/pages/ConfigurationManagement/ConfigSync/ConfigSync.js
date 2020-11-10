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
import { Button, Checkbox, ConfigProvider, Dialog, Field, Form, Input, Loading } from '@alifd/next';
import SuccessDialog from '../../../components/SuccessDialog';
import { getParams, request } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';

import './index.scss';

@ConfigProvider.config
class ConfigSync extends React.Component {
  static displayName = 'ConfigSync';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.successDialog = React.createRef();
    this.field = new Field(this);
    this.dataId = getParams('dataId') || 'yanlin';
    this.group = getParams('group') || '';
    this.serverId = getParams('serverId') || '';

    this.state = {
      configType: 0,

      envvalues: [],
      commonvalue: [],
      envComponent: '',
      envGroups: [],
      envlist: [],
      loading: false,
      showmore: false,
    };
    this.codeValue = '';
    this.mode = 'text';
    this.ips = '';
  }

  componentDidMount() {
    this.getDataDetail();
    //  this.getDomain();
  }

  toggleMore() {
    this.setState({
      showmore: !this.state.showmore,
    });
  }

  getEnvList(value) {
    this.setState({
      envvalues: value,
    });
    this.envs = value;
  }

  getDomain() {
    const self = this;
    request({
      url: '/diamond-ops/env/domain',
      success(data) {
        if (data.code === 200) {
          const { envGroups } = data.data;

          self.setState({
            envGroups,
          });
        }
      },
    });
  }

  getDataDetail() {
    const self = this;
    const { locale = {} } = this.props;
    this.tenant = getParams('namespace') || '';
    this.serverId = getParams('serverId') || 'center';
    let url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}/tenant/${this.tenant}?id=`;
    if (this.tenant === 'global' || !this.tenant) {
      url = `/diamond-ops/configList/detail/serverId/${this.serverId}/dataId/${this.dataId}/group/${this.group}?id=`;
    }
    request({
      url,
      beforeSend() {
        self.openLoading();
      },
      success(result) {
        if (result.code === 200) {
          const { data = {} } = result;

          self.field.setValue('dataId', data.dataId);
          // self.field.setValue('content', data.content);
          self.field.setValue('appName', data.appName);
          // self.field.setValue('envs', self.serverId);
          self.field.setValue('group', data.group);
          // self.field.setValue('md5', data.md5);
          self.field.setValue('content', data.content || '');
          const env = data.envs || [];
          const envvalues = [];
          const envlist = [];
          for (let i = 0; i < env.length; i++) {
            envlist.push({
              value: env[i].serverId,
              label: env[i].name,
            });
            if (env[i].serverId === self.serverId) {
              envvalues.push(self.serverId);
            }
          }
          self.setState({
            envlist,
            envvalues,
            // self.setState({
            //     envname: env.name,
            // })
            // self.serverId = env.serverId;
          });
        } else {
          Dialog.alert({
            title: locale.error,
            content: result.message,
          });
        }
      },
      complete() {
        self.closeLoading();
      },
    });
  }

  goList() {
    this.props.history.push(
      `/configurationManagement?serverId=${this.serverId}&group=${this.group}&dataId=${this.dataId}`
    );
  }

  sync() {
    const self = this;
    const { locale = {} } = this.props;
    const payload = {
      dataId: this.field.getValue('dataId'),
      appName: this.field.getValue('appName'),
      group: this.field.getValue('group'),
      content: this.field.getValue('content'),
      betaIps: this.ips,
      targetEnvs: this.envs,
    };
    request({
      type: 'put',
      contentType: 'application/json',
      url: `/diamond-ops/configList/serverId/${this.serverId}/dataId/${payload.dataId}/group/${payload.group}?id=`,
      data: JSON.stringify(payload),
      success(res) {
        const _payload = {};
        _payload.maintitle = locale.syncConfigurationMain;
        _payload.title = locale.syncConfiguration;
        _payload.content = '';
        _payload.dataId = payload.dataId;
        _payload.group = payload.group;
        _payload.isok = res.code === 200;
        if (!_payload.isok) {
          _payload.isok = false;
          _payload.message = res.message;
        }
        self.successDialog.current.openDialog(_payload);
      },
    });
  }

  syncResult() {
    const dataId = this.field.getValue('dataId');
    const gruop = this.field.getValue('group');
    this.props.history.push(
      generateUrl('/diamond-ops/static/pages/config-sync/index.html', { dataId, gruop })
    );
  }

  changeEnv(values) {
    this.targetEnvs = values;
    this.setState({
      envvalues: values,
    });
  }

  getIps(value) {
    this.ips = value;
  }

  goResult() {
    const { serverId, dataId, group } = this;
    this.props.history.push(generateUrl('/consistencyEfficacy', { serverId, dataId, group }));
  }

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  render() {
    const { init } = this.field;
    const { locale = {} } = this.props;
    const formItemLayout = {
      labelCol: {
        span: 2,
      },
      wrapperCol: {
        span: 22,
      },
    };

    return (
      <div style={{ padding: 10 }}>
        <Loading
          shape="flower"
          style={{ position: 'relative', width: '100%' }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <h1>{locale.syncConfiguration}</h1>
          <Form field={this.field}>
            <Form.Item label="Data ID:" required {...formItemLayout}>
              <Input htmlType="text" disabled={'disabled'} {...init('dataId')} />
              <div style={{ marginTop: 10 }}>
                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                  {this.state.showmore ? locale.collapse : locale.advancedOptions}
                </a>
              </div>
            </Form.Item>
            <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
              <Form.Item label="Group ID:" required {...formItemLayout}>
                <Input htmlType="text" disabled={'disabled'} {...init('group')} />
              </Form.Item>
              <Form.Item label={locale.home} required {...formItemLayout}>
                <Input htmlType="text" disabled={'disabled'} {...init('appName')} />
              </Form.Item>
            </div>
            <Form.Item label={locale.region} required {...formItemLayout}>
              <Input htmlType="text" disabled={'disabled'} {...init('envs')} />
            </Form.Item>

            <Form.Item label={locale.configuration} required {...formItemLayout}>
              <Input.TextArea
                htmlType="text"
                multiple
                rows={15}
                disabled={'disabled'}
                {...init('content')}
              />
            </Form.Item>
            <Form.Item label={locale.target} required {...formItemLayout}>
              <div>
                <Checkbox.Group
                  value={this.state.envvalues}
                  onChange={this.changeEnv.bind(this)}
                  dataSource={this.state.envlist}
                />
              </div>
            </Form.Item>
            <Form.Item label=" " {...formItemLayout}>
              <div style={{ textAlign: 'right' }}>
                <Button type="primary" onClick={this.sync.bind(this)} style={{ marginRight: 10 }}>
                  {locale.sync}
                </Button>
                {}
                <Button type="light" onClick={this.goList.bind(this)}>
                  {locale.back}
                </Button>
              </div>
            </Form.Item>
          </Form>
          <SuccessDialog ref={this.successDialog} />
        </Loading>
      </div>
    );
  }
}

export default ConfigSync;
