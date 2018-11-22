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
import { getParams, request, aliwareIntl } from '../../globalLib';
import { Button, Dialog, Field, Form, Select } from '@alifd/next';

const FormItem = Form.Item;
const { AutoComplete: Combobox } = Select;

class CloneDialog extends React.Component {
  constructor(props) {
    super(props);
    this.allPolicy = [
      {
        value: 'abort',
        label: aliwareIntl.get('nacos.component.CloneDialog.Terminate_the_clone0'),
      },
      { value: 'skip', label: aliwareIntl.get('nacos.component.CloneDialog.skip') },
      { value: 'overwrite', label: aliwareIntl.get('nacos.component.CloneDialog.cover') },
    ];
    this.defaultPolicy = 'abort';
    this.state = {
      visible: false,
      serverId: '',
      tenantFrom: {},
      tenantTo: '',
      dataId: '',
      group: '',
      appName: '',
      configTags: '',
      records: [],
      namespaces: [],
      policy: this.defaultPolicy,
      policyLabel: aliwareIntl.get('nacos.component.CloneDialog.Terminate_the_clone0'),
      total: 0,
    };
    this.field = new Field(this);
    this.formItemLayout = {
      labelCol: {
        fixedSpan: 6,
      },
      wrapperCol: {
        span: 18,
      },
    };
  }

  componentDidMount() {}

  openDialog(payload, callback) {
    const serverId = getParams('serverId') || 'center';
    this.checkData = payload.checkData;
    this.callback = callback;
    request({
      type: 'get',
      url: `/diamond-ops/service/serverId/${serverId}/namespaceInfo`,
      success: res => {
        if (res.code === 200) {
          const dataSource = [];
          res.data.forEach(value => {
            if (value.namespace !== payload.tenantFrom.id) {
              dataSource.push({
                value: value.namespace,
                label: `${value.namespaceShowName} | ${value.namespace}`,
              });
            }
          });
          this.setState({
            visible: true,
            serverId: payload.serverId,
            tenantFrom: payload.tenantFrom,
            tenantTo: '',
            dataId: payload.dataId,
            group: payload.group,
            appName: payload.appName,
            configTags: payload.configTags,
            records: payload.records,
            namespaces: dataSource,
            total: payload.total,
          });
          this.field.setValue('select', '');
        } else {
          Dialog.alert({
            language: aliwareIntl.currentLanguageCode || 'zh-cn',
            title: aliwareIntl.get('nacos.component.CloneDialog.get_the_namespace_failed'),
            content: res.message,
          });
        }
      },
    });
  }

  closeDialog() {
    this.setState({
      visible: false,
    });
  }

  setTenantTo(value) {
    this.field.setValue(value);
    this.setState({
      tenantTo: value,
    });
  }

  setPolicy(...value) {
    this.setState({
      policyLabel: value[1].label,
      policy: value[0],
    });
  }

  getQuery() {
    if (this.state.records.length > 0) {
      return aliwareIntl.get('nacos.component.CloneDialog.|_the_selected_entry4');
    }
    if (
      this.state.dataId === '' &&
      this.state.group === '' &&
      this.state.appName === '' &&
      this.state.configTags.length === 0
    ) {
      return '';
    }
    let query = ' |';
    if (this.state.dataId !== '') {
      query += ` DataId: ${this.state.dataId},`;
    }
    if (this.state.group !== '') {
      query += ` Group: ${this.state.group},`;
    }
    if (this.state.appName !== '') {
      query += `${aliwareIntl.get('nacos.component.CloneDialog.HOME_Application') +
        this.state.appName},`;
    }
    if (this.state.configTags.length !== 0) {
      query += `${aliwareIntl.get('nacos.component.CloneDialog.tags') + this.state.configTags},`;
    }
    return query.substr(0, query.length - 1);
  }

  doClone() {
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      this.closeDialog();
      this.checkData.tenantTo = this.state.tenantTo;
      this.checkData.policy = this.state.policy;
      this.callback(this.checkData, this.state.policyLabel);
    });
  }

  render() {
    const { init } = this.field;
    const footer = (
      <div>
        <Button
          type="primary"
          onClick={this.doClone.bind(this)}
          {...{ disabled: this.state.total <= 0 }}
        >
          {aliwareIntl.get('nacos.component.CloneDialog.start_cloning')}
        </Button>
      </div>
    );

    return (
      <div>
        <Dialog
          visible={this.state.visible}
          footer={footer}
          footerAlign="center"
          language={aliwareIntl.currentLanguageCode || 'zh-cn'}
          style={{ width: 555 }}
          onCancel={this.closeDialog.bind(this)}
          onClose={this.closeDialog.bind(this)}
          title={`${aliwareIntl.get('nacos.component.CloneDialog.configuration_cloning\uFF08') +
            this.state.serverId}）`}
        >
          <Form field={this.field}>
            <FormItem
              label={aliwareIntl.get('nacos.component.CloneDialog.source_space')}
              {...this.formItemLayout}
            >
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.tenantFrom.name}</span>
                {` | ${this.state.tenantFrom.id}`}
              </p>
            </FormItem>
            <FormItem
              label={aliwareIntl.get('nacos.component.CloneDialog.configuration_number')}
              {...this.formItemLayout}
            >
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.total}</span> {this.getQuery()}{' '}
              </p>
            </FormItem>
            <FormItem
              label={aliwareIntl.get('nacos.component.CloneDialog.target_space')}
              {...this.formItemLayout}
            >
              <Combobox
                style={{ width: '80%' }}
                size="medium"
                hasArrow
                placeholder={aliwareIntl.get('nacos.component.CloneDialog.select_namespace')}
                dataSource={this.state.namespaces}
                {...init('select', {
                  props: {
                    onChange: this.setTenantTo.bind(this),
                  },
                  rules: [
                    {
                      required: true,
                      message: aliwareIntl.get('nacos.component.CloneDialog.select_namespace'),
                    },
                  ],
                })}
                language={aliwareIntl.currentLanguageCode}
              />
            </FormItem>
            <FormItem
              label={aliwareIntl.get('nacos.component.CloneDialog.the_same_configuration')}
              {...this.formItemLayout}
            >
              <Select
                size="medium"
                hasArrow
                defaultValue={this.defaultPolicy}
                dataSource={this.allPolicy}
                onChange={this.setPolicy.bind(this)}
                language={aliwareIntl.currentLanguageCode}
              />
            </FormItem>
          </Form>
        </Dialog>
      </div>
    );
  }
}

export default CloneDialog;
