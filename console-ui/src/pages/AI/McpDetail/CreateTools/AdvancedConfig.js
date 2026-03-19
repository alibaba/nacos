
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
                    callback('requestTemplate.url 是必填字段');
                    return;
                }
                if (!parsed.requestTemplate.method) {
                    callback('requestTemplate.method 是必填字段');
                    return;
                }

                // 验证互斥字段
                const mutexFields = ['body', 'argsToJsonBody', 'argsToUrlParam', 'argsToFormBody'];
                const activeMutexFields = mutexFields.filter(field => parsed.requestTemplate[field]);
                if (activeMutexFields.length > 1) {
                    callback(`requestTemplate 中 ${activeMutexFields.join(', ')} 字段互斥，只能选择一个`);
                    return;
                }
            }

            // 验证 argsPosition 的有效值
            if (parsed.argsPosition) {
                if (typeof parsed.argsPosition !== 'object' || Array.isArray(parsed.argsPosition)) {
                    callback('argsPosition 必须是一个对象');
                    return;
                }

                const validPositions = ['query', 'path', 'header', 'cookie', 'body'];
                const invalidPositions = Object.values(parsed.argsPosition).filter(
                    position => !validPositions.includes(position)
                );

                if (invalidPositions.length > 0) {
                    callback(
                        `argsPosition 的值必须是以下之一: ${validPositions.join(
                            ', '
                        )}，发现无效值: ${invalidPositions.join(', ')}`
                    );
                    return;
                }
            }

            // 验证 mcpServers 数组长度（仅在 Local Server 配置时）
            if (parsed.mcpServers && Array.isArray(parsed.mcpServers)) {
                if (parsed.mcpServers.length !== 1) {
                    callback('mcpServers 只能包含一个元素');
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
                    callback('responseTemplate 中 body 与 prependBody/appendBody 互斥');
                    return;
                }
            }

            callback();
        } catch (jsonError) {
            callback(locale.templateShouldBeJson || '模板格式错误，请输入有效的 JSON 格式');
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
                            通过网关提供的协议转化模版进行协议转化，详情请见文档{' '}
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
                                📋 配置选项详细说明
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
                                        请求体配置（以下选项互斥，只能选择一个）：
                                    </div>
                                    • <strong>argsToJsonBody</strong>: 参数作为JSON请求体
                                    <br />• <strong>argsToUrlParam</strong>: 参数作为URL查询参数
                                    <br />• <strong>argsToFormBody</strong>: 参数作为表单数据
                                    <br />• <strong>body</strong>: 自定义请求体模板
                                    <br />
                                    <div
                                        style={{
                                            marginTop: '8px',
                                            marginBottom: '4px',
                                            fontWeight: '500',
                                            color: '#333',
                                        }}
                                    >
                                        参数位置配置：
                                    </div>
                                    • <strong>argsPosition</strong>:
                                    参数位置映射对象，用于指定每个参数在请求中的具体位置
                                    <br />
                                    <div
                                        style={{
                                            marginTop: '8px',
                                            marginBottom: '4px',
                                            fontWeight: '500',
                                            color: '#333',
                                        }}
                                    >
                                        响应处理配置（以下选项互斥）：
                                    </div>
                                    • <strong>responseTemplate.body</strong>: 完整响应转换模板
                                    <br />• <strong>responseTemplate.prependBody/appendBody</strong>:
                                    响应前后缀文本
                                    <br />
                                    <div
                                        style={{
                                            marginTop: '8px',
                                            marginBottom: '4px',
                                            fontWeight: '500',
                                            color: '#333',
                                        }}
                                    >
                                        参数位置说明：
                                    </div>
                                    • <strong>query</strong>: 参数作为URL查询字符串
                                    <br />• <strong>path</strong>: 参数作为URL路径变量
                                    <br />• <strong>header</strong>: 参数作为HTTP请求头
                                    <br />• <strong>cookie</strong>: 参数作为Cookie值
                                    <br />• <strong>body</strong>: 参数作为请求体内容
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
                                JSON请求体
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
                                URL参数
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
                                表单数据
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
                                自定义请求体
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
                                参数位置-Path
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
                            💡 点击按钮快速生成对应类型的配置模板，包含完整的请求和响应配置
                            <br />
                            🔹 参数位置模式：通过 argsPosition 对象指定每个参数在请求中的位置（支持
                            query/path/header/cookie/body）
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
                        <span>{locale.transparentAuth || '启用透明认证'}</span>
                        <Tag color="orange" style={{ margin: 0, fontSize: '12px' }}>
                            安全
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
                        checkedChildren={locale.enable || '启用'}
                        unCheckedChildren={locale.disable || '禁用'}
                        isPreview={onlyEditRuntimeInfo}
                    />
                    <span
                        style={{
                            marginLeft: '12px',
                            color: '#666',
                            fontSize: '14px',
                        }}
                    >
                        {getValue('transparentAuth') ? '认证信息将透明传递' : '使用默认认证方式'}
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
                        {locale.securitySchemes || '认证方案配置'}
                    </h4>

                    <Row gutter={24}>
                        <Col span={12}>
                            <Form.Item
                                label={
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                        <span>{locale.backendAuth || '后端认证方式'}</span>
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
                                                message: locale.pleaseSelectSecurityScheme || '请选择认证方案',
                                            },
                                        ],
                                    })}
                                    dataSource={
                                        serverConfig?.toolSpec?.securitySchemes?.map(scheme => ({
                                            label: `${scheme.id} (${scheme.type})`,
                                            value: scheme.id,
                                        })) || []
                                    }
                                    placeholder={locale.pleaseSelectSecurityScheme || '请选择认证方案'}
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
                                        <span>{locale.clientAuth || '客户端认证方式'}</span>
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
                                                message: locale.pleaseSelectSecurityScheme || '请选择认证方案',
                                            },
                                        ],
                                    })}
                                    dataSource={
                                        serverConfig?.toolSpec?.securitySchemes?.map(scheme => ({
                                            label: `${scheme.id} (${scheme.type})`,
                                            value: scheme.id,
                                        })) || []
                                    }
                                    placeholder={locale.pleaseSelectSecurityScheme || '请选择认证方案'}
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
