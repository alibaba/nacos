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
  Message,
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

  // 复制内容到剪贴板
  copyToClipboard = async text => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        // 使用现代的 Clipboard API
        await navigator.clipboard.writeText(text);
      } else {
        // 回退到传统方法
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        textArea.style.top = '-9999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }

      Message.success('配置已复制到剪贴板');
    } catch (err) {
      console.error('复制失败:', err);
      Message.error('复制失败，请手动复制');
    }
  };

  // 将Package定义转换为MCP Server配置
  convertPackageToMcpConfig = packageDef => {
    if (!packageDef || !packageDef.name) {
      return null;
    }

    const config = {
      mcpServers: {},
    };

    // 构建服务器名称，使用包名
    const serverName = packageDef.name.replace(/[^a-zA-Z0-9-_]/g, '-');

    const serverConfig = {};

    // 处理运行时命令
    if (packageDef.runtime_hint) {
      serverConfig.command = packageDef.runtime_hint;
    } else if (packageDef.registry_name === 'npm') {
      serverConfig.command = 'npx';
    } else {
      // 默认命令根据注册表类型推断
      const registryCommands = {
        npm: 'npx',
        pip: 'python',
        docker: 'docker',
        uv: 'uvx',
        dnx: 'dnx',
      };
      serverConfig.command = registryCommands[packageDef.registry_name] || 'npx';
    }

    // 构建参数数组
    const args = [];

    // 添加运行时参数
    if (packageDef.runtime_arguments && Array.isArray(packageDef.runtime_arguments)) {
      packageDef.runtime_arguments.forEach(arg => {
        args.push(...this.processArgument(arg));
      });
    }

    // 添加包名和版本（根据不同的注册表类型处理）
    if (packageDef.registry_name === 'npm' && serverConfig.command === 'npx') {
      args.push('-y'); // 自动确认安装
      if (packageDef.version && packageDef.version !== 'latest') {
        args.push(`${packageDef.name}@${packageDef.version}`);
      } else {
        args.push(packageDef.name);
      }
    } else if (packageDef.registry_name === 'docker') {
      args.push('run', '--rm', '-i');
      if (packageDef.version && packageDef.version !== 'latest') {
        args.push(`${packageDef.name}:${packageDef.version}`);
      } else {
        args.push(packageDef.name);
      }
    } else if (packageDef.registry_name === 'pip' || packageDef.registry_name === 'uv') {
      args.push('-m');
      args.push(packageDef.name.split('/').pop()); // 取包名的最后部分
    } else {
      args.push(packageDef.name);
      if (packageDef.version && packageDef.version !== 'latest') {
        args.push(packageDef.version);
      }
    }

    // 添加包参数
    if (packageDef.package_arguments && Array.isArray(packageDef.package_arguments)) {
      packageDef.package_arguments.forEach(arg => {
        args.push(...this.processArgument(arg));
      });
    }

    serverConfig.args = args;

    // 处理环境变量
    if (packageDef.environment_variables && Array.isArray(packageDef.environment_variables)) {
      const env = {};
      packageDef.environment_variables.forEach(envVar => {
        if (envVar.name) {
          let value = envVar.value || envVar.default;
          if (!value) {
            // 根据变量名提供更友好的占位符
            if (envVar.name.includes('API_KEY') || envVar.name.includes('TOKEN')) {
              value = `YOUR_${envVar.name}_HERE`;
            } else if (envVar.name.includes('URL')) {
              value = 'https://api.example.com';
            } else if (envVar.name.includes('PORT')) {
              value = '3000';
            } else {
              value = `<${envVar.name}>`;
            }
          }

          // 替换变量占位符
          if (envVar.variables) {
            value = this.replaceVariables(value, envVar.variables);
          }

          env[envVar.name] = value;
        }
      });
      if (Object.keys(env).length > 0) {
        serverConfig.env = env;
      }
    }

    // 添加描述
    if (packageDef.description) {
      serverConfig.description = packageDef.description;
    } else {
      serverConfig.description = `MCP Server for ${packageDef.name}`;
    }

    config.mcpServers[serverName] = serverConfig;
    return config;
  };

  // 处理单个参数
  processArgument = arg => {
    if (!arg || !arg.type) {
      return [];
    }

    const result = [];

    switch (arg.type) {
      case 'positional':
        if (arg.value) {
          result.push(this.replaceVariables(arg.value, arg.variables));
        } else if (arg.value_hint) {
          result.push(`<${arg.value_hint}>`);
        } else if (arg.default) {
          result.push(this.replaceVariables(arg.default, arg.variables));
        }
        break;

      case 'named':
        if (arg.name) {
          if (arg.value) {
            // 支持不同的命名参数格式
            if (arg.value === true || arg.value === 'true') {
              result.push(arg.name); // 布尔标志
            } else {
              result.push(`${arg.name}=${this.replaceVariables(arg.value, arg.variables)}`);
            }
          } else if (arg.default) {
            if (arg.default === true || arg.default === 'true') {
              result.push(arg.name);
            } else {
              result.push(`${arg.name}=${this.replaceVariables(arg.default, arg.variables)}`);
            }
          } else {
            result.push(`${arg.name}=<value>`);
          }
        }
        break;

      default:
        // 处理其他类型的参数
        if (arg.value) {
          result.push(this.replaceVariables(arg.value, arg.variables));
        } else if (arg.default) {
          result.push(this.replaceVariables(arg.default, arg.variables));
        }
        break;
    }

    return result;
  };

  // 替换变量占位符
  replaceVariables = (value, variables) => {
    if (!value || !variables) {
      return value;
    }

    let result = value;
    Object.keys(variables).forEach(key => {
      const placeholder = `{${key}}`;
      if (result.includes(placeholder)) {
        const variableValue = variables[key].value || variables[key].default || `<${key}>`;
        result = result.replace(new RegExp(placeholder, 'g'), variableValue);
      }
    });

    return result;
  };

  // 渲染单个Package的详细信息
  renderPackageDetails = (packageDef, index) => {
    const { locale = {} } = this.props;

    return (
      <div
        style={{
          border: '1px solid #e6e6e6',
          borderRadius: '8px',
          padding: '20px',
          backgroundColor: '#fafafa',
          marginBottom: '16px',
        }}
      >
        {/* 基本信息 */}
        <div style={{ marginBottom: '24px' }}>
          <h3
            style={{
              color: '#000',
              marginBottom: '16px',
              borderBottom: '2px solid #1890ff',
              paddingBottom: '8px',
            }}
          >
            {locale.basicInformation || '基本信息'}
          </h3>
          <Row wrap style={{ textAlign: 'left' }}>
            <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                {locale.packageName || '包名'}:
              </p>
              {(() => {
                const repositoryUrl = this.getPackageRepositoryUrl(packageDef);
                if (repositoryUrl) {
                  return (
                    <a
                      href={repositoryUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        fontFamily: 'monospace',
                        backgroundColor: '#f5f5f5',
                        padding: '2px 6px',
                        borderRadius: '3px',
                        color: '#1890ff',
                        textDecoration: 'none',
                      }}
                      onMouseEnter={e => {
                        e.target.style.backgroundColor = '#e8f4fd';
                        e.target.style.textDecoration = 'underline';
                      }}
                      onMouseLeave={e => {
                        e.target.style.backgroundColor = '#f5f5f5';
                        e.target.style.textDecoration = 'none';
                      }}
                    >
                      {packageDef.name}
                    </a>
                  );
                } else {
                  return (
                    <p
                      style={{
                        fontFamily: 'monospace',
                        backgroundColor: '#f5f5f5',
                        padding: '2px 6px',
                        borderRadius: '3px',
                        color: '#000',
                      }}
                    >
                      {packageDef.name}
                    </p>
                  );
                }
              })()}
            </Col>
            <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                {locale.version || '版本'}:
              </p>
              <p
                style={{
                  fontFamily: 'monospace',
                  backgroundColor: '#f5f5f5',
                  padding: '2px 6px',
                  borderRadius: '3px',
                  color: '#000',
                }}
              >
                {packageDef.version || 'latest'}
              </p>
            </Col>
            <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                {locale.registryType || '注册表类型'}:
              </p>
              <p
                style={{
                  backgroundColor: this.getRegistryColor(packageDef.registry_name),
                  color: 'white',
                  padding: '2px 8px',
                  borderRadius: '12px',
                  fontSize: '12px',
                  fontWeight: 'bold',
                }}
              >
                {packageDef.registry_name}
              </p>
            </Col>
            {packageDef.runtime_hint && (
              <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                  {locale.runtimeHint || '运行时提示'}:
                </p>
                <p
                  style={{
                    fontFamily: 'monospace',
                    backgroundColor: '#f5f5f5',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    color: '#000',
                  }}
                >
                  {packageDef.runtime_hint}
                </p>
              </Col>
            )}
            {packageDef.description && (
              <Col span={24} style={{ display: 'flex', marginBottom: '8px' }}>
                <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                  {locale.description || '描述'}:
                </p>
                <p style={{ color: '#000' }}>{packageDef.description}</p>
              </Col>
            )}
          </Row>
        </div>

        {/* 运行时参数 */}
        {packageDef.runtime_arguments && packageDef.runtime_arguments.length > 0 && (
          <div style={{ marginBottom: '24px' }}>
            <h4
              style={{
                color: '#000',
                marginBottom: '12px',
                borderBottom: '1px solid #d9d9d9',
                paddingBottom: '4px',
              }}
            >
              {locale.runtimeArguments || '运行时参数'}
            </h4>
            <div style={{ marginLeft: '16px' }}>
              {packageDef.runtime_arguments.map((arg, argIndex) => (
                <div
                  key={argIndex}
                  style={{
                    marginBottom: '12px',
                    padding: '12px',
                    border: '1px solid #e6e6e6',
                    borderRadius: '6px',
                    backgroundColor: '#ffffff',
                  }}
                >
                  <Row gutter={16} style={{ alignItems: 'center' }}>
                    <Col span={4}>
                      <span
                        style={{
                          backgroundColor: arg.type === 'positional' ? '#52c41a' : '#1890ff',
                          color: 'white',
                          padding: '2px 8px',
                          borderRadius: '12px',
                          fontSize: '12px',
                          fontWeight: 'bold',
                        }}
                      >
                        {arg.type === 'positional' ? '位置参数' : '命名参数'}
                      </span>
                    </Col>
                    <Col span={8}>
                      <p
                        style={{
                          fontFamily: 'monospace',
                          backgroundColor: '#f5f5f5',
                          padding: '4px 8px',
                          borderRadius: '3px',
                          margin: 0,
                          color: '#000',
                        }}
                      >
                        {arg.value || arg.default || '<未设置>'}
                      </p>
                    </Col>
                    <Col span={12}>
                      <p style={{ color: '#000', margin: 0, fontSize: '13px' }}>
                        {arg.description || '无描述'}
                      </p>
                    </Col>
                  </Row>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 包参数 */}
        {packageDef.package_arguments && packageDef.package_arguments.length > 0 && (
          <div style={{ marginBottom: '24px' }}>
            <h4
              style={{
                color: '#000',
                marginBottom: '12px',
                borderBottom: '1px solid #d9d9d9',
                paddingBottom: '4px',
              }}
            >
              {locale.packageArguments || '包参数'}
            </h4>
            <div style={{ marginLeft: '16px' }}>
              {packageDef.package_arguments.map((arg, argIndex) => (
                <div
                  key={argIndex}
                  style={{
                    marginBottom: '12px',
                    padding: '12px',
                    border: '1px solid #e6e6e6',
                    borderRadius: '6px',
                    backgroundColor: '#ffffff',
                  }}
                >
                  <Row gutter={16} style={{ alignItems: 'center' }}>
                    <Col span={4}>
                      <span
                        style={{
                          backgroundColor: arg.type === 'positional' ? '#52c41a' : '#1890ff',
                          color: 'white',
                          padding: '2px 8px',
                          borderRadius: '12px',
                          fontSize: '12px',
                          fontWeight: 'bold',
                        }}
                      >
                        {arg.type === 'positional' ? '位置参数' : '命名参数'}
                      </span>
                    </Col>
                    <Col span={8}>
                      <p
                        style={{
                          fontFamily: 'monospace',
                          backgroundColor: '#f5f5f5',
                          padding: '4px 8px',
                          borderRadius: '3px',
                          margin: 0,
                          color: '#000',
                        }}
                      >
                        {arg.name
                          ? `${arg.name}=${arg.value || arg.default || '<value>'}`
                          : arg.value || arg.default || '<未设置>'}
                      </p>
                    </Col>
                    <Col span={12}>
                      <p style={{ color: '#000', margin: 0, fontSize: '13px' }}>
                        {arg.description || '无描述'}
                      </p>
                    </Col>
                  </Row>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 环境变量 */}
        {packageDef.environment_variables && packageDef.environment_variables.length > 0 && (
          <div style={{ marginBottom: '16px' }}>
            <h4
              style={{
                color: '#000',
                marginBottom: '12px',
                borderBottom: '1px solid #d9d9d9',
                paddingBottom: '4px',
              }}
            >
              {locale.environmentVariables || '环境变量'}
            </h4>
            <div style={{ marginLeft: '16px' }}>
              {packageDef.environment_variables.map((envVar, envIndex) => (
                <div
                  key={envIndex}
                  style={{
                    marginBottom: '12px',
                    padding: '12px',
                    border: '1px solid #e6e6e6',
                    borderRadius: '6px',
                    backgroundColor: '#ffffff',
                  }}
                >
                  <Row gutter={16} style={{ alignItems: 'center', marginBottom: '8px' }}>
                    <Col span={6}>
                      <p
                        style={{
                          fontFamily: 'monospace',
                          backgroundColor: '#f5f5f5',
                          padding: '4px 8px',
                          borderRadius: '3px',
                          margin: 0,
                          fontWeight: 'bold',
                          color: '#000',
                        }}
                      >
                        {envVar.name}
                      </p>
                    </Col>
                    <Col span={8}>
                      <p
                        style={{
                          fontFamily: 'monospace',
                          backgroundColor: '#f0f0f0',
                          padding: '4px 8px',
                          borderRadius: '3px',
                          margin: 0,
                          color: '#000',
                        }}
                      >
                        {envVar.value || envVar.default || '<未设置>'}
                      </p>
                    </Col>
                    <Col span={6}>
                      <div style={{ display: 'flex', gap: '8px' }}>
                        {envVar.is_required && (
                          <span
                            style={{
                              backgroundColor: '#ff4d4f',
                              color: 'white',
                              padding: '2px 6px',
                              borderRadius: '10px',
                              fontSize: '11px',
                              fontWeight: 'bold',
                            }}
                          >
                            必填
                          </span>
                        )}
                        {envVar.is_secret && (
                          <span
                            style={{
                              backgroundColor: '#faad14',
                              color: 'white',
                              padding: '2px 6px',
                              borderRadius: '10px',
                              fontSize: '11px',
                              fontWeight: 'bold',
                            }}
                          >
                            敏感
                          </span>
                        )}
                      </div>
                    </Col>
                    <Col span={4}>
                      <p style={{ color: '#000', margin: 0, fontSize: '13px' }}>
                        {envVar.description || '无描述'}
                      </p>
                    </Col>
                  </Row>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  };

  // 获取格式类型对应的颜色
  getFormatColor = format => {
    const colors = {
      string: '#52c41a',
      number: '#1890ff',
      boolean: '#722ed1',
      filepath: '#fa8c16',
    };
    return colors[format] || '#666666';
  };

  // 获取注册表类型对应的颜色
  getRegistryColor = registryType => {
    const colors = {
      npm: '#cb3837',
      docker: '#2496ed',
      pip: '#3776ab',
      uv: '#6b73ff',
      dnx: '#512bd4',
    };
    return colors[registryType] || '#666666';
  };

  // 获取包名对应的仓库链接
  getPackageRepositoryUrl = packageDef => {
    const { registry_name, name } = packageDef;

    switch (registry_name) {
      case 'npm':
        return `https://www.npmjs.com/package/${name}`;
      case 'docker':
        // Docker Hub 链接
        return `https://hub.docker.com/r/${name}`;
      case 'pip':
        // PyPI 链接
        return `https://pypi.org/project/${name}/`;
      case 'uv':
        // UV 通常也是 PyPI 包
        return `https://pypi.org/project/${name}/`;
      case 'dnx':
        // NuGet 链接
        return `https://www.nuget.org/packages/${name}/`;
      default:
        return null;
    }
  };

  render() {
    const localServerConfig = JSON.stringify(this.state.serverConfig?.localServerConfig, null, 2);
    const { locale = {} } = this.props;
    const versions = this.state.serverConfig?.allVersions
      ? this.state.serverConfig?.allVersions
      : [];

    // 示例Headers数据（用于测试展示非stdio协议的Headers配置）
    const exampleHeaders = [
      {
        name: 'Authorization',
        value: 'Bearer ${API_TOKEN}',
        default: 'Bearer your-api-token-here',
        description: 'API认证令牌',
        is_required: true,
        is_secret: true,
        format: 'string',
      },
      {
        name: 'Content-Type',
        value: 'application/json',
        description: '请求内容类型',
        is_required: false,
        is_secret: false,
        format: 'string',
        choices: ['application/json', 'application/xml', 'text/plain'],
      },
      {
        name: 'X-API-Version',
        value: '1.0',
        default: '1.0',
        description: 'API版本号',
        is_required: false,
        is_secret: false,
        format: 'string',
      },
    ];

    // 如果是非stdio协议且没有headers数据，添加示例headers（仅用于演示）
    if (
      this.state.serverConfig?.protocol !== 'stdio' &&
      (!this.state.serverConfig?.headers || this.state.serverConfig.headers.length === 0)
    ) {
      // 为了演示效果，临时添加示例headers
      if (this.state.serverConfig) {
        this.state.serverConfig.headers = exampleHeaders;
      }
    }

    // 示例Package数据（用于测试展示）
    const examplePackages = {
      'brave-search': {
        registry_name: 'npm',
        name: '@modelcontextprotocol/server-brave-search',
        version: '1.0.2',
        runtime_hint: 'npx',
        description: 'MCP Server for Brave Search API',
        environment_variables: [
          {
            name: 'BRAVE_API_KEY',
            description: 'API key for Brave Search',
            is_secret: true,
            is_required: true,
          },
        ],
      },
      filesystem: {
        registry_name: 'npm',
        name: 'io.modelcontextprotocol/filesystem',
        version: '1.0.2',
        runtime_hint: 'npx',
        description: 'MCP Server for filesystem operations',
        package_arguments: [
          {
            type: 'positional',
            value_hint: 'base_path',
            description: 'Base path for filesystem operations',
            value: '/tmp',
          },
        ],
      },
      'docker-example': {
        registry_name: 'docker',
        name: 'mcpserver/example',
        version: 'latest',
        description: 'Docker-based MCP Server',
        environment_variables: [
          {
            name: 'CONFIG_PATH',
            value: '/app/config',
          },
        ],
        package_arguments: [
          {
            type: 'named',
            name: '--port',
            value: '8080',
          },
        ],
      },
    };

    // 如果没有packageDef但有示例数据，可以选择展示示例
    let packagesToShow = [];

    // 如果服务器配置中有packages数组，使用它
    if (this.state.serverConfig?.packages && Array.isArray(this.state.serverConfig.packages)) {
      packagesToShow = this.state.serverConfig.packages;
    }
    // 如果有单个packageDef，转为数组
    else if (this.state.serverConfig?.packageDef) {
      packagesToShow = [this.state.serverConfig.packageDef];
    }
    // 否则根据服务器名称匹配示例Package（用于演示）
    else if (this.state.serverConfig?.name) {
      const serverName = this.state.serverConfig.name.toLowerCase();
      if (serverName.includes('brave')) {
        packagesToShow = [examplePackages['brave-search']];
      } else if (serverName.includes('filesystem')) {
        packagesToShow = [examplePackages['filesystem']];
      } else if (serverName.includes('docker')) {
        packagesToShow = [examplePackages['docker-example']];
      } else if (serverName.includes('multi') || serverName.includes('example')) {
        // 展示多个package的示例
        packagesToShow = [
          examplePackages['brave-search'],
          examplePackages['filesystem'],
          examplePackages['docker-example'],
        ];
      }
    }

    // 构建Package配置数组（类似endpoints的处理方式）
    const packageConfigs = [];
    for (let i = 0; i < packagesToShow.length; i++) {
      const packageDef = packagesToShow[i];
      // 简化包名用于Tab标题
      const shortName = packageDef.name.split('/').pop() || packageDef.name;
      const packageConfig = {
        index: i,
        packageName: `${packageDef.name}@${packageDef.version}`,
        shortTitle: `${shortName}@${packageDef.version}`,
        registryType: packageDef.registry_name,
        description: packageDef.description,
        mcpConfig: this.convertPackageToMcpConfig(packageDef),
      };
      packageConfigs.push(packageConfig);
    }

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

    let restToMcpBackendProtocol = 'off';
    if (
      this.state?.serverConfig?.protocol === 'https' ||
      this.state?.serverConfig?.protocol === 'http'
    ) {
      restToMcpBackendProtocol = this.state?.serverConfig?.protocol;
    }

    const endpoints = [];
    let serverReturnEndpoints = [];
    if (restToMcpBackendProtocol === 'off') {
      serverReturnEndpoints = this.state?.serverConfig?.backendEndpoints;
    } else {
      serverReturnEndpoints = this.state?.serverConfig?.frontendEndpoints;
    }

    for (let i = 0; i < serverReturnEndpoints?.length; i++) {
      const item = serverReturnEndpoints[i];
      const endpoint = item.address + ':' + item.port + item.path;
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
              <span>{locale.version}</span>
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
                    label: locale.serviceRef,
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

          {/* Security Schemes 展示 - 只在非 stdio 协议且有数据时显示 */}
          {this.state.serverConfig?.protocol !== 'stdio' &&
            this.state.serverConfig?.toolSpec?.securitySchemes?.length > 0 && (
              <>
                <Divider></Divider>
                <h2
                  style={{
                    color: '#333',
                    fontWeight: 'bold',
                  }}
                >
                  {locale.backendServiceAuth || '后端服务认证方式'}
                </h2>
                <div style={{ marginTop: '16px' }}>
                  {this.state.serverConfig.toolSpec.securitySchemes.map((scheme, index) => (
                    <div
                      key={index}
                      style={{
                        border: '1px solid #e6e6e6',
                        borderRadius: '4px',
                        padding: '16px',
                        marginBottom: '12px',
                        backgroundColor: '#fafafa',
                      }}
                    >
                      <Row wrap style={{ textAlign: 'left' }}>
                        <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                          <p style={{ minWidth: 120, fontWeight: 'bold' }}>
                            {locale.authType || '认证类型'}:
                          </p>
                          <p>{scheme.type}</p>
                        </Col>
                        {scheme.scheme && (
                          <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                            <p style={{ minWidth: 120, fontWeight: 'bold' }}>
                              {locale.authScheme || '认证方案'}:
                            </p>
                            <p>{scheme.scheme}</p>
                          </Col>
                        )}
                        {scheme.in && (
                          <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                            <p style={{ minWidth: 120, fontWeight: 'bold' }}>
                              {locale.keyLocation || '密钥位置'}:
                            </p>
                            <p>{scheme.in}</p>
                          </Col>
                        )}
                        {scheme.name && (
                          <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                            <p style={{ minWidth: 120, fontWeight: 'bold' }}>
                              {locale.keyName || '密钥名称'}:
                            </p>
                            <p>{scheme.name}</p>
                          </Col>
                        )}
                        {scheme.defaultCredential && (
                          <Col span={24} style={{ display: 'flex', marginBottom: '8px' }}>
                            <p style={{ minWidth: 120, fontWeight: 'bold' }}>
                              {locale.defaultCredential || '默认凭证'}:
                            </p>
                            <p
                              style={{
                                wordBreak: 'break-all',
                                fontFamily: 'monospace',
                                backgroundColor: '#f5f5f5',
                                padding: '4px 8px',
                                borderRadius: '3px',
                              }}
                            >
                              {scheme.defaultCredential}
                            </p>
                          </Col>
                        )}
                      </Row>
                    </div>
                  ))}
                </div>
              </>
            )}

          {this.state.serverConfig?.protocol === 'stdio' && (
            <>
              <Divider></Divider>
              <h2>Server Config</h2>
              {packageConfigs?.length > 0 ? (
                packageConfigs.length === 1 ? (
                  // 单个Package的展示
                  <div>
                    <pre
                      style={{
                        cursor: 'pointer',
                        border: '1px solid #ddd',
                        borderRadius: '4px',
                        padding: '12px',
                        backgroundColor: '#f8f8f8',
                        position: 'relative',
                        transition: 'all 0.2s ease',
                      }}
                      onClick={() =>
                        this.copyToClipboard(JSON.stringify(packageConfigs[0].mcpConfig, null, 2))
                      }
                      onMouseEnter={e => {
                        e.target.style.backgroundColor = '#e8f4fd';
                        e.target.style.borderColor = '#1890ff';
                        e.target.style.boxShadow = '0 2px 8px rgba(24, 144, 255, 0.15)';
                      }}
                      onMouseLeave={e => {
                        e.target.style.backgroundColor = '#f8f8f8';
                        e.target.style.borderColor = '#ddd';
                        e.target.style.boxShadow = 'none';
                      }}
                      title="点击复制配置"
                    >
                      {JSON.stringify(packageConfigs[0].mcpConfig, null, 2)}
                    </pre>
                  </div>
                ) : (
                  // 多个Package的Tab展示
                  <Tab excessMode="dropdown" defaultActiveKey={0}>
                    {packageConfigs.map(item => (
                      <Tab.Item
                        key={item.index}
                        title={`${item.shortTitle} (${item.registryType})`}
                      >
                        <pre
                          style={{
                            cursor: 'pointer',
                            border: '1px solid #ddd',
                            borderRadius: '4px',
                            padding: '12px',
                            backgroundColor: '#f8f8f8',
                            position: 'relative',
                            transition: 'all 0.2s ease',
                          }}
                          onClick={() =>
                            this.copyToClipboard(JSON.stringify(item.mcpConfig, null, 2))
                          }
                          onMouseEnter={e => {
                            e.target.style.backgroundColor = '#e8f4fd';
                            e.target.style.borderColor = '#1890ff';
                            e.target.style.boxShadow = '0 2px 8px rgba(24, 144, 255, 0.15)';
                          }}
                          onMouseLeave={e => {
                            e.target.style.backgroundColor = '#f8f8f8';
                            e.target.style.borderColor = '#ddd';
                            e.target.style.boxShadow = 'none';
                          }}
                          title="点击复制配置"
                        >
                          {JSON.stringify(item.mcpConfig, null, 2)}
                        </pre>
                      </Tab.Item>
                    ))}
                  </Tab>
                )
              ) : (
                // 原有的localServerConfig显示
                <pre
                  style={{
                    cursor: 'pointer',
                    border: '1px solid #ddd',
                    borderRadius: '4px',
                    padding: '12px',
                    backgroundColor: '#f8f8f8',
                    transition: 'all 0.2s ease',
                  }}
                  onClick={() => this.copyToClipboard(localServerConfig)}
                  onMouseEnter={e => {
                    e.target.style.backgroundColor = '#e8f4fd';
                    e.target.style.borderColor = '#1890ff';
                    e.target.style.boxShadow = '0 2px 8px rgba(24, 144, 255, 0.15)';
                  }}
                  onMouseLeave={e => {
                    e.target.style.backgroundColor = '#f8f8f8';
                    e.target.style.borderColor = '#ddd';
                    e.target.style.boxShadow = 'none';
                  }}
                  title="点击复制配置"
                >
                  {localServerConfig}
                </pre>
              )}
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

          {/* Headers 配置展示 - 只在非 stdio 协议且有 Headers 数据时显示 */}
          {this.state.serverConfig?.protocol !== 'stdio' &&
            this.state.serverConfig?.headers?.length > 0 && (
              <>
                <Divider></Divider>
                <h2
                  style={{
                    color: '#333',
                    fontWeight: 'bold',
                  }}
                >
                  {locale.httpHeaders || 'HTTP Headers 配置'}
                </h2>
                <div style={{ marginTop: '16px' }}>
                  {this.state.serverConfig.headers.map((header, index) => (
                    <div
                      key={index}
                      style={{
                        border: '1px solid #e6e6e6',
                        borderRadius: '8px',
                        padding: '16px',
                        marginBottom: '12px',
                        backgroundColor: '#fafafa',
                      }}
                    >
                      <Row wrap style={{ textAlign: 'left' }}>
                        <Col span={8} style={{ display: 'flex', marginBottom: '8px' }}>
                          <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                            {locale.headerName || 'Header 名称'}:
                          </p>
                          <p
                            style={{
                              fontFamily: 'monospace',
                              backgroundColor: '#f5f5f5',
                              padding: '2px 6px',
                              borderRadius: '3px',
                              color: '#000',
                            }}
                          >
                            {header.name}
                          </p>
                        </Col>
                        <Col span={8} style={{ display: 'flex', marginBottom: '8px' }}>
                          <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                            {locale.headerValue || 'Header 值'}:
                          </p>
                          <p
                            style={{
                              fontFamily: 'monospace',
                              backgroundColor: '#f5f5f5',
                              padding: '2px 6px',
                              borderRadius: '3px',
                              color: '#000',
                            }}
                          >
                            {header.value || header.default || '<未设置>'}
                          </p>
                        </Col>
                        <Col span={8} style={{ display: 'flex', marginBottom: '8px' }}>
                          <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                            {locale.format || '格式类型'}:
                          </p>
                          <p
                            style={{
                              backgroundColor: this.getFormatColor(header.format),
                              color: 'white',
                              padding: '2px 8px',
                              borderRadius: '12px',
                              fontSize: '12px',
                              fontWeight: 'bold',
                            }}
                          >
                            {header.format || 'string'}
                          </p>
                        </Col>
                      </Row>

                      {(header.description || header.default) && (
                        <Row wrap style={{ textAlign: 'left', marginTop: '8px' }}>
                          {header.description && (
                            <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                                {locale.description || '描述'}:
                              </p>
                              <p style={{ color: '#000' }}>{header.description}</p>
                            </Col>
                          )}
                          {header.default && (
                            <Col span={12} style={{ display: 'flex', marginBottom: '8px' }}>
                              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                                {locale.defaultValue || '默认值'}:
                              </p>
                              <p
                                style={{
                                  fontFamily: 'monospace',
                                  backgroundColor: '#f0f0f0',
                                  padding: '2px 6px',
                                  borderRadius: '3px',
                                  color: '#000',
                                }}
                              >
                                {header.default}
                              </p>
                            </Col>
                          )}
                        </Row>
                      )}

                      {(header.is_required ||
                        header.is_secret ||
                        (header.choices && header.choices.length > 0)) && (
                        <Row wrap style={{ textAlign: 'left', marginTop: '8px' }}>
                          <Col span={8} style={{ display: 'flex', marginBottom: '8px' }}>
                            <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                              {locale.properties || '属性'}:
                            </p>
                            <div style={{ display: 'flex', gap: '8px' }}>
                              {header.is_required && (
                                <span
                                  style={{
                                    backgroundColor: '#ff4d4f',
                                    color: 'white',
                                    padding: '2px 6px',
                                    borderRadius: '10px',
                                    fontSize: '11px',
                                    fontWeight: 'bold',
                                  }}
                                >
                                  必填
                                </span>
                              )}
                              {header.is_secret && (
                                <span
                                  style={{
                                    backgroundColor: '#faad14',
                                    color: 'white',
                                    padding: '2px 6px',
                                    borderRadius: '10px',
                                    fontSize: '11px',
                                    fontWeight: 'bold',
                                  }}
                                >
                                  敏感信息
                                </span>
                              )}
                            </div>
                          </Col>
                          {header.choices && header.choices.length > 0 && (
                            <Col span={16} style={{ display: 'flex', marginBottom: '8px' }}>
                              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                                {locale.choices || '可选值'}:
                              </p>
                              <p
                                style={{
                                  fontFamily: 'monospace',
                                  backgroundColor: '#f5f5f5',
                                  padding: '2px 6px',
                                  borderRadius: '3px',
                                  color: '#000',
                                }}
                              >
                                {Array.isArray(header.choices)
                                  ? header.choices.join(', ')
                                  : header.choices}
                              </p>
                            </Col>
                          )}
                        </Row>
                      )}
                    </div>
                  ))}
                </div>
              </>
            )}

          {/* Package 详细信息展示 - 只在 stdio 协议且有 Package 数据时显示 */}
          {this.state.serverConfig?.protocol === 'stdio' && packagesToShow?.length > 0 && (
            <>
              <Divider></Divider>
              <h2>Package Details</h2>
              {packagesToShow.length === 1 ? (
                // 单个Package的详细信息展示
                <div style={{ marginTop: '16px' }}>
                  {this.renderPackageDetails(packagesToShow[0], 0)}
                </div>
              ) : (
                // 多个Package的Tab展示
                <Tab excessMode="dropdown" defaultActiveKey={0}>
                  {packagesToShow.map((packageDef, index) => {
                    const shortName = packageDef.name.split('/').pop() || packageDef.name;
                    return (
                      <Tab.Item
                        key={index}
                        title={`${shortName}@${packageDef.version} (${packageDef.registry_name})`}
                      >
                        <div style={{ marginTop: '16px' }}>
                          {this.renderPackageDetails(packageDef, index)}
                        </div>
                      </Tab.Item>
                    );
                  })}
                </Tab>
              )}
            </>
          )}

          <Divider></Divider>
          <h2>Tools</h2>
          <ShowTools
            restToMcpSwitch={restToMcpBackendProtocol}
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
