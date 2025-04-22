import { Dialog, Message } from '@alifd/next';
import { request } from '../../../../globalLib';
import React from 'react';

const DeleteTool = props => {
  const { record, locale, getServerDetail, serverConfig } = props;
  const [visible, setVisible] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const handleDelete = async _serverConfig => {
    setLoading(true);
    const result = await request({
      url: `v3/console/ai/mcp`,
      method: 'PUT',
      data: {
        mcpName: serverConfig?.name,
        serverSpecification: JSON.stringify({
          type: serverConfig?.type,
          name: serverConfig?.name,
          description: serverConfig?.description,
          version: serverConfig?.version,
          enbled: true,
          remoteServerConfig: {
            exportPath: serverConfig?.remoteServerConfig?.exportPath,
            backendProtocol: serverConfig?.remoteServerConfig?.backendProtocol,
          },
        }),
        toolSpecification: JSON.stringify(_serverConfig?.tools),
        endpointSpecification: JSON.stringify({
          type: 'REF',
          data: serverConfig?.remoteServerConfig?.serviceRef,
        }),
      },
    });
    setLoading(false);

    if (result.code === 0) {
      getServerDetail();
      closeDialog();
      Message.success(locale.deleteToolSuccess);
    } else {
      Message.error(result.message || locale.deleteToolFailed);
    }
  };

  const deleteTool = () => {
    setVisible(true);
  };
  const closeDialog = () => {
    setVisible(false);
  };

  return (
    <>
      <a onClick={deleteTool} style={{ color: '#FF4D4F' }}>
        {locale.operationToolDelete}
      </a>
      <Dialog
        visible={visible}
        title={locale.deleteToolTitle}
        content={locale.deleteToolContent}
        onOk={() => {
          const _serverConfig = {
            ...serverConfig,
            tools: serverConfig?.tools
              ? serverConfig.tools.filter(item => item.name !== record.name)
              : [],
          };
          handleDelete(_serverConfig);
        }}
        okProps={{
          loading,
        }}
        onCancel={closeDialog}
      >
        <div style={{ minWidth: 300 }}>
          <div>{locale.deleteToolContent}</div>
          <div>
            {locale?.toolName}: {record?.name}
          </div>
        </div>
      </Dialog>
    </>
  );
};

export default DeleteTool;
