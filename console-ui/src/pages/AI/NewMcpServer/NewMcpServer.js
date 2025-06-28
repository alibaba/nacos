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
  ConfigProvider,
  Switch,
} from '@alifd/next';
import ShowTools from '../McpDetail/ShowTools';
import { McpServerManagementRoute } from '../../../layouts/menu';
const { Row, Col } = Grid;

const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;
const localServerConfigDesc = `示例：
{
    "mcpServers":
    {
        "amap-mcp-server":
        {
            "description": "高德地图服务",
            "command": "npx",
            "args":
            [
                "-y",
                "@amap/amap-maps-mcp-server"
            ],
            "env":
            {
                "AMAP_MAPS_API_KEY": "<API_KEY>"
            }
        }
    }
}
`;

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
      restToMcpSwitch: 'http',
      currentVersion: '',
      isLatestVersion: false,
      versionsList: [],
      isInputError: false,
    };
  }

  componentDidMount() {
    if (!getParams('namespace')) {
      this.props?.history?.push({
        pathname: McpServerManagementRoute,
      });
    }
    this.initEditedData();
  }

  // 编辑数据 初始化
  initEditedData = async () => {
    const mcpServerId = getParams('id') || '';
    const version = getParams('version') || '';
    const mcptype = getParams('mcptype') || '';
    const namespace = getParams('namespace') || '';
    this.getServiceList(namespace);
    if (mcpServerId && mcptype === 'edit') {
      let url = `v3/console/ai/mcp?mcpId=${mcpServerId}`;
      if (version !== '') {
        url += `&version=${version}`;
      }
      const result = await request({ url: url });
      if (result.code === 0 && result.data) {
        const {
          description = '',
          name = '',
          versionDetail = '',
          protocol = '',
          frontProtocol = '',
          localServerConfig = {},
          remoteServerConfig = {},
          allVersions = [],
        } = result.data;

        const initFileData = {
          serverName: name,
          protocol,
          frontProtocol,
          description: description,
          version: versionDetail.version,
        };

        const allPublishedVersions = [];
        for (let i = 0; i < allVersions.length; i++) {
          if (i === allVersions.length - 1 && !allVersions[i].is_latest) {
            break;
          }
          allPublishedVersions.push(allVersions[i].version);
        }

        this.setState({
          currentVersion: versionDetail.version,
          isLatestVersion: versionDetail.is_latest,
          versionsList: allPublishedVersions,
        });

        initFileData['localServerConfig'] = JSON.stringify(localServerConfig, null, 2);

        if (remoteServerConfig) {
          initFileData['exportPath'] = remoteServerConfig?.exportPath;
          initFileData['useExistService'] = remoteServerConfig?.serviceRef?.serviceName
            ? true
            : false;
          initFileData['namespace'] = remoteServerConfig?.serviceRef?.namespaceId;
          initFileData['service'] =
            remoteServerConfig?.serviceRef?.groupName +
            '@@' +
            remoteServerConfig?.serviceRef?.serviceName;
        }

        this.field.setValues(initFileData);

        let restToMcpBackendProtocol = 'off';
        if (protocol === 'https' || protocol === 'http') {
          restToMcpBackendProtocol = protocol;
        }

        this.setState({
          serverConfig: result.data,
          useExistService: true, // 编辑时 默认使用已有服务，隐藏新建服务
          restToMcpSwitch: restToMcpBackendProtocol,
        });
      }
    }
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
          this.setState({
            isInputError: true,
          });
          return resolve({ errors });
        }

        let protocol =
          this.state.restToMcpSwitch === 'off' ? values.frontProtocol : this.state.restToMcpSwitch;
        if (values.frontProtocol === 'stdio') {
          protocol = values.frontProtocol;
        }
        const mcpServerId = getParams('id') || '';
        const params = {
          id: mcpServerId,
          serverSpecification: JSON.stringify(
            {
              protocol: protocol,
              frontProtocol: values?.frontProtocol,
              name: values?.serverName,
              id: mcpServerId,
              description: values?.description || '',
              versionDetail: {
                version: values?.version || '1.0.0',
              },
              enabled: true,
              localServerConfig: values?.localServerConfig
                ? JSON.parse(values?.localServerConfig)
                : '{}',
            },
            null,
            2
          ),
          toolSpecification: JSON.stringify(this.state?.serverConfig?.toolSpec || {}),
        };

        if (values?.frontProtocol !== 'stdio') {
          // 获取服务组
          params.serverSpecification = JSON.stringify(
            {
              protocol: protocol,
              frontProtocol: values?.frontProtocol,
              name: values?.serverName,
              id: mcpServerId,
              description: values?.description || '',
              versionDetail: {
                version: values?.version || '1.0.0',
              },
              enabled: true,
              remoteServerConfig: {
                exportPath: values?.exportPath || '',
              },
            },
            null,
            2
          );
          // 添加服务

          if (useExistService) {
            const group = values?.service.split('@@')[0];
            const serviceName = values?.service.split('@@')[1];
            params.endpointSpecification = JSON.stringify(
              {
                type: 'REF',
                data: {
                  namespaceId: values?.namespace || '',
                  serviceName: serviceName || '',
                  groupName: group || '',
                },
              },
              null,
              2
            );
          } else {
            params.endpointSpecification = `{"type": "DIRECT","data":{"address":"${values?.address}","port": "${values?.port}"}}`;
          }
        }

        resolve(params);
      });
    });
  };

  publishConfig = async isPublish => {
    if (this.state.isInputError) {
      return;
    }

    const params = await this.handleData();
    if (params.errors) {
      return;
    }

    params['latest'] = isPublish;
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
    const chartReg = /^[a-zA-Z0-9_-]+$/;

    if (!chartReg.test(value)) {
      callback(locale.doNotEnter);
      this.setState({
        isInputError: true,
      });
    } else {
      callback();
      this.setState({
        isInputError: false,
      });
    }
  }
  //
  // validateVersion = (rule, value, callback) => {
  //   const { locale = {} } = this.props;
  //   const versionReg = /^[0-9]+\.[0-9]+\.[0-9]+$/;
  //   if (!versionReg.test(value)) {
  //     callback(locale.versionFormatError);
  //     this.setState({
  //       isInputError: true,
  //     })
  //   } else if (this.state.versionsList.includes(value)) {
  //     callback(locale.cannotUseExistVersion);
  //     this.setState({
  //       isInputError: true,
  //     })
  //   } else {
  //     callback()
  //     this.setState({
  //       isInputError: false,
  //     })
  //   }
  // }

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
          value: item.groupName + '@@' + item.name,
          ...item,
        })),
      });
    } else {
      Message.error(result.message);
    }
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
    params['latest'] = false;
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

  render() {
    const { locale = {} } = this.props;
    const { init } = this.field;
    const isEdit = getParams('mcptype') && getParams('mcptype') === 'edit';
    const formItemLayout = { labelCol: { span: 3 }, wrapperCol: { span: 20 } };
    const textAreaProps = { 'aria-label': 'auto height', autoHeight: { minRows: 20, maxRows: 50 } };
    const descAreaProps = { 'aria-label': 'auto height', autoHeight: { minRows: 5, maxRows: 10 } };
    const currentNamespace = getParams('namespace');

    const versions = this.state.serverConfig?.allVersions
      ? this.state.serverConfig?.allVersions
      : [];

    let hasDraftVersion = false;
    if (versions.length > 0) {
      hasDraftVersion = !versions[versions.length - 1].is_latest;
    }

    let currentVersionExist = versions
      .map(item => item.version)
      .includes(this.field.getValue('version'));

    return (
      <Loading
        shape={'flower'}
        tip={'Loading...'}
        style={{ width: '100%', position: 'relative' }}
        visible={this.state.loading}
        color={'#333'}
      >
        <Row>
          <Col span={16}>
            <h1>
              {!getParams('mcptype') && locale.addNewMcpServer}
              {getParams('mcptype') && (isEdit ? locale.editService : locale.viewService)}
            </h1>
          </Col>
          <Col span={8}>
            <FormItem label=" ">
              <div style={{ textAlign: 'right' }}>
                {isEdit ? (
                  <>
                    <Button
                      type="primary"
                      onClick={() => {
                        this.publishConfig(false);
                      }}
                      style={{ marginRight: 10 }}
                    >
                      {locale.createNewVersionAndSave}
                    </Button>

                    <Button
                      type="primary"
                      onClick={() => {
                        this.publishConfig(true);
                      }}
                      style={{ marginRight: 10 }}
                    >
                      {locale.createNewVersionAndPublish}
                    </Button>
                  </>
                ) : (
                  <Button
                    type={'primary'}
                    style={{ marginRight: 10 }}
                    onClick={() => {
                      this.publishConfig(true);
                    }}
                  >
                    {locale.escExit}
                  </Button>
                )}
                <Button type={'normal'} onClick={this.goList.bind(this)}>
                  {locale.release}
                </Button>
              </div>
            </FormItem>
          </Col>
        </Row>
        <Form className="new-config-form" field={this.field} {...formItemLayout}>
          <Form.Item label={locale.namespace}>
            <p>{this.tenant ? this.tenant : 'public'}</p>
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
          <FormItem label={locale.serverType} required>
            <RadioGroup
              {...init('frontProtocol', {
                initValue: 'stdio',
                props: {
                  onChange: value => {
                    this.setState({
                      useExistService: ['mcp-sse', 'mcp-streamable'].includes(value) ? false : true,
                    });
                  },
                },
              })}
              disabled={isEdit}
            >
              <Row>
                <Radio key={'stdio'} id={'stdio'} value={'stdio'}>
                  stdio
                </Radio>
                <Radio key={'mcp-sse'} id={'mcp-sse'} value={'mcp-sse'}>
                  sse
                </Radio>
                <Radio key={'mcp-streamable'} id={'mcp-streamable'} value={'mcp-streamable'}>
                  streamable
                </Radio>
              </Row>
            </RadioGroup>
          </FormItem>
          {this.field.getValue('frontProtocol') !== 'stdio' ? (
            <>
              {/* 编辑时，隐藏 后端服务 表单项 */}
              <FormItem
                label={locale.openConverter}
                required
                help={<>{locale.restToMcpNeedHigress}</>}
              >
                <Row>
                  <RadioGroup
                    disabled={isEdit}
                    value={this.state.restToMcpSwitch}
                    onChange={data => {
                      this.setState({
                        restToMcpSwitch: data,
                      });
                    }}
                  >
                    <Radio id={'off'} value={'off'}>
                      {locale.off}
                    </Radio>
                    <Radio id={'http'} value={'http'}>
                      http
                    </Radio>
                    <Radio id={'https'} value={'https'}>
                      https
                    </Radio>
                  </RadioGroup>
                </Row>
              </FormItem>
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
                    {// mcp-sse 和 mcp-streamable 不使用已有服务
                    (!['mcp-sse', 'mcp-streamable'].includes(
                      this.field.getValue('frontProtocol')
                    ) ||
                      this.state.restToMcpSwitch !== 'off') && (
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
                    <Col span={4}>
                      <FormItem label="namespace">
                        <p>{currentNamespace}</p>
                      </FormItem>
                    </Col>
                    <Col span={12}>
                      <FormItem label="service">
                        <Select
                          isPreview={currentVersionExist}
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
                <FormItem label={locale.useNewService} required disabled={currentVersionExist}>
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
              {this.state.restToMcpSwitch === 'off' && (
                <FormItem label={locale.exportPath} required help={locale.exportPathDesc}>
                  <Input
                    isPreview={currentVersionExist}
                    placeholder={locale.exportPathEg}
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
              )}
            </>
          ) : (
            // Local Server 配置
            <>
              <FormItem label={this.LocalServerConfigLabel()} required>
                <Input.TextArea
                  isPreview={currentVersionExist}
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
                            if (value?.length > 0) {
                              const parsedValue = JSON.parse(value);
                              if (parsedValue['description']) {
                                this.field.setValue('description', parsedValue['description']);
                              }
                            }
                            callback();
                            this.setState({
                              isInputError: false,
                            });
                          } catch (e) {
                            callback(locale.localServerConfigError);
                            this.setState({
                              isInputError: true,
                            });
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
              isPreview={currentVersionExist}
              {...init('description', {
                rules: [{ required: true, message: locale.pleaseEnter }],
                props: descAreaProps,
              })}
            />
          </FormItem>
          {/* 服务版本 */}
          <FormItem label={locale.serverVersion} required>
            <Input
              {...init('version', { props: { placeholder: 'e.g. 1.0.0' }, rules: [{ required: true }] })}
            />
            {currentVersionExist && (
              <>
                <p style={{ color: 'red' }}>{locale.editExistVersionMessage}</p>
                <p style={{ color: 'red' }}>{locale.editMoreNeedNewVersion}</p>
              </>
            )}
          </FormItem>

          {getParams('mcptype') && (
            <FormItem label={'Tools'} {...formItemLayout}>
              <ShowTools
                locale={locale}
                restToMcpSwitch={this.state.restToMcpSwitch}
                serverConfig={this.state.serverConfig}
                getServerDetail={this.initEditedData}
                onChange={this.toolsChange}
                onlyEditRuntimeInfo={currentVersionExist}
              />
            </FormItem>
          )}
        </Form>
      </Loading>
    );
  }
}

export default NewMcpServer;
