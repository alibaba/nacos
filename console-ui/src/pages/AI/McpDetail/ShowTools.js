import React, { useRef, useState } from 'react';
import { Button, Card, Dialog, Form, Grid, Icon, Input, Message, Tree, Upload } from '@alifd/next';
import CreateTools from './CreateTools';
import DeleteTool from './CreateTools/DeleteTool';
import { getParams, request } from '../../../globalLib';
import swagger2openapi from 'swagger2openapi';
import YAML from 'js-yaml';
import { extractToolsFromOpenAPI } from './Swagger2Tools';
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

  // 初始化参数映射表
  const parameterMap = useRef(new Map());

  const getServerDetail = () => {
    props.getServerDetail && props.getServerDetail();
  };

  // 构建参数树形数据结构
  const buildParameterTreeData = (properties, required = [], parentKey = '') => {
    if (!properties) return [];

    // 只在顶层调用时清空参数映射表
    if (!parentKey) {
      parameterMap.current = new Map();
    }

    return Object.entries(properties).map(([paramName, paramDef], index) => {
      const nodeKey = parentKey ? `${parentKey}-${paramName}-${index}` : `${paramName}-${index}`;
      const isRequired = required.includes(paramName);
      const hasDefault = paramDef.default !== undefined;
      const paramType = paramDef.type || 'string';

      // 将参数信息存储到映射表中
      parameterMap.current.set(nodeKey, {
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
        parameterMap.current.set(descKey, {
          name: '描述',
          type: 'info',
          description: paramDef.description,
          isInfoNode: true,
        });
        children.push({
          key: descKey,
          label: `描述: ${truncateText(paramDef.description, 64)}`,
          isLeaf: true,
        });
      }

      if (hasDefault) {
        const defaultKey = `${nodeKey}-default`;
        parameterMap.current.set(defaultKey, {
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
        parameterMap.current.set(enumKey, {
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
        parameterMap.current.set(formatKey, {
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
        const objectChildren = buildParameterTreeData(
          paramDef.properties,
          objectRequired,
          `${nodeKey}-props`
        );

        if (objectChildren.length > 0) {
          const propsKey = `${nodeKey}-properties`;
          parameterMap.current.set(propsKey, {
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
              `${itemKey}-props`
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
              parameterMap.current.set(itemsNodeKey, {
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
            if (itemDef.type) itemInfo.push(`类型: ${itemDef.type}`);
            if (itemDef.description) itemInfo.push(`描述: ${itemDef.description}`);
            if (itemDef.format) itemInfo.push(`格式: ${itemDef.format}`);

            if (itemInfo.length > 0) {
              const itemInfoKey = `${itemKey}-info`;
              parameterMap.current.set(itemInfoKey, {
                name: '数组项信息',
                type: 'info',
                description: itemInfo.join(', '),
                isInfoNode: true,
              });
              subChildren.push({
                key: itemInfoKey,
                label: `数组项信息: ${itemInfo.join(', ')}`,
                isLeaf: true,
              });
            }
          }
          return subChildren;
        };

        const itemChildren = buildArrayItemSubtree(paramDef.items, `${nodeKey}-items`);

        if (itemChildren.length > 0) {
          const itemsKey = `${nodeKey}-items-group`;
          parameterMap.current.set(itemsKey, {
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
  };

  const handleFileChange = fileList => {
    if (fileList && fileList.length > 0) {
      fileList[0].state = 'success';
      setFile(fileList[0].originFileObj || fileList[0].file);
    }
  };

  const handleConfirm = async () => {
    if (!file) {
      Message.error(locale.pleaseSelectFile);
      return;
    }

    try {
      const content = await readAndParseFile(file);
      const doc = await parseOpenAPI(content);

      let config = extractToolsFromOpenAPI(doc);
      console.log(config);
      // 提取 OpenAPI 顶层的 securitySchemes
      const securitySchemes = Array.isArray(config?.server?.securitySchemes)
        ? config.server.securitySchemes
        : [];

      const toolsMeta = config.tools.reduce((acc, tool) => {
        const argsPosition = tool.args.reduce((acc, arg) => {
          acc[arg.name] = arg.position;
          return acc;
        }, {});
        acc[tool.name] = {
          enabled: true,
          templates: {
            'json-go-template': {
              responseTemplate: tool.responseTemplate,
              requestTemplate: tool.requestTemplate,
              argsPosition,
            },
          },
        };
        return acc;
      }, {});

      const tools = config.tools.map(tool => ({
        name: tool.name,
        description: tool.description,
        inputSchema: {
          type: 'object',
          properties: tool.args.reduce((acc, arg) => {
            acc[arg.name] = {
              type: arg.type,
              description: arg.description,
              properties: arg.properties,
            };
            return acc;
          }, {}),
          required: tool.args.filter(arg => arg.required).map(arg => arg.name),
        },
      }));

      // 在生成最终 specification 之前：将 argsPosition 合并进 requestTemplate
      try {
        // 建立一个快速索引：toolName -> args 数组（含类型、position）
        const toolArgsByName = config.tools.reduce((acc, t) => {
          acc[t.name] = t.args || [];
          return acc;
        }, {});

        const ensureHeadersArray = headers => {
          // 规范化 headers 为数组 [{key, value}, ...]
          if (!headers) return [];
          if (Array.isArray(headers)) return headers;
          if (typeof headers === 'object') {
            return Object.entries(headers).map(([k, v]) => ({ key: k, value: String(v) }));
          }
          return [];
        };

        const hasHeaderKey = (headers, key) => {
          return headers.some(h => (h.key || '').toLowerCase() === String(key).toLowerCase());
        };

        const getContentType = headers => {
          const h = headers.find(it => (it.key || '').toLowerCase() === 'content-type');
          return h ? String(h.value).toLowerCase() : '';
        };

        Object.keys(toolsMeta || {}).forEach(toolName => {
          const meta = toolsMeta[toolName];
          const tmpl = meta?.templates?.['json-go-template'];
          if (!tmpl || !tmpl.requestTemplate) return;

          const argsPos = tmpl.argsPosition || {};
          let url = tmpl.requestTemplate.url || '';
          let headers = ensureHeadersArray(tmpl.requestTemplate.headers);
          let body = tmpl.requestTemplate.body; // 可能为字符串或对象，保留原样优先

          // 收集各类参数名
          const allArgs = toolArgsByName[toolName] || [];
          const byName = allArgs.reduce((acc, a) => {
            acc[a.name] = a;
            return acc;
          }, {});

          const entries = Object.entries(argsPos);
          const pathArgs = entries.filter(([, pos]) => pos === 'path').map(([n]) => n);
          const queryArgs = entries.filter(([, pos]) => pos === 'query').map(([n]) => n);
          const headerArgs = entries.filter(([, pos]) => pos === 'header').map(([n]) => n);
          const cookieArgs = entries.filter(([, pos]) => pos === 'cookie').map(([n]) => n);
          const bodyArgs = entries.filter(([, pos]) => pos === 'body').map(([n]) => n);

          // 标记是否需要保留 argsPosition（当依赖 argsTo* flags 时需要）
          let shouldKeepArgsPosition = false;

          // 1) 处理 path 占位：将 {name} 替换为 {{urlqueryescape .args.name}}
          pathArgs.forEach(name => {
            const re = new RegExp(
              '\\{' + name.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\$&') + '\\}',
              'g'
            );
            // 不使用模板函数，直接插入占位 {{.args.name}}
            url = url.replace(re, `{{.args.${name}}}`);
          });

          // 统计总体位置
          const totalArgsCount = entries.length;
          const allInQuery = totalArgsCount > 0 && queryArgs.length === totalArgsCount;
          const allInBody = totalArgsCount > 0 && bodyArgs.length === totalArgsCount;

          // 2) 处理 query：当全部在 query 时，使用 argsToUrlParam 标记，不拼接到 URL
          if (allInQuery) {
            tmpl.requestTemplate.argsToUrlParam = true;
          } else if (queryArgs.length > 0) {
            // 混合场景下仍然把 query 参数拼接到 URL
            const pairs = queryArgs.map(name => `${name}={{.args.${name}}}`);
            const connector = url.includes('?') ? '&' : '?';
            url = url + (pairs.length > 0 ? connector + pairs.join('&') : '');
          }

          // 3) 处理 header：为每个 header 参数添加 header 条目
          if (headerArgs.length > 0) {
            headerArgs.forEach(name => {
              if (!hasHeaderKey(headers, name)) {
                // 不使用 toString，直接占位
                headers.push({ key: name, value: `{{.args.${name}}}` });
              }
            });
          }

          // 4) 处理 cookie：将所有 cookie 参数合并为一个 Cookie 头
          if (cookieArgs.length > 0) {
            const cookiePairs = cookieArgs.map(name => `${name}={{.args.${name}}}`);
            const cookieValue = cookiePairs.join('; ');
            const idx = headers.findIndex(h => (h.key || '').toLowerCase() === 'cookie');
            if (idx >= 0) {
              headers[idx].value = headers[idx].value
                ? `${headers[idx].value}; ${cookieValue}`
                : cookieValue;
            } else {
              headers.push({ key: 'Cookie', value: cookieValue });
            }
          }

          // 5) 处理 body：
          //    - 如果全部在 body：根据 Content-Type 设置 argsToJsonBody/argsToFormBody，不直接生成 body
          //    - 否则（混合场景）：若未显式提供 body/argsTo*，再根据 Content-Type 生成
          const hasExplicit =
            body !== undefined ||
            tmpl.requestTemplate.argsToJsonBody === true ||
            tmpl.requestTemplate.argsToFormBody === true ||
            tmpl.requestTemplate.argsToUrlParam === true;

          if (bodyArgs.length > 0) {
            const ct = getContentType(headers);
            if (allInBody) {
              // 全部在 body：通过标记控制
              if (
                ct.includes('application/x-www-form-urlencoded') ||
                ct.includes('multipart/form-data')
              ) {
                tmpl.requestTemplate.argsToFormBody = true;
              } else {
                tmpl.requestTemplate.argsToJsonBody = true;
                if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
                  headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
                }
              }
            } else if (!hasExplicit) {
              // 混合场景且未显式指定：保持原有自动生成策略
              if (ct.includes('application/x-www-form-urlencoded')) {
                const formPairs = bodyArgs.map(name => `${name}={{.args.${name}}}`);
                body = formPairs.join('&');
              } else {
                const hasComplex = bodyArgs.some(n => {
                  const a = byName[n];
                  const t = a && (a.type || (a.schema && a.schema.type));
                  return t === 'object' || t === 'array';
                });

                if (hasComplex) {
                  tmpl.requestTemplate.argsToJsonBody = true;
                  shouldKeepArgsPosition = true;
                  if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
                    headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
                  }
                } else {
                  const jsonPairs = bodyArgs.map(name => {
                    const a = byName[name];
                    const t = a && (a.type || (a.schema && a.schema.type));
                    const isString = t === 'string';
                    const valueTpl = isString ? `"{{.args.${name}}}"` : `{{.args.${name}}}`;
                    return `  \"${name}\": ${valueTpl}`;
                  });
                  body = `{$\n${jsonPairs.join(',\n')}\n}`.replace('{$\n', '{\n');
                  if (!getContentType(headers) && !hasHeaderKey(headers, 'Content-Type')) {
                    headers.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
                  }
                }
              }
            }
          }

          // 写回模板，并移除 argsPosition 字段
          tmpl.requestTemplate.url = url;
          if (headers.length > 0) {
            tmpl.requestTemplate.headers = headers;
          }
          if (body !== undefined) {
            tmpl.requestTemplate.body = body;
            // 当生成了明确的 body 时，移除 flags（避免冲突）
            delete tmpl.requestTemplate.argsToJsonBody;
            delete tmpl.requestTemplate.argsToUrlParam;
            delete tmpl.requestTemplate.argsToFormBody;
          } else {
            // 未生成明确 body，但存在 bodyArgs 且 Content-Type 为表单时，设置表单标记
            const ct2 = getContentType(headers);
            if (!allInBody) {
              if (bodyArgs.length > 0 && ct2.includes('application/x-www-form-urlencoded')) {
                tmpl.requestTemplate.argsToFormBody = true;
                shouldKeepArgsPosition = true;
              }
            }
          }
          // 仅在不依赖 flags 的情况下删除 argsPosition；
          // 若全部在 query/body 已由 flags 控制，也可删除
          if (!shouldKeepArgsPosition || allInQuery || allInBody) {
            delete tmpl.argsPosition;
          }
        });
      } catch (e) {
        // 转换失败不影响导入流程，仅记录日志
        // eslint-disable-next-line no-console
        console.warn('argsPosition to requestTemplate transform failed:', e);
      }

      const toolSpecification = JSON.stringify({
        tools,
        toolsMeta,
        securitySchemes,
      });
      if (props?.onChange) {
        props.onChange(JSON.parse(toolSpecification));
      }
      Message.success(locale.importSuccess);
      setOpenApiDialogVisible(false);
    } catch (error) {
      Message.error(locale.fileInvalidFormat + ': ' + error.message);
      console.error('导入失败:', error);
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

  // 解析 $ref 引用的辅助函数
  const resolveRefs = (obj, root, visited = new Set()) => {
    if (!obj || typeof obj !== 'object') {
      return obj;
    }

    // 处理数组
    if (Array.isArray(obj)) {
      return obj.map(item => resolveRefs(item, root, visited));
    }

    // 处理 $ref 引用
    if (obj.$ref && typeof obj.$ref === 'string') {
      // 检查循环引用
      if (visited.has(obj.$ref)) {
        console.warn('检测到循环引用:', obj.$ref);
        return { error: 'Circular reference detected' };
      }

      // 解析引用路径
      const refPath = obj.$ref;

      // 处理内部引用 (#/components/schemas/xxx)
      if (refPath.startsWith('#/')) {
        const pathParts = refPath.substring(2).split('/');
        let refObj = root;

        for (const part of pathParts) {
          if (refObj && typeof refObj === 'object' && refObj[part] !== undefined) {
            refObj = refObj[part];
          } else {
            console.warn('无法解析引用路径:', refPath);
            return obj; // 返回原始引用，避免破坏数据
          }
        }

        // 递归解析引用的对象，添加到访问记录中
        visited.add(refPath);
        const resolved = resolveRefs(refObj, root, new Set(visited));
        visited.delete(refPath);
        return resolved;
      }

      // 其他类型的引用暂时返回原始对象
      console.warn('不支持的引用类型:', refPath);
      return obj;
    }

    // 递归处理对象的所有属性
    const result = {};
    for (const [key, value] of Object.entries(obj)) {
      result[key] = resolveRefs(value, root, visited);
    }
    return result;
  };

  // 校验格式并解析 OpenAPI
  const parseOpenAPI = async content => {
    try {
      // 自动识别 JSON/YAML 格式
      let parsedContent;
      try {
        parsedContent = JSON.parse(content);
      } catch (jsonError) {
        // 尝试 YAML 解析
        try {
          parsedContent = YAML.load(content);
        } catch (yamlError) {
          throw new Error('Invalid JSON/YAML format');
        }
      }
      parsedContent = resolveRefs(parsedContent, parsedContent);
      if (parsedContent.swagger) {
        const converted = await swagger2openapi.convertObj(parsedContent, {});
        return converted.openapi;
      }

      // 验证 OpenAPI 3.x 文档
      if (parsedContent.openapi) {
        // 可以添加更多验证逻辑
        return parsedContent;
      }
    } catch (e) {
      console.error('解析失败:', e);
      throw new Error(locale.fileInvalidFormat);
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
              {serverConfig.toolSpec.tools.map((tool, index) => {
                // 获取工具的在线状态
                const toolsMeta = serverConfig?.toolSpec?.toolsMeta?.[tool.name];
                const isOnline = toolsMeta ? toolsMeta.enabled : true;

                return (
                  <div
                    key={index}
                    className={`tool-item ${activeToolIndex === index ? 'active' : ''}`}
                    onClick={() => setActiveToolIndex(index)}
                  >
                    <div className="tool-item-title">{tool.name}</div>
                    <div className="tool-item-status-bar">
                      <span className={`tool-status-badge ${isOnline ? 'enabled' : 'disabled'}`}>
                        {isOnline ? '启用' : '禁用'}
                      </span>
                      {tool.inputSchema?.properties && (
                        <span className="tool-param-count">
                          {Object.keys(tool.inputSchema.properties).length} 个参数
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
                                tool.inputSchema.required
                              )}
                              showLine
                              isLabelBlock
                              className="parameters-tree"
                              labelRender={node => {
                                // 从参数映射表中获取节点数据
                                const nodeData = parameterMap.current?.get(node.key);

                                // 如果是子节点（详情信息）
                                // if (node.isLeaf) {
                                //   return (
                                //     <span style={{
                                //       fontFamily: 'Monaco, Consolas, "Courier New", monospace',
                                //       color: '#000',
                                //       fontSize: '13px'
                                //     }}>
                                //       {node.label}
                                //     </span>
                                //   );
                                // }

                                // 检查是否是组织节点（属性、数组项定义等）
                                if (nodeData?.isGroupNode) {
                                  return <span className="tree-group-label">{node.label}</span>;
                                }

                                // 检查是否是参数节点（通过映射表中的 isParameterNode 标识）
                                if (nodeData?.isParameterNode || node.isLeaf) {
                                  return (
                                    <div className="param-row">
                                      <span className="param-name">{nodeData.name}</span>
                                      <span className="type-badge">
                                        [{nodeData.type || 'string'}]
                                      </span>
                                      {nodeData.isRequired && (
                                        <span className="required-badge">*必填</span>
                                      )}
                                      {nodeData.hasDefault && (
                                        <span className="default-tag">[默认值]</span>
                                      )}
                                      <span
                                        className="param-desc"
                                        title={nodeData.description || '-'}
                                      >
                                        - {truncateText(nodeData.description || '-', 64)}
                                      </span>
                                      {nodeData.hasDefault &&
                                        nodeData.defaultValue !== undefined && (
                                          <span className="default-value">
                                            ({JSON.stringify(nodeData.defaultValue)})
                                          </span>
                                        )}
                                      {nodeData.enum && (
                                        <span className="enum-values">
                                          [
                                          {Array.isArray(nodeData.enum)
                                            ? nodeData.enum.join(', ')
                                            : nodeData.enum}
                                          ]
                                        </span>
                                      )}
                                    </div>
                                  );
                                }

                                // 回退处理：如果节点数据存在但不是参数节点，可能是旧数据格式
                                if (
                                  nodeData &&
                                  !nodeData.isParameterNode &&
                                  !nodeData.isGroupNode &&
                                  !nodeData.isInfoNode
                                ) {
                                  return (
                                    <div className="param-row">
                                      <span className="param-name">
                                        {nodeData.name || node.label}
                                      </span>
                                      <span className="type-badge">
                                        [{nodeData.type || 'string'}]
                                      </span>
                                    </div>
                                  );
                                }

                                // 信息节点（如 描述/默认值/可选值/格式）
                                if (nodeData?.isInfoNode) {
                                  const isDesc = nodeData.name === '描述';
                                  const displayText = isDesc
                                    ? `${nodeData.name}: ${truncateText(nodeData.description, 64)}`
                                    : `${nodeData.name}: ${nodeData.description}`;
                                  return (
                                    <span
                                      className="info-label"
                                      title={`${nodeData.name}: ${nodeData.description}`}
                                    >
                                      {displayText}
                                    </span>
                                  );
                                }

                                // 默认渲染（其他类型的节点）
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
                                        <span className="kv-label">启用状态: </span>
                                        <span
                                          className={`kv-value ${
                                            templateData.security.passthrough ? 'green' : ''
                                          }`}
                                        >
                                          {templateData.security.passthrough ? '已启用' : '未启用'}
                                        </span>
                                      </div>
                                      {templateData.security.id && (
                                        <div className="kv-row">
                                          <span className="kv-label">客户端认证方式: </span>
                                          <span className="kv-value blue">
                                            {templateData.security.id}
                                          </span>
                                        </div>
                                      )}
                                      {templateData.security.type && (
                                        <div className="kv-row">
                                          <span className="kv-label">认证类型: </span>
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
                                          <span className="kv-label">HTTP 方法: </span>
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
                                          <span className="kv-label">请求路径: </span>
                                          <span className="url-chip">
                                            {templateData.requestTemplate.url}
                                          </span>
                                        </div>
                                      )}
                                      {templateData.requestTemplate.security && (
                                        <div className="kv-row">
                                          <span className="kv-label">后端认证方式: </span>
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
                                              <div className="other-config-title">其他配置:</div>
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
                                          <div className="empty-tip">暂无响应模板配置</div>
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
              beforeUpload={() => false} // 禁止自动上传
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

              let url = `/v3/console/ai/mcp/importToolsFromMcp?transportType=${frontProtocol}&baseUrl=${mcpBaseUrl}&endpoint=${exportPath}`;
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
