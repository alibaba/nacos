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
import { request } from '../../globalLib';
import { Button, ConfigProvider, Dialog, Field, Form, Input, Loading } from '@alifd/next';

import './index.scss';
import PropTypes from 'prop-types';

const FormItem = Form.Item;

const formItemLayout = {
  labelCol: { fixedSpan: 6 },
  wrapperCol: { span: 18 },
};

@ConfigProvider.config
class NewNameSpace extends React.Component {
  static displayName = 'NewNameSpace';

  static propTypes = {
    locale: PropTypes.object,
    getNameSpaces: PropTypes.func,
  };

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
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) return;
      const flag = this.state.dataSource.every(
        val => val.namespaceShowName !== values.namespaceShowName
      );
      if (!flag) {
        Dialog.alert({ content: locale.norepeat });
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
        beforeSend: () => this.openLoading(),
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
              title: locale.notice,
              content: res.message,
            });
          }
        },
        complete: () => this.closeLoading(),
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
      callback(locale.input);
    } else {
      callback();
    }
  }

  render() {
    const { locale = {} } = this.props;
    const footer = (
      <div>
        <Button type="primary" onClick={this.handleSubmit.bind(this)} disabled={this.disabled}>
          {locale.ok}
        </Button>
        <Button type="normal" onClick={this.closeDialog.bind(this)} style={{ marginLeft: 5 }}>
          {locale.cancel}
        </Button>
      </div>
    );
    return (
      <div>
        <Dialog
          title={locale.newnamespce}
          style={{ width: '50%' }}
          visible={this.state.dialogvisible}
          onOk={this.handleSubmit.bind(this)}
          onCancel={this.closeDialog.bind(this)}
          footer={footer}
          onClose={this.closeDialog.bind(this)}
        >
          <Form field={this.field}>
            <Loading
              tip={locale.loading}
              style={{ width: '100%', position: 'relative' }}
              visible={this.state.loading}
            >
              <FormItem label={locale.name} required {...formItemLayout}>
                <Input
                  {...this.field.init('namespaceShowName', {
                    rules: [
                      {
                        required: true,
                        message: locale.namespacenotnull,
                      },
                      { validator: this.validateChart.bind(this) },
                    ],
                  })}
                  style={{ width: '100%' }}
                />
              </FormItem>
              <FormItem label={locale.description} required {...formItemLayout}>
                <Input
                  {...this.field.init('namespaceDesc', {
                    rules: [
                      {
                        required: true,
                        message: locale.namespacedescnotnull,
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
