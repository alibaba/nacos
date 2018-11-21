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
import { request, aliwareIntl } from '../../globalLib';
import { Button, Dialog, Field, Form, Input, Loading } from '@alifd/next';

const FormItem = Form.Item;

class EditorNameSpace extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      dialogvisible: false,
      loading: false,
    };
    this.field = new Field(this);
  }

  componentDidMount() {}

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
    this.field.setValues(record);
    request({
      type: 'get',
      url: `v1/console/namespaces?show=all&namespaceId=${record.namespace}`,
      success: res => {
        if (res !== null) {
          this.field.setValue('namespaceDesc', res.namespaceDesc);
        } else {
          Dialog.alert({
            language: aliwareIntl.currentLanguageCode || 'zh-cn',
            title: aliwareIntl.get('com.alibaba.nacos.component.NameSpaceList.Prompt'),
            content: res.message,
          });
        }
      },
      error: res => {
        window.namespaceList = [];
        this.handleNameSpaces(window.namespaceList);
      },
    });
  }

  handleSubmit() {
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
              language: aliwareIntl.currentLanguageCode || 'zh-cn',
              title: aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.prompt'),
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
    const chartReg = /[@#\$%\^&\*]+/g;

    if (chartReg.test(value)) {
      callback(aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.please_do'));
    } else {
      callback();
    }
  }

  render() {
    const formItemLayout = {
      labelCol: {
        fixedSpan: 6,
      },
      wrapperCol: {
        span: 18,
      },
    };

    const footer =
      this.state.type === 0 ? (
        <div />
      ) : (
        <Button type="primary" onClick={this.handleSubmit.bind(this)}>
          {aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.public_space')}
        </Button>
      );
    return (
      <div>
        <Dialog
          title={aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.confirm_modify')}
          style={{ width: '50%' }}
          visible={this.state.dialogvisible}
          footer={footer}
          onCancel={this.closeDialog.bind(this)}
          onClose={this.closeDialog.bind(this)}
          language={aliwareIntl.currentLanguageCode}
        >
          <Loading
            tip={aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.edit_namespace')}
            style={{ width: '100%', position: 'relative' }}
            visible={this.state.loading}
          >
            <Form field={this.field}>
              <FormItem
                label={aliwareIntl.get('com.alibaba.nacos.component.EditorNameSpace.load')}
                required
                {...formItemLayout}
              >
                <Input
                  {...this.field.init('namespaceShowName', {
                    rules: [
                      {
                        required: true,
                        message: aliwareIntl.get(
                          'com.alibaba.nacos.component.EditorNameSpace.namespace'
                        ),
                      },
                      { validator: this.validateChart.bind(this) },
                    ],
                  })}
                  disabled={this.state.type === 0}
                />
              </FormItem>
              <FormItem
                label={aliwareIntl.get('nacos.page.configdetail.Description')}
                required
                {...formItemLayout}
              >
                <Input
                  {...this.field.init('namespaceDesc', {
                    rules: [
                      {
                        required: true,
                        message: aliwareIntl.get(
                          'com.alibaba.nacos.component.EditorNameSpace.namespace'
                        ),
                      },
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
