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
  const [expandedKeys, setExpandedKeys] = useState(['args']); // 控制树节点展开状态
  const [currentNode, setCurrentNode] = useState({
    description: '',
    type: 'object',
    // eslint-disable-next-line react/prop-types
    label: locale.ArgumentsList,
    key: 'args',
    children: [],
  });
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

  const convertPropertiesToTreeData = (properties, prefix) => {
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
      ? convertPropertiesToTreeData(inputSchema?.properties, 'args')
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
        children: [],
      };
      rootNode.children = [defaultNewArg];
      args['args@@NewArg1'] = defaultNewArg;
    }

    rawData.push(rootNode);
    setRawData(rawData);
    setData(JSON.parse(JSON.stringify(rawData)));
    setArgs(args);

    // 初始化时展开所有节点
    const allKeys = collectAllKeys([rootNode]);
    setExpandedKeys(allKeys);

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
      templatesStr = JSON.stringify(toolsMeta?.templates['json-go-template']);

      // 从模板中提取安全方案ID和透明认证状态
      try {
        const templateObj = toolsMeta?.templates['json-go-template'];

        // 提取后端认证方式 (从 requestTemplate.security 中)
        if (templateObj?.requestTemplate?.security) {
          const securityKeys = Object.keys(templateObj.requestTemplate.security);
          if (securityKeys.length > 0) {
            const securityObj = templateObj.requestTemplate.security[securityKeys[0]];
            extractedSecuritySchemeId = securityObj?.id || securityKeys[0];
          }
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

    field.setValues({
      name,
      description,
      toolParams: inputSchema?.properties ? inputSchema?.properties : {},
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
    setExpandedKeys(['args']); // 重置展开状态
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

      if (
        (records.protocol === 'http' || records.protocol === 'https') &&
        values?.templates?.length > 0
      ) {
        try {
          // 使用生成的模板（已经注入了安全配置）
          let jsonGoTemplate = JSON.parse(generateTemplateWithSecurity());

          if (Object.keys(jsonGoTemplate).length > 0) {
            templates['json-go-template'] = jsonGoTemplate;
          }
        } catch (error) {
          console.error('Error parsing template JSON:', error);
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

  const validateTemplateJsonFormat = (rule, value, callback) => {
    try {
      if (value?.length > 0) {
        JSON.parse(value);
      }
      callback();
    } catch (e) {
      callback(locale.templateShouldBeJson);
    }
  };

  // 生成注入了安全配置的模板
  const generateTemplateWithSecurity = () => {
    try {
      if (!originalTemplate || !field.getValue('transparentAuth')) {
        return originalTemplate;
      }

      let jsonGoTemplate = JSON.parse(originalTemplate);
      let modified = false;

      // 注入后端认证方式
      const securitySchemeId = field.getValue('securitySchemeId');
      if (securitySchemeId) {
        const selectedScheme = props?.serverConfig?.toolSpec?.securitySchemes?.find(
          scheme => scheme.id === securitySchemeId
        );

        if (selectedScheme) {
          if (!jsonGoTemplate.requestTemplate) {
            jsonGoTemplate.requestTemplate = {};
          }
          jsonGoTemplate.requestTemplate.security = {
            [selectedScheme.id]: {
              id: selectedScheme.id,
            },
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
          jsonGoTemplate.security = {
            id: clientSelectedScheme.id,
            passthrough: true,
          };
          modified = true;
        }
      }

      return modified ? JSON.stringify(jsonGoTemplate, null, 2) : originalTemplate;
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
          validator: validateTemplateJsonFormat,
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

      arg.description = element.description;
      arg.type = element.type;
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
      children: [],
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
      children: [],
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
    field.setValue('toolParams', rawDataToFiledValue(rawData[0].children));
  };

  const isPreview = type === 'preview';
  return (
    <div>
      {visible ? (
        <Dialog
          v2
          title={'Tools'}
          footer={
            onlyEditRuntimeInfo ? (
              <p style={{ color: 'red' }}>{locale.editExistVersionMessage}</p>
            ) : isPreview ? (
              <Button type="primary" onClick={closeDialog}>
                {locale.close}
              </Button>
            ) : (
              true
            )
          }
          visible
          footerActions={isPreview ? [] : ['ok', 'cancel']}
          onOk={createItems}
          okProps={{ loading: okLoading }}
          onClose={closeDialog}
          style={{ width: '70%' }}
        >
          <Form field={field} {...formitemLayout}>
            {/* 名称 */}
            <Form.Item label={locale.toolName} required isPreview={!!type}>
              <Input
                placeholder={locale.toolName}
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
            <Form.Item label={locale.toolDescription} required>
              <Input.TextArea
                placeholder={locale.toolDescription}
                {...init('description', {
                  rules: [{ required: true, message: locale.toolDescriptionRequired }],
                })}
              />
            </Form.Item>

            {/* 是否上线 */}
            <Form.Item label={locale.toolOnline} required>
              <Switch
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
            </Form.Item>

            {/* 入参描述 */}
            <Form.Item label={locale.toolInputSchema} required style={{ margin: '16px 0 0' }} />
            <Form.Item label={locale.ArgumentTree} style={{ margin: '16px 0 0' }}>
              <Row>
                <Col style={{ marginTop: 5 }}>
                  <Button
                    type="primary"
                    size={'small'}
                    onClick={AddPropertiesToArgs}
                    disabled={isPreview || onlyEditRuntimeInfo}
                  >
                    {locale.AddNewArg || '增加参数'}
                  </Button>
                  &nbsp;&nbsp;
                  {/* 为 object 类型节点添加属性按钮 */}
                  {currentNode.type === 'object' && currentNode.key !== 'args' && (
                    <>
                      <Button
                        type="primary"
                        size="small"
                        onClick={AddPropertiesToCurrentNode}
                        disabled={isPreview || onlyEditRuntimeInfo}
                      >
                        {locale.AddNewProperties || '增加属性'}
                      </Button>
                      &nbsp;&nbsp;
                    </>
                  )}
                  {isPreview || onlyEditRuntimeInfo
                    ? locale.editExistVersionMessage || '只读模式不可编辑'
                    : ''}
                </Col>
              </Row>
              <Row style={{ marginTop: 5 }}>
                <Col>
                  <Tree
                    defaultExpandAll
                    autoExpandParent
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
                                <a>{node.label}</a>&nbsp;&nbsp;({args[node.key].type})
                              </Col>
                              <Col style={{ textOverflow: 'ellipsis', marginLeft: 10 }}>
                                {args[node.key].description?.length <= 25
                                  ? args[node.key].description
                                  : `${args[node.key].description?.substring(0, 20)}...`}
                              </Col>
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
                </Col>
              </Row>
            </Form.Item>
            {currentNode.key !== '' && currentNode.key !== 'args' && (
              <Form.Item label={locale.ArgumentInfo}>
                <Row>
                  <Col>
                    <Form.Item
                      name="args.name"
                      label={locale.toolParamName}
                      required
                      requiredTrigger="onBlur"
                      asterisk={false}
                    >
                      <Input
                        isPreview={onlyEditRuntimeInfo}
                        disabled={currentNode.key === 'args'}
                        value={currentNode.label}
                        onChange={data => {
                          if (currentNode.key !== '') {
                            currentNode.label = data;
                            changeNodeInfo(currentNode);
                          }
                        }}
                      />
                    </Form.Item>
                  </Col>
                  <Col offset={1}>
                    <Form.Item name="args.type" label={locale.toolParamType}>
                      <Select
                        isPreview={onlyEditRuntimeInfo}
                        disabled={currentNode.key === 'args'}
                        value={currentNode.type}
                        dataSource={[
                          { label: '字符串类型 string', value: 'string' },
                          { label: '数字类型 number', value: 'number' },
                          { label: '整数类型 integer', value: 'integer' },
                          { label: '布尔类型 boolean', value: 'boolean' },
                          { label: '数组类型 array', value: 'array' },
                          { label: '对象类型 object', value: 'object' },
                          // { label:'对象类型，使用 properties 字段定义对象属性的模式', value:'object' },
                        ]}
                        style={{ width: '60%' }}
                        onChange={data => {
                          if (currentNode.key !== '') {
                            if (!(data === 'array' || data === 'object')) {
                              currentNode.children = [];
                            }
                            if (data === 'array') {
                              const itemNode = {
                                label: 'items',
                                type: 'string',
                                description: '',
                                key: `${currentNode.key}@@items`,
                              };
                              currentNode.type = data;
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
                                key: `${currentNode.key}@@property1`,
                                children: [],
                              };
                              currentNode.children = [defaultPropertyNode];
                              currentNode.type = data;
                              args[`${currentNode.key}@@property1`] = defaultPropertyNode;

                              // 确保当前节点展开，以显示新添加的属性
                              const updatedExpandedKeys = [...expandedKeys];
                              if (!updatedExpandedKeys.includes(currentNode.key)) {
                                updatedExpandedKeys.push(currentNode.key);
                              }
                              setExpandedKeys(updatedExpandedKeys);

                              changeNodeInfo(currentNode);
                            } else {
                              currentNode.type = data;
                              changeNodeInfo(currentNode);
                            }
                          }
                        }}
                      />
                    </Form.Item>
                  </Col>
                </Row>
                <Row>
                  <Col>
                    <Form.Item
                      label={locale.toolParamDescription}
                      name="args.description"
                      asterisk={false}
                    >
                      <Input.TextArea
                        disabled={currentNode.key === 'args'}
                        value={currentNode.description}
                        onChange={data => {
                          if (currentNode.key !== '') {
                            currentNode.description = data;
                            changeNodeInfo(currentNode);
                          }
                        }}
                      />
                    </Form.Item>
                  </Col>
                </Row>
              </Form.Item>
            )}
            {showTemplates ? (
              <>
                <Form.Item
                  label={locale.invokeTemplates}
                  extra={locale.httpToMcpDoc}
                  style={{ marginTop: '10px' }}
                >
                  {onlyEditRuntimeInfo ? (
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
                        maxHeight: '300px',
                        overflow: 'auto',
                        margin: 0,
                      }}
                    >
                      {(() => {
                        try {
                          const templateValue = generateTemplateWithSecurity();
                          return templateValue
                            ? JSON.stringify(JSON.parse(templateValue), null, 2)
                            : templateValue;
                        } catch (error) {
                          return generateTemplateWithSecurity();
                        }
                      })()}
                    </pre>
                  ) : (
                    <MonacoEditor
                      language="json"
                      height="200px"
                      value={generateTemplateWithSecurity()}
                      onChange={value => {
                        // 当用户直接编辑时，更新原始模板和表单值
                        setOriginalTemplate(value);
                        field.setValue('templates', value);
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
                </Form.Item>

                {/* 透明认证开关 */}
                <Form.Item label={locale.transparentAuth || '启用透明认证'}>
                  <Switch
                    {...init('transparentAuth', {
                      valueName: 'checked',
                      initValue: false,
                    })}
                    checkedChildren={locale.enable || '启用'}
                    unCheckedChildren={locale.disable || '禁用'}
                    isPreview={onlyEditRuntimeInfo}
                  />
                </Form.Item>

                {/* Security Schemes 选择器 */}
                {field.getValue('transparentAuth') && (
                  <Form.Item label={locale.securitySchemes || '认证方案'}>
                    <Row gutter={16}>
                      {/* 后端认证方式 */}
                      <Col span={12}>
                        <Form.Item
                          label={locale.backendAuth || '后端认证方式'}
                          style={{ marginBottom: 0 }}
                        >
                          <Select
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
                            style={{ width: '100%' }}
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
                          label={locale.clientAuth || '客户端认证方式'}
                          style={{ marginBottom: 0 }}
                        >
                          <Select
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
                            style={{ width: '100%' }}
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
                  </Form.Item>
                )}
              </>
            ) : null}
          </Form>
        </Dialog>
      ) : null}
    </div>
  );
});

export default CreateTools;
