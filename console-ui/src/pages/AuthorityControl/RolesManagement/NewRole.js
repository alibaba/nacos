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
import { connect } from 'react-redux';
import { Field, Form, Input, Dialog, ConfigProvider, Select } from '@alifd/next';
import { searchUsers } from '../../../reducers/authority';

const FormItem = Form.Item;

const formItemLayout = {
  labelCol: { fixedSpan: 4 },
  wrapperCol: { span: 19 },
};
@connect(state => ({ users: state.authority.users }), { searchUsers })
@ConfigProvider.config
class NewRole extends React.Component {
  static displayName = 'NewRole';

  field = new Field(this);

  static propTypes = {
    locale: PropTypes.object,
    visible: PropTypes.bool,
    onOk: PropTypes.func,
    getUsers: PropTypes.func,
    onCancel: PropTypes.func,
  };

  state = {
    dataSource: [],
  };

  check() {
    const { locale } = this.props;
    const errors = {
      role: locale.roleError,
      username: locale.usernameError,
    };
    const vals = Object.keys(errors).map(key => {
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

  handleChange = value => {
    if (value.length > 0) {
      searchUsers(value).then(val => {
        this.setState({ dataSource: val });
      });
    }
  };

  render() {
    const { locale } = this.props;
    const { getError } = this.field;
    const { visible, onOk, onCancel } = this.props;
    return (
      <>
        <Dialog
          title={locale.bindingRoles}
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
            <FormItem label={locale.role} required help={getError('role')}>
              <Input name="role" trim placeholder={locale.rolePlaceholder} />
            </FormItem>
            <FormItem label={locale.username} required help={getError('username')}>
              <Select.AutoComplete
                name="username"
                style={{ width: 316 }}
                filterLocal={false}
                placeholder={locale.usernamePlaceholder}
                onChange={this.handleChange}
                dataSource={this.state.dataSource}
              />
            </FormItem>
          </Form>
        </Dialog>
      </>
    );
  }
}

export default NewRole;
