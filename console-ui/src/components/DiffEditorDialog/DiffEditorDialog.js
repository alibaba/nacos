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
import { Button, ConfigProvider, Dialog, Grid } from '@alifd/next';

import './index.scss';

const { Row, Col } = Grid;

@ConfigProvider.config
class DiffEditorDialog extends React.Component {
  static displayName = 'DiffEditorDialog';

  static propTypes = {
    locale: PropTypes.object,
  };

  static propTypes = {
    publishConfig: PropTypes.func,
    title: PropTypes.string,
    currentArea: PropTypes.string,
    originalArea: PropTypes.string,
  };

  constructor(props) {
    super(props);
    this.diffeditor = React.createRef();
    this.state = {
      dialogvisible: false,
    };
  }

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
    const target = this.diffeditor.current;
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
      revertButtons: typeof this.props.publishConfig === 'function',
    });
  }

  confirmPub() {
    this.closeDialog();
    this.props.publishConfig(this.diffeditor.editor().getValue());
  }

  render() {
    const { locale = {}, title, currentArea, originalArea } = this.props;
    const publishButton = (
      <Button type="primary" onClick={this.confirmPub.bind(this)}>
        {locale.publish}
      </Button>
    );
    const footer = (
      <div>
        {' '}
        {typeof this.props.publishConfig === 'function' ? (
          publishButton
        ) : (
          <Button type="primary" onClick={this.closeDialog.bind(this)}>
            {locale.back}
          </Button>
        )}
      </div>
    );
    return (
      <div>
        <Dialog
          title={title}
          style={{ width: '80%' }}
          visible={this.state.dialogvisible}
          footer={footer}
          onClose={this.closeDialog.bind(this)}
        >
          <div style={{ height: 400 }}>
            <div>
              <Row>
                <Col style={{ textAlign: 'center' }}>{currentArea}</Col>
                <Col style={{ textAlign: 'center' }}>{originalArea}</Col>
              </Row>
            </div>
            <div style={{ clear: 'both', height: 480 }} ref={this.diffeditor} />
          </div>
        </Dialog>
      </div>
    );
  }
}

export default DiffEditorDialog;
