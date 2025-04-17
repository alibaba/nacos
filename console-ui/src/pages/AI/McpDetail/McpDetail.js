import React from 'react';
import { Divider, ConfigProvider, Field, Form, Loading, Grid, Table } from '@alifd/next';
import { getParams, request } from '../../../globalLib';
import PropTypes from 'prop-types';
import CreateTools from './CreateTools/index';
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
      tools: [],
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
    };
    this.field = new Field(this);
    this.toolsRef = React.createRef();
  }

  componentDidMount() {
    this.getServerDetail();
  }

  getServerDetail = async () => {
    const { locale = {} } = this.props;
    const mcpname = getParams('mcpname');
    this.setState({ loading: true, tools: [] });
    const result = await request({
      url: `v3/console/ai/mcp?mcpName=${mcpname}`,
    });
    this.setState({ loading: false });
    console.log('【 查询详情获取参数 result】=》', result);

    if (result.code == 0 && result.data) {
      this.setState({
        serverConfig: result.data,
        tools: result.data.tools,
      });
    }
  };

  openToolDetial = params => {
    const { type, record } = params;
    this.toolsRef?.current?.openVisible({ type, record });
  };

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const { tools } = this.state;
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
            <Row>
              <Col>
                <FormItem label={locale.namespace}>
                  <p>{getParams('namespace') || ''}</p>
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
                <FormItem label={locale.serverType}>
                  <p>
                    {this.state.serverConfig.type}
                    {/* 类型 */}
                  </p>
                </FormItem>
              </Col>
              <Col>
                <FormItem label={locale.serverDescription}>
                  <p>
                    {this.state.serverConfig.description}
                    {/* 描述 */}
                  </p>
                </FormItem>
              </Col>
            </Row>

            <Row>
              <Col>
                <FormItem label={locale.backendProtocol}>
                  <p>
                    {this.state.serverConfig?.remoteServerConfig?.backendProtocol}
                    {/* 后端协议 */}
                  </p>
                </FormItem>
              </Col>
              <Col>
                <FormItem label={locale.exportPath}>
                  <p>
                    {this.state.serverConfig?.remoteServerConfig?.exportPath}
                    {/* 暴露路径 */}
                  </p>
                </FormItem>
              </Col>
            </Row>
          </Form>
          <Divider></Divider>

          <h2>Tools</h2>
          <CreateTools
            locale={locale}
            serverConfig={this.state.serverConfig}
            showTemplates={this.state.serverConfig?.remoteServerConfig?.backendProtocol == 'http'}
            ref={this.toolsRef}
          />

          <Table style={{ marginTop: '20px' }} dataSource={tools}>
            <Table.Column sortable={true} title={locale.toolName} dataIndex={'name'} />
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
                    <a>
                      {locale.operationToolDelete} {/* 删除 */}
                    </a>
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
