import { Dialog, Message } from '@alifd/next';
import { request } from '../../../../globalLib';
import React from 'react';
import PropTypes from 'prop-types';

const DeleteTool = props => {
  const { record, locale, getServerDetail, serverConfig } = props;
  const [visible, setVisible] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const handleDelete = async _toolSpec => {
    props?.onChange && props.onChange(_toolSpec, closeDialog);
    // setLoading(true);
    // const result = await request({
    //   url: `v3/console/ai/mcp`,
    //   method: 'PUT',
    //   data: {
    //     mcpName: serverConfig?.name,
    //     serverSpecification: JSON.stringify({
    //       type: serverConfig?.type,
    //       name: serverConfig?.name,
    //       description: serverConfig?.description,
    //       version: serverConfig?.version,
    //       enbled: true,
    //       remoteServerConfig: {
    //         exportPath: serverConfig?.remoteServerConfig?.exportPath,
    //       },
    //     }),
    //     toolSpecification: JSON.stringify(_serverConfig?.tools),
    //     endpointSpecification: JSON.stringify({
    //       type: 'REF',
    //       data: serverConfig?.remoteServerConfig?.serviceRef,
    //     }),
    //   },
    // });
    // setLoading(false);

    // if (result.code === 0) {
    //   getServerDetail();
    //   closeDialog();
    //   Message.success(locale.deleteToolSuccess);
    // } else {
    //   Message.error(result.message || locale.deleteToolFailed);
    // }
  };

  const deleteTool = () => {
    setVisible(true);
  };
  const closeDialog = () => {
    setVisible(false);
  };

  return (
    <>
      <a onClick={deleteTool} style={{ fontSize: '12px' }}>
        {locale.operationToolDelete}
      </a>
      <Dialog
        visible={visible}
        title={locale.deleteToolTitle}
        content={locale.deleteToolContent}
        onOk={() => {
          const _toolsMeta = JSON.parse(JSON.stringify(serverConfig?.toolSpec?.toolsMeta));
          delete _toolsMeta[record.name];
          handleDelete({
            tools: serverConfig?.toolSpec?.tools.filter(item => item.name !== record.name),
            toolsMeta: _toolsMeta,
          });
          closeDialog();
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

DeleteTool.propTypes = {
  record: PropTypes.object,
  locale: PropTypes.object,
  getServerDetail: PropTypes.func,
  serverConfig: PropTypes.object,
  onChange: PropTypes.func,
};
