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
  Tree,
} from '@alifd/next';
import { getParams, request } from '../../../globalLib';
import PropTypes from 'prop-types';
import { generateUrl } from '../../../utils/nacosutil';
import ShowTools from './ShowTools';
import './McpDetail.css';

const KNOWN_SECURITY_FIELDS = ['id', 'passthrough', 'credential'];
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
      // 控制各个包的参数Tab展开状态
      packageTabsExpanded: {},
      // 控制每个Tool的参数展开状态
      toolParametersExpanded: {},
      // 控制参数类型容器的展开状态 - 格式: {packageIndex: {runtime: true, package: false, env: true}}
      parameterContainersExpanded: {},
      // 当前选中的Tool索引
      activeToolIndex: 0,
      defaultSecurityCollapsed: {
        downstream: true,
        upstream: true,
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
      <Row wrap className="mcp-form-row">
        {list.map((item, index) => {
          return (
            <Col key={item.label} span={12} className="mcp-form-col">
              <p className="mcp-label-with-min-width">{item.label}</p>
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

  // 切换包参数Tab的展开/收起状态
  togglePackageTabs = packageIndex => {
    this.setState(prevState => ({
      packageTabsExpanded: {
        ...prevState.packageTabsExpanded,
        [packageIndex]: !prevState.packageTabsExpanded[packageIndex],
      },
    }));
  };

  // 切换Tool参数的展开/收起状态
  toggleToolParameters = toolIndex => {
    this.setState(prevState => ({
      toolParametersExpanded: {
        ...prevState.toolParametersExpanded,
        [toolIndex]: !prevState.toolParametersExpanded[toolIndex],
      },
    }));
  };

  // 切换参数容器的展开/收起状态
  toggleParameterContainer = (packageIndex, containerType) => {
    this.setState(prevState => ({
      parameterContainersExpanded: {
        ...prevState.parameterContainersExpanded,
        [packageIndex]: {
          ...prevState.parameterContainersExpanded[packageIndex],
          [containerType]: !prevState.parameterContainersExpanded[packageIndex]?.[containerType],
        },
      },
    }));
  };

  toggleDefaultSecuritySection = sectionKey => {
    this.setState(prevState => ({
      defaultSecurityCollapsed: {
        ...prevState.defaultSecurityCollapsed,
        [sectionKey]: !prevState.defaultSecurityCollapsed?.[sectionKey],
      },
    }));
  };

  // 构建参数树形数据结构
  buildParameterTreeData = (properties, required = [], parentKey = '') => {
    if (!properties) return [];

    // 初始化参数映射表（如果还没有的话）
    if (!this.parameterMap) {
      this.parameterMap = new Map();
    }

    return Object.entries(properties).map(([paramName, paramDef], index) => {
      const nodeKey = parentKey ? `${parentKey}-${paramName}-${index}` : `${paramName}-${index}`;
      const isRequired = required.includes(paramName);
      const hasDefault = paramDef.default !== undefined;
      const paramType = paramDef.type || 'string';

      // 将参数信息存储到映射表中
      this.parameterMap.set(nodeKey, {
        name: paramName,
        type: paramType,
        description: paramDef.description || '',
        isRequired,
        hasDefault,
        defaultValue: paramDef.default,
        enum: paramDef.enum,
        format: paramDef.format,
        isParameterNode: true,
        originalDef: paramDef,
      });

      // 构建子节点（属性详情）
      const children = [];

      // 添加基本信息子节点
      if (paramDef.description) {
        const descKey = `${nodeKey}-desc`;
        this.parameterMap.set(descKey, {
          name: '描述',
          type: 'info',
          description: paramDef.description,
          isInfoNode: true,
        });
        children.push({
          key: descKey,
          label: `描述: ${paramDef.description}`,
          isLeaf: true,
        });
      }

      if (hasDefault) {
        const defaultKey = `${nodeKey}-default`;
        this.parameterMap.set(defaultKey, {
          name: '默认值',
          type: 'info',
          description: JSON.stringify(paramDef.default),
          isInfoNode: true,
        });
        children.push({
          key: defaultKey,
          label: `默认值: ${JSON.stringify(paramDef.default)}`,
          isLeaf: true,
        });
      }

      if (paramDef.enum) {
        const enumValue = Array.isArray(paramDef.enum) ? paramDef.enum.join(', ') : paramDef.enum;
        const enumKey = `${nodeKey}-enum`;
        this.parameterMap.set(enumKey, {
          name: '可选值',
          type: 'info',
          description: enumValue,
          isInfoNode: true,
        });
        children.push({
          key: enumKey,
          label: `可选值: ${enumValue}`,
          isLeaf: true,
        });
      }

      if (paramDef.format) {
        const formatKey = `${nodeKey}-format`;
        this.parameterMap.set(formatKey, {
          name: '格式',
          type: 'info',
          description: paramDef.format,
          isInfoNode: true,
        });
        children.push({
          key: formatKey,
          label: `格式: ${paramDef.format}`,
          isLeaf: true,
        });
      }

      // 递归处理object类型的属性
      if (paramType === 'object' && paramDef.properties) {
        const objectRequired = paramDef.required || [];
        const objectChildren = this.buildParameterTreeData(
          paramDef.properties,
          objectRequired,
          `${nodeKey}-props`
        );

        if (objectChildren.length > 0) {
          const propsKey = `${nodeKey}-properties`;
          this.parameterMap.set(propsKey, {
            name: '属性',
            type: 'group',
            description: '对象属性',
            isGroupNode: true,
          });
          children.push({
            key: propsKey,
            label: '属性',
            children: objectChildren,
            isLeaf: false,
          });
        }
      }

      // 递归处理array类型的属性
      if (paramType === 'array' && paramDef.items) {
        const arrayItemChildren = [];

        // 如果数组项是对象类型
        if (paramDef.items.type === 'object' && paramDef.items.properties) {
          const itemRequired = paramDef.items.required || [];
          const itemChildren = this.buildParameterTreeData(
            paramDef.items.properties,
            itemRequired,
            `${nodeKey}-items`
          );

          if (itemChildren.length > 0) {
            const itemPropsKey = `${nodeKey}-item-properties`;
            this.parameterMap.set(itemPropsKey, {
              name: '数组项属性',
              type: 'group',
              description: '数组项的属性',
              isGroupNode: true,
            });
            arrayItemChildren.push({
              key: itemPropsKey,
              label: '数组项属性',
              children: itemChildren,
              isLeaf: false,
            });
          }
        } else {
          // 基本类型的数组项
          const itemInfo = [];
          if (paramDef.items.type) {
            itemInfo.push(`类型: ${paramDef.items.type}`);
          }
          if (paramDef.items.description) {
            itemInfo.push(`描述: ${paramDef.items.description}`);
          }
          if (paramDef.items.format) {
            itemInfo.push(`格式: ${paramDef.items.format}`);
          }

          if (itemInfo.length > 0) {
            const itemInfoKey = `${nodeKey}-item-info`;
            this.parameterMap.set(itemInfoKey, {
              name: '数组项信息',
              type: 'info',
              description: itemInfo.join(', '),
              isInfoNode: true,
            });
            arrayItemChildren.push({
              key: itemInfoKey,
              label: `数组项信息: ${itemInfo.join(', ')}`,
              isLeaf: true,
            });
          }
        }

        if (arrayItemChildren.length > 0) {
          const itemsKey = `${nodeKey}-items`;
          this.parameterMap.set(itemsKey, {
            name: '数组项定义',
            type: 'group',
            description: '数组项的定义',
            isGroupNode: true,
          });
          children.push({
            key: itemsKey,
            label: '数组项定义',
            children: arrayItemChildren,
            isLeaf: false,
          });
        }
      }

      // 返回树节点
      const result = {
        key: nodeKey,
        label: paramName,
        children: children.length > 0 ? children : undefined,
        isLeaf: children.length === 0,
      };
      return result;
    });
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
    const pkgName = packageDef?.identifier || packageDef?.name;
    if (!packageDef || !pkgName) {
      return null;
    }

    const config = {
      mcpServers: {},
    };

    // 使用当前 MCP Server 的名称而不是包名
    let serverName = this.state.serverConfig?.name || 'mcp-server';
    // 如果服务器名称为空，使用默认名称
    if (!serverName || serverName.trim() === '') {
      serverName = 'mcp-server';
    }
    const serverConfig = {};
    const runtimeHint = packageDef.runtimeHint || packageDef.runtime_hint;
    if (runtimeHint) {
      serverConfig.command = runtimeHint;
    } else if (this.getRegistryType(packageDef) === 'npm') {
      serverConfig.command = 'npx';
    } else {
      // 默认命令根据注册表类型推断
      const registryCommands = {
        npm: 'npx',
        pip: 'python',
        docker: 'docker',
        uv: 'uvx',
        dnx: 'dnx',
        oci: 'docker',
      };
      serverConfig.command = registryCommands[this.getRegistryType(packageDef)] || 'npx';
    }

    // 构建参数数组
    const args = [];

    // 检查是否已经有runtime_arguments包含了包名
    let hasPackageInRuntimeArgs = false;
    const runtimeArguments = packageDef.runtimeArguments || packageDef.runtime_arguments || [];
    if (runtimeArguments && Array.isArray(runtimeArguments)) {
      for (const arg of runtimeArguments) {
        if (arg.value && arg.value.includes(pkgName)) {
          hasPackageInRuntimeArgs = true;
          break;
        }
      }
    }

    // 先添加运行时参数
    if (runtimeArguments && Array.isArray(runtimeArguments)) {
      runtimeArguments.forEach(arg => {
        args.push(...this.processArgument(arg));
      });
    }

    // 如果runtime_arguments中没有包含包名，则添加包名和版本
    if (!hasPackageInRuntimeArgs) {
      // 添加包名和版本（根据不同的注册表类型处理）
      if (this.getRegistryType(packageDef) === 'npm' && serverConfig.command === 'npx') {
        // 检查是否已经有 -y 参数
        if (!args.includes('-y')) {
          args.push('-y'); // 自动确认安装
        }
        if (packageDef.version && packageDef.version !== 'latest') {
          args.push(`${pkgName}@${packageDef.version}`);
        } else {
          args.push(pkgName);
        }
      } else if (
        this.getRegistryType(packageDef) === 'docker' ||
        this.getRegistryType(packageDef) === 'oci'
      ) {
        args.push('run', '--rm', '-i');
        if (packageDef.version && packageDef.version !== 'latest') {
          args.push(`${pkgName}:${packageDef.version}`);
        } else {
          args.push(pkgName);
        }
      } else if (
        this.getRegistryType(packageDef) === 'pip' ||
        this.getRegistryType(packageDef) === 'uv'
      ) {
        args.push('-m');
        args.push(pkgName.split('/').pop()); // 取包名的最后部分
      } else {
        args.push(pkgName);
        if (packageDef.version && packageDef.version !== 'latest') {
          args.push(packageDef.version);
        }
      }
    }

    // 添加包参数
    const packageArguments = packageDef.packageArguments || packageDef.package_arguments || [];
    if (packageArguments && Array.isArray(packageArguments)) {
      packageArguments.forEach(arg => {
        args.push(...this.processArgument(arg));
      });
    }

    serverConfig.args = args;

    // 处理环境变量
    const environmentVariables =
      packageDef.environmentVariables || packageDef.environment_variables || [];
    if (environmentVariables && Array.isArray(environmentVariables)) {
      const env = {};
      environmentVariables.forEach(envVar => {
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

    // 描述字段已移除：不再从 packageDef 读取，也不生成默认描述

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
        } else if (arg.value_hint || arg.valueHint) {
          result.push(`<${arg.value_hint || arg.valueHint}>`);
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
    const isTabsExpanded = this.state.packageTabsExpanded[index];

    // 统计各类参数数量
    const runtimeArguments = packageDef.runtimeArguments || packageDef.runtime_arguments || [];
    const packageArguments = packageDef.packageArguments || packageDef.package_arguments || [];
    const environmentVariables =
      packageDef.environmentVariables || packageDef.environment_variables || [];
    const runtimeArgsCount = runtimeArguments?.length || 0;
    const packageArgsCount = packageArguments?.length || 0;
    const envVarsCount = environmentVariables?.length || 0;
    const totalParamsCount = runtimeArgsCount + packageArgsCount + envVarsCount;

    return (
      <div className="mcp-card">
        {/* 基本信息 */}
        <div className="mcp-margin-bottom-24">
          <h3 className="mcp-subsection-title">{locale.basicInformation || '基本信息'}</h3>
          <Row wrap className="mcp-form-row-aligned">
            <Col span={24} className="mcp-form-col">
              <p className="mcp-label">{locale.packageName || '包名'}:</p>
              {(() => {
                const repositoryUrl = this.getPackageRepositoryUrl(packageDef);
                const displayName = this.getPackageName(packageDef);
                if (repositoryUrl) {
                  return (
                    <a
                      href={repositoryUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="mcp-package-link"
                    >
                      {displayName}
                    </a>
                  );
                } else {
                  return <p className="mcp-monospace">{displayName}</p>;
                }
              })()}
            </Col>
            <Col span={24} className="mcp-form-col">
              <p className="mcp-label">{locale.version || '版本'}:</p>
              <p className="mcp-monospace">{packageDef.version || '无'}</p>
            </Col>
            <Col span={24} className="mcp-form-col">
              <p className="mcp-label">{locale.registryType || '注册表类型'}:</p>
              <p
                className={`mcp-registry-badge mcp-registry-${this.getRegistryType(
                  packageDef
                ).toLowerCase()}`}
              >
                {this.getRegistryType(packageDef)}
              </p>
            </Col>
            {(packageDef.runtimeHint || packageDef.runtime_hint) && (
              <Col span={24} className="mcp-form-col">
                <p className="mcp-label">{locale.runtimeHint || '运行时提示'}:</p>
                <p className="mcp-monospace">{packageDef.runtimeHint || packageDef.runtime_hint}</p>
              </Col>
            )}
          </Row>
        </div>

        {/* 参数配置区域 - 只在有参数时显示 */}
        {totalParamsCount > 0 && (
          <div className="mcp-margin-bottom-16">
            <div className="mcp-param-container-flex">
              <h3 className="mcp-subsection-title-inline">
                {locale.parameterConfiguration || '参数配置'}
                <span className="mcp-param-count">(共 {totalParamsCount} 项)</span>
              </h3>
              <Button
                size="small"
                type="normal"
                onClick={() => this.togglePackageTabs(index)}
                className="mcp-button-expand"
              >
                {isTabsExpanded ? '收起' : '展开'}
              </Button>
            </div>

            {isTabsExpanded && (
              <div className="mcp-param-container">
                {/* 运行时参数容器 */}
                {runtimeArgsCount > 0 && (
                  <div className="mcp-margin-bottom-16">
                    <div
                      className="mcp-param-toggle mcp-param-toggle-runtime"
                      onClick={() => this.toggleParameterContainer(index, 'runtime')}
                    >
                      <div className="mcp-param-toggle-label">
                        <span className="mcp-param-toggle-label-runtime">
                          {locale.runtimeArguments || '运行时参数'}
                        </span>
                        <span className="mcp-param-toggle-count">({runtimeArgsCount})</span>
                      </div>
                      <span className="mcp-param-toggle-arrow-runtime">
                        {this.state.parameterContainersExpanded[index]?.runtime
                          ? '收起 ▲'
                          : '展开 ▼'}
                      </span>
                    </div>
                    {this.state.parameterContainersExpanded[index]?.runtime && (
                      <div className="mcp-param-padding">
                        {runtimeArguments.map((arg, argIndex) => (
                          <div
                            key={argIndex}
                            className={
                              argIndex < runtimeArguments.length - 1
                                ? 'mcp-param-item'
                                : 'mcp-param-item-last'
                            }
                          >
                            <div className="mcp-param-row">
                              <span className={`mcp-param-type-badge mcp-param-type-${arg.type}`}>
                                {arg.type === 'positional' ? '位置参数' : '命名参数'}
                              </span>
                              <span className="mcp-monospace-min-width">
                                {arg.value || arg.default || '<未设置>'}
                              </span>
                              <span className="mcp-description-small">
                                {arg.description || '无描述'}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 包参数容器 */}
                {packageArgsCount > 0 && (
                  <div className="mcp-margin-bottom-16">
                    <div
                      className="mcp-param-toggle mcp-param-toggle-package"
                      onClick={() => this.toggleParameterContainer(index, 'package')}
                    >
                      <div className="mcp-param-toggle-label">
                        <span className="mcp-param-toggle-label-package">
                          {locale.packageArguments || '包参数'}
                        </span>
                        <span className="mcp-param-toggle-count">({packageArgsCount})</span>
                      </div>
                      <span className="mcp-param-toggle-arrow-package">
                        {this.state.parameterContainersExpanded[index]?.package
                          ? '收起 ▲'
                          : '展开 ▼'}
                      </span>
                    </div>
                    {this.state.parameterContainersExpanded[index]?.package && (
                      <div className="mcp-param-padding">
                        {packageArguments.map((arg, argIndex) => (
                          <div
                            key={argIndex}
                            className={
                              argIndex < packageArguments.length - 1
                                ? 'mcp-param-item'
                                : 'mcp-param-item-last'
                            }
                          >
                            <div className="mcp-param-row">
                              <span className={`mcp-param-type-badge mcp-param-type-${arg.type}`}>
                                {arg.type === 'positional' ? '位置参数' : '命名参数'}
                              </span>
                              <span className="mcp-monospace-min-width">
                                {arg.name
                                  ? `${arg.name}=${arg.value || arg.default || '<value>'}`
                                  : arg.value || arg.default || '<未设置>'}
                              </span>
                              <span className="mcp-description-small">
                                {arg.description || '无描述'}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 环境变量容器 */}
                {envVarsCount > 0 && (
                  <div className="mcp-margin-bottom-16">
                    <div
                      className="mcp-param-toggle mcp-param-toggle-env"
                      onClick={() => this.toggleParameterContainer(index, 'env')}
                    >
                      <div className="mcp-param-toggle-label">
                        <span className="mcp-param-toggle-label-env">
                          {locale.environmentVariables || '环境变量'}
                        </span>
                        <span className="mcp-param-toggle-count">({envVarsCount})</span>
                      </div>
                      <span className="mcp-param-toggle-arrow-env">
                        {this.state.parameterContainersExpanded[index]?.env ? '收起 ▲' : '展开 ▼'}
                      </span>
                    </div>
                    {this.state.parameterContainersExpanded[index]?.env && (
                      <div className="mcp-param-padding">
                        {environmentVariables.map((envVar, envIndex) => (
                          <div key={envIndex} className="mcp-env-grid">
                            {/* 变量名标签 */}
                            <span className="mcp-env-label">变量名:</span>
                            {/* 变量名值 */}
                            <span className="mcp-monospace-value">{envVar.name}</span>

                            {/* 变量值标签 */}
                            <span className="mcp-env-label">变量值:</span>
                            {/* 变量值 */}
                            <span className="mcp-monospace-lighter">
                              {envVar.value || envVar.default || '<未设置>'}
                            </span>

                            {/* 标签标签 */}
                            <span className="mcp-env-label">标签:</span>
                            {/* 标签值 */}
                            <div className="mcp-env-tags">
                              {(envVar.isRequired || envVar.is_required) && (
                                <span className="mcp-badge-required">必填</span>
                              )}
                              {(envVar.isSecret || envVar.is_secret) && (
                                <span className="mcp-badge-secret">敏感</span>
                              )}
                              {!(
                                envVar.isRequired ||
                                envVar.is_required ||
                                envVar.isSecret ||
                                envVar.is_secret
                              ) && <span className="mcp-badge-no-label">无标签</span>}
                            </div>

                            {/* 描述标签 */}
                            {envVar.description && (
                              <>
                                <span className="mcp-env-label">描述:</span>
                                {/* 描述值 */}
                                <span className="mcp-env-description">{envVar.description}</span>
                              </>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
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
      oci: '#009688',
    };
    return colors[registryType] || '#666666';
  };

  // 注册表类型：优先 registry_type，兼容旧 registry_name
  getRegistryType = packageDef => {
    if (!packageDef) return '';
    return packageDef.registryType || packageDef.registry_type || packageDef.registry_name || '';
  };

  // 包名显示与链接用：优先 identifier，兼容旧 name
  getPackageName = packageDef => {
    if (!packageDef) return '';
    return packageDef.identifier || packageDef.name || '';
  };

  // 获取包名对应的仓库链接
  getPackageRepositoryUrl = packageDef => {
    const registryType = this.getRegistryType(packageDef);
    const name = (packageDef && (packageDef.identifier || packageDef.name)) || '';

    switch (registryType) {
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

  // 渲染 Headers 配置
  renderHeaders = (headers, locale) => {
    if (!headers || headers.length === 0) {
      return (
        <div className="mcp-margin-bottom-16">
          <div className="mcp-empty-state">
            <div>
              <div className="mcp-empty-icon">📋</div>
              <p className="mcp-empty-text">
                {locale.noHeadersAvailable || '该端点无 Headers 配置'}
              </p>
            </div>
          </div>
        </div>
      );
    }

    return (
      <div className="mcp-margin-bottom-16">
        {headers.map((header, index) => (
          <div key={index} className="mcp-card-small">
            {/* Header 名称行 */}
            <div className="mcp-header-display-row">
              <span className="mcp-label-small">{locale.headerName || 'Name'}:</span>
              <span className="mcp-monospace-badge">{header.name}</span>
              {(header.isRequired || header.is_required) && (
                <span className="mcp-badge-required-small">必填</span>
              )}
              {(header.isSecret || header.is_secret) && (
                <span className="mcp-badge-secret-small">敏感</span>
              )}
            </div>

            {/* Header 值行 */}
            <div className="mcp-header-display-row">
              <span className="mcp-label-small">{locale.headerValue || 'Value'}:</span>
              <span className="mcp-monospace">{header.value || header.default || '<未设置>'}</span>
            </div>

            {/* 格式类型行 */}
            <div className="mcp-header-display-row">
              <span className="mcp-label-small">{locale.format || 'Type'}:</span>
              <span className={`mcp-format-badge mcp-format-${header.format || 'string'}`}>
                {header.format || 'string'}
              </span>
            </div>

            {/* 描述行 */}
            {header.description && (
              <div className="mcp-header-display-row-flex-start">
                <span className="mcp-label-small">{locale.description || 'Desc'}:</span>
                <span className="mcp-description-text">{header.description}</span>
              </div>
            )}

            {/* 默认值行 */}
            {header.default && (
              <div className="mcp-header-display-row">
                <span className="mcp-label-small">{locale.defaultValue || 'Default'}:</span>
                <span className="mcp-monospace-lighter">{header.default}</span>
              </div>
            )}

            {/* 可选值行 */}
            {header.choices && header.choices.length > 0 && (
              <div className="mcp-header-display-row-full">
                <span className="mcp-label-small">{locale.choices || 'Choices'}:</span>
                <span className="mcp-monospace-badge-light">
                  {Array.isArray(header.choices) ? header.choices.join(', ') : header.choices}
                </span>
              </div>
            )}
          </div>
        ))}
      </div>
    );
  };

  render() {
    const localServerConfig = JSON.stringify(this.state.serverConfig?.localServerConfig, null, 2);
    const { locale = {} } = this.props;
    const versions = this.state.serverConfig?.allVersions
      ? this.state.serverConfig?.allVersions
      : [];

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
    // 构建Package配置数组（类似endpoints的处理方式）
    const packageConfigs = [];
    for (let i = 0; i < packagesToShow.length; i++) {
      const packageDef = packagesToShow[i];
      // 简化包名用于Tab标题（优先 identifier，其次 name）
      const fullName = (packageDef && (packageDef.identifier || packageDef.name)) || '';
      const shortName = fullName.split('/').pop() || fullName;
      const packageConfig = {
        index: i,
        packageName: `${fullName}@${packageDef.version}`,
        shortTitle: `${shortName}@${packageDef.version}`,
        registryType: this.getRegistryType(packageDef),
        // 描述字段已移除
        mcpConfig: this.convertPackageToMcpConfig(packageDef),
      };
      packageConfigs.push(packageConfig);
    }

    const securitySchemes = this.state.serverConfig?.toolSpec?.securitySchemes || [];
    const securitySchemeMap = securitySchemes.reduce((acc, scheme) => {
      if (scheme && scheme.id) {
        acc[scheme.id] = scheme;
      }
      return acc;
    }, {});
    const toolSpecExtensions = this.state.serverConfig?.toolSpec?.extensions || {};
    const defaultDownstreamSecurity = toolSpecExtensions['server.defaultDownstreamSecurity'];
    const defaultUpstreamSecurity = toolSpecExtensions['server.defaultUpstreamSecurity'];
    const hasDefaultSecurity = defaultDownstreamSecurity || defaultUpstreamSecurity;

    const versionSelections = [];
    for (let i = 0; i < versions.length; i++) {
      const item = versions[i];
      if (item.isLatest || item.is_latest) {
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
      if (this.state?.serverConfig?.frontendEndpoints?.length > 0) {
        serverReturnEndpoints = this.state?.serverConfig?.frontendEndpoints;
      } else {
        serverReturnEndpoints = this.state?.serverConfig?.backendEndpoints;
      }
    } else {
      serverReturnEndpoints = this.state?.serverConfig?.frontendEndpoints;
    }

    for (let i = 0; i < serverReturnEndpoints?.length; i++) {
      const item = serverReturnEndpoints[i];

      // 根据 protocol 字段判断使用 https 还是 http 前缀
      const protocolPrefix = (item.protocol || 'http') + '://';
      const endpoint = protocolPrefix + item.address + ':' + item.port + item.path;

      const serverConfig = {
        index: i,
        endpoint: endpoint,
        address: item.address,
        serverConfig: {
          mcpServers: {},
        },
        headers: item.headers || [],
      };
      serverConfig.serverConfig.mcpServers[this.state.serverConfig?.name] = {
        url: endpoint,
      };
      endpoints.push(serverConfig);
    }

    const buildDefaultSecurityFields = (sectionKey, security) => {
      if (!security) {
        return { fields: [], message: null };
      }

      const fields = [];
      const pushField = (key, label, value, options = {}) => {
        if (value === undefined || value === null || value === '') {
          return;
        }
        fields.push({ key, label, value, ...options });
      };

      pushField('scheme-id', locale.schemeId || 'ID', security.id, { monospace: true });

      if (sectionKey === 'downstream') {
        const passthroughLabel = security.passthrough
          ? locale.downstreamPassthroughEnabled || '启用'
          : locale.downstreamPassthroughDisabled || '禁用';
        if (security.passthrough !== undefined) {
          pushField(
            'downstream-passthrough',
            locale.downstreamPassthroughLabel || '透明认证',
            passthroughLabel
          );
        }
      }

      if (sectionKey === 'upstream') {
        pushField(
          'upstream-credential',
          locale.upstreamCredentialLabel || '覆盖凭证',
          security.credential,
          { code: true, fullWidth: true }
        );
      }

      Object.keys(security).forEach(key => {
        if (KNOWN_SECURITY_FIELDS.includes(key)) {
          return;
        }
        const value = security[key];
        const displayValue =
          typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
        pushField(`extension-${key}`, key, displayValue, {
          code: typeof value === 'object',
          fullWidth: typeof value === 'object',
        });
      });

      let message = null;
      if (!security.id) {
        message = (
          <p className="mcp-description-small">
            {locale.securitySchemeNotConfigured || '未配置认证方案'}
          </p>
        );
      } else if (!securitySchemeMap[security.id]) {
        message = (
          <p className="mcp-description-small">
            {(locale.securitySchemeNotFound || '未找到对应的认证方案') + ` (${security.id})`}
          </p>
        );
      }

      return { fields, message };
    };

    const renderFieldColumns = fields => {
      if (!Array.isArray(fields) || fields.length === 0) {
        return [];
      }

      const columns = [];
      fields
        .filter(field => field && field.value !== undefined && field.value !== null && field.value !== '')
        .forEach(field => {
          const span = field.fullWidth ? 24 : 12;
          const valueClassNames = ['mcp-field-value'];
          if (field.monospace) {
            valueClassNames.push('mcp-monospace');
          }
          if (field.code) {
            valueClassNames.push('mcp-monospace-code');
          }
          columns.push(
            <Col span={span} className="mcp-form-col" key={field.key}>
              <p className="mcp-scheme-label">{field.label}:</p>
              <p className={valueClassNames.join(' ')}>{field.value}</p>
            </Col>
          );
        });
      return columns;
    };

    const renderDefaultSecurityCard = (sectionKey, title, description, security) => {
      if (!security) {
        return null;
      }
      const defaultSecurityInfo = buildDefaultSecurityFields(sectionKey, security);
      const allFields = defaultSecurityInfo.fields || [];
      const isCollapsed = this.state.defaultSecurityCollapsed?.[sectionKey] !== false;
      const BASIC_FIELD_COUNT = 3;
      const visibleFields = isCollapsed ? allFields.slice(0, BASIC_FIELD_COUNT) : allFields;
      const rows = renderFieldColumns(visibleFields);
      const hasMore = allFields.length > visibleFields.length;
      return (
        <div className="mcp-card mcp-default-security-card">
          <div className="mcp-default-security-card-header">
            <div className="mcp-default-security-card-text">
              <div className="mcp-default-security-card-title">{title}</div>
              <div className="mcp-default-security-card-desc">{description}</div>
            </div>
            {allFields.length > BASIC_FIELD_COUNT && (
              <div className="mcp-default-security-card-actions">
                <Button
                  text
                  size="small"
                  className="mcp-card-toggle-btn"
                  onClick={() => this.toggleDefaultSecuritySection(sectionKey)}
                >
                  {isCollapsed ? locale.expand || '展开' : locale.collapse || '收起'}
                </Button>
              </div>
            )}
          </div>
          <div className="mcp-default-security-meta">
            {defaultSecurityInfo.message}
            {rows.length > 0 && (
              <Row wrap className="mcp-form-row-aligned mcp-default-security-row">{rows}</Row>
            )}
            {isCollapsed && hasMore && (
              <p className="mcp-default-security-hint">
                {locale.defaultSecurityCollapsedHint || '展开查看更多详细配置'}
              </p>
            )}
          </div>
        </div>
      );
    };

    return (
      <div>
        <Loading
          shape={'flower'}
          tip={'Loading...'}
          className="mcp-loading-container"
          visible={this.state.loading}
          color={'#333'}
        >
          <Row>
            <Col span={16}>
              <h1 className="mcp-heading-main">
                {this.state.serverConfig?.name || locale.mcpServerDetail || 'MCP Server'}
              </h1>
            </Col>
            <Col span={4}>
              <span>{locale.version}</span>
              <Select
                dataSource={versionSelections}
                className="mcp-version-select"
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

          {/* 服务描述 - 平铺展示 */}
          {this.state.serverConfig?.description && (
            <div className="mcp-margin-vertical-20">
              <p className="mcp-description-text">{this.state.serverConfig.description}</p>
            </div>
          )}

          <h2 className="mcp-section-title">{locale.basicInformation || '基本信息'}</h2>

          <div className="mcp-margin-top-16">
            <div className="mcp-card">
              <Row wrap className="mcp-form-row-aligned">
                <Col span={12} className="mcp-form-col-namespace">
                  <div className="mcp-label">{locale.namespace || '命名空间'}:</div>
                  <div className="mcp-namespace-box">{getParams('namespace') || 'default'}</div>
                </Col>
                <Col span={12} className="mcp-form-col-namespace">
                  <div className="mcp-label">{locale.serverType || '服务类型'}:</div>
                  <div className="mcp-server-type-badge">
                    {this.state.serverConfig.frontProtocol}
                  </div>
                </Col>
                {this.state.serverConfig?.protocol !== 'stdio' &&
                  this.state.serverConfig?.remoteServerConfig?.serviceRef && (
                    <Col span={12} className="mcp-form-col-namespace">
                      <div className="mcp-label">{locale.serviceRef || '服务引用'}:</div>
                      <div>
                        <a
                          onClick={() => {
                            this.goToServiceDetail(
                              this.state.serverConfig?.remoteServerConfig?.serviceRef
                            );
                          }}
                          className="mcp-link"
                        >
                          {this.state.serverConfig?.remoteServerConfig?.serviceRef.namespaceId}/
                          {this.state.serverConfig?.remoteServerConfig?.serviceRef.groupName}/
                          {this.state.serverConfig?.remoteServerConfig?.serviceRef.serviceName}
                        </a>
                      </div>
                    </Col>
                  )}
              </Row>
            </div>
          </div>

          <Divider></Divider>

          {/* 响应式布局：桌面端左右分栏，移动端上下堆叠 */}
          <div className="responsive-layout">
            {/* 左侧：Package 和 Tool 信息 */}
            <div className="left-content">
              {/* Security Schemes 展示 - 只在非 stdio 协议且有数据时显示 */}
              {this.state.serverConfig?.protocol !== 'stdio' &&
                this.state.serverConfig?.toolSpec?.securitySchemes?.length > 0 && (
                  <>
                    <h2 className="mcp-section-title mcp-margin-bottom-16">
                      {locale.backendServiceAuth || '后端服务认证方式'}
                    </h2>
                    <div className="mcp-margin-bottom-24">
                      {this.state.serverConfig.toolSpec.securitySchemes.map((scheme, index) => (
                        <div key={index} className="mcp-card mcp-security-scheme-card">
                          <Row wrap className="mcp-form-row-aligned">
                            <Col span={12} className="mcp-form-col">
                              <p className="mcp-scheme-label">{locale.schemeId || 'ID'}:</p>
                              <p className="mcp-monospace">{scheme.id || '-'}</p>
                            </Col>
                            <Col span={12} className="mcp-form-col">
                              <p className="mcp-scheme-label">{locale.authType || '认证类型'}:</p>
                              <p>{scheme.type}</p>
                            </Col>
                            {scheme.scheme && (
                              <Col span={12} className="mcp-form-col">
                                <p className="mcp-scheme-label">
                                  {locale.authScheme || '认证方案'}:
                                </p>
                                <p>{scheme.scheme}</p>
                              </Col>
                            )}
                            {scheme.in && (
                              <Col span={12} className="mcp-form-col">
                                <p className="mcp-scheme-label">
                                  {locale.keyLocation || '密钥位置'}:
                                </p>
                                <p>{scheme.in}</p>
                              </Col>
                            )}
                            {scheme.name && (
                              <Col span={12} className="mcp-form-col">
                                <p className="mcp-scheme-label">{locale.keyName || '密钥名称'}:</p>
                                <p>{scheme.name}</p>
                              </Col>
                            )}
                            {scheme.defaultCredential && (
                              <Col span={24} className="mcp-form-col">
                                <p className="mcp-scheme-label">
                                  {locale.defaultCredential || '默认凭证'}:
                                </p>
                                <p className="mcp-monospace-code">{scheme.defaultCredential}</p>
                              </Col>
                            )}
                          </Row>
                        </div>
                      ))}
                    </div>
                  </>
                )}

              {hasDefaultSecurity && (
                <>
                  <h2 className="mcp-section-title mcp-margin-bottom-16">
                    {locale.defaultSecurityHeading || '默认安全配置'}
                  </h2>
                  <div className="mcp-default-security-sections">
                    {renderDefaultSecurityCard(
                      'downstream',
                      locale.defaultDownstreamSecurityTitle || '默认下行认证',
                      locale.defaultDownstreamSecurityDesc ||
                        '作用于客户端到网关的请求以及未配置 security 的工具。',
                      defaultDownstreamSecurity
                    )}
                    {renderDefaultSecurityCard(
                      'upstream',
                      locale.defaultUpstreamSecurityTitle || '默认上行认证',
                      locale.defaultUpstreamSecurityDesc ||
                        '作用于网关访问后端时的默认配置。',
                      defaultUpstreamSecurity
                    )}
                  </div>
                </>
              )}

              {/* Tools 展示 */}
              <ShowTools
                serverConfig={this.state.serverConfig}
                frontProtocol={this.state.serverConfig?.frontProtocol || 'stdio'}
                restToMcpSwitch={this.state.serverConfig?.protocol !== 'stdio'}
                locale={this.props.locale}
                isPreview={true}
                onlyEditRuntimeInfo={false}
              />
            </div>

            {/* 右侧：Server Config 信息 */}
            <div className="right-content">
              <div className="server-config-responsive">
                {/* stdio 协议的 Server Config */}
                {this.state.serverConfig?.protocol === 'stdio' && (
                  <>
                    {packageConfigs?.length > 0 ? (
                      // 多个Package的Tab展示
                      <div className="mcp-margin-top-12">
                        <Tab excessMode="dropdown" defaultActiveKey={0}>
                          {packageConfigs.map((item, index) => {
                            const packageDef = packagesToShow[index];
                            return (
                              <Tab.Item
                                key={item.index}
                                title={`${item.shortTitle} (${item.registryType})`}
                              >
                                <div className="mcp-margin-top-12">
                                  {/* Server Config */}
                                  <div className="mcp-margin-bottom-24">
                                    <h4 className="mcp-header-title">
                                      {locale.serverConfig || '客户端配置'}
                                    </h4>
                                    <pre
                                      className="mcp-code-block"
                                      onClick={() =>
                                        this.copyToClipboard(
                                          JSON.stringify(item.mcpConfig, null, 2)
                                        )
                                      }
                                      title="点击复制配置"
                                    >
                                      {JSON.stringify(item.mcpConfig, null, 2)}
                                    </pre>
                                  </div>

                                  {/* 依赖详情 */}
                                  <div>
                                    <h4 className="mcp-header-title">依赖详情</h4>
                                    {this.renderPackageDetails(packageDef, index)}
                                  </div>
                                </div>
                              </Tab.Item>
                            );
                          })}
                        </Tab>
                      </div>
                    ) : (
                      // 原有的localServerConfig显示
                      <pre
                        className="mcp-code-block-full"
                        onClick={() => this.copyToClipboard(localServerConfig)}
                        title="点击复制配置"
                      >
                        {localServerConfig}
                      </pre>
                    )}
                  </>
                )}

                {/* 非 stdio 协议的 Endpoint 配置 */}
                {this.state.serverConfig?.protocol !== 'stdio' && (
                  <>
                    {endpoints?.length > 0 ? (
                      <div className="mcp-margin-top-12">
                        <Tab excessMode="dropdown" defaultActiveKey={0}>
                          {endpoints?.map(item => (
                            <Tab.Item key={item.index} title={item.address}>
                              <div className="mcp-margin-top-12">
                                {/* Server Config */}
                                <div className="mcp-margin-bottom-24">
                                  <h4 className="mcp-header-title">
                                    {locale.serverConfig || '客户端配置'}
                                  </h4>
                                  <pre
                                    className="mcp-code-block"
                                    onClick={() =>
                                      this.copyToClipboard(
                                        JSON.stringify(item.serverConfig, null, 2)
                                      )
                                    }
                                    title="点击复制配置"
                                  >
                                    {JSON.stringify(item.serverConfig, null, 2)}
                                  </pre>
                                </div>

                                {/* Headers 配置 */}
                                <div>
                                  <h4 className="mcp-header-title">
                                    {locale.httpHeaders || 'HTTP Headers 配置'}
                                  </h4>
                                  {this.renderHeaders(item.headers, locale)}
                                </div>
                              </div>
                            </Tab.Item>
                          ))}
                        </Tab>
                      </div>
                    ) : (
                      <div>
                        <div className="mcp-empty-state-large">
                          <div>
                            <div className="mcp-empty-icon-large">📡</div>
                            <p className="mcp-empty-text-large">
                              {locale.noAvailableEndpoint || '暂无可用的端点'}
                            </p>
                          </div>
                        </div>
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
        </Loading>
      </div>
    );
  }
}

export default McpDetail;
