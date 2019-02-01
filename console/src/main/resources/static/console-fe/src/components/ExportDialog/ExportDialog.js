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
import { Button, ConfigProvider, Dialog, Form } from '@alifd/next';

import './index.scss';

const FormItem = Form.Item;

/**
 * @deprecated
 */
@ConfigProvider.config
class ExportDialog extends React.Component {
  static displayName = 'ExportDialog';

  constructor(props) {
    super(props);
    this.state = {
      visible: false,
      serverId: '',
      tenant: '',
      dataId: '',
      group: '',
      appName: '',
      configTags: '',
      records: [],
      total: 0,
    };
    this.formItemLayout = {
      labelCol: { fixedSpan: 4 },
      wrapperCol: { span: 20 },
    };
  }

  componentDidMount() {}

  openDialog(payload) {
    this.setState({
      visible: true,
      serverId: payload.serverId,
      tenant: payload.tenant,
      dataId: payload.dataId,
      group: payload.group,
      appName: payload.appName,
      configTags: payload.configTags,
      records: payload.records,
      total: payload.total,
    });
  }

  closeDialog = () => {
    this.setState({
      visible: false,
    });
  };

  getQuery() {
    const { locale = {} } = this.props;
    if (this.state.records.length > 0) return locale.selectedEntry;
    if (
      this.state.dataId === '' &&
      this.state.group === '' &&
      this.state.appName === '' &&
      this.state.configTags.length === 0
    ) {
      return '';
    }
    let query = ' |';
    if (this.state.dataId !== '') {
      query += ` DataId: ${this.state.dataId},`;
    }
    if (this.state.group !== '') {
      query += ` Group: ${this.state.group},`;
    }
    if (this.state.appName !== '') {
      query += `${locale.application + this.state.appName},`;
    }
    if (this.state.configTags.length !== 0) {
      query += `${locale.tags + this.state.configTags},`;
    }
    return query.substr(0, query.length - 1);
  }

  doExport = () => {
    // document.getElementById('downloadLink').click();
    const url = this.getLink();
    window.open(url);
    this.closeDialog();
  };

  getLink() {
    const data = [];
    this.state.records.forEach(record => {
      data.push({ dataId: record.dataId, group: record.group });
    });
    const query = `?dataId=${this.state.dataId}&group=${this.state.group}&appName=${
      this.state.appName
    }&tags=${this.state.configTags || ''}&data=${encodeURI(JSON.stringify(data))}`;
    const baseLink = `/diamond-ops/batch/export/serverId/${this.state.serverId}/tenant/${
      this.state.tenant.id
    }${query}`;
    if (window.globalConfig.isParentEdas()) {
      return `/authgw/${window.edasprefix}${baseLink}`;
    }
    return baseLink;
  }

  render() {
    const { locale = {} } = this.props;
    const footer = (
      <div>
        {/* <a id="downloadLink" style={{ display: "none" }} href={this.getLink()} /> */}
        <Button type="primary" onClick={this.doExport} {...{ disabled: this.state.total <= 0 }}>
          {locale.exportBtn}
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
          title={`${locale.exportConfiguration + this.state.serverId}）`}
        >
          <Form>
            <FormItem label={locale.source} {...this.formItemLayout}>
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.tenant.name}</span>
                {` | ${this.state.tenant.id}`}
              </p>
            </FormItem>
            <FormItem label={locale.items} {...this.formItemLayout}>
              <p>
                <span style={{ color: '#33cde5' }}>{this.state.total}</span> {this.getQuery()}{' '}
              </p>
            </FormItem>
          </Form>
        </Dialog>
      </div>
    );
  }
}

export default ExportDialog;
