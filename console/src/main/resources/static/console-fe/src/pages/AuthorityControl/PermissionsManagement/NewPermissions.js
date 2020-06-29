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
import { Field, Form, Input, Select, Dialog, ConfigProvider } from '@alifd/next';
import { connect } from 'react-redux';
import { getNamespaces } from '../../../reducers/namespace';

const FormItem = Form.Item;
const { Option } = Select;

const formItemLayout = {
  labelCol: { fixedSpan: 4 },
  wrapperCol: { span: 19 },
};

@connect(state => ({ namespaces: state.namespace.namespaces }), { getNamespaces })
@ConfigProvider.config
class NewPermissions extends React.Component {
  static displayName = 'NewPermissions';

  field = new Field(this);

  static propTypes = {
    locale: PropTypes.object,
    visible: PropTypes.bool,
    getNamespaces: PropTypes.func,
    onOk: PropTypes.func,
    onCancel: PropTypes.func,
    namespaces: PropTypes.array,
  };

  componentDidMount() {
    this.props.getNamespaces();
  }

  check() {
    const { locale } = this.props;
    const errors = {
      role: locale.roleError,
      resource: locale.resourceError,
      action: locale.actionError,
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
    const { visible, onOk, onCancel, locale, namespaces } = this.props;
    return (
      <>
        <Dialog
          title={locale.addPermission}
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
            <FormItem label={locale.resource} required help={getError('resource')}>
              <Select
                name="resource"
                placeholder={locale.resourcePlaceholder}
                style={{ width: '100%' }}
              >
                {namespaces.map(({ namespace, namespaceShowName }) => (
                  <Option value={`${namespace}:*:*`}>
                    {namespaceShowName} {namespace ? `(${namespace})` : ''}
                  </Option>
                ))}
              </Select>
            </FormItem>
            <FormItem label={locale.action} required help={getError('action')}>
              <Select
                name="action"
                placeholder={locale.actionPlaceholder}
                style={{ width: '100%' }}
              >
                <Option value="r">{locale.readOnly}(r)</Option>
                <Option value="w">{locale.writeOnly}(w)</Option>
                <Option value="rw">{locale.readWrite}(rw)</Option>
              </Select>
            </FormItem>
          </Form>
        </Dialog>
      </>
    );
  }
}

export default NewPermissions;
