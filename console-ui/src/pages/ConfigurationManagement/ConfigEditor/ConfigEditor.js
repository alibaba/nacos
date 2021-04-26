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

import $ from 'jquery';
import React from 'react';
import PropTypes from 'prop-types';
import { getParams, request } from '../../../globalLib';
import DiffEditorDialog from '../../../components/DiffEditorDialog';
import SuccessDialog from '../../../components/SuccessDialog';
import validateContent from 'utils/validateContent';
import {
  Balloon,
  Button,
  Dialog,
  Field,
  Form,
  Icon,
  Input,
  Loading,
  Radio,
  Select,
  Tab,
  Message,
  ConfigProvider,
} from '@alifd/next';

import './index.scss';

const TabPane = Tab.Item;
const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;

@ConfigProvider.config
class ConfigEditor extends React.Component {
  static displayName = 'ConfigEditor';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.diffEditorDialog = React.createRef();
    this.successDialog = React.createRef();
    this.edasAppName = getParams('edasAppName') || '';
    this.edasAppId = getParams('edasAppId') || '';
    this.inApp = this.edasAppName;
    this.field = new Field(this);
    this.dataId = getParams('dataId') || 'yanlin';
    this.group = getParams('group') || 'DEFAULT_GROUP';
    this.tenant = getParams('namespace') || '';
    this.state = {
      configType: 'text',
      codeValue: '',
      envname: 'center',
      targetEnvName: '',
      envlist: [],
      envvalues: [],
      loading: false,
      showmore: false,
      activeKey: 'normal',
      hasbeta: false,
      ips: '',
      checkedBeta: false,
      tagLst: [],
      config_tags: [],
      switchEncrypt: false,
      tag: [],
    };
    this.codeValue = '';
    this.mode = 'text';
    this.ips = '';
    this.valueMap = {}; // 存储不同版本的数据
    this.searchDataId = getParams('searchDataId') || '';
    this.searchGroup = getParams('searchGroup') || '';
  }

  componentDidMount() {
    this.initData();
    this.betaips = document.getElementById('betaips');
    this.getDataDetail();
    this.chontenttab = document.getElementById('chontenttab'); // diff标签
  }

  initData() {
    const { locale = {} } = this.props;
    this.setState({
      tag: [
        {
          title: locale.official,
          key: 'normal',
        },
      ],
    });
    if (this.dataId.startsWith('cipher-')) {
      this.setState({ switchEncrypt: true });
    }
  }

  initMoacoEditor(language, value) {
    if (!window.monaco) {
      window.importEditor(() => {
        this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
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
        });
      });
    } else {
      this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
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
      });
    }
  }

  toggleMore() {
    this.setState({
      showmore: !this.state.showmore,
    });
  }

  navTo(url) {
    this.serverId = getParams('serverId') || '';
    this.tenant = getParams('namespace') || ''; // 为当前实例保存tenant参数
    this.props.history.push(
      `${url}?serverId=${this.serverId || ''}&dataId=${this.dataId}&group=${this.group}&namespace=${
        this.tenant
      }`
    );
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

  getDataDetail() {
    const { locale = {} } = this.props;
    const self = this;
    this.tenant = getParams('namespace') || '';
    this.serverId = getParams('serverId') || 'center';
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
          // self.field.setValue('content', data.content);
          self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
          // self.field.setValue('envs', self.serverId);
          self.field.setValue('group', data.group);

          // self.field.setValue('type', data.type);
          self.field.setValue('desc', data.desc);
          // self.field.setValue('md5', data.md5);
          self.codeValue = data.content || '';
          const type = data.type || 'text';
          self.setState({
            // 设置radio 高亮
            configType: type,
          });
          self.initMoacoEditor(type, self.codeValue);

          // self.createCodeMirror('text', self.codeValue);
          // self.codeValue = self.commoneditor.doc.getValue();
          if (data.configTags != null) {
            const tagArr = data.configTags.split(',');
            self.setConfigTags(tagArr);
          }

          const envvalues = [];
          const env = {};
          self.serverId = env.serverId;
          self.targetEnvs = envvalues;
        } else {
          Dialog.alert({ title: locale.wrong, content: result.message });
        }
      },
      complete() {
        self.closeLoading();
      },
    });
  }

  goList() {
    const tenant = getParams('namespace');
    this.props.history.push(
      `/configurationManagement?serverId=${this.serverId}&group=${this.searchGroup}&dataId=${this.searchDataId}&namespace=${tenant}`
    );
  }

  createCodeMirror(mode, value) {
    const commontarget = this.refs.commoneditor;
    commontarget.innerHTML = '';
    this.commoneditor = window.CodeMirror(commontarget, {
      value,
      mode,
      lineNumbers: true,
      theme: 'xq-light',
      lint: true,
      gutters: ['CodeMirror-lint-markers'],
      extraKeys: {
        F1(cm) {
          cm.setOption('fullScreen', !cm.getOption('fullScreen'));
        },
        Esc(cm) {
          if (cm.getOption('fullScreen')) cm.setOption('fullScreen', false);
        },
      },
    });
    this.commoneditor.on('change', this.codemirrorValueChanged.bind(this));
  }

  codemirrorValueChanged(doc) {
    if (this.diffeditor) {
      this.diffeditor.edit.doc.setValue(doc.getValue());
    }
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

  changeConfig(value) {
    if (value === 0) {
      this.createCodeMirror('text', this.codeValue);
      this.mode = 'text';
    }
    if (value === 1) {
      this.createCodeMirror('application/json', this.codeValue);
      this.mode = 'application/json';
    }
    if (value === 2) {
      this.createCodeMirror('xml', this.codeValue);
      this.mode = 'xml';
    }
    this.setState({
      configType: value,
    });
  }

  setCodeValue(value) {
    this.setState({
      codeValue: value,
    });
  }

  toggleDiff(checked) {
    if (checked) {
      this.chontenttab.style.display = 'block';

      const nowvalue = this.commoneditor.doc.getValue();
      if (!this.diffeditor) {
        this.createDiffCodeMirror(nowvalue, this.codeValue);
      }
    } else {
      this.chontenttab.style.display = 'none';
      // this.diffeditor = null;
      // let target = this.refs["diffeditor"];
      // target.innerHTML = '';
    }
  }

  publishConfig() {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      let content = '';
      let { configType } = this.state;

      if (this.monacoEditor) {
        content = this.monacoEditor.getValue();
      } else {
        content = this.codeValue;
      }
      if (!content) {
        Message.error({
          content: locale.submitFailed,
          align: 'cc cc',
        });
        return;
      }
      if (validateContent.validate({ content, type: configType })) {
        this._publishConfig(content);
      } else {
        Dialog.confirm({
          content: '配置信息可能有语法错误, 确定提交吗?',
          onOk: () => {
            this._publishConfig(content);
          },
        });
      }
    });
  }

  _publishConfig = content => {
    const { locale = {} } = this.props;
    const self = this;
    this.codeValue = content;
    this.tenant = getParams('namespace') || '';
    this.serverId = getParams('serverId') || 'center';

    const payload = {
      dataId: this.field.getValue('dataId'),
      appName: this.inApp ? this.edasAppId : this.field.getValue('appName'),
      group: this.field.getValue('group'),
      desc: this.field.getValue('desc'),
      config_tags: this.state.config_tags.join(','),
      type: this.state.configType,
      content,
      tenant: this.tenant,
    };
    const url = 'v1/cs/configs';
    request({
      type: 'post',
      contentType: 'application/x-www-form-urlencoded',
      url,
      data: payload,
      success(res) {
        const _payload = {};
        _payload.maintitle = locale.toedittitle;
        _payload.title = <div>{locale.toedit}</div>;
        _payload.content = '';
        _payload.dataId = payload.dataId;
        _payload.group = payload.group;

        if (res != null) {
          _payload.isok = true;
          const activeKey = self.state.activeKey.split('-')[0];
          if (activeKey === 'normal' && self.hasips === true) {
            // 如果是在normal面板选择了beta发布
            const sufex = new Date().getTime();
            self.setState({
              tag: [
                { title: locale.official, key: `normal-${sufex}` },
                { title: 'BETA', key: `beta-${sufex}` },
              ],
              hasbeta: true,
              activeKey: `beta-${sufex}`,
            });
            payload.betaIps = payload.betaIps || payload.ips;
            self.valueMap.beta = payload; // 赋值beta
            self.changeTab(`beta-${sufex}`);
          }
          if (activeKey === 'normal' && self.hasips === false) {
            // 如果是在normal面板选择了发布
            self.valueMap.normal = payload; // 赋值正式
          }
          if (activeKey === 'beta' && self.hasips === true) {
            // 如果是在beta面板继续beta发布
            self.valueMap.beta = payload; // 赋值beta
          }
        } else {
          _payload.isok = false;
          _payload.message = res.message;
        }
        self.successDialog.current.getInstance().openDialog(_payload);
      },
      error() {},
    });
  };

  validateChart(rule, value, callback) {
    const { locale = {} } = this.props;
    const chartReg = /[@#\$%\^&\*]+/g;
    if (chartReg.test(value)) {
      callback(locale.vdchart);
    } else {
      callback();
    }
  }

  changeEnv(values) {
    this.targetEnvs = values;
    this.setState({
      envvalues: values,
    });
  }

  changeBeta(selected) {
    if (selected) {
      this.betaips.style.display = 'block';
    } else {
      this.betaips.style.display = 'none';
    }
    this.setState({
      checkedBeta: selected,
    });
  }

  getIps(value) {
    this.ips = value;
    this.setState({
      ips: value,
    });
  }

  setConfigTags(value) {
    if (value.length > 5) {
      value.pop();
    }
    value.forEach((v, i) => {
      if (v.indexOf(',') !== -1 || v.indexOf('=') !== -1) {
        value.splice(i, 1);
      }
    });
    this.setState({
      config_tags: value,
    });
  }

  onInputUpdate(value) {
    if (this.inputtimmer) {
      clearTimeout(this.inputtimmer);
    }
    this.inputtimmer = setTimeout(() => {
      const { tagLst } = this.state;

      let hastag = false;
      tagLst.forEach((v, i) => {
        if (v.value === value) {
          hastag = true;
        }
      });
      if (!hastag) {
        tagLst.push({
          value,
          label: value,
          time: Math.random(),
        });
      }
      this.setState({ tagLst });
    }, 500);
  }

  openDiff(hasips) {
    this.hasips = hasips; // 是否包含ips
    let leftvalue = this.monacoEditor.getValue(); // this.commoneditor.doc.getValue();
    let rightvalue = this.codeValue;
    leftvalue = leftvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
    rightvalue = rightvalue.replace(/\r\n/g, '\n').replace(/\n/g, '\r\n');
    // let rightvalue = this.diffeditor.doc.getValue();
    // console.log(this.commoneditor, leftvalue==rightvalue)
    this.diffEditorDialog.current.getInstance().openDialog(leftvalue, rightvalue);
  }

  changeTab(value) {
    const self = this;
    const key = value.split('-')[0];

    const data = this.valueMap[key];
    this.setState({
      activeKey: value,
    });
    self.field.setValue('dataId', data.dataId);

    self.field.setValue('appName', self.inApp ? self.edasAppName : data.appName);
    // self.field.setValue('envs', self.serverId);
    self.field.setValue('group', data.group);
    // self.field.setValue('md5', data.md5);
    self.codeValue = data.content || '';
    self.createCodeMirror('text', self.codeValue);
    if (data.betaIps) {
      self.getIps(data.betaIps);
      self.changeBeta(true);
    } else {
      self.getIps('');
      self.changeBeta(false);
    }
  }

  newChangeConfig(value) {
    this.setState({
      configType: value,
    });
    this.changeModel(value);
  }

  changeModel(type, value) {
    if (!this.monacoEditor) {
      $('#container').empty();
      this.initMoacoEditor(type, value);
      return;
    }
    const oldModel = this.monacoEditor.getModel();
    const oldValue = this.monacoEditor.getValue();
    const newModel = window.monaco.editor.createModel(oldValue, type);
    this.monacoEditor.setModel(newModel);
    if (oldModel) {
      oldModel.dispose();
    }
  }

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const formItemLayout = {
      labelCol: { span: 2 },
      wrapperCol: { span: 22 },
    };

    // const list = [{
    //     value: 0,
    //     label: 'TEXT'
    // }, {
    //     value: 1,
    //     label: 'JSON'
    // }, {
    //     value: 2,
    //     label: 'XML'
    // }];
    const list = [
      { value: 'text', label: 'TEXT' },
      { value: 'json', label: 'JSON' },
      { value: 'xml', label: 'XML' },
      { value: 'yaml', label: 'YAML' },
      { value: 'html', label: 'HTML' },
      { value: 'properties', label: 'Properties' },
    ];
    const activeKey = this.state.activeKey.split('-')[0];

    return (
      <div style={{ padding: 10 }}>
        <Loading
          shape="flower"
          style={{ position: 'relative', width: '100%' }}
          visible={this.state.loading}
          tip="Loading..."
          color="#333"
        >
          <h1 style={{ overflow: 'hidden', height: 50, width: '100%' }}>
            <div>{locale.toedit}</div>
          </h1>
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

          <Form field={this.field}>
            <FormItem label="Data ID:" {...formItemLayout}>
              <Input
                disabled
                {...init('dataId', {
                  rules: [
                    { required: true, message: locale.recipientFrom },
                    { validator: this.validateChart.bind(this) },
                  ],
                })}
              />
            </FormItem>
            <FormItem label="Group:" {...formItemLayout}>
              <Input
                disabled
                {...init('group', {
                  rules: [
                    { required: true, message: locale.homeApplication },
                    { validator: this.validateChart.bind(this) },
                  ],
                })}
              />
            </FormItem>
            <FormItem label="" {...formItemLayout}>
              <div>
                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                  {this.state.showmore ? locale.collapse : locale.groupNotEmpty}
                </a>
              </div>
            </FormItem>
            <div style={{ height: this.state.showmore ? 'auto' : '0', overflow: 'hidden' }}>
              <FormItem label={locale.tags} {...formItemLayout}>
                <Select
                  size="medium"
                  hasArrow
                  style={{ width: '100%' }}
                  autoWidth
                  mode="tag"
                  filterLocal
                  placeholder={locale.pleaseEnterTag}
                  dataSource={this.state.tagLst}
                  value={this.state.config_tags}
                  onChange={this.setConfigTags.bind(this)}
                  hasClear
                />
              </FormItem>

              <FormItem label={locale.targetEnvironment} {...formItemLayout}>
                <Input {...init('appName')} readOnly={!!this.inApp} />
              </FormItem>
            </div>

            <FormItem label={locale.description} {...formItemLayout}>
              <Input.TextArea htmlType="text" multiple rows={3} {...init('desc')} />
            </FormItem>
            <FormItem label={locale.format} {...formItemLayout}>
              <RadioGroup
                dataSource={list}
                value={this.state.configType}
                onChange={this.newChangeConfig.bind(this)}
              />
            </FormItem>
            <FormItem
              label={
                <span style={{ marginRight: 5 }}>
                  {locale.configcontent}
                  <Balloon
                    trigger={
                      <Icon
                        type="help"
                        size={'small'}
                        style={{
                          color: '#1DC11D',
                          marginRight: 5,
                          verticalAlign: 'middle',
                          marginTop: 2,
                        }}
                      />
                    }
                    align="t"
                    style={{ marginRight: 5 }}
                    triggerType="hover"
                  >
                    <p>{locale.escExit}</p>
                    <p>{locale.releaseBeta}</p>
                  </Balloon>
                  :
                </span>
              }
              {...formItemLayout}
            >
              <div style={{ clear: 'both', height: 300 }} id="container" />
            </FormItem>
            <FormItem {...formItemLayout} label="">
              <div style={{ textAlign: 'right' }}>
                {activeKey === 'beta' ? (
                  <Button
                    style={{ marginRight: 10 }}
                    type="primary"
                    onClick={this.openDiff.bind(this, true)}
                  >
                    {locale.release}
                  </Button>
                ) : (
                  ''
                )}
                {activeKey === 'normal' ? (
                  <Button
                    type="primary"
                    disabled={this.state.hasbeta}
                    style={{ marginRight: 10 }}
                    onClick={this.openDiff.bind(this, this.state.checkedBeta)}
                  >
                    {this.state.checkedBeta ? locale.release : locale.publish}
                  </Button>
                ) : (
                  <Button
                    type="primary"
                    style={{ marginRight: 10 }}
                    onClick={this.openDiff.bind(this, false)}
                  >
                    {locale.publish}
                  </Button>
                )}

                <Button type="normal" onClick={this.goList.bind(this)}>
                  {locale.back}
                </Button>
              </div>
            </FormItem>
          </Form>
          <DiffEditorDialog
            ref={this.diffEditorDialog}
            publishConfig={this.publishConfig.bind(this)}
          />
          <SuccessDialog ref={this.successDialog} />
        </Loading>
      </div>
    );
  }
}

export default ConfigEditor;
