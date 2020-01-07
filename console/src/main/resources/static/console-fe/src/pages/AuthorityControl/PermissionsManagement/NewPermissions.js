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

const FormItem = Form.Item;

const formItemLayout = {
  labelCol: { fixedSpan: 3 },
  wrapperCol: { span: 20 },
};

@ConfigProvider.config
class NewPermissions extends React.Component {
  static displayName = 'NewPermissions';

  field = new Field(this);

  static propTypes = {
    locale: PropTypes.object,
    visible: PropTypes.bool,
  };

  check() {
    const errors = {
      role: '角色不能为空!',
      resource: '资源不能为空!',
      action: '动作不能为空!',
    };
    const vals = Object.keys(errors).map(key => {
      const val = this.field.getValue(key);
      if (!val) {
        this.field.setError(key, errors[key]);
      }
      return val;
    });
    if (vals.filter(v => v).length === 3) {
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
          title="添加授权"
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
            <FormItem label="角色" required help={getError('role')}>
              <Input name="role" trim placeholder="Please Enter Role" />
            </FormItem>
            <FormItem label="资源" required help={getError('resource')}>
              <Input name="resource" trim placeholder="Please Enter Resource" />
            </FormItem>
            <FormItem label="动作" required help={getError('username')}>
              <Input name="action" trim placeholder="Please Enter Action" />
            </FormItem>
          </Form>
        </Dialog>
      </>
    );
  }
}

export default NewPermissions;
