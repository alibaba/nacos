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

class NewNameSpace extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      dialogvisible: false,
      loading: false,
      disabled: false,
      dataSource: [],
    };

    this.field = new Field(this);
    this.disabled = false;
  }

  componentDidMount() {
    this.groupLabel = document.getElementById('groupwrapper');
  }

  openDialog(dataSource) {
    this.setState({
      dialogvisible: true,
      disabled: false,
      dataSource,
    });
    this.disabled = false;
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

  showGroup() {
    this.groupLabel.style.display = 'block';
  }

  hideGroup() {
    this.groupLabel.style.display = 'none';
  }

  changeType(value) {
    if (value === 0) {
      this.showGroup();
    } else {
      this.hideGroup();
    }
  }

  handleSubmit() {
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      const flag = this.state.dataSource.every(val => {
        if (val.namespaceShowName === values.namespaceShowName) {
          return false;
        }
        return true;
      });
      if (!flag) {
        Dialog.alert({
          content: aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.norepeat'),
          language: aliwareIntl.currentLanguageCode,
        });
        return;
      }
      this.disabled = true;
      this.setState({
        disabled: true,
      });
      request({
        type: 'post',
        url: 'v1/console/namespaces',
        contentType: 'application/x-www-form-urlencoded',
        beforeSend: () => {
          this.openLoading();
        },
        data: {
          namespaceName: values.namespaceShowName,
          namespaceDesc: values.namespaceDesc,
        },
        success: res => {
          this.disabled = false;
          this.setState({
            disabled: false,
          });
          if (res === true) {
            this.closeDialog();
            this.props.getNameSpaces();
            this.refreshNameSpace(); // 刷新全局namespace
          } else {
            Dialog.alert({
              title: aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.prompt'),
              content: res.message,
              language: aliwareIntl.currentLanguageCode,
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
      callback(aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.input'));
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

    const footer = (
      <div>
        <Button type="primary" onClick={this.handleSubmit.bind(this)} disabled={this.disabled}>
          {aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.confirm')}
        </Button>
        <Button type="normal" onClick={this.closeDialog.bind(this)} style={{ marginLeft: 5 }}>
          {aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.cancel')}
        </Button>
      </div>
    );
    return (
      <div>
        <Dialog
          title={aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.newnamespce')}
          style={{ width: '50%' }}
          visible={this.state.dialogvisible}
          onOk={this.handleSubmit.bind(this)}
          onCancel={this.closeDialog.bind(this)}
          footer={footer}
          onClose={this.closeDialog.bind(this)}
          language={aliwareIntl.currentLanguageCode}
        >
          <Form field={this.field}>
            <Loading
              tip={aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.loading')}
              style={{ width: '100%', position: 'relative' }}
              visible={this.state.loading}
            >
              <FormItem
                label={aliwareIntl.get('com.alibaba.nacos.component.NewNameSpace.name')}
                required
                {...formItemLayout}
              >
                <Input
                  {...this.field.init('namespaceShowName', {
                    rules: [
                      {
                        required: true,
                        message: aliwareIntl.get(
                          'com.alibaba.nacos.component.NewNameSpace.namespacenotnull'
                        ),
                      },
                      { validator: this.validateChart.bind(this) },
                    ],
                  })}
                  style={{ width: '100%' }}
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
                          'com.alibaba.nacos.component.NewNameSpace.namespacenotnull'
                        ),
                      },
                      { validator: this.validateChart.bind(this) },
                    ],
                  })}
                  style={{ width: '100%' }}
                />
              </FormItem>
            </Loading>
          </Form>
        </Dialog>
      </div>
    );
  }
}

export default NewNameSpace;
