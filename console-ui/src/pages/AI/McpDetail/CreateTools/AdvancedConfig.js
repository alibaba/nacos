
/* eslint-disable react/jsx-indent-props */
import React, { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Button, Form, Switch, Select, Tag, Input } from '@alifd/next';
import { Grid } from '@alifd/next';
import MonacoEditor from '../../../../components/MonacoEditor';
import './CreateTools.css';

const { Row, Col } = Grid;

const AdvancedConfig = ({
    locale,
    field,
    serverConfig,
    onlyEditRuntimeInfo,
    initialOriginalTemplate,
    refreshKey,
}) => {
    const { init, getValue, setValue } = field;
    const [originalTemplate, setOriginalTemplate] = useState('');
    const [editorKey, setEditorKey] = useState(0);
    const [showTemplateHelp, setShowTemplateHelp] = useState(false);

    useEffect(() => {
        if (initialOriginalTemplate !== undefined) {
            setOriginalTemplate(initialOriginalTemplate);
            setEditorKey(prev => prev + 1);
            setShowTemplateHelp(false);
        }
    }, [refreshKey, initialOriginalTemplate]);

    const parseTemplateContent = content => {
        if (!content || content.trim().length === 0) {
            return null;
        }
        try {
            return JSON.parse(content);
        } catch (jsonError) {
            throw new Error('Invalid template format. Please provide valid JSON.');
        }
    };

    const validateTemplateFormat = (rule, value, callback) => {
        if (!value || value.trim().length === 0) {
            callback();
            return;
        }

        try {
            // 只验证 JSON 格式
            const parsed = JSON.parse(value);

            // 验证必填字段
            if (parsed.requestTemplate) {
                if (!parsed.requestTemplate.url) {
                    callback(locale.requestTemplateUrlRequired || 'requestTemplate.url is required');
                    return;
                }
                if (!parsed.requestTemplate.method) {
                    callback(locale.requestTemplateMethodRequired || 'requestTemplate.method is required');
                    return;
                }

                // 验证互斥字段
                const mutexFields = ['body', 'argsToJsonBody', 'argsToUrlParam', 'argsToFormBody'];
                const activeMutexFields = mutexFields.filter(field => parsed.requestTemplate[field]);
                if (activeMutexFields.length > 1) {
                    callback((locale.requestTemplateMutexFields || 'The following fields in requestTemplate are mutually exclusive, only one can be selected: {0}').replace('{0}', activeMutexFields.join(', ')));
                    return;
                }
            }

            // 验证 argsPosition 的有效值
            if (parsed.argsPosition) {
                if (typeof parsed.argsPosition !== 'object' || Array.isArray(parsed.argsPosition)) {
                    callback(locale.argsPositionMustBeObject || 'argsPosition must be an object');
                    return;
                }

                const validPositions = ['query', 'path', 'header', 'cookie', 'body'];
                const invalidPositions = Object.values(parsed.argsPosition).filter(
                    position => !validPositions.includes(position)
                );

                if (invalidPositions.length > 0) {
                    callback(
                        (locale.argsPositionInvalidValues || 'argsPosition values must be one of: {0}, invalid values found: {1}')
                            .replace('{0}', validPositions.join(', '))
                            .replace('{1}', invalidPositions.join(', '))
                    );
                    return;
                }
            }

            // 验证 mcpServers 数组长度（仅在 Local Server 配置时）
            if (parsed.mcpServers && Array.isArray(parsed.mcpServers)) {
                if (parsed.mcpServers.length !== 1) {
                    callback(locale.mcpServersOneElement || 'mcpServers can only contain one element');
                    return;
                }
            }

            if (parsed.responseTemplate) {
                // 验证响应模板互斥字段
                const responseFields = ['body', 'prependBody', 'appendBody'];
                const hasBody = !!parsed.responseTemplate.body;
                const hasPrependOrAppend = !!(
                    parsed.responseTemplate.prependBody || parsed.responseTemplate.appendBody
                );

                if (hasBody && hasPrependOrAppend) {
                    callback(locale.responseTemplateMutexFields || 'body and prependBody/appendBody in responseTemplate are mutually exclusive');
                    return;
                }
            }

            callback();
        } catch (jsonError) {
            callback(locale.templateShouldBeJson || 'Template format error, please enter valid JSON format');
        }
    };

    const generateDefaultTemplate = () => {
        const defaultTemplate = {
            requestTemplate: {
                url: 'https://api.example.com/endpoint',
                method: 'GET',
                headers: [
                    {
                        key: 'Authorization',
                        value: 'Bearer your key',
                    },
                ],
                argsToUrlParam: true,
            },
            responseTemplate: {
                body: '{{.}}',
            },
        };

        const templateStr = JSON.stringify(defaultTemplate, null, 2);
        setOriginalTemplate(templateStr);
        setValue('templates', templateStr);
        setEditorKey(prev => prev + 1);
    };

    const generateTemplateByType = type => {
        let template = {};

        const getCurrentParams = () => {
            const toolParams = getValue('toolParams');
            if (toolParams && typeof toolParams === 'object') {
                return Object.keys(toolParams);
            }
            return ['id', 'name'];
        };

        switch (type) {
            case 'json-body':
                template = {
                    requestTemplate: {
                        url: 'https://api.example.com/endpoint',
                        method: 'POST',
                        headers: [
                            { key: 'Content-Type', value: 'application/json' },
                            { key: 'Authorization', value: 'Bearer your key' },
                        ],
                        argsToJsonBody: true,
                    },
                    responseTemplate: {
                        body: '{{.}}',
                    },
                };
                break;

            case 'url-params':
                template = {
                    requestTemplate: {
                        url: 'https://api.example.com/endpoint',
                        method: 'GET',
                        headers: [{ key: 'Authorization', value: 'Bearer your key' }],
                        argsToUrlParam: true,
                    },
                    responseTemplate: {
                        body: '{{.}}',
                    },
                };
                break;

            case 'form-body':
                template = {
                    requestTemplate: {
                        url: 'https://api.example.com/endpoint',
                        method: 'POST',
                        headers: [
                            { key: 'Content-Type', value: 'application/x-www-form-urlencoded' },
                            { key: 'Authorization', value: 'Bearer your key' },
                        ],
                        argsToFormBody: true,
                    },
                    responseTemplate: {
                        body: '{{.}}',
                    },
                };
                break;

            case 'custom-body':
                template = {
                    requestTemplate: {
                        url: 'https://api.example.com/endpoint',
                        method: 'POST',
                        headers: [
                            { key: 'Content-Type', value: 'application/json' },
                            { key: 'Authorization', value: 'Bearer your key' },
                        ],
                        body: '{\n  "query": "{{.args.query}}",\n  "limit": {{.args.limit}}\n}',
                    },
                    responseTemplate: {
                        body: '{{.}}',
                    },
                };
                break;

            case 'args-path':
                const currentParams = getCurrentParams();
                const argsPosition = {};

                currentParams.forEach((paramName, index) => {
                    if (index === 0) {
                        argsPosition[paramName] = 'path';
                    } else {
                        argsPosition[paramName] = 'query';
                    }
                });

                let url = 'https://api.example.com/endpoint';

                template = {
                    requestTemplate: {
                        url: url,
                        method: 'GET',
                        headers: [{ key: 'Authorization', value: 'Bearer your key' }],
                    },
                    responseTemplate: {
                        body: '{{.}}',
                    },
                    argsPosition: argsPosition,
                };
                break;

            default:
                return generateDefaultTemplate();
        }

        const templateStr = JSON.stringify(template, null, 2);
        setOriginalTemplate(templateStr);
        setValue('templates', templateStr);
        setEditorKey(prev => prev + 1);
    };

    const handleEditorChange = value => {
        setOriginalTemplate(value);
        setValue('templates', value);
    };

    const generateTemplateWithSecurity = () => {
        try {
            if (!originalTemplate || !getValue('transparentAuth')) {
                return originalTemplate;
            }

            let templateObject = parseTemplateContent(originalTemplate);
            let modified = false;

            const securitySchemeId = getValue('securitySchemeId');
            if (securitySchemeId) {
                const selectedScheme = serverConfig?.toolSpec?.securitySchemes?.find(
                    scheme => scheme.id === securitySchemeId
                );

                if (selectedScheme) {
                    if (!templateObject.requestTemplate) {
                        templateObject.requestTemplate = {};
                    }
                    templateObject.requestTemplate.security = {
                        id: selectedScheme.id,
                    };
                    modified = true;
                }
            }

            const clientSecuritySchemeId = getValue('clientSecuritySchemeId');
            if (clientSecuritySchemeId) {
                const clientSelectedScheme = serverConfig?.toolSpec?.securitySchemes?.find(
                    scheme => scheme.id === clientSecuritySchemeId
                );

                if (clientSelectedScheme) {
                    templateObject.security = {
                        id: clientSelectedScheme.id,
                        passthrough: true,
                    };
                    modified = true;
                }
            }

            return modified ? JSON.stringify(templateObject, null, 2) : originalTemplate;
        } catch (error) {
            return originalTemplate;
        }
    };

    return (
        <div className="create-tools-card">
            <Form.Item
                label={locale.invokeTemplates}
                extra={
                    <div
                        style={{
                            color: '#666',
                            fontSize: '13px',
                            marginTop: '8px',
                            padding: '8px 12px',
                            backgroundColor: '#f6f8fa',
                            borderRadius: '4px',
                            border: '1px solid #e1e4e8',
                        }}
                    >
                        <div style={{ marginBottom: '8px' }}>
                            {locale.templateDocumentation || 'Protocol conversion through the gateway-provided template, see documentation'}{' '}
                            <a
                                href="https://nacos.io/docs/v3.0/manual/user/mcp-template"
                                target="_blank"
                                rel="noopener noreferrer"
                                style={{ color: '#1890ff' }}
                            >
                                https://nacos.io/docs/v3.0/manual/user/mcp-template
                            </a>
                        </div>

                        <div style={{ marginTop: '8px' }}>
                            <div
                                onClick={() => setShowTemplateHelp(!showTemplateHelp)}
                                style={{
                                    cursor: 'pointer',
                                    color: '#52c41a',
                                    fontSize: '12px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px',
                                    userSelect: 'none',
                                }}
                            >
                                <span>{showTemplateHelp ? '▼' : '▶'}</span>
                                📋 {locale.configurationDetails || 'Configuration Options Details'}
                            </div>

                            {showTemplateHelp && (
                                <div
                                    style={{
                                        fontSize: '11px',
                                        color: '#666',
                                        marginTop: '8px',
                                        marginLeft: '16px',
                                        lineHeight: '1.4',
                                        padding: '8px',
                                        backgroundColor: '#fafafa',
                                        borderRadius: '4px',
                                        border: '1px solid #e8e8e8',
                                    }}
                                >
                                    <div style={{ marginBottom: '4px', fontWeight: '500', color: '#333' }}>
                                        {locale.requestBodyConfig || 'Request Body Configuration (mutually exclusive, only one can be selected)'}:
                                    </div>
                                    • <strong>argsToJsonBody</strong>: {locale.argsAsJsonBody || 'Parameters as JSON request body'}
                                    <br />• <strong>argsToUrlParam</strong>: {locale.argsAsUrlParams || 'Parameters as URL query parameters'}
                                    <br />• <strong>argsToFormBody</strong>: {locale.argsAsFormData || 'Parameters as form data'}
                                    <br />• <strong>body</strong>: {locale.customBodyTemplate || 'Custom request body template'}
                                    <br />
                                    <div
                                        style={{
                                            marginTop: '8px',
                                            marginBottom: '4px',
                                            fontWeight: '500',
                                            color: '#333',
                                        }}
                                    >
                                        {locale.argsPositionConfig || 'Arguments Position Configuration'}:
                                    </div>
                                    • <strong>argsPosition</strong>:
                                    {locale.argsPositionHelp || 'Parameter position mapping object to specify the location of each parameter in the request'}
                                    <br />
                                    <div
                                        style={{
                                            marginTop: '8px',
                                            marginBottom: '4px',
                                            fontWeight: '500',
                                            color: '#333',
                                        }}
                                    >
                                        {locale.responseProcessingConfig || 'Response Processing Configuration (mutually exclusive)'}:
                                    </div>
                                    • <strong>responseTemplate.body</strong>: {locale.fullResponseTemplate || 'Complete response transformation template'}
                                    <br />• <strong>responseTemplate.prependBody/appendBody</strong>:
                                    {locale.responsePrefixSuffix || 'Response prefix/suffix text'}
                                    <br />
                                    <div
                                        style={{
                                            marginTop: '8px',
                                            marginBottom: '4px',
                                            fontWeight: '500',
                                            color: '#333',
                                        }}
                                    >
                                        {locale.parameterPositionDescription || 'Parameter Position Description'}:
                                    </div>
                                    • <strong>query</strong>: {locale.paramAsQueryString || 'Parameter as URL query string'}
                                    <br />• <strong>path</strong>: {locale.paramAsPathVariable || 'Parameter as URL path variable'}
                                    <br />• <strong>header</strong>: {locale.paramAsHttpHeader || 'Parameter as HTTP request header'}
                                    <br />• <strong>cookie</strong>: {locale.paramAsCookieValue || 'Parameter as Cookie value'}
                                    <br />• <strong>body</strong>: {locale.paramAsBodyContent || 'Parameter as request body content'}
                                </div>
                            )}
                        </div>
                    </div>
                }
                style={{ marginBottom: '24px' }}
            >
                {!onlyEditRuntimeInfo && (
                    <div className="template-generator-container">
                        <div
                            style={{
                                marginBottom: '8px',
                                fontSize: '13px',
                                fontWeight: '500',
                                color: '#24292e',
                            }}
                        >
                            {locale.templateGenerator || '配置模板生成器'}
                        </div>
                        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                            <Button
                                type="normal"
                                size="small"
                                onClick={() => generateTemplateByType('json-body')}
                                style={{
                                    borderRadius: '4px',
                                    fontSize: '12px',
                                    backgroundColor: '#e3f2fd',
                                    borderColor: '#2196f3',
                                    color: '#1976d2',
                                }}
                            >
                                {locale.jsonRequestBody || 'JSON Request Body'}
                            </Button>
                            <Button
                                type="normal"
                                size="small"
                                onClick={() => generateTemplateByType('url-params')}
                                style={{
                                    borderRadius: '4px',
                                    fontSize: '12px',
                                    backgroundColor: '#f3e5f5',
                                    borderColor: '#9c27b0',
                                    color: '#7b1fa2',
                                }}
                            >
                                {locale.urlParameters || 'URL Parameters'}
                            </Button>
                            <Button
                                type="normal"
                                size="small"
                                onClick={() => generateTemplateByType('form-body')}
                                style={{
                                    borderRadius: '4px',
                                    fontSize: '12px',
                                    backgroundColor: '#e8f5e8',
                                    borderColor: '#4caf50',
                                    color: '#388e3c',
                                }}
                            >
                                {locale.formData || 'Form Data'}
                            </Button>
                            <Button
                                type="normal"
                                size="small"
                                onClick={() => generateTemplateByType('custom-body')}
                                style={{
                                    borderRadius: '4px',
                                    fontSize: '12px',
                                    backgroundColor: '#fff3e0',
                                    borderColor: '#ff9800',
                                    color: '#f57c00',
                                }}
                            >
                                {locale.customRequestBody || 'Custom Request Body'}
                            </Button>
                            <Button
                                type="normal"
                                size="small"
                                onClick={() => generateTemplateByType('args-path')}
                                style={{
                                    borderRadius: '4px',
                                    fontSize: '12px',
                                    backgroundColor: '#fce4ec',
                                    borderColor: '#e91e63',
                                    color: '#c2185b',
                                }}
                            >
                                {locale.argsPositionPath || 'Args Position - Path'}
                            </Button>
                        </div>
                        <div
                            style={{
                                marginTop: '8px',
                                fontSize: '11px',
                                color: '#586069',
                                lineHeight: '1.4',
                            }}
                        >
                            💡 {locale.quickGenerateTemplate || 'Click buttons to quickly generate corresponding configuration templates, including complete request and response configuration'}
                            <br />
                            🔹 {locale.argsPositionMode || 'Args Position Mode: Specify the location of each parameter in the request through argsPosition object (supports query/path/header/cookie/body)'}
                        </div>
                    </div>
                )}
                {onlyEditRuntimeInfo ? (
                    <div
                        style={{
                            backgroundColor: '#f6f7f9',
                            border: '1px solid #dcdee3',
                            borderRadius: '8px',
                            padding: '16px',
                            fontSize: '13px',
                            fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
                            lineHeight: '1.6',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-all',
                            maxHeight: '400px',
                            overflow: 'auto',
                            margin: 0,
                        }}
                    >
                        {(() => {
                            try {
                                const templateValue = generateTemplateWithSecurity();
                                if (!templateValue) return templateValue;

                                // 尝试解析并格式化为JSON显示
                                const parsedTemplate = parseTemplateContent(templateValue);
                                return JSON.stringify(parsedTemplate, null, 2);
                            } catch (error) {
                                // 如果解析失败，返回原始内容
                                return generateTemplateWithSecurity();
                            }
                        })()}
                    </div>
                ) : (
                    <div
                        style={{
                            border: '1px solid #d9d9d9',
                            borderRadius: '8px',
                            overflow: 'hidden',
                        }}
                    >
                        <Form.Item required requiredTrigger="onBlur" style={{ marginBottom: 0 }}>
                            <div style={{ height: '250px' }}>
                                <MonacoEditor
                                    key={editorKey}
                                    language="json"
                                    height="250px"
                                    value={generateTemplateWithSecurity()}
                                    onChange={handleEditorChange}
                                    options={{
                                        minimap: { enabled: false },
                                        scrollBeyondLastLine: false,
                                        fontSize: 13,
                                        tabSize: 2,
                                        insertSpaces: true,
                                        wordWrap: 'on',
                                        lineNumbers: 'on',
                                        formatOnPaste: true,
                                        formatOnType: true,
                                        theme: 'vs',
                                        renderLineHighlight: 'all',
                                        selectOnLineNumbers: true,
                                    }}
                                />
                            </div>
                            <Input.TextArea
                                style={{ display: 'none' }}
                                {...init('templates', {
                                    rules: [
                                        {
                                            validator: validateTemplateFormat,
                                        },
                                    ],
                                })}
                            />
                        </Form.Item>
                    </div>
                )}
            </Form.Item>

            <Form.Item
                label={
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span>{locale.transparentAuth || 'Enable Transparent Auth'}</span>
                        <Tag color="orange" style={{ margin: 0, fontSize: '12px' }}>
                            {locale.security || 'Security'}
                        </Tag>
                    </div>
                }
                style={{ marginBottom: '24px' }}
            >
                <div style={{ display: 'flex', alignItems: 'center' }}>
                    <Switch
                        size="large"
                        {...init('transparentAuth', {
                            valueName: 'checked',
                            initValue: false,
                        })}
                        checkedChildren={locale.enable || 'Enable'}
                        unCheckedChildren={locale.disable || 'Disable'}
                        isPreview={onlyEditRuntimeInfo}
                    />
                    <span
                        style={{
                            marginLeft: '12px',
                            color: '#666',
                            fontSize: '14px',
                        }}
                    >
                        {getValue('transparentAuth') ? (locale.authInfoTransparent || 'Authentication information will be transparently passed') : (locale.useDefaultAuth || 'Use default authentication method')}
                    </span>
                </div>
            </Form.Item>

            {getValue('transparentAuth') && (
                <div className="security-schemes-container">
                    <h4
                        style={{
                            margin: '0 0 16px 0',
                            fontSize: '14px',
                            fontWeight: '600',
                            color: '#495057',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                        }}
                    >
                        {locale.securitySchemes || 'Security Schemes Configuration'}
                    </h4>

                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item
                                label={
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <span>{locale.backendAuth || 'Backend Authentication'}</span>
                                    </div>
                                }
                                style={{ marginBottom: '16px' }}
                            >
                                <Select
                                    size="large"
                                    style={{ borderRadius: '6px' }}
                                    {...init('securitySchemeId', {
                                        rules: [
                                            {
                                                required: true,
                                                message: locale.pleaseSelectSecurityScheme || 'Please select security scheme',
                                            },
                                        ],
                                    })}
                                    dataSource={
                                        serverConfig?.toolSpec?.securitySchemes?.map(scheme => ({
                                            label: `${scheme.id} (${scheme.type})`,
                                            value: scheme.id,
                                        })) || []
                                    }
                                    placeholder={locale.pleaseSelectSecurityScheme || 'Please select security scheme'}
                                    isPreview={onlyEditRuntimeInfo}
                                    onChange={value => {
                                        setValue('securitySchemeId', value);
                                        setTimeout(() => {
                                            setValue('templates', generateTemplateWithSecurity());
                                        }, 0);
                                    }}
                                />
                            </Form.Item>
                        </Col>

                        <Col span={12}>
                            <Form.Item
                                label={
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <span>{locale.clientAuth || 'Client Authentication'}</span>
                                    </div>
                                }
                                style={{ marginBottom: '16px' }}
                            >
                                <Select
                                    size="large"
                                    style={{ borderRadius: '6px' }}
                                    {...init('clientSecuritySchemeId', {
                                        rules: [
                                            {
                                                required: true,
                                                message: locale.pleaseSelectSecurityScheme || 'Please select security scheme',
                                            },
                                        ],
                                    })}
                                    dataSource={
                                        serverConfig?.toolSpec?.securitySchemes?.map(scheme => ({
                                            label: `${scheme.id} (${scheme.type})`,
                                            value: scheme.id,
                                        })) || []
                                    }
                                    placeholder={locale.pleaseSelectSecurityScheme || 'Please select security scheme'}
                                    isPreview={onlyEditRuntimeInfo}
                                    onChange={value => {
                                        setValue('clientSecuritySchemeId', value);
                                        setTimeout(() => {
                                            setValue('templates', generateTemplateWithSecurity());
                                        }, 0);
                                    }}
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                </div>
            )}
        </div>
    );
};

AdvancedConfig.propTypes = {
    locale: PropTypes.object.isRequired,
    field: PropTypes.object.isRequired,
    serverConfig: PropTypes.object,
    onlyEditRuntimeInfo: PropTypes.bool,
    initialOriginalTemplate: PropTypes.string,
    refreshKey: PropTypes.number,
};

export default AdvancedConfig;
