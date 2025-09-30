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
      } else if (this.getRegistryType(packageDef) === 'docker') {
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
      <div
        style={{
          border: '1px solid rgba(230, 230, 230, 0.4)',
          borderRadius: '8px',
          padding: '20px',
          backgroundColor: 'rgba(250, 250, 250, 0.7)',
          backdropFilter: 'blur(10px)',
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
          marginBottom: '16px',
          transition: 'all 0.3s ease',
        }}
        onMouseEnter={e => {
          e.currentTarget.style.transform = 'translateY(-2px)';
          e.currentTarget.style.boxShadow =
            '0 4px 16px rgba(0, 0, 0, 0.08), 0 2px 8px rgba(0, 0, 0, 0.05)';
        }}
        onMouseLeave={e => {
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow =
            '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
        }}
      >
        {/* 基本信息 */}
        <div style={{ marginBottom: '24px' }}>
          <h3
            style={{
              color: '#000',
              marginBottom: '16px',
              borderBottom: '2px solid #e6e6e6',
              paddingBottom: '8px',
            }}
          >
            {locale.basicInformation || '基本信息'}
          </h3>
          <Row wrap style={{ textAlign: 'left' }}>
            <Col span={24} style={{ display: 'flex', marginBottom: '8px' }}>
              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                {locale.packageName || '包名'}:
              </p>
              {(() => {
                const repositoryUrl = this.getPackageRepositoryUrl(packageDef);
                const displayName = this.getPackageName(packageDef);
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
                      {displayName}
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
                      {displayName}
                    </p>
                  );
                }
              })()}
            </Col>
            <Col span={24} style={{ display: 'flex', marginBottom: '8px' }}>
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
            <Col span={24} style={{ display: 'flex', marginBottom: '8px' }}>
              <p style={{ minWidth: 120, fontWeight: 'bold', color: '#000' }}>
                {locale.registryType || '注册表类型'}:
              </p>
              <p
                style={{
                  backgroundColor: this.getRegistryColor(this.getRegistryType(packageDef)),
                  color: 'white',
                  padding: '2px 8px',
                  borderRadius: '12px',
                  fontSize: '12px',
                  fontWeight: 'bold',
                }}
              >
                {this.getRegistryType(packageDef)}
              </p>
            </Col>
            {(packageDef.runtimeHint || packageDef.runtime_hint) && (
              <Col span={24} style={{ display: 'flex', marginBottom: '8px' }}>
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
                  {packageDef.runtimeHint || packageDef.runtime_hint}
                </p>
              </Col>
            )}
            {/* 包描述已移除，不再展示 */}
          </Row>
        </div>

        {/* 参数配置区域 - 只在有参数时显示 */}
        {totalParamsCount > 0 && (
          <div style={{ marginBottom: '16px' }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '16px',
              }}
            >
              <h3
                style={{
                  color: '#000',
                  margin: 0,
                  borderBottom: '2px solid #e6e6e6',
                  paddingBottom: '8px',
                  flex: 1,
                }}
              >
                {locale.parameterConfiguration || '参数配置'}
                <span style={{ marginLeft: '8px', color: '#666', fontSize: '14px' }}>
                  (共 {totalParamsCount} 项)
                </span>
              </h3>
              <Button
                size="small"
                type="normal"
                onClick={() => this.togglePackageTabs(index)}
                style={{ marginLeft: '16px' }}
              >
                {isTabsExpanded ? '收起' : '展开'}
              </Button>
            </div>

            {isTabsExpanded && (
              <div
                style={{
                  border: '1px solid rgba(230, 230, 230, 0.4)',
                  borderRadius: '8px',
                  backgroundColor: 'rgba(255, 255, 255, 0.7)',
                  backdropFilter: 'blur(10px)',
                  padding: '16px',
                }}
              >
                {/* 运行时参数容器 */}
                {runtimeArgsCount > 0 && (
                  <div style={{ marginBottom: '16px' }}>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '12px 16px',
                        backgroundColor: 'rgba(24, 144, 255, 0.1)',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        border: '1px solid rgba(24, 144, 255, 0.2)',
                        marginBottom: '8px',
                      }}
                      onClick={() => this.toggleParameterContainer(index, 'runtime')}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '14px', fontWeight: 'bold', color: '#1890ff' }}>
                          {locale.runtimeArguments || '运行时参数'}
                        </span>
                        <span style={{ color: '#666', fontSize: '12px' }}>
                          ({runtimeArgsCount})
                        </span>
                      </div>
                      <span style={{ color: '#1890ff', fontSize: '12px' }}>
                        {this.state.parameterContainersExpanded[index]?.runtime
                          ? '收起 ▲'
                          : '展开 ▼'}
                      </span>
                    </div>
                    {this.state.parameterContainersExpanded[index]?.runtime && (
                      <div style={{ padding: '8px 16px' }}>
                        {runtimeArguments.map((arg, argIndex) => (
                          <div
                            key={argIndex}
                            style={{
                              marginBottom: '8px',
                              paddingBottom: '8px',
                              borderBottom:
                                argIndex < runtimeArguments.length - 1
                                  ? '1px solid #e6e6e6'
                                  : 'none',
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                              <span
                                style={{
                                  backgroundColor:
                                    arg.type === 'positional' ? '#52c41a' : '#1890ff',
                                  color: 'white',
                                  padding: '2px 8px',
                                  borderRadius: '12px',
                                  fontSize: '11px',
                                  fontWeight: 'bold',
                                  minWidth: '70px',
                                  textAlign: 'center',
                                }}
                              >
                                {arg.type === 'positional' ? '位置参数' : '命名参数'}
                              </span>
                              <span
                                style={{
                                  fontFamily: 'monospace',
                                  backgroundColor: '#f5f5f5',
                                  padding: '3px 6px',
                                  borderRadius: '3px',
                                  color: '#000',
                                  fontSize: '12px',
                                  minWidth: '120px',
                                }}
                              >
                                {arg.value || arg.default || '<未设置>'}
                              </span>
                              <span style={{ color: '#666', fontSize: '12px', flex: 1 }}>
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
                  <div style={{ marginBottom: '16px' }}>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '12px 16px',
                        backgroundColor: 'rgba(82, 196, 26, 0.1)',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        border: '1px solid rgba(82, 196, 26, 0.2)',
                        marginBottom: '8px',
                      }}
                      onClick={() => this.toggleParameterContainer(index, 'package')}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '14px', fontWeight: 'bold', color: '#52c41a' }}>
                          {locale.packageArguments || '包参数'}
                        </span>
                        <span style={{ color: '#666', fontSize: '12px' }}>
                          ({packageArgsCount})
                        </span>
                      </div>
                      <span style={{ color: '#52c41a', fontSize: '12px' }}>
                        {this.state.parameterContainersExpanded[index]?.package
                          ? '收起 ▲'
                          : '展开 ▼'}
                      </span>
                    </div>
                    {this.state.parameterContainersExpanded[index]?.package && (
                      <div style={{ padding: '8px 16px' }}>
                        {packageArguments.map((arg, argIndex) => (
                          <div
                            key={argIndex}
                            style={{
                              marginBottom: '8px',
                              paddingBottom: '8px',
                              borderBottom:
                                argIndex < packageArguments.length - 1
                                  ? '1px solid #e6e6e6'
                                  : 'none',
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                              <span
                                style={{
                                  backgroundColor:
                                    arg.type === 'positional' ? '#52c41a' : '#1890ff',
                                  color: 'white',
                                  padding: '2px 8px',
                                  borderRadius: '12px',
                                  fontSize: '11px',
                                  fontWeight: 'bold',
                                  minWidth: '70px',
                                  textAlign: 'center',
                                }}
                              >
                                {arg.type === 'positional' ? '位置参数' : '命名参数'}
                              </span>
                              <span
                                style={{
                                  fontFamily: 'monospace',
                                  backgroundColor: '#f5f5f5',
                                  padding: '3px 6px',
                                  borderRadius: '3px',
                                  color: '#000',
                                  fontSize: '12px',
                                  minWidth: '120px',
                                }}
                              >
                                {arg.name
                                  ? `${arg.name}=${arg.value || arg.default || '<value>'}`
                                  : arg.value || arg.default || '<未设置>'}
                              </span>
                              <span style={{ color: '#666', fontSize: '12px', flex: 1 }}>
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
                  <div style={{ marginBottom: '16px' }}>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '12px 16px',
                        backgroundColor: 'rgba(250, 140, 22, 0.1)',
                        borderRadius: '6px',
                        cursor: 'pointer',
                        border: '1px solid rgba(250, 140, 22, 0.2)',
                        marginBottom: '8px',
                      }}
                      onClick={() => this.toggleParameterContainer(index, 'env')}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontSize: '14px', fontWeight: 'bold', color: '#fa8c16' }}>
                          {locale.environmentVariables || '环境变量'}
                        </span>
                        <span style={{ color: '#666', fontSize: '12px' }}>({envVarsCount})</span>
                      </div>
                      <span style={{ color: '#fa8c16', fontSize: '12px' }}>
                        {this.state.parameterContainersExpanded[index]?.env ? '收起 ▲' : '展开 ▼'}
                      </span>
                    </div>
                    {this.state.parameterContainersExpanded[index]?.env && (
                      <div style={{ padding: '8px 16px' }}>
                        {environmentVariables.map((envVar, envIndex) => (
                          <div
                            key={envIndex}
                            style={{
                              marginBottom: '8px',
                              paddingBottom: '8px',
                              borderBottom:
                                envIndex < environmentVariables.length - 1
                                  ? '1px solid #e6e6e6'
                                  : 'none',
                            }}
                          >
                            <div
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                marginBottom: '4px',
                              }}
                            >
                              <span
                                style={{
                                  fontFamily: 'monospace',
                                  backgroundColor: '#f5f5f5',
                                  padding: '3px 6px',
                                  borderRadius: '3px',
                                  fontWeight: 'bold',
                                  color: '#000',
                                  fontSize: '12px',
                                  minWidth: '120px',
                                }}
                              >
                                {envVar.name}
                              </span>
                              <span
                                style={{
                                  fontFamily: 'monospace',
                                  backgroundColor: '#f0f0f0',
                                  padding: '3px 6px',
                                  borderRadius: '3px',
                                  color: '#000',
                                  fontSize: '12px',
                                  minWidth: '120px',
                                }}
                              >
                                {envVar.value || envVar.default || '<未设置>'}
                              </span>
                              <div style={{ display: 'flex', gap: '6px' }}>
                                {(envVar.isRequired || envVar.is_required) && (
                                  <span
                                    style={{
                                      backgroundColor: '#ff4d4f',
                                      color: 'white',
                                      padding: '1px 4px',
                                      borderRadius: '8px',
                                      fontSize: '10px',
                                      fontWeight: 'bold',
                                    }}
                                  >
                                    必填
                                  </span>
                                )}
                                {(envVar.isSecret || envVar.is_secret) && (
                                  <span
                                    style={{
                                      backgroundColor: '#faad14',
                                      color: 'white',
                                      padding: '1px 4px',
                                      borderRadius: '8px',
                                      fontSize: '10px',
                                      fontWeight: 'bold',
                                    }}
                                  >
                                    敏感
                                  </span>
                                )}
                              </div>
                              <span style={{ color: '#666', fontSize: '12px', flex: 1 }}>
                                {envVar.description || '无描述'}
                              </span>
                            </div>
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
        <div style={{ marginBottom: '16px' }}>
          <div
            style={{
              border: '1px solid rgba(230, 230, 230, 0.4)',
              borderRadius: '8px',
              padding: '16px',
              backgroundColor: 'rgba(250, 250, 250, 0.7)',
              backdropFilter: 'blur(10px)',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
              textAlign: 'center',
              minHeight: '60px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <div>
              <div
                style={{
                  fontSize: '24px',
                  color: '#d9d9d9',
                  marginBottom: '8px',
                  fontWeight: '300',
                }}
              >
                📋
              </div>
              <p
                style={{
                  color: '#666',
                  fontStyle: 'italic',
                  margin: 0,
                  fontSize: '12px',
                }}
              >
                {locale.noHeadersAvailable || '该端点无 Headers 配置'}
              </p>
            </div>
          </div>
        </div>
      );
    }

    return (
      <div style={{ marginBottom: '16px' }}>
        {headers.map((header, index) => (
          <div
            key={index}
            style={{
              border: '1px solid rgba(230, 230, 230, 0.4)',
              borderRadius: '8px',
              padding: '12px',
              marginBottom: '8px',
              backgroundColor: 'rgba(250, 250, 250, 0.7)',
              backdropFilter: 'blur(10px)',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
              transition: 'all 0.3s ease',
            }}
            onMouseEnter={e => {
              e.currentTarget.style.transform = 'translateY(-1px)';
              e.currentTarget.style.boxShadow =
                '0 4px 12px rgba(0, 0, 0, 0.08), 0 2px 6px rgba(0, 0, 0, 0.05)';
            }}
            onMouseLeave={e => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow =
                '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
            }}
          >
            {/* Header 名称行 */}
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '4px' }}>
              <span
                style={{ fontWeight: 'bold', color: '#000', minWidth: '80px', fontSize: '13px' }}
              >
                {locale.headerName || 'Name'}:
              </span>
              <span
                style={{
                  fontFamily: 'monospace',
                  backgroundColor: '#f5f5f5',
                  padding: '2px 6px',
                  borderRadius: '3px',
                  color: '#000',
                  fontSize: '12px',
                  fontWeight: '600',
                }}
              >
                {header.name}
              </span>
              {(header.isRequired || header.is_required) && (
                <span
                  style={{
                    backgroundColor: '#ff4d4f',
                    color: 'white',
                    padding: '1px 4px',
                    borderRadius: '8px',
                    fontSize: '10px',
                    fontWeight: 'bold',
                    marginLeft: '8px',
                  }}
                >
                  必填
                </span>
              )}
              {(header.isSecret || header.is_secret) && (
                <span
                  style={{
                    backgroundColor: '#faad14',
                    color: 'white',
                    padding: '1px 4px',
                    borderRadius: '8px',
                    fontSize: '10px',
                    fontWeight: 'bold',
                    marginLeft: '4px',
                  }}
                >
                  敏感
                </span>
              )}
            </div>

            {/* Header 值行 */}
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '4px' }}>
              <span
                style={{ fontWeight: 'bold', color: '#000', minWidth: '80px', fontSize: '13px' }}
              >
                {locale.headerValue || 'Value'}:
              </span>
              <span
                style={{
                  fontFamily: 'monospace',
                  backgroundColor: '#f5f5f5',
                  padding: '2px 6px',
                  borderRadius: '3px',
                  color: '#000',
                  fontSize: '12px',
                }}
              >
                {header.value || header.default || '<未设置>'}
              </span>
            </div>

            {/* 格式类型行 */}
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '4px' }}>
              <span
                style={{ fontWeight: 'bold', color: '#000', minWidth: '80px', fontSize: '13px' }}
              >
                {locale.format || 'Type'}:
              </span>
              <span
                style={{
                  backgroundColor: this.getFormatColor(header.format),
                  color: 'white',
                  padding: '1px 6px',
                  borderRadius: '10px',
                  fontSize: '11px',
                  fontWeight: 'bold',
                }}
              >
                {header.format || 'string'}
              </span>
            </div>

            {/* 描述行 */}
            {header.description && (
              <div style={{ display: 'flex', alignItems: 'flex-start', marginBottom: '4px' }}>
                <span
                  style={{ fontWeight: 'bold', color: '#000', minWidth: '80px', fontSize: '13px' }}
                >
                  {locale.description || 'Desc'}:
                </span>
                <span style={{ color: '#666', fontSize: '12px', lineHeight: '1.4' }}>
                  {header.description}
                </span>
              </div>
            )}

            {/* 默认值行 */}
            {header.default && (
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: '4px' }}>
                <span
                  style={{ fontWeight: 'bold', color: '#000', minWidth: '80px', fontSize: '13px' }}
                >
                  {locale.defaultValue || 'Default'}:
                </span>
                <span
                  style={{
                    fontFamily: 'monospace',
                    backgroundColor: '#f0f0f0',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    color: '#000',
                    fontSize: '12px',
                  }}
                >
                  {header.default}
                </span>
              </div>
            )}

            {/* 可选值行 */}
            {header.choices && header.choices.length > 0 && (
              <div style={{ display: 'flex', alignItems: 'flex-start' }}>
                <span
                  style={{ fontWeight: 'bold', color: '#000', minWidth: '80px', fontSize: '13px' }}
                >
                  {locale.choices || 'Choices'}:
                </span>
                <span
                  style={{
                    fontFamily: 'monospace',
                    backgroundColor: '#f5f5f5',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    color: '#000',
                    fontSize: '12px',
                    lineHeight: '1.4',
                  }}
                >
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

    return (
      <div>
        <style>
          {`
            .responsive-layout {
              display: flex;
              gap: 24px;
            }
            
            .left-content {
              flex: 1;
              min-width: 0;
            }
            
            .right-content {
              width: 350px;
              flex-shrink: 0;
              overflow-x: auto;
              word-wrap: break-word;
              word-break: break-word;
            }
            
            @media (max-width: 768px) {
              .server-config-responsive {
                margin-top: 24px !important;
              }
              .responsive-layout {
                flex-direction: column !important;
                gap: 0 !important;
              }
              .left-content {
                width: 100% !important;
                margin-bottom: 24px !important;
              }
              .right-content {
                width: 100% !important;
                order: 2;
                overflow-x: auto;
                word-wrap: break-word;
                word-break: break-word;
              }
            }
            
            @media (max-width: 1024px) and (min-width: 769px) {
              .right-content {
                width: 280px;
              }
            }
            
            @media (max-width: 900px) and (min-width: 769px) {
              .responsive-layout {
                flex-direction: column !important;
                gap: 0 !important;
              }
              .left-content {
                width: 100% !important;
                margin-bottom: 24px !important;
              }
              .right-content {
                width: 100% !important;
                order: 2;
                overflow-x: auto;
                word-wrap: break-word;
                word-break: break-word;
              }
            }
            
            @media (max-width: 1024px) {
              .right-content {
                width: 300px;
              }
            }
          `}
        </style>
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
                {this.state.serverConfig?.name || locale.mcpServerDetail || 'MCP Server'}
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

          {/* 服务描述 - 平铺展示 */}
          {this.state.serverConfig?.description && (
            <div style={{ marginTop: '20px', marginBottom: '20px' }}>
              <p
                style={{
                  color: '#666',
                  fontSize: '16px',
                  lineHeight: '1.6',
                  margin: 0,
                  fontStyle: 'italic',
                  textAlign: 'left',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  overflowWrap: 'anywhere',
                }}
              >
                {this.state.serverConfig.description}
              </p>
            </div>
          )}

          <h2
            style={{
              color: '#333',
              fontWeight: 'bold',
            }}
          >
            {locale.basicInformation || '基本信息'}
          </h2>

          <div style={{ marginTop: '16px' }}>
            <div
              style={{
                border: '1px solid rgba(230, 230, 230, 0.4)',
                borderRadius: '8px',
                padding: '20px',
                backgroundColor: 'rgba(250, 250, 250, 0.7)',
                backdropFilter: 'blur(10px)',
                boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                marginBottom: '16px',
                transition: 'all 0.3s ease',
              }}
              onMouseEnter={e => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow =
                  '0 4px 16px rgba(0, 0, 0, 0.08), 0 2px 8px rgba(0, 0, 0, 0.05)';
              }}
              onMouseLeave={e => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow =
                  '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
              }}
            >
              <Row wrap style={{ textAlign: 'left' }}>
                <Col span={12} style={{ display: 'flex', marginBottom: '16px' }}>
                  <div
                    style={{ minWidth: 120, fontWeight: 'bold', color: '#000', fontSize: '14px' }}
                  >
                    {locale.namespace || '命名空间'}:
                  </div>
                  <div
                    style={{
                      fontFamily: 'monospace',
                      backgroundColor: '#f5f5f5',
                      padding: '4px 12px',
                      borderRadius: '4px',
                      color: '#000',
                      fontSize: '13px',
                      border: '1px solid #e8e8e8',
                    }}
                  >
                    {getParams('namespace') || 'default'}
                  </div>
                </Col>
                <Col span={12} style={{ display: 'flex', marginBottom: '16px' }}>
                  <div
                    style={{ minWidth: 120, fontWeight: 'bold', color: '#000', fontSize: '14px' }}
                  >
                    {locale.serverType || '服务类型'}:
                  </div>
                  <div
                    style={{
                      backgroundColor: '#1890ff',
                      color: 'white',
                      padding: '4px 12px',
                      borderRadius: '12px',
                      fontSize: '12px',
                      fontWeight: 'bold',
                    }}
                  >
                    {this.state.serverConfig.frontProtocol}
                  </div>
                </Col>
                {this.state.serverConfig?.protocol !== 'stdio' &&
                  this.state.serverConfig?.remoteServerConfig?.serviceRef && (
                    <Col span={12} style={{ display: 'flex', marginBottom: '16px' }}>
                      <div
                        style={{
                          minWidth: 120,
                          fontWeight: 'bold',
                          color: '#000',
                          fontSize: '14px',
                        }}
                      >
                        {locale.serviceRef || '服务引用'}:
                      </div>
                      <div>
                        <a
                          onClick={() => {
                            this.goToServiceDetail(
                              this.state.serverConfig?.remoteServerConfig?.serviceRef
                            );
                          }}
                          style={{
                            color: '#1890ff',
                            cursor: 'pointer',
                            textDecoration: 'none',
                            fontFamily: 'monospace',
                            fontSize: '13px',
                            padding: '2px 8px',
                            borderRadius: '3px',
                            backgroundColor: '#f0f8ff',
                            border: '1px solid #d6ebff',
                          }}
                          onMouseEnter={e => {
                            e.target.style.backgroundColor = '#e6f7ff';
                            e.target.style.textDecoration = 'underline';
                          }}
                          onMouseLeave={e => {
                            e.target.style.backgroundColor = '#f0f8ff';
                            e.target.style.textDecoration = 'none';
                          }}
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
                    <h2
                      style={{
                        color: '#333',
                        fontWeight: 'bold',
                        marginBottom: '16px',
                      }}
                    >
                      {locale.backendServiceAuth || '后端服务认证方式'}
                    </h2>
                    <div style={{ marginBottom: '24px' }}>
                      {this.state.serverConfig.toolSpec.securitySchemes.map((scheme, index) => (
                        <div
                          key={index}
                          style={{
                            border: '1px solid rgba(230, 230, 230, 0.4)',
                            borderRadius: '8px',
                            padding: '16px',
                            marginBottom: '12px',
                            backgroundColor: 'rgba(250, 250, 250, 0.7)',
                            backdropFilter: 'blur(10px)',
                            boxShadow:
                              '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                            transition: 'all 0.3s ease',
                          }}
                          onMouseEnter={e => {
                            e.currentTarget.style.transform = 'translateY(-2px)';
                            e.currentTarget.style.boxShadow =
                              '0 8px 24px rgba(0, 0, 0, 0.12), 0 4px 12px rgba(0, 0, 0, 0.08)';
                          }}
                          onMouseLeave={e => {
                            e.currentTarget.style.transform = 'translateY(0)';
                            e.currentTarget.style.boxShadow =
                              '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
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
              <div
                className="server-config-responsive"
                style={{
                  marginTop: '0px',
                }}
              >
                {/* stdio 协议的 Server Config */}
                {this.state.serverConfig?.protocol === 'stdio' && (
                  <>
                    {packageConfigs?.length > 0 ? (
                      // 多个Package的Tab展示
                      <div style={{ marginTop: '12px' }}>
                        <Tab excessMode="dropdown" defaultActiveKey={0}>
                          {packageConfigs.map((item, index) => {
                            const packageDef = packagesToShow[index];
                            return (
                              <Tab.Item
                                key={item.index}
                                title={`${item.shortTitle} (${item.registryType})`}
                              >
                                <div style={{ marginTop: '12px' }}>
                                  {/* Server Config */}
                                  <div style={{ marginBottom: '24px' }}>
                                    <h4
                                      style={{
                                        color: '#333',
                                        fontWeight: 'bold',
                                        marginBottom: '12px',
                                        fontSize: '14px',
                                      }}
                                    >
                                      {locale.serverConfig || '客户端配置'}
                                    </h4>
                                    <pre
                                      style={{
                                        cursor: 'pointer',
                                        border: '1px solid rgba(221, 221, 221, 0.4)',
                                        borderRadius: '8px',
                                        padding: '12px',
                                        backgroundColor: 'rgba(248, 248, 248, 0.7)',
                                        backdropFilter: 'blur(10px)',
                                        boxShadow:
                                          '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                                        position: 'relative',
                                        transition: 'all 0.3s ease',
                                        overflow: 'auto',
                                        maxHeight: '400px',
                                        fontSize: '12px',
                                        lineHeight: '1.4',
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-all',
                                        margin: 0,
                                      }}
                                      onClick={() =>
                                        this.copyToClipboard(
                                          JSON.stringify(item.mcpConfig, null, 2)
                                        )
                                      }
                                      onMouseEnter={e => {
                                        e.target.style.backgroundColor = 'rgba(232, 244, 253, 0.8)';
                                        e.target.style.borderColor = 'rgba(24, 144, 255, 0.6)';
                                        e.target.style.boxShadow =
                                          '0 4px 16px rgba(24, 144, 255, 0.1), 0 2px 8px rgba(24, 144, 255, 0.05)';
                                        e.target.style.transform = 'translateY(-2px)';
                                      }}
                                      onMouseLeave={e => {
                                        e.target.style.backgroundColor = 'rgba(248, 248, 248, 0.7)';
                                        e.target.style.borderColor = 'rgba(221, 221, 221, 0.4)';
                                        e.target.style.boxShadow =
                                          '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
                                        e.target.style.transform = 'translateY(0)';
                                      }}
                                      title="点击复制配置"
                                    >
                                      {JSON.stringify(item.mcpConfig, null, 2)}
                                    </pre>
                                  </div>

                                  {/* 依赖详情 */}
                                  <div>
                                    <h4
                                      style={{
                                        color: '#333',
                                        fontWeight: 'bold',
                                        marginBottom: '12px',
                                        fontSize: '14px',
                                      }}
                                    >
                                      依赖详情
                                    </h4>
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
                        style={{
                          cursor: 'pointer',
                          border: '1px solid rgba(221, 221, 221, 0.4)',
                          borderRadius: '8px',
                          padding: '12px',
                          backgroundColor: 'rgba(248, 248, 248, 0.7)',
                          backdropFilter: 'blur(10px)',
                          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                          transition: 'all 0.3s ease',
                          overflow: 'auto',
                          maxHeight: '400px',
                          fontSize: '12px',
                          lineHeight: '1.4',
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-all',
                        }}
                        onClick={() => this.copyToClipboard(localServerConfig)}
                        onMouseEnter={e => {
                          e.target.style.backgroundColor = 'rgba(232, 244, 253, 0.8)';
                          e.target.style.borderColor = 'rgba(24, 144, 255, 0.6)';
                          e.target.style.boxShadow =
                            '0 4px 16px rgba(24, 144, 255, 0.1), 0 2px 8px rgba(24, 144, 255, 0.05)';
                          e.target.style.transform = 'translateY(-2px)';
                        }}
                        onMouseLeave={e => {
                          e.target.style.backgroundColor = 'rgba(248, 248, 248, 0.7)';
                          e.target.style.borderColor = 'rgba(221, 221, 221, 0.4)';
                          e.target.style.boxShadow =
                            '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
                          e.target.style.transform = 'translateY(0)';
                        }}
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
                      <div style={{ marginTop: '12px' }}>
                        <Tab excessMode="dropdown" defaultActiveKey={0}>
                          {endpoints?.map(item => (
                            <Tab.Item key={item.index} title={item.address}>
                              <div style={{ marginTop: '12px' }}>
                                {/* Server Config */}
                                <div style={{ marginBottom: '24px' }}>
                                  <h4
                                    style={{
                                      color: '#333',
                                      fontWeight: 'bold',
                                      marginBottom: '12px',
                                      fontSize: '14px',
                                    }}
                                  >
                                    {locale.serverConfig || '客户端配置'}
                                  </h4>
                                  <pre
                                    style={{
                                      cursor: 'pointer',
                                      border: '1px solid rgba(221, 221, 221, 0.4)',
                                      borderRadius: '8px',
                                      padding: '12px',
                                      backgroundColor: 'rgba(248, 248, 248, 0.7)',
                                      backdropFilter: 'blur(10px)',
                                      boxShadow:
                                        '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                                      transition: 'all 0.3s ease',
                                      margin: 0,
                                      overflow: 'auto',
                                      maxHeight: '400px',
                                      fontSize: '12px',
                                      lineHeight: '1.4',
                                      whiteSpace: 'pre-wrap',
                                      wordBreak: 'break-all',
                                    }}
                                    onClick={() =>
                                      this.copyToClipboard(
                                        JSON.stringify(item.serverConfig, null, 2)
                                      )
                                    }
                                    onMouseEnter={e => {
                                      e.target.style.backgroundColor = 'rgba(232, 244, 253, 0.8)';
                                      e.target.style.borderColor = 'rgba(24, 144, 255, 0.6)';
                                      e.target.style.boxShadow =
                                        '0 4px 16px rgba(24, 144, 255, 0.1), 0 2px 8px rgba(24, 144, 255, 0.05)';
                                      e.target.style.transform = 'translateY(-2px)';
                                    }}
                                    onMouseLeave={e => {
                                      e.target.style.backgroundColor = 'rgba(248, 248, 248, 0.7)';
                                      e.target.style.borderColor = 'rgba(221, 221, 221, 0.4)';
                                      e.target.style.boxShadow =
                                        '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
                                      e.target.style.transform = 'translateY(0)';
                                    }}
                                    title="点击复制配置"
                                  >
                                    {JSON.stringify(item.serverConfig, null, 2)}
                                  </pre>
                                </div>

                                {/* Headers 配置 */}
                                <div>
                                  <h4
                                    style={{
                                      color: '#333',
                                      fontWeight: 'bold',
                                      marginBottom: '12px',
                                      fontSize: '14px',
                                    }}
                                  >
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
                        <div
                          style={{
                            border: '1px solid rgba(230, 230, 230, 0.4)',
                            borderRadius: '8px',
                            padding: '16px',
                            marginBottom: '12px',
                            backgroundColor: 'rgba(250, 250, 250, 0.7)',
                            backdropFilter: 'blur(10px)',
                            boxShadow:
                              '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                            transition: 'all 0.3s ease',
                            textAlign: 'center',
                            minHeight: '120px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                          onMouseEnter={e => {
                            e.currentTarget.style.transform = 'translateY(-2px)';
                            e.currentTarget.style.boxShadow =
                              '0 8px 24px rgba(0, 0, 0, 0.12), 0 4px 12px rgba(0, 0, 0, 0.08)';
                          }}
                          onMouseLeave={e => {
                            e.currentTarget.style.transform = 'translateY(0)';
                            e.currentTarget.style.boxShadow =
                              '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)';
                          }}
                        >
                          <div>
                            <div
                              style={{
                                fontSize: '48px',
                                color: '#d9d9d9',
                                marginBottom: '12px',
                                fontWeight: '300',
                              }}
                            >
                              📡
                            </div>
                            <p
                              style={{
                                color: '#666',
                                fontStyle: 'italic',
                                margin: 0,
                                fontSize: '14px',
                              }}
                            >
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
