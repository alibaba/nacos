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
import { Button, Dialog, Form, Select } from '@alifd/next';

const FormItem = Form.Item;

class ImportDialog extends React.Component {
  static displayName = 'ZADialog';

  constructor(props) {
    super(props);

    this.state = {
      visible: false,
      serverId: '',
      serverName: '',
      canSubmit: true,
      policy: '',
      policyLabel: '',
      allPolicy: [],
    };
  }

  componentDidMount() {
    this.setState({
      policy: 'terminate',
      policyLabel: '终止导入',
      allPolicy: [
        { value: 'terminate', label: '终止导入' },
        { value: 'skip', label: '跳过' },
        { value: 'overwrite', label: '覆盖' },
      ],
    });
  }

  openDialog(payload = {}, callback = () => {}) {
    this.callback = callback;
    this.setState({
      visible: true,
      canSubmit: true,
      serverId: payload.id,
      serverName: payload.name,
    });
  }

  closeDialog = () => {
    this.setState({
      visible: false,
    });
  };

  setPolicy = (...value) => {
    this.setState({
      policyLabel: value[2].label,
      policy: value[0],
    });
  };

  handleFileChanged(e) {
    const files = e.target.files;

    if (!files || files.length === 0) {
    } else {
      let targetFile = files[0];

      this.setUploadingflag(true);
      this.callback(targetFile, this.state.policy);
    }
  }

  setUploadingflag(flag) {
    this.setState({ isUploading: flag });
  }

  render() {
    const footer = (
      <div>
        <input
          type="file"
          accept=".zip"
          style={{ display: 'none' }}
          ref={ref => (this.uploader = ref)}
          onChange={this.handleFileChanged.bind(this)}
        />
        <Button
          type={'primary'}
          onClick={() => this.uploader.click()}
          disabled={this.state.isUploading}
        >
          选择文件
        </Button>
      </div>
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
          title={`导入配置${this.state.serverName}`}
        >
          <Form>
            <FormItem label="目标空间:">
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.serverName}</span>
                {` | ${this.state.serverId}`}
              </p>
            </FormItem>
            <FormItem label="相同配置:">
              <Select
                size={'medium'}
                hasArrow
                defaultValue={this.state.policyLabel}
                dataSource={this.state.allPolicy}
                onChange={this.setPolicy}
              />
            </FormItem>
            文件上传后将直接导入配置，请务必谨慎操作！
          </Form>
        </Dialog>
      </div>
    );
  }
}

export default ImportDialog;
