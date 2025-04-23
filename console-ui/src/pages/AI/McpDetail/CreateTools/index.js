import React, { useEffect, useImperativeHandle, useState } from 'react';
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
  Switch,
} from '@alifd/next';
import { formitemLayout, GetTitle, tableOperation } from './components';
import { request } from '../../../../globalLib';
const { Row, Col } = Grid;

const CreateTools = React.forwardRef((props, ref) => {
  const { locale, showTemplates = false } = props;
  const field = Field.useField({
    parseName: true,
    values: {
      toolParams: [],
      invokeContext: [],
      templates: [],
    },
  });
  const { init } = field;
  const [visible, setVisible] = useState(false);
  const [idx, setIdx] = useState(0);
  const [invokeIdx, setInvokeIdx] = useState(0);
  const [templateIdx, setTemplateIdx] = useState(0);
  const [type, setType] = useState('');
  const [okLoading, setOkLoading] = useState(false);
  useEffect(() => {
    if (visible) {
      if (field.getValue('toolParams') && !field.getValue('toolParams')?.length) {
        addNewToolParam();
      }
      if (field.getValue('invokeContext') && !field.getValue('invokeContext')?.length) {
        addNewToolMetadata();
      } else {
        field.setValues({ invokeContext: [] });
      }
      if (field.getValue('templates') && !field.getValue('templates')?.length) {
        addNewTemplates();
      } else {
        field.setValues({ templates: [] });
      }
    }
  }, [visible]);

  const openVisible = ({ record, type, toolsMeta }) => {
    const { name, description, inputSchema } = record;
    setType(type);

    const _toolParams = inputSchema?.properties
      ? Object.keys(inputSchema?.properties).map(key => ({
          name: key,
          type: inputSchema?.properties[key].type,
          description: inputSchema?.properties[key].description,
        }))
      : [];
    setIdx(_toolParams.length + 1);

    const _invokeContext = toolsMeta?.invokeContext
      ? Object.keys(toolsMeta?.invokeContext).map(key => ({
          key,
          value: toolsMeta?.invokeContext[key],
        }))
      : [];
    setInvokeIdx(_invokeContext.length + 1);

    const _templates = toolsMeta?.templates
      ? Object.keys(toolsMeta?.templates).map(key => ({
          key,
          // 判断 toolsMeta?.templates[key] 是否是字符串
          value:
            typeof toolsMeta?.templates[key] === 'string'
              ? toolsMeta?.templates[key]
              : JSON.stringify(toolsMeta?.templates[key]),
        }))
      : [];
    setTemplateIdx(_templates.length + 1);

    field.setValues({
      name,
      description,
      toolParams: _toolParams,
      invokeContext: _invokeContext,
      templates: _templates,
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
  };

  const createItems = () => {
    field.validate((error, values) => {
      const records = props?.serverConfig;
      if (error) {
        return;
      }

      const properties = {};
      if (values?.toolParams?.length) {
        values?.toolParams?.forEach(
          item =>
            (properties[item.name] = {
              type: item.type,
              description: item.description,
            })
        );
      }

      const invokeContext = {};
      if (values?.invokeContext?.length) {
        values?.invokeContext?.forEach(item => (invokeContext[item.key] = item.value));
      }

      const templates = {};
      if (values?.templates?.length) {
        values?.templates?.forEach(item => (templates[item.key] = item.value));
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

  // 添加入参描述
  const addNewToolParam = () => {
    setIdx(idx + 1);
    field.addArrayValue('toolParams', idx, {
      id: idx + 1,
      name: '',
      type: 'string',
      description: '',
    });
  };
  // 删除入参描述
  const deleteToolParam = index => {
    field.deleteArrayValue('toolParams', index);
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
  // 添加模板 template
  const addNewTemplates = () => {
    setTemplateIdx(templateIdx + 1);
    field.addArrayValue('templates', templateIdx, {
      id: templateIdx + 1,
      key: '',
      value: '',
    });
  };
  // 删除模板 template
  const deleteTemplates = index => {
    field.deleteArrayValue('templates', index);
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
    if (component == 'textArea') {
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

    if (component == 'select') {
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

  const isPreview = type == 'preview' ? true : false;
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
                })}
              />
            </Form.Item>

            {/* 入参描述 */}
            <GetTitle
              label={locale.toolInputSchema}
              onClick={addNewToolParam}
              locale={locale}
              disabled={isPreview}
            />
            <Row>
              <Col span={20} offset={4}>
                <Table
                  size="small"
                  style={{ marginTop: '10px' }}
                  dataSource={field.getValue('toolParams')}
                >
                  <Table.Column
                    title={locale.toolParamName}
                    dataIndex="name"
                    width={200}
                    cell={(value, index, record) =>
                      renderTableCell({ key: `toolParams.${index}.name` })
                    }
                  />
                  <Table.Column
                    title={locale.toolParamType}
                    dataIndex="type"
                    width={200}
                    cell={(value, index, record) =>
                      renderTableCell({ key: `toolParams.${index}.type`, component: 'select' })
                    }
                  />
                  <Table.Column
                    title={locale.toolParamDescription}
                    dataIndex="description"
                    cell={(value, index, record) =>
                      renderTableCell({
                        key: `toolParams.${index}.description`,
                        component: 'textArea',
                      })
                    }
                  />
                  {/* delete */}
                  {tableOperation({ onClick: deleteToolParam, locale, disabled: isPreview })}
                </Table>
              </Col>
            </Row>

            <h3>{locale.toolMetadata}</h3>
            {/* Tool 元数据 */}
            <GetTitle
              label={locale.invokeContext}
              onClick={addNewToolMetadata}
              locale={locale}
              disabled={isPreview}
            />
            <Row>
              <Col span={20} offset={4}>
                <Table
                  size="small"
                  style={{ marginTop: '10px' }}
                  dataSource={field.getValue('invokeContext')}
                >
                  <Table.Column
                    title="Key"
                    dataIndex="key"
                    width={200}
                    cell={(value, index, record) =>
                      renderTableCell({ key: `invokeContext.${index}.key` })
                    }
                  />
                  <Table.Column
                    title="Value"
                    dataIndex="value"
                    cell={(value, index, record) =>
                      renderTableCell({
                        key: `invokeContext.${index}.value`,
                        component: 'textArea',
                      })
                    }
                  />
                  {/* delete */}
                  {tableOperation({ onClick: deleteToolMetadata, locale, disabled: isPreview })}
                </Table>
              </Col>
            </Row>

            {/* 调用模板 */}
            {showTemplates ? (
              <>
                <GetTitle
                  label={locale.invokeTemplates}
                  onClick={addNewTemplates}
                  locale={locale}
                  disabled={isPreview}
                />
                <Row>
                  <Col span={20} offset={4}>
                    <Table
                      size="small"
                      style={{ marginTop: '10px' }}
                      dataSource={field.getValue('templates')}
                    >
                      <Table.Column
                        title="Key"
                        dataIndex="key"
                        width={200}
                        cell={(value, index, record) =>
                          renderTableCell({ key: `templates.${index}.key` })
                        }
                      />
                      <Table.Column
                        title="Value"
                        dataIndex="value"
                        cell={(value, index, record) =>
                          renderTableCell({
                            key: `templates.${index}.value`,
                            component: 'textArea',
                          })
                        }
                      />
                      {/* delete */}
                      {tableOperation({ onClick: deleteTemplates, locale, disabled: isPreview })}
                    </Table>
                  </Col>
                </Row>
              </>
            ) : null}
          </Form>
        </Dialog>
      ) : null}
    </div>
  );
});

export default CreateTools;
