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
import { Field, Form, Input, Dialog, ConfigProvider, Select } from '@alifd/next';
import { connect } from 'react-redux';
import { getNamespaces } from '../../../reducers/namespace';
import { request } from '../../../globalLib';
const FormItem = Form.Item;
const { Option } = Select;

const formItemLayout = {
  labelCol: { fixedSpan: 4 },
  wrapperCol: { span: 19 },
};
@connect(state => ({ namespaces: state.namespace.namespaces }), { getNamespaces })
@ConfigProvider.config
class ConfigCompared extends React.Component {
  static displayName = 'ConfigCompare';

  field = new Field(this);

  static propTypes = {
    locale: PropTypes.object,
    dataId: PropTypes.string,
    group: PropTypes.string,
    visible: PropTypes.bool,
    onOk: PropTypes.func,
    onCancel: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      namespacesDataSource: [],
    };
  }

  componentDidMount() {
    this.getNamespaces();
  }

  getNamespaces() {
    request({
      type: 'get',
      url: 'v1/console/namespaces',
      success: res => {
        if (res.code === 200) {
          const { namespacesDataSource } = this.state;
          this.setState({ namespacesDataSource: res.data });
        } else {
          Dialog.alert({
            title: prompt,
            content: res.message,
          });
        }
      },
      error: res => {
        window.namespaceList = [
          {
            namespace: '',
            namespaceShowName: '公共空间',
            type: 0,
          },
        ];
      },
    });
  }

  render() {
    const { locale = {} } = this.props;
    const { getError } = this.field;
    const { visible, onOk, onCancel, dataId, group } = this.props;
    const { namespacesDataSource } = this.state;
    return (
      <>
        <Dialog
          title={locale.configComparisonTitle}
          visible={visible}
          onOk={() => {
            const fields = {
              dataId: 'dataId',
              group: 'group',
              namespace: 'namespace',
            };
            const vals = Object.keys(fields).map(key => {
              return this.field.getValue(key);
            });
            onOk(vals);
          }}
          onClose={onCancel}
          onCancel={onCancel}
          afterClose={() => this.field.reset()}
        >
          <Form style={{ width: 430 }} {...formItemLayout} field={this.field}>
            <FormItem label={'namespace'} help={getError('namespace')}>
              <Select
                name="namespace"
                placeholder={locale.namespaceSelect}
                style={{ width: '100%' }}
              >
                {namespacesDataSource.map(({ namespace, namespaceShowName }) => (
                  <Option value={namespace}>
                    {namespaceShowName} {namespace ? `(${namespace})` : ''}
                  </Option>
                ))}
              </Select>
            </FormItem>
            <FormItem label={'Data Id'} required help={getError('Data Id')}>
              <Input name="dataId" trim placeholder={locale.dataIdInput} defaultValue={dataId} />
            </FormItem>
            <FormItem label={'Group'} required help={getError('Group')}>
              <Input name="group" trim placeholder={locale.configComparison} defaultValue={group} />
            </FormItem>
          </Form>
        </Dialog>
      </>
    );
  }
}

export default ConfigCompared;
