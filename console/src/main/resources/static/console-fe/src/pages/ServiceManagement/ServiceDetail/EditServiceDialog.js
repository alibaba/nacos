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
import { Dialog, Form, Input, Select, Message, ConfigProvider } from '@alifd/next';
import { DIALOG_FORM_LAYOUT } from './constant';

@ConfigProvider.config
class EditServiceDialog extends React.Component {
  static displayName = 'EditServiceDialog';

  constructor(props) {
    super(props);
    this.state = {
      isCreate: false,
      editService: {},
      editServiceDialogVisible: false,
    };
    this.show = this.show.bind(this);
  }

  show(_editService = {}) {
    let editService = _editService;
    const { metadata = {}, name } = editService;
    if (Object.keys(metadata).length) {
      editService.metadataText = Object.keys(metadata)
        .map(k => `${k}=${metadata[k]}`)
        .join(',');
    }
    this.setState({ editService, editServiceDialogVisible: true, isCreate: !name });
  }

  hide() {
    this.setState({ editServiceDialogVisible: false });
  }

  onConfirm() {
    const { isCreate } = this.state;
    const editService = Object.assign({}, this.state.editService);
    const { name, protectThreshold, healthCheckMode, metadataText } = editService;
    request({
      method: isCreate ? 'PUT' : 'POST',
      url: `v1/ns/service/${isCreate ? 'create' : 'update'}`,
      data: { serviceName: name, protectThreshold, healthCheckMode, metadata: metadataText },
      dataType: 'text',
      beforeSend: () => this.setState({ loading: true }),
      success: res => {
        if (res !== 'ok') {
          Message.error(res);
          return;
        }
        if (isCreate) {
          this.props.queryServiceList();
        } else {
          this.props.getServiceDetail();
        }
      },
      error: res => Message.error(res.responseText || res.statusText),
      complete: () => this.setState({ loading: false }),
    });
    this.hide();
  }

  onChangeCluster(changeVal) {
    const { editService = {} } = this.state;
    this.setState({
      editService: Object.assign({}, editService, changeVal),
    });
  }

  render() {
    const { locale = {} } = this.props;
    const { isCreate, editService, editServiceDialogVisible } = this.state;
    const { name, protectThreshold, healthCheckMode, metadataText } = editService;
    return (
      <Dialog
        className="service-detail-edit-dialog"
        title={isCreate ? locale.createService : locale.updateService}
        visible={editServiceDialogVisible}
        onOk={() => this.onConfirm()}
        onCancel={() => this.hide()}
        onClose={() => this.hide()}
      >
        <Form {...DIALOG_FORM_LAYOUT}>
          <Form.Item label={`${locale.serviceName}:`}>
            {!isCreate ? (
              <p>{name}</p>
            ) : (
              <Input
                className="in-text"
                value={name}
                onChange={name => this.onChangeCluster({ name })}
              />
            )}
          </Form.Item>
          <Form.Item label={`${locale.protectThreshold}:`}>
            <Input
              className="in-text"
              value={protectThreshold}
              onChange={protectThreshold => this.onChangeCluster({ protectThreshold })}
            />
          </Form.Item>
          <Form.Item label={`${locale.healthCheckPattern}:`}>
            <Select
              className="in-select"
              defaultValue={healthCheckMode}
              onChange={healthCheckMode => this.onChangeCluster({ healthCheckMode })}
            >
              <Select.Option value="server">{locale.healthCheckPatternService}</Select.Option>
              <Select.Option value="client">{locale.healthCheckPatternClient}</Select.Option>
              <Select.Option value="none">{locale.healthCheckPatternNone}</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label={`${locale.metadata}:`}>
            <Input
              className="in-text"
              value={metadataText}
              onChange={metadataText => this.onChangeCluster({ metadataText })}
            />
          </Form.Item>
        </Form>
      </Dialog>
    );
  }
}

export default EditServiceDialog;
