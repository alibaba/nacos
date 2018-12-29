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
import { request } from '../../../globalLib';
import { Dialog, Form, Input, Switch, Message, ConfigProvider } from '@alifd/next';
import { DIALOG_FORM_LAYOUT } from './constant';

@ConfigProvider.config
class EditInstanceDialog extends React.Component {
  static displayName = 'EditInstanceDialog';

  constructor(props) {
    super(props);
    this.state = {
      editInstance: {},
      editInstanceDialogVisible: false,
    };
    this.show = this.show.bind(this);
  }

  show(_editInstance) {
    let editInstance = _editInstance;
    const { metadata = {} } = editInstance;
    if (Object.keys(metadata).length) {
      editInstance.metadataText = Object.keys(metadata)
        .map(k => `${k}=${metadata[k]}`)
        .join(',');
    }
    this.setState({ editInstance, editInstanceDialogVisible: true });
  }

  hide() {
    this.setState({ editInstanceDialogVisible: false });
  }

  onConfirm() {
    const { serviceName, clusterName, getInstanceList, openLoading, closeLoading } = this.props;
    const { ip, port, weight, enabled, metadataText } = this.state.editInstance;
    request({
      method: 'POST',
      url: 'v1/ns/instance/update',
      data: { serviceName, clusterName, ip, port, weight, enable: enabled, metadata: metadataText },
      dataType: 'text',
      beforeSend: () => openLoading(),
      success: res => {
        if (res !== 'ok') {
          Message.error(res);
          return;
        }
        this.hide();
        getInstanceList();
      },
      complete: () => closeLoading(),
    });
  }

  onChangeCluster(changeVal) {
    const { editInstance = {} } = this.state;
    this.setState({
      editInstance: Object.assign({}, editInstance, changeVal),
    });
  }

  render() {
    const { locale = {} } = this.props;
    const { editInstanceDialogVisible, editInstance } = this.state;
    return (
      <Dialog
        className="instance-edit-dialog"
        title={locale.updateInstance}
        visible={editInstanceDialogVisible}
        onOk={() => this.onConfirm()}
        onCancel={() => this.hide()}
        onClose={() => this.hide()}
      >
        <Form {...DIALOG_FORM_LAYOUT}>
          <Form.Item label="IP:">
            <p>{editInstance.ip}</p>
          </Form.Item>
          <Form.Item label={`${locale.port}:`}>
            <p>{editInstance.port}</p>
          </Form.Item>
          <Form.Item label={`${locale.weight}:`}>
            <Input
              className="in-text"
              value={editInstance.weight}
              onChange={weight => this.onChangeCluster({ weight })}
            />
          </Form.Item>
          <Form.Item label={`${locale.whetherOnline}:`}>
            <Switch
              checked={editInstance.enabled}
              onChange={enabled => this.onChangeCluster({ enabled })}
            />
          </Form.Item>
          <Form.Item label={`${locale.metadata}:`}>
            <Input
              className="in-text"
              value={editInstance.metadataText}
              onChange={metadataText => this.onChangeCluster({ metadataText })}
            />
          </Form.Item>
        </Form>
      </Dialog>
    );
  }
}

export default EditInstanceDialog;
