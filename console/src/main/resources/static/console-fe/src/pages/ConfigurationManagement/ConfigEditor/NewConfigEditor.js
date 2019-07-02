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
import { getParams } from '../../../globalLib';
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

const { Row, Col } = Grid;

const LANGUAGE_LIST = [
  { value: 'text', label: 'TEXT' },
  { value: 'json', label: 'JSON' },
  { value: 'xml', label: 'XML' },
  { value: 'yaml', label: 'YAML' },
  { value: 'html', label: 'HTML' },
  { value: 'properties', label: 'Properties' },
];

const TAB_LIST = [{ key: 'production', label: '正式' }, { key: 'beta', label: 'BETA' }];

@ConfigProvider.config
class ConfigEditor extends React.Component {
  static displayName = 'ConfigEditor';

  static propTypes = {
    locale: PropTypes.object,
    // history: PropTypes.object,
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
      openAdvancedSettings: false,
    };
    this.successDialog = React.createRef();
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
          () => this.getConfig()
        );
      } else {
        if (group) {
          this.setState({ group });
        }
        this.initMoacoEditor('text', '');
      }
    });
  }

  initMoacoEditor(language, value) {
    const container = document.getElementById('container');
    container.innerHTML = '';
    this.monacoEditor = null;
    const options = {
      value,
      language: this.state.configType,
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

  clickTab(tabActiveKey) {
    this.setState({ tabActiveKey }, () => this.getConfig(tabActiveKey === 'bata'));
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
      this._publishConfig();
    } else {
      Dialog.confirm({
        content: locale.codeValErrorPrompt,
        onOk: () => this._publishConfig(),
      });
    }
  }

  _publishConfig(beta = false) {
    const { locale } = this.props;
    const { betaIps, isNewConfig } = this.state;
    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (beta) {
      headers.betaIps = betaIps;
    }
    const data = { ...this.state.form, content: this.getCodeVal() };
    return request({
      url: 'v1/cs/configs',
      method: 'post',
      data,
      transformRequest: [
        function(data) {
          let ret = '';
          for (let it in data) {
            ret += encodeURIComponent(it) + '=' + encodeURIComponent(data[it]) + '&';
          }
          return ret;
        },
      ],
      headers,
    }).then(res => {
      this.successDialog.current.getInstance().openDialog({
        title: <div>{locale.toedit}</div>,
        isok: true,
        ...data,
      });
      if (res) {
        this.setState({ isNewConfig: false });
      }
      return res;
    });
  }

  publishBeta() {
    this._publishConfig(true).then(res => {
      if (res) {
        this.setState({ betaPublishSuccess: true, tabActiveKey: 'beta' });
      }
    });
  }

  stopBeta() {
    const { dataId, group } = this.state.form;
    request
      .delete('v1/cs/configs', {
        params: {
          beta: true,
          dataId,
          group,
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
    this.changeForm({ config_tags: tags });
  }

  goBack() {
    const serverId = getParams('serverId') || '';
    const tenant = getParams('namespace');
    const searchGroup = getParams('searchGroup') || '';
    const searchDataId = getParams('searchDataId') || '';
    this.props.history.push(
      `/configurationManagement?serverId=${serverId}&group=${searchGroup}&dataId=${searchDataId}&namespace=${tenant}`
    );
  }

  getConfig(beta = false) {
    const namespace = getParams('namespace');
    const { dataId, group } = this.state.form;
    return request
      .get('v1/cs/configs', {
        params: {
          dataId,
          group,
          beta,
          show: 'all',
          namespaceId: namespace,
          tenant: namespace,
        },
      })
      .then(res => {
        const { type, content, configTags } = res;
        this.changeForm({ ...res, config_tags: configTags ? configTags.split(',') : [] });
        this.initMoacoEditor(type, content);
        this.codeVal = content;
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
      openAdvancedSettings,
      isBeta,
      isNewConfig,
      betaPublishSuccess,
      form,
      tagDataSource,
      tabActiveKey,
      dataIdError = {},
      groupError = {},
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
              {TAB_LIST.map(({ key, label }) => (
                <Tab.Item title={label} key={key}>
                  {label}
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
            {!isNewConfig && !betaPublishSuccess && (
              <Form.Item label={locale.betaPublish}>
                <Checkbox checked={isBeta} onChange={isBeta => this.setState({ isBeta })}>
                  {locale.betaSwitchPrompt}
                </Checkbox>
                {isBeta && (
                  <Input.TextArea
                    aria-label="TextArea"
                    placeholder="127.0.0.1,127.0.0.2"
                    onChange={betaIps => this.setState({ betaIps })}
                  />
                )}
              </Form.Item>
            )}
            <Form.Item label={locale.format}>
              <Radio.Group
                value={form.type}
                onChange={type => {
                  this.initMoacoEditor(type, '');
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
              <div style={{ clear: 'both', height: 300 }} id="container" />
            </Form.Item>
          </Form>
          <Row>
            <Col span="24" className="button-list">
              {(!isBeta || tabActiveKey === 'production') && (
                <Button
                  size="large"
                  type="primary"
                  disabled={tabActiveKey === 'production'}
                  onClick={() => this.publish()}
                >
                  {locale.publish}
                </Button>
              )}
              {isBeta && betaPublishSuccess && tabActiveKey !== 'production' && (
                <Button size="large" type="primary" onClick={() => this.stopBeta()}>
                  {locale.stopPublishBeta}
                </Button>
              )}
              {isBeta && tabActiveKey !== 'production' && (
                <Button size="large" type="primary" onClick={() => this.publishBeta()}>
                  {locale.release}
                </Button>
              )}
              <Button size="large" type="normal" onClick={() => this.goBack()}>
                {locale.back}
              </Button>
            </Col>
          </Row>
          <SuccessDialog ref={this.successDialog} />
        </Loading>
      </div>
    );
  }
}

export default ConfigEditor;
