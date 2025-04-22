import React from 'react';
import { Divider, ConfigProvider, Field, Form, Loading, Grid, Table } from '@alifd/next';
import { getParams, request } from '../../../globalLib';
import PropTypes from 'prop-types';
import CreateTools from './CreateTools/index';
import DeleteTool from './CreateTools/DeleteTool';
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
        tools: [],
      },
    };
    this.field = new Field(this);
    this.toolsRef = React.createRef();
  }

  componentDidMount() {
    this.getServerDetail();
  }

  getServerDetail = async () => {
    const mcpname = getParams('mcpname');
    this.setState({ loading: true });
    const result = await request({
      url: `v3/console/ai/mcp?mcpName=${mcpname}`,
    });
    this.setState({ loading: false });

    if (result.code == 0 && result.data) {
      this.setState({
        serverConfig: result.data,
      });
    }
  };

  openToolDetial = params => {
    const { type, record } = params;
    this.toolsRef?.current?.openVisible({ type, record });
  };

  getFormItem = params => {
    const { list = [] } = params;
    return (
      <Row>
        {list.map((item, index) => {
          return (
            <Col key={index}>
              <FormItem label={item.label}>
                <p>{item.value}</p>
              </FormItem>
            </Col>
          );
        })}
      </Row>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const formItemLayout = {
      labelCol: {
        span: 2,
      },
      wrapperCol: {
        span: 22,
      },
    };

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
            {this.getFormItem({
              list: [
                { label: locale.namespace, value: getParams('namespace') || '' }, // 命名空间
                { label: locale.serverName, value: this.state.serverConfig.name }, // 名称
              ],
            })}

            {this.getFormItem({
              list: [
                { label: locale.serverType, value: this.state.serverConfig.type }, // 类型
                { label: locale.serverDescription, value: this.state.serverConfig.description }, // 描述
              ],
            })}

            {this.state.serverConfig?.remoteServerConfig &&
              this.getFormItem({
                list: [
                  {
                    label: locale.backendProtocol,
                    value: this.state.serverConfig?.remoteServerConfig?.backendProtocol,
                  }, // 后端协议
                  {
                    label: locale.exportPath,
                    value: this.state.serverConfig?.remoteServerConfig?.exportPath,
                  }, // 暴露路径
                ],
              })}
          </Form>
          <Divider></Divider>

          <h2>Tools</h2>
          {!this.state.loading && (
            <CreateTools
              key={JSON.stringify(this.state?.serverConfig)}
              locale={locale}
              serverConfig={this.state.serverConfig}
              showTemplates={this.state.serverConfig?.remoteServerConfig?.backendProtocol == 'http'}
              ref={this.toolsRef}
              getServerDetail={this.getServerDetail}
            />
          )}

          <Table style={{ marginTop: '20px' }} dataSource={this.state?.serverConfig?.tools || []}>
            <Table.Column title={locale.toolName} dataIndex={'name'} />
            <Table.Column title={locale.toolDescription} dataIndex={'description'} />
            <Table.Column
              title={locale.operations}
              cell={(value, index, record) => {
                const { locale = {} } = this.props;
                return (
                  <div>
                    <a onClick={() => this.openToolDetial({ type: 'preview', record })}>
                      {locale.operationToolDetail}
                      {/* 详情 */}
                    </a>
                    <span style={{ margin: '0 5px' }}>|</span>
                    <a
                      style={{ marginRight: 5 }}
                      onClick={() => this.openToolDetial({ type: 'edit', record })}
                    >
                      {locale.operationToolEdit}
                      {/* 编辑 */}
                    </a>
                    <span style={{ margin: '0 5px' }}>|</span>
                    <DeleteTool
                      record={record}
                      locale={locale}
                      serverConfig={this.state.serverConfig}
                      getServerDetail={this.getServerDetail}
                    />
                  </div>
                );
              }}
            />
          </Table>
        </Loading>
      </div>
    );
  }
}

export default McpDetail;
