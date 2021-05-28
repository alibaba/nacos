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
import {
  Button,
  ConfigProvider,
  Dialog,
  Field,
  Form,
  Input,
  Loading,
  Tab,
  Grid,
} from '@alifd/next';
import { getParams, request } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';
import DiffEditorDialog from '../../../components/DiffEditorDialog';

import './index.scss';
import PropTypes from 'prop-types';
import requestUtils from '../../../utils/request';
import ConfigCompared from './ConfigCompared';

const TabPane = Tab.Item;
const FormItem = Form.Item;
const { Row, Col } = Grid;

@ConfigProvider.config
class ConfigDetail extends React.Component {
  static displayName = 'ConfigDetail';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      showmore: false,
      activeKey: 'normal',
      hasbeta: false,
      ips: '',
      checkedBeta: false,
      switchEncrypt: false,
      tag: [],
      editorClass: 'editor-normal',
    };
    this.field = new Field(this);
    this.dataId = getParams('dataId') || 'yanlin';
    this.group = getParams('group') || 'DEFAULT_GROUP';
    this.ips = '';
    this.valueMap = {}; // 存储不同版本的数据
    this.tenant = getParams('namespace') || '';
    this.searchDataId = getParams('searchDataId') || '';
    this.searchGroup = getParams('searchGroup') || '';
    this.pageSize = getParams('pageSize');
    this.pageNo = getParams('pageNo');
    this.diffEditorDialog = React.createRef();
    this.compareEditorDialog = React.createRef();
    // this.params = window.location.hash.split('?')[1]||'';
  }

  componentDidMount() {
    this.initData();
    this.getDataDetail();
    this.initFullScreenEvent();
  }

  initData() {
    const { locale = {} } = this.props;
    if (this.dataId.startsWith('cipher-')) {
      this.setState({
        switchEncrypt: true,
      });
    }
    this.setState({ tag: [{ title: locale.official, key: 'normal' }] });
  }

  initFullScreenEvent() {
    document.body.addEventListener('keydown', e => {
      if (e.key === 'F1') {
        e.preventDefault();
        this.setState({
          editorClass: 'editor-full-screen',
        });
      }
      if (e.key === 'Escape') {
        this.setState({
          editorClass: 'editor-normal',
        });
      }
    });
  }

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  changeTab(value) {
    const self = this;
    const key = value.split('-')[0];
    const data = this.valueMap[key];
    this.setState({
      activeKey: value,
    });

    self.field.setValue('content', data.content);

    if (data.betaIps) {
      self.setState({
        ips: data.betaIps,
      });
    }
  }

  toggleMore() {
    this.setState({
      showmore: !this.state.showmore,
    });
  }

  getDataDetail() {
    const { locale = {} } = this.props;
    const self = this;
    this.serverId = getParams('serverId') || 'center';
    this.tenant = getParams('namespace') || '';
    this.edasAppName = getParams('edasAppName') || '';
    this.inApp = this.edasAppName;
    const url = `v1/cs/configs?show=all&dataId=${this.dataId}&group=${this.group}`;
    request({
      url,
      beforeSend() {
        self.openLoading();
      },
      success(result) {
        if (result != null) {
          const data = result;
          self.valueMap.normal = data;
          self.field.setValue('dataId', data.dataId);
          self.field.setValue('content', data.content);
          self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
          self.field.setValue('envs', self.serverId);
          self.field.setValue('group', data.group);
          self.field.setValue('config_tags', data.configTags);
          self.field.setValue('desc', data.desc);
          self.field.setValue('md5', data.md5);
          self.field.setValue('type', data.type);
          self.initMoacoEditor(data.type, data.content);
        } else {
          Dialog.alert({ title: locale.error, content: result.message });
        }
      },
      complete() {
        self.closeLoading();
      },
    });
  }

  goList() {
    this.props.history.push(
      generateUrl('/configurationManagement', {
        serverId: this.serverId,
        group: this.searchGroup,
        dataId: this.searchDataId,
        namespace: this.tenant,
        pageNo: this.pageNo,
        pageSize: this.pageSize,
      })
    );
  }

  initMoacoEditor(language, value) {
    const container = document.getElementById('container');
    container.innerHTML = '';
    this.monacoEditor = null;
    const options = {
      value,
      language,
      codeLens: true,
      selectOnLineNumbers: true,
      roundedSelection: false,
      readOnly: true,
      lineNumbersMinChars: true,
      theme: 'vs-dark',
      wordWrapColumn: 120,
      folding: false,
      showFoldingControls: 'always',
      wordWrap: 'wordWrapColumn',
      cursorStyle: 'line',
      automaticLayout: true,
    };
    if (!window.monaco) {
      window.importEditor(() => {
        this.monacoEditor = window.monaco.editor.create(container, options);
      });
    } else {
      this.monacoEditor = window.monaco.editor.create(container, options);
    }
  }

  openDiff() {
    let self = this;
    const { locale = {} } = this.props;
    let leftvalue = this.monacoEditor.getValue();
    let url = `v1/cs/history/previous?id=${this.valueMap.normal.id}`;
    request({
      url,
      beforeSend() {
        self.openLoading();
      },
      success(result) {
        if (result != null) {
          let rightvalue = result.content;
          leftvalue = leftvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
          rightvalue = rightvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
          self.diffEditorDialog.current.getInstance().openDialog(leftvalue, rightvalue);
        } else {
          Dialog.alert({ title: locale.error, content: result.message });
        }
      },
      complete() {
        self.closeLoading();
      },
    });
  }

  openCompare = ([dataId, group, tenant]) => {
    let self = this;
    const { locale = {} } = this.props;
    let leftvalue = this.monacoEditor.getValue();
    const params = {
      show: 'all',
      group,
      dataId,
      tenant,
    };
    requestUtils.get('v1/cs/configs', { params }).then(res => {
      if (res != null && res !== '') {
        let rightvalue = res.content;
        leftvalue = leftvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
        rightvalue = rightvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
        self.compareEditorDialog.current.getInstance().openDialog(leftvalue, rightvalue);
      } else {
        Dialog.alert({ title: locale.error, content: locale.configNotFind });
      }
    });
  };

  onClickConfigCompare() {
    this.setState({ configCompareVisible: true });
  }

  closeConfigCompare() {
    this.setState({ configCompareVisible: false });
  }

  render() {
    const { locale = {} } = this.props;
    const { configCompareVisible, editorClass } = this.state;
    const { init } = this.field;
    const formItemLayout = {
      labelCol: {
        span: 2,
      },
      wrapperCol: {
        span: 22,
      },
    };
    const activeKey = this.state.activeKey.split('-')[0];
    return (
      <div style={{ padding: 10 }}>
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          style={{ width: '100%', position: 'relative' }}
          visible={this.state.loading}
          color={'#333'}
        >
          <h1 style={{ position: 'relative', width: '100%' }}>{locale.configurationDetails}</h1>
          {this.state.hasbeta ? (
            <div style={{ display: 'inline-block', height: 40, width: '80%', overflow: 'hidden' }}>
              <Tab
                shape={'wrapped'}
                onChange={this.changeTab.bind(this)}
                lazyLoad={false}
                activeKey={this.state.activeKey}
              >
                {this.state.tag.map(tab => (
                  <TabPane title={tab.title} key={tab.key} />
                ))}
              </Tab>
            </div>
          ) : (
            ''
          )}
          <Form inline={false} field={this.field}>
            <FormItem label={'Data ID:'} required {...formItemLayout}>
              <Input htmlType={'text'} readOnly {...init('dataId')} />
            </FormItem>
            <FormItem label={'Group:'} required {...formItemLayout}>
              <Input htmlType={'text'} readOnly {...init('group')} />
            </FormItem>
            <div style={{ marginTop: 10 }}>
              <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                {this.state.showmore ? locale.collapse : locale.more}
              </a>
            </div>
            {this.state.showmore ? (
              <div>
                <FormItem label={locale.home} {...formItemLayout}>
                  <Input htmlType={'text'} readOnly {...init('appName')} />
                </FormItem>

                <FormItem label={locale.tags} {...formItemLayout}>
                  <Input htmlType={'text'} readOnly {...init('config_tags')} />
                </FormItem>
              </div>
            ) : (
              ''
            )}

            <FormItem label={locale.description} {...formItemLayout}>
              <Input.TextArea htmlType={'text'} multiple rows={3} readOnly {...init('desc')} />
            </FormItem>
            {activeKey === 'normal' ? (
              ''
            ) : (
              <FormItem label={locale.betaRelease} {...formItemLayout}>
                <div style={{ width: '100%' }} id={'betaips'}>
                  <Input.TextArea
                    multiple
                    style={{ width: '100%' }}
                    value={this.state.ips}
                    readOnly
                    placeholder={'127.0.0.1,127.0.0.2'}
                  />
                </div>
              </FormItem>
            )}
            <FormItem label={'MD5:'} required {...formItemLayout}>
              <Input htmlType={'text'} readOnly {...init('md5')} />
            </FormItem>
            <FormItem label={locale.configuration} required {...formItemLayout}>
              <div className={editorClass} id="container" />
            </FormItem>
          </Form>
          <Row>
            <Col span="24" className="button-list">
              <Button size="large" type="primary" onClick={() => this.onClickConfigCompare()}>
                {locale.configComparison}
              </Button>{' '}
              <Button size="large" type="primary" onClick={this.openDiff.bind(this)}>
                {locale.versionComparison}
              </Button>{' '}
              <Button size="large" type="normal" onClick={this.goList.bind(this)}>
                {locale.back}
              </Button>
            </Col>
          </Row>
          <DiffEditorDialog
            ref={this.diffEditorDialog}
            title={locale.versionComparison}
            currentArea={locale.dialogCurrentArea}
            originalArea={locale.dialogOriginalArea}
          />
          <DiffEditorDialog
            ref={this.compareEditorDialog}
            title={locale.configComparison}
            currentArea={locale.dialogCurrentConfig}
            originalArea={locale.dialogComparedConfig}
          />
        </Loading>
        <ConfigCompared
          visible={configCompareVisible}
          dataId={this.dataId}
          group={this.group}
          onOk={config => {
            this.openCompare(config);
          }}
          onCancel={() => this.closeConfigCompare()}
        />
      </div>
    );
  }
}

export default ConfigDetail;
