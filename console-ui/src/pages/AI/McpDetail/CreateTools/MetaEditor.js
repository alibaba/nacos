
/* eslint-disable react/jsx-indent-props */
import React, { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Form, Input } from '@alifd/next';
import { formitemLayout } from './components';

const FormItem = Form.Item;

/**
 * MetaEditor component for editing _meta field of MCP Tool.
 * The _meta field is a JSON object that can contain arbitrary key-value pairs.
 */
const MetaEditor = ({ locale, field, isPreview, refreshKey }) => {
    const [jsonError, setJsonError] = useState('');

    const initMeta = useCallback(() => {
        const currentMeta = field.getValue('meta');
        if (currentMeta === undefined) {
            field.setValue('meta', '');
        }
    }, [field]);

    // Initialize meta from field values
    useEffect(() => {
        initMeta();
    }, [refreshKey, initMeta]);

    const handleMetaChange = (value) => {
        field.setValue('meta', value);

        // Validate JSON format
        if (value && value.trim()) {
            try {
                JSON.parse(value);
                setJsonError('');
            } catch (e) {
                setJsonError(locale?.metaJsonError || 'Invalid JSON format');
            }
        } else {
            setJsonError('');
        }
    };

    const currentValue = field.getValue('meta') || '';

    return (
        <div className="meta-editor-container" style={{ padding: '0 24px' }}>
            <FormItem
                {...formitemLayout}
                label={
                    <span>
                        {locale?.metaField || 'Meta'}
                        <span
                            style={{
                                marginLeft: '8px',
                                color: '#999',
                                fontSize: '12px',
                                fontWeight: 'normal'
                            }}
                        >
                            ({locale?.metaFieldDesc || 'MCP protocol _meta field, JSON format'})
                        </span>
                    </span>
                }
                validateState={jsonError ? 'error' : undefined}
                help={jsonError}
            >
                <Input.TextArea
                    name="meta"
                    placeholder={locale?.metaPlaceholder || '{\n  "hint": "example value"\n}'}
                    value={currentValue}
                    onChange={handleMetaChange}
                    disabled={isPreview}
                    autoHeight={{ minRows: 6, maxRows: 15 }}
                    style={{
                        fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                        fontSize: '13px'
                    }}
                />
            </FormItem>
        </div>
    );
};

MetaEditor.propTypes = {
    locale: PropTypes.object,
    field: PropTypes.object.isRequired,
    isPreview: PropTypes.bool,
    refreshKey: PropTypes.number,
};

export default MetaEditor;
