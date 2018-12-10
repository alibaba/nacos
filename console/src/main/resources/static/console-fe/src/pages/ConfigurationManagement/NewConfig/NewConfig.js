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
import $ from 'jquery';
import SuccessDialog from '../../../components/SuccessDialog';
import { getParams, setParams, request, aliwareIntl } from '../../../globalLib';
import {
  Balloon,
  Button,
  Dialog,
  Field,
  Form,
  Icon,
  Input,
  Loading,
  Message,
  Select,
  Radio,
  ConfigProvider,
} from '@alifd/next';
import validateContent from 'utils/validateContent';

import './index.scss';

const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;
const { AutoComplete: Combobox } = Select;

@ConfigProvider.config
class NewConfig extends React.Component {
  static displayName = 'NewConfig';

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.edasAppName = getParams('edasAppName') || '';
    this.edasAppId = getParams('edasAppId') || '';
    this.inApp = this.edasAppName;
    this.field.setValue('appName', this.inApp ? this.edasAppName : '');
    this.inEdas = window.globalConfig.isParentEdas();
    this.dataId = getParams('dataId') || '';
    this.group = getParams('group') || 'DEFAULT_GROUP';
    this.searchDataId = getParams('searchDataId') || '';
    this.searchGroup = getParams('searchGroup') || '';
    this.state = {
      configType: 'text',
      codeValue: '',
      envname: '',
      targetEnvName: '',
      groups: [],
      groupNames: [],
      envlist: [],
      tagLst: [],
      config_tags: [],
      envvalues: [],
      showmore: false,
      loading: false,
      encrypt: false,
      addonBefore: '',
      showGroupWarning: false,
    };
    this.codeValue = '';
    this.mode = 'text';
    this.ips = '';
  }

  componentDidMount() {
    this.betaips = document.getElementById('betaips');
    // this.createCodeMirror('text', '');
    this.chontenttab = document.getElementById('chontenttab'); // diff标签
    this.tenant = getParams('namespace') || '';
    this.field.setValue('group', this.group);
    if (!window.monaco) {
      window.importEditor(() => {
        this.initMoacoEditor();
      });
    } else {
      this.initMoacoEditor();
    }
  }

  changeModel(type) {
    if (!this.monacoEditor) {
      $('#container').empty();
      this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
        model: null,
      });
      return;
    }
    const oldModel = this.monacoEditor.getModel();
    const oldValue = this.monacoEditor.getValue();
    const newModel = window.monaco.editor.createModel(oldValue, type);
    this.monacoEditor.setModel(newModel);
    // this.monacoEditor.setValue('xx')
    if (oldModel) {
      oldModel.dispose();
    }
  }

  initMoacoEditor() {
    this.monacoEditor = window.monaco.editor.create(document.getElementById('container'), {
      value: this.codeValue,
      language: this.state.configType,
      codeLens: true,
      selectOnLineNumbers: true,
      roundedSelection: false,
      readOnly: false,
      lineNumbersMinChars: true,
      theme: 'vs-dark',
      wordWrapColumn: 120,
      folding: true,
      showFoldingControls: 'always',
      wordWrap: 'wordWrapColumn',
      cursorStyle: 'line',
      automaticLayout: true,
    });
  }

  setGroup(value) {
    this.group = value || '';
    this.field.setValue('group', this.group);
    if (this.inEdas) {
      this.setState({
        showGroupWarning: this.group !== '' && this.state.groupNames.indexOf(value) < 0,
      });
    }
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

  toggleMore() {
    this.setState({
      showmore: !this.state.showmore,
    });
  }

  goList() {
    this.tenant = getParams('namespace') || '';
    this.serverId = getParams('serverId') || '';
    this.props.history.push(
      `/configurationManagement?serverId=${this.serverId}&group=${this.searchGroup}&dataId=${
        this.searchDataId
      }&namespace=${this.tenant}`
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

  newChangeConfig(value) {
    this.setState({
      configType: value,
    });

    this.changeModel(value);
  }

  setCodeValue(value) {
    this.setState({
      codeValue: value,
    });
  }

  publishConfig() {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      let { configType } = this.state;
      let content = '';
      const self = this;
      if (this.monacoEditor) {
        content = this.monacoEditor.getValue();
      } else {
        content = this.codeValue;
      }
      if (!content) {
        Message.error({
          content: aliwareIntl.get('nacos.page.ConfigEditor.submit_failed'),
          align: 'cc cc',
        });
        return;
      }

      if (validateContent.validate({ content, type: configType })) {
        this._publishConfig(content);
      } else {
        Dialog.confirm({
          content: '配置信息可能有语法错误, 确定提交吗?',
          language: aliwareIntl.currentLanguageCode || 'zh-cn',
          onOk: () => {
            this._publishConfig(content);
          },
        });
      }
    });
  }

  _publishConfig = content => {
    const self = this;
    let { addonBefore, config_tags, configType } = this.state;
    this.tenant = getParams('namespace') || '';
    const payload = {
      dataId: addonBefore + this.field.getValue('dataId'),
      group: this.field.getValue('group'),
      content,
      desc: this.field.getValue('desc'),
      config_tags: config_tags.join(),
      type: configType,
      appName: this.inApp ? this.edasAppId : this.field.getValue('appName'),
      tenant: this.tenant,
    };
    this.serverId = getParams('serverId') || 'center';
    const url = 'v1/cs/configs';
    request({
      type: 'post',
      contentType: 'application/x-www-form-urlencoded',
      url,
      data: payload,
      beforeSend: () => {
        this.openLoading();
      },
      success(res) {
        const _payload = {};
        _payload.maintitle = aliwareIntl.get('com.alibaba.nacos.page.newconfig.new_listing_main');
        _payload.title = aliwareIntl.get('com.alibaba.nacos.page.newconfig.new_listing');
        _payload.content = '';
        _payload.dataId = payload.dataId;
        _payload.group = payload.group;
        if (res === true) {
          self.group = payload.group;
          self.dataId = payload.dataId;
          setParams({ group: payload.group, dataId: payload.dataId }); // 设置参数
          _payload.isok = true;
        } else {
          _payload.isok = false;
          _payload.message = res.message;
        }
        self.refs.success.openDialog(_payload);
      },
      complete() {
        self.closeLoading();
      },
      error(res) {
        Dialog.alert({
          language: aliwareIntl.currentLanguageCode || 'zh-cn',
          content: aliwareIntl.get('com.alibaba.nacos.page.newconfig.publish_failed'),
        });
        self.closeLoading();
      },
    });
  };

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
  }

  getIps(value) {
    this.ips = value;
  }

  validateChart(rule, value, callback) {
    const { locale = {} } = this.props;
    const chartReg = /[@#\$%\^&\*]+/g;

    if (chartReg.test(value)) {
      callback(locale.doNotEnte);
    } else {
      callback();
    }
  }

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const formItemLayout = {
      labelCol: {
        span: 2,
      },
      wrapperCol: {
        span: 22,
      },
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
      {
        value: 'text',
        label: 'TEXT',
      },
      {
        value: 'json',
        label: 'JSON',
      },
      {
        value: 'xml',
        label: 'XML',
      },
      {
        value: 'yaml',
        label: 'YAML',
      },
      {
        value: 'text/html',
        label: 'HTML',
      },
      {
        value: 'properties',
        label: 'Properties',
      },
    ];

    return (
      <div style={{ padding: 10 }}>
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          style={{ width: '100%', position: 'relative' }}
          visible={this.state.loading}
          color={'#333'}
        >
          <h1>{locale.newListing}</h1>
          <Form field={this.field}>
            <FormItem label={'Data ID:'} required {...formItemLayout}>
              <Input
                {...init('dataId', {
                  rules: [
                    {
                      required: true,
                      message: locale.newConfig,
                    },
                    {
                      max: 255,
                      message: locale.dataIdIsNotEmpty,
                    },
                    { validator: this.validateChart.bind(this) },
                  ],
                })}
                addonTextBefore={
                  this.state.addonBefore ? (
                    <div style={{ minWidth: 100, color: '#373D41' }}>{this.state.addonBefore}</div>
                  ) : null
                }
              />
            </FormItem>
            <FormItem label={'Group:'} required {...formItemLayout}>
              <Combobox
                style={{ width: '100%' }}
                size={'large'}
                hasArrow
                dataSource={this.state.groups}
                placeholder={locale.groupPlaceholder}
                defaultValue={this.group}
                {...init('group', {
                  rules: [
                    {
                      required: true,
                      message: locale.moreAdvanced,
                    },
                    {
                      max: 127,
                      message: locale.groupNotEmpty,
                    },
                    { validator: this.validateChart.bind(this) },
                  ],
                })}
                onChange={this.setGroup.bind(this)}
                hasClear
              />
            </FormItem>
            <FormItem
              label={' '}
              {...formItemLayout}
              style={{ display: this.state.showGroupWarning ? 'block' : 'none' }}
            >
              <Message type={'warning'} size={'medium'} animation={false}>
                {locale.annotation}
              </Message>
            </FormItem>
            <FormItem label={''} {...formItemLayout}>
              <div>
                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                  {this.state.showmore ? locale.dataIdLength : locale.collapse}
                </a>
              </div>
            </FormItem>

            <div style={{ overflow: 'hidden', height: this.state.showmore ? 'auto' : '0' }}>
              <FormItem label={locale.tags} {...formItemLayout}>
                <Select
                  size={'medium'}
                  hasArrow
                  style={{ width: '100%', height: '100%!important' }}
                  autoWidth
                  multiple
                  mode="tag"
                  filterLocal
                  placeholder={locale.pleaseEnterTag}
                  dataSource={this.state.tagLst}
                  value={this.state.config_tags}
                  onChange={this.setConfigTags.bind(this)}
                  hasClear
                />
              </FormItem>

              <FormItem label={locale.groupIdCannotBeLonger} {...formItemLayout}>
                <Input {...init('appName')} readOnly={this.inApp} />
              </FormItem>
            </div>

            <FormItem label={locale.description} {...formItemLayout}>
              <Input.TextArea htmlType={'text'} multiple rows={3} {...init('desc')} />
            </FormItem>

            <FormItem label={locale.targetEnvironment} {...formItemLayout}>
              <RadioGroup
                dataSource={list}
                value={this.state.configType}
                onChange={this.newChangeConfig.bind(this)}
              />
            </FormItem>
            <FormItem
              label={
                <span>
                  {locale.configurationFormat}
                  <Balloon
                    trigger={
                      <Icon
                        type={'help'}
                        size={'small'}
                        style={{
                          color: '#1DC11D',
                          marginRight: 5,
                          verticalAlign: 'middle',
                          marginTop: 2,
                        }}
                      />
                    }
                    align={'t'}
                    style={{ marginRight: 5 }}
                    triggerType={'hover'}
                  >
                    <p>{locale.configureContentsOf}</p>
                    <p>{locale.fullScreen}</p>
                  </Balloon>
                  :
                </span>
              }
              required
              {...formItemLayout}
            >
              <div id={'container'} style={{ width: '100%', height: 300 }} />
            </FormItem>

            <FormItem {...formItemLayout} label={''}>
              <div style={{ textAlign: 'right' }}>
                <Button
                  type={'primary'}
                  style={{ marginRight: 10 }}
                  onClick={this.publishConfig.bind(this)}
                >
                  {locale.escExit}
                </Button>

                <Button type={'light'} onClick={this.goList.bind(this)}>
                  {locale.release}
                </Button>
              </div>
            </FormItem>
          </Form>
          <SuccessDialog ref={'success'} />
        </Loading>
      </div>
    );
  }
}

export default NewConfig;
