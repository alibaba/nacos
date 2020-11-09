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
import { request } from '../../globalLib';
import { Button, ConfigProvider, Dialog, Field, Form, Input, Loading } from '@alifd/next';

import './index.scss';

const FormItem = Form.Item;

@ConfigProvider.config
class EditorNameSpace extends React.Component {
  static displayName = 'EditorNameSpace';

  static propTypes = {
    getNameSpaces: PropTypes.func,
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      dialogvisible: false,
      loading: false,
    };
    this.field = new Field(this);
  }

  openDialog(record) {
    this.getNamespaceDetail(record);
    this.setState({
      dialogvisible: true,
      type: record.type,
    });
  }

  closeDialog() {
    this.setState({
      dialogvisible: false,
    });
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

  getNamespaceDetail(record) {
    const { locale = {} } = this.props;
    this.field.setValues(record);
    request({
      type: 'get',
      url: `v1/console/namespaces?show=all&namespaceId=${record.namespace}`,
      success: res => {
        if (res !== null) {
          this.field.setValue('namespaceDesc', res.namespaceDesc);
        } else {
          Dialog.alert({
            title: locale.notice,
            content: res.message,
          });
        }
      },
      error: () => {
        window.namespaceList = [];
        this.handleNameSpaces(window.namespaceList);
      },
    });
  }

  handleSubmit() {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      request({
        type: 'put',
        beforeSend: () => {
          this.openLoading();
        },
        url: 'v1/console/namespaces',
        contentType: 'application/x-www-form-urlencoded',
        data: {
          namespace: values.namespace,
          namespaceShowName: values.namespaceShowName,
          namespaceDesc: values.namespaceDesc,
        },
        success: res => {
          if (res === true) {
            this.closeDialog();
            this.props.getNameSpaces();
            this.refreshNameSpace(); // 刷新全局namespace
          } else {
            Dialog.alert({
              title: locale.notice,
              content: res.message,
            });
          }
        },
        complete: () => {
          this.closeLoading();
        },
      });
    });
  }

  refreshNameSpace() {
    setTimeout(() => {
      request({
        type: 'get',
        url: 'v1/console/namespaces',
        success: res => {
          if (res.code === 200) {
            window.namespaceList = res.data;
          }
        },
      });
    }, 2000);
  }

  validateChart(rule, value, callback) {
    const { locale = {} } = this.props;
    const chartReg = /[@#\$%\^&\*]+/g;
    if (chartReg.test(value)) {
      callback(locale.pleaseDo);
    } else {
      callback();
    }
  }

  render() {
    const { locale = {} } = this.props;
    const formItemLayout = {
      labelCol: { fixedSpan: 6 },
      wrapperCol: { span: 18 },
    };

    const footer =
      this.state.type === 0 ? (
        <div />
      ) : (
        <Button type="primary" onClick={this.handleSubmit.bind(this)}>
          {locale.publicSpace}
        </Button>
      );
    return (
      <div>
        <Dialog
          title={locale.confirmModify}
          style={{ width: '50%' }}
          visible={this.state.dialogvisible}
          footer={footer}
          onCancel={this.closeDialog.bind(this)}
          onClose={this.closeDialog.bind(this)}
        >
          <Loading
            tip={locale.editNamespace}
            style={{ width: '100%', position: 'relative' }}
            visible={this.state.loading}
          >
            <Form field={this.field}>
              <FormItem label={locale.load} required {...formItemLayout}>
                <Input
                  {...this.field.init('namespaceShowName', {
                    rules: [
                      { required: true, message: locale.namespace },
                      { validator: this.validateChart.bind(this) },
                    ],
                  })}
                  disabled={this.state.type === 0}
                />
              </FormItem>
              <FormItem label={locale.description} required {...formItemLayout}>
                <Input
                  {...this.field.init('namespaceDesc', {
                    rules: [
                      { required: true, message: locale.namespaceDesc },
                      { validator: this.validateChart.bind(this) },
                    ],
                  })}
                  disabled={this.state.type === 0}
                />
              </FormItem>
            </Form>
          </Loading>
        </Dialog>
      </div>
    );
  }
}

export default EditorNameSpace;
