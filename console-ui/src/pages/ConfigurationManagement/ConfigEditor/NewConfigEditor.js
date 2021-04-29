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
import { getParams } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';
import request from '../../../utils/request';
import validateContent from 'utils/validateContent';
import SuccessDialog from '../../../components/SuccessDialog';
import DiffEditorDialog from '../../../components/DiffEditorDialog';
import './index.scss';
import {
  Balloon,
  Button,
  Dialog,
  Field,
  Form,
  Checkbox,
  Icon,
  Input,
  Loading,
  Radio,
  Switch,
  Select,
  Tab,
  Message,
  Grid,
  ConfigProvider,
} from '@alifd/next';
import { resolve } from 'url';
import qs from 'qs';

const { Row, Col } = Grid;

const LANGUAGE_LIST = [
  { value: 'text', label: 'TEXT' },
  { value: 'json', label: 'JSON' },
  { value: 'xml', label: 'XML' },
  { value: 'yaml', label: 'YAML' },
  { value: 'html', label: 'HTML' },
  { value: 'properties', label: 'Properties' },
];

const TAB_LIST = ['production', 'beta'];

@ConfigProvider.config
class ConfigEditor extends React.Component {
  static displayName = 'ConfigEditor';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      isBeta: false,
      isNewConfig: true,
      betaPublishSuccess: false,
      betaIps: '',
      tabActiveKey: '',
      form: {
        dataId: '', // 配置 ID
        group: '', // 分组
        content: '', // 配置内容
        appName: '', // 应用名
        desc: '', // 描述
        config_tags: [],
        type: 'text', // 配置格式
      },
      tagDataSource: [],
      subscriberDataSource: [],
      openAdvancedSettings: false,
      editorClass: 'editor-normal',
    };
    this.successDialog = React.createRef();
    this.diffEditorDialog = React.createRef();
  }

  componentDidMount() {
    const isNewConfig = !getParams('dataId');
    const group = getParams('group').trim();
    this.setState({ isNewConfig }, () => {
      if (!isNewConfig) {
        this.changeForm(
          {
            dataId: getParams('dataId').trim(),
            group,
          },
          () => {
            this.getConfig(true).then(res => {
              if (!res) {
                this.getConfig();
                return;
              }
              this.setState({
                isBeta: true,
                tabActiveKey: 'beta',
                betaPublishSuccess: true,
              });
            });
            this.getSubscribesByNamespace();
          }
        );
      } else {
        if (group) {
          this.setState({ group });
        }
        this.initMoacoEditor('text', '');
      }
      this.initFullScreenEvent();
    });
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
      readOnly: false,
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

  createDiffCodeMirror(leftCode, rightCode) {
    const target = this.diffEditorDialog.current.getInstance();
    target.innerHTML = '';
    this.diffeditor = window.CodeMirror.MergeView(target, {
      value: leftCode || '',
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

  openDiff(cbName) {
    this.diffcb = cbName;
    let leftvalue = this.monacoEditor.getValue();
    let rightvalue = this.codeVal || '';
    leftvalue = leftvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
    rightvalue = rightvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
    this.diffEditorDialog.current.getInstance().openDialog(leftvalue, rightvalue);
  }

  clickTab(tabActiveKey) {
    this.setState({ tabActiveKey }, () => this.getConfig(tabActiveKey === 'beta'));
  }

  getCodeVal() {
    const { locale = {} } = this.props;
    const { type, content } = this.state.form;
    const codeVal = this.monacoEditor ? this.monacoEditor.getValue() : content;
    if (!codeVal) {
      Message.error({
        content: locale.submitFailed,
        align: 'cc cc',
      });
      return false;
    }
    return codeVal;
  }

  setCodeVal(codeVal) {
    const { form } = this.state;
    this.setState({ form: { ...form, content: codeVal } });
    if (this.monacoEditor) {
      this.monacoEditor.setValue(codeVal);
    }
  }

  publish() {
    const { locale = {} } = this.props;
    const { type } = this.state.form;
    if (this.state.isNewConfig) {
      this.validation();
    }
    const content = this.getCodeVal();
    if (!content) {
      return;
    }
    if (validateContent.validate({ content, type })) {
      return this._publishConfig();
    } else {
      return new Promise((resolve, reject) => {
        Dialog.confirm({
          content: locale.codeValErrorPrompt,
          onOk: () => resolve(this._publishConfig()),
          onCancel: () => resolve(false),
        });
      });
    }
  }

  _publishConfig(beta = false) {
    const { betaIps, isNewConfig } = this.state;
    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (beta) {
      headers.betaIps = betaIps;
    }
    const form = { ...this.state.form, content: this.getCodeVal() };
    const payload = {};
    Object.keys(form).forEach(key => {
      payload[key] = form[key];
    });
    let configTags = this.state.form.config_tags;
    if (configTags.length > 0) {
      payload.config_tags = configTags.join(',');
    }
    const stringify = require('qs/lib/stringify');
    this.setState({ loading: true });
    return request({
      url: 'v1/cs/configs',
      method: 'post',
      data: stringify(payload),
      headers,
    }).then(res => {
      if (res) {
        if (isNewConfig) {
          this.setState({ isNewConfig: false });
        }
        this.getConfig(beta);
      }
      this.setState({ loading: false });
      return res;
    });
  }

  publishBeta() {
    return this._publishConfig(true).then(res => {
      if (res) {
        this.setState({
          betaPublishSuccess: true,
          tabActiveKey: 'beta',
        });
        return res;
      }
    });
  }

  stopBeta() {
    const { locale } = this.props;
    const { dataId, group } = this.state.form;
    const tenant = getParams('namespace');
    return request
      .delete('v1/cs/configs', {
        params: {
          beta: true,
          dataId,
          group,
          tenant,
        },
      })
      .then(res => {
        if (res.data) {
          this.setState(
            {
              isBeta: false,
              betaPublishSuccess: false,
              tabActiveKey: '',
            },
            () => this.getConfig()
          );
        }
        return res;
      });
  }

  changeForm(item, cb) {
    const { form } = this.state;
    this.setState({ form: { ...form, ...item } }, () => {
      if (cb) {
        cb();
      }
    });
  }

  setConfigTags(tags) {
    const { tagDataSource } = this.state;
    if (tags.length > 0) {
      const lastTag = tags[tags.length - 1];
      if (tagDataSource.indexOf(lastTag) < 0) {
        this.setState({ tagDataSource: [...tagDataSource, lastTag] });
      }
      if (tags.length > 5) {
        tags.pop();
      }
      tags.forEach((v, i) => {
        if (v.indexOf(',') !== -1 || v.indexOf('=') !== -1) {
          tags.splice(i, 1);
        }
      });
    }
    this.changeForm({ config_tags: tags });
  }

  goBack() {
    const serverId = getParams('serverId') || '';
    const namespace = getParams('namespace');
    const group = getParams('searchGroup') || '';
    const dataId = getParams('searchDataId') || '';
    const pageSize = getParams('pageSize');
    const pageNo = getParams('pageNo');
    this.props.history.push(
      generateUrl('/configurationManagement', {
        serverId,
        group,
        dataId,
        namespace,
        pageSize,
        pageNo,
      })
    );
  }

  getConfig(beta = false, decide = false) {
    const namespace = getParams('namespace');
    const { dataId, group } = this.state.form;
    const params = {
      dataId,
      group,
      namespaceId: namespace,
      tenant: namespace,
    };
    if (beta) {
      params.beta = true;
    }
    if (!beta) {
      params.show = 'all';
    }
    return request.get('v1/cs/configs', { params }).then(res => {
      const form = beta ? res.data : res;
      if (!form) return false;
      const { type, content, configTags, betaIps } = form;
      this.setState({ betaIps });
      this.changeForm({ ...form, config_tags: configTags ? configTags.split(',') : [] });
      this.initMoacoEditor(type, content);
      this.codeVal = content;
      this.setState({
        tagDataSource: this.state.form.config_tags,
      });
      return res;
    });
  }

  getSubscribesByNamespace() {
    const namespace = getParams('namespace');
    const { dataId, group } = this.state.form;
    const params = {
      dataId,
      group,
      namespaceId: namespace,
      tenant: namespace,
    };
    // get subscribes of the namespace
    return request.get('v1/cs/configs/listener', { params }).then(res => {
      const { subscriberDataSource } = this.state;
      const lisentersGroupkeyIpMap = res.lisentersGroupkeyStatus;
      if (lisentersGroupkeyIpMap) {
        this.setState({
          subscriberDataSource: subscriberDataSource.concat(Object.keys(lisentersGroupkeyIpMap)),
        });
      }
      return res;
    });
  }

  validation() {
    const { locale } = this.props;
    const { form } = this.state;
    const { dataId, group } = form;
    if (!dataId) {
      this.setState({
        dataIdError: {
          validateState: 'error',
          help: locale.recipientFrom,
        },
      });
      return false;
    }
    if (!group) {
      this.setState({
        groupError: {
          validateState: 'error',
          help: locale.homeApplication,
        },
      });
      return false;
    }
    return true;
  }

  render() {
    const {
      loading,
      betaIps,
      openAdvancedSettings,
      isBeta,
      isNewConfig,
      betaPublishSuccess,
      form,
      tagDataSource,
      tabActiveKey,
      dataIdError = {},
      groupError = {},
      subscriberDataSource,
      editorClass,
    } = this.state;
    const { locale = {} } = this.props;

    return (
      <div className="config-editor">
        <Loading
          shape="flower"
          style={{ position: 'relative', width: '100%' }}
          visible={loading}
          tip="Loading..."
          color="#333"
        >
          <h1 className="func-title">
            <div>{locale.toedit}</div>
          </h1>
          {betaPublishSuccess && (
            <Tab shape="wrapped" activeKey={tabActiveKey} onChange={key => this.clickTab(key)}>
              {TAB_LIST.map(key => (
                <Tab.Item title={locale[key]} key={key}>
                  {locale[key]}
                </Tab.Item>
              ))}
            </Tab>
          )}
          <Form className="form">
            <Form.Item label="Data ID:" required {...dataIdError}>
              <Input
                value={form.dataId}
                onChange={dataId =>
                  this.changeForm({ dataId }, () => this.setState({ dataIdError: {} }))
                }
                disabled={!isNewConfig}
              />
            </Form.Item>
            <Form.Item label="Group:" required {...groupError}>
              <Input
                value={form.group}
                onChange={group =>
                  this.changeForm({ group }, () => this.setState({ groupError: {} }))
                }
                disabled={!isNewConfig}
              />
            </Form.Item>
            <Form.Item label=" ">
              <div
                className="switch"
                onClick={() => this.setState({ openAdvancedSettings: !openAdvancedSettings })}
              >
                {openAdvancedSettings ? locale.collapse : locale.groupNotEmpty}
              </div>
            </Form.Item>
            {openAdvancedSettings && (
              <>
                <Form.Item label={locale.tags}>
                  <Select
                    size="medium"
                    hasArrow
                    autoWidth
                    mode="tag"
                    filterLocal
                    value={form.config_tags}
                    dataSource={tagDataSource}
                    onChange={config_tags => this.setConfigTags(config_tags)}
                    hasClear
                  />
                </Form.Item>
                <Form.Item label={locale.targetEnvironment}>
                  <Input value={form.appName} onChange={appName => this.changeForm({ appName })} />
                </Form.Item>
              </>
            )}
            <Form.Item label={locale.description}>
              <Input.TextArea
                value={form.desc}
                aria-label="TextArea"
                onChange={desc => this.changeForm({ desc })}
              />
            </Form.Item>
            {!isNewConfig && tabActiveKey !== 'production' && (
              <Form.Item label={locale.betaPublish}>
                {!betaPublishSuccess && (
                  <Checkbox checked={isBeta} onChange={isBeta => this.setState({ isBeta })}>
                    {locale.betaSwitchPrompt}
                  </Checkbox>
                )}
                {isBeta && (
                  <Select
                    size="medium"
                    hasArrow
                    autoWidth
                    mode="tag"
                    filterLocal
                    dataSource={subscriberDataSource}
                    onChange={betaIps => this.setState({ betaIps: betaIps.join(',') })}
                    hasClear
                    value={betaIps ? betaIps.split(',') : []}
                  />
                )}
              </Form.Item>
            )}
            <Form.Item label={locale.format}>
              <Radio.Group
                defaultValue="text"
                value={form.type}
                onChange={type => {
                  this.initMoacoEditor(type, form.content);
                  this.changeForm({ type });
                }}
              >
                {LANGUAGE_LIST.map(item => (
                  <Radio value={item.value} key={item.value}>
                    {item.label}
                  </Radio>
                ))}
              </Radio.Group>
            </Form.Item>
            <Form.Item
              label={
                <div className="help-label">
                  <span>{locale.configcontent}</span>
                  <Balloon
                    trigger={<Icon type="help" size="small" />}
                    align="t"
                    style={{ marginRight: 5 }}
                    triggerType="hover"
                  >
                    <p>{locale.escExit}</p>
                    <p>{locale.releaseBeta}</p>
                  </Balloon>
                  <span>:</span>
                </div>
              }
            >
              <div id="container" className={editorClass} />
            </Form.Item>
          </Form>
          <Row>
            <Col span="24" className="button-list">
              {isBeta && betaPublishSuccess && tabActiveKey !== 'production' && (
                <Button
                  size="large"
                  type="primary"
                  onClick={() =>
                    this.stopBeta().then(() => {
                      this.successDialog.current.getInstance().openDialog({
                        title: <div>{locale.stopPublishBeta}</div>,
                        isok: true,
                        ...form,
                      });
                    })
                  }
                >
                  {locale.stopPublishBeta}
                </Button>
              )}
              {isBeta && tabActiveKey !== 'production' && (
                <Button
                  size="large"
                  type="primary"
                  disabled={!betaIps || betaPublishSuccess}
                  onClick={() => this.openDiff('publishBeta')}
                >
                  {locale.release}
                </Button>
              )}
              <Button
                size="large"
                type="primary"
                disabled={tabActiveKey === 'production'}
                onClick={() => this.openDiff('publish')}
              >
                {locale.publish}
              </Button>
              <Button size="large" type="normal" onClick={() => this.goBack()}>
                {locale.back}
              </Button>
            </Col>
          </Row>
          <DiffEditorDialog
            ref={this.diffEditorDialog}
            publishConfig={codeVal => {
              this.setCodeVal(codeVal);
              const res = this[this.diffcb]();
              res.then(res => {
                if (!res) {
                  return;
                }
                let title = locale.toedit;
                if (isNewConfig) {
                  title = locale.newConfigEditor;
                }
                if (this.diffcb === 'publishBeta') {
                  title = locale.betaPublish;
                }
                if (this.diffcb === 'publish' && tabActiveKey === 'beta') {
                  title = locale.stopPublishBeta;
                  this.stopBeta();
                }
                this.successDialog.current.getInstance().openDialog({
                  title: <div>{title}</div>,
                  isok: true,
                  ...form,
                });
              });
            }}
            title={locale.dialogTitle}
            currentArea={locale.dialogCurrentArea}
            originalArea={locale.dialogOriginalArea}
          />
          <SuccessDialog ref={this.successDialog} />
        </Loading>
      </div>
    );
  }
}

export default ConfigEditor;
