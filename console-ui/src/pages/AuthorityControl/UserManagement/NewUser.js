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
import { Field, Form, Input, Dialog, ConfigProvider } from '@alifd/next';
import './UserManagement.scss';

const FormItem = Form.Item;

const formItemLayout = {
  labelCol: { fixedSpan: 4 },
  wrapperCol: { span: 19 },
};

@ConfigProvider.config
class NewUser extends React.Component {
  static displayName = 'NewUser';

  field = new Field(this);

  static propTypes = {
    locale: PropTypes.object,
    visible: PropTypes.bool,
    onOk: PropTypes.func,
    onCancel: PropTypes.func,
  };

  check() {
    const { locale } = this.props;
    const errors = {
      username: locale.usernameError,
      password: locale.passwordError,
      rePassword: locale.rePasswordError,
    };
    const vals = Object.keys(errors).map(key => {
      const val = this.field.getValue(key);
      if (!val) {
        this.field.setError(key, errors[key]);
      }
      return val;
    });
    if (vals.filter(v => v).length !== 3) {
      return null;
    }
    const [password, rePassword] = ['password', 'rePassword'].map(k => this.field.getValue(k));
    if (password !== rePassword) {
      this.field.setError('rePassword', locale.rePasswordError2);
      return null;
    }
    return vals;
  }

  render() {
    const { locale } = this.props;
    const { getError } = this.field;
    const { visible, onOk, onCancel } = this.props;
    return (
      <>
        <Dialog
          title={locale.createUser}
          visible={visible}
          onOk={() => {
            const vals = this.check();
            if (vals) {
              onOk(vals).then(() => onCancel());
            }
          }}
          onClose={onCancel}
          onCancel={onCancel}
          afterClose={() => this.field.reset()}
        >
          <Form style={{ width: 400 }} {...formItemLayout} field={this.field}>
            <FormItem label={locale.username} required help={getError('username')}>
              <Input name="username" trim placeholder={locale.usernamePlaceholder} />
            </FormItem>
            <FormItem label={locale.password} required help={getError('password')}>
              <Input name="password" htmlType="password" placeholder={locale.passwordPlaceholder} />
            </FormItem>
            <FormItem label={locale.rePassword} required help={getError('rePassword')}>
              <Input
                name="rePassword"
                htmlType="password"
                placeholder={locale.rePasswordPlaceholder}
              />
            </FormItem>
          </Form>
        </Dialog>
      </>
    );
  }
}

export default NewUser;
