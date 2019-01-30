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
import { Button, ConfigProvider, Dialog, Grid, Icon } from '@alifd/next';

import './index.scss';

const { Row, Col } = Grid;

@ConfigProvider.config
class DeleteDialog extends React.Component {
  static displayName = 'DeleteDialog';

  static propTypes = {
    locale: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      visible: false,
      title: '',
      content: '',
      isok: true,
      dataId: '',
      group: '',
    };
  }

  componentDidMount() {
    this.initData();
  }

  initData() {
    const { locale = {} } = this.props;
    this.setState({ title: locale.confManagement });
  }

  openDialog(payload) {
    this.setState({
      visible: true,
      title: payload.title,
      content: payload.content,
      isok: payload.isok,
      dataId: payload.dataId,
      group: payload.group,
      message: payload.message,
    });
  }

  closeDialog() {
    this.setState({
      visible: false,
    });
  }

  render() {
    const { locale = {} } = this.props;
    const footer = (
      <div style={{ textAlign: 'right' }}>
        <Button type="primary" onClick={this.closeDialog.bind(this)}>
          {locale.determine}
        </Button>
      </div>
    );
    return (
      <div>
        <Dialog
          visible={this.state.visible}
          footer={footer}
          style={{ width: 555 }}
          onCancel={this.closeDialog.bind(this)}
          onClose={this.closeDialog.bind(this)}
          title={locale.deletetitle}
        >
          <div>
            <Row>
              <Col span={'4'} style={{ paddingTop: 16 }}>
                <Icon
                  type={`${this.state.isok ? 'success' : 'delete'}-filling`}
                  style={{ color: this.state.isok ? 'green' : 'red' }}
                  size={'xl'}
                />
              </Col>
              <Col span={'20'}>
                <div>
                  <h3>{this.state.isok ? locale.deletedSuccessfully : locale.deleteFailed}</h3>
                  <p>
                    <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
                    <span style={{ color: '#c7254e' }}>{this.state.dataId}</span>
                  </p>
                  <p>
                    <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
                    <span style={{ color: '#c7254e' }}>{this.state.group}</span>
                  </p>
                  {this.state.isok ? '' : <p style={{ color: 'red' }}>{this.state.message}</p>}
                </div>
              </Col>
            </Row>
          </div>
        </Dialog>
      </div>
    );
  }
}

export default DeleteDialog;
