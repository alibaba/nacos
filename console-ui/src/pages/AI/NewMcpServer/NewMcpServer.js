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
  NumberPicker,
  Select,
  Radio,
  ConfigProvider,
  Switch,
  Tab,
} from '@alifd/next';
import ShowTools from '../McpDetail/ShowTools';
import MonacoEditor from '../../../components/MonacoEditor';
import { McpServerManagementRoute } from '../../../layouts/menu';
import './NewMcpServer.css';
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
        defaultDownstreamSecurityId: '',
        defaultDownstreamSecurityPassthrough: false,
        defaultUpstreamSecurityId: '',
        defaultUpstreamSecurityCredential: '',
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
      restToMcpSwitch: true, // true表示开启HTTP转MCP服务，false表示关闭
      currentVersion: '',
      isLatestVersion: false,
      versionsList: [],
      isInputError: '',
      securitySchemeIdx: 0,
      advancedConfigCollapsed: true, // 高级配置默认折叠
      frontEndpointConfigList: [], // 保存原始的 frontEndpointConfigList，用于编辑时原封不动地返回
    };
  }

  componentDidMount() {
    this._mounted = true;
    if (!getParams('namespace')) {
      this.props?.history?.push({
        pathname: McpServerManagementRoute,
      });
    }
    this.initEditedData();
  }

  componentWillUnmount() {
    this._mounted = false;
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
          if (
            i === allVersions.length - 1 &&
            !(allVersions[i].isLatest || allVersions[i].is_latest)
          ) {
            break;
          }
          allPublishedVersions.push(allVersions[i].version);
        }

        this.setState({
          currentVersion: versionDetail.version,
          isLatestVersion: versionDetail.isLatest || versionDetail.is_latest,
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

          // 如果 protocol 是 'off' 且有 address 和 port 信息，重建 MCP Server endpoint
          if (protocol === 'off' && result.data?.backendEndpoints?.length > 0) {
            const endpoint = result.data.backendEndpoints[0];
            if (endpoint.address && endpoint.port) {
              const protocol = endpoint.protocol || 'http';
              const exportPath = remoteServerConfig?.exportPath || '/';
              const mcpServerEndpoint = `${protocol}://${endpoint.address}:${endpoint.port}${exportPath}`;
              initFileData['mcpServerEndpoint'] = mcpServerEndpoint;
            }
          }
        }

        // 合并initFileData与现有的field数据
        this.field.setValues({
          ...this.field.getValues(),
          ...initFileData,
        });

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

        this.setDefaultSecurityFields(result.data?.toolSpec?.extensions);

        let restToMcpSwitchValue = false;
        if (protocol === 'https' || protocol === 'http') {
          restToMcpSwitchValue = true;
        }

        // 从 serviceRef 的 transportProtocol 字段获取传输协议，如果不存在则使用后端 protocol 字段
        const transportProtocolValue =
          remoteServerConfig?.serviceRef?.transportProtocol ||
          (protocol === 'https' || protocol === 'http' ? protocol : 'http');

        // 设置传输协议字段的值
        this.field.setValues({
          ...this.field.getValues(),
          serviceTransportProtocol: transportProtocolValue,
          newServiceTransportProtocol: transportProtocolValue,
        });

        this.setState({
          serverConfig: result.data,
          useExistService: true, // 编辑时 默认使用已有服务，隐藏新建服务
          restToMcpSwitch: restToMcpSwitchValue,
          // 保存原始的 frontEndpointConfigList，用于编辑时原封不动地返回
          frontEndpointConfigList: result.data?.remoteServerConfig?.frontEndpointConfigList || [],
        });
      }
    }
  };

  goList() {
    const { history = {} } = this.props;
    history && history.goBack();
  }

  // 从 serverConfig 转换为 packages 的方法
  convertServerConfigToPackages = serverConfig => {
    if (!serverConfig || !serverConfig.mcpServers) {
      return [];
    }

    const packages = [];

    Object.entries(serverConfig.mcpServers).forEach(([serverName, config]) => {
      if (!config.command) {
        return; // 跳过没有 command 的配置
      }

      // 解析命令行，支持两种格式：
      // 1. command + args 分离的格式
      // 2. command 包含完整命令行的格式
      let parsedCommand, parsedArgs;

      if (config.args && Array.isArray(config.args)) {
        // 格式1：command 和 args 分离
        parsedCommand = config.command;
        parsedArgs = config.args;
      } else {
        // 格式2：command 包含完整命令行，需要解析
        const commandParts = this.parseCommandLine(config.command);
        parsedCommand = commandParts.command;
        parsedArgs = commandParts.args;
      }

      const pkg = {
        registryType: this.inferRegistryType(parsedCommand),
        identifier: this.extractPackageNameFromArgs(parsedArgs, parsedCommand),
        version: this.extractPackageVersionFromArgs(parsedArgs),
      };

      // 处理 runtime hint 和 runtime arguments
      if (parsedCommand && parsedCommand !== pkg.identifier) {
        pkg.runtimeHint = parsedCommand;

        // 从 args 中提取 runtime_arguments 和 package_arguments
        if (parsedArgs && Array.isArray(parsedArgs)) {
          const { runtimeArgs, packageArgs } = this.separateArguments(parsedArgs, pkg.identifier);

          if (runtimeArgs.length > 0) {
            pkg.runtimeArguments = runtimeArgs.map(arg => ({
              type: 'positional',
              value: arg,
              format: 'string',
            }));
          }

          if (packageArgs.length > 0) {
            pkg.packageArguments = packageArgs.map(arg => ({
              type: 'positional',
              value: arg,
              format: 'string',
            }));
          }
        }
      } else if (parsedArgs && Array.isArray(parsedArgs)) {
        // 如果 command 就是包名，所有 args 都是 package_arguments
        pkg.packageArguments = parsedArgs.map(arg => ({
          type: 'positional',
          value: arg,
          format: 'string',
        }));
      }

      // 处理环境变量
      if (config.env && typeof config.env === 'object') {
        pkg.environmentVariables = Object.entries(config.env).map(([name, value]) => ({
          name: name,
          value: value,
          format: 'string',
        }));
      }

      packages.push(pkg);
    });

    return packages;
  };

  // 解析完整的命令行
  parseCommandLine = commandLine => {
    if (!commandLine || typeof commandLine !== 'string') {
      return { command: '', args: [] };
    }

    // 简单的命令行解析，处理空格分隔的参数
    // 支持引号包围的参数（虽然这个例子中没有用到）
    const parts = [];
    let current = '';
    let inQuotes = false;
    let quoteChar = '';

    for (let i = 0; i < commandLine.length; i++) {
      const char = commandLine[i];

      if (!inQuotes && (char === '"' || char === "'")) {
        inQuotes = true;
        quoteChar = char;
      } else if (inQuotes && char === quoteChar) {
        inQuotes = false;
        quoteChar = '';
      } else if (!inQuotes && char === ' ') {
        if (current.trim()) {
          parts.push(current.trim());
          current = '';
        }
      } else {
        current += char;
      }
    }

    if (current.trim()) {
      parts.push(current.trim());
    }

    return {
      command: parts[0] || '',
      args: parts.slice(1),
    };
  };

  // 从参数中提取包名
  extractPackageNameFromArgs = (args, command) => {
    if (args && Array.isArray(args) && args.length > 0) {
      // 查找第一个看起来像包名的参数
      for (const arg of args) {
        // 跳过常见的标志参数
        if (arg.startsWith('-')) {
          continue;
        }
        // 跳过 URL 参数
        if (arg.startsWith('http://') || arg.startsWith('https://')) {
          continue;
        }
        // 如果参数包含 @ 或 / 或看起来像包名，就认为是包名
        if (arg.includes('@') || arg.includes('/') || arg.match(/^[a-zA-Z0-9][\w.-]*$/)) {
          // 如果包含版本号(@version)，提取包名部分
          if (arg.includes('@') && arg.split('@').length > 1) {
            const parts = arg.split('@');
            // 如果最后一部分看起来像版本号，返回除了版本的部分
            const lastPart = parts[parts.length - 1];
            if (lastPart.match(/^\d+\.\d+/)) {
              return parts.slice(0, -1).join('@');
            }
          }
          return arg;
        }
      }
    }

    // 如果没找到合适的包名，使用 command 作为包名
    return command || 'unknown-package';
  };

  // 从参数中提取包版本
  extractPackageVersionFromArgs = args => {
    if (args && Array.isArray(args)) {
      // 查找版本信息
      for (let i = 0; i < args.length; i++) {
        const arg = args[i];
        // 检查是否有 @version 格式
        if (arg.includes('@') && arg.split('@').length > 1) {
          const parts = arg.split('@');
          const version = parts[parts.length - 1];
          if (version.match(/^\d+\.\d+\.\d+/)) {
            return version;
          }
        }
        // 检查下一个参数是否是版本号
        if ((arg === '--version' || arg === '-v') && i + 1 < args.length) {
          const nextArg = args[i + 1];
          if (nextArg.match(/^\d+\.\d+\.\d+/)) {
            return nextArg;
          }
        }
      }
    }

    return 'latest'; // 默认版本
  };

  // 推断注册表类型
  inferRegistryType = command => {
    if (!command) return 'npm';

    // 如果 command 包含空格，取第一个词作为实际命令
    const actualCommand = command.split(' ')[0];

    const registryMap = {
      npm: 'npm',
      npx: 'npm',
      yarn: 'npm',
      pnpm: 'npm',
      pip: 'pypi',
      python: 'pypi',
      uvx: 'pypi',
      uv: 'pypi',
      dotnet: 'nuget',
      dnx: 'nuget',
      docker: 'docker',
      java: 'maven',
      mvn: 'maven',
      gradle: 'maven',
    };

    return registryMap[actualCommand] || 'npm'; // 默认为 npm
  };

  // 提取包名
  extractPackageName = config => {
    if (config.args && Array.isArray(config.args) && config.args.length > 0) {
      // 查找第一个看起来像包名的参数
      for (const arg of config.args) {
        // 跳过常见的标志参数
        if (arg.startsWith('-')) {
          continue;
        }
        // 如果参数包含 @ 或 / 或看起来像包名，就认为是包名
        if (arg.includes('@') || arg.includes('/') || arg.match(/^[a-zA-Z0-9][\w.-]*$/)) {
          // 如果包含版本号(@version)，提取包名部分
          if (arg.includes('@') && arg.split('@').length > 1) {
            const parts = arg.split('@');
            // 如果最后一部分看起来像版本号，返回除了版本的部分
            const lastPart = parts[parts.length - 1];
            if (lastPart.match(/^\d+\.\d+/)) {
              return parts.slice(0, -1).join('@');
            }
          }
          return arg;
        }
      }
    }

    // 如果没找到合适的包名，使用 command 作为包名
    return config.command || 'unknown-package';
  };

  // 提取包版本
  extractPackageVersion = config => {
    if (config.args && Array.isArray(config.args)) {
      // 查找版本信息
      for (let i = 0; i < config.args.length; i++) {
        const arg = config.args[i];
        // 检查是否有 @version 格式
        if (arg.includes('@') && arg.split('@').length > 1) {
          const parts = arg.split('@');
          const version = parts[parts.length - 1];
          if (version.match(/^\d+\.\d+\.\d+/)) {
            return version;
          }
        }
        // 检查下一个参数是否是版本号
        if ((arg === '--version' || arg === '-v') && i + 1 < config.args.length) {
          const nextArg = config.args[i + 1];
          if (nextArg.match(/^\d+\.\d+\.\d+/)) {
            return nextArg;
          }
        }
      }
    }

    return 'latest'; // 默认版本
  };

  // 分离 runtime arguments 和 package arguments
  separateArguments = (args, packageName) => {
    const runtimeArgs = [];
    const packageArgs = [];

    let foundPackage = false;

    for (let i = 0; i < args.length; i++) {
      const arg = args[i];

      if (!foundPackage) {
        // 在找到包名之前的都是 runtime arguments
        if (
          arg === packageName ||
          arg.includes(packageName) ||
          // 处理带版本号的情况：如果arg包含@且包名匹配
          (arg.includes('@') && arg.startsWith(packageName + '@'))
        ) {
          foundPackage = true;
          // 如果包名包含额外信息（如版本），整个参数都视为 runtime argument
          if (arg !== packageName) {
            runtimeArgs.push(arg);
          }
        } else {
          runtimeArgs.push(arg);
        }
      } else {
        // 找到包名之后的都是 package arguments
        packageArgs.push(arg);
      }
    }

    return { runtimeArgs, packageArgs };
  };

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
            isInputError: errors,
          });
          console.log('Form validation errors:', errors);
          return resolve({ errors });
        }

        let protocol = values.frontProtocol;
        console.log('origin protocol', protocol);
        if (values.frontProtocol === 'stdio') {
          protocol = values.frontProtocol;
        } else {
          // 当HTTP转MCP服务开启时，使用传输协议下拉框的值
          if (this.state.restToMcpSwitch) {
            if (useExistService) {
              protocol = values.serviceTransportProtocol || 'http';
            } else {
              protocol = values.newServiceTransportProtocol || 'http';
            }
          }
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

        // 构建服务器规范
        const defaultDescription = values?.description?.trim()
          ? values.description
          : `${values?.serverName || 'MCP Server'} v${values?.version || '1.0.0'}`;

        let serverSpec = {
          protocol: protocol,
          frontProtocol: values?.frontProtocol,
          name: values?.serverName,
          id: mcpServerId,
          description: defaultDescription,
          versionDetail: {
            version: values?.version || '1.0.0',
          },
          enabled: true,
          localServerConfig: values?.localServerConfig
            ? JSON.parse(values?.localServerConfig)
            : '{}',
        };

        // 如果是 stdio 协议，优先使用 localServerConfig 生成 packages，否则回退到查询结果
        if (values?.frontProtocol === 'stdio') {
          let generatedPackages = [];

          if (values?.localServerConfig) {
            try {
              const localConfig = JSON.parse(values.localServerConfig);
              generatedPackages = this.convertServerConfigToPackages(localConfig) || [];
            } catch (error) {
              console.error('Failed to parse localServerConfig or convert to packages:', error);
            }
          }

          if (generatedPackages.length > 0) {
            serverSpec.packages = generatedPackages;
          } else if (
            Array.isArray(this.state?.serverConfig?.packages) &&
            this.state.serverConfig.packages.length > 0
          ) {
            // 深拷贝，避免直接引用 state
            serverSpec.packages = JSON.parse(JSON.stringify(this.state.serverConfig.packages));
          }
        }

        const extensions = this.collectExtensionsPayload(values?.extensions);
        const toolSpecPayload = {
          ...(this.state?.serverConfig?.toolSpec || {}),
          securitySchemes: securitySchemes,
        };
        if (extensions) {
          toolSpecPayload.extensions = extensions;
        } else {
          delete toolSpecPayload.extensions;
        }

        const params = {
          id: mcpServerId,
          serverSpecification: JSON.stringify(serverSpec, null, 2),
          toolSpecification: JSON.stringify(toolSpecPayload || {}, null, 2),
        };

        if (values?.frontProtocol !== 'stdio') {
          // 处理 MCP Server endpoint 的情况
          if (!this.state.restToMcpSwitch && values?.mcpServerEndpoint) {
            try {
              const url = new URL(values.mcpServerEndpoint);
              const address = url.hostname;
              const port = url.port || (url.protocol === 'https:' ? '443' : '80');
              const exportPath = url.pathname || '/';

              // 从URL中提取传输协议，并追加到protocol字段之后
              const transportProtocol = url.protocol.replace(':', ''); // 去掉冒号，得到 http 或 https

              params.serverSpecification = JSON.stringify(
                {
                  protocol: protocol,
                  frontProtocol: values?.frontProtocol,
                  name: values?.serverName,
                  id: mcpServerId,
                  description: defaultDescription,
                  versionDetail: {
                    version: values?.version || '1.0.0',
                  },
                  enabled: true,
                  remoteServerConfig: {
                    exportPath: exportPath,
                    // 编辑时，原封不动返回 frontEndpointConfigList
                    ...(this.state.frontEndpointConfigList &&
                    this.state.frontEndpointConfigList.length > 0
                      ? { frontEndpointConfigList: this.state.frontEndpointConfigList }
                      : {}),
                  },
                },
                null,
                2
              );

              params.endpointSpecification = JSON.stringify({
                type: 'DIRECT',
                data: {
                  address: address,
                  port: port,
                  transportProtocol: transportProtocol,
                },
              });
            } catch (error) {
              console.error('Failed to parse MCP Server endpoint URL:', error);
              return resolve({ errors: { mcpServerEndpoint: '无效的 URL 格式' } });
            }
          } else {
            // 原有的处理逻辑
            params.serverSpecification = JSON.stringify(
              {
                protocol: protocol,
                frontProtocol: values?.frontProtocol,
                name: values?.serverName,
                id: mcpServerId,
                description: defaultDescription,
                versionDetail: {
                  version: values?.version || '1.0.0',
                },
                enabled: true,
                remoteServerConfig: {
                  exportPath: values?.exportPath || '',
                  // 编辑时，原封不动返回 frontEndpointConfigList
                  ...(this.state.frontEndpointConfigList &&
                  this.state.frontEndpointConfigList.length > 0
                    ? { frontEndpointConfigList: this.state.frontEndpointConfigList }
                    : {}),
                },
              },
              null,
              2
            );
            // 添加服务，并携带所选传输协议到 endpointSpecification.protocol

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
                    // 传输协议：来自已有服务下拉框
                    transportProtocol: values?.serviceTransportProtocol || 'http',
                  },
                },
                null,
                2
              );
            } else {
              // 直连新服务：携带 address/port 以及用户选择的传输协议
              const directSpec = {
                type: 'DIRECT',
                data: {
                  address: values?.address,
                  port: `${values?.port}`,
                  transportProtocol: values?.newServiceTransportProtocol || 'http',
                },
              };
              params.endpointSpecification = JSON.stringify(directSpec, null, 2);
            }
          }
        }

        resolve(params);
      });
    });
  };

  publishConfig = async isPublish => {
    if (this.state.isInputError.length > 0) {
      console.log('input error ' + this.state.isInputError);
      Dialog.alert(this.state.isInputError);
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
    const chartReg = /^[a-zA-Z0-9_/\-\.]+$/;
    if (!chartReg.test(value)) {
      console.log('Invalid chart name:', value);
      callback(locale.doNotEnter);
      this.setState({
        isInputError:
          'Server name should only contain letters, numbers, underscores, hyphens, and slashes.',
      });
    } else {
      callback();
      this.setState({
        isInputError: '',
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

  // 解析 MCP Server Endpoint 并更新相关字段
  updateEndpointFields = endpoint => {
    if (!endpoint || typeof endpoint !== 'string') {
      return;
    }

    try {
      const url = new URL(endpoint);

      // 提取地址（hostname）
      const address = url.hostname;

      // 提取端口，如果没有指定则使用默认端口
      const port = url.port || (url.protocol === 'https:' ? '443' : '80');

      // 提取路径
      const exportPath = url.pathname || '/';

      // 提取传输协议
      const transportProtocol = url.protocol.replace(':', ''); // 去掉冒号

      // 更新表单字段
      this.field.setValue('address', address);
      this.field.setValue('port', parseInt(port, 10));
      this.field.setValue('exportPath', exportPath);

      // 根据URL的协议设置传输协议
      if (['http', 'https'].includes(transportProtocol)) {
        this.field.setValue('serviceTransportProtocol', transportProtocol);
        this.field.setValue('newServiceTransportProtocol', transportProtocol);
      }

      // 同步更新 serverConfig
      this.setState({
        serverConfig: {
          ...this.state.serverConfig,
          endpoint: {
            address: address,
            port: parseInt(port, 10),
          },
          remoteServerConfig: {
            ...this.state.serverConfig?.remoteServerConfig,
            exportPath: exportPath,
          },
          protocol: transportProtocol,
        },
      });
    } catch (error) {
      // URL 解析失败时不做任何处理，保持现有值
      console.warn('Failed to parse MCP Server Endpoint URL:', error);
    }
  };

  setDefaultSecurityFields = extensions => {
    const downstream = extensions?.['server.defaultDownstreamSecurity'] || {};
    const upstream = extensions?.['server.defaultUpstreamSecurity'] || {};

    this.field.setValues({
      defaultDownstreamSecurityId: downstream.id || '',
      defaultDownstreamSecurityPassthrough:
        typeof downstream.passthrough === 'boolean' ? downstream.passthrough : false,
      defaultUpstreamSecurityId: upstream.id || '',
      defaultUpstreamSecurityCredential: upstream.credential || '',
    });
  };

  collectExtensionsPayload = baseExtensions => {
    const sourceExtensions =
      baseExtensions != null
        ? baseExtensions
        : this.state?.serverConfig?.toolSpec?.extensions || {};

    const preservedExtensions = {};
    Object.keys(sourceExtensions || {}).forEach(key => {
      if (
        key !== 'server.defaultDownstreamSecurity' &&
        key !== 'server.defaultUpstreamSecurity'
      ) {
        preservedExtensions[key] = sourceExtensions[key];
      }
    });

    const downstreamId = this.field.getValue('defaultDownstreamSecurityId');
    const downstreamPassthrough = this.field.getValue(
      'defaultDownstreamSecurityPassthrough'
    );
    if (downstreamId) {
      preservedExtensions['server.defaultDownstreamSecurity'] = {
        id: downstreamId,
        passthrough: Boolean(downstreamPassthrough),
      };
    }

    const upstreamId = this.field.getValue('defaultUpstreamSecurityId');
    const upstreamCredential = this.field.getValue('defaultUpstreamSecurityCredential');
    if (upstreamId) {
      preservedExtensions['server.defaultUpstreamSecurity'] = {
        id: upstreamId,
        ...(upstreamCredential ? { credential: upstreamCredential } : {}),
      };
    }

    return Object.keys(preservedExtensions).length ? preservedExtensions : {};
  };

  handleExtensionsFieldChange = (options = {}) => {
    const { skipServerConfigUpdate = false } = options;
    const downstreamId = this.field.getValue('defaultDownstreamSecurityId');
    if (!downstreamId && this.field.getValue('defaultDownstreamSecurityPassthrough')) {
      this.field.setValue('defaultDownstreamSecurityPassthrough', false);
    }
    const upstreamId = this.field.getValue('defaultUpstreamSecurityId');
    if (!upstreamId && this.field.getValue('defaultUpstreamSecurityCredential')) {
      this.field.setValue('defaultUpstreamSecurityCredential', '');
    }
    const extensions = this.collectExtensionsPayload();
    if (!skipServerConfigUpdate) {
      this.setState(prevState => {
        const nextToolSpec = {
          ...(prevState.serverConfig?.toolSpec || {}),
          extensions,
        };
        return {
          serverConfig: {
            ...prevState.serverConfig,
            toolSpec: nextToolSpec,
          },
        };
      });
    }

    return extensions;

  };

  // 切换高级配置展开/折叠状态
  toggleAdvancedConfig = () => {
    this.setState({
      advancedConfigCollapsed: !this.state.advancedConfigCollapsed,
    });
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
      if (!this._mounted) return;
      this.toolsChange();
    }, 100);
  };

  // 删除安全认证方案
  deleteSecurityScheme = index => {
    this.field.deleteArrayValue('securitySchemes', index);
    // 更新 serverConfig 以便 CreateTools 能够实时获取到最新的 securitySchemes
    setTimeout(() => {
      if (!this._mounted) return;
      this.toolsChange();
    }, 100);
  };

  // 处理 securitySchemes 字段变化
  handleSecuritySchemeChange = () => {
    // 延迟执行，确保表单字段已经更新
    setTimeout(() => {
      if (!this._mounted) return;
      const currentSchemes = this.field.getValue('securitySchemes') || [];
      const availableIds = currentSchemes.map(item => item?.id).filter(Boolean);
      const downstreamId = this.field.getValue('defaultDownstreamSecurityId');
      const upstreamId = this.field.getValue('defaultUpstreamSecurityId');
      if (downstreamId && !availableIds.includes(downstreamId)) {
        this.field.setValue('defaultDownstreamSecurityId', '');
        this.field.setValue('defaultDownstreamSecurityPassthrough', false);
      }
      if (upstreamId && !availableIds.includes(upstreamId)) {
        this.field.setValue('defaultUpstreamSecurityId', '');
        this.field.setValue('defaultUpstreamSecurityCredential', '');
      }
      this.handleExtensionsFieldChange({ skipServerConfigUpdate: true });
      this.toolsChange();
    }, 100);
  };

  toolsChange = async (_toolSpec = {}, cb = () => {}) => {
    const { locale = {} } = this.props;

    // 获取当前表单中的 securitySchemes 数据
    const formSecuritySchemes = this.field.getValue('securitySchemes') || [];
    const securitySchemesFromForm = formSecuritySchemes.map(scheme => {
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

    // 处理从子组件传入的 securitySchemes（来自 OpenAPI 导入）
    const importedSchemes = Array.isArray(_toolSpec?.securitySchemes)
      ? _toolSpec.securitySchemes
      : [];

    // 合并并按 id 去重（导入优先覆盖同 id）
    const mergedMap = new Map();
    securitySchemesFromForm.forEach(s => {
      if (s?.id) mergedMap.set(s.id, s);
    });
    importedSchemes.forEach(s => {
      if (s?.id) {
        mergedMap.set(s.id, {
          id: s.id,
          type: s.type,
          scheme: s.scheme,
          in: s.in,
          name: s.name,
          // 默认凭证仅保留表单里已存在的值，导入的一般不含默认值
          defaultCredential: mergedMap.get(s.id)?.defaultCredential || '',
        });
      }
    });
    const mergedSecuritySchemes = Array.from(mergedMap.values());

    // 将合并后的结果同步回表单字段，便于用户在 UI 中看到并进一步编辑
    this.field.setValue(
      'securitySchemes',
      mergedSecuritySchemes.map((s, idx) => ({
        // 确保表单行有稳定的 id 字段
        id: s.id || `securityScheme_${idx + 1}`,
        type: s.type,
        scheme: s.scheme,
        in: s.in,
        name: s.name,
        defaultCredential: s.defaultCredential,
      }))
    );
    // 同步 internal 计数器，便于后续新增时生成唯一行 id
    this.setState({ securitySchemeIdx: mergedSecuritySchemes.length });

    const incomingExtensions = _toolSpec?.extensions;
    if (incomingExtensions) {
      this.setDefaultSecurityFields(incomingExtensions);
    }
    const extensions = this.collectExtensionsPayload(incomingExtensions);

    await new Promise(resolve => {
      this.setState(prevState => {
        const prevToolSpec = prevState.serverConfig?.toolSpec || {};
        const nextToolSpec = {
          ...prevToolSpec,
          tools: _toolSpec?.tools || prevToolSpec.tools || [],
          toolsMeta: _toolSpec?.toolsMeta || prevToolSpec.toolsMeta || {},
          securitySchemes: mergedSecuritySchemes,
        };
        if (extensions) {
          nextToolSpec.extensions = extensions;
        } else {
          delete nextToolSpec.extensions;
        }
        return {
          serverConfig: {
            ...prevState.serverConfig,
            toolSpec: nextToolSpec,
          },
        };
      }, resolve);
    });
  };

  LocalServerConfigLabel = () => {
    const { locale = {} } = this.props;
    const trigger = <Icon type="help" color="#333" size="small" className="help-icon" />;
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
          <div className="help-tooltip-tips">2. {locale.localServerTips3}</div>
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

    const securitySchemesData = this.field.getValue('securitySchemes') || [];
    const availableSecuritySchemeOptions = securitySchemesData
      .filter(item => item?.id)
      .map(item => ({
        label: item.id,
        value: item.id,
      }));

    let hasDraftVersion = false;
    if (versions.length > 0) {
      hasDraftVersion = !(
        versions[versions.length - 1].isLatest || versions[versions.length - 1].is_latest
      );
    }

    let currentVersionExist = versions
      .map(item => item.version)
      .includes(this.field.getValue('version'));

    return (
      <Loading
        shape={'flower'}
        tip={'Loading...'}
        className="new-mcp-server-container"
        visible={this.state.loading}
        color={'#333'}
      >
        <Row className="new-mcp-server-header">
          <Col span={16} className="new-mcp-server-title">
            <h1>
              {!getParams('mcptype') && locale.addNewMcpServer}
              {getParams('mcptype') && (isEdit ? locale.editService : locale.viewService)}
            </h1>
          </Col>
          <Col span={8} className="new-mcp-server-actions">
            <FormItem label=" ">
              <div className="text-right">
                {isEdit ? (
                  <>
                    <Button
                      type="primary"
                      onClick={() => {
                        this.publishConfig(false);
                      }}
                      className="new-mcp-server-actions button"
                    >
                      {locale.createNewVersionAndSave}
                    </Button>

                    <Button
                      type="primary"
                      onClick={() => {
                        this.publishConfig(true);
                      }}
                      className="new-mcp-server-actions button"
                    >
                      {locale.createNewVersionAndPublish}
                    </Button>
                  </>
                ) : (
                  <Button
                    type={'primary'}
                    className="new-mcp-server-actions button"
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
                props: {
                  onChange: value => {
                    this.setState({
                      serverConfig: {
                        ...this.state.serverConfig,
                        name: value,
                      },
                    });
                  },
                },
              })}
              maxLength={255}
              addonTextBefore={
                this.state.addonBefore ? (
                  <div className="form-addon-before">{this.state.addonBefore}</div>
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
                    // 更新useExistService状态
                    const newUseExistService = ['mcp-sse', 'mcp-streamable'].includes(value)
                      ? false
                      : true;
                    this.setState({
                      useExistService: newUseExistService,
                    });

                    // 当协议类型切换时，设置默认值
                    if (value !== 'stdio') {
                      // 非stdio协议的默认配置

                      // 设置默认的HTTP转MCP服务状态
                      let newRestToMcpSwitch = this.state.restToMcpSwitch;
                      if (!this.state.restToMcpSwitch) {
                        newRestToMcpSwitch = true; // 默认开启HTTP转MCP协议转化
                      }

                      // 设置默认的传输协议
                      if (!this.field.getValue('serviceTransportProtocol')) {
                        this.field.setValue('serviceTransportProtocol', 'http');
                      }
                      if (!this.field.getValue('newServiceTransportProtocol')) {
                        this.field.setValue('newServiceTransportProtocol', 'http');
                      }

                      // 设置默认的端口
                      let defaultPort = this.field.getValue('port');
                      if (!defaultPort) {
                        defaultPort = 8080;
                        this.field.setValue('port', defaultPort);
                      }

                      // 设置默认的地址
                      let defaultAddress = this.field.getValue('address');
                      if (!defaultAddress) {
                        defaultAddress = 'localhost';
                        this.field.setValue('address', defaultAddress);
                      }

                      // 设置默认的导出路径
                      let defaultExportPath = this.field.getValue('exportPath');
                      if (!defaultExportPath) {
                        defaultExportPath = '/mcp';
                        this.field.setValue('exportPath', defaultExportPath);
                      }

                      // 设置默认的命名空间
                      if (!this.field.getValue('namespace')) {
                        this.field.setValue('namespace', getParams('namespace') || 'public');
                      }

                      // 更新状态并同步到 serverConfig
                      this.setState({
                        restToMcpSwitch: newRestToMcpSwitch,
                        serverConfig: {
                          ...this.state.serverConfig,
                          frontProtocol: value,
                          protocol: newRestToMcpSwitch
                            ? this.state.useExistService
                              ? this.field.getValue('serviceTransportProtocol') || 'http'
                              : this.field.getValue('newServiceTransportProtocol') || 'http'
                            : value,
                          remoteServerConfig: {
                            ...this.state.serverConfig?.remoteServerConfig,
                            exportPath: defaultExportPath,
                          },
                          // 如果有endpoint配置，也同步更新
                          ...(defaultAddress && defaultPort
                            ? {
                                endpoint: {
                                  address: defaultAddress,
                                  port: defaultPort,
                                },
                              }
                            : {}),
                        },
                      });
                      console.log('state', this.state);
                    } else {
                      // stdio协议时重置HTTP转MCP服务状态并更新serverConfig
                      this.setState({
                        restToMcpSwitch: false,
                        serverConfig: {
                          ...this.state.serverConfig,
                          frontProtocol: value,
                          protocol: value,
                        },
                      });
                    }
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
                <Switch
                  disabled={isEdit}
                  checked={this.state.restToMcpSwitch}
                  onChange={checked => {
                    this.setState({
                      restToMcpSwitch: checked,
                      ...(checked ? {} : { useExistService: false }),
                    });
                  }}
                />
                <span className="switch-label">{this.state.restToMcpSwitch ? '开启' : '关闭'}</span>
              </FormItem>
              {/*{!isEdit && (*/}

              {/* 只有在 HTTP 转 MCP 服务开启时才显示后端服务选项 */}
              {
                <FormItem label={locale.backendService}>
                  <RadioGroup
                    disabled={currentVersionExist}
                    value={this.state.useExistService ? 'useExistService' : 'useRemoteService'}
                    onChange={value => {
                      const newUseExistService = value === 'useExistService' ? true : false;
                      this.setState({
                        useExistService: newUseExistService,
                      });
                    }}
                  >
                    {// mcp-sse 和 mcp-streamable 不使用已有服务
                    (!['mcp-sse', 'mcp-streamable'].includes(
                      this.field.getValue('frontProtocol')
                    ) ||
                      isEdit ||
                      this.state.restToMcpSwitch) && (
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
              }

              {/* HTTP 转 MCP 服务关闭时显示 MCP Server endpoint */}
              {!this.state.useExistService && !this.state.restToMcpSwitch && (
                <FormItem
                  label={locale.mcpServerEndpoint || 'MCP Server Endpoint'}
                  required
                  help={
                    locale.mcpEndpointDesc ||
                    '请输入完整的 MCP 服务端点 URL，例如：http://example.com/sse'
                  }
                >
                  <Input
                    {...init('mcpServerEndpoint', {
                      rules: [
                        {
                          required: true,
                          message: locale.pleaseEnter || '请输入 MCP Server Endpoint',
                        },
                        {
                          validator: (rule, value, callback) => {
                            if (!value) {
                              callback();
                              return;
                            }
                            try {
                              const url = new URL(value);
                              if (!['http:', 'https:'].includes(url.protocol)) {
                                callback('请输入有效的 HTTP 或 HTTPS URL');
                                return;
                              }
                              callback();
                            } catch (error) {
                              callback('请输入有效的 URL 格式');
                            }
                          },
                        },
                      ],
                      props: {
                        onChange: value => {
                          // 解析URL并更新相关字段
                          this.updateEndpointFields(value);

                          this.setState({
                            serverConfig: {
                              ...this.state.serverConfig,
                              mcpServerEndpoint: value,
                            },
                          });
                        },
                      },
                    })}
                    placeholder={
                      this.field.getValue('frontProtocol') === 'mcp-sse'
                        ? 'http://example.com/sse'
                        : this.field.getValue('frontProtocol') === 'mcp-streamable'
                        ? 'http://example.com/streamable'
                        : 'http://example.com/mcp'
                    }
                    disabled={currentVersionExist}
                    maxLength={500}
                  />
                </FormItem>
              )}

              {/* 只有在 HTTP 转 MCP 服务开启时才显示服务配置 */}
              {this.state.useExistService && (
                <>
                  <FormItem label={locale.serviceRef} required>
                    <Row gutter={8}>
                      <Col span={4}>
                        <FormItem label="namespace">
                          <p>{currentNamespace}</p>
                        </FormItem>
                      </Col>
                      <Col span={8}>
                        <FormItem label="service">
                          <Select
                            isPreview={currentVersionExist}
                            {...init('service', {
                              rules: [{ required: true, message: locale.placeSelect }],
                              props: {
                                dataSource: this.state.serviceList,
                                style: { width: '100%' },
                                placeholder: 'service',
                                onChange: value => {
                                  this.setState({
                                    serverConfig: {
                                      ...this.state.serverConfig,
                                      service: value,
                                    },
                                  });
                                },
                              },
                            })}
                          />
                        </FormItem>
                      </Col>
                      <Col span={6}>
                        <FormItem label={locale.transportProtocol || '传输协议'} required>
                          <Select
                            {...init('serviceTransportProtocol', {
                              initValue: 'http',
                              rules: [{ required: true, message: locale.pleaseSelect }],
                              props: {
                                onChange: value => {
                                  // 当传输协议改变且HTTP转MCP服务开启时，同步更新协议值
                                  if (this.state.restToMcpSwitch && this.state.useExistService) {
                                    this.setState({
                                      serverConfig: {
                                        ...this.state.serverConfig,
                                        protocol: value,
                                      },
                                    });
                                  } else {
                                    this.setState({
                                      serverConfig: {
                                        ...this.state.serverConfig,
                                        serviceTransportProtocol: value,
                                      },
                                    });
                                  }
                                },
                              },
                            })}
                            disabled={currentVersionExist}
                            dataSource={[
                              { label: 'HTTP', value: 'http' },
                              { label: 'HTTPS', value: 'https' },
                            ]}
                            placeholder={locale.pleaseSelect || '请选择'}
                          />
                        </FormItem>
                      </Col>
                      {!this.state.restToMcpSwitch && (
                        <Col span={6}>
                          <FormItem label="exportPath">
                            <Input
                              isPreview={currentVersionExist}
                              {...init('exportPath', {
                                rules: [{ required: true, message: locale.pleaseSelect }],
                                props: {
                                  onChange: value => {
                                    this.setState({
                                      serverConfig: {
                                        ...this.state.serverConfig,
                                        remoteServerConfig: {
                                          ...this.state.serverConfig?.remoteServerConfig,
                                          exportPath: value,
                                        },
                                      },
                                    });
                                  },
                                },
                              })}
                              placeholder={this.field.getValue('exportPath') || '/mcp'}
                            />
                          </FormItem>
                        </Col>
                      )}
                    </Row>
                  </FormItem>
                </>
              )}

              {/* 只有在 HTTP 转 MCP 服务开启且选择新建服务时才显示 */}
              {this.state.restToMcpSwitch && !this.state.useExistService && (
                <>
                  <FormItem label={locale.useNewService} required disabled={currentVersionExist}>
                    <Row gutter={8}>
                      <Col span={12}>
                        <FormItem label="address">
                          <Input
                            {...init('address', {
                              rules: [{ required: true, message: locale.pleaseEnter }],
                              props: {
                                onChange: value => {
                                  this.setState({
                                    serverConfig: {
                                      ...this.state.serverConfig,
                                      endpoint: {
                                        ...this.state.serverConfig?.endpoint,
                                        address: value,
                                      },
                                    },
                                  });
                                },
                              },
                            })}
                            className="new-service-address"
                          />
                        </FormItem>
                      </Col>
                      <Col span={4}>
                        <FormItem label="port">
                          <NumberPicker
                            {...init('port', {
                              rules: [{ required: true, message: locale.pleaseEnter }],
                              props: {
                                onChange: value => {
                                  this.setState({
                                    serverConfig: {
                                      ...this.state.serverConfig,
                                      endpoint: {
                                        ...this.state.serverConfig?.endpoint,
                                        port: value,
                                      },
                                    },
                                  });
                                },
                              },
                            })}
                            min={1}
                            max={65535}
                            step={1}
                            className="port-number-picker"
                            placeholder="8080"
                          />
                        </FormItem>
                      </Col>
                      <Col span={8}>
                        <FormItem label={locale.transportProtocol || '传输协议'} required>
                          <Select
                            {...init('newServiceTransportProtocol', {
                              initValue: 'http',
                              rules: [{ required: true, message: locale.pleaseSelect }],
                              props: {
                                onChange: value => {
                                  // 当传输协议改变且HTTP转MCP服务开启时，同步更新协议值
                                  if (this.state.restToMcpSwitch && !this.state.useExistService) {
                                    this.setState({
                                      serverConfig: {
                                        ...this.state.serverConfig,
                                        protocol: value,
                                      },
                                    });
                                  } else {
                                    this.setState({
                                      serverConfig: {
                                        ...this.state.serverConfig,
                                        newServiceTransportProtocol: value,
                                      },
                                    });
                                  }
                                },
                              },
                            })}
                            disabled={currentVersionExist}
                            dataSource={[
                              { label: 'HTTP', value: 'http' },
                              { label: 'HTTPS', value: 'https' },
                            ]}
                            placeholder={locale.pleaseSelect || '请选择'}
                          />
                        </FormItem>
                      </Col>
                    </Row>
                  </FormItem>
                </>
              )}
            </>
          ) : (
            // Local Server 配置
            <>
              {/* Local Server Config 配置 */}
              <FormItem label={this.LocalServerConfigLabel()} required>
                {currentVersionExist ? (
                  // 预览模式使用格式化的 pre 标签
                  <pre className="config-preview-pre">
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
                      // 添加防护，确保组件未被卸载
                      if (!this._mounted) return;

                      this.field.setValue('localServerConfig', value);

                      // 同步到 serverConfig
                      try {
                        const parsedConfig = value ? JSON.parse(value) : {};
                        this.setState({
                          serverConfig: {
                            ...this.state.serverConfig,
                            localServerConfig: parsedConfig,
                          },
                        });
                      } catch (error) {
                        // 如果 JSON 解析失败，仍然保存原始字符串
                        this.setState({
                          serverConfig: {
                            ...this.state.serverConfig,
                            localServerConfig: value,
                          },
                        });
                      }

                      // 执行验证逻辑
                      try {
                        if (value?.length > 0) {
                          const parsedValue = JSON.parse(value);

                          // 提取 MCP Server 名称和描述
                          if (
                            parsedValue &&
                            parsedValue.mcpServers &&
                            typeof parsedValue.mcpServers === 'object'
                          ) {
                            const serverNames = Object.keys(parsedValue.mcpServers);
                            if (serverNames.length > 0) {
                              const firstServerName = serverNames[0];
                              const firstServerConfig = parsedValue.mcpServers[firstServerName];

                              // 只有在当前serverName为空或者是默认值时才自动填充
                              const currentServerName = this.field.getValue('serverName');
                              if (!currentServerName || currentServerName.trim() === '') {
                                this.field.setValue('serverName', firstServerName);
                              }

                              // 如果服务器配置中有描述，自动填充到描述字段
                              if (firstServerConfig && firstServerConfig.description) {
                                const currentDescription = this.field.getValue('description');
                                if (!currentDescription || currentDescription.trim() === '') {
                                  this.field.setValue('description', firstServerConfig.description);
                                }
                              }
                            }
                          }

                          // 保持原有的逻辑：如果配置根级别有description字段也可以使用
                          if (parsedValue != null && parsedValue['description']) {
                            const currentDescription = this.field.getValue('description');
                            if (!currentDescription || currentDescription.trim() === '') {
                              this.field.setValue('description', parsedValue['description']);
                            }
                          }
                        }
                        this.setState({
                          isInputError: '',
                        });
                      } catch (e) {
                        console.log('Local Server Config JSON parse error:', e.message);
                        this.setState({
                          isInputError: 'Local Server Config 格式错误 ' + e.message,
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
              </FormItem>
            </>
          )}

          {/* 服务版本 */}
          <FormItem label={locale.serverVersion} required>
            <Input
              {...init('version', {
                initValue: '1.0.0',
                props: {
                  placeholder: 'e.g. 1.0.0',
                  onChange: value => {
                    this.setState({
                      serverConfig: {
                        ...this.state.serverConfig,
                        versionDetail: {
                          ...this.state.serverConfig?.versionDetail,
                          version: value,
                        },
                      },
                    });
                  },
                },
                rules: [{ required: true }],
              })}
            />
            {currentVersionExist && (
              <>
                <p className="version-error-text">{locale.editExistVersionMessage}</p>
                <p className="version-error-text">{locale.editMoreNeedNewVersion}</p>
              </>
            )}
          </FormItem>

          <FormItem label={locale.description}>
            <Input.TextArea
              isPreview={currentVersionExist}
              renderPreview={value => <div className="description-preview">{value || '-'}</div>}
              {...init('description', {
                props: {
                  ...descAreaProps,
                  onChange: value => {
                    this.setState({
                      serverConfig: {
                        ...this.state.serverConfig,
                        description: value,
                      },
                    });
                  },
                },
              })}
            />
          </FormItem>

          {/* Security Schemes 配置 - 只在非stdio协议且 restToMcpSwitch 开启时显示 */}
          {this.field.getValue('frontProtocol') !== 'stdio' && this.state.restToMcpSwitch && (
            <FormItem label={locale.advancedConfig || '高级配置'}>
              <div className="advanced-config-container">
                <Button
                  type="normal"
                  size="small"
                  onClick={this.toggleAdvancedConfig}
                  className="advanced-config-toggle-btn"
                >
                  <Icon
                    type={this.state.advancedConfigCollapsed ? 'arrow-right' : 'arrow-down'}
                    className="advanced-config-toggle-icon"
                  />
                  {locale.securitySchemes || '安全认证方案'}
                </Button>
              </div>

              {!this.state.advancedConfigCollapsed && (
                <div className="advanced-config-content">
                  <div className="default-security-sections">
                    <div className="default-security-card">
                      <div className="default-security-card-header">
                        <div className="default-security-card-title">
                          {locale.defaultDownstreamSecurityTitle || 'Default Downstream Security'}
                        </div>
                        <div className="default-security-card-desc">
                          {locale.defaultDownstreamSecurityDesc ||
                            'Applies to client-to-gateway requests (e.g., tools/list) when no tool override exists.'}
                        </div>
                      </div>
                      <Row gutter={8}>
                        <Col span={12}>
                          <FormItem label={locale.selectSecurityScheme || 'Select Security Scheme'}>
                            <Select
                              {...init('defaultDownstreamSecurityId', {
                                props: {
                                  onChange: () => this.handleExtensionsFieldChange(),
                                },
                              })}
                              dataSource={availableSecuritySchemeOptions}
                              placeholder={
                                locale.defaultDownstreamSecurityPlaceholder ||
                                locale.pleaseSelectSecurityScheme ||
                                'Select security scheme'
                              }
                              hasClear
                              showSearch
                            />
                          </FormItem>
                        </Col>
                        <Col span={12}>
                          <FormItem label={locale.transparentAuth || 'Enable Transparent Auth'}>
                            <Switch
                              {...init('defaultDownstreamSecurityPassthrough', {
                                valueName: 'checked',
                                initValue: false,
                                props: {
                                  onChange: () => this.handleExtensionsFieldChange(),
                                },
                              })}
                              disabled={!this.field.getValue('defaultDownstreamSecurityId')}
                            />
                            <span className="switch-label">
                              {this.field.getValue('defaultDownstreamSecurityPassthrough')
                                ? locale.enable || 'Enable'
                                : locale.disable || 'Disable'}
                            </span>
                          </FormItem>
                        </Col>
                      </Row>
                    </div>
                    <div className="default-security-card">
                      <div className="default-security-card-header">
                        <div className="default-security-card-title">
                          {locale.defaultUpstreamSecurityTitle || 'Default Upstream Security'}
                        </div>
                        <div className="default-security-card-desc">
                          {locale.defaultUpstreamSecurityDesc ||
                            'Applies to gateway-to-backend calls (e.g., default requestTemplate.security).'}
                        </div>
                      </div>
                      <Row gutter={8}>
                        <Col span={12}>
                          <FormItem label={locale.selectSecurityScheme || 'Select Security Scheme'}>
                            <Select
                              {...init('defaultUpstreamSecurityId', {
                                props: {
                                  onChange: () => this.handleExtensionsFieldChange(),
                                },
                              })}
                              dataSource={availableSecuritySchemeOptions}
                              placeholder={
                                locale.defaultUpstreamSecurityPlaceholder ||
                                locale.pleaseSelectSecurityScheme ||
                                'Select security scheme'
                              }
                              hasClear
                              showSearch
                            />
                          </FormItem>
                        </Col>
                        <Col span={12}>
                          <FormItem label={locale.upstreamCredentialLabel || 'Override Credential'}>
                            <Input.TextArea
                              {...init('defaultUpstreamSecurityCredential', {
                                props: {
                                  onChange: () => this.handleExtensionsFieldChange(),
                                },
                              })}
                              placeholder={
                                locale.upstreamCredentialPlaceholder || 'Optional credential override'
                              }
                              autoHeight={{ minRows: 2, maxRows: 4 }}
                              disabled={!this.field.getValue('defaultUpstreamSecurityId')}
                            />
                          </FormItem>
                        </Col>
                      </Row>
                    </div>
                  </div>
                  <Button
                    type="primary"
                    size="small"
                    onClick={this.addNewSecurityScheme}
                    className="add-security-scheme-btn"
                  >
                    {locale.addSecurityScheme || '添加认证方案'}
                  </Button>

                  {this.field.getValue('securitySchemes') &&
                    this.field.getValue('securitySchemes').map((item, index) => (
                      <div key={index} className="security-scheme-item">
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
                </div>
              )}
            </FormItem>
          )}

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

/*
 * Server Config 到 Package 转换示例：
 *
 * 输入的 Server Config (格式1 - 分离式):
 * {
 *   "mcpServers": {
 *     "amap-mcp-server": {
 *       "description": "高德地图服务",
 *       "command": "npx",
 *       "args": ["-y", "@amap/amap-maps-mcp-server"],
 *       "env": {
 *         "AMAP_MAPS_API_KEY": "<API_KEY>"
 *       }
 *     }
 *   }
 * }
 *
 * 转换后的 Package:
 * {
 *   "registry_name": "npm",
 *   "name": "@amap/amap-maps-mcp-server",
 *   "version": "latest",
 *   "runtime_hint": "npx",
 *   "runtime_arguments": [
 *     {
 *       "type": "positional",
 *       "value": "-y",
 *       "format": "string"
 *     }
 *   ],
 *   "environment_variables": [
 *     {
 *       "name": "AMAP_MAPS_API_KEY",
 *       "value": "<API_KEY>",
 *       "format": "string"
 *     }
 *   ]
 * }
 *
 * 输入的 Server Config (格式2 - 完整命令行):
 * {
 *   "mcpServers": {
 *     "tavily-remote-mcp": {
 *       "command": "npx -y mcp-remote https://mcp.tavily.com/mcp/?tavilyApiKey=tvly-dev-xxx",
 *       "env": {}
 *     }
 *   }
 * }
 *
 * 转换后的 Package:
 * {
 *   "registry_name": "npm",
 *   "name": "mcp-remote",
 *   "version": "latest",
 *   "runtime_hint": "npx",
 *   "runtime_arguments": [
 *     {
 *       "type": "positional",
 *       "value": "-y",
 *       "format": "string"
 *     },
 *     {
 *       "type": "positional",
 *       "value": "mcp-remote",
 *       "format": "string"
 *     },
 *     {
 *       "type": "positional",
 *       "value": "https://mcp.tavily.com/mcp/?tavilyApiKey=tvly-dev-xxx",
 *       "format": "string"
 *     }
 *   ],
 *   "environment_variables": []
 * }
 *
 * 另一个示例，带版本号：
 * 输入:
 * {
 *   "mcpServers": {
 *     "filesystem": {
 *       "command": "uvx",
 *       "args": ["mcp-server-filesystem@1.0.2", "--path", "/tmp"]
 *     }
 *   }
 * }
 *
 * 转换后:
 * {
 *   "registry_name": "pypi",
 *   "name": "mcp-server-filesystem",
 *   "version": "1.0.2",
 *   "runtime_hint": "uvx",
 *   "runtime_arguments": [
 *     {
 *       "type": "positional",
 *       "value": "mcp-server-filesystem@1.0.2",
 *       "format": "string"
 *     }
 *   ],
 *   "package_arguments": [
 *     {
 *       "type": "positional",
 *       "value": "--path",
 *       "format": "string"
 *     },
 *     {
 *       "type": "positional",
 *       "value": "/tmp",
 *       "format": "string"
 *     }
 *   ]
 * }
 */
