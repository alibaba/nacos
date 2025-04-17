import React from 'react';
import { Form, Table, Button } from '@alifd/next';

// 表单布局
export const formitemLayout = {
  labelCol: {
    span: 4,
  },
  wrapperCol: {
    span: 20,
  },
};

// 表格头组件
export const GetTitle = ({ label, onClick, text = '', locale }) => (
  <Form.Item {...formitemLayout} label={label} required style={{ margin: '16px 0 0' }}>
    <Button type="primary" onClick={onClick} size="small">
      {text || locale.newMcpTool}
    </Button>
  </Form.Item>
);

// 表格操作组件
export const tableOperation = ({ onClick, locale }) => (
  <Table.Column
    title={locale.toolOperation}
    width={40}
    dataIndex={'operation'}
    cell={(t, index, record) => {
      return (
        <Button type="primary" size="small" warning onClick={() => onClick(index)}>
          {locale.deleteParam}
        </Button>
      );
    }}
  />
);
