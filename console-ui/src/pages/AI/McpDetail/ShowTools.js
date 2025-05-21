import React, { useRef } from 'react';
import { Table, Button } from '@alifd/next';
import CreateTools from './CreateTools';
import DeleteTool from './CreateTools/DeleteTool';

const ShowTools = props => {
  const {
    serverConfig = {
      protocol: '',
    },
    locale,
    isPreview = false,
  } = props;
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

  return (
    <div>
      {!isPreview && (
        <Button type="primary" onClick={openDialog}>
          {locale.newMcpTool}
        </Button>
      )}

      <CreateTools
        key={JSON.stringify(serverConfig)}
        locale={locale}
        serverConfig={serverConfig}
        showTemplates={serverConfig?.protocol === 'http'}
        ref={toolsRef}
        getServerDetail={getServerDetail}
        onChange={props?.onChange}
      />

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
                <span style={{ margin: '0 5px' }}>|</span>
                <DeleteTool
                  record={record}
                  locale={locale}
                  serverConfig={serverConfig}
                  getServerDetail={getServerDetail}
                  onChange={props?.onChange}
                />
              </div>
            );
          }}
        />
      </Table>
    </div>
  );
};

export default ShowTools;
