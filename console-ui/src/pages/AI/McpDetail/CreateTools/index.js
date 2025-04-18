import React, { useEffect, useImperativeHandle, useState } from 'react';
import { Dialog, Field, Form, Input, Grid, Table, Button } from '@alifd/next';
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
  useEffect(() => {
    if (field.getValue('toolParams') && !field.getValue('toolParams')?.length) {
      addNewToolParam();
    }
    if (field.getValue('invokeContext') && !field.getValue('invokeContext')?.length) {
      addNewToolMetadata();
    }
    if (field.getValue('templates') && !field.getValue('templates')?.length) {
      addNewTemplates();
    }
  }, []);

  useImperativeHandle(ref, () => ({
    openVisible: ({ record, type }) => {
      const { name, description, inputSchema, toolMeta } = record;
      setType(type);

      const _toolParams = inputSchema?.properties
        ? Object.keys(inputSchema?.properties).map(key => ({
            name: key,
            type: inputSchema?.properties[key].type,
            description: inputSchema?.properties[key].description,
          }))
        : [];
      setIdx(_toolParams.length + 1);

      const _invokeContext = toolMeta?.invokeContext
        ? Object.keys(toolMeta?.invokeContext).map(key => ({
            key,
            value: toolMeta?.invokeContext[key],
          }))
        : [];
      setInvokeIdx(_invokeContext.length + 1);

      const _templates = toolMeta?.templates
        ? Object.keys(toolMeta?.templates).map(key => ({
            key,
            value: JSON.stringify(toolMeta?.templates[key]),
          }))
        : [];
      setTemplateIdx(_templates.length + 1);

      field.setValues({
        name,
        description,
        toolParams: _toolParams,
        invokeContext: _invokeContext,
        templates: _templates,
      });

      setVisible(true);
      console.log('{first}', { record, type });
    },
  }));

  const openDialog = () => {
    setVisible(true);
  };

  const closeDialog = () => {
    setVisible(false);
    setType('');
    field.reset();
  };

  const createItems = () => {
    field.validate((error, values) => {
      if (error) {
        return;
      }
      console.log('values', values);
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

      const serverSpecification = `{
        "name": "${props?.serverConfig?.name}",
        "type": "${props?.serverConfig?.type}",
        "version": "${props?.serverConfig?.version}",
        "description": "${props?.serverConfig?.description}",
        "backendProtocol": "${props?.serverConfig?.remoteServerConfig?.backendProtocol}",
        "exportPath": "${props?.serverConfig?.remoteServerConfig?.exportPath}",
        "localServerConfig": ${JSON.stringify(props?.serverConfig?.localServerConfig || {})},
        "capabilitys": ${JSON.stringify(props?.serverConfig?.capabilitys || [])},
        "backendEndpoints": ${JSON.stringify(props?.serverConfig?.backendEndpoints)},
        "tools": ${JSON.stringify(props?.serverConfig?.tools || [])}
      }`;

      const toolSpecification = `[{
        "name": "${values?.name}",
        "description": "${values?.description}",
        "inputSchema": {
          "type": "object",
          "properties": ${JSON.stringify(properties)}
        },
        "toolMeta": {
          "enabled": true,
          "invokeContext": ${JSON.stringify(invokeContext)}
        }
      }]`;

      const endpointSpecification = `{
        "type": "REF",
        "data":{
          "namespaceId":"${props?.serverConfig?.remoteServerConfig?.serviceRef?.namespaceId}",
          "serviceName": "${props?.serverConfig?.remoteServerConfig?.serviceRef?.serviceName}",
          "groupName":"${props?.serverConfig?.remoteServerConfig?.serviceRef?.groupName}"
        }
      }`;

      const params = {
        mcpName: props?.serverConfig?.name,
        serverSpecification,
        toolSpecification,
        endpointSpecification,
      };

      window.serverSpecification = serverSpecification;
      window.params = params;
      window.putMcp = putMcp;

      putMcp(params);
    });
  };

  const putMcp = async params => {
    const result = await request({
      url: 'v3/console/ai/mcp',
      method: 'put',
      data: params,
    });

    console.log('result', result);
  };

  // 添加入参描述
  const addNewToolParam = () => {
    setIdx(idx + 1);
    const item = {
      id: idx + 1,
      name: '',
      type: '',
      description: '',
    };
    field.addArrayValue('toolParams', idx, item);
  };
  // 删除入参描述
  const deleteToolParam = index => {
    field.deleteArrayValue('toolParams', index);
  };
  // 添加Tool 元数据
  const addNewToolMetadata = () => {
    setInvokeIdx(invokeIdx + 1);
    field.addArrayValue('invokeContext', invokeIdx, { id: invokeIdx + 1, key: '', value: '' });
  };
  // 删除Tool 元数据
  const deleteToolMetadata = index => {
    field.deleteArrayValue('invokeContext', index);
  };
  // 添加模板 template
  const addNewTemplates = () => {
    setTemplateIdx(templateIdx + 1);
    field.addArrayValue('templates', templateIdx, { id: templateIdx + 1, key: '', value: '' });
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
            autoHeight={{ minRows: 2, maxRows: 8 }}
            {...field.init(key, { rules })}
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
      <Button type="primary" onClick={openDialog}>
        {locale.newMcpTool}
      </Button>

      {visible ? (
        <Dialog
          v2
          title={locale.newMcpTool}
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
          onClose={closeDialog}
          style={{ width: '70%' }}
        >
          <Form field={field} {...formitemLayout} isPreview={isPreview}>
            <h3>{locale.baseData}</h3>
            {/* 名称 */}
            <Form.Item label={locale.toolName} required>
              <Input
                placeholder={locale.toolName}
                {...init('name', { rules: [{ required: true, message: locale.toolNameRequired }] })}
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
                      renderTableCell({ key: `toolParams.${index}.type` })
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
