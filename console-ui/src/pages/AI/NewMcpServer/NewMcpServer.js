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
import MonacoEditor from '../../../components/MonacoEditor';
import { McpServerManagementRoute } from '../../../layouts/menu';
const { Row, Col } = Grid;

const FormItem = Form.Item;
const { Group: RadioGroup } = Radio;
const localServerConfigDesc = `{
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
    this.field = new Field(this, {
      parseName: true,
      values: {
        securitySchemes: [],
        localServerConfig: localServerConfigDesc,
      },
    });
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
      securitySchemeIdx: 0,
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

        // 初始化 securitySchemes 数据
        const securitySchemes = result.data?.toolSpec?.securitySchemes || [];
        const securitySchemesFormData = securitySchemes.map((scheme, index) => ({
          // 如果后端数据中有 id 则使用，否则生成一个用于表单管理
          id: scheme.id || `securityScheme_${index + 1}`,
          ...scheme,
        }));
        this.setState({
          securitySchemeIdx: securitySchemes.length,
        });
        this.field.setValues({
          ...this.field.getValues(),
          securitySchemes: securitySchemesFormData,
        });

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

        // 处理 securitySchemes 数据
        const formSecuritySchemes = values?.securitySchemes || [];
        const securitySchemes = formSecuritySchemes.map(scheme => {
          // 保留所有字段，包括 id
          return {
            id: scheme.id,
            type: scheme.type,
            scheme: scheme.scheme,
            in: scheme.in,
            name: scheme.name,
            defaultCredential: scheme.defaultCredential,
          };
        });

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
          toolSpecification: JSON.stringify(
            {
              ...this.state?.serverConfig?.toolSpec,
              securitySchemes: securitySchemes,
            } || {}
          ),
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
      console.log('input error');
      return;
    }

    const params = await this.handleData();
    if (params.errors) {
      console.log('handleData errors', params.errors);
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

  // 添加新的安全认证方案
  addNewSecurityScheme = () => {
    const { securitySchemeIdx } = this.state;
    const newIdx = securitySchemeIdx + 1;
    this.setState({
      securitySchemeIdx: newIdx,
    });
    this.field.addArrayValue('securitySchemes', newIdx, {
      id: `securityScheme_${newIdx}`,
      type: 'http',
      scheme: '',
      in: '',
      name: '',
      defaultCredential: '',
    });
    // 更新 serverConfig 以便 CreateTools 能够实时获取到最新的 securitySchemes
    setTimeout(() => {
      this.toolsChange();
    }, 100);
  };

  // 删除安全认证方案
  deleteSecurityScheme = index => {
    this.field.deleteArrayValue('securitySchemes', index);
    // 更新 serverConfig 以便 CreateTools 能够实时获取到最新的 securitySchemes
    setTimeout(() => {
      this.toolsChange();
    }, 100);
  };

  // 处理 securitySchemes 字段变化
  handleSecuritySchemeChange = () => {
    // 延迟执行，确保表单字段已经更新
    setTimeout(() => {
      this.toolsChange();
    }, 100);
  };

  toolsChange = async (_toolSpec = {}, cb = () => {}) => {
    const { locale = {} } = this.props;

    // 获取当前表单中的 securitySchemes 数据
    const formSecuritySchemes = this.field.getValue('securitySchemes') || [];
    const securitySchemes = formSecuritySchemes.map(scheme => {
      // 保留所有字段，包括 id
      return {
        id: scheme.id,
        type: scheme.type,
        scheme: scheme.scheme,
        in: scheme.in,
        name: scheme.name,
        defaultCredential: scheme.defaultCredential,
      };
    });

    await new Promise(resolve => {
      this.setState(
        {
          serverConfig: {
            ...this.state?.serverConfig,
            toolSpec: {
              ...this.state?.serverConfig?.toolSpec,
              tools: _toolSpec?.tools || [],
              toolsMeta: _toolSpec?.toolsMeta || {},
              securitySchemes: securitySchemes,
            },
          },
        },
        resolve
      );
    });
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
                      data === 'off' &&
                        this.setState({
                          useExistService: false,
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
              {/*{!isEdit && (*/}
              <FormItem label={locale.backendService}>
                <RadioGroup
                  disabled={currentVersionExist}
                  value={this.state.useExistService ? 'useExistService' : 'useRemoteService'}
                  onChange={value => {
                    this.setState({
                      useExistService: value === 'useExistService' ? true : false,
                    });
                  }}
                >
                  {// mcp-sse 和 mcp-streamable 不使用已有服务
                  (!['mcp-sse', 'mcp-streamable'].includes(this.field.getValue('frontProtocol')) ||
                    isEdit ||
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

              {/* Security Schemes 配置 - 只在 restToMcpSwitch 不为 off 时显示 */}
              {this.state.restToMcpSwitch !== 'off' && (
                <FormItem label={locale.securitySchemes || '安全认证方案'}>
                  <Button
                    type="primary"
                    size="small"
                    onClick={this.addNewSecurityScheme}
                    style={{ marginBottom: 10 }}
                  >
                    {locale.addSecurityScheme || '添加认证方案'}
                  </Button>

                  {this.field.getValue('securitySchemes') &&
                    this.field.getValue('securitySchemes').map((item, index) => (
                      <div
                        key={index}
                        style={{
                          border: '1px solid #ddd',
                          padding: '15px',
                          marginBottom: '10px',
                          borderRadius: '4px',
                        }}
                      >
                        <Row gutter={8}>
                          <Col span={6}>
                            <FormItem label={locale.schemeId || 'ID'} required>
                              <Input
                                {...init(`securitySchemes.${index}.id`, {
                                  rules: [{ required: true, message: locale.pleaseEnter }],
                                  props: {
                                    onChange: this.handleSecuritySchemeChange,
                                  },
                                })}
                                placeholder="unique-id"
                              />
                            </FormItem>
                          </Col>
                          <Col span={6}>
                            <FormItem label={locale.schemeType || '认证类型'} required>
                              <Select
                                {...init(`securitySchemes.${index}.type`, {
                                  initValue: 'http',
                                  rules: [{ required: true, message: locale.pleaseSelect }],
                                  props: {
                                    onChange: this.handleSecuritySchemeChange,
                                  },
                                })}
                                dataSource={[
                                  { label: 'HTTP (Basic/Bearer)', value: 'http' },
                                  { label: 'API Key', value: 'apiKey' },
                                ]}
                              />
                            </FormItem>
                          </Col>
                          <Col span={6}>
                            {this.field.getValue(`securitySchemes.${index}.type`) === 'http' ? (
                              <FormItem label={locale.scheme || 'Scheme'}>
                                <Select
                                  {...init(`securitySchemes.${index}.scheme`, {
                                    props: {
                                      onChange: this.handleSecuritySchemeChange,
                                    },
                                  })}
                                  dataSource={[
                                    { label: 'Basic', value: 'basic' },
                                    { label: 'Bearer', value: 'bearer' },
                                  ]}
                                  placeholder={locale.selectScheme || '选择认证方案'}
                                />
                              </FormItem>
                            ) : (
                              <FormItem label={locale.keyLocation || '密钥位置'}>
                                <Select
                                  {...init(`securitySchemes.${index}.in`, {
                                    props: {
                                      onChange: this.handleSecuritySchemeChange,
                                    },
                                  })}
                                  dataSource={[
                                    { label: 'Header', value: 'header' },
                                    { label: 'Query', value: 'query' },
                                  ]}
                                  placeholder="选择位置"
                                />
                              </FormItem>
                            )}
                          </Col>
                          <Col span={4}>
                            <FormItem label=" ">
                              <Button
                                type="normal"
                                warning
                                size="small"
                                onClick={() => this.deleteSecurityScheme(index)}
                              >
                                {locale.delete || '删除'}
                              </Button>
                            </FormItem>
                          </Col>
                        </Row>

                        <Row gutter={8}>
                          {this.field.getValue(`securitySchemes.${index}.type`) === 'apiKey' && (
                            <Col span={12}>
                              <FormItem label={locale.keyName || '密钥名称'}>
                                <Input
                                  {...init(`securitySchemes.${index}.name`, {
                                    props: {
                                      onChange: this.handleSecuritySchemeChange,
                                    },
                                  })}
                                  placeholder="X-API-Key"
                                />
                              </FormItem>
                            </Col>
                          )}
                          <Col span={12}>
                            <FormItem label={locale.defaultCredential || '默认凭证'}>
                              <Input
                                {...init(`securitySchemes.${index}.defaultCredential`, {
                                  props: {
                                    onChange: this.handleSecuritySchemeChange,
                                  },
                                })}
                                placeholder="默认凭证值"
                              />
                            </FormItem>
                          </Col>
                        </Row>
                      </div>
                    ))}
                </FormItem>
              )}
            </>
          ) : (
            // Local Server 配置
            <>
              <FormItem label={this.LocalServerConfigLabel()} required>
                {currentVersionExist ? (
                  // 预览模式使用格式化的 pre 标签
                  <pre
                    style={{
                      backgroundColor: '#f6f7f9',
                      border: '1px solid #dcdee3',
                      borderRadius: '4px',
                      padding: '8px 12px',
                      fontSize: '12px',
                      fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                      lineHeight: '1.5',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      maxHeight: '400px',
                      overflow: 'auto',
                      margin: 0,
                    }}
                  >
                    {(() => {
                      try {
                        const configValue = this.field.getValue('localServerConfig');
                        return configValue
                          ? JSON.stringify(JSON.parse(configValue), null, 2)
                          : configValue;
                      } catch (error) {
                        return this.field.getValue('localServerConfig');
                      }
                    })()}
                  </pre>
                ) : (
                  // 编辑模式使用 Monaco Editor
                  <MonacoEditor
                    language="json"
                    height="300px"
                    value={this.field.getValue('localServerConfig') || localServerConfigDesc}
                    onChange={value => {
                      this.field.setValue('localServerConfig', value);
                      // 执行验证逻辑
                      try {
                        if (value?.length > 0) {
                          const parsedValue = JSON.parse(value);
                          if (parsedValue['description']) {
                            this.field.setValue('description', parsedValue['description']);
                          }
                        }
                        this.setState({
                          isInputError: false,
                        });
                      } catch (e) {
                        this.setState({
                          isInputError: true,
                        });
                      }
                    }}
                    options={{
                      minimap: { enabled: false },
                      scrollBeyondLastLine: false,
                      fontSize: 12,
                      tabSize: 2,
                      insertSpaces: true,
                      wordWrap: 'on',
                      lineNumbers: 'on',
                      formatOnPaste: true,
                      formatOnType: true,
                    }}
                  />
                )}
                {/* 隐藏的表单字段，用于验证 */}
                <div style={{ display: 'none' }}>
                  <Input.TextArea
                    {...init('localServerConfig', {
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
                  />
                </div>
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
              {...init('version', {
                props: { placeholder: 'e.g. 1.0.0' },
                rules: [{ required: true }],
              })}
            />
            {currentVersionExist && (
              <>
                <p style={{ color: 'red' }}>{locale.editExistVersionMessage}</p>
                <p style={{ color: 'red' }}>{locale.editMoreNeedNewVersion}</p>
              </>
            )}
          </FormItem>

          <FormItem label={'Tools'} {...formItemLayout}>
            <ShowTools
              locale={locale}
              restToMcpSwitch={this.state.restToMcpSwitch}
              frontProtocol={this.field.getValue('frontProtocol')}
              address={this.field.getValue('address')}
              port={this.field.getValue('port')}
              useExistService={this.state.useExistService}
              service={this.field.getValue('service')}
              exportPath={this.field.getValue('exportPath')}
              serverConfig={this.state.serverConfig}
              getServerDetail={this.initEditedData}
              onChange={this.toolsChange}
              onlyEditRuntimeInfo={currentVersionExist}
            />
          </FormItem>
        </Form>
      </Loading>
    );
  }
}

export default NewMcpServer;
