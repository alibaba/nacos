import React, { useEffect, useState } from 'react';
import { Dialog, Field, Form, Input, Grid, Table, Button } from '@alifd/next';
import { formitemLayout, GetTitle, tableOperation } from './components';
import { request } from '../../../../globalLib';
const { Row, Col } = Grid;

const CreateTools = props => {
  const { locale, showTemplates = false, serverConfig = {} } = props;
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
  const openDialog = () => {
    setVisible(true);
  };
  const closeDialog = () => {
    setVisible(false);
  };
  const createItems = () => {
    field.validate((error, values) => {
      if (error) {
        return;
      }
      console.log('values', values);
      const toolitem = {
        name: values?.name,
        description: values?.description,
        inputSchema: {
          type: 'object',
          properties: values?.toolParams?.map(item => ({
            [item.name]: {
              type: item.type,
              description: item.description,
            },
          })),
        },
        toolMeta: {
          enabled: true,
          invokeContext: values?.invokeContext.map(item => ({
            [item.key]: item.value,
          })),
          templates: values?.templates.map(item => ({
            [item.key]: item.value,
          })),
        },
      };
      const params = {
        mcpName: serverConfig?.name,
        serverSpecification: JSON.stringify(serverConfig),
        toolSpecification: JSON.stringify(toolitem),
      };
      console.log('toolitem ===>', {
        serverConfig,
        toolitem,
        params,
      });
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
    field.removeArrayValue('invokeContext', index);
  };
  // 添加模板 template
  const addNewTemplates = () => {
    setTemplateIdx(templateIdx + 1);
    field.addArrayValue('templates', templateIdx, { id: templateIdx + 1, key: '', value: '' });
  };
  // 删除模板 template
  const deleteTemplates = index => {
    field.removeArrayValue('templates', index);
  };

  // 渲染表格
  const renderTableCell = params => {
    const { component = 'input', key = '', rulesMessage = locale.placeInput } = params;

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
      <Form.Item style={{ margin: 0 }}>
        <Input {...field.init(key, { rules })} />
      </Form.Item>
    );
  };

  return (
    <div>
      <Button type="primary" onClick={openDialog}>
        {locale.newMcpTool}2
      </Button>

      <Dialog
        v2
        title={locale.newMcpTool}
        visible={visible}
        onOk={createItems}
        onClose={closeDialog}
        style={{ width: '70%' }}
      >
        <Form field={field} {...formitemLayout}>
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
          <GetTitle label={locale.toolInputSchema} onClick={addNewToolMetadata} locale={locale} />
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
                  // cell={(value, index) => (
                  //   <Form.Item style={{ margin: 0 }}>
                  //     <Input
                  //       {...field.init(`toolParams.${index}.name`, {
                  //         rules: [{ required: true, message: locale.toolParamNameRequired }]
                  //       })}
                  //     />
                  //   </Form.Item>
                  // )}
                  cell={(value, index, record) =>
                    renderTableCell({ key: `toolParams.${index}.name` })
                  }
                />
                <Table.Column
                  title={locale.toolParamType}
                  dataIndex="type"
                  // cell={(value, index) => (
                  //   <Form.Item style={{ margin: 0 }}>
                  //     <Input {...field.init(`toolParams.${index}.type`, { rules: [{ required: true, message: locale.toolParamTypeRequired }] })} />
                  //   </Form.Item>
                  // )}
                  cell={(value, index, record) =>
                    renderTableCell({ key: `toolParams.${index}.type` })
                  }
                />
                <Table.Column
                  title={locale.toolParamDescription}
                  dataIndex="description"
                  // cell={(value, index) => (
                  //   <Form.Item style={{ margin: 0 }}>
                  //     <Input.TextArea
                  //       aria-label="auto height"
                  //       autoHeight={{ minRows: 2, maxRows: 8 }}
                  //       {...field.init(`toolParams.${index}.description`, {
                  //         rules: [
                  //           { required: true, message: locale.toolInputSchemaRequired }
                  //         ]
                  //       })}
                  //     />
                  //   </Form.Item>
                  // )}
                  cell={(value, index, record) =>
                    renderTableCell({
                      key: `toolParams.${index}.description`,
                      component: 'textArea',
                    })
                  }
                />
                {/* delete */}
                {tableOperation({ onClick: deleteToolParam, locale })}
              </Table>
            </Col>
          </Row>

          <h3>{locale.toolMetadata}</h3>
          {/* Tool 元数据 */}
          <GetTitle label={locale.invokeContext} onClick={addNewToolMetadata} locale={locale} />
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
                  // cell={(value, index) => (
                  //   <Form.Item style={{ margin: 0 }}>
                  //     <Input
                  //       {...field.init(`invokeContext.${index}.key`, {
                  //         rules: [{ required: true, message: locale.placeInput }]
                  //       })}
                  //     />
                  //   </Form.Item>
                  // )}
                  cell={(value, index, record) =>
                    renderTableCell({ key: `invokeContext.${index}.key` })
                  }
                />
                <Table.Column
                  title="Value"
                  dataIndex="value"
                  // cell={(value, index) => (
                  //   <Form.Item style={{ margin: 0 }}>
                  //     <Input
                  //       {...field.init(`invokeContext.${index}.value`, {
                  //         rules: [{ required: true, message: locale.placeInput }]
                  //       })}
                  //     />
                  //   </Form.Item>
                  // )}
                  cell={(value, index, record) =>
                    renderTableCell({ key: `invokeContext.${index}.value`, component: 'textArea' })
                  }
                />

                {/* delete */}
                {tableOperation({ onClick: deleteToolMetadata, locale })}
              </Table>
            </Col>
          </Row>

          {showTemplates ? (
            <>
              <GetTitle label={locale.invokeTemplates} onClick={addNewTemplates} locale={locale} />
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
                      // cell={(value, index) => (
                      //   <Form.Item style={{ margin: 0 }}>
                      //     <Input
                      //       {...field.init(`templates.${index}.key`, {
                      //         rules: [{ required: true, message: locale.placeInput }]
                      //       })}
                      //     />
                      //   </Form.Item>
                      // )}
                      cell={(value, index, record) =>
                        renderTableCell({ key: `templates.${index}.key` })
                      }
                    />
                    <Table.Column
                      title="Value"
                      dataIndex="value"
                      // cell={(value, index) => (
                      //   <Form.Item style={{ margin: 0 }}>
                      //     <Input.TextArea
                      //       aria-label="auto height"
                      //       autoHeight={{ minRows: 2, maxRows: 8 }}
                      //       {...field.init(`templates.${index}.value`, {
                      //         rules: [{ required: true, message: locale.placeInput }]
                      //       })}
                      //     />
                      //   </Form.Item>
                      // )}
                      cell={(value, index, record) =>
                        renderTableCell({ key: `templates.${index}.value`, component: 'textArea' })
                      }
                    />

                    {/* delete */}
                    {tableOperation({ onClick: deleteTemplates, locale })}
                  </Table>
                </Col>
              </Row>
            </>
          ) : null}
        </Form>
      </Dialog>
    </div>
  );
};

export default CreateTools;
