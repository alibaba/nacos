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
import { Button, Dialog, Form } from '@alifd/next';

const FormItem = Form.Item;

class ImportDialog extends React.Component {
  static displayName = 'ZADialog';

  constructor(props) {
    super(props);

    this.state = {
      visible: false,
      serverId: '',
      serverName: '',
      totalSelected: 0,
    };
  }

  componentDidMount() {}

  openDialog(payload = {}, callback = () => {}) {
    this.callback = callback;
    this.setState({
      visible: true,
      serverId: payload.id,
      serverName: payload.name,
      totalSelected: payload.total,
    });
  }

  closeDialog = () => {
    this.setState({
      visible: false,
    });
  };

  setPolicy = (...value) => {
    this.setState({
      policyLabel: value[1].label,
      policy: value[0],
    });
  };

  render() {
    const { totalSelected } = this.state;
    const footer =
      totalSelected !== 0 ? (
        <Button type={'primary'} onClick={() => this.callback()}>
          导出
        </Button>
      ) : (
        <Button type={'primary'} onClick={() => this.closeDialog()}>
          确定
        </Button>
      );

    return (
      <div>
        <Dialog
          visible={this.state.visible}
          footer={footer}
          footerAlign="center"
          style={{ width: 480 }}
          onCancel={this.closeDialog}
          onClose={this.closeDialog}
          title={`导出配置${this.state.serverName}`}
        >
          {totalSelected === 0 ? (
            '请先选择导出项'
          ) : (
            <Form>
              <FormItem label="源空间:">
                <p>
                  <span style={{ color: '#33cde5' }}>{this.state.serverName}</span>
                  {` | ${this.state.serverId}`}
                </p>
              </FormItem>
              <FormItem label="配置数量:">{totalSelected}个条目已选中</FormItem>
            </Form>
          )}
        </Dialog>
      </div>
    );
  }
}

export default ImportDialog;
