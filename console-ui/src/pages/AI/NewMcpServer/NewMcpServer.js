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
        packages: [],
        headers: [], // 初始化headers数组
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
      configMode: 'localConfig', // 'localConfig' 或 'packageConfig'
      packageIdx: 0,
      expandedPackages: {}, // 记录每个包的展开状态
      headerIdx: 0, // Headers 索引
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

        // 初始化 packages 数据
        const packages = result.data?.packages || [];
        const packagesFormData = packages.map((pkg, index) => ({
          id: pkg.id || `package_${index + 1}`,
          ...pkg,
        }));
        this.setState({
          packageIdx: packages.length,
          configMode: packages.length > 0 ? 'packageConfig' : 'localConfig',
        });
        this.field.setValues({
          ...this.field.getValues(),
          packages: packagesFormData,
        });

        // 初始化 headers 数据
        const headers = result.data?.toolSpec?.headers || [];
        const headersFormData = headers.map((header, index) => ({
          id: header.id || `header_${index + 1}`,
          name: header.name || '',
          value: header.value || '',
          default: header.default || '',
          description: header.description || '',
          is_required: header.is_required || false,
          is_secret: header.is_secret || false,
          format: header.format || 'string',
          choices: header.choices || [],
        }));
        this.setState({
          headerIdx: headers.length,
        });
        this.field.setValues({
          ...this.field.getValues(),
          headers: headersFormData,
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

        // 处理 packages 数据
        const formPackages = values?.packages || [];
        const packages = formPackages.map(pkg => {
          return {
            id: pkg.id,
            name: pkg.name,
            version: pkg.version,
            registry_name: pkg.registry_name,
            description: pkg.description,
            runtime_hint: pkg.runtime_hint,
            runtime_arguments: pkg.runtime_arguments || [],
            package_arguments: pkg.package_arguments || [],
            environment_variables: pkg.environment_variables || [],
          };
        });

        // 处理 headers 数据
        const formHeaders = values?.headers || [];
        const headers = formHeaders.map(header => {
          return {
            id: header.id,
            name: header.name,
            value: header.value,
            default: header.default,
            description: header.description,
            is_required: header.is_required,
            is_secret: header.is_secret,
            format: header.format,
            choices: header.choices || [],
          };
        });

        // 构建服务器规范，根据配置模式选择不同的配置
        let serverSpec = {
          protocol: protocol,
          frontProtocol: values?.frontProtocol,
          name: values?.serverName,
          id: mcpServerId,
          description: values?.description || '',
          versionDetail: {
            version: values?.version || '1.0.0',
          },
          enabled: true,
        };

        // 根据配置模式添加不同的配置
        if (this.state.configMode === 'packageConfig' && packages.length > 0) {
          serverSpec.packages = packages;
        } else {
          serverSpec.localServerConfig = values?.localServerConfig
            ? JSON.parse(values?.localServerConfig)
            : '{}';
        }

        const params = {
          id: mcpServerId,
          serverSpecification: JSON.stringify(serverSpec, null, 2),
          toolSpecification: JSON.stringify(
            {
              ...this.state?.serverConfig?.toolSpec,
              securitySchemes: securitySchemes,
              headers: headers,
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

  // 添加新的 Package
  addNewPackage = () => {
    const { packageIdx } = this.state;
    const newIdx = packageIdx + 1;
    this.setState({
      packageIdx: newIdx,
    });
    this.field.addArrayValue('packages', newIdx, {
      id: `package_${newIdx}`,
      name: '',
      version: 'latest',
      registry_name: 'npm',
      description: '',
      runtime_hint: '',
      runtime_arguments: [],
      package_arguments: [],
      environment_variables: [],
    });
  };

  // 删除 Package
  deletePackage = index => {
    this.field.deleteArrayValue('packages', index);
  };

  // 处理 packages 字段变化
  handlePackageChange = () => {
    // 延迟执行，确保表单字段已经更新
    setTimeout(() => {
      this.toolsChange();
    }, 100);
  };

  // 添加运行时参数
  addRuntimeArgument = packageIndex => {
    const currentArgs = this.field.getValue(`packages.${packageIndex}.runtime_arguments`) || [];
    const newArgs = [...currentArgs, { type: 'positional', value: '', description: '' }];
    this.field.setValue(`packages.${packageIndex}.runtime_arguments`, newArgs);
    this.handlePackageChange();
  };

  // 删除运行时参数
  deleteRuntimeArgument = (packageIndex, argIndex) => {
    const currentArgs = this.field.getValue(`packages.${packageIndex}.runtime_arguments`) || [];
    const newArgs = currentArgs.filter((_, index) => index !== argIndex);
    this.field.setValue(`packages.${packageIndex}.runtime_arguments`, newArgs);
    this.handlePackageChange();
  };

  // 添加包参数
  addPackageArgument = packageIndex => {
    const currentArgs = this.field.getValue(`packages.${packageIndex}.package_arguments`) || [];
    const newArgs = [...currentArgs, { type: 'positional', value: '', description: '' }];
    this.field.setValue(`packages.${packageIndex}.package_arguments`, newArgs);
    this.handlePackageChange();
  };

  // 删除包参数
  deletePackageArgument = (packageIndex, argIndex) => {
    const currentArgs = this.field.getValue(`packages.${packageIndex}.package_arguments`) || [];
    const newArgs = currentArgs.filter((_, index) => index !== argIndex);
    this.field.setValue(`packages.${packageIndex}.package_arguments`, newArgs);
    this.handlePackageChange();
  };

  // 添加环境变量
  addEnvironmentVariable = packageIndex => {
    const currentVars = this.field.getValue(`packages.${packageIndex}.environment_variables`) || [];
    const newVars = [
      ...currentVars,
      { name: '', value: '', description: '', is_required: false, is_secret: false },
    ];
    this.field.setValue(`packages.${packageIndex}.environment_variables`, newVars);
    this.handlePackageChange();
  };

  // 删除环境变量
  deleteEnvironmentVariable = (packageIndex, varIndex) => {
    const currentVars = this.field.getValue(`packages.${packageIndex}.environment_variables`) || [];
    const newVars = currentVars.filter((_, index) => index !== varIndex);
    this.field.setValue(`packages.${packageIndex}.environment_variables`, newVars);
    this.handlePackageChange();
  };

  // 切换包的展开状态
  togglePackageExpansion = packageIndex => {
    this.setState(prevState => ({
      expandedPackages: {
        ...prevState.expandedPackages,
        [packageIndex]: !prevState.expandedPackages[packageIndex],
      },
    }));
  };

  // 添加新的 Header
  addNewHeader = () => {
    const { headerIdx } = this.state;
    const newIdx = headerIdx + 1;
    this.setState({
      headerIdx: newIdx,
    });
    this.field.addArrayValue('headers', newIdx, {
      id: `header_${newIdx}`,
      name: '',
      value: '',
      default: '',
      description: '',
      is_required: false,
      is_secret: false,
      format: 'string',
      choices: [],
    });
    // 更新 serverConfig 以便 CreateTools 能够实时获取到最新的 headers
    setTimeout(() => {
      this.toolsChange();
    }, 100);
  };

  // 删除 Header
  deleteHeader = index => {
    this.field.deleteArrayValue('headers', index);
    // 更新 serverConfig 以便 CreateTools 能够实时获取到最新的 headers
    setTimeout(() => {
      this.toolsChange();
    }, 100);
  };

  // 处理 headers 字段变化
  handleHeaderChange = () => {
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

    // 获取当前表单中的 packages 数据
    const formPackages = this.field.getValue('packages') || [];
    const packages = formPackages.map(pkg => {
      return {
        id: pkg.id,
        name: pkg.name,
        version: pkg.version,
        registry_name: pkg.registry_name,
        description: pkg.description,
        runtime_hint: pkg.runtime_hint,
        runtime_arguments: pkg.runtime_arguments || [],
        package_arguments: pkg.package_arguments || [],
        environment_variables: pkg.environment_variables || [],
      };
    });

    // 获取当前表单中的 headers 数据
    const formHeaders = this.field.getValue('headers') || [];
    const headers = formHeaders.map(header => {
      return {
        id: header.id,
        name: header.name,
        value: header.value,
        default: header.default,
        description: header.description,
        is_required: header.is_required,
        is_secret: header.is_secret,
        format: header.format,
        choices: header.choices || [],
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
            packages: packages, // 添加 packages 数据
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

              {/* Headers 配置 - 只在非 stdio 协议时显示 */}
              <FormItem label={locale.headers || 'HTTP Headers'}>
                <Button
                  type="primary"
                  size="small"
                  onClick={this.addNewHeader}
                  style={{ marginBottom: 10 }}
                  disabled={currentVersionExist}
                >
                  {locale.addHeader || '添加 Header'}
                </Button>

                {this.field.getValue('headers') &&
                  this.field.getValue('headers').map((item, index) => (
                    <div
                      key={index}
                      style={{
                        border: '1px solid #ddd',
                        padding: '15px',
                        marginBottom: '10px',
                        borderRadius: '4px',
                        backgroundColor: '#fafafa',
                      }}
                    >
                      <Row gutter={8} style={{ marginBottom: '10px' }}>
                        <Col span={6}>
                          <FormItem label={locale.headerName || 'Header 名称'} required>
                            <Input
                              {...init(`headers.${index}.name`, {
                                rules: [{ required: true, message: locale.pleaseEnter }],
                                props: {
                                  onChange: this.handleHeaderChange,
                                },
                              })}
                              placeholder="Content-Type, Authorization, etc."
                              disabled={currentVersionExist}
                            />
                          </FormItem>
                        </Col>
                        <Col span={6}>
                          <FormItem label={locale.format || '格式类型'}>
                            <Select
                              {...init(`headers.${index}.format`, {
                                initValue: 'string',
                                props: {
                                  onChange: this.handleHeaderChange,
                                },
                              })}
                              dataSource={[
                                { label: 'String', value: 'string' },
                                { label: 'Number', value: 'number' },
                                { label: 'Boolean', value: 'boolean' },
                                { label: 'File Path', value: 'filepath' },
                              ]}
                              disabled={currentVersionExist}
                            />
                          </FormItem>
                        </Col>
                        <Col span={8}>
                          <FormItem label={locale.headerValue || 'Header 值'}>
                            <Input
                              {...init(`headers.${index}.value`, {
                                props: {
                                  onChange: this.handleHeaderChange,
                                },
                              })}
                              placeholder="如: application/json, Bearer {token}"
                              disabled={currentVersionExist}
                            />
                          </FormItem>
                        </Col>
                        <Col span={4}>
                          <FormItem label=" ">
                            <Button
                              type="normal"
                              warning
                              size="small"
                              onClick={() => this.deleteHeader(index)}
                              disabled={currentVersionExist}
                            >
                              {locale.delete || '删除'}
                            </Button>
                          </FormItem>
                        </Col>
                      </Row>

                      <Row gutter={8} style={{ marginBottom: '10px' }}>
                        <Col span={12}>
                          <FormItem label={locale.defaultValue || '默认值'}>
                            <Input
                              {...init(`headers.${index}.default`, {
                                props: {
                                  onChange: this.handleHeaderChange,
                                },
                              })}
                              placeholder="默认值"
                              disabled={currentVersionExist}
                            />
                          </FormItem>
                        </Col>
                        <Col span={12}>
                          <FormItem label={locale.headerDescription || 'Header 描述'}>
                            <Input
                              {...init(`headers.${index}.description`, {
                                props: {
                                  onChange: this.handleHeaderChange,
                                },
                              })}
                              placeholder="Header 用途说明"
                              disabled={currentVersionExist}
                            />
                          </FormItem>
                        </Col>
                      </Row>

                      <Row gutter={8} style={{ marginBottom: '10px' }}>
                        <Col span={6}>
                          <FormItem label=" ">
                            <label style={{ fontSize: '12px' }}>
                              <input
                                type="checkbox"
                                checked={
                                  this.field.getValue(`headers.${index}.is_required`) || false
                                }
                                onChange={e => {
                                  this.field.setValue(
                                    `headers.${index}.is_required`,
                                    e.target.checked
                                  );
                                  this.handleHeaderChange();
                                }}
                                disabled={currentVersionExist}
                                style={{ marginRight: '4px' }}
                              />
                              必填
                            </label>
                          </FormItem>
                        </Col>
                        <Col span={6}>
                          <FormItem label=" ">
                            <label style={{ fontSize: '12px' }}>
                              <input
                                type="checkbox"
                                checked={this.field.getValue(`headers.${index}.is_secret`) || false}
                                onChange={e => {
                                  this.field.setValue(
                                    `headers.${index}.is_secret`,
                                    e.target.checked
                                  );
                                  this.handleHeaderChange();
                                }}
                                disabled={currentVersionExist}
                                style={{ marginRight: '4px' }}
                              />
                              敏感信息
                            </label>
                          </FormItem>
                        </Col>
                        <Col span={12}>
                          <FormItem label={locale.choices || '可选值'} help="多个值用逗号分隔">
                            <Input
                              value={
                                this.field.getValue(`headers.${index}.choices`)?.join(',') || ''
                              }
                              onChange={value => {
                                const choices = value
                                  ? value
                                      .split(',')
                                      .map(v => v.trim())
                                      .filter(v => v)
                                  : [];
                                this.field.setValue(`headers.${index}.choices`, choices);
                                this.handleHeaderChange();
                              }}
                              placeholder="如: application/json, text/plain"
                              disabled={currentVersionExist}
                            />
                          </FormItem>
                        </Col>
                      </Row>
                    </div>
                  ))}
              </FormItem>
            </>
          ) : (
            // Local Server 配置
            <>
              {/* 配置模式选择 */}
              <FormItem label={locale.configMode || '配置模式'} required>
                <RadioGroup
                  value={this.state.configMode}
                  onChange={value => {
                    this.setState({ configMode: value });
                    // 触发验证，确保切换模式时验证规则正确应用
                    setTimeout(() => {
                      this.field.validate(['localServerConfig', 'packagesValidation']);
                    }, 100);
                  }}
                  disabled={currentVersionExist}
                >
                  <Radio id="localConfig" value="localConfig">
                    {locale.localServerConfig || 'Local Server Config'}
                  </Radio>
                  <Radio id="packageConfig" value="packageConfig">
                    {locale.packageConfig || 'Package Config'}
                  </Radio>
                </RadioGroup>
              </FormItem>

              {/* Local Server Config 配置 */}
              {this.state.configMode === 'localConfig' && (
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
                            required: this.state.configMode === 'localConfig',
                            message: locale.pleaseEnter,
                          },
                          {
                            validator: (rule, value, callback) => {
                              if (this.state.configMode !== 'localConfig') {
                                callback();
                                return;
                              }
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
              )}

              {/* Package Config 配置 */}
              {this.state.configMode === 'packageConfig' && (
                <FormItem label={locale.packageConfig || 'Package Config'}>
                  <Button
                    type="primary"
                    size="small"
                    onClick={this.addNewPackage}
                    style={{ marginBottom: 10 }}
                    disabled={currentVersionExist}
                  >
                    {locale.addPackage || '添加 Package'}
                  </Button>

                  {this.field.getValue('packages') &&
                    this.field.getValue('packages').map((item, index) => (
                      <div
                        key={index}
                        style={{
                          border: '1px solid #ddd',
                          padding: '15px',
                          marginBottom: '15px',
                          borderRadius: '4px',
                          backgroundColor: '#fafafa',
                        }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginBottom: '15px',
                          }}
                        >
                          <h4 style={{ margin: 0, color: '#333' }}>
                            Package {index + 1}: {item.name || '未命名'}
                          </h4>
                          <div>
                            <Button
                              type="normal"
                              size="small"
                              onClick={() => this.togglePackageExpansion(index)}
                              style={{ marginRight: '8px' }}
                            >
                              {this.state.expandedPackages[index] ? '收起参数' : '展开参数'}
                            </Button>
                            <Button
                              type="normal"
                              warning
                              size="small"
                              onClick={() => this.deletePackage(index)}
                              disabled={currentVersionExist}
                            >
                              {locale.delete || '删除'}
                            </Button>
                          </div>
                        </div>

                        <Row gutter={8}>
                          <Col span={8}>
                            <FormItem label={locale.packageName || '包名'} required>
                              <Input
                                {...init(`packages.${index}.name`, {
                                  rules: [{ required: true, message: locale.pleaseEnter }],
                                  props: {
                                    onChange: this.handlePackageChange,
                                  },
                                })}
                                placeholder="@scope/package-name"
                                disabled={currentVersionExist}
                              />
                            </FormItem>
                          </Col>
                          <Col span={4}>
                            <FormItem label={locale.version || '版本'}>
                              <Input
                                {...init(`packages.${index}.version`, {
                                  initValue: 'latest',
                                  props: {
                                    onChange: this.handlePackageChange,
                                  },
                                })}
                                placeholder="latest"
                                disabled={currentVersionExist}
                              />
                            </FormItem>
                          </Col>
                          <Col span={4}>
                            <FormItem label={locale.registryType || '注册表类型'}>
                              <Select
                                {...init(`packages.${index}.registry_name`, {
                                  initValue: 'npm',
                                  props: {
                                    onChange: this.handlePackageChange,
                                  },
                                })}
                                dataSource={[
                                  { label: 'npm', value: 'npm' },
                                  { label: 'docker', value: 'docker' },
                                  { label: 'pip', value: 'pip' },
                                  { label: 'uv', value: 'uv' },
                                ]}
                                disabled={currentVersionExist}
                              />
                            </FormItem>
                          </Col>
                        </Row>

                        <Row gutter={8}>
                          <Col span={12}>
                            <FormItem label={locale.description || '描述'}>
                              <Input
                                {...init(`packages.${index}.description`, {
                                  props: {
                                    onChange: this.handlePackageChange,
                                  },
                                })}
                                placeholder="Package description"
                                disabled={currentVersionExist}
                              />
                            </FormItem>
                          </Col>
                          <Col span={12}>
                            <FormItem label={locale.runtimeHint || '运行时提示'}>
                              <Input
                                {...init(`packages.${index}.runtime_hint`, {
                                  props: {
                                    onChange: this.handlePackageChange,
                                  },
                                })}
                                placeholder="npx, uvx, docker, etc."
                                disabled={currentVersionExist}
                              />
                            </FormItem>
                          </Col>
                        </Row>

                        {/* 参数配置部分 - 可折叠 */}
                        {this.state.expandedPackages[index] && (
                          <>
                            {/* 运行时参数配置 */}
                            <div style={{ marginTop: '15px' }}>
                              <Row gutter={8}>
                                <Col span={12}>
                                  <FormItem label={locale.runtimeArguments || '运行时参数'}>
                                    <Button
                                      type="normal"
                                      size="small"
                                      onClick={() => this.addRuntimeArgument(index)}
                                      disabled={currentVersionExist}
                                      style={{ marginBottom: '10px' }}
                                    >
                                      {locale.addArgument || '添加参数'}
                                    </Button>
                                    {(
                                      this.field.getValue(`packages.${index}.runtime_arguments`) ||
                                      []
                                    ).map((arg, argIndex) => (
                                      <div
                                        key={argIndex}
                                        style={{
                                          marginBottom: '10px',
                                          padding: '8px',
                                          border: '1px solid #e6e6e6',
                                          borderRadius: '4px',
                                          backgroundColor: '#f9f9f9',
                                        }}
                                      >
                                        <div
                                          style={{
                                            display: 'flex',
                                            gap: '8px',
                                            marginBottom: '8px',
                                          }}
                                        >
                                          <Select
                                            value={arg.type || 'positional'}
                                            onChange={value => {
                                              const args =
                                                this.field.getValue(
                                                  `packages.${index}.runtime_arguments`
                                                ) || [];
                                              args[argIndex] = { ...args[argIndex], type: value };
                                              this.field.setValue(
                                                `packages.${index}.runtime_arguments`,
                                                args
                                              );
                                              this.handlePackageChange();
                                            }}
                                            dataSource={[
                                              { label: '位置参数', value: 'positional' },
                                              { label: '命名参数', value: 'named' },
                                            ]}
                                            style={{ width: '120px' }}
                                            disabled={currentVersionExist}
                                          />
                                          <Input
                                            value={arg.value || ''}
                                            onChange={value => {
                                              const args =
                                                this.field.getValue(
                                                  `packages.${index}.runtime_arguments`
                                                ) || [];
                                              args[argIndex] = { ...args[argIndex], value };
                                              this.field.setValue(
                                                `packages.${index}.runtime_arguments`,
                                                args
                                              );
                                              this.handlePackageChange();
                                            }}
                                            placeholder={
                                              arg.type === 'named' ? '--flag=value' : 'argument'
                                            }
                                            style={{ flex: 1 }}
                                            disabled={currentVersionExist}
                                          />
                                          <Button
                                            type="normal"
                                            warning
                                            size="small"
                                            onClick={() =>
                                              this.deleteRuntimeArgument(index, argIndex)
                                            }
                                            disabled={currentVersionExist}
                                          >
                                            删除
                                          </Button>
                                        </div>
                                        <Input
                                          value={arg.description || ''}
                                          onChange={value => {
                                            const args =
                                              this.field.getValue(
                                                `packages.${index}.runtime_arguments`
                                              ) || [];
                                            args[argIndex] = {
                                              ...args[argIndex],
                                              description: value,
                                            };
                                            this.field.setValue(
                                              `packages.${index}.runtime_arguments`,
                                              args
                                            );
                                            this.handlePackageChange();
                                          }}
                                          placeholder="参数描述"
                                          disabled={currentVersionExist}
                                          size="small"
                                        />
                                      </div>
                                    ))}
                                  </FormItem>
                                </Col>
                                <Col span={12}>
                                  <FormItem label={locale.packageArguments || '包参数'}>
                                    <Button
                                      type="normal"
                                      size="small"
                                      onClick={() => this.addPackageArgument(index)}
                                      disabled={currentVersionExist}
                                      style={{ marginBottom: '10px' }}
                                    >
                                      {locale.addArgument || '添加参数'}
                                    </Button>
                                    {(
                                      this.field.getValue(`packages.${index}.package_arguments`) ||
                                      []
                                    ).map((arg, argIndex) => (
                                      <div
                                        key={argIndex}
                                        style={{
                                          marginBottom: '10px',
                                          padding: '8px',
                                          border: '1px solid #e6e6e6',
                                          borderRadius: '4px',
                                          backgroundColor: '#f9f9f9',
                                        }}
                                      >
                                        <div
                                          style={{
                                            display: 'flex',
                                            gap: '8px',
                                            marginBottom: '8px',
                                          }}
                                        >
                                          <Select
                                            value={arg.type || 'positional'}
                                            onChange={value => {
                                              const args =
                                                this.field.getValue(
                                                  `packages.${index}.package_arguments`
                                                ) || [];
                                              args[argIndex] = { ...args[argIndex], type: value };
                                              this.field.setValue(
                                                `packages.${index}.package_arguments`,
                                                args
                                              );
                                              this.handlePackageChange();
                                            }}
                                            dataSource={[
                                              { label: '位置参数', value: 'positional' },
                                              { label: '命名参数', value: 'named' },
                                            ]}
                                            style={{ width: '120px' }}
                                            disabled={currentVersionExist}
                                          />
                                          <Input
                                            value={arg.value || ''}
                                            onChange={value => {
                                              const args =
                                                this.field.getValue(
                                                  `packages.${index}.package_arguments`
                                                ) || [];
                                              args[argIndex] = { ...args[argIndex], value };
                                              this.field.setValue(
                                                `packages.${index}.package_arguments`,
                                                args
                                              );
                                              this.handlePackageChange();
                                            }}
                                            placeholder={
                                              arg.type === 'named' ? '--flag=value' : 'argument'
                                            }
                                            style={{ flex: 1 }}
                                            disabled={currentVersionExist}
                                          />
                                          <Button
                                            type="normal"
                                            warning
                                            size="small"
                                            onClick={() =>
                                              this.deletePackageArgument(index, argIndex)
                                            }
                                            disabled={currentVersionExist}
                                          >
                                            删除
                                          </Button>
                                        </div>
                                        <Input
                                          value={arg.description || ''}
                                          onChange={value => {
                                            const args =
                                              this.field.getValue(
                                                `packages.${index}.package_arguments`
                                              ) || [];
                                            args[argIndex] = {
                                              ...args[argIndex],
                                              description: value,
                                            };
                                            this.field.setValue(
                                              `packages.${index}.package_arguments`,
                                              args
                                            );
                                            this.handlePackageChange();
                                          }}
                                          placeholder="参数描述"
                                          disabled={currentVersionExist}
                                          size="small"
                                        />
                                      </div>
                                    ))}
                                  </FormItem>
                                </Col>
                              </Row>
                            </div>

                            {/* 环境变量配置 */}
                            <div style={{ marginTop: '15px' }}>
                              <FormItem label={locale.environmentVariables || '环境变量'}>
                                <Button
                                  type="normal"
                                  size="small"
                                  onClick={() => this.addEnvironmentVariable(index)}
                                  disabled={currentVersionExist}
                                  style={{ marginBottom: '10px' }}
                                >
                                  {locale.addEnvironmentVariable || '添加环境变量'}
                                </Button>
                                {(
                                  this.field.getValue(`packages.${index}.environment_variables`) ||
                                  []
                                ).map((envVar, envIndex) => (
                                  <div
                                    key={envIndex}
                                    style={{
                                      marginBottom: '12px',
                                      padding: '10px',
                                      border: '1px solid #e6e6e6',
                                      borderRadius: '4px',
                                      backgroundColor: '#f9f9f9',
                                    }}
                                  >
                                    <Row gutter={8} style={{ marginBottom: '8px' }}>
                                      <Col span={6}>
                                        <Input
                                          value={envVar.name || ''}
                                          onChange={value => {
                                            const vars =
                                              this.field.getValue(
                                                `packages.${index}.environment_variables`
                                              ) || [];
                                            vars[envIndex] = { ...vars[envIndex], name: value };
                                            this.field.setValue(
                                              `packages.${index}.environment_variables`,
                                              vars
                                            );
                                            this.handlePackageChange();
                                          }}
                                          placeholder="变量名 (如: API_KEY)"
                                          disabled={currentVersionExist}
                                        />
                                      </Col>
                                      <Col span={6}>
                                        <Input
                                          value={envVar.value || ''}
                                          onChange={value => {
                                            const vars =
                                              this.field.getValue(
                                                `packages.${index}.environment_variables`
                                              ) || [];
                                            vars[envIndex] = { ...vars[envIndex], value };
                                            this.field.setValue(
                                              `packages.${index}.environment_variables`,
                                              vars
                                            );
                                            this.handlePackageChange();
                                          }}
                                          placeholder="默认值"
                                          disabled={currentVersionExist}
                                        />
                                      </Col>
                                      <Col span={8}>
                                        <Input
                                          value={envVar.description || ''}
                                          onChange={value => {
                                            const vars =
                                              this.field.getValue(
                                                `packages.${index}.environment_variables`
                                              ) || [];
                                            vars[envIndex] = {
                                              ...vars[envIndex],
                                              description: value,
                                            };
                                            this.field.setValue(
                                              `packages.${index}.environment_variables`,
                                              vars
                                            );
                                            this.handlePackageChange();
                                          }}
                                          placeholder="描述说明"
                                          disabled={currentVersionExist}
                                        />
                                      </Col>
                                      <Col span={4}>
                                        <Button
                                          type="normal"
                                          warning
                                          size="small"
                                          onClick={() =>
                                            this.deleteEnvironmentVariable(index, envIndex)
                                          }
                                          disabled={currentVersionExist}
                                        >
                                          删除
                                        </Button>
                                      </Col>
                                    </Row>
                                    <Row gutter={8}>
                                      <Col span={6}>
                                        <label style={{ fontSize: '12px' }}>
                                          <input
                                            type="checkbox"
                                            checked={envVar.is_required || false}
                                            onChange={e => {
                                              const vars =
                                                this.field.getValue(
                                                  `packages.${index}.environment_variables`
                                                ) || [];
                                              vars[envIndex] = {
                                                ...vars[envIndex],
                                                is_required: e.target.checked,
                                              };
                                              this.field.setValue(
                                                `packages.${index}.environment_variables`,
                                                vars
                                              );
                                              this.handlePackageChange();
                                            }}
                                            disabled={currentVersionExist}
                                            style={{ marginRight: '4px' }}
                                          />
                                          必填
                                        </label>
                                      </Col>
                                      <Col span={6}>
                                        <label style={{ fontSize: '12px' }}>
                                          <input
                                            type="checkbox"
                                            checked={envVar.is_secret || false}
                                            onChange={e => {
                                              const vars =
                                                this.field.getValue(
                                                  `packages.${index}.environment_variables`
                                                ) || [];
                                              vars[envIndex] = {
                                                ...vars[envIndex],
                                                is_secret: e.target.checked,
                                              };
                                              this.field.setValue(
                                                `packages.${index}.environment_variables`,
                                                vars
                                              );
                                              this.handlePackageChange();
                                            }}
                                            disabled={currentVersionExist}
                                            style={{ marginRight: '4px' }}
                                          />
                                          敏感信息
                                        </label>
                                      </Col>
                                    </Row>
                                  </div>
                                ))}
                              </FormItem>
                            </div>
                          </>
                        )}
                      </div>
                    ))}

                  {/* 隐藏的表单字段，用于验证 packages */}
                  <div style={{ display: 'none' }}>
                    <Input
                      {...init('packagesValidation', {
                        rules: [
                          {
                            validator: (rule, value, callback) => {
                              if (this.state.configMode !== 'packageConfig') {
                                callback();
                                return;
                              }
                              const packages = this.field.getValue('packages') || [];
                              if (packages.length === 0) {
                                callback(
                                  locale.pleaseAddAtLeastOnePackage || '请至少添加一个 Package'
                                );
                                return;
                              }
                              // 检查每个 package 是否有必填字段
                              for (let i = 0; i < packages.length; i++) {
                                const pkg = packages[i];
                                if (!pkg.name) {
                                  callback(`第 ${i + 1} 个 Package 的包名不能为空`);
                                  return;
                                }
                              }
                              callback();
                            },
                          },
                        ],
                      })}
                    />
                  </div>
                </FormItem>
              )}
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
