import React from 'react';
import PropTypes from 'prop-types';
import { getParams, setParams, request } from '../../../globalLib';
import {
  Balloon,
  Button,
  Dialog,
  Field,
  Form,
  Icon,
  Grid,
  Input,
  Loading,
  Message,
  Select,
  Radio,
  Icon,
  Balloon,
  ConfigProvider,
} from '@alifd/next';
import { McpServerManagementRouteName, McpServerManagementRoute } from '../../../layouts/menu';
import ShowTools from '../McpDetail/ShowTools';
const { Row, Col } = Grid;

const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;
const localServerConfigDesc = `示例：{
  "mcpServers":{
  "description": "高德地图服务",
  "command": "npx",
  "args": [
    "-y",
    "@amap/amap-maps-mcp-server"
  ],
  "env": {
    "AMAP_MAPS_API_KEY": "<API_KEY>" // 配置API_KEY信息
  }
}}`;

@ConfigProvider.config
class NewMcpServer extends React.Component {
  static displayName = 'NewMcpServer';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.field = new Field(this);
    this.tenant = getParams('namespace') || '';
    this.state = {
      loading: false,
      addonBefore: '',
      namespaceSelects: [],
      useExistService: true,
      serviceList: [],
      serverConfig: {},
      credentials: {},
    };
  }

  componentDidMount() {
    if (!getParams('namespace')) {
      this.props?.history?.push({
        pathname: McpServerManagementRoute,
      });
    }
    this.initEditedData();
    this.getNamespaces();
  }

  // 编辑数据 初始化
  initEditedData = async () => {
    const mcpname = getParams('mcpname') || '';
    const mcptype = getParams('mcptype') || '';
    if (mcpname && mcptype === 'edit') {
      const result = await request({ url: `v3/console/ai/mcp?mcpName=${mcpname}` });
      if (result.code === 0 && result.data) {
        const {
          description = '',
          name = '',
          version = '',
          protocol = '',
          localServerConfig = {},
          remoteServerConfig = {},
          credentials = {},
        } = result.data;

        const initFileData = {
          serverName: name,
          protocol,
          description: description,
          version: version,
          credentials: credentials,
        };

        if (localServerConfig && JSON.stringify(localServerConfig, null, 2) !== '{}') {
          initFileData['localServerConfig'] = JSON.stringify(localServerConfig, null, 2);
        }

        if (remoteServerConfig) {
          initFileData['exportPath'] = remoteServerConfig?.exportPath;
          initFileData['useExistService'] = remoteServerConfig?.serviceRef?.serviceName
            ? true
            : false;
          initFileData['namespace'] = remoteServerConfig?.serviceRef?.namespaceId;
          initFileData['service'] = remoteServerConfig?.serviceRef?.serviceName;

          // 通过 namespaceId 获取服务列表
          if (remoteServerConfig?.serviceRef?.namespaceId) {
            this.getServiceList(remoteServerConfig?.serviceRef?.namespaceId);
          }
        }

        this.field.setValues(initFileData);
        this.setState({
          serverConfig: result.data,
          useExistService: true, // 编辑时 默认使用已有服务，隐藏新建服务
        });
      }
    }
    this.getCredentials();
  };

  // 获取服务组列表
  getNamespaces = () => {
    request({
      type: 'get',
      url: 'v3/console/core/namespace/list',
      data: {
        pageNo: 1,
        pageSize: 1000,
      },
      success: res => {
        if (res.code == 0 && res.data) {
          this.setState({
            namespaceSelects: res.data?.map(item => ({
              ...item,
              label: item.namespaceShowName,
              value: item.namespace,
            })),
          });
        }
      },
    });
  };

  goList() {
    const { history = {} } = this.props;
    history && history.goBack();
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

  // 整合数据函数
  handleData = () => {
    const { serviceList = [], useExistService = true } = this.state;
    return new Promise((resolve, reject) => {
      this.field.validate((errors, values) => {
        if (errors) {
          return resolve({ errors });
        }
        const params = {
          mcpName: values?.serverName,
          serverSpecification: JSON.stringify(
            {
              protocol: values?.protocol,
              name: values?.serverName,
              description: values?.description || '',
              version: values?.version || '1.0.0',
              enabled: true,
              credentials: values?.credentials,
              localServerConfig: values?.localServerConfig
                ? JSON.parse(values?.localServerConfig)
                : '{}',
            },
            null,
            2
          ),
          toolSpecification: JSON.stringify(this.state?.serverConfig?.toolSpec || {}),
        };

        if (values?.protocol !== 'stdio') {
          // 获取服务组
          params.serverSpecification = JSON.stringify(
            {
              protocol: values?.protocol,
              name: values?.serverName,
              description: values?.description || '',
              version: values?.version || '1.0.0',
              enabled: true,
              remoteServerConfig: {
                exportPath: values?.exportPath || '',
              },
              credentials: values?.credentials,
            },
            null,
            2
          );
          // 添加服务
          const serverGroup = serviceList.find(item => item.value === values?.service);

          params.endpointSpecification = useExistService
            ? JSON.stringify(
                {
                  type: 'REF',
                  data: {
                    namespaceId: values?.namespace || '',
                    serviceName: values?.service || '',
                    groupName: serverGroup?.groupName || '',
                  },
                },
                null,
                2
              )
            : `{"type": "DIRECT","data":{"address":"${values?.address}","port": "${values?.port}"}}`;
        }

        resolve(params);
      });
    });
  };

  publishConfig = async () => {
    const params = await this.handleData();
    if (getParams('mcptype') === 'edit') {
      return this.editMcpServer(params);
    } else {
      return this.createMcpServer(params);
    }
  };

  // 新建服务
  createMcpServer = async params => {
    const { locale = {} } = this.props;
    this.openLoading();
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'post',
      data: params,
      error: err => {
        this.closeLoading();
      },
    });

    if (result.data === 'ok' || result.message === 'success') {
      Message.success({
        content: locale.publishSuccessfully,
        align: 'tc tc',
        animation: true,
        duration: 800,
        afterClose: () => {
          this.goList();
          this.closeLoading();
        },
      });
    }
  };

  // 编辑服务
  editMcpServer = async params => {
    const { locale = {} } = this.props;
    this.openLoading();
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'put',
      data: params,
      error: err => {
        this.closeLoading();
      },
    });

    if (result.data === 'ok' || result.message === 'success') {
      Message.success({
        content: locale.editSuccessfully,
        align: 'tc tc',
        animation: true,
        duration: 800,
        afterClose: () => {
          this.goList();
          this.closeLoading();
        },
      });
    }
  };

  validateChart(rule, value, callback) {
    const { locale = {} } = this.props;
    const chartReg = /[@#\$%\^&\*\s]+/g;

    if (chartReg.test(value)) {
      callback(locale.doNotEnter);
    } else {
      callback();
    }
  }

  getServiceList = async namespaceId => {
    const data = {
      ignoreEmptyService: false,
      withInstances: false,
      pageNo: 1,
      pageSize: 100,
      namespaceId: namespaceId,
    };
    const result = await request({
      url: 'v3/console/ns/service/list',
      method: 'get',
      data,
    });

    if (result.code === 0) {
      this.setState({
        serviceList: result.data.pageItems.map(item => ({
          label: `${item.groupName} / ${item.name}`,
          value: item.name,
          ...item,
        })),
      });
    } else {
      Message.error(result.message);
    }
  };
  handleNamespaceChange = value => {
    this.field.reset('service');
    this.getServiceList(value);
  };

  toolsChange = async (_toolSpec = {}, cb = () => {}) => {
    const { locale = {} } = this.props;
    // 更新 tools 之后, 立即调用接口全量覆盖。
    const validate = await this.handleData();
    if (!validate || validate?.errors) {
      // 请先完善基础配置
      cb && cb();
      return Message.warning(locale.pleaseComplete);
    }

    await new Promise(resolve => {
      this.setState(
        {
          serverConfig: {
            ...this.state?.serverConfig,
            toolSpec: {
              ...this.state?.serverConfig?.toolSpec,
              tools: _toolSpec?.tools || [],
              toolsMeta: _toolSpec?.toolsMeta || {},
            },
          },
        },
        resolve
      );
    });
    const params = await this.handleData();

    this.openLoading();
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'put',
      data: params,
      error: err => {
        this.closeLoading();
      },
    });
    this.closeLoading();

    if (result.data === 'ok' || result.message === 'success') {
      Message.success(locale.editSuccessfully);
    }
  };

  LocalServerConfigLabel = () => {
    const { locale = {} } = this.props;
    const trigger = (
      <Icon type="help" color="#333" size="small" style={{ marginLeft: 2, cursor: 'pointer' }} />
    );
    return (
      <span>
        {locale.localServerConfig}
        <Balloon.Tooltip
          v2
          triggerType="hover"
          trigger={trigger}
          align="rt"
          popupStyle={{ minWidth: 450 }}
        >
          <div>
            1. {locale.localServerTips1}{' '}
            <a href="https://github.com/nacos-group/nacos-mcp-router" target="_blank">
              nacos-mcp-router
            </a>{' '}
            {locale.localServerTips2}
          </div>
          <div style={{ margin: '10px 0' }}>2. {locale.localServerTips3}</div>
          <div>2. {locale.localServerTips4}</div>
        </Balloon.Tooltip>
      </span>
    );
  };

  handleCredentialChange = value => {
    const result = {};
    for (const credentialId in value) {
      const key = value[credentialId];
      result[key] = {
        ref: key,
      };
    }
    this.field.setValue('credentials', result);
  };

  getCredentials = () => {
    const self = this;
    const url = `v3/console/cs/config/list?dataId=&groupName=credentials`;
    request({
      url: url,
      type: 'get',
      data: {
        pageNo: 1,
        pageSize: 1000,
      },
      success(result) {
        if (result.code === 0) {
          self.setState({
            credentials: result.data.pageItems.map(item => ({
              label: item.dataId,
              value: item.dataId,
            })),
          });
        }
      },
    });
  };

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const isEdit = getParams('mcptype') && getParams('mcptype') === 'edit';
    const formItemLayout = { labelCol: { span: 3 }, wrapperCol: { span: 20 } };
    const textAreaProps = { 'aria-label': 'auto height', autoHeight: { minRows: 12, maxRows: 20 } };
    const descAreaProps = { 'aria-label': 'auto height', autoHeight: { minRows: 5, maxRows: 10 } };

    return (
      <Loading
        shape={'flower'}
        tip={'Loading...'}
        style={{ width: '100%', position: 'relative' }}
        visible={this.state.loading}
        color={'#333'}
      >
        <Row>
          <Col span={19}>
            <h1>
              {!getParams('mcptype') && locale.addNewMcpServer}
              {getParams('mcptype') && (isEdit ? locale.editService : locale.viewService)}
            </h1>
          </Col>
          <Col span={4}>
            <FormItem label=" ">
              <div style={{ textAlign: 'right' }}>
                <Button type={'primary'} style={{ marginRight: 10 }} onClick={this.publishConfig}>
                  {isEdit ? locale.updateExit : locale.escExit}
                </Button>

                <Button type={'normal'} onClick={this.goList.bind(this)}>
                  {locale.release}
                </Button>
              </div>
            </FormItem>
          </Col>
        </Row>
        <Form className="new-config-form" field={this.field} {...formItemLayout}>
          <Form.Item label={locale.namespace}>
            <p>{this.tenant ? this.tenant : McpServerManagementRouteName}</p>
          </Form.Item>
          <FormItem label={locale.serverName} required>
            <Input
              {...init('serverName', {
                rules: [
                  {
                    required: true,
                    message: locale.serverNameCannotBeEmpty,
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
          {/* 协议类型 */}
          <FormItem label={locale.serverType} help={isEdit ? null : locale.serverTypeDesc}>
            <RadioGroup
              {...init('protocol', {
                initValue: 'stdio',
                props: {
                  onChange: value => {
                    this.setState({
                      useExistService: ['mcp-sse', 'mcp-streamble'].includes(value) ? false : true,
                    });
                  },
                },
              })}
              isPreview={isEdit}
            >
              <Row>
                {['stdio', 'mcp-sse', 'mcp-streamble'].map(item => (
                  <Radio key={item} id={item} value={item}>
                    {item.charAt(0).toUpperCase() + item.slice(1)}
                  </Radio>
                ))}
              </Row>
              <Row>
                {['http', 'dubbo'].map(item => (
                  <Radio key={item} id={item} value={item}>
                    {item.charAt(0).toUpperCase() + item.slice(1)}
                  </Radio>
                ))}
              </Row>
            </RadioGroup>
          </FormItem>
          {this.field.getValue('protocol') !== 'stdio' ? (
            <>
              {/* 编辑时，隐藏 后端服务 表单项 */}
              {!isEdit && (
                <FormItem label={locale.backendService}>
                  <RadioGroup
                    value={this.state.useExistService ? 'useExistService' : 'useRemoteService'}
                    onChange={value => {
                      this.setState({
                        useExistService: value === 'useExistService' ? true : false,
                      });
                    }}
                  >
                    {// mcp-sse 和 mcp-streamble 不使用已有服务
                    !['mcp-sse', 'mcp-streamble'].includes(this.field.getValue('protocol')) && (
                      <Radio id="useExistService" value="useExistService">
                        {locale.useExistService}
                      </Radio>
                    )}
                    <Radio id="useRemoteService" value="useRemoteService">
                      {locale.useNewService}
                      {/* 新建服务 */}
                    </Radio>
                  </RadioGroup>
                </FormItem>
              )}
              {this.state.useExistService ? (
                <FormItem label={locale.serviceRef} required>
                  <Row gutter={8}>
                    <Col span={12}>
                      <FormItem label="namespace">
                        <Select
                          {...init('namespace', {
                            rules: [{ required: true, message: locale.placeSelect }],
                            props: {
                              placeholder: 'namespace',
                              dataSource: this.state.namespaceSelects,
                              onChange: this.handleNamespaceChange,
                              style: { width: '100%' },
                            },
                          })}
                        />
                      </FormItem>
                    </Col>
                    <Col span={12}>
                      <FormItem label="service">
                        <Select
                          {...init('service', {
                            rules: [{ required: true, message: locale.placeSelect }],
                            props: {
                              dataSource: this.state.serviceList,
                              style: { width: '100%' },
                              placeholder: 'service',
                            },
                          })}
                        />
                      </FormItem>
                    </Col>
                  </Row>
                </FormItem>
              ) : (
                <FormItem label={locale.useNewService} required>
                  <Row gutter={8}>
                    <Col span={12}>
                      <FormItem label="address">
                        <Input
                          {...init('address', {
                            rules: [{ required: true, message: locale.pleaseEnter }],
                          })}
                          style={{ width: '100%' }}
                        />
                      </FormItem>
                    </Col>
                    <Col span={4}>
                      <FormItem label="port">
                        <Input
                          {...init('port', {
                            rules: [{ required: true, message: locale.pleaseEnter }],
                          })}
                        />
                      </FormItem>
                    </Col>
                  </Row>
                </FormItem>
              )}
              {/* 暴露路径 */}
              <FormItem label={locale.exportPath} required help={locale.exportPathDesc}>
                <Input
                  {...init('exportPath', {
                    rules: [
                      {
                        required: true,
                        message: locale.pleaseEnter,
                      },
                    ],
                  })}
                  maxLength={255}
                  addonTextBefore={
                    this.state.addonBefore ? (
                      <div style={{ minWidth: 100, color: '#373D41' }}>
                        {this.state.addonBefore}
                      </div>
                    ) : null
                  }
                />
              </FormItem>
              <FormItem label={locale.CredentialRef}>
                <Select
                  mode="multiple"
                  showSearch
                  onChange={this.handleCredentialChange}
                  defaultValue={
                    this.field.getValue('credentials')
                      ? Object.keys(this.field.getValue('credentials'))
                      : []
                  }
                  dataSource={this.state.credentials}
                  style={{ width: '100%', marginRight: 8 }}
                />
              </FormItem>
            </>
          ) : (
            // Local Server 配置
            <>
              <FormItem label={this.LocalServerConfigLabel()} required>
                <Input.TextArea
                  {...init('localServerConfig', {
                    props: textAreaProps,
                    rules: [
                      {
                        required: true,
                        message: locale.pleaseEnter,
                      },
                      {
                        validator: (rule, value, callback) => {
                          try {
                            JSON.parse(value);
                            callback();
                          } catch (e) {
                            callback(locale.localServerConfigError);
                          }
                        },
                      },
                    ],
                  })}
                  placeholder={localServerConfigDesc}
                />
              </FormItem>
            </>
          )}
          <FormItem label={locale.description} required>
            <Input.TextArea
              {...init('description', {
                rules: [{ required: true, message: locale.pleaseEnter }],
                props: descAreaProps,
              })}
            />
          </FormItem>
          {/* 服务版本 */}
          <FormItem label={locale.serverVersion}>
            <Input {...init('version', { props: { placeholder: '1.0.0' } })} />
          </FormItem>

          {getParams('mcptype') && (
            <FormItem label={'Tools'} {...formItemLayout}>
              <ShowTools
                locale={locale}
                serverConfig={this.state.serverConfig}
                getServerDetail={this.initEditedData}
                onChange={this.toolsChange}
              />
            </FormItem>
          )}
        </Form>
      </Loading>
    );
  }
}

export default NewMcpServer;
