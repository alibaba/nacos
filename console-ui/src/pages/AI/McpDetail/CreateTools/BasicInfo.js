
/* eslint-disable react/jsx-indent-props */
import React from 'react';
import PropTypes from 'prop-types';
import { Form, Input, Switch } from '@alifd/next';
import './CreateTools.css';

const BasicInfo = ({ locale, field, type, serverConfig, isPreview }) => {
    const { init, getValue } = field;

    return (
        <div className="create-tools-card">
            {/* 名称 */}
            <Form.Item
                label={locale.toolName}
                required
                isPreview={!!type}
                style={{ marginBottom: '20px' }}
            >
                <Input
                    placeholder={locale.toolName}
                    size="large"
                    style={{ borderRadius: '6px' }}
                    {...init('name', {
                        rules: [
                            { required: true, message: locale.toolNameRequired },
                            {
                                validator: (rule, value, callback) => {
                                    const _tools = serverConfig?.toolSpec?.tools || [];
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
            <Form.Item label={locale.toolDescription} required style={{ marginBottom: '20px' }}>
                <Input.TextArea
                    placeholder={locale.toolDescription}
                    size="large"
                    style={{ borderRadius: '6px', minHeight: '80px' }}
                    {...init('description', {
                        rules: [{ required: true, message: locale.toolDescriptionRequired }],
                    })}
                />
            </Form.Item>

            {/* 是否上线 */}
            <Form.Item label={locale.toolOnline} required style={{ marginBottom: '0' }}>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                        size="large"
                        {...init('enabled', {
                            valueName: 'checked',
                            initValue: true,
                            props: isPreview
                                ? {
                                    checkedChildren: locale.online,
                                    unCheckedChildren: locale.offline,
                                }
                                : {},
                        })}
                    />
                    <span
                        style={{
                            marginLeft: '12px',
                            color: '#666',
                            fontSize: '14px',
                        }}
                    >
                        {getValue('enabled') ? locale.online || '上线' : locale.offline || '下线'}
                    </span>
                </div>
            </Form.Item>
        </div>
    );
};

BasicInfo.propTypes = {
    locale: PropTypes.object.isRequired,
    field: PropTypes.object.isRequired,
    type: PropTypes.string,
    serverConfig: PropTypes.object,
    isPreview: PropTypes.bool,
};

export default BasicInfo;
