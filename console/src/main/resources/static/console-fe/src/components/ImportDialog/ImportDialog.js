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
import { aliwareIntl } from '@/globalLib';
import { isParentEdas } from '@/lib';
import './index.scss';
import { Balloon, Button, Dialog, Form, Icon, Select, Upload } from '@alifd/next';

const FormItem = Form.Item;

class ImportDialog extends React.Component {
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
    this.allPolicy = [
      {
        value: 'abort',
        label: aliwareIntl.get('nacos.component.ImportDialog.To_terminate_the_import0'),
      },
      { value: 'skip', label: aliwareIntl.get('nacos.component.ImportDialog.skip1') },
      {
        value: 'overwrite',
        label: aliwareIntl.get('nacos.component.ImportDialog.cover2'),
      },
    ];
    this.defaultPolicy = 'abort';
    this.state = {
      visible: false,
      serverId: '',
      tenant: '',
      policy: this.defaultPolicy,
      policyLabel: aliwareIntl.get('nacos.component.ImportDialog.To_terminate_the_import0'),
    };
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
    let uploadLink = `/diamond-ops/batch/import/serverId/${this.state.serverId}/tenant/${
      this.state.tenant.id
    }?policy=${this.state.policy}`;

    if (isParentEdas()) {
      uploadLink = `/authgw/${window.edasprefix}${uploadLink}`;
    }
    const helpTip = (
      <Balloon
        trigger={
          <span>
            Data ID{' '}
            <Icon
              type={'help'}
              size={'small'}
              style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }}
            />
          </span>
        }
        align={'t'}
        style={{ marginRight: 5 }}
        triggerType={'hover'}
      >
        <a href={window._getLink && window._getLink('knowDataid')} target={'_blank'}>
          {aliwareIntl.get(
            'nacos.component.ImportDialog.You_can_only_upload._zip_file_format0'
          ) /* 只能上传.zip格式的文件 */}
        </a>
      </Balloon>
    );
    const footer = (
      <div>
        <Upload
          language={aliwareIntl.currentLanguageCode || 'zh-cn'}
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
          <Button type={'primary'}>
            {aliwareIntl.get('nacos.component.ImportDialog.Upload_File3')}
          </Button>
        </Upload>
      </div>
    );

    return (
      <div>
        <Dialog
          visible={this.state.visible}
          footer={footer}
          footerAlign={'center'}
          language={aliwareIntl.currentLanguageCode || 'zh-cn'}
          style={{ width: 480 }}
          onCancel={this.closeDialog}
          onClose={this.closeDialog}
          title={`${aliwareIntl.get('nacos.component.ImportDialog.Import_configuration4') +
            this.state.serverId}）`}
        >
          <Form>
            <FormItem
              label={aliwareIntl.get('nacos.component.ImportDialog.target_space5')}
              {...this.formItemLayout}
            >
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.tenant.name}</span>
                {` | ${this.state.tenant.id}`}
              </p>
            </FormItem>
            <FormItem
              label={aliwareIntl.get('nacos.component.ImportDialog.the_same_configuration6')}
              {...this.formItemLayout}
            >
              <Select
                size={'medium'}
                hasArrow
                defaultValue={this.defaultPolicy}
                dataSource={this.allPolicy}
                onChange={this.setPolicy}
                language={aliwareIntl.currentLanguageCode}
              />
            </FormItem>
          </Form>

          <div style={{ textAlign: 'center' }}>
            <Icon
              type={'warning'}
              style={{ color: '#ff8a00', marginRight: 5, verticalAlign: 'middle' }}
            />
            {aliwareIntl.get(
              'nacos.component.ImportDialog.file_upload_directly_after_importing_the_configuration,_please_be_sure_to_exercise_caution7'
            )}
            {helpTip}
          </div>
        </Dialog>
      </div>
    );
  }
}

export default ImportDialog;
