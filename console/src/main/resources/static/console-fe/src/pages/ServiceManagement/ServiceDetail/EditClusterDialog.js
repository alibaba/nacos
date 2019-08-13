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
import { request } from '../../../globalLib';
import { Dialog, Form, Input, Switch, Select, Message, ConfigProvider } from '@alifd/next';
import { DIALOG_FORM_LAYOUT, METADATA_SEPARATOR, METADATA_ENTER } from './constant';
import MonacoEditor from 'components/MonacoEditor';

@ConfigProvider.config
class EditClusterDialog extends React.Component {
  static displayName = 'EditClusterDialog';

  static propTypes = {
    openLoading: PropTypes.func,
    closeLoading: PropTypes.func,
    getServiceDetail: PropTypes.func,
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      editCluster: {},
      editClusterDialogVisible: false,
    };
    this.show = this.show.bind(this);
  }

  show(_editCluster) {
    let editCluster = _editCluster;
    const { metadata = {} } = editCluster;
    if (Object.keys(metadata).length) {
      editCluster.metadataText = JSON.stringify(metadata, null, '\t');
    }
    this.setState({
      editCluster,
      editClusterDialogVisible: true,
    });
  }

  hide() {
    this.setState({ editClusterDialogVisible: false });
  }

  onConfirm() {
    const { openLoading, closeLoading, getServiceDetail } = this.props;
    const {
      name,
      serviceName,
      metadataText,
      defaultCheckPort,
      useIPPort4Check,
      healthChecker,
    } = this.state.editCluster;
    request({
      method: 'PUT',
      url: 'v1/ns/cluster',
      data: {
        serviceName,
        clusterName: name,
        metadata: metadataText,
        checkPort: defaultCheckPort,
        useInstancePort4Check: useIPPort4Check,
        healthChecker: JSON.stringify(healthChecker),
      },
      dataType: 'text',
      beforeSend: () => openLoading(),
      success: res => {
        if (res !== 'ok') {
          Message.error(res);
          return;
        }
        this.hide();
        getServiceDetail();
      },
      complete: () => closeLoading(),
    });
  }

  onChangeCluster(changeVal) {
    const { editCluster = {} } = this.state;
    this.setState({
      editCluster: Object.assign({}, editCluster, changeVal),
    });
  }

  render() {
    const { locale = {} } = this.props;
    const { updateCluster, checkType, checkPort, useIpPortCheck, checkPath, checkHeaders } = locale;
    const { editCluster = {}, editClusterDialogVisible } = this.state;
    const {
      healthChecker = {},
      useIPPort4Check,
      defaultCheckPort = '80',
      metadataText = '',
    } = editCluster;
    const { type, path, headers } = healthChecker;
    const healthCheckerChange = changeVal =>
      this.onChangeCluster({
        healthChecker: Object.assign({}, healthChecker, changeVal),
      });
    return (
      <Dialog
        className="cluster-edit-dialog"
        style={{ width: 600 }}
        title={updateCluster}
        visible={editClusterDialogVisible}
        onOk={() => this.onConfirm()}
        onCancel={() => this.hide()}
        onClose={() => this.hide()}
      >
        <Form {...DIALOG_FORM_LAYOUT}>
          <Form.Item label={`${checkType}:`}>
            <Select
              className="in-select"
              defaultValue={type}
              onChange={type => healthCheckerChange({ type })}
            >
              <Select.Option value="TCP">TCP</Select.Option>
              <Select.Option value="HTTP">HTTP</Select.Option>
              <Select.Option value="NONE">NONE</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label={`${checkPort}:`}>
            <Input
              className="in-text"
              value={defaultCheckPort}
              onChange={defaultCheckPort => this.onChangeCluster({ defaultCheckPort })}
            />
          </Form.Item>
          <Form.Item label={`${useIpPortCheck}:`}>
            <Switch
              checked={useIPPort4Check}
              onChange={useIPPort4Check => this.onChangeCluster({ useIPPort4Check })}
            />
          </Form.Item>
          {type === 'HTTP' && [
            <Form.Item label={`${checkPath}:`}>
              <Input
                className="in-text"
                value={path}
                onChange={path => healthCheckerChange({ path })}
              />
            </Form.Item>,
            <Form.Item label={`${checkHeaders}:`}>
              <Input
                className="in-text"
                value={headers}
                onChange={headers => healthCheckerChange({ headers })}
              />
            </Form.Item>,
          ]}
          <Form.Item label={`${locale.metadata}:`}>
            <MonacoEditor
              language="json"
              width={'100%'}
              height={200}
              value={metadataText}
              onChange={metadataText => this.onChangeCluster({ metadataText })}
            />
          </Form.Item>
        </Form>
      </Dialog>
    );
  }
}

export default EditClusterDialog;
