import React, { useRef, useState } from 'react';
import { Table, Button, Dialog, Message, Input, Form, Grid, Upload } from '@alifd/next';
import CreateTools from './CreateTools';
import DeleteTool from './CreateTools/DeleteTool';
import { getParams, request } from '../../../globalLib';
import SwaggerParser from 'swagger-parser';
import { extractToolsFromOpenAPI } from './Swagger2Tools';
const { Row, Col } = Grid;
const currentNamespace = getParams('namespace');

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
    restToMcpSwitch = 'off',
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
  const getServerDetail = () => {
    props.getServerDetail && props.getServerDetail();
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

      const toolSpecification = JSON.stringify({
        tools,
        toolsMeta,
      });
      if (props?.onChange) {
        props.onChange(JSON.parse(toolSpecification));
      }
      Message.success(locale.importSuccess);
      setOpenApiDialogVisible(false);
    } catch (error) {
      Message.error(error.message || locale.fileInvalidFormat);
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

  // 校验格式并解析 OpenAPI
  const parseOpenAPI = async content => {
    try {
      // 自动识别 JSON/YAML 格式
      let parsedContent;
      try {
        parsedContent = JSON.parse(content);
      } catch (jsonError) {
        throw new Error('Invalid JSON/YAML format');
      }
      // 再使用 SwaggerParser 验证和解析 OpenAPI 文档
      const api = await SwaggerParser.validate(parsedContent);
      return api;
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

  return (
    <div>
      {!isPreview && !onlyEditRuntimeInfo && (
        <Button type="primary" onClick={openDialog} style={{ marginRight: 10 }}>
          {locale.newMcpTool}
        </Button>
      )}

      {!isPreview &&
        !onlyEditRuntimeInfo &&
        frontProtocol === 'mcp-sse' &&
        restToMcpSwitch === 'off' && (
          <Button
            type="primary"
            onClick={autoImportToolsFromMCPServer}
            style={{ marginRight: 10 }}
            loading={importLoading}
            disabled={importLoading}
          >
            {importLoading ? locale.importing : locale.importToolsFromMCP}
          </Button>
        )}

      {!isPreview &&
        !onlyEditRuntimeInfo &&
        frontProtocol !== 'stdio' &&
        restToMcpSwitch !== 'off' && (
          <Button
            type="primary"
            onClick={importToolsFromOpenApi}
            style={{ marginRight: 10 }}
            loading={importLoading}
            disabled={importLoading}
          >
            {importLoading ? locale.importing : locale.importToolsFromOpenAPI}
          </Button>
        )}

      <CreateTools
        key={JSON.stringify(serverConfig)}
        locale={locale}
        serverConfig={serverConfig}
        showTemplates={restToMcpSwitch !== 'off'}
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
        style={{ width: 800 }}
      >
        <Form>
          <Form.Item label={locale.selectOpenAPIFile}>
            <Upload
              listType="picture-card"
              accept=".json,.yaml,.yml"
              onChange={handleFileChange}
              beforeUpload={() => false} // 禁止自动上传
              dragable
              style={{
                border: '2px dashed #ccc',
                borderRadius: '8px',
                padding: '20px',
                backgroundColor: '#f9f9f9',
                transition: 'all 0.3s ease',
                textAlign: 'center',
                width: '100%',
              }}
            >
              <div style={{ padding: '20px', textAlign: 'center' }}>
                <p style={{ color: '#595959', fontSize: '14px' }}>
                  {locale.dragAndDropFileHereOrClickToSelect}
                </p>
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
          style={{ width: 600 }}
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

      <Table style={{ marginTop: '20px' }} dataSource={serverConfig?.toolSpec?.tools || []}>
        <Table.Column
          width={'15%'}
          title={locale.toolName}
          cell={(value, index, record) => {
            return <div style={{ minWidth: '100px' }}>{record.name}</div>;
          }}
        />
        <Table.Column title={locale.toolDescription} dataIndex={'description'} />
        <Table.Column
          title={locale.toolOnline}
          width={100}
          cell={(value, index, record) => {
            const onlineText = (
              <div style={{ color: 'green', textAlign: 'center' }}>{locale.online}</div>
            );
            const offlineText = (
              <div style={{ color: 'red', textAlign: 'center' }}>{locale.offline}</div>
            );
            if (serverConfig?.toolSpec?.toolsMeta?.[record.name]) {
              return serverConfig?.toolSpec?.toolsMeta?.[record.name]?.enabled
                ? onlineText
                : offlineText;
            } else {
              return onlineText;
            }
          }}
        />
        <Table.Column
          title={locale.operations}
          width={200}
          cell={(value, index, record) => {
            if (isPreview) {
              return (
                <a onClick={() => openToolDetail({ type: 'preview', record })}>
                  {locale.operationToolDetail}
                </a>
              );
            }

            return (
              <div>
                <a onClick={() => openToolDetail({ type: 'preview', record })}>
                  {locale.operationToolDetail}
                </a>
                <span style={{ margin: '0 5px' }}>|</span>
                <a
                  style={{ marginRight: 5 }}
                  onClick={() => openToolDetail({ type: 'edit', record })}
                >
                  {locale.operationToolEdit}
                  {/* 编辑 */}
                </a>
                {!onlyEditRuntimeInfo && (
                  <>
                    <span style={{ margin: '0 5px' }}>|</span>
                    <DeleteTool
                      record={record}
                      locale={locale}
                      serverConfig={serverConfig}
                      getServerDetail={getServerDetail}
                      onChange={props?.onChange}
                    />
                  </>
                )}
              </div>
            );
          }}
        />
      </Table>
    </div>
  );
};

export default ShowTools;
