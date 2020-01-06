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
import { Field, Form, Input, Dialog, ConfigProvider } from '@alifd/next';
import './UserManagement.scss';

const FormItem = Form.Item;

const formItemLayout = {
  labelCol: { fixedSpan: 3 },
  wrapperCol: { span: 20 },
};

@ConfigProvider.config
class NewUser extends React.Component {
  static displayName = 'NewUser';

  field = new Field(this);

  static propTypes = {
    locale: PropTypes.object,
    visible: PropTypes.bool,
  };

  check() {
    const errors = {
      username: '用户名不能为空!',
      password: '密码不能为空!',
    };
    const vals = ['username', 'password'].map(key => {
      const val = this.field.getValue(key);
      if (!val) {
        this.field.setError(key, errors[key]);
      }
      return val;
    });
    if (vals.filter(v => v).length === 2) {
      return vals;
    }
    return null;
  }

  render() {
    const { getError } = this.field;
    const { visible, onOk, onCancel } = this.props;
    return (
      <>
        <Dialog
          title="创建用户"
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
            <FormItem label="用户名" required help={getError('username')}>
              <Input name="username" trim placeholder="Please Enter Username" />
            </FormItem>
            <FormItem label="密码" required help={getError('password')}>
              <Input name="password" htmlType="password" placeholder="Please Enter Password" />
            </FormItem>
          </Form>
        </Dialog>
      </>
    );
  }
}

export default NewUser;
