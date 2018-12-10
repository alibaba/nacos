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
import { aliwareIntl } from '../../globalLib';
import './index.scss';
import { Button, Dialog, Grid } from '@alifd/next';

const { Row, Col } = Grid;

class DiffEditorDialog extends React.Component {
  static propTypes = {
    publishConfig: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      dialogvisible: false,
    };
  }

  componentDidMount() {}

  openDialog(letfcode, rightcode) {
    this.setState({
      dialogvisible: true,
    });
    setTimeout(() => {
      this.createDiffCodeMirror(letfcode, rightcode);
    });
  }

  closeDialog() {
    this.setState({
      dialogvisible: false,
    });
  }

  createDiffCodeMirror(leftCode, rightCode) {
    const target = this.refs.diffeditor;
    target.innerHTML = '';

    this.diffeditor = window.CodeMirror.MergeView(target, {
      value: leftCode || '',
      readOnly: true,
      origLeft: null,
      orig: rightCode || '',
      lineNumbers: true,
      mode: this.mode,
      theme: 'xq-light',
      highlightDifferences: true,
      connect: 'align',
      collapseIdentical: false,
    });
  }

  confirmPub() {
    this.closeDialog();
    this.props.publishConfig();
  }

  render() {
    const footer = (
      <div>
        {' '}
        <Button type="primary" onClick={this.confirmPub.bind(this)}>
          {aliwareIntl.get('com.alibaba.nacos.component.DiffEditorDialog.confirm_that_the')}
        </Button>
      </div>
    );
    return (
      <div>
        <Dialog
          title={aliwareIntl.get('com.alibaba.nacos.component.DiffEditorDialog.contents')}
          language={aliwareIntl.currentLanguageCode || 'zh-cn'}
          style={{ width: '80%' }}
          visible={this.state.dialogvisible}
          footer={footer}
          onClose={this.closeDialog.bind(this)}
        >
          <div style={{ height: 400 }}>
            <div>
              <Row>
                <Col style={{ textAlign: 'center' }}>
                  {aliwareIntl.get(
                    'com.alibaba.nacos.component.DiffEditorDialog.of_the_current_area'
                  )}
                </Col>
                <Col style={{ textAlign: 'center' }}>
                  {aliwareIntl.get('com.alibaba.nacos.component.DiffEditorDialog.original_value')}
                </Col>
              </Row>
            </div>
            <div style={{ clear: 'both', height: 480 }} ref="diffeditor" />
          </div>
        </Dialog>
      </div>
    );
  }
}

export default DiffEditorDialog;
