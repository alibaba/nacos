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
  Divider,
  ConfigProvider,
  Dialog,
  Field,
  Form,
  Input,
  Loading,
  Tab,
  Grid,
  Table,
  Drawer,
} from '@alifd/next';
import { getParams, request } from '../../../globalLib';
import { generateUrl } from '../../../utils/nacosutil';

import PropTypes from 'prop-types';
import requestUtils from '../../../utils/request';
import { Button } from '@alifd/theme-design-pro';
import CreateTools from './CreateTools/index';

const TabPane = Tab.Item;
const FormItem = Form.Item;
const { Row, Col } = Grid;

@ConfigProvider.config
class McpDetail extends React.Component {
  static displayName = 'McpDetail';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      showmore: false,
      openToolDetial: false,
      activeTool: {
        paramData: [],
        toolMeta: {},
        description: '',
        name: '',
      },
      toolsMap: {},
      tools: [],
      backendProltol: '',
      toolsMeta: {},
      serverConfig: {
        name: '',
        type: '',
        description: '',
        version: '',
        backendProtocol: '',
        exportPath: '',
        remoteServerConfig: {
          exportPath: '',
          backendProtocol: '',
          serviceRef: {},
        },
      },
      addNewToolDiaOpen: false,
    };
    this.field = new Field(this, {
      parseName: true,
      values: {
        toolParams: [],
      },
    });
    this.tenant = getParams('namespace') || '';
    this.searchMcpServer = getParams('searchMcpServer') || '';
  }

  componentDidMount() {
    this.getServerDetail();
  }

  openLoading() {
    this.setState({
      loading: true,
    });
  }

  openAddNewTool() {
    this.setState({
      addNewToolDiaOpen: true,
    });
  }

  closeAddNewTool() {
    this.setState({
      addNewToolDiaOpen: false,
    });
  }

  closeLoading() {
    this.setState({
      loading: false,
    });
  }

  toggleMore() {
    this.setState({
      showmore: !this.state.showmore,
    });
  }

  openToolDetial(toolName) {
    const paramData = [];
    const properties = this.state.toolsMap[toolName].inputSchema.properties;
    const toolMeta = this.state.toolsMeta[toolName];
    for (const key in properties) {
      paramData.push({
        name: key,
        type: properties[key].type,
        description: properties[key].description,
      });
    }
    this.setState({
      openToolDetial: true,
      activeTool: {
        paramData: paramData,
        name: toolName,
        toolMeta: toolMeta,
        description: this.state.toolsMap[toolName].description,
      },
    });
  }

  closeToolDetial() {
    this.setState({
      openToolDetial: false,
      activeTool: {
        paramData: [],
        name: '',
        description: '',
        toolMeta: {
          InvokeContext: {},
        },
      },
    });
  }

  getServerDetail = async () => {
    const { locale = {} } = this.props;
    const mcpname = getParams('mcpname');
    const result = await request({
      url: `v3/console/ai/mcp?mcpName=${mcpname}`,
    });
    console.log('【 查询详情获取参数 result】=》', result);

    if (result.code == 0 && result.data) {
      this.setState({
        serverConfig: result.data,
      });
    }

    // const self = this;
    // this.tenant = getParams('namespace') || '';
    // console.log('current server name ', this.serverName);
    // const serverUrl = `v1/cs/configs?show=all&dataId=${this.serverName}-mcp-server.json&group=mcp-server`;
    // const toolsUrl = `v1/cs/configs?show=all&dataId=${this.serverName}-mcp-tools.json&group=mcp-tools`;
    // request({
    //   url: serverUrl,
    //   beforeSend() {
    //     self.openLoading();
    //   },
    //   success(result) {
    //     if (result != null) {
    //       const data = JSON.parse(result.content);
    //       self.state.serverConfig = data;
    //     } else {
    //       Dialog.alert({ title: locale.error, content: result.message });
    //     }
    //   },
    //   complete() {},
    // }).then(() => {
    //   request({
    //     url: toolsUrl,
    //     beforeSend() {},
    //     success(result) {
    //       if (result != null) {
    //         const data = JSON.parse(result.content);
    //         self.state.tools = data.tools;
    //         self.state.toolsMeta = data.toolsMeta;
    //         const toolsMap = {};
    //         for (const index in data.tools) {
    //           toolsMap[data.tools[index].name] = data.tools[index];
    //         }
    //         self.state.toolsMap = toolsMap;
    //         //self.initMoacoEditor(data.type, data.content);
    //       } else {
    //         Dialog.alert({ title: locale.error, content: result.message });
    //       }
    //     },
    //     complete() {
    //       self.closeLoading();
    //     },
    //   });
    // });
  };
  renderCol(value, index, record) {
    const { locale = {} } = this.props;
    return (
      <div>
        <a
          style={{ marginRight: 5 }}
          onClick={() => {
            this.openToolDetial(record.name);
          }}
        >
          {locale.operationToolDetail}
        </a>
        <span style={{ marginRight: 5 }}>|</span>
        <a style={{ marginRight: 5 }}>{locale.operationToolEdit}</a>
      </div>
    );
  }

  addNewToolParam(name, type, description) {
    console.log('addNewToolParam');
    this.field.addArrayValue('toolParams', 1, { name, type, description });
    console.log(this.field.getValue('toolParams'));
  }

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const { tools, activeTool } = this.state;
    const formItemLayout = {
      labelCol: {
        span: 2,
      },
      wrapperCol: {
        span: 22,
      },
    };

    const invokeContext = [];
    for (const key in activeTool.toolMeta.InvokeContext) {
      invokeContext.push({
        key: key,
        value: activeTool.toolMeta.InvokeContext[key],
      });
    }

    this.newToolType = (value, index) => <Input {...this.field.init(`toolParams.${index}.type`)} />;
    this.newToolDescription = (value, index) => (
      <Input.TextArea {...this.field.init(`toolParams.${index}.description`)} />
    );
    this.newToolName = (value, index) => <Input {...this.field.init(`toolParams.${index}.name`)} />;
    const dataSource = this.field.getValue('toolParams');
    const self = this;
    return (
      <div>
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          style={{ width: '100%', position: 'relative' }}
          visible={this.state.loading}
          color={'#333'}
        >
          <h1 style={{ position: 'relative', width: '60%' }}>{locale.mcpServerDetail}</h1>
          <Form inline={false} field={this.field} {...formItemLayout}>
            <Row>
              <Col>
                <FormItem label={locale.namespace}>
                  <p>{this.tenant}</p>
                </FormItem>
              </Col>
              <Col>
                <FormItem label={locale.serverName}>
                  <p>{this.state.serverConfig.name}</p>
                </FormItem>
              </Col>
            </Row>

            <Row>
              <Col>
                {/* 类型 */}
                <FormItem label={locale.serverType}>
                  <p>{this.state.serverConfig.type}</p>
                </FormItem>
              </Col>
              <Col>
                {/* 描述 */}
                <FormItem label={locale.serverDescription}>
                  <p>{this.state.serverConfig.description}</p>
                </FormItem>
              </Col>
            </Row>

            <Row>
              <Col>
                {/* 后端协议 */}
                <FormItem label={locale.backendProtocol}>
                  <p>{this.state.serverConfig?.remoteServerConfig?.backendProtocol}</p>
                </FormItem>
              </Col>
              <Col>
                {/* 暴露路径 */}
                <FormItem label={locale.exportPath}>
                  <p>{this.state.serverConfig?.remoteServerConfig?.exportPath}</p>
                </FormItem>
              </Col>
            </Row>

            <FormItem label=" ">
              <div>
                <a style={{ fontSize: '12px' }} onClick={this.toggleMore.bind(this)}>
                  {this.state.showmore ? locale.collapse : locale.more}
                </a>
              </div>
            </FormItem>
          </Form>
          <Divider></Divider>

          <h2>Tools</h2>
          <CreateTools
            locale={locale}
            serverConfig={this.state.serverConfig}
            showTemplates={this.state.serverConfig?.remoteServerConfig?.backendProtocol == 'http'}
          />
          <Button
            type="primary"
            onClick={() => {
              self.openAddNewTool();
            }}
          >
            {locale.newMcpTool}
          </Button>
          <Table style={{ marginTop: '20px' }} dataSource={tools}>
            <Table.Column sortable={true} title={locale.toolName} dataIndex={'name'} />
            <Table.Column title={locale.operations} cell={this.renderCol.bind(this)} />
          </Table>

          <Drawer
            v2
            title={locale.toolDetail}
            placement="right"
            visible={this.state.openToolDetial}
            width={'40%'}
            onClose={() => {
              self.closeToolDetial();
            }}
          >
            <Form>
              <FormItem label={locale.toolName}>
                <p>{activeTool.name}</p>
              </FormItem>
              <FormItem label={locale.toolDescription}>
                <p>{activeTool.description}</p>
              </FormItem>
              <FormItem label={locale.toolInputSchema}>
                <Table dataSource={activeTool.paramData}>
                  <Table.Column sortable={true} title={locale.toolParamName} dataIndex={'name'} />
                  <Table.Column title={locale.toolParamType} dataIndex={'type'} />
                  <Table.Column title={locale.toolParamDescription} dataIndex={'description'} />
                </Table>
              </FormItem>

              <FormItem label={locale.invokeContext}>
                <Table dataSource={invokeContext}>
                  <Table.Column title={locale.toolParamType} dataIndex={'key'} />
                  <Table.Column title={locale.toolParamDescription} dataIndex={'value'} />
                </Table>
              </FormItem>
            </Form>
          </Drawer>
          <Dialog
            v2
            title={locale.newMcpTool}
            visible={self.state.addNewToolDiaOpen}
            onOk={() => {
              self.closeAddNewTool();
            }}
            onClose={() => {
              self.closeAddNewTool();
            }}
            style={{ width: '70%' }}
          >
            <Form>
              <FormItem label={locale.toolName} required>
                <Input placeholder={locale.toolName} />
              </FormItem>
              <FormItem label={locale.toolDescription} required>
                <Input.TextArea placeholder={locale.toolDescription} />
              </FormItem>
              <FormItem label={locale.toolInputSchema} required>
                <Button
                  type="primary"
                  onClick={() => {
                    self.addNewToolParam();
                  }}
                >
                  {locale.newMcpTool}
                </Button>
                <Table style={{ marginTop: '10px' }} dataSource={dataSource}>
                  <Table.Column
                    title={locale.toolParamName}
                    dataIndex="name"
                    cell={this.newToolName}
                  />
                  <Table.Column
                    title={locale.toolParamType}
                    dataIndex="type"
                    cell={this.newToolType}
                  />
                  <Table.Column
                    title={locale.toolParamDescription}
                    dataIndex="description"
                    cell={this.newToolDescription}
                  />
                </Table>
              </FormItem>
            </Form>
          </Dialog>
        </Loading>
      </div>
    );
  }
}

export default McpDetail;
