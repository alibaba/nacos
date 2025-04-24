import React from 'react';
import { Divider, ConfigProvider, Loading, Grid, Table } from '@alifd/next';
import { getParams, request } from '../../../globalLib';
import PropTypes from 'prop-types';
import ShowTools from './ShowTools';
import { generateUrl } from '../../../utils/nacosutil';
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
        protocol: '',
        description: '',
        version: '',
        exportPath: '',
        remoteServerConfig: {
          exportPath: '',
          serviceRef: {},
        },
        tools: [],
      },
    };
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

  getFormItem = params => {
    const { list = [] } = params;
    return (
      <Row wrap style={{ textAlign: 'left', marginBottom: '8px' }}>
        {list.map((item, index) => {
          return (
            <Col key={item.label} span={12} style={{ display: 'flex' }}>
              <p style={{ minWidth: 80 }}>{item.label}</p>
              <p>{item.value}</p>
            </Col>
          );
        })}
      </Row>
    );
  };

  goToServiceDetail = serviceRef => {
    this.props.history.push(
      generateUrl('/serviceDetail', {
        namespaceId: serviceRef.namespaceId,
        groupName: serviceRef.groupName,
        serviceName: serviceRef.serviceName,
      })
    );
  };

  render() {
    const localServerConfig = JSON.stringify(this.state.serverConfig?.localServerConfig, null, 2);
    const { locale = {} } = this.props;
    const credentials = this.state.serverConfig?.credentials;
    const credentialsTables = [];
    if (credentials) {
      for (const credentialsKey in credentials) {
        credentialsTables.push({
          id: credentialsKey,
          name: credentialsKey,
        });
      }
    }
    return (
      <div>
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          style={{
            width: '100%',
            position: 'relative',
          }}
          visible={this.state.loading}
          color={'#333'}
        >
          <h1
            style={{
              position: 'relative',
              width: '60%',
            }}
          >
            {locale.mcpServerDetail}
          </h1>
          <h2
            style={{
              color: '#333',
              fontWeight: 'bold',
            }}
          >
            {locale.basicInformation}
          </h2>
          <div style={{ marginTop: '16px' }}>
            {this.getFormItem({
              list: [
                {
                  label: locale.namespace,
                  value: getParams('namespace') || '',
                }, // 命名空间
                {
                  label: locale.serverName,
                  value: this.state.serverConfig.name,
                }, // 名称
              ],
            })}

            {this.getFormItem({
              list: [
                {
                  label: locale.serverType,
                  value: this.state.serverConfig.protocol,
                }, // 类型
                {
                  label: locale.serverDescription,
                  value: this.state.serverConfig.description,
                }, // 描述
              ],
            })}

            {this.state.serverConfig?.protocol !== 'stdio' &&
              this.getFormItem({
                list: [
                  {
                    label: locale.exportPath,
                    value: this.state.serverConfig?.remoteServerConfig?.exportPath,
                  }, // 暴露路径
                  {
                    label: '服务引用',
                    value: (
                      <a
                        onClick={() => {
                          this.goToServiceDetail(
                            this.state.serverConfig?.remoteServerConfig?.serviceRef
                          );
                        }}
                      >
                        {this.state.serverConfig?.remoteServerConfig?.serviceRef.namespaceId}/
                        {this.state.serverConfig?.remoteServerConfig?.serviceRef.groupName}/
                        {this.state.serverConfig?.remoteServerConfig?.serviceRef.serviceName}
                      </a>
                    ),
                  },
                ],
              })}
          </div>
          {this.state.serverConfig?.protocol === 'stdio' && (
            <>
              <Divider></Divider>
              <h2>Local Server Config</h2>
              <pre>{localServerConfig}</pre>
            </>
          )}
          <Divider></Divider>
          <h2>Credentials</h2>
          <Table dataSource={credentialsTables}>
            <Table.Column title="Credential" dataIndex="id" />
          </Table>

          <Divider></Divider>
          <h2>Tools</h2>
          <ShowTools
            locale={locale}
            serverConfig={this.state.serverConfig}
            getServerDetail={this.getServerDetail}
            isPreview={true}
          />
        </Loading>
      </div>
    );
  }
}

export default McpDetail;
