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
import { getParams, request } from '../../globalLib';
import { Button, ConfigProvider, Dialog, Field, Form, Select } from '@alifd/next';

const FormItem = Form.Item;
const { AutoComplete: Combobox } = Select;

/**
 * @deprecated
 */
@ConfigProvider.config
class CloneDialog extends React.Component {
  static displayName = 'CloneDialog';

  constructor(props) {
    super(props);
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
      policyLabel: '',
      total: 0,
      allPolicy: [],
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

  componentDidMount() {
    const { locale = {} } = this.props;
    this.setState({
      policyLabel: locale.terminate,
      allPolicy: [
        { value: 'abort', label: locale.terminate },
        { value: 'skip', label: locale.skip },
        { value: 'overwrite', label: locale.cover },
      ],
    });
  }

  openDialog(payload, callback) {
    const { locale = {} } = this.props;
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
            title: locale.getNamespaceFailed,
            content: res.message,
          });
        }
      },
    });
  }

  closeDialog() {
    this.setState({ visible: false });
  }

  setTenantTo(value) {
    this.field.setValue(value);
    this.setState({ tenantTo: value });
  }

  setPolicy(...value) {
    this.setState({
      policyLabel: value[1].label,
      policy: value[0],
    });
  }

  getQuery() {
    const { locale = {} } = this.props;
    if (this.state.records.length > 0) return locale.selectedEntry;
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
      query += `${locale.homeApplication + this.state.appName},`;
    }
    if (this.state.configTags.length !== 0) {
      query += `${locale.tags + this.state.configTags},`;
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
    const { locale = {} } = this.props;
    const { init } = this.field;
    const footer = (
      <div>
        <Button
          type="primary"
          onClick={this.doClone.bind(this)}
          {...{ disabled: this.state.total <= 0 }}
        >
          {locale.startCloning}
        </Button>
      </div>
    );

    return (
      <div>
        <Dialog
          visible={this.state.visible}
          footer={footer}
          footerAlign="center"
          style={{ width: 555 }}
          onCancel={this.closeDialog.bind(this)}
          onClose={this.closeDialog.bind(this)}
          title={`${locale.configurationCloning + this.state.serverId}）`}
        >
          <Form field={this.field}>
            <FormItem label={locale.source} {...this.formItemLayout}>
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.tenantFrom.name}</span>
                {` | ${this.state.tenantFrom.id}`}
              </p>
            </FormItem>
            <FormItem label={locale.configurationNumber} {...this.formItemLayout}>
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.total}</span> {this.getQuery()}{' '}
              </p>
            </FormItem>
            <FormItem label={locale.target} {...this.formItemLayout}>
              <Combobox
                style={{ width: '80%' }}
                size="medium"
                hasArrow
                placeholder={locale.selectNamespace}
                dataSource={this.state.namespaces}
                {...init('select', {
                  props: { onChange: this.setTenantTo.bind(this) },
                  rules: [{ required: true, message: locale.selectNamespace }],
                })}
              />
            </FormItem>
            <FormItem label={locale.conflict} {...this.formItemLayout}>
              <Select
                size="medium"
                hasArrow
                defaultValue={this.defaultPolicy}
                dataSource={this.state.allPolicy}
                onChange={this.setPolicy.bind(this)}
              />
            </FormItem>
          </Form>
        </Dialog>
      </div>
    );
  }
}

export default CloneDialog;
