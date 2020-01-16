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
import PropTypes from 'prop-types';
import RegionGroup from 'components/RegionGroup';
import { ConfigProvider, Input, Field, Form, Message } from '@alifd/next';
import { getParams, setParams, request } from '../../globalLib';

import './index.scss';

const FormItem = Form.Item;

@ConfigProvider.config
class Password extends React.Component {
  static displayName = 'Password';

  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.state = {};
  }

  componentDidMount() {}

  validatePassword(rule, value, callback) {
    const { locale = {} } = this.props;
    if (this.field.getValue('newPassword') !== this.field.getValue('confirmNewPassword')) {
      callback(locale.passwordNotConsistent);
    } else {
      callback();
    }
  }

  handleSubmit = () => {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      request({
        type: 'post',
        url: 'v1/auth/login',
        data: values,
        success: ({ code, data }) => {
          if (code === 200) {
            // TODO: 封装一个方法存储、读取token
            localStorage.setItem('token', data);
            // TODO: 使用react router
            this.props.history.push('/');
          }
          if (code === 401) {
            Message.error({
              content: locale.invalidUsernameOrPassword,
            });
          }
        },
        error: () => {
          Message.error({
            content: locale.invalidUsernameOrPassword,
          });
        },
      });
    });
  };

  changePassword() {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      request({
        type: 'put',
        url: 'v1/auth/password',
        data: values,
        success: ({ code, data }) => {
          if (code === 200) {
            window.localStorage.clear();
            this.props.history.push('/login');
          }
          if (code === 401) {
            Message.error({
              content: locale.invalidPassword,
            });
          }
        },
        error: () => {
          Message.error({
            content: locale.invalidPassword,
          });
        },
      });
    });
  }

  render() {
    const { locale = {} } = this.props;
    const formItemLayout = {
      labelCol: { fixedSpan: 6 },
      wrapperCol: { span: 18 },
    };
    return (
      <div style={{ padding: 10 }}>
        <RegionGroup left={locale.changePassword} />
        <Form style={{ width: '300px' }} field={this.field}>
          <FormItem label={locale.oldPassword} required {...formItemLayout}>
            <Input
              htmlType="password"
              placeholder={locale.pleaseInputOldPassword}
              {...this.field.init('oldPassword', {
                rules: [
                  {
                    required: true,
                    message: locale.passwordRequired,
                  },
                ],
              })}
              disabled={this.state.type === 0}
            />
          </FormItem>
          <FormItem label={locale.newPassword} required {...formItemLayout}>
            <Input
              htmlType="password"
              placeholder={locale.pleaseInputNewPassword}
              {...this.field.init('newPassword', {
                rules: [
                  {
                    required: true,
                    message: locale.passwordRequired,
                  },
                ],
              })}
              disabled={this.state.type === 0}
            />
          </FormItem>
          <FormItem label={locale.checkPassword} required {...formItemLayout}>
            <Input
              htmlType="password"
              placeholder={locale.pleaseInputNewPasswordAgain}
              {...this.field.init('confirmNewPassword', {
                rules: [
                  {
                    required: true,
                    message: locale.passwordRequired,
                  },
                  { validator: this.validatePassword.bind(this) },
                ],
              })}
              disabled={this.state.type === 0}
            />
          </FormItem>
          <FormItem label=" " {...formItemLayout}>
            <Form.Submit type="primary" onClick={this.changePassword.bind(this)}>
              {locale.changePassword}
            </Form.Submit>
          </FormItem>
        </Form>
      </div>
    );
  }
}

export default Password;
