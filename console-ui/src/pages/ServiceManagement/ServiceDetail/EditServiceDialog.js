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
import {
  Dialog,
  Form,
  Input,
  Select,
  Message,
  ConfigProvider,
  Icon,
  Button,
  Balloon,
} from '@alifd/next';
import { DIALOG_FORM_LAYOUT, METADATA_SEPARATOR, METADATA_ENTER } from './constant';
import MonacoEditor from 'components/MonacoEditor';

@ConfigProvider.config
class EditServiceDialog extends React.Component {
  static displayName = 'EditServiceDialog';

  static propTypes = {
    queryServiceList: PropTypes.func,
    getServiceDetail: PropTypes.func,
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      isCreate: false,
      editService: {},
      editServiceDialogVisible: false,
      errors: { name: {}, protectThreshold: {} },
      selectors: [],
    };
    this.show = this.show.bind(this);
  }

  show(_editService = {}) {
    let editService = _editService;
    const { metadata = {}, name } = editService;
    if (Object.keys(metadata).length) {
      editService.metadataText = JSON.stringify(metadata, null, '\t');
    }
    let selectors = [];
    let type = editService.selector ? editService.selector.type : 'none';
    if (type === 'none') {
      selectors.push({ type: 'none' });
    } else if (type === 'label') {
      selectors.push(editService.selector);
    } else {
      selectors = editService.selector.selectors;
    }
    this.setState({ editService, editServiceDialogVisible: true, isCreate: !name, selectors });
  }

  hide() {
    this.setState({ editServiceDialogVisible: false });
  }

  validator(field) {
    const { locale = {} } = this.props;
    const errors = Object.assign({}, this.state.errors);
    const helpMap = {
      name: locale.serviceNameRequired,
      protectThreshold: locale.protectThresholdRequired,
    };
    if (field.protectThreshold === 0) {
      field.protectThreshold = '0';
    }
    for (const key in field) {
      if (!field[key]) {
        errors[key] = { validateState: 'error', help: helpMap[key] };
        this.setState({ errors });
        return false;
      }
    }
    return true;
  }

  onConfirm() {
    const { isCreate, selectors } = this.state;
    const editService = Object.assign({}, this.state.editService);
    const { name, protectThreshold, groupName, metadataText = '' } = editService;
    const json = { type: 'multi', selectors };
    if (!this.validator({ name, protectThreshold })) return;
    request({
      method: isCreate ? 'POST' : 'PUT',
      url: 'v1/ns/service',
      data: {
        serviceName: name,
        groupName: groupName || 'DEFAULT_GROUP',
        protectThreshold,
        metadata: metadataText,
        selector: JSON.stringify(json),
      },
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
    const resetKey = ['name', 'protectThreshold'];
    const { editService = {} } = this.state;
    const errors = Object.assign({}, this.state.errors);
    resetKey.forEach(key => {
      if (changeVal[key]) {
        errors[key] = {};
        this.setState({ errors });
      }
    });
    this.setState({
      editService: Object.assign({}, editService, changeVal),
    });
  }

  getFormItemLayout = () => ({
    labelCol: { span: 6 },
    wrapperCol: { span: 14 },
  });

  changeSelector(index, type, expression) {
    let { selectors } = this.state;
    selectors = selectors.map((selector, i) => {
      if (index === i) {
        selector.type = type;
        selector.expression = expression;
      }
      return selector;
    });
    this.setState(selectors);
  }

  addSelector() {
    let { selectors } = this.state;
    selectors.push({ type: 'none' });
    selectors = selectors.map(s => s);
    this.setState(selectors);
  }

  removeSelector(index) {
    let { selectors } = this.state;
    let newSelectors = [];
    selectors.forEach((s, i) => {
      if (i !== index) {
        newSelectors.push(s);
      }
    });
    this.setState({ selectors: newSelectors });
  }

  render() {
    const { locale = {} } = this.props;
    const { isCreate, editService, editServiceDialogVisible, errors, selectors } = this.state;
    const { name, protectThreshold, groupName, metadataText } = editService;
    const formItemLayout = this.getFormItemLayout();
    const selectorElement = selectors.map((selector, index) => (
      <div>
        <Form.Item label={`${locale.type + '-' + (index + 1)}:`} {...formItemLayout}>
          <Select
            className="full-width"
            defaultValue={selector.type}
            value={selector.type}
            onChange={type => this.changeSelector(index, type, '')}
          >
            <Select.Option value="label">{locale.typeLabel}</Select.Option>
            <Select.Option value="none">{locale.typeNone}</Select.Option>
          </Select>
          {index !== 0 && (
            <Icon
              type="error"
              style={{ color: '#FF3333', marginLeft: '10px', cursor: 'pointer' }}
              onClick={() => this.removeSelector(index)}
            />
          )}
        </Form.Item>

        {selector.type === 'label' && (
          <Form.Item label={`${locale.selector}:`} {...formItemLayout}>
            <Input.TextArea
              value={
                selector.expression ? selector.expression : 'CONSUMER.label.key=PROVIDER.label.key'
              }
              onChange={expression => this.changeSelector(index, selector.type, expression)}
            />
          </Form.Item>
        )}
      </div>
    ));
    return (
      <Dialog
        className="service-detail-edit-dialog"
        title={this.getDialogTitle(locale)}
        visible={editServiceDialogVisible}
        onOk={() => this.onConfirm()}
        onCancel={() => this.hide()}
        onClose={() => this.hide()}
      >
        <Form {...DIALOG_FORM_LAYOUT}>
          <Form.Item
            required={isCreate}
            {...formItemLayout}
            label={`${locale.serviceName}:`}
            {...errors.name}
          >
            {!isCreate ? (
              <p>{name}</p>
            ) : (
              <Input value={name} onChange={name => this.onChangeCluster({ name })} />
            )}
          </Form.Item>
          <Form.Item
            required
            {...formItemLayout}
            label={`${locale.protectThreshold}:`}
            {...errors.protectThreshold}
          >
            <Input
              value={protectThreshold}
              onChange={protectThreshold => this.onChangeCluster({ protectThreshold })}
            />
          </Form.Item>
          <Form.Item {...formItemLayout} label={`${locale.groupName}:`}>
            <Input
              defaultValue={groupName}
              placeholder="DEFAULT_GROUP"
              readOnly={!isCreate}
              onChange={groupName => this.onChangeCluster({ groupName })}
            />
          </Form.Item>
          <Form.Item label={`${locale.metadata}:`} {...formItemLayout}>
            <MonacoEditor
              language="json"
              width={'100%'}
              height={200}
              value={metadataText}
              onChange={metadataText => this.onChangeCluster({ metadataText })}
            />
          </Form.Item>
          {selectorElement}
          <Form.Item label={' '} {...formItemLayout}>
            <Button
              type="normal"
              style={{ width: '100%', borderStyle: 'dashed' }}
              onClick={() => this.addSelector()}
            >
              {' '}
              <Icon type="add" />
              &nbsp;&nbsp;{locale.addNewSelector}
            </Button>
          </Form.Item>
        </Form>
      </Dialog>
    );
  }

  getDialogTitle(locale) {
    const { isCreate } = this.state;
    return (
      <div>
        {isCreate ? locale.createService : locale.updateService}
        <Balloon
          triggerType="hover"
          trigger={
            <Icon type="help" size={'small'} style={{ color: '#FFA003', marginLeft: '8px' }} />
          }
        >
          {locale.selectorMessage}
        </Balloon>
      </div>
    );
  }
}

export default EditServiceDialog;
