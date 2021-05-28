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
import { request } from '../../../globalLib';
import { Dialog, Form, Input, Switch, Message, ConfigProvider } from '@alifd/next';
import { DIALOG_FORM_LAYOUT, METADATA_ENTER, METADATA_SEPARATOR } from './constant';
import MonacoEditor from 'components/MonacoEditor';

@ConfigProvider.config
class EditInstanceDialog extends React.Component {
  static displayName = 'EditInstanceDialog';

  static propTypes = {
    serviceName: PropTypes.string,
    clusterName: PropTypes.string,
    groupName: PropTypes.string,
    openLoading: PropTypes.string,
    closeLoading: PropTypes.string,
    getInstanceList: PropTypes.func,
    locale: PropTypes.object,
  };

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
      editInstance.metadataText = JSON.stringify(metadata, null, '\t');
    }
    this.setState({ editInstance, editInstanceDialogVisible: true });
  }

  hide() {
    this.setState({ editInstanceDialogVisible: false });
  }

  onConfirm() {
    const {
      serviceName,
      clusterName,
      groupName,
      getInstanceList,
      openLoading,
      closeLoading,
    } = this.props;
    const { ip, port, ephemeral, weight, enabled, metadataText } = this.state.editInstance;
    request({
      method: 'PUT',
      url: 'v1/ns/instance',
      data: {
        serviceName,
        clusterName,
        groupName,
        ip,
        port,
        ephemeral,
        weight,
        enabled,
        metadata: metadataText,
      },
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
      error: e => Message.error(e.responseText || 'error'),
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
        style={{ width: 600 }}
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
            <MonacoEditor
              language="json"
              width={'100%'}
              height={200}
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
