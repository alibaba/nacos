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

import $, { data } from 'jquery';
import React from 'react';
import PropTypes from 'prop-types';
import SuccessDialog from '../../../components/SuccessDialog';
import { getParams, setParams, request } from '../../../globalLib';
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

const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;
const { AutoComplete: Combobox } = Select;

@ConfigProvider.config
class NewCredential extends React.Component {
  static displayName = 'NewCredential';

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
      configType: 'json',
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
    this.codeValue = '{"api-key": "keyxxxxx"}';
    this.mode = 'json';
    this.ips = '';
  }

  componentDidMount() {
    const credentialId = getParams('credentialId') || '';
    const mcptype = getParams('mcptype') || '';
    const self = this;
    this.credentialId = credentialId;
    if (credentialId && mcptype === 'edit') {
      request({
        url: `v3/console/cs/config`,
        data: {
          dataId: credentialId,
          groupName: 'credentials',
          tenant: 'nacos-default-mcp',
          namespaceId: 'nacos-default-mcp',
        },
        success(result) {
          self.field.setValue('credentialId', credentialId);
          self.field.setValue('desc', result.data.desc);

          if (result.code == 0 && result.data) {
            self.codeValue = result.data.content;
          }
          if (!window.monaco) {
            window.importEditor(() => {
              self.initMoacoEditor();
            });
          } else {
            self.initMoacoEditor();
          }
        },
      });
    } else {
      if (!window.monaco) {
        window.importEditor(() => {
          self.initMoacoEditor();
        });
      } else {
        self.initMoacoEditor();
      }
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
      theme: 'vs-dark-enhanced',
      folding: true,
      showFoldingControls: 'always',
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
    this.tenant = getParams('namespace') || 'public';
    this.serverId = getParams('serverId') || '';
    this.props.history.push(
      generateUrl('/credentialManagement', {
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
        this.publishconfiginner(content);
      } else {
        Dialog.confirm({
          content: locale.confirmSyanx,
          onOk: () => {},
        });
      }
    });
  }

  publishconfiginner(content) {
    const mcptype = getParams('mcptype');
    if (mcptype === 'edit') {
      this._publishConfig(content);
    } else if (mcptype === 'new') {
      this.publicConfigBeforeCheck(content);
    }
  }

  /**
   * 因为后端接口没有做是否存在配置逻辑 会覆盖原先配置 所以提交前先判断是否存在
   */
  publicConfigBeforeCheck = content => {
    const { locale = {} } = this.props;
    const { addonBefore } = this.state;
    request({
      url: 'v3/console/cs/config',
      data: {
        dataId: addonBefore + this.field.getValue('credentialId'),
        groupName: 'credentials',
        namespaceId: 'nacos-default-mcp',
      },
      success: res => {
        // 检查 res.data 是否为 null，如果不是 null，说明存在就不提交配置
        if (res.data !== null) {
          Message.error({
            content: locale.credentialExist,
            align: 'cc cc',
          });
        } else {
          // 如果 res.data 为 null，表示没有数据，可以继续处理
          this._publishConfig(content);
        }
      },
      error: err => {
        // 后端接口很不规范，响应为空，说明没有数据，可以新增
        const { status } = err || {};
        if (status === 403) {
          Dialog.alert({
            content: locale.publishFailed403,
          });
          return;
        }
        this._publishConfig(content);
      },
    });
  };

  _publishConfig = content => {
    const self = this;
    const { locale = {} } = this.props;
    let { addonBefore, config_tags, configType } = this.state;
    this.tenant = 'nacos-default-mcp';
    const payload = {
      dataId: addonBefore + this.field.getValue('credentialId'),
      groupName: 'credentials',
      content,
      desc: this.field.getValue('desc'),
      type: configType,
      namespaceId: 'nacos-default-mcp',
    };
    this.serverId = getParams('serverId') || 'center';
    const url = 'v3/console/cs/config';
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
        this.goList();
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
    const chartReg = /^[a-zA-Z0-9_]+$/;
    if (!chartReg.test(value)) {
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
    const { editorClass } = this.state;
    const isEdit = getParams('mcptype') === 'edit';

    return (
      <Loading
        shape={'flower'}
        tip={'Loading...'}
        style={{ width: '100%', position: 'relative' }}
        visible={this.state.loading}
        color={'#333'}
      >
        <h1>{locale.newListing}</h1>
        <Form className="new-config-form" field={this.field} {...formItemLayout}>
          <FormItem label={locale.credentialId} required>
            <Input
              {...init('credentialId', {
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
              isPreview={isEdit}
            />
          </FormItem>

          <FormItem label={locale.description}>
            <Input.TextArea htmlType={'text'} multiple rows={3} {...init('desc')} />
          </FormItem>
          <FormItem
            label={
              <>
                <span>{locale.credentialContent}</span>
                <p>{locale.onlySupportJsonFormat}</p>
              </>
            }
            required
          >
            <div id={'container'} className={editorClass} style={{ minHeight: 450 }} />
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

              <Button type={'normal'} onClick={this.goList.bind(this)}>
                {locale.release}
              </Button>
            </div>
          </FormItem>
        </Form>
        <SuccessDialog ref={this.successDialog} />
      </Loading>
    );
  }
}

export default NewCredential;
