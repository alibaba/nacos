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
import SuccessDialog from '../../../components/SuccessDialog';
import { getParams, setParams, request, aliwareIntl } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';
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

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.successDialog = React.createRef();
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
      editorClass: 'editor-normal',
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
    this.initFullScreenEvent();
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

  setGroup(value) {
    this.group = value || '';
    this.field.setValue('group', this.group);
    if (this.inEdas) {
      this.setState({
        showGroupWarning: this.group !== '' && this.state.groupNames.indexOf(value) < 0,
      });
    }
  }

  tagSearch(value) {
    const { tagLst } = this.state;
    if (!tagLst.includes(value)) {
      this.setState({ tagLst: [value, ...tagLst] });
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
      tagLst: value,
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
      generateUrl('/configurationManagement', {
        serverId: this.serverId,
        group: this.searchGroup,
        dataId: this.searchDataId,
        namespace: this.tenant,
      })
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
          content: locale.dataRequired,
          align: 'cc cc',
        });
        return;
      }

      if (validateContent.validate({ content, type: configType })) {
        this.publicConfigBeforeCheck(content);
      } else {
        Dialog.confirm({
          content: locale.confirmSyanx,
          onOk: () => {
            this.publicConfigBeforeCheck(content);
          },
        });
      }
    });
  }

  /**
   * 因为后端接口没有做是否存在配置逻辑 会覆盖原先配置 所以提交前先判断是否存在
   */
  publicConfigBeforeCheck = content => {
    const { locale = {} } = this.props;
    const { addonBefore } = this.state;
    request({
      url: 'v1/cs/configs',
      data: {
        show: 'all',
        dataId: addonBefore + this.field.getValue('dataId'),
        group: this.field.getValue('group'),
        tenant: getParams('namespace') || '',
      },
      success: res => {
        // 返回成功 说明存在就不提交配置
        Message.error({
          content: locale.dataIdExists,
          align: 'cc cc',
        });
      },
      error: err => {
        // 后端接口很不规范 响应为空 说明没有数据 就可以新增
        this._publishConfig(content);
      },
    });
  };

  _publishConfig = content => {
    const self = this;
    const { locale = {} } = this.props;
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
        _payload.maintitle = locale.newListingMain;
        _payload.title = locale.newListing;
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
        self.successDialog.current.getInstance().openDialog(_payload);
      },
      complete: () => {
        this.closeLoading();
      },
      error: res => {
        this.closeLoading();
        Dialog.alert({
          content: locale.publishFailed,
        });
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
    const chartReg = /[@#\$%\^&\*\s]+/g;

    if (chartReg.test(value)) {
      callback(locale.doNotEnter);
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
        value: 'html',
        label: 'HTML',
      },
      {
        value: 'properties',
        label: 'Properties',
      },
    ];
    const { editorClass } = this.state;

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
          <Form className="new-config-form" field={this.field} {...formItemLayout}>
            <FormItem label={'Data ID:'} required>
              <Input
                {...init('dataId', {
                  rules: [
                    {
                      required: true,
                      message: locale.newConfig,
                    },
                    { validator: this.validateChart.bind(this) },
                  ],
                })}
                maxLength={255}
                addonTextBefore={
                  this.state.addonBefore ? (
                    <div style={{ minWidth: 100, color: '#373D41' }}>{this.state.addonBefore}</div>
                  ) : null
                }
              />
            </FormItem>
            <FormItem label={'Group:'} required>
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
                      maxLength: 127,
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
              style={{ display: this.state.showGroupWarning ? 'block' : 'none' }}
            >
              <Message type={'warning'} size={'medium'} animation={false}>
                {locale.annotation}
              </Message>
            </FormItem>

            <FormItem
              label={locale.tags}
              className={`more-item${!this.state.showmore ? ' hide' : ''}`}
            >
              <Select
                size={'medium'}
                showSearch
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
                onSearch={val => this.tagSearch(val)}
                hasClear
              />
            </FormItem>

            <FormItem
              label={locale.groupIdCannotBeLonger}
              className={`more-item${!this.state.showmore ? ' hide' : ''}`}
            >
              <Input {...init('appName')} readOnly={this.inApp} />
            </FormItem>
            <FormItem label=" ">
              <div className="more-container">
                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                  {this.state.showmore ? locale.dataIdLength : locale.collapse}
                </a>
              </div>
            </FormItem>

            <FormItem label={locale.description}>
              <Input.TextArea htmlType={'text'} multiple rows={3} {...init('desc')} />
            </FormItem>

            <FormItem label={locale.targetEnvironment}>
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
                          margin: '0 5px',
                          verticalAlign: 'middle',
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
            >
              <div id={'container'} className={editorClass} />
            </FormItem>

            <FormItem label=" ">
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
          <SuccessDialog ref={this.successDialog} />
        </Loading>
      </div>
    );
  }
}

export default NewConfig;
