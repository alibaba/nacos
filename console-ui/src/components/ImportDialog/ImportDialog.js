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
import { isParentEdas } from '@/lib';
import { Balloon, Button, ConfigProvider, Dialog, Form, Icon, Select, Upload } from '@alifd/next';

import './index.scss';

const FormItem = Form.Item;

/**
 * @deprecated
 */
@ConfigProvider.config
class ImportDialog extends React.Component {
  static displayName = 'ImportDialog';

  constructor(props) {
    super(props);
    this.formItemLayout = {
      labelCol: {
        fixedSpan: 4,
      },
      wrapperCol: {
        span: 20,
      },
    };
    this.defaultPolicy = 'abort';
    this.state = {
      visible: false,
      serverId: '',
      tenant: '',
      policy: this.defaultPolicy,
      policyLabel: '',
      allPolicy: [],
    };
  }

  componentDidMount() {
    const { locale = {} } = this.props;
    this.setState({
      policyLabel: locale.terminate,
      allPolicy: [
        { value: 'abort', label: locale.terminate },
        { value: 'skip', label: locale.skip },
        { value: 'overwrite', label: locale.overwrite },
      ],
    });
  }

  openDialog(payload, callback) {
    this.callback = callback;
    this.setState({
      visible: true,
      serverId: payload.serverId,
      tenant: payload.tenant,
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

  formatter = res => {
    if (res.code === 200) {
      return {
        code: '0',
        retData: res,
      };
    } else {
      return {
        code: '1',
        error: {
          message: res.message,
        },
        retData: res,
      };
    }
  };

  render() {
    const { locale = {} } = this.props;
    let uploadLink = `/diamond-ops/batch/import/serverId/${this.state.serverId}/tenant/${this.state.tenant.id}?policy=${this.state.policy}`;

    if (isParentEdas()) {
      uploadLink = `/authgw/${window.edasprefix}${uploadLink}`;
    }
    const helpTip = (
      <Balloon
        trigger={
          <span>
            Data ID{' '}
            <Icon
              type="help"
              size="small"
              style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }}
            />
          </span>
        }
        align="t"
        style={{ marginRight: 5 }}
        triggerType="hover"
      >
        <a href={window._getLink && window._getLink('knowDataid')} target="_blank">
          {locale.zipFileFormat}
        </a>
      </Balloon>
    );
    const footer = (
      <div>
        <Upload
          listType={'text'}
          action={uploadLink}
          limit={1}
          accept={'.zip'}
          onSuccess={(...args) => {
            this.callback(args[0].retData, this.state.policyLabel);
            this.closeDialog();
          }}
          onError={(...args) => {
            this.callback(args[0].response.retData, this.state.policyLabel);
            this.closeDialog();
          }}
          formatter={this.formatter}
          headers={{ poweredBy: 'simpleMVC', projectName: 'nacos' }}
        >
          <Button type={'primary'}>{locale.uploadFile}</Button>
        </Upload>
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
          title={`${locale.importLabel + this.state.serverId}）`}
        >
          <Form>
            <FormItem label={locale.target} {...this.formItemLayout}>
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.tenant.name}</span>
                {` | ${this.state.tenant.id}`}
              </p>
            </FormItem>
            <FormItem label={locale.conflict} {...this.formItemLayout}>
              <Select
                size={'medium'}
                hasArrow
                defaultValue={this.defaultPolicy}
                dataSource={this.state.allPolicy}
                onChange={this.setPolicy}
              />
            </FormItem>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Icon
              type={'warning'}
              style={{ color: '#ff8a00', marginRight: 5, verticalAlign: 'middle' }}
            />
            {locale.beSureExerciseCaution}
            {helpTip}
          </div>
        </Dialog>
      </div>
    );
  }
}

export default ImportDialog;
