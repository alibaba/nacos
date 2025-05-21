import React from 'react';
import {
  Divider,
  ConfigProvider,
  Loading,
  Grid,
  Table,
  Button,
  Select,
  Form,
  Tab,
} from '@alifd/next';
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
    const mcpServerId = getParams('id');
    const version = getParams('version');
    const namespace = getParams('namespace');
    this.setState({ loading: true });
    const result =
      version === null
        ? await request({
            url: `v3/console/ai/mcp?mcpId=${mcpServerId}&namespaceId=${namespace}`,
          })
        : await request({
            url: `v3/console/ai/mcp?mcpId=${mcpServerId}&version=${version}&namespaceId=${namespace}`,
          });
    this.setState({ loading: false });

    if (result.code == 0 && result.data) {
      this.setState({
        serverConfig: result.data,
      });
    }

    console.log(result.data);
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
        name: serviceRef.serviceName,
      })
    );
  };

  goToVersion = version => {
    this.props.history.push(
      generateUrl('/mcpServerDetail', {
        namespace: getParams('namespace'),
        id: getParams('id'),
        version: version,
      })
    );
    this.getServerDetail();
  };

  goToToEditVersion = version => {
    this.props.history.push(
      generateUrl('/newMcpServer', {
        namespace: getParams('namespace'),
        id: getParams('id'),
        version: this.state.serverConfig.versionDetail.version,
        mcptype: 'edit',
      })
    );
  };

  render() {
    const localServerConfig = JSON.stringify(this.state.serverConfig?.localServerConfig, null, 2);
    const { locale = {} } = this.props;
    const versions = this.state.serverConfig?.allVersions
      ? this.state.serverConfig?.allVersions
      : [];

    const versionSelections = [];
    for (let i = 0; i < versions.length; i++) {
      const item = versions[i];
      if (item.is_latest) {
        versionSelections.push({
          label: item.version + ` (` + locale.versionIsPublished + ')',
          value: item.version,
        });
      } else {
        versionSelections.push({ label: item.version, value: item.version });
      }
    }

    this.state?.serverConfig?.backendEndpoints;
    const endpoints = [];
    for (let i = 0; i < this.state?.serverConfig?.backendEndpoints?.length; i++) {
      const item = this.state?.serverConfig?.backendEndpoints[i];
      const endpoint = item.address + item.port + item.path;
      const serverConfig = {
        index: i,
        endpoint: endpoint,
        serverConfig: {
          mcpServers: {},
        },
      };
      serverConfig.serverConfig.mcpServers[this.state.serverConfig?.name] = {
        url: endpoint,
      };
      endpoints.push(serverConfig);
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
          <Row>
            <Col span={16}>
              <h1
                style={{
                  position: 'relative',
                  width: '60%',
                }}
              >
                {locale.mcpServerDetail}
              </h1>
            </Col>
            <Col span={4}>
              <span>版本</span>
              <Select
                dataSource={versionSelections}
                style={{
                  marginLeft: 10,
                  width: '80%',
                }}
                value={this.state.serverConfig?.versionDetail?.version}
                onChange={data => {
                  this.goToVersion(data);
                }}
              ></Select>
            </Col>

            <Col span={4}>
              <Button type={'primary'} onClick={this.goToToEditVersion}>
                {locale.createNewVersionBasedOnCurrentVersion}
              </Button>
            </Col>
          </Row>
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
                  value: this.state.serverConfig.frontProtocol,
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
              <h2>Server Config</h2>
              <pre>{localServerConfig}</pre>
            </>
          )}

          {this.state.serverConfig?.protocol !== 'stdio' && (
            <>
              <Divider></Divider>
              <h2>Server Config</h2>
              {endpoints?.length > 0 ? (
                <Tab excessMode="dropdown" defaultActiveKey={0}>
                  {endpoints?.map(item => (
                    <Tab.Item key={item.index} title={item.endpoint}>
                      <pre>{JSON.stringify(item.serverConfig, null, 2)}</pre>
                    </Tab.Item>
                  ))}
                </Tab>
              ) : (
                <p>{locale.noAvailableEndpoint}</p>
              )}

              {/* <Table dataSource={this.state.serverConfig.backendEndpoints}> */}
              {/*   <Table.Column */}
              {/*     title={'endpoint'} */}
              {/*     cell={(value, index, record) => { */}
              {/*       return 'http://' + record.address + ':' + record.port + record.path; */}
              {/*     }} */}
              {/*   ></Table.Column> */}
              {/* </Table> */}
            </>
          )}

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
