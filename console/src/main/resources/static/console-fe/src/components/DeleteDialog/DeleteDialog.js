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
import './index.scss';
import { aliwareIntl } from '../../globalLib';
import { Button, Dialog, Grid, Icon } from '@alifd/next';

const { Row, Col } = Grid;

class DeleteDialog extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      visible: false,
      title: aliwareIntl.get('nacos.component.DeleteDialog.Configuration_management'),
      content: '',
      isok: true,
      dataId: '',
      group: '',
    };
  }

  componentDidMount() {}

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
    const footer = (
      <div style={{ textAlign: 'right' }}>
        <Button type="primary" onClick={this.closeDialog.bind(this)}>
          {aliwareIntl.get('nacos.component.DeleteDialog.determine')}
        </Button>
      </div>
    );
    return (
      <div>
        <Dialog
          visible={this.state.visible}
          footer={footer}
          language={aliwareIntl.currentLanguageCode || 'zh-cn'}
          style={{ width: 555 }}
          onCancel={this.closeDialog.bind(this)}
          onClose={this.closeDialog.bind(this)}
          title={aliwareIntl.get('nacos.component.DeleteDialog.deletetitle')}
        >
          <div>
            <Row>
              <Col span={'4'} style={{ paddingTop: 16 }}>
                {this.state.isok ? (
                  <Icon type="success-filling" style={{ color: 'green' }} size={'xl'} />
                ) : (
                  <Icon type="delete-filling" style={{ color: 'red' }} size={'xl'} />
                )}
              </Col>
              <Col span={'20'}>
                <div>
                  <h3>
                    {this.state.isok
                      ? aliwareIntl.get(
                          'nacos.component.DeleteDialog.deleted_successfully_configured'
                        )
                      : aliwareIntl.get(
                          'nacos.component.DeleteDialog.delete_the_configuration_failed'
                        )}
                  </h3>
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
