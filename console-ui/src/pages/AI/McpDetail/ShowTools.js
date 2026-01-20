import React, { useRef, useState } from 'react';
import { Button, Card, Dialog, Form, Grid, Icon, Input, Message, Tree, Upload } from '@alifd/next';
import CreateTools from './CreateTools';
import DeleteTool from './CreateTools/DeleteTool';
import { getParams, request } from '../../../globalLib';
import extractToolsFromOpenAPI from './Swagger2Tools';
import './ShowTools.css';

const { Row, Col } = Grid;
const currentNamespace = getParams('namespace');

// 文本截断工具：超过指定长度使用省略号
const truncateText = (text, maxLen = 16) => {
  if (!text) return '';
  const str = String(text);
  return str.length > maxLen ? str.slice(0, maxLen) + '...' : str;
};

const ShowTools = props => {
  const {
    serverConfig = {
      protocol: '',
    },
    frontProtocol = 'stdio',
    address,
    port,
    useExistService,
    service,
    exportPath,
    restToMcpSwitch = false,
    locale,
    isPreview = false,
    onlyEditRuntimeInfo = false,
  } = props;
  const [importLoading, setImportLoading] = useState(false);
  const [token, setToken] = useState('');
  const [tokenDialogVisible, setTokenDialogVisible] = useState(false);
  const [backendAddress, setBackendAddress] = useState(address);
  const [backendPort, setBackendPort] = useState(port);
  const toolsRef = useRef(null);
  const [file, setFile] = useState(null);
  const [openApiDialogVisible, setOpenApiDialogVisible] = useState(false);
  const [activeToolIndex, setActiveToolIndex] = useState(0);
  const [toolSearchKeyword, setToolSearchKeyword] = useState('');

  // 初始化参数映射表
  const inputParameterMap = useRef(new Map());
  const outputParameterMap = useRef(new Map());

  const getServerDetail = () => {
    props.getServerDetail && props.getServerDetail();
  };

  // 构建参数树形数据结构
  const buildParameterTreeData = (properties, required = [], parentKey = '', mapRef) => {
    if (!properties) return [];

    const targetMapRef = mapRef || inputParameterMap;

    // 只在顶层调用时清空参数映射表
    if (!parentKey) {
      targetMapRef.current = new Map();
    }

    return Object.entries(properties).map(([paramName, paramDef], index) => {
      const nodeKey = parentKey ? `${parentKey}-${paramName}-${index}` : `${paramName}-${index}`;
      const isRequired = required.includes(paramName);
      const hasDefault = paramDef.default !== undefined;
      const paramType = paramDef.type || 'string';

      // 将参数信息存储到映射表中
      targetMapRef.current.set(nodeKey, {
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
        targetMapRef.current.set(descKey, {
          name: locale?.description || 'Description',
          type: 'info',
          description: paramDef.description,
          isInfoNode: true,
        });
        children.push({
          key: descKey,
          label: `${locale?.description || 'Description'}: ${truncateText(paramDef.description, 64)}`,
          isLeaf: true,
        });
      }

      if (hasDefault) {
        const defaultKey = `${nodeKey}-default`;
        targetMapRef.current.set(defaultKey, {
          name: locale?.defaultValue || 'Default',
          type: 'info',
          description: JSON.stringify(paramDef.default),
          isInfoNode: true,
        });
        children.push({
          key: defaultKey,
          label: `${locale?.defaultValue || 'Default'}: ${JSON.stringify(paramDef.default)}`,
          isLeaf: true,
        });
      }

      if (paramDef.enum) {
        const enumValue = Array.isArray(paramDef.enum) ? paramDef.enum.join(', ') : paramDef.enum;
        const enumKey = `${nodeKey}-enum`;
        targetMapRef.current.set(enumKey, {
          name: locale?.enum || 'Enum',
          type: 'info',
          description: enumValue,
          isInfoNode: true,
        });
        children.push({
          key: enumKey,
          label: `${locale?.enum || 'Enum'}: ${enumValue}`,
          isLeaf: true,
        });
      }

      if (paramDef.format) {
        const formatKey = `${nodeKey}-format`;
        targetMapRef.current.set(formatKey, {
          name: locale?.format || 'Format',
          type: 'info',
          description: paramDef.format,
          isInfoNode: true,
        });
        children.push({
          key: formatKey,
          label: `${locale?.format || 'Format'}: ${paramDef.format}`,
          isLeaf: true,
        });
      }

      // 递归处理object类型的属性
      if (paramType === 'object' && paramDef.properties) {
        const objectRequired = paramDef.required || [];
        const objectChildren = buildParameterTreeData(
          paramDef.properties,
          objectRequired,
          `${nodeKey}-props`,
          targetMapRef
        );

        if (objectChildren.length > 0) {
          const propsKey = `${nodeKey}-properties`;
          targetMapRef.current.set(propsKey, {
            name: locale?.properties || 'Properties',
            type: 'group',
            description: locale?.objectProperties || 'Object properties',
            isGroupNode: true,
          });
          children.push({
            key: propsKey,
            label: locale?.properties || 'Properties',
            children: objectChildren,
            isLeaf: false,
          });
        }
      }

      // 递归处理array类型的属性
      if (paramType === 'array' && paramDef.items) {
        // 递归构建数组项的子树
        const buildArrayItemSubtree = (itemDef, itemKey) => {
          const subChildren = [];
          const itemType = itemDef.type || (itemDef.properties ? 'object' : 'string');

          // 如果数组项是对象
          if (itemType === 'object' && itemDef.properties) {
            const itemRequired = itemDef.required || [];
            const propertiesChildren = buildParameterTreeData(
              itemDef.properties,
              itemRequired,
              `${itemKey}-props`,
              targetMapRef
            );
            if (propertiesChildren.length > 0) {
              subChildren.push(...propertiesChildren);
            }
          }
          // 如果数组项是另一个数组（嵌套数组）
          else if (itemType === 'array' && itemDef.items) {
            const nestedItemKey = `${itemKey}-items`;
            const nestedChildren = buildArrayItemSubtree(itemDef.items, nestedItemKey);
            if (nestedChildren.length > 0) {
              const itemsNodeKey = `${nestedItemKey}-group`;
              targetMapRef.current.set(itemsNodeKey, {
                name: 'items',
                type: itemDef.items.type,
                isGroupNode: true,
              });
              subChildren.push({
                key: itemsNodeKey,
                label: `items (${itemDef.items.type || 'object'})`,
                children: nestedChildren,
                isLeaf: false,
              });
            }
          }
          // 如果数组项是基本类型
          else {
            const itemInfo = [];
            if (itemDef.type) itemInfo.push(`${locale?.type || 'Type'}: ${itemDef.type}`);
            if (itemDef.description) itemInfo.push(`${locale?.description || 'Description'}: ${itemDef.description}`);
            if (itemDef.format) itemInfo.push(`${locale?.format || 'Format'}: ${itemDef.format}`);

            if (itemInfo.length > 0) {
              const itemInfoKey = `${itemKey}-info`;
              targetMapRef.current.set(itemInfoKey, {
                name: locale?.arrayItemInfo || 'Array item info',
                type: 'info',
                description: itemInfo.join(', '),
                isInfoNode: true,
              });
              subChildren.push({
                key: itemInfoKey,
                label: `${locale?.arrayItemInfo || 'Array item info'}: ${itemInfo.join(', ')}`,
                isLeaf: true,
              });
            }
          }
          return subChildren;
        };

        const itemChildren = buildArrayItemSubtree(paramDef.items, `${nodeKey}-items`);

        if (itemChildren.length > 0) {
          const itemsKey = `${nodeKey}-items-group`;
          targetMapRef.current.set(itemsKey, {
            name: 'items',
            type: paramDef.items.type,
            isGroupNode: true,
          });
          children.push({
            key: itemsKey,
            label: `items (${paramDef.items.type || 'object'})`,
            children: itemChildren,
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

  const openToolDetail = params => {
    const { type, record } = params;
    const toolsMeta = serverConfig?.toolSpec?.toolsMeta?.[record.name];
    toolsRef?.current?.openVisible && toolsRef.current.openVisible({ type, record, toolsMeta });
  };

  const importToolsFromOpenApi = () => {
    setOpenApiDialogVisible(true);
    setFile(null);
  };

  const handleFileChange = info => {
    // Check if info is a File object directly (standard drag/drop or input)
    if (info instanceof File || info instanceof Blob) {
      setFile(info);
      return;
    }

    // Handle @alifd/next Upload component callback structure
    // info can be { file: { originFileObj: File, ... }, fileList: [...] }
    const originFile = info.file
      ? info.file.originFileObj
      : info.originFileObj
      ? info.originFileObj
      : info;

    if (originFile instanceof File || originFile instanceof Blob) {
      setFile(originFile);
    } else {
      // Fallback or error handling if needed, though usually Upload ensures valid file
      console.warn('Unable to extract File object from upload callback', info);
    }
  };

  // 读取文件内容
  const readAndParseFile = file => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();

      reader.onload = e => {
        const content = e.target?.result;
        if (!content) {
          reject(new Error(locale.fileReadFailed));
          return;
        }
        resolve(content);
      };

      reader.onerror = () => {
        reject(new Error(locale.fileReadFailed));
      };

      reader.readAsText(file);
    });
  };

  const handleConfirm = async () => {
    if (!file) {
      Message.error(locale.pleaseSelectFile);
      return;
    }

    try {
      const content = await readAndParseFile(file);
      // Dynamic import to avoid circular dependency issues if any, or just standard import usage
      const { parseOpenAPI, transformToolsFromConfig } = await import('../services/OpenApiService');
      const doc = await parseOpenAPI(content);

      // Swagger2Tools might still be needed for initial extraction from doc if transformToolsFromConfig expects internal format
      // transformToolsFromConfig expects the output of extractToolsFromOpenAPI?
      // Check: extractToolsFromOpenAPI(doc) returns { tools: [], server: {...} }
      const config = extractToolsFromOpenAPI(doc);

      const toolSpecification = transformToolsFromConfig(config);

      if (props?.onChange) {
        props.onChange(toolSpecification);
      }
      Message.success(locale.importSuccess);
      setOpenApiDialogVisible(false);
    } catch (error) {
      Message.error(locale.fileInvalidFormat + ': ' + error.message);
      console.error('Import failed:', error);
    }
  };

  const openDialog = () => {
    toolsRef?.current?.openVisible &&
      toolsRef.current.openVisible({
        record: {
          name: '',
          description: '',
        },
        type: '',
        toolsMeta: {
          enabled: true,
        },
      });
  };

  const autoImportToolsFromMCPServer = async () => {
    setBackendAddress(address);
    setBackendPort(port);
    if (!useExistService && (!address || !port)) {
      Message.error(locale.pleaseEnterIPAndPort); // 弹出错误提示
      return; // 阻止后续逻辑执行
    }
    if (useExistService && !service) {
      Message.error(locale.pleaseEnterServiceName);
      return; // 弹出错误提示
    }

    if (useExistService) {
      const [groupName, serviceName] = service.split('@@');

      const url =
        currentNamespace === null
          ? 'v3/console/ns/instance/list'
          : `v3/console/ns/instance/list?namespaceId=${currentNamespace}`;
      try {
        const result = await request({
          url,
          data: {
            serviceName,
            groupName,
            pageSize: 100,
            pageNo: 1,
          },
        });
        if (result.code === 0 && result.data) {
          const healthyInstance = result.data.pageItems.find(item => item?.healthy === true);
          if (healthyInstance) {
            setBackendAddress(healthyInstance.ip);
            setBackendPort(healthyInstance.port);
          } else {
            Message.error(locale.noHealthyInstance);
            return;
          }
        } else {
          Message.error(locale.noHealthyInstance);
          return;
        }
      } catch (error) {
        Message.error(locale.noHealthyInstance);
        console.error('Import tools failed:', error);
        return;
      }
    }

    // 弹出 Token 输入弹窗
    setToken('');
    setTokenDialogVisible(true);
  };

  console.log('isPreview:', isPreview);
  console.log('onlyEditRuntimeInfo:', onlyEditRuntimeInfo);
  console.log('restToMcpSwitch:', restToMcpSwitch);
  console.log('fontProtocol:', frontProtocol);

  return (
    <Card
      className={`show-tools-card ${
        isPreview || onlyEditRuntimeInfo ? (isPreview ? 'preview' : 'edit-mode') : ''
      }`}
      contentHeight="auto"
    >
      {/* Tools 展示 - 使用与 McpDetail 相同的左右分栏风格 */}
      {serverConfig?.toolSpec?.tools && serverConfig.toolSpec.tools.length > 0 ? (
        <>
          {/* 当有tools时，显示添加按钮 */}
          {!isPreview && !onlyEditRuntimeInfo && (
            <Button type="primary" onClick={openDialog} className="show-tools-btn-mr">
              {locale.newMcpTool}
            </Button>
          )}

          {!isPreview && !onlyEditRuntimeInfo && frontProtocol === 'mcp-sse' && !restToMcpSwitch && (
            <Button
              type="primary"
              onClick={autoImportToolsFromMCPServer}
              className="show-tools-btn-mr"
              loading={importLoading}
              disabled={importLoading}
            >
              {importLoading ? locale.importing : locale.importToolsFromMCP}
            </Button>
          )}

          {!isPreview && !onlyEditRuntimeInfo && frontProtocol !== 'stdio' && restToMcpSwitch && (
            <Button
              type="primary"
              onClick={importToolsFromOpenApi}
              className="show-tools-btn-mr"
              loading={importLoading}
              disabled={importLoading}
            >
              {importLoading ? locale.importing : locale.importToolsFromOpenAPI}
            </Button>
          )}

          <div className="tools-layout">
            {/* 左侧标签栏 */}
            <div className="tools-sidebar">
              {/* Sidebar Search */}
              <div style={{ padding: '0 12px 12px 0' }}>
                <Input
                  hasClear
                  placeholder={locale.searchTool || 'Search tools...'}
                  value={toolSearchKeyword}
                  onChange={val => setToolSearchKeyword(val)}
                  style={{ width: '100%' }}
                />
              </div>
              <div className="tools-sidebar-list">
                {(serverConfig.toolSpec.tools || [])
                  .filter(
                    t =>
                      !toolSearchKeyword ||
                      t.name.toLowerCase().includes(toolSearchKeyword.toLowerCase())
                  )
                  .map((tool, index) => {
                    // 获取工具的在线状态
                    const toolsMeta = serverConfig?.toolSpec?.toolsMeta?.[tool.name];
                    const isOnline = toolsMeta ? toolsMeta.enabled : true;
                    const originalIndex = serverConfig.toolSpec.tools.findIndex(
                      t => t.name === tool.name
                    );
                    const isActive = activeToolIndex === originalIndex;

                    return (
                      <div
                        key={tool.name}
                        className={`tool-item ${isActive ? 'active' : ''}`}
                        onClick={() => setActiveToolIndex(originalIndex)}
                      >
                        <div className="tool-item-title">{tool.name}</div>
                        <div className="tool-item-status-bar">
                          <span
                            className={`tool-status-badge ${isOnline ? 'enabled' : 'disabled'}`}
                          >
                            {isOnline ? (locale?.enabled || 'Enabled') : (locale?.disabled || 'Disabled')}
                          </span>
                          {tool.inputSchema?.properties && (
                            <span className="tool-param-count">
                              {Object.keys(tool.inputSchema.properties).length} {locale?.parameters || 'parameters'}
                            </span>
                          )}
                        </div>
                        {/* 操作按钮 - 只保留编辑和删除 */}
                        {!isPreview && (
                          <div className="tool-item-actions">
                            <div className="tool-item-actions-row">
                              <a
                                className="tool-action-link"
                                onClick={e => {
                                  e.stopPropagation();
                                  openToolDetail({ type: 'edit', record: tool });
                                }}
                              >
                                {locale.operationToolEdit}
                              </a>
                              {!onlyEditRuntimeInfo && (
                                <>
                                  <span className="tool-action-separator">|</span>
                                  <DeleteTool
                                    record={tool}
                                    locale={locale}
                                    serverConfig={serverConfig}
                                    getServerDetail={getServerDetail}
                                    onChange={props?.onChange}
                                    size="small"
                                  />
                                </>
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
              </div>
            </div>

            {/* 右侧内容区 */}
            <div className="tools-content">
              {(() => {
                const tool = serverConfig.toolSpec.tools[activeToolIndex];
                if (!tool) return null;

                return (
                  <div className="tool-detail-container">
                    {/* Tool 标题 */}
                    <h2 className="tool-detail-title">{tool.name}</h2>

                    {/* Tool 信息 */}
                    {tool.description && (
                      <div className="tool-detail-description">
                        <p>{tool.description}</p>
                      </div>
                    )}

                    {/* Meta 信息 */}
                    {tool._meta && Object.keys(tool._meta).length > 0 && (
                      <div className="parameters-section">
                        <h3 className="parameters-section-title">
                          {locale?.metaField || 'Meta'}
                          <span className="parameters-section-count">(MCP _meta)</span>
                        </h3>
                        <div className="content-box" style={{ backgroundColor: '#f8f9fa' }}>
                          <pre
                            style={{
                              margin: 0,
                              padding: '12px',
                              fontSize: '13px',
                              fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-all',
                              backgroundColor: 'transparent',
                            }}
                          >
                            {JSON.stringify(tool._meta, null, 2)}
                          </pre>
                        </div>
                      </div>
                    )}

                    {/* Annotations 信息 */}
                    {tool.annotations && (
                      <div className="parameters-section">
                        <h3 className="parameters-section-title">
                          {locale?.annotationsConfig || 'Annotations'}
                          <span className="parameters-section-count">(Tool Hints)</span>
                        </h3>
                        <div className="content-box" style={{ backgroundColor: '#f8f9fa' }}>
                          <div style={{ padding: '12px' }}>
                            {tool.annotations.title && (
                              <div className="kv-row" style={{ marginBottom: '8px' }}>
                                <span className="kv-label" style={{ fontWeight: '500' }}>
                                  Title:{' '}
                                </span>
                                <span className="kv-value">{tool.annotations.title}</span>
                              </div>
                            )}
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px' }}>
                              <div className="kv-row">
                                <span className="kv-label">Read Only: </span>
                                <span
                                  className={`kv-value ${
                                    tool.annotations.readOnlyHint ? 'green' : ''
                                  }`}
                                >
                                  {tool.annotations.readOnlyHint ? 'Yes' : 'No'}
                                </span>
                              </div>
                              <div className="kv-row">
                                <span className="kv-label">Destructive: </span>
                                <span
                                  className={`kv-value ${
                                    tool.annotations.destructiveHint ? 'red' : 'green'
                                  }`}
                                >
                                  {tool.annotations.destructiveHint ? 'Yes' : 'No'}
                                </span>
                              </div>
                              <div className="kv-row">
                                <span className="kv-label">Idempotent: </span>
                                <span
                                  className={`kv-value ${
                                    tool.annotations.idempotentHint ? 'green' : ''
                                  }`}
                                >
                                  {tool.annotations.idempotentHint ? 'Yes' : 'No'}
                                </span>
                              </div>
                              <div className="kv-row">
                                <span className="kv-label">Open World: </span>
                                <span className="kv-value">
                                  {tool.annotations.openWorldHint ? 'Yes' : 'No'}
                                </span>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Tool 参数配置 */}
                    {tool.inputSchema?.properties &&
                      Object.keys(tool.inputSchema.properties).length > 0 && (
                        <div className="parameters-section">
                          <h3 className="parameters-section-title">
                            {locale?.parameters || '参数配置'}
                            <span className="parameters-section-count">
                              (共 {Object.keys(tool.inputSchema.properties).length} 项)
                            </span>
                          </h3>

                          <div className="parameters-container">
                            <Tree
                              dataSource={buildParameterTreeData(
                                tool.inputSchema.properties,
                                tool.inputSchema.required,
                                '',
                                inputParameterMap
                              )}
                              showLine
                              isLabelBlock
                              className="parameters-tree"
                              labelRender={node => {
                                // 从参数映射表中获取节点数据
                                const nodeData = inputParameterMap.current?.get(node.key);

                                // 检查是否是组织节点（属性、数组项定义等）
                                if (nodeData?.isGroupNode) {
                                  return (
                                    <span className="tree-group-label">
                                      {node.label}{' '}
                                      <span className="group-type-badge">{nodeData.type}</span>
                                    </span>
                                  );
                                }

                                // 检查是否是参数节点
                                if (nodeData?.isParameterNode || node.isLeaf) {
                                  const typeLower = (nodeData.type || 'string').toLowerCase();
                                  return (
                                    <div className="param-row">
                                      <span className="param-name">{nodeData.name}</span>

                                      <span className={`type-badge type-${typeLower}`}>
                                        {nodeData.type || 'string'}
                                      </span>

                                      {nodeData.isRequired && (
                                        <span className="required-prop">* Required</span>
                                      )}

                                      {nodeData.hasDefault && (
                                        <span className="default-prop">
                                          Default: {String(nodeData.defaultValue)}
                                        </span>
                                      )}

                                      {nodeData.enum && (
                                        <span className="enum-prop">
                                          Enum: [
                                          {Array.isArray(nodeData.enum)
                                            ? nodeData.enum.join(', ')
                                            : nodeData.enum}
                                          ]
                                        </span>
                                      )}

                                      <span
                                        className="param-desc"
                                        title={nodeData.description || ''}
                                      >
                                        {nodeData.description
                                          ? `- ${truncateText(nodeData.description, 100)}`
                                          : ''}
                                      </span>
                                    </div>
                                  );
                                }

                                // 信息节点
                                if (nodeData?.isInfoNode) {
                                  return (
                                    <span
                                      className="info-label"
                                      title={`${nodeData.name}: ${nodeData.description}`}
                                    >
                                      <span className="info-key">{nodeData.name}:</span>{' '}
                                      {nodeData.description}
                                    </span>
                                  );
                                }

                                return <span className="plain-label">{node.label}</span>;
                              }}
                            />
                          </div>
                        </div>
                      )}

                    {tool.outputSchema?.properties &&
                      Object.keys(tool.outputSchema.properties).length > 0 && (
                        <div className="parameters-section">
                          <h3 className="parameters-section-title">
                            {locale?.toolOutputSchema || '出参配置'}
                            <span className="parameters-section-count">
                              (共 {Object.keys(tool.outputSchema.properties).length} 项)
                            </span>
                          </h3>

                          <div className="parameters-container">
                            <Tree
                              dataSource={buildParameterTreeData(
                                tool.outputSchema.properties,
                                tool.outputSchema.required,
                                '',
                                outputParameterMap
                              )}
                              showLine
                              isLabelBlock
                              className="parameters-tree"
                              labelRender={node => {
                                const nodeData = outputParameterMap.current?.get(node.key);

                                if (nodeData?.isGroupNode) {
                                  return (
                                    <span className="tree-group-label">
                                      {node.label}{' '}
                                      <span className="group-type-badge">{nodeData.type}</span>
                                    </span>
                                  );
                                }

                                if (nodeData?.isParameterNode || node.isLeaf) {
                                  const typeLower = (nodeData.type || 'string').toLowerCase();
                                  return (
                                    <div className="param-row">
                                      <span className="param-name">{nodeData.name}</span>
                                      <span className={`type-badge type-${typeLower}`}>
                                        {nodeData.type || 'string'}
                                      </span>

                                      {nodeData.isRequired && (
                                        <span className="required-prop">* Required</span>
                                      )}

                                      {nodeData.hasDefault && (
                                        <span className="default-prop">
                                          Default: {String(nodeData.defaultValue)}
                                        </span>
                                      )}

                                      <span
                                        className="param-desc"
                                        title={nodeData.description || ''}
                                      >
                                        {nodeData.description
                                          ? `- ${truncateText(nodeData.description, 100)}`
                                          : ''}
                                      </span>
                                    </div>
                                  );
                                }

                                if (nodeData?.isInfoNode) {
                                  return (
                                    <span className="info-label">
                                      <span className="info-key">{nodeData.name}:</span>{' '}
                                      {nodeData.description}
                                    </span>
                                  );
                                }

                                return <span className="plain-label">{node.label}</span>;
                              }}
                            />
                          </div>
                        </div>
                      )}

                    {/* RestToMcp 场景下的协议转化配置和透明认证信息 */}
                    {frontProtocol !== 'stdio' &&
                      restToMcpSwitch &&
                      (() => {
                        const toolsMeta = serverConfig?.toolSpec?.toolsMeta?.[tool.name];
                        const templateData = toolsMeta?.templates?.['json-go-template'];

                        if (templateData) {
                          return (
                            <div className="protocol-conversion-section">
                              <h3 className="protocol-conversion-title">
                                {locale?.protocolConversion || '协议转化配置'}
                              </h3>

                              <div className="protocol-conversion-container">
                                {/* 透明认证信息 */}
                                {templateData.security && (
                                  <div className="show-tools-mb-16">
                                    <h4 className="subsection-title transparent-auth">
                                      {locale?.transparentAuth || '透明认证信息'}
                                    </h4>
                                    <div className="content-box">
                                      <div className="kv-row">
                                        <span className="kv-label">{locale?.enableStatus || 'Enable Status'}: </span>
                                        <span
                                          className={`kv-value ${
                                            templateData.security.passthrough ? 'green' : ''
                                          }`}
                                        >
                                          {templateData.security.passthrough ? (locale?.enabled || 'Enabled') : (locale?.disabled || 'Disabled')}
                                        </span>
                                      </div>
                                      {templateData.security.id && (
                                        <div className="kv-row">
                                          <span className="kv-label">{locale?.clientAuthMethod || 'Client Auth Method'}: </span>
                                          <span className="kv-value blue">
                                            {templateData.security.id}
                                          </span>
                                        </div>
                                      )}
                                      {templateData.security.type && (
                                        <div className="kv-row">
                                          <span className="kv-label">{locale?.authType || 'Auth Type'}: </span>
                                          <span className="kv-value">
                                            {templateData.security.type}
                                          </span>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                )}

                                {/* 请求模板信息 */}
                                {templateData.requestTemplate && (
                                  <div className="show-tools-mb-16">
                                    <h4 className="subsection-title request-template">
                                      {locale?.requestTemplate || '请求模板配置'}
                                    </h4>
                                    <div className="content-box light-blue">
                                      {templateData.requestTemplate.method && (
                                        <div className="kv-row">
                                          <span className="kv-label">{locale?.httpMethod || 'HTTP Method'}: </span>
                                          <span
                                            className={`http-method-badge ${
                                              String(
                                                templateData.requestTemplate.method
                                              ).toLowerCase() === 'get'
                                                ? 'get'
                                                : String(
                                                    templateData.requestTemplate.method
                                                  ).toLowerCase() === 'post'
                                                ? 'post'
                                                : String(
                                                    templateData.requestTemplate.method
                                                  ).toLowerCase() === 'put'
                                                ? 'put'
                                                : String(
                                                    templateData.requestTemplate.method
                                                  ).toLowerCase() === 'delete'
                                                ? 'delete'
                                                : 'other'
                                            }`}
                                          >
                                            {templateData.requestTemplate.method}
                                          </span>
                                        </div>
                                      )}
                                      {templateData.requestTemplate.url && (
                                        <div className="kv-row">
                                          <span className="kv-label">{locale?.requestPath || 'Request Path'}: </span>
                                          <span className="url-chip">
                                            {templateData.requestTemplate.url}
                                          </span>
                                        </div>
                                      )}
                                      {templateData.requestTemplate.security && (
                                        <div className="kv-row">
                                          <span className="kv-label">{locale?.backendAuthMethod || 'Backend Auth Method'}: </span>
                                          <span className="kv-value orange">
                                            {templateData.requestTemplate.security.id}
                                          </span>
                                        </div>
                                      )}

                                      {/* 请求头 */}
                                      {templateData.requestTemplate.headers &&
                                        Object.keys(templateData.requestTemplate.headers).length >
                                          0 && (
                                          <div className="show-tools-mb-12">
                                            <div className="headers-title">headers:</div>
                                            <div className="headers-box">
                                              {typeof templateData.requestTemplate.headers ===
                                              'object' ? (
                                                Object.entries(
                                                  templateData.requestTemplate.headers
                                                ).map(([key, value], index) => (
                                                  <div key={index} className="header-row">
                                                    <span className="header-key">{key}:</span>
                                                    <span className="header-value">
                                                      {typeof value === 'object'
                                                        ? JSON.stringify(value)
                                                        : String(value)}
                                                    </span>
                                                  </div>
                                                ))
                                              ) : (
                                                <div className="header-raw">
                                                  {templateData.requestTemplate.headers}
                                                </div>
                                              )}
                                            </div>
                                          </div>
                                        )}

                                      {/* 请求体 */}
                                      {templateData.requestTemplate.body && (
                                        <div className="show-tools-mb-12">
                                          <div className="body-title">body:</div>
                                          <div className="body-box">
                                            {typeof templateData.requestTemplate.body === 'object'
                                              ? JSON.stringify(
                                                  templateData.requestTemplate.body,
                                                  null,
                                                  2
                                                )
                                              : templateData.requestTemplate.body}
                                          </div>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                )}

                                {/* 响应模板信息 */}
                                {templateData.responseTemplate && (
                                  <div>
                                    <h4 className="subsection-title response-template">
                                      {locale?.responseTemplate || '响应模板配置'}
                                    </h4>
                                    <div className="content-box light-orange">
                                      {/* 响应体模板 */}
                                      {templateData.responseTemplate.body && (
                                        <div className="show-tools-mb-12">
                                          <div className="section-title-sm">body:</div>
                                          <div className="resp-body-box">
                                            {templateData.responseTemplate.body}
                                          </div>
                                        </div>
                                      )}

                                      {/* 响应前缀 */}
                                      {templateData.responseTemplate.prependBody && (
                                        <div className="show-tools-mb-12">
                                          <div className="section-title-sm">prependBody:</div>
                                          <div className="resp-prepend-box">
                                            {templateData.responseTemplate.prependBody}
                                          </div>
                                        </div>
                                      )}

                                      {/* 响应后缀 */}
                                      {templateData.responseTemplate.appendBody && (
                                        <div className="show-tools-mb-12">
                                          <div className="section-title-sm">appendBody:</div>
                                          <div className="resp-append-box">
                                            {templateData.responseTemplate.appendBody}
                                          </div>
                                        </div>
                                      )}

                                      {/* 其他响应模板字段 */}
                                      {(() => {
                                        const responseTemplate = templateData.responseTemplate;
                                        const knownFields = ['body', 'prependBody', 'appendBody'];
                                        const otherFields = Object.keys(responseTemplate).filter(
                                          key => !knownFields.includes(key)
                                        );

                                        if (otherFields.length > 0) {
                                          return (
                                            <div>
                                              <div className="other-config-title">{locale?.otherConfig || 'Other Config'}:</div>
                                              {otherFields.map(field => (
                                                <div key={field} className="show-tools-mb-6">
                                                  <span className="other-config-key">
                                                    {field}:{' '}
                                                  </span>
                                                  <span className="other-config-value">
                                                    {typeof responseTemplate[field] === 'object'
                                                      ? JSON.stringify(
                                                          responseTemplate[field],
                                                          null,
                                                          2
                                                        )
                                                      : String(responseTemplate[field])}
                                                  </span>
                                                </div>
                                              ))}
                                            </div>
                                          );
                                        }
                                        return null;
                                      })()}

                                      {/* 如果没有任何字段，显示完整对象 */}
                                      {!templateData.responseTemplate.body &&
                                        !templateData.responseTemplate.prependBody &&
                                        !templateData.responseTemplate.appendBody &&
                                        Object.keys(templateData.responseTemplate).length === 0 && (
                                          <div className="empty-tip">{locale?.noResponseTemplateConfig || 'No response template configuration'}</div>
                                        )}
                                    </div>
                                  </div>
                                )}
                              </div>
                            </div>
                          );
                        }
                        return null;
                      })()}
                  </div>
                );
              })()}
            </div>
          </div>
        </>
      ) : (
        <div className="no-tools-container">
          <div className="no-tools-emoji">🔧</div>
          <p className="no-tools-text">{locale.noToolsAvailable || '暂无可用的 Tools'}</p>

          {!isPreview && !onlyEditRuntimeInfo && (
            <div className="no-tools-actions">
              <Button type="primary" onClick={openDialog} className="btn-wide">
                {locale.newMcpTool}
              </Button>

              {frontProtocol === 'mcp-sse' && !restToMcpSwitch && (
                <Button
                  type="normal"
                  onClick={autoImportToolsFromMCPServer}
                  loading={importLoading}
                  disabled={importLoading}
                  className="btn-wide"
                >
                  {importLoading ? locale.importing : locale.importToolsFromMCP}
                </Button>
              )}

              {frontProtocol !== 'stdio' && restToMcpSwitch && (
                <Button
                  type="normal"
                  onClick={importToolsFromOpenApi}
                  loading={importLoading}
                  disabled={importLoading}
                  className="btn-wide"
                >
                  {importLoading ? locale.importing : locale.importToolsFromOpenAPI}
                </Button>
              )}
            </div>
          )}
        </div>
      )}

      <CreateTools
        key={JSON.stringify(serverConfig)}
        locale={locale}
        serverConfig={serverConfig}
        showTemplates={frontProtocol !== 'stdio' && restToMcpSwitch}
        ref={toolsRef}
        getServerDetail={getServerDetail}
        onChange={props?.onChange}
        onlyEditRuntimeInfo={onlyEditRuntimeInfo}
      />

      <Dialog
        title={locale.importToolsFromOpenAPI}
        visible={openApiDialogVisible}
        onOk={handleConfirm}
        onCancel={() => setOpenApiDialogVisible(false)}
        onClose={() => setOpenApiDialogVisible(false)}
        className="openapi-dialog"
      >
        <Form>
          <Form.Item label={locale.selectOpenAPIFile}>
            <Upload
              listType="text"
              accept=".json,.yaml,.yml"
              onChange={handleFileChange}
              limit={1}
              reUpload={true}
              beforeUpload={handleFileChange} // Use same handler to capture file
              dragable
              className="upload-drag-area"
            >
              <p className="upload-drag-icon">
                <Icon type="upload" />
              </p>
              <div className="upload-drag-inner">
                <p className="upload-drag-text">{locale.dragAndDropFileHereOrClickToSelect}</p>
              </div>
            </Upload>
          </Form.Item>
        </Form>
      </Dialog>

      {tokenDialogVisible && (
        <Dialog
          title={locale.importToolsFromMCP}
          visible={tokenDialogVisible}
          onOk={async () => {
            // if (!token) {
            //   Message.error(locale.pleaseEnterToken);
            //   return;
            // }

            setImportLoading(true);
            setTokenDialogVisible(false);

            try {
              const protocol = Number(backendPort) === 443 ? 'https' : 'http';
              const mcpBaseUrl = `${protocol}://${backendAddress}:${backendPort}`;

              let url = `v3/console/ai/mcp/importToolsFromMcp?transportType=${frontProtocol}&baseUrl=${mcpBaseUrl}&endpoint=${exportPath}`;
              if (token) {
                url += `&authToken=${token}`;
              }

              const result = await request({
                url,
              });

              if (result.code === 0 && result.data) {
                const _tools = result.data;
                const _toolsMeta = {};
                const toolSpecification = JSON.stringify({
                  tools: _tools,
                  toolsMeta: _toolsMeta,
                });
                if (props?.onChange) {
                  props.onChange(JSON.parse(toolSpecification));
                }
                Message.success(locale.importSuccess);
              } else {
                Message.error(locale.importToolsFailed + ' ' + result.message);
                console.error('Import tools failed:', result);
              }
            } catch (error) {
              Message.error(locale.importToolsFailed);
              console.error('Import tools failed:', error);
            } finally {
              setImportLoading(false);
            }
          }}
          onCancel={() => setTokenDialogVisible(false)}
          onClose={() => setTokenDialogVisible(false)}
          className="token-dialog"
        >
          <Form>
            <Row gutter={20}>
              <Col span={15}>
                <Form.Item label={locale.address} labelAlign="left">
                  <span className="next-form-text">{backendAddress}</span>
                </Form.Item>
              </Col>
              <Col span={3}>
                <Form.Item label={locale.port} labelAlign="left">
                  <span className="next-form-text">{backendPort}</span>
                </Form.Item>
              </Col>
              <Col span={5}>
                <Form.Item label={locale.exportPath} labelAlign="left">
                  <span className="next-form-text">{exportPath}</span>
                </Form.Item>
              </Col>
            </Row>

            <Form.Item label={locale.authToken}>
              <Input.Password value={token} onChange={setToken} />
            </Form.Item>
          </Form>
        </Dialog>
      )}
    </Card>
  );
};

export default ShowTools;
