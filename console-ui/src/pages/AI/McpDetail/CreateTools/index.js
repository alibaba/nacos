import React, { Children, useEffect, useImperativeHandle, useRef, useState } from 'react';
import {
  Dialog,
  Field,
  Form,
  Input,
  Grid,
  Table,
  Button,
  Message,
  Select,
  Tree,
  Switch,
  Tag,
} from '@alifd/next';
import { formitemLayout, GetTitle, tableOperation } from './components';
import { request } from '../../../../globalLib';
import { object } from 'prop-types';
import MonacoEditor from '../../../../components/MonacoEditor';

const { Row, Col } = Grid;

const CreateTools = React.forwardRef((props, ref) => {
  // eslint-disable-next-line react/prop-types
  const { locale, showTemplates = false, onlyEditRuntimeInfo = false } = props;
  const field = Field.useField({
    parseName: true,
    values: {
      toolParams: [],
      invokeContext: [],
      templates: '',
      transparentAuth: false,
      securitySchemeId: '',
      clientSecuritySchemeId: '',
    },
  });
  const { init } = field;
  const [visible, setVisible] = useState(false);
  const [invokeIdx, setInvokeIdx] = useState(0);
  const [templateIdx, setTemplateIdx] = useState(0);
  const [type, setType] = useState('');
  const [okLoading, setOkLoading] = useState(false);
  const [rawData, setRawData] = useState([]);
  const [data, setData] = useState([]);
  const [args, setArgs] = useState({});
  const [originalTemplate, setOriginalTemplate] = useState(''); // 存储原始模板
  const [expandedKeys, setExpandedKeys] = useState([]); // 控制树节点展开状态，默认折叠
  const [currentNode, setCurrentNode] = useState({
    description: '',
    type: 'object',
    // eslint-disable-next-line react/prop-types
    label: locale.ArgumentsList,
    key: 'args',
    children: [],
  });
  const [editorKey, setEditorKey] = useState(0); // 编辑器重新渲染的key
  const [showTemplateHelp, setShowTemplateHelp] = useState(false); // 控制模板帮助信息展开状态
  // useEffect(() => {
  //   if (visible) {
  //     if (field.getValue('invokeContext') && !field.getValue('invokeContext')?.length) {
  //       addNewToolMetadata();
  //     } else {
  //       field.setValues({ invokeContext: [] });
  //     }
  //     if (field.getValue('templates') && !field.getValue('templates')?.length) {
  //       // addNewTemplates();
  //     } else {
  //       field.setValues({ templates: 'aaa' });
  //     }
  //   }
  // }, [visible]);

  const convertPropertiesToTreeData = (properties, prefix, requiredList = []) => {
    if (properties == null) {
      return [];
    }
    const keys = Object.keys(properties);
    let result = [];
    for (let index = 0; index < keys.length; index++) {
      const element = keys[index];
      const arg = properties[element];
      let children = [];
      if (arg.type === 'object') {
        // 嵌套对象暂不处理其 required 列表（当前仅支持根级 required）
        children = convertPropertiesToTreeData(arg.properties, `${prefix}@@${element}`);
      } else if (arg.type === 'array') {
        children = convertPropertiesToTreeData(
          {
            items: arg.items,
          },
          `${prefix}@@${element}`
        );
      }
      const node = {
        label: element,
        type: arg.type,
        arg: arg,
        description: arg.description ? arg.description : '',
        defaultValue: arg.default || '',
        // 仅根级 required 使用 inputSchema.required 进行标记
        required: Array.isArray(requiredList) ? requiredList.includes(element) : false,
        children,
        key: `${prefix}@@${element}`,
      };
      result.push(node);
      args[`${prefix}@@${element}`] = node;
    }
    return result;
  };

  // 收集树中所有节点的key用于展开
  const collectAllKeys = nodes => {
    const keys = [];
    const traverse = nodeList => {
      nodeList.forEach(node => {
        keys.push(node.key);
        if (node.children && node.children.length > 0) {
          traverse(node.children);
        }
      });
    };
    traverse(nodes);
    return keys;
  };

  const openVisible = ({ record, type, toolsMeta }) => {
    const { name, description, inputSchema } = record;
    setType(type);

    const _toolParams = inputSchema?.properties
      ? convertPropertiesToTreeData(
          inputSchema?.properties,
          'args',
          Array.isArray(inputSchema?.required) ? inputSchema?.required : []
        )
      : [];

    let rootNode = {
      type: 'object',
      label: locale.ArgumentsList,
      key: 'args',
      description: '',
      children: [],
    };

    args.args = rootNode;
    rootNode.children = _toolParams;
    if (rootNode.children.length === 0) {
      const defaultNewArg = {
        type: 'string',
        label: 'NewArg1',
        key: 'args@@NewArg1',
        description: '',
        defaultValue: '',
        required: false,
        children: [],
        arg: {
          type: 'string',
          description: '',
        },
      };
      rootNode.children = [defaultNewArg];
      args['args@@NewArg1'] = defaultNewArg;
    }

    rawData.push(rootNode);
    setRawData(rawData);
    setData(JSON.parse(JSON.stringify(rawData)));
    setArgs(args);

    // 保存默认参数到表单字段
    const defaultParams = rawDataToFiledValue(rootNode.children);

    // 默认不展开任何节点
    setExpandedKeys([]);

    const _invokeContext = toolsMeta?.invokeContext
      ? Object.keys(toolsMeta?.invokeContext).map(key => ({
          key,
          value: toolsMeta?.invokeContext[key],
        }))
      : [];
    setInvokeIdx(_invokeContext.length + 1);

    let templatesStr = '';
    let extractedSecuritySchemeId = '';
    let extractedClientSecuritySchemeId = '';
    let extractedTransparentAuth = false;

    if (toolsMeta?.templates !== undefined && 'json-go-template' in toolsMeta?.templates) {
      templatesStr = JSON.stringify(toolsMeta?.templates['json-go-template'], null, 2);

      // 从模板中提取安全方案ID和透明认证状态
      try {
        const templateObj = toolsMeta?.templates['json-go-template'];

        // 提取后端认证方式 (从 requestTemplate.security 中)
        if (templateObj?.requestTemplate?.security) {
          extractedSecuritySchemeId = templateObj?.requestTemplate?.security.id;
        }

        // 提取客户端认证方式和透明认证状态 (从根级别 security 中)
        if (templateObj?.security) {
          if (templateObj.security.id) {
            extractedClientSecuritySchemeId = templateObj.security.id;
          }
          // 检查 passthrough 字段来确定透明认证状态
          if (templateObj.security.passthrough === true) {
            extractedTransparentAuth = true;
          }
        }
      } catch (error) {
        console.warn('Failed to parse template for security schemes:', error);
      }
    }

    // 设置原始模板
    setOriginalTemplate(templatesStr);

    // 重新渲染编辑器
    setEditorKey(prev => prev + 1);

    field.setValues({
      name,
      description,
      toolParams: inputSchema?.properties ? inputSchema?.properties : defaultParams,
      required: inputSchema?.required,
      invokeContext: _invokeContext,
      templates: templatesStr,
      enabled: toolsMeta?.enabled,
      transparentAuth: extractedTransparentAuth || toolsMeta?.transparentAuth || false,
      securitySchemeId: extractedSecuritySchemeId || toolsMeta?.securitySchemeId || '',
      clientSecuritySchemeId:
        extractedClientSecuritySchemeId || toolsMeta?.clientSecuritySchemeId || '',
    });
    setOkLoading(false);
    setVisible(true);
  };

  useImperativeHandle(ref, () => ({
    openVisible,
  }));

  const openDialog = () => {
    openVisible({
      record: {
        name: '',
        description: '',
      },
      type: '',
      toolsMeta: {
        enabled: true,
        transparentAuth: false,
        securitySchemeId: '',
        clientSecuritySchemeId: '',
      },
    });
  };

  const closeDialog = () => {
    setVisible(false);
    setType('');
    setOriginalTemplate('');
    setEditorKey(0); // 重置编辑器key
    setShowTemplateHelp(false); // 重置帮助信息状态

    setExpandedKeys([]); // 重置展开状态为折叠
    setCurrentNode({
      description: '',
      type: 'object',
      label: '',
      key: '',
      children: [],
    });
    setData([]);
    setRawData([]);
    setArgs([]);
  };

  const createItems = () => {
    field.validate((error, values) => {
      const records = props?.serverConfig;
      if (error) {
        return;
      }

      const invokeContext = {};
      if (values?.invokeContext?.length) {
        // eslint-disable-next-line no-unused-expressions
        values?.invokeContext?.forEach(item => (invokeContext[item.key] = item.value));
      }

      const templates = {};

      console.log('records', records);
      if (
        (records.protocol === 'http' || records.protocol === 'https') &&
        values?.templates?.length > 0
      ) {
        try {
          // 使用生成的模板（已经注入了安全配置）
          let templateContent = generateTemplateWithSecurity();
          let parsedTemplate = parseTemplateContent(templateContent);

          if (parsedTemplate && Object.keys(parsedTemplate).length > 0) {
            templates['json-go-template'] = parsedTemplate;
          }
        } catch (error) {
          console.error('Error parsing template:', error);
          // 如果解析失败，跳过模板注入
          Message.error(locale.templateParseError || '模板格式错误，请检查 JSON 格式');
          return;
        }
      }

      const serverSpecification = JSON.stringify({
        protocol: records?.protocol,
        name: records?.name,
        description: records?.description,
        version: records?.version,
        enbled: true,
        remoteServerConfig: {
          exportPath: records?.remoteServerConfig?.exportPath,
        },
      });

      // 根据 item.name  去除 重复的 name 值
      let _tool = JSON.parse(JSON.stringify(records?.toolSpec?.tools || []));
      let _toolsMeta = JSON.parse(JSON.stringify(records?.toolSpec?.toolsMeta || {}));
      const properties = values?.toolParams;
      const _toolitem = {
        name: values?.name,
        description: values?.description,
        inputSchema: {
          type: 'object',
          properties,
          required: values?.required,
        },
      };
      const _toolsMetaitem = {
        [values?.name]: {
          enabled: values?.enabled,
          invokeContext,
          templates,
          transparentAuth: values?.transparentAuth || false,
          securitySchemeId: values?.securitySchemeId || '',
          clientSecuritySchemeId: values?.clientSecuritySchemeId || '',
        },
      };

      if (type == 'edit') {
        _tool
          .map(i => i.name)
          .forEach((name, index) => {
            if (values?.name === name) {
              _tool[index] = _toolitem;
              _toolsMeta[values?.name] = _toolsMetaitem[values?.name];
            }
          });
      } else {
        _tool.push(_toolitem);
        _toolsMeta = {
          ..._toolsMeta,
          ..._toolsMetaitem,
        };
      }
      const toolSpecification = JSON.stringify({
        tools: _tool,
        toolsMeta: _toolsMeta,
      });

      const endpointSpecification = JSON.stringify({
        type: 'REF',
        data: {
          namespaceId: records?.remoteServerConfig?.serviceRef?.namespaceId,
          serviceName: records?.remoteServerConfig?.serviceRef?.serviceName,
          groupName: records?.remoteServerConfig?.serviceRef?.groupName,
        },
      });

      const params = {
        mcpName: records?.name,
        serverSpecification,
        toolSpecification,
      };

      if (records?.protocol !== 'stdio') {
        params.endpointSpecification = endpointSpecification;
      }

      console.log('params', params);

      if (props?.onChange) {
        // eslint-disable-next-line no-unused-expressions
        props?.onChange(JSON.parse(toolSpecification));
        closeDialog();
      }
    });
  };

  const putMcp = async params => {
    setOkLoading(true);
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'put',
      data: params,
      error: err => setOkLoading(false),
    });
    setOkLoading(false);

    if (result?.code === 0 && result?.data === 'ok') {
      if (type == 'edit') {
        Message.success(locale.editToolSuccess);
      } else {
        Message.success(locale.createToolSuccess);
      }
      await new Promise(resolve => setTimeout(resolve, 300));
      closeDialog();
      // eslint-disable-next-line no-unused-expressions
      props?.getServerDetail();
    } else if (type == 'edit') {
      Message.error(result?.message || locale.editToolFailed);
    } else {
      Message.error(result?.message || locale.createToolFailed);
    }
  };

  // 添加Tool 元数据
  const addNewToolMetadata = () => {
    setInvokeIdx(invokeIdx + 1);
    field.addArrayValue('invokeContext', invokeIdx, {
      id: invokeIdx + 1,
      key: '',
      value: '',
    });
  };
  // 删除Tool 元数据
  const deleteToolMetadata = index => {
    field.deleteArrayValue('invokeContext', index);
  };

  const validateTemplateFormat = (rule, value, callback) => {
    if (!value || value.trim().length === 0) {
      callback();
      return;
    }

    try {
      // 只验证 JSON 格式
      const parsed = JSON.parse(value);

      // 验证必填字段
      if (parsed.requestTemplate) {
        if (!parsed.requestTemplate.url) {
          callback('requestTemplate.url 是必填字段');
          return;
        }
        if (!parsed.requestTemplate.method) {
          callback('requestTemplate.method 是必填字段');
          return;
        }

        // 验证互斥字段
        const mutexFields = ['body', 'argsToJsonBody', 'argsToUrlParam', 'argsToFormBody'];
        const activeMutexFields = mutexFields.filter(field => parsed.requestTemplate[field]);
        if (activeMutexFields.length > 1) {
          callback(`requestTemplate 中 ${activeMutexFields.join(', ')} 字段互斥，只能选择一个`);
          return;
        }
      }

      // 验证 argsPosition 的有效值
      if (parsed.argsPosition) {
        if (typeof parsed.argsPosition !== 'object' || Array.isArray(parsed.argsPosition)) {
          callback('argsPosition 必须是一个对象');
          return;
        }

        const validPositions = ['query', 'path', 'header', 'cookie', 'body'];
        const invalidPositions = Object.values(parsed.argsPosition).filter(
          position => !validPositions.includes(position)
        );

        if (invalidPositions.length > 0) {
          callback(
            `argsPosition 的值必须是以下之一: ${validPositions.join(
              ', '
            )}，发现无效值: ${invalidPositions.join(', ')}`
          );
          return;
        }
      }

      // 验证 mcpServers 数组长度（仅在 Local Server 配置时）
      if (parsed.mcpServers && Array.isArray(parsed.mcpServers)) {
        if (parsed.mcpServers.length !== 1) {
          callback('mcpServers 只能包含一个元素');
          return;
        }
      }

      if (parsed.responseTemplate) {
        // 验证响应模板互斥字段
        const responseFields = ['body', 'prependBody', 'appendBody'];
        const hasBody = !!parsed.responseTemplate.body;
        const hasPrependOrAppend = !!(
          parsed.responseTemplate.prependBody || parsed.responseTemplate.appendBody
        );

        if (hasBody && hasPrependOrAppend) {
          callback('responseTemplate 中 body 与 prependBody/appendBody 互斥');
          return;
        }
      }

      callback();
    } catch (jsonError) {
      // JSON 解析失败，返回错误信息
      callback(locale.templateShouldBeJson || '模板格式错误，请输入有效的 JSON 格式');
    }
  };
  // 检测内容格式并返回对应的语言模式 - 只支持JSON
  const detectLanguageMode = content => {
    // 始终返回 json 模式
    return 'json';
  };

  // 解析模板内容为对象（只支持JSON）
  const parseTemplateContent = content => {
    if (!content || content.trim().length === 0) {
      return null;
    }

    try {
      // 只解析 JSON 格式
      return JSON.parse(content);
    } catch (jsonError) {
      throw new Error('Invalid template format. Please provide valid JSON.');
    }
  };

  // 生成默认的JSON配置模板
  const generateDefaultTemplate = () => {
    const defaultTemplate = {
      requestTemplate: {
        url: 'https://api.example.com/endpoint',
        method: 'GET',
        headers: [
          {
            key: 'Authorization',
            value: 'Bearer your key',
          },
        ],
        // 默认使用URL参数模式
        argsToUrlParam: true,
      },
      responseTemplate: {
        // 完整响应体转换模板
        body: '{{.}}',
      },
    };

    const templateStr = JSON.stringify(defaultTemplate, null, 2);
    setOriginalTemplate(templateStr);
    field.setValue('templates', templateStr);
    setEditorKey(prev => prev + 1); // 重新渲染编辑器
  };

  // 生成多种配置模板的选择
  const generateTemplateByType = type => {
    let template = {};

    // 获取当前参数列表
    const getCurrentParams = () => {
      const toolParams = field.getValue('toolParams');
      if (toolParams && typeof toolParams === 'object') {
        return Object.keys(toolParams);
      }
      // 如果没有参数，返回默认示例参数
      return ['id', 'name'];
    };

    switch (type) {
      case 'json-body':
        template = {
          requestTemplate: {
            url: 'https://api.example.com/endpoint',
            method: 'POST',
            headers: [
              { key: 'Content-Type', value: 'application/json' },
              { key: 'Authorization', value: 'Bearer your key' },
            ],
            argsToJsonBody: true,
          },
          responseTemplate: {
            body: '{{.}}',
          },
        };
        break;

      case 'url-params':
        template = {
          requestTemplate: {
            url: 'https://api.example.com/endpoint',
            method: 'GET',
            headers: [{ key: 'Authorization', value: 'Bearer your key' }],
            argsToUrlParam: true,
          },
          responseTemplate: {
            body: '{{.}}',
          },
        };
        break;

      case 'form-body':
        template = {
          requestTemplate: {
            url: 'https://api.example.com/endpoint',
            method: 'POST',
            headers: [
              { key: 'Content-Type', value: 'application/x-www-form-urlencoded' },
              { key: 'Authorization', value: 'Bearer your key' },
            ],
            argsToFormBody: true,
          },
          responseTemplate: {
            body: '{{.}}',
          },
        };
        break;

      case 'custom-body':
        template = {
          requestTemplate: {
            url: 'https://api.example.com/endpoint',
            method: 'POST',
            headers: [
              { key: 'Content-Type', value: 'application/json' },
              { key: 'Authorization', value: 'Bearer your key' },
            ],
            body: '{\n  "query": "{{.args.query}}",\n  "limit": {{.args.limit}}\n}',
          },
          responseTemplate: {
            body: '{{.}}',
          },
        };
        break;

      case 'args-path':
        const currentParams = getCurrentParams();
        const argsPosition = {};

        // 根据当前参数生成位置映射
        currentParams.forEach((paramName, index) => {
          if (index === 0) {
            // 第一个参数作为路径参数
            argsPosition[paramName] = 'path';
          } else {
            // 其他参数作为查询参数
            argsPosition[paramName] = 'query';
          }
        });

        // 构建URL，如果有路径参数，添加到URL中
        let url = 'https://api.example.com/endpoint';
        const pathParams = Object.entries(argsPosition).filter(
          ([, position]) => position === 'path'
        );

        template = {
          requestTemplate: {
            url: url,
            method: 'GET',
            headers: [{ key: 'Authorization', value: 'Bearer your key' }],
          },
          responseTemplate: {
            body: '{{.}}',
          },
          argsPosition: argsPosition,
        };
        break;

      default:
        return generateDefaultTemplate();
    }

    const templateStr = JSON.stringify(template, null, 2);
    setOriginalTemplate(templateStr);
    field.setValue('templates', templateStr);
    setEditorKey(prev => prev + 1);
  };

  // 处理编辑器内容变化 - 只支持JSON
  const handleEditorChange = value => {
    // 更新原始模板和表单值
    setOriginalTemplate(value);
    field.setValue('templates', value);

    // 编辑器始终保持JSON模式，无需动态切换
  };

  // 生成注入了安全配置的模板
  const generateTemplateWithSecurity = () => {
    try {
      if (!originalTemplate || !field.getValue('transparentAuth')) {
        return originalTemplate;
      }

      // 使用新的解析函数支持YAML和JSON
      let templateObject = parseTemplateContent(originalTemplate);
      let modified = false;

      // 注入后端认证方式
      const securitySchemeId = field.getValue('securitySchemeId');
      if (securitySchemeId) {
        const selectedScheme = props?.serverConfig?.toolSpec?.securitySchemes?.find(
          scheme => scheme.id === securitySchemeId
        );

        if (selectedScheme) {
          if (!templateObject.requestTemplate) {
            templateObject.requestTemplate = {};
          }
          templateObject.requestTemplate.security = {
            id: selectedScheme.id,
          };
          modified = true;
        }
      }

      // 注入客户端认证方式
      const clientSecuritySchemeId = field.getValue('clientSecuritySchemeId');
      if (clientSecuritySchemeId) {
        const clientSelectedScheme = props?.serverConfig?.toolSpec?.securitySchemes?.find(
          scheme => scheme.id === clientSecuritySchemeId
        );

        if (clientSelectedScheme) {
          templateObject.security = {
            id: clientSelectedScheme.id,
            passthrough: true,
          };
          modified = true;
        }
      }

      // 如果模板被修改了，返回JSON格式（统一输出格式）
      return modified ? JSON.stringify(templateObject, null, 2) : originalTemplate;
    } catch (error) {
      return originalTemplate;
    }
  };

  // 渲染表格
  const renderTableCell = params => {
    const {
      component = 'input',
      key = '',
      rulesMessage = locale.placeInput,
      minWidth = 200,
    } = params;

    const rules = [{ required: true, message: rulesMessage }];
    if (component === 'textArea') {
      if (key.startsWith('templates')) {
        rules.push({
          validator: validateTemplateFormat,
        });
      }

      return (
        <Form.Item style={{ margin: 0 }}>
          <Input.TextArea
            aria-label="auto height"
            style={{ minHeight: 32 }}
            autoHeight={{ minRows: 1, maxRows: 8 }}
            {...field.init(key, { rules })}
          />
        </Form.Item>
      );
    }

    if (component === 'select') {
      return (
        <Form.Item style={{ margin: 0 }}>
          <Select
            style={{ width: '100%', maxWidth: minWidth }}
            dataSource={[
              { label: '字符串类型 string', value: 'string' },
              { label: '数字类型 number', value: 'number' },
              { label: '整数类型 integer', value: 'integer' },
              { label: '布尔类型 boolean', value: 'boolean' },
              { label: '数组类型 array', value: 'array' },
              // { label:'对象类型，使用 properties 字段定义对象属性的模式', value:'object' },
            ]}
            {...field.init(key, { initValue: 'string', rules })}
          />
        </Form.Item>
      );
    }

    return (
      <Form.Item style={{ margin: 0, minWidth }}>
        <Input {...field.init(key, { rules })} />
      </Form.Item>
    );
  };

  const rawDataToFiledValue = rawData => {
    const result = {};
    if (!rawData) {
      return result;
    }
    for (let index = 0; index < rawData.length; index++) {
      const element = rawData[index];
      let arg = {
        ...element.arg,
        type: element.type,
      };
      // items 节点不需要描述
      if (!String(element.key || '').endsWith('@@items')) {
        arg.description = element.description;
      } else if (arg && arg.description !== undefined) {
        delete arg.description;
      }
      arg.type = element.type;
      if (
        element.defaultValue !== undefined &&
        element.defaultValue !== '' &&
        !String(element.key || '').endsWith('@@items')
      ) {
        // 根据类型设置默认值
        if (element.type === 'boolean') {
          arg.default = element.defaultValue === 'true';
        } else if (element.type === 'number') {
          const num = parseFloat(element.defaultValue);
          if (!isNaN(num)) {
            arg.default = num;
          }
        } else if (element.type === 'integer') {
          const int = parseInt(element.defaultValue, 10);
          if (!isNaN(int)) {
            arg.default = int;
          }
        } else {
          arg.default = element.defaultValue;
        }
      }
      if (element.type === 'object' && element.children.length > 0) {
        arg.properties = rawDataToFiledValue(element.children);
      } else if (element.type === 'array') {
        arg.items = rawDataToFiledValue(element.children).items;
      }
      result[element.label] = arg;
    }
    return result;
  };

  const AddPropertiesToArgs = () => {
    // 总是在根级 args 下添加新参数
    const parentNode = args['args'];
    if (!parentNode.children) {
      parentNode.children = [];
    }
    const childLen = parentNode.children.length + 1;
    const newArgsName = `newArg${childLen}`;
    const newNodeKey = `args@@${newArgsName}`;
    const newNode = {
      label: newArgsName,
      key: newNodeKey,
      type: 'string',
      description: '',
      defaultValue: '',
      required: false,
      children: [],
      arg: {
        type: 'string',
        description: '',
      },
    };

    args[newNodeKey] = newNode;
    if (!parentNode.children) {
      parentNode.children = [];
    }
    parentNode.children.push(newNode);

    // 确保新节点的父节点展开
    const updatedExpandedKeys = [...expandedKeys];
    if (!updatedExpandedKeys.includes('args')) {
      updatedExpandedKeys.push('args');
    }
    setExpandedKeys(updatedExpandedKeys);

    setRawData(rawData);
    setArgs(args);
    setData(JSON.parse(JSON.stringify(rawData)));
    saveParamToFiled();
  };

  // 为指定的节点添加属性
  const AddPropertiesToCurrentNode = () => {
    if (!currentNode || !currentNode.key || currentNode.type !== 'object') {
      return;
    }

    const parentNode = args[currentNode.key];
    if (!parentNode.children) {
      parentNode.children = [];
    }
    const childLen = parentNode.children.length + 1;
    const newPropertyName = `newProperty${childLen}`;
    const newNodeKey = `${currentNode.key}@@${newPropertyName}`;
    const newNode = {
      label: newPropertyName,
      key: newNodeKey,
      type: 'string',
      description: '',
      defaultValue: '',
      required: false,
      children: [],
      arg: {
        type: 'string',
        description: '',
      },
    };

    args[newNodeKey] = newNode;
    if (!parentNode.children) {
      parentNode.children = [];
    }
    parentNode.children.push(newNode);

    // 确保当前节点展开，以显示新添加的属性
    const updatedExpandedKeys = [...expandedKeys];
    if (!updatedExpandedKeys.includes(currentNode.key)) {
      updatedExpandedKeys.push(currentNode.key);
    }
    setExpandedKeys(updatedExpandedKeys);

    setRawData(rawData);
    setArgs(args);
    setData(JSON.parse(JSON.stringify(rawData)));
    saveParamToFiled();
  };

  // 删除参数或属性
  const deleteNode = nodeKey => {
    if (nodeKey === 'args') {
      // 不允许删除根节点
      return;
    }

    // 显示确认对话框
    Dialog.confirm({
      title: locale.confirmDelete || '确认删除',
      content: locale.confirmDeleteMessage || '确定要删除此项及其所有子项吗？',
      onOk: () => {
        // 执行删除操作
        const performDelete = targetKey => {
          // 递归删除所有子节点
          const deleteChildrenRecursively = key => {
            Object.keys(args).forEach(argKey => {
              if (argKey.startsWith(key + '@@')) {
                delete args[argKey];
              }
            });
          };
          deleteChildrenRecursively(targetKey);

          // 从args对象中删除该节点
          delete args[targetKey];

          // 找到父节点并从其children数组中删除该节点
          const keyParts = targetKey.split('@@');
          const parentKey = keyParts.slice(0, -1).join('@@');

          if (parentKey && args[parentKey]) {
            const parentNode = args[parentKey];
            if (parentNode.children) {
              parentNode.children = parentNode.children.filter(child => child.key !== targetKey);

              // 检查父节点是否为object类型且删除后没有子节点了
              if (
                parentNode.type === 'object' &&
                parentNode.children.length === 0 &&
                parentKey !== 'args'
              ) {
                // 递归删除空的object父节点
                performDelete(parentKey);
                return;
              }
            }
          }
        };

        // 开始删除操作
        performDelete(nodeKey);

        // 如果删除的节点或其父节点是当前选中的节点，重置当前节点
        if (
          currentNode.key === nodeKey ||
          currentNode.key.startsWith(nodeKey + '@@') ||
          nodeKey.startsWith(currentNode.key + '@@') ||
          !args[currentNode.key]
        ) {
          setCurrentNode({
            key: '',
            label: '',
            type: 'string',
            description: '',
          });
        }

        // 重新构建rawData
        const rebuildRawData = () => {
          const rootNode = args['args'];
          if (rootNode) {
            setRawData([rootNode]);
            setData(JSON.parse(JSON.stringify([rootNode])));
            setArgs(args);
            saveParamToFiled();
          }
        };

        rebuildRawData();
      },
    });
  };

  const changeNodeInfo = () => {
    setData(JSON.parse(JSON.stringify(rawData)));
    saveParamToFiled();
  };

  const saveParamToFiled = () => {
    if (!rawData || rawData.length === 0) {
      return;
    }
    const root = rawData[0];
    field.setValue('toolParams', rawDataToFiledValue(root.children));
    // 同步 required（仅根级参数）
    const req = Array.isArray(root.children)
      ? root.children.filter(n => n.required).map(n => n.label)
      : [];
    field.setValue('required', req);
  };

  const isPreview = type === 'preview';
  // 判断是否为数组的 items 子节点
  const isArrayItemsNode = node => {
    if (!node || !node.key) return false;
    if (!node.key.endsWith('@@items')) return false;
    const parentKey = node.key
      .split('@@')
      .slice(0, -1)
      .join('@@');
    return args[parentKey]?.type === 'array';
  };
  return (
    <div>
      {visible ? (
        <Dialog
          v2
          title={
            <div
              style={{
                fontSize: '18px',
                fontWeight: '600',
                color: '#262626',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <span
                style={{
                  display: 'inline-block',
                  width: '4px',
                  height: '18px',
                  backgroundColor: '#1890ff',
                  marginRight: '12px',
                  borderRadius: '2px',
                }}
              />
              {type === 'edit'
                ? locale.editTool || '编辑工具'
                : type === 'preview'
                ? locale.previewTool || '预览工具'
                : locale.createTool || '创建工具'}
            </div>
          }
          footer={
            isPreview ? (
              <Button
                type="primary"
                size="large"
                onClick={closeDialog}
                style={{
                  borderRadius: '6px',
                  height: '40px',
                  fontSize: '16px',
                  fontWeight: '500',
                }}
              >
                {locale.close}
              </Button>
            ) : (
              true
            )
          }
          visible
          footerActions={isPreview ? [] : ['ok', 'cancel']}
          onOk={createItems}
          okProps={{
            loading: okLoading,
            size: 'large',
            style: {
              borderRadius: '6px',
              height: '40px',
              fontSize: '16px',
              fontWeight: '500',
            },
          }}
          cancelProps={{
            size: 'large',
            style: {
              borderRadius: '6px',
              height: '40px',
              fontSize: '16px',
            },
          }}
          onClose={closeDialog}
          style={{
            width: '80%',
            maxWidth: '1200px',
            minWidth: '800px',
          }}
          bodyStyle={{
            padding: '24px',
            backgroundColor: '#fafafa',
          }}
        >
          <Form
            field={field}
            {...formitemLayout}
            style={{
              backgroundColor: '#fff',
              borderRadius: '8px',
              padding: '24px',
              marginBottom: '16px',
            }}
          >
            {/* 基础信息区域 */}
            <div
              style={{
                marginBottom: '32px',
                padding: '20px',
                backgroundColor: '#f8f9fa',
                borderRadius: '8px',
                border: '1px solid #e8e8e8',
              }}
            >
              <h3
                style={{
                  margin: '0 0 20px 0',
                  fontSize: '16px',
                  fontWeight: '600',
                  color: '#262626',
                  borderBottom: '2px solid #d9d9d9',
                  paddingBottom: '8px',
                }}
              >
                {locale.basicInfo || '基础信息'}
              </h3>

              {/* 名称 */}
              <Form.Item
                label={locale.toolName}
                required
                isPreview={!!type}
                style={{ marginBottom: '20px' }}
              >
                <Input
                  placeholder={locale.toolName}
                  size="large"
                  style={{ borderRadius: '6px' }}
                  {...init('name', {
                    rules: [
                      { required: true, message: locale.toolNameRequired },
                      {
                        validator: (rule, value, callback) => {
                          const _tools = props?.serverConfig?.toolSpec?.tools || [];
                          if (_tools?.length && !type) {
                            const names = _tools.map(item => item.name);
                            if (names.includes(value)) {
                              callback(locale.toolNameRepeat);
                            }
                          }
                          callback();
                        },
                      },
                    ],
                  })}
                />
              </Form.Item>

              {/* 描述 */}
              <Form.Item label={locale.toolDescription} required style={{ marginBottom: '20px' }}>
                <Input.TextArea
                  placeholder={locale.toolDescription}
                  size="large"
                  style={{ borderRadius: '6px', minHeight: '80px' }}
                  {...init('description', {
                    rules: [{ required: true, message: locale.toolDescriptionRequired }],
                  })}
                />
              </Form.Item>

              {/* 是否上线 */}
              <Form.Item label={locale.toolOnline} required style={{ marginBottom: '0' }}>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <Switch
                    size="large"
                    {...init('enabled', {
                      valueName: 'checked',
                      initValue: true,
                      props: isPreview
                        ? {
                            checkedChildren: locale.online,
                            unCheckedChildren: locale.offline,
                          }
                        : {},
                    })}
                  />
                  <span
                    style={{
                      marginLeft: '12px',
                      color: '#666',
                      fontSize: '14px',
                    }}
                  >
                    {field.getValue('enabled') ? locale.online || '上线' : locale.offline || '下线'}
                  </span>
                </div>
              </Form.Item>
            </div>

            {/* 参数配置区域 */}
            <div
              style={{
                marginBottom: '32px',
                padding: '20px',
                backgroundColor: '#fff',
                borderRadius: '8px',
                border: '1px solid #e8e8e8',
                boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
              }}
            >
              <h3
                style={{
                  margin: '0 0 20px 0',
                  fontSize: '16px',
                  fontWeight: '600',
                  color: '#262626',
                  borderBottom: '2px solid #d9d9d9',
                  paddingBottom: '8px',
                }}
              >
                {locale.toolInputSchema || '入参配置'}
              </h3>

              <Form.Item label={locale.ArgumentTree || '参数树'} style={{ margin: '16px 0 0' }}>
                <div
                  style={{
                    backgroundColor: '#fafafa',
                    border: '1px solid #d9d9d9',
                    borderRadius: '8px',
                    padding: '16px',
                  }}
                >
                  <Row style={{ marginBottom: '16px' }}>
                    <Col style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <Button
                        type="primary"
                        size="medium"
                        onClick={AddPropertiesToArgs}
                        disabled={isPreview || onlyEditRuntimeInfo}
                        style={{
                          borderRadius: '6px',
                          height: '36px',
                          fontWeight: '500',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                        }}
                      >
                        {locale.AddNewArg || '增加参数'}
                      </Button>

                      {/* 为 object 类型节点添加属性按钮 */}
                      {currentNode.type === 'object' && currentNode.key !== 'args' && (
                        <Button
                          type="primary"
                          size="medium"
                          onClick={AddPropertiesToCurrentNode}
                          disabled={isPreview || onlyEditRuntimeInfo}
                          style={{
                            borderRadius: '6px',
                            height: '36px',
                            fontWeight: '500',
                            backgroundColor: '#722ed1',
                            borderColor: '#722ed1',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                          }}
                        >
                          {locale.AddNewProperties || '增加属性'}
                        </Button>
                      )}

                      {(isPreview || onlyEditRuntimeInfo) && (
                        <Tag color="orange" style={{ margin: 0 }}>
                          {locale.editExistVersionMessage || '只读模式不可编辑'}
                        </Tag>
                      )}
                    </Col>
                  </Row>

                  <Row>
                    <Col style={{ width: '100%' }}>
                      <div
                        style={{
                          backgroundColor: '#fff',
                          border: '1px solid #e8e8e8',
                          borderRadius: '6px',
                          padding: '12px',
                          maxHeight: '400px',
                          overflowY: 'auto',
                        }}
                      >
                        <Tree
                          showLine
                          isLabelBlock
                          dataSource={data}
                          defaultSelectedKeys={['args']}
                          expandedKeys={expandedKeys}
                          onExpand={keys => setExpandedKeys(keys)}
                          aria-label={'test'}
                          labelRender={node => {
                            return (
                              <Row
                                style={{ fontSize: 'medium', width: '100%' }}
                                justify="space-between"
                                align="middle"
                              >
                                <Col>
                                  <Row>
                                    <Col>
                                      <a>
                                        {node.label}
                                        {node.key.split('@@').length === 2 && (
                                          <span style={{ color: '#fa541c', marginLeft: 6 }}>
                                            {args[node.key].required ? '*' : ''}
                                          </span>
                                        )}
                                      </a>
                                      &nbsp;&nbsp;({args[node.key].type})
                                    </Col>
                                    {/* items 节点不显示描述 */}
                                    {!isArrayItemsNode(args[node.key]) && (
                                      <Col style={{ textOverflow: 'ellipsis', marginLeft: 10 }}>
                                        {args[node.key].description?.length <= 25
                                          ? args[node.key].description
                                          : `${args[node.key].description?.substring(0, 20)}...`}
                                      </Col>
                                    )}
                                  </Row>
                                </Col>
                                {/* 删除按钮 - 不能删除根节点args */}
                                {node.key !== 'args' && !isPreview && !onlyEditRuntimeInfo && (
                                  <Col>
                                    <Button
                                      type="primary"
                                      warning
                                      size="small"
                                      onClick={e => {
                                        e.stopPropagation(); // 阻止事件冒泡，避免触发节点选择
                                        deleteNode(node.key);
                                      }}
                                      style={{
                                        marginLeft: 10,
                                        padding: '2px 8px',
                                        fontSize: '12px',
                                        height: '20px',
                                        lineHeight: '16px',
                                      }}
                                    >
                                      ×
                                    </Button>
                                  </Col>
                                )}
                              </Row>
                            );
                          }}
                          onSelect={data => {
                            if (data.length === 1) {
                              const currentNode = args[data];
                              setCurrentNode(currentNode);
                            } else if (data.length === 0) {
                              setCurrentNode({
                                key: '',
                                label: '',
                                type: 'string',
                                description: '',
                              });
                            }
                          }}
                        />
                      </div>
                    </Col>
                  </Row>
                </div>
              </Form.Item>
            </div>
            {currentNode.key !== '' && currentNode.key !== 'args' && (
              <div
                style={{
                  marginBottom: '32px',
                  padding: '20px',
                  backgroundColor: '#fff',
                  borderRadius: '8px',
                  border: '1px solid #e8e8e8',
                  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                }}
              >
                <h3
                  style={{
                    margin: '0 0 20px 0',
                    fontSize: '16px',
                    fontWeight: '600',
                    color: '#262626',
                    borderBottom: '2px solid #d9d9d9',
                    paddingBottom: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                  }}
                >
                  {locale.ArgumentInfo || '参数详情'}
                </h3>

                <Row gutter={24}>
                  <Col span={12}>
                    <Form.Item
                      name="args.name"
                      label={locale.toolParamName}
                      required
                      requiredTrigger="onBlur"
                      asterisk={false}
                      style={{ marginBottom: '20px' }}
                    >
                      <Input
                        size="large"
                        style={{ borderRadius: '6px' }}
                        isPreview={onlyEditRuntimeInfo}
                        disabled={currentNode.key === 'args' || isArrayItemsNode(currentNode)}
                        value={currentNode.label}
                        placeholder="请输入参数名称"
                        onChange={data => {
                          if (currentNode.key !== '' && !isArrayItemsNode(currentNode)) {
                            currentNode.label = data;
                            changeNodeInfo(currentNode);
                          }
                        }}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="args.type"
                      label={locale.toolParamType}
                      style={{ marginBottom: '20px' }}
                    >
                      <Select
                        size="large"
                        style={{ borderRadius: '6px' }}
                        isPreview={onlyEditRuntimeInfo}
                        disabled={currentNode.key === 'args'}
                        value={currentNode.type}
                        placeholder="请选择参数类型"
                        dataSource={[
                          { label: '字符串类型 (string)', value: 'string' },
                          { label: '数字类型 (number)', value: 'number' },
                          { label: '整数类型 (integer)', value: 'integer' },
                          { label: '布尔类型 (boolean)', value: 'boolean' },
                          { label: '数组类型 (array)', value: 'array' },
                          { label: '对象类型 (object)', value: 'object' },
                        ]}
                        onChange={data => {
                          if (currentNode.key !== '') {
                            if (!(data === 'array' || data === 'object')) {
                              currentNode.children = [];
                            }
                            currentNode.type = data;
                            if (currentNode.arg) {
                              currentNode.arg.type = data;
                            }
                            if (data === 'array') {
                              const itemNode = {
                                label: 'items',
                                type: 'string',
                                description: '',
                                defaultValue: '',
                                key: `${currentNode.key}@@items`,
                                arg: {
                                  type: 'string',
                                  description: '',
                                },
                              };
                              currentNode.children = [itemNode];
                              args[`${currentNode.key}@@items`] = itemNode;

                              // 确保当前节点展开，以显示新添加的items
                              const updatedExpandedKeys = [...expandedKeys];
                              if (!updatedExpandedKeys.includes(currentNode.key)) {
                                updatedExpandedKeys.push(currentNode.key);
                              }
                              setExpandedKeys(updatedExpandedKeys);

                              changeNodeInfo(currentNode);
                            } else if (data === 'object') {
                              // 为 object 类型自动创建一个默认属性
                              const defaultPropertyNode = {
                                label: 'property1',
                                type: 'string',
                                description: '',
                                defaultValue: '',
                                key: `${currentNode.key}@@property1`,
                                children: [],
                                arg: {
                                  type: 'string',
                                  description: '',
                                },
                              };
                              currentNode.children = [defaultPropertyNode];
                              args[`${currentNode.key}@@property1`] = defaultPropertyNode;

                              // 确保当前节点展开，以显示新添加的属性
                              const updatedExpandedKeys = [...expandedKeys];
                              if (!updatedExpandedKeys.includes(currentNode.key)) {
                                updatedExpandedKeys.push(currentNode.key);
                              }
                              setExpandedKeys(updatedExpandedKeys);

                              changeNodeInfo(currentNode);
                            } else {
                              changeNodeInfo(currentNode);
                            }
                          }
                        }}
                      />
                    </Form.Item>
                  </Col>
                </Row>

                {/* items 节点不需要描述，隐藏描述编辑 */}
                {!isArrayItemsNode(currentNode) && (
                  <Row>
                    <Col span={24}>
                      <Form.Item
                        label={locale.toolParamDescription}
                        name="args.description"
                        asterisk={false}
                        style={{ marginBottom: '20px' }}
                      >
                        <Input.TextArea
                          size="large"
                          style={{ borderRadius: '6px', minHeight: '80px' }}
                          disabled={currentNode.key === 'args'}
                          value={currentNode.description}
                          placeholder="请输入参数描述信息"
                          onChange={data => {
                            if (currentNode.key !== '') {
                              currentNode.description = data;
                              if (currentNode.arg) {
                                currentNode.arg.description = data;
                              }
                              changeNodeInfo(currentNode);
                            }
                          }}
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                )}

                {/* 是否必填 - 仅对根级参数（args 的直接子节点）展示 */}
                {currentNode.key && currentNode.key.split('@@').length === 2 && (
                  <Row>
                    <Col span={24}>
                      <Form.Item
                        label={locale.toolParamRequired || '是否必填'}
                        name="args.required"
                        asterisk={false}
                        style={{ marginBottom: '12px' }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center' }}>
                          <Switch
                            size="large"
                            checked={!!currentNode.required}
                            onChange={checked => {
                              if (currentNode.key) {
                                currentNode.required = !!checked;
                                changeNodeInfo(currentNode);
                              }
                            }}
                            disabled={onlyEditRuntimeInfo}
                            checkedChildren={locale.required || '必填'}
                            unCheckedChildren={locale.optional || '可选'}
                          />
                          <span style={{ marginLeft: 12, color: '#666', fontSize: 12 }}>
                            {currentNode.required
                              ? locale.required || '必填'
                              : locale.optional || '可选'}
                          </span>
                        </div>
                      </Form.Item>
                    </Col>
                  </Row>
                )}

                {/* 默认值输入 - 仅对非 object/array 且非 items 节点显示 */}
                {currentNode.type &&
                  currentNode.type !== 'object' &&
                  currentNode.type !== 'array' &&
                  currentNode.key !== 'args' &&
                  !isArrayItemsNode(currentNode) && (
                    <Row>
                      <Col span={24}>
                        <Form.Item
                          label={locale.toolParamDefaultValue || '默认值'}
                          name="args.defaultValue"
                          asterisk={false}
                          style={{ marginBottom: '0' }}
                          extra={
                            <div
                              style={{
                                color: '#666',
                                fontSize: '12px',
                                marginTop: '4px',
                              }}
                            >
                              {currentNode.type === 'boolean'
                                ? '布尔类型请输入 true 或 false'
                                : currentNode.type === 'number' || currentNode.type === 'integer'
                                ? '请输入数字'
                                : '可选：为此参数设置默认值'}
                            </div>
                          }
                        >
                          {currentNode.type === 'boolean' ? (
                            <Select
                              size="large"
                              style={{ borderRadius: '6px' }}
                              value={currentNode.defaultValue}
                              placeholder="请选择默认值"
                              allowClear
                              dataSource={[
                                { label: 'true', value: 'true' },
                                { label: 'false', value: 'false' },
                              ]}
                              onChange={data => {
                                if (currentNode.key !== '' && !isArrayItemsNode(currentNode)) {
                                  currentNode.defaultValue = data || '';
                                  if (currentNode.arg) {
                                    if (data) {
                                      currentNode.arg.default = data === 'true';
                                    } else {
                                      delete currentNode.arg.default;
                                    }
                                  }
                                  changeNodeInfo(currentNode);
                                }
                              }}
                            />
                          ) : (
                            <Input
                              size="large"
                              style={{ borderRadius: '6px' }}
                              value={currentNode.defaultValue}
                              placeholder={
                                currentNode.type === 'number' || currentNode.type === 'integer'
                                  ? '请输入数字默认值'
                                  : '请输入默认值'
                              }
                              onChange={data => {
                                if (currentNode.key !== '' && !isArrayItemsNode(currentNode)) {
                                  currentNode.defaultValue = data;
                                  if (currentNode.arg) {
                                    if (data && data.trim()) {
                                      // 根据类型转换默认值
                                      if (currentNode.type === 'number') {
                                        const num = parseFloat(data);
                                        if (!isNaN(num)) {
                                          currentNode.arg.default = num;
                                        }
                                      } else if (currentNode.type === 'integer') {
                                        const int = parseInt(data, 10);
                                        if (!isNaN(int)) {
                                          currentNode.arg.default = int;
                                        }
                                      } else {
                                        currentNode.arg.default = data;
                                      }
                                    } else {
                                      delete currentNode.arg.default;
                                    }
                                  }
                                  changeNodeInfo(currentNode);
                                }
                              }}
                            />
                          )}
                        </Form.Item>
                      </Col>
                    </Row>
                  )}
              </div>
            )}
            {showTemplates ? (
              <div
                style={{
                  marginBottom: '32px',
                  padding: '20px',
                  backgroundColor: '#fff',
                  borderRadius: '8px',
                  border: '1px solid #e8e8e8',
                  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03)',
                }}
              >
                <h3
                  style={{
                    margin: '0 0 20px 0',
                    fontSize: '16px',
                    fontWeight: '600',
                    color: '#262626',
                    borderBottom: '2px solid #d9d9d9',
                    paddingBottom: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                  }}
                >
                  {locale.advancedConfig || '高级配置'}
                </h3>

                <Form.Item
                  label={locale.invokeTemplates}
                  extra={
                    <div
                      style={{
                        color: '#666',
                        fontSize: '13px',
                        marginTop: '8px',
                        padding: '8px 12px',
                        backgroundColor: '#f6f8fa',
                        borderRadius: '4px',
                        border: '1px solid #e1e4e8',
                      }}
                    >
                      <div style={{ marginBottom: '8px' }}>
                        通过网关提供的协议转化模版进行协议转化，详情请见文档{' '}
                        <a
                          href="https://nacos.io/docs/v3.0/manual/user/mcp-template"
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{ color: '#1890ff' }}
                        >
                          https://nacos.io/docs/v3.0/manual/user/mcp-template
                        </a>
                      </div>

                      {/* 可折叠的详细配置说明 */}
                      <div style={{ marginTop: '8px' }}>
                        <div
                          onClick={() => setShowTemplateHelp(!showTemplateHelp)}
                          style={{
                            cursor: 'pointer',
                            color: '#52c41a',
                            fontSize: '12px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            userSelect: 'none',
                          }}
                        >
                          <span>{showTemplateHelp ? '▼' : '▶'}</span>
                          📋 配置选项详细说明
                        </div>

                        {showTemplateHelp && (
                          <div
                            style={{
                              fontSize: '11px',
                              color: '#666',
                              marginTop: '8px',
                              marginLeft: '16px',
                              lineHeight: '1.4',
                              padding: '8px',
                              backgroundColor: '#fafafa',
                              borderRadius: '4px',
                              border: '1px solid #e8e8e8',
                            }}
                          >
                            <div style={{ marginBottom: '4px', fontWeight: '500', color: '#333' }}>
                              请求体配置（以下选项互斥，只能选择一个）：
                            </div>
                            • <strong>argsToJsonBody</strong>: 参数作为JSON请求体
                            <br />• <strong>argsToUrlParam</strong>: 参数作为URL查询参数
                            <br />• <strong>argsToFormBody</strong>: 参数作为表单数据
                            <br />• <strong>body</strong>: 自定义请求体模板
                            <br />
                            <div
                              style={{
                                marginTop: '8px',
                                marginBottom: '4px',
                                fontWeight: '500',
                                color: '#333',
                              }}
                            >
                              参数位置配置：
                            </div>
                            • <strong>argsPosition</strong>:
                            参数位置映射对象，用于指定每个参数在请求中的具体位置
                            <br />
                            <div
                              style={{
                                marginTop: '8px',
                                marginBottom: '4px',
                                fontWeight: '500',
                                color: '#333',
                              }}
                            >
                              响应处理配置（以下选项互斥）：
                            </div>
                            • <strong>responseTemplate.body</strong>: 完整响应转换模板
                            <br />• <strong>responseTemplate.prependBody/appendBody</strong>:
                            响应前后缀文本
                            <br />
                            <div
                              style={{
                                marginTop: '8px',
                                marginBottom: '4px',
                                fontWeight: '500',
                                color: '#333',
                              }}
                            >
                              参数位置说明：
                            </div>
                            • <strong>query</strong>: 参数作为URL查询字符串
                            <br />• <strong>path</strong>: 参数作为URL路径变量
                            <br />• <strong>header</strong>: 参数作为HTTP请求头
                            <br />• <strong>cookie</strong>: 参数作为Cookie值
                            <br />• <strong>body</strong>: 参数作为请求体内容
                          </div>
                        )}
                      </div>
                    </div>
                  }
                  style={{ marginBottom: '24px' }}
                >
                  {/* 生成默认配置按钮组 */}
                  {!onlyEditRuntimeInfo && (
                    <div
                      style={{
                        marginBottom: '12px',
                        padding: '12px',
                        backgroundColor: '#fafbfc',
                        borderRadius: '6px',
                        border: '1px solid #e1e4e8',
                      }}
                    >
                      <div
                        style={{
                          marginBottom: '8px',
                          fontSize: '13px',
                          fontWeight: '500',
                          color: '#24292e',
                        }}
                      >
                        {locale.templateGenerator || '配置模板生成器'}
                      </div>
                      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                        <Button
                          type="normal"
                          size="small"
                          onClick={() => generateTemplateByType('json-body')}
                          style={{
                            borderRadius: '4px',
                            fontSize: '12px',
                            backgroundColor: '#e3f2fd',
                            borderColor: '#2196f3',
                            color: '#1976d2',
                          }}
                        >
                          JSON请求体
                        </Button>
                        <Button
                          type="normal"
                          size="small"
                          onClick={() => generateTemplateByType('url-params')}
                          style={{
                            borderRadius: '4px',
                            fontSize: '12px',
                            backgroundColor: '#f3e5f5',
                            borderColor: '#9c27b0',
                            color: '#7b1fa2',
                          }}
                        >
                          URL参数
                        </Button>
                        <Button
                          type="normal"
                          size="small"
                          onClick={() => generateTemplateByType('form-body')}
                          style={{
                            borderRadius: '4px',
                            fontSize: '12px',
                            backgroundColor: '#e8f5e8',
                            borderColor: '#4caf50',
                            color: '#388e3c',
                          }}
                        >
                          表单数据
                        </Button>
                        <Button
                          type="normal"
                          size="small"
                          onClick={() => generateTemplateByType('custom-body')}
                          style={{
                            borderRadius: '4px',
                            fontSize: '12px',
                            backgroundColor: '#fff3e0',
                            borderColor: '#ff9800',
                            color: '#f57c00',
                          }}
                        >
                          自定义请求体
                        </Button>
                        <Button
                          type="normal"
                          size="small"
                          onClick={() => generateTemplateByType('args-path')}
                          style={{
                            borderRadius: '4px',
                            fontSize: '12px',
                            backgroundColor: '#fce4ec',
                            borderColor: '#e91e63',
                            color: '#c2185b',
                          }}
                        >
                          参数位置-Path
                        </Button>
                      </div>
                      <div
                        style={{
                          marginTop: '8px',
                          fontSize: '11px',
                          color: '#586069',
                          lineHeight: '1.4',
                        }}
                      >
                        💡 点击按钮快速生成对应类型的配置模板，包含完整的请求和响应配置
                        <br />
                        🔹 参数位置模式：通过 argsPosition 对象指定每个参数在请求中的位置（支持
                        query/path/header/cookie/body）
                      </div>
                    </div>
                  )}
                  {onlyEditRuntimeInfo ? (
                    <div
                      style={{
                        backgroundColor: '#f6f7f9',
                        border: '1px solid #dcdee3',
                        borderRadius: '8px',
                        padding: '16px',
                        fontSize: '13px',
                        fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                        lineHeight: '1.6',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-all',
                        maxHeight: '400px',
                        overflow: 'auto',
                        margin: 0,
                      }}
                    >
                      {(() => {
                        try {
                          const templateValue = generateTemplateWithSecurity();
                          if (!templateValue) return templateValue;

                          // 尝试解析并格式化为JSON显示
                          const parsedTemplate = parseTemplateContent(templateValue);
                          return JSON.stringify(parsedTemplate, null, 2);
                        } catch (error) {
                          // 如果解析失败，返回原始内容
                          return generateTemplateWithSecurity();
                        }
                      })()}
                    </div>
                  ) : (
                    <div
                      style={{
                        border: '1px solid #d9d9d9',
                        borderRadius: '8px',
                        overflow: 'hidden',
                      }}
                    >
                      <MonacoEditor
                        key={editorKey}
                        language="json"
                        height="250px"
                        value={generateTemplateWithSecurity()}
                        onChange={handleEditorChange}
                        options={{
                          minimap: { enabled: false },
                          scrollBeyondLastLine: false,
                          fontSize: 13,
                          tabSize: 2,
                          insertSpaces: true,
                          wordWrap: 'on',
                          lineNumbers: 'on',
                          formatOnPaste: true,
                          formatOnType: true,
                          theme: 'vs',
                          renderLineHighlight: 'all',
                          selectOnLineNumbers: true,
                        }}
                      />
                    </div>
                  )}
                </Form.Item>

                {/* 透明认证开关 */}
                <Form.Item
                  label={
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span>{locale.transparentAuth || '启用透明认证'}</span>
                      <Tag color="orange" style={{ margin: 0, fontSize: '12px' }}>
                        安全
                      </Tag>
                    </div>
                  }
                  style={{ marginBottom: '24px' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                      size="large"
                      {...init('transparentAuth', {
                        valueName: 'checked',
                        initValue: false,
                      })}
                      checkedChildren={locale.enable || '启用'}
                      unCheckedChildren={locale.disable || '禁用'}
                      isPreview={onlyEditRuntimeInfo}
                    />
                    <span
                      style={{
                        marginLeft: '12px',
                        color: '#666',
                        fontSize: '14px',
                      }}
                    >
                      {field.getValue('transparentAuth')
                        ? '认证信息将透明传递'
                        : '使用默认认证方式'}
                    </span>
                  </div>
                </Form.Item>

                {/* Security Schemes 选择器 */}
                {field.getValue('transparentAuth') && (
                  <div
                    style={{
                      padding: '20px',
                      backgroundColor: '#f8f9fa',
                      borderRadius: '8px',
                      border: '1px solid #e9ecef',
                    }}
                  >
                    <h4
                      style={{
                        margin: '0 0 16px 0',
                        fontSize: '14px',
                        fontWeight: '600',
                        color: '#495057',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                      }}
                    >
                      {locale.securitySchemes || '认证方案配置'}
                    </h4>

                    <Row gutter={24}>
                      {/* 后端认证方式 */}
                      <Col span={12}>
                        <Form.Item
                          label={
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <span>{locale.backendAuth || '后端认证方式'}</span>
                            </div>
                          }
                          style={{ marginBottom: '16px' }}
                        >
                          <Select
                            size="large"
                            style={{ borderRadius: '6px' }}
                            {...init('securitySchemeId', {
                              rules: [
                                {
                                  required: true,
                                  message: locale.pleaseSelectSecurityScheme || '请选择认证方案',
                                },
                              ],
                            })}
                            dataSource={
                              props?.serverConfig?.toolSpec?.securitySchemes?.map(scheme => ({
                                label: `${scheme.id} (${scheme.type})`,
                                value: scheme.id,
                              })) || []
                            }
                            placeholder={locale.pleaseSelectSecurityScheme || '请选择认证方案'}
                            isPreview={onlyEditRuntimeInfo}
                            onChange={value => {
                              // 更新字段值
                              field.setValue('securitySchemeId', value);
                              // 当认证方案改变时，强制重新渲染
                              setTimeout(() => {
                                field.setValue('templates', generateTemplateWithSecurity());
                              }, 0);
                            }}
                          />
                        </Form.Item>
                      </Col>

                      {/* 客户端认证方式 */}
                      <Col span={12}>
                        <Form.Item
                          label={
                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <span>{locale.clientAuth || '客户端认证方式'}</span>
                            </div>
                          }
                          style={{ marginBottom: '16px' }}
                        >
                          <Select
                            size="large"
                            style={{ borderRadius: '6px' }}
                            {...init('clientSecuritySchemeId', {
                              rules: [
                                {
                                  required: true,
                                  message: locale.pleaseSelectSecurityScheme || '请选择认证方案',
                                },
                              ],
                            })}
                            dataSource={
                              props?.serverConfig?.toolSpec?.securitySchemes?.map(scheme => ({
                                label: `${scheme.id} (${scheme.type})`,
                                value: scheme.id,
                              })) || []
                            }
                            placeholder={locale.pleaseSelectSecurityScheme || '请选择认证方案'}
                            isPreview={onlyEditRuntimeInfo}
                            onChange={value => {
                              // 更新字段值
                              field.setValue('clientSecuritySchemeId', value);
                              // 当认证方案改变时，强制重新渲染
                              setTimeout(() => {
                                field.setValue('templates', generateTemplateWithSecurity());
                              }, 0);
                            }}
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                  </div>
                )}
              </div>
            ) : null}
          </Form>
        </Dialog>
      ) : null}
    </div>
  );
});

export default CreateTools;
