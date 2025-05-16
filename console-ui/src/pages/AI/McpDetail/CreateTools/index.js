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

const { Row, Col } = Grid;

const CreateTools = React.forwardRef((props, ref) => {
  // eslint-disable-next-line react/prop-types
  const { locale, showTemplates = false } = props;
  const field = Field.useField({
    parseName: true,
    values: {
      toolParams: [],
      invokeContext: [],
      templates: '',
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
        description: arg.description ? arg.description : '',
        children,
        key: `${prefix}@@${element}`,
      };
      result.push(node);
      args[prefix + '@@' + element] = node;
    }
    return result;
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

    args['args'] = rootNode;
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

    const _invokeContext = toolsMeta?.invokeContext
      ? Object.keys(toolsMeta?.invokeContext).map(key => ({
          key,
          value: toolsMeta?.invokeContext[key],
        }))
      : [];
    setInvokeIdx(_invokeContext.length + 1);

    let templatesStr = '';
    if (toolsMeta?.templates !== undefined && 'json-go-template' in toolsMeta?.templates) {
      templatesStr = JSON.stringify(toolsMeta?.templates['json-go-template']);
    }

    field.setValues({
      name,
      description,
      toolParams: inputSchema?.properties ? inputSchema?.properties : {},
      invokeContext: _invokeContext,
      templates: templatesStr,
      enabled: toolsMeta?.enabled,
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
      },
    });
  };

  const closeDialog = () => {
    setVisible(false);
    setType('');
    setCurrentNode({
      description: '',
      type: 'string',
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
        values?.invokeContext?.forEach(item => (invokeContext[item.key] = item.value));
      }

      const templates = {};

      if (values.protocol === 'http') {
        const jsonGoTemplate = JSON.parse(values?.templates);
        if (Object.keys(jsonGoTemplate).length > 0) {
          templates['json-go-template'] = jsonGoTemplate;
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
        },
      };
      const _toolsMetaitem = {
        [values?.name]: {
          enabled: values?.enabled,
          invokeContext,
          templates,
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
        params['endpointSpecification'] = endpointSpecification;
      }

      if (props?.onChange) {
        props?.onChange(JSON.parse(toolSpecification));
        closeDialog();
      } else {
        putMcp(params);
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
      props?.getServerDetail();
    } else {
      if (type == 'edit') {
        Message.error(result?.message || locale.editToolFailed);
      } else {
        Message.error(result?.message || locale.createToolFailed);
      }
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
      JSON.parse(value);
      callback();
    } catch (e) {
      callback(locale.templateShouldBeJson);
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
        type: element.type,
        description: element.description,
      };
      if (element.type === 'object' && element.children.length > 0) {
        arg.properties = rawDataToFiledValue(element.children);
      } else if (element.type === 'array') {
        arg.items = rawDataToFiledValue(element.children)['items'];
      }
      result[element.label] = arg;
    }
    return result;
  };

  const AddPropertiesToArgs = () => {
    const parentNode = args[currentNode.key];
    if (!parentNode.children) {
      parentNode.children = [];
    }
    const childLen = parentNode.children.length + 1;
    const newArgsName = 'newArg' + childLen;
    const newNode = {
      label: newArgsName,
      key: currentNode.key + '@@' + newArgsName,
      type: 'string',
      description: '',
      children: [],
    };

    args[currentNode.key + '@@' + newArgsName] = newNode;
    if (!parentNode.children) {
      parentNode.children = [];
    }
    parentNode.children.push(newNode);
    setRawData(rawData);
    setArgs(args);
    setData(JSON.parse(JSON.stringify(rawData)));
    saveParamToFiled();
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
          visible={true}
          footer={
            isPreview ? (
              <Button type="primary" onClick={closeDialog}>
                {locale.close}
              </Button>
            ) : (
              true
            )
          }
          footerActions={isPreview ? [] : ['ok', 'cancel']}
          onOk={createItems}
          okProps={{ loading: okLoading }}
          onClose={closeDialog}
          style={{ width: '70%' }}
        >
          <Form field={field} {...formitemLayout} isPreview={isPreview}>
            <h3>{locale.baseData}</h3>
            {/* 名称 */}
            <Form.Item label={locale.toolName} required isPreview={type ? true : false}>
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
            <Form.Item
              label={locale.toolInputSchema}
              required
              style={{ margin: '16px 0 0' }}
            ></Form.Item>
            <Form.Item label={locale.ArgumentTree} style={{ margin: '16px 0 0' }}>
              {!isPreview && (
                <Row>
                  <Col style={{ marginTop: 5 }}>
                    <Button
                      type="primary"
                      size={'small'}
                      onClick={AddPropertiesToArgs}
                      disabled={currentNode.type !== 'object'}
                    >
                      {currentNode.key === 'args' ? locale.AddNewArg : locale.AddNewProperties}
                    </Button>
                    &nbsp;&nbsp;
                    {currentNode.type !== 'object' ? locale.OnlyObjectSupportAddProperties : ''}
                  </Col>
                </Row>
              )}
              <Row style={{ marginTop: 5 }}>
                <Col>
                  <Tree
                    defaultExpandAll
                    autoExpandParent
                    showLine
                    isLabelBlock
                    dataSource={data}
                    defaultSelectedKeys={['args']}
                    aria-label={'test'}
                    labelRender={node => {
                      return (
                        <Row style={{ fontSize: 'medium' }}>
                          <Col>
                            <a>{node.label}</a>&nbsp;&nbsp;({args[node.key].type})
                          </Col>
                          <Col style={{ textOverflow: 'ellipsis' }}>
                            {args[node.key].description?.length <= 25
                              ? args[node.key].description
                              : args[node.key].description?.substring(0, 20) + '...'}
                          </Col>
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
                                key: currentNode.key + '@@items',
                              };
                              currentNode.type = data;
                              currentNode.children = [itemNode];
                              args[currentNode.key + '@@items'] = itemNode;
                              changeNodeInfo(currentNode);
                            } else if (data === 'object') {
                              currentNode.children = [];
                              currentNode.type = data;
                              changeNodeInfo(currentNode);
                            } else {
                              currentNode.type = data;
                              changeNodeInfo(currentNode);
                            }
                          }
                        }}
                      ></Select>
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
                  <Input.TextArea
                    aria-label="auto height"
                    style={{ minHeight: 32 }}
                    multiline
                    autoHeight={{ minRows: 5, maxRows: 8 }}
                    {...field.init('templates', {
                      rules: [{ validator: validateTemplateJsonFormat }],
                    })}
                  />
                </Form.Item>
              </>
            ) : null}
          </Form>
        </Dialog>
      ) : null}
    </div>
  );
});

export default CreateTools;
