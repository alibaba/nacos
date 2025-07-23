import React, { useRef, useState } from 'react';
import { Table, Button, Dialog, Message, Input, Form, Grid } from '@alifd/next';
import CreateTools from './CreateTools';
import DeleteTool from './CreateTools/DeleteTool';
import { getParams, request } from '../../../globalLib';
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
  const getServerDetail = () => {
    props.getServerDetail && props.getServerDetail();
  };

  const openToolDetial = params => {
    const { type, record } = params;
    const toolsMeta = serverConfig?.toolSpec?.toolsMeta?.[record.name];
    toolsRef?.current?.openVisible && toolsRef.current.openVisible({ type, record, toolsMeta });
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

  const importToolsFromOpenApi = async () => {};

  return (
    <div>
      {!isPreview && !onlyEditRuntimeInfo && (
        <Button type="primary" onClick={openDialog} style={{ marginRight: 10 }}>
          {locale.newMcpTool}
        </Button>
      )}

      {!isPreview &&
        !onlyEditRuntimeInfo &&
        frontProtocol !== 'stdio' &&
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
        frontProtocol === 'stdio' &&
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
              } else {
                Message.error(locale.importToolsFailed + result.message);
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
                <a onClick={() => openToolDetial({ type: 'preview', record })}>
                  {locale.operationToolDetail}
                </a>
              );
            }

            return (
              <div>
                <a onClick={() => openToolDetial({ type: 'preview', record })}>
                  {locale.operationToolDetail}
                </a>
                <span style={{ margin: '0 5px' }}>|</span>
                <a
                  style={{ marginRight: 5 }}
                  onClick={() => openToolDetial({ type: 'edit', record })}
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
