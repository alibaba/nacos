import React from 'react';
import { Form, Table, Button } from '@alifd/next';
import PropTypes from 'prop-types';

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
export const GetTitle = ({
  label,
  onClick,
  text = '',
  locale,
  disabled = false,
  required = true,
}) => (
  <Form.Item {...formitemLayout} label={label} required={required} style={{ margin: '16px 0 0' }}>
    {disabled ? null : (
      <Button type="primary" onClick={onClick} size="small">
        {text || locale.newMcpTool}
      </Button>
    )}
  </Form.Item>
);

GetTitle.propTypes = {
  label: PropTypes.string,
  onClick: PropTypes.func,
  text: PropTypes.string,
  locale: PropTypes.object,
  disabled: PropTypes.bool,
  required: PropTypes.bool,
};

// 表格操作组件
export const tableOperation = ({ onClick, locale, disabled = false }) => {
  if (disabled) {
    return null;
  }

  return (
    <Table.Column
      title={locale.toolOperation}
      width={100}
      dataIndex={'operation'}
      cell={(t, index, record) => {
        return (
          <Button type="primary" warning onClick={() => onClick(index)}>
            {locale.deleteParam}
          </Button>
        );
      }}
    />
  );
};
