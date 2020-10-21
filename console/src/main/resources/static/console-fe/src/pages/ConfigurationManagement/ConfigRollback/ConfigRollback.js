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
import { getParams, request } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';
import { Button, ConfigProvider, Dialog, Field, Form, Input } from '@alifd/next';

import './index.scss';

const FormItem = Form.Item;

@ConfigProvider.config
class ConfigRollback extends React.Component {
  static displayName = 'ConfigRollback';

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.dataId = getParams('dataId') || 'yanlin';
    this.group = getParams('group') || 'DEFAULT_GROUP';
    this.serverId = getParams('serverId') || 'center';
    this.nid = getParams('nid') || '';
    this.state = {
      envName: '',
      visible: false,
      showmore: false,
    };
    // this.params = window.location.hash.split('?')[1]||'';
  }

  static propTypes = {
    history: PropTypes.object,
    locale: PropTypes.object,
  };

  componentDidMount() {
    const { locale = {} } = this.props;
    this.typeMap = {
      // 操作映射提示
      U: 'publish',
      I: locale.rollbackDelete,
      D: 'publish',
    };
    this.typeMapName = {
      // 操作映射名
      U: locale.update,
      I: locale.insert,
      D: locale.rollbackDelete,
    };
    this.getDataDetail();
  }

  toggleMore() {
    this.setState({
      showmore: !this.state.showmore,
    });
  }

  getDataDetail() {
    const self = this;
    this.tenant = getParams('namespace') || '';
    this.serverId = getParams('serverId') || 'center';
    const url = `v1/cs/history?dataId=${this.dataId}&group=${this.group}&nid=${this.nid}`;
    request({
      url,
      success(result) {
        if (result != null) {
          const data = result;
          const envName = self.serverId;
          self.id = data.id; // 详情的id
          self.field.setValue('dataId', data.dataId);
          self.field.setValue('content', data.content);
          self.field.setValue('appName', data.appName);
          self.field.setValue('opType', self.typeMapName[data.opType.trim()]);
          self.opType = data.opType; // 当前回滚类型I:插入,D:删除,U:'更新'
          self.field.setValue('group', data.group);
          self.field.setValue('md5', data.md5);
          self.field.setValue('envName', envName);
          self.setState({
            envName,
          });
        }
      },
    });
  }

  goList() {
    const namespace = getParams('namespace');
    const { serverId, dataId, group } = this;
    this.props.history.push(
      generateUrl('/historyRollback', { serverId, dataId, group, namespace })
    );
  }

  onOpenConfirm() {
    const { locale = {} } = this.props;
    const self = this;
    let type = 'post';
    if (this.opType.trim() === 'I') {
      type = 'delete';
    }
    Dialog.confirm({
      title: locale.rollBack,
      content: (
        <div style={{ marginTop: '-20px' }}>
          <h3>
            {locale.determine} {locale.followingConfiguration}
          </h3>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
            <span style={{ color: '#c7254e' }}>{self.field.getValue('dataId')}</span>
          </p>
          <p>
            <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
            <span style={{ color: '#c7254e' }}>{self.field.getValue('group')}</span>
          </p>
        </div>
      ),
      onOk() {
        self.tenant = getParams('namespace') || '';
        self.serverId = getParams('serverId') || 'center';
        self.dataId = self.field.getValue('dataId');
        self.group = self.field.getValue('group');
        let postData = {
          appName: self.field.getValue('appName'),
          dataId: self.dataId,
          group: self.group,
          content: self.field.getValue('content'),
          tenant: self.tenant,
        };

        let url = 'v1/cs/configs';
        if (self.opType.trim() === 'I') {
          url = `v1/cs/configs?dataId=${self.dataId}&group=${self.group}`;
          postData = {};
        }

        // ajax
        request({
          type,
          contentType: 'application/x-www-form-urlencoded',
          url,
          data: postData,
          success(data) {
            if (data === true) {
              Dialog.alert({ content: locale.rollbackSuccessful });
            }
          },
        });
      },
    });
  }

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const formItemLayout = {
      labelCol: {
        fixedSpan: 6,
      },
      wrapperCol: {
        span: 18,
      },
    };
    return (
      <div style={{ padding: 10 }}>
        <h1>{locale.configurationRollback}</h1>
        <Form field={this.field}>
          <FormItem label="Data ID:" required {...formItemLayout}>
            <Input htmlType="text" readOnly {...init('dataId')} />
            <div style={{ marginTop: 10 }}>
              <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                {this.state.showmore ? locale.collapse : locale.more}
              </a>
            </div>
          </FormItem>
          <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
            <FormItem label="Group:" required {...formItemLayout}>
              <Input htmlType="text" readOnly {...init('group')} />
            </FormItem>
            <FormItem label={locale.home} {...formItemLayout}>
              <Input htmlType="text" readOnly {...init('appName')} />
            </FormItem>
          </div>
          <FormItem label={locale.actionType} required {...formItemLayout}>
            <Input htmlType="text" readOnly {...init('opType')} />
          </FormItem>
          <FormItem label="MD5:" required {...formItemLayout}>
            <Input htmlType="text" readOnly {...init('md5')} />
          </FormItem>
          <FormItem label={locale.configuration} required {...formItemLayout}>
            <Input.TextArea htmlType="text" multiple rows={15} readOnly {...init('content')} />
          </FormItem>
          <FormItem label=" " {...formItemLayout}>
            <Button
              type="primary"
              style={{ marginRight: 10 }}
              onClick={this.onOpenConfirm.bind(this)}
            >
              {locale.rollBack}
            </Button>
            <Button type="normal" onClick={this.goList.bind(this)}>
              {locale.back}
            </Button>
          </FormItem>
        </Form>
      </div>
    );
  }
}

export default ConfigRollback;
