
/* eslint-disable react/jsx-indent-props */
import React, { useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Form, Input, Switch } from '@alifd/next';
import { formitemLayout } from './components';

const FormItem = Form.Item;

/**
 * AnnotationsEditor component for editing MCP Tool annotations.
 * Annotations are hints describing tool behavior to clients.
 */
const AnnotationsEditor = ({ locale, field, isPreview, refreshKey }) => {
    const { init, getValue } = field;

    const initAnnotations = useCallback(() => {
        // Initialize with default values if not set
        if (getValue('annotationsTitle') === undefined) {
            field.setValues({
                annotationsTitle: '',
                readOnlyHint: false,
                destructiveHint: true,
                idempotentHint: false,
                openWorldHint: true,
            });
        }
    }, [field, getValue]);

    useEffect(() => {
        initAnnotations();
    }, [refreshKey, initAnnotations]);

    return (
        <div className="annotations-editor-container" style={{ padding: '0 24px' }}>
            {/* Title */}
            <FormItem
                {...formitemLayout}
                label={locale?.annotationsTitle || 'Title'}
                help={locale?.annotationsTitleHelp || 'A human-readable title for the tool'}
            >
                <Input
                    placeholder={locale?.annotationsTitlePlaceholder || 'Enter tool title'}
                    disabled={isPreview}
                    style={{ borderRadius: '6px' }}
                    {...init('annotationsTitle')}
                />
            </FormItem>

            {/* Read Only Hint */}
            <FormItem
                {...formitemLayout}
                label={locale?.readOnlyHint || 'Read Only'}
                help={locale?.readOnlyHintHelp || 'If true, the tool does not modify its environment (Default: false)'}
            >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                        disabled={isPreview}
                        {...init('readOnlyHint', {
                            valueName: 'checked',
                            initValue: false,
                        })}
                    />
                    <span style={{ marginLeft: '12px', color: '#666', fontSize: '14px' }}>
                        {getValue('readOnlyHint') ? (locale?.yes || 'Yes') : (locale?.no || 'No')}
                    </span>
                </div>
            </FormItem>

            {/* Destructive Hint */}
            <FormItem
                {...formitemLayout}
                label={locale?.destructiveHint || 'Destructive'}
                help={locale?.destructiveHintHelp || 'If true, the tool may perform destructive updates (Default: true)'}
            >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                        disabled={isPreview || getValue('readOnlyHint')}
                        {...init('destructiveHint', {
                            valueName: 'checked',
                            initValue: true,
                        })}
                    />
                    <span style={{ marginLeft: '12px', color: '#666', fontSize: '14px' }}>
                        {getValue('destructiveHint') ? (locale?.yes || 'Yes') : (locale?.no || 'No')}
                    </span>
                    {getValue('readOnlyHint') && (
                        <span style={{ marginLeft: '8px', color: '#999', fontSize: '12px' }}>
                            ({locale?.notApplicableWhenReadOnly || 'Not applicable when Read Only is true'})
                        </span>
                    )}
                </div>
            </FormItem>

            {/* Idempotent Hint */}
            <FormItem
                {...formitemLayout}
                label={locale?.idempotentHint || 'Idempotent'}
                help={locale?.idempotentHintHelp || 'If true, repeated calls with same arguments have no additional effect (Default: false)'}
            >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                        disabled={isPreview || getValue('readOnlyHint')}
                        {...init('idempotentHint', {
                            valueName: 'checked',
                            initValue: false,
                        })}
                    />
                    <span style={{ marginLeft: '12px', color: '#666', fontSize: '14px' }}>
                        {getValue('idempotentHint') ? (locale?.yes || 'Yes') : (locale?.no || 'No')}
                    </span>
                    {getValue('readOnlyHint') && (
                        <span style={{ marginLeft: '8px', color: '#999', fontSize: '12px' }}>
                            ({locale?.notApplicableWhenReadOnly || 'Not applicable when Read Only is true'})
                        </span>
                    )}
                </div>
            </FormItem>

            {/* Open World Hint */}
            <FormItem
                {...formitemLayout}
                label={locale?.openWorldHint || 'Open World'}
                help={locale?.openWorldHintHelp || 'If true, the tool may interact with external entities (Default: true)'}
            >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                        disabled={isPreview}
                        {...init('openWorldHint', {
                            valueName: 'checked',
                            initValue: true,
                        })}
                    />
                    <span style={{ marginLeft: '12px', color: '#666', fontSize: '14px' }}>
                        {getValue('openWorldHint') ? (locale?.yes || 'Yes') : (locale?.no || 'No')}
                    </span>
                </div>
            </FormItem>
        </div>
    );
};

AnnotationsEditor.propTypes = {
    locale: PropTypes.object,
    field: PropTypes.object.isRequired,
    isPreview: PropTypes.bool,
    refreshKey: PropTypes.number,
};

export default AnnotationsEditor;
