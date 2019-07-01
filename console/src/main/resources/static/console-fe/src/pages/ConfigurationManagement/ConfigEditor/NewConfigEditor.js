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
      betaPublishSuccess: false,
      betaIps: '',
      tabActiveKey: '',
      form: {
        dataId: '', // 配置 ID
        group: '', // 分组
        tenant: '', // 租户 ID
        content: '', // 配置内容
        appName: '', // 应用名
        desc: '', // 描述
        tags: [],
        type: 'text', // 配置格式
      },
      tagDataSource: [],
      openAdvancedSettings: false,
    };
    this.field = new Field(this);
  }

  componentDidMount() {
    this.initMoacoEditor('json', '{"a":100}');
  }

  initMoacoEditor(language, value) {
    const container = document.getElementById('container');
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
    this.setState({ tabActiveKey });
  }

  publish() {
    console.log('publish...', this.state.form);
  }

  publishBeta() {
    this.setState({ betaPublishSuccess: true, tabActiveKey: 'beta' });
  }

  stopBeta() {
    this.setState({ isBeta: false, betaPublishSuccess: false, tabActiveKey: '' });
  }

  changeForm(item) {
    const { form } = this.state;
    this.setState({ form: { ...form, ...item } });
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
    this.changeForm({ tags });
  }

  goBack() {
    console.log('goBack');
  }

  render() {
    const {
      loading,
      openAdvancedSettings,
      isBeta,
      betaPublishSuccess,
      form,
      tagDataSource,
      tabActiveKey,
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
            <Form.Item label="Data ID:" required>
              <Input onChange={dataId => this.changeForm({ dataId })} />
            </Form.Item>
            <Form.Item label="Group:" required>
              <Input onChange={group => this.changeForm({ group })} />
            </Form.Item>
            <Form.Item label=" ">
              <div
                className="switch"
                onClick={() => this.setState({ openAdvancedSettings: !openAdvancedSettings })}
              >
                显示高级选项
              </div>
            </Form.Item>
            {openAdvancedSettings && (
              <>
                <Form.Item label="标签:">
                  <Select
                    size="medium"
                    hasArrow
                    autoWidth
                    mode="tag"
                    filterLocal
                    dataSource={tagDataSource}
                    onChange={tags => this.setConfigTags(tags)}
                    hasClear
                  />
                </Form.Item>
                <Form.Item label="标归属应用:">
                  <Input onChange={appName => this.changeForm({ appName })} />
                </Form.Item>
              </>
            )}
            <Form.Item label="描述:">
              <Input.TextArea aria-label="TextArea" onChange={desc => this.changeForm({ desc })} />
            </Form.Item>
            <Form.Item label="租户 ID:">
              <Input onChange={tenant => this.changeForm({ tenant })} />
            </Form.Item>
            {!betaPublishSuccess && (
              <Form.Item label="Beta发布:">
                <Checkbox checked={isBeta} onChange={isBeta => this.setState({ isBeta })}>
                  默认不要勾选。
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
            <Form.Item label="配置格式:">
              <Radio.Group value={form.type} onChange={type => this.changeForm({ type })}>
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
                  <span>配置内容</span>
                  <Balloon
                    trigger={<Icon type="help" size="small" />}
                    align="t"
                    style={{ marginRight: 5 }}
                    triggerType="hover"
                  />
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
                  发布
                </Button>
              )}
              {isBeta && betaPublishSuccess && tabActiveKey !== 'production' && (
                <Button size="large" type="primary" onClick={() => this.stopBeta()}>
                  停止Beta
                </Button>
              )}
              {isBeta && tabActiveKey !== 'production' && (
                <Button size="large" type="primary" onClick={() => this.publishBeta()}>
                  发布Beta
                </Button>
              )}
              <Button size="large" type="normal" onClick={() => this.goBack()}>
                返回
              </Button>
            </Col>
          </Row>
        </Loading>
      </div>
    );
  }
}

export default ConfigEditor;
