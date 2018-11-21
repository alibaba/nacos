/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import './index.scss';
import { getParams, request, aliwareIntl } from '../../../globalLib';
import { Button, Dialog, Field, Form, Input } from '@alifd/next';

const FormItem = Form.Item;

class ConfigRollback extends React.Component {
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
    this.typeMap = {
      // 操作映射提示
      U: 'publish',
      I: aliwareIntl.get('com.alibaba.nacos.page.configRollback.delete'),
      D: 'publish',
    };
    this.typeMapName = {
      // 操作映射名
      U: aliwareIntl.get('com.alibaba.nacos.page.configRollback.updated'),
      I: aliwareIntl.get('com.alibaba.nacos.page.configRollback.inserted'),
      D: aliwareIntl.get('com.alibaba.nacos.page.configRollback.delete'),
    };
  }

  componentDidMount() {
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
    const tenant = getParams('namespace');
    this.props.history.push(
      `/historyRollback?serverId=${this.serverId}&group=${this.group}&dataId=${
        this.dataId
      }&namespace=${tenant}`
    );
  }

  onOpenConfirm() {
    const self = this;
    let type = 'post';
    if (this.opType.trim() === 'I') {
      type = 'delete';
    }
    Dialog.confirm({
      language: aliwareIntl.currentLanguageCode || 'zh-cn',
      title: aliwareIntl.get('com.alibaba.nacos.page.configRollback.please_confirm_rollback'),
      content: (
        <div style={{ marginTop: '-20px' }}>
          <h3>
            {aliwareIntl.get('com.alibaba.nacos.page.configRollback.determine')}{' '}
            {aliwareIntl.get('com.alibaba.nacos.page.configRollback.the_following_configuration')}
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
              Dialog.alert({
                language: aliwareIntl.currentLanguageCode || 'zh-cn',
                content: aliwareIntl.get(
                  'com.alibaba.nacos.page.configRollback.rollback_successful'
                ),
              });
            }
          },
        });
      },
    });
  }

  render() {
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
        <h1>{aliwareIntl.get('com.alibaba.nacos.page.configRollback.configuration_rollback')}</h1>
        <Form field={this.field}>
          <FormItem label="Data ID:" required {...formItemLayout}>
            <Input htmlType="text" readOnly {...init('dataId')} />
            <div style={{ marginTop: 10 }}>
              <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                {this.state.showmore
                  ? aliwareIntl.get('com.alibaba.nacos.page.configRollback.retracted')
                  : aliwareIntl.get('com.alibaba.nacos.page.configRollback.for_more_advanced')}
              </a>
            </div>
          </FormItem>
          <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
            <FormItem label="Group:" required {...formItemLayout}>
              <Input htmlType="text" readOnly {...init('group')} />
            </FormItem>
            <FormItem
              label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.home')}
              {...formItemLayout}
            >
              <Input htmlType="text" readOnly {...init('appName')} />
            </FormItem>
          </div>
          <FormItem
            label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.action_type')}
            required
            {...formItemLayout}
          >
            <Input htmlType="text" readOnly {...init('opType')} />
          </FormItem>
          <FormItem label="MD5:" required {...formItemLayout}>
            <Input htmlType="text" readOnly {...init('md5')} />
          </FormItem>
          <FormItem
            label={aliwareIntl.get('com.alibaba.nacos.page.configRollback.configuration')}
            required
            {...formItemLayout}
          >
            <Input.TextArea htmlType="text" multiple rows={15} readOnly {...init('content')} />
          </FormItem>
          <FormItem label=" " {...formItemLayout}>
            <Button
              type="primary"
              style={{ marginRight: 10 }}
              onClick={this.onOpenConfirm.bind(this)}
            >
              {aliwareIntl.get('com.alibaba.nacos.page.configRollback.rollback')}
            </Button>
            <Button type="normal" onClick={this.goList.bind(this)}>
              {aliwareIntl.get('com.alibaba.nacos.page.configRollback.return')}
            </Button>
          </FormItem>
        </Form>
      </div>
    );
  }
}

export default ConfigRollback;
